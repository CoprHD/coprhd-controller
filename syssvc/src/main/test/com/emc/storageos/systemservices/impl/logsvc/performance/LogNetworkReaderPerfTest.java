/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.logsvc.performance;

import java.io.BufferedOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.systemservices.impl.logsvc.LogMessage;
import com.emc.storageos.systemservices.impl.logsvc.stream.LogNetworkReader;
import com.emc.storageos.systemservices.impl.logsvc.LogNetworkWriter;
import com.emc.storageos.systemservices.impl.logsvc.LogStatusInfo;
import com.emc.storageos.systemservices.impl.logsvc.LogSvcPropertiesLoader;
import com.emc.vipr.model.sys.logging.LogRequest;

public class LogNetworkReaderPerfTest {
    private static final Logger log = LoggerFactory.getLogger(LogNetworkReaderPerfTest.class);

    private static volatile LogSvcPropertiesLoader propertiesLoader;

    @BeforeClass
    public static void setup() {
        propertiesLoader = new LogSvcPropertiesLoader() {
            public List<String> getLogFilePaths() {
                return Arrays.asList("/opt/storageos/logs/testData/*.log");
            }

            public List<String> getExcludedLogFilePaths() {
                return Arrays.asList("/opt/storageos/logs/*native.log");
            }
        };
    }

    @Test
    public void testPerformance() throws Exception {
        List<String> svcs = new ArrayList<String>() {
            {
                add("controllersvc");
                add("coordinatorsvc");
                add("apisvc");
            }
        };
        int bufSize = 1024 * 64;
        LogRequest req = new LogRequest.Builder().baseNames(svcs).build();
        final LogNetworkWriter writer = new LogNetworkWriter(req, propertiesLoader);
        try (final PipedOutputStream out = new PipedOutputStream();
             final BufferedOutputStream outputStream = new BufferedOutputStream(out, bufSize);
             final PipedInputStream inputStream = new PipedInputStream(out)) {
            LogNetworkReader reader = new LogNetworkReader("vipr1","vipr1", inputStream,
                    new LogStatusInfo());

            long totalSize = 0;
            long startTime = System.nanoTime();
            new Thread(
                    new Runnable() {
                        public void run() {
                            try {
                                writer.write(outputStream);
                            } catch (Exception e) {
                                e.printStackTrace(); // NOSONAR
                                                     // ("squid:S1148 suppress sonar warning on printStackTrace. It's a test case and exceptions are meant to be printed to stdout/stderr")
                            }
                        }
                    }).start();
            LogMessage log = null;
            while ((log = reader.readNextLogMessage()) != null) {
                totalSize += log.toStringOriginalFormat().getBytes().length;
            }
            long endTime = System.nanoTime();

            totalSize /= (1024L * 1024L);
            double elapsedTime = (double) (endTime - startTime) / 1000000000.0;
            System.out.println("Total read " + totalSize + " MB;" + " Used " + elapsedTime +
                    " sec; Average " + (totalSize / elapsedTime) + " MB/sec.");
        }
    }
}
