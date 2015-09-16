/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.lang.reflect.Constructor;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.List;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.junit.Before;
import org.junit.Test;

import com.emc.storageos.api.mapper.SiteMapper;
import com.emc.storageos.coordinator.client.model.ProductName;
import com.emc.storageos.coordinator.client.model.RepositoryInfo;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.coordinator.client.model.SoftwareVersion;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Site;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.model.dr.DRNatCheckParam;
import com.emc.storageos.model.dr.DRNatCheckResponse;
import com.emc.storageos.model.dr.SiteAddParam;
import com.emc.storageos.model.dr.SiteList;
import com.emc.storageos.model.dr.SiteRestRep;
import com.emc.storageos.security.authentication.InternalApiSignatureKeyGenerator;
import com.emc.storageos.security.authentication.InternalApiSignatureKeyGenerator.SignatureKeyType;
import com.emc.storageos.services.util.SysUtils;

public class DisasterRecoveryServiceTest {

    private DisasterRecoveryService drService;
    private DbClient dbClientMock;
    private CoordinatorClient coordinator;
    private Site standbySite1;
    private Site standbySite2;
    private Site standbySite3;
    private Site standbyConfig;
    private SiteAddParam primarySiteParam;
    private List<URI> uriList;
    private List<Site> standbySites;
    private SiteAddParam standby;
    private DRNatCheckParam natCheckParam;
    private InternalApiSignatureKeyGenerator apiSignatureGeneratorMock;

    @Before
    public void setUp() throws Exception {
        uriList = new LinkedList<URI>();
        standbySites = new LinkedList<Site>();
        
        Constructor constructor = ProductName.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        ProductName productName = (ProductName)constructor.newInstance(null);
        productName.setName("vipr");
        
        SoftwareVersion version = new SoftwareVersion("vipr-2.4.0.0.100");
        LinkedList<SoftwareVersion> available = new LinkedList<SoftwareVersion>();
        available.add(version);
        RepositoryInfo repositoryInfo = new RepositoryInfo(new SoftwareVersion("vipr-2.4.0.0.100"), available);
        
        // setup local VDC
        VirtualDataCenter localVDC = new VirtualDataCenter();
        
        standby = new SiteAddParam();
        standby.setFreshInstallation(true);
        standby.setDbSchemaVersion("2.4");
        standby.setSoftwareVersion("vipr-2.4.0.0.150");

        // setup standby site
        standbySite1 = new Site();
        standbySite1.setId(new URI("site-object-id-1"));
        standbySite1.setUuid("site-uuid-1");
        standbySite1.setVdc(localVDC.getId());
        standbySite1.setVip("10.247.101.110");
        standbySite1.getHostIPv4AddressMap().put("vipr1", "10.247.101.111");
        standbySite1.getHostIPv4AddressMap().put("vipr2", "10.247.101.112");
        standbySite1.getHostIPv4AddressMap().put("vipr3", "10.247.101.113");

        standbySite2 = new Site();
        standbySite2.setId(new URI("site-object-id-2"));
        standbySite2.setUuid("site-uuid-2");
        standbySite2.setVdc(localVDC.getId());

        standbySite3 = new Site();
        standbySite3.setId(new URI("site-object-id-3"));
        standbySite3.setUuid("site-uuid-3");
        standbySite3.setVdc(new URI("fake-vdc-id"));

        primarySiteParam = new SiteAddParam();
        primarySiteParam.setUuid("primary-site-uuid");
        primarySiteParam.setVip("127.0.0.1");
        primarySiteParam.setSecretKey("secret-key");
        primarySiteParam.setHostIPv4AddressMap(standbySite1.getHostIPv4AddressMap());
        primarySiteParam.setHostIPv6AddressMap(standbySite1.getHostIPv6AddressMap());
        
        localVDC.setApiEndpoint("127.0.0.2");
        localVDC.setHostIPv4AddressesMap(standbySite1.getHostIPv4AddressMap());
        localVDC.getHostIPv6AddressesMap().put("vipr1", "11:11:11:11");
        localVDC.getHostIPv6AddressesMap().put("vipr2", "22:22:22:22");
        localVDC.getHostIPv6AddressesMap().put("vipr4", "33:33:33:33");
        
        // mock DBClient
        dbClientMock = mock(DbClient.class);
        
        // mock coordinator client
        coordinator = mock(CoordinatorClient.class);
        
        natCheckParam = new DRNatCheckParam();

        localVDC.getSiteUUIDs().add(standbySite1.getUuid());
        localVDC.getSiteUUIDs().add(standbySite2.getUuid());
        localVDC.setApiEndpoint("127.0.0.2");
        localVDC.setHostIPv4AddressesMap(standbySite1.getHostIPv4AddressMap());
        localVDC.getHostIPv6AddressesMap().put("vipr1", "11:11:11:11");
        localVDC.getHostIPv6AddressesMap().put("vipr2", "22:22:22:22");
        localVDC.getHostIPv6AddressesMap().put("vipr4", "33:33:33:33");

        // mock DBClient
        dbClientMock = mock(DbClient.class);
        apiSignatureGeneratorMock = mock(InternalApiSignatureKeyGenerator.class);
        
        drService = spy(new DisasterRecoveryService());
        drService.setDbClient(dbClientMock);
        drService.setCoordinator(coordinator);
        drService.setSiteMapper(new MockSiteMapper());
        drService.setSysUtils(new SysUtils());

        drService.setApiSignatureGenerator(apiSignatureGeneratorMock);
        
        standbyConfig = new Site();
        standbyConfig.setUuid("standby-site-uuid-1");
        standbyConfig.setVip(localVDC.getApiEndpoint());
        standbyConfig.setHostIPv4AddressMap(localVDC.getHostIPv4AddressesMap());
        standbyConfig.setHostIPv6AddressMap(localVDC.getHostIPv6AddressesMap());
        
        doReturn(standbyConfig.getUuid()).when(coordinator).getSiteId();
        doReturn("primary-site-id").when(coordinator).getPrimarySiteId();
        doReturn(localVDC).when(drService).queryLocalVDC();
        doReturn("2.4").when(coordinator).getCurrentDbSchemaVersion();
        doReturn(repositoryInfo).when(coordinator).getTargetInfo(RepositoryInfo.class);
    }
    
