/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class Option {
    
    private String key;
    private String value;

    public Option() {
    }

    public Option(String key, String value) {
        super();
        this.key = key;
        this.value = value;
    }

    @Override
    public String toString() {
        return String.format("%s %s", key, value);
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
