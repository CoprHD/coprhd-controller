/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.RelatedResourceRep;

/**
 * REST Response representing an Host.
 */
@XmlRootElement(name = "host")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class HostRestRep extends ComputeSystemRestRep {

    private String hostName;
    private String type;
    private String username;
    private Integer portNumber;
    private RelatedResourceRep cluster;
    private RelatedResourceRep project;
    private RelatedResourceRep computeElement;
    private RelatedResourceRep bootVolume;
    private String osVersion;
    private Boolean useSsl;
    private RelatedResourceRep vCenterDataCenter;
    private Boolean discoverable;
    private String provisioningJobStatus;

    public HostRestRep() {
    }

    /**
     * The cluster when the host is in a cluster.
     * 
     */
    @XmlElement(name = "cluster")
    public RelatedResourceRep getCluster() {
        return cluster;
    }

    public void setCluster(RelatedResourceRep cluster) {
        this.cluster = cluster;
    }

    /**
     * The host name.
     * 
     */
    @XmlElement(name = "host_name")
    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    /**
     * The operating system version of the host.
     * 
     */
    @XmlElement(name = "os_version")
    public String getOsVersion() {
        return osVersion;
    }

    public void setOsVersion(String osVersion) {
        this.osVersion = osVersion;
    }

    /**
     * The host management port number.
     * 
     */
    @XmlElement(name = "port_number")
    public Integer getPortNumber() {
        return portNumber;
    }

    public void setPortNumber(Integer portNumber) {
        this.portNumber = portNumber;
    }

    /**
     * The project to which the host is assigned.
     * 
     */
    @XmlElement(name = "project")
    public RelatedResourceRep getProject() {
        return project;
    }

    public void setProject(RelatedResourceRep project) {
        this.project = project;
    }

    @XmlElement(name = "compute_element")
    public RelatedResourceRep getComputeElement() {
        return computeElement;
    }

    public void setComputeElement(RelatedResourceRep computeElement) {
        this.computeElement = computeElement;
    }

    /**
     * The host type.
     * 
     */
    @XmlElement(name = "type")
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * The login account name.
     * 
     */
    @XmlElement(name = "user_name")
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * The boolean flag that indicates if SSL should be used when communicating with the host.
     * 
     */
    @XmlElement(name = "use_ssl")
    public Boolean getUseSsl() {
        return useSsl;
    }

    public void setUseSsl(Boolean useSsl) {
        this.useSsl = useSsl;
    }

    /**
     * The name of the data center in vCenter where this host resides.
     * 
     */
    @XmlElement(name = "vcenter_data_center")
    public RelatedResourceRep getvCenterDataCenter() {
        return vCenterDataCenter;
    }

    public void setvCenterDataCenter(RelatedResourceRep vCenterDataCenter) {
        this.vCenterDataCenter = vCenterDataCenter;
    }

    /**
     * Gets the discoverable flag. Discoverable indicates if automatic discovery should be
     * performed against this host.
     * 
     * @return true if automatic discovery is enabled, false if automatic discovery is disabled.
     */
    @XmlElement(name = "discoverable")
    public Boolean getDiscoverable() {
        return discoverable;
    }

    public void setDiscoverable(Boolean discoverable) {
        this.discoverable = discoverable;
    }

    /**
     * The id of boot volume.
     * 
     * @return The bootVolume
     */
    @XmlElement(name = "boot_volume")
    public RelatedResourceRep getBootVolume() {
        return bootVolume;
    }

    public void setBootVolume(RelatedResourceRep bootVolume) {
        this.bootVolume = bootVolume;
    }

    /**
     * The state of the most recent provisioning Job (can be one of these values
     * : IN_PROGRESS / COMPLETE / ERROR. These statuses correspond to the task
     * status, which can be pending,ready or error.
     * 
     * @return Provisioning Job Status
     */
    @XmlElement(name = "provisioning_job_status")
    public String getProvisioningJobStatus() {
        return provisioningJobStatus;
    }

    public void setProvisioningJobStatus(String provisioningJobStatus) {
        this.provisioningJobStatus = provisioningJobStatus;
    }

}
