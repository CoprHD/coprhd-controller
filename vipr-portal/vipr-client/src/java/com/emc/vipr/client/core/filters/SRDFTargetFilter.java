/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.filters;

import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.block.VolumeRestRep.SRDFRestRep;

/**
 * Filter for volumes that are SRDF targets.
 */
public class SRDFTargetFilter extends DefaultResourceFilter<VolumeRestRep> {
    @Override
    public boolean accept(VolumeRestRep item) {
        if ((item.getProtection() != null) && (item.getProtection().getSrdfRep() != null)) {
            SRDFRestRep srdf = item.getProtection().getSrdfRep();
            return srdf.getAssociatedSourceVolume() != null;
        }
        else {
            return false;
        }
    }
}
