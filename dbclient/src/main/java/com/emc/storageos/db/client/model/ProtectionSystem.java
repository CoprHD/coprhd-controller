/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.util.Calendar;
import java.util.StringTokenizer;

/**
 * Protection System data object
 */
@Cf("ProtectionSystem")
public class ProtectionSystem extends DiscoveredSystemObject {

    // installation ID
    private String _installationId;

    // device OS/firmware major version
    private String _majorVersion;

    // device OS/firmware minor version
    private String _minorVersion;

    // secondary/backup management interface IP address
    private String _secondaryIP;

    // management port number
    private Integer _portNumber;

    // management interface user
    // TODO - this needs to be encrypted
    private String _username;

    // management interface password
    // TODO - this needs to be encrypted
    private String _password;

    // management interface IP address
    private String _ipAddress;

    // Determining, whether an Array is reachable
    private Boolean _reachable;

    // Topology information (<clusterid1>:<clusterid2>:<protocol>)
    private StringSet _clusterTopology;

    // For placement decisions: record the static load on the recoverpoint appliances

    // Number of CGs on the Protection System
    private Long _cgCount;

    // Number of CGs the Protection System can have
    private Long _cgCapacity;

    // Number of replication sets on the Protection System
    private Long _rsetCount;

    // Number of replication sets the Protection System can have
    private Long _rsetCapacity;

    // Remote replication capacity the Protection System currently is handling
    private Long _remoteReplicationGBCount;

    // Remote replication capacity the Protection System
    private Long _remoteReplicationGBCapacity;

    // For a site ID, number of VNX splitters
    private StringMap _siteVNXSplitterCount;

    // For a site ID, number of VNX splitters the site can have
    private StringMap _siteVNXSplitterCapacity;

    // For a site ID, amount of local replication
    private StringMap _siteLocalReplicationGBCount;

    // For a site ID, amount of local replication the site can handle
    private StringMap _siteLocalReplicationGBCapacity;

    // For a site ID, amount of volume that the site has
    private StringMap _siteVolumeCount;

    // For a site ID, amount of volumes that the site can handle
    private StringMap _siteVolumeCapacity;

    // For a site ID, amount of volumes attached to splitter
    private StringMap _siteVolumesAttachedToSplitterCount;

    // For a site ID, total number of volumes able to be attached to the splitter
    private StringMap _siteVolumesAttachedToSplitterCapacity;

    // For a site ID, amount of paths that the site has
    private StringMap _sitePathCount;

    // For a site ID, amount of paths that the site can handle
    private StringMap _sitePathCapacity;

    // For a site ID, all the site initiators
    private StringSetMap siteInitiators = new StringSetMap();

    // VirtualArrays where this Protection System is available
    private StringSet _virtualArrays;

    // Storage Systems that are associated to the Protection System.
    // An associatedStorageSystem entry is in the format of: "RPSiteName -space- StorageSystemId"
    // Ex: "0x28829064d5c46b6e urn:storageos:StorageSystem:a00eba65-8a2b-4635-b590-824dfea225c7:vdc1"
    // TODO Need to create a JIRA - convert to this into String Map instead
    private StringSet _associatedStorageSystems;

    // Maps the internalSiteName to the siteName (more readable) of the RPSite
    private StringMap rpSiteNames;

    // For a site ID, the virtual arrays allowed to use it.
    private StringSetMap siteAssignedVirtualArrays = new StringSetMap();

    // For a site ID, the arrays that are seen by it (serial numbers). Useful for placement decisions in pre-configured environments
    private StringSetMap siteVisibleStorageArrays = new StringSetMap();

    // Set of all cluster management IPs for this Protection System excluding the IP that
    // was used to register the ProtectionSystem.
    private StringSet clusterManagementIPs;

    private Calendar cgLastCreatedTime;

    // The RP constant, used in controller, device type, virtual pool, etc.
    public static final String _RP = "rp";

    @AlternateId("AltIdIndex")
    @Name("installationId")
    public String getInstallationId() {
        return _installationId;
    }

    public void setInstallationId(String installationId) {
        this._installationId = installationId;
        setChanged("installationId");
    }

