/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.varray;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.valid.Length;

/**
 * Parameter for virtual array update
 */
@XmlRootElement(name = "varray_update")
public class VirtualArrayUpdateParam extends VirtualArrayParam {

    private String label;

    public VirtualArrayUpdateParam() {
    }

    public VirtualArrayUpdateParam(String label) {
        super();
        this.label = label;
    }

    public VirtualArrayUpdateParam(Boolean autoSanZoning, String label) {
        super(autoSanZoning);
        this.label = label;
    }

    /**
     * The new name for the virtual array.
     * 
     * @valid none
     */
    @XmlElement(required = false, name = "name")
    @Length(min = 2, max = 128)
    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

}
