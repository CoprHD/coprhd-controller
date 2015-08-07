/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import org.codehaus.jackson.annotate.JsonProperty;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.net.URI;

/**
 * Specifies the parameters to change the virtual array for a volume.
 */
@Deprecated
@XmlRootElement(name = "volume_varray_change")
public class VirtualArrayChangeParam {

    private URI virtualArray;

    public VirtualArrayChangeParam() {
    }

    public VirtualArrayChangeParam(URI virtualArray) {
        this.virtualArray = virtualArray;
    }

    // The new virtual array.
    @XmlElement(required = true, name = "varray")
    @JsonProperty("varray")
    public URI getVirtualArray() {
        return virtualArray;
    }

    public void setVirtualArray(URI virtualArray) {
        this.virtualArray = virtualArray;
    }
}
