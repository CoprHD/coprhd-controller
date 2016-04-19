/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

/**
 * Migration handler to update the internal flags of Export Group
 * and Initiator objects for RecoverPoint.
 */
public class RpExportGroupInternalFlagMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(RpExportGroupInternalFlagMigration.class);

    @Override
    public void process() throws MigrationCallbackException {
        updateFlagsForInitiators();
        updateFlagsForExportGroups();
    }

    /**
     * Update initiators that need to have the internal flags set.
     */
    private void updateFlagsForInitiators() {
        DbClient dbClient = getDbClient();
        List<URI> initiatorURIs = dbClient.queryByType(Initiator.class, false);
        Iterator<Initiator> initiators = dbClient.queryIterativeObjects(Initiator.class, initiatorURIs);
        while (initiators.hasNext()) {
            Initiator initiator = initiators.next();
            log.debug("Examining initiator (id={}) for upgrade", initiator.getId().toString());

            // Check to see if this is a RP Initiator
            if (checkIfInitiatorForRPBeforeMigration(initiator)) {
                log.info("Initiator (id={}) must be upgraded", initiator.getId().toString());
                initiator.addInternalFlags(Flag.RECOVERPOINT);
                dbClient.persistObject(initiator);
                log.info("Marked initiator (id={}) as RecoverPoint", initiator.getId().toString());
            }
        }
    }

    /**
     * Update export groups that need to have the internal flags set.
     */
    private void updateFlagsForExportGroups() {
        DbClient dbClient = getDbClient();
        List<URI> exportGroupURIs = dbClient.queryByType(ExportGroup.class, false);
        Iterator<ExportGroup> exportGroups = dbClient.queryIterativeObjects(ExportGroup.class, exportGroupURIs);
        while (exportGroups.hasNext()) {
            ExportGroup exportGroup = exportGroups.next();
            log.debug("Examining export group (id={}) for upgrade", exportGroup.getId().toString());

            // Check to see if this export group has RP Initiators
            if (checkIfInitiatorsForRPAfterMigration(exportGroup.getInitiators())) {
                log.info("Export group (id={}) must be upgraded", exportGroup.getId().toString());
                exportGroup.addInternalFlags(Flag.RECOVERPOINT);
                dbClient.persistObject(exportGroup);
                log.info("Marked export group (id={}) as RecoverPoint", exportGroup.getId().toString());
            }
        }
    }

    /**
     * Check if the passed in initiator is for RP
     * 
     * @param initiator
     *            -- initiator to check
     * @return true if the initiators are for RP, false otherwise
     */
    private boolean checkIfInitiatorForRPBeforeMigration(Initiator initiator) {
        if (initiator == null) {
            return false;
        }

        boolean isRP = true;
        if (NullColumnValueGetter.isNullValue(initiator.getHostName())
                || !NullColumnValueGetter.isNullURI(initiator.getHost())) {
            isRP = false;
        }

        log.debug("RP initiator? " + (isRP ? "Yes!" : "No!"));
        return isRP;
    }

    /**
     * Check if the passed in initiators are for RP
     * 
     * @param initiators
     *            -- initiators to check
     * @return true if the initiators are for RP, false otherwise
     */
    private boolean checkIfInitiatorsForRPAfterMigration(StringSet initiators) {
        if (initiators == null) {
            return false;
        }

        boolean isRP = false;
        for (String initiatorId : initiators) {
            Initiator initiator = dbClient.queryObject(Initiator.class, URI.create(initiatorId));
            if (initiator != null) {
                isRP = initiator.checkInternalFlags(Flag.RECOVERPOINT);
                if (isRP) {
                    break;
                }
            }
        }

        log.debug("RP initiators? " + (isRP ? "Yes!" : "No!"));
        return isRP;
    }
}
