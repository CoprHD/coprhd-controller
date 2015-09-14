/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.BulkRestRep;

@XmlRootElement(name = "bulk_unmanaged_export_masks")
public class UnManagedExportMaskBulkRep extends BulkRestRep {

    private List<UnManagedExportMaskRestRep> unManagedExportMasks;

    public UnManagedExportMaskBulkRep() {
    }

    public UnManagedExportMaskBulkRep(List<UnManagedExportMaskRestRep> unManagedExportMasks) {
        this.unManagedExportMasks = unManagedExportMasks;
    }

    @XmlElement(name = "unmanaged_export_mask")
    public List<UnManagedExportMaskRestRep> getUnManagedExportMasks() {
        if (unManagedExportMasks == null) {
            unManagedExportMasks = new ArrayList<UnManagedExportMaskRestRep>();
        }
        return unManagedExportMasks;
    }

    public void setUnManagedExportMasks(List<UnManagedExportMaskRestRep> unManagedExportMasks) {
        this.unManagedExportMasks = unManagedExportMasks;
    }

}
