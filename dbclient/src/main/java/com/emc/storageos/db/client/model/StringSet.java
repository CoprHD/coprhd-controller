/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.util.*;

/**
 * Set type supported by DB mapper
 */
public class StringSet extends AbstractChangeTrackingSet<String> {
    /**
     * Default constructor
     */
    public StringSet() {
    }

    /**
     * Constructs a new set with the same set as source
     * 
     * @param source
     */
    public StringSet(Collection<String> source) {
        super(source);
    }

    @Override
    public String valFromString(String value) {
        return value;
    }

    @Override
    public String valToString(String value) {
        return value;
    }
}
