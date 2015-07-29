/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import com.emc.storageos.model.RelatedResourceRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "unmanaged_export_masks")
public class UnManagedExportMaskList {

    private List<RelatedResourceRep> unManagedExportMasks;

    public UnManagedExportMaskList() {
    }

    public UnManagedExportMaskList(List<RelatedResourceRep> unManagedExportMasks) {
        this.unManagedExportMasks = unManagedExportMasks;
    }

    @XmlElement(name = "unmanaged_export_mask")
    public List<RelatedResourceRep> getUnManagedExportMasks() {
        if (unManagedExportMasks == null) {
            unManagedExportMasks = new ArrayList<RelatedResourceRep>();
        }
        return unManagedExportMasks;
    }

    public void setUnManagedExportMasks(List<RelatedResourceRep> unManagedExportMasks) {
        this.unManagedExportMasks = unManagedExportMasks;
    }

}
