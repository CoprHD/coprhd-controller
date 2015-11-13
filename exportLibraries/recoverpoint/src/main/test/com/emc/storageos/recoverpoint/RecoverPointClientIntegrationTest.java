/*
 * Copyright (c) 2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.recoverpoint;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.recoverpoint.exceptions.RecoverPointException;
import com.emc.storageos.recoverpoint.impl.RecoverPointClient;
import com.emc.storageos.recoverpoint.objectmodel.RPSite;
import com.emc.storageos.recoverpoint.objectmodel.RPSystem;
import com.emc.storageos.recoverpoint.requests.CGRequestParams;
import com.emc.storageos.recoverpoint.requests.CreateBookmarkRequestParams;
import com.emc.storageos.recoverpoint.requests.CreateCopyParams;
import com.emc.storageos.recoverpoint.requests.CreateRSetParams;
import com.emc.storageos.recoverpoint.requests.CreateVolumeParams;
import com.emc.storageos.recoverpoint.requests.MultiCopyDisableImageRequestParams;
import com.emc.storageos.recoverpoint.requests.MultiCopyEnableImageRequestParams;
import com.emc.storageos.recoverpoint.requests.MultiCopyRestoreImageRequestParams;
import com.emc.storageos.recoverpoint.requests.RPCopyRequestParams;
import com.emc.storageos.recoverpoint.responses.GetCGsResponse;
import com.emc.storageos.recoverpoint.responses.GetCopyResponse;
import com.emc.storageos.recoverpoint.responses.GetRSetResponse;
import com.emc.storageos.recoverpoint.responses.GetVolumeResponse;
import com.emc.storageos.recoverpoint.responses.RecoverPointVolumeProtectionInfo;
import com.emc.storageos.recoverpoint.utils.RecoverPointClientFactory;
import com.emc.storageos.recoverpoint.utils.WwnUtils;
//
// RP test config requirements
// Prod/CDP VNX: https://10.247.160.30/start.html
// Required LUNS (masked to lrmb133): 501, 502, 503, 504, 505, 506, 507, 508
//
// CRR VNX: http://10.247.177.35/start.html
// Required LUNS (masked to lrms023/lrms024):
//
import com.emc.storageos.services.util.EnvConfig;
import com.emc.storageos.services.util.LoggingUtils;

public class RecoverPointClientIntegrationTest {
    static {
        LoggingUtils.configureIfNecessary("recoverpoint-log4j.properties");
    }
    private static final Logger logger = LoggerFactory.getLogger(RecoverPointClientIntegrationTest.class);
    
    // Put sanity.properties in c:\Users\<you>\ on Windows
    private static final String UNIT_TEST_CONFIG_FILE = "sanity";

    private boolean isSymmDevices = false;

    private static final String site1InternalSiteName = "0x5fd991b1295c0901";

    private static final String BourneRPTestProdLUN2WWN = EnvConfig.get(UNIT_TEST_CONFIG_FILE,
            "recoverpoint.RecoverPointClientIntegrationTest.BourneRPTestProdLUN2WWN");
    private static final String BourneRPTestCDPLUN2WWN = EnvConfig.get(UNIT_TEST_CONFIG_FILE,
            "recoverpoint.RecoverPointClientIntegrationTest.BourneRPTestCDPLUN2WWN");
    private static final String BourneRPTestProdLUN1WWN = EnvConfig.get(UNIT_TEST_CONFIG_FILE,
            "recoverpoint.RecoverPointClientIntegrationTest.BourneRPTestProdLUN1WWN");
    private static final String BourneRPTestCDPLUN1WWN = EnvConfig.get(UNIT_TEST_CONFIG_FILE,
            "recoverpoint.RecoverPointClientIntegrationTest.BourneRPTestCDPLUN1WWN");
    private static final String BourneRPTestJrnlLUN1WWN = EnvConfig.get(UNIT_TEST_CONFIG_FILE,
            "recoverpoint.RecoverPointClientIntegrationTest.BourneRPTestJrnlLUN1WWN");
    private static final String BourneRPTestJrnlLUN2WWN = EnvConfig.get(UNIT_TEST_CONFIG_FILE,
            "recoverpoint.RecoverPointClientIntegrationTest.BourneRPTestJrnlLUN2WWN");
    private static final String BourneRPTestJrnlLUN3WWN = EnvConfig.get(UNIT_TEST_CONFIG_FILE,
            "recoverpoint.RecoverPointClientIntegrationTest.BourneRPTestJrnlLUN3WWN");
    private static final String BourneRPTestJrnlLUN4WWN = EnvConfig.get(UNIT_TEST_CONFIG_FILE,
            "recoverpoint.RecoverPointClientIntegrationTest.BourneRPTestJrnlLUN4WWN");
    // Site ID: 2
    private static final String BourneRPTestCRRLUN1WWN = EnvConfig.get(UNIT_TEST_CONFIG_FILE,
            "recoverpoint.RecoverPointClientIntegrationTest.BourneRPTestCRRLUN1WWN");
    private static final String BourneRPTestCRRLUN2WWN = EnvConfig.get(UNIT_TEST_CONFIG_FILE,
            "recoverpoint.RecoverPointClientIntegrationTest.BourneRPTestCRRLUN2WWN");
    private static final String BourneRPTestJrnlLUN5WWN = EnvConfig.get(UNIT_TEST_CONFIG_FILE,
            "recoverpoint.RecoverPointClientIntegrationTest.BourneRPTestJrnlLUN5WWN");
    private static final String BourneRPTestJrnlLUN6WWN = EnvConfig.get(UNIT_TEST_CONFIG_FILE,
            "recoverpoint.RecoverPointClientIntegrationTest.BourneRPTestJrnlLUN6WWN");

    /*
    private static final String RP_USERNAME = "admin"; //EnvConfig.get(UNIT_TEST_CONFIG_FILE, "recoverpoint.RP_USERNAME");
    private static final String RP_PASSWORD = "admin"; //EnvConfig.get(UNIT_TEST_CONFIG_FILE, "recoverpoint.RP_PASSWORD");
    private static final String RPSiteToUse = "lglw1044.lss.emc.com"; // EnvConfig.get(UNIT_TEST_CONFIG_FILE, "recoverpoint.RPSiteToUse");
    private static final String RPSystemName = "lglw1044"; // EnvConfig.get(UNIT_TEST_CONFIG_FILE, "recoverpoint.RPSystemName");
    private static final String SITE_MGMT_IPV4 = "10.247.169.83";//EnvConfig.get(UNIT_TEST_CONFIG_FILE, "recoverpoint.SITE_MGMT_IPV4");
     */
    
    private static final String RP_USERNAME = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "recoverpoint.RP_USERNAME");
    private static final String RP_PASSWORD = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "recoverpoint.RP_PASSWORD");
    private static final String RPSiteToUse = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "recoverpoint.RPSiteToUse");
    private static final String RPSystemName = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "recoverpoint.RPSystemName");
    private static final String SITE_MGMT_IPV4 = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "recoverpoint.SITE_MGMT_IPV4");
    
    private static final String FAKE_WWN = "6006016018C12D00";
    private static volatile RecoverPointClient rpClient;

    private static final String preURI = "https://";
    private static final String postURI = ":7225/fapi/version4_1" + "?wsdl";

    private static final String site2InternalSiteName = "";

    private static volatile String Bookmarkname = "";

    @BeforeClass
    public static void setup() {
        setupClient(RPSiteToUse, RP_USERNAME, RP_PASSWORD);
    }

    public static void setupClient(String ip, String username, String password) {
        URI endpoint = null;
        try {
            endpoint = new URI(preURI + RPSiteToUse + postURI);
            List<URI> endpoints = new ArrayList<URI>();
            endpoints.add(endpoint);
            rpClient = RecoverPointClientFactory.getClient(URI.create("http://anykey.com/"), endpoints, username, password);
        } catch (URISyntaxException e) {
            logger.error(e.getMessage(), e);
        } catch (RecoverPointException e) {
            logger.error(e.getMessage(), e);
        }

        logger.error("Hello error");
        logger.debug("Hello debug");
        logger.info("Hello info");
        if (logger.isInfoEnabled()) {
            logger.info("Info enabled");
        } else {
            logger.error("Info not enabled. ");
        }
    }

    @Test
    public void testRecoverPointServicePing() {
        boolean foundError = false;
        logger.info("Testing RecoverPoint Service ping");
        int retVal = 0;
        logger.info("Testing good credentials");
        try {
            retVal = rpClient.ping();
        } catch (RecoverPointException e) {
            foundError = true;
            fail(e.getMessage());
        }

        if (!foundError) {
            logger.info("TestRecoverPointServicePing PASSED");
        }
    }

    @Test
    public void testRecoverPointServiceTopology() {
        logger.info("Testing RecoverPoint Service topology");
        Set<String> topologies;
        try {
            topologies = rpClient.getClusterTopology();
            for (String topology : topologies) {
                logger.info("Topology: " + topology);
            }
        } catch (RecoverPointException e) {
            fail(e.getMessage());
        }

    }

    @Test
    public void testGetAllCGs() {
        logger.info("Testing RecoverPoint CG Retrieval");
        Set<GetCGsResponse> cgs;
        try {
            cgs = rpClient.getAllCGs();
            Set<String> wwns = new HashSet<String>();
            for (GetCGsResponse cg : cgs) {
                logger.info("CG: " + cg);
     
                assertNotNull(cg.getCgName());
                assertNotNull(cg.getCgId());
                
                // Make sure certain fields are filled-in
                if (cg.getCopies() != null) {
                    for (GetCopyResponse copy : cg.getCopies()) {
                        assertNotNull(copy.getJournals());
                        assertNotNull(copy.getName());
                        for (GetVolumeResponse volume : copy.getJournals()) {
                            assertNotNull(volume.getInternalSiteName());
                            assertNotNull(volume.getRpCopyName());
                            assertNotNull(volume.getWwn());
                            // Make sure the same volume isn't in more than one place in the list.
                            assertFalse(wwns.contains(volume.getWwn()));
                            wwns.add(volume.getWwn());
                        }
                    }
                }

                if (cg.getRsets() != null) {
                    for (GetRSetResponse rset : cg.getRsets()) {
                        assertNotNull(rset.getName());
                        assertNotNull(rset.getVolumes());
                        for (GetVolumeResponse volume : rset.getVolumes()) {
                            assertNotNull(volume.getInternalSiteName());
                            assertNotNull(volume.getRpCopyName());
                            assertNotNull(volume.getWwn());
                            // Make sure the same volume isn't in more than one place in the list.
                            assertFalse(wwns.contains(volume.getWwn()));
                            wwns.add(volume.getWwn());
                        }
                    }
                }
                
                // Make sure you have journals, sources, and targets
            }
        } catch (RecoverPointException e) {
            fail(e.getMessage());
        }         
    }
    
    @Test
    public void testGetAllSites() {
        RPSystem rpSystem = new RPSystem();
        RPSite site = new RPSite();
        Set<RPSite> siteList = new HashSet<RPSite>();
        site.setCredentials(RP_USERNAME, RP_PASSWORD);
        site.setSiteManagementIPv4(RPSiteToUse);
        siteList.add(site);
        rpSystem.setSites(siteList);
        rpSystem.setName(RPSystemName);

        setup();
        logger.info("Testing RecoverPoint Service getAllSites");
        site.setSiteManagementIPv4(RPSiteToUse); // NOSONAR

        try {
            Set<RPSite> returnList;
            setupClient(RPSiteToUse, RP_USERNAME, RP_PASSWORD);
            returnList = rpClient.getAssociatedRPSites();
            if (returnList.isEmpty()) {
                logger.info("getAssociatedRPSites FAILED");
                fail();
            }
        } catch (Exception e) {
            logger.error(e.toString());
            logger.info("TestGetAllSites FAILED");
            fail();
        }
        logger.info("Setting sitename to RPA IP address");
        setup();
        site.setSiteManagementIPv4(SITE_MGMT_IPV4); // NOSONAR
        try {
            Set<RPSite> returnList;
            setupClient(RPSiteToUse, RP_USERNAME, RP_PASSWORD);
            returnList = rpClient.getAssociatedRPSites();
            if (returnList.isEmpty()) {
                logger.info("getAssociatedRPSites FAILED");
                fail();
            }
        } catch (Exception e) {
            logger.error(e.toString());
            logger.info("TestGetAllSites FAILED");
            fail();
        }
    }

    @Test
    public void testGetAllArrays() {
        RPSystem rpSystem = new RPSystem();
        RPSite site = new RPSite();
        Set<RPSite> siteList = new HashSet<RPSite>();
        site.setCredentials(RP_USERNAME, RP_PASSWORD);
        site.setSiteManagementIPv4(RPSiteToUse);
        siteList.add(site);
        rpSystem.setSites(siteList);
        rpSystem.setName(RPSystemName);

        setup();
        logger.info("Testing RecoverPoint Service getArraysForCluster");
        site.setSiteManagementIPv4(RPSiteToUse); // NOSONAR

        try {
            Map<String, Set<String>> returnList;
            setupClient(RPSiteToUse, RP_USERNAME, RP_PASSWORD);
            returnList = rpClient.getArraysForClusters();
            if (returnList.size() == 0) {
                logger.info("getArraysForCluster FAILED");
                fail();
            }
        } catch (Exception e) {
            logger.error(e.toString());
            logger.info("TestGetAllArrays FAILED");
            fail();
        }
    }

    @Test
    public void testRecoverPointServiceGetSiteWWNs() {
        RPSystem rpSystem = new RPSystem();
        RPSite site = new RPSite();
        Set<RPSite> siteList = new HashSet<RPSite>();
        site.setCredentials(RP_USERNAME, RP_PASSWORD);
        site.setSiteManagementIPv4(RPSiteToUse);
        siteList.add(site);
        rpSystem.setSites(siteList);
        rpSystem.setName(RPSystemName);
        setup();
        logger.info("Testing RecoverPoint Service getAllSites");
        site.setSiteManagementIPv4(RPSiteToUse); // NOSONAR

        try {
            Set<RPSite> returnList;
            setupClient(RPSiteToUse, RP_USERNAME, RP_PASSWORD);
            returnList = rpClient.getAssociatedRPSites();
            if (returnList.isEmpty()) {
                logger.info("getAssociatedRPSites FAILED");
                fail();
            }
            for (RPSite rpSite : returnList) {
                boolean foundError = false;
                logger.info("Testing RecoverPoint Get Site WWNs");
                Map<String, Map<String, String>> WWNs = null;
                WWNs = rpClient.getInitiatorWWNs(rpSite.getInternalSiteName());
                if (WWNs == null || WWNs.size() < 1) {
                    foundError = true;
                    fail("No WWNs were returned");
                }
                if (!foundError) {                	
                    logger.info("TestRecoverPointServiceGetSiteWWNs PASSED.  Found " + WWNs.size() + " Initiator WWNs");
                }
            }
        } catch (Exception e) {
            logger.error(e.toString());
            logger.info("TestGetAllSites FAILED");
            fail();
        }
    }

    // @Test
    public void testWwnUtils() {
        logger.info("Testing WWN Converter");

        String wwnString = FAKE_WWN;
        @SuppressWarnings("unused")
        Long wwnLong = WwnUtils.convertWWNtoLong(wwnString);
        logger.info("TestWwnUtils PASSED.");
    }

    // @Test
    public void testGetProtectionInfoForVolume() {
        logger.info("Testing RecoverPoint Get Protection Info For Volume");
        RecoverPointVolumeProtectionInfo protectionInfo = null;
        boolean foundError = false;
        try {
            recreateCG();
            protectionInfo = rpClient.getProtectionInfoForVolume(BourneRPTestProdLUN1WWN);
        } catch (RecoverPointException e) {
            foundError = true;
            fail(e.getMessage());
        }
        if (protectionInfo == null) {
            foundError = true;
            fail("Failed to find protection info for WWN: " + BourneRPTestProdLUN1WWN);
        }
        if (!foundError) {
            // Verify this is a source
            if (protectionInfo.getRpVolumeCurrentProtectionStatus() == RecoverPointVolumeProtectionInfo.volumeProtectionStatus.PROTECTED_SOURCE) {
                logger.info("RecoverPoint Get Protection Info For Volume PASSED");
                logger.info("Protected source volume " + BourneRPTestProdLUN1WWN + " is on CG Name: "
                        + protectionInfo.getRpProtectionName());
            } else {
                foundError = true;
                fail("Volume " + BourneRPTestProdLUN1WWN + " did not map to a protected source volume");
            }
        }
        protectionInfo = null;
        try {
            protectionInfo = rpClient.getProtectionInfoForVolume(BourneRPTestCRRLUN1WWN);
        } catch (RecoverPointException e) {
            foundError = true;
            fail(e.getMessage());
        }
        if (protectionInfo == null) {
            foundError = true;
            fail("Failed to find protection info for WWN: " + BourneRPTestCRRLUN1WWN);
        }
        if (!foundError) {
            // Verify this is a source
            if (protectionInfo.getRpVolumeCurrentProtectionStatus() == RecoverPointVolumeProtectionInfo.volumeProtectionStatus.PROTECTED_TARGET) {
                logger.info("RecoverPoint Get Protection Info For Volume PASSED");
                logger.info("Protected target volume " + BourneRPTestCRRLUN1WWN + " is on CG Name: " + protectionInfo.getRpProtectionName());
            } else {
                foundError = true;
                fail("Volume " + BourneRPTestCRRLUN1WWN + " did not map to a protected source target");
            }
        }

        try {
            protectionInfo = rpClient.getProtectionInfoForVolume(BourneRPTestJrnlLUN1WWN);
        } catch (RecoverPointException e) {
            foundError = true;
            fail(e.getMessage());
        }
        if (protectionInfo == null) {
            foundError = true;
            fail("Failed to find protection info for WWN: " + BourneRPTestJrnlLUN1WWN);
        }
        if (!foundError) {
            // Verify this is a source
            if (protectionInfo.getRpVolumeCurrentProtectionStatus() == RecoverPointVolumeProtectionInfo.volumeProtectionStatus.SOURCE_JOURNAL) {
                logger.info("RecoverPoint Get Protection Info For Volume PASSED");
                logger.info("Source journal volume " + BourneRPTestJrnlLUN1WWN + " is on CG Name: " + protectionInfo.getRpProtectionName());
            } else {
                foundError = true;
                fail("Volume " + BourneRPTestJrnlLUN1WWN + " did not map to a source journal volume");
            }
        }
        protectionInfo = null;
        try {
            protectionInfo = rpClient.getProtectionInfoForVolume(BourneRPTestJrnlLUN5WWN);
        } catch (RecoverPointException e) {
            foundError = true;
            fail(e.getMessage());
        }
        if (protectionInfo == null) {
            foundError = true;
            fail("Failed to find protection info for WWN: " + BourneRPTestJrnlLUN5WWN);
        }
        if (!foundError) {
            // Verify this is a source
            if (protectionInfo.getRpVolumeCurrentProtectionStatus() == RecoverPointVolumeProtectionInfo.volumeProtectionStatus.TARGET_JOURNAL) {
                logger.info("RecoverPoint Get Protection Info For Volume PASSED");
                logger.info("Target journal volume " + BourneRPTestJrnlLUN5WWN + " is on CG Name: " + protectionInfo.getRpProtectionName());
            } else {
                foundError = true;
                fail("Volume " + BourneRPTestJrnlLUN5WWN + " did not map to a protected source target");
            }
        }
        logger.info("TestGetProtectionInfoForVolumeAndEnableAndDisable PASSED");
    }

    // @Test
    public void testStopStartPauseResume() {
        logger.info("Testing RecoverPoint Stop, Start, Pause, Resume");
        RecoverPointVolumeProtectionInfo protectionInfo = null;
        RecoverPointVolumeProtectionInfo crrCopyProtectionInfo = null;
        RecoverPointVolumeProtectionInfo cdpCopyProtectionInfo = null;
        boolean foundError = false;
        try {
            recreateCG();
            protectionInfo = rpClient.getProtectionInfoForVolume(BourneRPTestProdLUN1WWN);
            crrCopyProtectionInfo = rpClient.getProtectionInfoForVolume(BourneRPTestCRRLUN1WWN);
            cdpCopyProtectionInfo = rpClient.getProtectionInfoForVolume(BourneRPTestCDPLUN1WWN);
        } catch (RecoverPointException e) {
            foundError = true;
            fail(e.getMessage());
        }
        if (protectionInfo == null) {
            foundError = true;
            fail("Failed to find protection info for WWN: " + BourneRPTestProdLUN1WWN);
        }

        if (!foundError) {
            logger.info("Testing RecoverPoint Disable/Enable using source");
            try {
                logger.info("Disable CG: " + protectionInfo.getRpProtectionName());
                rpClient.disableProtection(protectionInfo);
                rpClient.getCGState(protectionInfo);
                logger.info("Enable CG: " + protectionInfo.getRpProtectionName());
                rpClient.enableProtection(protectionInfo);
                rpClient.getCGState(protectionInfo);
            } catch (RecoverPointException e) {
                foundError = true;
                fail(e.getMessage());
            }
        }

        if (!foundError) {
            logger.info("Testing RecoverPoint Pause/Resume using source");
            try {
                logger.info("Pause CG: " + protectionInfo.getRpProtectionName());
                rpClient.pauseTransfer(protectionInfo);
                rpClient.getCGState(protectionInfo);
                logger.info("Resume CG: " + protectionInfo.getRpProtectionName());
                rpClient.resumeTransfer(protectionInfo);
                rpClient.getCGState(protectionInfo);
                ;

            } catch (RecoverPointException e) {
                foundError = true;
                fail(e.getMessage());
            }
        }
        if (!foundError) {
            logger.info("Testing RecoverPoint Disable/Enable using CRR");
            try {
                logger.info("Disable CG copy: " + crrCopyProtectionInfo.getRpProtectionName());
                rpClient.disableProtection(crrCopyProtectionInfo);
                rpClient.getCGState(crrCopyProtectionInfo);
                logger.info("Enable CG copy: " + crrCopyProtectionInfo.getRpProtectionName());
                rpClient.enableProtection(crrCopyProtectionInfo);
                rpClient.getCGState(crrCopyProtectionInfo);
            } catch (RecoverPointException e) {
                foundError = true;
                fail(e.getMessage());
            }
        }

        if (!foundError) {
            logger.info("Testing RecoverPoint Pause/Resume using CRR");
            try {
                logger.info("Pause CG copy: " + crrCopyProtectionInfo.getRpProtectionName());
                rpClient.pauseTransfer(crrCopyProtectionInfo);
                rpClient.getCGState(crrCopyProtectionInfo);
                logger.info("Resume CG copy: " + crrCopyProtectionInfo.getRpProtectionName());
                rpClient.resumeTransfer(crrCopyProtectionInfo);
                rpClient.getCGState(crrCopyProtectionInfo);

            } catch (RecoverPointException e) {
                foundError = true;
                fail(e.getMessage());
            }
        }
        if (!foundError) {
            logger.info("Testing RecoverPoint Disable/Enable using CDP");
            try {
                logger.info("Disable CG copy: " + cdpCopyProtectionInfo.getRpProtectionName());
                rpClient.disableProtection(cdpCopyProtectionInfo);
                rpClient.getCGState(cdpCopyProtectionInfo);
                logger.info("Enable CG copy: " + cdpCopyProtectionInfo.getRpProtectionName());
                rpClient.enableProtection(cdpCopyProtectionInfo);
                rpClient.getCGState(cdpCopyProtectionInfo);
            } catch (RecoverPointException e) {
                foundError = true;
                fail(e.getMessage());
            }
        }

        if (!foundError) {
            logger.info("Testing RecoverPoint Pause/Resume using CDP");
            try {
                logger.info("Pause CG copy: " + cdpCopyProtectionInfo.getRpProtectionName());
                rpClient.pauseTransfer(cdpCopyProtectionInfo);
                rpClient.getCGState(cdpCopyProtectionInfo);
                logger.info("Resume CG copy: " + cdpCopyProtectionInfo.getRpProtectionName());
                rpClient.resumeTransfer(cdpCopyProtectionInfo);
                rpClient.getCGState(cdpCopyProtectionInfo);

            } catch (RecoverPointException e) {
                foundError = true;
                fail(e.getMessage());
            }
        }
    }

    @Test
    public void testRecoverPointServiceCreateDeleteCG() {
        logger.info("Testing RecoverPoint Create CG");

        try {
            // RecreateCG();
            recreateCGCDPOnly();
            // RecoverPointVolumeProtectionInfo prodInfo = rpClient.getProtectionInfoForVolume(BourneRPTestProdLUN1WWN);
            // rpClient.deleteCG(prodInfo);
            logger.info("TestRecoverPointServiceCreateDeleteCG PASSED");
        } catch (RecoverPointException e) {
            fail(e.getMessage());
        }
    }

    // @Test
    public void testCreateRPBookmarks() {
        boolean foundError = false;
        logger.info("Testing RecoverPoint Create Bookmark");
        try {
            recreateCGAndBookmark();
        } catch (RecoverPointException e) {
            foundError = true;
            fail(e.getMessage());
        }
        if (!foundError) {
            logger.info("TestCreateRPBookmarks PASSED");
        }
    }

    // @Test
    public void testEnableDisableRPBookmarks() throws InterruptedException {
        boolean foundError = false;
        logger.info("Testing RecoverPoint Enable/Disable Bookmark");
        MultiCopyEnableImageRequestParams enableParams = new MultiCopyEnableImageRequestParams();
        MultiCopyDisableImageRequestParams disableParams = new MultiCopyDisableImageRequestParams();
        try {
            recreateCGAndBookmark();
            enableParams.setBookmark(Bookmarkname);
            Set<String> WWNSetForTest = new HashSet<String>();
            WWNSetForTest.add(BourneRPTestCRRLUN1WWN);
            WWNSetForTest.add(BourneRPTestCRRLUN2WWN);
            enableParams.setVolumeWWNSet(WWNSetForTest);
            disableParams.setVolumeWWNSet(WWNSetForTest);
            disableParams.setEmName(Bookmarkname);
            rpClient.enableImageCopies(enableParams);
            logger.info("Sleep 15 seconds before disable of image");
            Thread.sleep(15000);
            rpClient.disableImageCopies(disableParams);
        } catch (RecoverPointException e) {
            foundError = true;
            fail(e.getMessage());
        }
        if (!foundError) {
            logger.info("TestEnableDisableRPBookmarks PASSED");
        }
    }

    @Test
    public void testRestoreRPBookmarks() throws InterruptedException {
        boolean foundError = false;
        logger.info("Testing RecoverPoint Create Bookmark");
        MultiCopyRestoreImageRequestParams restoreParams = new MultiCopyRestoreImageRequestParams();
        try {
            recreateCGAndBookmark();
            restoreParams.setBookmark(Bookmarkname);
            Set<String> WWNSetForTest = new HashSet<String>();
            WWNSetForTest.add(BourneRPTestCRRLUN1WWN);
            WWNSetForTest.add(BourneRPTestCRRLUN2WWN);
            restoreParams.setVolumeWWNSet(WWNSetForTest);
            restoreParams.setVolumeWWNSet(WWNSetForTest);

            rpClient.restoreImageCopies(restoreParams);
        } catch (RecoverPointException e) {
            foundError = true;
            fail(e.getMessage());
        }
        if (!foundError) {
            logger.info("TestRestoreRPBookmarks PASSED");
        }
    }

    // @Test
    public void testFailoverTestAndTestCancel() {
        logger.info("Testing RecoverPoint Failover Cancel For Volume");
        RecoverPointVolumeProtectionInfo protectionInfo = null;
        boolean foundError = false;
        try {
            recreateCGAndBookmark();
            protectionInfo = rpClient.getProtectionInfoForVolume(BourneRPTestCRRLUN1WWN);
        } catch (RecoverPointException e) {
            foundError = true;
            fail(e.getMessage());
        }
        if (protectionInfo == null) {
            foundError = true;
            fail("Failed to find protection info for WWN: " + BourneRPTestCRRLUN1WWN);
        }

        if (!foundError) {
            // Verify this is a source
            if (protectionInfo.getRpVolumeCurrentProtectionStatus() == RecoverPointVolumeProtectionInfo.volumeProtectionStatus.PROTECTED_TARGET) {
                logger.info("Protected target volume " + BourneRPTestCRRLUN1WWN + " is on CG Name: " + protectionInfo.getRpProtectionName());
            } else {
                foundError = true;
                fail("Volume " + BourneRPTestCRRLUN1WWN + " did not map to a protected source target");
            }
        }
        RPCopyRequestParams failoverTestRequest = new RPCopyRequestParams();
        failoverTestRequest.setCopyVolumeInfo(protectionInfo);
        try {
            try {
                logger.info("Delete the CDP copy");
                RecoverPointVolumeProtectionInfo deleteCopyInfo = null;
                deleteCopyInfo = rpClient.getProtectionInfoForVolume(BourneRPTestCDPLUN1WWN);
                rpClient.deleteCopy(deleteCopyInfo);
            } catch (Exception e) {
                logger.info("Ignore error deleting CDP copy");
            }
            logger.info("Failover test start");
            rpClient.failoverCopyTest(failoverTestRequest);
            logger.info("Sleep 15 seconds before cancel of failover test");
            Thread.sleep(15000);
            logger.info("Failover test cancel start");
            rpClient.failoverCopyTestCancel(failoverTestRequest);
        } catch (RecoverPointException e) {
            foundError = true;
            fail(e.getMessage());
        } catch (InterruptedException e) {
            foundError = true;
            fail(e.getMessage());
        }
        if (!foundError) {
            logger.info("TestFailoverTestAndTestCancel PASSED");
        }
    }

    // @Test
    public void testFailoverTestAndFailoverAndFailback() {
        logger.info("Testing RecoverPoint Failover Cancel For Volume");
        RecoverPointVolumeProtectionInfo protectionInfo = null;
        RecoverPointVolumeProtectionInfo failbackProtectionInfo = null;
        boolean foundError = false;
        try {
            recreateCGAndBookmark();

            protectionInfo = rpClient.getProtectionInfoForVolume(BourneRPTestCRRLUN1WWN);
            failbackProtectionInfo = rpClient.getProtectionInfoForVolume(BourneRPTestProdLUN1WWN);
        } catch (RecoverPointException e) {
            foundError = true;
            fail(e.getMessage());
        }
        if (protectionInfo == null) {
            foundError = true;
            fail("Failed to find protection info for WWN: " + BourneRPTestCRRLUN1WWN);
        }
        if (failbackProtectionInfo == null) {
            foundError = true;
            fail("Failed to find protection info for WWN: " + BourneRPTestProdLUN1WWN);
        }
        if (!foundError) {
            // Verify this is a target
            if (protectionInfo.getRpVolumeCurrentProtectionStatus() == RecoverPointVolumeProtectionInfo.volumeProtectionStatus.PROTECTED_TARGET) {
                logger.info("Protected target volume " + BourneRPTestCRRLUN1WWN + " is on CG Name: " + protectionInfo.getRpProtectionName());
            } else {
                foundError = true;
                fail("Volume " + BourneRPTestCRRLUN1WWN + " did not map to a protected source target");
            }
        }
        RPCopyRequestParams failoverTestRequest = new RPCopyRequestParams();
        RPCopyRequestParams failbackRequest = new RPCopyRequestParams();
        failoverTestRequest.setCopyVolumeInfo(protectionInfo);
        failbackRequest.setCopyVolumeInfo(failbackProtectionInfo);
        try {
            try {
                logger.info("Delete the CDP copy");
                RecoverPointVolumeProtectionInfo deleteCopyInfo = null;
                deleteCopyInfo = rpClient.getProtectionInfoForVolume(BourneRPTestCDPLUN1WWN);
                rpClient.deleteCopy(deleteCopyInfo);
            } catch (Exception e) {
                logger.info("Ignore error deleting CDP copy");
            }
            logger.info("Failover test start");
            rpClient.failoverCopyTest(failoverTestRequest);
            logger.info("Sleep 15 seconds before complete of failover");
            Thread.sleep(15000);
            rpClient.failoverCopy(failoverTestRequest);
            logger.info("Sleep 15 seconds before failback");
            Thread.sleep(15000);
            rpClient.failoverCopy(failbackRequest);
        } catch (RecoverPointException e) {
            foundError = true;
            fail(e.getMessage());
        } catch (InterruptedException e) {
            foundError = true;
            fail(e.getMessage());
        }
        if (!foundError) {
            logger.info("TestFailoverTestAndFailoverAndFailback PASSED");
        }
    }

    // @Test
    public void testFailover() {
        logger.info("Testing RecoverPoint Failover For Volume");
        RecoverPointVolumeProtectionInfo protectionInfo = null;
        RecoverPointVolumeProtectionInfo failbackProtectionInfo = null;
        boolean foundError = false;
        try {
            recreateCGAndBookmark();

            protectionInfo = rpClient.getProtectionInfoForVolume(BourneRPTestCRRLUN1WWN);
            failbackProtectionInfo = rpClient.getProtectionInfoForVolume(BourneRPTestProdLUN1WWN);
        } catch (RecoverPointException e) {
            foundError = true;
            fail(e.getMessage());
        }
        if (protectionInfo == null) {
            foundError = true;
            fail("Failed to find protection info for WWN: " + BourneRPTestCRRLUN1WWN);
        }
        if (failbackProtectionInfo == null) {
            foundError = true;
            fail("Failed to find protection info for WWN: " + BourneRPTestProdLUN1WWN);
        }
        if (!foundError) {
            // Verify this is a source
            if (protectionInfo.getRpVolumeCurrentProtectionStatus() == RecoverPointVolumeProtectionInfo.volumeProtectionStatus.PROTECTED_TARGET) {
                logger.info("RecoverPoint Get Protection Info For Volume PASSED");
                logger.info("Protected target volume " + BourneRPTestCRRLUN1WWN + " is on CG Name: " + protectionInfo.getRpProtectionName());
            } else {
                foundError = true;
                fail("Volume " + BourneRPTestCRRLUN1WWN + " did not map to a protected source target");
            }
        }
        RPCopyRequestParams failoverRequest = new RPCopyRequestParams();
        RPCopyRequestParams failbackRequest = new RPCopyRequestParams();
        failoverRequest.setCopyVolumeInfo(protectionInfo);
        failbackRequest.setCopyVolumeInfo(failbackProtectionInfo);
        try {
            // try {
            // logger.info("Delete the CDP copy");
            // RecoverPointVolumeProtectionInfo deleteCopyInfo = null;
            // deleteCopyInfo = rpClient.getProtectionInfoForVolume(BourneRPTestCDPLUN1WWN);
            // rpClient.deleteCopy(deleteCopyInfo);
            // } catch (Exception e) {
            // logger.info("Ignore error deleting CDP copy");
            // }
            logger.info("Failover start");
            rpClient.failoverCopy(failoverRequest);
            logger.info("Sleep 15 seconds before failback");
            Thread.sleep(15000);
            logger.info("Failback start");
            rpClient.failoverCopy(failbackRequest);
        } catch (RecoverPointException e) {
            foundError = true;
            fail(e.getMessage());
        } catch (InterruptedException e) {
            foundError = true;
            fail(e.getMessage());
        }
        if (!foundError) {
            logger.info("TestFailover PASSED");
        }
    }

    // @Test
    public void testExceptionUseCases() throws InterruptedException {
        boolean foundError = false;
        logger.info("Testing RecoverPoint Exception Use Cases");

        try {
            // RecreateCGAndBookmark();
            logger.info("Pause Pause Resume on a CG Copy");
            RecoverPointVolumeProtectionInfo protectionInfo = null;
            protectionInfo = rpClient.getProtectionInfoForVolume(BourneRPTestCRRLUN1WWN);
            rpClient.pauseTransfer(protectionInfo);
            rpClient.pauseTransfer(protectionInfo);
            rpClient.resumeTransfer(protectionInfo);
            logger.info("Pause Pause Resume on a CG Copy PASSED");

        } catch (RecoverPointException e) {
            foundError = true;
            fail(e.getMessage());
        }
        try {
            logger.info("Disable Disable Enable on a CG Copy");
            RecoverPointVolumeProtectionInfo protectionInfo = null;
            protectionInfo = rpClient.getProtectionInfoForVolume(BourneRPTestCRRLUN1WWN);
            rpClient.disableProtection(protectionInfo);
            rpClient.disableProtection(protectionInfo);
            rpClient.enableProtection(protectionInfo);
            logger.info("Disable Disable Enable on a CG Copy PASSED");
        } catch (RecoverPointException e) {
            foundError = true;
            fail(e.getMessage());
        }
        try {
            logger.info("Enable Most Recent Image, Enable Bookmark (should fail)");

            CreateBookmarkRequestParams params = new CreateBookmarkRequestParams();
            Bookmarkname = "BourneBookmark_";
            Random randomnumber = new Random();
            Bookmarkname += Math.abs(randomnumber.nextInt());
            params.setBookmark(Bookmarkname);
            Set<String> WWNSetToBookmark = new HashSet<String>();
            WWNSetToBookmark.add(BourneRPTestCRRLUN1WWN);
            WWNSetToBookmark.add(BourneRPTestCRRLUN2WWN);
            params.setVolumeWWNSet(WWNSetToBookmark);
            rpClient.createBookmarks(params);

            MultiCopyEnableImageRequestParams enableParams = new MultiCopyEnableImageRequestParams();
            MultiCopyEnableImageRequestParams enableParams1 = new MultiCopyEnableImageRequestParams();
            MultiCopyDisableImageRequestParams disableParams = new MultiCopyDisableImageRequestParams();
            Set<String> WWNSetForTest = new HashSet<String>();
            WWNSetForTest.add(BourneRPTestCRRLUN1WWN);
            WWNSetForTest.add(BourneRPTestCRRLUN2WWN);
            enableParams.setVolumeWWNSet(WWNSetForTest);
            enableParams1.setVolumeWWNSet(WWNSetForTest);
            disableParams.setVolumeWWNSet(WWNSetForTest);
            disableParams.setEmName(Bookmarkname);
            rpClient.enableImageCopies(enableParams1);
            try {
                rpClient.enableImageCopies(enableParams);
                foundError = true;
            } catch (RecoverPointException e) {
                logger.info("Enable Most Recent Image, Enable Bookmark PASSED");
            }
            logger.info("Sleep 15 seconds before disable of image");
            Thread.sleep(15000);
            rpClient.disableImageCopies(disableParams);
        } catch (RecoverPointException e) {
            foundError = true;
            fail(e.getMessage());
        }
        if (!foundError) {
            logger.info("TestExceptionUseCases PASSED");
        } else {
            fail("TestExceptionUseCases FAILED");
        }
    }

    // @Test
    public void testUpdateProtectionJournal() throws InterruptedException {
        logger.info("Testing RecoverPoint Update CG Protection Journal");

        try {
            recreateCGSmall();
            RecoverPointVolumeProtectionInfo protectionTargetInfo = rpClient.getProtectionInfoForVolume(BourneRPTestCRRLUN1WWN);
            RecoverPointVolumeProtectionInfo protectionProdInfo = rpClient.getProtectionInfoForVolume(BourneRPTestProdLUN1WWN);
            // rpClient.addJournalToCopy(protectionTargetInfo, BourneRPTestJrnlLUN6WWN);
            // rpClient.addJournalToCopy(protectionProdInfo, BourneRPTestJrnlLUN3WWN);
            logger.info("Journals added.  Sleep 10 seconds before removing them");
            Thread.sleep(10000);
            rpClient.deleteJournalFromCopy(protectionTargetInfo, BourneRPTestJrnlLUN6WWN);
            rpClient.deleteJournalFromCopy(protectionProdInfo, BourneRPTestJrnlLUN3WWN);

            logger.info("TestUpdateProtectionJournal PASSED");
        } catch (RecoverPointException e) {
            fail(e.getMessage());
        }
    }

    // @Test
    public void testUpdateProtectionAddCopy() throws InterruptedException {
        logger.info("Testing RecoverPoint Update CG Protection Add Copy");

        try {
            recreateCGCDPOnly();
            logger.info("TestUpdateProtectionAddCopy PASSED");
        } catch (RecoverPointException e) {
            fail(e.getMessage());
        }
    }

    public void recreateCG() throws RecoverPointException {

        RecoverPointVolumeProtectionInfo protectionInfo = null;
        try {
            protectionInfo = rpClient.getProtectionInfoForVolume(BourneRPTestProdLUN1WWN);
        } catch (RecoverPointException e) {
            logger.info("Ignore getProtectionInfoForVolume error");
        }
        if (protectionInfo != null) {
            logger.info("Delete previous CG (if it exists)");
            rpClient.deleteCG(protectionInfo);
        }
        logger.info("Create the CG with two replication sets");
        CGRequestParams createCGParams = createCGParamsHelper(true, true, 2);
        rpClient.createCG(createCGParams, false, false);
    }

    @Test
    public void recreateCGSmall() throws RecoverPointException {

        RecoverPointVolumeProtectionInfo protectionInfo = null;
        try {
            protectionInfo = rpClient.getProtectionInfoForVolume(BourneRPTestProdLUN1WWN);
        } catch (RecoverPointException e) {
            logger.info("Ignore getProtectionInfoForVolume error");
        }
        if (protectionInfo != null) {
            logger.info("Delete previous CG (if it exists)");
            rpClient.deleteCG(protectionInfo);
        }
        logger.info("Create the CG with one replication set");

        // CGRequestParams createCGParams = CreateCGParamsHelper(true, true, 1);
        // rpClient.createCG(createCGParams);
    }

    public void recreateCGCDPOnly() throws RecoverPointException {

        RecoverPointVolumeProtectionInfo protectionInfo = null;
        try {
            protectionInfo = rpClient.getProtectionInfoForVolume(BourneRPTestProdLUN1WWN);
        } catch (RecoverPointException e) {
            logger.info("Ignore getProtectionInfoForVolume error");
        }
        if (protectionInfo != null) {
            logger.info("Delete previous CG (if it exists)");
            rpClient.deleteCG(protectionInfo);
        }
        logger.info("Create the CG with one replication set");

        // CreateCGRequestParams createCGParams = CreateCGParamsHelper(true, false, 2);
        CGRequestParams createCGParams = createCGParamsHelper(true, false, 1);

        rpClient.createCG(createCGParams, false, false);
    }

    public void recreateCGAndBookmark() throws RecoverPointException {
        CreateBookmarkRequestParams params = new CreateBookmarkRequestParams();
        Bookmarkname = "BourneBookmark_";
        Random randomnumber = new Random();
        Bookmarkname += Math.abs(randomnumber.nextInt());
        params.setBookmark(Bookmarkname);
        Set<String> WWNSetToBookmark = new HashSet<String>();
        WWNSetToBookmark.add(BourneRPTestCRRLUN1WWN);
        WWNSetToBookmark.add(BourneRPTestCRRLUN2WWN);
        params.setVolumeWWNSet(WWNSetToBookmark);
        recreateCG();
        rpClient.createBookmarks(params);
    }

    public CGRequestParams createCGParamsHelper(boolean createCDP, boolean createCRR, int numRSets) {

        CGRequestParams params = new CGRequestParams();
        params.setJunitTest(true);

        if (createCDP && createCRR) {
            params.setCgName("BourneRPTestCDPAndCRR");
            logger.info("Create CG for CDP and CRR: BourneRPTestCDPAndCRR");
        } else if (createCDP) {
            params.setCgName("BourneRPTestCDPOnly");
            logger.info("Create CG for CDP: BourneRPTestCDPOnly");
        } else {
            params.setCgName("BourneRPTestCRROnly");
            logger.info("Create CG for CRR: BourneRPTestCRROnly");
        }
        List<CreateRSetParams> rsetParamList = new LinkedList<CreateRSetParams>();

        // Production copy (VNX: 10.247.160.30)
        CreateVolumeParams copyprodVolumeParams = new CreateVolumeParams();
        copyprodVolumeParams.setWwn(BourneRPTestProdLUN1WWN);
        copyprodVolumeParams.setInternalSiteName(site1InternalSiteName);
        copyprodVolumeParams.setProduction(true);
        List<CreateVolumeParams> rsetVolumeList = new ArrayList<CreateVolumeParams>();
        rsetVolumeList.add(copyprodVolumeParams);

        if (createCDP) {
            CreateVolumeParams copylocalVolumeParams = new CreateVolumeParams();
            copylocalVolumeParams.setWwn(BourneRPTestCDPLUN1WWN);
            copylocalVolumeParams.setInternalSiteName(site1InternalSiteName);
            copylocalVolumeParams.setProduction(false);
            rsetVolumeList.add(copylocalVolumeParams);
        }

        if (createCRR) {
            CreateVolumeParams copyremoteVolumeParams = new CreateVolumeParams();
            copyremoteVolumeParams.setWwn(BourneRPTestCRRLUN1WWN);
            copyremoteVolumeParams.setInternalSiteName(site2InternalSiteName);
            copyremoteVolumeParams.setProduction(false);
            rsetVolumeList.add(copyremoteVolumeParams);
        }

        CreateRSetParams rset = new CreateRSetParams();

        rset.setName("RSet1");
        rset.setVolumes(rsetVolumeList);
        rsetParamList.add(rset);

        // Replication set 2
        if (numRSets > 1) {
            CreateVolumeParams r2copyprodVolumeParams = new CreateVolumeParams();
            r2copyprodVolumeParams.setWwn(BourneRPTestProdLUN2WWN);
            r2copyprodVolumeParams.setInternalSiteName(site1InternalSiteName);
            r2copyprodVolumeParams.setProduction(true);

            rsetVolumeList = new ArrayList<CreateVolumeParams>();
            rsetVolumeList.add(r2copyprodVolumeParams);

            if (createCDP) {
                CreateVolumeParams r2copylocalVolumeParams = new CreateVolumeParams();
                r2copylocalVolumeParams = new CreateVolumeParams();
                r2copylocalVolumeParams.setWwn(BourneRPTestCDPLUN2WWN);
                r2copylocalVolumeParams.setInternalSiteName(site1InternalSiteName);
                r2copylocalVolumeParams.setProduction(false);
                rsetVolumeList.add(r2copylocalVolumeParams);
            }

            if (createCRR) {
                CreateVolumeParams r2copyremoteVolumeParams = new CreateVolumeParams();
                r2copyremoteVolumeParams = new CreateVolumeParams();
                r2copyremoteVolumeParams.setWwn(BourneRPTestCRRLUN2WWN);
                r2copyremoteVolumeParams.setInternalSiteName(site2InternalSiteName);
                r2copyremoteVolumeParams.setProduction(false);
                rsetVolumeList.add(r2copyremoteVolumeParams);
            }

            rset = new CreateRSetParams();

            rset.setName("RSet2");
            rset.setVolumes(rsetVolumeList);
            rsetParamList.add(rset);

        }
        params.setRsets(rsetParamList);

        CreateCopyParams prodCopyParams = new CreateCopyParams();
        CreateCopyParams localCopyParams = new CreateCopyParams();
        CreateCopyParams remoteCopyParams = new CreateCopyParams();

        CreateVolumeParams prodJournalParams = new CreateVolumeParams();
        prodJournalParams.setWwn(BourneRPTestJrnlLUN1WWN);
        prodJournalParams.setInternalSiteName(site1InternalSiteName);
        prodJournalParams.setProduction(true);  // necessary for now
        List<CreateVolumeParams> prodJournalVolumeList = new ArrayList<CreateVolumeParams>();
        prodJournalVolumeList.add(prodJournalParams);
        if (isSymmDevices) {
            // extra jrnl for symm
            CreateVolumeParams prodJournalParams2 = new CreateVolumeParams();
            prodJournalParams2.setWwn(BourneRPTestJrnlLUN3WWN);
            prodJournalParams2.setInternalSiteName(site1InternalSiteName);
            prodJournalParams2.setProduction(true);
            prodJournalVolumeList.add(prodJournalParams2);
        }

        prodCopyParams.setName("production");
        prodCopyParams.setJournals(prodJournalVolumeList);
        List<CreateCopyParams> copyList = new ArrayList<CreateCopyParams>();
        copyList.add(prodCopyParams);

        if (createCDP) {
            CreateVolumeParams localJournalParams = new CreateVolumeParams();
            localJournalParams.setWwn(BourneRPTestJrnlLUN2WWN);
            localJournalParams.setInternalSiteName(site1InternalSiteName);
            localJournalParams.setProduction(false);
            List<CreateVolumeParams> localJournalVolumeList = new ArrayList<CreateVolumeParams>();
            localJournalVolumeList.add(localJournalParams);
            if (isSymmDevices) {
                // extra jrnl for symm
                CreateVolumeParams localJournalParams2 = new CreateVolumeParams();
                localJournalParams2.setWwn(BourneRPTestJrnlLUN4WWN);
                localJournalParams2.setInternalSiteName(site1InternalSiteName);
                localJournalParams2.setProduction(false);
                localJournalVolumeList.add(localJournalParams2);
            }
            //
            localCopyParams.setName("local");
            localCopyParams.setJournals(localJournalVolumeList);
            copyList.add(localCopyParams);
        }

        if (createCRR) {
            CreateVolumeParams remoteJournalParams = new CreateVolumeParams();
            remoteJournalParams.setWwn(BourneRPTestJrnlLUN5WWN);
            remoteJournalParams.setInternalSiteName(site2InternalSiteName);
            remoteJournalParams.setProduction(false);
            List<CreateVolumeParams> remoteJournalVolumeList = new ArrayList<CreateVolumeParams>();
            remoteJournalVolumeList.add(remoteJournalParams);
            if (isSymmDevices) {
                // extra jrnl for symm
                CreateVolumeParams remoteJournalParams2 = new CreateVolumeParams();
                remoteJournalParams2.setWwn(BourneRPTestJrnlLUN6WWN);
                remoteJournalParams2.setInternalSiteName(site2InternalSiteName);
                remoteJournalParams2.setProduction(false);
                remoteJournalVolumeList.add(remoteJournalParams2);
            }

            remoteCopyParams.setName("remote");
            remoteCopyParams.setJournals(remoteJournalVolumeList);
            copyList.add(remoteCopyParams);
        }

        params.setCopies(copyList);

        logger.info(params.toString());
        return params;
    }

}
