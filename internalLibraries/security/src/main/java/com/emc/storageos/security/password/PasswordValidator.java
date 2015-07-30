/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
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
     * @param rules to validate
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
