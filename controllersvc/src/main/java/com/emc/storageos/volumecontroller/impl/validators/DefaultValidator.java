/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators;

import com.emc.storageos.volumecontroller.impl.validators.contexts.ExceptionContext;
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

    private ExceptionContext exceptionContext;
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

    public ExceptionContext getExceptionContext() {
        return exceptionContext;
    }

    public void setExceptionContext(ExceptionContext exceptionContext) {
        this.exceptionContext = exceptionContext;
    }

    @Override
    public boolean validate() throws Exception {
        try {
            validator.validate();
        } catch (Exception e) {
            log.error("Exception occurred during validation: ", e);
            if (shouldThrowException()) {
                throw DeviceControllerException.exceptions.unexpectedCondition(e.getMessage());
            }
        }

        if (logger.hasErrors() && shouldThrowException()) {
            logger.generateException(type);
        }

        return true;
    }

    private boolean shouldThrowException() {
        return config.isValidationEnabled() && (exceptionContext == null || exceptionContext.isAllowExceptions());
    }
}
