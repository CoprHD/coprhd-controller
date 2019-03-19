/*
 * Copyright (c) 2018 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.network;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "readOnlyField")
public class ReadOnlyFieldRestRep {

    private List<FieldRestRep> field;

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
