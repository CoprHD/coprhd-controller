package com.emc.storageos.hp3par.impl;

public class HP3PARConstants {

    public static final String DRIVER_NAME="HP-3PAR";
    
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
}
