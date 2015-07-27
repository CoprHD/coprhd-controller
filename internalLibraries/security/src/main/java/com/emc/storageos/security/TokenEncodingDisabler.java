/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.security;

/**
 * When present in the deployment, disables token encoding.
 * This means the tokens on the wire are just the token ids instead
 * of the fully encoded and signed token.  Used for debugging.
 */
public class TokenEncodingDisabler {
    public TokenEncodingDisabler() {
        
    }
}
