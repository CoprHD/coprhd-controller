/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import com.emc.storageos.services.util.StorageDriverManager;

public class DiscoveredDataObject extends DataObject {

    // Unique Bourne identifier.
    private String _nativeGuid;

    // Indicates if the object is Southbound driver managed.
    private Boolean _isDriverManaged = false;

    private static StorageDriverManager storageDriverManager = (StorageDriverManager)StorageDriverManager.
                                              getApplicationContext().getBean("storageDriverManager");

    // known device types
    public static enum Type {
        isilon,
        ddmc,
        datadomain,
        vnxblock,
        vnxfile,
        vmax,
        netapp,
        netappc,
        vplex,
        mds,
        brocade,
        rp,
        srdf,
        host,
        vcenter,
        hds,
        ucs,
        ibmxiv,
        openstack,
        vnxe,
        scaleio,
        xtremio,
        ecs,
        driversystem;

        static public boolean isDriverManagedStorageSystem(String storageType) {
            return storageDriverManager.isDriverManaged(storageType);
        }

        static public boolean isFileStorageSystem(String storageType) {
            if (storageDriverManager.isDriverManaged(storageType)) {
                return storageDriverManager.isFileStorageSystem(storageType);
            } else {
                Type type = Type.valueOf(storageType);
                return (type == isilon || type == vnxfile || type == netapp || type == netappc || type == vnxe || type == datadomain);
            }
        }

        static public boolean isProviderStorageSystem(String storageType) {
            if (storageDriverManager.isDriverManaged(storageType)) {
                return storageDriverManager.isProviderStorageSystem(storageType);
            } else {
                Type type = Type.valueOf(storageType);
                return (type == vnxblock) ||
                        (type == datadomain) ||
                        (type == vmax) ||
                        (type == hds) ||
                        (type == openstack) ||
                        (type == vplex) ||
                        (type == ibmxiv) ||
                        (type == xtremio) ||
                        (type == scaleio);
            }
        }

        static public boolean isVPlexStorageSystem(Type type) {
            return (type == vplex);
        }

        static public boolean isIBMXIVStorageSystem(Type type) {
            return (type == ibmxiv);
        }

        static public boolean isBlockStorageSystem(String storageType) {
            if (storageDriverManager.isDriverManaged(storageType)) {
                return storageDriverManager.isBlockStorageSystem(storageType);
            } else {
                Type type = Type.valueOf(storageType);
                return (type == vnxblock || type == vmax || type == vnxe || type == hds || type == ibmxiv || type == xtremio || type == driversystem);
            }
        }

        static public boolean isHDSStorageSystem(Type type) {
            return (type == hds);
        }

        static public boolean isObjectStorageSystem(Type type) {
            return (type == ecs);
        }

    }

    public static enum DataCollectionJobStatus {
        CREATED,
        SCHEDULED,
        IN_PROGRESS,
        COMPLETE,
        ERROR
    }

    public static enum RegistrationStatus {
        REGISTERED,
        UNREGISTERED,
    }

    public static enum CompatibilityStatus {
        COMPATIBLE,
        INCOMPATIBLE,
        UNKNOWN
    }

    public static enum DiscoveryStatus {
        VISIBLE,
        NOTVISIBLE,
    }

    @AlternateId("AltIdIndex")
    @Name("nativeGuid")
    public String getNativeGuid() {
        return _nativeGuid == null ? "" : _nativeGuid;
    }

    public void setNativeGuid(String nativeGuid) {
        _nativeGuid = nativeGuid;
        setChanged("nativeGuid");
    }

    @Name("isDriverManaged")
    public Boolean getIsDriverManaged() {
        return _isDriverManaged;
    }

    public void setIsDriverManaged(Boolean isDriverManaged) {
        _isDriverManaged = isDriverManaged;
        setChanged("isDriverManaged");
    }
}
