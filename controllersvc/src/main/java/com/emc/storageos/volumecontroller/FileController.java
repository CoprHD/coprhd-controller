/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller;

import java.net.URI;
import java.util.List;

import com.emc.storageos.model.file.CifsShareACLUpdateParams;
import com.emc.storageos.model.file.FileExportUpdateParams;
import com.emc.storageos.model.file.NfsACLUpdateParams;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;

/**
 * Main block volume controller interfaces.
 * 
 * URI pool: The following information will be available from pool URI lookup.
 * id: Pool identifier.
 * type: Pool type.
 * 
 * URI fs: The following information will be available from fs URI lookup.
 * name: Friendly name for the FS share.
 * capacity: Size of the filesystem share.
 * thinProvision: Whether FS share is thinly provisioned.
 * cosParams: Class-of-service parameters specified as one or more key-value pairs.
 * 
 * URI snapshot: Snapshot and FS shares are identical from storage controller perspective (for now).
 */
public interface FileController extends StorageController {
    /**
     * Create a file system share.
     * 
     * @param storage
     *            URI of storage controller.
     * @param pool
     *            URI of storage pool of FS share.
     * @param fs
     *            URI of filesystem share to be created.
     * @param suggestedNativeFsId
     *            Suggested Native FileSystem Id
     * @param opId
     *            Operation status ID to track this operation (in FileShare)
     * @throws InternalException
     *             Storage controller exceptions.
     */
    public void createFS(URI storage, URI pool, URI fs, String suggestedNativeFsId, String opId) throws InternalException;

    /**
     * Delete a filesystem share. All exports must be removed prior to deletion.
     * 
     * @param storage
     *            URI of storage controller.
     * @param pool
     *            URI of storage pool of FS share.
     * @param uri
     *            URI of filesystem share or snapshot to be deleted.
     * @param forceDelete
     *            optional boolean that represents to force delete of a file system.
     * @param deleteType
     *            optional String that represents to type of delete(FULL/VIPR_ONLY) of a file system.
     * @param opId
     *            Operation status ID to track this operation (in FileShare)
     * @throws InternalException
     *             Storage controller exceptions.
     */
    public void delete(URI storage, URI pool, URI uri, boolean forceDelete, String deleteType, String opId) throws InternalException;

    /**
     * Export a filesystem to one or more remote mount points.
     * 
     * @param storage
     *            URI of storage controller.
     * @param uri
     *            URI of filesystem or snapshot to be exported.
     * @param exports
     *            Filesystem export list for addition.
     * @throws InternalException
     *             Storage controller exceptions.
     */
    public void export(URI storage, URI uri, List<FileShareExport> exports, String opId) throws InternalException;

    /**
     * Remove filesystem exports for one or more remote mount points. If exports
     * list is empty, all export entries are removed.
     * 
     * @param storage
     *            URI of storage controller.
     * @param uri
     *            URI of filesystem or snapshot for removing exports.
     * @param exports
     *            Filesystem export list for removal.
     * @throws InternalException
     *             Storage controller exceptions.
     */
    public void unexport(URI storage, URI uri, List<FileShareExport> exports, String opId) throws InternalException;

    /**
     * Modify class-of-service parameters and storage pool of a filesystem share.
     * 
     * @param storage
     *            URI of storage controller.
     * @param pool
     *            URI of storage pool of FS share.
     * @param fs
     *            URI of filesystem share to be modified.
     * @throws InternalException
     *             Storage controller exceptions.
     */
    public void modifyFS(URI storage, URI pool, URI fs, String opId) throws InternalException;

    /**
     * Expand filesystem .
     * 
     * @param storage
     *            URI of storage controller.
     * @param fs
     *            URI of filesystem to be expanded.
     * @param size
     *            filesystem expansion size
     * @throws InternalException
     *             Storage controller exceptions.
     */
    public void expandFS(URI storage, URI fs, long size, String opId) throws InternalException;

    /**
     * Create SMB share for file system/snapshot uri
     * 
     * @param storage
     *            URI of storage device
     * @param uri
     *            URI of file system to be shared
     * @param smbShare
     *            FileSMBShare instance with share properties
     * @param opId
     *            Task id of this operation
     * @throws InternalException
     */
    public void share(URI storage, URI uri, FileSMBShare smbShare, String opId) throws InternalException;

    /**
     * Delete SMB share for file system/snapshot uri
     * 
     * @param storage
     *            URI of storage device
     * @param uri
     *            URI of file system for which share is deleted
     * @param smbShare
     *            FileSMBShare instance with share properties
     * @param opId
     *            Task id of this operation
     * @throws InternalException
     */
    public void deleteShare(URI storage, URI uri, FileSMBShare smbShare, String opId) throws InternalException;

    /**
     * Create a snapshot of a filesystem.
     * 
     * @param storage
     *            URI of storage controller.
     * @param snapshot
     *            URI of snapshot being created.
     * @param fs
     *            URI of filesystem to create snapshot.
     * @throws InternalException
     *             Storage controller exceptions.
     */
    public void snapshotFS(URI storage, URI snapshot, URI fs, String opId) throws InternalException;

    /**
     * Restore contents of a filesystem from a given snapshot.
     * 
     * @param storage
     *            URI of storage controller.
     * @param fs
     *            URI of filesystem to be restored.
     * @param snapshot
     *            URI of snapshot used for restoration.
     * @throws InternalException
     *             Storage controller exceptions.
     */
    public void restoreFS(URI storage, URI fs, URI snapshot, String opId) throws InternalException;

