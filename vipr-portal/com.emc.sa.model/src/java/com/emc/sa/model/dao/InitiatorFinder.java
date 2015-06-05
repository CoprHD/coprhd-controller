/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.sa.model.dao;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.util.EndpointUtility;

public class InitiatorFinder extends ModelFinder<Initiator> {

    private static final String HOST_COLUMN_NAME = "host";
    private static final String PORT_COLUMN_NAME = "iniport";

    public InitiatorFinder(DBClientWrapper client) {
        super(Initiator.class, client);
    }

    public List<NamedElement> findIdsByHost(URI hostId) {
        return client.findBy(Initiator.class, HOST_COLUMN_NAME, hostId);
    }

    public List<Initiator> findByHost(URI hostId) {
        List<NamedElement> ids = findIdsByHost(hostId);
        return findByIds(toURIs(ids));
    }

    public List<Initiator> findByPort(String port) {
        String initiatorPort = EndpointUtility.changeCase(port);
        List<NamedElement> ids = client.findByAlternateId(Initiator.class, PORT_COLUMN_NAME, initiatorPort);
        return findByIds(toURIs(ids));
    }
}
