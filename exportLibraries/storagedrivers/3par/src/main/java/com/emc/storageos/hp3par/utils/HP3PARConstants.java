/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.utils;

public class HP3PARConstants {

    public static final String DRIVER_NAME ="HP-3PAR";
    public static final String IP_ADDRESS = "IP_ADDRESS"; 
    public static final String PORT_NUMBER = "PORT_NUMBER";
    public static final String USER_NAME = "USER_NAME";
    public static final String PASSWORD = "PASSWORD";
    
    public static final String S_NON_EXISTENT_HOST = "17";
    public static final Integer I_NON_EXISTENT_HOST = 17;
    public static final Integer OP_SUCCESS = 0;
    
    public static Long KILO_BYTE = (long) 1024;
    public static Long MEGA_BYTE = (long) 1024 * 1024;
    
    public static final Integer MODE_SUSPENDED = 1;
    public static final Integer MODE_TARGET = 2;
    public static final Integer TYPE_FREE = 3;
    public static final Integer TYPE_DISK = 2;
    public static final Integer LINK_READY = 4;
    
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

    public static final String TASK_TYPE_CREATE_CLONE_VOLUMES="create-clone-volumes";
    public static final String TASK_TYPE_RESTORE_CLONE_VOLUMES="restore-clone-volumes";
    public static final String TASK_TYPE_DETACH_CLONE_VOLUMES="detach-clone-volumes";
    public static final String TASK_TYPE_DELETE_CLONE_VOLUMES="delete-clone-volumes";
	
    public static final String TASK_TYPE_CREATE_CONSISTENCY_GROUP = "create-consistency-group";
    public static final String TASK_TYPE_DELETE_CONSISTENCY_GROUP = "delete-consistency-group";
    public static final String TASK_TYPE_SNAPSHOT_CONSISTENCY_GROUP = "snapshot-consistency-group";
    public static final String TASK_TYPE_CLONE_CONSISTENCY_GROUP = "clone-consistency-group";
    public static final String TASK_TYPE_UPDATE_CONSISTENCY_GROUP = "update-consistency-group";

    public static final String TASK_TYPE_DELETE_SNAPSHOT_CONSISTENCY_GROUP = "delete-snapshot-consistency-group";
    public static final String TASK_TYPE_DELETE_CLONE_CONSISTENCY_GROUP = "delete-clone-consistency-group";
    public static final String TASK_TYPE_REMOVE_VOLUME_FROM_CONSISTENCY_GROUP = "remove-volume-from-consistency-group";
    public static final String TASK_TYPE_ADD_VOLUME_TO_CONSISTENCY_GROUP = "add-volume-to-consistency-group";
    
    public static final String VLUN_DOES_NOT_EXIST = "VLUN does not exist";

    public static enum provisioningType
    {
        FULL("FULL" , 1),
        TPVV("TPVV", 2),
        SNP("SNP", 3),
    	PEER("PEER", 4),
    	TDVV("TDVV", 5);
    	
        public String type= "";
        public int value = 0;
        
        public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}

		public int getValue() {
			return value;
		}

		public void setValue(int value) {
			this.value = value;
		}

        provisioningType(String type, int value)
        {
            this.type = type;
            this.value= value;
        }       
    }

    
    public static enum copyType
    {
    	BASE("BASE" , 1),
    	PHYSICAL_COPY("PHYSICAL_COPY", 2),
    	VIRTUAL_COPY("VIRTUAL_COPY", 3);
        	
        public String type= "";
        public int value = 0;
        
        public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}

		public int getValue() {
			return value;
		}

		public void setValue(int value) {
			this.value = value;
		}

		copyType(String type, int value)
        {
            this.type = type;
            this.value= value;
        }       
    }

    public static enum vLunType
    {
    	EMPTY("EMPTY" , 1),
    	PORT("PORT", 2),
    	HOST("HOST", 3),
    	MATCHED_SET("MATCHED_SET", 4),
    	HOST_SET("HOST_SET", 5);
        	
        public String type= "";
        public int value = 0;
        
        public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}

		public int getValue() {
			return value;
		}

		public void setValue(int value) {
			this.value = value;
		}

		vLunType(String type, int value)
        {
            this.type = type;
            this.value= value;
        }       
    }

}

