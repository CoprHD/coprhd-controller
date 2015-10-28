/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.isilon.restapi;
import java.util.ArrayList;
/*
 * Class representing the isilon AccessZone object
 * member names should match the key names in json object
 * 
 */
public class IsilonAccessZone {
    
    //authenticate provider
    private ArrayList<String> auth_providers;
    
    //nas id
    private String id;

    private String name;
    

    //root directory path
    private String path;

    
    //nas type
    private boolean system;
    
    //zone id
    private Integer zone_id;

    public ArrayList<String> getAuth_providers() {
        return auth_providers;
    }

    public String getPath() {
        return path;
    }

    public Integer getZone_id() {
        return zone_id;
    }

    public boolean isSystem() {
        return system;
    }

    public String getName() {
        return name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setSystem(boolean system) {
        this.system = system;
    }

    public void setZone_id(Integer zone_id) {
        this.zone_id = zone_id;
    }

    public void setAuth_providers(ArrayList<String> auth_providers) {
        this.auth_providers = auth_providers;
    }

    @Override
    public String toString() {
        return "IsilonAccessZone{" +
                "auth_providers=" + auth_providers +
                ", id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", path='" + path + '\'' +
                ", system=" + system +
                ", zone_id=" + zone_id +
                '}';
    }
}
