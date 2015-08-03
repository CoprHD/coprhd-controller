/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
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
