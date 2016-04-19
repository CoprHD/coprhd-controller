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

import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.dbcli.adapter.StringMapAdapter;

@XmlRootElement(name = "wrapper")
@XmlAccessorType(XmlAccessType.FIELD)
public class StringMapWrapper extends Wrapper<StringMap> {

    @XmlJavaTypeAdapter(StringMapAdapter.class)
    @XmlElement(name = "stringMap")
    private StringMap value = new StringMap();

    public StringMap getValue() {
        return value;
    }

    public void setValue(StringMap stringMap) {
        value = stringMap;
    }

}
