/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client;

import java.util.List;
import java.util.ArrayList;
import java.net.URI;
import java.net.URISyntaxException;

import org.junit.Assert;
import org.junit.Test;

import com.emc.storageos.db.client.impl.BulkDataObjPersistIterator;
import com.emc.storageos.db.client.impl.BulkDataObjQueryResultIterator;

import com.emc.storageos.db.exceptions.DatabaseException;

public class BulkDataIteratorTest {

    @Test
    public void testBulkDataObjPersistIterator() {
        List<String> data = new ArrayList();

        for (int i = 0; i < 200; i++) {
            data.add("string" + i);
        }

        BulkDataObjPersistIterator<String> bulkPersistIterator =
                new BulkDataObjPersistIterator(data.iterator()) {
                    @Override
                    protected void run() throws DatabaseException {
                    }
                };

        int count = 0;
        while (bulkPersistIterator.hasNext()) {
            count++;
            List<String> bulk = bulkPersistIterator.next();
            Assert.assertEquals(100, bulk.size());
        }

        Assert.assertEquals(2, count);
    }

    @Test
    public void testBulkDataObjQueryResultIterator() {
        List<URI> ids = new ArrayList();

        try {
            for (int i = 0; i < 200; i++) {
                ids.add(new URI("file://tmp/a" + i));
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        BulkDataObjQueryResultIterator<String> bulkQueryIterator =
                new BulkDataObjQueryResultIterator(ids.iterator()) {
                    @Override
                    protected void run() throws DatabaseException {
                        currentIt = null;
                        getNextBatch();
                        while (!nextBatch.isEmpty()) {
                            List<String> data = prepareQueryData();
                            currentIt = data.iterator();
                            break;
                        }
                    }
                };

        int count = 0;
        while (bulkQueryIterator.hasNext()) {
            count++;
            bulkQueryIterator.next();
        }

        Assert.assertEquals(200, count);
    }

    private List<String> prepareQueryData() {
        // prepare bulk data
        List<String> data = new ArrayList();
        for (int i = 0; i < 100; i++) {
            data.add("string" + i);
        }

        return data;
    }
}
