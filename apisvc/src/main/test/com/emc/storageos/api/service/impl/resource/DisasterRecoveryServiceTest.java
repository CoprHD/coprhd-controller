/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.lang.reflect.Constructor;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.apache.http.client.utils.URIUtils;
import org.eclipse.jetty.util.log.Log;
import org.junit.Before;
import org.junit.Test;
import org.omg.PortableServer.POAManagerPackage.State;

import com.emc.storageos.api.mapper.SiteMapper;
import com.emc.storageos.coordinator.client.model.ProductName;
import com.emc.storageos.coordinator.client.model.RepositoryInfo;
import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteInfo;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.coordinator.client.model.SoftwareVersion;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.model.dr.DRNatCheckParam;
import com.emc.storageos.model.dr.DRNatCheckResponse;
import com.emc.storageos.model.dr.SiteAddParam;
import com.emc.storageos.model.dr.SiteConfigParam;
import com.emc.storageos.model.dr.SiteConfigRestRep;
import com.emc.storageos.model.dr.SiteList;
import com.emc.storageos.model.dr.SiteParam;
import com.emc.storageos.model.dr.SiteRestRep;
import com.emc.storageos.security.authentication.InternalApiSignatureKeyGenerator;
import com.emc.storageos.security.authentication.InternalApiSignatureKeyGenerator.SignatureKeyType;
import com.emc.storageos.services.util.SysUtils;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.vipr.client.ClientConfig;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.model.sys.ClusterInfo;

public class DisasterRecoveryServiceTest {

    private DisasterRecoveryService drService;
    private DbClient dbClientMock;
    private CoordinatorClient coordinator;
    private Site standbySite1;
    private Site standbySite2;
    private Site standbySite3;
    private Site standbyConfig;
    private SiteParam primarySiteParam;
    private List<URI> uriList;
    private List<Site> standbySites;
    private SiteConfigRestRep standby;
    private DRNatCheckParam natCheckParam;
    private InternalApiSignatureKeyGenerator apiSignatureGeneratorMock;
    private VirtualDataCenter localVDC;
    
