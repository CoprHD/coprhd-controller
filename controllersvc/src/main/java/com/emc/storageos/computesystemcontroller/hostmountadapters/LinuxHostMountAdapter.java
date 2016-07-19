package com.emc.storageos.computesystemcontroller.hostmountadapters;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

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
import com.emc.storageos.svcs.errorhandling.resources.InternalException;

public class LinuxHostMountAdapter extends AbstractMountAdapter {

    MountUtils mountUtils;

    public LinuxHostMountAdapter(DbClient _dbClient, CoordinatorClient _coordinator, Host host) {
        setCoordinator(_coordinator);
        setDbClient(_dbClient);
        mountUtils = new MountUtils(host);
    }

    @Override
    public void doMount(HostDeviceInputOutput args) throws InternalException {

        FileShare fs = dbClient.queryObject(FileShare.class, args.getResId());
        FileExport export = findExport(fs, args.getSubDirectory(), args.getSecurity());
        String fsType = args.getFsType() == null ? "auto" : args.getFsType();
        try {
            // verify mount point
            mountUtils.verifyMountPoint(args.getMountPath());
            // Create directory
            mountUtils.createDirectory(args.getMountPath());
            // Add to the /etc/fstab to allow the os to mount on restart
            mountUtils.addToFSTab(args.getMountPath(), export.getMountPoint(), fsType, "nolock,sec=" + args.getSecurity());
            // Mount the device
            mountUtils.mountPath(args.getMountPath());
        } catch (InternalException e) {
            throw e;

        }
        // Set the fs tag containing mount info
        setTag(fs.getId(), mountUtils.generateMountTag(args.getHostId(), args.getMountPath(),
                args.getSubDirectory(), args.getSecurity()));
    }

    @Override
    public void doUnmount(HostDeviceInputOutput args) {
        // unmount the Export
        mountUtils.unmountPath(args.getMountPath());
        // remove from fstab
        mountUtils.removeFromFSTab(args.getMountPath());
        // delete the directory entry if it's empty
        if (mountUtils.isDirectoryEmpty(args.getMountPath())) {
            mountUtils.deleteDirectory(args.getMountPath());
        }
        String tag = findTag(args.getHostId().toString(), args.getResId().toString(), args.getMountPath());
        removeTag(args.getResId(), tag);
    }

    public FileExport findExport(FileShare fs, String subDirectory, String securityType) {
        List<FileExport> exportList = queryDBFSExports(fs);
        dbClient.queryByType(FileShare.class, true);
        if (subDirectory.equalsIgnoreCase("!nodir") || subDirectory.isEmpty() || subDirectory == null) {
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
        FileShare fs = dbClient.queryObject(FileShare.class, fsId);
        fs.getTag().remove(new ScopedLabel(fs.getTenant().getURI().toString(), tag));
        dbClient.updateObject(fs);
    }

    private String findTag(String hostId, String fsId, String mountPath) {
        List<String> tags = getTags(dbClient.queryObject(FileShare.class, URIUtil.uri(fsId)));
        for (String tag : tags) {
            if (tag.contains(hostId) && tag.contains(mountPath)) {
                return tag;
            }
        }
        return null;
    }
}
