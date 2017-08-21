/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;

/**
 * Migration response for volume or consistency group.
 * TODO update doc for new fields
 */
@XmlAccessorType(XmlAccessType.PROPERTY)
@XmlRootElement(name = "block_migration")
public class MigrationRestRep extends DataObjectRestRep {
    private RelatedResourceRep volume;
    private RelatedResourceRep source;
    private RelatedResourceRep target;
    private RelatedResourceRep consistencyGroup;
    private RelatedResourceRep sourceSystem;
    private RelatedResourceRep targetSystem;
    private String sourceSystemSerialNumber;
    private String targetSystemSerialNumber;
    private String startTime;
    private String endTime;
    private String status;
    private String percentageDone;
    private String jobStatus;
    private Set<String> dataStoresAffected;
    private Set<String> zonesCreated;
    private Set<String> zonesReused;
    private Set<String> initiators;
    private Set<String> targetStoragePorts;
    private String label;

    /**
     * The percentage of the migration which has been completed.
     * 
     * 
     * @return The percent done for the migration.
     */
    @XmlElement(name = "percent_done")
    public String getPercentageDone() {
        return percentageDone;
    }

    public void setPercentageDone(String percentageDone) {
        this.percentageDone = percentageDone;
    }

    /**
     * The status of the last performed operation.
     * 
     * @return The job status.
     */
    @XmlElement(name = "job_status")
    public String getJobStatus() {
        return jobStatus;
    }

    public void setJobStatus(String jobStatus) {
        this.jobStatus = jobStatus;
    }

    /**
     * The source volume for the migration. This volume holds the data
     * to be migrated.
     * 
     * 
     * @return The related resource representation for the migration source.
     */
    @XmlElement(name = "source")
    public RelatedResourceRep getSource() {
        return source;
    }

    public void setSource(RelatedResourceRep source) {
        this.source = source;
    }

    /**
     * The start time of the migration.
     * 
     * 
     * @return The migration start time.
     */
    @XmlElement(name = "start_time")
    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    /**
     * The end time of the migration.
     * 
     * @return The migration end time.
     */
    @XmlElement(name = "end_time")
    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    /**
     * The status of the migration.
     * Valid values:
     * in_progress = The migration is in progress
     * complete = The migration has completed
     * paused = The migration has been paused
     * cancelled = The migration has been canceled
     * committed = The migration has been committed
     * ready = The initial state for a migration after it has been created
     * error = The migration failed
     * partially-committed = The migration is in the process of being committed
     * partially-cancelled = The migration is in the process of being canceled
     * queued = The migration is queued and awaiting execution
     * 
     * @return The migration status.
     */
    @XmlElement(name = "status")
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * The target volume for the migration. This is the volume to which
     * the data on the source volume is migrated.
     * 
     * @return The related resource representation for the migration target.
     */
    @XmlElement(name = "target")
    public RelatedResourceRep getTarget() {
        return target;
    }

    public void setTarget(RelatedResourceRep target) {
        this.target = target;
    }

    /**
     * The volume being migrated.
     * 
     * 
     * @return The related resource representation for the volume being migrated.
     */
    @XmlElement(name = "volume")
    public RelatedResourceRep getVolume() {
        return volume;
    }

    public void setVolume(RelatedResourceRep volume) {
        this.volume = volume;
    }

    /**
     * The consistency group being migrated.
     * 
     * @return The related resource representation for the consistency group being migrated.
     */
    @XmlElement(name = "consistency_group")
    public RelatedResourceRep getConsistencyGroup() {
        return consistencyGroup;
    }

    public void setConsistencyGroup(RelatedResourceRep consistencyGroup) {
        this.consistencyGroup = consistencyGroup;
    }

    /**
     * The source system for the migration.
     * 
     * @return The related resource representation for the migration source system.
     */
    @XmlElement(name = "source_system")
    public RelatedResourceRep getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(RelatedResourceRep sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    /**
     * The target System for the migration.
     * 
     * @return The related resource representation for the migration target system.
     */
    @XmlElement(name = "target_system")
    public RelatedResourceRep getTargetSystem() {
        return targetSystem;
    }

    public void setTargetSystem(RelatedResourceRep targetSystem) {
        this.targetSystem = targetSystem;
    }

    /**
     * The serial number of the migration source system.
     * 
     * @return The serial number of the migration source system.
     */
    @XmlElement(name = "source_system_serial_number")
    public String getSourceSystemSerialNumber() {
        return sourceSystemSerialNumber;
    }

    public void setSourceSystemSerialNumber(String sourceSystemSerialNumber) {
        this.sourceSystemSerialNumber = sourceSystemSerialNumber;
    }

    /**
     * The serial number of the migration target system.
     * 
     * @return The serial number of the migration target system.
     */
    @XmlElement(name = "target_system_serial_number")
    public String getTargetSystemSerialNumber() {
        return targetSystemSerialNumber;
    }

    public void setTargetSystemSerialNumber(String targetSystemSerialNumber) {
        this.targetSystemSerialNumber = targetSystemSerialNumber;
    }

    @XmlElement(name = "datastores_affected")
    public Set<String> getDataStoresAffected() {
        return dataStoresAffected;
    }

    public void setDataStoresAffected(Set<String> dataStoresAffected) {
        this.dataStoresAffected = dataStoresAffected;
    }

    @XmlElement(name = "zones_created")
    public Set<String> getZonesCreated() {
        return zonesCreated;
    }

    public void setZonesCreated(Set<String> zonesCreated) {
        this.zonesCreated = zonesCreated;
    }

    @XmlElement(name = "zones_reused")
    public Set<String> getZonesReused() {
        return zonesReused;
    }

    public void setZonesReused(Set<String> zonesReused) {
        this.zonesReused = zonesReused;
    }

    @XmlElement(name = "initiators")
    public Set<String> getInitiators() {
        return initiators;
    }

    public void setInitiators(Set<String> initiators) {
        this.initiators = initiators;
    }

    @XmlElement(name = "target_storage_ports")
    public Set<String> getTargetStoragePorts() {
        return targetStoragePorts;
    }

    public void setTargetStoragePorts(Set<String> targetStoragePorts) {
        this.targetStoragePorts = targetStoragePorts;
    }

    /**
     * The label of the migration.
     *
     * @return The migration label.
     */
    @XmlElement(name = "label")
    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
