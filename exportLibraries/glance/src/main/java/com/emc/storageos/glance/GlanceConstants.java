/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.glance;

public interface GlanceConstants {
    
    String HTTPS_URL = "https://";
    String HTTP_URL = "http://";
    String COLON = ":";
    String HYPHEN = "-";
    char   CHAR_HYPHEN = '-';
    String DEFAULT = "DEFAULT";
    
    public static String GLANCE_SSH_PORT = "22";
    public static String GLANCE_REST_PORT = "9292";  
    public static String AUTH_TOKEN_HEADER = "X-Auth-Token";
    public static String REST_API_VERSION_1 = "/v1";
    public static String REST_API_VERSION_2 = "/v2";
    public static String DEFAULT_API_VERSION = REST_API_VERSION_1;  // default version V1
    public static String IMAGE_NAME = "/images/";
        
    public final static String GLANCE_PORT_GROUP = "Glance-PortGroup";        
    
    /*
     * Enum types for the status check of the components
     * 
     */
    public static enum ComponentStatus 
    {
    	
    	AVAILABLE ("Available"),
    	ERROR ("Error"),
    	DOWNLOADING ("Downloading");
    		
    	public String status = "";
    	ComponentStatus(String status)
    	{
    		this.status = status;
    	}
    	
    	public String getStatus()
    	{
    		return this.status;
    	}
    }
    
    
}
