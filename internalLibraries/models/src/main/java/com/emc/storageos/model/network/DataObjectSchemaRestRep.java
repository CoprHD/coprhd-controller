/*
 * Copyright (c) 2018 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.network;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "data_object_schema")
public class DataObjectSchemaRestRep {

    private String name;
    private List<RecordRestRep> record;

    @XmlAttribute(name = "name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XmlElement(name = "record")
    public List<RecordRestRep> getRecord() {

        if (record == null) {
            record = new ArrayList<RecordRestRep>();
        }
        return record;
    }

    public void setRecord(List<RecordRestRep> record) {
        this.record = record;
    }

}
