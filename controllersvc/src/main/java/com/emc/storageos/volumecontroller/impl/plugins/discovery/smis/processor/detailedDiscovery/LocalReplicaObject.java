/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.detailedDiscovery;

import com.emc.storageos.db.client.model.StringSet;

public class LocalReplicaObject {
    public enum Types {
        Volume, FullCopy, BlockMirror, BlockSnapshot
    }

    // Native Guid of the volume/clone/mirror/snapshot
    private String nativeGuid;

    // source Volume Native Guid
    private String sourceNativeGuid;

    // Native Guids of full copies
    private StringSet fullCopies;

    // Native Guids of local mirrors
    private StringSet mirrors;

    // Native Guids of snapshots
    private StringSet snapshots;

    // for clone
    private String replicaState;

    // for clone and snapshot
    private boolean isSyncActive = false;

    // for local mirror
    private String syncState;
    private String syncType;
    private String synchronizedInstance;

    // for snapshot
    private boolean needsCopyToTarget = false;
    private String technologyType;
    private String settingsInstance;

    private Types type;

    public LocalReplicaObject(String nativeGuid) {
        this.nativeGuid = nativeGuid;
    }

    public String getNativeGuid() {
        return nativeGuid;
    }

    public String getReplicaState() {
        return replicaState;
    }

    public void setReplicaState(String replicaState) {
        this.replicaState = replicaState;
    }

    public String getSyncState() {
        return syncState;
    }

    public void setSyncState(String syncState) {
        this.syncState = syncState;
    }

    public String getSyncType() {
        return syncType;
    }

    public void setSyncType(String syncType) {
        this.syncType = syncType;
    }

    public String getSynchronizedInstance() {
        return synchronizedInstance;
    }

    public void setSynchronizedInstance(String synchronizedInstance) {
        this.synchronizedInstance = synchronizedInstance;
    }

    public boolean isNeedsCopyToTarget() {
        return needsCopyToTarget;
    }

    public void setNeedsCopyToTarget(boolean needsCopyToTarget) {
        this.needsCopyToTarget = needsCopyToTarget;
    }

    public String getTechnologyType() {
        return technologyType;
    }

    public void setTechnologyType(String technologyType) {
        this.technologyType = technologyType;
    }

    public String getSettingsInstance() {
        return settingsInstance;
    }

    public void setSettingsInstance(String settingsInstance) {
        this.settingsInstance = settingsInstance;
    }

    public StringSet getFullCopies() {
        return fullCopies;
    }

    public void setFullCopies(StringSet fullCopies) {
        this.fullCopies = fullCopies;
    }

    public StringSet getMirrors() {
        return mirrors;
    }

    public void setMirrors(StringSet mirrors) {
        this.mirrors = mirrors;
    }

    public StringSet getSnapshots() {
        return snapshots;
    }

    public void setSnapshots(StringSet snapshots) {
        this.snapshots = snapshots;
    }

    public String getSourceNativeGuid() {
        return sourceNativeGuid;
    }

    public void setSourceNativeGuid(String sourceNativeGuid) {
        this.sourceNativeGuid = sourceNativeGuid;
    }

    public Types getType() {
        return type;
    }

    public void setType(Types type) {
        this.type = type;
    }

    public boolean isSyncActive() {
        return isSyncActive;
    }

    public void setSyncActive(boolean isSyncActive) {
        this.isSyncActive = isSyncActive;
    }

    public boolean isReplica() {
        if (sourceNativeGuid != null) {
            return true;
        }

        return false;
    }

    public boolean hasReplica() {
        if ((fullCopies != null && !fullCopies.isEmpty()) ||
                (mirrors != null && !mirrors.isEmpty()) ||
                (snapshots != null && !snapshots.isEmpty())) {
            return true;
        }

        return false;
    }

    public StringSet getReplicas() {
        StringSet stringSet = new StringSet();
        if (fullCopies != null && !fullCopies.isEmpty()) {
            stringSet.addAll(fullCopies);
        }

        if (mirrors != null && !mirrors.isEmpty()) {
            stringSet.addAll(mirrors);
        }

        if (snapshots != null && !snapshots.isEmpty()) {
            stringSet.addAll(snapshots);
        }

        return stringSet;
    }

    public String toString() {
        StringBuffer buffer = new StringBuffer();
        buffer.append("nativeGuid:");
        buffer.append(nativeGuid);
        buffer.append(";type:");
        buffer.append(type);
        buffer.append(";sourceNativeGuid:");
        buffer.append(sourceNativeGuid);
        buffer.append(";fullCopies:");
        buffer.append(fullCopies);
        buffer.append(";mirrors:");
        buffer.append(mirrors);
        buffer.append(";snapshots:");
        buffer.append(snapshots);
        buffer.append(";isCloneRestorable:");
        buffer.append(replicaState);
        buffer.append(";syncState:");
        buffer.append(syncState);
        buffer.append(";syncType:");
        buffer.append(syncType);
        buffer.append(";synchronizedInstance:");
        buffer.append(synchronizedInstance);
        buffer.append(";needsCopyToTarget:");
        buffer.append(needsCopyToTarget);
        buffer.append(";isSyncActive:");
        buffer.append(isSyncActive);
        buffer.append(";technologyType:");
        buffer.append(technologyType);
        buffer.append(";settingsInstance:");
        buffer.append(settingsInstance);

        return buffer.toString();
    }
}
