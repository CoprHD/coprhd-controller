/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.vipr.client.core.filters;

import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.vipr.client.core.util.ResourceUtils;

import java.net.URI;

public class ProjectFilter extends DefaultResourceFilter<ExportGroupRestRep> {

    private final URI projectId;

    public ProjectFilter(URI projectId) {
        this.projectId = projectId;
    }

    @Override
    public boolean accept(ExportGroupRestRep item) {
        if (projectId != null && !item.getProject().getId().equals(projectId)) {
            return false;
        }

        return true;
    }
}
