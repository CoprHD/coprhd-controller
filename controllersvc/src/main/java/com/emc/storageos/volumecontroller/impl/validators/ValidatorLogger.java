/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators;

import org.slf4j.Logger;

/**
 * Logger for validations.
 */
public class ValidatorLogger {
    private Logger log;
    private StringBuilder msgs = new StringBuilder();

    // For logging: the name of the object we validated, along with the storage system
    private String validatedObjectName = null;
    private String storageSystemName = null;

    public static final String CONTACT_EMC_SUPPORT = "Contact EMC Support";
    public static final String INVENTORY_DELETE_VOLUME = "Inventory delete the affected volume(s)";

    public ValidatorLogger() {
    }

    /**
     * Log a discrepancy in the data.
     * 
     * @param id
     *            -- Identity of the domain object
     * @param field
     *            -- Field with discrepancy
     * @param db
     *            -- Database value
     * @param hw
     *            -- Hardware value
     */
    public void logDiff(String id, String field, String db, String hw) {
        String msg = String.format("id: %s field: %s database: %s hardware: %s", id, field, db, hw);
        msgs.append(msg + "\n");
        if (log != null) {
            log.info(msg);
        }
    }

    public ValidatorLogger(Logger log, String validatedObjectName, String storageSystemName) {
        this.log = log;
    }

    public void setLog(Logger log) {
        this.log = log;
    }

    public String getValidatedObjectName() {
        return validatedObjectName;
    }

    public void setValidatedObjectName(String validatedObjectName) {
        this.validatedObjectName = validatedObjectName;
    }

    public String getStorageSystemName() {
        return storageSystemName;
    }

    public void setStorageSystemName(String storageSystemName) {
        this.storageSystemName = storageSystemName;
    }

    public StringBuilder getMsgs() {
        return msgs;
    }

    public boolean hasErrors() {
        return msgs.length() > 0;
    }
}
