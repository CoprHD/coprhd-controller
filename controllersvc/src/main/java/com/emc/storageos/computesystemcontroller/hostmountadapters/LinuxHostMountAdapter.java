/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller.hostmountadapters;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.FSExportMap;
import com.emc.storageos.db.client.model.FileExport;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.ScopedLabel;
import com.emc.storageos.db.client.model.ScopedLabelSet;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.Workflow.Method;
import com.emc.storageos.workflow.WorkflowException;
import com.emc.storageos.workflow.WorkflowStepCompleter;

/**
 * 
 * @author yelkaa
 *
 */
public class LinuxHostMountAdapter extends AbstractMountAdapter {

    private MountUtils mountUtils;

    private static final String ROLLBACK_METHOD_NULL = "rollbackMethodNull";

    public MountUtils getMountUtils() {
        return mountUtils;
    }

    public void setMountUtils(MountUtils mountUtils) {
        this.mountUtils = mountUtils;
    }

    public LinuxHostMountAdapter() {

    }

    /**
     * Empty rollback method
     * 
     * @return workflow method that is empty
     */
    private Workflow.Method rollbackMethodNullMethod() {
        return new Workflow.Method(ROLLBACK_METHOD_NULL);
    }

    /**
     * A rollback workflow method that does nothing, but allows rollback
     * to continue to prior steps back up the workflow chain. It says the rollback step succeeded,
     * which will then allow other rollback operations to execute for other
     * workflow steps executed by the other controller.
     *
     * @param stepId
     *            The id of the step being rolled back.
     *
     * @throws WorkflowException
     */
    public void rollbackMethodNull(String stepId) throws WorkflowException {
        WorkflowStepCompleter.stepSucceded(stepId);
    }

    @Override
    public void doMount(HostDeviceInputOutput args) throws InternalException {
        mountUtils = new MountUtils(dbClient.queryObject(Host.class, args.getHostId()));
        FileShare fs = dbClient.queryObject(FileShare.class, args.getResId());
        FileExport export = findExport(fs, args.getSubDirectory(), args.getSecurity());
        String fsType = args.getFsType() == null ? "auto" : args.getFsType();
        String subDirectory = args.getSubDirectory() == null ? "!nodir" : args.getSubDirectory();
        // verify mount point
        mountUtils.verifyMountPoint(args.getMountPath());
        // Create directory
        mountUtils.createDirectory(args.getMountPath());
        // Add to the /etc/fstab to allow the os to mount on restart
        mountUtils.addToFSTab(args.getMountPath(), export.getMountPoint(), fsType, "nolock,sec=" + args.getSecurity());
        // Mount the device
        mountUtils.mountPath(args.getMountPath());
        // Set the fs tag containing mount info
        setTag(fs.getId(), mountUtils.generateMountTag(args.getHostId(), args.getMountPath(),
                subDirectory, args.getSecurity()));
    }

