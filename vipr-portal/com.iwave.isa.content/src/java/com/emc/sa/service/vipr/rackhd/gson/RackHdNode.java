package com.emc.sa.service.vipr.rackhd.gson;

public class RackHdNode {
    private String id;
    private String type;

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }

    public boolean isComputeNode() {
        return (getType() != null) && getType().equals("compute");
    }
}