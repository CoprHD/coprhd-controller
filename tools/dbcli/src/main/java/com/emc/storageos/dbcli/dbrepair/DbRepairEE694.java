/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.dbcli.dbrepair;

import java.lang.reflect.Field;
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

public class DbRepairEE694 implements DbRepairStub {

    @Override
    public Map<String, String> getParameters() {
        return Collections.emptyMap();
    }

    @Override
    public String getDescription() {
        return "This DB Repair will examine the ExportMask objects. It will look at any that have their createdBySystem property "
                + "set to true.\nIf true, all the ExportMask object's existingInitiators will be moved to the userAddedInitiators list.\n";
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
            System.out.printf("Examining ExportMask %s (%s) ...%n", exportMask.getMaskName(), exportMask.getId());
            // If there are any existing initiators for the ExportMask ...
            if (exportMask.getExistingInitiators() != null && !exportMask.getExistingInitiators().isEmpty()) {
                System.out.printf("There are existing initiators: %s%n",
                        CommonTransformerFunctions.collectionString(exportMask.getExistingInitiators()));
                List<String> existingInitiatorsToRemove = new ArrayList<>();
                // Move each of the existing initiators to the user-added list
                for (String portName : exportMask.getExistingInitiators()) {
                    Initiator initiator = dbModelClient.findByUniqueAlternateId(Initiator.class, "iniport", portName);
                    if (initiator != null && !initiator.getInactive()) {
                        existingInitiatorsToRemove.add(portName);
                    }
                    exportMask.addToUserCreatedInitiators(initiator);
                }
                System.out.printf("The following ports will be moved from existing to userAdded: %s%n",
                        Joiner.on(',').join(existingInitiatorsToRemove));
                exportMask.removeFromExistingInitiators(existingInitiatorsToRemove);
                exportMasksToUpdate.add(exportMask);
            } else {
                System.out.printf("ExportMask %s (%s) has no existing initiators.%n",
                        exportMask.getMaskName(), exportMask.getId());
            }
        }
        if (commitChanges) {
            dbModelClient.update(exportMasksToUpdate);
        } else {
            System.out.println("*** DRY RUN: Showing the diff of objects that would have been changed ***");
            for (ExportMask changed : exportMasksToUpdate) {
                ExportMask inDb = dbClient.queryObject(ExportMask.class, changed.getId());
                System.out.printf("Export in DB:%n%s%n", inDb.toString());
                System.out.printf("Diff:%n%s%n", diff(inDb, changed));
            }
        }
        return true;
    }

    private String diff(ExportMask fromDb, ExportMask updated) {
        StringBuilder buffer = new StringBuilder();
        for (Field field : ExportMask.class.getFields()) {
            try {
                Object dbValue = field.get(fromDb);
                Object updatedValue = field.get(updated);
                if (!dbValue.equals(updatedValue)) {
                    buffer.append(String.format(" - %s: %s --> %s\n", field.getName(), dbValue, updatedValue));
                }
            } catch (IllegalAccessException e) {
                System.out.printf("ERROR: Could not get field %s - %s\n", field.getName(), e.getMessage());
            }
        }
        return buffer.toString();
    }
}
