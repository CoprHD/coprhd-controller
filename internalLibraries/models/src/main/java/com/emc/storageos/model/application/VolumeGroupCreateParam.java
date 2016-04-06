/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.application;

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
    private String parent;
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
     * Valid values: 
     *     minimum of 2 characters
     *     maximum of 128 characters
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
    @XmlElement(required = false)
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
     * Valid values:
     *     COPY
     *     DR
     */
    @XmlElement(name = "role", required = true)
    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    /**
     * @return the parent
     */
    @XmlElement
    public String getParent() {
        return parent;
    }

    /**
     * @param parent the parent to set
     */
    public void setParent(String parent) {
        this.parent = parent;
    }

    @XmlElement(name = "migration_type")
    public String getMigrationType() {
        return migrationType;
    }

    public void setMigrationType(String migrationType) {
        this.migrationType = migrationType;
    }

    @XmlElement(name = "migration_group_by")
    public String getMigrationGroupBy() {
        return migrationGroupBy;
    }

    public void setMigrationGroupBy(String migrationGroupBy) {
        this.migrationGroupBy = migrationGroupBy;
    }

}
