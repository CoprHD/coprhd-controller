/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.systemservices.impl.logsvc.performance;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import com.emc.storageos.systemservices.impl.logsvc.LogSvcPropertiesLoader;
import com.emc.storageos.systemservices.impl.logsvc.LogNetworkWriter;
import com.emc.vipr.model.sys.logging.LogRequest;

public class LogNetworkWriterPerfTest {
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
    public void testPerformance() throws Exception{
        List<String> svcs = new ArrayList<String>() {{
            add("controllersvc");
            add("coordinatorsvc");
            add("apisvc");
        }};
        LogRequest req = new LogRequest.Builder().baseNames(svcs).build();
        LogNetworkWriter writer = new LogNetworkWriter(req, propertiesLoader);
        ByteCountingOutputStream outputStream = new ByteCountingOutputStream();
        long startTime = System.nanoTime();
        writer.write(outputStream);
        long endTime = System.nanoTime();
        long totalSize = outputStream.getSize() / (1024L * 1024L);
        double elapsedTime = (double) (endTime - startTime) / 1000000000.0;
        System.out.println("Total read " + totalSize + " MB;" + " Used " + elapsedTime +
                " sec; Average " + (totalSize / elapsedTime) + " MB/sec.");
    }

    private static class ByteCountingOutputStream extends OutputStream {
        private long size;

        @Override
        public void write(int b) throws IOException {
            size++;
        }

        @Override
        public void write(byte b[], int off, int len) throws IOException {
            size += len;
        }

        public long getSize() {
            return size;
        }
    }
}
