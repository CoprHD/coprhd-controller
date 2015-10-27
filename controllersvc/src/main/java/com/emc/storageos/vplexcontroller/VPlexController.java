/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplexcontroller;

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.emc.storageos.Controller;
import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.ControllerException;

public interface VPlexController extends Controller {

    /**
     * Migrates the data on the backend storage volumes of the passed virtual
     * volume on the passed VPlex storage system. This function starts a job for
     * each backend volume migration. When complete, the migrations are
     * automatically committed and cleaned. The old source volumes are then
     * deleted so that storage can be reclaimed.
     * 
     * @param vplexURI The URI of the VPlex storage system.
     * @param virtualVolumeURI The URI of the VPlex virtual volume.
     * @param targetVolumeURIs The URIs of the volume(s) to which the data is
     *            migrated.
     * @param migrationsMap The URIs of the migrations keyed by target volume.
     * @param poolVolumeMap The pool map keys specify the storage pools on which
     *            the new backend volumes should be created, while the values
     *            specify the volumes to be created on a given pool.
     * @param newCoSURI The new CoS for the virtual volumes after the migrations
     *            complete and are committed.
     * @param newNhURI The new varray for the virtual volumes after the
     *            migrations complete and are committed.
     * @param successMsg The task message on success.
     * @param failMsg The task message on failure.
     * @param opType operation type enum, for audit
     * @param opId The unique task identifier for the migration.
     * @param wfStepId Workflow step id when called from a step in another workflow or null.
     * 
     * @throws InternalException When an error occurs creating the migrations.
     */
    public abstract void migrateVolumes(URI vplexURI, URI virtualVolumeURI,
            List<URI> targetVolumeURIs, Map<URI, URI> migrationsMap,
            Map<URI, URI> poolVolumeMap, URI newCoSURI, URI newNhURI, String successMsg,
            String failMsg, OperationTypeEnum opType, String opId, String wfStepId) throws InternalException;

    /**
     * This code handles two use cases:
     * 1. Importing a non-vplex volume to either vplex_local or vplex_distributed.
     * 2. Upgrading a vplex_local to vplex_distributed.
     * 
     * @param vplexURI
     * @param descriptors -- A list of VolumeDescriptor. This will have multiple descriptors:
     *            1. The VPLEX_VIRT_VOLUME descriptor representing the VPLEX virtual volume to be created.
     *            2. For use case 1, a VPLEX_IMPORT_VOLUME that represents the existing volume to be imported.
     *            3. For either an import to a distributed virtual volume, or an upgrade from vplex_local to
     *            vplex_distributed (use case 2), a BLOCK_DATA volume that is to be created on the HA varray.
     * @param vplexSystemProject -- The imported volume will be moved to this Project if successful.
     * @param vplexSystemTenant -- The imported volume will be moved to this Tenant if successful.
     * @param newCos -- the new CoS that will be applied to the volume after an import.
     * @param newLabel -- the new label that will be applied to the volume after an import.
     * 
     * @param opId -- The task id.
     * @throws InternalException
     */
    public abstract void importVolume(URI vplexURI, List<VolumeDescriptor> descriptors,
            URI vplexSystemProject, URI vplexSystemTenant, URI newCos, String newLabel,
            String opId) throws InternalException;

    /**
     * Expands the virtual volume by migrating the backend volumes to new
     * passed target volumes which are of the new expanded size.
     * 
     * @param vplexURI The URI of the VPlex storage system.
     * @param vplexVolumeURI The URI of the VPlex volume.
     * @param targetVolumeURIs The URIs of the volume(s) to which the data is
     *            migrated.
     * @param migrationsMap The URIs of the migrations keyed by target volume.
     * @param poolVolumeMap The pool map keys specify the storage pools on which
     *            the new target volumes should be created, while the values
     *            specify the volumes to be created on a given pool.
     * @param newSize The requested new volume size.
     * @param opId The unique task identifier for the expansion.
     * 
     * @throws InternalException When an error occurs creating the expansion workflow.
     */
    public abstract void expandVolumeUsingMigration(URI vplexURI, URI vplexVolumeURI,
            List<URI> targetVolumeURIs, Map<URI, URI> migrationsMap,
            Map<URI, URI> poolVolumeMap, Long newSize, String opId) throws InternalException;

