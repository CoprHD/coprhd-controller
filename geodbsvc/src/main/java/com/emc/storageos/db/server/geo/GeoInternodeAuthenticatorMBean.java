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

package com.emc.storageos.db.server.geo;

import java.util.List;

/**
 * The API for blacklist
 */
public interface GeoInternodeAuthenticatorMBean {
    static final String MBEAN_NAME = "com.emc.storageos.db.server.geo:type=GeoInternodeAuthenticator";
	
    /**
     * Add node IPs to blacklist
     * 
     * @param newBlacklist
     */
    void addToBlacklist(List<String> newBlacklist); 
	
    /**
     * Remove give list of node IP from blacklist
     * 
     * @param nodeList
     */
    void removeFromBlacklist(List<String> nodeList);
     
     /**
      * Get list of node IP in blacklist
      * 
      * @return
      */
    List<String> getBlacklist(); 

}
