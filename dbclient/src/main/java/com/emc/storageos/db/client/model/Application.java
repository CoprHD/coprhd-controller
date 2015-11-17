/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import javax.xml.bind.annotation.XmlTransient;

@Cf("Application")
public class Application extends DataObject implements ProjectResource{

    private static final long serialVersionUID = 2559507385303958088L;

    // Description of the application
    private String description;
    // Volumes URIs in the application
    private StringSet volumes;
    // project this application is associated with
    private NamedURI project;
 // Tenant who owns this application
    private NamedURI tenant;
    

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
    
    @Override
    @NamedRelationIndex(cf = "NamedRelation", type = Project.class)
    @Name("project")
    public NamedURI getProject() {
        return project;
    }

    public void setProject(NamedURI project) {
        this.project = project;
        setChanged("project");
    }
    
    @Override
    @XmlTransient
    @NamedRelationIndex(cf = "NamedRelation")
    @Name("tenant")
    public NamedURI getTenant() {
        return tenant;
    }

    public void setTenant(NamedURI tenant) {
        this.tenant = tenant;
        setChanged("tenant");
    }
}
