/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller;

import com.emc.storageos.db.client.model.*;

import java.net.URI;
import java.util.List;

/**
 * Class defining input/output from Block storage device interface
 * to expose only the fields that are needed/can be modified by storage device implementations
 */
public class BlockDeviceInputOutput {
    private StoragePool         pool;
    private Volume              volume;
    private BlockSnapshot       snapshot;
    private String      storageGroupName;
    private String _opId;
    private List<StoragePort> _storagePorts;

    /**
     *
     * @return
     */
    public String getStorageGroupName() {
        return (storageGroupName != null) ? storageGroupName : "";
    }

    /**
     * The StorageGroup name to be used
     * @param storageGroupName
     */
    public void setStorageGroupName(String storageGroupName) {
        this.storageGroupName = storageGroupName;
    }

    /**
     * add storage pool 
     * @param pool        StoragePool object
     */
    public void addStoragePool(StoragePool pool){
        this.pool = pool;
    }

    /**
     * add volume 
     * @param volume        FileShare object
     */
    public void addVolume(Volume volume) {
        this.volume = volume;
    }

    public URI getVolumeURI() {
        return volume.getId();
    }

    /**
     * Get volume capacity
     * @return  Long
     */
    public Long getVolumeCapacity(){
        return volume.getCapacity();
    }

    /**
     * Get volume label
     * @return
     */
    public String getVolumeLabel(){
        return volume.getLabel();
    }

    /**
     * Get volume nativeId
     * @return
     */
    public String getVolumeNativeId(){
        return volume.getNativeId();
    }

    /**
     * Get volume extensions
     * @return
     */
    public StringMap getVolumeExtensions(){
        return volume.getExtensions();
    }

    /**
     * Set volume nativeId
     * @param id
     */
    public void setVolumeNativeId(String id){
        volume.setNativeId(id);
    }

    /**
     * Set volume nativeGuid
     * @param id
     */
    public void setVolumeNativeGuid(String id){
        volume.setNativeGuid(id);
    }

    /**
     * Initialize volume extensions map
     */
    public void initVolumeExtensions(){
        volume.setExtensions(new StringMap());
    }

    /**
     * Set the snapshot
     * @param snap
     */
    public void addSnapshot(BlockSnapshot snap) {
        snapshot = snap;
    }

    /**
     * Get the Snapshot Native Id
     * @return
     */
    public String getSnapshotNativeId() {
        return snapshot.getNativeId();
    }

    /**
     * Set the Snapshot Native Id
     * @return
     */
    public void setSnapshotNativeId(String newId) {
        snapshot.setNativeId(newId);
    }

    /**
     * Get the SnapSet label
     */
    public String getSnapsetLabel() {
        return "";
    }

    public void setOpId(String opId) {
        _opId = opId;
    }

    public String getOpId() {
        return _opId;
    }

	/**
	 * Get the Consistency Group Label
	 */
	public URI getSnapConsistencyGroup() {
		URI value = snapshot.getConsistencyGroup();
		return value;
	}

    public void setStoragePorts(List<StoragePort> ports) {
        _storagePorts = ports;
    }

    public List<StoragePort> getStoragePorts() {
        return _storagePorts;
    }

    /**
     * Get snapshot extensions
     * 
     */
    public StringMap getSnapshotExtensions(){
        return volume.getExtensions();
    }

    /**
     * Get snapshot's volume Id
     * 
     */
    public String getSnapshotVolumeId() {
        return snapshot.getNewVolumeNativeId();
    }
}