    @Name("majorVersion")
    public String getMajorVersion() {
        return _majorVersion;
    }

    public void setMajorVersion(String majorVersion) {
        this._majorVersion = majorVersion;
        setChanged("majorVersion");
    }

    @Name("minorVersion")
    public String getMinorVersion() {
        return _minorVersion;
    }

    public void setMinorVersion(String minorVersion) {
        this._minorVersion = minorVersion;
        setChanged("minorVersion");
    }

    @Name("ipAddress")
    public String getIpAddress() {
        return _ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this._ipAddress = ipAddress;
        setChanged("ipAddress");
    }

    @Deprecated
    @Name("secondaryIP")
    public String getSecondaryIP() {
        return _secondaryIP;
    }

    @Deprecated
    public void setSecondaryIP(String secondaryIP) {
        _secondaryIP = secondaryIP;
        setChanged("secondaryIP");
    }

    @Name("portNumber")
    public Integer getPortNumber() {
        return _portNumber;
    }

    public void setPortNumber(Integer portNumber) {
        this._portNumber = portNumber;
        setChanged("portNumber");
    }

    @Name("username")
    public String getUsername() {
        return _username;
    }

    public void setUsername(String username) {
        this._username = username;
        setChanged("username");
    }

    @Encrypt
    @Name("password")
    public String getPassword() {
        return _password;
    }

    public void setPassword(String password) {
        this._password = password;
        setChanged("password");
    }

    @Name("reachable")
    public Boolean getReachableStatus() {
        return _reachable;
    }

    public void setReachableStatus(Boolean reachable) {
        _reachable = reachable;
        setChanged("reachable");
    }

    @Name("cgCount")
    public Long getCgCount() {
        return _cgCount;
    }

    public void setCgCount(Long _cgCount) {
        this._cgCount = _cgCount;
        setChanged("cgCount");
    }

    @Name("cgCapacity")
    public Long getCgCapacity() {
        return _cgCapacity;
    }

    public void setCgCapacity(Long _cgCapacity) {
        this._cgCapacity = _cgCapacity;
        setChanged("cgCapacity");
    }

    @Name("rsetCount")
    public Long getRsetCount() {
        return _rsetCount;
    }

    public void setRsetCount(Long _rsetCount) {
        this._rsetCount = _rsetCount;
        setChanged("rsetCount");
    }

    @Name("rsetCapacity")
    public Long getRsetCapacity() {
        return _rsetCapacity;
    }

    public void setRsetCapacity(Long _rsetCapacity) {
        this._rsetCapacity = _rsetCapacity;
        setChanged("rsetCapacity");
    }

    @Name("remoteReplicationGBCount")
    public Long getRemoteReplicationGBCount() {
        return _remoteReplicationGBCount;
    }

    public void setRemoteReplicationGBCount(Long _remoteReplicationGBCount) {
        this._remoteReplicationGBCount = _remoteReplicationGBCount;
        setChanged("remoteReplicationGBCount");
    }

    @Name("remoteReplicationGBCapacity")
    public Long getRemoteReplicationGBCapacity() {
        return _remoteReplicationGBCapacity;
    }

    public void setRemoteReplicationGBCapacity(Long _remoteReplicationGBCapacity) {
        this._remoteReplicationGBCapacity = _remoteReplicationGBCapacity;
        setChanged("remoteReplicationGBCapacity");
    }

    @Name("siteVNXSplitterCount")
    public StringMap getSiteVNXSplitterCount() {
        return _siteVNXSplitterCount;
    }

    public void setSiteVNXSplitterCount(StringMap _siteVNXSplitterCount) {
        this._siteVNXSplitterCount = _siteVNXSplitterCount;
    }

    @Name("siteVNXSplitterCapacity")
    public StringMap getSiteVNXSplitterCapacity() {
        return _siteVNXSplitterCapacity;
    }

    public void setSiteVNXSplitterCapacity(StringMap _siteVNXSplitterCapacity) {
        this._siteVNXSplitterCapacity = _siteVNXSplitterCapacity;
        setChanged("siteVNXSplitterCount");
    }

