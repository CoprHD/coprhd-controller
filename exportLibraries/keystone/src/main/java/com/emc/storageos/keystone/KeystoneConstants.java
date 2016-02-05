/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.keystone;

public class KeystoneConstants {

    public static String KEYSTONE = "keystone";
    public static String AUTH_TOKEN = "X-Auth-Token";

    public static String BASE_URI_V2 = "/v2.0/";
    public static String URI_TOKENS = BASE_URI_V2 + "tokens";
    public static String VALIDATE_TOKEN = URI_TOKENS + "/%1$s";

}
