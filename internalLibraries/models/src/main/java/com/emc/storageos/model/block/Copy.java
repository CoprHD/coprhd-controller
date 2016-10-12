/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.io.Serializable;
import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Specifies the copy to be operated on
 *
 * type: type of protection (rp, native, srdf)
 * sync: synchronize the mirror
 * copyID: the URI of the copy to be operated on, if none specified operate on all copies for that type
 * name: name of a new mirror being created by start operation.
 * name in the case of a failover operation.
 * count: number of mirrors to create using start operation
 * pointInTime: any point-in-time - used for the failover operation
 */
@XmlRootElement(name = "copy")
public class Copy implements Serializable {

    private static final long serialVersionUID = -8250892549720042299L;

    private String type;
    private String sync;
    private URI copyID;
    private String name;
    private Integer count;
    private String syncDirection;
    private String copyMode;
    // Format: "yyyy-MM-dd_HH:mm:ss" or datetime in milliseconds
    private String pointInTime;
    // The desired access mode for the target. Applies to RecoverPoint copies.
    private String accessMode;

    public enum SyncDirection {
        SOURCE_TO_TARGET,
        TARGET_TO_SOURCE
    }

    public enum ImageAccessMode {
        DIRECT_ACCESS
    }

    public Copy() {
    }

    public Copy(String type, String sync, URI copyID, String name, Integer count) {
        this.type = type;
        this.sync = sync;
        this.copyID = copyID;
        this.name = name;
        this.count = count;
    }

    /**
     * When pausing continuous copies, optionally specify if synchronization is required.
     */
    @XmlElement(name = "sync", required = false, defaultValue = "false")
    public String getSync() {
        return sync;
    }

    public void setSync(String sync) {
        this.sync = sync;
    }

    /**
     * Type of protection.
     *
     * Valid values:
     * <ul>
     *     <li>NATIVE</li>
     *     <li>SRDF</li>
     *     <li>RP</li>
     * </ul>
     */
    @XmlElement(name = "type", required = true)
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * ViPR ID of the continuous copy.  Not required when creating continuous copies.
     *
     * When operating on existing continuous copies in a consistency group, omitting this field
     * will cause ViPR to act on all copies in the consistency group.
     */
    @XmlElement(name = "copyID", required = false)
    public URI getCopyID() {
        return copyID;
    }

    public void setCopyID(URI copyID) {
        this.copyID = copyID;
    }

    /**
     * User provided name.  Required when creating a continuous copy.
     */
    @XmlElement(name = "name", required = false)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * User provided number of copies.
     *
     */
    @XmlElement(name = "count", required = false, defaultValue = "1")
    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    /**
     * User provided direction for the synchronization.
     * Valid values SOURCE_TO_TARGET, TARGET_TO_SOURCE
     *
     * @return The Sync Direction
     */
    @XmlElement(name = "syncDirection", required = false)
    public String getSyncDirection() {
        return syncDirection;
    }

    public void setSyncDirection(String syncDirection) {
        this.syncDirection = syncDirection;
    }

    /**
     * User provided SRDF copy mode for the synchronization.
     * Valid values:
     * SYNCHRONOUS = Change SRDf copy mode to SYNCHRONOUS
     * ASYNCHRONOUS = Change SRDf copy mode to ASYNCHRONOUS
     * ADAPTIVECOPY = Change SRDf copy mode to ADAPTIVECOPY
     *
     * @return
     */
    @XmlElement(name = "copyMode", required = false)
    public String getCopyMode() {
        return copyMode;
    }

    public void setCopyMode(String copyMode) {
        this.copyMode = copyMode;
    }

    /**
     * User provided any point-in-time for copy operations.
     * Valid value: "yyyy-MM-dd_HH:mm:ss" formatted date or datetime in milliseconds.
     *
     * @return the UTC date/time "yyyy-MM-dd_HH:mm:ss" or milliseconds.
     */
    @XmlElement(name = "pointInTime", required = false)
    public String getPointInTime() {
        return pointInTime;
    }

    public void setPointInTime(String pointInTime) {
        this.pointInTime = pointInTime;
    }

    @XmlElement(name = "accessMode", required = false)
    public String getAccessMode() {
        return accessMode;
    }

    public void setAccessMode(String accessMode) {
        this.accessMode = accessMode;
    }
}