    @Name("siteLocalReplicationGBCount")
    public StringMap getSiteLocalReplicationGBCount() {
        return _siteLocalReplicationGBCount;
    }

    public void setSiteLocalReplicationGBCount(
            StringMap _siteLocalReplicationGBCount) {
        this._siteLocalReplicationGBCount = _siteLocalReplicationGBCount;
        setChanged("siteLocalReplicationGBCount");
    }

    @Name("siteLocalReplicationGBCapacity")
    public StringMap getSiteLocalReplicationGBCapacity() {
        return _siteLocalReplicationGBCapacity;
    }

    public void setSiteLocalReplicationGBCapacity(
            StringMap _siteLocalReplicationGBCapacity) {
        this._siteLocalReplicationGBCapacity = _siteLocalReplicationGBCapacity;
        setChanged("siteLocalReplicationGBCapacity");
    }

    @Name("siteVolumeCount")
    public StringMap getSiteVolumeCount() {
        return _siteVolumeCount;
    }

    public void setSiteVolumeCount(StringMap _siteVolumeCount) {
        this._siteVolumeCount = _siteVolumeCount;
        setChanged("siteVolumeCount");
    }

    @Name("siteVolumeCapacity")
    public StringMap getSiteVolumeCapacity() {
        return _siteVolumeCapacity;
    }

    public void setSiteVolumeCapacity(StringMap _siteVolumeCapacity) {
        this._siteVolumeCapacity = _siteVolumeCapacity;
        setChanged("siteVolumeCapacity");
    }

    @Name("siteVolumesAttachedToSplitterCount")
    public StringMap getSiteVolumesAttachedToSplitterCount() {
        return _siteVolumesAttachedToSplitterCount;
    }

    public void setSiteVolumesAttachedToSplitterCount(StringMap _siteVolumesAttachedToSplitterCount) {
        this._siteVolumesAttachedToSplitterCount = _siteVolumesAttachedToSplitterCount;
        setChanged("siteVolumesAttachedToSplitterCount");
    }

    @Name("siteVolumesAttachedToSplitterCapacity")
    public StringMap getSiteVolumesAttachedToSplitterCapacity() {
        return _siteVolumesAttachedToSplitterCapacity;
    }

    public void setSiteVolumesAttachedToSplitterCapacity(StringMap _siteVolumesAttachedToSplitterCapacity) {
        this._siteVolumesAttachedToSplitterCapacity = _siteVolumesAttachedToSplitterCapacity;
        setChanged("siteVolumesAttachedToSplitterCapacity");
    }

    @Name("sitePathCount")
    public StringMap getSitePathCount() {
        return _sitePathCount;
    }

    public void setSitePathCount(StringMap _sitePathCount) {
        this._sitePathCount = _sitePathCount;
        setChanged("sitePathCount");
    }

    @Name("sitePathCapacity")
    public StringMap getSitePathCapacity() {
        return _sitePathCapacity;
    }

    public void setSitePathCapacity(StringMap _sitePathCapacity) {
        this._sitePathCapacity = _sitePathCapacity;
        setChanged("sitePathCapacity");
    }

    @Name("virtualArrays")
    @AlternateId("varrayAltIdIndex")
    public StringSet getVirtualArrays() {
        return _virtualArrays;
    }

    public void setVirtualArrays(StringSet virtualArrays) {
        _virtualArrays = virtualArrays;
        setChanged("virtualArrays");
    }

    @Name("clusterTopology")
    public StringSet getClusterTopology() {
        return _clusterTopology;
    }

    public void setClusterTopology(StringSet clusterTopology) {
        _clusterTopology = clusterTopology;
        setChanged("clusterTopology");
    }

    public String assembleClusterTopology(String cluster1, String cluster2, String protocol) {
        return (cluster1 + " " + cluster2 + " " + protocol);
    }

    // Helper methods for the topology field
    static public String retrieveClusterTopologyInternalSiteName1(String clusterTopology) {
        if (clusterTopology != null) {
            StringTokenizer str = new StringTokenizer(clusterTopology);
            return (String) str.nextElement();
        }
        return null;
    }

