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
