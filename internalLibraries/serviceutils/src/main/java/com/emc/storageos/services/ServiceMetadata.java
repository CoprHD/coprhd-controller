/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.services;

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
