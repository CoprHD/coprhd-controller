/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import org.codehaus.jackson.annotate.JsonProperty;

@XmlAccessorType(XmlAccessType.PROPERTY)
public class VirtualArrayRelatedResourceRep extends RelatedResourceRep {
    private RelatedResourceRep virtualArray;

    /**
     * Virtual array where this Block object exists.
     * @valid none
     */
    @XmlElement(name = "varray")
    @JsonProperty("varray")
    public RelatedResourceRep getVirtualArray() {
        return virtualArray;
    }

    public void setVirtualArray(RelatedResourceRep virtualArray) {
        this.virtualArray = virtualArray;
    }
    
}
