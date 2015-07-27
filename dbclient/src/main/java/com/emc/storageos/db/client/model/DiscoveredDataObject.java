/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) 2012. EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.db.client.model;

public class DiscoveredDataObject extends DataObject{
   
    // Unique Bourne identifier.
    private String _nativeGuid;
    
    // known device types
    public  static enum Type {
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
        rpvplex,
        ibmxiv,
        openstack,
        vnxe,
        scaleio,
        xtremio;

        static public boolean isFileStorageSystem(Type type) {
            return (type == isilon || type == vnxfile || type == netapp || type == netappc || type==vnxe || type == datadomain);
        }

        static public boolean isProviderStorageSystem(Type type) {
            return  (type == vnxblock)  ||
                    (type == datadomain) ||
                    (type == vmax)      ||
                    (type == hds)       ||
                    (type == openstack) ||
                    (type == vplex)     ||
                    (type == ibmxiv)     ||                   
                    (type == scaleio);
        }

        static public boolean isVPlexStorageSystem(Type type) {
            return (type == vplex);
        }
       
        static public boolean isIBMXIVStorageSystem(Type type) {
            return (type == ibmxiv);
        }
        
        static public boolean isBlockStorageSystem(Type type) {
            return (type == vnxblock || type == vmax || type == vnxe || type==hds || type == ibmxiv || type == xtremio);
        }
        
        static public boolean isHDSStorageSystem(Type type) {
            return (type == hds);
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
