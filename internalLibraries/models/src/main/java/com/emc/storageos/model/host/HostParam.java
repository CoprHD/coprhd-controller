/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.host;

import com.emc.storageos.model.valid.Endpoint;
import com.emc.storageos.model.valid.Length;
import com.emc.storageos.model.valid.Range;

import javax.xml.bind.annotation.XmlElement;

import org.codehaus.jackson.annotate.JsonProperty;

import java.net.URI;

/**
 * Captures POST/PUT data for a host.
 */
public abstract class HostParam {

    private String type;
    private String hostName;
    private String osVersion;

    private String name;
    private Integer portNumber;
    private String userName;
    private String password;
    private Boolean useSsl;
    private URI cluster;
    private URI vcenterDataCenter;
    private URI project;
    private URI tenant;
    private Boolean discoverable;
    private URI bootVolume;

    public HostParam() {
    }

    /**
     * The host type.
     * Valid values:
     *   Windows
     *   HPUX
     *   Linux
     *   Esx
     *   SUNVCS
     *   Other
     * 
     */
    // @EnumType(Host.HostType.class)
    @XmlElement(required = false)
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * The short or fully qualified host name or IP address of the host management interface.
     * 
     */
    @XmlElement(name = "host_name", required = false)
    @Endpoint(type = Endpoint.EndpointType.HOST)
    @JsonProperty("host_name")
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
    @XmlElement(name = "os_version", required = false)
    @JsonProperty("os_version")
    public String getOsVersion() {
        return osVersion;
    }

    public void setOsVersion(String osVersion) {
        this.osVersion = osVersion;
    }

    /**
     * The user label for this host.
     * 
     */
    @Length(min = 2, max = 128)
    @XmlElement()
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * The integer port number of the host management interface.
     */
    @XmlElement(name = "port_number")
    @Range(min = 1, max = 65535)
    public Integer getPortNumber() {
        return portNumber;
    }

    public void setPortNumber(Integer portNumber) {
        this.portNumber = portNumber;
    }

    /**
     * The user name used to log in to the host.
     * 
     */
    @XmlElement(name = "user_name")
    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    /**
     * The password credential used to login to the host.
     * 
     */
    @XmlElement(name = "password")
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
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
     * The URI of the cluster if the host is in a cluster.
     * 
     */
    @XmlElement()
    public URI getCluster() {
        return cluster;
    }

    public void setCluster(URI cluster) {
        this.cluster = cluster;
    }

    /**
     * The URI of a vCenter data center if the host is an ESX host in a data center.
     * 
     */
    @XmlElement(name = "vcenter_data_center")
    public URI getVcenterDataCenter() {
        return vcenterDataCenter;
    }

    public void setVcenterDataCenter(URI vcenterDataCenter) {
        this.vcenterDataCenter = vcenterDataCenter;
    }

    /**
     * This field is currently not used. Any values passed into it will be ignored.
     * 
     */
    @XmlElement()
    public URI getProject() {
        return project;
    }

    public void setProject(URI project) {
        this.project = project;
    }

    /**
     * Gets the discoverable flag. Discoverable indicates if automatic discovery should be
     * performed against this host. Defaults to true.
     * 
     * @return true if automatic discovery is enabled, false if automatic discovery is disabled.
     * default value is true
     */
    @XmlElement(name = "discoverable")
    public Boolean getDiscoverable() {
        return discoverable;
    }

    public void setDiscoverable(Boolean discoverable) {
        this.discoverable = discoverable;
    }

    /**
     * The URI of the tenant owning the host.
     * 
     */
    @XmlElement(name = "tenant")
    public URI getTenant() {
        return tenant;
    }

    public void setTenant(URI tenant) {
        this.tenant = tenant;
    }

    @XmlElement(name = "boot_volume", required = false)
    public URI getBootVolume() {
        return bootVolume;
    }

    public void setBootVolume(URI bootVolume) {
        this.bootVolume = bootVolume;
    }
}
