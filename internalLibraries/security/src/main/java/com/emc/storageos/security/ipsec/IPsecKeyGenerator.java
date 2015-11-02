/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.security.ipsec;

import org.apache.commons.lang.RandomStringUtils;

/**
 * The generator to generate a 64-byte length pre shared key.
 */
public class IPsecKeyGenerator {
    private static final int KEY_LENGHT = 64;

    /**
     * generate a 64-byte key for IPsec
     * @return
     */
    public String generate() {
        return RandomStringUtils.random(KEY_LENGHT, true, true);
    }
}
