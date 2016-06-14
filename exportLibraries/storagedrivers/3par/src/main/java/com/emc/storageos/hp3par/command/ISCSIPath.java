/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.command;

public class ISCSIPath {
    private String name;
    private Position postPos;
    
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public Position getPostPos() {
        return postPos;
    }
    public void setPostPos(Position postPos) {
        this.postPos = postPos;
    }
}
