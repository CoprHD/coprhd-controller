package com.emc.sa.service.vipr.oe.gson;

public class ViprResource {

    private String name;
    private String id;

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
    
    public boolean isValid() {
    	return getName() != null && getId() != null;
    }
}
