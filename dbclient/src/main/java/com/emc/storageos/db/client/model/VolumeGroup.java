/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;


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

    public static class MigrationOptions {

        // private List<String> unamagedVolumeWWNs;
        private URI targetStorageSystem;

        @XmlElement(name = "target_storage_system")
        public URI getTargetStorageSystem() {
            return targetStorageSystem;
        }

        public void setTargetStorageSystem(URI targetStorageSystem) {
            this.targetStorageSystem = targetStorageSystem;
        }
    }

    private MigrationOptions migrationOptions;

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

    /**
     * Migration Attributes of the volume group
     * 
     */
    @XmlElement(name = "migration_attributes")
    public MigrationOptions getMigrationOptions() {
        return migrationOptions;
    }

    public void setMigrationOptions(MigrationOptions migrationOptions) {
        this.migrationOptions = migrationOptions;
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
}
