/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.compute;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;

@XmlRootElement(name = "compute_images")
public class ComputeImageList {

    private List<NamedRelatedResourceRep> computeImages;

    public ComputeImageList() {
    }

    public ComputeImageList(List<NamedRelatedResourceRep> computeImages) {
        this.computeImages = computeImages;
    }

    /**
     * List of storage system URLs with name
     * 
     * @valid none
     */
    @XmlElement(name = "compute_image")
    public List<NamedRelatedResourceRep> getComputeImages() {
        if (computeImages == null) {
            computeImages = new ArrayList<NamedRelatedResourceRep>();
        }
        return computeImages;
    }

    public void setComputeImages(List<NamedRelatedResourceRep> computeImages) {
        this.computeImages = computeImages;
    }
}
