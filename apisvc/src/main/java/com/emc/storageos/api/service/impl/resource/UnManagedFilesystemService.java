/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.FileMapper.map;

import java.net.URI;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.emc.storageos.db.client.model.CifsShareACL;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedCifsShareACL;
import com.emc.storageos.model.BulkIdParam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.mapper.functions.MapUnmanagedFileSystem;
import com.emc.storageos.api.service.impl.resource.utils.CapacityUtils;
import com.emc.storageos.api.service.impl.resource.utils.FileSystemIngestionUtil;
import com.emc.storageos.api.service.impl.resource.utils.PropertySetterUtil;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.FileExportRule;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageProtocol;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualNAS;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileExportRule;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem.SupportedFileSystemInformation;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.file.FileSystemIngest;
import com.emc.storageos.model.file.NamedFileSystemList;
import com.emc.storageos.model.file.UnManagedFileBulkRep;
import com.emc.storageos.model.file.UnManagedFileSystemRestRep;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.FileControllerConstants;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.AuditBlockUtil;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableBourneEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.RecordType;

@Path("/vdc/unmanaged/filesystems")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class UnManagedFilesystemService extends TaggedResource {
    private static final String EVENT_SERVICE_TYPE = "file";
    private static final String EVENT_SERVICE_SOURCE = "UnManagedFilesystemService";
    private static final String QUOTA = "quota";
    /**
     * Reference to logger
     */
    private static final Logger _logger = LoggerFactory
            .getLogger(UnManagedFilesystemService.class);

    @Override
    protected DataObject queryResource(URI id) {
        ArgValidator.checkUri(id);
        UnManagedFileSystem unManagedFileSystem = _dbClient.queryObject(
                UnManagedFileSystem.class, id);
        ArgValidator.checkEntityNotNull(unManagedFileSystem, id, isIdEmbeddedInURL(id));
        return unManagedFileSystem;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    /**
     * 
     * Show the details of an unmanaged file system.
     * 
     * @param id the URN of a ViPR unmanaged file system
     * @prereq none
     * @brief Show unmanaged file system
     * @return UnManagedFileSystemRestRep
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public UnManagedFileSystemRestRep getUnManagedFileSystemInfo(
            @PathParam("id") URI id) {
        UnManagedFileSystem unManagedFileSystem = _dbClient.queryObject(
                UnManagedFileSystem.class, id);
        ArgValidator.checkEntityNotNull(unManagedFileSystem, id, isIdEmbeddedInURL(id));
        return map(unManagedFileSystem);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<UnManagedFileSystem> getResourceClass() {
        return UnManagedFileSystem.class;
    }

    /**
     * List data of specified unmanaged file systems.
     * 
     * @param param POST data containing the id list.
     * @prereq none
     * @brief List data of unmanaged file systems.
     * @return list of representations.
     * 
     * @throws DatabaseException When an error occurs querying the database.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public UnManagedFileBulkRep getBulkResources(BulkIdParam param) {
        return (UnManagedFileBulkRep) super.getBulkResources(param);
    }

    @Override
    public UnManagedFileBulkRep queryBulkResourceReps(List<URI> ids) {
        Iterator<UnManagedFileSystem> _dbIterator = _dbClient
                .queryIterativeObjects(UnManagedFileSystem.class, ids);
        return new UnManagedFileBulkRep(BulkList.wrapping(_dbIterator,
                MapUnmanagedFileSystem.getInstance()));
    }

    @Override
    public UnManagedFileBulkRep queryFilteredBulkResourceReps(List<URI> ids) {
        verifySystemAdmin();
        return queryBulkResourceReps(ids);
    }

    /**
     * 
     * UnManaged file systems are file systems, which are present within ViPR
     * storage systems,but have not been ingested by ViPR which moves the unmanaged file systems under ViPR management.
     * 
     * File system ingest provides flexibility in bringing unmanaged
     * file systems under ViPR management.
     * An unmanaged file system must be associated with a virtual pool, project,
     * and virtual array before it can be managed by ViPR.
     * List of supported virtual pools for each unmanaged file system is exposed using /vdc/unmanaged/filesystems/bulk.
     * Using an unsupported virtual pool results in an error
     * 
     * Size of unmanaged file systems which can be ingested via a single API Call
     * is limited to 4000.
     * 
     * @param param parameters required for unmanaged filesystem ingestion
     * 
     * @prereq none
     * @brief Ingest unmanaged file systems
     * @throws InternalException
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/ingest")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public NamedFileSystemList ingestFileSystems(FileSystemIngest param) throws InternalException {

        if ((null == param.getUnManagedFileSystems())
                || (param.getUnManagedFileSystems().toString().length() == 0)
                || (param.getUnManagedFileSystems().isEmpty())
                || (param.getUnManagedFileSystems().get(0).toString().isEmpty())) {
            throw APIException.badRequests
                    .invalidParameterUnManagedFsListEmpty();
        }

        if (null == param.getProject() || (param.getProject().toString().length() == 0)) {
            throw APIException.badRequests.invalidParameterProjectEmpty();
        }

        if (null == param.getVarray() || (param.getVarray().toString().length() == 0)) {
            throw APIException.badRequests.invalidParameterVirtualArrayEmpty();
        }

        if (null == param.getVpool() || (param.getVpool().toString().length() == 0)) {
            throw APIException.badRequests.invalidParameterVirtualPoolEmpty();
        }

        if (param.getUnManagedFileSystems().size() > getMaxBulkSize()) {
            throw APIException.badRequests.exceedingLimit("unmanaged filesystems", getMaxBulkSize());
        }

        _logger.info("Ingest called with Virtual Array {}", param.getVarray());
        _logger.info("Ingest called with Virtual Pool {}", param.getVpool());
        _logger.info("Ingest called with Project {}", param.getProject());
        _logger.info("Ingest called with UnManagedFileSystems {}", param.getUnManagedFileSystems());

        NamedFileSystemList filesystemList = new NamedFileSystemList();
        List<UnManagedFileSystem> unManagedFileSystems = new ArrayList<UnManagedFileSystem>();
        try {
            // Get and validate the project.
            Project project = _permissionsHelper.getObjectById(param.getProject(), Project.class);
            ArgValidator.checkUri(param.getProject());
            ArgValidator.checkEntity(project, param.getProject(), false);

            VirtualArray neighborhood = FileSystemIngestionUtil
                    .getVirtualArrayForFileSystemCreateRequest(project, param.getVarray(),
                            _permissionsHelper, _dbClient);

            // Get and validate the VirtualPool.
            VirtualPool cos = FileSystemIngestionUtil.getVirtualPoolForFileSystemCreateRequest(
                    project, param.getVpool(), _permissionsHelper, _dbClient);

            if (null != cos.getVirtualArrays() && !cos.getVirtualArrays().isEmpty() &&
                    !cos.getVirtualArrays().contains(param.getVarray().toString())) {
                throw APIException.internalServerErrors.virtualPoolNotMatchingVArray(param.getVarray());
            }

            // check for Quotas
            long unManagedFileSystemsCapacity = FileSystemIngestionUtil.getTotalUnManagedFileSystemCapacity(_dbClient,
                    param.getUnManagedFileSystems());
            _logger.info("Requested UnManagedFile System Capacity {}", unManagedFileSystemsCapacity);

            TenantOrg tenant = _dbClient.queryObject(TenantOrg.class, project.getTenantOrg().getURI());
            CapacityUtils.validateQuotasForProvisioning(_dbClient, cos, project, tenant, unManagedFileSystemsCapacity, "filesystem");

            FileSystemIngestionUtil.isIngestionRequestValidForUnManagedFileSystems(
                    param.getUnManagedFileSystems(), cos, _dbClient);

            List<FileShare> filesystems = new ArrayList<FileShare>();
            List<FileExportRule> fsExportRules = new ArrayList<FileExportRule>();

            List<CifsShareACL> fsCifsShareAcls = new ArrayList<CifsShareACL>();

            List<UnManagedFileExportRule> inActiveUnManagedExportRules = new ArrayList<UnManagedFileExportRule>();
            List<UnManagedCifsShareACL> inActiveUnManagedShareCifs = new ArrayList<UnManagedCifsShareACL>();

            // cifs share acl's
            List<CifsShareACL> cifsShareACLList = new ArrayList<CifsShareACL>();
            List<URI> full_pools = new ArrayList<URI>();
            List<URI> full_systems = new ArrayList<URI>();
            Calendar timeNow = Calendar.getInstance();
            for (URI unManagedFileSystemUri : param.getUnManagedFileSystems()) {
                UnManagedFileSystem unManagedFileSystem = _dbClient.queryObject(
                        UnManagedFileSystem.class, unManagedFileSystemUri);

                if (null == unManagedFileSystem || null == unManagedFileSystem.getFileSystemCharacterstics()
                        || null == unManagedFileSystem.getFileSystemInformation()) {
                    _logger.warn(
                            "UnManaged FileSystem {} partially discovered, hence not enough information available to validate neither virtualPool nor other criterias.Skipping Ingestion..",
                            unManagedFileSystemUri);
                    continue;
                }

                if (unManagedFileSystem.getInactive()) {
                    _logger.warn("UnManaged FileSystem {} is inactive.Skipping Ingestion..", unManagedFileSystemUri);
                    continue;
                }
                StringSetMap unManagedFileSystemInformation = unManagedFileSystem
                        .getFileSystemInformation();
                String fsNativeGuid = unManagedFileSystem.getNativeGuid().replace(
                        FileSystemIngestionUtil.UNMANAGEDFILESYSTEM,
                        FileSystemIngestionUtil.FILESYSTEM);
                String deviceLabel = PropertySetterUtil.extractValueFromStringSet(
                        SupportedFileSystemInformation.DEVICE_LABEL.toString(),
                        unManagedFileSystemInformation);
                String fsName = PropertySetterUtil.extractValueFromStringSet(
                        SupportedFileSystemInformation.NAME.toString(),
                        unManagedFileSystemInformation);
                URI storagePoolUri = unManagedFileSystem.getStoragePoolUri();
                String storagePortUri = PropertySetterUtil.extractValueFromStringSet(
                        SupportedFileSystemInformation.STORAGE_PORT.toString(),
                        unManagedFileSystemInformation);
                String capacity = PropertySetterUtil.extractValueFromStringSet(
                        SupportedFileSystemInformation.PROVISIONED_CAPACITY.toString(),
                        unManagedFileSystemInformation);
                String usedCapacity = PropertySetterUtil.extractValueFromStringSet(
                        SupportedFileSystemInformation.ALLOCATED_CAPACITY.toString(),
                        unManagedFileSystemInformation);
                String nasUri = PropertySetterUtil.extractValueFromStringSet(
                        SupportedFileSystemInformation.NAS.toString(),
                        unManagedFileSystemInformation);

                String path = PropertySetterUtil.extractValueFromStringSet(
                        SupportedFileSystemInformation.PATH.toString(),
                        unManagedFileSystemInformation);

                String mountPath = PropertySetterUtil.extractValueFromStringSet(
                        SupportedFileSystemInformation.MOUNT_PATH.toString(),
                        unManagedFileSystemInformation);

                Long lcapcity = Long.valueOf(capacity);
                Long lusedCapacity = Long.valueOf(usedCapacity);
                // pool uri cannot be null
                StoragePool pool = _dbClient.queryObject(StoragePool.class, storagePoolUri);
                
                StoragePort port = null;
                if (storagePortUri != null ) {
                    port = _dbClient.queryObject(StoragePort.class, URI.create(storagePortUri));
                }
                
                StorageHADomain dataMover = null;
                if (port != null && port.getStorageHADomain() != null) {
                    dataMover = _dbClient.queryObject(StorageHADomain.class, port.getStorageHADomain());
                }
                if (dataMover != null) {
                    _logger.info("Data Mover to Use {} {} {}",
                            new Object[] { dataMover.getAdapterName(), dataMover.getName(), dataMover.getLabel() });
                }
                
                //check ingest is valid for given project
                
                if(!isIngestUmfsValidForProject(project, _dbClient, nasUri)) {
                    _logger.info("ingest umfs {} is mounted to vNAS Uri {} invalid for project", path, nasUri);
                    continue;
                }
                
                // Check to see if UMFS's storagepool's Tagged neighborhood has the "passed in" neighborhood.
                // if not don't ingest
                if (null != pool) {
                    StringSet taggedVirtualArrays = pool
                            .getTaggedVirtualArrays();
                    if ((null == taggedVirtualArrays) || (!taggedVirtualArrays.contains(neighborhood.getId()
                            .toString()))) {
                        _logger.warn(
                                "UnManaged FileSystem {} storagepool doesn't related to the Virtual Array {}. Skipping Ingestion..",
                                unManagedFileSystemUri, neighborhood.getId()
                                        .toString());
                        continue;
                    }
                } else {
                    _logger.warn(
                            "UnManaged FileSystem {} doesn't contain a storagepool. Skipping Ingestiong",
                            unManagedFileSystemUri);
                    continue;
                }

                if (full_pools.contains(storagePoolUri)) {
                    // skip this fileshare
                    continue;
                }
                if (pool.getIsResourceLimitSet()) {
                    if (pool.getMaxResources() <= StoragePoolService.getNumResources(pool, _dbClient)) {
                        // reached limit for this pool
                        full_pools.add(storagePoolUri);
                        continue;
                    }
                }

                FileShare filesystem = new FileShare();
                filesystem.setId(URIUtil.createId(FileShare.class));
                filesystem.setNativeGuid(fsNativeGuid);
                filesystem.setCapacity(lcapcity);
                filesystem.setUsedCapacity(lusedCapacity);
                filesystem.setPath(path);
                filesystem.setMountPath(mountPath);
                filesystem.setVirtualPool(param.getVpool());
                filesystem.setVirtualArray(param.getVarray());
                if(nasUri != null) {
                	filesystem.setVirtualNAS(URI.create(nasUri));
                }

                URI storageSystemUri = unManagedFileSystem.getStorageSystemUri();
                StorageSystem system = _dbClient.queryObject(StorageSystem.class, storageSystemUri);
                if (full_systems.contains(storageSystemUri)) {
                    // skip this fileshare
                    continue;
                }
                if (system.getIsResourceLimitSet()) {
                    if (system.getMaxResources() <= StorageSystemService.getNumResources(system, _dbClient)) {
                        // reached limit for this system
                        full_systems.add(storageSystemUri);
                        continue;
                    }
                }

                filesystem.setStorageDevice(storageSystemUri);
                filesystem.setCreationTime(timeNow);
                filesystem.setPool(storagePoolUri);
                filesystem.setProtocol(new StringSet());
                StringSet fsSupportedProtocols = new StringSet();
                for (StorageProtocol.File fileProtocol : StorageProtocol.File.values()) {
                    fsSupportedProtocols.add(fileProtocol.name());
                }

                fsSupportedProtocols.retainAll(pool.getProtocols());
                filesystem.getProtocol().addAll(fsSupportedProtocols);
                filesystem.setLabel(null == deviceLabel ? "" : deviceLabel);
                filesystem.setName(null == fsName ? "" : fsName);
                filesystem.setTenant(new NamedURI(project.getTenantOrg().getURI(), filesystem.getLabel()));
                filesystem.setProject(new NamedURI(param.getProject(), filesystem.getLabel()));

                _logger.info("Un Managed File System {} has exports? : {}", unManagedFileSystem.getId(),
                        unManagedFileSystem.getHasExports());
                
                StoragePort sPort = null;
                if (port != null && neighborhood != null) {
                    sPort = compareAndSelectPortURIForUMFS(system, port, neighborhood);
                }
                if (unManagedFileSystem.getHasExports()) {
                    _logger.info("Storage Port Found {}", sPort);
                    if (null != sPort) {
                        filesystem.setPortName(sPort.getPortName());
                        filesystem.setStoragePort(sPort.getId());
                        filesystem.setFsExports(PropertySetterUtil.convertUnManagedExportMapToManaged(
                                unManagedFileSystem.getFsUnManagedExportMap(), sPort.getPortName(), dataMover));

                        _logger.info("Export map for {} = {}", fsName, filesystem.getFsExports());

                        // Process Exports
                        // Step 1 : Query them and Retrive associated Exports
                        List<UnManagedFileExportRule> exports = queryDBFSExports(unManagedFileSystem);
                        _logger.info("Number of Exports Found : {} for UnManaged Fs path : {}", exports.size(),
                                unManagedFileSystem.getMountPath());

                        if (exports != null && !exports.isEmpty()) {
                            for (UnManagedFileExportRule rule : exports) {
                                // Step 2 : Convert them to File Export Rule
                                // Step 3 : Keep them as a list to store in db, down the line at a shot
                                rule.setFileSystemId(filesystem.getId()); // Important to relate the exports to a FileSystem.
                                createRule(rule, fsExportRules);
                                // Step 4: Update the UnManaged Exports : Set Inactive as true
                                rule.setInactive(true);
                                // Step 5 : Keep this list as updated.
                                inActiveUnManagedExportRules.add(rule);
                            }
                        }

                    } else {
                        _logger.warn(
                                "UnManaged FileSystem {}'s storageport doesn't belong to a matching NetWork. Skipping Ingestion",
                                unManagedFileSystemUri);
                        continue;
                    }

                }

                if (unManagedFileSystem.getHasShares()) {
                    _logger.info("Storage Port Found {}", sPort);
                    if (null != sPort) {
                        filesystem.setPortName(sPort.getPortName());
                        filesystem.setStoragePort(sPort.getId());
                        filesystem.setSMBFileShares(PropertySetterUtil.convertUnManagedSMBMapToManaged(
                                unManagedFileSystem.getUnManagedSmbShareMap(), sPort, dataMover));

                        _logger.info("Share map for {} = {}", fsName, filesystem.getSMBFileShares());

                        // Process Exports
                        // Step 1 : Query them and Retrive associated Exports
                        List<UnManagedCifsShareACL> cifsACLs = queryDBCifsShares(unManagedFileSystem);
                        _logger.info("Number of Cifs ACL Found : {} for UnManaged Fs path : {}", cifsACLs.size(),
                                unManagedFileSystem.getMountPath());

                        if (cifsACLs != null && !cifsACLs.isEmpty()) {
                            for (UnManagedCifsShareACL umCifsAcl : cifsACLs) {
                                // Step 2 : Convert them to Cifs Share ACL
                                // Step 3 : Keep them as a list to store in db, down the line at a shot
                                umCifsAcl.setFileSystemId(filesystem.getId()); // Important to relate the shares to a FileSystem.
                                createACL(umCifsAcl, fsCifsShareAcls, filesystem);
                                // Step 4: Update the UnManaged Share ACL : Set Inactive as true
                                umCifsAcl.setInactive(true);
                                // Step 5 : Keep this list as updated.
                                inActiveUnManagedShareCifs.add(umCifsAcl);
                            }
                        }
                    }
                }

                if (!unManagedFileSystem.getHasShares() && !unManagedFileSystem.getHasExports()) {
                    if (null != sPort) {
                        _logger.info("Storage Port Found {}", sPort);
                        filesystem.setPortName(sPort.getPortName());
                        filesystem.setStoragePort(sPort.getId());
                    } else {
                        filesystem.setStoragePort(null);
                        _logger.info("Storage Port not found for fs {}", fsName);
                    }
                }

                // Set quota
                if (null != unManagedFileSystem.getExtensions() &&
                        null != unManagedFileSystem.getExtensions().get(QUOTA)) {
                    if (null == filesystem.getExtensions()) {
                        filesystem.setExtensions(new StringMap());
                    }
                    filesystem.getExtensions().put(QUOTA, unManagedFileSystem.getExtensions().get(QUOTA));
                }

                filesystems.add(PropertySetterUtil.addFileSystemDetails(
                        unManagedFileSystemInformation, filesystem));

                // Process Export Rules for the validated FS.

                filesystemList.getFilesystems().add(toNamedRelatedResource(ResourceTypeEnum.FILE,
                        filesystem.getId(), filesystem.getNativeGuid()));
                unManagedFileSystem.setInactive(true);
                unManagedFileSystems.add(unManagedFileSystem);
            }

            int i = 0;
            // Test
            for (FileShare fs : filesystems) {
                ++i;
                _logger.info("{} --> Saving FS to DB {}", i, fs);
                _logger.info(" --> Fs  Storage Pool {} and Virtual Pool {}", fs.getPool(), fs.getVirtualPool());
            }
            _dbClient.createObject(filesystems);

            i = 0;
            // Test
            for (FileExportRule rule : fsExportRules) {
                ++i;
                _logger.info("{} --> Saving Export rule to DB {}", i, rule);
            }
            // Step 6.1 : Update the same in DB & Add new export rules
            _dbClient.createObject(fsExportRules);

            // Step 6.2 : Update Cifs Acls in DB & Add new ACLs
            i = 0;
            for (CifsShareACL acl : fsCifsShareAcls) {
                ++i;
                _logger.info("{} --> Saving New Cifs ACL to DB {}", i, acl);
            }

            if (fsCifsShareAcls != null && !fsCifsShareAcls.isEmpty()) {
                _dbClient.createObject(fsCifsShareAcls);
            }

            // Step 7.1 : Update the same in DB & clean ingested UnManagedCifsACLs
            i = 0;
            for (UnManagedCifsShareACL acl : inActiveUnManagedShareCifs) {
                ++i;
                _logger.info("{} Updating UnManagedACL DB as InActive TRUE {}", acl);
            }
            _dbClient.persistObject(inActiveUnManagedShareCifs);
            // Step 7.2 : Update the same in DB & clean Unmanaged ExportRule
            i = 0;
            for (UnManagedFileExportRule rule : inActiveUnManagedExportRules) {
                ++i;
                _logger.info("{} Updating DB as InActive TRUE {}", rule);
            }
            _dbClient.persistObject(inActiveUnManagedExportRules);

            _dbClient.persistObject(unManagedFileSystems);

            // record the events after they have been created
            for (FileShare filesystem : filesystems) {
                recordFileSystemOperation(_dbClient,
                        OperationTypeEnum.CREATE_FILE_SYSTEM, Status.ready,
                        filesystem.getId());
            }

        } catch (InternalException e) {
            throw e;
        } catch (Exception e) {
            _logger.error("Unexpected exception:", e);
            throw APIException.internalServerErrors.genericApisvcError(e.getMessage(), e);
        }
        return filesystemList;
    }

    private void createRule(UnManagedFileExportRule orig, List<FileExportRule> fsExportRules) {
        FileExportRule dest = new FileExportRule();
        dest.setId(URIUtil.createId(FileExportRule.class));
        dest.setFileSystemId(orig.getFileSystemId());
        dest.setExportPath(orig.getExportPath());
        dest.setSecFlavor(orig.getSecFlavor());
        dest.setAnon(orig.getAnon());
        dest.setMountPoint(orig.getMountPoint());
        dest.setDeviceExportId(orig.getDeviceExportId());

        if (orig.getReadOnlyHosts() != null && !orig.getReadOnlyHosts().isEmpty()) {
            dest.setReadOnlyHosts(new StringSet(orig.getReadOnlyHosts()));
        }

        if (orig.getReadWriteHosts() != null && !orig.getReadWriteHosts().isEmpty()) {
            dest.setReadWriteHosts(new StringSet(orig.getReadWriteHosts()));
        }

        if (orig.getRootHosts() != null && !orig.getRootHosts().isEmpty()) {
            dest.setRootHosts(new StringSet(orig.getRootHosts()));
        }

        _logger.info("Ingesting Export Rule : {}", dest);
        fsExportRules.add(dest);
    }

    private List<UnManagedFileExportRule> queryDBFSExports(UnManagedFileSystem fs)
    {
        _logger.info("Querying all ExportRules Using FsId {}", fs.getId());
        try {
            ContainmentConstraint containmentConstraint = ContainmentConstraint.Factory.getUnManagedFileExportRulesConstraint(fs.getId());
            List<UnManagedFileExportRule> fileExportRules = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient,
                    UnManagedFileExportRule.class,
                    containmentConstraint);
            return fileExportRules;
        } catch (Exception e) {
            _logger.error("Error while querying {}", e);
        }

        return new ArrayList<UnManagedFileExportRule>();
    }

    private List<UnManagedCifsShareACL> queryDBCifsShares(UnManagedFileSystem fs)
    {
        _logger.info("Querying All Cifs Share ACLs Using FsId {}", fs.getId());
        try {
            ContainmentConstraint containmentConstraint = ContainmentConstraint.Factory.getUnManagedCifsShareAclsConstraint(fs.getId());
            List<UnManagedCifsShareACL> cifsShareACLList = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient,
                    UnManagedCifsShareACL.class, containmentConstraint);
            return cifsShareACLList;
        } catch (Exception e) {
            _logger.error("Error while querying {}", e);
        }

        return new ArrayList<UnManagedCifsShareACL>();
    }

    /**
     * copy unmanaged cifs share into new cifs share acls
     * 
     * @param origACLList
     * @param shareACLList
     * @param fileshare
     */

    private void copyACLs(List<UnManagedCifsShareACL> origACLList, List<CifsShareACL> shareACLList, FileShare fileshare)
    {
        CifsShareACL shareACL = null;
        for (UnManagedCifsShareACL origACL : origACLList) {

            shareACL = new CifsShareACL();
            // user, permission, permission type
            shareACL.setId(URIUtil.createId(CifsShareACL.class));
            shareACL.setUser(origACL.getUser());

            shareACL.setPermission(origACL.getPermission());
            // share name
            shareACL.setShareName(origACL.getShareName());
            // file system id
            shareACL.setFileSystemId(fileshare.getId());

            // Add new acl into ACL list
            shareACLList.add(shareACL);
            _logger.info("share ACLs details {}", shareACL.toString());
        }
    }
    
    /**
     * Validate vnas of unmanaged file system association with project       
     * @param project
     * @param dbClient
     * @param nasUri
     * @return
     */
    public boolean isIngestUmfsValidForProject(Project project, DbClient dbClient, String nasUri) {
        boolean isIngestValid = true;
        //step -1 check file system is mounted to VNAS
        if( nasUri != null && URIUtil.getTypeName(nasUri).equals("VirtualNAS")) {
            VirtualNAS virtualNAS = dbClient.queryObject(VirtualNAS.class, URI.create(nasUri));
            
            //step -2 if project has any associated vNAS
            StringSet vnasStringSet = project.getAssignedVNasServers();
            if(vnasStringSet != null && !vnasStringSet.isEmpty()) {
                //step -3 then check nasUri in project associated vnas list
                if(vnasStringSet.contains(nasUri) == false && virtualNAS.isNotAssignedToProject() == false) {
                    _logger.info("vNAS {} is assicated with other project", nasUri);
                    isIngestValid = false;
                }
            } else {//then check vnas associated with any other project
                //step -3 //if is associated with other project then don't ingest
                if(virtualNAS.isNotAssignedToProject() == false) {
                    _logger.info("vNAS {} is assicated with other project", nasUri);
                    isIngestValid = false;
                }
            }
        }
        return isIngestValid;
    }


    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.UNMANAGED_FILESYSTEMS;
    }

    /**
     * Record filesystem related event and audit
     * 
     * @param dbClient db client
     * @param opType operation type
     * @param status operation status
     * @param evDesc event description
     * @param extParam parameters array from which we could generate detail
     *            audit message
     */
    public void recordFileSystemOperation(DbClient dbClient, OperationTypeEnum opType,
            Operation.Status status, Object... extParam) {
        try {
            boolean opStatus = (Operation.Status.ready == status) ? true : false;
            String evType;
            evType = opType.getEvType(opStatus);
            String evDesc = opType.getDescription();
            String opStage = AuditLogManager.AUDITOP_END;
            _logger.info("opType: {} detail: {}", opType.toString(), evType.toString() + ':' + evDesc);

            URI uri = (URI) extParam[0];
            recordBourneFileSystemEvent(dbClient, evType, status, evDesc, uri);

            String id = uri.toString();
            AuditBlockUtil.auditBlock(dbClient, opType, opStatus, opStage, id);

        } catch (Exception e) {
            _logger.error("Failed to record filesystem operation {}, err:", opType.toString(), e);
        }
    }

    /**
     * copy unmanaged cifs share into new cifs share acls
     * 
     * @param origACL
     * @param shareACLList
     * @param fileshare
     */

    private void createACL(UnManagedCifsShareACL origACL, List<CifsShareACL> shareACLList, FileShare fileshare) {
        CifsShareACL shareACL = null;

        shareACL = new CifsShareACL();
        // user, permission, permission type
        shareACL.setId(URIUtil.createId(CifsShareACL.class));

        String user = origACL.getUser();
        if (user != null) {
            shareACL.setUser(user);
        } else {
            shareACL.setGroup(origACL.getGroup());
        }

        String permissionText = null;
        switch (origACL.getPermission().toLowerCase()) {
            case "read":
                permissionText = FileControllerConstants.CIFS_SHARE_PERMISSION_READ;
                break;
            case "change":
                permissionText = FileControllerConstants.CIFS_SHARE_PERMISSION_CHANGE;
                break;
            case "full":
            case "fullcontrol":
                permissionText = FileControllerConstants.CIFS_SHARE_PERMISSION_FULLCONTROL;
                break;
        }
        shareACL.setPermission(permissionText);
        // share name
        shareACL.setShareName(origACL.getShareName());
        // file system id
        shareACL.setFileSystemId(fileshare.getId());

        // Add new acl into ACL list
        shareACLList.add(shareACL);
        _logger.info("share ACLs details {}", shareACL.toString());

    }

    /**
     * Generate and Record a Bourne filesystem specific event
     * 
     * @param dbClient
     * @param evtType
     * @param status
     * @param desc
     * @throws Exception
     */
    public void recordBourneFileSystemEvent(DbClient dbClient,
            String evtType, Operation.Status status, String desc, URI id)
            throws Exception {

        RecordableEventManager eventManager = new RecordableEventManager();
        eventManager.setDbClient(dbClient);

        FileShare fileShareObj = dbClient.queryObject(FileShare.class, id);
        RecordableBourneEvent event = ControllerUtils
                .convertToRecordableBourneEvent(fileShareObj, evtType,
                        desc, "", dbClient,
                        EVENT_SERVICE_TYPE,
                        RecordType.Event.name(),
                        EVENT_SERVICE_SOURCE);

        try {
            eventManager.recordEvents(event);
            _logger.info("Bourne {} event recorded", evtType);
        } catch (Exception ex) {
            _logger.error(
                    "Failed to record event. Event description: {}. Error:",
                    evtType, ex);
        }
    }

    /**
     * Check to see if StoragePort has a StorageHADomain associated (for example in vnxfile case), if does check all the ports that belong
     * to the
     * StorageDomain and verify to see if any of the ports belong to Neighborhood. If there is a match, associate that port to Filesystem's
     * StoragePort feild.
     * If StorageHADomain doesn't exists for port (in case of netapp and isilon). Get all the ports for storagesystem and select a random
     * storageport to be
     * able to associate one of the FileSystem's StoragePort.
     * 
     * 
     * @param system
     * @param currentUMFSPort
     * @param vArray
     * @return Matching StoragePort.
     */
    private StoragePort compareAndSelectPortURIForUMFS(StorageSystem system,
            StoragePort currentUMFSPort, VirtualArray vArray) {
        StoragePort sPort = null;
        URI adapterNativeGuid = currentUMFSPort.getStorageHADomain();
        List<URI> storagePortsForVArray = returnAllPortsInVArray(vArray
                .getId());
        List<URI> matchedPorts = new ArrayList<URI>();
        /*
         * If There adapterNativeId is not null we know that storageHADomain exists
         * and we get list of storageports to see if anyone matches
         */
        if (null != adapterNativeGuid) {
            List<URI> fellowPortURIs = returnAllFellowPortsInHADomain(adapterNativeGuid);
            for (URI fPortUri : fellowPortURIs) {
                // Can't use .contains for URIQueryResultList since it's not implement hence this
                // for loop.
                for (URI vaPortUri : storagePortsForVArray) {
                    if (fPortUri.toString().equals(vaPortUri.toString())) {
                        URI localURI = fPortUri;
                        matchedPorts.add(localURI);
                    }
                }
            }
        } else {
            matchedPorts = returnAllPortsforStgArrayAndVArray(system, storagePortsForVArray);
        }

        if (matchedPorts != null && !matchedPorts.isEmpty()) {
            // Shuffle Storageports and return the first one.
            Collections.shuffle(matchedPorts);
            sPort = _dbClient.queryObject(StoragePort.class, matchedPorts.get(0));
        }

        return sPort;
    }

    /**
     * @param hdDomain
     * @return List of StoragePorts for specific StorageHADomain
     */
    private URIQueryResultList returnAllFellowPortsInHADomain(URI hdDomain) {
        URIQueryResultList haDomainPortURIs = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getStorageHADomainStoragePortConstraint(hdDomain),
                haDomainPortURIs);
        return haDomainPortURIs;
    }

    /**
     * @param vArray
     * @return List of StoragePort URIs for a VirtualArray.
     */
    private List<URI> returnAllPortsInVArray(URI vArray) {
        URIQueryResultList vArrayPortURIs = new URIQueryResultList();
        List<URI> sPorts = new ArrayList<URI>();

        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getVirtualArrayStoragePortsConstraint(vArray.toString()),
                vArrayPortURIs);

        Iterator<URI> sPortIterator = vArrayPortURIs.iterator();
        while (sPortIterator.hasNext()) {
            sPorts.add(sPortIterator.next());
        }

        return sPorts;
    }

    /**
     * @param system
     * @return List of StoragePorts for a StorageSystems in a Virtual Array.
     */
    private List<URI> returnAllPortsforStgArrayAndVArray(StorageSystem system,
            List<URI> storagePortsForVArray) {
        List<URI> sPorts = new ArrayList<URI>();
        URIQueryResultList storagePortURIs = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getStorageDeviceStoragePortConstraint(system.getId()),
                storagePortURIs);
        Iterator<URI> storagePortIter = storagePortURIs.iterator();
        while (storagePortIter.hasNext()) {
            StoragePort port = _dbClient.queryObject(StoragePort.class,
                    storagePortIter.next());
            for (URI spVArray : storagePortsForVArray) {
                if (port.getId().toString().equals(spVArray.toString())) {
                    sPorts.add(port.getId());
                }
            }
        }
        return sPorts;
    }
}
