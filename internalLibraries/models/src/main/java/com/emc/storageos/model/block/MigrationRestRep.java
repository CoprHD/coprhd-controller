/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;

/**
 * Migration response for volume or consistency group.
 * TODO update doc for source, target, status
 */
@XmlAccessorType(XmlAccessType.PROPERTY)
@XmlRootElement(name = "block_migration")
public class MigrationRestRep extends DataObjectRestRep {
    private RelatedResourceRep volume;
    private RelatedResourceRep consistencyGroup;
    private RelatedResourceRep source;
    private RelatedResourceRep target;
    private String startTime;
    private String status;
    private String percentageDone;

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
}
