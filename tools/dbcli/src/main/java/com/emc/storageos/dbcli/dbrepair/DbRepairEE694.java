/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.dbcli.dbrepair;

import static com.emc.storageos.dbcli.dbrepair.DbRepairUtils.displayDiffs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.DbModelClient;
import com.emc.storageos.db.client.impl.DbModelClientImpl;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.google.common.base.Joiner;

/**
 * Applicable Jira: https://asdjira.isus.emc.com:8443/browse/EE-694
 *
 * Any ExportMasks that were created by the system (meaning they were created by ViPR or ingested by ViPR) will be examined. If they
 * have existing initiators, these will be removed from the existing list and added to the user-add initiators list.
 */
public class DbRepairEE694 implements DbRepairStub {

    @Override
    public Map<String, String> getParameters() {
        return Collections.emptyMap();
    }

    @Override
    public String getDescription() {
        return "This DB Repair will examine the ExportMask objects. It will look at any that have their createdBySystem property "
                + "set to true. If true, all the ExportMask object's existingInitiators will be moved to the userAddedInitiators list.\n";
    }

    @Override
    public String getExpectedDbVersion() {
        return "2.3";
    }

    @Override
    public boolean run(DbClient dbClient, Map<String, String> parameters, boolean commitChanges) {
        DbModelClient dbModelClient = new DbModelClientImpl(dbClient);
        List<ExportMask> exportMasksToUpdate = new ArrayList<>();
        // Get all ExportMask that have createdBySystem = true ...
        Iterator<ExportMask> exportMaskIterator = dbModelClient.find(ExportMask.class, "createdBySystem", Boolean.TRUE);
        while (exportMaskIterator.hasNext()) {
            ExportMask exportMask = exportMaskIterator.next();
            System.out.printf("%nExamining ExportMask %s (%s)%n", exportMask.getMaskName(), exportMask.getId());
            // If there are any existing initiators for the ExportMask ...
            if (exportMask.getExistingInitiators() != null && !exportMask.getExistingInitiators().isEmpty()) {
                System.out.printf("There are existing initiators: %s%n",
                        CommonTransformerFunctions.collectionString(exportMask.getExistingInitiators()));
                List<String> existingInitiatorsToRemove = new ArrayList<>();
                // Move each of the existing initiators to the user-added list
                for (String portName : exportMask.getExistingInitiators()) {
                    Initiator initiator = dbModelClient.findByUniqueAlternateId(Initiator.class, "iniport",
                            Initiator.toPortNetworkId(portName));
                    if (initiator != null) {
                        existingInitiatorsToRemove.add(portName);
                        if (initiator.getInactive()) {
                            System.out.printf(
                                    "Initiator %s (%s) is inactive. It will be removed from the existing list, but not added to the user-added list.",
                                    initiator.getId(), initiator.getInitiatorPort());
                            continue;
                        }
                        exportMask.addToUserCreatedInitiators(initiator);
                        if (!exportMask.hasInitiator(initiator.getId().toString())) {
                            // Rather strange case. Initiator is in the existing list, but not in the initiators list.
                            System.out.printf(
                                    "Initiator %s (%s) does not exist in ExportMask's initiator list. It will be added.%n",
                                    initiator.getId(), initiator.getInitiatorPort());
                            exportMask.addInitiator(initiator);
                        }
                    }
                }
                System.out.printf("The following ports will be moved from existing to userAdded: %s%n",
                        Joiner.on(',').join(existingInitiatorsToRemove));
                exportMask.removeFromExistingInitiators(existingInitiatorsToRemove);
                exportMasksToUpdate.add(exportMask);
            } else {
                System.out.println("\t- No existing initiators found for the ExportMask");
            }
        }
        if (commitChanges) {
            dbModelClient.update(exportMasksToUpdate);
        } else {
            displayDiffs(dbClient, exportMasksToUpdate);
        }
        return true;
    }
}
