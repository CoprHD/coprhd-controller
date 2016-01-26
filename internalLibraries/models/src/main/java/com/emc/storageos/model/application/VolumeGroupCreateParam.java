/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.application;

import java.net.URI;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.valid.Length;

/**
 * VolumeGroup creation parameters
 */
@XmlRootElement(name = "volume_group_create")
public class VolumeGroupCreateParam {
    private String name;
    private String description;
    private Set<String> roles;
    private Set<String> parents;
    private URI sourceStorageSystem;
    private URI sourceVirtualPool;
    private String migrationType;
    private String migrationGroupBy;

    public VolumeGroupCreateParam() {
    }

    public VolumeGroupCreateParam(String name, String description, Set<String> roles) {
        this.name = name;
        this.description = description;
        this.roles = roles;
    }

    /**
     * volume group unique name
     * 
     * @valid minimum of 2 characters
     * @valid maximum of 128 characters
     */
    @XmlElement(required = true)
    @Length(min = 2, max = 128)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * volume group description
     */
    @XmlElement
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @XmlElementWrapper(name = "roles", required = true)
    /**
     * The set of supported roles for the volume group.
     * 
     * @valid COPY
     * @valid DR
     */
    @XmlElement(name = "role", required = true)
    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    /**
     * @return the parents
     */
    @XmlElement
    public Set<String> getParents() {
        return parents;
    }

    /**
     * @param parents the parent to set
     */
    public void setParents(Set<String> parents) {
        this.parents = parents;
    }

    @XmlElement(name = "sourceStorageSystem")
    public URI getSourceStorageSystem() {
        return sourceStorageSystem;
    }

    public void setSourceStorageSystem(URI sourceStorageSystem) {
        this.sourceStorageSystem = sourceStorageSystem;
    }

    @XmlElement(name = "sourceVirtualPool")
    public URI getSourceVirtualPool() {
        return sourceVirtualPool;
    }

    public void setSourceVirtualPool(URI sourceVirtualPool) {
        this.sourceVirtualPool = sourceVirtualPool;
    }

    @XmlElement(name = "migrationType")
    public String getMigrationType() {
        return migrationType;
    }

    public void setMigrationType(String migrationType) {
        this.migrationType = migrationType;
    }

    @XmlElement(name = "migrationGroupBy")
    public String getMigrationGroupBy() {
        return migrationGroupBy;
    }

    public void setMigrationGroupBy(String migrationGroupBy) {
        this.migrationGroupBy = migrationGroupBy;
    }

}
