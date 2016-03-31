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

import com.emc.storageos.db.client.model.FSExportMap;
import com.emc.storageos.dbcli.adapter.FSExportMapAdapter;

@XmlRootElement(name = "wrapper")
@XmlAccessorType(XmlAccessType.FIELD)
public class FSExportMapWrapper extends Wrapper<FSExportMap> {

    @XmlJavaTypeAdapter(FSExportMapAdapter.class)
    @XmlElement(name = "fSExportMap")
    private FSExportMap value = new FSExportMap();

    public FSExportMap getValue() {
        return value;
    }

    public void setValue(FSExportMap fSExportMap) {
        value = fSExportMap;
    }

}
