/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;

/**
 * Recommendation for a placement is a storage pool and its storage device.
 */
@SuppressWarnings("serial")
public class RPProtectionRecommendation extends Recommendation {

    // Source
    private String _sourceInternalSiteName;
    private Map<URI, Protection> _varrayProtectionMap;
    private URI _vpoolChangeVolume;
    private URI _vpoolChangeVpool;
    private boolean vpoolChangeProtectionAlreadyExists;
    private URI _sourceJournalStoragePool;
    private URI _sourceJournalVarray;
    private URI _sourceJournalVpool;
    private URI _standbySourceJournalVarray;
    private URI _standbySourceJournalVpool;

    // Protection/Replication mover (RP, for instance)
    private URI _protectionDevice;
    private PlacementProgress _placementStepsCompleted;
    private String _protectionSystemCriteriaError;
    // This is needed for MetroPoint. The concatenated string containing
    // both the RP internal site name + associated storage system.
    private String rpSiteAssociateStorageSystem;
    // This is the Storage System that was chosen by placement for connectivity/visibility to the RP Cluster
    private URI sourceInternalSiteStorageSystem;

    public static enum PlacementProgress {
        NONE, IDENTIFIED_SOLUTION_FOR_SOURCE, IDENTIFIED_SOLUTION_FOR_SUBSET_OF_TARGETS,
        IDENTIFIED_SOLUTION_FOR_ALL_TARGETS, PROTECTION_SYSTEM_CANNOT_FULFILL_REQUEST
    }

    public RPProtectionRecommendation() {
        _varrayProtectionMap = new HashMap<URI, Protection>();
        _placementStepsCompleted = PlacementProgress.NONE;
    }

    public RPProtectionRecommendation(RPProtectionRecommendation copy) {
        // properties of Recommendation
        this.setSourcePool(copy.getSourcePool());
        this.setSourceDevice(copy.getSourceDevice());
        this.setDeviceType(copy.getDeviceType());
        this.setResourceCount(copy.getResourceCount());
        // properties of RPProtectionRecommendation
        this._placementStepsCompleted = copy.getPlacementStepsCompleted();
        this._protectionDevice = copy.getProtectionDevice();
        this._sourceInternalSiteName = copy.getSourceInternalSiteName();
        this._sourceJournalStoragePool = copy.getSourceJournalStoragePool();
        this._sourceJournalVarray = copy.getSourceJournalVarray();
        this._sourceJournalVpool = copy.getSourceJournalVpool();
        this._standbySourceJournalVarray = copy.getStandbySourceJournalVarray();
        this._standbySourceJournalVpool = copy.getStandbySourceJournalVpool();
        this._vpoolChangeVolume = copy.getVpoolChangeVolume();
        this._vpoolChangeVpool = copy.getVpoolChangeVpool();
        // map
        this._varrayProtectionMap = new HashMap<URI, Protection>();
        this._varrayProtectionMap.putAll(copy.getVirtualArrayProtectionMap());
    }

    public void setSourceJournalStoragePool(URI sourceJournalStoragePool) {
        _sourceJournalStoragePool = sourceJournalStoragePool;
    }

    public URI getSourceJournalStoragePool() {
        return _sourceJournalStoragePool;
    }

    public String getSourceInternalSiteName() {
        return _sourceInternalSiteName;
    }

    public URI getProtectionDevice() {
        return _protectionDevice;
    }

    public boolean containsTargetInternalSiteName(String destInternalSiteName) {
        if (getVirtualArrayProtectionMap() != null) {
            for (Protection protection : getVirtualArrayProtectionMap().values()) {
                if (protection.getTargetInternalSiteName().equals(destInternalSiteName)) {
                    return true;
                }
            }
        }
        return false;
    }

    public Map<URI, Protection> getVirtualArrayProtectionMap() {
        return _varrayProtectionMap;
    }

    public void setVirtualArrayProtectionMap(
            Map<URI, Protection> varrayProtectionMap) {
        this._varrayProtectionMap = varrayProtectionMap;
    }

    public void setSourceInternalSiteName(String sourceInternalSiteName) {
        this._sourceInternalSiteName = sourceInternalSiteName;
    }