    @Test
    public void testAddStandby() {
        //TODO this test case will be add when implement attach standby feature
    }

    @Test
    public void testGetAllStandby() {
        
        doReturn(uriList).when(dbClientMock).queryByType(Site.class, true);

        standbySites.add(standbySite1);
        standbySites.add(standbySite2);
        standbySites.add(standbySite3);
        doReturn(standbySites.iterator()).when(dbClientMock).queryIterativeObjects(Site.class, uriList);

        SiteList responseList = drService.getAllStandby();

        assertNotNull(responseList.getSites());
        assertEquals(2, responseList.getSites().size());

        compareSiteResponse(responseList.getSites().get(0), standbySite1);
        compareSiteResponse(responseList.getSites().get(1), standbySite2);
    }

    @Test
    public void testGetStandby() {
        doReturn(uriList).when(dbClientMock).queryByType(Site.class, true);

        standbySites.add(standbySite1);
        standbySites.add(standbySite2);
        doReturn(standbySites.iterator()).when(dbClientMock).queryIterativeObjects(Site.class, uriList);

        SiteRestRep response = drService.getStandby("site-uuid-1");
        compareSiteResponse(response, standbySite1);
    }

    @Test
    public void testGetStandby_NotBelongLocalVDC() {
        doReturn(uriList).when(dbClientMock).queryByType(Site.class, true);

        standbySites.add(standbySite1);
        standbySites.add(standbySite2);
        standbySites.add(standbySite3);
        doReturn(standbySites.iterator()).when(dbClientMock).queryIterativeObjects(Site.class, uriList);

        SiteRestRep response = drService.getStandby("site-uuid-not-exist");
        assertNull(response);
    }
    
    @Test
    public void testRemoveStandby() {
        //TODO this test case will be add when implement detach standby feature
    }
    
    @Test
    public void testPrecheckForStandbyAttach() throws Exception {
        doReturn("primary-site-id").when(coordinator).getSiteId();
        drService.precheckForStandbyAttach(standby);
    }

    @Test
    public void testPrecheckForStandbyAttach_FreshInstall() throws Exception {
        try {
            standby.setFreshInstallation(false);
            drService.precheckForStandbyAttach(standby);
            fail();
        } catch (Exception e) {
            // ignore expected exception
        }
    }

    @Test
    public void testPrecheckForStandbyAttach_DBSchema() throws Exception {
        try {
            standby.setDbSchemaVersion("2.3");
            drService.precheckForStandbyAttach(standby);
            fail();
        } catch (Exception e) {
            // ignore expected exception
        }
    }    
    
