package com.emc.storageos.api.service.impl.resource;

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.FilePolicy;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.fileorchestrationcontroller.FileStorageSystemAssociation;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.file.CifsShareACLUpdateParams;
import com.emc.storageos.model.file.FileExportUpdateParams;
import com.emc.storageos.model.file.FileSystemParam;
import com.emc.storageos.model.file.policy.FilePolicyUpdateParam;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.FileSMBShare;
import com.emc.storageos.volumecontroller.FileShareExport;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

public interface FileServiceApi {
    public static final String DEFAULT = "default";

    public static final String CONTROLLER_SVC = "controllersvc";
    public static final String CONTROLLER_SVC_VER = "1";
    public static final String EVENT_SERVICE_TYPE = "file";

    /**
     * Define the default FileServiceApi implementation.
     */

    /**
     * Create filesystems
     * 
     * @param param
     *            -The filesystem creation post parameter
     * @param project
     *            -project requested
     * @param varray
     *            -source VirtualArray
     * @param vpool
     *            -VirtualPool requested
     * @param recommendations
     *            -Placement recommendation object
     * @param taskList
     *            -list of tasks for source filesystems
     * @param task
     *            -task ID
     * @param vpoolCapabilities
     *            -wrapper for vpool params
     * @return TaskList
     * 
     * @throws InternalException
     */
    public TaskList createFileSystems(FileSystemParam param, Project project,
            VirtualArray varray, VirtualPool vpool, TenantOrg tenantOrg,
            DataObject.Flag[] flags, List<Recommendation> recommendations,
            TaskList taskList, String task, VirtualPoolCapabilityValuesWrapper vpoolCapabilities)
                    throws InternalException;

    /**
     * Delete the passed filesystems for the passed system.
     * 
     * @param systemURI
     *            -URI of the system owing the filesystems.
     * @param fileSystemURIs-
     *            The URIs of the filesystems to be deleted.
     * @param deletionType
     *            -The type of deletion to perform.
     * @param
     * @param task
     *            -The task identifier.
     * 
     * @throws InternalException
     */
    public void deleteFileSystems(URI systemURI, List<URI> fileSystemURIs, String deletionType,
            boolean forceDelete, boolean deleteOnlyMirrors, String task) throws InternalException;

    /**
     * Check if a resource can be deactivated safely.
     * 
     * @return detail type of the dependency if exist, null otherwise
     * 
     * @throws InternalException
     */
    public <T extends DataObject> String checkForDelete(T object) throws InternalException;

    /**
     * Expand the capacity of size of given size
     *
     * @param fileshare
     * @param newSize
     * @param taskId
     * @throws InternalException
     */
    public void expandFileShare(FileShare fileshare, Long newSize, String taskId)
            throws InternalException;

    /**
     * Reduction of file system quota, supported only on Isilon
     *
     * @param fileshare
     * @param newSize
     * @param taskId
     * @throws InternalException
     */
    public void  reduceFileShareQuota(FileShare fileshare, Long newSize, String taskId)
            throws InternalException;
    /**
     * Create Continuous Copies for existing source file system
     * 
     * @param fs
     *            -source file system for which mirror file system to be created
     * @param project
     *            -project requested
     * @param varray
     *            -source VirtualArray
     * @param vpool
     *            -VirtualPool requested
     * @param recommendations
     *            -Placement recommendation object
     * @param taskList
     *            -list of tasks for source filesystems
     * @param task
     *            -task ID
     * @param vpoolCapabilities
     *            -wrapper for vpool params
     * @return TaskList
     * 
     * @throws InternalException
     */
    public TaskResourceRep createTargetsForExistingSource(FileShare fs, Project project,
            VirtualPool vpool, VirtualArray varray, TaskList taskList, String task, List<Recommendation> recommendations,
            VirtualPoolCapabilityValuesWrapper vpoolCapabilities)
                    throws InternalException;

    /**
     * Create CIFS share for the FileSystem
     * 
     * @param storageSystem
     * @param fileSystem
     * @param smbShare
     * @param task
     * @throws InternalException
     */
    void share(URI storageSystem, URI fileSystem, FileSMBShare smbShare, String task)
            throws InternalException;

    /**
     * Create NFS Exports for the FileSystem
     * 
     * @param storage
     * @param fsURI
     * @param exports
     * @param opId
     * @throws InternalException
     */
    void export(URI storage, URI fsURI, List<FileShareExport> exports, String opId)
            throws InternalException;

    /**
     * Update NFS Exports Rules for the FileSystem
     * 
     * @param storage
     * @param fsURI
     * @param param
     * @param unmountExport
     * @param opId
     * @throws InternalException
     */
    void updateExportRules(URI storage, URI fsURI, FileExportUpdateParams param, boolean unmountExport, String opId)
            throws InternalException;

