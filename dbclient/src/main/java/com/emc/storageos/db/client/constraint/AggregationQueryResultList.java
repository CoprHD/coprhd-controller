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

package com.emc.storageos.db.client.constraint;

import java.net.URI;
import java.util.UUID;

/**
 * URI specialization for convenience
 */
public class AggregationQueryResultList extends QueryResultList<AggregationQueryResultList.AggregatedEntry> {

    public static class AggregatedEntry {
        private URI id;
        private Object value;

        public AggregatedEntry(URI id, Object value) {
            this.id = id;
            this.value = value;
        }

        public Object getValue() {
            return value;
        }

        public void setValue(Object value) {
            this.value = value;
        }

        public URI getId() {
            return id;
        }

        public void setId(URI id) {
            this.id = id;
        }
    }

    @Override
    public AggregatedEntry createQueryHit(URI uri) {
        return new AggregatedEntry(uri, null);
    }

    @Override
    public AggregatedEntry createQueryHit(URI uri, String name, UUID timestamp) {
        return new AggregatedEntry(uri, name);
    }

    // default implementation
    public AggregatedEntry createQueryHit(URI uri, Object value) {
        return new AggregatedEntry(uri, value);
    }

}
