/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.util.Set;

@Cf("Application")
public class Application extends DataObject {

    private static final long serialVersionUID = 2559507385303958088L;

    // Description of the application
    private String description;
    // Volumes URIs in the application
    private StringSet volumes;
    // The role of the application, either COPY or DR
    private StringSet roles;
    
    public static enum ApplicationRole {
        COPY,
        DR
    }

    @Name("description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        setChanged("description");
    }

    @Name("volumes")
    public StringSet getVolumes() {
        return volumes;
    }

    public void setVolumes(StringSet volumes) {
        this.volumes = volumes;
        setChanged("volumes");
    }
    
    @Name("roles")
    public StringSet getRoles() {
        return roles;
    }
    
    public void setRoles(StringSet roles) {
        this.roles = roles;
        setChanged("roles");
    }
    
    /**
     * Add roles.
     * 
     * @param roles
     */
    public void addRoles(final Set<String> newRoles) {
        if (null == this.roles) {
            setRoles(new StringSet());
        }
        if (newRoles != null && !newRoles.isEmpty()) {
            roles.addAll(newRoles);
        }
    }
}
