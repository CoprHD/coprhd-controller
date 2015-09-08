package com.emc.storageos.api.service.impl.resource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.net.URI;
import java.util.LinkedList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.emc.storageos.api.mapper.SiteMapper;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Site;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.model.dr.SiteList;
import com.emc.storageos.model.dr.SiteRestRep;

public class DisasterRecoveryServiceTest {

    private DisasterRecoveryService drService;
    private DbClient dbClientMock;
    private Site standbySite1;
    private Site standbySite2;
    private Site standbySite3;

    @Before
    public void setUp() throws Exception {

        // setup standby site
        standbySite1 = new Site();
        standbySite1.setId(new URI("site-object-id-1"));
        standbySite1.setUuid("site-uuid-1");
        standbySite1.setVip("10.247.101.110");
        standbySite1.getHostIPv4AddressMap().put("vipr1", "10.247.101.111");
        standbySite1.getHostIPv4AddressMap().put("vipr2", "10.247.101.112");
        standbySite1.getHostIPv4AddressMap().put("vipr3", "10.247.101.113");

        standbySite2 = new Site();
        standbySite2.setId(new URI("site-object-id-2"));
        standbySite2.setUuid("site-uuid-2");

        standbySite3 = new Site();
        standbySite3.setId(new URI("site-object-id-3"));
        standbySite3.setUuid("site-uuid-3");

        // setup local VDC
        VirtualDataCenter localVDC = new VirtualDataCenter();
        localVDC.getStandbyIDs().add(standbySite1.getId().toString());
        localVDC.getStandbyIDs().add(standbySite2.getId().toString());

        // mock DBClient
        dbClientMock = mock(DbClient.class);

        drService = spy(new DisasterRecoveryService());
        drService.setDbClient(dbClientMock);
        drService.setSiteMapper(new MockSiteMapper());

        doReturn(localVDC).when(drService).queryLocalVDC();
    }
    
    @Test
    public void testAddStandby() {
        //TODO this test case will be add when implement attach standby feature
    }

    @Test
    public void testGetAllStandby() {
        LinkedList<URI> uriList = new LinkedList<URI>();
        doReturn(uriList).when(dbClientMock).queryByType(Site.class, true);

        List<Site> standbySites = new LinkedList<Site>();
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
        LinkedList<URI> uriList = new LinkedList<URI>();
        doReturn(uriList).when(dbClientMock).queryByType(Site.class, true);

        List<Site> standbySites = new LinkedList<Site>();
        standbySites.add(standbySite1);
        standbySites.add(standbySite2);
        doReturn(standbySites.iterator()).when(dbClientMock).queryIterativeObjects(Site.class, uriList);

        SiteRestRep response = drService.getStandby("site-uuid-1");
        compareSiteResponse(response, standbySite1);
    }

    @Test
    public void testGetStandby_NotBelongLocalVDC() {
        LinkedList<URI> uriList = new LinkedList<URI>();
        doReturn(uriList).when(dbClientMock).queryByType(Site.class, true);

        List<Site> standbySites = new LinkedList<Site>();
        standbySites.add(standbySite1);
        standbySites.add(standbySite2);
        standbySites.add(standbySite3);
        doReturn(standbySites.iterator()).when(dbClientMock).queryIterativeObjects(Site.class, uriList);

        SiteRestRep response = drService.getStandby("site-uuid-3");
        assertNull(response);

        response = drService.getStandby("site-uuid-not-exist");
        assertNull(response);
    }
    
    @Test
    public void testRemoveStandby() {
        //TODO this test case will be add when implement detach standby feature
    }

    protected void compareSiteResponse(SiteRestRep response, Site site) {
        assertNotNull(response);
        assertEquals(response.getUuid(), site.getUuid());
        assertEquals(response.getId(), site.getId());
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
