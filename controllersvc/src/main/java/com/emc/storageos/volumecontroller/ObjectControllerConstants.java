/*
 * Copyright (c) 2012-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller;

public interface ObjectControllerConstants {

    String BUCKET_PERMISSION_EXECUTE = "execute";
    String BUCKET_PERMISSION_FULL_CONTROL = "full_control";
    String BUCKET_PERMISSION_DELETE = "delete";
    String BUCKET_PERMISSION_NONE = "none";
    String BUCKET_PERMISSION_READ = "read";
    String BUCKET_PERMISSION_PRIVILEGED_WRITE = "privileged_write";
    String BUCKET_PERMISSION_WRITE = "write";
    String BUCKET_PERMISSION_READ_ACL = "read_acl";
    String BUCKET_PERMISSION_WRITE_ACL = "write_acl";
    
    public enum DeleteTypeEnum {
        FULL,
        INTERNAL_DB_ONLY,
    }

}
