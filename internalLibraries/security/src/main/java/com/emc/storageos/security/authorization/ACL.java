/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.security.authorization;

/**
 *  Global enums for ACLs
 */
public enum ACL {
    ANY, // has any of the following acls - used only in code, never persisted to db
    OWN, // can do all
    ALL, // can do all resource ops
    USE, // can use resource - used for CoS and Neighborhood for now
    BACKUP // has access to only the read-only stuff
}
