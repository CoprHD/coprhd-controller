/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model;

import javax.xml.bind.annotation.XmlElement;

public class EndpointAliasRestRep extends StringHashMapEntry {

    private String alias;

    public EndpointAliasRestRep() {
    }

    public EndpointAliasRestRep(String name, String value) {
        super(name, value);
    }

    public EndpointAliasRestRep(String name, String value, String alias) {
        this(name, value);
        setAlias(alias);
    }

    @XmlElement(name = "alias")
    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }
}