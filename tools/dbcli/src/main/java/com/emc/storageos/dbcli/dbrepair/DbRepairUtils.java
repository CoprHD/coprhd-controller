/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.dbcli.dbrepair;

import static java.lang.String.format;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject;

public class DbRepairUtils {

    public static final String IN_DB = "IN DB:";
    public static final String UPDATED = "UPDATED:";

    /**
     * Compares the properties of two DataObjects, one that is from the DB and one that is in memory. Output goes to the returned String
     * 
     * @param fromDb [IN] - DataObject retrieved from DB
     * @param updated [IN]- DataObject that has been changed by the repair process
     * @return String List of changed properties
     */
    public static String diff(DataObject fromDb, DataObject updated) {
        StringBuilder out = new StringBuilder();
        Object[] emptyParameters = new Object[] {};
        // Loop through the DataObject's methods ...
        for (Method method : fromDb.getClass().getDeclaredMethods()) {
            try {
                // If it looks like a getter, call it against both DataObjects and compare
                if (method.getName().startsWith("get")) {
                    Object dbValue = method.invoke(fromDb, emptyParameters);
                    Object updatedValue = method.invoke(updated, emptyParameters);
                    if (dbValue != null && !dbValue.equals(updatedValue)) {
                        out.append(format("\t=== %s ===%n\t%-10s %s%n\t%-10s %s%n", method.getName(), IN_DB, dbValue, UPDATED, updatedValue));
                    }
                }
            } catch (IllegalAccessException | InvocationTargetException e) {
                System.out.printf("ERROR: Could not get field %s - %s\n", method.getName(), e.getMessage());
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }
        return out.toString();
    }

    /**
     * Given a list of changed DataObjects, go through them and dump their differences
     * 
     * @param dbClient [IN] - DbClient for accessing DB
     * @param changedObjects [IN] - DataObjects that were changed by the DB repair process
     */
    public static void displayDiffs(DbClient dbClient, Collection<? extends DataObject> changedObjects) {
        System.out.println("*** DRY RUN: Showing the diff of objects that would have been changed ***");
        if (!changedObjects.isEmpty()) {
            for (DataObject changed : changedObjects) {
                DataObject inDb = dbClient.queryObject(changed.getClass(), changed.getId());
                System.out.printf("%s in DB:%n%s%n", changed.getClass().getSimpleName(), inDb.toString());
                System.out.printf("Diff:%n%s%n", diff(inDb, changed));
            }
        } else {
            System.out.println("*** DRY RUN: No objects needed to be changed ***");
        }
    }
}
