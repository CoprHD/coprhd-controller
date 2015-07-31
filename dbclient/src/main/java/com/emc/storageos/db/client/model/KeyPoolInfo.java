/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import com.emc.storageos.model.valid.EnumType;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import java.net.URI;
import java.util.Calendar;
import java.util.Date;

/**
 * KeyPoolInfo contains all the information required for key pool file access.
 */
@NoInactiveIndex
@ExcludeFromGarbageCollection
@Cf("KeyPoolInfo")
@DbKeyspace(DbKeyspace.Keyspaces.GLOBAL)
@XmlRootElement(name = "key_pool")
public class KeyPoolInfo extends DataObject {

    // Keypool version #
    private Long _version;

    // Time when this row was last updated
    private Date _lastUpdated;

    // Current file access mode for this keypool
    private String _fileAccessMode;

    // previous file access mode for this keypool
    // the value of the field is valid if _fileAccessMode is in switching mode RequestAuditFilter
    private String _prevfileAccessMode;

    // Time at which the current file access mode expires (ir-relevant if the current file access mode is DISABLED)
    private Long _fileAccessModeTimeout;

    // ID of the project to which this keypool belongs
    private URI _projectID;

    // start version for file access switching
    private Long _startVersion;

    // end version for file access switching
    private Long _endVersion;

    // hosts allowed to mount, separated by comma
    private String _hosts;

    // uid used for file access
    private Long _uid;

    // keypool owner id
    private String _ownerUid;

    // Information regarding the hosting devices which host objects for this keypool. Each string in this set will
    // represent one hosting device. It has the following format:
    // <EndHash:HostingDeviceID>.
    // e.g. { 4:hd1, 8:hd2, 12:hd3, 16:hd4 }. An object which hashes to 8 will be on device hd2.
    // -1 is a special value for EndHash - it indicates any object hash maps to that device.
    private StringSet _hostingDevicesInfo;

    // to keep various flags associated with keypool properties
    private Long _flags;

    // acl saved in db
    private String _aclStr;

    // flag indicating whether this keypool has been initialized - initialization is specific to the underlying hosting
    // device. Once the KP has been initialized on all the hosting devices, this flag should be turned ON
    private Boolean _isInitialized;

    // ID for the VirtualPool associated with this keypool
    // Deprecated for v2, meaningless var
    private URI _vPoolId;

    // replication group id associated with this keypool
    private URI _replicationGroup;

    // api type (S3, Swift, Atmos)
    private String _apiType;

    private String _keypoolExpirationPolicy;

    private String _keyPoolCorsConfig;

    // metadata
    private StringMap _metadata;

    // source Keypool Name for versioning (only used by swift)
    private String _sourceKeypool;

    private Boolean _fileSystemAccessEnabled;

    private Boolean _deleted;

    private String _vpoolType;

    private URI _ingestionDeviceId;

    private Boolean _preserveDirectoryStructure;

    private String _rootDirForDirectoryPreservingFileAccess;

    private URI _fileAccessDeviceId;

    private Boolean _hidden;

    // head metadata
    private byte[] _headMetaData;

    private String _ownerZone;

    public KeyPoolInfo() {
        super();
        setVersion(new Long(1));
        setFileAccessMode("disabled");
        setPrevFileAccessMode("disabled");
        setStartVersion(-1L);
        setEndVersion(-1L);
        setFileAccessModeTimeOut(Long.MAX_VALUE);
        setFlags(0L);
        setCreationTime(Calendar.getInstance());
        setPreserveDirectoryStructure(Boolean.FALSE);
        setOwnerZone("");
    }

    public KeyPoolInfo(KeyPoolInfo info) {

    }

    public static enum ApiType {
        S3,
        SWIFT,
        ATMOS;
    }

    /**
     * CoS of the hosting device
     * Deprecated field for v2
     */
    @XmlElement
    @Name("vPoolId")
    public URI getVPoolId() {
        return _vPoolId;
    }

