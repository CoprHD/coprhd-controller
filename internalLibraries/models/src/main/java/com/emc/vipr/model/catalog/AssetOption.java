/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.net.URI;

public class AssetOption {

    public String key;
    public String value;
    public boolean disabled;

    public AssetOption() {
    }
    
    public AssetOption(String key, String value) {
        this(key, value, false);
    }

    public AssetOption(String key, String value, boolean disabled) {
        this.key = key;
        this.value = value;
        this.disabled = disabled;
    }

    public AssetOption(URI key, String value) {
        this(key, value, false);
    }
    
    public AssetOption(URI key, String value, boolean disabled) {
        this.key = key.toString();
        this.value = value;
        this.disabled = disabled;
    }

    @Override
    public String toString() {
        return String.format("%s %s", key, value);
    }
}
