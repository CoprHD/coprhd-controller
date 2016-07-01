/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client;

import java.net.URI;
import java.util.UUID;

import com.datastax.driver.core.utils.UUIDs;
import com.emc.storageos.db.client.constraint.QueryResultList;

public class TimestampedURIQueryResult extends QueryResultList<TimestampedURIQueryResult.TimestampedURI> {

    /**
     * A URI from an Index with a timestamp indicating when it was added
     */
    public class TimestampedURI {
        private final URI uri;
        private final String name;
        private final Long timestamp;

        public TimestampedURI(URI uri, String name, long timestamp) {
            this.uri = uri;
            this.name = name;
            this.timestamp = timestamp;
        }

        public URI getUri() {
            return uri;
        }

        public String getName() {
            return name;
        }

        public Long getTimestamp() {
            return timestamp;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            TimestampedURI that = (TimestampedURI) o;

            if (uri != null ? !uri.equals(that.uri) : that.uri != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            return uri != null ? uri.hashCode() : 0;
        }
    }

    @Override
    public TimestampedURI createQueryHit(URI uri) {
        return new TimestampedURI(uri, "", 0);
    }

    @Override
    public TimestampedURI createQueryHit(URI uri, String name, UUID timestamp) {
        return new TimestampedURI(uri, name, UUIDs.unixTimestamp(timestamp) * 1000);
    }
}
