/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.dbcli.wrapper;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.dbcli.adapter.OpStatusMapAdapter;

@XmlRootElement(name="wrapper")
public class OpStatusMapWrapper extends Wrapper<OpStatusMap>{

    private OpStatusMap opStatusMap = new OpStatusMap();
 
    @XmlJavaTypeAdapter(OpStatusMapAdapter.class)
    @XmlElement(name="opStatusMap")
    public OpStatusMap getValue() {
        return opStatusMap;
    }
 
    public void setValue(OpStatusMap opStatusMap) {
        this.opStatusMap = opStatusMap;
    }

}
