/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private StringMap preferredPools;
    private String uuid;
    private String bios;
    public static String ALTER_ID_FIELD = "hostName";
    private URI _serviceProfile;

    /**
     * Older hosts report UUID in Big-Endian or "network-byte-order" (Most Significant Byte first)
     *     e.g.: {00112233-4455-6677-8899-AABBCCDDEEFF}
     * Newer Hosts' BIOSs supporting SMBIOS 2.6 or later report UUID in Little-Endian or "wire-format", where
     *   first 3 parts are in byte revered order (aka: 'mixed-endian')
     *     e.g.: {33221100-5544-7766-8899-AABBCCDDEEFF}
     **/
    public static String getUuidOldFormat(String uuid, String bios) {

        if(uuid == null || bios == null) {
            return null;
        }

        //TODO: decide based on matrix when available from Cisco
        /**
        final Map<String,Double> biosToSmBiosMap = new HashMap<>();
        biosToSmBiosMap.put("BIOS1-V2.2",2.2);
        biosToSmBiosMap.put("BIOS2-V2.4",2.4);
        biosToSmBiosMap.put("BIOS3-V2.5",2.5);
        biosToSmBiosMap.put("B200M4.2.2.4a.0.041620151912",2.6);
        **/

        // use blade model number to determine UUID format (TEMPORARY!!)
        try {
            final String biosVersionPattern = "^B\\d+M(\\d)";
            Pattern r = Pattern.compile(biosVersionPattern);
            String biosModel = "0"; // default is old model, since old BIOS's may not follow pattern
            Matcher m = r.matcher(bios);
            if(m.find()) {
                biosModel = m.group(1);
            }
            if(Integer.parseInt(biosModel) < 3 ) {
                return uuid; // do not reverse bytes for older blades
            }
        } catch (NumberFormatException|IllegalStateException e) {
            return null;  // cannot determine desired format
        }

        // reverse bytes
        UUID uuidObj = UUID.fromString(uuid);
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuidObj.getMostSignificantBits());
        bb.putLong(uuidObj.getLeastSignificantBits());
        byte[] reorderedUuid = new byte[16];
        reorderedUuid[0] = bb.get(3); // reverse bytes in 1st part
        reorderedUuid[1] = bb.get(2);
        reorderedUuid[2] = bb.get(1);
        reorderedUuid[3] = bb.get(0);
        reorderedUuid[4] = bb.get(5); // reverse bytes in 2nd part
        reorderedUuid[5] = bb.get(4);
        reorderedUuid[6] = bb.get(7); // reverse bytes in 3rd part
        reorderedUuid[7] = bb.get(6);
        for(int byteIndex = 8; byteIndex < 16; byteIndex++ ) {
            reorderedUuid[byteIndex] = bb.get(byteIndex); // copy 4th & 5th parts unchanged
        }
        bb = ByteBuffer.wrap(reorderedUuid);
        UUID uuidNew = new UUID(bb.getLong(), bb.getLong());
        return uuidNew.toString();
    }
    


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

    @RelationIndex(cf = "RelationIndex", type = UCSServiceProfile.class)
    @Name("serviceProfile")
    public URI getServiceProfile() {
        return _serviceProfile;
    }

    /**
     * Sets the service profile on UCS for this host
     *
     * @param serviceProfile URI of serviceProfile for this host
     */
    public void setServiceProfile(URI serviceProfile) {
        this._serviceProfile = serviceProfile;
        setChanged("serviceProfile");
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

    @Name("bios")
    public String getBios() {
        return bios;
    }

    public void setBios(String bios) {
        this.bios = bios;
        setChanged("bios");
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

    /**
     * Getter for the preferred storage pools
     * 
     * @return The map of storage pools
     */
    @Name("preferredPools")
    public StringMap getPreferredPools() {
        if (preferredPools == null) {
            preferredPools = new StringMap();
        }
        return preferredPools;
    }

    /**
     * Setter for the the preferred storage pools
     */
    public void setPreferredPools(StringMap preferredPools) {
        this.preferredPools = preferredPools;
        setChanged("preferredPools");
    }
}
