/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server;

import org.apache.commons.lang.RandomStringUtils;
import org.junit.Test;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.Volume;

public class LargeWriteRequestTest extends DbsvcTestBase {
    @Test
    public void moreThan15MBWriteRequest() throws Exception {
        Volume volume = new Volume();
        volume.setId(URIUtil.createId(Volume.class));
        volume.setPool(URIUtil.createId(StoragePool.class));
        volume.setInactive(false);
        volume.setAllocatedCapacity(1000L);
        volume.setProvisionedCapacity(2000L);
        volume.setLinkStatus(RandomStringUtils.randomAscii(1024 * 1024 * 15));
        getDbClient().updateObject(volume);
    }
}