    /**
     * Update CIFS Share ACLs for the FileSystem
     * 
     * @param storage
     * @param fsURI
     * @param shareName
     * @param param
     * @param opId
     * @throws InternalException
     */
    void updateShareACLs(URI storage, URI fsURI, String shareName, CifsShareACLUpdateParams param, String opId)
            throws InternalException;

    /**
     * Create FileSystem Snapshot
     * 
     * @param storage
     * @param snapshot
     * @param fsURI
     * @param opId
     * @throws InternalException
     */
    void snapshotFS(URI storage, URI snapshot, URI fsURI, String opId)
            throws InternalException;

    /**
     * Delete FileSystem Share
     * 
     * @param storage
     * @param uri
     * @param fileSMBShare
     * @param task
     * @throws InternalException
     */
    void deleteShare(URI storage, URI uri, FileSMBShare fileSMBShare, String task) throws InternalException;

    /**
     * Delete FileSystem Export Rules
     * 
     * @param storage
     * @param uri
     * @param allDirs
     * @param subDirs
     * @param taskId
     * @throws InternalException
     */
    void deleteExportRules(URI storage, URI uri, boolean allDirs, String subDirs, boolean unmountExport, String taskId)
            throws InternalException;

    /**
     * Fail over the File System to target system
     * 
     * @param fsURI
     * @param nfsPort
     * @param cifsPort
     * @param replicateConfiguration
     * @param taskId
     * @throws InternalException
     */
    public void failoverFileShare(URI fsURI, StoragePort nfsPort, StoragePort cifsPort, boolean replicateConfiguration, String taskId)
            throws InternalException;

    /**
     * Fail Back to source File System.
     * 
     * @param fsURI
     * @param nfsPort
     * @param cifsPort
     * @param replicateConfiguration
     * @param taskId
     * @throws InternalException
     */
    public void failbackFileShare(URI fsURI, StoragePort nfsPort, StoragePort cifsPort, boolean replicateConfiguration, String taskId)
            throws InternalException;

    /**
     * Restore File System Snapshot
     * 
     * @param storage
     * @param fs
     * @param snapshot
     * @param opId
     * @throws ControllerException
     */
    void restoreFS(URI storage, URI fs, URI snapshot, String opId)
            throws InternalException;

    /**
     * 
     * @param storage
     * @param pool
     * @param uri
     * @param forceDelete
     * @param deleteType
     * @param opId
     * @throws ControllerException
     */
    void deleteSnapshot(URI storage, URI pool, URI uri, boolean forceDelete, String deleteType, String opId) throws InternalException;

    /**
     * 
     * @param storage
     * @param uri
     * @param shareName
     * @param taskId
     * @throws InternalException
     */
    void deleteShareACLs(URI storage, URI uri, String shareName, String taskId) throws InternalException;

    void assignFilePolicyToVirtualPools(Map<URI, List<URI>> vpoolToStorageSystemMap, URI filePolicyToAssign,
            String taskId);

    void assignFilePolicyToProjects(Map<URI, List<URI>> vpoolToStorageSystemMap, List<URI> projectURIs, URI filePolicyToAssign,
            String taskId);

    void updateFileProtectionPolicy(URI policy, FilePolicyUpdateParam param, String taskId);

    void assignFileReplicationPolicyToVirtualPools(List<FileStorageSystemAssociation> associations,
            List<URI> vpoolURIs, URI filePolicyToAssign, String taskId);

    void assignFileReplicationPolicyToProjects(List<FileStorageSystemAssociation> associations, URI vpoolURI, List<URI> projectURIs,
            URI filePolicyToAssign, String taskId);

    /**
     * 
     * @param fs
     * @param filePolicy
     * @param project
     * @param vpool
     * @param varray
     * @param taskList
     * @param task
     * @param recommendations
     * @param vpoolCapabilities
     * @param targetFs 
     * @return
     * @throws InternalException
     */
    void assignFilePolicyToFileSystem(FileShare fs, FilePolicy filePolicy, Project project,
            VirtualPool vpool, VirtualArray varray, TaskList taskList, String task, List<Recommendation> recommendations,
            VirtualPoolCapabilityValuesWrapper vpoolCapabilities)
            throws InternalException;
    
    /**
     * Adding new call to keep the backward compatibilty.
     * @param fs
     * @param filePolicy
     * @param project
     * @param vpool
     * @param varray
     * @param taskList
     * @param task
     * @param recommendations
     * @param vpoolCapabilities
     * @param targetFs 
     * @return
     * @throws InternalException
     */
    void assignFilePolicyToFileSystem(FileShare fs, FilePolicy filePolicy, Project project,
            VirtualPool vpool, VirtualArray varray, TaskList taskList, String task, List<Recommendation> recommendations,
            VirtualPoolCapabilityValuesWrapper vpoolCapabilities, FileShare targetFs)
            throws InternalException;
}
