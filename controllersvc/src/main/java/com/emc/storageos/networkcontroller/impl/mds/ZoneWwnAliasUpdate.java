/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.networkcontroller.impl.mds;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.db.client.util.EndpointUtility;
import com.emc.storageos.model.valid.Endpoint.EndpointType;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

public class ZoneWwnAliasUpdate extends ZoneWwnAlias {
    private String newName;
    private String newAddress;

    public ZoneWwnAliasUpdate() {
        // TODO Auto-generated constructor stub
    }

    public ZoneWwnAliasUpdate(String name, String newName, String newAddress, String oldAddress) {
        super(name, oldAddress);
        setNewName(newName);
        setNewAddress(newAddress);
    }
    
    public String getNewName() {
        return newName;
    }

    public void setNewName(String newName) {
        this.newName = newName;
    }

    public String getNewAddress() {
        return this.newAddress;
    }
    
    public void setNewAddress(String newAddress) {
        if ( StringUtils.isEmpty(newAddress) ) 
            return;
        
        if ( EndpointUtility.isValidEndpoint(newAddress, EndpointType.WWN)) {      
            this.newAddress =  EndpointUtility.changeCase(newAddress) ;
        } else {
            throw APIException.badRequests.illegalWWN(newAddress);         
        }     
    }    
}
