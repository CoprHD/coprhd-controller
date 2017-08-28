/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.HostMapper.map;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import com.emc.storageos.api.mapper.HostMapper;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.api.service.impl.response.BulkList.PermissionsEnforcingResourceFilter;
import com.emc.storageos.api.service.impl.response.BulkList.ResourceFilter;
import com.emc.storageos.api.service.impl.response.ProjOwnedResRepFilter;
import com.emc.storageos.api.service.impl.response.ResRepFilter;
import com.emc.storageos.api.service.impl.response.RestLinkFactory;
import com.emc.storageos.api.service.impl.response.SearchedResRepList;
import com.emc.storageos.computesystemcontroller.impl.ComputeSystemHelper;
import com.emc.storageos.customconfigcontroller.CustomConfigConstants;
import com.emc.storageos.customconfigcontroller.impl.CustomConfigHandler;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.ModelClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.ContainmentPrefixConstraint;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.ActionableEvent;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshot.TechnologyType;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DiscoveryStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportGroup.ExportGroupType;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.ExportPathParams;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.ScopedLabel;
import com.emc.storageos.db.client.model.ScopedLabelSet;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StoragePortGroup;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.Task;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.util.EventUtils;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NameGenerator;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.StringMapUtil;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.BulkRestRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.RestLinkRep;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.export.ChangePortGroupParam;
import com.emc.storageos.model.block.export.ExportCreateParam;
import com.emc.storageos.model.block.export.ExportGroupBulkRep;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.storageos.model.block.export.ExportPathParameters;
import com.emc.storageos.model.block.export.ExportPathsAdjustmentParam;
import com.emc.storageos.model.block.export.ExportPathsAdjustmentPreviewParam;
import com.emc.storageos.model.block.export.ExportPathsAdjustmentPreviewRestRep;
import com.emc.storageos.model.block.export.ExportUpdateParam;
import com.emc.storageos.model.block.export.ITLRestRepList;
import com.emc.storageos.model.block.export.InitiatorPathParam;
import com.emc.storageos.model.block.export.InitiatorPortMapRestRep;
import com.emc.storageos.model.block.export.VolumeParam;
import com.emc.storageos.model.search.SearchResultResourceRep;
import com.emc.storageos.model.search.SearchResults;
import com.emc.storageos.protectioncontroller.impl.recoverpoint.RPHelper;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.util.NetworkLite;
import com.emc.storageos.util.NetworkUtil;
import com.emc.storageos.util.VPlexUtil;
import com.emc.storageos.volumecontroller.BlockExportController;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorConfig;
import com.emc.storageos.volumecontroller.placement.BlockStorageScheduler;
import com.emc.storageos.volumecontroller.placement.PlacementException;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

@Path("/block/exports")
@DefaultPermissions(readRoles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, readAcls = { ACL.OWN, ACL.ALL }, writeRoles = {
        Role.TENANT_ADMIN }, writeAcls = { ACL.OWN, ACL.ALL })
public class ExportGroupService extends TaskResourceService {

    private static final String SEARCH_HOST = "host";
    private static final String SEARCH_CLUSTER = "cluster";
    private static final String SEARCH_INITIATOR = "initiator";
    private static final String SEARCH_LEVEL = "self_only";

    static final Logger _log = LoggerFactory.getLogger(ExportGroupService.class);

    private static final String EVENT_SERVICE_TYPE = "export";
    private static final int MAX_VOLUME_COUNT = 100;
    private static final String OLD_INITIATOR_TYPE_NAME = "Exclusive";
    private static final String PATH_ADJUST_REQUIRE_SUSPEND = "controller_pathadjust_require_suspend";

    private static final String STORAGE_SYSTEM_CLUSTER = "-cluster-";

    private static volatile BlockStorageScheduler _blockStorageScheduler;

    // From KnownMachineTypes, which is upstream so re-defining here.
    // Used in ensuring unexport isn't happening to mounted volumes.
    private static String ISA_NAMESPACE = "vipr";
    private static String ISA_SEPARATOR = ":";
    private static String MOUNTPOINT = ISA_NAMESPACE + ISA_SEPARATOR + "mountPoint";
    private static String VMFS_DATASTORE = ISA_NAMESPACE + ISA_SEPARATOR + "vmfsDatastore";
    
    @Autowired
    private CustomConfigHandler customConfigHandler;
    
    public CustomConfigHandler getCustomConfigHandler() {
        return customConfigHandler;
    }

    public void setBlockStorageScheduler(BlockStorageScheduler blockStorageScheduler) {
        if (_blockStorageScheduler == null) {
            _blockStorageScheduler = blockStorageScheduler;
        }
    }

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    private NameGenerator _nameGenerator;

    public NameGenerator getNameGenerator() {
        return _nameGenerator;
    }

    public void setNameGenerator(NameGenerator nameGenerator) {
        _nameGenerator = nameGenerator;
    }

    @Autowired
    private ModelClient modelClient;

    public ModelClient getModelClient() {
        return modelClient;
    }

    public void setModelClient(ModelClient modelClient) {
        this.modelClient = modelClient;
    }

    // Block service implementations
    static volatile private Map<String, ExportGroupServiceApi> _exportGroupServiceApis;

    static public void setExportGroupServiceApis(
            Map<String, ExportGroupServiceApi> serviceInterfaces) {
        _exportGroupServiceApis = serviceInterfaces;
    }

    static public ExportGroupServiceApi getExportGroupServiceImpl(String type) {
        return _exportGroupServiceApis.get(type);
    }

