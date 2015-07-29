/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.varray;

import com.emc.storageos.model.NamedRelatedResourceRep;
import org.codehaus.jackson.annotate.JsonProperty;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Used for virtual array listing
 */
@XmlRootElement(name = "varrays")
public class VirtualArrayList {
    private List<NamedRelatedResourceRep> varrays;

    public VirtualArrayList() {
    }

    public VirtualArrayList(List<NamedRelatedResourceRep> varrays) {
        this.varrays = varrays;
    }

    /**
     * A virtual array.
     * 
     * @valid none.
     * 
     * @return A virtual array.
     */
    @XmlElement(name = "varray")
    @JsonProperty("varray")
    public List<NamedRelatedResourceRep> getVirtualArrays() {
        if (varrays == null) {
            varrays = new ArrayList<NamedRelatedResourceRep>();
        }
        return varrays;
    }

    public void setVirtualArrays(List<NamedRelatedResourceRep> varrays) {
        this.varrays = varrays;
    }
}