    public void setVPoolId(URI vPool) {
        _vPoolId = vPool;
        setChanged("vPoolId");
        setLastUpdated(new Date());
    }

    /**
     * replication group
     */
    @XmlElement
    @Name("repGroup")
    public URI getReplicationGroup() {
        return _replicationGroup;
    }

    public void setReplicationGroup(URI repGroup) {
        _replicationGroup = repGroup;
        setChanged("repGroup");
        setLastUpdated(new Date());
    }

    @XmlElement
    @Name("isInitialized")
    public Boolean getIsInitialized() {
        return (_isInitialized != null) && _isInitialized;
    }

    public void setIsInitialized(Boolean isInitialized) {
        _isInitialized = isInitialized;
        setChanged("isInitialized");
        setLastUpdated(new Date());
    }

    /**
     * ID of the project to which this key pool belongs
     * 
     * @return
     */
    @XmlElement
    @Name("projectId")
    public URI getProjectId() {
        return _projectID;
    }

    public void setProjectId(URI projectId) {
        _projectID = projectId;
        setChanged("projectId");
        setLastUpdated(new Date());
    }

    @XmlTransient
    @Name("hostingDevices")
    public StringSet getHostingDevicesInfo() {
        return _hostingDevicesInfo;
    }

    public void setHostingDevicesInfo(StringSet hostingDevicesInfo) {
        _hostingDevicesInfo = hostingDevicesInfo;
        setChanged("hostingDevices");
        setLastUpdated(new Date());
    }

    /**
     * Time at which the current file access mode expires.
     * <p>
     * Not used if the current file access mode is DISABLED
     * </p>
     * 
     * @return
     */
    @XmlElement
    @Name("fileAccessModeTimeout")
    public Long getFileAccessModeTimeOut() {
        return _fileAccessModeTimeout;
    }

    public void setFileAccessModeTimeOut(Long timeout) {
        _fileAccessModeTimeout = timeout;
        setChanged("fileAccessModeTimeout");
        setLastUpdated(new Date());
    }

    /**
     * <p>
     * Current file access mode for this key pool
     * </p>
     * <p>
     * Valid modes are:
     * </p>
     * <li>NONE - no mode, file access disabled</li> <li>disabled - file access disabled, REST-only access permitted</li> <li>
     * switchingToReadOnly - switch to read-only mode in progress</li> <li>readOnly - read-only mode enabled</li> <li>switchingToReadWrite -
     * switch to read-write mode in progress</li> <li>readWrite - read-write mode enabled</li> <li>switchingToDisabled - switch to disable
     * mode in progress</li>
     * 
     * @return
     */
    @XmlElement
    @Name("fileAccessMode")
    public String getFileAccessMode() {
        return _fileAccessMode;
    }

    public void setFileAccessMode(String fileAccessMode) {
        _fileAccessMode = fileAccessMode;
        setChanged("fileAccessMode");
        setLastUpdated(new Date());
    }

    /**
     * previous file access mode for this key pool
     * the value of the field is valid if fileAccessMode is in switching mode
     * 
     * @return
     */
    @XmlElement
    @Name("prevFileAccessMode")
    public String getPrevFileAccessMode() {
        return _prevfileAccessMode;
    }

    public void setPrevFileAccessMode(String prevfileAccessMode) {
        _prevfileAccessMode = prevfileAccessMode;
        setChanged("prevFileAccessMode");
        setLastUpdated(new Date());
    }

    /**
     * Key pool version number
     * 
     * @return
     */
    @XmlElement
    @Name("version")
    public Long getVersion() {
        return _version;
    }

    public void setVersion(Long version) {
        _version = version;
        setChanged("version");
        setLastUpdated(new Date());
    }

    /**
     * Time when this key pool information was last updated
     * 
     * @return
     */
    @XmlElement
    @Name("lastUpdated")
    public Date getLastUpdated() {
        return _lastUpdated;
    }