    /**
     * Create block export.
     * <p>
     * Block export method is use to export one or more volumes to one or more hosts. This is a required step for a host to be able to
     * access a block volume, although in some scenarios, additional configurations may be required. There are three main types of export
     * group to meet the common use cases:
     * <ol>
     *
     * <li>Create an initiator type export group so that a single host can see one or more volumes. An example would be an export group for
     * a host boot lun or a private volume that is meant to be used by only one host. The assumption is, in this case the user wants the
     * boot or private volume to be accessed via known initiators. For this type of export, the request object is expected to have only
     * initiators (i.e. no hosts or clusters). Further, the initiators are expected to belong to the same host. While an initiator type
     * export group can belong to only one host, this does not mean the host can only have the initiator type export group. A hosts can be
     * part of many export groups of any type. The export group type {@link ExportGroupType#Initiator} should be specified in the request
     * for this type of export.</li>
     *
     * <li>Create an export group so that one or more hosts, which are not part of a cluster, can access one or more volumes. This is the
     * use case of a shared data lun. In this case, it is assumed that the user wants all the hosts initiators that are connected to the
     * storage array (up to the maximum specified by the virtual pool) to be able to access the volume. The export group type
     * {@link ExportGroupType#Host} should be specified in the request for this type of export.</li>
     *
     * <li>Create an export group so that one or more clusters of hosts can access one or more volumes. This is the same use case of shared
     * data lun as the {@link ExportGroupType#Host} use case with the exception that the user is managing a cluster of hosts as opposed to
     * individual hosts. In this case, the same assumption about the initiators as in the previous case is made. The export group type
     * {@link ExportGroupType#Cluster} should be specified in the request for this type of export.</li>
     * </ol>
     *
     * Note that the above discussion only mentions volumes but mirrors and snapshots can also be used in export groups.
     *
     * <p>
     * Once a block export is created, following incremental changes can be applied to it: - add volume or volume snapshot to the shared
     * storage pool - remove volume or volume snapshot from the shared storage pool - add new server to the cluster by adding initiator from
     * that server to the block export - remove visibility of shared storage to a server by removing initiators from the block export
     *
     * <p>
     * Similar to block storage provisioning, block export is also created within the scope of a varray. Hence, volumes and snapshots being
     * added to a block export must belong to the same varray. Fibre Channel and iSCSI initiators must be part of SANs belonging to the same
     * varray as block export.
     * <p>
     * For Fibre Channel initiators, SAN zones will also be created when the export group is created if the networks are discovered and:
     * <ol>
     * <li>at least one of the Network Systems can provision the Vsan or Fabric in which the each endpoint exists, and</li>
     * <li>the VirtualArray has "auto_san_zoning" set to true.</li>
     * </ol>
     * The SAN zones each consists of an initiator (from the arguments) and a storage port that is selected. The number of zones created
     * will be determined from the number of required initiator/storage-port communication paths.
     * <p>
     * NOTE: This is an asynchronous operation.
     *
     * @param param Export creation parameters
     * @brief Create block export
     * @return Block export details
     * @throws ControllerException
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public TaskResourceRep createExportGroup(ExportCreateParam param) throws ControllerException {

        // Validate count of number of volumes to export
        if (param.getVolumes() != null && param.getVolumes().size() > MAX_VOLUME_COUNT) {
            throw APIException.badRequests.exceedingLimit("count", MAX_VOLUME_COUNT);
        }

        // validate input for the type of export
        validateCreateInputForExportType(param);

        // Validate that the create is not attempting to add VPLEX
        // backend volumes to a group.
        if (param.getVolumes() != null && !param.getVolumes().isEmpty()) {
            List<URI> addVolumeURIs = new ArrayList<URI>();
            for (VolumeParam volParam : param.getVolumes()) {
                addVolumeURIs.add(volParam.getId());
            }
            BlockService.validateNoInternalBlockObjects(_dbClient, addVolumeURIs, false);
            /**
             * Validate ExportGroup add volume's nativeId/nativeGuid
             */
            validateBlockObjectNativeId(addVolumeURIs);
        }

        // Validate the project and check its permissions
        Project project = queryObject(Project.class, param.getProject(), true);
        StorageOSUser user = getUserFromContext();
        if (!(_permissionsHelper.userHasGivenRole(user, project.getTenantOrg().getURI(), Role.TENANT_ADMIN) || _permissionsHelper
                .userHasGivenACL(user, project.getId(), ACL.OWN, ACL.ALL))) {
            throw APIException.forbidden.insufficientPermissionsForUser(user.getName());
        }

        // Validate the varray and check its permissions
        VirtualArray neighborhood = _dbClient.queryObject(VirtualArray.class, param.getVarray());
        _permissionsHelper.checkTenantHasAccessToVirtualArray(project.getTenantOrg().getURI(), neighborhood);

        validateBlockSnapshotsForExportGroupCreate(param);

        // prepare the export group object
        ExportGroup exportGroup = prepareExportGroup(project, param);

        // validate block objects input and package them
        Map<URI, Map<URI, Integer>> storageMap = new HashMap<URI, Map<URI, Integer>>();
        Map<URI, Integer> volumeMap = validateBlockObjectsAndGetMap(param.getVolumes(), exportGroup, storageMap);
        _log.info("Computed storage map: {} volumes in {} storage systems: {}",
                new Object[] { volumeMap.size(), storageMap.size(), storageMap.keySet().toArray() });

        // Validate that there is not already an ExportGroup of the same name, project, and varray.
        // If so, this is like because concurrent operations were in the API at the same time and another created
        // the ExportGroup.
        validateNotSameNameProjectAndVarray(param);

        // If export path policy URI and/or ExportPathParameter block is present, 
        // and volumes are present, validate have permissions.
        // Processing will be in the aysnc. task.
        lookupPathParams(param.getExportPathPolicy());
        ExportPathParameters pathParam = param.getExportPathParameters();
        if (pathParam != null && !volumeMap.keySet().isEmpty()) {
            // Only [RESTRICTED_]SYSTEM_ADMIN may override the Vpool export parameters
            if ((pathParam.getMaxPaths() != null || 
                    pathParam.getMaxPaths() != null ||
                    pathParam.getPathsPerInitiator() != null) &&
                    !_permissionsHelper.userHasGivenRole(user,
                            null, Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN)) {
                throw APIException.forbidden.onlySystemAdminsCanOverrideVpoolPathParameters(exportGroup.getLabel());
            }
        }

        validatePortGroupWhenAddVolumesForExportGroup(volumeMap.keySet(), (pathParam != null ? pathParam.getPortGroup() : null), null);
        
        // COP-14028
        // Changing the return of a TaskList to return immediately while the underlying tasks are
        // being built up. Steps:
        // 1. Create a task object ahead of time and persist it for the export group
        // 2. Fire off a thread that does the scheduling (planning) of the export operation
        // 3. Return to the caller the new Task objects that is in the pending state.

        // create export groups in the array but only when the export
        // group has both block objects and initiators.
        String task = UUID.randomUUID().toString();
        Operation.Status status = storageMap.isEmpty() ? Operation.Status.ready : Operation.Status.pending;

        _dbClient.createObject(exportGroup);

        Operation op = initTaskStatus(exportGroup, task, status, ResourceOperationTypeEnum.CREATE_EXPORT_GROUP);

        // persist the export group to the database
        auditOp(OperationTypeEnum.CREATE_EXPORT_GROUP, true, AuditLogManager.AUDITOP_BEGIN,
                param.getName(), neighborhood.getId().toString(), project.getId().toString());

        TaskResourceRep taskRes = toTask(exportGroup, task, op);

        // call thread that does the work.
        CreateExportGroupSchedulingThread.executeApiTask(this, _asyncTaskService.getExecutorService(), _dbClient, neighborhood, project,
                exportGroup, storageMap, param.getClusters(), param.getHosts(),
                param.getInitiators(), volumeMap, param.getExportPathPolicy(), param.getExportPathParameters(), task, taskRes);

        _log.info("Kicked off thread to perform export create scheduling. Returning task: " + taskRes.getId());

        return taskRes;
    }

    /**
     * Validates the given list of BlockObject's are having valid nativeId
     * 
     * @param blockObjectURIs Block Object's URI list
     */
    private void validateBlockObjectNativeId(List<URI> blockObjectURIs) {
        if (!CollectionUtils.isEmpty(blockObjectURIs)) {
            for (URI boURI : blockObjectURIs) {
                BlockObject bo = BlockObject.fetch(_dbClient, boURI);
                ArgValidator.checkEntity(bo, boURI, false);
                if (NullColumnValueGetter.isNullValue(bo.getNativeId())) {
                    throw APIException.badRequests.nativeIdCannotBeNull(boURI);
                }
            }
        }
    }

    /**
     * When creating an export group the input request should be restricted based on the the
     * export type as follows:
     * <ol>
     * <li>{@link ExportGroup.ExportGroupType#Initiator}: only initiators can be supplied.</li>
     * <li>{@link ExportGroup.ExportGroupType#Host}: only hosts can be supplied.</li>
     * <li>{@link ExportGroup.ExportGroupType#Cluster}: only clusters can be supplied.</li>
     * </ol>
     *
     * @param param
     */
    private void validateCreateInputForExportType(ExportCreateParam param) {
        String type = param.getType();
        // check null and OLD_INITIATOR_TYPE_NAME for backward compatibility
        // TODO - remove the checking in 2.0
        if (type == null || type.equals(OLD_INITIATOR_TYPE_NAME) || type.equals(ExportGroupType.Initiator.name())) {
            if (hasItems(param.getHosts()) || hasItems(param.getClusters())) {
                throw APIException.badRequests.invalidParameterOnlyInitiatorsForExportType(type);
            }
        } else if (type.equals(ExportGroupType.Host.name())) {
            if (hasItems(param.getInitiators()) || hasItems(param.getClusters())) {
                throw APIException.badRequests.invalidParameterOnlyHostsForExportType(type);
            }
        } else if (type.equals(ExportGroupType.Cluster.name())) {
            if (hasItems(param.getInitiators()) || hasItems(param.getHosts())) {
                throw APIException.badRequests.invalidParameterOnlyClustersForExportType(type);
            }
        } else {
            throw APIException.badRequests.invalidParameterValueWithExpected("type", type,
                    EnumSet.allOf(ExportGroupType.class).toArray());
        }
    }
    
    
    /**
     * A simple util to to check for null and empty on a collection
     *
     * @param col the collection
     * @return
     */
    private boolean hasItems(Collection<? extends Object> col) {
        return col != null && !col.isEmpty();
    }

    /**
     * When updating an export group the input request should be validated to
     * ensure any initiators that are requested to be removed are initiators that
     * we are allowed to remove.
     *
     * @param param
     * @param exportGroup
     */
    private void validateUpdateRemoveInitiators(ExportUpdateParam param, ExportGroup exportGroup) {
        if (param != null && param.getInitiators() != null && param.getInitiators().hasRemoved() && exportGroup != null && 
        		exportGroup.getExportMasks() != null) {
            for (URI initiatorId : param.getInitiators().getRemove()) {
                // Check all export masks associated with this export group
                if (!ExportMaskUtils.getExportMasks(_dbClient, exportGroup).isEmpty()) {
                    // This logic is changed in 3.5 to do two things:
                    // 1. Check to make sure none of the Export Group's masks have initiators in its existing list
                    // 2. Allow user to remove an initiator that isn't part of any ExportMask (because of pathing)
                    boolean okToRemove = true;
                    ExportMask mask = null;
                    for (ExportMask exportMask : ExportMaskUtils.getExportMasks(_dbClient, exportGroup)) {
                    	mask = exportMask;
                        if (exportMask.hasExistingInitiator(initiatorId.toString())) {
                            okToRemove = false;
                        }
                    }
                    
                    if (!okToRemove) {
                        Initiator initiator = _dbClient.queryObject(Initiator.class, initiatorId);
                        throw APIException.badRequests.invalidParameterRemovePreexistingInitiator(mask.getMaskName(),
                                initiator.getInitiatorPort());
                    }
                }
            }
        }
    }

    /**
     * When updating an export group the input request should be restricted based on the the
     * export type as follows:
     * <ol>
     * <li>{@link ExportGroup.ExportGroupType#Initiator}: only initiators that belong to the same host as existing initiators can be
     * supplied.</li>
     * <li>{@link ExportGroup.ExportGroupType#Host}: only hosts and initiators that belong to already existing hosts can be supplied.</li>
     * <li>{@link ExportGroup.ExportGroupType#Cluster}: clusters, hosts in already existing clusters and initiators in already existing
     * hosts can be supplied.</li>
     * </ol>
     *
     * @param param
     */
    private void validateUpdateInputForExportType(ExportUpdateParam param, ExportGroup exportGroup) {
        String type = exportGroup.getType();
        if (exportGroup.forInitiator()) {
            if ((param.getHosts() != null && param.getHosts().hasUpdates())
                    || (param.getClusters() != null && param.getClusters().hasUpdates())) {
                throw APIException.badRequests.invalidParameterOnlyInitiatorsForExportType(type);
            }
        } else if (exportGroup.forHost()) {
            if (param.getClusters() != null && param.getClusters().hasUpdates()) {
                throw APIException.badRequests.invalidParameterOnlyHostsOrInitiatorsForExportType(type);
            }
        }
    }

    /**
     * Validate the BlockSnapshot exports from the request param.
     *
     * @param param the export group create request param.
     */
    private void validateBlockSnapshotsForExportGroupCreate(ExportCreateParam param) {
        if (param != null) {
            List<URI> blockObjURIs = new ArrayList<URI>();
            for (VolumeParam volParam : param.getVolumes()) {
                blockObjURIs.add(volParam.getId());
            }

            // validate the RP BlockSnapshots for ExportGroup create
            validateDuplicateRPBlockSnapshotsForExport(blockObjURIs);
            
            // Validate VPLEX backend snapshots for export.
            validateVPLEXBlockSnapshotsForExport(blockObjURIs);
        }
    }

    /**
     * Validate the BlockSnapshot exports being added from the request param.
     *
     * @param param the export group update request param.
     * @param exportGroup the existing export group being updated.
     */
    private void validateBlockSnapshotsForExportGroupUpdate(ExportUpdateParam param, ExportGroup exportGroup) {
        if (param != null && exportGroup != null) {
            List<URI> blockObjToAdd = new ArrayList<URI>();
            List<URI> blockObjExisting = new ArrayList<URI>();
            List<URI> blockObjURIs = new ArrayList<URI>();

            List<VolumeParam> addVolumeParams = param.getVolumes().getAdd();

            // We only care about the BlockObjects we are adding, not removing
            if (addVolumeParams != null && !addVolumeParams.isEmpty()) {
                // Collect the block objects being added from the request param
                for (VolumeParam volParam : addVolumeParams) {
                    blockObjToAdd.add(volParam.getId());
                    blockObjURIs.add(volParam.getId());
                }
                
                // Validate VPLEX backend snapshots for export.
                validateVPLEXBlockSnapshotsForExport(blockObjURIs);

                // Collect the existing block objects from the export group. We are combining
                // the block objects being added with the existing export block objects so that
                // BlockSnapshots for the same target copy (virtual array) are identified more
                // easily through validation.
                if (exportGroup.getVolumes() != null) {
                    for (Map.Entry<String, String> entry : exportGroup.getVolumes().entrySet()) {
                        URI uri = URI.create(entry.getKey());
                        blockObjURIs.add(uri);
                        blockObjExisting.add(uri);
                    }
                }

                // validate the RP BlockSnapshots for ExportGroup create
                validateDuplicateRPBlockSnapshotsForExport(blockObjURIs);
            }

            // Validate any RP BlockSnapshots being added to ensure no corresponding target volumes
            // have already been exported.
            validateSnapshotTargetNotExported(blockObjToAdd, blockObjExisting);
        }
    }
    
    /**
     * Validate VPLEX backend snapshots for export.
     * 
     * @param blockObjURIs The URIs of the block objects to be exported.
     */
    private void validateVPLEXBlockSnapshotsForExport(List<URI> blockObjURIs) {
        // We do not allow a VPLEX backend snapshot that is exposed as a VPLEX
        // volume to be exported to a host/cluster. If the user was to write
        // to the snapshot, this would invalidate the VPLEX volume as the 
        // VPLEX would not be aware of these writes. Further, we know this will
        // fail for VMAX3 backend snapshots with the error "A device cannot belong
        // to more than one storage group in use by FAST".
        for (URI blockObjectURI : blockObjURIs) {
            if (URIUtil.isType(blockObjectURI, BlockSnapshot.class)) {
                BlockSnapshot snapshot = _dbClient.queryObject(BlockSnapshot.class, blockObjectURI);
                if (!CustomQueryUtility.getActiveVolumeByNativeGuid(_dbClient, snapshot.getNativeGuid()).isEmpty()) {
                    throw APIException.badRequests.cantExportSnapshotExposedAsVPLEXVolume(snapshot.getLabel());
                }
            }
        }
    }

    /**
     * Validate any RP BlockSnapshots being added to ensure no corresponding target volumes
     * have already been exported.
     *
     * @param blockObjectsToAdd the list of block object to export
     * @param blockObjectsExisting the list of block objects already exported
     */
    private void validateSnapshotTargetNotExported(List<URI> blockObjectsToAdd, List<URI> blockObjectsExisting) {
        for (URI blockObjToAdd : blockObjectsToAdd) {
            if (URIUtil.isType(blockObjToAdd, BlockSnapshot.class)) {
                BlockSnapshot snapshot = _dbClient.queryObject(BlockSnapshot.class, blockObjToAdd);

                // Search the list of existing BlockObjects for a Volume that corresponds to the snapshot's
                // referenced target Volume. This is done by matching on the nativeId.

                for (URI blockObjExisting : blockObjectsExisting) {
                    if (URIUtil.isType(blockObjExisting, Volume.class)) {
                        Volume volume = _dbClient.queryObject(Volume.class, blockObjExisting);
                        if (snapshot.getNativeId() != null && snapshot.getNativeId().equals(volume.getNativeId())) {
                            throw APIException.badRequests.snapshotTargetAlreadyExported(volume.getId(), snapshot.getId());
                        }
                    }
                }
            }
        }
    }

    /**
     * Validates the list of BlockObjects for RecoverPoint bookmarks that reference
     * the same consistency group and RP site.
     *
     * @param blockObjURIs the list of BlockObject URIs.
     */
    private void validateDuplicateRPBlockSnapshotsForExport(List<URI> blockObjURIs) {
        Map<URI, List<String>> cgToRpSiteMap = new HashMap<URI, List<String>>();

        for (URI blockObjectURI : blockObjURIs) {
            if (URIUtil.isType(blockObjectURI, BlockSnapshot.class)) {
                BlockSnapshot snapshot = _dbClient.queryObject(BlockSnapshot.class, blockObjectURI);

                if (TechnologyType.RP.name().equalsIgnoreCase(snapshot.getTechnologyType())
                        && !NullColumnValueGetter.isNullURI(snapshot.getConsistencyGroup())) {

                    // Check to see if there is an RP bookmark that references
                    // the same RP copy and consistency group.
                    List<String> rpSites = cgToRpSiteMap.get(snapshot.getConsistencyGroup());

                    if (rpSites == null) {
                        // Initialize the list of RP sites
                        rpSites = new ArrayList<String>();
                        cgToRpSiteMap.put(snapshot.getConsistencyGroup(), rpSites);
                    }

                    if (rpSites.contains(snapshot.getEmInternalSiteName())) {
                        // Request is trying to export 2 RP bookmarks for the same target copy and
                        // consistency group.
                        BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class,
                                snapshot.getConsistencyGroup());
                        String rpCopyName = RPHelper.getCgCopyName(_dbClient, cg, snapshot.getVirtualArray(), false);
                        throw APIException.badRequests.duplicateRpBookmarkExport(rpCopyName, cg.getLabel());
                    } else {
                        rpSites.add(snapshot.getEmInternalSiteName());
                    }
                }
            }
        }
    }

    /**
     * This is a helper function to perform the input validation for an export group
     * volumes and snapshots and return the map to be sent to the controller.
     *
     * @param volumes the input parameter
     * @param exportGroup the export group
     * @param storageMap an empty map that will be filled in by the function with the
     *            the block objects to export mapped by storage system
     *
     * @return a map of block object URI to LUN Id for the objects to be exported.
     */
    private Map<URI, Integer> validateBlockObjectsAndGetMap(List<VolumeParam> volumes,
            ExportGroup exportGroup, Map<URI, Map<URI, Integer>> storageMap) {
        if (volumes != null) {
            // validate volumes Lun Ids
            validateVolumeLunIdParam(volumes);

            Map<URI, String> systemURIToSystemTypeMap = new HashMap<>();
            // add volumes/snapshot to export group
            for (VolumeParam volumeParam : volumes) {
                BlockObject block = getAndValidateVolume(exportGroup, volumeParam.getId());
                Integer lun = volumeParam.getLun();
                if (block != null) {
                    if (block instanceof BlockSnapshot) {
                        BlockSnapshot snapshot = (BlockSnapshot) block;
                        checkIfOpenStackSnapshot(snapshot);
                        checkForActiveBlockSnapshot(snapshot);
                    } else if (block instanceof Volume) {
                        // ignore user specified HLU for cinder volume
                        if (lun != ExportGroup.LUN_UNASSIGNED
                                && isCinderVolume((Volume) block, systemURIToSystemTypeMap)) {
                            _log.info("User specified HLU ({}) is ignored for cinder type volume"
                                    + " since Cinder API does not take HLU while exporting.", lun);
                            lun = ExportGroup.LUN_UNASSIGNED;
                        }
                    }
                }
                exportGroup.addVolume(volumeParam.getId(), lun);
            }
        }

        storageMap.putAll(ExportUtils.getStorageToVolumeMap(exportGroup, false, _dbClient));

        // get volumes
        Map<URI, Integer> volumeMap = new HashMap<URI, Integer>();
        for (URI storage : storageMap.keySet()) {
            volumeMap.putAll(storageMap.get(storage));
        }
        return volumeMap;
    }

    /**
     * Checks if the given volume is cinder volume.
     *
     * @param volume the volume
     * @param systemURIToSystemTypeMap the system uri to system type map
     * @return true, if it is cinder volume
     */
    private boolean isCinderVolume(Volume volume,
            Map<URI, String> systemURIToSystemTypeMap) {
        URI systemURI = volume.getStorageController();
        String systemType = systemURIToSystemTypeMap.get(systemURI);
        if (systemType == null) {
            StorageSystem system = _dbClient.queryObject(StorageSystem.class, systemURI);
            systemType = system.getSystemType();
            systemURIToSystemTypeMap.put(systemURI, systemType);
        }
        return Type.openstack.name().equalsIgnoreCase(systemType);
    }

    /**
     * Check if OpenStack snapshot and throw error
     * since snapshot export is not supported for OpenStack systems.
     *
     * @param BLOCK the block snapshot
     */
    private void checkIfOpenStackSnapshot(BlockSnapshot snapshot) {
        if (TechnologyType.NATIVE.toString().equalsIgnoreCase(snapshot.getTechnologyType())) {
            StorageSystem system = _dbClient.queryObject(StorageSystem.class, snapshot.getStorageController());
            if (system.getSystemType().equalsIgnoreCase(Type.openstack.name())) {
                throw APIException.badRequests.snapshotExportNotSupported(Type.openstack.name(), snapshot.getId());
            }
        }
    }

    /**
     * Validate the blocksnapshot is active for export, if one is being exported
     *
     * @param BLOCK - BlockSnapshot object to export
     */
    private void checkForActiveBlockSnapshot(BlockSnapshot snapshot) {
        if (!TechnologyType.RP.toString().equalsIgnoreCase(snapshot.getTechnologyType())) {
            if (!snapshot.getIsSyncActive()) {
                throw APIException.badRequests
                        .invalidParameterBlockSnapshotCannotBeExportedWhenInactive(
                                snapshot.getLabel(), snapshot.getId());
            }
        }
    }

    /**
     * This helper function is used by {@link #updateExportGroup(URI, ExportUpdateParam)} to
     * validate the user input and compute the updated lists of initiators, hosts and clusters.
     *
     * @param exportGroup the export group being updated.
     * @param project the export group project
     * @param storageSystems the storage systems where the export group volumes exist
     * @param param the input parameter
     * @param newClusters a list to be populated with the updated list of clusters
     * @param newHosts a list to be populated with the updated list of hosts
     * @param newInitiators a list to be populated with the updated list of initiators
     * @param addedClusters new clusters to be added to the given Export Group
     * @param removedClusters Clusters to be removed from the given Export Group
     * @param addedHosts New hosts to be added to the give Export Group
     * @param removedHosts Hosts to be removed from the give Export Group
     * @param addedInitiators New initiators to be added to the given Export Group
     * @param removedInitiators Initiators to be removed from the given Export Group
     */
    void validateClientsAndUpdate(ExportGroup exportGroup,
            Project project, Collection<URI> storageSystems,
            ExportUpdateParam param, List<URI> newClusters,
            List<URI> newHosts, List<URI> newInitiators, Set<URI> addedClusters, Set<URI> removedClusters, Set<URI> addedHosts, Set<URI> removedHosts, Set<URI> addedInitiators, Set<URI> removedInitiators) {
        if (param.getClusters() != null) {
            if (!CollectionUtils.isEmpty(param.getClusters().getRemove())) {
                for (URI uri : param.getClusters().getRemove()) {
                    if (!removedClusters.contains(uri)) {
                        removedClusters.add(uri);
                        // removeClusterData(uri, newHosts, newInitiators);
                    }

                }
            }
            if (!CollectionUtils.isEmpty(param.getClusters().getAdd())) {
                for (URI uri : param.getClusters().getAdd()) {
                    Cluster cluster = queryObject(Cluster.class, uri, true);
                    validateClusterData(cluster, exportGroup, storageSystems, project, newHosts, newInitiators);
                    if (!addedClusters.contains(uri)) {
                        addedClusters.add(uri);
                    }
                }
            }
        }

        _log.info("Updated list of Added clusters: {}", addedClusters.toArray());
        _log.info("Updated list of Removed clusters: {}", removedClusters.toArray());

        if (param.getHosts() != null) {
            if (!CollectionUtils.isEmpty(param.getHosts().getRemove())) {
                for (URI uri : param.getHosts().getRemove()) {
                    removedHosts.add(uri);
                    // removeHostData(uri, newInitiators);
                }
            }
            if (!CollectionUtils.isEmpty(param.getHosts().getAdd())) {
                for (URI uri : param.getHosts().getAdd()) {
                    Host host = queryObject(Host.class, uri, true);
                    // If the export type is cluster
                    if (exportGroup.forCluster()) {
                        // make sure the host belongs to one of the group's clusters
                        if (!hasItems(newClusters) || !newClusters.contains(host.getCluster())) {
                            throw APIException.badRequests.invalidParameterHostNotInCluster(host.getHostName());
                        }
                    }
                    validateHostData(host, exportGroup, storageSystems, project, newInitiators);
                    if (!addedHosts.contains(uri)) {
                        addedHosts.add(uri);
                    }
                }
            }
        }
        _log.info("Updated list of Added Hosts: {}", addedHosts.toArray());
        _log.info("Updated list of Removed Hosts: {}", removedHosts.toArray());

        if (param.getInitiators() != null) {
            if (!CollectionUtils.isEmpty(param.getInitiators().getRemove())) {
                for (URI uri : param.getInitiators().getRemove()) {
                    removedInitiators.add(uri);
                }
            }
            if (!CollectionUtils.isEmpty(param.getInitiators().getAdd())) {
                // TODO - Temporarily commented out for backward compatibility
                URI initiatorHostUri = getInitiatorExportGroupHost(exportGroup);
                for (URI uri : param.getInitiators().getAdd()) {
                    Initiator initiator = queryObject(Initiator.class, uri, true);
                    if (exportGroup.forInitiator()) {
                        if (initiatorHostUri == null) {
                            initiatorHostUri = initiator.getHost();
                        } else {
                            if (!initiatorHostUri.equals(initiator.getHost())) {
                                throw APIException.badRequests.initiatorExportGroupInitiatorsBelongToSameHost();
                            }
                        }
                    }
                    validateInitiatorRegistered(initiator);
                    validateInitiatorNetworkRegistered(initiator, exportGroup.getVirtualArray());
                    validateInitiatorData(initiator, exportGroup);
                    if (exportGroup.forCluster() || exportGroup.forHost()) {
                        if (!newHosts.isEmpty() &&
                                !newHosts.contains(initiator.getHost())) {
                            throw APIException.badRequests.invalidParameterExportGroupInitiatorNotInHost(initiator.getId());
                        }
                    }
                    if (!addedInitiators.contains(uri)) {
                        addedInitiators.add(uri);
                    }
                }
            }
        }
        validateInitiatorHostOS(addedInitiators);
        List<URI> connectStorageSystems = new ArrayList<>();
        newInitiators.addAll(addedInitiators);
        newInitiators.removeAll(removedInitiators);
        filterOutInitiatorsNotAssociatedWithVArray(exportGroup, storageSystems, connectStorageSystems, newInitiators);

        // Validate if we're adding new Volumes to the export. If so, we want to make sure that there
        // connections from the volumes to the StorageSystems. If the newInitiators list is empty, then
        // it would mean that not all StorageSystems are connected to the initiators. In which case,
        // the add volumes should fail. The user would need to make sure that the StorageSystem has
        // the necessary connections before proceeding.
        List<VolumeParam> addVolumeParams = param.getVolumes().getAdd();
        if (exportGroup.hasInitiators() && !CollectionUtils.isEmpty(addVolumeParams) &&
                CollectionUtils.isEmpty(newInitiators)) {
            Set<URI> uniqueStorageSystemSet = new HashSet<>();
            for (VolumeParam addVolumeParam : addVolumeParams) {
                BlockObject blockObject = BlockObject.fetch(_dbClient, addVolumeParam.getId());
                if (blockObject != null) {
                    uniqueStorageSystemSet.add(blockObject.getStorageController());
                }
            }
            List<String> storageSystemNames = new ArrayList<>();
            for (URI storageSystemURI : uniqueStorageSystemSet) {
                // Check if it's in the list of connected StorageSystems. If so, then skip it.
                if (connectStorageSystems.contains(storageSystemURI)) {
                    continue;
                }
                StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storageSystemURI);
                if (storageSystem != null) {
                    storageSystemNames.add(storageSystem.getNativeGuid());
                }
            }
            throw APIException.badRequests.storageSystemsNotConnectedForAddVolumes(Joiner.on(',').join(storageSystemNames));
        }
        _log.info("Updated list of initiators: {}", Joiner.on(',').join(newInitiators));
    }

    /**
     * Validate if the initiator is linked to the VirtualArray through some Network
     * Routine will examine the 'newInitiators' list and remove any that do not have any association
     * to the VirtualArrays associated with the StorageSystems.
     *
     * @param exportGroup [in] - ExportGroup object
     * @param storageSystems [in] - Collection of StorageSystem URIs associated with this VArray
     * @param connectedStorageSystems [in/out] - Optional parameter that will contain a list of
     *            StorageSystem URIs that have connections to the initiators
     * @param newInitiators [in/out] - List of initiator URIs to examine.
     */
    private void filterOutInitiatorsNotAssociatedWithVArray(ExportGroup exportGroup, Collection<URI> storageSystems,
            List<URI> connectedStorageSystems,
            List<URI> newInitiators) {
        Iterator<URI> it = newInitiators.iterator();
        BlockStorageScheduler blockScheduler = new BlockStorageScheduler();
        blockScheduler.setDbClient(_dbClient);
        List<URI> exportGroupInitiatorURIs = StringSetUtil.stringSetToUriList(exportGroup.getInitiators());
        while (it.hasNext()) {
            URI uri = it.next();
            Initiator initiator = _dbClient.queryObject(Initiator.class, uri);
            if (initiator == null) {
                _log.info(String.format("Initiator %s was not found in DB. Will be eliminated from request payload.", uri.toString()));
                it.remove();
                continue;
            }
            Set<String> varraysConsidered = new HashSet<String>();
            if (!hasConnectivityToAllSystems(initiator, storageSystems, connectedStorageSystems, exportGroup) ||
                    !isInitiatorInStorageSystemsNetwork(exportGroup, initiator, storageSystems, varraysConsidered)) {
                _log.info(String.format("Initiator %s (%s) will be eliminated from the payload. " +
                        "It was either not found to be connected to any of these StorageSystems [%s] that are " +
                        "associated with VirtualArray(s) %s or not connected to any of its networks.",
                        initiator.getInitiatorPort(), initiator.getId().toString(), Joiner.on(',').join(storageSystems),
                        varraysConsidered.toString()));
                // CTRL-9694: Only remove the initiator if doesn't already belong to the ExportGroup and it is
                // not showing any connection to StorageSystems associated to the ExportGroup's VArray.
                // This would prevent the removal of initiators unless they were explicitly removed, which would
                // have been done before this method was called anyway. So, the only case where the initiator
                // would be removed is when the initiator did not belong to the ExportGroup and there are no
                // connections to the StorageSystems.
                if (!exportGroupInitiatorURIs.contains(uri)) {
                    it.remove();
                }
            }
        }
    }

    /**
     * Determines is an initiator is in one of the StorageSystems networks.
     * This is changed to accomodate VPlex, which uses two Varrays per ExportGroup for distributed export.
     *
     * @param exportGroup -- the ExportGroup
     * @param initiator [in] - the initiator
     * @param system [in] - collection of StorageSystems
     * @param varrays [out] - the varrays considered for the export
     * @return true iff the initiator belongs to a Network and that Network has the VirtualArray
     */
    private boolean isInitiatorInStorageSystemsNetwork(ExportGroup exportGroup, Initiator initiator,
            Collection<URI> systems, Set<String> outVarrays) {
        boolean foundAnAssociatedNetwork = false;
        Set<String> exportGroupVarrays = new HashSet<String>();
        exportGroupVarrays.add(exportGroup.getVirtualArray().toString());
        for (URI systemURI : systems) {
            List<URI> arrayVarrays = ExportUtils.getVarraysForStorageSystemVolumes(exportGroup, systemURI, _dbClient);
            for (URI arrayVarray : arrayVarrays) {
                if (!exportGroupVarrays.contains(arrayVarray.toString())) {
                    exportGroupVarrays.add(arrayVarray.toString());
                }
            }
        }
        outVarrays.addAll(exportGroupVarrays);
        Set<NetworkLite> networks = NetworkUtil.getEndpointAllNetworksLite(initiator.getInitiatorPort(), _dbClient);
        if (networks == null || networks.isEmpty()) {
            // No network associated with the initiator, so it should be removed from the list
            _log.info(String.format("Initiator %s (%s) is not associated with any network.",
                    initiator.getInitiatorPort(), initiator.getId().toString()));
            return false;
        } else {
            // Search through the networks determining if the any are associated with ExportGroup's VirtualArray.
            for (NetworkLite networkLite : networks) {
                if (networkLite == null) {
                    continue;
                }
                Set<String> varraySet = networkLite.fetchAllVirtualArrays();
                if (varraySet != null) {
                    Set<String> intersection = Sets.intersection(varraySet, exportGroupVarrays);
                    if (!intersection.isEmpty()) {
                        _log.info(String.format("Initiator %s (%s) was found to be associated to VirtualArrays %s through network %s.",
                                initiator.getInitiatorPort(), initiator.getId().toString(), intersection.toString(),
                                networkLite.getNativeGuid()));
                        foundAnAssociatedNetwork = true;
                        // Though we could break this loop here, let's continue the loop so that
                        // we can log what other networks that the initiator is seen in
                    }
                }
            }
        }
        return foundAnAssociatedNetwork;
    }

    /**
     * For Initiator type export groups, find the host to which the initiators belong.
     *
     * @param exportGroup the export group
     * @return the URI of the initiators host
     */
    private URI getInitiatorExportGroupHost(ExportGroup exportGroup) {
        URI hostUri = null;
        StringSet exportGroupInitiators = exportGroup.getInitiators();
        if (exportGroup.forInitiator() && exportGroupInitiators != null) {
            for (String uri : exportGroupInitiators) {
                Initiator initiator = queryObject(Initiator.class, URI.create(uri), false);
                hostUri = initiator.getHost();
                break;
            }
        }
        return hostUri;
    }

    /**
     * Updates the lists of hosts and initiator when a cluster is removed.
     *
     * @param cluster the cluster being removed
     * @param newHosts the list of hosts to update
     * @param newInitiators the list of initiators to update
     */
    private void removeClusterData(URI cluster, List<URI> newHosts, List<URI> newInitiators) {
        List<URI> hostUris = ComputeSystemHelper.getChildrenUris(_dbClient, cluster, Host.class, "cluster");
        for (URI hosturi : hostUris) {
            newHosts.remove(hosturi);
            newInitiators.removeAll(ComputeSystemHelper.getChildrenUris(_dbClient, hosturi, Initiator.class, "host"));
        }
    }

    /**
     * Updates the list of initiator when a host is removed.
     *
     * @param hosturi the host being removed
     * @param newInitiators the list of initiators to update
     */
    private void removeHostData(URI hosturi, List<URI> newInitiators) {
        newInitiators.removeAll(ComputeSystemHelper.getChildrenUris(_dbClient, hosturi, Initiator.class, "host"));
    }

    /**
     * Validate that all the initiators to be added to the export group belong to the same host type
     *
     * @param initiators the list of initiators to validate
     */
    private void validateInitiatorHostOS(Set<URI> initiators) {
        Set<String> hostTypes = new HashSet<String>();
        List<URI> hostList = new ArrayList<URI>();

        // Dummy URI used in case we encounter null values
        URI fillerHostURI = NullColumnValueGetter.getNullURI();

        if (initiators != null && !initiators.isEmpty()) {

            for (URI initiatorUri : initiators) {
                Initiator ini = queryObject(Initiator.class, initiatorUri, true);

                // If ini.getHost() returns a null value, set hostURI to fillerHostURI
                URI hostURI = (ini.getHost() == null) ? fillerHostURI : ini.getHost();

                // If we have already come across this URI implies
                // that we have checked its host type, so there is
                // no need to go to the DB again..
                if (!hostList.isEmpty() && hostList.contains(hostURI)) {
                    continue;
                } else {
                    // add the hostURI to the hostList so that it can
                    // help in the next iteration.
                    hostList.add(hostURI);
                }

                if (hostURI == fillerHostURI) {
                    hostTypes.add(String.valueOf(fillerHostURI));
                } else {
                    Host host = queryObject(Host.class, hostURI, true);
                    hostTypes.add(host.getType());
                }
            }

            if (hostTypes.size() != 1) {
                throw APIException.badRequests.initiatorHostsInSameOS();
            }
        }
    }

    /**
     * Validate the data of an initiator. This validation is required when the user
     * has explicitly requested for the initiator to be added.
     *
     * @param initiator the initiator being validated.
     * @param exportGroup the export group where the initiator is to be added
     */
    private void validateInitiatorData(Initiator initiator, ExportGroup exportGroup) {
        validatePortConnectivity(exportGroup, Collections.singletonList(initiator));
    }

    /**
     * Validate the input data for clusters, hosts and initiators for {@link #createExportGroup(ExportCreateParam)}
     *
     * @param exportGroup the export group to populate
     * @param project the export group project
     * @param varray the export group varray
     * @param storageSystems the storage systems the export group has block object in
     * @param clusters the list of clusters to validate
     * @param hosts the list of hosts to validate
     * @param initiators the list of initiators to validate
     * @param volumes The list of volumes being exported (used to calculate numPaths)
     * @param pathParam Optional ExportPathParameters block (ignored if null)
     * @return the aggregate list of initiators needed to export all the hosts and clusters and initiators
     */
    List<URI> validateClientsAndPopulate(ExportGroup exportGroup,
            Project project, VirtualArray varray, Collection<URI> storageSystems,
            List<URI> clusters, List<URI> hosts,
            List<URI> initiators, Collection<URI> volumes, ExportPathParameters pathParam) {
        List<URI> allInitiators = new ArrayList<>();
        List<URI> allHosts = new ArrayList<URI>();
        if (initiators != null && !initiators.isEmpty()) {
            List<Initiator> temp = new ArrayList<Initiator>();
            Set<URI> initiatorsHost = new HashSet<URI>(1);
            for (URI initiatorUri : initiators) {
                Initiator initiator = queryObject(Initiator.class, initiatorUri, true);
                if (initiator.getHost() == null || NullColumnValueGetter.isNullURI(initiator.getHost())) {
                    throw APIException.badRequests.cannotExportInitiatorWithNoCompute(exportGroup.getLabel(), initiator.getInitiatorPort());
                }
                validateInitiatorRegistered(initiator);
                validateInitiatorNetworkRegistered(initiator, varray.getId());
                allInitiators.add(initiator.getId());
                initiatorsHost.add(initiator.getHost());
                temp.add(initiator);
            }
            validateInitiatorsData(temp, initiatorsHost, exportGroup);
        }
        if (hosts != null && !hosts.isEmpty()) {
            for (URI hostUri : hosts) {
                // validate the host
                Host host = queryObject(Host.class, hostUri, true);
                validateHostData(host, exportGroup, storageSystems,
                        project, allInitiators);
                allHosts.add(host.getId());
            }
        }
        if (clusters != null && !clusters.isEmpty()) {
            for (URI clusterUri : clusters) {
                // validate the cluster
                Cluster cluster = queryObject(Cluster.class, clusterUri, true);
                validateClusterData(cluster, exportGroup, storageSystems,
                        project, allHosts, allInitiators);
                exportGroup.addCluster(cluster);
            }
        }

        // validate tagged storage ports in varray
        validateVarrayStoragePorts(storageSystems, varray, volumes, allHosts);

        if (!allInitiators.isEmpty()) {
            // Validate StoragePorts can be assigned
            validatePortAssignmentOnStorageSystems(storageSystems,
                    exportGroup, allInitiators, volumes, pathParam);
        }

        filterOutInitiatorsNotAssociatedWithVArray(exportGroup, storageSystems, null, allInitiators);

        // Validate the Host Operating Systems
        validateInitiatorHostOS(new HashSet(allInitiators));
        _log.info("All clients were found to be valid.");
        // now set the initiators to the export group before saving it
        exportGroup.setInitiators(StringSetUtil.uriListToStringSet(allInitiators));
        exportGroup.setHosts(StringSetUtil.uriListToStringSet(allHosts));
        return allInitiators;
    }

    /**
     * Validates that the initiator doesn't belong to a deregistered Network
     *
     * @param initiator the initiator to validate
     * @param virtualArray the virtual array
     */
    private void validateInitiatorNetworkRegistered(Initiator initiator, URI virtualArray) {
        NetworkLite network = BlockStorageScheduler.getInitiatorNetwork(initiator, _dbClient);
        if (network != null && !RegistrationStatus.REGISTERED.name().equalsIgnoreCase(network.getRegistrationStatus())) {
            throw APIException.badRequests.invalidParameterInitiatorBelongsToDeregisteredNetwork(initiator, network.getId());
        }
    }

    /**
     * Validates that the initiator is registered
     *
     * @param initiator the initiator to validate
     */

    private void validateInitiatorRegistered(Initiator initiator) {
        if (initiator != null && !RegistrationStatus.REGISTERED.name().equalsIgnoreCase(initiator.getRegistrationStatus())) {
            throw APIException.badRequests.invalidParameterInitiatorIsDeregistered(initiator);
        }
    }

    /**
     * Validates that the host belongs to same tenant org and/or project as the export group.
     * Also validates that the host has connectivity to all the storage systems that the
     * export group has block objects in.
     *
     * @param host the host being validated
     * @param exportGroup the export group where the host will be added
     * @param storageSystems the storage systems the export group has block objects in.
     * @param project the export group project
     * @param initiators the list of initiators to be updated with the host initiators.
     */
    private void validateInitiatorsData(List<Initiator> initiators, Set<URI> initiatorsHosts,
            ExportGroup exportGroup) {
        if (initiatorsHosts.size() != 1) {
            throw APIException.badRequests.initiatorExportGroupInitiatorsBelongToSameHost();
        }

        Host host = queryObject(Host.class, initiatorsHosts.iterator().next(), true);
        // if the host is in a project
        if (!NullColumnValueGetter.isNullURI(host.getProject())) {
            // validate it is in the same project as the as the export group,
            if (!host.getProject().equals(exportGroup.getProject().getURI())) {
                throw APIException.badRequests.invalidParameterExportGroupHostAssignedToDifferentProject(host.getHostName(), exportGroup
                        .getProject().getName());
            }
        } else {
            // validate the host is in the same tenant Org as the as the export group,
            Project project = queryObject(Project.class, exportGroup.getProject().getURI(), true);
            if (!host.getTenant().equals(project.getTenantOrg().getURI())) {
                throw APIException.badRequests.invalidParameterExportGroupHostAssignedToDifferentTenant(host.getHostName(),
                        project.getLabel());
            }
        }

        validatePortConnectivity(exportGroup, initiators);
        _log.info("The initiators were validated successfully.");
    }

    /**
     * Validates that the host belongs to same tenant org and/or project as the export group.
     * Also validates that the host has connectivity to all the storage systems that the
     * export group has block objects in.
     *
     * @param host the host being validated
     * @param exportGroup the export group where the host will be added
     * @param storageSystems the storage systems the export group has block objects in.
     * @param project the export group project
     * @param initiators the list of initiators to be updated with the host initiators.
     */
    private void validateHostData(Host host, ExportGroup exportGroup,
            Collection<URI> storageSystems, Project project,
            List<URI> initiators) {
        // if the host is in a project
        if (!NullColumnValueGetter.isNullURI(host.getProject())) {
            // validate it is in the same project as the as the export group,
            if (!host.getProject().equals(project.getId())) {
                throw APIException.badRequests.invalidParameterExportGroupHostAssignedToDifferentProject(host.getHostName(),
                        project.getLabel());
            }
        } else {
            // validate the host is in the same tenant Org as the as the export group,
            if (!host.getTenant().equals(project.getTenantOrg().getURI())) {
                throw APIException.badRequests.invalidParameterExportGroupHostAssignedToDifferentTenant(host.getHostName(),
                        project.getLabel());
            }
        }
        // get host connected initiators
        List<URI> hostInitiators = getHostConnectedInitiators(host, storageSystems, exportGroup);
        if (hostInitiators.isEmpty()) {
            throw APIException.badRequests.noIntiatorsConnectedToVolumes();
        }

        for (URI uri : hostInitiators) {
            if (!initiators.contains(uri)) {
                initiators.add(uri);
            }
        }
        _log.info("Host {} was validated successfully.", host.getId().toString());
    }

    /**
     * Validates that a cluster is in the same tenant org and/or project as the export group.
     * Also makes sure that all hosts in the cluster have connectivity to all storage systems
     * the export group has block objects in.
     *
     * @param cluster the cluster being validated
     * @param exportGroup the export where the cluster will be added
     * @param storageSystems the storage systems the export group has block objects in.
     * @param project the export group project
     * @param allHosts the list of hosts to be updated with the cluster hosts
     * @param allInitiators the list of initiators to be updated with the cluster initiators.
     */
    private void validateClusterData(Cluster cluster, ExportGroup exportGroup,
            Collection<URI> storageSystems, Project project,
            List<URI> allHosts, List<URI> allInitiators) {

        boolean newCluster = exportGroup.getClusters() == null ||
                !exportGroup.getClusters().contains(cluster.getId().toString());
        // if the host is in a project
        if (!NullColumnValueGetter.isNullURI(cluster.getProject())) {
            // validate it is in the same project as the as the export group,
            if (!(cluster.getProject().equals(project.getId()) || !newCluster)) {
                throw APIException.badRequests.invalidParameterClusterAssignedToDifferentProject(cluster.getLabel(), project.getLabel());
            }
        } else {
            // validate it is in the same tenant Org as the as the export group,
            if (!cluster.getTenant().equals(project.getTenantOrg().getURI()) && newCluster) {
                throw APIException.badRequests.invalidParameterClusterInDifferentTenantToProject(cluster.getLabel(), project.getLabel());
            }
        }
        List<Host> clusterHosts = getChildren(cluster.getId(), Host.class, "cluster");
        for (Host host : clusterHosts) {
            validateHostData(host, exportGroup, storageSystems, project, allInitiators);
            if (!allHosts.contains(host.getId())) {
                allHosts.add(host.getId());
            }

        }
        _log.info("Cluster {} was validated successfully", cluster.getId().toString());
    }

    /**
     * For a given set of storage arrays, find the registered initiators on a host that
     * can connect to all the storage arrays given the possible varrays.
     *
     * @param host the host
     * @param storageSystems the set of arrays
     * @exportGroup - ExportGroup used to determine the Varrays
     * @return the list of initiators that have connectivity to all the storage
     *         systems via the varray.
     */
    private List<URI> getHostConnectedInitiators(Host host,
            Collection<URI> storageSystems, ExportGroup exportGroup) {
        List<URI> initiators = new ArrayList<URI>();
        List<Initiator> hostInitiators = getChildren(host.getId(), Initiator.class, "host");
        for (Initiator initiator : hostInitiators) {
            if (initiator.getRegistrationStatus().equals(RegistrationStatus.REGISTERED.toString())
                    && hasConnectivityToAllSystems(initiator, storageSystems,
                            exportGroup)) {
                initiators.add(initiator.getId());
            }
        }
        return initiators;
    }

    /**
     * This function is to retrieve the children of a given class.
     *
     * @param id the URN of the parent
     * @param clzz the child class
     * @param linkField the name of the field in the child class that stored the parent id
     * @return a list of children of tenant for the given class
     */
    protected <T extends DataObject> List<T> getChildren(URI id, Class<T> clzz, String linkField) {
        List<URI> uris = _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getContainedObjectsConstraint(id, clzz, linkField));
        List<T> retDataObjects = new ArrayList<T>();
        if (uris != null && !uris.isEmpty()) {
            List<T> dataObjects = _dbClient.queryObject(clzz, uris);
            for (T dataObject : dataObjects) {
                if (!dataObject.getInactive()) {
                    retDataObjects.add(dataObject);
                }
            }
        }
        return retDataObjects;
    }

    /**
     * Update an export group which includes:
     * <ol>
     * <li>Add/Remove block objects (volumes, mirrors and snapshots)</li>
     * <li>Add/remove clusters</li>
     * <li>Add/remove hosts</li>
     * <li>Add/remove initiators</li>
     * </ol>
     * Depending on the export group type (Initiator, Host or Cluster), the
     * request is restricted to enforce the same rules as {@link #createExportGroup(ExportCreateParam)}:
     * <ol>
     * <li>For initiator type groups, only initiators are accepted in the request. Further the initiators must be in the same host as the
     * existing initiators.</li>
     * <li>For host type groups, only hosts and initiators that belong to existing hosts will be accepted.</li>
     * <li>For cluster type groups, only clusters, hosts and initiators will be accepted. Hosts and initiators must belong to existing
     * clusters and hosts.</li>
     * </ol>
     * <b>Note:</b> The export group name, project and varray can not be modified.
     *
     * @param id the URN of a ViPR export group to be updated
     * @param param the request parameter
     * @brief Update block export
     * @return the update job tracking task id
     * @throws ControllerException
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskResourceRep updateExportGroup(@PathParam("id") URI id, ExportUpdateParam param)
            throws ControllerException {

        // Basic validation of ExportGroup and update request
        ExportGroup exportGroup = queryObject(ExportGroup.class, id, true);
        if (exportGroup.checkInternalFlags(DataObject.Flag.DELETION_IN_PROGRESS)) {
            throw BadRequestException.badRequests.deletionInProgress(
                    exportGroup.getClass().getSimpleName(), exportGroup.getLabel());
        }
        Project project = queryObject(Project.class, exportGroup.getProject().getURI(), true);
        validateUpdateInputForExportType(param, exportGroup);
        validateUpdateRemoveInitiators(param, exportGroup);
        validateUpdateIsNotForVPlexBackendVolumes(param, exportGroup);
        validateBlockSnapshotsForExportGroupUpdate(param, exportGroup);
        validateExportGroupNoPendingEvents(exportGroup);

        /**
         * ExportGroup Add/Remove volume should have valid nativeId
         */
        List<URI> boURIList = new ArrayList<>();
        if (param.getVolumes() != null) {
            if (param.getVolumes().getAdd() != null) {
                for (VolumeParam volParam : param.getVolumes().getAdd()) {
                    boURIList.add(volParam.getId());
                }
            }
            if (param.getVolumes().getRemove() != null) {
                for (URI volURI : param.getVolumes().getRemove()) {
                    boURIList.add(volURI);
                }
                validateVolumesNotMounted(exportGroup, param.getVolumes().getRemove());
            }
        }
        validateBlockObjectNativeId(boURIList);

        lookupPathParams(param.getExportPathPolicy());
        if (param.getExportPathParameters() != null) {
            // Only [RESTRICTED_]SYSTEM_ADMIN may override the Vpool export parameters
            ExportPathParameters pathParam = param.getExportPathParameters();
            if ((pathParam.getMaxPaths() != null || 
                    pathParam.getMaxPaths() != null ||
                    pathParam.getPathsPerInitiator() != null) &&
                    !_permissionsHelper.userHasGivenRole(getUserFromContext(),
                            null, Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN)) {
                throw APIException.forbidden.onlySystemAdminsCanOverrideVpoolPathParameters(exportGroup.getLabel());
            }
        }

        // call the controller to handle all updated
        String task = UUID.randomUUID().toString();
        Operation op = initTaskStatus(exportGroup, task, Operation.Status.pending, ResourceOperationTypeEnum.UPDATE_EXPORT_GROUP);

        // persist the export group to the database
        _dbClient.persistObject(exportGroup);
        auditOp(OperationTypeEnum.UPDATE_EXPORT_GROUP, true, AuditLogManager.AUDITOP_BEGIN,
                exportGroup.getLabel(), exportGroup.getId().toString(),
                exportGroup.getVirtualArray().toString(), exportGroup.getProject().toString());

        TaskResourceRep taskRes = toTask(exportGroup, task, op);

        CreateExportGroupUpdateSchedulingThread.executeApiTask(this, _asyncTaskService.getExecutorService(), _dbClient, project,
                exportGroup, param, task, taskRes);

        _log.info("Kicked off thread to perform export update scheduling. Returning task: " + taskRes.getId());

        return taskRes;
    }

    /**
     * Validate there are no pending or failed events (actionable events) against the hosts or clusters
     * associated with this export group.
     * 
     * @param exportGroup
     *            export group we wish to update/delete
     */
    private void validateExportGroupNoPendingEvents(ExportGroup exportGroup) {
        // Find the compute resource and see if there are any pending or failed events
        List<URI> computeResourceIDs = new ArrayList<>();

        if (exportGroup == null) {
            return;
        }

        // Grab all clusters from the export group
        if (exportGroup.getClusters() != null && !exportGroup.getClusters().isEmpty()) {
            computeResourceIDs.addAll(URIUtil.toURIList(exportGroup.getClusters()));
        }

        // Grab all hosts from the export group
        if (exportGroup.getHosts() != null && !exportGroup.getHosts().isEmpty()) {
            computeResourceIDs.addAll(URIUtil.toURIList(exportGroup.getHosts()));
        }

        StringBuffer errMsg = new StringBuffer();
        // Query for actionable events with these resources
        for (URI computeResourceID : computeResourceIDs) {
            List<ActionableEvent> events = EventUtils.findAffectedResourceEvents(_dbClient, computeResourceID);
            if (events != null && !events.isEmpty()) {
                for (ActionableEvent event : events) {
                    if (event.getEventStatus().equalsIgnoreCase(ActionableEvent.Status.pending.name())
                            || event.getEventStatus().equalsIgnoreCase(ActionableEvent.Status.failed.name())) {
                        // Make sure no duplicate event strings are present
                        if (!errMsg.toString().contains(event.forDisplay())) {
                            errMsg.append(event.forDisplay());
                            errMsg.append(", ");
                        }
                    }
                }
            }
        }

        if (errMsg.length() != 0) {
            // Remove trailing comma and space from the error message
            if (errMsg.length() > 2) {
                errMsg = errMsg.delete(errMsg.length() - 2, errMsg.length());
            }
            throw APIException.badRequests.cannotExecuteOperationWhilePendingOrFailedEvent(errMsg.toString());
        }
    }
    
    /**
     * This function starts with the existing volumes and computes the final volumes
     * map. This is needed to check the validity of the lun values and for finding
     * the list of storage system against which the clients should be validated
     *
     * @param param
     * @param exportGroup the export group
     * @return the updateVolumesMap derived from the params
     */
    Map<URI, Integer> getUpdatedVolumesMap(ExportUpdateParam param, ExportGroup exportGroup) {
        Map<URI, Integer> newVolumes = StringMapUtil.stringMapToVolumeMap(exportGroup.getVolumes());
        // get the new block objects map
        Map<URI, Integer> addedVolumesMap = getChangedVolumes(param, true);
        _log.info("Added volumes list: {}", Joiner.on(',').join(addedVolumesMap.keySet()));
        Map<URI, Integer> removedVolumesMap = getChangedVolumes(param, false);
        _log.info("Removed volumes list: {}", Joiner.on(',').join(removedVolumesMap.keySet()));
        // remove old volumes - Do not check if the volume existed indeed or not
        // we should allow re-entry and account for the possibility that the db is
        // out of sync with the storage system
        for (URI uri : removedVolumesMap.keySet()) {
            newVolumes.remove(uri);
        }
        newVolumes.putAll(addedVolumesMap);
        return newVolumes;
    }

    /**
     * Given the requested changes, return a map of volume-lun.
     *
     * @param param the export group update request object
     * @param added a boolean that indicates if the map should be computed for
     *            the added or removed volumes in the request object.
     * @return a map of volume-lun as specified in the update export group request
     */
    Map<URI, Integer> getChangedVolumes(ExportUpdateParam param, boolean added) {
        Map<URI, Integer> newVolumes = new HashMap<URI, Integer>();
        if (param.getVolumes() != null) {
            if (added && param.getVolumes().getAdd() != null) {
                for (VolumeParam objParam : param.getVolumes().getAdd()) {
                    newVolumes.put(objParam.getId(), objParam.getLun());
                }
            } else if (!added && param.getVolumes().getRemove() != null) {
                _log.info("getChangedVolumes, amount of volumes to remove: " + param.getVolumes().getRemove().size());
                for (URI uri : param.getVolumes().getRemove()) {
                    _log.info("getChangedVolumes, removing volume: " + uri);
                    newVolumes.put(uri, -1);
                }
            }
        }
        return newVolumes;
    }

    /**
     * Validates the block object input for {@link #updateExportGroup(URI, ExportUpdateParam)}. It
     * checks that
     * <ol>
     * <li>the block objects exists and that it is in the same project as the export group.</li>
     * <li>All block object are assigned a unique and valid lun Id, or that all of the block objects are NOT assigned a lun id. In this case
     * the system will assign a lun id.</li>
     *
     * @param blockObjectsMap a map of block object to lun id
     * @param exportGroup the export group to be updated
     * @return a map of storage systems to volume/lun maps
     */
    Map<URI, Map<URI, Integer>> computeAndValidateVolumes(
            Map<URI, Integer> blockObjectsMap, ExportGroup exportGroup, ExportUpdateParam param) {
        Map<URI, Map<URI, Integer>> storageMap = new HashMap<URI, Map<URI, Integer>>();
        List<Integer> luns = new ArrayList<Integer>();
        Map<URI, String> systemURIToSystemTypeMap = new HashMap<>();
        Map<URI, Integer> existingVols = StringMapUtil.stringMapToVolumeMap(exportGroup.getVolumes());
        for (URI uri : blockObjectsMap.keySet()) {
            // make sure the volumes are valid
            Integer lun = blockObjectsMap.get(uri);
            // make sure the luns are either unique or all unassigned
            if (!existingVols.containsKey(uri)) {
                validateBlockObjectLun(luns, lun, existingVols.values());
            }

            /*
             * cq612014 - add null sanity check for blockobject. If null, throw details exception for
             * ease of debug
             */
            BlockObject object = getAndValidateVolume(exportGroup, uri, existingVols.keySet());
            if (object != null) {
                if (object instanceof BlockSnapshot) {
                    BlockSnapshot snapshot = (BlockSnapshot) object;
                    checkIfOpenStackSnapshot(snapshot);
                    // We should validate syncactive check only for the current volumes to remove.
                    if (null != param.getVolumes() && null != param.getVolumes().getRemove()
                            && param.getVolumes().getRemove().contains(object.getId())) {
                        checkForActiveBlockSnapshot(snapshot);
                    }
                } else if (object instanceof Volume) {
                    // ignore user specified HLU for cinder volume
                    if (!existingVols.containsKey(uri)
                            && lun != ExportGroup.LUN_UNASSIGNED
                            && isCinderVolume((Volume) object, systemURIToSystemTypeMap)) {
                        _log.info("User specified HLU ({}) is ignored for cinder type volume"
                                + " since Cinder API does not take HLU while exporting.", lun);
                        lun = ExportGroup.LUN_UNASSIGNED;
                        blockObjectsMap.put(uri, lun);
                    }
                }

                Map<URI, Integer> temp = storageMap.get(object.getStorageController());
                if (temp == null) {
                    temp = new HashMap<URI, Integer>();
                    storageMap.put(object.getStorageController(), temp);
                }
                temp.put(uri, lun);
            } else {
                throw APIException.badRequests.invalidParameterVolumeExportMismatch(uri, exportGroup.getId());
            }
        }
        return storageMap;
    }

    /**
     * checks that either all lun ids are unique or all {@link ExportGroup#LUN_UNASSIGNED}
     *
     * @param luns the list of all luns
     * @param lun the lun to be validated
     */
    private void validateBlockObjectLun(List<Integer> luns, Integer lun, Collection<Integer> existingLuns) {
        if (luns.contains(ExportGroup.LUN_UNASSIGNED)) {
            // if the previous ones were unassigned, this one should be unassigned
            if (!lun.equals(ExportGroup.LUN_UNASSIGNED)) {
                throw APIException.badRequests.uniqueLunsOrNoLunValue();
            }
        } else {
            // luns is either empty or have something that is assigned
            if (!luns.isEmpty()) {
                // if the previous ones were assigned, this one should be assigned
                // and have a different lun id from the others
                if (luns.contains(lun) || lun.equals(ExportGroup.LUN_UNASSIGNED)) {
                    throw APIException.badRequests.uniqueLunsOrNoLunValue();
                }
            }
            // we also need to check that it is not in the existing luns
            // unless it is unassigned then it can match previous luns
            if (existingLuns.contains(lun) && !lun.equals(ExportGroup.LUN_UNASSIGNED)) {
                throw APIException.badRequests.invalidParameterExportGroupAlreadyHasLun(lun);
            }
            luns.add(lun);
        }
    }

    /**
     * Get block export details - the list of volumes and snapshots and the list of SCSI initiators
     * that the shared storage is exported to.
     *
     * @param groupId Export group Identifier
     * @brief Show block export
     * @return Block export details
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public ExportGroupRestRep getExportGroup(@PathParam("id") URI groupId) {
        ExportGroup exportGroup = queryResource(groupId);
        return toExportResponse(exportGroup);
    }

    private ExportGroupRestRep toExportResponse(ExportGroup export) {
        // getInitiators() getVolumes()
        return map(export, getInitiators(export), getVolumes(export),
                getHosts(export), getClusters(export), getPathParameters(export));
    }

    // This was originally in the ExportGroupRestRep
    private List<Initiator> getInitiators(ExportGroup export) {
        List<Initiator> initiators = new ArrayList<Initiator>();
        // Set the initiators for this export group, which will be
        // included in the response for the export group.
        StringSet initiatorIds = export.getInitiators();
        if ((initiatorIds != null) && !initiatorIds.isEmpty()) {
            List<URI> initiatorURIs = new ArrayList<URI>();
            for (String initiatorId : initiatorIds) {
                initiatorURIs.add(URI.create(initiatorId));
            }
            try {
                initiators.addAll(_dbClient.queryObject(Initiator.class, initiatorURIs));
            } catch (Exception e) {
                _log.error("Error getting initiators for export group {}", export.getId());
            }
        }
        return initiators;
    }

    // This was originally in the ExportGroupRestRep
    private Map<String, Integer> getVolumes(ExportGroup exportGroup) {
        Map<String, Integer> volumesMap = new HashMap<String, Integer>();
        StringMap volumes = exportGroup.getVolumes();
        
        for (ExportMask exportMask : ExportMaskUtils.getExportMasks(_dbClient, exportGroup)) {               
            try {
                if (exportMask != null && exportMask.getVolumes() != null) {
                    for (Map.Entry<String, String> entry : exportMask.getVolumes().entrySet()) {
                        if (!volumesMap.containsKey(entry.getKey()) && (entry.getValue() != null)) {
                            // ensure that this volume is referenced by this export group
                            if (volumes != null && volumes.containsKey(entry.getKey())) {
                                volumesMap.put(entry.getKey(), Integer.valueOf(entry.getValue()));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                _log.error("Error getting volumes for export group {}", exportGroup.getId(), e);
            }
        }
        
        /*
         * Now include any volumes that might be a part of the Export Group
         * but not of the Export Mask.
         */
        if (volumes != null) {
            for (Map.Entry<String, String> entry : volumes.entrySet()) {
                if (!volumesMap.containsKey(entry.getKey()) && (entry.getValue() != null)) {
                    volumesMap.put(entry.getKey(), Integer.valueOf(entry.getValue()));
                }
            }
        }

        return volumesMap;
    }

    // This was originally in the ExportGroupRestRep
    private List<Host> getHosts(ExportGroup export) {
        if (hasItems(export.getHosts())) {
            List<URI> uris = StringSetUtil.stringSetToUriList(export.getHosts());
            return _dbClient.queryObject(Host.class, uris);
        }
        return new ArrayList<Host>();
    }

    // This was originally in the ExportGroupRestRep
    private List<Cluster> getClusters(ExportGroup export) {
        if (hasItems(export.getClusters())) {
            List<URI> uris = StringSetUtil.stringSetToUriList(export.getClusters());
            return _dbClient.queryObject(Cluster.class, uris);
        }
        return new ArrayList<Cluster>();
    }

    private List<ExportPathParams> getPathParameters(ExportGroup export) {
        if (!export.getPathParameters().isEmpty()) {
            List<String> ids = new ArrayList<String>(export.getPathParameters().values());
            List<URI> uris = URIUtil.toURIList(ids);
            return _dbClient.queryObject(ExportPathParams.class, uris);
        } else {
            return new ArrayList<ExportPathParams>();
        }
    }

    /**
     * Deactivate block export. It will be deleted by the garbage collector on a
     * subsequent iteration
     * <p>
     * This removes visibility of shared storage in the block export to servers through initiators in the block export.
     * <p>
     * If SAN Zones were created as a result of this Export Group (see Export Group Create), they will be removed if they are not in use by
     * other Export Groups.
     * <p>
     *
     * NOTE: This is an asynchronous operation.
     *
     * @param groupId Block export identifier
     * @brief Delete block export
     * @return Task resource representation
     * @throws ControllerException
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/deactivate")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskResourceRep deactivateExportGroup(@PathParam("id") URI groupId)
            throws ControllerException {
        String task = UUID.randomUUID().toString();
        Operation op = null;
        ExportGroup exportGroup = lookupExportGroup(groupId);
        Map<URI, Map<URI, Integer>> storageMap = ExportUtils.getStorageToVolumeMap(exportGroup, true, _dbClient);
        validateExportGroupNoPendingEvents(exportGroup);

        // Validate that none of the volumes are mounted (datastores, etc)
        if (exportGroup.getVolumes() != null) {
            validateVolumesNotMounted(exportGroup, URIUtil.toURIList(exportGroup.getVolumes().keySet()));
        }

        // Don't allow deactivation if there is an operation in progress.
        Set<URI> tenants = new HashSet<URI>();
        tenants.add(exportGroup.getTenant().getURI());
        Set<ExportGroup> dataObjects = new HashSet<ExportGroup>();
        dataObjects.add(exportGroup);
        checkForPendingTasks(tenants, dataObjects);
        // Mark deletion in progress. This will cause future updates to fail.
        exportGroup.addInternalFlags(DataObject.Flag.DELETION_IN_PROGRESS);
        // Remove any associated ExportPathParam
        if (exportGroup.getVolumes() != null && !exportGroup.getVolumes().isEmpty()
                && !exportGroup.getPathParameters().isEmpty()) {
            removeBlockObjectsFromPathParamMap(URIUtil.uris(exportGroup.getVolumes().keySet()), exportGroup);
        }

        if (storageMap.isEmpty()) {
            op = initTaskStatus(exportGroup, task, Operation.Status.ready, ResourceOperationTypeEnum.DELETE_EXPORT_GROUP);
            _dbClient.markForDeletion(exportGroup);
        } else {
            op = initTaskStatus(exportGroup, task, Operation.Status.pending, ResourceOperationTypeEnum.DELETE_EXPORT_GROUP);
            _dbClient.persistObject(exportGroup);

            BlockExportController exportController = getExportController();
            exportController.exportGroupDelete(exportGroup.getId(), task);
        }
        auditOp(OperationTypeEnum.DELETE_EXPORT_GROUP, true, AuditLogManager.AUDITOP_BEGIN,
                exportGroup.getLabel(), exportGroup.getId().toString(),
                exportGroup.getVirtualArray().toString(), exportGroup.getProject().toString());

        return toTask(exportGroup, task, op);
    }

    /**
     * Retrieve resource representations based on input ids.
     *
     * @param param POST data containing the id list.
     * @brief List data of export group resources
     * @return list of representations.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public ExportGroupBulkRep getBulkResources(BulkIdParam param) {
        return (ExportGroupBulkRep) super.getBulkResources(param);
    }

    @Override
    protected URI getTenantOwner(URI id) {
        ExportGroup exportGroup = queryResource(id);
        return exportGroup.getTenant().getURI();
    }

    @Override
    protected ExportGroup queryResource(URI id) {
        return lookupExportGroup(id);
    }

    /**
     * Convenience method for initializing a task object with a status
     *
     * @param exportGroup export group
     * @param task task ID
     * @param status status to initialize with
     * @param opType operation type
     * @return operation object
     */
    private Operation initTaskStatus(ExportGroup exportGroup, String task, Operation.Status status, ResourceOperationTypeEnum opType) {
        if (exportGroup.getOpStatus() == null) {
            exportGroup.setOpStatus(new OpStatusMap());
        }
        Operation op = new Operation();
        op.setResourceType(opType);
        if (status == Operation.Status.ready) {
            op.ready();
        }
        _dbClient.createTaskOpStatus(ExportGroup.class, exportGroup.getId(), task, op);
        return op;
    }

    /**
     *
     * @param project
     * @param param
     * @return
     */
    private ExportGroup prepareExportGroup(Project project, ExportCreateParam param) {
        TenantOrg tenantOrg = _permissionsHelper.getObjectById(project.getTenantOrg().getURI(), TenantOrg.class);
        ExportGroup exportGroup = new ExportGroup();
        exportGroup.setLabel(param.getName());
        // TODO - For temporary backward compatibility
        String type = param.getType();
        exportGroup.setType((type == null || type.equals(OLD_INITIATOR_TYPE_NAME)) ? ExportGroupType.Initiator.name() : type);
        exportGroup.setId(URIUtil.createId(ExportGroup.class));
        exportGroup.setProject(new NamedURI(project.getId(), exportGroup.getLabel()));
        exportGroup.setVirtualArray(param.getVarray());
        exportGroup.setTenant(new NamedURI(project.getTenantOrg().getURI(), exportGroup.getLabel()));

        String generatedName = _nameGenerator.generate(tenantOrg.getLabel(),
                exportGroup.getLabel(), exportGroup.getId().toString(), '_', 56);
        exportGroup.setGeneratedName(generatedName);

        return exportGroup;
    }

    /**
     * Return project of volume storage (volume/snapshot)
     *
     * @param block
     * @return
     */
    private URI getBlockProject(BlockObject block) {
        if (block.getClass() == Volume.class) {
            return ((Volume) block).getProject().getURI();
        } else if (block.getClass() == BlockSnapshot.class) {
            return ((BlockSnapshot) block).getProject().getURI();
        } else if (block.getClass() == BlockMirror.class) {
            return ((BlockMirror) block).getProject().getURI();
        } else {
            throw APIException.badRequests.invalidBlockObjectToExport(block.getLabel(), block.getClass().getSimpleName());
        }
    }

    /**
     * Validate the the input volume uri to ensure:
     * <ol>
     * <li>An active volume or snapshot exists for the uri</li>
     * <li>The export group varray is valid for the volume as determined by {@link #getBlockObjectVirtualArrays(BlockObject)}.</li>
     * <li>The volume is in the same project as the export group.</li>
     * </ol>
     *
     * @param exportGroup the export group where the volume is being added
     * @param volUri the uri of the volume of snapshot being added
     * @param currentVolumes the volumes that are already in the export group.
     * @return BlockObject the volume or snapshot being added to the export group.
     */
    private BlockObject getAndValidateVolume(ExportGroup exportGroup, URI volUri, Collection<URI> currentVolumes) {
        BlockObject block = BlockObject.fetch(_dbClient, volUri);
        // we only want to validate when the volume is added to the export group
        // to avoid getting the user stuck if something is wrong with the current
        // volumes states
        if (currentVolumes == null || !currentVolumes.contains(volUri)) {
            if (block == null || block.getInactive()) {
                throw APIException.badRequests.invalidParameterVolumeExportMismatch(volUri, exportGroup.getId());
            }
            if (!exportGroup.getProject().getURI().equals(getBlockProject(block))) {
                throw APIException.badRequests.invalidParameterVolumeExportProjectsMismatch(getBlockProject(block), exportGroup
                        .getProject().getURI());
            }
            // Validate that the ExportGroup varray == the Volume varray, except when
            // it's a VPlex, the ExportGroup varray can be any Varray on the StorageSystem.
            Set<URI> blockObjectVarrays = ExportUtils.getBlockObjectVarrays(block, _dbClient);
            if (!blockObjectVarrays.contains(exportGroup.getVirtualArray())) {
                VirtualPool vpool = ExportUtils.getBlockObjectVirtualPool(block, _dbClient);
                if (vpool != null && vpool.getHighAvailability() != null
                        && vpool.getHighAvailability().equals(VirtualPool.HighAvailabilityType.vplex_local.name())) {
                    // Don't fail for a local VPLEX volume; it might be on the HA side
                    _log.info(String.format("Local VPLEX volume %s not in Export Group Varray %s", block.getLabel(),
                            exportGroup.getVirtualArray()));
                } else {
                    throw APIException.badRequests.invalidParameterVolumeExportVirtualArrayMismatch(block.getId(), block.getVirtualArray(),
                            exportGroup.getVirtualArray());
                }
            }
        }
        return block;
    }

    /**
     * Gets the volume for the given id and if the volume is being added to the
     * export group, then also validate it.
     *
     * @param exportGroup the export grouo where the volume is being added
     * @param id the URN of a ViPR volume
     * @see #getAndValidateVolume(ExportGroup, URI, Collection)
     * @return the volume.
     */
    private BlockObject getAndValidateVolume(ExportGroup exportGroup, URI id) {
        return getAndValidateVolume(exportGroup, id, null);
    }

    /**
     * Given the export name and a project URI, get the applicable export object.
     *
     *
     * @param groupId@return - null, if not found, otherwise the EXPORT associated
     *            with the project with name as 'groupName'.
     */
    private ExportGroup lookupExportGroup(URI groupId) {
        ArgValidator.checkUri(groupId);
        ExportGroup group = _permissionsHelper.getObjectById(groupId, ExportGroup.class);
        ArgValidator.checkEntityNotNull(group, groupId, isIdEmbeddedInURL(groupId));
        return group;
    }

    /**
     * Check if initiators have connectivity to a storage port.
     *
     * @param exportGroup
     * @param initiators
     */
    private void validatePortConnectivity(ExportGroup exportGroup,
            List<Initiator> initiators) {
        Map<URI, Map<URI, Integer>> storageMap = ExportUtils.getStorageToVolumeMap(
                exportGroup, false, _dbClient);
        // we want to make sure the initiator can access each storage
        for (URI storage : storageMap.keySet()) {
            StorageSystem storageSystem = _dbClient.queryObject(
                    StorageSystem.class, storage);
            List<URI> varrays = ExportUtils.getVarraysForStorageSystemVolumes(exportGroup, storage, _dbClient);
            for (Initiator initiator : initiators) {
                // check the initiator has connectivity
                if (!hasConnectivityToSystem(storageSystem, varrays, initiator)) {
                    throw APIException.badRequests.initiatorNotConnectedToStorage(initiator.toString(), storageSystem.getNativeGuid());
                }
            }
        }
    }

    /**
     * Checks if an initiator has connectivity to a storage system in a varray.
     *
     * @param storageSystem the storage system where connectivity is needed
     * @param varrays - A list of varrays to check for matches in (multiple varrays for VPLEX clusters)
     * @param initiator the initiator
     * @return true if at least one port is found
     */
    private boolean hasConnectivityToSystem(StorageSystem storageSystem,
            List<URI> varrays,
            Initiator initiator) {
        try {
            return ConnectivityUtil.isInitiatorConnectedToStorageSystem(initiator, storageSystem, varrays, _dbClient);
        } catch (PlacementException ex) {
            _log.info(String.format("Initiator %s (%s) has no connectivity to StorageSystem %s (%s) in varrays %s",
                    initiator.getInitiatorPort(), initiator.getId(), storageSystem.getNativeGuid(),
                    storageSystem.getId(), varrays.toString()));
            return false;
        } catch (Exception ex) {
            _log.error("An error occurred while verifying Initiator connectivity: ", ex);
            throw APIException.badRequests.errorVerifyingInitiatorConnectivity(
                    initiator.toString(), storageSystem.getNativeGuid(), ex.getMessage());
        }
    }

    /**
     * Validate that we can assign the required number of ports for the varray(s)
     * required to complete the export. Multiple varrays could be used if VPLEX.
     *
     * @param storageSystemURIs
     * @param exportGroup
     * @param initiatorURIs
     * @param volumes
     * @param pathParam optional ExportPathParameter block to override validation check
     */
    private void validatePortAssignmentOnStorageSystems(Collection<URI> storageSystemURIs,
            ExportGroup exportGroup, List<URI> initiatorURIs, Collection<URI> volumes, ExportPathParameters pathParam) {
        // Do not validate ExportGroup Initiator type exports
        if (exportGroup.forInitiator()) {
            return;
        }

        for (URI storageSystemURI : storageSystemURIs) {
            StorageSystem storageSystem = _dbClient.queryObject(
                    StorageSystem.class, storageSystemURI);
            // If a VPlex system, we might be exporting from either Varray for the distributed volumes.
            // Validate the initiators in their respective varrays.
            if (storageSystem.getSystemType().equals(DiscoveredDataObject.Type.vplex.name())) {
                List<URI> varrays = new ArrayList<URI>();
                Map<URI, Set<URI>> varrayToVolumes = VPlexUtil.mapBlockObjectsToVarrays(_dbClient,
                        volumes, storageSystemURI, exportGroup);
                varrays.addAll(varrayToVolumes.keySet());
                Map<URI, List<URI>> varrayToInitiatorsMap = VPlexUtil.partitionInitiatorsByVarray(_dbClient,
                        initiatorURIs, varrays, storageSystem);
                int nValidations = 0;
                for (URI varrayKey : varrays) {
                    if (varrayToInitiatorsMap.get(varrayKey) == null
                            || varrayToInitiatorsMap.get(varrayKey).isEmpty()) {
                        continue;
                    }
                    List<Initiator> initiators = _dbClient.queryObject(Initiator.class, varrayToInitiatorsMap.get(varrayKey));
                    if (varrayToVolumes.get(varrayKey) != null) {
                        nValidations++;
                        Collection<String> initiatorAddresses = Collections2.transform(initiators,
                                CommonTransformerFunctions.fctnInitiatorToPortName());
                        _log.info(String.format("Validating port assignments varray %s initiators %s",
                                varrayKey.toString(), initiatorAddresses));
                        validatePortAssignment(storageSystem, varrayKey, _blockStorageScheduler,
                                initiators, varrayToVolumes.get(varrayKey), exportGroup.getId(), pathParam);
                    }
                }
                if (nValidations == 0) {
                    _log.info("No validations made for VPlex port assignment");
                }
            } else {
                List<Initiator> initiators = _dbClient.queryObject(Initiator.class, initiatorURIs);
                URI varray = exportGroup.getVirtualArray();
                Collection<String> initiatorAddresses = Collections2.transform(initiators,
                        CommonTransformerFunctions.fctnInitiatorToPortName());
                _log.info(String.format("Validating port assignments varray %s initiators %s",
                        varray.toString(), initiatorAddresses));
                validatePortAssignment(storageSystem, varray, _blockStorageScheduler, initiators,
                        volumes, exportGroup.getId(), pathParam);
            }
        }
    }

    /**
     * Verifies that StoragePorts can be assigned for a StorageSystem given a set of Initiators.
     * This will verify that the numPaths variable is not too low or too high to allow assignment.
     *
     * @param storageSystem
     * @param varray VirtualArray of ExportGroup
     * @param blockScheduler
     * @param initiators List<Initiators>
     * @param exportPathParameters Optional export path parameters to affect validation
     * @param numPaths
     */
    private void validatePortAssignment(StorageSystem storageSystem, URI varray,
            BlockStorageScheduler blockScheduler, List<Initiator> initiators,
            Collection<URI> volumes, URI exportGroupURI, ExportPathParameters exportPathParameters) {
        try {

            ExportPathParams pathParams = blockScheduler.calculateExportPathParamForVolumes(
                    volumes, 0, storageSystem.getId(), exportGroupURI);
            if (exportPathParameters != null) {
                // Override the path parameters for the validity check.
                if (exportPathParameters.getMaxPaths() != null) {
                    pathParams.setMaxPaths(exportPathParameters.getMaxPaths());
                }
                if (exportPathParameters.getMinPaths() != null) {
                    pathParams.setMinPaths(exportPathParameters.getMinPaths());
                }
                if (exportPathParameters.getPathsPerInitiator() != null) {
                    pathParams.setPathsPerInitiator(exportPathParameters.getPathsPerInitiator());
                }
                if (exportPathParameters.getPortGroup() != null) {
                    URI pgURI = exportPathParameters.getPortGroup();
                    pathParams.setPortGroup(pgURI);
                    StoragePortGroup portGroup = _dbClient.queryObject(StoragePortGroup.class, pgURI);
                    if (portGroup != null) {
                        pathParams.setStoragePorts(portGroup.getStoragePorts());
                    }
                }
            }
            blockScheduler.assignStoragePorts(storageSystem,
                    varray, initiators, pathParams, null, volumes);
        } catch (ControllerException ex) {
            _log.error(ex.getLocalizedMessage());
            throw (ex);
        }
    }

    /**
     * Checks if any of the volume is vplex volume if yes diverts it to VpelxImpl
     * to validate vplex storage ports in varray.
     * Default impl does nothing for now for this check.
     *
     * @param storageSystemURIs
     * @param varray VirtualArray of ExportGroup
     * @param volumes Volumes that will be exported
     * @param allHosts All hosts of ExportGroup
     */
    private void validateVarrayStoragePorts(Collection<URI> storageSystemURIs,
            VirtualArray varray, Collection<URI> volumes, List<URI> allHosts) {

        Set<URI> vplexStorageSystemURIs = new HashSet<URI>();
        if (!allHosts.isEmpty()) {
            for (URI uri : volumes) {
                BlockObject blockObject = BlockObject.fetch(_dbClient, uri);
                if (blockObject == null) {
                    continue;
                }
                StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class,
                        blockObject.getStorageController());
                if (storageSystemURIs.contains(storageSystem.getId())
                        && storageSystem.getSystemType().equals(
                                DiscoveredDataObject.Type.vplex.name())) {
                    vplexStorageSystemURIs.add(storageSystem.getId());
                }
            }
        }
        ExportGroupServiceApi exportGroupServiceApi = null;
        if (!vplexStorageSystemURIs.isEmpty()) {
            exportGroupServiceApi = getExportGroupServiceImpl(DiscoveredDataObject.Type.vplex
                    .name());
        } else {
            exportGroupServiceApi = getExportGroupServiceImpl(ExportGroupServiceApi.DEFAULT);
        }
        exportGroupServiceApi.validateVarrayStoragePorts(vplexStorageSystemURIs, varray,
                allHosts);
    }

    /**
     * Checks if an initiator has connectivity to a storage system in a varray.
     *
     * @param storageSystems the storage systems where connectivity is needed
     * @param initiator the initiator
     * @return true if at least one port is found
     */
    private boolean hasConnectivityToAllSystems(
            Initiator initiator, Collection<URI> storageSystems,
            ExportGroup exportGroup) {
        return hasConnectivityToAllSystems(initiator, storageSystems, null, exportGroup);
    }

    /**
     * Checks if an initiator has connectivity to a storage system in a varray.
     *
     * @param storageSystems the storage systems where connectivity is needed
     * @param neighborhoodUri the varray of the storage volume
     * @param blockScheduler an instance of {@link BlockStorageScheduler}
     * @param initiator the initiator
     * @return true if at least one port is found
     */
    private boolean hasConnectivityToAllSystems(
            Initiator initiator, Collection<URI> storageSystems, List<URI> connectedStorageSystems,
            ExportGroup exportGroup) {
        boolean hasConnectivity = true;
        if (connectedStorageSystems != null) {
            connectedStorageSystems.addAll(storageSystems);
        }
        // we want to make sure the initiator can access each storage
        for (URI storage : storageSystems) {
            StorageSystem storageSystem = _dbClient.queryObject(
                    StorageSystem.class, storage);
            List<URI> varrays = ExportUtils.getVarraysForStorageSystemVolumes(exportGroup,
                    storage, _dbClient);
            // check the initiator has connectivity
            if (!hasConnectivityToSystem(storageSystem,
                    varrays, initiator)) {
                hasConnectivity = false;
                if (connectedStorageSystems != null) {
                    connectedStorageSystems.remove(storage);
                }
            }
        }
        return hasConnectivity;
    }

    /**
     * While creating an export group, a user has the choice of supplying
     * Host Lun Units corresponding to the volumes being added to the export
     * group. This function makes sure that either the user has not supplied any
     * HLUs (in which case the underlying storage device assigns them) or if the
     * user has supplied them, then (s)he has supplied HLUs for ALL volumes. *
     */
    private void validateVolumeLunIdParam(List<VolumeParam> volumes) {
        int numDeviceNumbers = 0;
        int volumeListSize = 0;
        if (volumes != null && !volumes.isEmpty()) {
            volumeListSize = volumes.size();
            for (VolumeParam volParam : volumes) {
                if (volParam.getLun() != ExportGroup.LUN_UNASSIGNED) {
                    numDeviceNumbers++;
                }
            }
        }
        if (numDeviceNumbers != 0 && numDeviceNumbers != volumeListSize) {
            throw APIException.badRequests.invalidVolumeParamsAllOrNoneShouldSpecifyLun(volumes);
        }
    }

    /**
     * Gets a list of ITLs for all the volumes and snapshots that are exported to a
     * list of initiators.
     * <p>
     * This function is not designed for a very large number of initiators and performance is likely to be unsatisfactory if thousands of
     * initiators are requested.
     *
     * @param initiatorPorts a comma-delimited list of initiators wwn or iqn.
     * @brief List ITLs of volumes and snapshots exported to a list of initiators
     * @return an object containing all ITLs for the initiators
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public ITLRestRepList getInitiatorsExports(@QueryParam("initiators") String initiatorPorts) {
        // make sure we have some initiators
        ArgValidator.checkFieldNotEmpty(initiatorPorts, "initiators");

        return com.emc.storageos.api.service.impl.resource.utils.ExportUtils
                .getInitiatorsItls(Arrays.asList(initiatorPorts.split(",")), _dbClient,
                        _permissionsHelper, getUserFromContext());
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<ExportGroup> getResourceClass() {
        return ExportGroup.class;
    }

    /**
     * Retrieve ExportGroup representations based on input ids.
     *
     * @return list of ExportGroup representations.
     */
    @Override
    public ExportGroupBulkRep queryBulkResourceReps(List<URI> ids) {

        Iterator<ExportGroup> _dbIterator = _dbClient.queryIterativeObjects(getResourceClass(), ids);

        BulkList<ExportGroupRestRep> list = new BulkList<ExportGroupRestRep>();
        list.setIterator(new ExportGroupRepIterator(
                _dbIterator, _dbClient));
        return new ExportGroupBulkRep(list);
    }

    @Override
    protected BulkRestRep queryFilteredBulkResourceReps(
            List<URI> ids) {

        Iterator<ExportGroup> _dbIterator = _dbClient.queryIterativeObjects(getResourceClass(), ids);

        BulkList<ExportGroupRestRep> filtered = new BulkList<ExportGroupRestRep>();
        filtered.setIterator(
                new ExportGroupRepIterator(
                        _dbIterator, _dbClient,
                        new BulkList.ProjectResourceFilter<ExportGroup>(
                                getUserFromContext(),
                                _permissionsHelper)));

        return new ExportGroupBulkRep(filtered);
    }

    /**
     * A special iterator to create ExportGroupRestRep elements from
     * an ExportGroup iterator
     *
     * ExportGroupRestRep can not use the generic iterator because it has
     * a different constructor which requires a ExportGroup object AND a dbclient.
     *
     */
    private class ExportGroupRepIterator
            implements Iterator<ExportGroupRestRep> {
        private final Iterator<ExportGroup> _dbIterator;
        private final ResourceFilter<ExportGroup> _filter;

        private ExportGroup _next = null;

        public ExportGroupRepIterator(Iterator<ExportGroup> dbIterator,
                DbClient dbClient) {
            _dbIterator = dbIterator;
            _dbClient = dbClient;
            _filter = new BulkList.ResourceFilter<ExportGroup>();
        }

        public ExportGroupRepIterator(Iterator<ExportGroup> dbIterator,
                DbClient dbClient, BulkList.ResourceFilter<ExportGroup> filter) {
            _dbIterator = dbIterator;
            _dbClient = dbClient;
            _filter = filter;
        }

        @Override
        public boolean hasNext() {
            if (null == _next) {
                while (_dbIterator.hasNext()) {
                    ExportGroup element = _dbIterator.next();
                    if (_filter.isExposed(element)) {
                        _next = element;
                        break;
                    }
                }
            }
            return _next != null;
        }

        @Override
        public ExportGroupRestRep next() {
            ExportGroup next = null;
            ExportGroupRestRep ret = null;

            if (_next != null) {
                next = _next;
            } else {
                if (hasNext()) {
                    next = _next;
                }
            }

            if (next != null) {
                ret = toExportResponse(next);
                _next = null;
            }
            return ret;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

    }

    /**
     * Simple wrapper that returns the BlockExportController proxy
     *
     * @return
     */
    BlockExportController getExportController() {
        return getController(BlockExportController.class, BlockExportController.EXPORT);
    }

    /**
     * Export group is not a zone level resource
     */
    @Override
    protected boolean isZoneLevelResource() {
        return false;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.EXPORT_GROUP;
    }

    /**
     * Get search results by name in zone or project.
     *
     * @return SearchedResRepList
     */
    @Override
    protected SearchedResRepList getNamedSearchResults(String name, URI projectId) {
        SearchedResRepList resRepList = new SearchedResRepList(getResourceType());
        if (projectId == null) {
            _dbClient.queryByConstraint(
                    PrefixConstraint.Factory.getLabelPrefixConstraint(getResourceClass(), name),
                    resRepList);
        } else {
            _dbClient.queryByConstraint(
                    ContainmentPrefixConstraint.Factory.getExportGroupUnderProjectConstraint(
                            projectId, name),
                    resRepList);
        }
        return resRepList;
    }

    /**
     * Get search results by project alone.
     *
     * @return SearchedResRepList
     */
    @Override
    protected SearchedResRepList getProjectSearchResults(URI projectId) {
        SearchedResRepList resRepList = new SearchedResRepList(getResourceType());
        _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getProjectExportGroupConstraint(projectId),
                resRepList);
        return resRepList;
    }

    /**
     * Additional search criteria for a export group.
     *
     * If a matching export group is not found, an empty list is returned.
     *
     * Parameters - host String - URI of the host
     * - cluster String - URI of the cluster
     * - initiator String - URI of the initiator
     * - self_only - Optional parameter to allow for specific type search, [true or false]
     */
    @Override
    protected SearchResults getOtherSearchResults(Map<String, List<String>> parameters, boolean authorized) {
        SearchResults result = new SearchResults();

        String[] searchCriteria = { SEARCH_HOST, SEARCH_CLUSTER, SEARCH_INITIATOR, SEARCH_LEVEL };

        validateSearchParameters(parameters, searchCriteria);

        boolean selfOnly = isSelfOnly(parameters, SEARCH_LEVEL);

        List<SearchResultResourceRep> resRepLists = new ArrayList<SearchResultResourceRep>();
        for (Map.Entry<String, List<String>> entry : parameters.entrySet()) {
            for (String searchValue : entry.getValue()) {
                if (entry.getKey().equals(SEARCH_HOST)) {
                    // Search for host export
                    searchForHostExport(searchValue, resRepLists, selfOnly, authorized);
                } else if (entry.getKey().equals(SEARCH_CLUSTER)) {
                    // search for cluster export
                    searchForClusterExport(searchValue, resRepLists, selfOnly, authorized);
                } else if (entry.getKey().equals(SEARCH_INITIATOR)) {
                    // search for initiator export
                    if (isInitiatorId(searchValue)) {
                        searchforInitiatorExport(searchValue, resRepLists, selfOnly, authorized);
                    } else {
                        searchforInitiatorExportByWWN(searchValue, resRepLists, selfOnly, authorized);
                    }
                }

                result.setResource(resRepLists);
            }
        }

        return result;
    }

    /**
     * Determine if string is a valid initiator id or wwn
     *
     * @param value to evaluate
     * @return true or false
     */
    private boolean isInitiatorId(String value) {
        URI initiatorUri = null;

        try {
            initiatorUri = URI.create(value);
        } catch (IllegalArgumentException e) {
            return false;
        }

        return URIUtil.isValid(initiatorUri);
    }

    /**
     * Validate if one param passed is valid
     *
     * @param params to evaluate
     * @param criterias that can be searched for
     * @return true of false
     */
    private boolean isValidSearch(Map<String, List<String>> params, String[] criterias) {
        for (String search : criterias) {
            if (params.containsKey(search)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Validate params
     *
     * @param params to evaluate
     * @param criterias that can be searched for
     */
    private void validateSearchParameters(Map<String, List<String>> params, String[] criterias) {
        String nonVolumeKey = null;
        boolean found = false;

        if (!isValidSearch(params, criterias)) {
            throw APIException.badRequests.invalidParameterSearchMissingParameter(getResourceClass().getName(), Arrays.toString(criterias));
        }

        // Make sure all parameters are our parameters, otherwise post an exception because we don't support other search criteria than our
        // own.
        for (Map.Entry<String, List<String>> entry : params.entrySet()) {
            found = false;
            for (String search : criterias) {
                if (entry.getKey().equals(search)) {
                    found = true;
                }
            }
            if (!found) {
                nonVolumeKey = entry.getKey();
            }
        }

        if (nonVolumeKey != null) {
            throw APIException.badRequests.parameterForSearchCouldNotBeCombinedWithAnyOtherParameter(getResourceClass().getName(),
                    Arrays.toString(criterias), nonVolumeKey);
        }
    }

    /**
     * Determine if searching for type specific or not
     *
     * @param params to evaluate
     * @param criteria the valid criteria
     * @return true of false
     */
    private boolean isSelfOnly(Map<String, List<String>> params, String criteria) {
        for (Map.Entry<String, List<String>> entry : params.entrySet()) {
            if (entry.getKey().equals(SEARCH_LEVEL)) {
                for (String searchValue : entry.getValue()) {
                    if (searchValue.equals("true")) {
                        return true;
                    } else if (searchValue.equals("false")) {
                        return false;
                    } else {
                        throw APIException.badRequests.invalidParameter(entry.getKey(), searchValue);
                    }
                }
            }
        }

        return false;
    }

    /**
     * Performs the search query based on the host Id.
     *
     * @param hostId the host Id to search
     * @param resRepLists search result are placed in this param
     * @param selfOnly true or false
     */
    private void searchForHostExport(String hostId, List<SearchResultResourceRep> resRepLists, boolean selfOnly, boolean authorized) {
        URIQueryResultList egUris = new URIQueryResultList();
        Set<URI> resultUris = new HashSet<URI>();
        List<ExportGroup> exportGroups = new ArrayList<ExportGroup>();

        if (selfOnly) {
            exportGroups = CustomQueryUtility.queryActiveResourcesByConstraint(
                    _dbClient, ExportGroup.class, AlternateIdConstraint.Factory
                            .getConstraint(ExportGroup.class, "hosts", hostId));
        } else {
            List<NamedElement> initiatorElements = getModelClient().initiators().findIdsByHost(URI.create(hostId));
            List<URI> initiators = toURIs(initiatorElements);
            for (URI iUri : initiators) {
                _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                        .getExportGroupInitiatorConstraint(iUri.toString()),
                        egUris);

                for (URI eUri : egUris) {
                    resultUris.add(eUri);
                }

            }

            exportGroups = _dbClient.queryObject(ExportGroup.class, resultUris, true);
        }

        buildExportGroupSearchResponse(exportGroups, resRepLists, selfOnly, ExportGroupType.Host.name(), authorized);
    }

    /**
     * Performs the search query based on the cluster Id.
     *
     * @param clusterId search param
     * @param resRepLists result
     * @param selfOnly true or false
     */
    private void searchForClusterExport(String clusterId, List<SearchResultResourceRep> resRepLists, boolean selfOnly, boolean authorized) {
        URIQueryResultList egUris = new URIQueryResultList();
        Set<URI> resultUris = new HashSet<URI>();
        List<ExportGroup> exportGroups = new ArrayList<ExportGroup>();

        if (selfOnly) {
            exportGroups = CustomQueryUtility.queryActiveResourcesByConstraint(
                    _dbClient, ExportGroup.class,
                    AlternateIdConstraint.Factory.getConstraint(
                            ExportGroup.class, "clusters", clusterId));
        } else {
            List<NamedElement> hostElements = getModelClient().hosts().findIdsByCluster(URI.create(clusterId));
            List<URI> hosts = toURIs(hostElements);
            for (URI hUri : hosts) {
                List<NamedElement> initiatorElements = getModelClient().initiators().findIdsByHost(hUri);
                List<URI> initiators = toURIs(initiatorElements);
                for (URI iUri : initiators) {
                    _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getExportGroupInitiatorConstraint(iUri.toString()), egUris);

                    for (URI eUri : egUris) {
                        resultUris.add(eUri);
                    }
                }
            }

            exportGroups = _dbClient.queryObject(ExportGroup.class, resultUris, true);
        }

        buildExportGroupSearchResponse(exportGroups, resRepLists, selfOnly, ExportGroupType.Cluster.name(), authorized);
    }

    /**
     * Performs the search query based on the initiator Id.
     *
     * @param initiatorId search param
     * @param resRepLists result
     * @param selfOnly true or false
     */
    private void searchforInitiatorExport(String initiatorId, List<SearchResultResourceRep> resRepLists, boolean selfOnly,
            boolean authorized) {
        URIQueryResultList egUris = new URIQueryResultList();
        Set<URI> resultUris = new HashSet<URI>();
        List<ExportGroup> exportGroups = new ArrayList<ExportGroup>();

        if (selfOnly) {
            exportGroups = CustomQueryUtility.queryActiveResourcesByConstraint(
                    _dbClient, ExportGroup.class, AlternateIdConstraint.Factory
                            .getConstraint(ExportGroup.class, "initiators", initiatorId));
        } else {
            _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getExportGroupInitiatorConstraint(initiatorId.toString()), egUris);

            for (URI eUri : egUris) {
                resultUris.add(eUri);
            }

            exportGroups = _dbClient.queryObject(ExportGroup.class, resultUris, true);
        }

        buildExportGroupSearchResponse(exportGroups, resRepLists, selfOnly, ExportGroupType.Initiator.name(), authorized);
    }

    /**
     * Performs the search query based on the initiator wwn. Will find the initiator associated with the passed
     * wwn in order to do the a search by initiator id.
     *
     * @param wwn search param
     * @param resRepLists result
     * @param selfOnly true or false
     */
    private void searchforInitiatorExportByWWN(String wwn, List<SearchResultResourceRep> resRepLists, boolean selfOnly,
            boolean authorized) {
        URIQueryResultList initiatorList = new URIQueryResultList();

        // find the initiator
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getInitiatorPortInitiatorConstraint(
                wwn), initiatorList);

        for (URI iUri : initiatorList) {
            searchforInitiatorExport(iUri.toString(), resRepLists, selfOnly, authorized);
        }
    }

    /**
     * Change elements to URI
     *
     * @param namedElements to change to URI
     * @return list of URI
     */
    private List<URI> toURIs(List<NamedElement> namedElements) {
        List<URI> out = Lists.newArrayList();
        if (namedElements != null) {
            for (NamedElement namedElement : namedElements) {
                out.add(namedElement.getId());
            }
        }
        return out;
    }

    /**
     * Build search response based on query result.
     *
     * @param exportGroups from query
     * @param resRepLists result
     * @param selfOnly true or false
     * @param type Cluster, Host or Initiator
     */
    private void buildExportGroupSearchResponse(List<ExportGroup> exportGroups, List<SearchResultResourceRep> resRepLists,
            boolean selfOnly, String type, boolean authorized) {
        PermissionsEnforcingResourceFilter<ExportGroup> filter = new ExportGroupSearchFilter(getUserFromContext(), _permissionsHelper);

        for (ExportGroup eg : exportGroups) {
            if (!authorized && !filter.isExposed(eg)) {
                continue; // authorization failed, don't add to search result
            }

            if (selfOnly) {
                if (!eg.getType().equals(type)) {
                    continue; // match invalid, only return matching types, process next
                }
            }

            RestLinkRep selfLink = new RestLinkRep("self", RestLinkFactory.newLink(getResourceType(), eg.getId()));
            SearchResultResourceRep searchResult = new SearchResultResourceRep(eg.getId(), selfLink, eg.getLabel());
            resRepLists.add(searchResult);
        }
    }

    public static class ExportGroupSearchFilter extends PermissionsEnforcingResourceFilter<ExportGroup> {

        public ExportGroupSearchFilter(StorageOSUser user,
                PermissionsHelper permissionsHelper) {
            super(user, permissionsHelper);
        }

        @Override
        public boolean isAccessible(ExportGroup resource) {
            boolean ret = false;

            if (resource == null) {
                return false;
            }
            ret = isTenantAccessible(resource.getTenant().getURI());
            if (!ret) {
                NamedURI proj = resource.getProject();
                if (proj != null) {
                    ret = isProjectAccessible(proj.getURI());
                }
            }
            return ret;
        }
    }

    /**
     * Get object specific permissions filter
     *
     */
    @Override
    public ResRepFilter<? extends RelatedResourceRep> getPermissionFilter(StorageOSUser user,
            PermissionsHelper permissionsHelper) {
        return new ProjOwnedResRepFilter(user, permissionsHelper, ExportGroup.class);
    }

    /**
     * Validates that we are not creating an ExportGroup of with a duplicate name
     * in the same project and varray. This is used to detect collisions where doing
     * concurrent exports from the UI.
     *
     * @param param
     */
    private void validateNotSameNameProjectAndVarray(ExportCreateParam param) {
        URIQueryResultList exportGroupURIList = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getProjectExportGroupConstraint(param.getProject()), exportGroupURIList);
        Iterator<URI> exportGroupURIIterator = exportGroupURIList.iterator();
        while (exportGroupURIIterator.hasNext()) {
            ExportGroup eg = _dbClient.queryObject(ExportGroup.class, exportGroupURIIterator.next());
            if ((null != eg) && eg.getLabel().equals(param.getName()) && eg.getVirtualArray().equals(param.getVarray())) {
                throw APIException.badRequests.duplicateExportGroupProjectAndVarray(param.getName());
            }
        }
    }

    /**
     * Validate that the update is not attempting to add/remove VPLEX backend volumes to/from a group.
     *
     * @param param [IN] - ExportUpdateParam holds the update request parameters
     * @param exportGroup [IN] - ExportGroup to update
     */
    private void validateUpdateIsNotForVPlexBackendVolumes(ExportUpdateParam param, ExportGroup exportGroup) {
        if (param.getVolumes() != null) {
            if (param.getVolumes().getAdd() != null) {
                if (param.getVolumes().getAdd().size() > MAX_VOLUME_COUNT) {
                    throw APIException.badRequests.exceedingLimit("count",
                            MAX_VOLUME_COUNT);
                }
                List<URI> addVolumeURIs = new ArrayList<URI>();
                for (VolumeParam volParam : param.getVolumes().getAdd()) {
                    addVolumeURIs.add(volParam.getId());
                }
                BlockService.validateNoInternalBlockObjects(_dbClient, addVolumeURIs, false);
            }

            if (param.getVolumes().getRemove() != null && param.getVolumes().getRemove().size() > MAX_VOLUME_COUNT) {
                throw APIException.badRequests.exceedingLimit("count", MAX_VOLUME_COUNT);
            }
            BlockService.validateNoInternalBlockObjects(_dbClient, param.getVolumes().getRemove(), false);
        }
    }

    /**
     * Validate path parameters are valid for the ExportGroup.
     * 
     * @param URI for the ExportPathParameters representing the ExportPathPolicy that was submitted
     * @param param -- ExportPathParameters block
     * @param exportGroup -- ExportGroup
     * @param blockObjectURIs -- Collection of block object URIs, used only for validating ports
     * @param warningMessage -- StringBuilder holder for warning messages
     * @return ExportPathParam suitable for persistence in the ExportGroup pathParameter map.
     * Can return null if no parameters were available.
     */
    ExportPathParams validateAndCreateExportPathParam(URI exportPathPolicyURI, ExportPathParameters param,
            ExportGroup exportGroup, Collection<URI> blockObjectURIs, StringBuilder warningMessages) {
        // IF no policy or explicit parameters, nothing to do, return null
        if (exportPathPolicyURI == null && param == null) {
            return null;
        }
        // IF a URI containing the ExportPathParams (normally from an ExportPathPolicyService record,
        // then look up that set of parameters.
        ExportPathParams policy = null;
        if (exportPathPolicyURI != null) {
            ArgValidator.checkUri(exportPathPolicyURI);
            policy = _dbClient.queryObject(ExportPathParams.class, exportPathPolicyURI);
            ArgValidator.checkEntityNotNull(policy, exportPathPolicyURI, false);
        }
        ExportPathParams pathParam = (policy != null) 
                ? new ExportPathParams(policy, false, "Created from path policy: " + policy.getLabel())
                : new ExportPathParams();
        pathParam.setId(URIUtil.createId(ExportPathParams.class));
        pathParam.setLabel(exportGroup.getLabel());
        pathParam.setExplicitlyCreated(false);

        // If minPaths is specified, or pathsPerInitiator is specified, maxPaths must be specified
        if (param != null && (param.getMinPaths() != null || param.getPathsPerInitiator() != null) && param.getMaxPaths() == null) {
            throw APIException.badRequests.maxPathsRequired();
        }
        
        if (param != null && param.getMaxPaths() != null) {
            ArgValidator.checkFieldMinimum(param.getMaxPaths(), 1, "max_paths");
            ArgValidator.checkFieldNotNull(param.getMinPaths(), "min_paths");
            ArgValidator.checkFieldNotNull(param.getPathsPerInitiator(), "paths_per_initiator");
            
            if (param.getMinPaths() != null) {
                ArgValidator.checkFieldMinimum(param.getMinPaths(), 1, "min_paths");
            } 
            if (param.getPathsPerInitiator() != null) {
                ArgValidator.checkFieldMinimum(param.getPathsPerInitiator(), 1, "paths_per_initiator");
            } 
            // minPaths must be <= than maxPaths.
            if (param.getMinPaths() != null && param.getMinPaths() > param.getMaxPaths()) {
                throw APIException.badRequests.minPathsGreaterThanMaxPaths();
            }
            // pathsPerInitiator must be <= maxPaths.
            if (param.getPathsPerInitiator() != null && param.getPathsPerInitiator() > param.getMaxPaths()) {
                throw APIException.badRequests.pathsPerInitiatorGreaterThanMaxPaths();
            }
            pathParam.setMaxPaths(param.getMaxPaths());
            if (param.getMinPaths() != null) {
                pathParam.setMinPaths(param.getMinPaths());
            }
            if (param.getPathsPerInitiator() != null) {
                pathParam.setPathsPerInitiator(param.getPathsPerInitiator());
            }
            if (param.getStoragePorts() != null) {
                pathParam.setStoragePorts(StringSetUtil.uriListToStringSet(param.getStoragePorts()));
            }
        }
        
        // Collect the list of Storage Systems used by the block objects.
        Set<URI> storageArrays = new HashSet<URI>();
        for (URI blockObjectURI : blockObjectURIs) {
            BlockObject blockObject = BlockObject.fetch(_dbClient, blockObjectURI);
            if (blockObject == null) {
                continue;
            }
            storageArrays.add(blockObject.getStorageController());
        }

        // validate storage ports if they are supplied
        validateExportPathParmPorts(pathParam, exportGroup, storageArrays);
        // Validate there are no existing exports for the hosts involved that we could not override.
        // If there are, issue task warning message (no longer a failure.)
        validateNoConflictingExports(exportGroup, storageArrays, pathParam, warningMessages);

        if (param != null && !NullColumnValueGetter.isNullURI(param.getPortGroup())) {
            // Check if the use port group config setting is on
            String value = customConfigHandler.getComputedCustomConfigValue(CustomConfigConstants.VMAX_USE_PORT_GROUP_ENABLED,
                    Type.vmax.name(), null);
            if (Boolean.FALSE.toString().equalsIgnoreCase(value)) {
                throw APIException.badRequests.portGroupSettingIsOff();
            }
            URI pgURI = param.getPortGroup();
            ArgValidator.checkFieldUriType(pgURI, StoragePortGroup.class, "portGroupId");
            StoragePortGroup portGroup = _dbClient.queryObject(StoragePortGroup.class, pgURI);
            if (portGroup == null || !portGroup.isUsable() ) {
                throw APIException.badRequests.portGroupInvalid(pgURI.toString());
            }
            pathParam.setPortGroup(pgURI);
        }
        return pathParam;
    }

    /**
     * Throw an error if we cannot override the Vpool path parameters because there is already
     * an existing export from the indicated host(s) to storage array(s).
     *
     * @param exportGroup
     * @param arrayURIs
     * @param pathParam -- New ExportPathParams to be used
     * @param warningMessages -- StringBuilder
     */
    private void validateNoConflictingExports(ExportGroup exportGroup, Set<URI> arrayURIs,
            ExportPathParams pathParam, StringBuilder warningMessages) {
        if (pathParam.getMaxPaths() == null || pathParam.getMinPaths() == null || pathParam.getPathsPerInitiator() == null) {
           _log.info("No path parameters specified... skip conflicting exports validation");
           return;
        }
        _log.info("Requested path parameters: " + pathParam.toString());
        Map<String, String> conflictingMasks = new HashMap<String, String>();
        StringSet initiators = exportGroup.getInitiators();
        if (initiators == null) {
            // No initiators currently in export, nothing to do
            return;
        }
        for (String initiatorId : initiators) {
            Initiator initiator = _dbClient.queryObject(Initiator.class, URI.create(initiatorId));
            if (initiator == null || initiator.getInactive()) {
                continue;
            }
            // Look up all the Export Masks for this Initiator
            List<ExportMask> exportMasks =
                    ExportUtils.getInitiatorExportMasks(initiator, _dbClient);
            
            for (ExportMask exportMask : exportMasks) {
                // If this mask is for the same Host and Storage combination, we cannot override
                if (arrayURIs.contains(exportMask.getStorageDevice())) {
                    ExportPathParams maskParam =
                            BlockStorageScheduler.calculateExportPathParamForExportMask(_dbClient, exportMask);
                    _log.info(String.format("Existing mask %s (%s) parameters: %s",
                            exportMask.getMaskName(), exportMask.getId(), maskParam));
                    // Determine if the mask is compatible with the requested parameters or not.
                    // To be compatible, the mask must have the same paths_per_initiator setting, and
                    // its max paths must be between the requested min paths and max paths.
                    // i.e. maskParams.ppi = pathParms.ppi and pathParams.minPath <= maskParams.maxpath <= pathParams.maxPath
                    if (pathParam.getPathsPerInitiator() == maskParam.getPathsPerInitiator()
                            && (pathParam.getMinPaths() <= maskParam.getMaxPaths()
                            && maskParam.getMaxPaths() <= pathParam.getMaxPaths())) {
                        _log.info(String.format("Export mask %s is compatible with the requested parameters",
                                exportMask.getMaskName()));
                    } else {
                        StorageSystem system = _dbClient.queryObject(StorageSystem.class, exportMask.getStorageDevice());
                        String hostName = (initiator.getHostName() != null) ? initiator.getHostName() : initiatorId;
                        String systemName = (system != null) ? system.getLabel() : exportMask.getStorageDevice().toString();
                        if (!conflictingMasks.containsKey(hostName)) {
                            String msg = String.format(
                                    "Export Mask %s for Host %s and Array %s has %d paths and paths_per_initiator %d. ",
                                    exportMask.getMaskName(), hostName, systemName, maskParam.getMaxPaths(),
                                    maskParam.getPathsPerInitiator());
                            conflictingMasks.put(hostName, msg);
                        }
                    }
                }
            }
        }
        if (!conflictingMasks.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            for (Entry<String, String> entry : conflictingMasks.entrySet()) {
                if (builder.length() != 0) {
                    builder.append("; ");
                }
                builder.append(entry.getValue());
            }
            warningMessages.append(APIException.badRequests.cannotChangePathsBecauseExistingExports(builder.toString()).getMessage());
        }
    }
    
    /**
     * Add a task warning message if appropriate
     * @param taskRes -- the TaskResourceRep containing the task
     * @param warningMessage -- String warning message (skipped if null or empty)
     */
    public void addTaskWarningMessage(TaskResourceRep taskRes, String warningMessage) {
            if (warningMessage == null || warningMessage.isEmpty()) {
                return;
            }
            URI id = taskRes.getId();
            Task task = _dbClient.queryObject(Task.class, id);
            if (task != null) {
                task.addWarningMessage(warningMessage);
                _dbClient.updateObject(task);
            }
    }

    /**
     * Validate that if ports are supplied in the ExportPathParameters, then ports are supplied
     * for every array in the list of volumes to be provisioned. Also verify
     * the ports can be located, and there are at least as many ports as maxPaths.
     *
     * @param param ExportPathParams block
     * @param exportGroup
     * @param StorageArrays Collection<URI> Arrays that will be used for the Exports
     */
    private void validateExportPathParmPorts(ExportPathParams param, ExportGroup exportGroup,
            Collection<URI> storageArrays) {
        if (param.getClass() == null || param.getStoragePorts() == null || param.getStoragePorts().isEmpty()) {
            return;
        }
        // Get database entries for all the ports in a map of array URI to set of StoragePort.
        Map<URI, Set<StoragePort>> arrayToStoragePorts = new HashMap<URI, Set<StoragePort>>();
        for (String portURI : param.getStoragePorts()) {
            StoragePort port = _dbClient.queryObject(StoragePort.class, URI.create(portURI));
            ArgValidator.checkEntityNotNull(port, URI.create(portURI), false);
            URI arrayURI = port.getStorageDevice();
            if (!arrayToStoragePorts.containsKey(arrayURI)) {
                arrayToStoragePorts.put(arrayURI, new HashSet<StoragePort>());
            }
            arrayToStoragePorts.get(arrayURI).add(port);
        }
        // Check that there are entries for all the arrays used by the volumes.
        for (URI storageArray : storageArrays) {
            if (!arrayToStoragePorts.containsKey(storageArray)) {
                throw APIException.badRequests.pathParameterPortsDoNotIncludeArray(storageArray);
            }
        }
        // Now check that each array has at least maxpaths number of ports supplied.
        for (Map.Entry<URI, Set<StoragePort>> entry : arrayToStoragePorts.entrySet()) {
            if (entry.getValue().size() < param.getMaxPaths()) {
                throw APIException.badRequests.notEnoughPortsForMaxpath(entry.getKey(), entry.getValue().size(), param.getMaxPaths());
            }
        }
    }

    /**
     * For all the block objects in the list, checks to see if they are associated with an ExportPathParam object.
     * If so they are removed. If there are no remaining block objects, the ExportPathParam is deleted.
     *
     * @param blockObjectURIs
     * @param exportGroup
     */
    void removeBlockObjectsFromPathParamMap(Collection<URI> blockObjectURIs, ExportGroup exportGroup) {
        // For each BlockObject, remove its association to a ExportPathParam.
        for (URI blockObjectURI : blockObjectURIs) {
            String pathParamId = exportGroup.getPathParameters().get(blockObjectURI.toString());
            if (pathParamId == null)
                continue;
            exportGroup.removeFromPathParameters(blockObjectURI);
            // If there are no more entries for the given ExportPathParam, mark it for deletion
            if (!exportGroup.getPathParameters().containsValue(pathParamId)) {
                URI pathParamURI = URI.create(pathParamId);
                ExportPathParams pathParam = _dbClient.queryObject(ExportPathParams.class, pathParamURI);
                if (pathParam != null) {
                    _dbClient.markForDeletion(pathParam);
                }
            }
        }
    }

    /**
     * Adds all the listed block objects to the specified export path parameters.
     *
     * @param blockObjectURIs
     * @param pathParamURI
     * @param exportGroup
     */
    void addBlockObjectsToPathParamMap(Collection<URI> blockObjectURIs, URI pathParamURI, ExportGroup exportGroup) {
        // Remove the block objects from any existing path parameters. Having duplicates is not allowed.
        removeBlockObjectsFromPathParamMap(blockObjectURIs, exportGroup);
        // Add them for the new ExportPathParam object.
        for (URI blockObjectURI : blockObjectURIs) {
            exportGroup.addToPathParameters(blockObjectURI, pathParamURI);
        }
    }

    /**
     * Verify that none of the volumes in the export group are mounted.
     * Unexporting a mounted volume is dangerous and should be avoided.
     * 
     * @param exportGroup
     *            export group
     * @param boURIList
     *            URI list of block objects
     */
    private void validateVolumesNotMounted(ExportGroup exportGroup, List<URI> boURIList) {
        if (exportGroup == null) {
            throw APIException.badRequests.exportGroupContainsMountedVolumesInvalidParam();
        }

        Map<URI, String> boToLabelMap = new HashMap<>();
        // It is valid for there to be no storage volumes in the EG, so only perform the check if there are volumes
        if (boURIList != null) {
            for (URI boID : boURIList) {
                BlockObject bo = BlockObject.fetch(_dbClient, boID);
                if (bo != null && bo.getTag() != null) {
                    ScopedLabelSet tagSet = bo.getTag();
                    Iterator<ScopedLabel> tagIter = tagSet.iterator();
                    while (tagIter.hasNext()) {
                        ScopedLabel sl = tagIter.next();
                        if (sl.getLabel() != null && (sl.getLabel().startsWith(MOUNTPOINT) || sl.getLabel().startsWith(VMFS_DATASTORE))) {

                            if (exportGroup.getClusters() != null) {
                                for (String clusterID : exportGroup.getClusters()) {
                                    if (sl.getLabel().contains(clusterID)) {
                                        boToLabelMap.put(boID, bo.forDisplay());
                                    }
                                }
                            }

                            if (exportGroup.getHosts() != null) {
                                for (String hostID : exportGroup.getHosts()) {
                                    if (sl.getLabel().contains(hostID)) {
                                        boToLabelMap.put(boID, bo.forDisplay());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!boToLabelMap.isEmpty()) {
            _log.error(
                    "Export Group {} has volumes {} that are marked as mounted.  It is recommended to unmount via controller before unexport.  This validation check can be disabled if needed.  Contact EMC Support.",
                    exportGroup.getId(), Joiner.on(",").join(boToLabelMap.values()));
            ValidatorConfig vc = new ValidatorConfig();
            vc.setCoordinator(_coordinator);
            if (vc.isValidationEnabled()) {
                throw APIException.badRequests.exportGroupContainsMountedVolumes(exportGroup.getId(),
                    Joiner.on(",").join(boToLabelMap.values()));
            }
        }
    }
    
    /**
     * Paths Adjustment Preview
     * 
     * This call does a PORT Allocation which is used for a path adjustment preview operation.
     * If the user is satisfied with the ports that are selected, they will invoke the provisioning through a
     * separate API.
     *
     * Inputs are the the Export Group URI, Storage System URI, an optional Varray URI, and the ExportPath parameters.
     * There is also a boolean that specifies the allocation should start assuming the existing paths are used (or not).
     *
     * @param id the URN of a ViPR export group to be updated; ports will be allocated in this context
     * @param param -- ExportPortAllocateParam block containing Storage System URI, Varray URI, ExportPathParameters
     * 
     * @brief Preview port allocation paths for export
     * @return a PortAllocatePreviewRestRep rest response which contains the new or existing paths that will be provisioned
     * or kept; the removed paths; and any other Export Groups that will be affected.
     * @throws ControllerException
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/paths-adjustment-preview")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public ExportPathsAdjustmentPreviewRestRep pathsAdjustmentPreview(@PathParam("id") URI id, 
            ExportPathsAdjustmentPreviewParam param)  throws ControllerException {
         // Basic validation of ExportGroup and update request
        ExportGroup exportGroup = queryObject(ExportGroup.class, id, true);
        if (exportGroup.checkInternalFlags(DataObject.Flag.DELETION_IN_PROGRESS)) {
            throw BadRequestException.badRequests.deletionInProgress(
                    exportGroup.getClass().getSimpleName(), exportGroup.getLabel());
        }
        
        validateExportGroupNoPendingEvents(exportGroup);        
        validateHostsInExportGroup(exportGroup, param.getHosts());
        
        // Validate storage system 
        ArgValidator.checkUri(param.getStorageSystem());
        StorageSystem system = queryObject(StorageSystem.class, param.getStorageSystem(), true);
        
        // Get the virtual array, default to Export Group varray. Validate it matches.
        URI varray = param.getVirtualArray();
        if (varray != null) {
           boolean validVarray = varray.equals(exportGroup.getVirtualArray());
           if (exportGroup.getAltVirtualArrays() != null 
                   && varray.toString().equals(exportGroup.getAltVirtualArrays().get(system.getId().toString()))) {
               validVarray = true;
           }
           if (!validVarray) {
               throw APIException.badRequests.varrayNotInExportGroup(varray.toString());
           }
        } else {    
            varray = exportGroup.getVirtualArray();
        }
        
        // Validate the hosts, if supplied.
        for (URI hostId : param.getHosts()) {
            // Throw exception of thoe hostId is invalid
            queryObject(Host.class, hostId, true);
        }
        
        // Get the initiators and validate the ExportMasks are usable.
        ExportPathsAdjustmentPreviewRestRep response = new ExportPathsAdjustmentPreviewRestRep();
        String storageSystem = system.getNativeGuid();
        if (Type.vplex.equals(Type.valueOf(system.getSystemType()))) {
            String vplexCluster = ConnectivityUtil.getVplexClusterForVarray(varray, system.getId(), _dbClient);
            storageSystem = storageSystem + STORAGE_SYSTEM_CLUSTER + vplexCluster;
        }
        response.setStorageSystem(storageSystem);

        List<Initiator> initiators = getInitiators(exportGroup);
        StringSetMap existingPathMap = new StringSetMap();
        StringSet storagePorts = new StringSet();
        validatePathAdjustment(exportGroup, initiators, system, varray, param.getHosts(), response, existingPathMap,
                param.getUseExistingPaths(), storagePorts);
        
        try {
            // Manufacture an ExportPathParams structure from the REST ExportPathParameters structure
            ExportPathParams pathParam = new ExportPathParams(param.getExportPathParameters(), exportGroup);
            if (!storagePorts.isEmpty()) {
                //check if storage ports are specified
                StringSet paramPorts = pathParam.getStoragePorts();
                if (paramPorts != null && !paramPorts.isEmpty()) {
                    if (!storagePorts.containsAll(paramPorts)) {
                        _log.error("Selected ports are not in the port group");
                        throw APIException.badRequests.pathAdjustmentSelectedPortsNotInPortGroup(
                                Joiner.on(',').join(paramPorts), Joiner.on(',').join(storagePorts));
                    } 
                } else {
                    pathParam.setStoragePorts(storagePorts);
                }
            }
            // Call the storage port allocator/assigner for all initiators (host or cluster) in
            // the Export Group. Pass in the existing paths if requested in the parameters.
            List<URI> volumes = StringSetUtil.stringSetToUriList(exportGroup.getVolumes().keySet());
            Map<URI, List<URI>>  zoningMap = _blockStorageScheduler.assignStoragePorts(
                    system, varray, initiators, pathParam, 
                    (param.getUseExistingPaths() ? existingPathMap : new StringSetMap()), volumes);
           
            for (Entry<URI, List<URI>> entry : zoningMap.entrySet()) {
                InitiatorPortMapRestRep zone = new InitiatorPortMapRestRep();
                Initiator initiator = queryObject(Initiator.class, entry.getKey(), false);
                zone.setInitiator(HostMapper.map(initiator));
                for (URI portURI : entry.getValue()) {
                    StoragePort port = queryObject(StoragePort.class, portURI, false);
                    zone.getStoragePorts().add(toNamedRelatedResource(port, port.getPortName()));
                }
                response.getAdjustedPaths().add(zone);
            }
            addRetainedPathsFromHosts(response, existingPathMap, param.getHosts());
            Collections.sort(response.getAdjustedPaths(), response.new InitiatorPortMapRestRepComparator());
            calculateRemovedPaths(response, zoningMap, exportGroup, system, varray, param.getHosts());
            response.logResponse(_log);
            return response;
        } catch (ControllerException ex) {
            _log.error(ex.getLocalizedMessage());
            throw (ex);
        }
    }
    
    /**
     * Add the existing paths from hosts that will not be provisioned into the rest response.
     * @param response -- The ExportPathsAdjustmentPreviewResponse being constructed
     * @param existingPathMap -- A map of initiator URI string to a set of port URI strings representing existing paths.
     * @param hosts --Set of host URIs
     */
    private void addRetainedPathsFromHosts(ExportPathsAdjustmentPreviewRestRep response, StringSetMap existingPathMap, Set<URI> hosts) {
        // If there are not specified hosts, then we do not need to add any paths for unprovisioned hosts since
        // all hosts will be provisioned.
        if (hosts.isEmpty()) {
            return;
        }
        // Look at each initiator in the existing paths map, and if it is not for one of
        // the hosts that is to be provisioned (specified in hosts), then add in the
        // existing paths for the initiator.
        for (String initiatorId : existingPathMap.keySet()) {
            Initiator initiator = _dbClient.queryObject(Initiator.class, URI.create(initiatorId));
            if (initiator == null || initiator.getInactive() || hosts.contains(initiator.getHost())) {
                continue;
            }
            InitiatorPortMapRestRep zone = new InitiatorPortMapRestRep();
            zone.setInitiator(HostMapper.map(initiator));
            for (String portId : existingPathMap.get(initiatorId)) {
                StoragePort port = queryObject(StoragePort.class, URI.create(portId), false);
                zone.getStoragePorts().add(toNamedRelatedResource(port, port.getPortName()));
            }
            response.getAdjustedPaths().add(zone);
        }
    }

    /**
     * Traverse the list of ExportMasks looking for StorageSystem matches and calculate the removed paths.
     * This is done by comparing the zoningMap containing the new paths with each ExportMasks's zoningMap,
     * and reporting eny paths in the EM's zoningMap that are not in the new zoningMap.
     * @param response -- OUT REST response where we are filling in removed paths
     * @param calculatedZoningMap -- new proposed zoningMap (overall for all ExportMasks)
     * @param exportGroup -- Export Group on which provisioning was initiated
     * @param system -- StorageSystem
     * @param varray -- Virtual Array URI
     * @param hosts -- For cluster export group, the hosts that are being processed. Assumes all if emptySet.
     */
    private void calculateRemovedPaths(ExportPathsAdjustmentPreviewRestRep response, Map<URI, List<URI>> calculatedZoningMap, 
            ExportGroup exportGroup, StorageSystem system, URI varray, Set<URI> hosts) {
        // Find the Export Masks for this Storage System 
        List<ExportMask> exportMasks = ExportMaskUtils.getExportMasks(_dbClient, exportGroup, system.getId());
        for (ExportMask exportMask : exportMasks) {
            // For VPLEX, must verify the Export Mask is in the appropriate Varray
            if (!ExportMaskUtils.exportMaskInVarray(_dbClient,  exportMask,  varray)) {
                continue;
            }
           
            // Get the zoning map from the exportMask, and compare it with the zoningMap newly allocated.
            // Remove all entries that are in the new zoningMap (and thus will be kept and not deleted)
            StringSetMap existingZoningMap = exportMask.getZoningMap();
            if (existingZoningMap == null || existingZoningMap.isEmpty()) {
                continue;
            }
            for (String maskInitiator : existingZoningMap.keySet()) {
                for (URI zoningInitiator : calculatedZoningMap.keySet()) {
                    if (maskInitiator.equalsIgnoreCase(zoningInitiator.toString())) {
                        // same initiator, remove ports from mask set that are in zoning map
                        StringSet maskPorts = existingZoningMap.get(maskInitiator);
                        for (URI zoningPort : calculatedZoningMap.get(zoningInitiator)) {
                            maskPorts.remove(zoningPort.toString());
                        }
                        existingZoningMap.put(maskInitiator, maskPorts);
                    }
                }
            }
            // Now output the response information for this Export Mask to the removedPaths section.
            for (String maskInitiator : existingZoningMap.keySet()) {
                StringSet maskPorts = existingZoningMap.get(maskInitiator);
                if (maskPorts == null || maskPorts.isEmpty()) {
                    continue;
                }
                InitiatorPortMapRestRep zone = new InitiatorPortMapRestRep();
                Initiator initiator = queryObject(Initiator.class, URI.create(maskInitiator), false);
                if (exportGroup.getType().equals(ExportGroupType.Cluster.name())
                        && !hosts.isEmpty() && !hosts.contains(initiator.getHost())) {
                    // If a Cluster export and not in the hosts list, don't process any removes for the initiator
                    continue;
                }
                zone.setInitiator(HostMapper.map(initiator));
                for (String maskPort : maskPorts) {
                    StoragePort port = queryObject(StoragePort.class, URI.create(maskPort), false);
                    zone.getStoragePorts().add(toNamedRelatedResource(port, port.getPortName()));
                }
                response.getRemovedPaths().add(zone);
            }
            Collections.sort(response.getRemovedPaths(), response.new InitiatorPortMapRestRepComparator());
        }
    }
    
    /**
     * Validates the ExportMask configuration for a port allocation preview.
     * Throws bad request exception if any ExportMask for the Storage System has existing initiators
     * or initiators that are not in the ExportGroup. If a set of hosts to be processed is specified
     * for a Cluster, removes any initiators not associated with those hosts.
     * If the port group used in the export mask is not mutable, storage ports that could be allocated have to 
     * be from the port group. Those storage ports will be set in storagePorts.
     * @param group -- ExportGroup
     * @param initiators -- List of initiators from the Export Group
     * @param system - Storage System
     * @param varray - Virtual array
     * @param hosts -- if ExportGroup is type CLUSTER, filter initiators by supplied hosts if any
     * @param response - OUT PortAllocationPreviewRestRep used to hold list of other affected Export Groups
     * @param existingPaths -- OUT parameter that is populated with existing paths
     * @param storagePorts - OUT Available storage ports to be allocated. It would only be set if port group 
     *                           used in the export mask is not mutable, and it is set to the storage ports 
     *                           in the port group.
     */
    private void validatePathAdjustment(ExportGroup exportGroup, List<Initiator> initiators, 
            StorageSystem system, URI varray, Set<URI> hosts,
            ExportPathsAdjustmentPreviewRestRep response, StringSetMap existingPaths,
            boolean useExistingPaths, StringSet storagePorts) {
        Set<URI> affectedGroupURIs = new HashSet<URI>();
        // Add our Export Group to the affected resources.
        affectedGroupURIs.add(exportGroup.getId());
        response.getAffectedExportGroups().add(toNamedRelatedResource(exportGroup, exportGroup.getLabel()));
        
        if (!Type.vmax.name().equalsIgnoreCase(system.getSystemType()) &&
                !Type.vplex.name().equalsIgnoreCase(system.getSystemType())) {
                throw APIException.badRequests.exportPathAdjustmentSystemNotSupported(system.getSystemType()); 
        }
        
        List<ExportMask> exportMasks = ExportMaskUtils.getExportMasks(_dbClient,  exportGroup, system.getId());
        if (exportMasks.isEmpty()) {
            throw APIException.badRequests.exportPathAdjustmentSystemExportGroupNotMatch(exportGroup.getLabel(), 
            			system.getNativeGuid());
        }
        
        // Make a map of Export Group initiator URI to Initiator Object
        Map<URI, Initiator> initiatorMap = new HashMap<URI, Initiator>();
        List<Initiator> initiatorsToRemove = new ArrayList<Initiator>();
        for (Initiator initiator : initiators) {
            if (!exportGroup.getType().equals(ExportGroupType.Cluster.name()) 
                    || hosts.isEmpty() || hosts.contains(initiator.getHost())) {
                initiatorMap.put(initiator.getId(), initiator);
            } else {
                // Initiator did not match the specified hosts
                initiatorsToRemove.add(initiator);
            }
        }
        // Remove any initiators not retained because of hosts specification
        initiators.removeAll(initiatorsToRemove);
        // Find the Export Masks for this Storage System 
        for (ExportMask exportMask : exportMasks) {
            // For VPLEX, must verify the Export Mask is in the appropriate Varray
            if (system.getSystemType().equalsIgnoreCase(StorageSystem.Type.vplex.name())) {
                if (!ExportMaskUtils.exportMaskInVarray(_dbClient, exportMask, varray)) {
                    // Check if storage ports belongs to the other varray
                    URI otherVarray = null;
                    if (!exportGroup.getVirtualArray().equals(varray)) {
                        otherVarray = exportGroup.getVirtualArray();
                    } else {
                        StringMap altVarrays = exportGroup.getAltVirtualArrays();
                        if (altVarrays != null) {
                            String altVarray = altVarrays.get(system.getId().toString());
                            if(NullColumnValueGetter.isNotNullValue(altVarray)) {
                                otherVarray = URI.create(altVarray);
                            }
                        }
                    }
                    
                    if (otherVarray != null && ExportMaskUtils.exportMaskInVarray(_dbClient, exportMask, otherVarray)) {
                        _log.info(String.format("VPLEX ExportMask %s (%s) not in selected varray %s, skipping", 
                            exportMask.getMaskName(), exportMask.getId(), varray));
                        continue;
                    } else {
                        throw APIException.badRequests.exportMaskNotInVarray(exportMask.getId().toString());
                    }
                    
                } 
            } else {
                // For other array types, throw error if ports not in the varray
                List<URI> portsNotInVarray = ExportMaskUtils.getExportMaskStoragePortsNotInVarray(_dbClient, exportMask, varray);
                if (!portsNotInVarray.isEmpty()) {
                    String errorPorts = Joiner.on(',').join(portsNotInVarray);
                    String error = String.format("The ports : %s are in the exportMask %s, but not in the varray %s", 
                          errorPorts, exportMask.getId().toString(), varray.toString());
                    _log.error(error);
                    throw APIException.badRequests.storagePortsNotInVarray(errorPorts, exportMask.getId().toString(), 
                            varray.toString());    
                }
            }
            // Now look to see if there are any existing initiators in the ExportMask
            if (exportMask.hasAnyExistingInitiators()) {
                _log.error("ExportMask has existing initiators: " + exportMask.getMaskName());
                throw APIException.badRequests.externallyAddedInitiators(
                        exportMask.getMaskName(), exportMask.getExistingInitiators().toString());
            }
            
            // If there are eixisting volumes in the ExportMask, useExistingPath has to be true
            if (exportMask.hasAnyExistingVolumes() && !useExistingPaths) {
                _log.error("ExportMask has existing volumes: " + exportMask.getMaskName());
                throw APIException.badRequests.externallyAddedVolumes(exportMask.getMaskName(),
                        exportMask.getExistingVolumes().toString());
            }
            
            URI currentPGUri = exportMask.getPortGroup();
            if (!NullColumnValueGetter.isNullURI(currentPGUri)){
                StoragePortGroup currentPG = queryObject(StoragePortGroup.class, currentPGUri, false);
                if (!currentPG.getMutable()) {
                    storagePorts.addAll(currentPG.getStoragePorts());
                }
            }
            // Populate the existing paths map.
            StringSetMap zoningMap = exportMask.getZoningMap();
            
            if (zoningMap != null && !zoningMap.isEmpty()) {
                for (String initiator : zoningMap.keySet()) {
                    if (zoningMap.get(initiator).isEmpty()) {
                        // No ports for the initiator, inconsistent
                        continue;
                    }
                    if (!existingPaths.keySet().contains(initiator)) {
                        existingPaths.put(initiator, new StringSet());
                    }
                    existingPaths.get(initiator).addAll(zoningMap.get(initiator));
                }    
            } 
                
            Set<Initiator> maskInitiators = ExportMaskUtils.getInitiatorsForExportMask(_dbClient, exportMask, null);
            Set<String> additionalInitiators = new HashSet<String>();
            boolean hasMatchingInitiator = false;
            Set<URI> hostURIsForInitiators = new HashSet<URI>();
            for (Initiator maskInitiator : maskInitiators) {
                if (maskInitiator.getHost() != null) {
                    hostURIsForInitiators.add(maskInitiator.getHost());
                }
                if (initiatorMap.keySet().contains(maskInitiator.getId())) {
                    hasMatchingInitiator = true;
                }
                if (!exportGroup.getInitiators().contains(maskInitiator.getId().toString())) {
                    additionalInitiators.add(maskInitiator.getInitiatorPort());
                }
            }
            
            // We may not have any matching initiators if the user specified a set of
            // hosts and this host wasn't present. If not, we just skip this mask.
            if (!hasMatchingInitiator) {
                continue;
            }
            
            // Check if this is a mask for a single ComputeResource and if there are additional usable initiators.
            // If so add them to the list of initiators.
            List<Host> hostsForInitiators = _dbClient.queryObject(Host.class, hostURIsForInitiators);
            for (Host host : hostsForInitiators) {
                _log.info(String.format("Checking connected initiators for host %s (%s)", host.getHostName(), host.getId()));
                List<URI> hostConnectedInitiators = getHostConnectedInitiators(host, Arrays.asList(system.getId()), exportGroup);
                for (URI hostConnectedInitiator : hostConnectedInitiators) {
                    if (!initiators.contains(hostConnectedInitiator)) {
                        Initiator newInitiator = _dbClient.queryObject(Initiator.class, hostConnectedInitiator);
                        _log.info(String.format("Found connected initiator %s host %s (%s) to add to initiators", 
                                newInitiator.getInitiatorPort(), newInitiator.getHostName(), newInitiator.getId()));
                        initiators.add(newInitiator);
                    }
                }
                _log.info("Connected Initiators check completed host: " + host.getHostName());
            }
            
            if (!additionalInitiators.isEmpty()) {
                _log.info("ExportMask has additional initiators: ", exportMask.getMaskName());
                throw APIException.badRequests.additionalInitiators(exportMask.getMaskName(), additionalInitiators.toString());
            }
            
            // Then look to see if any other ExportGroups are using this ExportMask
            List<ExportGroup> assocExportGroups = ExportMaskUtils.getExportGroups(_dbClient, exportMask);
            for (ExportGroup assocGroup : assocExportGroups) {
                if (!assocGroup.getId().equals(exportGroup.getId()) && !affectedGroupURIs.contains(assocGroup.getId())) {
                    affectedGroupURIs.add(assocGroup.getId());
                    response.getAffectedExportGroups().add(toNamedRelatedResource(assocGroup, assocGroup.getLabel()));
                }
            }
        }
    }

	/**
	 * Validates that the hosts in the exportgroup against the passed in list of hosts.
	 * If there is at least one host that doesnt match, an exception is thrown.
	 * @param exportGroup
	 * @param hosts
	 */
	private void validateHostsInExportGroup(ExportGroup exportGroup, Set<URI> hosts) {
		StringSet egHosts = exportGroup.getHosts();
        Set<URI> egHostURIs = new HashSet<URI>();
        for (String egHost : egHosts) {
        	egHostURIs.add(URI.create(egHost));
        }
        
        Set<Host> mismatchHosts = new HashSet<Host>();
        for (URI host : hosts) {
        	if (!egHostURIs.contains(host)) {
        		Host h = _dbClient.queryObject(Host.class, host);
        		_log.info("Host %s is not part of the specified ExportGroup", h.getHostName());
        		mismatchHosts.add(h);
        	}
        }
        
        if (!mismatchHosts.isEmpty()) {
        	StringBuilder mismatchHostsStr =  new StringBuilder();
        	for (Host mismatchHost : mismatchHosts) {
        		mismatchHostsStr.append(mismatchHost.getHostName() + " ");
        	}
        	throw APIException.badRequests.exportPathAdjustmentSystemExportGroupHostsMismatch(mismatchHostsStr.toString());
        }
	}
   
    /**
     * Export paths adjustment
     *  
     * @param id The export group id
     * @param param The parameters including addedPaths, removedPaths, storage system URI, exportPathParameters, 
     *                  and waitBeforeRemovePaths
     * @brief Initiate port allocations for export
     * @return The pending task
     * @throws ControllerException
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/paths-adjustment")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskResourceRep pathsAdjustment(@PathParam("id") URI id, ExportPathsAdjustmentParam param)
            throws ControllerException {
         // Basic validation of ExportGroup and the request
        ExportGroup exportGroup = queryObject(ExportGroup.class, id, true);
        if (exportGroup.checkInternalFlags(DataObject.Flag.DELETION_IN_PROGRESS)) {
            throw BadRequestException.badRequests.deletionInProgress(
                    exportGroup.getClass().getSimpleName(), exportGroup.getLabel());
        }
        validateExportGroupNoPendingEvents(exportGroup);
        validateSuspendSetForNonDiscoverableHosts(exportGroup, param.getWaitBeforeRemovePaths(), param.getRemovedPaths().isEmpty());

        ArgValidator.checkUri(param.getStorageSystem());
        StorageSystem system = queryObject(StorageSystem.class, param.getStorageSystem(), true);
        
        // Log the input parameters
        param.logParameters(_log);
        
        // Get the virtual array, default to Export Group varray. Validate it matches.
        URI varray = param.getVirtualArray();
        if (varray != null) {
           boolean validVarray = varray.equals(exportGroup.getVirtualArray());
           if (exportGroup.getAltVirtualArrays() != null 
                   && varray.toString().equals(exportGroup.getAltVirtualArrays().get(system.getId().toString()))) {
               validVarray = true;
           }
           if (!validVarray) {
               throw APIException.badRequests.varrayNotInExportGroup(varray.toString());
           }
        } else {    
            varray = exportGroup.getVirtualArray();
        }

        validatePathAdjustment(exportGroup, system, param, varray);
        Boolean wait = new Boolean(param.getWaitBeforeRemovePaths());
        
        String task = UUID.randomUUID().toString();
        Operation op = initTaskStatus(exportGroup, task, Operation.Status.pending, ResourceOperationTypeEnum.EXPORT_PATHS_ADJUSTMENT);

        // persist the export group to the database
        _dbClient.updateObject(exportGroup);
        auditOp(OperationTypeEnum.EXPORT_PATH_ADJUSTMENT, true, AuditLogManager.AUDITOP_BEGIN,
                exportGroup.getLabel(), exportGroup.getId().toString(),
                exportGroup.getVirtualArray().toString(), exportGroup.getProject().toString());

        TaskResourceRep taskRes = toTask(exportGroup, task, op);
        BlockExportController exportController = getExportController();
        _log.info("Submitting export path adjustment request.");
        Map<URI, List<URI>> addedPaths = convertInitiatorPathParamToMap(param.getAdjustedPaths());
        Map<URI, List<URI>> removedPaths = convertInitiatorPathParamToMap(param.getRemovedPaths());
        ExportPathParams pathParam = new ExportPathParams(param.getExportPathParameters(), exportGroup);
        exportController.exportGroupPortRebalance(param.getStorageSystem(), id, varray, addedPaths, removedPaths, 
                pathParam, wait, task);
        return taskRes;
    }
    
    /**
     * Convert input InititaorPathParam to a map of initiator URI to list of storage ports URI
     * 
     * @param paths
     * @return
     */
    private Map<URI, List<URI>> convertInitiatorPathParamToMap(List<InitiatorPathParam> paths) {
        Map<URI, List<URI>> result = null;
        if (paths != null && !paths.isEmpty()) {
            result = new HashMap<URI, List<URI>>();
            for (InitiatorPathParam path : paths) {
                result.put(path.getInitiator(), path.getStoragePorts());
            }
        }
        return result;
    }
    
    /**
     * Validate the path adjustment request parameters
     * 
     * @param exportGroup ExportGroup object
     * @param system StorageSystem object
     * @param param Export Path Adjustment Parameters
     * @param varray URI of the virtual array, used to check any supplied ports are in correct varray
     */
    private void validatePathAdjustment(ExportGroup exportGroup, StorageSystem system, 
            ExportPathsAdjustmentParam param, URI varray) {
        String systemType = system.getSystemType();
        if (!Type.vmax.name().equalsIgnoreCase(systemType) &&
            !Type.vplex.name().equalsIgnoreCase(systemType)) {
            throw APIException.badRequests.exportPathAdjustmentSystemNotSupported(systemType); 
        }
        List<ExportMask> exportMasks = ExportMaskUtils.getExportMasks(_dbClient,  exportGroup, system.getId());
        if (exportMasks.isEmpty()) {
            throw APIException.badRequests.exportPathAdjustmentSystemExportGroupNotMatch(exportGroup.getLabel(), system.getNativeGuid());
        }
        ExportPathParameters pathParam = param.getExportPathParameters();
        if (pathParam == null) {
            throw APIException.badRequests.exportPathAdjustementNoPathParameters();
        }
        URI pgURI = pathParam.getPortGroup();
        // Check if exportMask has existing volumes, if it does, make sure no remove paths.
        for (ExportMask exportMask : exportMasks) {
            List<InitiatorPathParam> removePaths = param.getRemovedPaths();
            if (removePaths.isEmpty() || !exportMask.hasAnyExistingVolumes()) {
                continue;
            }
            Map<URI, List<URI>> removes = new HashMap<URI, List<URI>>();
            for (InitiatorPathParam initPath : removePaths) {
                removes.put(initPath.getInitiator(), initPath.getStoragePorts());
            }
            Map<URI, List<URI>> removedPathForMask = ExportMaskUtils.getRemovePathsForExportMask(exportMask, removes);
            if (removedPathForMask != null && !removedPathForMask.isEmpty() && pgURI == null) {
                _log.error("It has removed path for the ExportMask with existing volumes: " + exportMask.getMaskName());
                throw APIException.badRequests.externallyAddedVolumes(exportMask.getMaskName(),
                        exportMask.getExistingVolumes().toString());
            }
            
        }
        // check adjusted paths are valid. initiators are in the export group, and the targets are in the storage system, and
        // in valid state.
        Map<URI, List<URI>>adjustedPaths = convertInitiatorPathParamToMap(param.getAdjustedPaths());
        List<URI> pathInitiatorURIs = new ArrayList<URI>(adjustedPaths.keySet());
        StringSet initiatorIds = exportGroup.getInitiators();
        if (!initiatorIds.containsAll(StringSetUtil.uriListToStringSet(pathInitiatorURIs))) {
            // Determine all the host URIs for the egInitiators
            Set<URI> egHostURIs = new HashSet<URI>();
            List<Initiator> egInitiators = ExportUtils.getExportGroupInitiators(exportGroup, _dbClient);
            for (Initiator egInitiator : egInitiators) {
                if (!NullColumnValueGetter.isNullURI(egInitiator.getHost())) {
                    egHostURIs.add(egInitiator.getHost());
                }
            }
            // Now, only throw error if there are initiators that are not of any of the egHostURIs
            List<Initiator> pathInitiators = _dbClient.queryObject(Initiator.class, pathInitiatorURIs);
            List<String> badInitiators = new ArrayList<String>();
            for (Initiator pathInitiator : pathInitiators) {
                // Bad if not in the EG initiatorIds AND not from an identifiable host or not from a host in EG
                if (!initiatorIds.contains(pathInitiator.getId().toString())) {
                    if (pathInitiator.getHost() == null || !egHostURIs.contains(pathInitiator.getHost())) {
                        badInitiators.add(pathInitiator.getHostName() + "-" + pathInitiator.getInitiatorPort());
                    }
                }
            }
            if (!badInitiators.isEmpty()) {
                throw APIException.badRequests.exportPathAdjustmentAdjustedPathNotValid(Joiner.on(", ").join(badInitiators));
            }
        }
        Set<URI> pathTargets = new HashSet<URI>();
        for (List<URI> targets : adjustedPaths.values()) {
            pathTargets.addAll(targets);
        }
        
        Set<URI> systemPorts = new HashSet<URI>();
        URIQueryResultList storagePortURIs = new URIQueryResultList();
        _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getStorageDeviceStoragePortConstraint(system.getId()),
                storagePortURIs);
        // Validate the targets to be provisioned have a valid state (COMPATIBLE, REGISTERED, and VISIBLE) and
        // that their tagged virtual array contains our varray.
        List<StoragePort> storagePorts = _dbClient.queryObject(StoragePort.class, storagePortURIs);
        for (StoragePort port : storagePorts) {
            if (!port.getInactive() && 
                    port.getCompatibilityStatus().equals(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name()) &&
                    port.getRegistrationStatus().equals(StoragePort.RegistrationStatus.REGISTERED.name()) &&
                    port.getDiscoveryStatus().equals(DiscoveryStatus.VISIBLE.name()) 
                    && port.getTaggedVirtualArrays() != null 
                    && port.getTaggedVirtualArrays().contains(varray.toString())) {
                systemPorts.add(port.getId());
            }
        }
        if (!systemPorts.containsAll(pathTargets)) {
            // List only the invalid targets
            pathTargets.removeAll(systemPorts);
            throw APIException.badRequests.exportPathAdjustmentAdjustedPathNotValid(Joiner.on(",").join(pathTargets));
        }
    }

    /**
     * Returns a Set of the FQDN names of hosts that are non discoverable in the ExportGroup.
     * @param exportGroup -- ExportGroup object
     * @param dbClient -- database handle
     * @return Set of non-discoverable host strings
     */
    public Set<String> getNonDiscoverableHostsInExportGroup(ExportGroup exportGroup) {
        Set<String> nonDiscoverable = new HashSet<String>();
        List<Host> hosts = getHosts(exportGroup);
        for (Host host : hosts) {
            if (!host.getDiscoverable()) {
                nonDiscoverable.add(host.getHostName());
            }
        }
        return nonDiscoverable;
    }
    
    /**
     * For export path adjustment, if suspend before removing paths is not set and we're likely to remove paths,
     * and there are hosts that are not discoverable, throw an error saying the user must set suspend before removing paths.
     * @param exportGroup -- ExportGroup object
     * @param suspendBeforeRemovingPaths -- flag from order
     * @param useExistingPaths -- indication that all existing paths will be maintained
     */
    public void validateSuspendSetForNonDiscoverableHosts(ExportGroup exportGroup,
            boolean suspendBeforeRemovingPaths, boolean useExistingPaths) {
        boolean requireSuspend = Boolean.valueOf(
                ControllerUtils.getPropertyValueFromCoordinator(_coordinator, PATH_ADJUST_REQUIRE_SUSPEND));
        // If either suspend is set or useExistingPaths flag is set, we're good
        if (!requireSuspend || suspendBeforeRemovingPaths || useExistingPaths) {
            return;
        }
        // Otherwise, if there are some non-discoverable hosts, we want to force an error to require
        // suspend to be set.
        Set<String> nonDiscoverableHosts = getNonDiscoverableHostsInExportGroup(exportGroup);
        if (!nonDiscoverableHosts.isEmpty()) {
            String hostList = Joiner.on(",").join(nonDiscoverableHosts);
            throw APIException.badRequests.pathAdjustmentOnNonDiscoverableHostsWithoutSuspend(hostList);
        }
    }
    
    
    /**
     * Validate port group, if the volumes to be exported are from VMAX, and the port group setting
     * is on.
     * 
     * @param addVolumes - Volume params to be exported
     * @param portGroup - Port group URI
     */
    public void validatePortGroupWhenAddVolumesForExportGroup(Collection<URI> addVolumes, URI portGroup, ExportGroup exportGroup) {
        if (addVolumes != null && !addVolumes.isEmpty()) {
            Set<URI> systems = new HashSet<URI>();
            for (URI blockURI : addVolumes) {
                BlockObject blockObject = BlockObject.fetch(_dbClient, blockURI);
                systems.add(blockObject.getStorageController());
            }
            boolean isVmax = false;
            StorageSystem storage = null;
            for (URI systemURI : systems) {
                storage = queryObject(StorageSystem.class, systemURI, true);
                if (Type.vmax.name().equals(storage.getSystemType())) {
                    isVmax = true;
                    break;
                }
            }
            if (isVmax) {
                String value = customConfigHandler.getComputedCustomConfigValue(CustomConfigConstants.VMAX_USE_PORT_GROUP_ENABLED,
                        "vmax", null);
                boolean useExistingPortGroup = Boolean.TRUE.toString().equalsIgnoreCase(value);
                if (!useExistingPortGroup) {
                    return;
                }
                if (exportGroup == null && portGroup == null) {
                    // create export group case, When use existing port group is on, users should provide a port group
                   throw APIException.badRequests.portGroupNotSpecified();
                } else if (exportGroup == null) {
                    // port group is specified, check the port group storage system is the same as the volume system
                    StoragePortGroup pgObject = queryObject(StoragePortGroup.class, portGroup, true);
                    if (!storage.getId().equals(pgObject.getStorageDevice()) || systems.size() > 1) {
                        throw APIException.badRequests.cannotExportVolumesFromDifferentSystems(pgObject.getNativeGuid());
                        
                    }
                } else if (exportGroup != null){
                    // update export group. check if export mask exists. if not, need to specify port group if using 
                    // existing port group
                    List<ExportMask> masks = ExportMaskUtils.getExportMasks(_dbClient,  exportGroup, storage.getId());
                    if (masks.isEmpty() && portGroup == null) {
                        throw APIException.badRequests.portGroupNotSpecified();
                    } else if (!masks.isEmpty() && portGroup != null) {
                        StoragePortGroup pgObject = queryObject(StoragePortGroup.class, portGroup, true);
                        if (!storage.getId().equals(pgObject.getStorageDevice()) || systems.size() > 1) {
                            throw APIException.badRequests.cannotExportVolumesFromDifferentSystems(pgObject.getNativeGuid());
                            
                        }
                    }
                }
                
            }
        }
    }
    
    /**
     * If supplied, lookup the ExportPathParams given a URI.
     * @param exportPathParamsURI -- URI of object to find
     * @return  ExportPathParams structure -- structure retrieved
     */
    public ExportPathParams lookupPathParams(URI exportPathParamsURI) {
        if (exportPathParamsURI == null) {
            return null;
        }
        ArgValidator.checkUri(exportPathParamsURI);
        ExportPathParams policy = _permissionsHelper.getObjectById(exportPathParamsURI, ExportPathParams.class);
        ArgValidator.checkEntityNotNull(policy, exportPathParamsURI, false);
        return policy;
    }

    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/change-port-group")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskResourceRep changePortGroup(@PathParam("id") URI id, ChangePortGroupParam param)
            throws ControllerException {
         // Basic validation of ExportGroup and the request
        param.logParameters(_log);
        ExportGroup exportGroup = queryObject(ExportGroup.class, id, true);
        if (exportGroup.checkInternalFlags(DataObject.Flag.DELETION_IN_PROGRESS)) {
            throw BadRequestException.badRequests.deletionInProgress(
                    exportGroup.getClass().getSimpleName(), exportGroup.getLabel());
        }
        validateExportGroupNoPendingEvents(exportGroup);
        Boolean wait = new Boolean(param.getWaitBeforeRemovePaths());
        validateSuspendSetForNonDiscoverableHosts(exportGroup, wait, true);

        ArgValidator.checkUri(param.getNewPortGroup());
        StoragePortGroup newPortGroup = queryObject(StoragePortGroup.class, param.getNewPortGroup(), true);
        if (!newPortGroup.isUsable()) {
            throw APIException.badRequests.portGroupInvalid(newPortGroup.getNativeGuid());
        }
        URI systemURI = newPortGroup.getStorageDevice();
        StorageSystem system = queryObject(StorageSystem.class, systemURI, true);
        
        // Get the virtual array, default to Export Group varray. Validate it matches.
        URI varray = exportGroup.getVirtualArray();
        
        String value = customConfigHandler.getComputedCustomConfigValue(CustomConfigConstants.VMAX_USE_PORT_GROUP_ENABLED,
                Type.vmax.name(), null);
        if (!Boolean.TRUE.toString().equalsIgnoreCase(value)) {
            throw APIException.badRequests.portGroupSettingIsOff();
        }
        com.emc.storageos.api.service.impl.resource.utils.ExportUtils.validatePortGroupWithVirtualArray(newPortGroup, varray, _dbClient);

        URI currentPortGroup = param.getCurrentPortGroup();
        if (currentPortGroup != null && currentPortGroup.equals(newPortGroup.getId())) {
            throw APIException.badRequests.changePortGroupSameNewPortGroup(newPortGroup.getNativeGuid());
        }
        
        URI exportMaskURI = param.getExportMask();
        ExportMask mask = null;
        if (exportMaskURI != null) {
            mask = queryObject(ExportMask.class, exportMaskURI, true);
            if (!exportGroup.getExportMasks().contains(exportMaskURI.toString())) {
                throw APIException.badRequests.changePortGroupInvalidExportMask(mask.getMaskName());
            }
            if (!systemURI.equals(mask.getStorageDevice())) {
                throw APIException.badRequests.changePortGroupInvalidExportMask(mask.getMaskName());
            
            }
            if (currentPortGroup != null && !currentPortGroup.equals(mask.getPortGroup())) {
                throw APIException.badRequests.changePortGroupInvalidExportMask(mask.getMaskName());
            }
        }
        List<ExportMask> exportMasks = new ArrayList<ExportMask> ();
        if (mask != null) {
            exportMasks.add(mask);
        } else {
            exportMasks = ExportMaskUtils.getExportMasks(_dbClient,  exportGroup, system.getId(), currentPortGroup);
        }
        if (exportMasks.isEmpty()) {
            throw APIException.badRequests.changePortGroupInvalidPortGroup(newPortGroup.getNativeGuid());
        }
        
        List<URI> affectedMasks = new ArrayList<URI>();
        for (ExportMask exportMask : exportMasks) {
            URI currentPGUri = exportMask.getPortGroup();
            StringSet newPorts = newPortGroup.getStoragePorts();
                
            if (!newPortGroup.getId().equals(currentPGUri)) {
                StoragePortGroup currentPG = queryObject(StoragePortGroup.class, currentPGUri, false);
                StringSet currentPorts = currentPG.getStoragePorts();
                if (!Collections.disjoint(newPorts, currentPorts)) {
                    throw APIException.badRequests.changePortGroupPortGroupNoOverlap(newPortGroup.getLabel());
                }
                // We could not support volumes with host IO limit for port group change. users have to disable Host IO limit first
                // because we could not add use the same storage group and a new port group to create the new masking view
                if (system.checkIfVmax3()) {
                    String volumeWithHostIO = ExportUtils.getVolumeHasHostIOLimitSet(_dbClient, exportMask.getVolumes()); 
                    if (volumeWithHostIO != null) {
                        throw APIException.badRequests.changePortGroupNotSupportedforHostIOLimit(volumeWithHostIO);
                    }
                
                }
                // Check if there is any existing volumes in the export mask
                if (exportMask.getExistingVolumes() != null && !exportMask.getExistingVolumes().isEmpty()) {
                    throw APIException.badRequests.changePortGroupExistingVolumes(exportMask.getMaskName(), 
                            Joiner.on(',').join(exportMask.getExistingVolumes().keySet()));
                }
                if (exportMask.getExistingInitiators() != null && !exportMask.getExistingInitiators().isEmpty()) {
                    throw APIException.badRequests.changePortGroupExistingInitiators(exportMask.getMaskName(),
                            Joiner.on(',').join(exportMask.getExistingInitiators()));
                }
                affectedMasks.add(exportMask.getId());
            } else {
                _log.info(String.format("The export mask %s uses the same port group %s", exportMask.getMaskName(), newPortGroup.getLabel()));
            }
        }
        
        String task = UUID.randomUUID().toString();
        Operation op = initTaskStatus(exportGroup, task, Operation.Status.pending, ResourceOperationTypeEnum.EXPORT_CHANGE_PORT_GROUP);

        // persist the export group to the database
        _dbClient.updateObject(exportGroup);
        auditOp(OperationTypeEnum.EXPORT_CHANGE_PORT_GROUP, true, AuditLogManager.AUDITOP_BEGIN,
                exportGroup.getLabel(), exportGroup.getId().toString(),
                exportGroup.getVirtualArray().toString(), exportGroup.getProject().toString());

        TaskResourceRep taskRes = toTask(exportGroup, task, op);
        if (affectedMasks.isEmpty()) {
            _log.info("No export mask to change port group, do nothing");
            op.ready();
            return taskRes;
        }
        
        BlockExportController exportController = getExportController();
        _log.info(String.format("Submitting change port group %s request.", newPortGroup.getNativeGuid()));
        
        exportController.exportGroupChangePortGroup(systemURI, id, newPortGroup.getId(), affectedMasks, wait, task);
        return taskRes;
    }
}
