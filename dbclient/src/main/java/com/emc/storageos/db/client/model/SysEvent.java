/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

/**
 * CF definition for System Event object.
 */
@Cf("SysEvent")
@Ttl(60 * 60 * 2 /* 2 hours */)
public class SysEvent extends DataObject {
}
