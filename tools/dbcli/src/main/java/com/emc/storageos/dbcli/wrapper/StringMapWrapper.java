/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.dbcli.wrapper;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.dbcli.adapter.StringMapAdapter;

@XmlRootElement(name = "wrapper")
public class StringMapWrapper extends Wrapper<StringMap> {

    private StringMap stringMap = new StringMap();

    @XmlJavaTypeAdapter(StringMapAdapter.class)
    @XmlElement(name = "stringMap")
    public StringMap getValue() {
        return stringMap;
    }

    public void setValue(StringMap stringMap) {
        this.stringMap = stringMap;
    }

}
