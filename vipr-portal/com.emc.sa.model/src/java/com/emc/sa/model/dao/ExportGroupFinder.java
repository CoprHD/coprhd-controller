/*
 * Copyright (c) 2017 Dell-EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.dao;

import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Host;

public class ExportGroupFinder extends TenantResourceFinder<ExportGroup> {
    protected static final String HOST_COLUMN_NAME = "hosts";

    public ExportGroupFinder(DBClientWrapper client) {
        super(ExportGroup.class, client);
    }
    
    public List<ExportGroup> findByHosts( List<Host> dbHosts){
        List<NamedElement> exportGroupUris = new ArrayList<NamedElement>();
        for (Host host : dbHosts) {
            exportGroupUris.addAll(client.findBy(ExportGroup.class, HOST_COLUMN_NAME, host.getId()));
        }
        return findByIds(toURIs(exportGroupUris));
    }

}
