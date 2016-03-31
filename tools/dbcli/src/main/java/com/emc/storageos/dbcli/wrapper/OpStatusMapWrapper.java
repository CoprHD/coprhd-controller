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

import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.dbcli.adapter.OpStatusMapAdapter;

@XmlRootElement(name = "wrapper")
@XmlAccessorType(XmlAccessType.FIELD)
public class OpStatusMapWrapper extends Wrapper<OpStatusMap> {

    @XmlJavaTypeAdapter(OpStatusMapAdapter.class)
    @XmlElement(name = "opStatusMap")
    private OpStatusMap value = new OpStatusMap();

    public OpStatusMap getValue() {
        return value;
    }

    public void setValue(OpStatusMap opStatusMap) {
        value = opStatusMap;
    }

}
