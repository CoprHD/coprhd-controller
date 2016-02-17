/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

/**
 * Represents a volume migration operation.
 */
@ExcludeFromGarbageCollection
@Cf("Migration")
public class Migration extends DataObject {

    // The URI of the volume associated with the migration.
    private URI _volume;

    // The URI of the migration source.
    private URI _source;

    // The URI of the migration target.
    private URI _target;

    // The migration start time.
    private String _startTime;

    // The status of the migration.
    private String _migrationStatus;

    // The percentage done.
    private String _percentDone;

    /**
     * Getter for the URI of the volume being migrated.
     * 
     * @return The URI of the volume being migrated.
     */
    @RelationIndex(cf = "RelationIndex", type = Volume.class)
    @Name("volume")
    public URI getVolume() {
        return _volume;
    }

    /**
     * Setter for the URI of the volume being migrated.
     * 
     * @param name The URI of the volume being migrated.
     */
    public void setVolume(URI volume) {
        _volume = volume;
        setChanged("volume");
    }

    /**
     * Getter for the URI of the migration source.
     * 
     * @return The URI of the migration source.
     */
    @Name("source")
    public URI getSource() {
        return _source;
    }

    /**
     * Setter for the URI of the migration source.
     * 
     * @param name The URI of the migration source.
     */
    public void setSource(URI source) {
        _source = source;
        setChanged("source");
    }

    /**
     * Getter for the URI of the migration target.
     * 
     * @return The URI of the migration target.
     */
    @Name("target")
    public URI getTarget() {
        return _target;
    }

    /**
     * Setter for the URI of the migration target.
     * 
     * @param name The URI of the migration target.
     */
    public void setTarget(URI target) {
        _target = target;
        setChanged("target");
    }

    /**
     * Getter for the migration start time.
     * 
     * @return The migration start time.
     */
    @Name("startTime")
    public String getStartTime() {
        return _startTime;
    }

    /**
     * Setter for the migration start time.
     * 
     * @param name The migration start time.
     */
    public void setStartTime(String startTime) {
        _startTime = startTime;
        setChanged("startTime");
    }

    /**
     * Getter for the migration status.
     * 
     * @return The status of the migration.
     */
    @Name("migrationStatus")
    public String getMigrationStatus() {
        return _migrationStatus;
    }

    /**
     * Setter for the migration status.
     * 
     * @param status The status of the migration.
     */
    public void setMigrationStatus(String status) {
        _migrationStatus = status;
        setChanged("migrationStatus");
    }

    /**
     * Getter for the migration percentage done.
     * 
     * @return The migration percentage done.
     */
    @Name("percentDone")
    public String getPercentDone() {
        return _percentDone;
    }

    /**
     * Setter for the migration percentage done.
     * 
     * @param name The migration percentage done.
     */
    public void setPercentDone(String percentDone) {
        _percentDone = percentDone;
        setChanged("percentDone");
    }
}
