package com.emc.sa.service.vipr.rackhd.gson;

public class Node {
    private String id;
    private String type;
    private String name;

    public String getId() {
        return id;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
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