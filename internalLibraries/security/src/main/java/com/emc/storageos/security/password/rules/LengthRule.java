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
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;

/**
 * Rule for determining if a password is within a desired length.
 */
public class LengthRule implements Rule {
    private static final Logger _log = LoggerFactory.getLogger(LengthRule.class);

    private int minimumLength = 8;

    /**
     * @param length min length of password
     */
    public LengthRule(final int length) {
        minimumLength = length;
    }

    @Override
    public void validate(Password password) {
        int length = password.getPassword().length();
        _log.info(MessageFormat.format("expect > {0}, real = {1}", minimumLength, length));

        if (length < minimumLength) {
            _log.info("fail");
            throw BadRequestException.badRequests.passwordInvalidLength(minimumLength);
        }
        _log.info("pass");
    }
}
