/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.command;

public class Position {
    private Integer node;
    private Integer slot;
    private Integer cardPort;
    
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
    
    @Override
    public String toString() {
        return "Position [node=" + node + ", slot=" + slot + ", cardPort=" + cardPort + "]";
    }
}
