/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

import com.emc.storageos.db.client.util.EndpointUtility;

/**
 * A compute host to which a volume or file system can be exported.
 * The host can be a stand-alone computer (server), or a server in
 * an ESX cluster that is managed by a Vcenter.
 * 
 * @author elalih
 * 
 */
@Cf("Host")
public class Host extends AbstractComputeSystem {

    public static enum ProvisioningJobStatus {
        NONE,
        IN_PROGRESS,
        COMPLETE,
        ERROR
    }

    private String _type;
    private String _hostName;
    private Integer _portNumber;
    private String _userName;
    private String _password;
    private String _osVersion;
    private Boolean _isManualCreation;
    private Boolean _useSsl;
    private URI _project;
    private URI _cluster;
    private URI _vcenterDataCenter;
    private URI _computeElement;
    private Boolean discoverable;
    private String provisioningStatus;
    private StringSet volumeGroupIds;

    /**
     * This is for recording the volumeId that was used in the OsInstallation phase. Will be used to remove the associated volume when
     * deactivating a Host
     */
    private URI bootVolumeId;
    /**
     * This is for recording the ComputeVirtualPool that was used to create the host (bare metal) - will be used to determine if the VCP is
     * in use
     */
    private URI computeVirtualPoolId;
    public static String ALTER_ID_FIELD = "hostName";
    private String uuid;

    /**
     * Gets the host type which is an instance of {@link HostType}
     * 
     * @return The type of host.
     */
    @Name("type")
    public String getType() {
        return _type;
    }

    /**
     * Sets the type of host
     * 
     * @see HostType
     * @param type the host type
     */
    public void setType(String type) {
        this._type = type;
        setChanged("type");
    }

    /**
     * Gets the login account name
     * 
     * @return the login account name
     */
    @Name("username")
    public String getUsername() {
        return _userName;
    }

    /**
     * Sets the login account name
     * 
     * @param username the login account name
     */
    public void setUsername(String username) {
        this._userName = username;
        setChanged("username");
    }

    /**
     * Gets the login account password
     * 
     * @return the login account password
     */
    @Encrypt
    @Name("password")
    public String getPassword() {
        return _password;
    }

    /**
     * Sets the login account password
     * 
     * @param password the login account password
     */
    public void setPassword(String password) {
        this._password = password;
        setChanged("password");
    }

    /**
     * The short or fully qualified host name
     * 
     * @return the short or fully qualified host name
     */
    @Name("hostName")
    @AlternateId("AltIdIndex")
    public String getHostName() {
        return _hostName;
    }

    /**
     * Sets the short or fully qualified host name or an IP address
     * 
     * @param hostName the host name
     */
    public void setHostName(String hostName) {
        this._hostName = EndpointUtility.changeCase(hostName);
        setChanged("hostName");
    }

    /**
     * Gets the host management port
     * 
     * @return the the host management port
     */
    @Name("portNumber")
    public Integer getPortNumber() {
        return _portNumber;
    }

    /**
     * Sets the host management port
     * 
     * @return the the host management port
     */
    public void setPortNumber(Integer portNumber) {
        this._portNumber = portNumber;
        setChanged("portNumber");
    }

    /**
     * Gets the cluster name when the host is in a cluster.
     * 
     * @return the cluster name when the host is in a cluster.
     */
    @RelationIndex(cf = "RelationIndex", type = Cluster.class)
    @Name("cluster")
    public URI getCluster() {
        return _cluster;
    }

    /**
     * Sets the cluster when the host is in a cluster.
     * 
     * @param cluster the cluster URI
     */
    public void setCluster(URI cluster) {
        _cluster = cluster;
        setChanged("cluster");
    }

    /**
     * Gets the OS version of the host
     * 
     * @return the OS version
     */
    @Name("osVersion")
    public String getOsVersion() {
        return _osVersion;
    }

    /**
     * Sets the OS version of the host
     * 
     * @param osVersion the host OS version
     */
    public void setOsVersion(String osVersion) {
        this._osVersion = osVersion;
        setChanged("osVersion");
    }

    /**
     * Gets the for manual creation flag.
     * 
     * @return true for manual creation, false otherwise.
     */
    @Deprecated
    @Name("isManualCreation")
    public Boolean getIsManualCreation() {
        return _isManualCreation;
    }

    /**
     * Sets for manual creation flag.
     * 
     * @param isManualCreation
     *            true for manual creation, false otherwise.
     */
    @Deprecated
    public void setIsManualCreation(Boolean isManualCreation) {
        _isManualCreation = isManualCreation;
        setChanged("isManualCreation");
    }

