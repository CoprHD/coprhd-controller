/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import com.emc.storageos.services.util.StorageDriverManager;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class DiscoveredDataObject extends DataObject {

    // Unique Bourne identifier.
    private String _nativeGuid;

    // Indicates if the object is Southbound driver managed.
    private Boolean _isDriverManaged = false;

    // Junits don't have the application context populated, so for them storageDriverManager is null.
    private static StorageDriverManager storageDriverManager = 
            ((StorageDriverManager.getApplicationContext() != null) ?
            (StorageDriverManager) StorageDriverManager.getApplicationContext()
                    .getBean(StorageDriverManager.STORAGE_DRIVER_MANAGER) :
            null);
    // known device types
    public static class Type implements Serializable {
        private static final long serialVersionUID = 1L;
        static private Map<String, Type> types = new HashMap<>();

        static public Type isilon = new Type("isilon", types.values().size());
        static public Type ddmc = new Type("ddmc", types.values().size());
        static public Type datadomain = new Type("datadomain", types.values().size());
        static public Type vnxblock = new Type("vnxblock", types.values().size());
        static public Type vnxfile = new Type("vnxfile", types.values().size());
        static public Type vmax = new Type("vmax", types.values().size());
        static public Type netapp = new Type("netapp", types.values().size());
        static public Type netappc = new Type("netappc", types.values().size());
        static public Type vplex = new Type("vplex", types.values().size());
        static public Type mds = new Type("mds", types.values().size());
        static public Type brocade = new Type("brocade", types.values().size());
        static public Type rp = new Type("rp", types.values().size());
        static public Type srdf = new Type("srdf", types.values().size());
        static public Type host = new Type("host", types.values().size());
        static public Type vcenter = new Type("vcenter", types.values().size());
        static public Type hds = new Type("hds", types.values().size());
        static public Type ucs = new Type("ucs", types.values().size());
        static public Type ibmxiv = new Type("ibmxiv", types.values().size());
        static public Type openstack = new Type("openstack", types.values().size());
        static public Type vnxe = new Type("vnxe", types.values().size());
        static public Type scaleio = new Type("scaleio", types.values().size());
        static public Type xtremio = new Type("xtremio", types.values().size());
        static public Type ecs = new Type("ecs", types.values().size());

        private String name;
        private int ordinal;

        public int getOrdinal() {
            return ordinal;
        }

        private Type(String typeName, int ordinal) {
            name = typeName;
            this.ordinal = ordinal;
            types.put(typeName, this);
        }

        @Override
        public boolean equals(Object type) {
            if (type != null && type instanceof Type) {
                return ((Type) type).name.equals(this.name);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public  String toString(){
            return name;
        }

        public String name() {
            return name;
        }

        static public Type valueOf(String typeName){
            // check map  with types
            if (types.containsKey(typeName)) {
                return types.get(typeName);
            } else if (storageDriverManager != null && storageDriverManager.isDriverManaged(typeName)){
                // check if this is new driver managed type
                types.put(typeName, new Type(typeName, types.values().size()));
                return types.get(typeName);
            } else {
                throw new IllegalArgumentException("Class "+ Type.class.getSimpleName() + "does not have member: " +typeName);
            }
        }

        public static Type []  values() {
            return  types.values().toArray(new Type[0]);
        }


        static public boolean isDriverManagedStorageSystem(String storageType) {
            return storageDriverManager.isDriverManaged(storageType);
        }

        static public boolean isFileStorageSystem(String storageType) {
            if (storageDriverManager != null && storageDriverManager.isDriverManaged(storageType)) {
                return storageDriverManager.isFileStorageSystem(storageType);
            } else {
                Type type = Type.valueOf(storageType);
                return (type.equals(isilon) || type.equals(vnxfile) || type.equals(netapp) || type.equals(netappc) || type.equals(vnxe) || type.equals(datadomain));
            }
        }

        static public boolean isProviderStorageSystem(String storageType) {
            if (storageDriverManager != null && storageDriverManager.isDriverManaged(storageType)) {
                return storageDriverManager.isProviderStorageSystem(storageType);
            } else {
                Type type = Type.valueOf(storageType);
                return  type.equals(vnxblock) ||
                        type.equals(datadomain) ||
                        type.equals(vmax) ||
                        type.equals(hds) ||
                        type.equals(openstack) ||
                        type.equals(vplex) ||
                        type.equals(ibmxiv) ||
                        type.equals(xtremio) ||
                        type.equals(scaleio);
            }
        }

        static public boolean isVPlexStorageSystem(Type type) {
            return type.equals(vplex);
        }

        static public boolean isIBMXIVStorageSystem(Type type) {
            return type.equals(ibmxiv);
        }

        static public boolean isBlockStorageSystem(String storageType) {
            if (storageDriverManager != null && storageDriverManager.isDriverManaged(storageType)) {
                return storageDriverManager.isBlockStorageSystem(storageType);
            } else {
                Type type = Type.valueOf(storageType);
                return (type.equals(vnxblock) || type.equals(vmax) || type.equals(vnxe) || type.equals(hds) || type.equals(ibmxiv) || type.equals(xtremio) || type.equals(scaleio));
            }
        }


        static public boolean isHDSStorageSystem(Type type) {
            return (type.equals(hds));
        }

        static public boolean isObjectStorageSystem(Type type) {
            return (type.equals(ecs));
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