    public void setLastUpdated(Date lastUpdated) {
        _lastUpdated = lastUpdated;
        setChanged("lastUpdated");
    }

    /**
     * start version for file access switching
     * 
     * @return
     */
    @Name("startVersion")
    public Long getStartVersion() {
        return _startVersion;
    }

    public void setStartVersion(Long startVersion) {
        _startVersion = startVersion;
        setChanged("startVersion");
        setLastUpdated(new Date());
    }

    /**
     * end version for file access switching
     * 
     * @return
     */
    @Name("endVersion")
    public Long getEndVersion() {
        return _endVersion;
    }

    public void setEndVersion(Long endVersion) {
        _endVersion = endVersion;
        setChanged("endVersion");
        setLastUpdated(new Date());
    }

    /**
     * uid (user ID) used for file access
     * 
     * @return
     */
    @Name("uid")
    public Long getUid() {
        return _uid;
    }

    public void setUid(Long uid) {
        _uid = uid;
        setChanged("uid");
        setLastUpdated(new Date());
    }

    /**
     * Key pool owner id
     * 
     * @return
     */
    @Name("ownerUid")
    public String getOwnerUid() {
        return _ownerUid;
    }

    public void setOwnerUid(String ownerUid) {
        _ownerUid = ownerUid;
        setChanged("ownerUid");
        setLastUpdated(new Date());
    }

    /**
     * Comma separated endpoint values allowed to mount in file access mode
     * 
     * @return
     */
    @Name("hosts")
    public String getHosts() {
        return _hosts;
    }

    public void setHosts(String hosts) {
        _hosts = hosts;
        setChanged("hosts");
        setLastUpdated(new Date());
    }

    /**
     * Various flags associated with key pool properties
     * 
     * @return
     */
    @XmlElement
    @Name("flags")
    public Long getFlags() {
        return _flags;
    }

    public void setFlags(Long flags) {
        _flags = flags;
        setChanged("flags");
        setLastUpdated(new Date());
    }

    // don't want to return this to customer
    @XmlTransient
    @Name("aclStr")
    public String getAclStr() {
        return _aclStr;
    }

    public void setAclStr(String aclStr) {
        _aclStr = aclStr;
        setChanged("aclStr");
        setLastUpdated(new Date());
    }

    /**
     * Api types (S3, Swift, Atmos)
     */
    @XmlTransient
    @EnumType(ApiType.class)
    @Name("apiType")
    public String getApiType() {
        return _apiType;
    }

    public void setApiType(String apiType) {
        _apiType = apiType;
        setChanged("apiType");
        setLastUpdated(new Date());
    }

    /**
     * Keypool expiration policy
     */
    @XmlTransient
    @Name("keypoolExpirationPolicy")
    public String getKeypoolExpirationPolicy() {
        return _keypoolExpirationPolicy;
    }

    public void setKeypoolExpirationPolicy(String keypoolExpirationPolicy) {
        _keypoolExpirationPolicy = keypoolExpirationPolicy;
        setChanged("keypoolExpirationPolicy");
    }

    /**
     * Keypool CORS configuration
     */
    @XmlTransient
    @Name("keypoolCorsConfig")
    public String getKeypoolCorsConfig() {
        return _keyPoolCorsConfig;
    }

    public void setKeypoolCorsConfig(String keyPoolCorsConfig) {
        if (keyPoolCorsConfig != null) {
            _keyPoolCorsConfig = keyPoolCorsConfig;
            setChanged("keypoolCorsConfig");
        }
    }

    @XmlTransient
    @Name("keypoolMetadata")
    public StringMap getMetadata() {
        return _metadata;
    }

    public void setMetadata(StringMap metadata) {
        _metadata = metadata;

        setChanged("keypoolMetadata");
        setLastUpdated(new Date());
    }

    @XmlTransient
    @Name("sourceKeypool")
    public String getSourceKeypool() {
        return _sourceKeypool;
    }

