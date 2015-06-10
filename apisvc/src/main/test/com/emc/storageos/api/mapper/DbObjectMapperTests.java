/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper;

import com.emc.storageos.db.client.model.NamedURI;
import org.junit.Assert;
import org.junit.Test;

import java.net.URI;

public class DbObjectMapperTests {

    @Test
    public void testDifferentURIs() {
        try {
            NamedURI uri = new NamedURI(URI.create("urn:storageos:SysEvent:e384e930-b52f-48de-b483-f013c372cdbf:vdc1:"), "SysEventTest");
            DbObjectMapper.toNamedRelatedResource(uri);
        } catch(Exception e) {
            Assert.fail();
        }

        try {
            // Test that a bad resource, doesn't cause a failure
            NamedURI uri = new NamedURI(URI.create("urn:storageos:ObjectMapperTester:e384e930-b52f-48de-b483-f013c372cdbf:vdc1:"), "ObjectMapperTester");
            DbObjectMapper.toNamedRelatedResource(uri);
        } catch(Exception e) {
            Assert.fail(e.getMessage());
        }
    }
}
