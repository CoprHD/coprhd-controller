/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.filters;

import com.emc.storageos.model.file.FileShareRestRep;

public class SourceTargetFileSystemsFilter extends DefaultResourceFilter<FileShareRestRep> {
    @Override
    public boolean accept(FileShareRestRep item) {
        return item.getProtection() == null ||
                item.getProtection().getPersonality().equalsIgnoreCase("source") ||
                item.getProtection().getPersonality().equalsIgnoreCase("target");
    }
}
