package com.emc.storageos.computesystemcontroller.hostmountadapters;

import java.net.URI;

/**
 * 
 * @author yelkaa
 *
 */
public class HostDeviceInputOutput {
    private URI resId; // Id of the associated resource like fs or vol
    private URI hostId;
    private String type; // cifs, nfs
    private String destinationMountPath;
    private String MountPoint;
    private String security;
    private String subDirectory;

    public URI getResId() {
        return resId;
    }

    public void setResId(URI resId) {
        this.resId = resId;
    }

    public URI getHostId() {
        return hostId;
    }

    public void setHostId(URI hostId) {
        this.hostId = hostId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDestinationMountPath() {
        return destinationMountPath;
    }

    public void setDestinationMountPath(String destinationMountPath) {
        this.destinationMountPath = destinationMountPath;
    }

    public String getMountPoint() {
        return MountPoint;
    }

    public void setMountPoint(String mountPoint) {
        MountPoint = mountPoint;
    }

    public String getSecurity() {
        return security;
    }

    public void setSecurity(String security) {
        this.security = security;
    }

    public String getSubDirectory() {
        return subDirectory;
    }

    public void setSubDirectory(String subDirectory) {
        this.subDirectory = subDirectory;
    }
}
