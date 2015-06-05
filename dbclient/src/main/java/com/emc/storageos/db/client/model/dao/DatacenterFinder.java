/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.db.client.model.dao;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.ModelClient;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.VcenterDataCenter;

public class DatacenterFinder extends ModelFinder<VcenterDataCenter> {
    private static final String VCENTER_COLUMN_NAME = "vcenter";

    public DatacenterFinder(ModelClient client) {
        super(VcenterDataCenter.class, client);
    }

    public Iterable<VcenterDataCenter> findByVCenter(URI vcenterId, boolean activeOnly) {
        List<NamedElement> datacenters = findIdsByVCenter(vcenterId);

        return findByIds(toURIs(datacenters), activeOnly);
    }

    public List<NamedElement> findIdsByVCenter(URI vcenterId) {
        return client.findBy(VcenterDataCenter.class, VCENTER_COLUMN_NAME, vcenterId);
    }

}
