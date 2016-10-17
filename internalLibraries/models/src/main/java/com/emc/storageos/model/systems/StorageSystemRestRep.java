/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.systems;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DiscoveredSystemObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.StringHashMapEntry;

@XmlRootElement(name = "storage_system")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class StorageSystemRestRep extends DiscoveredSystemObjectRestRep {
    private String serialNumber;
    private String majorVersion;
    private String minorVersion;
    private String ipAddress;
    private Set<String> secondaryIPs;
    private Integer portNumber;
    private String smisProviderIP;
    private Integer smisPortNumber;
    private String smisUserName;
    private String smisConnectionStatus;
    private Boolean smisUseSSL;
    private List<StringHashMapEntry> exportMasks;
    private Set<String> protocols;
    private Boolean reachableStatus;
    private String firmwareVersion;
    private RelatedResourceRep activeProvider;
    private List<RelatedResourceRep> providers;
    private String username;
    private String model;
    private Set<String> associatedSystems;
    private String supportedProvisioningType;
    private Set<String> supportedAsynchronousActions;
    private Integer maxResources;
    private Integer numResources;
    private Set<String> supportedReplicationTypes;
    private Set<String> remotelyConnectedTo;
    private Boolean hasSRDFActiveRAGroups;
    private Double averagePortMetrics;
    private Boolean supportsSoftLimit = false;
    private Boolean supportsNotificationLimit = false;
    private String arrayAffinityJobStatus;
    private String lastArrayAffinityStatusMessage;
    private Long lastArrayAffinityRunTime;
    private Long nextArrayAffinityRunTime;
    private Long successArrayAffinityTime;

    public StorageSystemRestRep() {
    }

    // TODO: We should change this to drop the _uri from the name. It is not a URI
    @XmlElement(name = "active_provider_uri")
    public RelatedResourceRep getActiveProvider() {
        return activeProvider;
    }

    public void setActiveProvider(RelatedResourceRep activeProvider) {
        this.activeProvider = activeProvider;
    }

    @Deprecated
    @XmlElementWrapper(name = "associated_systems")
    @XmlElement(name = "associated_system")
    public Set<String> getAssociatedSystems() {
        if (associatedSystems == null) {
            associatedSystems = new LinkedHashSet<String>();
        }
        return associatedSystems;
    }

    @Deprecated
    public void setAssociatedSystems(Set<String> associatedSystems) {
        this.associatedSystems = associatedSystems;
    }

    @XmlElementWrapper(name = "export_masks")
    @XmlElement(name = "export_mask")
    public List<StringHashMapEntry> getExportMasks() {
        if (exportMasks == null) {
            exportMasks = new ArrayList<StringHashMapEntry>();
        }
        return exportMasks;
    }

    public void setExportMasks(List<StringHashMapEntry> exportMasks) {
        this.exportMasks = exportMasks;
    }

    @XmlElement(name = "firmware_version")
    public String getFirmwareVersion() {
        return firmwareVersion;
    }

    public void setFirmwareVersion(String firmwareVersion) {
        this.firmwareVersion = firmwareVersion;
    }

    @XmlElement(name = "ip_address")
    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    @XmlElement(name = "major_version")
    public String getMajorVersion() {
        return majorVersion;
    }

    public void setMajorVersion(String majorVersion) {
        this.majorVersion = majorVersion;
    }

    @XmlElement(name = "minor_version")
    public String getMinorVersion() {
        return minorVersion;
    }

    public void setMinorVersion(String minorVersion) {
        this.minorVersion = minorVersion;
    }

    @XmlElement(name = "model")
    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    @XmlElement(name = "port_number")
    public Integer getPortNumber() {
        return portNumber;
    }

    public void setPortNumber(Integer portNumber) {
        this.portNumber = portNumber;
    }

    @XmlElementWrapper(name = "protocols")
    @XmlElement(name = "protocol")
    public Set<String> getProtocols() {
        if (protocols == null) {
            protocols = new LinkedHashSet<String>();
        }
        return protocols;
    }

    public void setProtocols(Set<String> protocols) {
        this.protocols = protocols;
    }

    @XmlElementWrapper(name = "smis_providers")
    @XmlElement(name = "smis_provider")
    public List<RelatedResourceRep> getProviders() {
        if (providers == null) {
            providers = new ArrayList<RelatedResourceRep>();
        }
        return providers;
    }

    public void setProviders(List<RelatedResourceRep> providers) {
        this.providers = providers;
    }

    @XmlElement(name = "reachable")
    public Boolean getReachableStatus() {
        return reachableStatus;
    }

    public void setReachableStatus(Boolean reachableStatus) {
        this.reachableStatus = reachableStatus;
    }

    @XmlElementWrapper(name = "secondary_ips")
    @XmlElement(name = "secondary_ip")
    public Set<String> getSecondaryIPs() {
        if (secondaryIPs == null) {
            secondaryIPs = new LinkedHashSet<String>();
        }
        return secondaryIPs;
    }

    public void setSecondaryIPs(Set<String> secondaryIPs) {
        this.secondaryIPs = secondaryIPs;
    }

    @XmlElement(name = "serial_number")
    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    @XmlElement(name = "smis_port_number")
    public Integer getSmisPortNumber() {
        return smisPortNumber;
    }

    public void setSmisPortNumber(Integer smisPortNumber) {
        this.smisPortNumber = smisPortNumber;
    }

    @XmlElement(name = "smis_provider_ip")
    public String getSmisProviderIP() {
        return smisProviderIP;
    }

    public void setSmisProviderIP(String smisProviderIP) {
        this.smisProviderIP = smisProviderIP;
    }

    @XmlElement(name = "smis_use_ssl")
    public Boolean getSmisUseSSL() {
        return smisUseSSL;
    }

    public void setSmisUseSSL(Boolean smisUseSSL) {
        this.smisUseSSL = smisUseSSL;
    }

    @XmlElement(name = "user_name")
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @XmlElement(name = "supported_provisioning_type")
    public String getSupportedProvisioningType() {
        return supportedProvisioningType;
    }

    public void setSupportedProvisioningType(String supportedProvisioningType) {
        this.supportedProvisioningType = supportedProvisioningType;
    }

    @XmlElementWrapper(name = "async_actions")
    @XmlElement(name = "async_action")
    public Set<String> getSupportedAsynchronousActions() {
        if (associatedSystems == null) {
            supportedAsynchronousActions = new LinkedHashSet<String>();
        }
        return supportedAsynchronousActions;
    }

    public void setSupportedAsynchronousActions(Set<String> supportedAsynchronousActions) {
        this.supportedAsynchronousActions = supportedAsynchronousActions;
    }

    @XmlElement(name = "smis_user_name")
    public String getSmisUserName() {
        return smisUserName;
    }

    public void setSmisUserName(String smisUserName) {
        this.smisUserName = smisUserName;
    }

    @XmlElement(name = "smis_connection_status")
    public String getSmisConnectionStatus() {
        return smisConnectionStatus;
    }

    public void setSmisConnectionStatus(String smisConnectionStatus) {
        this.smisConnectionStatus = smisConnectionStatus;
    }

    @XmlElement(name = "max_resources")
    public Integer getMaxResources() {
        return maxResources;
    }

    public void setMaxResources(Integer maxResources) {
        this.maxResources = maxResources;
    }

    @XmlElement(name = "num_resources")
    public Integer getNumResources() {
        return numResources;
    }

    public void setNumResources(Integer numResources) {
        this.numResources = numResources;
    }

    @XmlElementWrapper(name = "supported_replication_types")
    @XmlElement(name = "supported_replication_type")
    public Set<String> getSupportedReplicationTypes() {
        return supportedReplicationTypes;
    }

    public void setSupportedReplicationTypes(Set<String> supportedReplicationTypes) {
        if (null == supportedReplicationTypes) {
            supportedReplicationTypes = new LinkedHashSet<String>();
        }
        this.supportedReplicationTypes = supportedReplicationTypes;
    }

    @XmlElementWrapper(name = "connected_systems")
    @XmlElement(name = "connected_system")
    public Set<String> getRemotelyConnectedTo() {
        return remotelyConnectedTo;
    }

    public void setRemotelyConnectedTo(Set<String> remotelyConnectedTo) {
        if (null == remotelyConnectedTo) {
            remotelyConnectedTo = new LinkedHashSet<String>();
        }
        this.remotelyConnectedTo = remotelyConnectedTo;
    }

    @XmlElement(name = "srdf_active_ra_groups_exist")
    public Boolean getHasSRDFRAGroups() {
        return hasSRDFActiveRAGroups;
    }

    public void setHasSRDFRAGroups(Boolean hasSRDFRAGroups) {
        this.hasSRDFActiveRAGroups = hasSRDFRAGroups;
    }

    @XmlElement(name = "average_port_metrics")
    public Double getAveragePortMetrics() {
        return averagePortMetrics;
    }

    public void setAveragePortMetrics(Double averagePortMetrics) {
        this.averagePortMetrics = averagePortMetrics;
    }

    @XmlElement(name = "supports_soft_limit", required = false)
    public Boolean getSupportsSoftLimit() {
        return supportsSoftLimit;
    }

    public void setSupportsSoftLimit(Boolean supportsSoftLimit) {
        this.supportsSoftLimit = supportsSoftLimit;
    }

    @XmlElement(name = "supports_notification_limit", required = false)
    public Boolean getSupportsNotificationLimit() {
        return supportsNotificationLimit;
    }

    public void setSupportsNotificationLimit(Boolean supportsNotificationLimit) {
        this.supportsNotificationLimit = supportsNotificationLimit;
    }

    /**
     * The status of the last array affinity job for this system
     * Valid values:
     *  CREATED
     *  IN_PROGRESS
     *  COMPLETE
     *  ERROR
     */
    @XmlElement(name = "job_arrayaffinity_status")
    public String getArrayAffinityJobStatus() {
        return arrayAffinityJobStatus;
    }

    public void setArrayAffinityJobStatus(String arrayAffinityJobStatus) {
        this.arrayAffinityJobStatus = arrayAffinityJobStatus;
    }

    /**
     * The last array affinity status message for this system
     */
    @XmlElement(name = "last_arrayaffinity_status_message")
    public String getLastArrayAffinityStatusMessage() {
        return lastArrayAffinityStatusMessage;
    }

    public void setLastArrayAffinityStatusMessage(String statusMessage) {
        lastArrayAffinityStatusMessage = statusMessage;
    }

    /**
     * The timestamp for the last array affinity job for this system
     */
    @XmlElement(name = "last_arrayaffinity_run_time")
    public Long getLastArrayAffinityRunTime() {
        return lastArrayAffinityRunTime;
    }

    public void setLastArrayAffinityRunTime(Long lastArrayAffinityRunTime) {
        this.lastArrayAffinityRunTime = lastArrayAffinityRunTime;
    }

    /**
     * The timestamp for the next scheduled array affinity job for this system
     */
    @XmlElement(name = "next_arrayaffinity_run_time")
    public Long getNextArrayAffinityRunTime() {
        return nextArrayAffinityRunTime;
    }

    public void setNextArrayAffinityRunTime(Long nextArrayAffinityRunTime) {
        this.nextArrayAffinityRunTime = nextArrayAffinityRunTime;
    }

    /**
     * The latest timestamp when the system run array affinity job successfully
     */
    @XmlElement(name = "success_arrayaffinity_time")
    public Long getSuccessArrayAffinityTime() {
        return successArrayAffinityTime;
    }

    public void setSuccessArrayAffinityTime(Long successArrayAffinityTime) {
        this.successArrayAffinityTime = successArrayAffinityTime;
    }
}