    /**
     * Gets the discoverable flag. Discoverable indicates if automatic discovery should be
     * performed against this host.
     * 
     * @return true if automatic discovery is enabled, false if automatic discovery is disabled.
     */
    @Name("discoverable")
    public Boolean getDiscoverable() {
        return discoverable;
    }

    /**
     * Sets the discoverable flag. Discoverable indicates if automatic discovery should be
     * performed against this host.
     * 
     * @param discoverable true if automatic discovery is enabled, false if automatic discovery is disabled.
     */
    public void setDiscoverable(Boolean discoverable) {
        this.discoverable = discoverable;
        setChanged("discoverable");
    }

    /**
     * This field is currently not used. Any values passed into it will be ignored.
     * 
     * @return null
     */
    @RelationIndex(cf = "RelationIndex", type = Project.class)
    @Name("project")
    public URI getProject() {
        return _project;
    }

    /**
     * This field is currently not used. Any values passed into it will be ignored.
     * 
     * @param project
     */
    public void setProject(URI project) {
        // _project = project;
        // setChanged("project");
    }

    /**
     * Get whether SSL should be used when communicating with the host
     * 
     * @return whether SSL should be used when communicating with the host
     */
    @Name("useSSL")
    public Boolean getUseSSL() {
        return _useSsl;
    }

    /**
     * Sets the flag that indicates if SSL should be used when communicating with the host
     * 
     * @param useSsl true or false to indicate if SSL should be used
     */
    public void setUseSSL(Boolean useSsl) {
        this._useSsl = useSsl;
        setChanged("useSSL");
    }

    /**
     * Returns the name of the data center in vcenter where this host resides
     * 
     * @return the name of the data center in vcenter where this host resides
     */
    @RelationIndex(cf = "RelationIndex", type = VcenterDataCenter.class)
    @Name("vcenterDataCenter")
    public URI getVcenterDataCenter() {
        return _vcenterDataCenter;
    }

    /**
     * Sets the data center in vcenter where this host resides
     * 
     * @param dataCenter the vcenter data center where the host resides
     */
    public void setVcenterDataCenter(URI dataCenter) {
        this._vcenterDataCenter = dataCenter;
        setChanged("vcenterDataCenter");
    }

    @RelationIndex(cf = "RelationIndex", type = ComputeElement.class)
    @Name("computeElement")
    public URI getComputeElement() {
        return _computeElement;
    }

    /**
     * Sets the data center in vcenter where this host resides
     * 
     * @param dataCenter the vcenter data center where the host resides
     */
    public void setComputeElement(URI computeElement) {
        this._computeElement = computeElement;
        setChanged("computeElement");
    }

    @Override
    public Object[] auditParameters() {
        return new Object[] { getHostName(),
                getVcenterDataCenter(), getCluster(), getTenant(), getId() };
    }

    /**
     * The expected list of host OS types
     * 
     */
    public enum HostType {
        Windows, HPUX, Linux, Esx, AIX, AIXVIO, SUNVCS, No_OS, Other
    }

    @RelationIndex(cf = "RelationIndex", type = Volume.class)
    @Name("bootVolumeId")
    public URI getBootVolumeId() {
        return bootVolumeId;
    }

    public void setBootVolumeId(URI bootVolumeId) {
        this.bootVolumeId = bootVolumeId;
        setChanged("bootVolumeId");
    }

    @RelationIndex(cf = "RelationIndex", type = ComputeVirtualPool.class)
    @Name("computeVirtualPoolId")
    public URI getComputeVirtualPoolId() {
        return computeVirtualPoolId;
    }

    public void setComputeVirtualPoolId(URI computeVirtualPoolId) {
        this.computeVirtualPoolId = computeVirtualPoolId;
        setChanged("computeVirtualPoolId");
    }

    @AlternateId("AltIdIndex")
    @Name("uuid")
    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
        setChanged("uuid");
    }

    @Name("provisioningStatus")
    public String getProvisioningStatus() {
        return provisioningStatus;
    }

    public void setProvisioningStatus(String provisioningStatus) {
        this.provisioningStatus = provisioningStatus;
        setChanged("provisioningStatus");
    }

    /**
     * Getter for the ids of the volume groups
     * 
     * @return The set of application ids
     */
    @Name("volumeGroupIds")
    @AlternateId("VolumeGroups")
    public StringSet getVolumeGroupIds() {
        if (volumeGroupIds == null) {
            volumeGroupIds = new StringSet();
        }
        return volumeGroupIds;
    }

    /**
     * Setter for the volume group ids
     */
    public void setVolumeGroupIds(StringSet applicationIds) {
        this.volumeGroupIds = applicationIds;
        setChanged("volumeGroupIds");
    }

}
