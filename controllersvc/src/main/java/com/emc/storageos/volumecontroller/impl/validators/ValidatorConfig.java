/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;

/**
 * Configuration class for the Validation framework.
 */
public class ValidatorConfig {

    private static final Logger log = LoggerFactory.getLogger(ValidatorConfig.class);

    // System Property. If this is false, it's OK to run validation checks, but don't fail out when they fail.
    // This may be a dangerous thing to do, so we see this as a "kill switch" when service is in a desperate
    // situation and they need to disable the feature.
    private static final String VALIDATION_CHECK_PROPERTY = "validation_check";
    private static final String VALIDATION_REFRESH_CHECK_PROPERTY = "refresh_provider_on_validation";

    private CoordinatorClient coordinator;

    public ValidatorConfig() {
    }

    public CoordinatorClient getCoordinator() {
        return coordinator;
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }

    /**
     * Check to see if the validation variable is set. Default to true.
     *
     * @return true if the validation check is on.
     */
    public boolean isValidationEnabled() {
        if (coordinator != null) {
            return Boolean.valueOf(ControllerUtils
                    .getPropertyValueFromCoordinator(coordinator, VALIDATION_CHECK_PROPERTY));
        } else {
            log.error("Bean wiring error: Coordinator not set, therefore validation will default to true.");
        }

        return true;
    }

    /**
     * Check to see if we should perform refresh sys of provider.
     * Usually this is only done when you are running automated suites where the provider may
     * be out of sync with outside-of-controller operations.
     *
     * @return true if the validation ref system check is on.
     */
    public boolean validationRefreshEnabled() {
        if (coordinator != null) {
            return Boolean.valueOf(ControllerUtils
                    .getPropertyValueFromCoordinator(coordinator, VALIDATION_REFRESH_CHECK_PROPERTY));
        } else {
            log.error("Bean wiring error: Coordinator not set, therefore validation will default to false.");
        }

        return false;
    }
}
