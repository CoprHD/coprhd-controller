/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.common;

import org.junit.Assert;
import org.junit.Test;

import com.emc.storageos.db.client.URIUtil;

public class UriUtilTest {
    
    @Test
    public void testParseVdcIdFromURI() {
        Assert.assertEquals("global", URIUtil.parseVdcIdFromURI("urn:storageos:VirtualPool:31a7ae6f-4f0d-4e2d-80b4-7aca7ec54fe9:global"));        
        Assert.assertEquals("vdc1", URIUtil.parseVdcIdFromURI("urn:storageos:VirtualPool:31a7ae6f-4f0d-4e2d-80b4-7aca7ec54fe9:vdc1"));
        Assert.assertNull(URIUtil.parseVdcIdFromURI("urn:storageos:VirtualPool:31a7ae6f-4f0d-4e2d-80b4-7aca7ec54fe9:"));
        Assert.assertNull(URIUtil.parseVdcIdFromURI("blah"));
        Assert.assertNull(URIUtil.parseVdcIdFromURI((String)null));
    }
}