    /**
     * Deletes the VPLEX consistency group with the passed URI on the VPLEX
     * storage system with the passed URI. Assumes that the consistency group
     * has no volumes in at the time of deletion.
     * 
     * @param vplexURI The URI of the VPlex storage system.
     * @param cgURI The URI of the consistency group.
     * @param opId The unique task identifier.
     * 
     * @throws InternalException When an error occurs configuring the
     *             consistency group deletion workflow.
     */
    public abstract void deleteConsistencyGroup(URI vplexURI, URI cgURI, String opId)
            throws InternalException;

    /**
     * Updates the VPLEX consistency group by adding/removing the passed volumes
     * to/from the consistency group.
     * 
     * @param vplexURI The URI of the VPLEX storage system.
     * @param cgURI The URI of the consistency group.
     * @param addVolumesList The URIs of the volumes to be added.
     * @param removeVolumesList The URIs of the volumes to be removed.
     * @param opId The unique task identifier.
     * 
     * @throws InternalException When an error occurs configuring the
     *             consistency update workflow.
     */
    public abstract void updateConsistencyGroup(URI vplexURI, URI cgURI,
            List<URI> addVolumesList, List<URI> removeVolumesList, String opId)
            throws InternalException;

    /**
     * Creates a full copy (clone) of a VPLEX volume on the passed VPLEX storage
     * system. The clone is created by natively cloning the source volume of the
     * VPLEX virtual volume and then importing the cloned backend volume as a
     * local or distributed virtual volume.
     * 
     * @param vplexURI The URI of the VPLEX storage system.
     * @param volumeDescriptors The volumes descriptors for the required
     *            volumes.
     * @param opId The unique task identifier.
     * 
     * @throws InternalException When an error occurs configuring the full
     *             copy workflow.
     */
    public abstract void createFullCopy(URI vplexURI,
            List<VolumeDescriptor> volumeDescriptors, String opId) throws InternalException;

    /**
     * Restore contents the source volumes for the full copies with the passed
     * URIs.
     * 
     * @param vplexURI The URI of the VPLEX storage system.
     * @param fullCopyURIs The URIs of the full copies to be restored.
     * @param opId The unique operation Id.
     * 
     * @throws InternalException When an exception occurs restoring the full
     *             copies.
     */
    public void restoreFromFullCopy(URI vplexURI, List<URI> fullCopyURIs, String opId)
            throws InternalException;

    /**
     * Resynchronize the full copies with the passed URIs from their
     * corresponding source volumes.
     * 
     * @param vplexURI The URI of the VPLEX storage system.
     * @param fullCopyURIs The URIs of the full copies to be resynchronized.
     * @param opId The unique operation Id.
     * 
     * @throws InternalException When an exception occurs resynchronizing the
     *             full copies.
     */
    public void resyncFullCopy(URI vplexURI, List<URI> fullCopyURIs, String opId)
            throws InternalException;

    /**
     * Detach the full copies with the passed URIs from their
     * corresponding source volumes.
     * 
     * @param vplexURI The URI of the VPLEX storage system.
     * @param fullCopyURIs The URIs of the full copies to be detached.
     * @param opId The unique operation Id.
     * 
     * @throws InternalException When an exception occurs detaching the
     *             full copies.
     */
    public void detachFullCopy(URI vplexURI, List<URI> fullCopyURIs, String opId)
            throws InternalException;

    /**
     * Restores a VPLEX volume by restoring a native snapshot of the source
     * backend volume for the VPLEX volume and invalidating the read cache for
     * the VPLEX volume. Presumes that I/O for exported volumes has been
     * quiesced at the host and that host write buffers/cache have been cleared.
     * 
     * @param vplexURI The URI of the VPLEX storage system.
     * @param snapshotURI The URI of the backend native block snapshot.
     * @param opId The unique task identifier.
     * @throws InternalException When an error occurs configuring the snapshot
     *             restore workflow.
     */
    public abstract void restoreVolume(URI vplexURI, URI snapshotURI, String opId)
            throws InternalException;

