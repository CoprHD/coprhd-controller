/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.fullcopy;

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.block.VolumeRestRep;

/**
 * Defines an interface for full copy services so that platform specific
 * requirements for full copy operations can be encapsulated in platform
 * specific implementations.
 */
public interface BlockFullCopyApi {

    /**
     * Get a list of all block objects to be operated on given the passed
     * full copy source object for a full copy request.
     * 
     * @param fcSourceObj A reference to a Volume or BlockSnapshot instance.
     * 
     * @return A list of all full copy source objects.
     */
    public List<BlockObject> getAllSourceObjectsForFullCopyRequest(BlockObject fcSourceObj);

    /**
     * Given the passed source and fully copy volume, get a map
     * of all full copies in the full copy set.
     * 
     * @param fcSourceObj A reference to the full copy source object.
     * @param fullCopyVolume A reference to a full copy of the source.
     * 
     * @return A map of full copy volumes keyed by full copy URI.
     */
    public Map<URI, Volume> getFullCopySetMap(BlockObject fcSourceObj,
            Volume fullCopyVolume);

    /**
     * Validate a full copy request for the passed Volumes or BlockSnapshots.
     * 
     * @param fcSourceObjList A list of Volumes or a list of BlockSnapshots.
     * @param count The number of full copies requested.
     */
    public void validateFullCopyCreateRequest(List<BlockObject> fcSourceObjList, int count);

    /**
     * Verifies that the volume's status with respect to full copies allows
     * snapshots to be created.
     * 
     * @param requestedVolume A reference to the volume for which a snapshot was
     *            requested.
     * @param volumesToSnap The list of volumes that would be snapped.
     */
    public void validateSnapshotCreateRequest(Volume requestedVolume,
            List<Volume> volumesToSnap);

    /**
     * Creates one or more full copies using the passed parameters.
     * 
     * @param fcSourceObj A list of Volumes or a list of BlockSnapshots
     * @param varray A reference to the virtual array.
     * @param name The name for the copies.
     * @param createInactive true to create the full copies inactive, false otherwise.
     * @param count The number of full copies to create.
     * @param taskId The unique task identifier.
     * 
     * @return TaskList
     */
    public TaskList create(List<BlockObject> fcSourceObjList, VirtualArray varray,
            String name, boolean createInactive, int count, String taskId);

    /**
     * Activate the passed full copy volume.
     * 
     * @param fcSourceObj A reference to the full copy source.
     * @param fullCopyVolume A reference to the full copy volume.
     * 
     * @returns TaskList
     */
    public TaskList activate(BlockObject fcSourceObj, Volume fullCopyVolume);

    /**
     * Detach the passed full copy volume.
     * 
     * @param fcSourceObj TODO
     * @param fullCopyVolume A reference to the full copy volume.
     * 
     * @returns TaskList
     */
    public TaskList detach(BlockObject fcSourceObj, Volume fullCopyVolume);

    /**
     * Restore the source volume from the passed full copy.
     * 
     * @param sourceVolume A reference to the full copy source volume to restore.
     * @param fullCopyVolume A reference to the full copy volume.
     * 
     * @return TaskList
     */
    public TaskList restoreSource(Volume sourceVolume, Volume fullCopyVolume);

    /**
     * Resynchronize the passed full copy of the passed source volume.
     * 
     * @param sourceVolume A reference to the full copy source volume.
     * @param fullCopyVolume A reference to the full copy volume.
     * 
     * @return TaskList
     */
    public TaskList resynchronizeCopy(Volume sourceVolume, Volume fullCopyVolume);

    /**
     * Creates group synchronized relation between volume group
     * and full copy group.
     * 
     * @param sourceVolume A reference to the full copy source volume.
     * @param fullCopyVolume A reference to the full copy volume.
     * 
     * @return TaskList
     */
    public TaskList establishVolumeAndFullCopyGroupRelation(Volume sourceVolume, Volume fullCopyVolume);

    /**
     * Checks the progress of a data copy between the source and full copy.
     * 
     * @param sourceURI A URI of the source.
     * @param fullCopyVolume A reference to the full copy volume.
     * 
     * @return VolumeRestRep
     */
    public VolumeRestRep checkProgress(URI sourceURI, Volume fullCopyVolume);
    
    /**
     * Verify that the passed volume can be deleted.
     * 
     * @param volume A reference to a volume.
     * 
     * @return true if the volume can be deleted, false otherwise.
     */
    public boolean volumeCanBeDeleted(Volume volume);

    /**
     * Verify that the passed volume can be expanded.
     * 
     * @param volume A reference to a volume.
     * 
     * @return true if the volume can be expanded, false otherwise.
     */
    public boolean volumeCanBeExpanded(Volume volume);
}
