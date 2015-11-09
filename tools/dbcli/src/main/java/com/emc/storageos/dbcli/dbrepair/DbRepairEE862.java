/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.dbcli.dbrepair;

import static com.emc.storageos.dbcli.dbrepair.DbRepairUtils.displayDiffs;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.DbModelClient;
import com.emc.storageos.db.client.impl.DbModelClientImpl;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.google.common.base.Joiner;

/**
 * Applicable Jira: https://asdjira.isus.emc.com:8443/browse/EE-862
 *
 * Will look for RP initiators in non-RP ExportGroups. If there are any, they will be removed from the ExportGroup.
 *
 */
public class DbRepairEE862 implements DbRepairStub {
    @Override
    public Map<String, String> getParameters() {
        return Collections.emptyMap();
    }

    @Override
    public String getDescription() {
        return "DB Repair will remove all initiators from all non-RP ExportGroups";
    }

    @Override
    public String getExpectedDbVersion() {
        return "2.3";
    }

    @Override
    public boolean run(DbClient dbClient, Map<String, String> parameters, boolean commitChanges) {
        DbModelClient dbModelClient = new DbModelClientImpl(dbClient);
        List<ExportGroup> exportGroupsToUpdate = new ArrayList<>();
        Iterator<ExportGroup> exportGroupIterator = dbModelClient.findAll(ExportGroup.class);
        while (exportGroupIterator.hasNext()) {
            ExportGroup exportGroup = exportGroupIterator.next();
            // If the ExportGroup is active, has initiators, and is not an RP ExportGroup ...
            if (!exportGroup.getInactive() && exportGroup.hasInitiators() &&
                    !(exportGroup.checkInternalFlags(DataObject.Flag.INTERNAL_OBJECT) &&
                            exportGroup.checkInternalFlags(DataObject.Flag.RECOVERPOINT))) {
                System.out.printf("%nExportGroup %s (%s) is not an RP ExportGroup with initiators, going to examine it ...%n",
                        exportGroup.getLabel(), exportGroup.getId());
                List<URI> initiatorsToRemove = new ArrayList<>();
                // Search through the ExportGroup's initiators ...
                Iterator<Initiator> initiatorIterator = dbModelClient.find(Initiator.class,
                        StringSetUtil.stringSetToUriList(exportGroup.getInitiators()));
                while (initiatorIterator.hasNext()) {
                    Initiator initiator = initiatorIterator.next();
                    // If the initiator has been flagged as RP, add it to the tracking list
                    if (initiator.checkInternalFlags(DataObject.Flag.RECOVERPOINT)) {
                        initiatorsToRemove.add(initiator.getId());
                    }
                }
                // If we found any RP initiators in the ExportGroup, remove the initiators from the ExportGroup and then add them to the
                // tracking list
                if (!initiatorsToRemove.isEmpty()) {
                    System.out.printf("The following RP initiators were found in this non-RP ExportGroup: %s%n",
                            Joiner.on(',').join(initiatorsToRemove));
                    exportGroup.removeInitiators(initiatorsToRemove);
                    exportGroupsToUpdate.add(exportGroup);
                } else {
                    System.out.println("\t- ExportGroup does not have any RP initiators");
                }
            }
        }

        if (commitChanges) {
            dbModelClient.update(exportGroupsToUpdate);
        } else {
            displayDiffs(dbClient, exportGroupsToUpdate);
        }

        return true;
    }
}
