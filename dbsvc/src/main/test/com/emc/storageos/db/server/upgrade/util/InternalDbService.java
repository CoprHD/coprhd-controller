/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server.upgrade.util;

import com.emc.storageos.db.server.impl.DbServiceImpl;

public class InternalDbService extends DbServiceImpl {

    public String getServiceVersion() {
        return _serviceInfo.getVersion();
    }
}
