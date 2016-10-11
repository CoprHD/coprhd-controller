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

    private String migrationGroupBy;

    private String migrationStatus;


    public static enum MigrationGroupBy {
        VOLUMES,
        HOSTS,
        CLUSTERS,
        STORAGEGROUP
    }

    public static enum VolumeGroupRole {
        COPY,
        DR,
        MOBILITY,
        APPLICATION
    }

    public static enum MigrationType {
        VPLEX,
        VMAX
    }

    public static enum MigrationStatus {
        NONE,
        CREATED,
        MIGRATEINPROGESS,
        MIGRATIONFAILED,
        MIGRATED,
        COMMITINPROGRESS,
        COMMITFAILED,
        COMMITCOMPLETED,
        OTHER
    }

    // migration options is a name value pair
    public static final String MIGRATION_TARGET_STORAGE_SYSTEM = "target_storage_system";
    private StringMap migrationOptions;

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

    @Name("migrationStatus")
    public String getMigrationStatus() {
        return migrationStatus;
    }

    public void setMigrationStatus(String migrationStatus) {
        this.migrationStatus = migrationStatus;
        setChanged("migrationStatus");
    }

    @Name("migrationGroupBy")

    public String getMigrationGroupBy() {
        return migrationGroupBy;
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

    /**
     * @return the migrationOptions
     */
    @Name("migrationOptions")
    public StringMap getMigrationOptions() {
        return migrationOptions;
    }

    /**
     * @param migrationOptions the migrationOptions to set
     */
    public void setMigrationOptions(StringMap migrationOptions) {
        this.migrationOptions = migrationOptions;
    }
}
