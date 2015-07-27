/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.services;

import java.util.Set;

/**
 * Class that defines service properties like its name, port..
 */
public class ServiceMetadata {

    private String name;
    private int port = -1;
    private boolean isControlNodeService = false;
    private boolean isExtraNodeService = false;
    private String roles;
    
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isControlNodeService() {
        return isControlNodeService;
    }

    public void setIsControlNodeService(boolean isControlNodeService) {
        this.isControlNodeService = isControlNodeService;
    }

    public boolean isExtraNodeService() {
        return isExtraNodeService;
    }

    public void setIsExtraNodeService(boolean isExtraNodeService) {
        this.isExtraNodeService = isExtraNodeService;
    }
    
    public String getRoles() {
        return this.roles;
    }
    
    public void setRoles(String roles) {
        this.roles = roles;
    }
}
