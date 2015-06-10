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

package com.emc.storageos.security.password;

import com.emc.storageos.security.password.rules.Rule;

import java.util.List;

/**
 * evaluating multiple password rules
 */

public class PasswordValidator implements Rule {

    private final List<Rule> passwordRules;

    /**
     * Creates a new password validator with the default message resolver.
     *
     * @param  rules  to validate
     */
    public PasswordValidator(final List<Rule> rules) {
        passwordRules = rules;
    }

    /**
     * Validates the supplied password data against the rules in this validator.
     */
    @Override
    public void validate(Password password) {
        for (Rule rule : passwordRules) {
            rule.validate(password);
        }
    }
}
