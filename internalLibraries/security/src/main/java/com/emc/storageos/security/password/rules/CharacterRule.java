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

public interface CharacterRule extends Rule {

    /**
     * Sets the number of characters to require in a password.
     * 
     * @param n number of characters to require where n > 0
     */
    void setNumberOfCharacters(int n);

    /**
     * Returns the number of characters which must exist in order for a password
     * to meet the requirements of this rule.
     * 
     * @return number of characters to require
     */
    int getNumberOfCharacters();
}