    @Before
    public void setUp() throws Exception {
        
        Constructor constructor = ProductName.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        ProductName productName = (ProductName)constructor.newInstance(null);
        productName.setName("vipr");
        
        SoftwareVersion version = new SoftwareVersion("vipr-2.4.0.0.100");
        LinkedList<SoftwareVersion> available = new LinkedList<SoftwareVersion>();
        available.add(version);
        RepositoryInfo repositoryInfo = new RepositoryInfo(new SoftwareVersion("vipr-2.4.0.0.100"), available);
        
        // setup local VDC
        localVDC = new VirtualDataCenter();
        localVDC.setId(URI.create("urn:storageos:VirtualDataCenter:a81058b2-a241-4a77-a89a-d9402d80ea55:vdc1"));
        
        standby = new SiteConfigRestRep();
        standby.setClusterStable(true);
        standby.setFreshInstallation(true);
        standby.setDbSchemaVersion("2.4");
        standby.setSoftwareVersion("vipr-2.4.0.0.150");

        // setup standby site
        standbySite1 = new Site();
        standbySite1.setUuid("site-uuid-1");
        standbySite1.setVip("10.247.101.110");
        standbySite1.getHostIPv4AddressMap().put("vipr1", "10.247.101.111");
        standbySite1.getHostIPv4AddressMap().put("vipr2", "10.247.101.112");
        standbySite1.getHostIPv4AddressMap().put("vipr3", "10.247.101.113");
        standbySite1.setState(SiteState.ACTIVE);
        standbySite1.setVdc(localVDC.getId());
        

        standbySite2 = new Site();
        standbySite2.setUuid("site-uuid-2");
        standbySite2.setState(SiteState.STANDBY_SYNCED);
        standbySite2.setVdc(localVDC.getId());

        standbySite3 = new Site();
        standbySite3.setUuid("site-uuid-3");
        standbySite3.setVdc(new URI("fake-vdc-id"));
        standbySite3.setState(SiteState.ACTIVE);
        standbySite3.setVdc(localVDC.getId());

        primarySiteParam = new SiteParam();
        /*primarySiteParam.setUuid("primary-site-uuid");
        primarySiteParam.setVip("127.0.0.1");
        primarySiteParam.setSecretKey("secret-key");
        primarySiteParam.setHostIPv4AddressMap(standbySite1.getHostIPv4AddressMap());
        primarySiteParam.setHostIPv6AddressMap(standbySite1.getHostIPv6AddressMap());*/
        
        localVDC.setApiEndpoint("127.0.0.2");
        localVDC.setHostIPv4AddressesMap(new StringMap(standbySite1.getHostIPv4AddressMap()));
        localVDC.getHostIPv6AddressesMap().put("vipr1", "11:11:11:11");
        localVDC.getHostIPv6AddressesMap().put("vipr2", "22:22:22:22");
        localVDC.getHostIPv6AddressesMap().put("vipr4", "33:33:33:33");
        
        // mock DBClient
        dbClientMock = mock(DbClient.class);
        
        // mock coordinator client
        coordinator = mock(CoordinatorClient.class);
        
        natCheckParam = new DRNatCheckParam();

        localVDC.setApiEndpoint("127.0.0.2");
        localVDC.setHostIPv4AddressesMap(new StringMap(standbySite1.getHostIPv4AddressMap()));
        localVDC.getHostIPv6AddressesMap().put("vipr1", "11:11:11:11");
        localVDC.getHostIPv6AddressesMap().put("vipr2", "22:22:22:22");
        localVDC.getHostIPv6AddressesMap().put("vipr4", "33:33:33:33");

        // mock DBClient
        dbClientMock = mock(DbClient.class);
        apiSignatureGeneratorMock = mock(InternalApiSignatureKeyGenerator.class);
        
        drService = spy(new DisasterRecoveryService());
        drService.setDbClient(dbClientMock);
        drService.setCoordinator(coordinator);
        drService.setSiteMapper(new SiteMapper());
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
        // prepare parameters for adding standby
        String name = "new-added-standby";
        String desc = "standby-site-1-description";
        String vip = "0.0.0.0";
        String username = "root";
        String password = "password";
        String uuid = "new-added-standby-site-1";

        // mock a ViPRCoreClient with specific UUID
        doReturn(mockViPRCoreClient(uuid)).when(drService).createViPRCoreClient(vip, username, password);

        // mock a local VDC
        doReturn(localVDC).when(drService).queryLocalVDC();
        List<Configuration> allConfigs = new ArrayList<Configuration>();
        allConfigs.add(standbySite1.toConfiguration());
        allConfigs.add(standbySite2.toConfiguration());
        doReturn(allConfigs).when(coordinator).queryAllConfiguration(Site.CONFIG_KIND);
        doReturn(standbySite1.toConfiguration()).when(coordinator).queryConfiguration(Site.CONFIG_KIND, standbySite1.getUuid());
        doReturn(standbySite2.toConfiguration()).when(coordinator).queryConfiguration(Site.CONFIG_KIND, standbySite2.getUuid());

        // mock new added site
        Site newAdded = new Site();
        newAdded.setUuid(uuid);
        newAdded.setVip(vip);
        newAdded.getHostIPv4AddressMap().put("vipr1", "1.1.1.1");
        newAdded.setState(SiteState.ACTIVE);
        doReturn(newAdded.toConfiguration()).when(coordinator).queryConfiguration(Site.CONFIG_KIND, newAdded.getUuid());

        // mock checking and validating methods
        doNothing().when(drService).precheckForStandbyAttach(any(SiteConfigRestRep.class));
        doNothing().when(drService).validateAddParam(any(SiteAddParam.class), any(List.class));

        // assemble parameters, add standby
        SiteAddParam params = new SiteAddParam();
        params.setName(name);
        params.setDescription(desc);
        params.setVip(vip);
        params.setUsername(username);
        params.setPassword(password);
        SiteRestRep rep = drService.addStandby(params);

        // verify the REST response
        assertEquals(name, rep.getName());
        assertEquals(desc, rep.getDescription());
        assertEquals(vip, rep.getVip());
    }

    @Test
    public void testGetAllStandby() throws Exception {
        doReturn(localVDC).when(drService).queryLocalVDC();
        List<Configuration> allConfigs = new ArrayList<Configuration>();
        allConfigs.add(standbySite1.toConfiguration());
        allConfigs.add(standbySite2.toConfiguration());
        doReturn(allConfigs).when(coordinator).queryAllConfiguration(Site.CONFIG_KIND);
        doReturn(standbySite1.toConfiguration()).when(coordinator).queryConfiguration(Site.CONFIG_KIND, standbySite1.getUuid());
        doReturn(standbySite2.toConfiguration()).when(coordinator).queryConfiguration(Site.CONFIG_KIND, standbySite2.getUuid());
        
        SiteList responseList = drService.getSites();

        assertNotNull(responseList.getSites());
        assertEquals(2, responseList.getSites().size());

        compareSiteResponse(responseList.getSites().get(0), standbySite1);
        compareSiteResponse(responseList.getSites().get(1), standbySite2);
    }

    @Test
    public void testGetStandby() throws Exception {
        doReturn(standbySite1.toConfiguration()).when(coordinator).queryConfiguration(Site.CONFIG_KIND, standbySite1.getUuid());

        SiteRestRep response = drService.getSite("site-uuid-1");
        compareSiteResponse(response, standbySite1);
    }

    @Test
    public void testGetStandby_NotBelongLocalVDC() throws Exception {
        doReturn(null).when(coordinator).queryConfiguration(Site.CONFIG_KIND, "site-uuid-not-exist");

        SiteRestRep response = drService.getSite("site-uuid-not-exist");
        assertNull(response);
    }
    
