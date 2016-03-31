/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.dbcli.wrapper;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.dbcli.adapter.StringSetMapAdapter;

@XmlRootElement(name = "wrapper")
@XmlAccessorType(XmlAccessType.FIELD)
public class StringSetMapWrapper extends Wrapper<StringSetMap> {

    @XmlElement(name = "stringSetMap")
    @XmlJavaTypeAdapter(StringSetMapAdapter.class)
    private StringSetMap value = new StringSetMap();

    public StringSetMap getValue() {
        return value;
    }

    public void setValue(StringSetMap stringSetMap) {
        value = stringSetMap;
    }

}
