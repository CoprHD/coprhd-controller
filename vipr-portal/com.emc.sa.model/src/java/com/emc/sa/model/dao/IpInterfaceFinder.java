/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.dao;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.IpInterface;

public class IpInterfaceFinder extends ModelFinder<IpInterface> {

    private static final String HOST_COLUMN_NAME = "host";

    public IpInterfaceFinder(DBClientWrapper client) {
        super(IpInterface.class, client);
    }

    public List<NamedElement> findIdsByHost(URI hostId) {
        return client.findBy(IpInterface.class, HOST_COLUMN_NAME, hostId);
    }

    public List<IpInterface> findByHost(URI hostId) {
        List<NamedElement> ids = findIdsByHost(hostId);
        return findByIds(toURIs(ids));
    }
}
