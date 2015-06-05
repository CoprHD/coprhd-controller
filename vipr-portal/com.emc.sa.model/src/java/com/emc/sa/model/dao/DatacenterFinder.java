/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.sa.model.dao;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.VcenterDataCenter;

public class DatacenterFinder extends ModelFinder<VcenterDataCenter> {
    private static final String VCENTER_COLUMN_NAME = "vcenter";

    public DatacenterFinder(DBClientWrapper client) {
        super(VcenterDataCenter.class, client);
    }

    public List<VcenterDataCenter> findByVCenter(URI vcenterId) {
        List<NamedElement> datacenters = findIdsByVCenter(vcenterId);

        return findByIds(toURIs(datacenters));
    }

    public List<NamedElement> findIdsByVCenter(URI vcenterId) {
        return client.findBy(VcenterDataCenter.class, VCENTER_COLUMN_NAME, vcenterId);
    }

}
