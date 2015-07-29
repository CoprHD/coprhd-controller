/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.logsvc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.junit.Assert.assertTrue;

import com.emc.storageos.systemservices.impl.logsvc.merger.LogStreamMerger;
import com.emc.vipr.model.sys.logging.LogRequest;

public class LogStreamMergerTest {

    private static volatile LogSvcPropertiesLoader propertiesLoader;
    private static final Logger logger = LoggerFactory.getLogger(LogStreamMergerTest.class);

    @BeforeClass
    public static void setup() {
        propertiesLoader = new LogSvcPropertiesLoader() {
            public List<String> getLogFilePaths() {
                return Arrays.asList("/opt/storageos/logs/*.log");
            }

            public List<String> getExcludedLogFilePaths() {
                return Arrays.asList("/opt/storageos/logs/*native.log");
            }
        };
    }

    /**
     * Test if merge process can result correct content
     */
    @Test
    @Ignore
    public void testMergeSortCorrectness() throws Exception {
        List<String> svcs = new ArrayList<String>() {
            {
                add("controllersvc");
                add("coordinatorsvc");
            }
        };
        LogRequest req = new LogRequest.Builder().baseNames(svcs).build();
        LogStreamMerger merger = new LogStreamMerger(req, propertiesLoader);
        long prevTime = -1;
        while (true) {
            LogMessage log = merger.readNextMergedLogMessage();
            if (log == null) {
                break;
            }
            long time = log.getTime();
            assertTrue("Output of StreamMerger should be sorted.", prevTime <= time);
            prevTime = time;
        }
    }
}
