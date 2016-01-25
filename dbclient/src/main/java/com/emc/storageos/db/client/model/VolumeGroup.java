/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;
import java.util.Set;

@Cf("VolumeGroup")
public class VolumeGroup extends DataObject {

    private static final long serialVersionUID = 2559507385303958088L;

    // Description of the volume group
    private String description;

    // The role of the volume group, either COPY or DR
    private StringSet roles;
    
    // parent volume group
    private URI parent;

    public static enum VolumeGroupRole {
        COPY,
        DR,
        MOBILITY
    }

    @Name("description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        setChanged("description");
    }

    @Name("roles")
    public StringSet getRoles() {
        return roles;
    }

    public void setRoles(StringSet theRoles) {
        this.roles = theRoles;
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

    /**
     * @return the parent
     */
    @RelationIndex(cf = "VolumeGroupParent", type = VolumeGroup.class)
    @Name("parent")
    public URI getParent() {
        return parent;
    }

    /**
     * @param parent the parent to set
     */
    public void setParent(URI parent) {
        this.parent = parent;
        setChanged("parent");
    }
}
