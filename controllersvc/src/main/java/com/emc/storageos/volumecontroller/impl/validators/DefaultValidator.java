/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;

/**
 * Uses a single {@link Validator} instance and shares its {@link ValidatorLogger}.
 * This class will perform validation and then check to see if any validation
 * errors occurred, throwing an exception if so.
 */
public class DefaultValidator implements Validator {

    private static final Logger log = LoggerFactory.getLogger(DefaultValidator.class);

    // System Property. If this is false, it's OK to run validation checks, but don't fail out when they fail.
    // This may be a dangerous thing to do, so we see this as a "kill switch" when service is in a desperate
    // situation and they need to disable the feature.
    private static final String VALIDATION_CHECK_PROPERTY = "validation_check";
    private static final String VALIDATION_REFRESH_CHECK_PROPERTY = "refresh_provider_on_validation";

    private CoordinatorClient coordinator;
    private Validator validator;
    private ValidatorLogger logger;
    private String type;

    public DefaultValidator(Validator validator, CoordinatorClient coordinator, ValidatorLogger logger, String type) {
        this.validator = validator;
        this.logger = logger;
        this.coordinator = coordinator;
        this.type = type;
    }

    @Override
    public boolean validate() throws Exception {
        try {
            validator.validate();
        } catch (Exception e) {
            log.error("Exception occurred during validation: ", e);
            if (DefaultValidator.validationEnabled(coordinator)) {
                throw DeviceControllerException.exceptions.unexpectedCondition(e.getMessage());
            }
        }

        if (logger.hasErrors()) {
            if (DefaultValidator.validationEnabled(coordinator)) {
                throw DeviceControllerException.exceptions.validationError(
                        type, logger.getMsgs().toString(), ValidatorLogger.CONTACT_EMC_SUPPORT);
            }
        }

        return true;
    }

    /**
     * Check to see if the validation variable is set. Default to true.
     * 
     * @param coordinator
     *            coordinator for system properties
     * @return true if the validation check is on.
     */
    public static boolean validationEnabled(CoordinatorClient coordinator) {
        if (coordinator != null) {
            return Boolean.valueOf(ControllerUtils
                    .getPropertyValueFromCoordinator(coordinator, VALIDATION_CHECK_PROPERTY));
        } else {
            log.error("Bean wiring error: Coordinator not set in validator, therefore validation will default to true.");
        }

        return true;
    }

    /**
     * Check to see if we should perform refresh sys of provider.
     * Usually this is only done when you are running automated suites where the provider may
     * be out of sync with outside-of-controller operations.
     * 
     * @param coordinator
     *            coordinator for system properties
     * @return true if the validation ref system check is on.
     */
    public static boolean validationRefreshEnabled(CoordinatorClient coordinator) {
        if (coordinator != null) {
            return Boolean.valueOf(ControllerUtils
                    .getPropertyValueFromCoordinator(coordinator, VALIDATION_REFRESH_CHECK_PROPERTY));
        } else {
            log.error("Bean wiring error: Coordinator not set in validator, therefore validation will default to false.");
        }

        return false;
    }
}
