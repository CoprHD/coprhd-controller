/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.security;

/**
 * When present in the deployment, disables token encoding.
 * This means the tokens on the wire are just the token ids instead
 * of the fully encoded and signed token. Used for debugging.
 */
public class TokenEncodingDisabler {
    public TokenEncodingDisabler() {

    }
}