    public void setSourceKeypool(String sourceKeypool) {
        this._sourceKeypool = sourceKeypool;
        setChanged("sourceKeypool");
    }

    @XmlElement
    @Name("fileSystemAccessEnabled")
    public Boolean getFileSystemAccessEnabled() {
        return _fileSystemAccessEnabled;
    }

    public void setFileSystemAccessEnabled(Boolean _fileSystemAccessEnabled) {
        if (_fileSystemAccessEnabled != null) {
            this._fileSystemAccessEnabled = _fileSystemAccessEnabled;
            setChanged("fileSystemAccessEnabled");
        }
    }

    @XmlElement
    @Name("deleted")
    public Boolean getDeleted() {
        return (_deleted != null) && _deleted;
    }

    public void setDeleted(Boolean deleted) {
        if (deleted != null) {
            _deleted = deleted;
            setChanged("deleted");
        }
    }

    @XmlTransient
    @Name("vpoolType")
    public String getVpoolType() {
        return _vpoolType == null ? ObjectStore.Type.OBJ.name() : _vpoolType;
    }

    public void setVpoolType(String vpoolType) {
        if (vpoolType == null) {
            return;
        }
        _vpoolType = vpoolType;
        setChanged("vpoolType");
    }

    // this should be preferably used to prevent assigning invalid string types
    public void setVpoolType(ObjectStore.Type type) {
        setVpoolType(type.toString());
    }

    @XmlElement
    @Name("ingestionDeviceId")
    public URI getIngestionDeviceId() {
        return _ingestionDeviceId;
    }

    public void setIngestionDeviceId(URI ingestionDeviceId) {
        _ingestionDeviceId = ingestionDeviceId;
        setChanged("ingestionDeviceId");
        setLastUpdated(new Date());
    }

    @XmlElement
    @Name("rootDirForDirectoryPreservingFileAccess")
    public String getRootDirForDirectoryPreservingFileAccess() {
        return _rootDirForDirectoryPreservingFileAccess;
    }

    public void setRootDirForDirectoryPreservingFileAccess(
            String rootDirForDirectoryPreservingFileAccess) {
        _rootDirForDirectoryPreservingFileAccess = rootDirForDirectoryPreservingFileAccess;
        setChanged("rootDirForDirectoryPreservingFileAccess");
    }

    @XmlElement
    @Name("fileAccessDeviceId")
    public URI getFileAccessDeviceId() {
        return _fileAccessDeviceId;
    }

    public void setFileAccessDeviceId(URI deviceId) {
        _fileAccessDeviceId = deviceId;
        setChanged("fileAccessDeviceId");
    }

    @XmlElement
    @Name("preserveDirectoryStructure")
    public Boolean getPreserveDirectoryStructure() {
        return _preserveDirectoryStructure == null
                ? Boolean.FALSE : _preserveDirectoryStructure;
    }

    public void setPreserveDirectoryStructure(Boolean preserveDirectoryStructure) {
        if (preserveDirectoryStructure != null) {
            _preserveDirectoryStructure = preserveDirectoryStructure;
            setChanged("preserveDirectoryStructure");
        }
    }

    @XmlElement
    @Name("hidden")
    public Boolean getHidden() {
        return _hidden == null ? Boolean.FALSE : _hidden;
    }

    public void setHidden(Boolean hidden) {
        if (hidden != null) {
            _hidden = hidden;
            setChanged("hidden");
        }

    }

    @XmlTransient
    @Name("headMetadata")
    public byte[] getHeadMetadata() {
        return _headMetaData.clone();
    }

    public void setHeadMetadata(byte[] headMetadata) {
        _headMetaData = headMetadata.clone();

        setChanged("headMetadata");
        setLastUpdated(new Date());
    }

    @XmlTransient
    @Name("ownerzone")
    public String getOwnerZone() {
        return _ownerZone;
    }

    public void setOwnerZone(String _ownerZone) {
        this._ownerZone = _ownerZone;
        setChanged("ownerzone");
    }
}
