/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.dbcli.dbrepair;

import java.util.Collections;
import java.util.Map;

import com.emc.storageos.db.client.DbClient;

public class DbRepairCOP13425 implements DbRepairStub {

    @Override
    public Map<String, String> getParameters() {
        return Collections.emptyMap();
    }

    @Override
    public String getDescription() {
        return "This DB Repair will examine the ExportMask objects. It will look at any that have their createdBySystem property " +
                "set to true. If true, all the ExportMask object's existingInitiators will be moved to the userAddedInitiators.";
    }

    @Override
    public String getExpectedDbVersion() {
        return "2.3";
    }

    @Override
    public boolean run(DbClient dbClient, Map<String, String> parameters, boolean commitChanges) {
        System.out.print("Ran the repair!");
        return true;
    }
}
