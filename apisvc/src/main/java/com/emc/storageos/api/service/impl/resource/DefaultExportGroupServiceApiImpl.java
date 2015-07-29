/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import java.net.URI;
import java.util.List;
import java.util.Set;

import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;

public class DefaultExportGroupServiceApiImpl extends
        AbstractExportGroupServiceApiImpl {
    @Override
    public void validateVarrayStoragePorts(Set<URI> storageSystemURIs,
            VirtualArray varray, List<URI> allHosts) throws InternalException {
        // Do nothing for now
    }

}
