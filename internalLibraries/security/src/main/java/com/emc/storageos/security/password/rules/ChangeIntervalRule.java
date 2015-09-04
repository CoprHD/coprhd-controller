/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.security.password.rules;

import com.emc.storageos.security.password.Password;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;

public class ChangeIntervalRule implements Rule {
    private static final Logger _log = LoggerFactory.getLogger(ChangeIntervalRule.class);

    // changeInterval in minutes
    // 0 -- disable the validation
    private int changeInterval = 0;

    public ChangeIntervalRule(int changeInterval) {
        this.changeInterval = changeInterval;
    }

    /**
     * validate the time passed since last change password operation.
     *
     * @param password
     */
    public void validate(Password password) {
        if (changeInterval == 0) {
            return;
        }

        long milliseconds = changeInterval * 60 * 1000;
        long lastChangeTime = password.getLatestChangedTime();
        long interval = System.currentTimeMillis() - lastChangeTime;
        _log.info(MessageFormat.format("expect > {0} minutes, real = {1} minutes", changeInterval, interval / (60 * 1000)));
        if (interval < milliseconds) {
            _log.info("fail");
            throw APIException.badRequests.passwordInvalidInterval(changeInterval);
        }
        _log.info("pass");

    }
}
