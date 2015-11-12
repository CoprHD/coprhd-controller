/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.net.URI;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;

/**
 * @author burckb
 * 
 */

@Cf("BlockSnapshot")
public class BlockSnapshot extends BlockObject implements ProjectResourceSnapshot {
    // Volume the snapshot was created from
    private NamedURI _parent;

    // Project the snapshot was associated to
    private NamedURI _project;

    // New volume generated when the snapshot was created or exported
    private String _newVolumeNativeId;

    // Set of snapshots generated at the same time, with the same consistency group
    private String _snapsetLabel;

    // Source ALU, for convenience
    private String _sourceNativeId;

    // Name reference of Clar_SynchronizationAspectForSource
    private String _settingsInstance;

    // Name reference of Snapshot'ed consistency group. That is,
    // after a snapshot of consistency group is taken, the group of snapshot volumes
    // will be placed in a grouping. This String references that group instance.
    @Deprecated
    private String _snapshotGroupInstance;

    // Name reference of Clar_SynchronizationAspectForSourceGroup. This references
    // some CIMObjectPath instance that is created after a restore is done. It is
    // necessary to maintain this reference because it is not navigable using the
    // API; so, as soon as restore operation is done, the instance value is captured
    // and saved for the blocksnapshot. Future operations, will have to use this
    // to manipulate the snapshot or snapset group (for example, disabling or
    // deleting the snapshot/snapset).
    private String _settingsGroupInstance;

    // This value is an indicator of whether or not the snapshot has been activated.
    // Activation means that the source and target synchronization has been initialized.
    // (Data may not have been written to the target, however)
    private Boolean _isSyncActive;

    // Logical size of a storage volume on array which is volume.ConsumableBlocks * volume.BlockSize.
    private Long _provisionedCapacity;

    // Total amount of storage space consumed within the StoragePool which is SpaceConsumed of CIM_AllocatedFromStoragePool.
    private Long _allocatedCapacity;

    // Snapshot technology type
    private String _technologyType;

    // TODO EMCW-RP: This RP information below belongs in a subclass extending BlockSnapshot.
    // This work will be done as part of the protection API work.

    // BEGIN RecoverPoint specific information
    private URI _protectionSet;
    private String _emName;
    private String _emBookmarkTime;

    // The next 3 values make up the ConsistencyGroupCopyUID of the copy
    private Integer _emCGGroupId;
    private Integer _emCGGroupCopyId;
    private String _emInternalSiteName;

    // The next 3 values make up the ConsistencyGroupCopyUID of the production at the time of replication
    private Integer _productionGroupId;
    private Integer _productionGroupCopyId;
    private String _productionInternalSiteName;
    // END RecoverPoint specific information

    // Is an indication that the snapshot needs to be copied to the target.
    private Boolean _needsCopyToTarget;

    // This value is an indicator if the snapshot is read only or writable
    private Boolean _isReadOnly;

    public enum TechnologyType {
        NATIVE,
        RP,
        SRDF
    };

    @Override
    @NamedRelationIndex(cf = "NamedRelationIndex", type = Volume.class)
    @Name("parent")
    public NamedURI getParent() {
        return _parent;
    }

    public void setParent(NamedURI parent) {
        _parent = parent;
        setChanged("parent");
    }

    @Override
    public Class<? extends DataObject> parentClass() {
        return Volume.class;
    }

    @Name("newVolumeNativeId")
    public String getNewVolumeNativeId() {
        return _newVolumeNativeId;
    }

    public void setNewVolumeNativeId(String newVolume) {
        _newVolumeNativeId = newVolume;
        setChanged("newVolumeNativeId");
    }

    @AlternateId("AltIdIndex")
    @Name("snapsetLabel")
    public String getSnapsetLabel() {
        return _snapsetLabel;
    }

    public void setSnapsetLabel(String snapsetLabel) {
        _snapsetLabel = snapsetLabel;
        setChanged("snapsetLabel");
    }

