/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.command;

import java.util.ArrayList;

public class HostSetDetailsCommandResult {
    private String id;
    private String name;
    private ArrayList<String> setmembers;
    
    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public ArrayList<String> getSetmembers() {
        return setmembers;
    }
    public void setSetmembers(ArrayList<String> setmembers) {
        this.setmembers = setmembers;
    }
}
