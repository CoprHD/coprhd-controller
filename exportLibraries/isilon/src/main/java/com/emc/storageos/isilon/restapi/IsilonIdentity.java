package com.emc.storageos.isilon.restapi;

public class IsilonIdentity {

    private String id;
    private String name;
    private String type;

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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("( Identity id: " + id);
        str.append(", type : " + type);
        str.append(",  name: " + name);
        str.append(")");
        return str.toString();
    }
}