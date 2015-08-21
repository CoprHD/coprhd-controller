/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.upgrade.callbacks;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.*;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.util.NullColumnValueGetter;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

/**
 * A migration callback for the migrating the tenant information
 * to the vCenters acls and make the vCenter as non sharable resources.
 *
 */
public class VcenterTenantsSharingMigration extends BaseCustomMigrationCallback {

    @Override
    public void process() {
        DbClient dbClient = getDbClient();
        List<URI> vcenterURIs = dbClient.queryByType(Vcenter.class, false);
        Iterator<Vcenter> vcentersIter = dbClient.queryIterativeObjects(Vcenter.class, vcenterURIs);
        while (vcentersIter.hasNext()) {
            Vcenter vcenter = vcentersIter.next();
            URI tenantURI = vcenter.getTenant();
            if (!NullColumnValueGetter.isNullURI(tenantURI)) {
                vcenter.setTenantCreated(Boolean.FALSE);
                vcenter.addAcl(tenantURI);
                dbClient.persistObject(vcenter);
            }
        }
    }
}