    @Test
    public void testRemoveStandby() {
        String invalidSiteId = "invalid_site_id";
        doReturn(standbySite1.toConfiguration()).when(coordinator).queryConfiguration(Site.CONFIG_KIND,
                standbySite1.getUuid());
        doReturn(standbySite2.toConfiguration()).when(coordinator).queryConfiguration(Site.CONFIG_KIND,
                standbySite2.getUuid());

        doReturn(localVDC).when(drService).queryLocalVDC();
        doNothing().when(coordinator).persistServiceConfiguration(any(Configuration.class));
        doReturn(null).when(coordinator).getTargetInfo(any(String.class), eq(SiteInfo.class));
        doNothing().when(coordinator).setTargetInfo(any(String.class), any(SiteInfo.class));

        SiteRestRep response = drService.removeStandby(standbySite2.getUuid());
    }

    @Test
    public void testPauseStandby() {
        String invalidSiteId = "invalid_site_id";
        doReturn(standbySite1.toConfiguration()).when(coordinator).queryConfiguration(Site.CONFIG_KIND,
                standbySite1.getUuid());
        doReturn(standbySite2.toConfiguration()).when(coordinator).queryConfiguration(Site.CONFIG_KIND,
                standbySite2.getUuid());
        doReturn(null).when(coordinator).queryConfiguration(Site.CONFIG_KIND, invalidSiteId);

        try {
            // primary site
            drService.pauseStandby(standbySite1.getUuid());
        } catch (APIException e) {
            assertEquals(e.getServiceCode(), ServiceCode.API_BAD_REQUEST);
        }
        
        try {
            drService.pauseStandby(invalidSiteId);
        } catch (APIException e) {
            assertEquals(e.getServiceCode(), ServiceCode.API_PARAMETER_INVALID);
        }
        
        doReturn(localVDC).when(drService).queryLocalVDC();
        doNothing().when(coordinator).persistServiceConfiguration(any(Configuration.class));
        doReturn(null).when(coordinator).getTargetInfo(any(String.class), eq(SiteInfo.class));
        doNothing().when(coordinator).setTargetInfo(any(String.class), any(SiteInfo.class));
        
        SiteRestRep response = drService.pauseStandby(standbySite2.getUuid());
        
        assertEquals(response.getState(), SiteState.STANDBY_PAUSED.toString());
    }
    
    @Test
    public void testPrecheckForStandbyAttach() throws Exception {
        doReturn(ClusterInfo.ClusterState.STABLE).when(coordinator).getControlNodesState();
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
        Site site = new Site();
        site.setState(SiteState.ACTIVE);
        doReturn(standbySite1.toConfiguration()).when(coordinator).queryConfiguration(Site.CONFIG_KIND, coordinator.getSiteId());
        SiteConfigRestRep response = drService.getStandbyConfig();
        compareSiteResponse(response, standbyConfig);
    }

    /*
    @Test
    public void testAddPrimary() {
        SiteRestRep response = drService.addStandby(primarySiteParam);
        assertNotNull(response);
        assertEquals(primarySiteParam.getUuid(), response.getUuid());
        assertEquals(response.getName(), primarySiteParam.getName());
        assertEquals(response.getVip(), primarySiteParam.getVip());
    }*/

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
        doReturn(ClusterInfo.ClusterState.STABLE).when(coordinator).getControlNodesState();
        doReturn(null).when(coordinator).getPrimarySiteId();
        drService.precheckForStandbyAttach(standby);
    }
    
    @Test
    public void testPrecheckForStandbyAttach_PrimarySite_IsPrimary() throws Exception {
        doReturn(ClusterInfo.ClusterState.STABLE).when(coordinator).getControlNodesState();
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
        assertEquals(response.getUuid(), site.getUuid());
        assertEquals(response.getName(), site.getName());
        assertEquals(response.getVip(), site.getVip());
    }
    
    protected void compareSiteResponse(SiteConfigRestRep response, Site site) {
        compareSiteResponse(response, site);

        for (String key : response.getHostIPv4AddressMap().keySet()) {
            assertNotNull(site.getHostIPv4AddressMap().get(key));
            assertEquals(response.getHostIPv4AddressMap().get(key), site.getHostIPv4AddressMap().get(key));
        }

        for (String key : response.getHostIPv6AddressMap().keySet()) {
            assertNotNull(site.getHostIPv6AddressMap().get(key));
            assertEquals(response.getHostIPv6AddressMap().get(key), site.getHostIPv6AddressMap().get(key));
        }
    }

    protected ViPRCoreClient mockViPRCoreClient(final String uuid) {
        class MockViPRCoreClient extends ViPRCoreClient {
            @Override
            public com.emc.vipr.client.core.Site site() {
                com.emc.vipr.client.core.Site site = mock(com.emc.vipr.client.core.Site.class);
                SiteConfigRestRep config = new SiteConfigRestRep();
                config.setUuid(uuid);
                config.setHostIPv4AddressMap(new HashMap<String, String>());
                config.setHostIPv6AddressMap(new HashMap<String, String>());
                doReturn(config).when(site).getStandbyConfig();
                doReturn(null).when(site).syncSite(any(SiteConfigParam.class));
                return site;
            }
        }
        return new MockViPRCoreClient();
    }
}
