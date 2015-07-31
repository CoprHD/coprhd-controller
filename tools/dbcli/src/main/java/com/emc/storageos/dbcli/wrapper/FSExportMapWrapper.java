/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.dbcli.wrapper;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import com.emc.storageos.db.client.model.FSExportMap;
import com.emc.storageos.dbcli.adapter.FSExportMapAdapter;

@XmlRootElement(name = "wrapper")
public class FSExportMapWrapper extends Wrapper<FSExportMap> {

    private FSExportMap fSExportMap = new FSExportMap();

    @XmlJavaTypeAdapter(FSExportMapAdapter.class)
    @XmlElement(name = "fSExportMap")
    public FSExportMap getValue() {
        return fSExportMap;
    }

    public void setValue(FSExportMap fSExportMap) {
        this.fSExportMap = fSExportMap;
    }

}
