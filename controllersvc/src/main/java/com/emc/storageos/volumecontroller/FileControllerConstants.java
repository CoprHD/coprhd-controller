/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller;

public interface FileControllerConstants {

    String CIFS_SHARE_PERMISSION_READ = "Read";
    String CIFS_SHARE_PERMISSION_CHANGE = "Change";
    String CIFS_SHARE_PERMISSION_FULLCONTROL = "FullControl";
    String CIFS_SHARE_USER_EVERYONE = "Everyone";
    String CIFS_SHARE_PERMISSION_TYPE_ALLOW = "allow";
    String CIFS_SHARE_PERMISSION_TYPE_DENY = "deny";

    String NFS_EXPORT_USER_NOBODY = "nobody";

    String NFS_FILE_PERMISSION_READ = "Read";
    String NFS_FILE_PERMISSION_WRITE = "write";
    String NFS_FILE_PERMISSION_EXECUTE = "execute";
    String NFS_FILE_PERMISSION_FULLCONTROL = "FullControl";
    String NFS_FILE_USER_EVERYONE = "Everyone";
    String NFS_FILE_PERMISSION_TYPE_ALLOW = "allow";
    String NFS_FILE_PERMISSION_TYPE_DENY = "deny";

    public enum DeleteTypeEnum {
        FULL,
        VIPR_ONLY,
    }

}
