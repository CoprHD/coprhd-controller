/*
 * Copyright (c) 2018 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.isilon.restapi;

import java.net.URI;

public class IsilonApiConstants {
	
    public static enum AuthType {
        BASIC,	// Isilon Basic Authentication
        CSRF	// Isilon Session Authentication with CSRF Protection
    }
	
    // Constants define the headers required when making HTTP requests to the
    // Isilon Management Station using the OneFS API.
    public static final URI URI_SESSION = URI.create("/session/1/session");
    public static final String AUTH_TYPE = "AuthType";
    public static final String SESSION_COOKIE = "isisessid";
    public static final String CSRF_COOKIE = "isicsrf";
    public static final String CSRF_HEADER = "X-CSRF-Token";
    public static final String REFERER_HEADER = "referer";
}