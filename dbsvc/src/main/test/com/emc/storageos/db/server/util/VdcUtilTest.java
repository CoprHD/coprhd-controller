/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * This computer code is copyright 2014 EMC Corporation. All rights reserved.
 */
package com.emc.storageos.db.server.util;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.db.server.DbsvcTestBase;

/**
 * @author cgarber
 *
 */
public class VdcUtilTest extends DbsvcTestBase {
    @Test
    public void testVdcUtil() {

        VirtualDataCenter localVdc = null;
        DbClient dbClient = getDbClient();

        Iterator<URI> vdcs = dbClient.queryByType(VirtualDataCenter.class, true).iterator();
        if (vdcs.hasNext()) {
            localVdc = dbClient.queryObject(VirtualDataCenter.class, vdcs.next());
        } else {
            Assert.fail("no VDC found in database");
        }

        if (vdcs.hasNext()) {
            Assert.fail("multiple VDC's found in database after initial bootstap");
        }

        Assert.assertEquals(localVdc.getId(), VdcUtil.getLocalVdc().getId());

        Assert.assertEquals(localVdc.getShortId(), VdcUtil.getLocalShortVdcId());

        Assert.assertEquals(localVdc.getId(), VdcUtil.getVdcUrn(localVdc.getShortId()));

        VirtualDataCenter remoteVdc1 = new VirtualDataCenter();
        remoteVdc1.setId(URIUtil.createId(VirtualDataCenter.class));
        remoteVdc1.setLocal(Boolean.FALSE);
        remoteVdc1.setShortId("vdc2");

        VirtualDataCenter remoteVdc2 = new VirtualDataCenter();
        remoteVdc2.setId(URIUtil.createId(VirtualDataCenter.class));
        remoteVdc2.setLocal(Boolean.FALSE);
        remoteVdc2.setShortId("vdc3");

        List<VirtualDataCenter> vdcList = new ArrayList<VirtualDataCenter>();
        vdcList.add(remoteVdc1);
        vdcList.add(remoteVdc2);
        dbClient.createObject(vdcList);

        VdcUtil.invalidateVdcUrnCache();

        Assert.assertEquals(localVdc.getId(), VdcUtil.getLocalVdc().getId());

        Assert.assertEquals(localVdc.getShortId(), VdcUtil.getLocalShortVdcId());

        Assert.assertEquals(localVdc.getId(), VdcUtil.getVdcUrn(localVdc.getShortId()));
        Assert.assertEquals(remoteVdc1.getId(), VdcUtil.getVdcUrn(remoteVdc1.getShortId()));
        Assert.assertEquals(remoteVdc2.getId(), VdcUtil.getVdcUrn(remoteVdc2.getShortId()));

        VirtualArray va = new VirtualArray();
        va.setId(URIUtil.createId(VirtualArray.class));

        Assert.assertFalse(VdcUtil.isRemoteObject(va));

        vdcList.clear();
        remoteVdc1.setLocal(Boolean.TRUE);
        localVdc.setLocal(Boolean.FALSE);
        vdcList.add(remoteVdc1);
        vdcList.add(localVdc);
        dbClient.updateAndReindexObject(vdcList);
        VdcUtil.invalidateVdcUrnCache();

        Assert.assertTrue(VdcUtil.isRemoteObject(va));

        URI globalId = URIUtil.createId(Project.class);
        URI localId = URIUtil.createId(FileShare.class);

        Assert.assertTrue(VdcUtil.getVdcId(Project.class, globalId).toString().equals(VdcUtil.getLocalShortVdcId()));
        Assert.assertTrue(VdcUtil.getVdcId(FileShare.class, localId).toString().equals(VdcUtil.getLocalShortVdcId()));
    }
}
