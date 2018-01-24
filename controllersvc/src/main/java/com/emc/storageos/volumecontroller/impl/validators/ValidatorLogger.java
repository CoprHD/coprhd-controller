/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators;

import org.slf4j.Logger;

import com.emc.storageos.exceptions.DeviceControllerException;

/**
 * Logger for validations.
 */
public class ValidatorLogger {
    private Logger log;
    private StringBuilder msgs = new StringBuilder();

    // For logging: the name of the object we validated, along with the storage system
    private String validatedObjectName = null;
    private String storageSystemName = null;

    public static final String EXPORT_MASK_TYPE = "Export Mask";
    public static final String VOLUME_TYPE = "Volume";

    public static final String CONTACT_EMC_SUPPORT = "Contact EMC Support";
    public static final String INVENTORY_DELETE_VOLUME = "Inventory delete the affected volume(s)";

    public static final String NO_MATCHING_ENTRY = "<no matching entry>";

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
        StringBuffer diffBuffer = new StringBuffer(String.format("Controller database object ID [%s], field [%s]: ", id, field));

        // Craft a message depending on whether the db field is non-existent, or if the hw field wasn't found
        if (db == null || db.isEmpty() || db.equalsIgnoreCase(NO_MATCHING_ENTRY)) {
            diffBuffer.append(String.format(
                    "The hardware reported entry [%s], whereas the controller is not managing or does not have a reference to the same resource\n",
                    hw));
        } else if (hw == null || hw.isEmpty() || hw.equalsIgnoreCase(NO_MATCHING_ENTRY)) {
            diffBuffer.append(String.format(
                    "The controller is managing resource [%s], whereas the hardware did not report that resource\n",
                    db));
        } else {
            diffBuffer.append(String.format(
                    "The controller references resource: [%s], whereas the hardware reported the actual resource as: [%s]\n",
                    db != null ? db : "null",
                    hw != null ? hw : "null"));
        }
        // Add to the logger object to track that differences were found.
        msgs.append(diffBuffer.toString() + "\n");
        if (log != null) {
            log.info(diffBuffer.toString());
        }
    }

    public ValidatorLogger(Logger log, String validatedObjectName, String storageSystemName) {
        this.log = log;
        this.validatedObjectName = validatedObjectName;
        this.storageSystemName = storageSystemName;
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

    /**
     * Generate an appropriate exception for the type of object validate.
     * @param type
     *            type of object validated
     * @param logger
     *            log object with details of failure
     */
    public void generateException(String type) {
        if (type.equalsIgnoreCase(ValidatorLogger.EXPORT_MASK_TYPE)) {
            throw DeviceControllerException.exceptions.validationExportMaskError(getValidatedObjectName(),
                    getStorageSystemName(), getMsgs().toString());
        }
    
        if (type.equalsIgnoreCase(ValidatorLogger.VOLUME_TYPE)) {
            throw DeviceControllerException.exceptions.validationVolumeError(getValidatedObjectName(),
                    getStorageSystemName(), getMsgs().toString());
        }
    
        // Generic validation exception
        throw DeviceControllerException.exceptions.validationError(type, getMsgs().toString(),
                ValidatorLogger.CONTACT_EMC_SUPPORT);
    }
}
