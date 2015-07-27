/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

/**
 * Class extends AbstractChangeTrackingSetMap for String
 */
public class StringSetMap extends AbstractChangeTrackingSetMap<String> {
    /**
     * Default constructor
     */
    public StringSetMap() { }

    @Override
    public AbstractChangeTrackingSet<String> createSetInstance(){
        return new StringSet();
    }

    @Override
    public StringSet get(String key) {
        return (StringSet) (getValueNoCreate(key));
    }
}