    static public String retrieveClusterTopologyInternalSiteName2(String clusterTopology) {
        if (clusterTopology != null) {
            StringTokenizer str = new StringTokenizer(clusterTopology);
            str.nextElement();
            return (String) str.nextElement();
        }
        return null;
    }

    static public String retrieveClusterTopologyProtocol(String clusterTopology) {
        if (clusterTopology != null) {
            StringTokenizer str = new StringTokenizer(clusterTopology);
            str.nextElement();
            str.nextElement();
            return (String) str.nextElement();
        }
        return null;
    }

    public int canProtectToHowManyClusters(String internalSiteName) {
        int count = 1; // An internal site can always protect to itself, but that won't appear in the list.
        if (_clusterTopology != null) {
            for (String clusterTopology : _clusterTopology) {
                if ((ProtectionSystem.retrieveClusterTopologyInternalSiteName1(clusterTopology).equals(internalSiteName)) &&
                        (!ProtectionSystem.retrieveClusterTopologyProtocol(clusterTopology).equals("NO_CONNECTION"))) {
                    count++;
                }
            }
        }
        return count;
    }

    public boolean canProtect(String cluster1, String cluster2) {
        if (cluster1.equals(cluster2)) {
            return true;
        }

        if (_clusterTopology != null) {
            for (String clusterTopology : _clusterTopology) {
                if ((ProtectionSystem.retrieveClusterTopologyInternalSiteName1(clusterTopology).equals(cluster1)) &&
                        (ProtectionSystem.retrieveClusterTopologyInternalSiteName2(clusterTopology).equals(cluster2)) &&
                        (!ProtectionSystem.retrieveClusterTopologyProtocol(clusterTopology).equals("NO_CONNECTION"))) {
                    return true;
                }
            }
        }
        return false;
    }

    @Name("associatedStorageSystems")
    @AlternateId("AssSystemsAltIdIndex")
    public StringSet getAssociatedStorageSystems() {
        if (_associatedStorageSystems == null) {
            setAssociatedStorageSystems(new StringSet());
        }
        return _associatedStorageSystems;
    }

    public void setAssociatedStorageSystems(StringSet storageSystems) {
        _associatedStorageSystems = storageSystems;
        setChanged("associatedStorageSystems");
    }

    @Name("siteInitiators")
    public StringSetMap getSiteInitiators() {
        return siteInitiators;
    }

    public void setSiteInitiators(StringSetMap siteInitiators) {
        this.siteInitiators = siteInitiators;
    }

    public void addSiteInitiators(StringSetMap siteInitiatorsEntries) {
        if (this.siteInitiators == null) {
            this.siteInitiators = new StringSetMap();
        }
        this.siteInitiators.putAll(siteInitiatorsEntries);
    }

    public void removeSiteInitiatorsEntry(String key) {
        if (this.siteInitiators != null) {
            // This seemingly contorted logic is to avoid
            // a concurrent update error.
            StringSet set = siteInitiators.get(key);
            if (set != null && !set.isEmpty()) {
                StringSet values = new StringSet();
                values.addAll(set);
                for (String value : values) {
                    siteInitiators.remove(key, value);
                }
            }
        }
    }

    /**
     * Add an entry to create a zone between an initiator and port.
     * 
     * @param internalSiteName URI as String
     * @param initiators site initiators
     */
    public void putSiteIntitiatorsEntry(String internalSiteName, StringSet initiators) {
        if (this.siteInitiators == null) {
            this.siteInitiators = new StringSetMap();
        }
        this.siteInitiators.put(internalSiteName, initiators);
    }

    static public String generateAssociatedStorageSystem(String siteName, String serialNumber) {
        return (siteName + " " + serialNumber);
    }

    static public String getAssociatedStorageSystemSiteName(String associatedStorageSystem) {
        if (associatedStorageSystem != null) {
            if (associatedStorageSystem.contains(" ")) {
                return (associatedStorageSystem.substring(0, associatedStorageSystem.indexOf(' ')));
            }
        }
        return null;
    }