    @Override
    @NamedRelationIndex(cf = "NamedRelationIndex", type = Project.class)
    @Name("project")
    public NamedURI getProject() {
        return _project;
    }

    public void setProject(NamedURI project) {
        _project = project;
        setChanged("project");
    }

    @Name("sourceNativeId")
    public String getSourceNativeId() {
        return _sourceNativeId;
    }

    public void setSourceNativeId(String sourceALU) {
        _sourceNativeId = sourceALU;
        setChanged("sourceNativeId");
    }

    @Name("settingsInstance")
    public String getSettingsInstance() {
        return _settingsInstance;
    }

    public void setSettingsInstance(String settingsInstance) {
        _settingsInstance = settingsInstance;
        setChanged("settingsInstance");
    }

    @Name("settingsGroupInstance")
    public String getSettingsGroupInstance() {
        return _settingsGroupInstance;
    }

    public void setSettingsGroupInstance(String groupSettings) {
        _settingsGroupInstance = groupSettings;
        setChanged("settingsGroupInstance");
    }

    @Name("snapshotGroupInstance")
    @Deprecated
    public String getSnapshotGroupInstance() {
        return _snapshotGroupInstance;
    }

    @Deprecated
    public void setSnapshotGroupInstance(String snapshotGroupInstance) {
        _snapshotGroupInstance = snapshotGroupInstance;
        setChanged("snapshotGroupInstance");
    }

    @Name("isSyncActive")
    public Boolean getIsSyncActive() {
        return (_isSyncActive != null) ? _isSyncActive : Boolean.FALSE;
    }

    public void setIsSyncActive(Boolean isSyncActive) {
        _isSyncActive = isSyncActive;
        setChanged("isSyncActive");
    }

    @Name("technologyType")
    public String getTechnologyType() {
        return _technologyType;
    }

    public void setTechnologyType(String technologyType) {
        this._technologyType = technologyType;
        setChanged("technologyType");
    }

    @Name("emName")
    public String getEmName() {
        return _emName;
    }

    public void setEmName(String emName) {
        this._emName = emName;
        setChanged("emName");
    }

    @Name("emBookmarkTime")
    public String getEmBookmarkTime() {
        return _emBookmarkTime;
    }

    public void setEmBookmarkTime(String emBookmarkTime) {
        this._emBookmarkTime = emBookmarkTime;
        setChanged("emBookmarkTime");
    }

    @Name("emCGGroupId")
    public Integer getEmCGGroupId() {
        return _emCGGroupId;
    }

    public void setEmCGGroupId(Integer emCGGroupId) {
        this._emCGGroupId = emCGGroupId;
        setChanged("emCGGroupId");
    }

    @Name("emCGGroupCopyId")
    public Integer getEmCGGroupCopyId() {
        return _emCGGroupCopyId;
    }

    public void setEmCGGroupCopyId(Integer emCGGroupCopyId) {
        this._emCGGroupCopyId = emCGGroupCopyId;
        setChanged("emCGGroupCopyId");
    }

    @Name("emInternalSiteName")
    public String getEmInternalSiteName() {
        return _emInternalSiteName;
    }

    public void setEmInternalSiteName(String emInternalSiteName) {
        this._emInternalSiteName = emInternalSiteName;
        setChanged("emInternalSiteName");
    }

    @Name("productionGroupId")
    public Integer getProductionGroupId() {
        return _productionGroupId;
    }

    public void setProductionGroupId(Integer productionGroupId) {
        this._productionGroupId = productionGroupId;
        setChanged("productionGroupId");
    }

    @Name("productionGroupCopyId")
    public Integer getProductionGroupCopyId() {
        return _productionGroupCopyId;
    }

    public void setProductionGroupCopyId(Integer productionGroupCopyId) {
        this._productionGroupCopyId = productionGroupCopyId;
        setChanged("productionGroupCopyId");
    }

