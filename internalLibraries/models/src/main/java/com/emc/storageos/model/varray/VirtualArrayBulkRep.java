/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
     * @valid none
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
