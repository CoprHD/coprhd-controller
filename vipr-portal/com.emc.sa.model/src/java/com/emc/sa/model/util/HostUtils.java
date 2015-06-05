/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.sa.model.util;

import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.util.NullColumnValueGetter;

/**
 */
public class HostUtils {

    public static boolean isInCluster(Host host) {
        return !NullColumnValueGetter.isNullURI(host.getCluster());
    }
}