    /**
     * Create a qtree within a filesystem.
     * 
     * @param storage
     *            URI of storage controller.
     * @param quotaDir
     *            URI of the quota dir being created.
     * @param fs
     *            URI of filesystem within which to create qtree.
     * @throws InternalException
     *             Storage controller exceptions.
     */
    public void createQuotaDirectory(URI storage, FileShareQuotaDirectory quotaDir, URI fs, String opId) throws InternalException;

    /**
     * Delete a qtree within a filesystem.
     * 
     * @param storage
     *            URI of storage controller.
     * @param quotaDir
     *            URI of the quota dir being created.
     * @param fs
     *            URI of filesystem within which to create qtree.
     * @throws InternalException
     *             Storage controller exceptions.
     */
    public void deleteQuotaDirectory(URI storage, URI quotaDir, URI fs, String opId) throws InternalException;

    /**
     * Update a qtree within a filesystem.
     * 
     * @param storage
     *            URI of storage controller.
     * @param qtree
     *            URI of qtree being created.
     * @param fs
     *            URI of filesystem within which to create qtree.
     * @throws InternalException
     *             Storage controller exceptions.
     */
    public void updateQuotaDirectory(URI storage, FileShareQuotaDirectory quotaDir, URI fs, String opId) throws InternalException;

    /**
     * Updates the NFS export rules
     * 
     * @param storage
     *            URI of storage controller.
     * @param fs
     *            URI of filesystem or snapshot whose export is to be updated.
     * @param param
     *            Filesystem export list for update.
     * @throws InternalException
     *             Storage controller exceptions.
     */
    public void updateExportRules(URI storage, URI fs, FileExportUpdateParams param, String opId)
            throws InternalException;

    /**
     * Delete the NFS export rules
     * 
     * @param storage
     *            URI of storage controller.
     * @param fileUri
     *            URI of associated filesystem or snapshot
     * @param allDirs
     *            whether exports of all directories should be deleted
     * @param subDir
     *            subdir for which export is to be deleted
     * @throws InternalException
     *             Storage controller exceptions.
     */
    public void deleteExportRules(URI storage, URI fileUri, boolean allDirs, String subDir, String opId)
            throws InternalException;

    /**
     * Update the CIFS Share ACLs
     * 
     * @param storage
     *            URI of storage controller.
     * @param fs
     *            URI of associated filesystem or snapshot
     * @param shareName
     *            Name of the share to be updated
     * @param param
     *            CIFS share ACLs update Params
     * @throws InternalException
     *             Storage controller exceptions.
     */
    public void updateShareACLs(URI storage, URI fs, String shareName,
            CifsShareACLUpdateParams param, String opId) throws InternalException;

    /**
     * Delete the CIFS Share ACLs
     * 
     * @param storage
     *            URI of storage controller.
     * @param fs
     *            URI of associated filesystem or snapshot
     * @param shareName
     *            Name of the share to be deleted
     * @param param
     *            CIFS share ACLs delete Params
     * @throws InternalException
     *             Storage controller exceptions.
     */
    public void deleteShareACLs(URI storage, URI fs, String shareName, String opId)
            throws InternalException;

    /**
     * Update the NFS ACLs
     * 
     * @param storage
     *            URI of storage controller.
     * @param fs
     *            URI of associated filesystem or snapshot
     * @param param
     *            NFS share ACLs update Params
     * @throws InternalException
     *             Storage controller exceptions.
     */
    public void updateNFSAcl(URI storage, URI fs, NfsACLUpdateParams param, String opId) throws InternalException;

    /**
     * delete the NFS ACLs
     * 
     * @param storage
     *            URI of storage controller.
     * @param fs
     *            URI of associated filesystem or snapshot
     * @param subDir
     *            subDir for which acls are to be deleted
     * @throws InternalException
     *             Storage controller exceptions.
     */
    public void deleteNFSAcls(URI storage, URI fs, String subDir, String opId) throws InternalException;

    /**
     * Rollback the create fs operations
     * 
     * @param systemURI
     *            URI of storage controller.
     * @param fileURIs
     *            List of fs to be rollbacked
     * @throws InternalException
     *             Storage controller exceptions.
     */
    public void rollBackCreateFileShares(URI systemURI, List<URI> fileURIs, String opId) throws InternalException;

    /**
     * Assign a filesystem snapshot policy to a fs
     * 
     * @param storage
     *            URI of storage controller.
     * @param fs
     *            URI of associated filesystem or snapshot
     * @param policy
     *            URI of the policy to be attached
     * @throws InternalException
     *             Storage controller exceptions.
     */
    public void assignFileSystemSnapshotPolicy(URI storage, URI fs, URI policy, String opId) throws InternalException;

    /**
     * Unassign a filesystem snapshot policy
     * 
     * @param storage
     *            URI of storage controller.
     * @param fs
     *            URI of associated filesystem
     * @param policy
     *            URI of the policy to be detached
     * @throws InternalException
     *             Storage controller exceptions.
     */
    public void unassignFileSystemSnapshotPolicy(URI storage, URI fs, URI policy, String opId) throws InternalException;

    /**
     * List snapshots associated with a filesystem snapshot policy
     * 
     * @param storage
     *            URI of storage controller.
     * @param fs
     *            URI of associated filesystem
     * @param policy
     *            URI of the policy
     * @throws InternalException
     *             Storage controller exceptions.
     */
    public void listSanpshotByPolicy(URI storage, URI fs, URI policy, String opId) throws InternalException;
}
