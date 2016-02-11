/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.dbcli.wrapper;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlElement;

import com.emc.storageos.db.client.model.StringSet;

@XmlRootElement(name = "wrapper")
@XmlAccessorType(XmlAccessType.FIELD)
public class StringSetWrapper extends Wrapper<StringSet> {

    @XmlElement(name = "stringSet")
    private StringSet value = new StringSet();

    public StringSet getValue() {
        return value;
    }

    public void setValue(StringSet stringSet) {
        value = stringSet;
    }
}
