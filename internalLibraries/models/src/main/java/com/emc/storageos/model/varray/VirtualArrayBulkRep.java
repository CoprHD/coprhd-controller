/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.varray;

import com.emc.storageos.model.BulkRestRep;
import org.codehaus.jackson.annotate.JsonProperty;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "bulk_varrays")
public class VirtualArrayBulkRep extends BulkRestRep {
    private List<VirtualArrayRestRep> varrays;

    /**
     * A list of virtual arrays.
     * 
     * 
     * @return A list of virtual arrays.
     */
    @XmlElement(name = "varray")
    @JsonProperty("varray")
    public List<VirtualArrayRestRep> getVirtualArrays() {
        if (varrays == null) {
            varrays = new ArrayList<VirtualArrayRestRep>();
        }
        return varrays;
    }

    public void setVirtualArrays(List<VirtualArrayRestRep> varrays) {
        this.varrays = varrays;
    }

    public VirtualArrayBulkRep() {
    }

    public VirtualArrayBulkRep(List<VirtualArrayRestRep> varrays) {
        this.varrays = varrays;
    }
}
