/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.common;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.VdcVersion;
import com.emc.storageos.db.client.model.VirtualDataCenter;

public class VdcUtilTest {
    private VdcVersion VDC1_GEO_VERSION = new VdcVersion();
    private VdcVersion VDC2_GEO_VERSION = new VdcVersion();
    private VdcVersion VDC3_GEO_VERSION = new VdcVersion();
    private static final URI VDC1_ID = URIUtil.createId(VdcVersion.class);
    private static final URI VDC2_ID = URIUtil.createId(VdcVersion.class);
    private static final URI VDC3_ID = URIUtil.createId(VdcVersion.class);
    private static final String VERSION_2_2 = "2.2";
    private static final String VERSION_2_5 = "2.5";
    private static final String VERSION_2_6 = "2.6";
    private static final Logger log = LoggerFactory.getLogger(VdcUtilTest.class);

    @Before
    public void setup() {
    	log.error("{} 1 {} 2 {}",new Object[] {"1","2","3"}, new RuntimeException());
        VDC1_GEO_VERSION.setVdcId(VDC1_ID);
        VDC1_GEO_VERSION.setVersion(VERSION_2_2);
        VDC2_GEO_VERSION.setVdcId(VDC2_ID);
        VDC2_GEO_VERSION.setVersion(VERSION_2_5);
        VDC3_GEO_VERSION.setVdcId(VDC3_ID);
        VDC3_GEO_VERSION.setVersion(VERSION_2_6);

    }

    @Test
    public void shouldReturnDefaultVersionIfNoGeoVersion() {
        DbClient dbClientMock = EasyMock.createMock(DbClient.class);
        EasyMock.expect(dbClientMock.queryByType(VdcVersion.class, true)).andReturn(new ArrayList<URI>());
        EasyMock.expect(dbClientMock.queryObject(VdcVersion.class, new ArrayList<URI>())).andReturn(new ArrayList<VdcVersion>());
        EasyMock.expect(dbClientMock.queryByType(VirtualDataCenter.class, true)).andReturn(new ArrayList<URI>());
        EasyMock.replay(dbClientMock);

        VdcUtil.setDbClient(dbClientMock);
        String minialVdcVersion = VdcUtil.getMinimalVdcVersion();
        String expectedVdcVersion = DbConfigConstants.DEFAULT_VDC_DB_VERSION;
        Assert.assertEquals(expectedVdcVersion, minialVdcVersion);
    }

    @Test
    public void shouldReturnDefaultVersionIfMissVdcVersion() {
        List<VdcVersion> geoVersions = new ArrayList<VdcVersion>();
        List<URI> vdcIds = new ArrayList<URI>();

        geoVersions.add(VDC2_GEO_VERSION);
        vdcIds.add(VDC1_ID);
        vdcIds.add(VDC2_ID);

        DbClient dbClientMock = EasyMock.createMock(DbClient.class);
        EasyMock.expect(dbClientMock.queryByType(VdcVersion.class, true)).andReturn(new ArrayList<URI>());
        EasyMock.expect(dbClientMock.queryObject(VdcVersion.class, new ArrayList<URI>())).andReturn(geoVersions);
        EasyMock.expect(dbClientMock.queryByType(VirtualDataCenter.class, true)).andReturn(vdcIds);
        EasyMock.replay(dbClientMock);

        VdcUtil.setDbClient(dbClientMock);
        String minialVdcVersion = VdcUtil.getMinimalVdcVersion();
        String expectedVdcVersion = DbConfigConstants.DEFAULT_VDC_DB_VERSION;
        Assert.assertEquals(expectedVdcVersion, minialVdcVersion);
    }

    @Test
    public void shouldReturnMinimalVersionInVdcVersions() {
        List<VdcVersion> geoVersions = new ArrayList<VdcVersion>();
        List<URI> vdcIds = new ArrayList<URI>();

        geoVersions.add(VDC1_GEO_VERSION);
        geoVersions.add(VDC2_GEO_VERSION);
        geoVersions.add(VDC3_GEO_VERSION);
        vdcIds.add(VDC1_ID);
        vdcIds.add(VDC2_ID);
        vdcIds.add(VDC3_ID);

        DbClient dbClientMock = EasyMock.createMock(DbClient.class);
        EasyMock.expect(dbClientMock.queryByType(VdcVersion.class, true)).andReturn(new ArrayList<URI>());
        EasyMock.expect(dbClientMock.queryObject(VdcVersion.class, new ArrayList<URI>())).andReturn(geoVersions);
        EasyMock.expect(dbClientMock.queryByType(VirtualDataCenter.class, true)).andReturn(vdcIds);
        EasyMock.replay(dbClientMock);

        VdcUtil.setDbClient(dbClientMock);
        String minialVdcVersion = VdcUtil.getMinimalVdcVersion();
        String expectedVdcVersion = DbConfigConstants.DEFAULT_VDC_DB_VERSION;
        Assert.assertEquals(expectedVdcVersion, minialVdcVersion);
    }
}
