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
import com.emc.storageos.security.password.PasswordUtils;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.text.MessageFormat;
import java.util.List;

public class HistoryRule implements Rule {
    private static final Logger _log = LoggerFactory.getLogger(HistoryRule.class);

    private int historySize = 5;
    private PasswordUtils passwordUtils;

    public HistoryRule(int size, PasswordUtils passwordUtils) {
        this.historySize = size;
        this.passwordUtils = passwordUtils;
    }


    /**
     * validate the new password is not in history.
     *
     * @param password
     */
    @Override
    public void validate(Password password) {
        if (historySize == 0) {
            return;
        }

        String username = password.getUsername();
        if (username == null || username.trim().length() == 0) {
            return;
        }

        String text = password.getPassword();
        List<String> previousPasswords = password.getPreviousPasswords(historySize);
        if (CollectionUtils.isEmpty(previousPasswords) ) {
            _log.info("pass, no previous password");
            return;
        }

        for (int i=0; i<previousPasswords.size(); i++) {
            if (passwordUtils.match(text, previousPasswords.get(i))) {
                _log.info(MessageFormat.format("fail, match previous password #{0}", i));
                throw BadRequestException.badRequests.passwordInvalidHistory(historySize);
            }
            _log.info(MessageFormat.format("good, do not match previous password #{0}", i));
        }

        _log.info("pass");
    }
}
