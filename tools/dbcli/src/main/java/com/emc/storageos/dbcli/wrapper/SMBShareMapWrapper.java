/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.dbcli.wrapper;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import com.emc.storageos.db.client.model.SMBShareMap;
import com.emc.storageos.dbcli.adapter.SMBShareMapAdapter;

@XmlRootElement(name = "wrapper")
public class SMBShareMapWrapper extends Wrapper<SMBShareMap> {

    private SMBShareMap sMBShareMap = new SMBShareMap();

    @XmlJavaTypeAdapter(SMBShareMapAdapter.class)
    @XmlElement(name = "sMBShareMap")
    public SMBShareMap getValue() {
        return sMBShareMap;
    }

    public void setValue(SMBShareMap sMBShareMap) {
        this.sMBShareMap = sMBShareMap;
    }

}
