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
    
    /**List of authentication providers used on this zone*/
    private ArrayList<String> auth_providers;
    
    /** Use all authentication providers available */
    private boolean all_auth_providers;
    
    //zone id
    private String id;

    //access zone name
    private String name;
 
    //root directory path
    private String path;
    
    //nas type (build in Zone)
    private boolean system;
    
    //zone id
    private Integer zone_id;
    
    private String system_provider;

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
    
    public boolean isAll_auth_providers() {
        return all_auth_providers;
    }

    public void setAll_auth_providers(boolean all_auth_providers) {
        this.all_auth_providers = all_auth_providers;
    }

    public void setAuth_providers(ArrayList<String> auth_providers) {
        this.auth_providers = auth_providers;
    }
    
    public String getSystem_provider() {
        return system_provider;
    }

    public void setSystem_provider(String system_provider) {
        this.system_provider = system_provider;
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