    static public String getAssociatedStorageSystemSerialNumber(String associatedStorageSystem) {
        if (associatedStorageSystem != null) {
            if (associatedStorageSystem.contains(" ")) {
                return (associatedStorageSystem.substring(associatedStorageSystem.indexOf(' ') + 1));
            }
        }
        return associatedStorageSystem;
    }

    /**
     * Returns the associatedStorageSystem entry from _associatedStorageSystems if it is
     * found using the filter passed in.
     * 
     * The associatedStorageSystem entry is in the format of: "RPSiteName -space- Serial Number"
     * Ex: "0x28829064d5c46b6e FNM00131400084"
     * 
     * @param filter Either RPSiteName or Serial Number
     * @return String containing "RPSiteName -space- Serial Number"
     */
    public String getAssociatedStorageSystemWithString(String filter) {
        if (_associatedStorageSystems != null) {
            for (String associatedStorageSystem : _associatedStorageSystems) {
                if (associatedStorageSystem.contains(filter)) {
                    return associatedStorageSystem;
                }
            }
        }
        return null;
    }

    /**
     * Returns a set of associatedStorageSystem entries from _associatedStorageSystems if they match
     * using the filter passed in.
     * 
     * An associatedStorageSystem entry is in the format of: "RPSiteName -space- StorageSystemId"
     * Ex: "0x28829064d5c46b6e urn:storageos:StorageSystem:a00eba65-8a2b-4635-b590-824dfea225c7:vdc1"
     * 
     * @param filter Either RPSiteName or StorageSystemId
     * @return StringSet containing entries of "RPSiteName -space- StorageSystemId"
     */
    public StringSet getAssociatedStorageSystemsWithString(String filter) {
        StringSet associatedStorageSystemsSet = new StringSet();
        if (_associatedStorageSystems != null) {
            for (String associatedStorageSystem : _associatedStorageSystems) {
                if (associatedStorageSystem.contains(filter)) {
                    associatedStorageSystemsSet.add(associatedStorageSystem);
                }
            }
        }
        return associatedStorageSystemsSet;
    }

    @Name("rpSiteNames")
    public StringMap getRpSiteNames() {
        return rpSiteNames;
    }

    public void setRpSiteNames(StringMap rpSiteNames) {
        this.rpSiteNames = rpSiteNames;
        setChanged("rpSiteNames");
    }

    @Name("siteAssignedVirtualArrays")
    public StringSetMap getSiteAssignedVirtualArrays() {
        return siteAssignedVirtualArrays;
    }

    public void setSiteAssignedVirtualArrays(StringSetMap siteAssignedVirtualArrays) {
        this.siteAssignedVirtualArrays = siteAssignedVirtualArrays;
        setChanged("siteAssignedVirtualArrays");
    }

    public void addSiteAssignedVirtualArrays(StringSetMap siteAssignedVirtualArraysEntries) {
        if (this.siteAssignedVirtualArrays == null) {
            this.siteAssignedVirtualArrays = new StringSetMap();
        }
        this.siteAssignedVirtualArrays.putAll(siteAssignedVirtualArraysEntries);
    }

    public void removeSiteAssignedVirtualArraysEntry(String key) {
        if (this.siteAssignedVirtualArrays != null) {
            // If you get a concurrent exception in here, refer to same method in siteInitiators and ask for help at DB level.
            // Otherwise remove this comment and change removeSiteInitiatorsEntry above to do the same as this.
            siteAssignedVirtualArrays.remove(key);
        }
    }

    /**
     * Add an entry to create authorized use between a site and virtual arrays.
     * 
     * @param internalSiteName URI as String
     * @param virtualArrays virtualArrays the site is assigned to.
     */
    public void putSiteAssignedVirtualArrayEntry(String internalSiteName, StringSet virtualArrays) {
        if (this.siteAssignedVirtualArrays == null) {
            this.siteAssignedVirtualArrays = new StringSetMap();
        }
        this.siteAssignedVirtualArrays.put(internalSiteName, virtualArrays);
    }

