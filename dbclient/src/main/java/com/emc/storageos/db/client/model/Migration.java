/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;
import java.util.Set;

/**
 * Represents a migration operation for volume or consistency group.
 * For volume migration, source and target fields represents volume.
 * For consistency group migration, source and target represents storage systems.
 */
@ExcludeFromGarbageCollection
@Cf("Migration")
public class Migration extends DataObject {

    // The URI of the volume associated with the migration.
    private URI _volume;

    // The URI of the migration source (volume)
    private URI _source;

    // The URI of the migration target (volume)
    private URI _target;

    // The URI of the consistency group associated with the migration.
    private URI _consistencyGroup;

    // The URI of the migration source system
    private URI _sourceSystem;

    // The URI of the migration target system
    private URI _targetSystem;

    // The Serial number of the migration source system
    private String _sourceSystemSerialNumber;

    // The Serial number of the migration target system
    private String _targetSystemSerialNumber;

    // The migration start time.
    private String _startTime;

    // The migration end time.
    private String _endTime;

    // The status of the migration.
    private String _migrationStatus;

    // The percentage done.
    private String _percentDone;

    // The status of the last performed job.
    private String _jobStatus;

    // The list of data stores affected.
    private StringSet _dataStoresAffected;

    // The list of SAN zones created.
    private StringSet _zonesCreated;

    // The list of SAN zones re-used.
    private StringSet _zonesReused;

    // The list of initiators involved in migration.
    private StringSet _initiators;

    // The list of target storage ports involved in migration.
    private StringSet _targetStoragePorts;
    
    //Host or Cluster URI
    private URI computeURI;

    public static enum JobStatus {
        CREATED,
        IN_PROGRESS,
        COMPLETE,
        ERROR
    }

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
     * Getter for the URI of the consistency group being migrated.
     * 
     * @return The URI of the consistency group being migrated.
     */
    @RelationIndex(cf = "MigrationIndex", type = BlockConsistencyGroup.class)
    @Name("consistencyGroup")
    public URI getConsistencyGroup() {
        return _consistencyGroup;
    }

    /**
     * Setter for the URI of the consistency group being migrated.
     * 
     * @param name The URI of the consistency group being migrated.
     */
    public void setConsistencyGroup(URI consistencyGroup) {
        _consistencyGroup = consistencyGroup;
        setChanged("consistencyGroup");
    }

    /**
     * Getter for the URI of the migration source system.
     * 
     * @return The URI of the migration source system.
     */
    @RelationIndex(cf = "MigrationIndex", type = StorageSystem.class)
    @Name("sourceSystem")
    public URI getSourceSystem() {
        return _sourceSystem;
    }

    /**
     * Setter for the URI of the migration source system.
     * 
     * @param name The URI of the migration source system.
     */
    public void setSourceSystem(URI sourceSystem) {
        _sourceSystem = sourceSystem;
        setChanged("sourceSystem");
    }

    /**
     * Getter for the URI of the migration target system.
     * 
     * @return The URI of the migration target system.
     */
    @Name("targetSystem")
    public URI getTargetSystem() {
        return _targetSystem;
    }

    /**
     * Setter for the URI of the migration target system.
     * 
     * @param name The URI of the migration target system.
     */
    public void setTargetSystem(URI targetSystem) {
        _targetSystem = targetSystem;
        setChanged("targetSystem");
    }

    /**
     * Getter for the serial number of the migration source system.
     * 
     * @return The serial number of the migration source system.
     */
    @Name("sourceSystemSerialNumber")
    public String getSourceSystemSerialNumber() {
        return _sourceSystemSerialNumber;
    }

    /**
     * Setter for the serial number of the migration source system.
     * 
     * @param name The serial number of the migration source system.
     */
    public void setSourceSystemSerialNumber(String sourceSystemSerialNumber) {
        _sourceSystemSerialNumber = sourceSystemSerialNumber;
        setChanged("sourceSystemSerialNumber");
    }

    /**
     * Getter for the serial number of the migration target system.
     * 
     * @return The serial number of the migration target system.
     */
    @Name("targetSystemSerialNumber")
    public String getTargetSystemSerialNumber() {
        return _targetSystemSerialNumber;
    }

