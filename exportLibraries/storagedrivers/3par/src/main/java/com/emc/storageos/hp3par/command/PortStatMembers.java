/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.command;

public class PortStatMembers {
    private Integer node;
    private Integer slot;
    private Integer cardPort;
    private Integer type;
    private Integer speed;
    
    public Integer getNode() {
        return node;
    }
    public void setNode(Integer node) {
        this.node = node;
    }
    public Integer getSlot() {
        return slot;
    }
    public void setSlot(Integer slot) {
        this.slot = slot;
    }
    public Integer getCardPort() {
        return cardPort;
    }
    public void setCardPort(Integer cardPort) {
        this.cardPort = cardPort;
    }
    public Integer getType() {
        return type;
    }
    public void setType(Integer type) {
        this.type = type;
    }
    public Integer getSpeed() {
        return speed;
    }
    public void setSpeed(Integer speed) {
        this.speed = speed;
    }
}
