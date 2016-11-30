package com.emc.storageos.db.client.impl;

import static org.junit.Assert.assertEquals;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.Before;
import org.junit.Test;

public class DbConsistencyCheckerHelperTest {
    private static final long TIME_STAMP_3_6 = 1480409489868000L;
    private static final long TIME_STAMP_3_5 = 1480318877880000L;
    private static final long TIME_STAMP_3_0 = 1480318251194000L;
    private static final long TIME_STAMP_2_4_1 = 1480317537505000L;
    private DbConsistencyCheckerHelper helper;
    
    @Before
    public void setUp() throws Exception {
        Map<Long, String> timeStampVersionMap = new TreeMap<Long, String>();
        timeStampVersionMap.put(TIME_STAMP_2_4_1, "2.4.1");
        timeStampVersionMap.put(TIME_STAMP_3_0, "3.0");
        timeStampVersionMap.put(TIME_STAMP_3_5, "3.5");
        timeStampVersionMap.put(TIME_STAMP_3_6, "3.6");
        
        helper = new DbConsistencyCheckerHelper(null) {

            @Override
            protected Map<Long, String> querySchemaVersions() {
                return timeStampVersionMap;
            }
            
        };
    }
    
    @Test
    public void testFindDataCreatedInWhichDBVersion() {
        assertEquals("Unknown",
                helper.findDataCreatedInWhichDBVersion(ThreadLocalRandom.current().nextLong(Long.MIN_VALUE, TIME_STAMP_2_4_1)));
        assertEquals("2.4.1",
                helper.findDataCreatedInWhichDBVersion(ThreadLocalRandom.current().nextLong(TIME_STAMP_2_4_1, TIME_STAMP_3_0)));
        assertEquals("3.0",
                helper.findDataCreatedInWhichDBVersion(ThreadLocalRandom.current().nextLong(TIME_STAMP_3_0, TIME_STAMP_3_5)));
        assertEquals("3.5",
                helper.findDataCreatedInWhichDBVersion(ThreadLocalRandom.current().nextLong(TIME_STAMP_3_5, TIME_STAMP_3_6)));
        assertEquals("3.6",
                helper.findDataCreatedInWhichDBVersion(ThreadLocalRandom.current().nextLong(TIME_STAMP_3_6, Long.MAX_VALUE)));
    }
}
