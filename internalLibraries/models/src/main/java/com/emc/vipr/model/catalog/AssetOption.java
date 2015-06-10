/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.net.URI;

public class AssetOption {

    public String key;
    public String value;

    public AssetOption() {
    }

    public AssetOption(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public AssetOption(URI key, String value) {
        this.key = key.toString();
        this.value = value;
    }

    @Override
    public String toString() {
        return String.format("%s %s", key, value);
    }
}