    @Override
    public void doUnmount(HostDeviceInputOutput args) throws InternalException {
        mountUtils = new MountUtils(dbClient.queryObject(Host.class, args.getHostId()));
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
        if (subDirectory == null || subDirectory.equalsIgnoreCase("!nodir") || subDirectory.isEmpty()) {
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
            for (ScopedLabel label : object.getTag()) {
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

    public void createDirectory(URI hostId, String mountPath, String stepId) {
        try {
            mountUtils = new MountUtils(dbClient.queryObject(Host.class, hostId));
            WorkflowStepCompleter.stepExecuting(stepId);
            // Create directory
            mountUtils.createDirectory(mountPath);
            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (ControllerException e) {
            WorkflowStepCompleter.stepFailed(stepId, e);
            throw e;
        }
    }

    public void addToFSTab(URI hostId, String mountPath, URI resId, String subDirectory, String security, String fsType, String stepId) {
        try {
            mountUtils = new MountUtils(dbClient.queryObject(Host.class, hostId));
            WorkflowStepCompleter.stepExecuting(stepId);
            FileShare fs = dbClient.queryObject(FileShare.class, resId);
            FileExport export = findExport(fs, subDirectory, security);
            // Add to etc/fstab
            mountUtils.addToFSTab(mountPath, export.getMountPoint(), fsType, "nolock,sec=" + security);
            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (ControllerException e) {
            WorkflowStepCompleter.stepFailed(stepId, e);
            throw e;
        }
    }

    public void mountDevice(URI hostId, String mountPath, String stepId) {
        try {
            mountUtils = new MountUtils(dbClient.queryObject(Host.class, hostId));
            WorkflowStepCompleter.stepExecuting(stepId);
            // mount device
            mountUtils.mountPath(mountPath);
            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (ControllerException e) {
            WorkflowStepCompleter.stepFailed(stepId, e);
            throw e;
        }
    }

    public void verifyMountPoint(URI hostId, String mountPath, String stepId) {
        try {
            mountUtils = new MountUtils(dbClient.queryObject(Host.class, hostId));
            WorkflowStepCompleter.stepExecuting(stepId);
            // verify if mount point already exists in host
            mountUtils.verifyMountPoint(mountPath);
            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (ControllerException e) {
            WorkflowStepCompleter.stepFailed(stepId, e);
            throw e;
        }
    }

    public void deleteDirectory(URI hostId, String mountPath, String stepId) {
        try {
            mountUtils = new MountUtils(dbClient.queryObject(Host.class, hostId));
            WorkflowStepCompleter.stepExecuting(stepId);
            // Delete directory
            if (mountUtils.isDirectoryEmpty(mountPath)) {
                mountUtils.deleteDirectory(mountPath);
            }
            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (ControllerException e) {
            WorkflowStepCompleter.stepFailed(stepId, e);
            throw e;
        }
    }

    public void removeFromFSTab(URI hostId, String mountPath, String stepId) {
        try {
            mountUtils = new MountUtils(dbClient.queryObject(Host.class, hostId));
            WorkflowStepCompleter.stepExecuting(stepId);
            // remove mount entry from /etc/fstab
            mountUtils.removeFromFSTab(mountPath);
            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (ControllerException e) {
            WorkflowStepCompleter.stepFailed(stepId, e);
            throw e;
        }
    }

    public void unmountDevice(URI hostId, String mountPath, String stepId) {
        try {
            mountUtils = new MountUtils(dbClient.queryObject(Host.class, hostId));
            WorkflowStepCompleter.stepExecuting(stepId);
            // unmount device
            mountUtils.unmountPath(mountPath);
            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (ControllerException e) {
            WorkflowStepCompleter.stepFailed(stepId, e);
            throw e;
        }
    }

    public void setMountTag(URI hostId, String mountPath, URI resId, String subDirectory, String security, String fsType, String stepId) {
        try {
            mountUtils = new MountUtils(dbClient.queryObject(Host.class, hostId));
            WorkflowStepCompleter.stepExecuting(stepId);
            // set mount tag on the fs
            setTag(resId, mountUtils.generateMountTag(hostId, mountPath, subDirectory, security));
            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (ControllerException e) {
            WorkflowStepCompleter.stepFailed(stepId, e);
            throw e;
        }
    }

    public void removeMountTag(URI hostId, String mountPath, URI resId, String stepId) {
        try {
            WorkflowStepCompleter.stepExecuting(stepId);
            // remove mount tag
            String tag = findTag(hostId.toString(), resId.toString(), mountPath);
            removeTag(resId, tag);
            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (ControllerException e) {
            WorkflowStepCompleter.stepFailed(stepId, e);
            throw e;
        }
    }

    @Override
    public Method createDirectoryMethod(HostDeviceInputOutput args) {
        return new Workflow.Method("createDirectory", args.getHostId(), args.getMountPath());
    }

    @Override
    public Method addtoFSTabMethod(HostDeviceInputOutput args) {
        return new Workflow.Method("addToFSTab", args.getHostId(), args.getMountPath(), args.getResId(), args.getSubDirectory(),
                args.getSecurity(), args.getFsType());
    }

    @Override
    public Method mountDeviceMethod(HostDeviceInputOutput args) {
        return new Workflow.Method("mountDevice", args.getHostId(), args.getMountPath());
    }

    @Override
    public Method verifyMountPointMethod(HostDeviceInputOutput args) {
        return new Workflow.Method("verifyMountPoint", args.getHostId(), args.getMountPath());
    }

    @Override
    public Method unmountDeviceMethod(HostDeviceInputOutput args) {
        return new Workflow.Method("unmountDevice", args.getHostId(), args.getMountPath());
    }

    @Override
    public Method removeFromFSTabMethod(HostDeviceInputOutput args) {
        return new Workflow.Method("removeFromFSTab", args.getHostId(), args.getMountPath());
    }

    @Override
    public Method deleteDirectoryMethod(HostDeviceInputOutput args) {
        return new Workflow.Method("deleteDirectory", args.getHostId(), args.getMountPath());
    }

    @Override
    public Method setMountTagMethod(HostDeviceInputOutput args) {
        return new Workflow.Method("setMountTag", args.getHostId(), args.getMountPath(), args.getResId(), args.getSubDirectory(),
                args.getSecurity(), args.getFsType());
    }

    @Override
    public Method removeMountTagMethod(HostDeviceInputOutput args) {
        return new Workflow.Method("removeMountTag", args.getHostId(), args.getMountPath(), args.getResId());
    }

    @Override
    public String addStepsForMountingDevice(Workflow workflow, String waitFor, HostDeviceInputOutput args, String taskId)
            throws InternalException {

        // create a step

        waitFor = workflow.createStep(null,
                String.format("Verifying mount point: %s", args.getMountPath()),
                null, args.getHostId(),
                getHostType(args.getHostId()),
                this.getClass(),
                verifyMountPointMethod(args),
                rollbackMethodNullMethod(), null);

        waitFor = workflow.createStep(null,
                String.format("Creating Directory: %s", args.getMountPath()),
                waitFor, args.getHostId(),
                getHostType(args.getHostId()),
                this.getClass(),
                createDirectoryMethod(args),
                deleteDirectoryMethod(args), null);

        waitFor = workflow.createStep(null,
                String.format("Adding to etc/fstab:%n%s", args.getMountPath()),
                waitFor, args.getHostId(),
                getHostType(args.getHostId()),
                this.getClass(),
                addtoFSTabMethod(args),
                removeFromFSTabMethod(args), null);

        waitFor = workflow.createStep(null,
                String.format("Mounting device:%n%s", args.getResId()),
                waitFor, args.getHostId(),
                getHostType(args.getHostId()),
                this.getClass(),
                mountDeviceMethod(args),
                removeFromFSTabMethod(args), null);

        workflow.createStep(null,
                String.format("Setting tag on :%n%s", args.getResId()),
                waitFor, args.getHostId(),
                getHostType(args.getHostId()),
                this.getClass(),
                setMountTagMethod(args),
                removeMountTagMethod(args), null);

        return waitFor;
    }

    @Override
    public String addStepsForUnmountingDevice(Workflow workflow, String waitFor, HostDeviceInputOutput args, String taskId)
            throws InternalException {

        // create a step

        waitFor = workflow.createStep(null,
                String.format("Creating Directory: %s", args.getMountPath()),
                null, args.getHostId(),
                getHostType(args.getHostId()),
                this.getClass(),
                unmountDeviceMethod(args),
                mountDeviceMethod(args), null);

        waitFor = workflow.createStep(null,
                String.format("Adding to etc/fstab:%n%s", args.getMountPath()),
                waitFor, args.getHostId(),
                getHostType(args.getHostId()),
                this.getClass(),
                removeFromFSTabMethod(args),
                addtoFSTabMethod(args), null);

        waitFor = workflow.createStep(null,
                String.format("Mounting device:%n%s", args.getResId()),
                waitFor, args.getHostId(),
                getHostType(args.getHostId()),
                this.getClass(),
                deleteDirectoryMethod(args),
                createDirectoryMethod(args), null);

        workflow.createStep(null,
                String.format("Setting tag on :%n%s", args.getResId()),
                waitFor, args.getHostId(),
                getHostType(args.getHostId()),
                this.getClass(),
                removeMountTagMethod(args),
                setMountTagMethod(args), null);

        return waitFor;
    }

    /**
     * Get the deviceType for a StorageSystem.
     * 
     * @param deviceURI
     *            -- StorageSystem URI
     * @return deviceType String
     */
    public String getHostType(URI hostURI) throws ControllerException {
        Host host = dbClient.queryObject(Host.class, hostURI);
        if (host == null) {
            throw DeviceControllerException.exceptions.getDeviceTypeFailed(hostURI.toString());
        }
        return host.getType();
    }
}
