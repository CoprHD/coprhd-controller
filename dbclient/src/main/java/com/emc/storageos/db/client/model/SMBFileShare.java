/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

@XmlRootElement(name = "smb_share")
public class SMBFileShare extends AbstractSerializableNestedObject {

    private static final String NAME = "name";
    private static final String MOUNTPOINT = "mountPoint";
    private static final String DESCRIPTION = "description";
    private static final String PERMISSION_TYPE = "permissionType";
    private static final String PERMISSION = "permission";
    private static final String MAX_USERS = "maxUsers";
    private static final String NATIVE_ID = "nativeId";
    private static final String PORT_GROUP = "portGroup";
    private static final String PATH = "path";
    private static final String IS_SUBDIR = "isSubDir";
    private static final String STORAGE_PORT_NAME = "storagePortName";
    private static final String STORAGE_PORT_NETWORK_ID = "storagePortNetworkId";
    private static final String NETBIOS_NAME = "netbiosName";
    private static final String DIRECTORY_ACLS_OPTIONS = "directoryAclsOptions";

    /**
     * JAXB requirement
     */
    public SMBFileShare() {
    }

    public SMBFileShare(String name, String description, String permissionType, String permission, int maxUsers,
            String mountPoint) {
        setName(name);
        setDescription(description);
        setPermissionType(permissionType);
        setPermission(permission);
        setMaxUsers(maxUsers);
        setMountPoint(mountPoint);
    }

    public SMBFileShare(String name, String description, String permissionType, String permission, int maxUsers) {
        setName(name);
        setDescription(description);
        setPermissionType(permissionType);
        setPermission(permission);
        setMaxUsers(maxUsers);
    }

    @XmlElement
    public String getName() {
        return getStringField(NAME);
    }

    public void setName(String name) {
        if (name == null) {
            name = "";
        }
        setField(NAME, name);
    }

    @XmlElement
    public String getDescription() {
        return getStringField(DESCRIPTION);
    }

    public void setDescription(String description) {
        if (description == null) {
            description = "";
        }
        setField(DESCRIPTION, description);
    }

    @XmlElement(name = "permission_type")
    public String getPermissionType() {
        return getStringField(PERMISSION_TYPE);
    }

    public void setPermissionType(String permissionType) {
        if (permissionType == null) {
            permissionType = "";
        }
        setField(PERMISSION_TYPE, permissionType);
    }

    @XmlElement(name = "permission")
    public String getPermission() {
        return getStringField(PERMISSION);
    }

    public void setPermission(String permission) {
        if (permission == null) {
            permission = "";
        }
        setField(PERMISSION, permission);
    }

    // @XmlElement(name = "max_users")
    @XmlTransient
    public int getMaxUsers() {
        return getIntField(MAX_USERS);
    }

    public void setMaxUsers(int maxUsers) {
        setField(MAX_USERS, maxUsers);
    }

    @XmlElement
    public String getMountPoint() {
        return getStringField(MOUNTPOINT);
    }

    // Mount Point For VNX,VNXe,NetApp7,NetAppCluster Mode
    public void setMountPoint(String netbiosName, String storagePortNetworkId, String storagePortNetworkName, String shareName) {
        String portName = null;

        if (netbiosName != null && !netbiosName.isEmpty()) {
            portName = netbiosName.trim();
        } else {
            if (storagePortNetworkId != null && !storagePortNetworkId.isEmpty()) {
                portName = storagePortNetworkId;
            } else {
                if (storagePortNetworkName != null && !storagePortNetworkName.isEmpty()) {
                    portName = storagePortNetworkName;
                }
            }
        }
        String mountPoint = (portName != null) ? "\\\\" + portName + "\\" + shareName : null;
        setField(MOUNTPOINT, mountPoint);
    }

    // Mount Point for Isilon and Data Domain
    public void setMountPoint(String storagePortNetworkId, String storagePortNetworkName, String shareName) {
        String portName = null;
        if (storagePortNetworkId != null && !storagePortNetworkId.isEmpty()) {
            portName = storagePortNetworkId;
        } else {
            if (storagePortNetworkName != null && !storagePortNetworkName.isEmpty()) {
                portName = storagePortNetworkName;
            }
        }

        String mountPoint = (portName != null) ? "\\\\" + portName + "\\" + shareName : null;
        setField(MOUNTPOINT, mountPoint);
    }

    // Mount Point for UnManaged FS SMB shares
    public void setMountPoint(String mountPoint) {
        if (mountPoint == null) {
            mountPoint = "";
        }
        setField(MOUNTPOINT, mountPoint);
    }

    @XmlElement(name = "native_id")
    public String getNativeId() {
        return getStringField(NATIVE_ID);
    }

    public void setNativeId(String nativeId) {
        if (nativeId == null) {
            nativeId = "";
        }
        setField(NATIVE_ID, nativeId);
    }

    @XmlElement
    public String getPortGroup() {
        return getStringField(PORT_GROUP);
    }

    public void setPortGroup(String portGroup) {
        if (portGroup == null) {
            portGroup = "";
        }
        setField(PORT_GROUP, portGroup);
    }

    @XmlElement
    public String getPath() {
        return getStringField(PATH);
    }

    public void setPath(String path) {
        if (path == null) {
            path = "";
        }
        setField(PATH, path);
    }

    @XmlElement
    public String isSubdir() {
        return getStringField(IS_SUBDIR);
    }

    public void setSubDir(boolean isSubDir) {
        setField(IS_SUBDIR, isSubDir);
    }

    @XmlElement
    public String getStoragePortName() {
        return getStringField(STORAGE_PORT_NAME);
    }

    public void setStoragePortName(String storagePortName) {
        if (storagePortName == null) {
            storagePortName = "";
        }
        setField(STORAGE_PORT_NAME, storagePortName);
    }

    @XmlElement
    public String getStoragePortNetworkId() {
        return getStringField(STORAGE_PORT_NETWORK_ID);
    }

    public void setStoragePortNetworkId(String storagePortNetworkId) {
        if (storagePortNetworkId == null) {
            storagePortNetworkId = "";
        }
        setField(STORAGE_PORT_NETWORK_ID, storagePortNetworkId);
    }

    @XmlElement
    public String getNetBIOSName() {
        return getStringField(NETBIOS_NAME);
    }

    public void setNetBIOSName(String netbiosName) {
        if (netbiosName == null) {
            netbiosName = "";
        }
        setField(NETBIOS_NAME, netbiosName);
    }

    @XmlElement
    public String getDirectoryAclsOptions() {
        return getStringField(DIRECTORY_ACLS_OPTIONS);
    }

    public void setDirectoryAclsOptions(String aclsOptions) {
        if (aclsOptions == null) {
            aclsOptions = "";
        }
        setField(DIRECTORY_ACLS_OPTIONS, aclsOptions);
    }

}
