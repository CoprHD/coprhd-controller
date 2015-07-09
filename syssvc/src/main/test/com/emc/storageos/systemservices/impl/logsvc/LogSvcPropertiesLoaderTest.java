/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.systemservices.impl.logsvc;

import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.junit.Assert;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * JUnit test class for {@link LogSvcPropertiesLoader}.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
public class LogSvcPropertiesLoaderTest {

    // Test constants.
    private static final String PROP_FILE_NAME = "logsvc.properties";
    private static final String LOG_FILE_PATHS_PROP = "logsvc.logFilePaths";
    private static final String NODE_COLLECTION_TO_PROP = "logsvc.nodeLogCollectionTimeout";
    private static final String FILE_COLLECTION_TO_PROP = "logsvc.fileLogCollectionTimeout";

    // The log service properties.
    private static Properties s_logsvcProps = new Properties();

    @Autowired
    private LogSvcPropertiesLoader _logSvcPropertiesLoader;

    /**
     * Loads the log service properties before executing any tests.
     */
    @BeforeClass
    public static void loadProperties() {
        try {
            URL url = ClassLoader.getSystemResource(PROP_FILE_NAME);
            s_logsvcProps.load(url.openStream());
        } catch (Exception e) {
            ;
        }
    }

    /**
     * Tests the getLogFilePaths method.
     */
    @Test
    public void testGetLogFilePaths() {
        Assert.assertNotNull(_logSvcPropertiesLoader);
        List<String> logFilePathsList = _logSvcPropertiesLoader.getLogFilePaths();
        StringBuilder pathsBuilder = new StringBuilder();
        Iterator<String> pathsIter = logFilePathsList.iterator();
        while (pathsIter.hasNext()) {
            if (pathsBuilder.length() != 0) {
                pathsBuilder.append(";");
            }
            pathsBuilder.append(pathsIter.next());
        }
        Assert.assertEquals(pathsBuilder.toString(),
            s_logsvcProps.getProperty(LOG_FILE_PATHS_PROP));
    }

    /**
     * Tests the getNodeLogCollectorTimeout method.
     */
    @Test
    public void testGetNodeLogCollectorTimeout() {
        Assert.assertNotNull(_logSvcPropertiesLoader);
        long nodeTimeout = _logSvcPropertiesLoader.getNodeLogCollectorTimeout();
        Assert.assertEquals(nodeTimeout,
            Long.parseLong(s_logsvcProps.getProperty(NODE_COLLECTION_TO_PROP)));
    }

    /**
     * Tests the getFileLogCollectorTimeout method.
     */
    @Test
    public void testGetFileLogCollectorTimeout() {
        Assert.assertNotNull(_logSvcPropertiesLoader);
        long logTimeout = _logSvcPropertiesLoader.getFileLogCollectorTimeout();
        Assert.assertEquals(logTimeout,
            Long.parseLong(s_logsvcProps.getProperty(FILE_COLLECTION_TO_PROP)));
    }

    /**
     * Tests the getNodeLogConnectionTimeout method.
     */
    @Test
    public void testGetNodeLogConnectionTimeout() {
        Assert.assertNotNull(_logSvcPropertiesLoader);
        long logTimeout = _logSvcPropertiesLoader.getNodeLogConnectionTimeout();
        Assert.assertEquals(logTimeout,
                Long.parseLong(s_logsvcProps.getProperty(FILE_COLLECTION_TO_PROP)));
    }
}
