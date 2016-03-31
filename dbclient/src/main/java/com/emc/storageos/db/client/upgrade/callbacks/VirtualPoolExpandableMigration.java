/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.upgrade.callbacks;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

/**
 * Migration handler to convert value of 'nonDisruptiveExpansion' property to 'expandable' and
 * 'fastExpansion' properties.
 * In 2.0 we allow expansion of all VMAX/VNX volumes, except volume with local mirrors.
 * Volumes with 'nodDisruptiveExpansion' true, are expanded with 'fastExpansion', otherwise we expand with 'fastExpansion' false.
 * NOTE: due to customer issue, need to set pools with nonDisruptiveExpansion=false to expandable=false.
 * Customer wants to enable mirroring for 1.1 non expandable pools after upgrade to 2.0. If we set expandable to true after upgrade,
 * apisvc validation won't allow to enable mirroring.
 */
public class VirtualPoolExpandableMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(VirtualPoolExpandableMigration.class);

    @Override
    public void process() throws MigrationCallbackException {
        log.info("Handle VirtualPool 'nonDisruptiveExpansion' conversion.");
        DbClient dbClient = getDbClient();
        List<URI> vpURIs = dbClient.queryByType(VirtualPool.class, true);
        Iterator<VirtualPool> vpIter = dbClient.queryIterativeObjects(VirtualPool.class, vpURIs);

        while (vpIter.hasNext()) {
            VirtualPool vp = vpIter.next();
            if (vp.getNonDisruptiveExpansion()) {
                vp.setExpandable(true);
                vp.setFastExpansion(true);
            } else if (VirtualPool.vPoolSpecifiesMirrors(vp, dbClient)) {
                // As of now, we do not allow expansion of virtual pools with local mirror protection
                vp.setExpandable(false);
                vp.setFastExpansion(false);
            } else {
                // Do not allow expansion. See NOTE above.
                vp.setExpandable(false);
                vp.setFastExpansion(false);
            }
            log.info(String.format(
                    "Migrated VirtualPool %s, nonDisruptiveExpansion: %s, local mirrors: %s, expandable: %s, fastExpansion; %s",
                    vp.getId().toString(), vp.getNonDisruptiveExpansion(), vp.getMaxNativeContinuousCopies(), vp.getExpandable(),
                    vp.getFastExpansion()));
            dbClient.persistObject(vp);
        }

        log.info("Completed VirtualPool 'nonDisruptiveExpansion' conversion.");
    }
}
