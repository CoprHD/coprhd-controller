/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.constraint;

import java.net.URI;
import java.util.UUID;

/**
 * URI specialization for convenience
 */
public class URIQueryResultList extends QueryResultList<URI> {
    @Override
    public URI createQueryHit(URI uri) {
        return uri;
    }

    @Override
    public URI createQueryHit(URI uri, String name, UUID timestamp) {
        return uri;
    }
}