    public void testGetStandbyConfig() {
        SecretKey key = null;
        try {
            KeyGenerator keyGenerator = null;
            keyGenerator = KeyGenerator.getInstance("HmacSHA256");
            key = keyGenerator.generateKey();
        } catch (NoSuchAlgorithmException e) {
            fail("generate key fail");
        }
        
        doReturn(key).when(apiSignatureGeneratorMock).getSignatureKey(SignatureKeyType.INTERVDC_API);
        doReturn(SiteState.ACTIVE).when(coordinator).getSiteState();
        SiteRestRep response = drService.getStandbyConfig();
        compareSiteResponse(response, standbyConfig);
    }
    
    @Test
    public void testAddPrimary() {
        SiteRestRep response = drService.addPrimary(primarySiteParam);
        assertNotNull(response);
        assertEquals(primarySiteParam.getUuid(), response.getUuid());
        assertEquals(response.getName(), primarySiteParam.getName());
        assertEquals(response.getVip(), primarySiteParam.getVip());
    }

    @Test
    public void testPrecheckForStandbyAttach_Version() throws Exception {
        try {
            standby.setSoftwareVersion("vipr-2.3.0.0.100");
            drService.precheckForStandbyAttach(standby);
            fail();
        } catch (Exception e) {
            // ignore expected exception
        }
    }
    
    @Test
    public void testPrecheckForStandbyAttach_NotPrimarySite() throws Exception {
        try {
            doReturn("654321").when(coordinator).getPrimarySiteId();
            doReturn("123456").when(coordinator).getSiteId();
            drService.precheckForStandbyAttach(standby);
            fail();
        } catch (Exception e) {
            // ignore expected exception
        }
    }
    
    @Test
    public void testPrecheckForStandbyAttach_PrimarySite_EmptyPrimaryID() throws Exception {
        doReturn(null).when(coordinator).getPrimarySiteId();
        drService.precheckForStandbyAttach(standby);
    }
    
    @Test
    public void testPrecheckForStandbyAttach_PrimarySite_IsPrimary() throws Exception {
        doReturn("123456").when(coordinator).getPrimarySiteId();
        doReturn("123456").when(coordinator).getSiteId();
        drService.precheckForStandbyAttach(standby);
    }
    
    @Test
    public void testCheckIfBehindNat_Fail() {
        try {
            drService.checkIfBehindNat(null, "");
            fail();
        } catch (Exception e) {
            //ignore expected exception
        }
        
        try {
            natCheckParam.setIPv4Address("10.247.0.1");
            natCheckParam.setIPv6Address("10.247.0.2");
            drService.checkIfBehindNat(natCheckParam, null);
            fail();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @Test
    public void testCheckIfBehindNat_NotBehindNAT() {
        natCheckParam.setIPv4Address("10.247.101.110");

        DRNatCheckResponse response = drService.checkIfBehindNat(natCheckParam, "10.247.101.110");
        assertEquals(false, response.isBehindNAT());
    }
    
    @Test
    public void testCheckIfBehindNat_IsBehindNAT() {
        natCheckParam.setIPv4Address("10.247.101.111");

        DRNatCheckResponse response = drService.checkIfBehindNat(natCheckParam, "10.247.101.110");
        assertEquals(true, response.isBehindNAT());
    }
    
    protected void compareSiteResponse(SiteRestRep response, Site site) {
        assertNotNull(response);
        assertEquals(response.getId(), site.getId());
        assertEquals(response.getUuid(), site.getUuid());
        assertEquals(response.getName(), site.getName());
        assertEquals(response.getVip(), site.getVip());

        for (String key : response.getHostIPv4AddressMap().keySet()) {
            assertNotNull(site.getHostIPv4AddressMap().get(key));
            assertEquals(response.getHostIPv4AddressMap().get(key), site.getHostIPv4AddressMap().get(key));
        }

        for (String key : response.getHostIPv6AddressMap().keySet()) {
            assertNotNull(site.getHostIPv6AddressMap().get(key));
            assertEquals(response.getHostIPv6AddressMap().get(key), site.getHostIPv6AddressMap().get(key));
        }
    }

    private static class MockSiteMapper extends SiteMapper {

        @Override
        protected void mapDataObjectFields(Site from, SiteRestRep to) {
            to.setId(from.getId());
        }
    }
}
