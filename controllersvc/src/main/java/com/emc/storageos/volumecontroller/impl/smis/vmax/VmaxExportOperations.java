/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.vmax;

import java.net.URI;
import java.text.MessageFormat;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import javax.cim.CIMArgument;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.wbem.CloseableIterator;
import javax.wbem.WBEMException;
import javax.wbem.client.WBEMClient;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.computesystemcontroller.impl.ComputeSystemHelper;
import com.emc.storageos.customconfigcontroller.CustomConfigConstants;
import com.emc.storageos.customconfigcontroller.DataSource;
import com.emc.storageos.customconfigcontroller.DataSourceFactory;
import com.emc.storageos.customconfigcontroller.impl.CustomConfigHandler;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportGroup.ExportGroupType;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.PersonalityTypes;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.networkcontroller.impl.NetworkDeviceController;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.HostIOLimitsParam;
import com.emc.storageos.volumecontroller.impl.StorageGroupPolicyLimitsParam;
import com.emc.storageos.volumecontroller.impl.VolumeURIHLU;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskInitiatorCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskVolumeToStorageGroupCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.RollbackExportGroupCreateCompleter;
import com.emc.storageos.volumecontroller.impl.job.QueueJob;
import com.emc.storageos.volumecontroller.impl.smis.CIMObjectPathFactory;
import com.emc.storageos.volumecontroller.impl.smis.CIMPropertyFactory;
import com.emc.storageos.volumecontroller.impl.smis.ExportMaskOperations;
import com.emc.storageos.volumecontroller.impl.smis.SmisCommandHelper;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.smis.SmisException;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisCreateMaskingViewJob;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisJob;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisMaskingViewAddVolumeJob;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisMaskingViewRemoveVolumeJob;
import com.emc.storageos.volumecontroller.impl.smis.job.SmisSynchSubTaskJob;
import com.emc.storageos.volumecontroller.impl.smis.vmax.ExportOperationContext.ExportOperationContextOperation;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.workflow.WorkflowService;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Collections2;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class VmaxExportOperations implements ExportMaskOperations {

    private static Logger _log = LoggerFactory.getLogger(VmaxExportOperations.class);

    private SmisCommandHelper _helper;

    private DbClient _dbClient;

    private CIMObjectPathFactory _cimPath;

    public static final String VIPR_NO_CLUSTER_SPECIFIED_NULL_VALUE = "VIPR-NO-CLUSTER-SPECIFIED-NULL-VALUE";

    @Autowired
    private DataSourceFactory dataSourceFactory;

    @Autowired
    private CustomConfigHandler customConfigHandler;

    @Autowired
    private NetworkDeviceController _networkDeviceController;

    public void setCimObjectPathFactory(CIMObjectPathFactory cimObjectPathFactory) {
        _cimPath = cimObjectPathFactory;
    }

    public void setSmisCommandHelper(SmisCommandHelper helper) {
        _helper = helper;
    }

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    /*
     * This private method ensures that all the initiators in the list either have the same
     * cluster name or none at all
     */
    private String getClusterNameFromInitiators(List<Initiator> initiatorList) {
        int nullClusterNameCount = 0;
        String storedClusterName = null;
        for (Initiator initiator : initiatorList) {
            String currentClusterName = initiator.getClusterName(); // cluster name of the current initiator
            if (currentClusterName == null || (currentClusterName.length() == 0)) {
                // Are all the cluster names so far null
                nullClusterNameCount++;
                if (storedClusterName != null) {
                    // no..we have a problem
                    return null;
                }
            } else {
                // we have a cluster name...
                if (nullClusterNameCount > 0) {
                    // we have a problem, we have seen "null" cluster name initiators before
                    // we are not expecting a name
                    return null;
                } else {
                    if (storedClusterName != null) {
                        if (!storedClusterName.equals(currentClusterName)) {
                            return null;
                        }
                    } else {
                        storedClusterName = currentClusterName;
                    }
                }
            }
        }
        return (storedClusterName == null) ? VIPR_NO_CLUSTER_SPECIFIED_NULL_VALUE :
                storedClusterName;
    }

    /*
     * This private method iterates thru' the list of initiators and clubs them by hostname.
     */
    private Map<String, List<Initiator>> clubInitiatorsByHostname(List<Initiator> initiatorList) {
        String hostName;
        Map<String, List<Initiator>> returnMap = new HashMap<String, List<Initiator>>();
        for (Initiator initiator : initiatorList) {
            hostName = initiator.getHostName();
            if (returnMap.containsKey(hostName)) {
                List<Initiator> initiatorsForHost = returnMap.get(hostName);
                if (initiatorsForHost == null) {
                    initiatorsForHost = new ArrayList<Initiator>();
                }
                initiatorsForHost.add(initiator);
                returnMap.put(hostName, initiatorsForHost);
            } else {
                List<Initiator> initiatorsForHost = new ArrayList<Initiator>();
                initiatorsForHost.add(initiator);
                returnMap.put(hostName, initiatorsForHost);
            }
        }
        return returnMap;
    }

    /*
     * This private method ensures that the OS of the host to which the initiators belong are
     * of the same type..The API SVC would have checked for this; however we are ensuring that
     * callers of this method directly..RP? adhere.
     */
    private boolean validateInitiatorHostOS(List<Initiator> initiatorList) {
        Set<String> hostTypes = new HashSet<String>();
        for (Initiator ini : initiatorList) {
            URI hostURI = ini.getHost();
            if (hostURI == null) {
                hostTypes.add(Host.HostType.Other.name());
            } else {
                Host host = _dbClient.queryObject(Host.class, hostURI);
                hostTypes.add(host.getType());
            }
        }
        return (hostTypes.size() == 1);
    }

    /**
     * This implementation will attempt to create a MaskingView on the VMAX with the
     * associated elements. The goal of this is to create a MaskingView per compute
     * resource. A compute resource is either a host or a cluster. A host is a single
     * entity with multiple initiators. A cluster is an entity consisting of multiple
     * hosts.
     * 
     * The idea is to maintain 1 MaskingView to 1 compute resource,
     * regardless of the number of ViPR Export*Groups* that are created. An
     * Export*Mask* will map to a MaskingView.
     * 
     * @param storage
     * @param exportMaskURI
     * @param volumeURIHLUs
     * @param targetURIList
     * @param initiatorList
     * @param taskCompleter
     * @throws DeviceControllerException
     */
    @Override
    public void createExportMask(StorageSystem storage,
            URI exportMaskURI,
            VolumeURIHLU[] volumeURIHLUs,
            List<URI> targetURIList,
            List<Initiator> initiatorList,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} createExportMask START...", storage.getSerialNumber());
        _log.info("createExportMask: volume-HLU pairs:  {}", volumeURIHLUs);
        _log.info("createExportMask: targets:    {}", targetURIList);
        _log.info("createExportMask: initiators: {}", initiatorList);
        Map<StorageGroupPolicyLimitsParam, CIMObjectPath> newlyCreatedChildVolumeGroups = new HashMap<StorageGroupPolicyLimitsParam, CIMObjectPath>();
        ExportOperationContext context = new VmaxExportOperationContext();
        // Prime the context object
        taskCompleter.updateWorkflowStepContext(context);

        try {

            ExportMask mask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
            String maskingViewName = generateMaskViewName(storage, mask);

            // Fill this in now so we have it in case of exceptions
            String cascadedIGCustomTemplateName = CustomConfigConstants.VMAX_HOST_CASCADED_IG_MASK_NAME;
            String initiatorGroupCustomTemplateName = CustomConfigConstants.VMAX_HOST_INITIATOR_GROUP_MASK_NAME;
            String cascadedSGCustomTemplateName = CustomConfigConstants.VMAX_HOST_CASCADED_SG_MASK_NAME;
            String portGroupCustomTemplateName = CustomConfigConstants.VMAX_HOST_PORT_GROUP_MASK_NAME;

            String exportType = ExportMaskUtils.getExportType(_dbClient, mask);
            if (ExportGroupType.Cluster.name().equals(exportType)) {
                cascadedIGCustomTemplateName = CustomConfigConstants.VMAX_CLUSTER_CASCADED_IG_MASK_NAME;
                initiatorGroupCustomTemplateName = CustomConfigConstants.VMAX_CLUSTER_INITIATOR_GROUP_MASK_NAME;
                cascadedSGCustomTemplateName = CustomConfigConstants.VMAX_CLUSTER_CASCADED_SG_MASK_NAME;
                portGroupCustomTemplateName = CustomConfigConstants.VMAX_CLUSTER_PORT_GROUP_MASK_NAME;
            }

            // 1. InitiatorGroup (IG)
            DataSource cascadedIGDataSource = ExportMaskUtils.getExportDatasource(storage, initiatorList, dataSourceFactory,
                    cascadedIGCustomTemplateName);
            String cigName = customConfigHandler.getComputedCustomConfigValue(cascadedIGCustomTemplateName, storage.getSystemType(),
                    cascadedIGDataSource);
            cigName = _helper.generateGroupName(_helper.getExistingInitiatorGroupsFromArray(storage), cigName);
            CIMObjectPath cascadedIG =
                    createOrUpdateInitiatorGroups(storage, exportMaskURI, cigName, initiatorGroupCustomTemplateName,
                            initiatorList, taskCompleter);
            if (cascadedIG == null) {
                // This is an error condition, in which case the createOrSelectX should
                // have stuff the task with the error message/code. We're just going to
                // return from here.
                return;
            }

            // 2. StorageGroup (SG)
            DataSource cascadedSGDataSource = ExportMaskUtils.getExportDatasource(storage, initiatorList, dataSourceFactory,
                    cascadedSGCustomTemplateName);
            String csgName = customConfigHandler.getComputedCustomConfigValue(cascadedSGCustomTemplateName, storage.getSystemType(),
                    cascadedSGDataSource);

            // 3. PortGroup (PG)
            DataSource portGroupDataSource = ExportMaskUtils.getExportDatasource(storage, initiatorList, dataSourceFactory,
                    portGroupCustomTemplateName);
            String pgGroupName = customConfigHandler.getComputedCustomConfigValue(portGroupCustomTemplateName, storage.getSystemType(),
                    portGroupDataSource);

            // CTRL-9054 Always create unique port Groups.
            pgGroupName = _helper.generateGroupName(_helper.getExistingPortGroupsFromArray(storage), pgGroupName);
            CIMObjectPath targetPortGroupPath =
                    createTargetPortGroup(storage, pgGroupName, targetURIList, taskCompleter);

            // 4. ExportMask = MaskingView (MV) = IG + SG + PG
            CIMObjectPath volumeParentGroupPath = storage.checkIfVmax3() ?
                    // TODO: Customized name for SLO based group
                    createOrSelectSLOBasedStorageGroup(storage, exportMaskURI, initiatorList, volumeURIHLUs, csgName,
                            newlyCreatedChildVolumeGroups, taskCompleter)
                    :
                    createOrSelectStorageGroup(storage, exportMaskURI, initiatorList, volumeURIHLUs, csgName,
                            newlyCreatedChildVolumeGroups, taskCompleter);
            createMaskingView(storage, exportMaskURI, maskingViewName, volumeParentGroupPath,
                    volumeURIHLUs, targetPortGroupPath, cascadedIG, taskCompleter);
        } catch (Exception e) {
            _log.error(String.format("createExportMask failed - maskName: %s", exportMaskURI.toString()), e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(_dbClient, serviceError);
        }
        _log.info("{} createExportMask END...", storage.getSerialNumber());
    }

    @Override
    public void deleteExportMask(StorageSystem storage,
            URI exportMaskURI,
            List<URI> volumeURIList,
            List<URI> targetURIList,
            List<Initiator> initiatorList,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} deleteExportMask START...", storage.getSerialNumber());
        try {
            boolean isVmax3 = storage.checkIfVmax3();
            WBEMClient client = _helper.getConnection(storage).getCimClient();
            String maskingViewName = _helper.getExportMaskName(exportMaskURI);

            // Always get the Storage Group from masking View, rather than depending on the name to find out SG.
            String groupName = _helper.getStorageGroupForGivenMaskingView(maskingViewName, storage);

            /*
             * The idea is to remove orphaned child Groups, after deleting masking view. We're getting
             * the list of childGroups here because once we call deleteMaskingView, the parent group
             * will be automatically deleted.
             * 
             * Run Associator Names to get details of child Storage Groups ,and group them based on
             * Fast Policy.
             */
            Map<StorageGroupPolicyLimitsParam, List<String>> childGroupsByFast = new HashMap<StorageGroupPolicyLimitsParam, List<String>>();

            // if SGs are already removed from masking view manually, then skip this part
            if (null != groupName) {
                childGroupsByFast = _helper.groupStorageGroupsByAssociation(storage, groupName);
            } else {
                _log.info("Masking View {} doesn't have any SGs associated, probably removed manually from Array",
                        maskingViewName);
            }

            /*
             * If a maskingView was created by other instance, can not delete it here during roll back. Hence,
             * set task as done.
             */
            if (taskCompleter instanceof RollbackExportGroupCreateCompleter) {
                /*
                 * The purpose of rollback is to delete the masking view created by this very ViPR instance as
                 * part of this workflow, and it should not delete masking view created externally or another ViPR
                 */
                // Get the context from the task completer, in case this is a rollback.
                ExportOperationContext context = (ExportOperationContext) WorkflowService.getInstance().loadStepData(
                        taskCompleter.getOpId());
                if (context != null) {
                    exportMaskRollback(storage, context, taskCompleter);
                }
            } else {
                if (!deleteMaskingView(storage, exportMaskURI, childGroupsByFast, taskCompleter)) {
                    // Could not delete the MaskingView. Error should be stuffed by the
                    // deleteMaskingView call. Simply return from here.
                    return;
                }

                // Need Mask's volume list for removing volumes from phantom storage group.
                ExportMask exportMask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
                List<URI> volumeURIs = ExportMaskUtils.getVolumeURIs(exportMask);

                for (Map.Entry<StorageGroupPolicyLimitsParam, List<String>> entry : childGroupsByFast.entrySet()) {
                    _log.info(String.format("Mask %s FAST Policy %s associated with %d Storage Group(s)", maskingViewName, entry.getKey(),
                            entry.getValue().size()));
                }

                if (groupName != null) {
                    // delete the CSG explicitly (CTRL-9236)
                    CIMObjectPath storageGroupPath = _cimPath.getMaskingGroupPath(storage, groupName,
                            SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup);
                    if (_helper.isCascadedSG(storage, storageGroupPath)) {
                        _helper.deleteMaskingGroup(storage, groupName,
                                SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup);
                    }

                    /**
                     * After successful deletion of masking view, try to remove the child Storage Groups ,which were part of cascaded
                     * Parent Group. If Fast Policy is not enabled, then those child groups can be removed.
                     * If Fast enabled, then try to find if this child Storage Group is associated with more than 1 Parent Cascaded
                     * Group, if yes, then we cannot delete the child Storage Group.
                     */
                    for (Entry<StorageGroupPolicyLimitsParam, List<String>> childGroupByFastEntry : childGroupsByFast.entrySet()) {
                        for (String childGroupName : childGroupByFastEntry.getValue()) {
                            _log.info("Processing Group {} deletion with Fast Policy {}", childGroupName, childGroupByFastEntry.getKey());
                            CIMObjectPath maskingGroupPath = _cimPath.getMaskingGroupPath(storage, childGroupName,
                                    SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup);
                            if (!_helper.isFastPolicy(childGroupByFastEntry.getKey().getAutoTierPolicyName())) {
                                /**
                                 * Remove the volumes from any phantom storage group (CTRL-8217).
                                 * 
                                 * Volumes part of Phantom Storage Group will be in Non-CSG Non-FAST Storage Group
                                 */
                                if (!_helper.isCascadedSG(storage, maskingGroupPath)) {
                                    // Get volumes which are part of this Storage Group
                                    List<URI> volumesInSG = _helper.findVolumesInStorageGroup(storage, childGroupName, volumeURIs);

                                    // Flag to indicate whether or not we need to use the EMCForce flag on this operation.
                                    // We currently use this flag when dealing with RP Volumes as they are tagged for RP and the
                                    // operation on these volumes would fail otherwise.
                                    boolean forceFlag = false;
                                    for (URI volURI : volumesInSG) {
                                        forceFlag = ExportUtils.useEMCForceFlag(_dbClient, volURI);
                                        if (forceFlag) {
                                            break;
                                        }
                                    }

                                    removeVolumesFromPhantomStorageGroup(storage,
                                            client, exportMaskURI, volumesInSG,
                                            childGroupName, forceFlag);
                                }

                                // Delete the Storage Group
                                _helper.deleteMaskingGroup(storage, childGroupName,
                                        SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup);
                            } else if (!_helper.findStorageGroupsAssociatedWithMultipleParents(
                                    storage, childGroupName) && !_helper.findStorageGroupsAssociatedWithOtherMaskingViews(
                                    storage, childGroupName)) {
                                // volumeDeviceIds and policyName are required in case of VMAX3 to add volumes back
                                // to parking to storage group.
                                Set<String> volumeDeviceIds = new HashSet<String>();
                                String policyName = childGroupByFastEntry.getKey().getAutoTierPolicyName();
                                if (isVmax3) {
                                    volumeDeviceIds = _helper.getVolumeDeviceIdsFromStorageGroup(storage, childGroupName);
                                }
                                // existing masking view had already been deleted, hence there should not be any other masking view which
                                // holds the group, if yes, then we should not delete this group
                                if (!isVmax3) {
                                    _log.debug("Removing Storage Group {} from Fast Policy {}", childGroupName,
                                            childGroupByFastEntry.getKey());
                                    _helper.removeVolumeGroupFromPolicyAndLimitsAssociation(client, storage, maskingGroupPath);
                                }
                                _log.debug("Deleting Storage Group {}", childGroupName);
                                _helper.deleteMaskingGroup(storage, childGroupName,
                                        SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup);

                                if (isVmax3 && !volumeDeviceIds.isEmpty()) {
                                    // We need to add volumes back to appropriate parking storage group.
                                    addVolumesToParkingStorageGroup(storage, policyName, volumeDeviceIds);
                                }
                            } else {
                                _log.info(
                                        "Storage Group {} is either having more than one parent Storage Group or its part of another existing masking view",
                                        childGroupName);
                                // set Host IO Limits on SG which we reseted before deleting MV
                                if (childGroupByFastEntry.getKey().isHostIOLimitIOPsSet()) {
                                    _helper.updateHostIOLimitIOPs(client, maskingGroupPath, childGroupByFastEntry.getKey()
                                            .getHostIOLimitIOPs());
                                }
                                if (childGroupByFastEntry.getKey().isHostIOLimitBandwidthSet()) {
                                    _helper.updateHostIOLimitBandwidth(client, maskingGroupPath, childGroupByFastEntry.getKey()
                                            .getHostIOLimitBandwidth());
                                }
                            }
                        }
                    }
                }
            }
            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _log.error(String.format("deleteExportMask failed - maskName: %s", exportMaskURI.toString()), e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(_dbClient, serviceError);
        }
        _log.info("{} deleteExportMask END...", storage.getSerialNumber());
    }

    /**
     * This method is used for VMAX3 to add volumes to parking storage group
     * once volumes are unexported.
     * 
     * @param storage
     * @param policyName
     * @param volumeDeviceIds
     * @throws Exception
     */
    private void addVolumesToParkingStorageGroup(StorageSystem storage, String policyName, Set<String> volumeDeviceIds) throws Exception {
        String[] tokens = policyName.split(Constants.SMIS_PLUS_REGEX);
        CIMObjectPath groupPath = _helper.getVolumeGroupBasedOnSLO(storage, tokens[0], tokens[1], tokens[2]);
        if (groupPath == null) {
            groupPath = _helper.createVolumeGroupBasedOnSLO(storage, tokens[0], tokens[1], tokens[2]);
        }
        CIMArgument[] inArgs = _helper.getAddVolumesToMaskingGroupInputArguments(storage, groupPath, volumeDeviceIds);
        CIMArgument[] outArgs = new CIMArgument[5];
        SmisJob addVolumesToSGJob = new SmisSynchSubTaskJob(null, storage.getId(),
                SmisConstants.ADD_MEMBERS);
        _helper.invokeMethodSynchronously(storage, _cimPath.getControllerConfigSvcPath(storage),
                "AddMembers", inArgs, outArgs, addVolumesToSGJob);
    }

    /**
     * Export mask operation rollback method.
     * 
     * @param storage storage device
     * @param taskCompleter task completer
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void exportMaskRollback(StorageSystem storage, ExportOperationContext context, TaskCompleter taskCompleter) throws Exception {
        // Go through each operation and roll it back.
        if (context.getOperations() != null) {
            WBEMClient client = _helper.getConnection(storage).getCimClient();
            ListIterator li = context.getOperations().listIterator(context.getOperations().size());
            while (li.hasPrevious()) {
                ExportOperationContextOperation operation = (ExportOperationContextOperation) li.previous();
                // Flag to indicate whether or not we need to use the EMCForce flag on this operation.
                // We currently use this flag when dealing with RP Volumes as they are tagged for RP and the
                // operation on these volumes would fail otherwise.
                //
                // IMPORTANT NOTE: Default is FALSE, each rollback method will need to determine if it should be set to
                // true if it is needed.
                boolean forceFlag = false;
                try {
                    switch (operation.getOperation()) {
                        case VmaxExportOperationContext.OPERATION_ADD_INITIATORS_TO_INITIATOR_GROUP:
                            // remove initiators from the initiator group
                            List<Initiator> initiatorList = (List<Initiator>) operation.getArgs().get(0);
                            CIMObjectPath initiatorGroupPath = (CIMObjectPath) operation.getArgs().get(1);
                            CIMArgument[] inArgs = _helper.getRemoveInitiatorsFromMaskingGroupInputArguments(storage, initiatorGroupPath,
                                    initiatorList);
                            CIMArgument[] outArgs = new CIMArgument[5];
                            _helper.invokeMethodSynchronously(storage, _cimPath.getControllerConfigSvcPath(storage),
                                    "RemoveMembers", inArgs, outArgs, null);
                            break;
                        case VmaxExportOperationContext.OPERATION_ADD_INITIATOR_GROUPS_TO_INITIATOR_GROUP:
                            // remove initiator groups from cascaded initiator group
                            CIMObjectPath childInitiatorGroup = (CIMObjectPath) operation.getArgs().get(0);
                            CIMObjectPath parentInitiatorGroup = (CIMObjectPath) operation.getArgs().get(1);
                            inArgs = _helper.getRemoveIGFromCIG(childInitiatorGroup, parentInitiatorGroup);
                            outArgs = new CIMArgument[5];
                            _helper.invokeMethodSynchronously(storage, _cimPath.getControllerConfigSvcPath(storage),
                                    "RemoveMembers", inArgs, outArgs, null);
                            break;
                        case VmaxExportOperationContext.OPERATION_CREATE_CASCADING_STORAGE_GROUP:
                        case VmaxExportOperationContext.OPERATION_CREATE_STORAGE_GROUP:
                            // Delete storage group
                            String groupName = (String) operation.getArgs().get(0);
                            // .get(2) arg is different depending on the operation, but for now we don't need it so we won't get it.
                            _helper.deleteMaskingGroup(storage, groupName, SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup);
                            break;
                        case VmaxExportOperationContext.OPERATION_CREATE_PORT_GROUP:
                            // Delete port group
                            groupName = (String) operation.getArgs().get(0);
                            _helper.deleteMaskingGroup(storage, groupName, SmisCommandHelper.MASKING_GROUP_TYPE.SE_TargetMaskingGroup);
                            break;
                        case VmaxExportOperationContext.OPERATION_ADD_TIER_TO_STORAGE_GROUP:
                            // Remove tier policy from storage group
                            String policyName = (String) operation.getArgs().get(0);
                            CIMObjectPath[] volumeGroupPaths = (CIMObjectPath[]) operation.getArgs().get(1);
                            for (CIMObjectPath volumeGroupPath : volumeGroupPaths) {
                                _helper.removeVolumeGroupFromPolicyAndLimitsAssociation(client, storage, volumeGroupPath);
                            }
                            break;
                        case VmaxExportOperationContext.OPERATION_ADD_STORAGE_GROUP_TO_CASCADING_STORAGE_GROUP:
                            // Remove storage group from cascading storage group
                            groupName = (String) operation.getArgs().get(0);
                            forceFlag = (boolean) operation.getArgs().get(2);
                            List<CIMObjectPath> volumeGroupPathList = (List<CIMObjectPath>) operation.getArgs().get(1);
                            inArgs = _helper.modifyCascadedStorageGroupInputArguments(
                                    storage, groupName, (CIMObjectPath[]) volumeGroupPathList.toArray(), forceFlag);
                            outArgs = new CIMArgument[5];
                            _helper.invokeMethodSynchronously(storage,
                                    _cimPath.getControllerConfigSvcPath(storage), "RemoveMembers", inArgs,
                                    outArgs, null);
                            break;
                        case VmaxExportOperationContext.OPERATION_CREATE_CASCADED_INITIATOR_GROUP:
                        case VmaxExportOperationContext.OPERATION_CREATE_INITIATOR_GROUP:
                            // Remove initiator group
                            groupName = (String) operation.getArgs().get(0);
                            _helper.deleteMaskingGroup(storage, groupName, SmisCommandHelper.MASKING_GROUP_TYPE.SE_InitiatorMaskingGroup);
                            break;
                        case VmaxExportOperationContext.OPERATION_CREATE_MASKING_VIEW:
                            // Remove masking view
                            String maskName = (String) operation.getArgs().get(0);

                            // Find the mask using the name
                            boolean foundMaskInDb = false;
                            ExportMask exportMask = null;
                            URIQueryResultList uriQueryList = new URIQueryResultList();
                            _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                                    .getExportMaskByNameConstraint(maskName), uriQueryList);
                            while (uriQueryList.iterator().hasNext()) {
                                URI uri = uriQueryList.iterator().next();
                                exportMask = _dbClient.queryObject(ExportMask.class, uri);
                                if (exportMask != null && !exportMask.getInactive() &&
                                        exportMask.getStorageDevice().equals(storage.getId())) {
                                    foundMaskInDb = true;
                                    // We're expecting there to be only one export mask of a
                                    // given name for any storage array.
                                    break;
                                }
                            }
                            // If we have the mask, check to see if we need to use the force flag
                            if (foundMaskInDb) {
                                for (String volURI : exportMask.getUserAddedVolumes().values()) {
                                    forceFlag = ExportUtils.useEMCForceFlag(_dbClient, URI.create(volURI));
                                    if (forceFlag) {
                                        break;
                                    }
                                }
                            }

                            inArgs = _helper.getDeleteMaskingViewInputArguments(storage, maskName, forceFlag);
                            outArgs = new CIMArgument[5];
                            _helper.invokeMethodSynchronously(storage,
                                    _cimPath.getControllerConfigSvcPath(storage),
                                    "DeleteMaskingView", inArgs, outArgs, null);
                            break;
                        case VmaxExportOperationContext.OPERATION_ADD_PORTS_TO_PORT_GROUP:
                            // Remove ports from port group
                            groupName = (String) operation.getArgs().get(0);
                            List<URI> targetURIList = (List<URI>) operation.getArgs().get(1);
                            inArgs = _helper.getRemoveTargetPortsFromMaskingGroupInputArguments(storage, groupName, targetURIList);
                            outArgs = new CIMArgument[5];
                            _helper.invokeMethodSynchronously(storage, _cimPath.getControllerConfigSvcPath(storage),
                                    "RemoveMembers", inArgs, outArgs, null);
                            break;
                        case VmaxExportOperationContext.OPERATION_ADD_VOLUMES_TO_STORAGE_GROUP:
                            // Remove volumes from storage group
                            groupName = (String) operation.getArgs().get(0);
                            forceFlag = (boolean) operation.getArgs().get(2);
                            VolumeURIHLU[] volumeList = (VolumeURIHLU[]) operation.getArgs().get(1);
                            List<URI> volumesInSG = new ArrayList<>();
                            for (VolumeURIHLU volumeUriHlu : volumeList) {
                                volumesInSG.add(volumeUriHlu.getVolumeURI());
                            }
                            inArgs = _helper.getRemoveVolumesFromMaskingGroupInputArguments(storage, groupName,
                                    volumesInSG, forceFlag);
                            outArgs = new CIMArgument[5];
                            _helper.invokeMethodSynchronously(storage, _cimPath.getControllerConfigSvcPath(storage),
                                    "RemoveMembers", inArgs, outArgs, null);
                            break;
                        default:
                    }
                } catch (Exception e) {
                    _log.error("Exception caught while running rollback", e);
                }
            }
        }
    }

    @Override
    public void addVolume(StorageSystem storage,
            URI exportMaskURI,
            VolumeURIHLU[] volumeURIHLUs,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} addVolume START...", storage.getSerialNumber());
        try {
            boolean isVmax3 = storage.checkIfVmax3();
            ExportOperationContext context = new VmaxExportOperationContext();
            // Prime the context object
            taskCompleter.updateWorkflowStepContext(context);
            String maskingViewName = _helper.getExportMaskName(exportMaskURI);
            // Always get the Storage Group from masking View, rather than depending on the name to find out SG.
            String parentGroupName = _helper.getStorageGroupForGivenMaskingView(maskingViewName, storage);
            // Neither masking view nor Storage Group exists. remove the volumes from the mask.
            // TODO I think we can delete the export mask too.
            if (null == parentGroupName) {
                List<URI> volumeURIList = new ArrayList<URI>();
                for (VolumeURIHLU vuh : volumeURIHLUs) {
                    volumeURIList.add(vuh.getVolumeURI());
                }
                ExportMask mask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
                mask.removeVolumes(volumeURIList);
                _dbClient.updateAndReindexObject(mask);
                taskCompleter.error(_dbClient, DeviceControllerException.errors
                        .vmaxStorageGroupNameNotFound(maskingViewName));
                return;
            }

            // Get the export mask initiator list. This is required to compute the storage group name
            ExportMask mask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
            Set<Initiator> initiators = ExportMaskUtils.getInitiatorsForExportMask(_dbClient, mask, null);

            // Flag to indicate whether or not we need to use the EMCForce flag on this operation.
            // We currently use this flag when dealing with RP Volumes as they are tagged for RP and the
            // operation on these volumes would fail otherwise.
            boolean forceFlag = false;

            // Determine if the volume is already in the masking view.
            // If so, log and remove from volumes we need to process.
            String storageGroup = _helper.getStorageGroupForGivenMaskingView(maskingViewName, storage);
            Set<String> deviceIds = _helper.getVolumeDeviceIdsFromStorageGroup(storage, storageGroup);
            List<VolumeURIHLU> removeURIs = new ArrayList<>();
            for (VolumeURIHLU volumeUriHLU : volumeURIHLUs) {
                BlockObject bo = BlockObject.fetch(_dbClient, volumeUriHLU.getVolumeURI());
                if (deviceIds.contains(bo.getNativeId())) {
                    _log.info("Found volume {} is already associated with masking view.  Assuming this is from a previous operation.",
                            bo.getLabel());
                    removeURIs.add(volumeUriHLU);
                }
            }

            // Create the new array of volumes that don't exist yet in the masking view.
            VolumeURIHLU[] addVolumeURIHLUs = new VolumeURIHLU[volumeURIHLUs.length - removeURIs.size()];
            int index = 0;
            for (VolumeURIHLU volumeUriHLU : volumeURIHLUs) {
                if (!removeURIs.contains(volumeUriHLU)) {
                    addVolumeURIHLUs[index++] = volumeUriHLU;
                }
            }

            /**
             * Group Volumes by Fast Policy and Host IO limit attributes
             * 
             * policyToVolumeGroupEntry - this will essentially have multiple Groups
             * E.g Group 1--> Fast Policy (FP1)+ FEBandwidth (100)
             * Group 2--> Fast Policy (FP2)+ IOPS (100)
             * Group 3--> FEBandwidth (100) + IOPS (100) ..
             */
            ListMultimap<StorageGroupPolicyLimitsParam, VolumeURIHLU> policyToVolumeGroup = ArrayListMultimap.create();
            for (VolumeURIHLU volumeUriHLU : addVolumeURIHLUs) {
                StorageGroupPolicyLimitsParam sgPolicyLimitsParam = null;
                URI boUri = volumeUriHLU.getVolumeURI();
                BlockObject bo = BlockObject.fetch(_dbClient, boUri);
                boolean fastAssociatedAlready = false;
                // Always treat fast volumes as non-fast if fast is associated on these volumes already
                // Export fast volumes to 2 different nodes.
                if (_helper.isFastPolicy(volumeUriHLU.getAutoTierPolicyName())) {
                    fastAssociatedAlready = _helper.checkVolumeAssociatedWithAnySGWithPolicy(bo.getNativeId(), storage,
                            volumeUriHLU.getAutoTierPolicyName());
                }

                // Force the policy name to NONE if any of the following conditions are true:
                // 1. FAST policy is already associated.
                // 2. The BO is a RP Journal - Per RecoverPoint best practices a journal volume
                // should not be created with a FAST policy assigned.
                if (fastAssociatedAlready || isRPJournalVolume(bo)) {
                    _log.info("Forcing policy name to NONE to prevent volume from using FAST policy.");
                    volumeUriHLU.setAutoTierPolicyName(Constants.NONE);
                    sgPolicyLimitsParam = new StorageGroupPolicyLimitsParam(Constants.NONE,
                            volumeUriHLU.getHostIOLimitBandwidth(),
                            volumeUriHLU.getHostIOLimitIOPs(),
                            storage);
                }
                else {
                    if (isVmax3) {
                        sgPolicyLimitsParam = new StorageGroupPolicyLimitsParam(volumeUriHLU, storage, _helper);
                    }
                    else {
                        sgPolicyLimitsParam = new StorageGroupPolicyLimitsParam(volumeUriHLU, storage);
                    }
                }

                policyToVolumeGroup.put(sgPolicyLimitsParam, volumeUriHLU);

                // The force flag only needs to be set once
                if (!forceFlag) {
                    forceFlag = ExportUtils.useEMCForceFlag(_dbClient, volumeUriHLU.getVolumeURI());
                }
            }

            _log.info(" {} Groups generated based on grouping volumes by fast policy", policyToVolumeGroup.size());

            String storageGroupCustomTemplateName = CustomConfigConstants.VMAX_HOST_STORAGE_GROUP_MASK_NAME;

            String exportType = ExportMaskUtils.getExportType(_dbClient, mask);
            if (ExportGroupType.Cluster.name().equals(exportType)) {
                storageGroupCustomTemplateName = CustomConfigConstants.VMAX_CLUSTER_STORAGE_GROUP_MASK_NAME;
            }

            DataSource sgDataSource = ExportMaskUtils.getExportDatasource(storage, new ArrayList<Initiator>(initiators), dataSourceFactory,
                    storageGroupCustomTemplateName);
            // Group volumes by Storage Group
            Map<String, Collection<VolumeURIHLU>> volumesByStorageGroup = _helper.groupVolumesByStorageGroup(storage, parentGroupName,
                    sgDataSource, storageGroupCustomTemplateName, policyToVolumeGroup, customConfigHandler);

            // Loop through each Storage Group and find if exists. If it does not, create a new Child Group and
            // associate it with fast if needed, then add the new Child Group to the existing Parent Cascaded Group
            for (Entry<String, Collection<VolumeURIHLU>> volumesByStorageGroupEntry : volumesByStorageGroup.entrySet()) {
                VolumeURIHLU[] volumeURIHLUArray = null;

                // Even though policy and limits info are already in volumURIHLU, let's still extract to a holder for easy access
                StorageGroupPolicyLimitsParam volumePolicyLimitsParam = new StorageGroupPolicyLimitsParam(Constants.NONE);
                if (null != volumesByStorageGroupEntry.getValue()) {
                    volumeURIHLUArray = volumesByStorageGroupEntry.getValue().toArray(new VolumeURIHLU[0]);
                    volumePolicyLimitsParam = _helper.createStorageGroupPolicyLimitsParam(volumesByStorageGroupEntry.getValue(), storage,
                            _dbClient);
                }

                String childGroupName = volumesByStorageGroupEntry.getKey().toString();
                if (isVmax3) {
                    childGroupName = childGroupName.replaceAll(Constants.SMIS_PLUS_REGEX, Constants.UNDERSCORE_DELIMITER);
                }
                _log.info("Group Name : {} --- volume storage group name : {}", childGroupName,
                        (volumeURIHLUArray != null ? Joiner.on("\t").join(volumeURIHLUArray) : ""));
                CIMObjectPath childGroupPath = _cimPath.getMaskingGroupPath(storage,
                        childGroupName,
                        SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup);
                CIMInstance childGroupInstance = _helper.checkExists(storage,
                        childGroupPath, false, false);

                if (null == childGroupInstance) {
                    // For the newly created group, set policy, and other required properties bandwidth, iops as needed.
                    // Once child group created, add this child Group to existing Parent Cascaded Group.
                    // Empty child group is created to be able to add volumes with HLUs
                    // once this child group is added in the cascaded storage group.
                    _log.info("Group {} doesn't exist, creating a new group", childGroupName);
                    CIMObjectPath childGroupCreated = createVolumeGroup(storage,
                            childGroupName, volumeURIHLUArray, taskCompleter, false);
                    _log.debug("Created Child Group {}", childGroupCreated);
                    // Get childGroupName from the child group path childGroupCreated as it
                    // could have been truncated in createVolumeGroup method
                    childGroupName = _cimPath.getMaskingGroupName(storage, childGroupCreated);

                    if (!isVmax3 && _helper.isFastPolicy(volumePolicyLimitsParam.getAutoTierPolicyName())
                            && !_helper.checkVolumeGroupAssociatedWithPolicy(storage,
                                    childGroupCreated, volumePolicyLimitsParam.getAutoTierPolicyName())) {
                        _log.debug("Adding Storage Group {} to Fast Policy {}", childGroupName,
                                volumePolicyLimitsParam.getAutoTierPolicyName());
                        addVolumeGroupToAutoTieringPolicy(storage, volumePolicyLimitsParam.getAutoTierPolicyName(),
                                childGroupCreated, taskCompleter);
                    }

                    // Add new Child Storage Group to Parent Cascaded Group
                    // NOTE: host IO limit is set during the first child SG created with cascaded group.
                    // Logically (or easy to follow) is to set limits after SG is created. However, because add job logic
                    // is executed in a queue. Hence, move update logic to SmisMaskingViewAddVolumeJob
                    addGroupsToCascadedVolumeGroup(storage, parentGroupName, childGroupCreated, null, taskCompleter, forceFlag);
                    _log.debug("Added newly created Storage Group {} to Parent Cascaded Group {}", childGroupName, parentGroupName);

                    // Add volumes to the newly created child group
                    _log.info("Adding Volumes to non cascaded Storage Group {} START",
                            childGroupName);
                    // Create a relatively empty completer associated with the export mask. We don't have the export group
                    // at this level, so there's nothing decent to attach the completer to anyway.
                    String task = UUID.randomUUID().toString();
                    ExportMaskVolumeToStorageGroupCompleter completer =
                            new ExportMaskVolumeToStorageGroupCompleter(null, exportMaskURI, task);
                    SmisMaskingViewAddVolumeJob job = new SmisMaskingViewAddVolumeJob(
                            null, storage.getId(), exportMaskURI, volumeURIHLUArray, childGroupCreated, completer);
                    job.setCIMObjectPathfactory(_cimPath);
                    _helper.addVolumesToStorageGroup(volumeURIHLUArray, storage, childGroupName, job, forceFlag);
                    ExportOperationContext.insertContextOperation(taskCompleter,
                            VmaxExportOperationContext.OPERATION_ADD_VOLUMES_TO_STORAGE_GROUP,
                            childGroupName, volumeURIHLUArray, forceFlag);
                    _log.info("Adding Volumes to non cascaded Storage Group {} END",
                            childGroupName);
                }
                // Only reusable groups will have null volumeURIHLUArray
                else if (null != volumeURIHLUArray) {
                    // Only add volumes that don't already exist in the StorageGroup
                    VolumeURIHLU[] newVolumesToAdd =
                            getVolumesThatAreNotAlreadyInSG(storage, childGroupPath,
                                    childGroupName, volumesByStorageGroupEntry.getValue().toArray(new VolumeURIHLU[0]));
                    if (newVolumesToAdd != null) {
                        // We should not disturb any existing masking views on the Array. In this case, if the found storage group is not
                        // associated with expected fast policy, then we cannot proceed with neither of the below
                        // 1. Adding fast volumes to existing non fast storage group part of masking view
                        // 2. Creating a new Cascaded Group, and adding the existing storage group to it, as existing SG-->MV needs to be
                        // broken

                        StorageGroupPolicyLimitsParam childGroupKey = _helper.createStorageGroupPolicyLimitsParam(storage,
                                childGroupInstance);
                        if (!isVmax3 && _helper.isFastPolicy(volumePolicyLimitsParam.getAutoTierPolicyName())
                                && !_helper.isFastPolicy(childGroupKey.getAutoTierPolicyName())) {

                            // Go through each volume associated with this storage group and pluck out by policy
                            for (StorageGroupPolicyLimitsParam phantomStorageGroupPolicyLimitsParam : policyToVolumeGroup.keySet()) {
                                String phantomPolicyName = phantomStorageGroupPolicyLimitsParam.getAutoTierPolicyName();
                                // If this is a non-FAST policy in the grouping, skip it.
                                if (!_helper.isFastPolicy(phantomPolicyName)) {
                                    continue;
                                }

                                // Find the volumes in the volumeURIHLU associated with this policy
                                VolumeURIHLU[] phantomVolumesToAdd =
                                        getVolumesThatBelongToPolicy(newVolumesToAdd, phantomStorageGroupPolicyLimitsParam, storage);

                                _log.info(String
                                        .format("In order to add this volume to the masking view, we need to create/find a storage group "
                                                +
                                                "for the FAST policy %s and add the volume to the storage group and the existing storage group associated with the masking view",
                                                phantomStorageGroupPolicyLimitsParam));

                                // Check to see if there already is a phantom storage group with this policy on the array
                                List<String> phantomStorageGroupNames = _helper.findPhantomStorageGroupAssociatedWithFastPolicy(storage,
                                        phantomStorageGroupPolicyLimitsParam);

                                // If there's no existing phantom storage group, create one.
                                if (phantomStorageGroupNames == null || phantomStorageGroupNames.isEmpty()) {
                                    // TODO: Probably need to use some name generator for this.
                                    String phantomStorageGroupName = childGroupName + "_" + phantomStorageGroupPolicyLimitsParam;
                                    // We need to create a new phantom storage group with our policy associated with it.
                                    _log.info("phantom storage group {} doesn't exist, creating a new group", phantomStorageGroupName);
                                    CIMObjectPath phantomStorageGroupCreated = createVolumeGroup(storage,
                                            phantomStorageGroupName, phantomVolumesToAdd, taskCompleter, true);
                                    _log.info("Adding Storage Group {} to Fast Policy {}", phantomStorageGroupName, phantomPolicyName);
                                    addVolumeGroupToAutoTieringPolicy(storage, phantomPolicyName,
                                            phantomStorageGroupCreated, taskCompleter);
                                } else {
                                    // take the first matching phantom SG from the list
                                    String phantomStorageGroupName = phantomStorageGroupNames.get(0);
                                    // We found the phantom storage group, but we need to make sure the volumes aren't already in the
                                    // storage
                                    // group from a previous operation or manual intervention.
                                    List<URI> phantomVolumeIds = new ArrayList<URI>();
                                    for (VolumeURIHLU phantomVolume : phantomVolumesToAdd) {
                                        phantomVolumeIds.add(phantomVolume.getVolumeURI());
                                    }
                                    phantomVolumeIds.removeAll(_helper.findVolumesInStorageGroup(storage, phantomStorageGroupName,
                                            phantomVolumeIds));
                                    if (phantomVolumeIds.isEmpty()) {
                                        _log.info("Found that the volume(s) we wanted to add to the phantom storage group are already added.");
                                    } else {
                                        // If we found a phantom storage group with our policy, use it.
                                        _log.info("Found that we need to add volumes to the phantom storage group: "
                                                + phantomStorageGroupName);
                                        // Create a relatively empty completer associated with the export mask. We don't have the export
                                        // group
                                        // at this level, so there's nothing decent to attach the completer to anyway.
                                        String task = UUID.randomUUID().toString();
                                        ExportMaskVolumeToStorageGroupCompleter completer =
                                                new ExportMaskVolumeToStorageGroupCompleter(null, exportMaskURI, task);
                                        SmisMaskingViewAddVolumeJob job = new SmisMaskingViewAddVolumeJob(
                                                null, storage.getId(), exportMaskURI, phantomVolumesToAdd, null, completer);
                                        job.setCIMObjectPathfactory(_cimPath);
                                        _helper.addVolumesToStorageGroup(phantomVolumesToAdd, storage, phantomStorageGroupName, job,
                                                forceFlag);
                                        ExportOperationContext.insertContextOperation(taskCompleter,
                                                VmaxExportOperationContext.OPERATION_ADD_VOLUMES_TO_STORAGE_GROUP, phantomStorageGroupName,
                                                phantomVolumesToAdd, forceFlag);
                                        _log.info("Adding Volumes to non cascaded Storage Group {} END",
                                                phantomStorageGroupName);
                                    }
                                }
                            }
                        }

                        if (isVmax3) {
                            // Remove volumes from the parking storage group
                            for (VolumeURIHLU volURIHLU : newVolumesToAdd) {
                                BlockObject bo = BlockObject.fetch(_dbClient, volURIHLU.getVolumeURI());
                                _helper.removeVolumeFromParkingSLOStorageGroup(storage, bo.getNativeId(), forceFlag);
                            }
                        }

                        _log.info("Adding Volumes to non cascaded Storage Group {} START",
                                childGroupName);
                        // Create a relatively empty completer associated with the export mask. We don't have the export group
                        // at this level, so there's nothing decent to attach the completer to anyway.
                        String task = UUID.randomUUID().toString();
                        ExportMaskVolumeToStorageGroupCompleter completer =
                                new ExportMaskVolumeToStorageGroupCompleter(null, exportMaskURI, task);
                        SmisMaskingViewAddVolumeJob job = new SmisMaskingViewAddVolumeJob(
                                null, storage.getId(), exportMaskURI, newVolumesToAdd, null, completer);
                        job.setCIMObjectPathfactory(_cimPath);
                        _helper.addVolumesToStorageGroup(newVolumesToAdd, storage, childGroupName, job, forceFlag);
                        ExportOperationContext.insertContextOperation(taskCompleter,
                                VmaxExportOperationContext.OPERATION_ADD_VOLUMES_TO_STORAGE_GROUP,
                                childGroupName, newVolumesToAdd, forceFlag);
                        _log.info("Adding Volumes to non cascaded Storage Group {} END",
                                childGroupName);
                    } else {
                        // newVolumesToAdd == null, implying that all the requested
                        // volumes are already in the StorageGroup. This is a no-op.
                        _log.info("All volumes are already in the storage group");
                    }
                }
                // Else, we have existing groups which hold these volumes, hence add those groups to
                // cascaded group.
                else {
                    _log.info("Adding Existing Storage Group {} to Cascaded Group {} START :",
                            childGroupName, parentGroupName);

                    if (!isVmax3 && _helper.isFastPolicy(volumePolicyLimitsParam.getAutoTierPolicyName())
                            && !_helper.checkVolumeGroupAssociatedWithPolicy(storage,
                                    childGroupPath, volumePolicyLimitsParam.getAutoTierPolicyName())) {
                        _log.info("Adding Storage Group {} to Fast Policy {}", childGroupName,
                                volumePolicyLimitsParam.getAutoTierPolicyName());
                        addVolumeGroupToAutoTieringPolicy(storage, volumePolicyLimitsParam.getAutoTierPolicyName(), childGroupPath,
                                taskCompleter);
                    }
                    String task = UUID.randomUUID().toString();
                    ExportMaskVolumeToStorageGroupCompleter completer =
                            new ExportMaskVolumeToStorageGroupCompleter(null, exportMaskURI, task);
                    SmisMaskingViewAddVolumeJob job = new SmisMaskingViewAddVolumeJob(null,
                            storage.getId(), exportMaskURI, volumeURIHLUArray, null, completer);
                    job.setCIMObjectPathfactory(_cimPath);
                    addGroupsToCascadedVolumeGroup(storage, parentGroupName, childGroupPath, job, taskCompleter, forceFlag);
                    _log.debug("Adding Existing Storage Group {} to Cascaded Group {} END :",
                            childGroupName, parentGroupName);
                }
            }
            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _log.error(String.format("addVolume failed - maskName: %s", exportMaskURI.toString()), e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(_dbClient, serviceError);
        }
        _log.info("{} addVolume END...", storage.getSerialNumber());
    }

    /**
     * Convenience method that collects volumes from the array that match the storage group.
     * 
     * @param volumeURIHLUs volume objects
     * @param storageGroupPolicyLimitsParam storage group attributes
     * @return thinned-out list of volume objects that correspond to the supplied policy, or null
     */
    private VolumeURIHLU[] getVolumesThatBelongToPolicy(
            VolumeURIHLU[] volumeURIHLUs, StorageGroupPolicyLimitsParam storageGroupPolicyLimitsParam, StorageSystem storage) {
        VolumeURIHLU[] result = null;

        Set<VolumeURIHLU> volumeURIHLUSet = new HashSet<VolumeURIHLU>();
        for (VolumeURIHLU volumeURIHLU : volumeURIHLUs) {
            StorageGroupPolicyLimitsParam tmpKey = new StorageGroupPolicyLimitsParam(volumeURIHLU.getAutoTierPolicyName(),
                    volumeURIHLU.getHostIOLimitBandwidth(), volumeURIHLU.getHostIOLimitIOPs(), storage);

            if (tmpKey.equals(storageGroupPolicyLimitsParam)) {
                volumeURIHLUSet.add(volumeURIHLU);
            }
        }

        if (!volumeURIHLUSet.isEmpty()) {
            result = new VolumeURIHLU[volumeURIHLUSet.size()];
            result = volumeURIHLUSet.toArray(result);
        }
        return result;
    }

    @Override
    public void removeVolume(StorageSystem storage,
            URI exportMaskURI,
            List<URI> volumeURIList,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} removeVolume START...", storage.getSerialNumber());

        try {
            boolean isVmax3 = storage.checkIfVmax3();
            WBEMClient client = _helper.getConnection(storage).getCimClient();
            // Get the context from the task completer, in case this is a rollback.
            ExportOperationContext context = (ExportOperationContext) WorkflowService.getInstance().loadStepData(taskCompleter.getOpId());
            if (context != null) {
                exportMaskRollback(storage, context, taskCompleter);
                taskCompleter.ready(_dbClient);
                return;
            } else {
                String maskingViewName = _helper.getExportMaskName(exportMaskURI);
                // Always get the Storage Group from masking View, rather than depending on the name to find out SG.
                String parentGroupName = _helper.getStorageGroupForGivenMaskingView(maskingViewName, storage);
                // Neither masking view nor Storage Group exists. remove the volumes from the mask.
                // TODO I think we can delete the export mask too.
                if (null == parentGroupName) {
                    ExportMask mask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
                    mask.removeVolumes(volumeURIList);
                    _dbClient.updateAndReindexObject(mask);
                    taskCompleter.error(_dbClient, DeviceControllerException.errors
                            .vmaxStorageGroupNameNotFound(maskingViewName));
                    return;
                }

                Map<String, List<URI>> volumesByGroup = _helper.groupVolumesBasedOnExistingGroups(storage, parentGroupName, volumeURIList);
                _log.info("Group Volumes by Storage Group size : {}", volumesByGroup.size());

                if (volumesByGroup.size() == 0) {
                    _log.info("Could not find any groups to which the volumes to remove belong.");
                    taskCompleter.ready(_dbClient);
                    return;
                }

                /**
                 * For each child Group bucket, remove the volumes from those bucket
                 * 
                 */
                for (Entry<String, List<URI>> volumeByGroupEntry : volumesByGroup.entrySet()) {
                    String childGroupName = volumeByGroupEntry.getKey();
                    volumeURIList = volumeByGroupEntry.getValue();
                    _log.info("Processing Group {} with volumes {}", childGroupName, Joiner.on("\t").join(volumeURIList));
                    /**
                     * Empty child Storage Groups cannot be associated with Fast Policy.
                     * hence, verify if storage group size is > 1, if not, then remove the
                     * child group from Fast Policy, and then proceed with removing the volume from group
                     */
                    if (volumesByGroup.get(childGroupName) != null && volumesByGroup.get(childGroupName).size() == volumeURIList.size() &&
                            !_helper.isStorageGroupSizeGreaterThanGivenVolumes(childGroupName, storage, volumeURIList.size())) {
                        _log.info("Storage Group has no more than {} volumes", volumeURIList.size());
                        URI blockURI = volumeURIList.get(0);
                        BlockObject blockObj = BlockObject.fetch(_dbClient, blockURI);
                        CIMObjectPath maskingGroupPath = _cimPath.getMaskingGroupPath(
                                storage, childGroupName,
                                SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup);
                        String policyName = ControllerUtils.getAutoTieringPolicyName(blockObj.getId(), _dbClient);
                        if (!isVmax3 && !Constants.NONE.equalsIgnoreCase(policyName)) {
                            _log.info(
                                    "Storage Group contains only 1 volume, hence this group will be disassociated from fast, as fast cannot be applied to empty group {}",
                                    childGroupName);
                            _helper.removeVolumeGroupFromPolicyAndLimitsAssociation(client, storage, maskingGroupPath);
                        }
                    }

                    Set<String> volumeDeviceIds = new HashSet<String>();
                    // Flag to indicate whether or not we need to use the EMCForce flag on this operation.
                    // We currently use this flag when dealing with RP Volumes as they are tagged for RP and the
                    // operation on these volumes would fail otherwise.
                    boolean forceFlag = false;
                    for (URI volURI : volumeURIList) {
                        BlockObject bo = Volume.fetchExportMaskBlockObject(
                                _dbClient, volURI);
                        volumeDeviceIds.add(bo.getNativeId());
                        // The force flag only needs to be set once
                        if (!forceFlag) {
                            forceFlag = ExportUtils.useEMCForceFlag(_dbClient, volURI);
                        }
                    }

                    List<CIMObjectPath> volumePaths = new ArrayList<CIMObjectPath>();

                    // Determine if the volumes are associated with any phantom storage groups.
                    // If so, we need to remove volumes from those storage groups and potentially remove them.
                    removeVolumesFromPhantomStorageGroup(storage, client,
                            exportMaskURI, volumeURIList, childGroupName,
                            forceFlag);

                    // Create a relatively empty completer associated with the export mask. We don't have the export group
                    // at this level, so there's nothing decent to attach the completer to anyway.
                    String task = UUID.randomUUID().toString();
                    ExportMaskVolumeToStorageGroupCompleter completer =
                            new ExportMaskVolumeToStorageGroupCompleter(null, exportMaskURI, task);
                    List<URI> volumesInSG = _helper.findVolumesInStorageGroup(storage, childGroupName, volumeURIList);
                    if (volumesInSG != null && !volumesInSG.isEmpty()) {
                        CIMArgument[] inArgs = _helper.getRemoveVolumesFromMaskingGroupInputArguments(storage, childGroupName,
                                volumesInSG, forceFlag);
                        CIMArgument[] outArgs = new CIMArgument[5];
                        _helper.invokeMethodSynchronously(storage, _cimPath.getControllerConfigSvcPath(storage),
                                "RemoveMembers", inArgs, outArgs, new SmisMaskingViewRemoveVolumeJob(null,
                                        storage.getId(), volumePaths, parentGroupName, childGroupName, _cimPath, completer));
                        if (isVmax3) {
                            // we need to add volumes to parking storage group
                            URI blockObjectURI = volumeURIList.get(0);
                            String policyName = _helper.getVMAX3FastSettingForVolume(blockObjectURI, null);
                            addVolumesToParkingStorageGroup(storage, policyName, volumeDeviceIds);
                        }
                    } else {
                        completer.ready(_dbClient);
                    }

                }
                taskCompleter.ready(_dbClient);
            }
        } catch (Exception e) {
            _log.error(String.format("removeVolume failed - maskName: %s", exportMaskURI.toString()), e);
            ServiceError serviceError = null;
            if (null != e.getMessage() && e.getMessage().contains("FAST association cannot have an empty storage group")) {
                serviceError = DeviceControllerException.errors.concurrentRemoveFromSGCausesEmptySG(e);
            } else {
                serviceError = DeviceControllerException.errors.jobFailed(e);
            }
            taskCompleter.error(_dbClient, serviceError);
        }
        _log.info("{} removeVolume END...", storage.getSerialNumber());
    }

    /**
     * Removes the volumes from any phantom storage group.
     * 
     * Determine if the volumes are associated with any phantom storage groups.
     * If so, we need to remove volumes from those storage groups and potentially remove them.
     */
    private void removeVolumesFromPhantomStorageGroup(StorageSystem storage,
            WBEMClient client, URI exportMaskURI, List<URI> volumeURIList,
            String childGroupName, boolean forceFlag) throws Exception {

        CloseableIterator<CIMObjectPath> volumePathItr = null;
        try {
            Map<StorageGroupPolicyLimitsParam, List<URI>> policyVolumeMap = _helper.groupVolumesBasedOnFastPolicy(storage, volumeURIList);
            for (StorageGroupPolicyLimitsParam storageGroupPolicyLimitsParam : policyVolumeMap.keySet()) {

                if (!_helper.isFastPolicy(storageGroupPolicyLimitsParam.getAutoTierPolicyName())) {
                    continue;
                }

                _log.info("Checking if volumes are associated with phantom storage groups with policy name: "
                        + storageGroupPolicyLimitsParam);

                // See if there's a phantom group with this policy
                List<String> storageGroupNames = _helper.findPhantomStorageGroupAssociatedWithFastPolicy(storage,
                        storageGroupPolicyLimitsParam);

                // We found a phantom storage group
                if (storageGroupNames != null) {
                    for (String storageGroupName : storageGroupNames) {
                        List<URI> volumesToRemove = new ArrayList<URI>();
                        List<Volume> volumes = _dbClient.queryObject(Volume.class, policyVolumeMap.get(storageGroupPolicyLimitsParam));

                        // Get the volumes associated with this storage group. Match up with our volumes.
                        volumePathItr = _helper.getAssociatorNames(storage,
                                _cimPath.getStorageGroupObjectPath(storageGroupName, storage), null,
                                SmisConstants.CIM_STORAGE_VOLUME, null, null);
                        while (volumePathItr.hasNext()) {
                            CIMObjectPath volumePath = volumePathItr.next();
                            for (Volume volume : volumes) {
                                if (volume.getNativeGuid().equalsIgnoreCase(_helper.getVolumeNativeGuid(volumePath))) {
                                    _log.info("Found volume " + volume.getLabel() + " is in phantom storage group " + storageGroupName);
                                    volumesToRemove.add(volume.getId());
                                }
                            }
                        }

                        // Check to see if the volumes are associated with other non-fast, non-cascading masking views.
                        // If so, we should not remove that volume from the phantom storage group because another view is relying on
                        // it being there.
                        List<URI> inMoreViewsVolumes = new ArrayList<URI>();
                        for (URI volumeToRemove : volumesToRemove) {
                            if (_helper.isPhantomVolumeInMultipleMaskingViews(storage, volumeToRemove, childGroupName)) {
                                Volume volume = _dbClient.queryObject(Volume.class, volumeToRemove);
                                _log.info("Volume " + volume.getLabel()
                                        + " is in other masking views, so we will not remove it from storage group " + storageGroupName);
                                inMoreViewsVolumes.add(volume.getId());
                            }
                        }
                        volumesToRemove.removeAll(inMoreViewsVolumes);

                        // If we found volumes in phantom storage groups associated with this policy, we need to remove them
                        // from the phantom storage group.
                        if (!volumesToRemove.isEmpty()) {
                            _log.info(String.format("Going to remove volumes %s from phantom storage group %s",
                                    Joiner.on("\t").join(volumesToRemove), storageGroupName));

                            Map<String, List<URI>> phantomGroupVolumeMap = _helper.groupVolumesBasedOnExistingGroups(storage,
                                    storageGroupName, volumesToRemove);
                            if (phantomGroupVolumeMap != null && phantomGroupVolumeMap.get(storageGroupName) != null &&
                                    phantomGroupVolumeMap.get(storageGroupName).size() == volumesToRemove.size() &&
                                    !_helper.isStorageGroupSizeGreaterThanGivenVolumes(storageGroupName, storage, volumesToRemove.size())) {
                                _log.info("Storage Group has no more than {} volumes", volumesToRemove.size());
                                URI blockURI = volumesToRemove.get(0);
                                BlockObject blockObj = BlockObject.fetch(_dbClient, blockURI);
                                CIMObjectPath maskingGroupPath = _cimPath.getMaskingGroupPath(
                                        storage, storageGroupName,
                                        SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup);
                                String foundPolicyName = ControllerUtils.getAutoTieringPolicyName(blockObj.getId(), _dbClient);
                                if (_helper.isFastPolicy(foundPolicyName)
                                        && storageGroupPolicyLimitsParam.getAutoTierPolicyName().equalsIgnoreCase(foundPolicyName)) {
                                    _log.info(
                                            "Storage Group {} contains only 1 volume, so this group will be disassociated from FAST because group can not be deleted if associated with FAST",
                                            storageGroupName);
                                    _helper.removeVolumeGroupFromPolicyAndLimitsAssociation(client, storage, maskingGroupPath);
                                }
                            }

                            // Create a relatively empty completer associated with the export mask. We don't have the export group
                            // at this level, so there's nothing decent to attach the completer to anyway.
                            String task = UUID.randomUUID().toString();
                            ExportMaskVolumeToStorageGroupCompleter completer =
                                    new ExportMaskVolumeToStorageGroupCompleter(null, exportMaskURI, task);
                            List<URI> volumesInSG = _helper.findVolumesInStorageGroup(storage, storageGroupName, volumesToRemove);

                            List<CIMObjectPath> volumePaths = new ArrayList<CIMObjectPath>();

                            // Remove the found volumes from the phantom storage group
                            if (volumesInSG != null && !volumesInSG.isEmpty()) {
                                CIMArgument[] inArgs = _helper.getRemoveVolumesFromMaskingGroupInputArguments(storage, storageGroupName,
                                        volumesInSG, forceFlag);
                                CIMArgument[] outArgs = new CIMArgument[5];
                                _helper.invokeMethodSynchronously(storage, _cimPath.getControllerConfigSvcPath(storage),
                                        "RemoveMembers", inArgs, outArgs, new SmisMaskingViewRemoveVolumeJob(null,
                                                storage.getId(), volumePaths, null, storageGroupName, _cimPath, completer));
                            }
                        }
                    }
                }
            }
        } finally {
            if (volumePathItr != null) {
                volumePathItr.close();
            }
        }
    }

    @Override
    public void addInitiator(StorageSystem storage, URI exportMaskURI,
            List<Initiator> initiatorList, List<URI> targetURIList, TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} addInitiator START...", storage.getSerialNumber());
        try {
            ExportOperationContext context = new VmaxExportOperationContext();
            // Prime the context object
            taskCompleter.updateWorkflowStepContext(context);
            ExportMask mask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
            String cascadedIGCustomTemplateName = CustomConfigConstants.VMAX_HOST_CASCADED_IG_MASK_NAME;
            String initiatorGroupCustomTemplateName = CustomConfigConstants.VMAX_HOST_INITIATOR_GROUP_MASK_NAME;

            String exportType = ExportMaskUtils.getExportType(_dbClient, mask);
            if (ExportGroupType.Cluster.name().equals(exportType)) {
                cascadedIGCustomTemplateName = CustomConfigConstants.VMAX_CLUSTER_CASCADED_IG_MASK_NAME;
                initiatorGroupCustomTemplateName = CustomConfigConstants.VMAX_CLUSTER_INITIATOR_GROUP_MASK_NAME;
            }

            // Get the export mask complete initiator list. This is required to compute the storage group name
            Set<Initiator> initiators = ExportMaskUtils.getInitiatorsForExportMask(_dbClient, mask, null);

            DataSource cascadedIGDataSource = ExportMaskUtils.getExportDatasource(storage, new ArrayList<Initiator>(initiators),
                    dataSourceFactory, cascadedIGCustomTemplateName);
            String cigName = customConfigHandler.getComputedCustomConfigValue(cascadedIGCustomTemplateName, storage.getSystemType(),
                    cascadedIGDataSource);

            createOrUpdateInitiatorGroups(storage, exportMaskURI, cigName, initiatorGroupCustomTemplateName,
                    initiatorList, taskCompleter);

            ExportMask exportMask =
                    _dbClient.queryObject(ExportMask.class, exportMaskURI);

            if (targetURIList != null && !targetURIList.isEmpty() &&
                    !exportMask.hasTargets(targetURIList)) {
                _log.info("Adding targets...");
                // always get the port group from the masking view
                CIMInstance portGroupInstance = _helper.getPortGroupInstance(storage, mask.getMaskName());
                if (null == portGroupInstance) {
                    String errMsg = String.format("addInitiator failed - maskName %s : Port group not found ", mask.getMaskName());
                    ServiceError serviceError = DeviceControllerException.errors.jobFailedMsg(errMsg, null);
                    taskCompleter.error(_dbClient, serviceError);
                    return;
                }
                String pgGroupName = (String) portGroupInstance.getPropertyValue(SmisConstants.CP_ELEMENT_NAME);

                // Get the current ports off of the storage group; only add the ones that aren't there already.
                WBEMClient client = _helper.getConnection(storage).getCimClient();
                List<String> storagePorts =
                        _helper.getStoragePortsFromLunMaskingInstance(client,
                                portGroupInstance);
                Set<URI> storagePortURIs = new HashSet<>();
                storagePortURIs.addAll(Collections2.transform(ExportUtils.storagePortNamesToURIs(_dbClient, storagePorts),
                        CommonTransformerFunctions.FCTN_STRING_TO_URI));
                // Google Sets.difference returns a non-serializable set, so drop it into a standard HashSet upon return.
                List<URI> diffPorts = new ArrayList<URI>(Sets.difference(Sets.newHashSet(targetURIList), storagePortURIs));

                if (!diffPorts.isEmpty()) {
                    CIMArgument[] inArgs = _helper.getAddTargetsToMaskingGroupInputArguments(storage, portGroupInstance.getObjectPath(),
                            mask.getMaskName(), Lists.newArrayList(diffPorts));
                    CIMArgument[] outArgs = new CIMArgument[5];
                    _helper.invokeMethodSynchronously(storage, _cimPath.getControllerConfigSvcPath(storage),
                            "AddMembers", inArgs, outArgs, null);
                    ExportOperationContext.insertContextOperation(taskCompleter,
                            VmaxExportOperationContext.OPERATION_ADD_PORTS_TO_PORT_GROUP, pgGroupName,
                            diffPorts);
                } else {
                    _log.info(String.format("Target ports already added to port group %s, likely by a previous operation.", pgGroupName));
                }

                _dbClient.updateAndReindexObject(exportMask);
            }
            _log.info(String.format("addInitiator succeeded - maskName: %s", exportMaskURI.toString()));
            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _log.error(String.format("addInitiator failed - maskName: %s", exportMaskURI.toString()), e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(_dbClient, serviceError);
        }
        _log.info("{} addInitiator END...", storage == null ? null : storage.getSerialNumber());
    }

    @Override
    public void removeInitiator(StorageSystem storage,
            URI exportMaskURI,
            List<Initiator> initiatorList,
            List<URI> targetURIList,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} removeInitiator START...", storage == null ? null : storage.getSerialNumber());
        String clusterName = getClusterNameFromInitiators(initiatorList);
        if (clusterName == null) {
            final String logMsg = "All initiators should belong to the same cluster or not have a cluster name at all";
            _log.error(String.format("removeInitiator failed - maskName: %s", exportMaskURI.toString()), logMsg);
            String opName = ResourceOperationTypeEnum.DELETE_EXPORT_INITIATOR.getName();
            ServiceError serviceError = DeviceControllerException.errors.jobFailedOp(opName);
            taskCompleter.error(_dbClient, serviceError);
            return;
        } else {
            CloseableIterator<CIMInstance> cigInstances = null;
            try {
                // Get the context from the task completer, in case this is a rollback.
                ExportOperationContext context = (ExportOperationContext) WorkflowService.getInstance().loadStepData(
                        taskCompleter.getOpId());
                if (context != null) {
                    exportMaskRollback(storage, context, taskCompleter);
                } else {
                    CIMArgument[] inArgs;
                    CIMArgument[] outArgs;
                    _log.info("Removing initiators ...");
                    // Create a mapping of the InitiatorPort String to Initiator.
                    Map<String, Initiator> nameToInitiator = new HashMap<String, Initiator>();
                    for (Initiator initiator : initiatorList) {
                        String normalizedName = Initiator.normalizePort(initiator.getInitiatorPort());
                        nameToInitiator.put(normalizedName, initiator);
                    }
                    // We're going to get a mapping of which InitiatorGroups the initiators belong.
                    // With this mapping we can remove initiators from their associated IGs sequentially
                    ListMultimap<CIMObjectPath, String> igToInitiators = ArrayListMultimap.create();
                    mapInitiatorsToInitiatorGroups(igToInitiators, storage, initiatorList);
                    for (CIMObjectPath igPath : igToInitiators.keySet()) {
                        List<String> initiatorPorts = igToInitiators.get(igPath);
                        List<Initiator> initiatorsForIG = new ArrayList<Initiator>();
                        // Using the mapping, create a list of Initiator objects
                        for (String port : initiatorPorts) {
                            Initiator initiator = nameToInitiator.get(port);
                            if (initiator != null) {
                                initiatorsForIG.add(initiator);
                            }
                        }
                        boolean removingAllPortsInIG = initiatorPorts.size() == initiatorsForIG.size();
                        if (removingAllPortsInIG) {
                            // We are apparently trying to remove all the initiators in an Initiator Group.
                            // This is a special condition. It is not a case of removing the initiators
                            // from an individual group, we will instead treat this as a removal of the
                            // IG from the cascade-IG (thereby preventing access to the host pointed to
                            // by this IG).
                            _log.info(String.format(
                                    "Request to remove all the initiators from IG %s, so we will remove the IG from the cascaded-IG",
                                    igPath.toString()));
                            ExportMask mask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
                            CIMObjectPath cigInMVPath = null;
                            CIMInstance mvInstance = _helper.getSymmLunMaskingView(storage, mask);
                            cigInstances = _helper.getAssociatorInstances(storage, mvInstance.getObjectPath(), null,
                                    SmisConstants.SE_INITIATOR_MASKING_GROUP, null, null, SmisConstants.PS_ELEMENT_NAME);
                            if (cigInstances.hasNext()) {
                                cigInMVPath = cigInstances.next().getObjectPath();
                            }
                            // Find the cascaded initiator group that this belongs to and remove the IG from it.
                            // Note: we should not be in here if the IG was associated directly to the MV. If the
                            // IG were related to the MV, then the masking orchestrator should have generated
                            // a workflow to delete the MV.
                            cigInstances = _helper.getAssociatorInstances(storage, igPath, null,
                                    SmisConstants.SE_INITIATOR_MASKING_GROUP, null, null, SmisConstants.PS_ELEMENT_NAME);
                            while (cigInstances.hasNext()) {
                                CIMObjectPath cigPath = cigInstances.next().getObjectPath();
                                if (!cigPath.equals(cigInMVPath)) {
                                    // Skip CIGs that are not part of the MaskingView that we are attempting
                                    // to remove the initiators from.
                                    continue;
                                }
                                _log.info(String.format("Removing IG %s from CIG %s", igPath.toString(), cigPath.toString()));
                                inArgs = _helper.getRemoveIGFromCIG(igPath, cigPath);
                                outArgs = new CIMArgument[5];
                                _helper.invokeMethodSynchronously(storage, _cimPath.getControllerConfigSvcPath(storage),
                                        "RemoveMembers", inArgs, outArgs, null);
                                // Determine if the IG contains all initiators that were added by user/ViPR, and if
                                // the IG is no longer referenced by masking views or parent IGs. If so, it can be removed.
                                boolean removeIG = true;
                                for (Initiator initiator : initiatorsForIG) {
                                    if (!mask.hasUserInitiator(initiator.getId())) {
                                        removeIG = false;
                                    }
                                }

                                if (removeIG) {
                                    List<CIMObjectPath> igList = new ArrayList<>();
                                    igList.add(igPath);
                                    this.checkIGsAndDeleteIfUnassociated(storage, igList);
                                }
                            }
                        } else {
                            inArgs = _helper.getRemoveInitiatorsFromMaskingGroupInputArguments(storage, igPath, initiatorsForIG);
                            outArgs = new CIMArgument[5];
                            _helper.invokeMethodSynchronously(storage, _cimPath.getControllerConfigSvcPath(storage),
                                    "RemoveMembers", inArgs, outArgs, null);
                        }
                    }
                    if (targetURIList != null && !targetURIList.isEmpty()) {
                        _log.info("Removing targets...");

                        ExportMask mask = _dbClient.queryObject(ExportMask.class, exportMaskURI);

                        CIMInstance portGroupInstance = _helper.getPortGroupInstance(storage, mask.getMaskName());
                        if (null == portGroupInstance) {
                            String errMsg = String.format("removeInitiator failed - maskName %s : Port group not found ",
                                    mask.getMaskName());
                            ServiceError serviceError = DeviceControllerException.errors.jobFailedMsg(errMsg, null);
                            taskCompleter.error(_dbClient, serviceError);
                            return;
                        }
                        String pgGroupName = (String) portGroupInstance.getPropertyValue(SmisConstants.CP_ELEMENT_NAME);

                        // Get the current ports off of the storage group; only add the ones that aren't there already.
                        WBEMClient client = _helper.getConnection(storage).getCimClient();
                        List<String> storagePorts =
                                _helper.getStoragePortsFromLunMaskingInstance(client,
                                        portGroupInstance);
                        Set<URI> storagePortURIs = new HashSet<>();
                        storagePortURIs.addAll(Collections2.transform(ExportUtils.storagePortNamesToURIs(_dbClient, storagePorts),
                                CommonTransformerFunctions.FCTN_STRING_TO_URI));
                        Set<URI> portsToRemove = Sets.intersection(Sets.newHashSet(targetURIList), storagePortURIs);
                        boolean removingLast = portsToRemove.size() == storagePortURIs.size();

                        if (!portsToRemove.isEmpty() && !removingLast) {
                            inArgs = _helper.getRemoveTargetPortsFromMaskingGroupInputArguments(storage, pgGroupName,
                                    Lists.newArrayList(portsToRemove));
                            outArgs = new CIMArgument[5];
                            _helper.invokeMethodSynchronously(storage, _cimPath.getControllerConfigSvcPath(storage),
                                    "RemoveMembers", inArgs, outArgs, null);
                        } else if (!removingLast) {
                            _log.info(String.format("Target ports already removed fom port group %s, likely by a previous operation.",
                                    pgGroupName));
                        } else {
                            // In this case, some programming, orchestration, or user-fiddling-with-things-outside-of-ViPR situation led us
                            // to this scenario.
                            // It's best to just print the ports and port group and leave it alone.
                            _log.error(String
                                    .format("Removing target ports would cause an empty port group %s, which is not allowed on VMAX.  Manual port removal may be required.",
                                            pgGroupName));
                            // This can lead to an inaccuracy in the ExportMask object, but may be recitified next time it's refreshed.
                        }
                    }
                }
                _log.info(String.format("removeInitiator succeeded - maskName: %s", exportMaskURI.toString()));
                taskCompleter.ready(_dbClient);
            } catch (Exception e) {
                _log.error(String.format("removeInitiator failed - maskName: %s", exportMaskURI.toString()), e);
                String opName = ResourceOperationTypeEnum.DELETE_EXPORT_INITIATOR.getName();
                ServiceError serviceError = DeviceControllerException.errors.jobFailedOpMsg(opName, e.getMessage());
                taskCompleter.error(_dbClient, serviceError);
            } finally {
                if (cigInstances != null) {
                    cigInstances.close();
                }
            }
        }
        _log.info("{} removeInitiator END...", storage == null ? null : storage.getSerialNumber());
    }

    /**
     * This call can be used to look up the passed in initiator/port names and find (if
     * any) to which export masks they belong on the 'storage' array.
     * 
     * 
     * @param storage [in] - StorageSystem object representing the array
     * @param initiatorNames [in] - Port identifiers (WWPN or iSCSI name)
     * @param mustHaveAllPorts [in] - Indicates if true, *all* the passed in initiators
     *            have to be in the existing matching mask. If false,
     *            a mask with *any* of the specified initiators will be
     *            considered a hit.
     * @return Map of port name to Set of ExportMask URIs
     */
    @Override
    public Map<String, Set<URI>> findExportMasks(StorageSystem storage,
            List<String> initiatorNames,
            boolean mustHaveAllPorts) {
        long startTime = System.currentTimeMillis();
        Map<String, Set<URI>> matchingMasks = new HashMap<String, Set<URI>>();
        Map<URI, ExportMask> maskMap = new HashMap<>();
        CloseableIterator<CIMInstance> maskInstanceItr = null;
        try {

            WBEMClient client = _helper.getConnection(storage).getCimClient();
            HashMap<String, CIMObjectPath> initiatorPathsMap = _cimPath.getInitiatorToInitiatorPath(storage, initiatorNames);

            List<String> maskNames = new ArrayList<String>();
            for (String initiatorName : initiatorPathsMap.keySet()) {
                CIMObjectPath initiatorPath = initiatorPathsMap.get(initiatorName);

                maskInstanceItr = _helper.getAssociatorInstances(storage, initiatorPath, null, SmisConstants.SYMM_LUN_MASKING_VIEW, null,
                        null, SmisConstants.PS_LUN_MASKING_CNTRL_NAME_AND_ROLE);
                while (maskInstanceItr.hasNext()) {
                    CIMInstance instance = maskInstanceItr.next();
                    String systemName = CIMPropertyFactory.getPropertyValue(instance,
                            SmisConstants.CP_SYSTEM_NAME);

                    if (!systemName.contains(storage.getSerialNumber())) {
                        // We're interested in the specific StorageSystem's masks.
                        // The above getSymmLunMaskingViews call will get
                        // a listing of for all the protocol controllers seen by the
                        // SMISProvider pointed to by 'storage' system.
                        continue;
                    }

                    String name = CIMPropertyFactory.getPropertyValue(instance, SmisConstants.CP_ELEMENT_NAME);
                    CIMProperty<String> deviceIdProperty = (CIMProperty<String>) instance.getObjectPath()
                            .getKey(SmisConstants.CP_DEVICE_ID);

                    // Look up ExportMask by deviceId/name and storage URI
                    boolean foundMaskInDb = false;
                    ExportMask exportMask = null;
                    URIQueryResultList uriQueryList = new URIQueryResultList();
                    _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                            .getExportMaskByNameConstraint(name), uriQueryList);
                    while (uriQueryList.iterator().hasNext()) {
                        URI uri = uriQueryList.iterator().next();
                        exportMask = _dbClient.queryObject(ExportMask.class, uri);
                        if (exportMask != null && !exportMask.getInactive() &&
                                exportMask.getStorageDevice().equals(storage.getId())) {
                            foundMaskInDb = true;
                            // We're expecting there to be only one export mask of a
                            // given name for any storage array.
                            break;
                        }
                    }

                    // If there was no export group found in the database,
                    // then create a new one
                    if (!foundMaskInDb) {
                        exportMask = new ExportMask();
                        exportMask.setMaskName(name);
                        exportMask.setNativeId(deviceIdProperty.getValue());
                        exportMask.setStorageDevice(storage.getId());
                        exportMask.setId(URIUtil.createId(ExportMask.class));
                        exportMask.setCreatedBySystem(false);
                    }

                    if (!maskNames.contains(name)) {
                        // Update the tracking containers
                        Map<String, Integer> volumeWWNs =
                                _helper.getVolumesFromLunMaskingInstance(client, instance);
                        exportMask.addToExistingVolumesIfAbsent(volumeWWNs);

                        // Grab the storage ports that have been allocated for this
                        // existing mask and add them.
                        List<String> storagePorts =
                                _helper.getStoragePortsFromLunMaskingInstance(client,
                                        instance);
                        List<String> storagePortURIs =
                                ExportUtils.storagePortNamesToURIs(_dbClient, storagePorts);
                        exportMask.setStoragePorts(storagePortURIs);
                        // Add the mask name to the list for which volumes are already updated
                        maskNames.add(name);
                        maskMap.put(exportMask.getId(), exportMask);
                    }
                    exportMask.addToExistingInitiatorsIfAbsent(initiatorName);

                    Initiator existingInitiator = ExportUtils.getInitiator(Initiator.toPortNetworkId(initiatorName), _dbClient);
                    if (existingInitiator == null) {
                        _log.warn(String
                                .format("Found that port %s is associated to MaskingView %s through SMI-S, but the port is not in the database",
                                        initiatorName, name));
                        continue;
                    }
                    exportMask.addInitiator(existingInitiator);
                    if (foundMaskInDb) {
                        ExportMaskUtils.sanitizeExportMaskContainers(_dbClient, exportMask);
                        _dbClient.updateAndReindexObject(exportMask);
                    } else {
                        _dbClient.createObject(exportMask);
                    }

                    if (matchesSearchCriteria(exportMask, Collections.singletonList(initiatorName), mustHaveAllPorts)) {
                        Set<URI> maskURIs = matchingMasks.get(initiatorName);
                        if (maskURIs == null) {
                            maskURIs = new HashSet<URI>();
                            matchingMasks.put(initiatorName, maskURIs);
                        }
                        maskURIs.add(exportMask.getId());
                    }
                }
            }
            StringBuilder builder = new StringBuilder();
            for (URI exportMaskURI : maskMap.keySet()) {
                ExportMask exportMask = maskMap.get(exportMaskURI);
                builder.append(String.format("\nXM:%s is matching: ", exportMask.getMaskName())).append('\n').append(exportMask.toString());
            }
            _log.info(builder.toString());
        } catch (Exception e) {
            String msg = "Error when attempting to query LUN masking information: " + e.getMessage();
            _log.error(MessageFormat.format("Encountered an SMIS error when attempting to query existing exports: {0}", msg), e);

            throw SmisException.exceptions.queryExistingMasksFailure(msg, e);
        } finally {
            if (maskInstanceItr != null) {
                maskInstanceItr.close();
            }
            long totalTime = System.currentTimeMillis() - startTime;
            _log.info(String.format("findExportMasks took %f seconds", (double) totalTime / (double) 1000));
        }
        return matchingMasks;
    }

    @Override
    public ExportMask refreshExportMask(StorageSystem storage, ExportMask mask) {
        long startTime = System.currentTimeMillis();
        try {
            CIMInstance instance =
                    _helper.getSymmLunMaskingView(storage, mask);
            if (instance != null) {
                StringBuilder builder = new StringBuilder();
                WBEMClient client = _helper.getConnection(storage).getCimClient();
                String name = CIMPropertyFactory.getPropertyValue(instance,
                        SmisConstants.CP_ELEMENT_NAME);
                // Get volumes and initiators for the masking instance
                Map<String, Integer> discoveredVolumes =
                        _helper.getVolumesFromLunMaskingInstance(client, instance);
                List<String> discoveredPorts =
                        _helper.getInitiatorsFromLunMaskingInstance(client, instance);

                Set existingInitiators = (mask.getExistingInitiators() != null) ?
                        mask.getExistingInitiators() : Collections.emptySet();
                Set existingVolumes = (mask.getExistingVolumes() != null) ?
                        mask.getExistingVolumes().keySet() : Collections.emptySet();

                builder.append(String.format("%nXM object: %s I{%s} V:{%s}%n", name,
                        Joiner.on(',').join(existingInitiators),
                        Joiner.on(',').join(existingVolumes)));

                builder.append(String.format("XM discovered: %s I:{%s} V:{%s}%n", name,
                        Joiner.on(',').join(discoveredPorts),
                        Joiner.on(',').join(discoveredVolumes.keySet())));

                // Check the initiators and update the lists as necessary
                boolean addInitiators = false;
                List<String> initiatorsToAdd = new ArrayList<String>();
                List<Initiator> initiatorIdsToAdd = new ArrayList<>();
                for (String port : discoveredPorts) {
                    String normalizedPort = Initiator.normalizePort(port);
                    if (!mask.hasExistingInitiator(normalizedPort) &&
                            !mask.hasUserInitiator(normalizedPort)) {
                        initiatorsToAdd.add(normalizedPort);
                        Initiator existingInitiator = ExportUtils.getInitiator(Initiator.toPortNetworkId(port), _dbClient);
                        if (existingInitiator != null) {
                            initiatorIdsToAdd.add(existingInitiator);
                        }
                        addInitiators = true;
                    }
                }

                boolean removeInitiators = false;
                List<String> initiatorsToRemove = new ArrayList<String>();
                List<URI> initiatorIdsToRemove = new ArrayList<>();
                if (mask.getExistingInitiators() != null &&
                        !mask.getExistingInitiators().isEmpty()) {
                    initiatorsToRemove.addAll(mask.getExistingInitiators());
                    initiatorsToRemove.removeAll(discoveredPorts);
                }

                if (mask.getInitiators() != null &&
                        !mask.getInitiators().isEmpty()) {
                    initiatorIdsToRemove.addAll(Collections2.transform(mask.getInitiators(),
                            CommonTransformerFunctions.FCTN_STRING_TO_URI));
                    for (String port : discoveredPorts) {
                        Initiator existingInitiator = ExportUtils.getInitiator(Initiator.toPortNetworkId(port), _dbClient);
                        if (existingInitiator != null) {
                            initiatorIdsToRemove.remove(existingInitiator.getId());
                        }
                    }
                }

                removeInitiators = !initiatorsToRemove.isEmpty() || !initiatorIdsToRemove.isEmpty();

                // Check the volumes and update the lists as necessary
                boolean addVolumes = false;
                Map<String, Integer> volumesToAdd = new HashMap<String, Integer>();
                for (Map.Entry<String, Integer> entry : discoveredVolumes.entrySet()) {
                    String normalizedWWN = BlockObject.normalizeWWN(entry.getKey());
                    if (!mask.hasExistingVolume(normalizedWWN) &&
                            !mask.hasUserCreatedVolume(normalizedWWN)) {
                        volumesToAdd.put(normalizedWWN, entry.getValue());
                        addVolumes = true;
                    }
                }

                boolean removeVolumes = false;
                List<String> volumesToRemove = new ArrayList<String>();
                if (mask.getExistingVolumes() != null &&
                        !mask.getExistingVolumes().isEmpty()) {
                    volumesToRemove.addAll(mask.getExistingVolumes().keySet());
                    volumesToRemove.removeAll(discoveredVolumes.keySet());
                    removeVolumes = !volumesToRemove.isEmpty();
                }

                // Grab the storage ports that have been allocated for this
                // existing mask and update them.
                List<String> storagePorts =
                        _helper.getStoragePortsFromLunMaskingInstance(client,
                                instance);
                List<String> storagePortURIs =
                        ExportUtils.storagePortNamesToURIs(_dbClient, storagePorts);

                // Check the storagePorts and update the lists as necessary
                boolean addStoragePorts = false;
                List<String> storagePortsToAdd = new ArrayList<>();
                if (mask.getStoragePorts() == null) {
                    mask.setStoragePorts(new ArrayList<String>());
                }

                for (String portID : storagePortURIs) {
                    if (!mask.getStoragePorts().contains(portID)) {
                        mask.getStoragePorts().add(portID);
                        addStoragePorts = true;
                    }
                }

                boolean removeStoragePorts = false;
                List<String> storagePortsToRemove = new ArrayList<String>();
                if (mask.getStoragePorts() != null &&
                        !mask.getStoragePorts().isEmpty()) {
                    storagePortsToRemove.addAll(mask.getStoragePorts());
                    storagePortsToRemove.removeAll(storagePortURIs);
                    removeStoragePorts = !storagePortsToRemove.isEmpty();
                }

                builder.append(
                        String.format("XM refresh: %s initiators; add:{%s} remove:{%s}%n",
                                name, Joiner.on(',').join(initiatorsToAdd),
                                Joiner.on(',').join(initiatorsToRemove)));
                builder.append(
                        String.format("XM refresh: %s volumes; add:{%s} remove:{%s}%n",
                                name, Joiner.on(',').join(volumesToAdd.keySet()),
                                Joiner.on(',').join(volumesToRemove)));
                builder.append(
                        String.format("XM refresh: %s ports; add:{%s} remove:{%s}%n",
                                name, Joiner.on(',').join(storagePortsToAdd),
                                Joiner.on(',').join(storagePortsToRemove)));

                // Any changes indicated, then update the mask and persist it
                if (addInitiators || removeInitiators || addVolumes ||
                        removeVolumes || addStoragePorts || removeStoragePorts) {
                    builder.append("XM refresh: There are changes to mask, " +
                            "updating it...\n");
                    mask.removeFromExistingInitiators(initiatorsToRemove);
                    if (initiatorIdsToRemove != null && !initiatorIdsToRemove.isEmpty()) {
                        mask.removeInitiators(_dbClient.queryObject(Initiator.class, initiatorIdsToRemove));
                    }
                    // https://coprhd.atlassian.net/browse/COP-17224 - For those cases where InitiatorGroups are shared by
                    // MaskingViews, if CoprHD processes one ExportMask by updating it with new initiators, then it could
                    // affect another ExportMasks. Consider that this refreshExportMask is against that other ExportMask.
                    // We shouldn't read the initiators that we find as 'existing' (that is created outside of CoprHD),
                    // instead we should consider them userAdded for this ExportMask, as well.
                    List<Initiator> userAddedInitiators = findIfInitiatorsAreUserAddedInAnotherMask(mask, initiatorIdsToAdd);
                    mask.addToUserCreatedInitiators(userAddedInitiators);
                    mask.addToExistingInitiatorsIfAbsent(initiatorsToAdd);
                    mask.addInitiators(initiatorIdsToAdd);
                    mask.removeFromExistingVolumes(volumesToRemove);
                    mask.addToExistingVolumesIfAbsent(volumesToAdd);
                    mask.getStoragePorts().addAll(storagePortsToAdd);
                    mask.getStoragePorts().removeAll(storagePortsToRemove);
                    ExportMaskUtils.sanitizeExportMaskContainers(_dbClient, mask);
                    _dbClient.updateAndReindexObject(mask);
                } else {
                    builder.append("XM refresh: There are no changes to the mask\n");
                }
                _networkDeviceController.refreshZoningMap(mask,
                        initiatorsToRemove, Collections.EMPTY_LIST,
                        (addInitiators || removeInitiators), true);
                _log.info(builder.toString());
            }
        } catch (Exception e) {
            boolean throwException = true;
            if (e instanceof WBEMException) {
                WBEMException we = (WBEMException) e;
                // Only throw exception if code is not CIM_ERROR_NOT_FOUND
                throwException = (we.getID() != WBEMException.CIM_ERR_NOT_FOUND);
            }
            if (throwException) {
                String msg = "Error when attempting to query LUN masking information: " + e.getMessage();
                _log.error(MessageFormat.format("Encountered an SMIS error when attempting to refresh existing exports: {0}", msg), e);

                throw SmisException.exceptions.refreshExistingMaskFailure(msg, e);
            }
        } finally {
            long totalTime = System.currentTimeMillis() - startTime;
            _log.info(String.format("refreshExportMask took %f seconds", (double) totalTime / (double) 1000));
        }
        return mask;
    }

    // //////////// VMAX specific export helpers ////////////////

    private CIMObjectPath createVolumeGroup(StorageSystem storage,
            String groupName, VolumeURIHLU[] volumeURIHLUs,
            TaskCompleter taskCompleter, boolean addVolumes) throws Exception {
        _log.debug("{} createVolumeGroup START...", storage.getSerialNumber());
        CIMObjectPath volumeGroupObjectPath = null;
        int index = 0;
        String[] volumeNames = new String[volumeURIHLUs.length];
        String policyName = null;
        // Flag to indicate whether or not we need to use the EMCForce flag on this operation.
        // We currently use this flag when dealing with RP Volumes as they are tagged for RP and the
        // operation on these volumes would fail otherwise.
        boolean forceFlag = false;
        for (VolumeURIHLU volURIHlu : volumeURIHLUs) {
            volumeNames[index++] = _helper.getBlockObjectNativeId(volURIHlu.getVolumeURI());
            if (null == policyName && storage.checkIfVmax3()) {
                policyName = _helper.getVMAX3FastSettingForVolume(volURIHlu.getVolumeURI(), volURIHlu.getAutoTierPolicyName());
            }
            // The force flag only needs to be set once
            if (!forceFlag) {
                forceFlag = ExportUtils.useEMCForceFlag(_dbClient, volURIHlu.getVolumeURI());
            }
        }
        CIMArgument[] inArgs = null;
        String truncatedGroupName = groupName.length() >= 64 ? StringUtils.substring(groupName, 0, 63) : groupName;
        if (storage.checkIfVmax3()) {
            for (String nativeId : volumeNames) {
                _helper.removeVolumeFromParkingSLOStorageGroup(storage, nativeId, forceFlag);
                _log.info("Done invoking remove volume {} from storage group before export.", nativeId);
            }

            String[] tokens = policyName.split(Constants.SMIS_PLUS_REGEX);
            inArgs = _helper.getCreateVolumeGroupInputArguments(storage, truncatedGroupName, tokens[0], tokens[2], tokens[1],
                    addVolumes ? volumeNames : null);
        } else {
            inArgs = _helper.getCreateVolumeGroupInputArguments(storage, truncatedGroupName, addVolumes ? volumeNames : null);
        }

        CIMArgument[] outArgs = new CIMArgument[5];
        try {
            _helper.invokeMethod(storage, _cimPath.getControllerConfigSvcPath(storage),
                    "CreateGroup", inArgs, outArgs);
            volumeGroupObjectPath = _cimPath.getCimObjectPathFromOutputArgs(outArgs, "MaskingGroup");
            ExportOperationContext.insertContextOperation(taskCompleter, VmaxExportOperationContext.OPERATION_CREATE_STORAGE_GROUP,
                    groupName, volumeURIHLUs);
        } catch (WBEMException we) {
            // If the VG by the same name exists, the WBEM exception thrown is CIM_ERR_FAILED.
            _log.info("{} Problem when trying to create volume group ... going to look up volume group.",
                    storage.getSystemType(), we);
            volumeGroupObjectPath = handleCreateMaskingGroupException(storage, truncatedGroupName, inArgs,
                    SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup);
            if (volumeGroupObjectPath == null) {
                _log.info("{} Problem looking up volume group.", storage.getSerialNumber(), we);
                throw we;
            } else {
                _log.info("{} Found volume group.", storage.getSerialNumber());
            }
        }
        _log.debug("{} createVolumeGroup END...", storage.getSerialNumber());
        return volumeGroupObjectPath;
    }

    private CIMObjectPath createCascadedVolumeGroup(StorageSystem storage, String groupName,
            List<CIMObjectPath> volumeGroupPaths, TaskCompleter taskCompleter) throws Exception {
        _log.debug("{} createCascadedVolumeGroup START...", storage.getSerialNumber());
        CIMObjectPath cascadedVolumeGroupObjectPath = null;
        CIMObjectPath[] volumePaths = new CIMObjectPath[volumeGroupPaths.size()];
        CIMArgument[] inArgs = _helper.getCascadedStorageGroupInputArguments(storage, groupName,
                volumeGroupPaths.toArray(volumePaths));
        CIMArgument[] outArgs = new CIMArgument[5];
        _helper.invokeMethod(storage, _cimPath.getControllerConfigSvcPath(storage), "CreateGroup",
                inArgs, outArgs);
        cascadedVolumeGroupObjectPath = _cimPath.getCimObjectPathFromOutputArgs(outArgs,
                "MaskingGroup");
        ExportOperationContext.insertContextOperation(taskCompleter, VmaxExportOperationContext.OPERATION_CREATE_CASCADING_STORAGE_GROUP,
                groupName, volumeGroupPaths);
        _log.debug("{} createCascadedVolumeGroup END...", storage.getSerialNumber());
        return cascadedVolumeGroupObjectPath;
    }

    private void addGroupsToCascadedVolumeGroup(
            StorageSystem storage, String groupName, CIMObjectPath volumeGroupPath,
            SmisJob job, TaskCompleter taskCompleter, boolean forceFlag) throws Exception {
        _log.debug("{} AddGroupsToCascadedVolumeGroup START...", storage.getSerialNumber());
        CIMObjectPath[] volumeGroupPaths = new CIMObjectPath[] { volumeGroupPath };
        CIMArgument[] inArgs = _helper.modifyCascadedStorageGroupInputArguments(
                storage, groupName, volumeGroupPaths, forceFlag);
        CIMArgument[] outArgs = new CIMArgument[5];
        _helper.invokeMethodSynchronously(storage,
                _cimPath.getControllerConfigSvcPath(storage), "AddMembers", inArgs,
                outArgs, job);
        ExportOperationContext.insertContextOperation(taskCompleter,
                VmaxExportOperationContext.OPERATION_ADD_STORAGE_GROUP_TO_CASCADING_STORAGE_GROUP, groupName, volumeGroupPaths, forceFlag);
        _log.debug("{} AddGroupsToCascadedVolumeGroup END...", storage.getSerialNumber());
    }

    private CIMObjectPath addVolumeGroupToAutoTieringPolicy(
            StorageSystem storage, String policyName, CIMObjectPath volumeGroupPath, TaskCompleter taskCompleter)
            throws Exception {
        _log.debug("{} addVolumeGroupToAutoTierPolicy START...",
                storage.getSerialNumber());
        CIMObjectPath cascadedVolumeGroupObjectPath = null;
        CIMObjectPath[] volumeGroupPaths = new CIMObjectPath[] { volumeGroupPath };
        CIMArgument[] inArgs = _helper.getVolumeGroupToTierInputArguments(storage,
                policyName, volumeGroupPaths);
        CIMArgument[] outArgs = new CIMArgument[5];
        try {
            _helper.invokeMethod(storage, _cimPath.getTierPolicySvcPath(storage),
                    "ModifyStorageTierPolicyRule", inArgs, outArgs);
            cascadedVolumeGroupObjectPath = _cimPath.getCimObjectPathFromOutputArgs(
                    outArgs, "MaskingGroup");
            ExportOperationContext.insertContextOperation(taskCompleter, VmaxExportOperationContext.OPERATION_ADD_TIER_TO_STORAGE_GROUP,
                    policyName, volumeGroupPaths);
        } catch (WBEMException we) {
            // If the VG by the same name exists, the WBEM exception thrown is CIM_ERR_FAILED.
            throw we;
        }
        _log.debug("{} addVolumeGroupToAutoTierPolicy END...", storage.getSerialNumber());
        return cascadedVolumeGroupObjectPath;
    }

    // add initiators to an existing initiator group
    private void addInitiatorsToInitiatorGroup(StorageSystem storage,
            List<Initiator> initiatorList,
            CIMObjectPath initiatorGroupPath, TaskCompleter taskCompleter) throws Exception {
        CIMArgument[] inArgs = _helper.getAddInitiatorsToMaskingGroupInputArguments(storage, initiatorGroupPath,
                initiatorList);
        CIMArgument[] outArgs = new CIMArgument[5];
        _helper.invokeMethodSynchronously(storage, _cimPath.getControllerConfigSvcPath(storage),
                "AddMembers", inArgs, outArgs, null);
        ExportOperationContext.insertContextOperation(taskCompleter,
                VmaxExportOperationContext.OPERATION_ADD_INITIATORS_TO_INITIATOR_GROUP, initiatorList, initiatorGroupPath);
    }

    // add a (child) initiator group to an existing (parent) initiator group
    private void addInitiatorGroupToInitiatorGroup(StorageSystem storage,
            CIMObjectPath childInitiatorGroup,
            CIMObjectPath parentInitiatorGroup, TaskCompleter taskCompleter) throws Exception {
        CIMArgument[] inArgs = _helper.getAddInitiatorGroupToMaskingGroupInputArguments(parentInitiatorGroup,
                childInitiatorGroup);
        CIMArgument[] outArgs = new CIMArgument[5];
        _helper.invokeMethodSynchronously(storage, _cimPath.getControllerConfigSvcPath(storage),
                "AddMembers", inArgs, outArgs, null);
        ExportOperationContext.insertContextOperation(taskCompleter,
                VmaxExportOperationContext.OPERATION_ADD_INITIATOR_GROUPS_TO_INITIATOR_GROUP, childInitiatorGroup, parentInitiatorGroup);
    }

    private CIMObjectPath createTargetPortGroup(StorageSystem storage,
            String portGroupName,
            List<URI> targetURIList, TaskCompleter taskCompleter) throws Exception {
        _log.debug("{} createTargetPortGroup START...", storage.getSerialNumber());
        CIMObjectPath targetPortGroupPath = null;

        CIMArgument[] inArgs = _helper.getCreateTargetPortGroupInputArguments(storage, portGroupName, targetURIList);
        CIMArgument[] outArgs = new CIMArgument[5];
        try {
            // Try to look up the port group. If it already exists, use it,
            // otherwise try to create it.
            targetPortGroupPath =
                    _cimPath.getMaskingGroupPath(storage, portGroupName,
                            SmisConstants.MASKING_GROUP_TYPE.SE_TargetMaskingGroup);
            CIMInstance instance =
                    _helper.checkExists(storage, targetPortGroupPath, false, false);
            if (instance == null) {
                _helper.invokeMethod(storage, _cimPath.getControllerConfigSvcPath(storage),
                        "CreateGroup", inArgs, outArgs);
                targetPortGroupPath = _cimPath.getCimObjectPathFromOutputArgs(outArgs, "MaskingGroup");
                ExportOperationContext.insertContextOperation(taskCompleter, VmaxExportOperationContext.OPERATION_CREATE_PORT_GROUP,
                        portGroupName, targetURIList);
            }
        } catch (WBEMException we) {
            _log.info("{} Problem when trying to create target port group ... going to look up target port group.",
                    storage.getSystemType(), we);
            targetPortGroupPath = handleCreateMaskingGroupException(storage, portGroupName, inArgs,
                    SmisCommandHelper.MASKING_GROUP_TYPE.SE_TargetMaskingGroup);
            if (targetPortGroupPath == null) {
                _log.info("{} Problem looking up target port group.", storage.getSerialNumber(), we);
                throw we;
            } else {
                _log.info("{} Found target port group.", storage.getSerialNumber());
            }
        }
        _log.debug("{} createTargetPortGroup END...", storage.getSerialNumber());
        return targetPortGroupPath;
    }

    private boolean hasInitiatorHPUX(List<Initiator> initiatorList) {
        boolean initiatorIsHPUX = false;
        if (!initiatorList.isEmpty()) {
            Initiator ini = initiatorList.get(0);
            URI hostURI = ini.getHost();
            if (hostURI == null) {
                return false;
            }
            Host host = _dbClient.queryObject(Host.class, hostURI);
            String hostType = host.getType();
            if (hostType.equals(Host.HostType.HPUX.toString())) {
                initiatorIsHPUX = true;
            }
        }
        return initiatorIsHPUX;
    }

    private boolean hasIGWithVSASet(StorageSystem storage,
            List<CIMObjectPath> initiatorGroupPaths) throws Exception {
        boolean foundIGWithVSASet = false;
        for (CIMObjectPath igPath : initiatorGroupPaths) {
            if (igHasVSASet(storage, igPath)) {
                foundIGWithVSASet = true;
                break;
            }
        }
        return foundIGWithVSASet;
    }

    private boolean igHasVSASet(StorageSystem storage,
            CIMObjectPath initiatorGroupPath) throws Exception {
        boolean vsaSet = false;
        CIMInstance initiatorGroupInstance = _helper.getInstance(storage, initiatorGroupPath, false, false, null);
        String emcVSAEnabled = CIMPropertyFactory.getPropertyValue(initiatorGroupInstance,
                SmisConstants.CP_EMC_VSA_ENABLED);
        if (emcVSAEnabled != null) {
            vsaSet = emcVSAEnabled.equalsIgnoreCase("true");
        }
        return vsaSet;
    }

    private void setVSAFlagForIG(StorageSystem storage,
            CIMObjectPath initiatorGroupPath,
            boolean VSAFlag) throws Exception {
        WBEMClient client = _helper.getConnection(storage).getCimClient();
        CIMPropertyFactory factoryRef = (CIMPropertyFactory) ControllerServiceImpl.getBean("CIMPropertyFactory");
        CIMInstance toUpdate = new CIMInstance(initiatorGroupPath,
                new CIMProperty[] {
                        factoryRef.bool(SmisConstants.CP_EMC_VSA_ENABLED, VSAFlag)
                }
                );
        client.modifyInstance(toUpdate, SmisConstants.PS_EMC_VSA_ENABLED);
    }

    private CIMObjectPath createInitiatorGroupWithInitiators(StorageSystem storage,
            String groupName,
            List<Initiator> initiatorList, boolean consistentLUNs, TaskCompleter taskCompleter) throws Exception {
        _log.debug("{} createInitiatorGroupWithInitiators START...", storage.getSerialNumber());
        CIMObjectPath initiatorGroupPath = null;
        String[] initiators = _helper.getInitiatorNames(initiatorList, storage);
        CIMArgument[] inArgs = _helper.getCreateInitiatorGroupInputArguments(storage, groupName, initiators, consistentLUNs);
        CIMArgument[] outArgs = new CIMArgument[5];
        try {
            CIMObjectPath cigPath = _helper.getInitiatorGroupPath(storage, groupName);
            CIMInstance igInstance =
                    _helper.checkExists(storage, cigPath, false, false);
            List<Initiator> iniAlreadyAvailableinIG = new ArrayList<Initiator>();
            if (igInstance == null) {
                _helper.invokeMethod(storage, _cimPath.getControllerConfigSvcPath(storage),
                        "CreateGroup", inArgs, outArgs);
                initiatorGroupPath = _cimPath.getCimObjectPathFromOutputArgs(outArgs, "MaskingGroup");
                ExportOperationContext.insertContextOperation(taskCompleter, VmaxExportOperationContext.OPERATION_CREATE_INITIATOR_GROUP,
                        groupName, initiatorList, initiatorGroupPath);

                _log.info("createInitiatorGroupWithInitiators - IG doesn't exist, " +
                        "creating new one {}", initiatorGroupPath);
                if (taskCompleter instanceof ExportMaskInitiatorCompleter) {
                    Collection<URI> initiatorIds = Collections2.transform(initiatorList,
                            CommonTransformerFunctions.fctnDataObjectToID());
                    ((ExportMaskInitiatorCompleter) taskCompleter).addInitiators(initiatorIds);
                }
            } else {
                initiatorGroupPath = igInstance.getObjectPath();
                _log.info("createInitiatorGroupWithInitiators - IG {} already exists, " +
                        "will reuse it for initiators", initiatorGroupPath);
                // Only add initiators if they don't already exist in the IG. This is
                // used to validate this scenario.
                Set<String> hwIds = new HashSet<String>();
                CloseableIterator<CIMInstance> initiatorIterator =
                        _helper.getAssociatorInstances(storage, initiatorGroupPath, null,
                                SmisConstants.CP_SE_STORAGE_HARDWARE_ID, null, null,
                                SmisConstants.PS_STORAGE_ID);
                if (initiatorIterator != null) {
                    while (initiatorIterator.hasNext()) {
                        CIMInstance cimInstance = initiatorIterator.next();
                        if (cimInstance != null) {
                            String hwId =
                                    CIMPropertyFactory.getPropertyValue(cimInstance,
                                            SmisConstants.CP_STORAGE_ID);
                            hwIds.add(hwId);
                        }
                    }
                    initiatorIterator.close();
                    Iterator<Initiator> it = initiatorList.iterator();
                    while (it.hasNext()) {
                        Initiator init = it.next();
                        String hwId = Initiator.normalizePort(init.getInitiatorPort());
                        if (hwIds.contains(hwId)) {
                            iniAlreadyAvailableinIG.add(init);
                            it.remove();
                        }
                    }
                }

                // N Masking view per Cluster
                // Find if the same initiator is already userAdded in any other ExportMask, if yes then make this as userAdded
                // N Masking View scenario per cluster - the initiator would have been added already to the IG while processing
                // 1st mask and considered as userAdded, when the 2nd mask is getting processed the same initiator should be
                // added as userAdded.
                if (!iniAlreadyAvailableinIG.isEmpty()) {
                    _log.info("Finding out whether the initiators already available in IG are actually userAdded in any other Masks");
                    for (Initiator ini : iniAlreadyAvailableinIG) {
                        _log.info("Processing initiator {} -- {} ", ini.getId(), ini.getInitiatorPort());
                        // check this ini is a userAdded in any other MV
                        String normalizedPort = Initiator.normalizePort(ini.getInitiatorPort());
                        List<URI> exportMaskUris = _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                                .getExportMaskInitiatorConstraint(ini.getId().toString()));
                        if (null != exportMaskUris && !exportMaskUris.isEmpty()) {
                            List<ExportMask> exportMasks = _dbClient.queryObject(ExportMask.class, exportMaskUris);
                            for (ExportMask eMask : exportMasks) {
                                _log.info("Processing Export mask {} on which the initiator is already available as existing",
                                        eMask.getId());
                                if (null != eMask.getUserAddedInitiators()
                                        && eMask.getUserAddedInitiators().containsKey(normalizedPort)) {
                                    _log.info("Adding ini {} to userAdded list in mask {}", ini.getId(), eMask.getMaskName());
                                    ((ExportMaskInitiatorCompleter) taskCompleter).addInitiator(ini.getId());
                                    break;
                                }
                            }
                        }
                    }
                }

                if (!initiatorList.isEmpty()) {
                    addInitiatorsToInitiatorGroup(storage, initiatorList, initiatorGroupPath, taskCompleter);
                    // If we added a partial list of initiators to an existing initiator group,
                    // we consider those initiators "user added". This will disallow the user from
                    // being able to remove initiators that were already there, as that may impact
                    // other masks that depend on it that ViPR's not aware of.
                    if (taskCompleter instanceof ExportMaskInitiatorCompleter) {
                        Collection<URI> initiatorIds = Collections2.transform(initiatorList,
                                CommonTransformerFunctions.fctnDataObjectToID());
                        ((ExportMaskInitiatorCompleter) taskCompleter).addInitiators(initiatorIds);
                    }
                }
                return initiatorGroupPath;
            }
            if (hasInitiatorHPUX(initiatorList)) {
                setVSAFlagForIG(storage, initiatorGroupPath, true);
            }
        } catch (WBEMException we) {
            _log.info("{} Problem when trying to create createInitiatorGroupWithInitiators ... going to look up initiator group.",
                    storage.getSystemType(), we);
            initiatorGroupPath = handleCreateMaskingGroupException(storage, groupName, inArgs,
                    SmisCommandHelper.MASKING_GROUP_TYPE.SE_InitiatorMaskingGroup);
            if (initiatorGroupPath == null) {
                _log.info("{} Problem looking up initiator group.", storage.getSerialNumber(), we);
                throw we;
            } else {
                _log.info("{} Found initiator group with expected members.", storage.getSerialNumber());
            }
        }
        _log.debug("{} createInitiatorGroupWithInitiators END...", storage.getSerialNumber());
        return initiatorGroupPath;
    }

    private CIMObjectPath createInitiatorGroupWithInitiatorGroups(StorageSystem storage,
            ExportMask mask,
            String groupName,
            List<CIMObjectPath> initiatorGroupPaths, boolean consistentLUNs, TaskCompleter taskCompleter)
            throws Exception {
        _log.debug("{} createInitiatorGroupWithInitiatorGroups START...", storage.getSerialNumber());
        CIMObjectPath initiatorGroupPath = null;
        CIMArgument[] inArgs;
        CIMArgument[] outArgs;
        boolean enableVSA = hasIGWithVSASet(storage, initiatorGroupPaths);
        if (enableVSA) {
            // If we are trying to create a cascaded IG with VSA enabled child IGs, we first
            // have to create an empty parent IG and enable VSA on it. The SMI-S provider
            // does not allow adding of VSA enabled IGs to a non-VSA enabled IG.
            inArgs = _helper.getCreateEmptyIGWithInitiatorGroupsInputArguments(groupName);
        } else {
            inArgs = _helper.getCreateInitiatorGroupWithInitiatorGroupsInputArguments(groupName,
                    initiatorGroupPaths, consistentLUNs);
        }
        outArgs = new CIMArgument[5];
        try {
            CIMObjectPath cigPath = _helper.getInitiatorGroupPath(storage, groupName);
            CIMInstance cigInstance = findCascadingInitiatorGroup(storage, mask, cigPath, initiatorGroupPaths);
            if (cigInstance == null) {
                _helper.invokeMethod(storage, _cimPath.getControllerConfigSvcPath(storage),
                        "CreateGroup", inArgs, outArgs);
                initiatorGroupPath = _cimPath.getCimObjectPathFromOutputArgs(outArgs, "MaskingGroup");
                _log.info("createInitiatorGroupWithInitiatorGroups - Created new CIG {}",
                        initiatorGroupPath);
                ExportOperationContext.insertContextOperation(taskCompleter,
                        VmaxExportOperationContext.OPERATION_CREATE_CASCADED_INITIATOR_GROUP, groupName, initiatorGroupPath);
            } else {
                initiatorGroupPath = cigInstance.getObjectPath();
                _log.info("createInitiatorGroupWithInitiatorGroups - Reusing CIG {}",
                        initiatorGroupPath);

                // Create a list of child initiator instance Ids,
                // so that we can check if the IG already exists in the cascaded
                // initiator group.
                Set<String> childIGs = new HashSet<String>();
                CloseableIterator<CIMObjectPath> childIGIterator =
                        _helper.getAssociatorNames(storage, initiatorGroupPath, null,
                                SmisConstants.SE_INITIATOR_MASKING_GROUP, null, null);
                if (childIGIterator != null) {
                    while (childIGIterator.hasNext()) {
                        CIMObjectPath path = childIGIterator.next();
                        if (path != null) {
                            String instanceId =
                                    path.getKey(SmisConstants.CP_INSTANCE_ID).
                                            getValue().toString();
                            childIGs.add(instanceId);
                        }
                    }
                    childIGIterator.close();
                }

                for (CIMObjectPath childIGPath : initiatorGroupPaths) {
                    String instanceId =
                            childIGPath.getKey(SmisConstants.CP_INSTANCE_ID).
                                    getValue().toString();
                    if (!childIGs.contains(instanceId)) {
                        addInitiatorGroupToInitiatorGroup(storage, childIGPath,
                                initiatorGroupPath, taskCompleter);
                    } else {
                        _log.info("createInitiatorGroupWithInitiatorGroups - ChildIG {}" +
                                " is already in {}",
                                childIGPath, initiatorGroupPath);
                    }
                }
                return initiatorGroupPath;
            }

            if (enableVSA) {
                setVSAFlagForIG(storage, initiatorGroupPath, true);
                // Now go ahead and add the members..
                for (CIMObjectPath childIGPath : initiatorGroupPaths) {
                    addInitiatorGroupToInitiatorGroup(storage, childIGPath, initiatorGroupPath, taskCompleter);
                }
            }
        } catch (WBEMException we) {
            _log.info("{} Problem when trying to create initiator group ... going to look up initiator group.",
                    storage.getSystemType(), we);
            initiatorGroupPath = handleCreateMaskingGroupException(storage, groupName, inArgs,
                    SmisCommandHelper.MASKING_GROUP_TYPE.SE_InitiatorMaskingGroup);
            if (initiatorGroupPath == null) {
                _log.info("{} Problem looking up initiator group.", storage.getSerialNumber(), we);
                throw we;
            } else {
                _log.info("{} Found initiator group with expected members.", storage.getSerialNumber());
            }
        }
        return initiatorGroupPath;
    }

    /**
     * Find a cascading initiator group that contains a subset of the child initiator groups sent in.
     * To match the orchestrator functionality, you should find more than one child initiator group
     * in the cascading group in order to qualify it.
     * 
     * @param storage storage system
     * @param mask the mask
     * @param cigPath cascaded initiator group path desired
     * @param initiatorGroupPaths child initiator groups
     * @return the CIM instance
     */
    private CIMInstance findCascadingInitiatorGroup(StorageSystem storage,
            ExportMask mask, CIMObjectPath cigPath, List<CIMObjectPath> initiatorGroupPaths) {
        CloseableIterator<CIMInstance> cigInstances = null;
        try {
            _log.info(String.format("findCascadingInitiatorGroup - Trying to find cascading initiator group for mask: %s",
                    mask.getMaskName()));

            // Get the masking view associated with the export.
            CIMInstance maskingViewInstance = this.maskingViewExists(storage, mask.getMaskName());

            // If the masking view doesn't exist yet, return null
            if (maskingViewInstance == null) {
                _log.info(String.format("findCascadingInitiatorGroup - Could not find a masking view associated with export mask: %s",
                        mask.getMaskName()));
                return null;
            }

            _log.info(String.format("findCascadingInitiatorGroup - Trying to find initiator group with paths: %s",
                    Joiner.on(',').join(initiatorGroupPaths)));

            // Get the initiator group associated with the masking view
            cigInstances = _helper.getAssociatorInstances(storage, maskingViewInstance.getObjectPath(), null,
                    SmisConstants.SE_INITIATOR_MASKING_GROUP, null, null, SmisConstants.PS_ELEMENT_NAME);

            // If there is no initiator group, this doesn't qualify
            if (!cigInstances.hasNext()) {
                _log.info(String.format(
                        "findCascadingInitiatorGroup - There is no cascading initiator group associated with export mask: %s",
                        mask.getMaskName()));
                return null;
            }

            CIMInstance cigInstance = cigInstances.next();
            // CTRL-7226 - IGs can be non-cascaded inside a MAsking View, hence check explicitly
            if (_helper.isCascadedIG(storage, cigInstance.getObjectPath())) {
                return cigInstance;
            }
            return null;
        }

        catch (Exception e) {
            _log.error(String.format("removeInitiator failed - maskName: %s", mask.getMaskName()), e);
            String opName = ResourceOperationTypeEnum.DELETE_EXPORT_INITIATOR.getName();
            ServiceError serviceError = DeviceControllerException.errors.jobFailedOpMsg(opName, e.getMessage());
        } finally {
            if (cigInstances != null) {
                cigInstances.close();
            }
        }

        _log.info("findCascadingInitiatorGroup - Did not find enough child initiator groups to consider any existing cascading initiator group a proper fit");
        return null;
    }

    private void createMaskingView(StorageSystem storage,
            URI exportMaskURI,
            String maskingViewName, CIMObjectPath volumeGroupPath,
            VolumeURIHLU[] volumeURIHLUs,
            CIMObjectPath targetPortGroupPath,
            CIMObjectPath initiatorGroupPath,
            TaskCompleter taskCompleter) throws Exception {
        _log.debug("{} createMaskingView START...", storage.getSerialNumber());
        // Flag to indicate whether or not we need to use the EMCForce flag on this operation.
        // We currently use this flag when dealing with RP Volumes as they are tagged for RP and the
        // operation on these volumes would fail otherwise.
        boolean forceFlag = false;

        List<String> deviceNumbers = new ArrayList<String>();
        for (VolumeURIHLU volURIHlu : volumeURIHLUs) {
            String hlu = volURIHlu.getHLU();
            // Add the HLU to the list only if it is non-null and not the
            // LUN_UNASSIGNED value (as a hex string).
            if (hlu != null &&
                    !hlu.equalsIgnoreCase(ExportGroup.LUN_UNASSIGNED_STR)) {
                deviceNumbers.add(hlu);
            }

            // The force flag only needs to be set once
            if (!forceFlag) {
                forceFlag = ExportUtils.useEMCForceFlag(_dbClient, volURIHlu.getVolumeURI());
            }
        }

        String[] deviceNumbersStr = {};
        CIMArgument[] inMVArgs = _helper.getCreateMaskingViewInputArguments(volumeGroupPath, targetPortGroupPath,
                initiatorGroupPath, deviceNumbers.toArray(deviceNumbersStr), maskingViewName, forceFlag);
        CIMArgument[] outMVArgs = new CIMArgument[5];
        try {
            _helper.invokeMethod(storage, _cimPath.getControllerConfigSvcPath(storage),
                    "CreateMaskingView", inMVArgs, outMVArgs);
            CIMObjectPath cimJobPath = _cimPath.getCimObjectPathFromOutputArgs(outMVArgs, "Job");
            if (cimJobPath != null) {
                ControllerServiceImpl.enqueueJob(new QueueJob(new SmisCreateMaskingViewJob(cimJobPath,
                        storage.getId(), exportMaskURI, volumeURIHLUs, volumeGroupPath, taskCompleter)));
            }
            // Rollback context is set in the job upon completion.
        } catch (WBEMException we) {
            _log.info("{} Problem when trying to create masking view ... going to look up masking view.",
                    storage.getSerialNumber(), we);
            if (handleCreateMaskingViewException(storage, maskingViewName)) {
                _log.info("{} Found masking view: {}", storage.getSerialNumber(), maskingViewName);
                taskCompleter.ready(_dbClient);
            } else {
                _log.debug("{} Problem when looking up masking view: {}", storage.getSerialNumber(), maskingViewName);
                throw we;
            }
        }
        _log.debug("{} createMaskingView END...", storage.getSerialNumber());
    }

    private CIMObjectPath handleCreateMaskingGroupException(StorageSystem storage,
            String groupName,
            CIMArgument[] inArgsFromFailedCommand,
            SmisCommandHelper.MASKING_GROUP_TYPE groupType)
            throws Exception {
        _log.debug("{} handleCreateMaskingGroupException START....", storage.getSerialNumber());
        CIMObjectPath resultMaskingGroupPath = null;
        List<CIMObjectPath> expectedMembers = getMembersFromArguments(inArgsFromFailedCommand);
        if (expectedMembers.isEmpty()) {
            _log.debug("{} handleCreateMaskingGroupException END....No expected members found in arguments.",
                    storage.getSerialNumber());
            return null;
        } else {
            _log.debug("Trying to create a masking group with #members {}, members: {}",
                    expectedMembers.size(), expectedMembers);
        }
        String associatorName = expectedMembers.get(0).getObjectName();
        if (associatorName == null) {
            _log.debug("{} handleCreateMaskingGroupException END...Could not determine associatorName for groupType: {}",
                    storage.getSerialNumber(), groupType.name());
            return null;
        }
        try {
            CIMObjectPath maskingGroupPath = _cimPath.getMaskingGroupPath(storage, groupName, groupType);
            _log.debug("Fetch existing members using maskingGroupPath: {}, associatorName: {}", maskingGroupPath,
                    associatorName);
            List<CIMObjectPath> existingMembers = getExistingMaskingGroupMembers(storage, maskingGroupPath,
                    associatorName);
            _log.debug("MaskingGroupPath {} has {} existing members.", maskingGroupPath, existingMembers.size());
            if (!existingMembers.isEmpty()) {
                List<String> existingMemberIds = getMaskingGroupMemberIdsFromPaths(existingMembers, groupType);
                List<String> expectedMemberIds = getMaskingGroupMemberIdsFromPaths(expectedMembers, groupType);
                expectedMemberIds.removeAll(existingMemberIds);
                if (expectedMemberIds.isEmpty()) {
                    _log.debug("Returning masking group {}, with required members.", maskingGroupPath);
                    resultMaskingGroupPath = maskingGroupPath;
                } else {
                    _log.debug("Masking group {}, members do not match !", maskingGroupPath);
                }
            }
        } catch (Exception e) {
            _log.debug(String.format("Caught exception in handleCreateMaskingGroupException - array: %s",
                    storage.getSerialNumber()), e);
        }
        _log.debug("{} handleCreateMaskingGroupException END....", storage.getSerialNumber());
        return resultMaskingGroupPath;
    }

    private boolean handleCreateMaskingViewException(StorageSystem storage, String groupName) throws Exception {
        CIMObjectPath maskingViewPath = _cimPath.getMaskingViewPath(storage, groupName);
        return (_helper.getInstance(storage, maskingViewPath, true, true, null) != null);
    }

    private boolean deleteMaskingView(StorageSystem storage,
            URI exportMaskURI, Map<StorageGroupPolicyLimitsParam, List<String>> childrenStorageGroupMap,
            TaskCompleter taskCompleter) throws Exception {
        boolean maskingWasDeleted = false;
        _log.debug("{} deleteMaskingView START...", storage.getSerialNumber());
        String groupName = _helper.getExportMaskName(exportMaskURI);
        CIMInstance maskingViewInstance = maskingViewExists(storage, groupName);
        if (maskingViewInstance == null) {
            _log.info("{} deleteMaskingView END...Masking view already deleted: {}",
                    storage.getSerialNumber(), groupName);
            return true;
        }

        try {
            // get parent ig from masking view
            CIMObjectPath igPath = _helper.getInitiatorGroupForGivenMaskingView(maskingViewInstance.getObjectPath(), storage);
            // Flag to indicate whether or not we need to use the EMCForce flag on this operation.
            // We currently use this flag when dealing with RP Volumes as they are tagged for RP and the
            // operation on these volumes would fail otherwise.
            boolean forceFlag = false;
            ExportMask exportMask =
                    _dbClient.queryObject(ExportMask.class, exportMaskURI);
            for (String volURI : exportMask.getUserAddedVolumes().values()) {
                forceFlag = ExportUtils.useEMCForceFlag(_dbClient, URI.create(volURI));
                if (forceFlag) {
                    break;
                }
            }

            CIMArgument[] inArgs = _helper.getDeleteMaskingViewInputArguments(storage, exportMaskURI, forceFlag);
            CIMArgument[] outArgs = new CIMArgument[5];

            // Collect the current list of associated IGs for the MaskingView. This
            // will include cascaded and child IGs.
            List<CIMObjectPath> igPaths = new ArrayList<CIMObjectPath>();
            getInitiatorGroupsFromMvOrIg(storage,
                    maskingViewInstance.getObjectPath(), igPaths);
            // remove parent IG
            igPaths.remove(igPath);

            WBEMClient client = _helper.getConnection(storage).getCimClient();
            // need to reset host IO limits before remove MV
            // if SG is associated with other MVs/parent groups, set IO Limits back on it at the end
            for (Entry<StorageGroupPolicyLimitsParam, List<String>> storageGroupEntry : childrenStorageGroupMap.entrySet()) {
                for (String storageGroupName : storageGroupEntry.getValue()) {
                    CIMObjectPath storageGroupPath = _cimPath.getMaskingGroupPath(
                            storage, storageGroupName,
                            SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup);
                    _helper.resetHostIOLimits(client, storage, storageGroupPath);
                }
            }

            // Invoke operation to delete the MaskingView. This should clean up
            // IG that are directly related to the MV. We will have to check if any
            // others that may be child IGs are left dangling. If they are and not
            // associated with other IGs or MVs, we shall have to delete them.
            SmisSynchSubTaskJob deleteJob = new SmisSynchSubTaskJob(null,
                    storage.getId(), "DeleteMaskingView");
            _helper.invokeMethodSynchronously(storage,
                    _cimPath.getControllerConfigSvcPath(storage),
                    "DeleteMaskingView", inArgs, outArgs, deleteJob);
            if (deleteJob.isSuccess()) {
                if (_helper.checkExists(storage, igPath, true, false) != null) {
                    List<CIMObjectPath> associatedMaskingViews = getAssociatedMaskingViews(storage, igPath);
                    List<CIMObjectPath> associatedIGs = getAssociatedParentIGs(storage, igPath);
                    if (associatedMaskingViews.isEmpty() &&
                            (associatedIGs.isEmpty() || _helper.isCascadedIG(storage, igPath))) {
                        // parentIGs has associated IGs, not the parent
                        // delete CIG if it is not associated with any MV (CTRL-9662)

                        // CTRL-9323 : deleting Parent IG associated with masking view.
                        deleteInitiatorGroup(storage, igPath);
                    } else {
                        _log.info(String.format(
                                "Did not delete %s as it is still associated to MaskingViews [%s] and/or AssociatedIGs [%s]",
                                igPath.toString(), Joiner.on(',').join(associatedMaskingViews), Joiner.on(',').join(associatedIGs)));
                    }
                } else {
                    _log.info("IG already deleted {}", igPath);
                }
                // CTRL-9323 : only child IGs will be processed, as parent is deleted already we will not hit the cyclic issue
                maskingWasDeleted = checkIGsAndDeleteIfUnassociated(storage, igPaths);
                if (!maskingWasDeleted) {
                    taskCompleter.error(_dbClient, DeviceControllerException.errors
                            .unableToDeleteIGs(groupName));
                    return false;
                }

                // Perform post-mask-delete cleanup steps
                ExportUtils.cleanupAssociatedMaskResources(_dbClient, exportMask);

            } else {
                String opName = ResourceOperationTypeEnum.DELETE_EXPORT_GROUP.getName();
                ServiceError serviceError = DeviceControllerException.errors.jobFailedOp(opName);
                taskCompleter.error(_dbClient, serviceError);
                return maskingWasDeleted;
            }
            _log.debug("{} deleteMaskingView END...", storage.getSerialNumber());
        } catch (WBEMException we) {
            _log.error(String.format("Problem when trying to delete masking view - array: %s, view: %s",
                    storage.getSerialNumber(), groupName), we);
            String opName = ResourceOperationTypeEnum.DELETE_EXPORT_GROUP.getName();
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(we);
            taskCompleter.error(_dbClient, serviceError);
            throw we;
        }
        _log.debug("{} deleteMaskingView END...", storage.getSerialNumber());
        return maskingWasDeleted;
    }

    /**
     * This method will navigate the provider to get a list of CIMObjectPath objects
     * that are associated with cimObjectPath, which can point to a MaskingView or an
     * InitiatorGroup. The routine tries to collect all of the IGs using cimObjectPath
     * as the root, so this allows you get a listing of all SGs for a Storage Group
     * in case it has a cascaded-SG.
     * 
     * @param storage [in] - StorageSystem object
     * @param cimObjectPath [in] - Object to start the search from
     * @param collectedPaths [in/out] - List of CIMObjectPath objects used for keeping
     *            track of the collected SGs.
     */
    private void getDeviceGroupsFromParent(StorageSystem storage,
            CIMObjectPath cimObjectPath,
            List<CIMObjectPath> collectedPaths)
            throws WBEMException {
        CloseableIterator<CIMObjectPath> sgPaths = null;
        if (collectedPaths == null) {
            return;
        }
        try {
            sgPaths = _helper.getAssociatorNames(storage, cimObjectPath, null,
                    SmisConstants.SE_DEVICE_MASKING_GROUP, null, null);
            while (sgPaths.hasNext()) {
                CIMObjectPath sgPath = sgPaths.next();
                // Check if the SG path is not the parent SG,
                // if not add it to the result list
                if (!collectedPaths.contains(sgPath)) {
                    collectedPaths.add(sgPath);
                    // This is recursive
                    getDeviceGroupsFromParent(storage, sgPath, collectedPaths);
                }
            }
        } finally {
            if (sgPaths != null) {
                sgPaths.close();
            }
        }
    }

    private List<CIMObjectPath> getMembersFromArguments(CIMArgument[] args) {
        CIMObjectPath[] members = null;
        List<CIMObjectPath> memberList = new ArrayList<CIMObjectPath>();
        for (int i = 0; i < args.length; i++) {
            CIMArgument inArg = args[i];
            if (inArg != null) {
                if (inArg.getName().equals("Members")) {
                    members = (CIMObjectPath[]) inArg.getValue();
                    break;
                }
            }
        }
        if (members != null) {
            for (CIMObjectPath memberPath : members) {
                if (memberPath != null) {
                    _log.debug("Adding expected member: {}", memberPath);
                    memberList.add(memberPath);
                }
            }
        }
        _log.debug("#expected members: {}", memberList.size());
        return memberList;
    }

    private List<CIMObjectPath> getExistingMaskingGroupMembers(StorageSystem storage,
            CIMObjectPath maskingGroupPath,
            String associatorName) {
        List<CIMObjectPath> existingMembers = new ArrayList<CIMObjectPath>();
        Iterator<CIMObjectPath> iterator = null;
        try {
            iterator = _helper.getConnection(storage).getCimClient().associatorNames(maskingGroupPath,
                    null, associatorName, null, null);
            while (iterator.hasNext()) {
                CIMObjectPath memberPath = iterator.next();
                _log.debug("Found existing member {}", memberPath);
                if (memberPath != null) {
                    existingMembers.add(memberPath);
                }
            }
        } catch (Exception e) {
            _log.debug(String.format("Problem when determining existing members of masking group - groupPath: %s",
                    maskingGroupPath), e);
        }
        return existingMembers;
    }

    private String getMaskingGroupMemberIdFromPath(CIMObjectPath memberPath,
            SmisCommandHelper.MASKING_GROUP_TYPE groupType) {
        String memberId = null;
        switch (groupType) {
            case SE_DeviceMaskingGroup:
                CIMProperty<String> deviceID = (CIMProperty<String>) memberPath.getKey("DeviceID");
                memberId = deviceID.getValue();
                break;
            case SE_InitiatorMaskingGroup:
                CIMProperty<String> instanceID = (CIMProperty<String>) memberPath.getKey("InstanceID");
                memberId = instanceID.getValue();
                break;
            case SE_TargetMaskingGroup:
                CIMProperty<String> name = (CIMProperty<String>) memberPath.getKey("Name");
                memberId = name.getValue();
                break;
        }
        return memberId;
    }

    private List<String> getMaskingGroupMemberIdsFromPaths(List<CIMObjectPath> memberPaths,
            SmisCommandHelper.MASKING_GROUP_TYPE groupType) {
        List<String> memberIds = new ArrayList<String>();
        for (CIMObjectPath memberPath : memberPaths) {
            String memberId = getMaskingGroupMemberIdFromPath(memberPath, groupType);
            if (memberId != null) {
                memberIds.add(memberId);
            }
        }
        return memberIds;
    }

    private void deleteMaskingGroups(StorageSystem storage,
            String groupName) throws Exception {
        _log.debug("{} Masking view does not exist .. cleaning up masking groups: {}",
                storage.getSerialNumber(), groupName);
        _log.info("disable RP tag");

        _helper.deleteMaskingGroup(storage, groupName, SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup);
        _helper.deleteMaskingGroup(storage, groupName, SmisCommandHelper.MASKING_GROUP_TYPE.SE_TargetMaskingGroup);
        _helper.deleteMaskingGroup(storage, groupName, SmisCommandHelper.MASKING_GROUP_TYPE.SE_InitiatorMaskingGroup);
        _log.debug("{} deleteMaskingGroups END...", storage.getSerialNumber());
    }

    private boolean isMaskingGroupCreated(StorageSystem storage, String groupName)
            throws Exception {
        _log.debug("{} isMaskingGroupCreated START...", storage.getSerialNumber());
        CIMInstance maskingViewInstance = maskingViewExists(storage, groupName);
        if (maskingViewInstance != null) {
            _log.info(
                    "{} Masking view exists ..END",
                    storage.getSerialNumber(), groupName);
            return true;
        }
        _log.debug("{} isMaskingGroupCreated END...", storage.getSerialNumber());
        return false;
    }

    private CIMInstance maskingViewExists(StorageSystem storage, String groupName) {
        CIMInstance maskingViewInstance = null;
        try {
            _log.debug("{} Looking up Masking View ...", storage.getSerialNumber());
            CIMObjectPath maskingViewPath = _cimPath.getMaskingViewPath(storage, groupName);
            maskingViewInstance = _helper.checkExists(storage, maskingViewPath, false, false);
        } catch (Exception e) {
            _log.debug(String.format("Did not find masking view - array: %s, view: %s",
                    storage.getSerialNumber(), groupName), e);
        }
        return maskingViewInstance;
    }

    private boolean isEmptyClusterName(String name) {
        return (name != null && name.equals(VIPR_NO_CLUSTER_SPECIFIED_NULL_VALUE));
    }

    /**
     * This method will navigate the provider to get a list of CIMObjectPath objects
     * that are associated with cimObjectPath, which can point to a MaskingView or an
     * InitiatorGroup. The routine tries to collect all of the IGs using cimObjectPath
     * as the root, so this allows you get a listing of all IGs for a MaskingView
     * in case it has a cascaded-IG.
     * 
     * @param storage [in] - StorageSystem object
     * @param cimObjectPath [in] - Object to start the search from (can be MaskingView
     *            or InitiatorGroup)
     * @param collectedPaths [in/out] - List of CIMObjectPath objects used for keeping
     *            track of the collected IGs.
     */
    private void getInitiatorGroupsFromMvOrIg(StorageSystem storage,
            CIMObjectPath cimObjectPath,
            List<CIMObjectPath> collectedPaths)
            throws WBEMException {
        CloseableIterator<CIMObjectPath> igPaths = null;
        if (collectedPaths == null) {
            return;
        }
        try {
            igPaths = _helper.getAssociatorNames(storage, cimObjectPath, null,
                    SmisConstants.SE_INITIATOR_MASKING_GROUP, null, null);
            while (igPaths.hasNext()) {
                CIMObjectPath igPath = igPaths.next();
                // Check if the IG path is not the parent IG,
                // if not add it to the result list
                if (!collectedPaths.contains(igPath)) {
                    collectedPaths.add(igPath);
                    // This is recursive, but IGs should only have 2 to 3 levels
                    getInitiatorGroupsFromMvOrIg(storage, igPath, collectedPaths);
                }
            }
        } finally {
            if (igPaths != null) {
                igPaths.close();
            }
        }
    }

    /**
     * Function will loop through the list of IG CIMObjectPath objects,
     * determining if any can be deleted. The IG will be delete if it no longer belongs
     * to any MaskingView. Any child IGs of the specified IGs will also be deleted,
     * if they are no longer part of any MaskingView.
     * 
     * @param storage [in] - StorageSystem object
     * @param igList [in] - List of CIMObjectPath objects to be checked
     * @throws Exception
     */
    private boolean checkIGsAndDeleteIfUnassociated(StorageSystem storage,
            List<CIMObjectPath> igList)
            throws Exception {
        boolean anyFailures = false;
        Collection<String> deviceIds =
                Collections2.transform(igList, cimObjectPathInstanceId());
        _log.info(String.format("Checking the following IGs { %s }",
                Joiner.on(',').join(deviceIds)));
        for (CIMObjectPath igPath : igList) {
            CIMInstance igInstance = _helper.checkExists(storage, igPath, true, true);
            if (igInstance == null) {
                continue;
            }
            String igDeviceId =
                    igPath.getKey(SmisConstants.CP_INSTANCE_ID).getValue().toString();
            List<CIMObjectPath> associatedMaskingViews =
                    getAssociatedMaskingViews(storage, igPath);
            // If there aren't any MaskingView to which this IG belongs,
            // then we can look for related IGs (if any) and delete it,
            // if there aren't any.
            if (associatedMaskingViews.isEmpty()) {
                _log.info(String.format("IG %s is not associated to any Masking View",
                        igDeviceId));
                // get associated IGs
                List<CIMObjectPath> associatedIGs = getAssociatedParentIGs(storage, igPath);
                if (associatedIGs.isEmpty()) {
                    // We got here: this means that there are no masking views or
                    // associated IGs for this IG that we are checking. It is a
                    // candidate for deletion, so we will attempt that...
                    _log.info(String.
                            format("IG %s is not associated with any other IGs, " +
                                    "it can be deleted", igDeviceId));
                    boolean success =
                            deleteInitiatorGroup(storage, igInstance.getObjectPath());
                    if (!success) {
                        anyFailures = true;
                    }
                } else {
                    // There are still some associated IGs for this IG. We cannot
                    // delete it.
                    Collection<String> igDevIds =
                            Collections2.transform(associatedIGs,
                                    cimObjectPathInstanceId());
                    _log.info(String.format("IG %s is associated to other IGs {%s}",
                            igDeviceId, Joiner.on(',').join(igDevIds)));
                }
            } else {
                // There are still some associated MaskingViews for this IG. We
                // cannot delete it.
                Collection<String> mvDevIds =
                        Collections2.transform(associatedMaskingViews,
                                cimObjectPathInstanceId());
                _log.info(String.format("IG %s is associated to Masking Views {%s}",
                        igDeviceId, Joiner.on(',').join(mvDevIds)));
            }
        }
        return (!anyFailures);
    }

    /**
     * Returns a List of CIMObjectObject of Symm_LunMaskingView objects for a given IG
     * CIMObjectPath.
     * 
     * @param storage [in] - StorageSystem object
     * @param igPath [in] - CIMObjectPath object to be query
     * @return List of CIMObjectPath
     */
    private List<CIMObjectPath> getAssociatedMaskingViews(StorageSystem storage,
            CIMObjectPath igPath)
            throws WBEMException {
        List<CIMObjectPath> result = new ArrayList<CIMObjectPath>();
        CloseableIterator<CIMObjectPath> maskingViewPaths = null;
        try {
            maskingViewPaths = _helper.getAssociatorNames(storage, igPath, null,
                    SmisConstants.SYMM_LUN_MASKING_VIEW, null, null);
            while (maskingViewPaths.hasNext()) {
                result.add(maskingViewPaths.next());
            }
        } finally {
            if (maskingViewPaths != null) {
                maskingViewPaths.close();
            }
        }
        return result;
    }

    /**
     * Returns a List of CIMObjectObject of SE_InitiatorMaskingGroup objects for a given IG CIMObjectPath.
     * 
     * @param storage [in] - StorageSystem object
     * @param igPath [in] - CIMObjectPath object to be query
     * @return List of CIMObjectPath
     */
    private List<CIMObjectPath> getAssociatedParentIGs(StorageSystem storage, CIMObjectPath igPath)
            throws WBEMException {
        List<CIMObjectPath> result = new ArrayList<>();
        CloseableIterator<CIMObjectPath> parentIGPaths = null;
        try {
            parentIGPaths = _helper.getAssociatorNames(storage, igPath, null,
                    SmisConstants.SE_INITIATOR_MASKING_GROUP, null, null);
            while (parentIGPaths.hasNext()) {
                result.add(parentIGPaths.next());
            }
        } finally {
            if (parentIGPaths != null) {
                parentIGPaths.close();
            }
        }
        return result;
    }

    /**
     * Function will delete the IG specified by the CIMObjectPath from the array.
     * 
     * @param storage [in] - StorageSystem object
     * @param igPath [in] - CIMObjectPath object to be query
     * @throws WBEMException
     * @throws Exception
     */
    private boolean deleteInitiatorGroup(StorageSystem storage, CIMObjectPath igPath)
            throws Exception {
        CIMArgument[] deleteIn =
                _helper.getDeleteInitiatorMaskingGroup(storage, igPath);
        CIMArgument[] deleteOut = new CIMArgument[5];
        SmisJob deleteIgJob = new SmisSynchSubTaskJob(null, storage.getId(),
                SmisConstants.DELETE_GROUP);
        _helper.invokeMethodSynchronously(storage,
                _cimPath.getControllerConfigSvcPath(storage),
                SmisConstants.DELETE_GROUP, deleteIn, deleteOut, deleteIgJob);
        return deleteIgJob.isSuccess();
    }

    /**
     * A functor used in Collection2.transform for the purpose of generating a list of
     * InstanceIDs from a list of CIMObjectPath objects.
     * 
     * @return Function that can be used by the transform operations
     */
    private Function<CIMObjectPath, String> cimObjectPathInstanceId() {
        return new Function<CIMObjectPath, String>() {
            @Override
            public String apply(CIMObjectPath cimObjectPath) {
                String result = SmisConstants.EMPTY_STRING;
                if (cimObjectPath != null) {
                    Object value = cimObjectPath.getKey(SmisConstants.CP_INSTANCE_ID);
                    result = (value != null) ? value.toString() :
                            SmisConstants.EMPTY_STRING;
                }
                return result;
            }
        };
    }

    private String checkRequestAndGetClusterName(List<Initiator> initiatorList,
            TaskCompleter taskCompleter) {
        String clusterName = null;
        if (!validateInitiatorHostOS(initiatorList)) {
            _log.error("Initiators have different OS types");
            taskCompleter.error(_dbClient,
                    DeviceControllerException.errors.initiatorsWithDifferentOSType());
            return clusterName;
        }

        clusterName = getClusterNameFromInitiators(initiatorList);
        if (clusterName == null) {
            _log.error("Initiators have clustered and non-clustered initiators");
            taskCompleter.error(_dbClient,
                    DeviceControllerException.errors
                            .mixingClusteredAndNonClusteredInitiators());
            return clusterName;
        }

        return clusterName;
    }

    private CIMObjectPath createOrSelectStorageGroup(StorageSystem storage,
            URI exportMaskURI,
            Collection<Initiator> initiators,
            VolumeURIHLU[] volumeURIHLUs, String parentGroupName,
            Map<StorageGroupPolicyLimitsParam, CIMObjectPath> newlyCreatedChildVolumeGroups, TaskCompleter taskCompleter)
            throws Exception {
        List<CIMObjectPath> childVolumeGroupsToBeAddedToParentGroup =
                new ArrayList<CIMObjectPath>();
        String groupName = null;
        CIMObjectPath groupPath = null;
        ExportMask mask = _dbClient.queryObject(ExportMask.class, exportMaskURI);

        // group volumes based on policy
        ListMultimap<StorageGroupPolicyLimitsParam, VolumeURIHLU> policyToVolumeGroup = ArrayListMultimap.create();
        WBEMClient client = _helper.getConnection(storage).getCimClient();

        /**
         * Group Volumes by Fast Policy and Host IO limit attributes
         * 
         * policyToVolumeGroupEntry - this will essentially have multiple Groups
         * E.g Group 1--> Fast Policy (FP1)+ FEBandwidth (100)
         * Group 2--> Fast Policy (FP2)+ IOPS (100)
         * Group 3--> FEBandwidth (100) + IOPS (100) ..
         * 
         * For each Group {
         * 1. Create a Storage Group.
         * 2. Associate Fast Policy, bandwidth and IOPs ,based on the Group key.
         * 
         * On failure ,remove the storage group, disassociate the added properties.
         * }
         */
        for (VolumeURIHLU volumeUriHLU : volumeURIHLUs) {
            StorageGroupPolicyLimitsParam sgPolicyLimitsParam = null;
            URI boUri = volumeUriHLU.getVolumeURI();
            BlockObject bo = BlockObject.fetch(_dbClient, boUri);
            boolean fastAssociatedAlready = false;
            // Always treat fast volumes as non-fast if fast is associated on these volumes already
            // Export fast volumes to 2 different nodes.
            if (_helper.isFastPolicy(volumeUriHLU.getAutoTierPolicyName())) {
                fastAssociatedAlready = _helper.checkVolumeAssociatedWithAnySGWithPolicy(bo.getNativeId(), storage,
                        volumeUriHLU.getAutoTierPolicyName());
            }

            // Force the policy name to NONE if any of the following conditions are true:
            // 1. FAST policy is already associated.
            // 2. The BO is a RP Journal - Per RecoverPoint best practices a journal volume
            // should not be created with a FAST policy assigned.
            if (fastAssociatedAlready || isRPJournalVolume(bo)) {
                _log.info("Forcing policy name to NONE to prevent volume from using FAST policy.");
                volumeUriHLU.setAutoTierPolicyName(Constants.NONE);
                sgPolicyLimitsParam = new StorageGroupPolicyLimitsParam(Constants.NONE,
                        volumeUriHLU.getHostIOLimitBandwidth(),
                        volumeUriHLU.getHostIOLimitIOPs(),
                        storage);
            }
            else {
                sgPolicyLimitsParam = new StorageGroupPolicyLimitsParam(volumeUriHLU, storage);
            }

            policyToVolumeGroup.put(sgPolicyLimitsParam, volumeUriHLU);
        }

        _log.info("{} Groups generated based on grouping volumes by fast policy", policyToVolumeGroup.size());

        /** Grouped Volumes based on Fast Policy */
        for (Entry<StorageGroupPolicyLimitsParam, Collection<VolumeURIHLU>> policyToVolumeGroupEntry : policyToVolumeGroup.asMap()
                .entrySet()) {

            List<CIMObjectPath> childVolumeGroupsToBeAdded = new ArrayList<CIMObjectPath>();
            StorageGroupPolicyLimitsParam storageGroupPolicyLimitsParam = policyToVolumeGroupEntry.getKey();
            ListMultimap<String, VolumeURIHLU> expectedVolumeHluMap = ControllerUtils
                    .getVolumeNativeGuids(policyToVolumeGroupEntry.getValue(), _dbClient);
            Map<String, Set<String>> existingGroupPaths;

            // in case of non-fast always create a new Storage Group
            if (!_helper.isFastPolicy(storageGroupPolicyLimitsParam.getAutoTierPolicyName())) {
                _log.info("Non-FAST create a new Storage Group");
                VolumeURIHLU[] volumeURIHLU = new VolumeURIHLU[policyToVolumeGroupEntry
                        .getValue().size()];
                volumeURIHLU = policyToVolumeGroupEntry.getValue().toArray(volumeURIHLU);

                groupName = generateStorageGroupName(storage, mask, initiators, storageGroupPolicyLimitsParam);

                _log.debug("Group Name Created {}", groupName);
                groupPath = createVolumeGroup(storage, groupName, volumeURIHLU, taskCompleter, true);
                _log.info("Volume Group {} created on Array", groupPath);

            }
            // in case of fast enabled, try to find any existing groups which can be reused.
            else {
                /**
                 * Find any existing Storage Groups can be reused, in
                 * case of Fast Enabled volumes
                 */
                _log.info("Running Storage Group Selection Process");
                existingGroupPaths = _helper.findAnyStorageGroupsCanBeReUsed(storage, expectedVolumeHluMap, storageGroupPolicyLimitsParam);

                if (existingGroupPaths.size() > 0) {
                    _log.info("Existing Storage Groups Found :"
                            + Joiner.on("\t").join(existingGroupPaths.keySet()));
                } else {
                    _log.info("No existing Storage Groups Found for policy: " + storageGroupPolicyLimitsParam.toString());
                }

                if (existingGroupPaths.size() > 0) {
                    childVolumeGroupsToBeAdded.addAll(_helper.constructMaskingGroupPathsFromNames(
                            existingGroupPaths.keySet(), storage));
                }
                Set<String> volumesInExistingStorageGroups = _helper
                        .constructVolumeNativeGuids(existingGroupPaths.values());
                _log.debug("Volumes part of existing reusable Storage Groups {}", Joiner.on("\t").join(volumesInExistingStorageGroups));
                // Storage Group needs to be created for those volumes,
                // which doesn't fit into
                // existing groups.
                Set<String> diff = Sets.difference(expectedVolumeHluMap.asMap().keySet(),
                        volumesInExistingStorageGroups);
                _log.debug("Remaining Volumes, for which new Storage Group needs to be created", Joiner.on("\t").join(diff));
                // need to construct a new group for remaining volumes.
                if (!diff.isEmpty()) {
                    VolumeURIHLU[] volumeURIHLU = ControllerUtils.constructVolumeUriHLUs(
                            diff, expectedVolumeHluMap);
                    groupName = generateStorageGroupName(storage, mask, initiators, storageGroupPolicyLimitsParam);
                    _log.debug("Group Name Created :", groupName);
                    groupPath = createVolumeGroup(storage, groupName, volumeURIHLU, taskCompleter, true);
                    _log.info("Volume Group {} created on Array {}", groupName, storage.getSerialNumber());
                }
            }
            if (null != groupPath) {
                /** used later in deleting created groups on failure */
                newlyCreatedChildVolumeGroups.put(storageGroupPolicyLimitsParam, groupPath);
                childVolumeGroupsToBeAdded.add(groupPath);
            }

            /**
             * check whether Storage Group is associated with Fast
             * Policy, if not associate
             */
            if (_helper.isFastPolicy(storageGroupPolicyLimitsParam.getAutoTierPolicyName())) {
                for (CIMObjectPath path : childVolumeGroupsToBeAdded) {
                    // CTRL-8527 new condition was added to support adding new node to a single node cluster case (specifically fast
                    // volumes).
                    if (!_helper.checkVolumeAssociatedWithPhantomSG(path, storage, storageGroupPolicyLimitsParam.getAutoTierPolicyName())
                            &&
                            !_helper.checkVolumeGroupAssociatedWithPolicy(storage, path,
                                    storageGroupPolicyLimitsParam.getAutoTierPolicyName())) {
                        _log.debug("Adding Volume Group {} to Fast Policy {}", path, storageGroupPolicyLimitsParam.getAutoTierPolicyName());
                        addVolumeGroupToAutoTieringPolicy(storage, storageGroupPolicyLimitsParam.getAutoTierPolicyName(), path,
                                taskCompleter);
                    }
                }
            }
            childVolumeGroupsToBeAddedToParentGroup.addAll(childVolumeGroupsToBeAdded);
        }
        Map<StorageGroupPolicyLimitsParam, Set<String>> allStorageGroups = _helper.getExistingSGNamesFromArray(storage);
        Set<String> existingGroupNames = new HashSet<>();
        for (Set<String> groupNames : allStorageGroups.values()) {
            existingGroupNames.addAll(groupNames);
        }
        // Avoid duplicate names for the Cascaded VolumeGroup
        parentGroupName = _helper.generateGroupName(existingGroupNames, parentGroupName);
        CIMObjectPath cascadedGroupPath = createCascadedVolumeGroup(storage, parentGroupName, childVolumeGroupsToBeAddedToParentGroup,
                taskCompleter);

        // update Host IO Limit properties for child storage group if applicable.
        // NOTE: this need to be done after createCascadedVolumeGroup, because the child groups must need to be associated to a parent
        // for proper roll back , that is volume removal, if exception is thrown during update
        for (Entry<StorageGroupPolicyLimitsParam, CIMObjectPath> createdChildVolumeGroupEntry : newlyCreatedChildVolumeGroups.entrySet()) {
            CIMObjectPath childGroupPath = createdChildVolumeGroupEntry.getValue();

            StorageGroupPolicyLimitsParam storageGroupPolicyLimitsParam = createdChildVolumeGroupEntry.getKey();

            if (storageGroupPolicyLimitsParam.isHostIOLimitBandwidthSet()) {
                _helper.updateHostIOLimitBandwidth(client, childGroupPath, storageGroupPolicyLimitsParam.getHostIOLimitBandwidth());
            }

            if (storageGroupPolicyLimitsParam.isHostIOLimitIOPsSet()) {
                _helper.updateHostIOLimitIOPs(client, childGroupPath, storageGroupPolicyLimitsParam.getHostIOLimitIOPs());
            }
        }

        return cascadedGroupPath;
    }

    /**
     * This is used only for VMAX3.
     */
    private CIMObjectPath
            createOrSelectSLOBasedStorageGroup(StorageSystem storage,
                    URI exportMaskURI,
                    Collection<Initiator> initiators,
                    VolumeURIHLU[] volumeURIHLUs, String parentGroupName,
                    Map<StorageGroupPolicyLimitsParam, CIMObjectPath> newlyCreatedChildVolumeGroups,
                    TaskCompleter taskCompleter)
                    throws Exception {
        List<CIMObjectPath> childVolumeGroupsToBeAddedToParentGroup =
                new ArrayList<CIMObjectPath>();
        String groupName = null;
        CIMObjectPath groupPath = null;
        ExportMask mask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
        // group volumes based on policy
        ListMultimap<StorageGroupPolicyLimitsParam, VolumeURIHLU> policyToVolumeGroup = ArrayListMultimap.create();
        WBEMClient client = _helper.getConnection(storage).getCimClient();

        for (VolumeURIHLU volumeUriHLU : volumeURIHLUs) {
            policyToVolumeGroup.put(new StorageGroupPolicyLimitsParam(volumeUriHLU, storage, _helper), volumeUriHLU);
        }

        _log.info("{} Groups generated based on grouping volumes by fast policy", policyToVolumeGroup.size());
        Map<StorageGroupPolicyLimitsParam, Set<String>> existingGroupNames = _helper.getExistingSGNamesFromArray(storage);
        /** Grouped Volumes based on Fast Policy */
        for (Entry<StorageGroupPolicyLimitsParam, Collection<VolumeURIHLU>> policyToVolumeGroupEntry : policyToVolumeGroup.asMap()
                .entrySet()) {

            List<CIMObjectPath> childVolumeGroupsToBeAdded = new ArrayList<CIMObjectPath>();

            StorageGroupPolicyLimitsParam storageGroupPolicyLimitsParam = policyToVolumeGroupEntry.getKey();
            ListMultimap<String, VolumeURIHLU> expectedVolumeHluMap = ControllerUtils
                    .getVolumeNativeGuids(policyToVolumeGroupEntry.getValue(), _dbClient);
            Map<String, Set<String>> existingGroupPaths;

            /**
             * Find any existing Storage Groups can be reused, in
             * case of Fast Enabled volumes
             */
            _log.info("Running Storage Group Selection Process");
            existingGroupPaths = _helper.findAnyStorageGroupsCanBeReUsed(storage, expectedVolumeHluMap, storageGroupPolicyLimitsParam);

            _log.info("Existing Storage Groups Found :"
                    + Joiner.on("\t").join(existingGroupPaths.keySet()));
            if (existingGroupPaths.size() > 0) {
                if (existingGroupPaths.size() > 0) {
                    childVolumeGroupsToBeAdded.addAll(_helper.constructMaskingGroupPathsFromNames(
                            existingGroupPaths.keySet(), storage));
                }
            }

            Set<String> volumesInExistingStorageGroups = _helper
                    .constructVolumeNativeGuids(existingGroupPaths.values());
            _log.debug("Volumes part of existing reusable Storage Groups {}", Joiner.on("\t").join(volumesInExistingStorageGroups));
            // Storage Group needs to be created for those volumes,
            // which doesn't fit into
            // existing groups.
            Set<String> diff = Sets.difference(expectedVolumeHluMap.asMap().keySet(),
                    volumesInExistingStorageGroups);
            _log.debug("Remaining Volumes, for which new Storage Group needs to be created", Joiner.on("\t").join(diff));
            // need to construct a new group for remaining volumes.
            if (!diff.isEmpty()) {
                VolumeURIHLU[] volumeURIHLU = ControllerUtils.constructVolumeUriHLUs(
                        diff, expectedVolumeHluMap);
                // TODO: VMAX3 customized names
                groupName = generateStorageGroupName(storage, mask, initiators, storageGroupPolicyLimitsParam);
                _log.debug("Group Name Created :", groupName);
                groupPath = createVolumeGroup(storage, groupName, volumeURIHLU, taskCompleter, true);
                _log.info("{} Volume Group created on Array {}", storage.getSerialNumber());
            }

            if (null != groupPath) {
                /** used later in deleting created groups on failure */
                newlyCreatedChildVolumeGroups.put(storageGroupPolicyLimitsParam, groupPath);
                childVolumeGroupsToBeAdded.add(groupPath);
            }

            childVolumeGroupsToBeAddedToParentGroup.addAll(childVolumeGroupsToBeAdded);
        }
        CIMObjectPath cascadedGroupPath = createCascadedVolumeGroup(storage, parentGroupName, childVolumeGroupsToBeAddedToParentGroup,
                taskCompleter);

        // update Host IO Limit properties for child storage group if applicable.
        // NOTE: this need to be done after createCascadedVolumeGroup, because the child groups must need to be associated to a parent
        // for proper roll back , that is volume removal, if exception is thrown during update
        for (Entry<StorageGroupPolicyLimitsParam, CIMObjectPath> createdChildVolumeGroupEntry : newlyCreatedChildVolumeGroups.entrySet()) {
            CIMObjectPath childGroupPath = createdChildVolumeGroupEntry.getValue();

            StorageGroupPolicyLimitsParam storageGroupPolicyLimitsParam = createdChildVolumeGroupEntry.getKey();

            if (storageGroupPolicyLimitsParam.isHostIOLimitBandwidthSet()) {
                _helper.updateHostIOLimitBandwidth(client, childGroupPath, storageGroupPolicyLimitsParam.getHostIOLimitBandwidth());
            }

            if (storageGroupPolicyLimitsParam.isHostIOLimitIOPsSet()) {
                _helper.updateHostIOLimitIOPs(client, childGroupPath, storageGroupPolicyLimitsParam.getHostIOLimitIOPs());
            }
        }

        return cascadedGroupPath;
    }

    private CIMObjectPath createOrUpdateInitiatorGroups(StorageSystem storage,
            URI exportMaskURI, String cigName,
            String igCustomTemplateName, List<Initiator> initiatorList,
            TaskCompleter taskCompleter)
            throws Exception {
        ExportMask mask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
        String exportType = ExportMaskUtils.getExportType(_dbClient, mask);
        boolean singleIG = false;

        // Determine if we are doing consistent LUNs. Default is to do so. For VPLEX IGs, we want Consistent LUNS false.
        // This parameter is set by the VPlexBackendManager for VPlex Export Masks.
        boolean consistentLUNs = true;
        StringSet consistentLUNsParameter = _helper.getExportMaskDeviceDataMapParameter(
                exportMaskURI, ExportMask.DeviceDataMapKeys.VMAXConsistentLUNs.name());
        if (consistentLUNsParameter != null && consistentLUNsParameter.contains("false")) {
            consistentLUNs = false;
        }

        String clusterName = checkRequestAndGetClusterName(initiatorList, taskCompleter);
        if (clusterName == null) {
            // Could not get the cluster name in the request so this is an invalid
            // request, which should have the error code stuffed already. Return null
            // and the client will have to check for this condition
            return null;
        }

        ListMultimap<CIMObjectPath, String> igToInitiators = ArrayListMultimap.create();
        Map<String, CIMObjectPath> initiatorsToIGs =
                mapInitiatorsToInitiatorGroups(igToInitiators, storage, initiatorList);

        if (isEmptyClusterName(clusterName)) {
            // This is case where the the initiators are not associated with a cluster.
            // So, we what to make sure that all the initiators that have been passed
            // belong to the existing IG (if one exists).
            if (igToInitiators.keySet().size() > 1) {
                taskCompleter.error(_dbClient, DeviceControllerException.errors
                        .nonClusterExportWithInitiatorsInDifferentExistingIGs());
                return null;
            }
        }

        // club the initiators by host names
        Map<String, List<Initiator>> initiatorListByHost =
                clubInitiatorsByHostname(initiatorList);

        List<CIMObjectPath> initiatorGroupPaths = new ArrayList<CIMObjectPath>();
        CIMObjectPath initiatorGroupPath = null;
        // Hosts that were grouped together for single IG support
        Set<String> groupedHosts = new HashSet<>();
        for (Map.Entry<String, List<Initiator>> entry : initiatorListByHost.entrySet()) {
            String hostName = entry.getKey();
            if (groupedHosts.contains(hostName)) {
                continue;
            }

            List<Initiator> initiatorListForHost = entry.getValue();
            Collection<String> initiatorPorts =
                    Collections2.transform(initiatorListForHost,
                            CommonTransformerFunctions.fctnInitiatorToPortName());

            Initiator sample = null;
            // CTRL-5068 - We will select an IG even if it has a subset of host initiators. So we need to go through all initiators to find
            // if there
            // is a matching IG
            for (Initiator initiator : initiatorListForHost) {
                sample = initiator;
                initiatorGroupPath = initiatorsToIGs.get(Initiator.
                        normalizePort(sample.getInitiatorPort()));
                if (initiatorGroupPath != null) {
                    _log.info("Found an initiator which is part of some IG");
                    break;
                }
            }

            if (initiatorGroupPath != null) {
                CIMInstance igInstance = _helper.getInstance(storage,
                        initiatorGroupPath, false, false, SmisConstants.PS_ELEMENT_NAME);
                String igName = CIMPropertyFactory.getPropertyValue(igInstance,
                        SmisConstants.CP_ELEMENT_NAME);
                _log.info("createOrUpdateInitiatorGroups - Existing initiatorGroup " +
                        "found, {}, validating it...", igName);

                // Yes - initiator is some IG, validate the IG that it's in has the
                // same ports or sub set of host ports. If not ==> error
                List<String> igInitiatorPorts = igToInitiators.get(initiatorGroupPath);

                _log.info("createOrUpdateInitiatorGroups - List of initiators: {} to create/update on host: " +
                        hostName, Joiner.on(',').join(initiatorPorts));

                _log.info("createOrUpdateInitiatorGroups - List of initiators: {} in IG path: " +
                        initiatorGroupPath, Joiner.on(',').join(igInitiatorPorts));

                // This error check will apply only in the non-VPlex case. Meaning that
                // there should be a reference to a Host object for the initiators.
                // That's why there is a check here for if sample.getHost() != null.
                if (sample.getHost() != null) {

                    Set<String> initiatorDiff = Sets.difference(new HashSet<String>(igInitiatorPorts), new HashSet<String>(initiatorPorts));
                    if (!initiatorDiff.isEmpty()) {
                        /*
                         * If the IG has initiators other than those in the export request,
                         * then be sure the it is subset of host's initiators in
                         * ViPR database.
                         */
                        List<String> initiatorsInDb = queryHostInitiators(sample.getHost());
                        Set<String> initiatorsTmp = new HashSet<>();
                        initiatorsTmp.addAll(initiatorDiff);

                        _log.info("createOrUpdateInitiatorGroups - List of initiators: {} in ViPR database on host: " +
                                hostName, Joiner.on(',').join(initiatorsInDb));

                        initiatorsTmp.removeAll(initiatorsInDb);
                        if (!initiatorsTmp.isEmpty() && isEmptyClusterName(clusterName)) {
                            // if not a subset, throw exception
                            taskCompleter.error(_dbClient, DeviceControllerException.errors
                                    .existingInitiatorGroupDoesNotHaveSamePorts(igName));
                            return null;
                        }

                        // In the case of a single IG that contains some, but not all, of the cluster's initiators,
                        // We can add any needed initiators right here and maintain the big IG.
                        if (!isEmptyClusterName(clusterName) && !initiatorsTmp.isEmpty()) {
                            initiatorsInDb = queryClusterInitiators(sample.getHost());

                            _log.info("createOrUpdateInitiatorGroups - List of initiators: {} in ViPR database on cluster: " +
                                    sample.getClusterName(), Joiner.on(',').join(initiatorsInDb));

                            initiatorsTmp.removeAll(initiatorsInDb);
                            if (!initiatorsTmp.isEmpty()) {
                                // if not a subset, throw exception
                                taskCompleter.error(_dbClient, DeviceControllerException.errors
                                        .existingInitiatorGroupDoesNotHaveSamePorts(igName));
                                return null;
                            }

                            _log.info("createOrUpdateInitiatorGroups - List of initiators in cluster found to match IG we want to use.");

                            // Update the initiatorListForHost to include all initiators for this cluster
                            for (String initiatorInDb : initiatorsInDb) {
                                Initiator initiator = ExportUtils.getInitiator(Initiator.toPortNetworkId(initiatorInDb), _dbClient);
                                if (!initiator.getHost().equals(sample.getHost())) {
                                    initiatorListForHost.add(initiator);
                                }
                                groupedHosts.add(initiator.getHostName());
                            }

                            singleIG = true;

                        }
                    }
                }

                // If we got here, then we're in the clear to use this IG
                _log.info("createOrUpdateInitiatorGroups - Existing initiatorGroup {} " +
                        "looks okay to use", igName);

                // if there are new initiators, add them to the IG
                initiatorGroupPath = createInitiatorGroupWithInitiators(storage, igName,
                        initiatorListForHost, consistentLUNs, taskCompleter);
                // If we're modifying a single IG that already exists in a masking view,
                // we're done updating the initiator group.
                if (singleIG) {
                    return initiatorGroupPath;
                }
            } else {
                // No - initiator is not in an IG, so will need to create an IG and add
                // the initiators to it

                // Get the initiator group name from the custom name framework
                DataSource igDataSource = ExportMaskUtils.getExportDatasource(storage, initiatorListForHost, dataSourceFactory,
                        igCustomTemplateName);
                String initiatorGroupName = customConfigHandler.getComputedCustomConfigValue(igCustomTemplateName, storage.getSystemType(),
                        igDataSource);

                // Non-VPLEX case
                if (sample != null && sample.getHost() != null) {
                    // In this case, we may be asked to add initiators to a mask where the initiators are part of a host,
                    // but the initiators are not yet represented in an IG, however other initiators in the host are. Get the ports
                    // associated with the host and see if you can find an IG (stand-alone or child) where the initiators in
                    // initiatorList belong that are part of the mask the orchestrator sent down.
                    Entry<String, Boolean> igName = findExistingIGForHostAndMask(storage, mask, sample.getHost());
                    if (igName != null && igName.getKey() != null) {
                        initiatorGroupName = igName.getKey();
                        _log.info("createOrUpdateInitiatorGroups - Found an existing " +
                                "initiator group. Will attempt to add initiators for host {} " +
                                "and initiators {}", hostName,
                                Joiner.on(',').join(initiatorPorts));
                        if (igName.getValue()) {
                            // We want to preserve a flat non-cascaded IG if possible.
                            singleIG = true;
                        }
                    } else {
                        _log.info("createOrUpdateInitiatorGroups - Did not find any existing " +
                                "initiator group. Will attempt to create a new one for host {} " +
                                "and initiators {}", hostName,
                                Joiner.on(',').join(initiatorPorts));
                    }
                }

                initiatorGroupPath =
                        createInitiatorGroupWithInitiators(storage, initiatorGroupName,
                                initiatorListForHost, consistentLUNs, taskCompleter);
            }
            initiatorGroupPaths.add(initiatorGroupPath);
        }
        // One node cluster - create CIG only if cluster type or if consistentLUNs==false (VPlex)
        if (consistentLUNs == false ||
                (!singleIG && ExportGroupType.Cluster.name().equalsIgnoreCase(exportType))) {
            // Create cascaded initiator group the IG(s) created above
            initiatorGroupPath =
                    createInitiatorGroupWithInitiatorGroups(storage,
                            _dbClient.queryObject(ExportMask.class, exportMaskURI),
                            cigName, initiatorGroupPaths, consistentLUNs, taskCompleter);
        }
        return initiatorGroupPath;
    }

    /**
     * Given a masking view and a host, find a standalone or child IG that has a subset of
     * initiators and return that ig name.
     * 
     * @param storage storage
     * @param mask export mask
     * @param host host uri
     * @return initiator group name and if it's a standalone IG or not. will never be a cascaded IG.
     */
    private Entry<String, Boolean> findExistingIGForHostAndMask(StorageSystem storage, ExportMask mask, URI host) {
        List<String> initiatorsInDb = queryHostInitiators(host);
        CloseableIterator<CIMInstance> cigInstances = null;
        CloseableIterator<CIMInstance> igInstances = null;
        try {
            // Find the initiator group associated with the masking view
            // Get the masking view associated with the export.
            CIMInstance maskingViewInstance = this.maskingViewExists(storage, mask.getMaskName());

            // We only care if the masking view exists already.
            if (maskingViewInstance != null) {
                // Get the initiator group associated with the masking view
                cigInstances = _helper.getAssociatorInstances(storage, maskingViewInstance.getObjectPath(), null,
                        SmisConstants.SE_INITIATOR_MASKING_GROUP, null, null, SmisConstants.PS_ELEMENT_NAME);

                // If there is no initiator group, this doesn't qualify (should never be the case)
                if (!cigInstances.hasNext()) {
                    _log.info(String.format("createOrUpdateInitiatorGroups - There is no initiator group associated with export mask: %s",
                            mask.getMaskName()));
                }

                // Get the single instance of the initiator group off the masking view.
                // This will either be a cascaded IG or a standalone IG.
                CIMInstance cigInstance = cigInstances.next();

                // Cyclic issue - IG is added to a masking view and also a child of an existing CIG.
                if (_helper.isCascadedIG(storage, cigInstance.getObjectPath())) {

                    // Get the children initiator group associated with the masking view (if applicable)
                    igInstances = _helper.getAssociatorInstances(storage, cigInstance.getObjectPath(), null,
                            SmisConstants.SE_INITIATOR_MASKING_GROUP, null, null, SmisConstants.PS_ELEMENT_NAME);

                    if (igInstances.hasNext()) {
                        while (igInstances.hasNext()) {
                            // Child initiator groups
                            CIMInstance igInstance = igInstances.next();
                            if (igContainsInitiators(storage, igInstance, initiatorsInDb)) {
                                return new AbstractMap.SimpleEntry<String, Boolean>(CIMPropertyFactory.getPropertyValue(
                                        igInstance, SmisConstants.CP_ELEMENT_NAME), Boolean.FALSE);
                            }
                        }
                    }
                } else {
                    // This parent initiator group doesn't contain children, therefore it is standalone.
                    if (igContainsInitiators(storage, cigInstance, initiatorsInDb)) {
                        return new AbstractMap.SimpleEntry<String, Boolean>(CIMPropertyFactory.getPropertyValue(cigInstance,
                                SmisConstants.CP_ELEMENT_NAME), Boolean.TRUE);
                    }
                }

            }
        } catch (Exception e) {
            // TODO: fill this in with task information
            _log.error(String.format("findExistingIGForHostAndMask failed - maskName: %s", mask.getMaskName()), e);
            String opName = ResourceOperationTypeEnum.ADD_EXPORT_INITIATOR.getName();
            ServiceError serviceError = DeviceControllerException.errors.jobFailedOpMsg(opName, e.getMessage());
        } finally {
            if (cigInstances != null) {
                cigInstances.close();
            }
            if (igInstances != null) {
                igInstances.close();
            }
        }
        return new AbstractMap.SimpleEntry<String, Boolean>(null, Boolean.FALSE);
    }

    /**
     * Compare initiators sent in with initiators in an initiator group. Any subset (intersection) returns true.
     * 
     * @param storage storage
     * @param igInstance initiator group
     * @param initiatorsInDb initiators to compare against
     * @return initiators
     */
    private boolean igContainsInitiators(StorageSystem storage, CIMInstance igInstance, List<String> initiatorsInDb) {
        CIMObjectPath igPath = igInstance.getObjectPath();
        CloseableIterator<CIMInstance> initiatorsForIg = null;
        try {
            initiatorsForIg = _helper.getAssociatorInstances(storage, igPath, null,
                    SmisConstants.CP_SE_STORAGE_HARDWARE_ID, null, null,
                    SmisConstants.PS_STORAGE_ID);
            if (initiatorsForIg != null) {
                while (initiatorsForIg.hasNext()) {
                    CIMInstance initiatorInstance = initiatorsForIg.next();
                    String initiatorPort =
                            CIMPropertyFactory.getPropertyValue(initiatorInstance,
                                    SmisConstants.CP_STORAGE_ID);
                    _log.info(String.format("mapInitiatorsToInitiatorGroups - igPath = %s has initiator %s",
                            igPath, initiatorPort));
                    if (initiatorsInDb.contains(initiatorPort)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            // TODO: fill this in with task information
        } finally {
            if (initiatorsForIg != null) {
                initiatorsForIg.close();
            }
        }
        return false;
    }

    /**
     * This method will navigate the provider and fill in some containers that will be
     * used for validation. It returns a mapping of the Initiator.id to the
     * SE_InitiatorMaskingGroup CIMObject that the Initiator.port is in. It also
     * returns a list of SE_InitiatorMaskingGroup CIMObjects to as list of Initiator
     * .port Strings.
     * 
     * 
     * @param igToInitiators - For the list of Initiators passed in,
     *            a MultiSet of initiator groups to the list of initiators
     *            will be returned. Essentially, given a set of initiators,
     *            you will get the IG that it belongs to along with all of
     *            the IGs ports.
     * @param storage - StorageSystem object
     * @param initiatorList - List of Initiators to check
     * @return - Map of Initiator.id to SE_InitiatorMaskingGroup CIMObjects
     * @throws Exception
     */
    private Map<String, CIMObjectPath>
            mapInitiatorsToInitiatorGroups(ListMultimap<CIMObjectPath, String> igToInitiators,
                    StorageSystem storage, List<Initiator> initiatorList)
                    throws Exception {
        Map<String, CIMObjectPath> result = new HashMap<String, CIMObjectPath>();

        // Use the initiator port names to match up with initiators CIMObjectPaths on
        // the device. We're doing it this way because generating the CIMObjectPaths
        // based on piecing together the initiator name is treacherous.
        Map<CIMObjectPath, String> portNameToIgPath =
                new HashMap<CIMObjectPath, String>();
        Collection<String> portNames = Collections2.transform(initiatorList,
                CommonTransformerFunctions.fctnInitiatorToPortName());

        // Multiple arrays can be managed by a single SMI-S instance. The SE_StorageHardwareID is
        // global to the provider, so we need to get the SE_StorageHardware_ID object that are
        // associated with a specific array.
        CIMObjectPath hwManagementIDSvcPath = _cimPath.getStorageHardwareIDManagementService(storage);
        CloseableIterator<CIMInstance> initiatorInstances = _helper.getAssociatorInstances(storage, hwManagementIDSvcPath, null,
                SmisConstants.CP_SE_STORAGE_HARDWARE_ID, null, null, SmisConstants.PS_STORAGE_ID);

        List<CIMObjectPath> initiatorPaths = new ArrayList<CIMObjectPath>();
        while (initiatorInstances.hasNext()) {
            CIMInstance initiatorInstance = initiatorInstances.next();
            String storageId =
                    CIMPropertyFactory.getPropertyValue(initiatorInstance,
                            SmisConstants.CP_STORAGE_ID);
            if (portNames.contains(storageId)) {
                CIMObjectPath path = initiatorInstance.getObjectPath();
                initiatorPaths.add(path);
                portNameToIgPath.put(path, storageId);
            }
        }
        initiatorInstances.close();

        for (CIMObjectPath initiatorPath : initiatorPaths) {
            CloseableIterator<CIMObjectPath> igPaths =
                    _helper.getAssociatorNames(storage, initiatorPath, null,
                            SmisConstants.SE_INITIATOR_MASKING_GROUP, null, null);
            while (igPaths != null && igPaths.hasNext()) {
                CIMObjectPath igPath = igPaths.next();

                if (!igPath.toString().contains(storage.getSerialNumber())) {
                    // If this igPath is for another array that's being managed by the
                    // same provider, then skip it
                    _log.info("Skipping {} since it is for a different array", igPath);
                    continue;
                }

                if (!igToInitiators.containsKey(igPath)) {
                    _log.info(String.format("mapInitiatorsToInitiatorGroups - initiatorPath = %s has igPath = %s",
                            initiatorPath, igPath));
                    CloseableIterator<CIMInstance> initiatorsForIg =
                            _helper.getAssociatorInstances(storage, igPath, null,
                                    SmisConstants.CP_SE_STORAGE_HARDWARE_ID, null, null,
                                    SmisConstants.PS_STORAGE_ID);
                    if (initiatorsForIg != null) {
                        while (initiatorsForIg.hasNext()) {
                            CIMInstance initiatorInstance = initiatorsForIg.next();
                            String initiatorPort =
                                    CIMPropertyFactory.getPropertyValue(initiatorInstance,
                                            SmisConstants.CP_STORAGE_ID);
                            _log.info(String.format("mapInitiatorsToInitiatorGroups - igPath = %s has initiator %s",
                                    igPath, initiatorPort));
                            igToInitiators.put(igPath, initiatorPort);
                        }
                        initiatorsForIg.close();
                    }
                }
                String portName = portNameToIgPath.get(initiatorPath);
                result.put(portName, igPath);
                igPaths.close();
            }
        }
        return result;
    }

    private VolumeURIHLU[]
            getVolumesThatAreNotAlreadyInSG(StorageSystem storage, CIMObjectPath groupPath,
                    String groupName, VolumeURIHLU[] volumeURIHLUs)
                    throws WBEMException {
        VolumeURIHLU[] result = null;
        // Build a mapping of BlockObject.deviceID to VolumeURIHLU object
        HashMap<String, VolumeURIHLU> deviceIdMap =
                new HashMap<String, VolumeURIHLU>();
        for (VolumeURIHLU vuh : volumeURIHLUs) {
            BlockObject bo = BlockObject.fetch(_dbClient,
                    vuh.getVolumeURI());
            deviceIdMap.put(bo.getNativeId(), vuh);
        }

        // Query the provider for the CIM_Volume instances associated with the
        // StorageGroup. Remove any deviceIdMap entry that shows up
        CloseableIterator<CIMInstance> volumeInstances =
                _helper.getAssociatorInstances(storage, groupPath,
                        null, SmisConstants.CIM_STORAGE_VOLUME, null, null,
                        SmisConstants.PS_DEVICE_ID);
        while (volumeInstances.hasNext()) {
            CIMInstance volInstance = volumeInstances.next();
            String deviceId =
                    CIMPropertyFactory.getPropertyValue(volInstance,
                            SmisConstants.CP_DEVICE_ID);
            deviceIdMap.remove(deviceId);
        }

        // At this point, we've done the processing and we should check if there are
        // any entries in the deviceIdMap. If there are, then these are the non-existent
        // devices that need to be added to the StorageGroup.
        if (!deviceIdMap.isEmpty()) {
            result = new VolumeURIHLU[deviceIdMap.size()];
            int index = 0;
            for (Map.Entry<String, VolumeURIHLU> entry : deviceIdMap.entrySet()) {
                result[index++] = entry.getValue();
            }
            _log.info(String.format("Requested %d volumes to be added to %s. After " +
                    "processing %d volumes need to be added to the StorageGroup",
                    volumeURIHLUs.length, groupName, result.length));
        } else {
            _log.info("StorageGroup {} has the requested volumes " +
                    "to add in it already.", groupName);
        }
        return result;
    }

    /**
     * Given the list of parameters determine if the search criteria for the matching
     * mask has been met. This is called in the context of the function that already
     * has found that there are initiators that exist in 'exportMask'. If
     * mustHaveAllPorts=false, then we return true. If mustHaveAllPorts=true,
     * then we will attempt find out if all the initiators are in the exportMask and
     * only return true if so.
     * 
     * @param exportMask [in] - ExportMask object.
     * @param initiatorNames [in] - Initiator name list (WWN/iSCSI name)
     * @param mustHaveAllPorts [in] - All 'initiatorNames' should be in exportMask or not
     * @return The ExportMask is a match
     */
    private boolean matchesSearchCriteria(ExportMask exportMask,
            List<String> initiatorNames,
            boolean mustHaveAllPorts) {
        if (!mustHaveAllPorts) {
            return true;
        }

        Set<String> exportMaskInitiators = new HashSet<String>();
        if (exportMask.getExistingInitiators() != null) {
            exportMaskInitiators.addAll(exportMask.getExistingInitiators());
        }
        if (exportMask.getUserAddedInitiators() != null) {
            exportMaskInitiators.addAll(exportMask.getUserAddedInitiators().keySet());
        }
        if (exportMaskInitiators.size() == initiatorNames.size()) {
            exportMaskInitiators.removeAll(initiatorNames);
            if (exportMaskInitiators.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * This function is to retrieve the initiators of the given host id (uri)
     * 
     * @param hostId - host uri
     * @return a list of initiator port transform name
     */
    private List<String> queryHostInitiators(URI hostId) {
        List<String> initiatorNames = new ArrayList<String>();
        List<URI> uris = _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getContainedObjectsConstraint(hostId, Initiator.class, "host"));
        if (uris != null && !uris.isEmpty()) {
            List<Initiator> initiators = _dbClient.queryObjectField(Initiator.class, "iniport", uris);
            Collection<String> intiatorHostPortNames = Collections2.transform(initiators,
                    CommonTransformerFunctions.fctnInitiatorToPortName());
            if (intiatorHostPortNames != null && !intiatorHostPortNames.isEmpty()) {
                initiatorNames.addAll(intiatorHostPortNames);
            }
        }
        return initiatorNames;
    }

    /**
     * This function is to retrieve the initiators of the given host id (uri)
     * 
     * @param hostId - host uri
     * @return a list of initiator port transform name
     */
    private List<String> queryClusterInitiators(URI hostId) {
        List<String> initiatorNames = new ArrayList<String>();
        Host host = _dbClient.queryObject(Host.class, hostId);
        if (host == null) {
            return initiatorNames;
        }

        if (host.getCluster() == null) {
            return initiatorNames;
        }

        List<URI> hostUris = ComputeSystemHelper.getChildrenUris(_dbClient, host.getCluster(), Host.class, "cluster");
        for (URI hosturi : hostUris) {
            initiatorNames.addAll(queryHostInitiators(hosturi));
        }

        return initiatorNames;
    }

    /**
     * Updates fast policy and host io limits for the SGs in the given Export Mask.
     * 
     * @param storage the storage
     * @param exportMask the export mask
     * @param volumeURIs the volume uris
     * @param newVirtualPool the new virtual pool where policy name and host io limits can be obtained.
     * @param rollback boolean to know if it is called as a roll back step from workflow.
     * @param taskCompleter
     * @throws Exception the exception
     */
    @Override
    public void updateStorageGroupPolicyAndLimits(StorageSystem storage, ExportMask exportMask,
            List<URI> volumeURIs, VirtualPool newVirtualPool, boolean rollback,
            TaskCompleter taskCompleter) throws Exception {
        // Note: These volumes in DB have new Policy and vPool updated.

        String message = rollback ? ("updateStorageGroupPolicyAndLimits" + "(rollback)") : ("updateStorageGroupPolicyAndLimits");
        _log.info("{} {} START...", storage.getSerialNumber(), message);
        if (exportMask != null) {
            // For VMAX3 exportmask could be null.
            _log.info("{} : ExportMask: {}", message, exportMask.getId());
        }
        _log.info("{} : volumeURIs: {}", message, volumeURIs);
        _log.info("{} : new vPool: {}", message, newVirtualPool.getId());

        boolean isVmax3 = storage.checkIfVmax3();
        boolean isSGUpdated = false;
        boolean isSGUpdatedForVolumes = false;
        boolean exceptionOccurred = false;
        try {
            if (null == exportMask && isVmax3) {
                updateVMAX3AutoTieringPolicy(storage, volumeURIs, newVirtualPool,
                        rollback, taskCompleter);
                isSGUpdatedForVolumes = true;
            } else {
                /** Get the SG for the given masking view from array */
                String storageGroupName = _helper.getStorageGroupForGivenMaskingView(
                        exportMask.getMaskName(), storage);

                /**
                 * if storage group is ViPR managed:
                 * group volumes by child storage groups,
                 * for each child SG:
                 * If it is non-Cascaded non-FAST SG and phantom SG available for some volumes
                 * separate flow (see below method for details)
                 * else regular SG flow - check if it has the same set of volumes as the volume list provided:
                 * (yes) dis-associate existing policy from this SG if any
                 * and associate the new policy if provided.
                 * (no) throw error to error that FAST policy change cannot
                 * be done for this case (partial list)
                 * 
                 * Note : Do not move volumes from one SG to another as
                 * it is a disruptive process (host cannot access it)
                 * (Exception: phantom SG case, and VMAX3 where array & provider (8.0.3) supports it)
                 */
                Volume volume = _dbClient.queryObject(Volume.class, volumeURIs.get(0));
                if (exportMask.getCreatedBySystem() || Volume.checkForVplexBackEndVolume(_dbClient, volume)) {
                    // ExportMask is not system created for VPLEX backend volumes
                    Map<String, List<URI>> volumesByStorageGroup = _helper
                            .groupVolumesBasedOnExistingGroups(storage,
                                    storageGroupName, volumeURIs);
                    _log.info("Group Volumes by Storage Group size : {}",
                            volumesByStorageGroup.size());

                    _log.info("Checking if there are phantom Storage groups for volumes in MV {}", exportMask.getMaskName());
                    Set<String> phantomSGNames = null;
                    if (!isVmax3) {
                        phantomSGNames = _helper.getPhantomStorageGroupsForGivenMaskingView(exportMask.getMaskName(),
                                storage);
                    }
                    _log.info("Phantom Storage groups {} found for volumes in MV {}", phantomSGNames, exportMask.getMaskName());
                    for (Entry<String, List<URI>> entry : volumesByStorageGroup.entrySet()) {
                        String childGroupName = entry.getKey();
                        List<URI> volumeURIList = entry.getValue();
                        isSGUpdated = validateAndUpdateStorageGroupPolicyAndLimits(
                                storage, exportMask, childGroupName, volumeURIList,
                                newVirtualPool, phantomSGNames, taskCompleter);
                    }

                } else {
                    /**
                     * not ViPR managed
                     * we cannot update policy associated with it
                     * since we do not manage other volumes.
                     */
                    _log.info(
                            "SG '{}' is not a ViPR managed one. FAST Policy cannot be updated on it.",
                            storageGroupName);
                    isSGUpdated = false;
                }
            }
        } catch (Exception e) {
            exceptionOccurred = true;
            String errMsg = null;
            if (exportMask != null) {
                errMsg = String
                        .format("An error occurred while updating FAST policy for Storage Groups on ExportMask %s",
                                exportMask.getMaskName());
            } else {
                errMsg = "An error occurred while updating fast policy for the VMAX3 volumes. ";
            }
            _log.error(errMsg, e);
            ServiceError serviceError = DeviceControllerException.errors
                    .jobFailedMsg(errMsg, e);
            taskCompleter.error(_dbClient, serviceError);
        } finally {
            if (!exceptionOccurred) {
                if (null != exportMask && !isSGUpdated && !rollback) {
                    // none of the SG's updated
                    // throw error to user to indicate it
                    String errMsg = String
                            .format("None of the Storage Groups on ExportMask %s is updated with new FAST policy or Host IO Limits." +
                                    " Because the given Volume list is not same as the one in Storage Group (or)" +
                                    " any of the criteria for 'moveMembers' didn't meet in case of VMAX3 volumes." +
                                    " Please check log for more details.",
                                    exportMask.getMaskName());
                    _log.error(errMsg);
                    ServiceError serviceError = DeviceControllerException.errors
                            .jobFailedOpMsg("updateStorageGroupPolicyAndLimits", errMsg);
                    taskCompleter.error(_dbClient, serviceError);
                } else if (isVmax3 && null == exportMask && !isSGUpdatedForVolumes && !rollback) {
                    // none of the SG's updated
                    // throw error to user to indicate it
                    String errMsg = "None of the parking storage groups for volumes were updated with new FAST Policy.";
                    _log.error(errMsg);
                    ServiceError serviceError = DeviceControllerException.errors
                            .jobFailedOpMsg("updateFastPolicy", errMsg);
                    taskCompleter.error(_dbClient, serviceError);
                } else {
                    taskCompleter.ready(_dbClient);
                }
            }
        }
        _log.info("{} {} END...", storage.getSerialNumber(), message);
    }

    /**
     * This method is used to change parking storage group for the
     * VMAX3 non exported volumes based on newPolicyName.
     */
    private void updateVMAX3AutoTieringPolicy(StorageSystem storage,
            List<URI> volumeURIs, VirtualPool newVirtualPool, boolean rollback,
            TaskCompleter taskCompleter) throws Exception {

        _log.info("Changing parking storage group for the VMAX3 non-exported volumes based on newPolicyName.");
        String newPolicyName = ControllerUtils.getFastPolicyNameFromVirtualPool(_dbClient, storage, newVirtualPool);
        if (null == newPolicyName) {
            newPolicyName = Constants.NONE;
        }
        // Remove volumes from the old parking storage group
        Set<String> volumeDeviceIds = new HashSet<String>();
        String fastSetting = null;
        for (URI volURI : volumeURIs) {
            Volume volume = _dbClient.queryObject(Volume.class, volURI);
            volumeDeviceIds.add(volume.getNativeId());
            if (null == fastSetting) {
                fastSetting = _helper.getVMAX3FastSettingForVolume(volURI, newPolicyName);
            }
            // Flag to indicate whether or not we need to use the EMCForce flag on this operation.
            // We currently use this flag when dealing with RP Volumes as they are tagged for RP and the
            // operation on these volumes would fail otherwise.
            boolean forceFlag = ExportUtils.useEMCForceFlag(_dbClient, volURI);
            _helper.removeVolumeFromParkingSLOStorageGroup(storage, volume.getNativeId(), forceFlag);
        }

        // Add volumes to the new parking storage group.
        addVolumesToParkingStorageGroup(storage, fastSetting, volumeDeviceIds);

    }

    /**
     * Validates and updates fast policy in storage group.
     * 
     * @param storage the storage system
     * @param exportMask exportMask
     * @param childGroupName the child group name
     * @param volumeURIs the volume uris
     * @param newVirtualPool the new virtual pool where new policy name and host limits can be obtained
     * @param phantomSGNames the phantom SG names if any
     * @param taskCompleter task completer
     * @return true, if successfully updated policy for SG
     * @throws WBEMException the wBEM exception
     * @throws Exception the exception
     */
    private boolean validateAndUpdateStorageGroupPolicyAndLimits(
            StorageSystem storage, ExportMask exportMask, String childGroupName, List<URI> volumeURIs,
            VirtualPool newVirtualPool, Set<String> phantomSGNames, TaskCompleter taskCompleter) throws WBEMException, Exception {
        boolean policyUpdated = false;
        boolean isVmax3 = storage.checkIfVmax3();
        _log.info("Checking on Storage Group {}", childGroupName);
        WBEMClient client = _helper.getConnection(storage).getCimClient();

        // we need auto tiering policy object to get its name.
        String newPolicyName = ControllerUtils.getFastPolicyNameFromVirtualPool(_dbClient, storage, newVirtualPool);
        if (isVmax3) {
            newPolicyName = _helper.getVMAX3FastSettingForVolume(volumeURIs.get(0), newPolicyName);
        }
        StorageGroupPolicyLimitsParam newVirtualPoolPolicyLimits =
                new StorageGroupPolicyLimitsParam(newPolicyName,
                        newVirtualPool.getHostIOLimitBandwidth(),
                        newVirtualPool.getHostIOLimitIOPs(), storage);

        CIMObjectPath childGroupPath = _cimPath.getMaskingGroupPath(storage,
                childGroupName,
                SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup);

        if (!isVmax3 && !phantomSGNames.isEmpty()
                && !_helper.isFastPolicy(_helper.getAutoTieringPolicyNameAssociatedWithVolumeGroup(storage, childGroupPath))
                && !_helper.isCascadedSG(storage, childGroupPath)) {
            /** Phantom SG will be taken into consideration only if MV contains Non-cascaded Non-FAST SG */

            _log.info("**** Phantom Storage Group ****");
            /**
             * For Volumes in Phantom SG - Volumes part of Phantom SG will be in Non-cascaded Non-FAST Storage Group (CTRL-9064)
             * 
             * We have the phantom SGs having volumes which are part of this Masking view
             * Group requested volumes by SG
             * volumes in each Phantom SG
             * also add an entry with volumes to Non-FAST SG (volumes requested minus volumes already in phantom SG)
             * 
             * For each SG,
             * If Phantom SG:
             * If it is requested for all volumes
             * change the policy associated with phantom SG
             * else
             * Remove the volumes from that phantom SG
             * add them to new/existing phantom SG which is associated with new policy
             * Note: if new policy is NONE, we just remove the volumes from phantom SG, no need to add them to another phantom SG.
             * Since these volumes are already part of Non-FAST SG, they are part of MV.
             * Else if it is Non-FAST SG:
             * place those volumes in new/existing Phantom SG which is associated with new Policy
             */

            Map<String, List<URI>> volumeGroup = new HashMap<String, List<URI>>();
            List<URI> volumeURIsOfNonFASTSG = new ArrayList<URI>();
            volumeURIsOfNonFASTSG.addAll(volumeURIs);
            for (String phantomSGName : phantomSGNames) {
                List<URI> volURIs = _helper.findVolumesInStorageGroup(storage, phantomSGName, volumeURIs);
                if (!volURIs.isEmpty()) {
                    volumeGroup.put(phantomSGName, volURIs);
                    volumeURIsOfNonFASTSG.removeAll(volURIs);
                }
            }
            // put Non-FAST SG with volumes (volumes requested minus volumes already in phantom SG)
            if (!volumeURIsOfNonFASTSG.isEmpty()) {
                volumeGroup.put(childGroupName, volumeURIsOfNonFASTSG);
            }

            for (Entry<String, List<URI>> sgNameToVolumes : volumeGroup.entrySet()) {
                String sgName = sgNameToVolumes.getKey();
                List<URI> volumesInSG = sgNameToVolumes.getValue();
                CIMObjectPath sgPath = _cimPath.getMaskingGroupPath(storage, sgName,
                        SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup);

                // Flag to indicate whether or not we need to use the EMCForce flag on this operation.
                // We currently use this flag when dealing with RP Volumes as they are tagged for RP and the
                // operation on these volumes would fail otherwise.
                boolean forceFlag = false;
                for (URI volURI : volumesInSG) {
                    // The force flag only needs to be set once
                    if (!forceFlag) {
                        forceFlag = ExportUtils.useEMCForceFlag(_dbClient, volURI);
                    }
                }

                /** update Policy on this SG. We don't use Phantom SG for IO Limits */
                String currentPolicyName = _helper.getAutoTieringPolicyNameAssociatedWithVolumeGroup(storage, sgPath);
                _log.info("FAST policy name associated with Storage Group {} : {}", sgName, currentPolicyName);

                if (!_helper.isFastPolicy(currentPolicyName)) {
                    /**
                     * Not a Phantom SG. Create new or use existing Phantom SG for the requested volumes
                     * Request: non-FAST volumes in SG (associated with MV) to FAST volumes
                     */
                    addVolumesToPhantomStorageGroup(storage, exportMask, volumesInSG, newPolicyName, childGroupName, taskCompleter,
                            forceFlag);
                } else {
                    /** phantom SG */
                    _log.info("Checking on Phantom Storage Group {}", sgName);
                    // check if phantom SG has the same set of volumes as the volume list provided
                    if (isGivenVolumeListSameAsInStorageGroup(storage, sgPath, volumesInSG)) {
                        _log.info(
                                "Changing Policy on Phantom Storage Group {} since it is requested "
                                        + "for all the volumes in the Group.",
                                sgName);
                        if (!currentPolicyName.equalsIgnoreCase(newPolicyName)) {
                            if (_helper.isFastPolicy(currentPolicyName)) {
                                _helper.removeVolumeGroupFromAutoTieringPolicy(storage, sgPath);
                            }

                            if (_helper.isFastPolicy(newPolicyName)) {
                                _log.info("Adding Storage Group {} to FAST Policy {}",
                                        sgName, newPolicyName);
                                addVolumeGroupToAutoTieringPolicy(storage, newPolicyName, sgPath, taskCompleter);

                                StorageGroupPolicyLimitsParam phantomStorageGroupPolicyLimitsParam = new StorageGroupPolicyLimitsParam(
                                        newPolicyName);
                                String newSGName = generateNewNameForPhantomSG(
                                        storage, childGroupName, phantomStorageGroupPolicyLimitsParam);
                                // update SG name according to new policy
                                _helper.updateStorageGroupName(client, sgPath, newSGName);
                            }
                        } else {
                            _log.info("Current and new policy names are same '{}'." +
                                    " No need to update it on SG.", currentPolicyName);
                        }

                        /**
                         * if new policy is NONE & all volumes in request
                         * remove phantom SG since it won't be needed anymore
                         * (because volumes are already part of Non-FAST SG associated with MV)
                         * Request: FAST volumes to Non-FAST
                         */
                        if (!_helper.isFastPolicy(newPolicyName)) {
                            removePhantomStorageGroup(storage, client, exportMask.getId(), sgName, sgPath, volumesInSG, forceFlag);
                        }
                    } else {
                        /**
                         * remove requested volumes from this phantom SG
                         * add them to the new/existing phantom SG which is associated with target policy
                         */
                        _log.info("Request is made for part of volumes in the Group");
                        removePhantomStorageGroup(storage, client, exportMask.getId(), sgName, sgPath, volumesInSG, forceFlag);

                        if (_helper.isFastPolicy(newPolicyName)) {
                            addVolumesToPhantomStorageGroup(storage, exportMask, volumesInSG, newPolicyName, childGroupName, taskCompleter,
                                    forceFlag);
                        }
                    }
                }
                policyUpdated = true;
            }
        } else {
            /**
             * Usual flow for regular SGs
             * 
             * check if SG has the same set of volumes as the volume list provided.
             */
            if (isGivenVolumeListSameAsInStorageGroup(storage, childGroupPath, volumeURIs)) {
                /** update Policy and Limits on this SG */
                _log.info("Request is made for all volumes in the Group. Updating Policy and Limits on this Storage Group..");
                CIMInstance childGroupInstance = null;
                if (isVmax3) {
                    childGroupInstance = _helper.getInstance(storage, childGroupPath, false,
                            false, SmisConstants.PS_V3_STORAGE_GROUP_PROPERTIES);
                } else {
                    childGroupInstance = _helper.checkExists(storage, childGroupPath, false, false);
                }
                StorageGroupPolicyLimitsParam currentStorageGroupPolicyLimits = _helper.createStorageGroupPolicyLimitsParam(storage,
                        childGroupInstance);

                String currentPolicyName = currentStorageGroupPolicyLimits.getAutoTierPolicyName();
                if (!currentPolicyName.equalsIgnoreCase(newPolicyName)) {

                    _log.info("FAST policy name associated with Storage Group {} : {}", childGroupName, currentPolicyName);
                    if (isVmax3) {
                        CIMInstance toUpdate = new CIMInstance(childGroupInstance.getObjectPath(),
                                _helper.getV3FastSettingProperties(newPolicyName));
                        _helper.modifyInstance(storage, toUpdate, SmisConstants.PS_V3_FAST_SETTING_PROPERTIES);
                        _log.info("Modified Storage Group {} FAST Setting to {}",
                                childGroupName, newPolicyName);
                    } else {
                        if (_helper.isFastPolicy(currentPolicyName)) {
                            _helper.removeVolumeGroupFromAutoTieringPolicy(storage, childGroupPath);
                        }

                        if (_helper.isFastPolicy(newPolicyName)) {
                            _log.info("Adding Storage Group {} to FAST Policy {}",
                                    childGroupName, newPolicyName);
                            addVolumeGroupToAutoTieringPolicy(storage, newPolicyName, childGroupPath, taskCompleter);
                        }
                    }

                } else {
                    _log.info("Current and new policy names are same '{}'." +
                            " No need to update it on Storage Group.", currentPolicyName);
                }
                // Even if we don't change policy name on device
                // we need to set policyUpdated = true else rollback kicks in
                policyUpdated = true;

                // update host io limits if need be
                if (!HostIOLimitsParam.isEqualsLimit(currentStorageGroupPolicyLimits.getHostIOLimitBandwidth(),
                        newVirtualPoolPolicyLimits.getHostIOLimitBandwidth())) {
                    _helper.updateHostIOLimitBandwidth(client, childGroupPath, newVirtualPoolPolicyLimits.getHostIOLimitBandwidth());
                    policyUpdated = true;
                }
                if (!HostIOLimitsParam.isEqualsLimit(currentStorageGroupPolicyLimits.getHostIOLimitIOPs(),
                        newVirtualPoolPolicyLimits.getHostIOLimitIOPs())) {
                    _helper.updateHostIOLimitIOPs(client, childGroupPath, newVirtualPoolPolicyLimits.getHostIOLimitIOPs());
                    policyUpdated = true;
                }

                if (policyUpdated) {
                    Set<Initiator> initiators = ExportMaskUtils.getInitiatorsForExportMask(_dbClient, exportMask, null);
                    _helper.updateStorageGroupName(client, childGroupPath,
                            generateStorageGroupName(storage, exportMask, initiators, newVirtualPoolPolicyLimits));
                }
            } else if (isVmax3) {
                /**
                 * Requested for fewer members in a SG
                 * 
                 * V3 supports moveMembers from one SG to another, provided some conditions met.
                 * see #helper.moveVolumesFromOneStorageGroupToAnother() for criteria.
                 * 
                 * validate that current SG is not a parent SG and it is not associated with MV
                 * check if there is another SG under CSG with new fastSetting & limits
                 * else create new SG (with new fastSetting, IO limits) and associate it with CSG
                 * call 'moveMembers' to move volumes from current SG to new SG
                 */
                _log.info("Request is made for part of volumes in the Group (VMAX3). Moving those volumes to new Storage Group..");
                if (!_helper.isCascadedSG(storage, childGroupPath) &&
                        !_helper.findStorageGroupsAssociatedWithOtherMaskingViews(storage, childGroupName)) {
                    String parentGroupName = _helper.getStorageGroupForGivenMaskingView(
                            exportMask.getMaskName(), storage);
                    Map<StorageGroupPolicyLimitsParam, List<String>> childGroupsByFast = _helper
                            .groupStorageGroupsByAssociation(storage, parentGroupName);

                    List<String> newChildGroups = childGroupsByFast.get(newVirtualPoolPolicyLimits);
                    CIMObjectPath newChildGroupPath = null;
                    boolean newGroup = false;
                    if (newChildGroups != null && !newChildGroups.isEmpty()) {
                        String newChildGroupName = newChildGroups.iterator().next();
                        newChildGroupPath = _cimPath.getMaskingGroupPath(storage, newChildGroupName,
                                SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup);
                    } else {
                        newGroup = true;
                        String[] tokens = newPolicyName.split(Constants.SMIS_PLUS_REGEX);
                        newChildGroupPath = _helper.createVolumeGroupBasedOnSLO(storage, tokens[0], tokens[1], tokens[2]);

                        // Flag to indicate whether or not we need to use the EMCForce flag on this operation.
                        // We currently use this flag when dealing with RP Volumes as they are tagged for RP and the
                        // operation on these volumes would fail otherwise.
                        boolean forceFlag = false;
                        for (URI volURI : volumeURIs) {
                            if (!forceFlag) {
                                forceFlag = ExportUtils.useEMCForceFlag(_dbClient, volURI);
                            }
                        }
                        addGroupsToCascadedVolumeGroup(storage, parentGroupName, newChildGroupPath, null, null, forceFlag);
                    }

                    SmisJob moveVolumesToSGJob = new SmisSynchSubTaskJob(null, storage.getId(),
                            SmisConstants.MOVE_MEMBERS);
                    _helper.moveVolumesFromOneStorageGroupToAnother(storage,
                            childGroupPath, newChildGroupPath, volumeURIs,
                            moveVolumesToSGJob);

                    if (newGroup) {
                        // update host IO limits if need be
                        if (newVirtualPoolPolicyLimits.isHostIOLimitBandwidthSet()) {
                            _helper.updateHostIOLimitBandwidth(client, newChildGroupPath,
                                    newVirtualPoolPolicyLimits.getHostIOLimitBandwidth());
                        }

                        if (newVirtualPoolPolicyLimits.isHostIOLimitIOPsSet()) {
                            _helper.updateHostIOLimitIOPs(client, newChildGroupPath, newVirtualPoolPolicyLimits.getHostIOLimitIOPs());
                        }

                        Set<Initiator> initiators = ExportMaskUtils.getInitiatorsForExportMask(_dbClient, exportMask, null);
                        _helper.updateStorageGroupName(client, newChildGroupPath,
                                generateStorageGroupName(storage, exportMask, initiators, newVirtualPoolPolicyLimits));
                    }
                    policyUpdated = true;
                } else {
                    _log.info(
                            "Conditions for 'moveMembers' didn't meet for Storage Group {}."
                                    + " Hence, cannot move volumes to new Storage Group with new policy and limits.",
                            childGroupName);
                }
            } else {
                _log.info(
                        "Given Volume list is not same as the one in Storage Group {}."
                                + " Hence, FAST policy change won't be done on it.",
                        childGroupName);
            }
        }
        return policyUpdated;
    }

    /**
     * Checks if is given volume list same as in storage group.
     */
    private boolean isGivenVolumeListSameAsInStorageGroup(StorageSystem storage,
            CIMObjectPath groupPath, List<URI> volumeURIs) throws WBEMException {
        Set<String> returnedNativeGuids = new HashSet<String>();
        Set<String> givenNativeGuids = new HashSet<String>();
        CloseableIterator<CIMObjectPath> volumePathItr = null;
        try {
            List<Volume> volumes = _dbClient.queryObject(Volume.class, volumeURIs);

            volumePathItr = _helper.getAssociatorNames(storage, groupPath, null,
                    SmisConstants.CIM_STORAGE_VOLUME, null, null);
            while (volumePathItr.hasNext()) {
                returnedNativeGuids.add(_helper.getVolumeNativeGuid(volumePathItr
                        .next()));
            }
            for (Volume volume : volumes) {
                givenNativeGuids.add(volume.getNativeGuid());
            }

            _log.info("NativeGuids of Volumes from SG: {}",
                    returnedNativeGuids);
            _log.info("NativeGuids of Requested Volumes in this SG: {}",
                    givenNativeGuids);
            Set<String> diff = Sets.difference(returnedNativeGuids,
                    givenNativeGuids);
            return (diff.isEmpty());
        } finally {
            volumePathItr.close();
        }
    }

    /**
     * Adds the volumes to new or existing phantom storage group which is associated with given policy.
     * This is used internally for operation - change volume's policy by moving them from one vPool to another.
     */
    private void addVolumesToPhantomStorageGroup(StorageSystem storage,
            ExportMask exportMask, List<URI> volumesInSG, String newPolicyName,
            String childGroupName, TaskCompleter taskCompleter, boolean forceFlag) throws Exception {

        // Check to see if there already is a phantom storage group with this policy on the array
        StorageGroupPolicyLimitsParam phantomStorageGroupPolicyLimitsParam = new StorageGroupPolicyLimitsParam(newPolicyName);
        List<String> phantomStorageGroupNames = _helper
                .findPhantomStorageGroupAssociatedWithFastPolicy(storage, phantomStorageGroupPolicyLimitsParam);
        VolumeURIHLU[] volumeURIHlus = constructVolumeURIHLUFromURIList(volumesInSG, newPolicyName);

        // If there's no existing phantom storage group, create one.
        if (phantomStorageGroupNames == null || phantomStorageGroupNames.isEmpty()) {
            String phantomStorageGroupName = generateNewNameForPhantomSG(
                    storage, childGroupName, phantomStorageGroupPolicyLimitsParam);
            // We need to create a new phantom storage group with our policy associated with it.
            _log.info("phantom storage group {} doesn't exist, creating a new group", phantomStorageGroupName);
            CIMObjectPath phantomStorageGroupCreated = createVolumeGroup(storage,
                    phantomStorageGroupName, volumeURIHlus, taskCompleter, true);
            _log.info("Adding Storage Group {} to Fast Policy {}", phantomStorageGroupName, newPolicyName);
            addVolumeGroupToAutoTieringPolicy(storage, newPolicyName,
                    phantomStorageGroupCreated, taskCompleter);
        } else {
            // take the first matching phantom SG from the list
            String phantomStorageGroupName = phantomStorageGroupNames.get(0);
            // We found a phantom storage group with our policy, use it.
            _log.info("Found that we need to add volumes to the phantom storage group: {}", phantomStorageGroupName);

            // Create a relatively empty completer associated with the export mask. We don't have the export group
            // at this level, so there's nothing decent to attach the completer to anyway.
            String task = UUID.randomUUID().toString();
            ExportMaskVolumeToStorageGroupCompleter completer =
                    new ExportMaskVolumeToStorageGroupCompleter(null, exportMask.getId(), task);
            SmisMaskingViewAddVolumeJob job = new SmisMaskingViewAddVolumeJob(
                    null, storage.getId(), exportMask.getId(), volumeURIHlus, null, completer);
            job.setCIMObjectPathfactory(_cimPath);
            _helper.addVolumesToStorageGroup(volumeURIHlus, storage, phantomStorageGroupName, job, forceFlag);
            ExportOperationContext.insertContextOperation(taskCompleter, VmaxExportOperationContext.OPERATION_ADD_VOLUMES_TO_STORAGE_GROUP,
                    phantomStorageGroupName,
                    volumeURIHlus, forceFlag);
            _log.info("Adding Volumes to Phantom Storage Group {}", phantomStorageGroupName);
        }
    }

    /**
     * Removes the volumes from phantom SG.
     * This is used internally for operation - change volume's policy by moving them from one vPool to another.
     * Request: to change FAST volumes in phantom SG to non-FAST volumes
     */
    private void removePhantomStorageGroup(StorageSystem storage,
            WBEMClient client, URI exportMaskURI, String phantomSGName,
            CIMObjectPath phantomSGPath, List<URI> volumesToRemove, boolean forceFlag)
            throws Exception {
        /**
         * Though the volumes are associated with other non-fast, non-cascading masking views,
         * we can remove volumes from phantom SG as we are removing its Policy.
         */
        if (!volumesToRemove.isEmpty()) {
            _log.info(String.format("Going to remove volumes %s from phantom storage group %s",
                    Joiner.on("\t").join(volumesToRemove), phantomSGName));

            /** remove FAST policy associated with SG if change is requested for all volumes in that SG */
            if (isGivenVolumeListSameAsInStorageGroup(storage, phantomSGPath, volumesToRemove)) {
                _log.info("Storage Group has no more than {} volumes", volumesToRemove.size());
                _log.info("Storage Group {} will be disassociated from FAST because group can not be deleted if associated with FAST",
                        phantomSGName);
                _helper.removeVolumeGroupFromPolicyAndLimitsAssociation(client, storage, phantomSGPath);
            }

            // Create a relatively empty completer associated with the export mask. We don't have the export group
            // at this level, so there's nothing decent to attach the completer to anyway.
            String task = UUID.randomUUID().toString();
            ExportMaskVolumeToStorageGroupCompleter completer =
                    new ExportMaskVolumeToStorageGroupCompleter(null, exportMaskURI, task);

            List<CIMObjectPath> volumePaths = new ArrayList<CIMObjectPath>();
            // Remove the volumes from the phantom storage group
            CIMArgument[] inArgs = _helper.getRemoveVolumesFromMaskingGroupInputArguments(storage, phantomSGName,
                    volumesToRemove, forceFlag);
            CIMArgument[] outArgs = new CIMArgument[5];
            _helper.invokeMethodSynchronously(storage, _cimPath.getControllerConfigSvcPath(storage),
                    "RemoveMembers", inArgs, outArgs, new SmisMaskingViewRemoveVolumeJob(null,
                            storage.getId(), volumePaths, null, phantomSGName, _cimPath, completer));
        }
    }

    private VolumeURIHLU[] constructVolumeURIHLUFromURIList(
            List<URI> volumesInSG, String newPolicyName) {
        List<VolumeURIHLU> volumeUriHLUs = new ArrayList<VolumeURIHLU>();
        for (URI volURI : volumesInSG) {
            VolumeURIHLU volumeUriHLU = new VolumeURIHLU(volURI, null, newPolicyName, null);
            volumeUriHLUs.add(volumeUriHLU);
        }
        VolumeURIHLU[] volumeURIHLUArr = new VolumeURIHLU[volumeUriHLUs.size()];
        return volumeUriHLUs.toArray(volumeURIHLUArr);
    }

    private String generateNewNameForPhantomSG(StorageSystem storage, String childGroupName,
            StorageGroupPolicyLimitsParam phantomStorageGroupPolicyLimitsParam) {
        String phantomStorageGroupName = childGroupName + "_" + phantomStorageGroupPolicyLimitsParam;

        // get all storage group names in storage system to use for checking duplicate
        Map<StorageGroupPolicyLimitsParam, Set<String>> allStorageGroups = _helper.getExistingSGNamesFromArray(storage);
        Set<String> existingGroupNames = new HashSet<>();
        for (Set<String> groupNames : allStorageGroups.values()) {
            existingGroupNames.addAll(groupNames);
        }
        // if generated name is duplicated in storage, append number to the end of the name
        return _helper.generateGroupName(existingGroupNames, phantomStorageGroupName);
    }

    /**
     * Leverage custom name generator module, this method generates storage group name via the following configuration
     * {host_name.FIRST(20)}_{array_serial_number.LAST(3)}_SG_{auto_tiering_policy_name.FIRST(32)} + {_count(3)}
     * NOTE: the generated name should be no more than 64 characters
     * 
     * @param storage
     * @param mask
     * @param initiators
     * @param storageGroupPolicyLimitsParam
     * @return
     */
    private String generateStorageGroupName(StorageSystem storage, ExportMask mask, Collection<Initiator> initiators,
            StorageGroupPolicyLimitsParam storageGroupPolicyLimitsParam) {
        String storageGroupCustomTemplateName = CustomConfigConstants.VMAX_HOST_STORAGE_GROUP_MASK_NAME;

        String exportType = ExportMaskUtils.getExportType(_dbClient, mask);
        if (ExportGroupType.Cluster.name().equals(exportType)) {
            storageGroupCustomTemplateName = CustomConfigConstants.VMAX_CLUSTER_STORAGE_GROUP_MASK_NAME;
        }

        // grab custom name generator data source to generate storage group name
        DataSource sgDataSource = ExportMaskUtils.getExportDatasource(storage, new ArrayList<Initiator>(initiators), dataSourceFactory,
                storageGroupCustomTemplateName);
        String policyName = storageGroupPolicyLimitsParam.getAutoTierPolicyName();
        if (storage.checkIfVmax3()) {
            policyName = policyName.replaceAll(Constants.SMIS_PLUS_REGEX, Constants.UNDERSCORE_DELIMITER);
        }
        policyName = _helper.isFastPolicy(policyName) ? policyName : StorageGroupPolicyLimitsParam.NON_FAST_POLICY;

        String hostIOLimitBandwidth = "";
        String hostIOLimitIops = "";
        if (storageGroupPolicyLimitsParam.isHostIOLimitBandwidthSet()) {
            hostIOLimitBandwidth = StorageGroupPolicyLimitsParam.BANDWIDTH + storageGroupPolicyLimitsParam.getHostIOLimitBandwidth();
        }
        if (storageGroupPolicyLimitsParam.isHostIOLimitIOPsSet()) {
            hostIOLimitIops = StorageGroupPolicyLimitsParam.IOPS + storageGroupPolicyLimitsParam.getHostIOLimitIOPs();
        }

        sgDataSource.addProperty(CustomConfigConstants.AUTO_TIERING_POLICY_NAME, policyName);
        sgDataSource.addProperty(CustomConfigConstants.HOST_IO_LIMIT_BANDWIDTH, hostIOLimitBandwidth);
        sgDataSource.addProperty(CustomConfigConstants.HOST_IO_LIMIT_IOPS, hostIOLimitIops);
        String baseStorageGroupName = customConfigHandler.getComputedCustomConfigValue(storageGroupCustomTemplateName,
                storage.getSystemType(), sgDataSource);

        // get all storage group names in storage system to use for checking duplicate
        Map<StorageGroupPolicyLimitsParam, Set<String>> allStorageGroups = _helper.getExistingSGNamesFromArray(storage);
        Set<String> existingGroupNames = new HashSet<>();
        for (Set<String> groupNames : allStorageGroups.values()) {
            existingGroupNames.addAll(groupNames);
        }
        // if generated name is duplicated in storage, append number to the end of the name
        return _helper.generateGroupName(existingGroupNames, baseStorageGroupName);
    }

    /**
     * Determines whether or not the BO passed in is a RP Journal Volume.
     * 
     * @param bo The BO to check
     * @return True if it's an RP Journal, false otherwise.
     */
    private boolean isRPJournalVolume(BlockObject bo) {
        boolean isRPJournal = false;
        if (bo != null && (bo instanceof Volume)) {
            Volume volume = (Volume) bo;
            if (volume.checkForRp()
                    && NullColumnValueGetter.isNotNullValue(volume.getPersonality())
                    && volume.getPersonality().equals(PersonalityTypes.METADATA.toString())) {
                _log.info(String.format("Volume [%s] is a RP Journal.", volume.getLabel()));
                isRPJournal = true;
            }
        }
        return isRPJournal;
    }

    @Override
    public Map<URI, Integer> getExportMaskHLUs(StorageSystem storage, ExportMask exportMask) {
        return Collections.emptyMap();
    }

    /**
     * Given a list of Initiators, find if any are in an ExportMask's userAddedInitiators list- other than the 'exportMask' passed into
     * the routine. If such an ExportMask is found, then the initiator will be removed from 'newInitiators' and added to the result list.
     *
     * @param exportMask [IN] - ExportMask that should be excluded from matches. This is the ExportMask that we're checking against.
     * @param newInitiators [OUT] - List of Initiators that need to be added to 'exportMask'. We need to determine if they need to go into
     *            the existingInitiators list or the userAddedInitiators list.
     * @return List of Initiators that should added to exportMask's userAddedInitiator list. ExportMasks should be on the same array as
     *         'exportMask'.
     */
    private List<Initiator> findIfInitiatorsAreUserAddedInAnotherMask(ExportMask exportMask, List<Initiator> newInitiators) {
        List<Initiator> userAddedInitiators = new ArrayList<>();

        // Iterate through the set of ExportMasks that contain 'newInitiators' and find if it has the initiator in its userAddedInitiator
        // list. If it does, we add the initiator to the result list and remove it from 'newInitiator'.
        for (ExportMask matchedMask : ExportMaskUtils.getExportMasksWithInitiators(_dbClient, newInitiators).values()) {
            // Exclude 'exportMask' and ExportMasks on different arrays from the search
            if (matchedMask.getId().equals(exportMask.getId()) ||
                    !matchedMask.getStorageDevice().equals(exportMask.getStorageDevice())) {
                continue;
            }
            // Iterate through the set of initiators and find if any exist in the ExportMask's userAddedInitiator list
            Iterator<Initiator> iterator = newInitiators.iterator();
            while (iterator.hasNext()) {
                Initiator initiator = iterator.next();
                if (matchedMask.hasUserInitiator(initiator.getId())) {
                    // This initiator is user-added for this matchedMask
                    userAddedInitiators.add(initiator);
                    // Since this ExportMask has the initiator, we need to remove it from 'newInitiators'.
                    iterator.remove();
                }
            }
        }

        Collection portNames = Collections2.transform(userAddedInitiators, CommonTransformerFunctions.fctnInitiatorToPortName());
        _log.info(String.format("The following initiators were found in another ExportMask as user-added initiators: %s",
                CommonTransformerFunctions.collectionToString(portNames)));
        return userAddedInitiators;
    }

    /**
     * There could already be a MaskingView with ExportMask.maskName already existing on the array, so check for this condition.
     * If so, we will have to generate a new name. This name will be returned by the routine and also saved to the ExportMask.
     *
     * @param storage [IN] - Storage array to check
     * @param exportMask [IN] - ExportMask that has the name to verify
     * @return String MaskingView name that does not already exist on the array
     */
    private String generateMaskViewName(StorageSystem storage, ExportMask exportMask) {
        String maskingViewName = exportMask.getMaskName();
        CIMInstance maskingViewInstance = maskingViewExists(storage, maskingViewName);

        // if name already existed, generate unique name by appended index
        if (maskingViewInstance != null) {
            _log.info(String.format("MaskingView '%s' already exists on %s. Going to generate a new name ...", maskingViewName,
                    storage.getNativeGuid()));
            int maskingViewNameIndex = 0;
            String name = maskingViewName;
            while (maskingViewInstance != null) {
                // generate new name if one already existed
                maskingViewName = String.format("%s_%d", name, ++maskingViewNameIndex);
                _log.info(String.format("Checking if '%s' already exists on %s", maskingViewName, storage.getNativeGuid()));
                _log.info("Trying new MaskingView name: {} ", maskingViewName);
                maskingViewInstance = maskingViewExists(storage, maskingViewName);
            }

            _log.info(String.format("MaskingView will be named '%s'", maskingViewName));
            exportMask.setMaskName(maskingViewName);
            _dbClient.persistObject(exportMask);
        }

        return maskingViewName;
    }

}
