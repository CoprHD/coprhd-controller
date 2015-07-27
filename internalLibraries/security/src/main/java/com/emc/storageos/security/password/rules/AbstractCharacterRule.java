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

public abstract class AbstractCharacterRule implements CharacterRule {
    private static final Logger _log = LoggerFactory.getLogger(AbstractCharacterRule.class);

    private int numCharacters = 1;

    @Override
    public void setNumberOfCharacters(final int n) {
        if (n > 0) {
            numCharacters = n;
        } else {
            throw new IllegalArgumentException("argument must be greater than zero");
        }
    }

    @Override
    public int getNumberOfCharacters() {
        return numCharacters;
    }


    /**
     * Returns the number of the type of characters in the supplied password for
     * the implementing class.
     *
     * @param  password  to get character count from
     *
     * @return  number of characters
     */
    public abstract int getNumber(Password password);


    /**
     * Returns the proper Exception
     *
     * @return
     */
    public abstract BadRequestException getException();

    /**
     * Returns the type of character managed by this rule.
     *
     * @return  name of a character type, e.g. "digits."
     */
    public abstract String getType();


    @Override
    public void validate(Password password) {
        _log.info(MessageFormat.format("expect >= {0}, real = {1}", numCharacters, getNumber(password)));
        if (getNumber(password) >= numCharacters) {
            _log.info(MessageFormat.format("Password CharacterRule{0} validation pass", getType()));
            return;
        } else {
            _log.info(MessageFormat.format("Password CharacterRule{0} validation fail", getType()));
            throw getException();
        }
    }

}