    /**
     * Setter for the serial number of the migration target system.
     * 
     * @param name The serial number of the migration target system.
     */
    public void setTargetSystemSerialNumber(String targetSystemSerialNumber) {
        _targetSystemSerialNumber = targetSystemSerialNumber;
        setChanged("targetSystemSerialNumber");
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
     * Getter for the migration end time.
     * 
     * @return The migration end time.
     */
    @Name("endTime")
    public String getEndTime() {
        return _endTime;
    }

    /**
     * Setter for the migration end time.
     * 
     * @param name The migration end time.
     */
    public void setEndTime(String endTime) {
        _endTime = endTime;
        setChanged("endTime");
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

    /**
     * Getter for the job status.
     * 
     * @return The job status.
     */
    @Name("jobStatus")
    public String getJobStatus() {
        return _jobStatus;
    }

    /**
     * Setter for the job status.
     * 
     * @param name The job status.
     */
    public void setJobStatus(String jobStatus) {
        _jobStatus = jobStatus;
        setChanged("jobStatus");
    }

    /**
     * Gets the data stores affected.
     *
     * @return the data stores affected
     */
    @Name("dataStoresAffected")
    public StringSet getDataStoresAffected() {
        if(null == _dataStoresAffected) _dataStoresAffected = new StringSet();
        return _dataStoresAffected;
    }

    /**
     * Sets the data stores affected.
     *
     * @param dataStoresAffected the new data stores affected
     */
    public void setDataStoresAffected(StringSet dataStoresAffected) {
        _dataStoresAffected = dataStoresAffected;
        setChanged("dataStoresAffected");
    }

    /**
     * Gets the zones created.
     *
     * @return the zones created
     */
    @Name("zonesCreated")
    public StringSet getZonesCreated() {
        if(null == _zonesCreated) _zonesCreated = new StringSet();
        return _zonesCreated;
    }

    /**
     * Sets the zones created.
     *
     * @param zonesCreated the new zones created
     */
    public void setZonesCreated(StringSet zonesCreated) {
        zonesCreated = _zonesCreated;
        setChanged("zonesCreated");
    }
    
    /**
     * add Created zones
     * @param createdZones
     */
    public void addZonesCreated(Set<String> createdZones) {
        getZonesCreated().addAll(createdZones);
    }
    
    

    /**
     * Gets the zones reused.
     *
     * @return the zones reused
     */
    @Name("zonesReused")
    public StringSet getZonesReused() {
        if(null == _zonesReused) _zonesReused = new StringSet();
        return _zonesReused;
    }

    /**
     * Sets the zones reused.
     *
     * @param zonesReused the new zones reused
     */
    public void setZonesReused(StringSet zonesReused) {
        _zonesReused = zonesReused;
        setChanged("zonesReused");
    }
    
    /**
     * Add Reused Zones
     * @param reusedZones
     */
    public void addReUsedZones(Set<String> reusedZones) {
        getZonesReused().addAll(reusedZones);
    }

    /**
     * Gets the initiators.
     *
     * @return the initiators
     */
    @Name("initiators")
    public StringSet getInitiators() {
        if(null == _initiators) _initiators = new StringSet();
        return _initiators;
    }

    /**
     * Sets the initiators.
     *
     * @param initiators the new initiators
     */
    public void setInitiators(StringSet initiators) {
        _initiators = initiators;
        setChanged("initiators");
    }
    
    /**
     * Add Initiators
     * @param initiators
     */
    public void addInitiators(Set<String> initiators) {
        getInitiators().addAll(initiators);
    }
    
    /**
     * Add Initiator
     * @param initiator
     */
    public void addInitiator(String initiator) {
        getInitiators().add(initiator);
    }

    /**
     * Gets the target storage ports.
     *
     * @return the target storage ports
     */
    @Name("targetStoragePorts")
    public StringSet getTargetStoragePorts() {
        if(null == _targetStoragePorts) _targetStoragePorts = new StringSet();
        return _targetStoragePorts;
    }

    /**
     * Sets the target storage ports.
     *
     * @param targetStoragePorts the new target storage ports
     */
    public void setTargetStoragePorts(StringSet targetStoragePorts) {
        _targetStoragePorts = targetStoragePorts;
        setChanged("targetStoragePorts");
    }
    
    /**
     * Add Target Storage Ports
     * @param ports
     */
    public void addStoragePorts(Set<String> ports) {
        getTargetStoragePorts().addAll(ports);
    }

    @AlternateId("MigrationComputeIndex")
    @Name("compute")
    public URI getComputeURI() {
        return computeURI;
    }

    public void setComputeURI(URI computeURI) {
        this.computeURI = computeURI;
        setChanged("compute");
    }
}
