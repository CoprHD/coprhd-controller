/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.authorization;

/**
 * Global enums for ACLs
 */
public enum ACL {
    ANY, // has any of the following acls - used only in code, never persisted to db
    OWN, // can do all
    ALL, // can do all resource ops
    USE, // can use resource - used for CoS and Neighborhood for now
    BACKUP // has access to only the read-only stuff
}
