/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.srdf.collectors;

import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.impl.smis.srdf.SRDFUtils;

import java.util.Collection;

/**
 * Created by bibbyi1 on 4/29/2015.
 */
public class BrokenSynchronizationsOnlyFilter implements CollectorResultFilter {
    private SRDFUtils utils;

    public BrokenSynchronizationsOnlyFilter(SRDFUtils utils) {
        this.utils = utils;
    }

    @Override
    public Collection filter(Collection results, StorageSystem provider) {
        return utils.filterBrokenLinks(results, provider);
    }
}
