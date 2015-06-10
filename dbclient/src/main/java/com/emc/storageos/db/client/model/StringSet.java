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
