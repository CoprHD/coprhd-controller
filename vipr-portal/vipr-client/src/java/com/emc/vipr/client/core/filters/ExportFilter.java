/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.vipr.client.core.filters;

import com.emc.storageos.model.block.export.ExportGroupRestRep;

import java.net.URI;

/**
 */
public class ExportFilter extends DefaultResourceFilter<ExportGroupRestRep>{
    private final URI projectId;
    private final URI varrayId;

    public ExportFilter(URI projectId, URI varrayId ) {
        this.projectId = projectId;
        this.varrayId = varrayId;
    }

    @Override
    public boolean accept(ExportGroupRestRep item) {
        if (projectId != null && !item.getProject().getId().equals(projectId)) {
            return false;
        }

        if (varrayId != null && !item.getVirtualArray().getId().equals(varrayId)) {
            return false;
        }

        return true;
    }
}
