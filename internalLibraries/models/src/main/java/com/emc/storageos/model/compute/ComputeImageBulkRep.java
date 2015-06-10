/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.compute;

import com.emc.storageos.model.BulkRestRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "bulk_compute_images")
public class ComputeImageBulkRep extends BulkRestRep {
    private List<ComputeImageRestRep> computeImages;

    public ComputeImageBulkRep() {
    }

    public ComputeImageBulkRep(List<ComputeImageRestRep> computeImages) {
        this.computeImages = computeImages;
    }

    /**
     * List of compute image objects that exist in ViPR.
     * @valid none
     */
    @XmlElement(name = "compute_image")
    public List<ComputeImageRestRep> getComputeImages() {
        if (computeImages == null) {
            computeImages = new ArrayList<ComputeImageRestRep>();
        }
        return computeImages;
    }

    public void setComputeImages(List<ComputeImageRestRep> computeImages) {
        this.computeImages = computeImages;
    }
}
