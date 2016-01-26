/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

public class InitiatorHostMigration extends BaseCustomMigrationCallback {
    public static final Long FLAG_DEFAULT = 2L;
    private static final Logger log = LoggerFactory.getLogger(InitiatorHostMigration.class);

    // Hold references to valid initiator URI based on port
    private Map<String, Initiator> portToUri = new HashMap<String, Initiator>();

    @Override
    public void process() throws MigrationCallbackException {
        processInitiatorCleanup();
    }

    private void processInitiatorCleanup() {
        // Hold list of initiator URIs to be deleted
        Set<URI> initiatorToDelete = new HashSet<URI>();

        // Grab all export
        List<URI> exports = dbClient.queryByType(ExportGroup.class, true);

        for (URI eUri : exports) {
            ExportGroup eg = dbClient.queryObject(ExportGroup.class, eUri);

            boolean updated = false;

            // skip if not host or cluster export
            if (eg.forInitiator() || eg.forHost() || eg.forCluster()) {
                // Grab export Initiators
                StringSet exportInitiators = eg.getInitiators();

                // search through initiators for null Host URI
                for (URI uri : StringSetUtil.stringSetToUriList(exportInitiators)) {
                    Initiator oldInitiator = dbClient.queryObject(Initiator.class, uri);
                    if (oldInitiator != null) {
                        if (NullColumnValueGetter.isNullURI(oldInitiator.getHost())) {
                            // Get valid initiator based on port
                            Initiator newInitiator = getInitiatorByPort(oldInitiator.getInitiatorPort());

                            if (newInitiator != null) {
                                // Duplicate found, add to delete list
                                initiatorToDelete.add(oldInitiator.getId());

                                updateExportGroupInitiators(eg, oldInitiator, newInitiator);
                                updated = true;

                                updateExportMask(eg, oldInitiator, newInitiator);
                            } else {
                                log.warn("Found initiator: " + oldInitiator.getId() +
                                        " with null Host uri in export group: " + eg.getId() +
                                        " - Additional info: [wwwpn: " + oldInitiator.getInitiatorPort() +
                                        " wwnn: " + oldInitiator.getInitiatorNode() +
                                        " hostname: " + oldInitiator.getHostName());
                            }
                        }
                    }
                }

                if (updated) {
                    dbClient.updateAndReindexObject(eg);
                }
            }
        }

        // Cleanup should be complete, initiators can now be marked for deletion
        for (URI uri : initiatorToDelete) {
            Initiator initiator = dbClient.queryObject(Initiator.class, uri);

            log.info("Setting " + initiator.getId() + " for deletion due to Null Host URI.");
            dbClient.markForDeletion(initiator);
        }
    }

    private Initiator getInitiatorByPort(String port) {

        if (port == null || port.isEmpty()) {
            return null;
        }

        // check map for initiator
        Initiator initiator = portToUri.get(port);

        if (initiator != null) {
            return initiator;
        }

        // Finds the Initiator that includes the initiator port specified, if any.
        List<URI> uris = dbClient.queryByConstraint(AlternateIdConstraint.Factory.getInitiatorPortInitiatorConstraint(port));

        // look for initiator with valid host URI
        for (URI iUri : uris) {
            Initiator i = dbClient.queryObject(Initiator.class, iUri);
            if (i != null) {
                if (!NullColumnValueGetter.isNullURI(i.getHost())) {
                    // add port name to URI map
                    portToUri.put(port, i);
                    return i;
                }
            }
        }

        return null;
    }

    private void updateExportGroupInitiators(ExportGroup eg, Initiator oldInitiator, Initiator newInitiator) {
        log.info("Updating export group: " + eg.getId() +
                " replacing old initiator: " + oldInitiator.getId() +
                " with new initiator: " + newInitiator.getId());
        // remove the old initiator from export due to null host URI
        eg.removeInitiator(oldInitiator);
        // Add valid initiator
        eg.addInitiator(newInitiator);
    }

    private void updateExportMask(ExportGroup eg, Initiator oldInitiator, Initiator newInitiator) {
        // update export mask
        StringSet exportMasks = eg.getExportMasks();
        for (URI maskUri : StringSetUtil.stringSetToUriList(exportMasks)) {
            ExportMask mask = dbClient.queryObject(ExportMask.class, maskUri);
            if (mask != null) {
                udpateExportMaskInitiators(mask, oldInitiator, newInitiator);
                updateExportMaskUserAddedInitiators(mask, oldInitiator, newInitiator);
                updateExportMaskZoningMap(mask, oldInitiator, newInitiator);

                dbClient.updateAndReindexObject(mask);
            }
        }
    }

    private void udpateExportMaskInitiators(ExportMask mask, Initiator oldInitiator, Initiator newInitiator) {
        // update export mask initiator
        StringSet maskInitiators = mask.getInitiators();
        if (maskInitiators != null && maskInitiators.contains(oldInitiator.getId().toString())) {
            log.info("Updating export mask: " + mask.getId() +
                    " replacing old initiator: " + oldInitiator.getId() +
                    " with new initiators: " + newInitiator.getId());
            mask.getInitiators().remove(oldInitiator.getId().toString());
            mask.addInitiator(newInitiator);
        }
    }

    private void updateExportMaskUserAddedInitiators(ExportMask mask, Initiator oldInitiator, Initiator newInitiator) {
        // update user added initiators
        StringMap userInitiators = mask.getUserAddedInitiators();
        StringSet userKeys = StringSetUtil.getStringSetFromStringMapKeySet(userInitiators);
        for (String key : userKeys) {
            String val = userInitiators.get(key);
            // if the value matches the old initiator
            if (val.equals(oldInitiator.getId().toString())) {
                log.info("Updating export mask: " + mask.getId() +
                        " replacing old user added initiator: " + oldInitiator.getId() +
                        " with new user added initiator: " + newInitiator.getId());

                // update the user initiator entry
                mask.getUserAddedInitiators().put(key, newInitiator.getId().toString());
            }
        }
    }

    private void updateExportMaskZoningMap(ExportMask mask, Initiator oldInitiator, Initiator newInitiator) {
        // update zoning map
        if (mask.getZoningMap() != null) {
            Set<String> zoningKeys = mask.getZoningMap().keySet();
            if (zoningKeys.contains(oldInitiator.getId().toString())) {
                // grab associated storage port from existing initiator
                StringSet storagePorts = mask.getZoningMap().get(oldInitiator.getId().toString());

                log.info("Updating export mask: " + mask.getId() +
                        " with new zoning map entry: " + newInitiator.getId() + " : " + storagePorts.toString() +
                        " - Swapped entry: " + oldInitiator.getId() + " for: " + newInitiator.getId());
                // add new zoning map entry linked to storagePorts
                mask.addZoningMapEntry(newInitiator.getId().toString(), storagePorts);
                // remove old zoning map entry
                mask.removeZoningMapEntry(oldInitiator.getId().toString());
            }
        }
    }
}
