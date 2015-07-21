/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.dbcli.wrapper;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.dbcli.adapter.StringSetMapAdapter;

@XmlRootElement(name="wrapper")
public class StringSetMapWrapper extends Wrapper<StringSetMap>{

    private StringSetMap stringSetMap = new StringSetMap();
 
    @XmlElement(name="stringSetMap")
    @XmlJavaTypeAdapter(StringSetMapAdapter.class)
    public StringSetMap getValue() {
        return stringSetMap;
    }

    public void setValue(StringSetMap stringSetMap) {
        this.stringSetMap = stringSetMap;
    }


}
