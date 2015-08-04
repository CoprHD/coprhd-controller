/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.filters;

import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.vipr.client.core.util.ResourceUtils;

import java.net.URI;

public class ExportHostFilter extends ExportFilter {
    public static final String HOST_EXPORT_TYPE = "Host";
    public static final String EXCLUSIVE_EXPORT_TYPE = "Exclusive";

    private final URI hostId;

    public ExportHostFilter(URI hostId, URI projectId, URI varrayId) {
        super(projectId, varrayId);
        this.hostId = hostId;
    }

    @Override
    public boolean accept(ExportGroupRestRep item) {
        if (!super.accept(item)) {
            return false;
        }

        return item.getType().equals(EXCLUSIVE_EXPORT_TYPE) ||
                (item.getType().equals(HOST_EXPORT_TYPE) && hasHost(item));
    }

    private boolean hasHost(ExportGroupRestRep item) {
        return ResourceUtils.find(item.getHosts(), hostId) != null;
    }
}
