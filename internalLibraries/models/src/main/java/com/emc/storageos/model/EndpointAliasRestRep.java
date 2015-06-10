/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.model;

import javax.xml.bind.annotation.XmlElement;

public class EndpointAliasRestRep extends StringHashMapEntry {

    private String alias;
    
    public EndpointAliasRestRep() {
    }
    
    public EndpointAliasRestRep(String name, String value) {
        super(name,value);
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