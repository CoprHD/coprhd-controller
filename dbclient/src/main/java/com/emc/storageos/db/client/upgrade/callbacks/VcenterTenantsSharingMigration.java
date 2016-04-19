/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.upgrade.callbacks;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.*;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

/**
 * A migration callback for the migrating the tenant information
 * to the vCenters acls and make the vCenter as a tenants sharable resources.
 *
 */
public class VcenterTenantsSharingMigration extends BaseCustomMigrationCallback {
    protected static final Logger _log = LoggerFactory.getLogger(VcenterTenantsSharingMigration.class);

    @Override
    public void process() throws MigrationCallbackException {
        DbClient dbClient = getDbClient();
        List<URI> vcenterURIs = dbClient.queryByType(Vcenter.class, false);
        Iterator<Vcenter> vcentersIter = dbClient.queryIterativeObjects(Vcenter.class, vcenterURIs);
        _log.info("Migrating vCenter tenant to acls - start");
        while (vcentersIter.hasNext()) {
            Vcenter vcenter = vcentersIter.next();
            URI tenantURI = vcenter.getTenant();
            if (!NullColumnValueGetter.isNullURI(tenantURI)) {
                _log.info("Migrating the tenant {} of the vCenter {}", vcenter.getTenant(), vcenter.getLabel());
                vcenter.setCascadeTenancy(Boolean.TRUE);
                vcenter.addAcl(tenantURI);
                vcenter.setTenant(NullColumnValueGetter.getNullURI());
                dbClient.persistObject(vcenter);
            }
        }
        _log.info("Migrating vCenter tenant to acls - end");
    }
}
