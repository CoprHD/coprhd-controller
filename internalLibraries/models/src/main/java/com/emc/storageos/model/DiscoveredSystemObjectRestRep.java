/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model;

import java.net.URI;
import java.util.Calendar;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.PROPERTY)
public abstract class DiscoveredSystemObjectRestRep extends DiscoveredDataObjectRestRep {
    private String systemType;
    private String discoveryJobStatus;
    private String meteringJobStatus;
    private String lastDiscoveryStatusMessage;
    private Long lastDiscoveryRunTime;
    private Long nextDiscoveryRunTime;
    private Long lastMeteringRunTime;
    private Long nextMeteringRunTime;
    private Long successDiscoveryTime;
    private Long successMeteringTime;
    private String compatibilityStatus;
    private String registrationStatus;

    public DiscoveredSystemObjectRestRep() {
    }

    public DiscoveredSystemObjectRestRep(String systemType,
            String discoveryJobStatus, String meteringJobStatus,
            String lastDiscoveryStatusMessage, Long lastDiscoveryRunTime,
            Long nextDiscoveryRunTime, Long lastMeteringRunTime,
            Long nextMeteringRunTime, String compatibilityStatus,
            String registrationStatus, String name, URI id, RestLinkRep link,
            Calendar creationTime, Boolean inactive, Set<String> tags,
            String nativeGuid) {
        super(name, id, link, creationTime, inactive, tags, nativeGuid);
        this.systemType = systemType;
        this.discoveryJobStatus = discoveryJobStatus;
        this.meteringJobStatus = meteringJobStatus;
        this.lastDiscoveryStatusMessage = lastDiscoveryStatusMessage;
        this.lastDiscoveryRunTime = lastDiscoveryRunTime;
        this.nextDiscoveryRunTime = nextDiscoveryRunTime;
        this.lastMeteringRunTime = lastMeteringRunTime;
        this.nextMeteringRunTime = nextMeteringRunTime;
        this.compatibilityStatus = compatibilityStatus;
        this.registrationStatus = registrationStatus;
    }

    /**
     * The last discovery status message for this system
     * 
     */
    @XmlElement(name = "last_discovery_status_message")
    public String getLastDiscoveryStatusMessage() {
        return lastDiscoveryStatusMessage;
    }

    public void setLastDiscoveryStatusMessage(String statusMessage) {
        lastDiscoveryStatusMessage = statusMessage;
    }

    /**
     * A short mnemonic that indicates what kind of system is being represented.
     * Valid values:
     *  brocade
     *  isilon
     *  netapp
     *  mds
     *  rp
     *  vmax
     *  vnxblock
     *  vnxfile
     *  vplex
     * 
     * 
     */
    @XmlElement(name = "system_type")
    public String getSystemType() {
        return systemType;
    }

    public void setSystemType(String systemType) {
        this.systemType = systemType;
    }

    /**
     * The status of the last discovery job for this system
     * Valid values:
     *  CREATED
     *  IN_PROGRESS
     *  COMPLETE
     *  ERROR
     *  
     */
    @XmlElement(name = "job_discovery_status")
    public String getDiscoveryJobStatus() {
        return discoveryJobStatus;
    }

    public void setDiscoveryJobStatus(String discoveryJobStatus) {
        this.discoveryJobStatus = discoveryJobStatus;
    }

    /**
     * The timestamp for the last discovery job for this system
     * 
     */
    @XmlElement(name = "last_discovery_run_time")
    public Long getLastDiscoveryRunTime() {
        return lastDiscoveryRunTime;
    }

    public void setLastDiscoveryRunTime(Long lastDiscoveryRunTime) {
        this.lastDiscoveryRunTime = lastDiscoveryRunTime;
    }

    /**
     * The timestamp for the last metric collection job for this system
     * 
     */
    @XmlElement(name = "last_metering_run_time")
    public Long getLastMeteringRunTime() {
        return lastMeteringRunTime;
    }

    public void setLastMeteringRunTime(Long lastMeteringRunTime) {
        this.lastMeteringRunTime = lastMeteringRunTime;
    }

    /**
     * The status of the last metric collection job for this system
     * Valid values:
     *  CREATED
     *  IN_PROGRESS
     *  COMPLETE
     *  ERROR
     * 
     */
    @XmlElement(name = "job_metering_status")
    public String getMeteringJobStatus() {
        return meteringJobStatus;
    }

    public void setMeteringJobStatus(String meteringJobStatus) {
        this.meteringJobStatus = meteringJobStatus;
    }

    /**
     * The timestamp for the next scheduled discovery job for this system
     * 
     */
    @XmlElement(name = "next_discovery_run_time")
    public Long getNextDiscoveryRunTime() {
        return nextDiscoveryRunTime;
    }

    public void setNextDiscoveryRunTime(Long nextDiscoveryRunTime) {
        this.nextDiscoveryRunTime = nextDiscoveryRunTime;
    }

    /**
     * The timestamp for the next scheduled metric collection job for this system
     * 
     */
    @XmlElement(name = "next_metering_run_time")
    public Long getNextMeteringRunTime() {
        return nextMeteringRunTime;
    }

    public void setNextMeteringRunTime(Long nextMeteringRunTime) {
        this.nextMeteringRunTime = nextMeteringRunTime;
    }

    /**
     * The latest timestamp when the system run Discovery successfully
     * 
     */
    @XmlElement(name = "success_discovery_time")
    public Long getSuccessDiscoveryTime() {
        return successDiscoveryTime;
    }

    public void setSuccessDiscoveryTime(Long successDiscoveryTime) {
        this.successDiscoveryTime = successDiscoveryTime;
    }

    /**
     * The latest timestamp when the system run Metering successfully
     * 
     */
    @XmlElement(name = "success_metering_time")
    public Long getSuccessMeteringTime() {
        return successMeteringTime;
    }

    public void setSuccessMeteringTime(Long successMeteringTime) {
        this.successMeteringTime = successMeteringTime;
    }

    /**
     * Whether or not the system is registered with ViPR. A system must be
     * registered before it can be managed by ViPR.
     * Valid values:
     *  REGISTERED
     *  UNREGISTERED
     */
    @XmlElement(name = "registration_status")
    public String getRegistrationStatus() {
        return registrationStatus;
    }

    public void setRegistrationStatus(String registrationStatus) {
        this.registrationStatus = registrationStatus;
    }

    /**
     * Whether or not this system is compatible with ViPR.
     * Valid values:
     *  OMPATIBLE
     *  INCOMPATIBLE
     *  UNKNOWN
     * 
     */
    @XmlElement(name = "compatibility_status")
    public String getCompatibilityStatus() {
        return compatibilityStatus;
    }

    public void setCompatibilityStatus(String compatibilityStatus) {
        this.compatibilityStatus = compatibilityStatus;
    }
}
