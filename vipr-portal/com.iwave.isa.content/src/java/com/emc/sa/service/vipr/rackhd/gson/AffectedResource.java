package com.emc.sa.service.vipr.rackhd.gson;

public class AffectedResource {
    private String id;
    private String name;

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
    public boolean isValid() {
        return (id != null) && (name != null);
    }
}
