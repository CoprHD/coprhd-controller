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

import com.emc.storageos.db.client.model.SMBShareMap;
import com.emc.storageos.dbcli.adapter.SMBShareMapAdapter;

@XmlRootElement(name = "wrapper")
@XmlAccessorType(XmlAccessType.FIELD)
public class SMBShareMapWrapper extends Wrapper<SMBShareMap> {

    @XmlJavaTypeAdapter(SMBShareMapAdapter.class)
    @XmlElement(name = "sMBShareMap")
    private SMBShareMap value = new SMBShareMap();

    public SMBShareMap getValue() {
        return value;
    }

    public void setValue(SMBShareMap sMBShareMap) {
        value = sMBShareMap;
    }

}
