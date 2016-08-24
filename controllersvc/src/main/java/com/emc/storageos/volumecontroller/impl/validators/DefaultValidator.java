/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.exceptions.DeviceControllerException;

/**
 * Uses a single {@link Validator} instance and shares its {@link ValidatorLogger}.
 * This class will perform validation and then check to see if any validation
 * errors occurred, throwing an exception if so.
 */
public class DefaultValidator implements Validator {

    private static final Logger log = LoggerFactory.getLogger(DefaultValidator.class);

    private ValidatorConfig config;
    private Validator validator;
    private ValidatorLogger logger;
    private String type;

    public DefaultValidator(Validator validator, ValidatorConfig config, ValidatorLogger logger, String type) {
        this.validator = validator;
        this.logger = logger;
        this.config = config;
        this.type = type;
    }

    @Override
    public boolean validate() throws Exception {
        try {
            validator.validate();
        } catch (Exception e) {
            log.error("Exception occurred during validation: ", e);
            if (config.validationEnabled()) {
                throw DeviceControllerException.exceptions.unexpectedCondition(e.getMessage());
            }
        }

        if (logger.hasErrors() && config.validationEnabled()) {
            generateException(type, logger);
        }

        return true;
    }

    /**
     * Generate an appropriate exception for the type of object validate.
     * @param type
     *            type of object validated
     * @param logger
     *            log object with details of failure
     */
    public static void generateException(String type, ValidatorLogger logger) {
        if (type.equalsIgnoreCase(ValidatorConfig.EXPORT_MASK_TYPE)) {
            throw DeviceControllerException.exceptions.validationExportMaskError(logger.getValidatedObjectName(),
                    logger.getStorageSystemName(), logger.getMsgs().toString());
        }

        if (type.equalsIgnoreCase(ValidatorConfig.VOLUME_TYPE)) {
            throw DeviceControllerException.exceptions.validationVolumeError(logger.getValidatedObjectName(),
                    logger.getStorageSystemName(), logger.getMsgs().toString());
        }

        // Generic validation exception
        throw DeviceControllerException.exceptions.validationError(type, logger.getMsgs().toString(),
                ValidatorLogger.CONTACT_EMC_SUPPORT);
    }
}
