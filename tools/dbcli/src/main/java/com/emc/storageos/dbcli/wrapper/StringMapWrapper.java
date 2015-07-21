/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.dbcli.wrapper;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.dbcli.adapter.StringMapAdapter;

@XmlRootElement(name="wrapper")
public class StringMapWrapper extends Wrapper<StringMap>{

    private StringMap stringMap = new StringMap();
 
    @XmlJavaTypeAdapter(StringMapAdapter.class)
    @XmlElement(name="stringMap")
    public StringMap getValue() {
        return stringMap;
    }
 
    public void setValue(StringMap stringMap) {
        this.stringMap = stringMap;
    }

}
