/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.security.password.rules;

import com.emc.storageos.security.password.Password;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;

public class ChangedNumberRule implements Rule {

    private static final Logger _log = LoggerFactory.getLogger(ChangedNumberRule.class);
    private int changedNumber = 2;

    public ChangedNumberRule(int changedNumber) {
        this.changedNumber = changedNumber;
    }

    /**
     * validate the number of characters get changed between old and new passwords.
     * 
     * @param password
     */
    public void validate(Password password) {
        if (changedNumber == 0) {
            return;
        }

        String oldPassword = password.getOldPassword();
        String newPassword = password.getPassword();

        if (oldPassword == null) {
            throw BadRequestException.badRequests.passwordInvalidOldPassword();
        }

        int gap = StringUtils.getLevenshteinDistance(oldPassword, newPassword);

        _log.info(MessageFormat.format("expect >= {0}", changedNumber));
        if (gap >= changedNumber) {
            _log.info(MessageFormat.format("pass: real diff = {0}", gap));
            return;
        } else {
            _log.info(MessageFormat.format("fail: real diff = {0}", gap));
            throw BadRequestException.badRequests.passwordInvalidChangeNumber(changedNumber);
        }
    }
}
