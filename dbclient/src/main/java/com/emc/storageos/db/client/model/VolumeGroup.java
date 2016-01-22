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

    // The role of the volume group, either COPY, DR, or MOBILITY
    private StringSet roles;

    // parent volume group
    private URI parent;

    private String migrationType;

    private URI sourceStorageSystem;

    private URI sourceVirtualPool;

    private String migrationGroupBy;

    public static enum MigrationGroupBy {
        VOLUMES,
        HOSTS,
        CLUSTERS,
        APPLICATIONS
    }

    public static enum VolumeGroupRole {
        COPY,
        DR,
        MOBILITY
    }

    public static enum MigrationType {
        VPLEX
    }

    @Name("sourceStorageSystem")
    public URI getSourceStorageSystem() {
        return sourceStorageSystem;
    }

    public void setSourceStorageSystem(URI sourceStorageSystem) {
        this.sourceStorageSystem = sourceStorageSystem;
        setChanged("sourceStorageSystem");
    }

    @Name("sourceVirtualPool")
    public URI getSourceVirtualPool() {
        return sourceVirtualPool;
    }

    public void setSourceVirtualPool(URI sourceVirtualPool) {
        this.sourceVirtualPool = sourceVirtualPool;
        setChanged("sourceVirtualPool");
    }

    @Name("migrationGroupBy")
    public String getMigrationGroupBy() {
        return migrationGroupBy;
    }

    public void setMigrationGroupBy(String migrationGroupBy) {
        this.migrationGroupBy = migrationGroupBy;
        setChanged("migrationGroupBy");
    }

    @Name("migrationType")
    public String getMigrationType() {
        return migrationType;
    }

    public void setMigrationType(String migrationType) {
        this.migrationType = migrationType;
        setChanged("migrationType");
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
