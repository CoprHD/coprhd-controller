/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.dbcli.wrapper;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlElement;
import com.emc.storageos.db.client.model.StringSet;

@XmlRootElement(name = "wrapper")
public class StringSetWrapper extends Wrapper<StringSet> {

    private StringSet stringSet = new StringSet();

    @XmlElement(name = "stringSet")
    public StringSet getValue() {
        return stringSet;
    }

    public void setValue(StringSet stringSet) {
        this.stringSet = stringSet;
    }
}
