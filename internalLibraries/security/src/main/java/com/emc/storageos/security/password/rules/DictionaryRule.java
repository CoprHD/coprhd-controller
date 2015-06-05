/**
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