    @Name("productionInternalSiteName")
    public String getProductionInternalSiteName() {
        return _productionInternalSiteName;
    }

    public void setProductionInternalSiteName(String productionInternalSiteName) {
        this._productionInternalSiteName = productionInternalSiteName;
        setChanged("productionInternalSiteName");
    }

    @XmlElement
    @RelationIndex(cf = "RelationIndex", type = ProtectionSet.class)
    @Name("protectionSet")
    public URI getProtectionSet() {
        return _protectionSet;
    }

    public void setProtectionSet(URI _protectionSet) {
        this._protectionSet = _protectionSet;
        setChanged("protectionSet");
    }

    @Name("provisionedCapacity")
    public Long getProvisionedCapacity() {
        return (null == _provisionedCapacity) ? 0L : _provisionedCapacity;
    }

    public void setProvisionedCapacity(Long provisionedCapacity) {
        _provisionedCapacity = provisionedCapacity;
        setChanged("provisionedCapacity");
    }

    @Name("allocatedCapacity")
    public Long getAllocatedCapacity() {
        return (null == _allocatedCapacity) ? 0L : _allocatedCapacity;
    }

    public void setAllocatedCapacity(Long allocatedCapacity) {
        _allocatedCapacity = allocatedCapacity;
        setChanged("allocatedCapacity");
    }

    @Name("needsCopyToTarget")
    public Boolean getNeedsCopyToTarget() {
        return (_needsCopyToTarget != null) ? _needsCopyToTarget : Boolean.FALSE;
    }

    public void setNeedsCopyToTarget(Boolean isAttached) {
        _needsCopyToTarget = isAttached;
        setChanged("needsCopyToTarget");
    }

    @Name("isReadOnly")
    public Boolean getIsReadOnly() {
        return (_isReadOnly != null) ? _isReadOnly : Boolean.FALSE;
    }

    public void setIsReadOnly(Boolean isReadOnly) {
        _isReadOnly = isReadOnly;
        setChanged("isReadOnly");
    }

    /**
     * Given a list of BlockSnapshot objects, determine if they were created as part of a
     * consistency group.
     * 
     * @param snapshotList
     *            [required] - List of BlockSnapshot objects
     * @return true if the BlockSnapshots were created as part of volume consistency group.
     */
    public static boolean inReplicationGroup(DbClient dbClient, final List<BlockSnapshot> snapshotList) {
        boolean isCgCreate = false;
        if (snapshotList.size() == 1) {
            BlockSnapshot snapshot = snapshotList.get(0);
            if (snapshot.hasConsistencyGroup()) {
                URI cgId = snapshot.getConsistencyGroup();
                final BlockConsistencyGroup group = dbClient.queryObject(
                        BlockConsistencyGroup.class, cgId);
                isCgCreate = group != null;
            }
        } else if (snapshotList.size() > 1) {
            isCgCreate = true;
        }
        return isCgCreate;
    }

    /**
     * Deprecated - Needed only for 2.1 migration callback.
     * 
     * Convenience method to get the consistency group for a snapshot. Snapshots
     * will only have one consistency group so get the first one.
     * 
     * @return
     */
    @Deprecated
    public URI fetchConsistencyGroup() {
        if (getConsistencyGroups() != null && !getConsistencyGroups().isEmpty()) {
            return URI.create(getConsistencyGroups().iterator().next());
        }
        return null;
    }

    /**
     * Returns true if the passed volume is in an export group, false otherwise.
     * 
     * @param dbClient A reference to a DbClient.
     * 
     * @return true if the passed volume is in an export group, false otherwise.
     */
    public boolean isSnapshotExported(DbClient dbClient) {
        URIQueryResultList exportGroupURIs = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.Factory.getBlockObjectExportGroupConstraint(getId()), exportGroupURIs);
        return exportGroupURIs.iterator().hasNext();
    }
}
