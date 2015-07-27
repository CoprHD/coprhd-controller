/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.util;

/**
 * @author cgarber
 *
 */
public interface DbClientCallbackEvent {
    
    public void call(Object...args);

}