    public void setProtectionDevice(URI protectionDevice) {
        this._protectionDevice = protectionDevice;
    }

    public void setVpoolChangeVolume(URI id) {
        _vpoolChangeVolume = id;
    }

    public URI getVpoolChangeVolume() {
        return _vpoolChangeVolume;
    }

    public PlacementProgress getPlacementStepsCompleted() {
        return _placementStepsCompleted;
    }

    public void setPlacementStepsCompleted(PlacementProgress identifiedProtectionSolution) {
        this._placementStepsCompleted = identifiedProtectionSolution;
    }

    public String getProtectionSystemCriteriaError() {
        return _protectionSystemCriteriaError;
    }

    public void setProtectionSystemCriteriaError(String protectionSystemCriteriaError) {
        this._protectionSystemCriteriaError = protectionSystemCriteriaError;
    }

    public int getNumberOfVolumes(String internalSiteName) {
        int count = 0;

        if (this.getSourceInternalSiteName().equals(internalSiteName)) {
            count += this.getResourceCount();
        }

        if (getVirtualArrayProtectionMap() != null) {
            for (Protection protection : getVirtualArrayProtectionMap().values()) {
                if (protection.getTargetInternalSiteName().equals(internalSiteName)) {
                    if (protection.getTargetJournalStoragePool() != null) {
                        count++; // Journal Volume
                    }
                    count += this.getResourceCount();
                }
            }
        }

        return count;
    }

    @Override
    public String toString() {
        return "RPProtectionRecommendation [_sourceInternalSiteName="
                + _sourceInternalSiteName + ", _varrayProtectionMap="
                + _varrayProtectionMap + ", _vpoolChangeVolume="
                + _vpoolChangeVolume + ", _sourceJournalPool="
                + _sourceJournalStoragePool + ", _vpoolChangeVpool="
                + _sourceJournalVarray + ", _sourceJournalVarray="
                + _sourceJournalVpool + ", _sourceJournalVpool="
                + _vpoolChangeVpool + ", _protectionDevice="
                + _protectionDevice + "]";
    }

    public String toString(DbClient dbClient) {
        StringBuffer buff = new StringBuffer("\nRecoverPoint-Protected Placement Results\n");
        StoragePool pool = (StoragePool) dbClient.queryObject(StoragePool.class, this.getSourcePool());
        StoragePool journalStoragePool = (StoragePool) dbClient.queryObject(StoragePool.class, this._sourceJournalStoragePool);
        StorageSystem system = (StorageSystem) dbClient.queryObject(StorageSystem.class, this.getSourceDevice());
        ProtectionSystem ps = dbClient.queryObject(ProtectionSystem.class, _protectionDevice);
        VirtualArray journalVarray = dbClient.queryObject(VirtualArray.class, getSourceJournalVarray());
        VirtualPool journalVpool = dbClient.queryObject(VirtualPool.class, getSourceJournalVpool());
        String sourceRPSiteName = (ps.getRpSiteNames() != null) ? ps.getRpSiteNames().get(_sourceInternalSiteName)
                : _sourceInternalSiteName;
        buff.append("--------------------------------------\n");
        buff.append("--------------------------------------\n");
        buff.append("Number of volumes placed in this result: " + this.getResourceCount() + "\n");
        buff.append("Protection System Allocated : " + ps.getLabel() + "\n");
        buff.append("Source RP Site Allocated : " + sourceRPSiteName + "\n");
        buff.append("Source Storage System : " + system.getLabel() + "\n");
        buff.append("Source Storage Pool : " + pool.getLabel() + "\n");
        buff.append("Source Journal Virtual Array :" + journalVarray.getLabel() + " \n");
        buff.append("Source Journal Virtual Pool : " + journalVpool.getLabel() + " \n");
        buff.append("Source Journal Storage Pool : " + journalStoragePool.getLabel() + "\n");
        for (URI varrayId : this.getVirtualArrayProtectionMap().keySet()) {
            VirtualArray varray = (VirtualArray) dbClient.queryObject(VirtualArray.class, varrayId);
            StoragePool targetPool = (StoragePool) dbClient.queryObject(StoragePool.class, this.getVirtualArrayProtectionMap()
                    .get(varrayId).getTargetStoragePool());
            StoragePool targetJournalStoragePool = (StoragePool) dbClient.queryObject(StoragePool.class, this
                    .getVirtualArrayProtectionMap().get(varrayId).getTargetJournalStoragePool());
            VirtualArray targetJournalVarray = dbClient.queryObject(VirtualArray.class, this.getVirtualArrayProtectionMap().get(varrayId)
                    .getTargetJournalVarray());
            VirtualPool targetJournalVpool = dbClient.queryObject(VirtualPool.class, this.getVirtualArrayProtectionMap().get(varrayId)
                    .getTargetJournalVpool());
            StorageSystem targetSystem = (StorageSystem) dbClient.queryObject(StorageSystem.class,
                    this.getVirtualArrayProtectionMap().get(varrayId).getTargetDevice());
            String targetInternalSiteName = this.getVirtualArrayProtectionMap().get(varrayId).getTargetInternalSiteName();
            String targetRPSiteName = (ps.getRpSiteNames() != null) ? ps.getRpSiteNames().get(targetInternalSiteName)
                    : targetInternalSiteName;
            buff.append("\n-----------------------------\n");
            buff.append("Protecting to Virtual Array : " + varray.getLabel() + "\n");
            buff.append("Protection to RP Site : " + targetRPSiteName + "\n");
            buff.append("Protection to Storage System : " + targetSystem.getLabel() + "\n");
            buff.append("Protection to Storage Pool : " + targetPool.getLabel() + "\n");
            buff.append("Protection Journal Virtual Array : " + targetJournalVarray.getLabel() + " \n");
            buff.append("Protection Journal Virtual Pool : " + targetJournalVpool.getLabel() + " \n");
            buff.append("Protection Journal Storage Pool : " + targetJournalStoragePool.getLabel() + "\n");
        }
        buff.append("\n--------------------------------------\n");
        buff.append("--------------------------------------");
        return buff.toString();
    }

