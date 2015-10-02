/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.varray;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.valid.Length;

/**
 * Parameter for virtual array creation
 */
@XmlRootElement(name = "varray_create")
public class VirtualArrayCreateParam extends VirtualArrayParam {

    private String label;

    public VirtualArrayCreateParam() {
    }

    public VirtualArrayCreateParam(String label) {
        this.label = label;
    }

    /**
     * The name of the virtual array.
     * 
     */
    @XmlElement(required = true, name = "name")
    @Length(min = 2, max = 128)
    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

}