    /**
     * Add an entry to create authorized use between a site and a virtual array
     * 
     * @param internalSiteName URI as String
     * @param virtualArray virtual array the site is assigned to.
     */
    public void addSiteAssignedVirtualArrayEntry(String internalSiteName, String virtualArray) {
        if (this.siteAssignedVirtualArrays == null) {
            this.siteAssignedVirtualArrays = new StringSetMap();
        }

        if (this.siteAssignedVirtualArrays.get(internalSiteName) == null) {
            siteAssignedVirtualArrays.put(internalSiteName, new StringSet());
        }

        this.siteAssignedVirtualArrays.get(internalSiteName).add(virtualArray);
    }

    /**
     * Remove an entry to create authorized use between a site and a virtual array
     * 
     * @param internalSiteName URI as String
     * @param virtualArray virtual array the site is no longer assigned to.
     */
    public void removeSiteAssignedVirtualArrayEntry(String internalSiteName, String virtualArray) {
        if (this.siteAssignedVirtualArrays == null) {
            return;
        }

        if (this.siteAssignedVirtualArrays.get(internalSiteName) == null) {
            return;
        }

        this.siteAssignedVirtualArrays.get(internalSiteName).remove(virtualArray);

        // If this is the last virtual array, remove the whole key.
        if (this.siteAssignedVirtualArrays.get(internalSiteName).isEmpty()) {
            this.siteAssignedVirtualArrays.remove(internalSiteName);
        }
    }

    @Name("siteVisibleStorageArrays")
    public StringSetMap getSiteVisibleStorageArrays() {
        return siteVisibleStorageArrays;
    }

    public void setSiteVisibleStorageArrays(StringSetMap siteVisibleStorageArrays) {
        this.siteVisibleStorageArrays = siteVisibleStorageArrays;
    }

    public void addSiteVisibleStorageArrays(StringSetMap siteVisibleStorageArraysEntries) {
        if (this.siteVisibleStorageArrays == null) {
            this.siteVisibleStorageArrays = new StringSetMap();
        }
        this.siteVisibleStorageArrays.putAll(siteVisibleStorageArraysEntries);
    }

    public void removeSiteVisibleStorageArraysEntry(String key) {
        if (this.siteVisibleStorageArrays != null) {
            // If you get a concurrent exception in here, refer to same method in siteInitiators and ask for help at DB level.
            // Otherwise remove this comment and change removeSiteInitiatorsEntry above to do the same as this.
            siteVisibleStorageArrays.remove(key);
        }
    }

    /**
     * Add an entry to create a storage system whose visible array is configured already.
     * 
     * @param internalSiteName URI as String
     * @param serialNumber storage system the site is assigned to.
     */
    public void addSiteVisibleStorageArrayEntry(String internalSiteName, String serialNumber) {
        if (this.siteVisibleStorageArrays == null) {
            this.siteVisibleStorageArrays = new StringSetMap();
        }

        this.siteVisibleStorageArrays.put(internalSiteName, serialNumber);
    }

    /**
     * Remove an entry to create a storage system whose visible array is configured already.
     * 
     * @param internalSiteName URI as String
     * @param serialNumber storage system the site is no longer assigned to.
     */
    public void removeSiteVisibleStorageArrayEntry(String internalSiteName, String serialNumber) {
        if (this.siteVisibleStorageArrays == null) {
            return;
        }

        if (this.siteVisibleStorageArrays.get(internalSiteName) == null) {
            return;
        }

        this.siteVisibleStorageArrays.remove(internalSiteName, serialNumber);
    }

    @Name("clusterManagementIPs")
    public StringSet getClusterManagementIPs() {
        if (clusterManagementIPs == null) {
            setClusterManagementIPs(new StringSet());
        }
        return clusterManagementIPs;
    }

    public void setClusterManagementIPs(StringSet clusterManagementIPs) {
        this.clusterManagementIPs = clusterManagementIPs;
    }

    @Name("cgLastCreatedTime")
    public Calendar getCgLastCreatedTime() {
        return cgLastCreatedTime;
    }

    public void setCgLastCreatedTime(Calendar cgLastCreatedTime) {
        this.cgLastCreatedTime = cgLastCreatedTime;
        setChanged("cgLastCreatedTime");
    }
}