    public void setVpoolChangeVpool(URI id) {
        _vpoolChangeVpool = id;
    }

    public URI getVpoolChangeVpool() {
        return _vpoolChangeVpool;
    }

    public String getRpSiteAssociateStorageSystem() {
        return rpSiteAssociateStorageSystem;
    }

    public void setRpSiteAssociateStorageSystem(String rpSiteAssociateStorageSystem) {
        this.rpSiteAssociateStorageSystem = rpSiteAssociateStorageSystem;
    }

    public boolean isVpoolChangeProtectionAlreadyExists() {
        return vpoolChangeProtectionAlreadyExists;
    }

    public void setVpoolChangeProtectionAlreadyExists(
            boolean vpoolChangeProtectionAlreadyExists) {
        this.vpoolChangeProtectionAlreadyExists = vpoolChangeProtectionAlreadyExists;
    }

    public URI getSourceJournalVarray() {
        return _sourceJournalVarray;
    }

    public void setSourceJournalVarray(URI _sourceJournalVarray) {
        this._sourceJournalVarray = _sourceJournalVarray;
    }

    public URI getSourceJournalVpool() {
        return _sourceJournalVpool;
    }

    public void setSourceJournalVpool(URI _sourceJournalVpool) {
        this._sourceJournalVpool = _sourceJournalVpool;
    }

    public URI getStandbySourceJournalVarray() {
        return _standbySourceJournalVarray;
    }

    public void setStandbySourceJournalVarray(
            URI _standbySourceJournalVarray) {
        this._standbySourceJournalVarray = _standbySourceJournalVarray;
    }

    public URI getStandbySourceJournalVpool() {
        return _standbySourceJournalVpool;
    }

    public void setStandbySourceJournalVpool(URI _standbySourceJournalVpool) {
        this._standbySourceJournalVpool = _standbySourceJournalVpool;
    }

    public URI getSourceInternalSiteStorageSystem() {
        return sourceInternalSiteStorageSystem;
    }

    public void setSourceInternalSiteStorageSystem(
            URI sourceInternalSiteStorageSystem) {
        this.sourceInternalSiteStorageSystem = sourceInternalSiteStorageSystem;
    }
}
