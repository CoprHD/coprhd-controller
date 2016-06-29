package com.emc.sa.service.vipr.rackhd.gson;

public class ViprTask {

    private String name;
    private String id;
    private ViprResource resource;
    private String state;
    private String op_id;
    
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public String getOp_id() {
        return op_id;
    }
    public void setOp_id(String op_id) {
        this.op_id = op_id;
    }
    public ViprResource getResource() {
        return resource;
    }
    public void setResource(ViprResource resource) {
        this.resource = resource;
    }
    public String getState() {
        return state;
    }
    public void setState(String state) {
        this.state = state;
    }
    public boolean isValid() {
        return (name != null) &&
                (id != null) &&
                (resource != null) &&
                (state != null) &&
                (op_id != null);
    }
}
