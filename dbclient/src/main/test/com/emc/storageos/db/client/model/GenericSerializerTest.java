/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;

/**
 * Tests the GenericSerializer class
 */
public class GenericSerializerTest {
    private static Logger _log = LoggerFactory.getLogger(GenericSerializerTest.class);
    private GenericSerializer serializer = new GenericSerializer();

    @Test
    public void testSerializeDeserialize() {
        // only longs
        long value1 = Long.MIN_VALUE;
        long value2 = Long.MAX_VALUE;
        byte[] value1_enc = serializer.encodeVariantLong(value1);
        byte[] value2_enc = serializer.encodeVariantLong(value2);
        long value1_dec = serializer.decodeVariantLong(value1_enc);
        Assert.assertEquals(value1, value1_dec);
        long value2_dec = serializer.decodeVariantLong(value2_enc);
        Assert.assertEquals(value2, value2_dec);

        // try full event obj
        String testStr = "{ 'acknowledged_time': 0,\n" +
                "                    'coalesce_instanceid': None,\n" +
                "                    'devid': 0,\n" +
                "                    'end': 0,\n" +
                "                    'event_id': 500010005,\n" +
                "                    'extreme_severity': 2,\n" +
                "                    'extreme_value': 0.0,\n" +
                "                    'instance_id': '1.9',\n" +
                "                    'is_coalescing': 0,\n" +
                "                    'last_save': 1331219074,\n" +
                "                    'latest_time': 1331219069,\n" +
                "                    'message': 'SmartQuotas Report failed: Failed to process report schedule',\n" +
                "                    'severity': 2,\n" +
                "                    'specifier': { 'devid': 0, 'lnn': 0, 'val': 0.0},\n" +
                "                    'start': 1331219069,\n" +
                "                    'update_count': None,\n" +
                "                    'value': 0.0}";
        URI uri = URIUtil.createId(AuthnProvider.class);
        long time = System.currentTimeMillis();
        String id = "eventid12345678";
        Event e = new Event();
        e.setTimeInMillis(time);
        e.setResourceId(uri);
        e.setEventId(id);
        e.setExtensions(testStr);
        byte[] encoded = serializer.toByteArray(Event.class, e);
        Assert.assertTrue(encoded.length > 0);
        Event e1 = serializer.fromByteArray(Event.class, encoded);
        Assert.assertTrue(e1 != null);
        Assert.assertEquals(e.getTimeInMillis(), e1.getTimeInMillis());
        Assert.assertEquals(e.getResourceId(), e1.getResourceId());
        Assert.assertEquals(e.getEventId(), e1.getEventId());
        Assert.assertEquals(e.getExtensions(), e1.getExtensions());
        byte[] encoded2 = serializer.toByteArray(Event.class, e1);
        Assert.assertEquals(encoded.length, encoded2.length);
    }
}
