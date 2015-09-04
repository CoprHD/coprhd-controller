/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.security.password.rules;

import com.emc.storageos.security.password.Password;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;

public class DictionaryRule implements Rule {

    private Dictionary dictionary;

    public DictionaryRule(Dictionary dictionary) {
        this.dictionary = dictionary;
    }

    @Override
    public void validate(Password password) {
        if (dictionary.search(password.getPassword())) {
            throw BadRequestException.badRequests.passwordInvalidDictionary();
        }
    }
}
