package com.emc.storageos.computesystemcontroller.hostmountadapters;

import static com.emc.sa.service.vipr.ViPRExecutionUtils.logInfo;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.emc.sa.machinetags.MachineTagUtils;
import com.emc.sa.service.linux.LinuxUtils;
import com.emc.sa.service.linux.file.LinuxFileSupport;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.FSExportMap;
import com.emc.storageos.db.client.model.FileExport;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.ScopedLabel;
import com.emc.storageos.db.client.model.ScopedLabelSet;
import com.emc.storageos.model.file.MountInfo;
import com.iwave.ext.linux.LinuxSystemCLI;

public class LinuxHostMountAdapter extends AbstractMountAdapter {

    public LinuxHostMountAdapter(DbClient _dbClient, CoordinatorClient _coordinator) {
        setCoordinator(_coordinator);
        setDbClient(_dbClient);
    }

    public void doMount(HostDeviceInputOutput args) {
        LinuxFileSupport linuxSupport = new LinuxFileSupport(initHost(args.getHostId()));
        FileShare fs = dbClient.queryObject(FileShare.class, args.getResId());
        FileExport export = findExport(fs, args.getSubDirectory(), args.getSecurity());
        // Create directory
        linuxSupport.createDirectory(args.getDestinationMountPath());
        // Add to the /etc/fstab to allow the os to mount on restart
        linuxSupport.addToFSTab(export.getMountPoint(), args.getDestinationMountPath(), "auto", "nolock,sec=" + args.getSecurity());
        // Mount the device
        linuxSupport.mountPath(args.getDestinationMountPath());
        // Set the fs tag containing mount info
        setTag(fs.getId(), MachineTagUtils.generateMountTag(args.getHostId(), args.getDestinationMountPath(),
                args.getSubDirectory(), args.getSecurity()));
    }

    public void doUnmount(HostDeviceInputOutput args) {
        LinuxFileSupport linuxSupport = new LinuxFileSupport(initHost(args.getHostId()));
        FileShare fs = dbClient.queryObject(FileShare.class, args.getResId());
        logInfo("linux.mount.file.export.unmount", args.getDestinationMountPath(), linuxSupport.getHostName());
        // unmount the Export
        linuxSupport.unmountPath(args.getDestinationMountPath());
        // remove from fstab
        linuxSupport.removeFromFSTab(args.getDestinationMountPath());
        // delete the directory entry if it's empty
        if (linuxSupport.isDirectoryEmpty(args.getDestinationMountPath())) {
            linuxSupport.deleteDirectory(args.getDestinationMountPath());
        }
        String tag = findTag(args.getHostId().toString(), args.getResId().toString(), args.getDestinationMountPath());
        removeTag(args.getResId(), tag.substring(0, tag.lastIndexOf(";")));
    }

    public List<MountInfo> getAllMounts(URI resId) {
        return MachineTagUtils.convertNFSTagsToMounts(getTags(dbClient.queryObject(FileShare.class, resId)));

    }

    protected LinuxSystemCLI initHost(URI hostId) {
        Host host = dbClient.queryObject(Host.class, hostId);
        if (host == null) {
            throw new IllegalArgumentException("Host " + hostId + " not found");
        }

        return LinuxUtils.convertHost(host);
    }

    public FileExport findExport(FileShare fs, String subDirectory, String securityType) {
        List<FileExport> exportList = queryDBFSExports(fs);
        dbClient.queryByType(FileShare.class, true);
        if (subDirectory.equalsIgnoreCase("!nodir")) {
            for (FileExport export : exportList) {
                if (export.getSubDirectory().isEmpty() && securityType.equals(export.getSecurityType())) {
                    return export;
                }
            }
        }
        for (FileExport export : exportList) {
            if (subDirectory.equals(export.getSubDirectory()) && securityType.equals(export.getSecurityType())) {
                return export;
            }
        }
        throw new IllegalArgumentException("no exports found");
    }

    private List<FileExport> queryDBFSExports(FileShare fs) {
        FSExportMap exportMap = fs.getFsExports();

        List<FileExport> fileExports = new ArrayList<FileExport>();
        if (exportMap != null) {
            fileExports.addAll(exportMap.values());
        }
        return fileExports;
    }

    private List<String> getTags(DataObject object) {
        List<String> mountTags = new ArrayList<String>();
        if (object.getTag() != null) {
            for (ScopedLabel label : object.getTag()) { // TODO filter tags for nfs
                mountTags.add(label.getLabel());
            }
        }
        return mountTags;
    }

    private void setTag(URI fsId, String tag) {
        ScopedLabelSet tags = new ScopedLabelSet();
        FileShare fs = dbClient.queryObject(FileShare.class, fsId);
        tags.add(new ScopedLabel(fs.getTenant().getURI().toString(), tag));
        fs.setTag(tags);
        dbClient.updateObject(fs);
    }

    private void removeTag(URI fsId, String tag) {
        ScopedLabelSet tags = new ScopedLabelSet();
        FileShare fs = dbClient.queryObject(FileShare.class, fsId);
        tags.add(new ScopedLabel(fs.getTenant().getURI().toString(), tag));
        fs.getTag().remove(tag);
        dbClient.updateObject(fs);
    }

    private String findTag(String hostId, String fsId, String destinationPath) {
        List<String> tags = getTags(dbClient.queryObject(FileShare.class, URIUtil.uri(fsId)));
        for (String tag : tags) {
            if (tag.contains(hostId) && tag.contains(fsId) && tag.contains(destinationPath)) {
                return tag;
            }
        }
        return null;
    }
}
