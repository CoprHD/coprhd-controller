/*
 * Copyright (c) 2018 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.network;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;

public class RecordRestRep {

    private String id;
    private ReadOnlyFieldRestRep readOnlyField;

    List<FieldRestRep> field;

    @XmlAttribute(name = "id")
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ReadOnlyFieldRestRep getReadOnlyField() {
        return readOnlyField;
    }

    public void setReadOnlyField(ReadOnlyFieldRestRep readOnlyField) {
        this.readOnlyField = readOnlyField;
    }


    public List<FieldRestRep> getField() {
        if (field == null) {
            field = new ArrayList<FieldRestRep>();
        }
        return field;
    }

    public void setField(List<FieldRestRep> field) {
        this.field = field;
    }

}
