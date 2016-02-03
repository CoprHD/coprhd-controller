/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.net.URI;

/**
 * File share data object
 */
@Cf("FileShare")
public class FileShare extends FileObject implements ProjectResource {
    // dummy project URN used for file systems marked for internal use by object
    public static final URI INTERNAL_OBJECT_PROJECT_URN = URI.create("urn:storageos:Project:INTERNAL_OBJECT");

    // project this file share is associated with
    private NamedURI _project;

    // tenant this file share is associated with
    private NamedURI _tenant;

    // Virtual Array where this file share exists
    private URI _virtualArray;

    // total capacity in bytes
    private Long _capacity;

    // used capacity in bytes
    private Long _usedCapacity;

    // virtual pool for this file share
    private URI _virtualPool;

    // storage protocols supported by this volume
    private StringSet _protocols;

    // data protection level chosen for this volume
    private String _dataProtection;

    // storage controller where this volume is located
    private URI _storageDevice;

    // Setting pool so that the it is available for the delete method
    private URI _pool;

    // name of the file share
    private String _name;

    // native device ID as created by storage device
    private String _nativeId;

    // native device ID to be indexed - this field is not exposed to client
    private String _nativeGuid;

    // indicates if file system is thinly provisioned, default is false
    private Boolean _thinlyProvisioned = false;

    // storage port selected for this fileshare
    private URI _storagePort;

    // storage port's name used in export/unexport with the storage system
    private String _portName;

    // set when a file share is release from a project for internal object use
    private URI _originalProject;

    private URI virtualNAS;
    
    private Long softLimit;
    
    private Boolean softLimitExceeded;
    
    private Integer softGracePeriod;
    
    private Long notificationLimit;
    
    //mirror related attributes
    
    //mirror target fileshares
    private StringSet _mirrorfsTargets;

    // source file share
    private NamedURI _parentFileShare;

    // file share accesss state
    private String _accessState;

    // file share mirror status
    private String _mirrorStatus;

    // Basic volume personality type (source, target)
    private String _personality;

    // policy associated with the file.
    private StringSet filePolicies;

    public enum MirrorStatus {
        UNKNOWN("0"),
        FAILED_OVER("1"),
        IN_SYNC("2"),
        SUSPENDED("3"),
        CONSISTENT("4"),
        FAILED_BACK("5"),
        DETACHED("6"),
        OTHER("7"),
        SYNCED("8");
        private final String status;

        MirrorStatus(String status) {
            this.status = status;
        }

        public String getStatus() {
            return status;
        }

        private static final MirrorStatus[] copyOfValues = values();

        public static String getLinkStatusDisplayName(String status) {
            for (MirrorStatus statusValue : copyOfValues) {
                if (statusValue.getStatus().contains(status)) {
                    return statusValue.name();
                }
            }
            return MirrorStatus.OTHER.name();
        }
    }

    public static enum FileAccessState {
        UNKNOWN("0"),
        READABLE("1"),
        WRITEABLE("2"),
        READWRITE("3"),
        NOT_READY("4");

        private final String state;

        FileAccessState(String state) {
            this.state = state;
        }

        public String getState() {
            return state;
        }
    }

    public static enum PersonalityTypes {
        SOURCE, // Source fileShare
        TARGET, // Target fileShare
    }

    @Name("mirrorfsTargets")
    public StringSet getMirrorfsTargets() {
        return _mirrorfsTargets;
    }

    public void setMirrorfsTargets(StringSet mirrorfsTargets) {
        this._mirrorfsTargets = mirrorfsTargets;
        setChanged("mirrorfsTargets");

    }

    @Name("parentFileShare")
    public NamedURI getParentFileShare() {
        return _parentFileShare;
    }

    public void setParentFileShare(NamedURI parentFileShare) {
        this._parentFileShare = parentFileShare;
        setChanged("parentFileShare");
    }

    @Name("accessState")
    public String getAccessState() {
        return _accessState;
    }

    public void setAccessState(String accessState) {
        this._accessState = accessState;
        setChanged("accessState");
    }

    @Name("mirrorStatus")
    public String getMirrorStatus() {
        return _mirrorStatus;
    }

    public void setMirrorStatus(String mirrorStatus) {
        this._mirrorStatus = mirrorStatus;
        setChanged("mirrorStatus");
    }

    @Name("personality")
    @AlternateId("AltIdIndex")
    public String getPersonality() {
        return _personality;
    }

    public void setPersonality(String personality) {
        this._personality = personality;
        setChanged("personality");
    }

    // getter and setter

    @Override
    @NamedRelationIndex(cf = "NamedRelation", type = Project.class)
    @Name("project")
    public NamedURI getProject() {
        return _project;
    }

    public void setProject(NamedURI project) {
        _project = project;
        setChanged("project");
    }

    @Override
    @NamedRelationIndex(cf = "NamedRelation")
    @Name("tenant")
    public NamedURI getTenant() {
        return _tenant;
    }

    public void setTenant(NamedURI tenant) {
        _tenant = tenant;
        setChanged("tenant");
    }

    @RelationIndex(cf = "RelationIndex", type = VirtualArray.class)
    @Name("varray")
    @AlternateId("AltIdIndex")
    public URI getVirtualArray() {
        return _virtualArray;
    }

    public void setVirtualArray(URI virtualArray) {
        _virtualArray = virtualArray;
        setChanged("varray");
    }

