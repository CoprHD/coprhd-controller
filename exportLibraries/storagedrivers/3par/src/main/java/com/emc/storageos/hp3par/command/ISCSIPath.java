/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.command;

public class ISCSIPath {
    private String name;
    private Position portPos;
    
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public Position getPortPos() {
        return portPos;
    }
    public void setPortPos(Position portPos) {
        this.portPos = portPos;
    }
}
