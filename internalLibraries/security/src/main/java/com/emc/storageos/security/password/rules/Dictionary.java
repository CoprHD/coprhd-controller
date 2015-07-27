/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.security.password.rules;


public interface Dictionary {

    /**
     * Returns whether the supplied word exists in the dictionary.
     *
     * @param word
     * @return
     */
    public boolean search(String word);

}
