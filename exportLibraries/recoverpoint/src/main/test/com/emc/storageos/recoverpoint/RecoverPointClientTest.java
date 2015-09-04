/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * This computer code is copyright 2013 EMC Corporation. All rights reserved.
 */
package com.emc.storageos.recoverpoint;

// Static import of EasyMock for ease of use
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Random;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.fapiclient.ws.ClusterUID;
import com.emc.fapiclient.ws.FunctionalAPIActionFailedException_Exception;
import com.emc.fapiclient.ws.FunctionalAPIImpl;
import com.emc.fapiclient.ws.FunctionalAPIInternalError_Exception;
import com.emc.storageos.recoverpoint.exceptions.RecoverPointException;
import com.emc.storageos.recoverpoint.impl.RecoverPointClient;
import com.emc.storageos.services.util.EnvConfig;

/**
 * JUnit Class for RecoverPointClient
 * 
 * @author hugheb2
 *
 */
public class RecoverPointClientTest {

    private static final String UNIT_TEST_CONFIG_FILE = "sanity";

    private static final String RP_SITE_TO_USE = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "recoverpoint.RecoverPointClientTest.RP_USERNAME");
    private static final String RP_USERNAME = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "recoverpoint.RP_USERNAME");
    private static final String RP_PASSWORD = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "recoverpoint.RP_PASSWORD");

    private static final String PRE_URI = "https://";
    private static final String POST_URI = ":7225/fapi/version4_1" + "?wsdl";

    private static final int LOCAL_SITE_ID = 1;

    private static FunctionalAPIImpl mockFunctionalAPIImpl;
    private static RecoverPointClient rpClient;
    private static Logger logger;
    private static String bookmarkName;

    @BeforeClass
    public static void setup() {
        bookmarkName = "BourneBookmark_";
        Random randomnumber = new Random();
        bookmarkName += Math.abs(randomnumber.nextInt());

        logger = LoggerFactory.getLogger(RecoverPointClientTest.class);
    }

    @Before
    public void setupClient() {
        URI endpoint = null;
        try {
            endpoint = new URI(PRE_URI + RP_SITE_TO_USE + POST_URI);
        } catch (URISyntaxException e) {
            logger.error(e.getMessage(), e);
        }

        mockFunctionalAPIImpl = createMock(FunctionalAPIImpl.class);
        rpClient = new RecoverPointClient(endpoint, RP_USERNAME, RP_PASSWORD);
        rpClient.setFunctionalAPI(mockFunctionalAPIImpl);
    }

    @Test
    public void testLogger() {
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
    public void testPing()
            throws FunctionalAPIActionFailedException_Exception, FunctionalAPIInternalError_Exception {

        logger.info("Testing RecoverPoint Service ping");
        int retVal = 0;
        logger.info("Testing good credentials");

        // ----- EasyMock Setup -----//
        ClusterUID localClusterUID = buildLocalClusterUID();

        expect(mockFunctionalAPIImpl.getLocalCluster()).andReturn(localClusterUID);

        // ----- EasyMock Start -----//
        replay(mockFunctionalAPIImpl);

        try {
            // ----- Main Test Method -----//
            retVal = rpClient.ping();
        } catch (RecoverPointException e) {
            fail(e.getMessage());
        }

        // ----- EasyMock Verify -----//
        verify(mockFunctionalAPIImpl);

        assertEquals(0, retVal);
    }

    /**
     * Builds a test local ClusterUID object.
     * 
     * @return
     */
    private ClusterUID buildLocalClusterUID() {
        ClusterUID localClusterUID = new ClusterUID();
        localClusterUID.setId(LOCAL_SITE_ID);

        return localClusterUID;
    }

}
