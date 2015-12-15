/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

public class DiscoveredDataObject extends DataObject {

    // Unique Bourne identifier.
    private String _nativeGuid;

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
        ecs;

        static public boolean isFileStorageSystem(Type type) {
            return (type == isilon || type == vnxfile || type == netapp || type == netappc || type == vnxe || type == datadomain);
        }

        static public boolean isProviderStorageSystem(Type type) {
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

        static public boolean isVPlexStorageSystem(Type type) {
            return (type == vplex);
        }

        static public boolean isIBMXIVStorageSystem(Type type) {
            return (type == ibmxiv);
        }

        static public boolean isBlockStorageSystem(Type type) {
            return (type == vnxblock || type == vmax || type == vnxe || type == hds || type == ibmxiv || type == xtremio ||
                    type == scaleio);
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
}