    /**
     * Attach new mirror(s) for the given volume
     * 
     * @param vplexURI The URI of the VPLEX storage system.
     * @param volumeDescriptors The complete list of VolumeDescriptors received from the API layer.
     *            This defines what mirrors need to be created.
     * @param sourceVolumeURI The URI of the source volume.
     * @param opId The overall opId for the operation.
     * 
     * @throws InternalException When an error occurs creating and attaching mirror to the VPLEX
     *             volume
     */
    public void attachContinuousCopies(URI vplexURI, List<VolumeDescriptor> volumeDescriptors,
            URI sourceVolumeURI, String opId) throws InternalException;

    /**
     * Detaches the mirror device from the source and deletes everything that related to mirror device
     * including the back-end storage volume
     * 
     * @param vplexURI The URI of the VPLEX storage system.
     * @param mirrorURI The URI of the mirror that needs to be removed.
     * @param volumeDescriptors The list of VolumeDescriptors received from the API layer.
     *            This defines back-end storage volume for the mirror
     * @param opId The overall opId for the operation.
     * 
     * @throws InternalException When an error occurs detaching mirror from the VPLEX volume and deleting mirror
     */
    public void deactivateMirror(URI vplexURI, URI mirrorURI, List<VolumeDescriptor> volumeDescriptors,
            String opId) throws InternalException;

    /**
     * @param vplexURI The URI of the VPLEX storage system.
     * @param sourceVolumeURI The URI of the source volume.
     * @param mirrors The URI of the mirrors that needs to be detached.
     * @param promotees The URI of the back-end volumes that will be promoted as independent volumes.
     * @param opId The overall opId for the operation.
     * 
     * @throws InternalException When an error occurs detaching mirror and converting mirror to a VPLEX Volume
     */
    public void detachContinuousCopies(URI vplexURI, URI sourceVolumeURI, List<URI> mirrors, List<URI> promotees,
            String opId) throws InternalException;

    /**
     * Validate a VPLEX Storage Provider connection.
     * 
     * @param ipAddress the Storage Provider's IP address
     * @param portNumber the Storage Provider's IP port
     * 
     * @return true if the Storage Provider connection is valid
     */
    public boolean validateStorageProviderConnection(String ipAddress, Integer portNumber);
    
    /**
     * Pause a migration that is in progress.
     * 
     * @param vplexURI The URI of the VPLEX storage system.
     * @param migrationURI The URI of the migration.
     * @param opId the opId for the operation
     */
    public void pauseMigration(URI vplexURI, URI migrationURI, String opId);
    
    /**
     * Resume a migration that is paused.
     * @param vplexURI
     * @param migrationURI
     * @param opId
     */
    public void resumeMigration(URI vplexURI, URI migrationURI, String opId);
    
    /**
     * Cancel a migration
     * @param vplexURI The URI of the VPLEX storage system.
     * @param migrationURI The URI of the migration.
     * @param opId the opId for the operation
     */
    public void cancelMigration(URI vplexURI, URI migrationURI, String opId);
    
    /**
     * Delete a migration
     * @param vplexURI The URI of the VPLEX storage system.
     * @param migrationURI The URI of the migration.
     * @param opId the opId for the operation
     */
    public void deleteMigration(URI vplexURI, URI migrationURI, String opId);

    /**
     * Establishes group relation between volume group and full copy group.
     * 
     * @param storage the storage
     * @param sourceVolume the source volume
     * @param fullCopy the full copy
     * @param opId the op id
     * @throws ControllerException the controller exception
     */
    public void establishVolumeAndFullCopyGroupRelation(URI storage, URI sourceVolume, URI fullCopy, String opId)
            throws InternalException;

    /**
     * Resynchronizes a snapshot of a VPLEX volume.
     * 
     * @param vplexURI The URI of the VPLEX storage system.
     * @param snapshotURI The URI of the snapshot.
     * @param opId The unique operation identifier.
     * 
     * @throws InternalException
     */
    public void resyncSnapshot(URI vplexURI, URI snapshotURI, String opId) throws InternalException;
}