package com.emc.storageos.driver.ibmsvcdriver.utils;

public final class IBMSVCConstants {
    
    public static final String REGEX_CAPACITY = "\\s+\\d+(?:\\.\\d+)?\\s+\\w{2}\\s+\\((.*?)\\)";
    public static final String REGEX_CAPACITY_NO_SPACE_IN_FRONT = "\\d+(?:\\.\\d+)?\\s+\\w{2}\\s+\\((.*?)\\)";
    public static final String REGEX_BYTES_CAPACITY = "\\s+(\\d+)\\s+Bytes\\s+";

    public static final String DRIVER_NAME="IBM-SVC";
    public static final String STORAGE_DEVICE_ID = "IBMSVC-V1";
    public static final String IP_ADDRESS="IpAddress";
    public static final String PORT_NUMBER="PortNumber";
    public static final String USER_NAME="UserName";
    public static final String PASSWORD="Password";
    
    public static final String TASK_TYPE_DISCOVER_STORAGE_SYSTEM="discover-storage-system";
    public static final String TASK_TYPE_DISCOVER_STORAGE_POOLS="discover-storage-pools";
    public static final String TASK_TYPE_DISCOVER_STORAGE_PORTS="discover-storage-ports";
    public static final String TASK_TYPE_DISCOVER_STORAGE_HOSTS="discover-storage-hosts";
    public static final String TASK_TYPE_GET_STORAGE_VOLUMES="get-storage-volumes";

    public static final String TASK_TYPE_CREATE_STORAGE_VOLUMES="create-storage-volumes";
    public static final String TASK_TYPE_EXPAND_STORAGE_VOLUMES="expand-storage-volumes";
    public static final String TASK_TYPE_DELETE_STORAGE_VOLUMES="delete-storage-volumes";
    public static final String TASK_TYPE_EXPORT_STORAGE_VOLUMES="export-storage-volumes";
    public static final String TASK_TYPE_UNEXPORT_STORAGE_VOLUMES="unexport-storage-volumes";

    public static final String TASK_TYPE_CREATE_SNAPSHOT_VOLUMES="create-snapshot-volumes";
    public static final String TASK_TYPE_RESTORE_SNAPSHOT_VOLUMES="restore-snapshot-volumes";
    public static final String TASK_TYPE_DELETE_SNAPSHOT_VOLUMES="delete-snapshot-volumes";

    public static final String TASK_TYPE_CREATE_FC_CONSISTGROUP="create-fc-consistgroup";
    public static final String TASK_TYPE_DELETE_FC_CONSISTGROUP="delete-fc-consistgroup";
    public static final String TASK_TYPE_CREATE_FC_CONSISTGROUP_SNAPSHOT="create-fc-consistgroup-snapshot";
    public static final String TASK_TYPE_DELETE_FC_CONSISTGROUP_SNAPSHOT="delete-fc-consistgroup-snapshot";
    public static final String TASK_TYPE_CREATE_FC_CONSISTGROUP_CLONE="create-fc-consistgroup-clone";
    public static final String TASK_TYPE_DELETE_FC_CONSISTGROUP_CLONE="delete-fc-consistgroup-clone";

    public static final String TASK_TYPE_CREATE_CLONE_VOLUMES="create-clone-volumes";
    public static final String TASK_TYPE_RESTORE_CLONE_VOLUMES="restore-clone-volumes";
    public static final String TASK_TYPE_DETACH_CLONE_VOLUMES="detach-clone-volumes";
    public static final String TASK_TYPE_DELETE_CLONE_VOLUMES="delete-clone-volumes";

    public static final String TASK_TYPE_CREATE_MIRROR_VOLUMES="create-mirror-volumes";
    public static final String TASK_TYPE_RESTORE_MIRROR_VOLUMES="restore-mirror-volumes";
    public static final String TASK_TYPE_DETACH_MIRROR_VOLUMES="detach-mirror-volumes";
    public static final String TASK_TYPE_DELETE_MIRROR_VOLUMES="delete-mirror-volumes";

    public static final int MAX_SOURCE_MAPPINGS = 256;

    public static final int MAX_MIRROR_COUNT = 2;

    public static final int FC_MAPPING_QUERY_TIMEOUT = 120;

    public enum TaskType{
        DISCOVER_STORAGE_SYSTEM,
        DISCOVER_STORAGE_POOLS,
        DISCOVER_STORAGE_PORTS,

        VOLUME_CREATE,
        VOLUME_EXPAND,
        VOLUME_DELETE,

        SNAPSHOT_CREATE,
        SNAPSHOT_DELETE,

        CLONE_CREATE,
        CLONE_DETACH,
        CLONE_DELETE,

        GET_ITL,
        EXPORT,
        UNEXPORT,

        CG_SNAP_CREATE,
        CG_SNAP_DELETE,
        CG_CLONE_CREATE,
        CG_CLONE_DELETE,

        /*Not Supported Operations in IBM-SVC*/
        SNAPSHOT_RESTORE,
        MIRROR_OPERATIONS,
        CLONE_RESTORE,
        CG_CREATE,
        CG_DELETE
    }
    
    // A Enum type for Type of Connection
    public enum ConnectionType {

        SMIS {
          @Override
          public String toString() {
            return "SMIS";
          }
        },
        SSH {
          @Override
          public String toString() {
            return "SSH";
          }
        }
      }
    
    // known device types
    public  static enum Type {
        isilon,
        ddmc,
        datadomain,
        vnxblock,
        vnxfile,
        vmax,
        netapp,
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
        ibmsvc,
        openstack,
        vnxe,
        scaleio,
        xtremio;

        static public boolean isFileStorageSystem(Type type) {
            return (type == isilon || type == vnxfile || type == netapp || type==vnxe || type == datadomain);
        }

        static public boolean isProviderStorageSystem(Type type) {
            return  (type == vnxblock)  ||
                    (type == datadomain)||
                    (type == vmax)      ||
                    (type == hds)       ||
                    (type == openstack) ||
                    (type == vplex)     ||
                    (type == ibmxiv)    ||
                    (type == ibmsvc)    ||
                    (type == scaleio);
        }

        static public boolean isVPlexStorageSystem(Type type) {
            return (type == vplex);
        }
       
        static public boolean isIBMXIVStorageSystem(Type type) {
            return (type == ibmxiv);
        }
        
        static public boolean isIBMSVCStorageSystem(Type type) {
            return (type == ibmsvc);
        }
        
        static public boolean isBlockStorageSystem(Type type) {
            return (type == vnxblock || type == vmax || type == vnxe || type==hds || type == ibmxiv || type == ibmsvc);
        }
    }
}