    @Name("capacity")
    @AggregatedIndex(cf = "AggregatedIndex", groupBy = "virtualPool,project", classGlobal = true)
    public Long getCapacity() {
        return _capacity;
    }

    public void setCapacity(Long capacity) {
        if (_capacity == null || !_capacity.equals(capacity)) {
            _capacity = capacity;
            setChanged("capacity");
        }
    }

    @Name("usedCapacity")
    @AggregatedIndex(cf = "AggregatedIndex", classGlobal = true)
    public Long getUsedCapacity() {
        return _usedCapacity;
    }

    public void setUsedCapacity(Long usedCapacity) {
        if (_usedCapacity == null || !_usedCapacity.equals(usedCapacity)) {
            _usedCapacity = usedCapacity;
            setChanged("usedCapacity");
        }
    }

    @RelationIndex(cf = "RelationIndex", type = VirtualPool.class)
    @Name("virtualPool")
    public URI getVirtualPool() {
        return _virtualPool;
    }

    public void setVirtualPool(URI virtualPool) {
        _virtualPool = virtualPool;
        setChanged("virtualPool");
    }

    @Name("protocols")
    public StringSet getProtocol() {
        return _protocols;
    }

    public void setProtocol(StringSet protocols) {
        _protocols = protocols;
        setChanged("protocols");
    }

    @Name("dataProtection")
    public String getDataProtection() {
        return _dataProtection;
    }

    public void setDataProtection(String dataProtection) {
        _dataProtection = dataProtection;
        setChanged("dataProtection");
    }

    @RelationIndex(cf = "RelationIndex", type = StorageSystem.class)
    @Name("storageDevice")
    public URI getStorageDevice() {
        return _storageDevice;
    }

    public void setStorageDevice(URI storageDevice) {
        _storageDevice = storageDevice;
        setChanged("storageDevice");
    }

    @RelationIndex(cf = "RelationIndex", type = StoragePool.class)
    @Name("pool")
    public URI getPool() {
        return _pool;
    }

    public void setPool(URI pool) {
        _pool = pool;
        setChanged("pool");
    }

    @Name("nativeId")
    public String getNativeId() {
        return _nativeId;
    }

    public void setNativeId(String nativeId) {
        _nativeId = nativeId;
        setChanged("nativeId");
    }

    @AlternateId("AltIdIndex")
    @Name("nativeGuid")
    public String getNativeGuid() {
        return _nativeGuid;
    }

    public void setNativeGuid(String nativeGuid) {
        _nativeGuid = nativeGuid;
        setChanged("nativeGuid");
    }

    public void setName(String name) {
        _name = name;
        setChanged("name");
    }

    @Name("name")
    public String getName() {
        return _name;
    }

    @Name("thinlyProvisioned")
    public Boolean getThinlyProvisioned() {
        return _thinlyProvisioned;
    }

    public void setThinlyProvisioned(Boolean thinlyProvisioned) {
        _thinlyProvisioned = thinlyProvisioned;
        setChanged("thinlyProvisioned");
    }

    @RelationIndex(cf = "RelationIndex", type = StoragePort.class)
    @Name("storagePort")
    public URI getStoragePort() {
        return _storagePort;
    }

    public void setStoragePort(URI storagePort) {
        _storagePort = storagePort;
        setChanged("storagePort");
    }

    @Name("portName")
    public String getPortName() {
        return _portName;
    }

    public void setPortName(String portName) {
        _portName = portName;
        setChanged("portName");
    }

    @Name("originalProject")
    public URI getOriginalProject() {
        return _originalProject;
    }

    public void setOriginalProject(URI originalProject) {
        _originalProject = originalProject;
        setChanged("originalProject");
    }

    @Name("virtualNAS")
	public URI getVirtualNAS() {
		return virtualNAS;
	}

    public void setVirtualNAS(URI vituralNAS) {
        this.virtualNAS = vituralNAS;
        setChanged("virtualNAS");
    }

    @Name("filePolicies")
    public StringSet getFilePolicies() {
        if (filePolicies == null) {
            filePolicies = new StringSet();
        }
        return filePolicies;
    }

    public void setFilePolicies(StringSet filePolicies) {
        this.filePolicies = filePolicies;
        setChanged("filePolicies");
    }

    @Name("softLimit")
    public Long getSoftLimit() {
        return softLimit;
    }

    public void setSoftLimit(Long softLimit) {
        this.softLimit = softLimit;
        setChanged("softLimit");
    }

    @Name("softLimitExceeded")
    public Boolean getSoftLimitExceeded() {
        return softLimitExceeded;
    }

    public void setSoftLimitExceeded(Boolean softLimitExceeded) {
        this.softLimitExceeded = softLimitExceeded;
        setChanged("softLimitExceeded");
    }

    @Name("softGracePeriod")
    public Integer getSoftGracePeriod() {
        return softGracePeriod;
    }

    public void setSoftGracePeriod(Integer softGracePeriod) {
        this.softGracePeriod = softGracePeriod;
        setChanged("softGracePeriod");
    }
    
    @Name("notificationLimit")
    public Long getNotificationLimit() {
        return notificationLimit;
    }

    public void setNotificationLimit(Long notificationLimit) {
        this.notificationLimit = notificationLimit;
        setChanged("notificationLimit");
    }

}
