/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.dbcli.dbrepair;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject;
import com.google.common.collect.Multimap;

import java.util.Map;

public interface DbRepairStub {

    /**
     * Return the parameters that the repair code takes
     *
     * @return Map of String parameter name to type of parameter
     */
    Map<String, String> getParameters();

    /**
     * A description of the what the repair script is supposed to cover or the relevant Jiras that it covers
     *
     * @return String description
     */
    String getDescription();

    /**
     * Return the expected version of the ViPR DB that the repair code should be
     * run against.
     *
     * @return String version value
     */
    String getExpectedDbVersion();

    /**
     * This method will be used for invocation of the DB repair code.
     *
     * @param dbClient [IN] - Object used for accessing the DB
     * @param parameters [IN] - Parameters for running the repair
     * @param commitChanges [IN] - If false, the implementation must NOT commit the DB changes
     *
     * @return False if there were any issues with the repair.
     */
    boolean run(DbClient dbClient, Map<String, String> parameters, boolean commitChanges);
}
