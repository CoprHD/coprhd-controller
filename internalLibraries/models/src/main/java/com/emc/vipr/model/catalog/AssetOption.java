/*
 * Copyright (c) 2015 EMC Corporation
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

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((key == null) ? 0 : key.hashCode());
        result = prime * result + ((value == null) ? 0 : value.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        AssetOption other = (AssetOption) obj;
        if (key == null) {
            if (other.key != null)
                return false;
        } else if (!key.equals(other.key))
            return false;
        if (value == null) {
            if (other.value != null)
                return false;
        } else if (!value.equals(other.value))
            return false;
        return true;
    }
}
