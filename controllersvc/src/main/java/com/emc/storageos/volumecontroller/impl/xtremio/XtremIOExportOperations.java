/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.xtremio;

import java.net.URI;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import com.emc.storageos.customconfigcontroller.CustomConfigConstants;
import com.emc.storageos.customconfigcontroller.DataSource;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.PerformancePolicy;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.util.InvokeTestFailure;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.VolumeURIHLU;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskRemoveInitiatorCompleter;
import com.emc.storageos.volumecontroller.impl.smis.ExportMaskOperations;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.volumecontroller.impl.utils.ExportOperationContext;
import com.emc.storageos.volumecontroller.impl.utils.ExportOperationContext.ExportOperationContextOperation;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorFactory;
import com.emc.storageos.volumecontroller.impl.validators.contexts.ExportMaskValidationContext;
import com.emc.storageos.volumecontroller.impl.validators.xtremio.XtremIOExportMaskInitiatorsValidator;
import com.emc.storageos.volumecontroller.impl.validators.xtremio.XtremIOExportMaskVolumesValidator;
import com.emc.storageos.volumecontroller.impl.xtremio.prov.utils.XtremIOProvUtils;
import com.emc.storageos.workflow.WorkflowService;
import com.emc.storageos.xtremio.restapi.XtremIOClient;
import com.emc.storageos.xtremio.restapi.XtremIOConstants;
import com.emc.storageos.xtremio.restapi.XtremIOConstants.XTREMIO_ENTITY_TYPE;
import com.emc.storageos.xtremio.restapi.errorhandling.XtremIOApiException;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOInitiator;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOInitiatorGroup;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOLunMap;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOObjectInfo;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOTag;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOVolume;
import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Collections2;
import com.google.common.collect.Sets;

public class XtremIOExportOperations extends XtremIOOperations implements ExportMaskOperations {
    private static final Logger _log = LoggerFactory.getLogger(XtremIOExportOperations.class);

    private ValidatorFactory validator;

    public void setValidator(ValidatorFactory validator) {
        this.validator = validator;
    }

    @Override
    public void createExportMask(StorageSystem storage, URI exportMaskURI,
            VolumeURIHLU[] volumeURIHLUs, List<URI> targetURIList, List<Initiator> initiatorList,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} createExportMask START...", storage.getSerialNumber());
        try {
            _log.info("createExportMask: Export mask id: {}", exportMaskURI);
            _log.info("createExportMask: volume-HLU pairs: {}", Joiner.on(',').join(volumeURIHLUs));
            _log.info("createExportMask: initiators: {}", Joiner.on(',').join(initiatorList));
            _log.info("createExportMask: assignments: {}", Joiner.on(',').join(targetURIList));

            ExportOperationContext context = new XtremIOExportOperationContext();
            // Prime the context object
            taskCompleter.updateWorkflowStepContext(context);

            ExportMask exportMask = dbClient.queryObject(ExportMask.class, exportMaskURI);
            if (exportMask == null || exportMask.getInactive()) {
                throw new DeviceControllerException("Invalid ExportMask URI: " + exportMaskURI);
            }

            XtremIOClient client = XtremIOProvUtils.getXtremIOClient(dbClient, storage, xtremioRestClientFactory);
            String xioClusterName = client.getClusterDetails(storage.getSerialNumber()).getName();

            List<Initiator> initiatorsToBeCreated = new ArrayList<Initiator>();
            ArrayListMultimap<String, Initiator> initiatorToIGMap = XtremIOProvUtils.mapInitiatorToInitiatorGroup(
                    storage.getSerialNumber(),
                    initiatorList, initiatorsToBeCreated, xioClusterName, client);

            runLunMapCreationAlgorithm(storage, exportMask, volumeURIHLUs, initiatorList,
                    targetURIList, client, xioClusterName, initiatorToIGMap, initiatorsToBeCreated, taskCompleter);
        } catch (final Exception ex) {
            _log.error("Problem in createExportMask: ", ex);
            ServiceError serviceError = DeviceControllerErrors.xtremio
                    .operationFailed("createExportMask", ex.getMessage());
            taskCompleter.error(dbClient, serviceError);
        }
        _log.info("{} createExportMask END...", storage.getSerialNumber());
    }

    @Override
    public void deleteExportMask(StorageSystem storage, URI exportMaskURI, List<URI> volumeURIList,
            List<URI> targetURIList, List<Initiator> initiatorList, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        _log.info("{} deleteExportMask START...", storage.getSerialNumber());

        try {
            _log.info("Export mask id: {}", exportMaskURI);

            if (volumeURIList != null) {
                _log.info("deleteExportMask: volumes:  {}", Joiner.on(',').join(volumeURIList));
            }
            if (targetURIList != null) {
                _log.info("deleteExportMask: assignments: {}", Joiner.on(',').join(targetURIList));
            }
            if (initiatorList != null) {
                _log.info("deleteExportMask: initiators: {}", Joiner.on(',').join(initiatorList));
            }
            List<URI> volumesToBeUnmapped = new ArrayList<URI>();
            boolean isRollback = WorkflowService.getInstance().isStepInRollbackState(taskCompleter.getOpId());
            if (isRollback) {
                _log.info("Handling deleteExportMask as a result of rollback");
                List<URI> addedVolumes = new ArrayList<URI>();

                // Get the context from the task completer
                ExportOperationContext context = (ExportOperationContext) WorkflowService.getInstance()
                        .loadStepData(taskCompleter.getOpId());
                if (context != null && context.getOperations() != null) {
                    ListIterator li = context.getOperations().listIterator(context.getOperations().size());
                    while (li.hasPrevious()) {
                        ExportOperationContextOperation operation = (ExportOperationContextOperation) li.previous();
                        if (operation != null
                                && XtremIOExportOperationContext.OPERATION_ADD_VOLUMES_TO_INITIATOR_GROUP
                                        .equals(operation.getOperation())) {
                            addedVolumes = (List<URI>) operation.getArgs().get(0);
                            _log.info("Removing volumes {} as part of rollback", Joiner.on(',').join(addedVolumes));
                        }
                    }
                }

                volumesToBeUnmapped = addedVolumes;
                if (volumesToBeUnmapped == null || volumesToBeUnmapped.isEmpty()) {
                    _log.info("There was no context found for add volumes. So there is nothing to rollback.");
                    taskCompleter.ready(dbClient);
                    return;
                }
            } else {
                if (volumeURIList != null) {
                    volumesToBeUnmapped = volumeURIList;
                }
            }
            ExportMask exportMask = dbClient.queryObject(ExportMask.class, exportMaskURI);
            if (exportMask == null || exportMask.getInactive()) {
                throw new DeviceControllerException("Invalid ExportMask URI: " + exportMaskURI);
            }

            runLunMapDeletionOrRemoveInitiatorAlgorithm(storage, exportMask, volumesToBeUnmapped, initiatorList, taskCompleter);
        } catch (final Exception ex) {
            _log.error("Problem in deleteExportMask: ", ex);
            ServiceError serviceError = DeviceControllerErrors.xtremio
                    .operationFailed("deleteExportMask", ex.getMessage());
            taskCompleter.error(dbClient, serviceError);
        }
        _log.info("{} deleteExportMask END...", storage.getSerialNumber());
    }

    @Override
    public void addVolumes(StorageSystem storage, URI exportMaskURI, VolumeURIHLU[] volumeURIHLUs,
            List<Initiator> initiatorList, TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} addVolumes START...", storage.getSerialNumber());

        try {
            _log.info("addVolumes: Export mask id: {}", exportMaskURI);
            _log.info("addVolumes: volume-HLU pairs: {}", Joiner.on(',').join(volumeURIHLUs));

            if (initiatorList != null) {
                _log.info("addVolumes: initiators impacted: {}", Joiner.on(',').join(initiatorList));
            }

            ExportOperationContext context = new XtremIOExportOperationContext();
            // Prime the context object
            taskCompleter.updateWorkflowStepContext(context);

            ExportMask exportMask = dbClient.queryObject(ExportMask.class, exportMaskURI);
            if (exportMask == null || exportMask.getInactive()) {
                throw new DeviceControllerException("Invalid ExportMask URI: " + exportMaskURI);
            }

            XtremIOClient client = XtremIOProvUtils.getXtremIOClient(dbClient, storage, xtremioRestClientFactory);
            String xioClusterName = client.getClusterDetails(storage.getSerialNumber()).getName();

            ArrayListMultimap<String, Initiator> initiatorToIGMap = XtremIOProvUtils.mapInitiatorToInitiatorGroup(
                    storage.getSerialNumber(),
                    initiatorList, null, xioClusterName, client);

            XtremIOExportMaskInitiatorsValidator initiatorsValidator = (XtremIOExportMaskInitiatorsValidator) validator.addVolumes(storage,
                    exportMaskURI, initiatorList);
            initiatorsValidator.setInitiatorToIGMap(initiatorToIGMap);
            initiatorsValidator.validate();

            runLunMapCreationAlgorithm(storage, exportMask, volumeURIHLUs, initiatorList, null, client, xioClusterName, initiatorToIGMap,
                    null, taskCompleter);
        } catch (final Exception ex) {
            _log.error("Problem in addVolumes: ", ex);
            ServiceError serviceError = DeviceControllerErrors.xtremio
                    .operationFailed("addVolumes", ex.getMessage());
            taskCompleter.error(dbClient, serviceError);
        }
        _log.info("{} addVolumes END...", storage.getSerialNumber());
    }

    @Override
    public void removeVolumes(StorageSystem storage, URI exportMaskURI, List<URI> volumeUris,
            List<Initiator> initiatorList, TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} removeVolumes START...", storage.getSerialNumber());

        try {
            _log.info("removeVolumes: Export mask id: {}", exportMaskURI);
            _log.info("removeVolumes: volumes: {}", Joiner.on(',').join(volumeUris));

            if (initiatorList != null) {
                _log.info("removeVolumes: impacted initiators: {}", Joiner.on(",").join(initiatorList));
            }
            boolean isRollback = WorkflowService.getInstance().isStepInRollbackState(taskCompleter.getOpId());
            if (isRollback) {
                _log.info("Handling removeVolumes as a result of rollback");
                List<URI> addedVolumes = new ArrayList<URI>();
                // Get the context from the task completer.
                ExportOperationContext context = (ExportOperationContext) WorkflowService.getInstance()
                        .loadStepData(taskCompleter.getOpId());
                if (context != null && context.getOperations() != null) {
                    ListIterator li = context.getOperations().listIterator(context.getOperations().size());
                    while (li.hasPrevious()) {
                        ExportOperationContextOperation operation = (ExportOperationContextOperation) li.previous();
                        if (operation != null
                                && XtremIOExportOperationContext.OPERATION_ADD_VOLUMES_TO_INITIATOR_GROUP
                                        .equals(operation.getOperation())) {
                            addedVolumes = (List<URI>) operation.getArgs().get(0);
                            _log.info("Removing volumes {} as part of rollback", Joiner.on(',').join(addedVolumes));
                        }
                    }
                }
                volumeUris = addedVolumes;
                if (volumeUris == null || volumeUris.isEmpty()) {
                    _log.info("There was no context found for add volumes. So there is nothing to rollback.");
                    taskCompleter.ready(dbClient);
                    return;
                }
            }
            ExportMask exportMask = dbClient.queryObject(ExportMask.class, exportMaskURI);
            if (exportMask == null || exportMask.getInactive()) {
                throw new DeviceControllerException("Invalid ExportMask URI: " + exportMaskURI);
            }

            runLunMapDeletionAlgorithm(storage, exportMask, volumeUris, initiatorList, taskCompleter);
        } catch (final Exception ex) {
            _log.error("Problem in removeVolumes: ", ex);
            ServiceError serviceError = DeviceControllerErrors.xtremio
                    .operationFailed("removeVolumes", ex.getMessage());
            taskCompleter.error(dbClient, serviceError);
        }
        _log.info("{} removeVolumes END...", storage.getSerialNumber());
    }

    @Override
    public void addInitiators(StorageSystem storage, URI exportMaskURI, List<URI> volumeURIs,
            List<Initiator> initiators, List<URI> targets, TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} addInitiators START...", storage.getSerialNumber());

        try {
            _log.info("addInitiators: Export mask id: {}", exportMaskURI);

            if (volumeURIs != null) {
                _log.info("addInitiators: volumes : {}", Joiner.on(',').join(volumeURIs));
            }
            _log.info("addInitiators: initiators : {}", Joiner.on(',').join(initiators));
            _log.info("addInitiators: targets : {}", Joiner.on(",").join(targets));
            ExportOperationContext context = new XtremIOExportOperationContext();
            // Prime the context object
            taskCompleter.updateWorkflowStepContext(context);

            ExportMask exportMask = dbClient.queryObject(ExportMask.class, exportMaskURI);
            if (exportMask == null || exportMask.getInactive()) {
                throw new DeviceControllerException("Invalid ExportMask URI: " + exportMaskURI);
            }
            XtremIOClient client = XtremIOProvUtils.getXtremIOClient(dbClient, storage, xtremioRestClientFactory);
            String xioClusterName = client.getClusterDetails(storage.getSerialNumber()).getName();

            List<Initiator> initiatorsToBeCreated = new ArrayList<Initiator>();
            ArrayListMultimap<String, Initiator> initiatorToIGMap = XtremIOProvUtils.mapInitiatorToInitiatorGroup(
                    storage.getSerialNumber(),
                    initiators, initiatorsToBeCreated, xioClusterName, client);

            XtremIOExportMaskVolumesValidator volumeValidator = (XtremIOExportMaskVolumesValidator) validator.addInitiators(storage,
                    exportMask, volumeURIs);
            volumeValidator.setIgNames(initiatorToIGMap.keySet());
            volumeValidator.validate();

            Map<URI, Integer> map = new HashMap<URI, Integer>();
            for (URI volumeURI : volumeURIs) {
                String hlu = exportMask.getVolumes().get(volumeURI.toString());
                if (NullColumnValueGetter.isNotNullValue(hlu)) {
                    map.put(volumeURI, Integer.parseInt(hlu));
                }
            }

            // to make it uniform , using these structures
            VolumeURIHLU[] volumeLunArray = ControllerUtils.getVolumeURIHLUArray(
                    storage.getSystemType(), map, dbClient);

            runLunMapCreationAlgorithm(storage, exportMask, volumeLunArray, initiators, targets, client, xioClusterName, initiatorToIGMap,
                    initiatorsToBeCreated, taskCompleter);
        } catch (final Exception ex) {
            _log.error("Problem in addInitiators: ", ex);
            ServiceError serviceError = DeviceControllerErrors.xtremio
                    .operationFailed("addInitiators", ex.getMessage());
            taskCompleter.error(dbClient, serviceError);
        }
        _log.info("{} addInitiators END...", storage.getSerialNumber());

    }

    @Override
    public void removeInitiators(StorageSystem storage, URI exportMaskURI,
            List<URI> volumeURIList, List<Initiator> initiators, List<URI> targets, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        _log.info("{} removeInitiators START...", storage.getSerialNumber());
        boolean isRollback = WorkflowService.getInstance().isStepInRollbackState(taskCompleter.getOpId());
        if (isRollback) {
            _log.info("Handling removeInitiators as a result of rollback");
            List<Initiator> addedInitiators = new ArrayList<Initiator>();
            // Get the context from the task completer.
            ExportOperationContext context = (ExportOperationContext) WorkflowService.getInstance().loadStepData(taskCompleter.getOpId());
            if (context != null && context.getOperations() != null) {
                ListIterator li = context.getOperations().listIterator(context.getOperations().size());
                while (li.hasPrevious()) {
                    ExportOperationContextOperation operation = (ExportOperationContextOperation) li.previous();
                    if (operation != null
                            && XtremIOExportOperationContext.OPERATION_ADD_INITIATORS_TO_INITIATOR_GROUP.equals(operation.getOperation())) {
                        addedInitiators = (List<Initiator>) operation.getArgs().get(0);
                        _log.info("Removing initiators {} as part of rollback", Joiner.on(',').join(addedInitiators));
                    }
                }
            }

            // Update the initiators in the task completer such that we update the export mask/group correctly
            for (Initiator initiator : initiators) {
                if (addedInitiators == null || !addedInitiators.contains(initiator)) {
                    ((ExportMaskRemoveInitiatorCompleter) taskCompleter).removeInitiator(initiator.getId());
                }
            }
            initiators = addedInitiators;
            if (initiators == null || initiators.isEmpty()) {
                _log.info("There was no context found for add initiator. So there is nothing to rollback.");
                taskCompleter.ready(dbClient);
                return;
            }
        }

        ExportMask exportMask = dbClient.queryObject(ExportMask.class, exportMaskURI);
        if (exportMask == null || exportMask.getInactive()) {
            throw new DeviceControllerException("Invalid ExportMask URI: " + exportMaskURI);
        }

        XtremIOClient client = null;
        // if host Name is not available in at least one of the initiator, then set it to
        // Default_IG;
        List<String> failedIGs = new ArrayList<String>();
        ArrayListMultimap<String, Initiator> groupInitiatorsByIG = ArrayListMultimap.create();
        try {            
            String hostName = null;
            String clusterName = null;
            client = XtremIOProvUtils.getXtremIOClient(dbClient, storage, xtremioRestClientFactory);
            String xioClusterName = client.getClusterDetails(storage.getSerialNumber()).getName();

            Iterator<Initiator> iniItr = initiators.iterator();
            while (iniItr.hasNext()) {
                Initiator initiator = iniItr.next();
                String igName = null;
                if (null != initiator.getHostName()) {
                    // initiators already grouped by Host
                    hostName = initiator.getHostName();
                    clusterName = initiator.getClusterName();
                }
                igName = XtremIOProvUtils.getIGNameForInitiator(initiator, storage.getSerialNumber(), client, xioClusterName);
                if (igName != null && !igName.isEmpty()) {
                    groupInitiatorsByIG.put(igName, initiator);
                } else {
                    // initiator not found in Array, remove from DB
                    exportMask.removeFromExistingInitiators(initiator);
                    exportMask.removeFromUserCreatedInitiators(initiator);
                    iniItr.remove();
                }                                
            }
            
            // We need to look at all related initiators from the affected EM. We can use this list
            // to then find all related volumes across all EMs. This will allow us to properly
            // perform our validations.
            List<Initiator> relatedInitiators = new ArrayList<Initiator>();
            if (exportMask.getInitiators() != null && !exportMask.getInitiators().isEmpty()) {
                Collection<URI> relatedInitiatorURIs = Collections2.transform(exportMask.getInitiators(),
                        CommonTransformerFunctions.FCTN_STRING_TO_URI);
                relatedInitiators.addAll(dbClient.queryObject(Initiator.class, relatedInitiatorURIs));
            } else {
                relatedInitiators.addAll(initiators);
            }
            
            Set<URI> allRelatedVolumes = new HashSet<URI>();
            allRelatedVolumes.addAll(findAllRelatedExportMaskVolumesForInitiator(relatedInitiators, exportMask.getStorageDevice()));
            
            _log.info("removeInitiators: Export mask id: {}", exportMaskURI);

            if (!CollectionUtils.isEmpty(allRelatedVolumes)) {
                _log.info("removeInitiators: volumes : {}", Joiner.on(',').join(allRelatedVolumes));
            }
            _log.info("removeInitiators: initiators : {}", Joiner.on(',').join(initiators));
            _log.info("removeInitiators: targets : {}", Joiner.on(',').join(targets));

            _log.info("List of  IGs found {} with size : {}",
                    Joiner.on(",").join(groupInitiatorsByIG.asMap().entrySet()), groupInitiatorsByIG.size());
            
            ExportMaskValidationContext ctx = new ExportMaskValidationContext();
            ctx.setStorage(storage);
            ctx.setExportMask(exportMask);
            ctx.setBlockObjects(allRelatedVolumes, dbClient);
            ctx.setAllowExceptions(!isRollback);
            XtremIOExportMaskVolumesValidator volumeValidator = (XtremIOExportMaskVolumesValidator) validator.removeInitiators(ctx);
            volumeValidator.setIgNames(groupInitiatorsByIG.keySet());
            volumeValidator.validate();

            // Deleting the initiator automatically removes the initiator from
            // lun map
            for (Initiator initiator : initiators) {
                try {
                    client.deleteInitiator(initiator.getMappedInitiatorName(storage.getSerialNumber()), xioClusterName);
                    exportMask.removeFromExistingInitiators(initiator);
                    exportMask.removeFromUserCreatedInitiators(initiator);
                } catch (Exception e) {
                    failedIGs.add(initiator.getLabel().concat(XtremIOConstants.DASH).concat(e.getMessage()));
                    _log.warn("Removal of Initiator {} failed", initiator.getLabel(), e);
                }
            }
            dbClient.updateObject(exportMask);

            if (!failedIGs.isEmpty()) {
                String errMsg = "Export Operations failed deleting these initiators: ".concat(Joiner.on(", ").join(
                        failedIGs));
                ServiceError serviceError = DeviceControllerException.errors.jobFailedMsg(errMsg, null);
                taskCompleter.error(dbClient, serviceError);
                return;
            }

            // Clean IGs if empty

            deleteInitiatorGroup(groupInitiatorsByIG, client, xioClusterName);
            // delete IG Folder as well if IGs are empty
            deleteInitiatorGroupFolder(client, xioClusterName, clusterName, hostName, storage);

            taskCompleter.ready(dbClient);
        } catch (Exception ex) {
            _log.error("Problem in removeInitiators: ", ex);
            ServiceError serviceError = DeviceControllerErrors.xtremio
                    .operationFailed("removeInitiators", ex.getMessage());
            taskCompleter.error(dbClient, serviceError);
            return;
        }
        _log.info("{} removeInitiators END...", storage.getSerialNumber());
    }

    @Override
    public Map<String, Set<URI>> findExportMasks(StorageSystem storage,
            List<String> initiatorNames, boolean mustHaveAllPorts) throws DeviceControllerException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Set<Integer> findHLUsForInitiators(StorageSystem storage, List<String> initiatorNames, boolean mustHaveAllPorts) {
        Set<Integer> usedHLUs = new HashSet<Integer>();
        try {
            XtremIOClient client = XtremIOProvUtils.getXtremIOClient(dbClient, storage, xtremioRestClientFactory);
            Set<String> igNames = new HashSet<>();
            String xioClusterName = client.getClusterDetails(storage.getSerialNumber()).getName();
            for (String initiatorName : initiatorNames) {
                initiatorName = Initiator.toPortNetworkId(initiatorName);
                URIQueryResultList initiatorResult = new URIQueryResultList();
                dbClient.queryByConstraint(AlternateIdConstraint.Factory.getInitiatorPortInitiatorConstraint(initiatorName),
                        initiatorResult);
                if (initiatorResult.iterator().hasNext()) {
                    Initiator initiator = dbClient.queryObject(Initiator.class, initiatorResult.iterator().next());
                    String igName = XtremIOProvUtils.getIGNameForInitiator(initiator, storage.getSerialNumber(), client, xioClusterName);
                    if (igName != null && !igName.isEmpty()) {
                        igNames.add(igName);
                    }
                }
            }

            // get the lun maps for IGs
            for (String igName : igNames) {
                List<XtremIOObjectInfo> lunMapLinks = XtremIOProvUtils.getInitiatorGroupLunMaps(igName, xioClusterName, client);
                List<XtremIOLunMap> lunMaps = client.getXtremIOLunMapsForLinks(lunMapLinks, xioClusterName);
                for (XtremIOLunMap lunMap : lunMaps) {
                    _log.info("Looking at lun map {}; IG name: {}, Volume: {}, HLU: {}",
                            lunMap.getMappingInfo().get(2), lunMap.getIgName(), lunMap.getVolumeName(), lunMap.getLun());
                    usedHLUs.add(Integer.valueOf(lunMap.getLun()));
                }
            }

            _log.info(String.format("HLUs found for Initiators { %s }: %s",
                    Joiner.on(',').join(initiatorNames), usedHLUs));
        } catch (Exception e) {
            String errMsg = "Encountered an error when attempting to query used HLUs for initiators: " + e.getMessage();
            _log.error(errMsg, e);
            throw XtremIOApiException.exceptions.hluRetrievalFailed(errMsg, e);
        }
        return usedHLUs;
    }

    /*
     * Refresh all export masks, not only the one passed in
     * @see com.emc.storageos.volumecontroller.impl.smis.ExportMaskOperations#refreshExportMask(com.emc.storageos.db.client.model.StorageSystem, com.emc.storageos.db.client.model.ExportMask)
     */
    @Override
    public ExportMask refreshExportMask(StorageSystem storage, ExportMask mask) throws DeviceControllerException {
        ExportMask maskToReturn = null;
        try {
            _log.info("Refreshing volumes and initiator labels in ViPR.. ");
            XtremIOClient client = XtremIOProvUtils.getXtremIOClient(dbClient, storage, xtremioRestClientFactory);
            Set<String> igNameSet = new HashSet<>();
            Map<URI, Set<String>> maskToIGNameMap = new HashMap<>();

            String xioClusterName = client.getClusterDetails(storage.getSerialNumber()).getName();
            List<XtremIOInitiator> initiators = client.getXtremIOInitiatorsInfo(xioClusterName);
            List<Initiator> initiatorObjs = new ArrayList<Initiator>();
            for (XtremIOInitiator initiator : initiators) {
                URIQueryResultList initiatorResult = new URIQueryResultList();
                dbClient
                        .queryByConstraint(AlternateIdConstraint.Factory.getInitiatorPortInitiatorConstraint(initiator.getPortAddress()),
                                initiatorResult);
                if (initiatorResult.iterator().hasNext()) {
                    Initiator initiatorObj = dbClient.queryObject(Initiator.class, initiatorResult.iterator().next());
                    _log.info("Updating Initiator label from {} to {} in ViPR DB", initiatorObj.getLabel(), initiator.getName());
                    initiatorObj.setLabel(initiator.getName());
                    initiatorObj.mapInitiatorName(storage.getSerialNumber(), initiator.getName());
                    initiatorObjs.add(initiatorObj);
                    
                    List<ExportMask> results = CustomQueryUtility.queryActiveResourcesByConstraint(dbClient, ExportMask.class,
                            ContainmentConstraint.Factory.getConstraint(ExportMask.class, "initiators", initiatorObj.getId()));
                    String igName = initiator.getInitiatorGroup().get(1);
                    for (ExportMask exportMask : results) {
                        if (exportMask != null && storage.getId().equals(exportMask.getStorageDevice())) {
                            igNameSet.add(igName);

                            // update export mask to IG name map
                            URI maskId = exportMask.getId();
                            Set<String> igNames = maskToIGNameMap.get(maskId);
                            if (igNames == null) {
                                igNames = new HashSet<String>();
                                maskToIGNameMap.put(maskId, igNames);
                            }
                            igNames.add(igName);
                        }
                    }
                } else {
                    _log.info("No initiator objects in vipr db for port address {}", initiator.getPortAddress());
                }
            }
            if (!initiatorObjs.isEmpty()) {
                dbClient.updateObject(initiatorObjs);
            }

            // get volumes for each IG
            Map<String, Map<String, Integer>> igNameToVolMap = new HashMap<>();
            for (String igName: igNameSet) {
                Map<String, Integer> discoveredVolumes = new HashMap<String, Integer>();

                List<XtremIOVolume> igVolumes = XtremIOProvUtils.getInitiatorGroupVolumes(igName, xioClusterName, client);
                for (XtremIOVolume igVolume : igVolumes) {
                    for (List<Object> lunMapEntries : igVolume.getLunMaps()) {
                        @SuppressWarnings("unchecked")
                        // This can't be null
                        List<Object> igDetails = (List<Object>) lunMapEntries.get(0);
                        if (null == igDetails.get(1) || null == lunMapEntries.get(2)) {
                            _log.warn("IG Name or hlu is null in returned lun map response for volume {}", igVolume.toString());
                            continue;
                        }
                        String igNameToProcess = (String) igDetails.get(1);
                        if (!igName.equalsIgnoreCase(igNameToProcess)) {
                            continue;
                        }

                        Double hluNumber = (Double) lunMapEntries.get(2);
                        _log.info("Found HLU {} for volume {}", hluNumber, igVolume.getVolInfo().get(1));
                        // for each IG involved, the same volume is visible through different HLUs.
                        // TODO we might need a list of HLU for each Volume URI
                        discoveredVolumes.put(BlockObject.normalizeWWN(igVolume.getWwn()), Integer.valueOf(hluNumber.intValue()));
                    }
                }
                
                igNameToVolMap.put(igName, discoveredVolumes);
            }

            // update each mask
            for (Entry<URI, Set<String>> entry : maskToIGNameMap.entrySet()) {
                URI maskId = entry.getKey();
                ExportMask exportMask = dbClient.queryObject(ExportMask.class, maskId);
                if (exportMask == null || exportMask.getInactive()) {
                    continue;
                }

                Map<String, Integer> discoveredVolumes = new HashMap<String, Integer>();
                for (String igName : entry.getValue()) {
                    discoveredVolumes.putAll(igNameToVolMap.get(igName));
                }

                // Clear the existing volumes to update with the latest info
                if (exportMask.getExistingVolumes() != null && !exportMask.getExistingVolumes().isEmpty()) {
                    exportMask.getExistingVolumes().clear();
                }
                
                //COP-27296 fix
                if (null == exportMask.getUserAddedVolumes()) {
                    exportMask.setUserAddedVolumes(new StringMap());
                }
                
                // We need to look at all related initiators from the affected EM. We can use this list
                // to then find all related volumes across all EMs. This will allow us to properly
                // perform our validations.
                List<Initiator> relatedInitiators = new ArrayList<Initiator>();
                if (exportMask.getInitiators() != null && !exportMask.getInitiators().isEmpty()) {
                    Collection<URI> relatedInitiatorURIs = Collections2.transform(exportMask.getInitiators(),
                            CommonTransformerFunctions.FCTN_STRING_TO_URI);
                    relatedInitiators.addAll(dbClient.queryObject(Initiator.class, relatedInitiatorURIs));
                }
                
                Set<URI> allRelatedVolumes = new HashSet<URI>();
                allRelatedVolumes.addAll(findAllRelatedExportMaskVolumesForInitiator(relatedInitiators, exportMask.getStorageDevice()));
                
                // If there are related volumes found, get the WWNs so we can diff against what has
                // been discovered on the array.
                Set<String> allRelatedVolumesWWN = new HashSet<String>();
                for (URI relatedVolumeURI : allRelatedVolumes) {
                    BlockObject relatedObj = BlockObject.fetch(dbClient, relatedVolumeURI);
                    if (relatedObj != null) {
                        allRelatedVolumesWWN.add(relatedObj.getWWN());
                    }
                }
                
                Set<String> existingVolumes = Sets.difference(discoveredVolumes.keySet(), allRelatedVolumesWWN);

                _log.info(String.format("XtremIO discovered volumes: {%s}%n", Joiner.on(',').join(discoveredVolumes.keySet())));
                _log.info(String.format("%nXtremIO mask existing volumes: {%s}%n", Joiner.on(',').join(existingVolumes)));

                for (String wwn : existingVolumes) {
                    exportMask.addToExistingVolumesIfAbsent(wwn, discoveredVolumes.get(wwn).toString());
                }
                // Update user added volume's HLU information in ExportMask and ExportGroup
                ExportMaskUtils.updateHLUsInExportMask(exportMask, discoveredVolumes, dbClient);
                dbClient.updateObject(exportMask);
                if (mask != null && maskId.equals(mask.getId())) {
                    maskToReturn = exportMask;
                }
            }
        } catch (Exception e) {
            if (null != e.getMessage() && !e.getMessage().contains(XtremIOConstants.OBJECT_NOT_FOUND)) {
                String msg = String.format("Error when refreshing export masks for the system %s, details: %s", storage.forDisplay(),
                        e.getMessage());
                throw XtremIOApiException.exceptions.refreshExistingMaskFailure(msg, e);
            } else {
                _log.warn("Error refreshing export masks for the system {}", storage.forDisplay());
            }
        }

        return maskToReturn;
    }

    private void runLunMapDeletionAlgorithm(StorageSystem storage, ExportMask exportMask,
            List<URI> volumes, List<Initiator> initiators, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        // find LunMap associated with Volume
        // Then find initiatorGroup associated with this lun map

        // find initiators associated with IG, if the given list is of initiators is same, then run
        // removeLunMap
        XtremIOClient client = null;
        // if host Name is not available in at least one of the initiator, then set it to
        // Default_IG;
        try {
            String hostName = null;
            String clusterName = null;
            client = XtremIOProvUtils.getXtremIOClient(dbClient, storage, xtremioRestClientFactory);
            String xioClusterName = client.getClusterDetails(storage.getSerialNumber()).getName();

            for (Initiator initiator : initiators) {
                if (null != initiator.getHostName()) {
                    // initiators already grouped by Host
                    hostName = initiator.getHostName();
                    clusterName = initiator.getClusterName();
                    break;
                }
            }
            ArrayListMultimap<String, Initiator> groupInitiatorsByIG = XtremIOProvUtils.mapInitiatorToInitiatorGroup(
                    storage.getSerialNumber(), initiators,
                    null, xioClusterName, client);
            ExportMaskValidationContext ctx = new ExportMaskValidationContext();
            ctx.setStorage(storage);
            ctx.setExportMask(exportMask);
            ctx.setInitiators(initiators);
            ctx.setAllowExceptions(!WorkflowService.getInstance().isStepInRollbackState(taskCompleter.getOpId()));
            XtremIOExportMaskInitiatorsValidator initiatorsValidator = (XtremIOExportMaskInitiatorsValidator) validator
                    .removeVolumes(ctx);
            initiatorsValidator.setInitiatorToIGMap(groupInitiatorsByIG);
            initiatorsValidator.validate();

            Set<String> igNames = groupInitiatorsByIG.keySet();
            List<String> failedVolumes = new ArrayList<String>();
            for (URI volumeUri : volumes) {
                BlockObject blockObj = BlockObject.fetch(dbClient, volumeUri);
                _log.info("Block Obj {} , wwn {}", blockObj.getId(), blockObj.getWWN());
                XtremIOVolume xtremIOVolume = null;
                if (URIUtil.isType(volumeUri, Volume.class)) {
                    xtremIOVolume = XtremIOProvUtils.isVolumeAvailableInArray(client,
                            blockObj.getDeviceLabel(), xioClusterName);

                    // It could be that the block object is actually a snapshot on the array
                    // because a VPLEX volume was created on top of a snapshot, and when this is
                    // done, a dummy backend volume is created using the data from the block
                    // snapshot because VPLEX volumes need to be built on volumes. So, if the
                    // returned value is null, check the snapshots.
                    if (xtremIOVolume == null) {
                        xtremIOVolume = XtremIOProvUtils.isSnapAvailableInArray(client,
                                blockObj.getDeviceLabel(), xioClusterName);
                    }
                } else {
                    xtremIOVolume = XtremIOProvUtils.isSnapAvailableInArray(client,
                            blockObj.getDeviceLabel(), xioClusterName);
                }

                if (null != xtremIOVolume) {
                    // I need lun map id and igName
                    // if iGName is available in the above group, then remove lunMap
                    _log.info("Volume Details {}", xtremIOVolume.toString());
                    _log.info("Volume lunMap details {}", xtremIOVolume.getLunMaps().toString());

                    Set<String> lunMaps = new HashSet<String>();
                    String volId = xtremIOVolume.getVolInfo().get(2);

                    if (xtremIOVolume.getLunMaps().isEmpty()) {
                        // handle scenarios where volumes gets unexported already
                        _log.info("Volume  {} doesn't have any existing export available on Array, unexported already.",
                                xtremIOVolume.toString());
                        exportMask.removeFromUserCreatedVolumes(blockObj);
                        exportMask.removeVolume(blockObj.getId());
                        continue;
                    }
                    for (List<Object> lunMapEntries : xtremIOVolume.getLunMaps()) {

                        @SuppressWarnings("unchecked")
                        List<Object> igDetails = (List<Object>) lunMapEntries.get(0);
                        String igName = (String) igDetails.get(1);

                        // Ig details is actually transforming to A double by deofault, even though
                        // its modeled as List<String>
                        // hence this logic
                        Double IgIdDouble = (Double) igDetails.get(2);
                        String igId = String.valueOf(IgIdDouble.intValue());

                        _log.info("IG Name: {} Id: {} found in Lun Map", igName, igId);
                        if (!igNames.contains(igName)) {
                            _log.info(
                                    "Volume is associated with IG {} which is not in the removal list requested, ignoring..",
                                    igName);
                            continue;
                        }

                        @SuppressWarnings("unchecked")
                        List<Object> tgtGroupDetails = (List<Object>) lunMapEntries.get(1);
                        Double tgIdDouble = (Double) tgtGroupDetails.get(2);
                        String tgtid = String.valueOf(tgIdDouble.intValue());
                        String lunMapId = volId.concat(XtremIOConstants.UNDERSCORE).concat(igId)
                                .concat(XtremIOConstants.UNDERSCORE).concat(tgtid);
                        _log.info("LunMap Id {} Found associated with Volume {}", lunMapId,
                                blockObj.getDeviceLabel());
                        lunMaps.add(lunMapId);

                    }
                    // deletion of lun Maps
                    // there will be only one lun map always
                    for (String lunMap : lunMaps) {
                        try {
                            client.deleteLunMap(lunMap, xioClusterName);
                        } catch (Exception e) {
                            failedVolumes.add(volumeUri.toString().concat(XtremIOConstants.DASH).concat(e.getMessage()));
                            _log.warn("Deletion of Lun Map {} failed}", lunMap, e);
                        }
                    }
                } else {
                    exportMask.removeFromUserCreatedVolumes(blockObj);
                    exportMask.removeVolume(blockObj.getId());
                }

            }
            dbClient.updateObject(exportMask);

            if (!failedVolumes.isEmpty()) {
                String errMsg = "Export Operations failed for these volumes: ".concat(Joiner.on(", ").join(
                        failedVolumes));
                ServiceError serviceError = DeviceControllerException.errors.jobFailedMsg(
                        errMsg, null);
                taskCompleter.error(dbClient, serviceError);
                return;
            }

            // Clean IGs if empty
            deleteInitiatorGroup(groupInitiatorsByIG, client, xioClusterName);
            // delete IG Folder as well if IGs are empty
            deleteInitiatorGroupFolder(client, xioClusterName, clusterName, hostName, storage);

            taskCompleter.ready(dbClient);
        } catch (Exception e) {
            _log.error(String.format("Export Operations failed - maskName: %s", exportMask.getId()
                    .toString()), e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }
    }

    /**
     * It deletes the LunMap if the IG contains no other initiators than the requested ones.
     * Else it removes the requested initiators from the IG
     */
    private void runLunMapDeletionOrRemoveInitiatorAlgorithm(StorageSystem storage, ExportMask exportMask,
            List<URI> volumes, List<Initiator> initiators, TaskCompleter taskCompleter)
                    throws DeviceControllerException {
        // find LunMap associated with Volume
        // Then find initiatorGroup associated with this lun map

        XtremIOClient client = null;
        // if host Name is not available in at least one of the initiator, then set it to
        // Default_IG;
        try {
            String hostName = null;
            String clusterName = null;
            client = XtremIOProvUtils.getXtremIOClient(dbClient, storage, xtremioRestClientFactory);
            String xioClusterName = client.getClusterDetails(storage.getSerialNumber()).getName();
            boolean initiatorsOfRP = ExportUtils.checkIfInitiatorsForRP(initiators);

            for (Initiator initiator : initiators) {
                if (null != initiator.getHostName()) {
                    // initiators already grouped by Host
                    hostName = initiator.getHostName();
                    clusterName = initiator.getClusterName();
                    break;
                }
            }
            ArrayListMultimap<String, Initiator> groupInitiatorsByIG = XtremIOProvUtils.mapInitiatorToInitiatorGroup(
                    storage.getSerialNumber(), initiators,
                    null, xioClusterName, client);
            ArrayListMultimap<String, Initiator> knownInitiatorsToIGMap = ArrayListMultimap.create();
            // DU validations for removing volumes from IG.
            ExportMaskValidationContext ctx = new ExportMaskValidationContext();
            ctx.setStorage(storage);
            ctx.setExportMask(exportMask);
            ctx.setInitiators(initiators);
            ctx.setAllowExceptions(!WorkflowService.getInstance().isStepInRollbackState(taskCompleter.getOpId()));
            XtremIOExportMaskInitiatorsValidator initiatorsValidator = (XtremIOExportMaskInitiatorsValidator) validator
                    .removeVolumes(ctx);
            initiatorsValidator.setInitiatorToIGMap(groupInitiatorsByIG);
            initiatorsValidator.setKnownInitiatorToIGMap(knownInitiatorsToIGMap);
            initiatorsValidator.validate();

            Set<String> igNames = groupInitiatorsByIG.keySet();
            List<String> failedVolumes = new ArrayList<String>();
            List<String> failedIGs = new ArrayList<String>();
            for (URI volumeUri : volumes) {
                BlockObject blockObj = BlockObject.fetch(dbClient, volumeUri);
                _log.info("Block Obj {} , wwn {}", blockObj.getId(), blockObj.getWWN());
                XtremIOVolume xtremIOVolume = null;

                if (URIUtil.isType(volumeUri, Volume.class)) {
                    xtremIOVolume = XtremIOProvUtils.isVolumeAvailableInArray(client,
                            blockObj.getDeviceLabel(), xioClusterName);
                } else {
                    if (URIUtil.isType(volumeUri, BlockSnapshot.class) && BlockObject.checkForRP(dbClient, volumeUri)) {
                        // If the BlockObject is a BlockSnapshot of type RP (bookmark), there will be no exported
                        // snapshot. In this case, a target volume will have been exported and the deviceLabel of
                        // the BlockSnapshot reflects the name of that target.
                        _log.info(String.format(
                                "Dealing with a RecoverPoint bookmark lun mapping.  Checking to see if volume %s is available on array.",
                                blockObj.getDeviceLabel()));
                        xtremIOVolume = XtremIOProvUtils.isVolumeAvailableInArray(client,
                                blockObj.getDeviceLabel(), xioClusterName);
                    } else {
                        xtremIOVolume = XtremIOProvUtils.isSnapAvailableInArray(client,
                                blockObj.getDeviceLabel(), xioClusterName);
                    }
                }

                if (null != xtremIOVolume) {
                    // I need lun map id and igName
                    // if iGName is available in the above group:
                    _log.info("Volume Details {}", xtremIOVolume.toString());
                    _log.info("Volume lunMap details {}", xtremIOVolume.getLunMaps().toString());

                    // Lun Maps to delete
                    Set<String> lunMaps = new HashSet<String>();
                    boolean removeInitiator = false;
                    String volId = xtremIOVolume.getVolInfo().get(2);

                    if (xtremIOVolume.getLunMaps().isEmpty()) {
                        // handle scenarios where volumes gets unexported already
                        _log.info("Volume  {} doesn't have any existing export available on Array, unexported already.",
                                xtremIOVolume.toString());
                        exportMask.removeFromUserCreatedVolumes(blockObj);
                        exportMask.removeVolume(blockObj.getId());
                        continue;
                    }
                    for (List<Object> lunMapEntries : xtremIOVolume.getLunMaps()) {
                        @SuppressWarnings("unchecked")
                        List<Object> igDetails = (List<Object>) lunMapEntries.get(0);
                        String igName = (String) igDetails.get(1);

                        // IG details is actually transforming to a double by default, even though
                        // its modeled as List<String>
                        // hence this logic
                        Double IgIdDouble = (Double) igDetails.get(2);
                        String igId = String.valueOf(IgIdDouble.intValue());

                        _log.info("IG Name: {} Id: {} found in Lun Map", igName, igId);
                        if (!igNames.contains(igName)) {
                            _log.info(
                                    "Volume is associated with IG {} which is not in the removal list requested, ignoring..",
                                    igName);
                            continue;
                        }

                        /**
                         * i) If Cluster export:
                         * If there are additional initiators other than the requested ones (Single IG with all cluster
                         * initiators)
                         * - - - remove initiator from IG,
                         * - - - Note: If initiators are of RP (CTRL-13622), always delete LunMap.
                         * - ii) Host export:
                         * - - -- delete LunMap
                         */
                        boolean igHasOtherHostInitiatorsOfSameCluster = knownInitiatorsToIGMap.get(igName).size() > groupInitiatorsByIG
                                .get(igName).size();
                        if (!initiatorsOfRP && clusterName != null && igHasOtherHostInitiatorsOfSameCluster) {
                            removeInitiator = true;
                        }

                        if (!removeInitiator) {
                            // delete LunMap
                            @SuppressWarnings("unchecked")
                            List<Object> tgtGroupDetails = (List<Object>) lunMapEntries.get(1);
                            Double tgIdDouble = (Double) tgtGroupDetails.get(2);
                            String tgtid = String.valueOf(tgIdDouble.intValue());
                            String lunMapId = volId.concat(XtremIOConstants.UNDERSCORE).concat(igId)
                                    .concat(XtremIOConstants.UNDERSCORE).concat(tgtid);
                            _log.info("LunMap Id {} Found associated with Volume {}", lunMapId,
                                    blockObj.getDeviceLabel());
                            lunMaps.add(lunMapId);
                        }
                    }

                    // deletion of lun Maps
                    // there will be only one lun map always
                    for (String lunMap : lunMaps) {
                        try {
                            client.deleteLunMap(lunMap, xioClusterName);
                        } catch (Exception e) {
                            failedVolumes.add(volumeUri.toString().concat(XtremIOConstants.DASH).concat(e.getMessage()));
                            _log.warn("Deletion of Lun Map {} failed}", lunMap, e);
                        }
                    }
                    // remove initiator from IG
                    if (removeInitiator) {
                        _log.info("Removing requested intiators from IG instead of deleting LunMap"
                                + " as the IG contains other Host's initiators belonging to same Cluster.");
                        ctx = new ExportMaskValidationContext();
                        ctx.setStorage(storage);
                        ctx.setExportMask(exportMask);
                        ctx.setBlockObjects(volumes, dbClient);
                        ctx.setAllowExceptions(!WorkflowService.getInstance().isStepInRollbackState(taskCompleter.getOpId()));
                        // DU validation when removing initiators
                        XtremIOExportMaskVolumesValidator volumeValidator = (XtremIOExportMaskVolumesValidator) validator
                                .removeInitiators(ctx);
                        volumeValidator.setIgNames(groupInitiatorsByIG.keySet());
                        volumeValidator.validate();
                        List<Initiator> initiatorsToBeRemoved = new ArrayList<Initiator>();
                        // Get the context from the task completer, in case this is a rollback.
                        ExportOperationContext context = (ExportOperationContext) WorkflowService.getInstance()
                                .loadStepData(taskCompleter.getOpId());
                        if (context != null && context.getOperations() != null) {
                            ListIterator li = context.getOperations().listIterator(context.getOperations().size());
                            while (li.hasPrevious()) {
                                _log.info("Handling deleteExportMask as a result of rollback");
                                ExportOperationContextOperation operation = (ExportOperationContextOperation) li.previous();
                                if (operation != null
                                        && XtremIOExportOperationContext.OPERATION_ADD_INITIATORS_TO_INITIATOR_GROUP
                                                .equals(operation.getOperation())) {
                                    initiatorsToBeRemoved = (List<Initiator>) operation.getArgs().get(0);
                                    _log.info("Removing initiators {} as part of rollback", Joiner.on(',').join(initiatorsToBeRemoved));
                                }
                            }
                        } else {
                            initiatorsToBeRemoved = initiators;
                        }
                        // Deleting the initiator automatically removes the initiator from lun map
                        for (Initiator initiator : initiatorsToBeRemoved) {
                            try {
                                // check if Initiator has already been deleted during previous volume processing
                                String initiatorName = initiator.getMappedInitiatorName(storage.getSerialNumber());
                                XtremIOInitiator initiatorObj = client.getInitiator(initiatorName, xioClusterName);
                                if (null != initiatorObj) {
                                    client.deleteInitiator(initiatorName, xioClusterName);
                                } else {
                                    _log.info("Initiator {} already deleted", initiatorName);
                                }
                            } catch (Exception e) {
                                failedIGs.add(initiator.getLabel().concat(XtremIOConstants.DASH).concat(e.getMessage()));
                                _log.warn("Removal of Initiator {} from IG failed", initiator.getLabel(), e);
                            }
                        }
                    }
                } else {
                    exportMask.removeFromUserCreatedVolumes(blockObj);
                    exportMask.removeVolume(blockObj.getId());
                }
            }
            dbClient.updateObject(exportMask);

            if (!failedVolumes.isEmpty()) {
                String errMsg = "Export Operations failed for these volumes: ".concat(Joiner.on(", ").join(
                        failedVolumes));
                ServiceError serviceError = DeviceControllerException.errors.jobFailedMsg(
                        errMsg, null);
                taskCompleter.error(dbClient, serviceError);
                return;
            }
            if (!failedIGs.isEmpty()) {
                String errMsg = "Export Operations failed deleting these initiators: ".concat(Joiner.on(", ").join(
                        failedIGs));
                ServiceError serviceError = DeviceControllerException.errors.jobFailedMsg(errMsg, null);
                taskCompleter.error(dbClient, serviceError);
                return;
            }

            // Clean IGs if empty
            deleteInitiatorGroup(groupInitiatorsByIG, client, xioClusterName);
            // delete IG Folder as well if IGs are empty
            deleteInitiatorGroupFolder(client, xioClusterName, clusterName, hostName, storage);

            taskCompleter.ready(dbClient);
        } catch (Exception e) {
            _log.error(String.format("Export Operations failed - maskName: %s", exportMask.getId()
                    .toString()), e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }
    }

    private String getInitiatorGroupFolderName(String clusterName, String hostName, StorageSystem storage) {
        String igFolderName = "";
        if (clusterName != null && !clusterName.isEmpty()) {
            // cluster
            DataSource dataSource = dataSourceFactory.createXtremIOClusterInitiatorGroupFolderNameDataSource(
                    clusterName, storage);
            igFolderName = customConfigHandler.getComputedCustomConfigValue(
                    CustomConfigConstants.XTREMIO_CLUSTER_INITIATOR_GROUP_FOLDER_NAME, storage.getSystemType(), dataSource);
        } else {
            DataSource dataSource = dataSourceFactory.createXtremIOHostInitiatorGroupFolderNameDataSource(
                    hostName, storage);
            igFolderName = customConfigHandler.getComputedCustomConfigValue(
                    CustomConfigConstants.XTREMIO_HOST_INITIATOR_GROUP_FOLDER_NAME, storage.getSystemType(), dataSource);
        }

        return igFolderName;
    }

    private void addInitiatorToInitiatorGroup(XtremIOClient client, String xioClusterName,
            String clusterName, String hostName, List<Initiator> initiatorsToBeCreated,
            Set<String> igNames, ExportMask exportMask, StorageSystem storage, TaskCompleter taskCompleter)
                    throws Exception {
        XtremIOInitiatorGroup igGroup = null;
        // create initiator group folder and initiator group
        String igFolderName = getInitiatorGroupFolderName(clusterName, hostName, storage);

        if (null == client.getTagDetails(igFolderName, XTREMIO_ENTITY_TYPE.InitiatorGroup.name(), xioClusterName)) {
            _log.info("Creating IG Folder with name {}", igFolderName);
            client.createTag(igFolderName, null, XtremIOConstants.XTREMIO_ENTITY_TYPE.InitiatorGroup.name(), xioClusterName);
        }

        DataSource dataSource = dataSourceFactory.createXtremIOInitiatorGroupNameDataSource(
                hostName, storage);
        String igName = customConfigHandler.getComputedCustomConfigValue(
                CustomConfigConstants.XTREMIO_INITIATOR_GROUP_NAME, storage.getSystemType(), dataSource);
        igGroup = client.getInitiatorGroup(igName, xioClusterName);
        if (null == igGroup) {
            // create a new IG
            _log.info("Creating Initiator Group with name {}", igName);

            client.createInitiatorGroup(igName, igFolderName, xioClusterName);
            ExportOperationContext.insertContextOperation(taskCompleter, XtremIOExportOperationContext.OPERATION_CREATE_INITIATOR_GROUP,
                    igName);
            igGroup = client.getInitiatorGroup(igName, xioClusterName);
            if (null == igGroup) {
                _log.info("Neither IG is already present nor able to create on Array {}", hostName);
            } else {
                _log.info("Created Initiator Group {} with # initiators {}", igGroup.getName(),
                        igGroup.getNumberOfInitiators());
                igNames.add(igGroup.getName());
            }
        } else {
            igNames.add(igGroup.getName());
            _log.info("Found Initiator Group {} with # initiators {}", igGroup.getName(),
                    igGroup.getNumberOfInitiators());

        }

        // add all the left out initiators to this folder
        for (Initiator remainingInitiator : initiatorsToBeCreated) {
            _log.info("Initiator {} Label {} ", remainingInitiator.getInitiatorPort(),
                    remainingInitiator.getLabel());
            String initiatorName = ((null == remainingInitiator.getLabel() || remainingInitiator
                    .getLabel().isEmpty()) ? remainingInitiator.getInitiatorPort()
                            : remainingInitiator.getLabel());
            List<Initiator> createdInitiators = new ArrayList<Initiator>();
            _log.info("Initiator {}  ", initiatorName);
            try {
                String os = null;
                if (client.isVersion2() && !NullColumnValueGetter.isNullURI(remainingInitiator.getHost())) {
                    Host host = dbClient.queryObject(Host.class, remainingInitiator.getHost());
                    os = XtremIOProvUtils.getInitiatorHostOS(host);
                }
                // create initiator
                client.createInitiator(initiatorName, igGroup.getName(),
                        remainingInitiator.getInitiatorPort(), os, xioClusterName);
                createdInitiators.add(remainingInitiator);
                remainingInitiator.setLabel(initiatorName);
                remainingInitiator.mapInitiatorName(storage.getSerialNumber(), initiatorName);
                dbClient.updateObject(remainingInitiator);

            } catch (Exception e) {
                // assume initiator already part of another group look for
                // port_address_not_unique
                // CTRL-5956 - Few Initiators cannot be registered on XtremIO Array, throw exception even if one
                // initiator registration
                // fails.
                _log.warn("Initiator {} already available or not able to register the same on Array. Rediscover the Array and try again.",
                        remainingInitiator.getInitiatorPort());
                throw e;

            } finally {
                ExportOperationContext.insertContextOperation(taskCompleter,
                        XtremIOExportOperationContext.OPERATION_ADD_INITIATORS_TO_INITIATOR_GROUP,
                        createdInitiators);
            }
        }
    }

    private void runLunMapCreationAlgorithm(StorageSystem storage, ExportMask exportMask, VolumeURIHLU[] volumeURIHLUs,
            List<Initiator> initiators, List<URI> targets, XtremIOClient client, String xioClusterName,
            ArrayListMultimap<String, Initiator> initiatorToIGMap, List<Initiator> initiatorsToBeCreated, TaskCompleter taskCompleter)
                    throws DeviceControllerException {

        Set<String> igNames = null;
        List<URI> mappedVolumes = new ArrayList<URI>();
        // if host Name is not available in at least one of the initiator, then set it to
        // Default_IG;
        try {
            String hostName = null;
            String clusterName = null;

            _log.info("Finding re-usable IGs available on Array {}", storage.getNativeGuid());

            List<URI> initiatorIds = new ArrayList<>();
            for (Initiator initiator : initiators) {
                initiatorIds.add(initiator.getId());
                if (null != initiator.getHostName()) {
                    // initiators already grouped by Host
                    hostName = initiator.getHostName();
                    clusterName = initiator.getClusterName();
                    break;
                }
            }

            // since we're reusing existing IGs, volumes might get exposed to other initiators in
            // IG.

            // for initiators without label, create IGs and add them to a host folder by name
            // initiator.getHost() in cases ,where initiator.getLabel() is not present, we try
            // creating IG, if fails
            // then we consider that it's already created
            igNames = new HashSet<String>();
            igNames.addAll(initiatorToIGMap.keySet());
            if (initiatorsToBeCreated != null && !initiatorsToBeCreated.isEmpty()) {
                // create new initiator and add to IG; add IG to IG folder
                addInitiatorToInitiatorGroup(client, xioClusterName, clusterName, hostName,
                        initiatorsToBeCreated, igNames, exportMask, storage, taskCompleter);
                List<URI> volumeURIs = new ArrayList<URI>();
                for (VolumeURIHLU volURIHLU : volumeURIHLUs) {
                    volumeURIs.add(volURIHLU.getVolumeURI());
                }
                XtremIOExportMaskVolumesValidator volumeValidator = (XtremIOExportMaskVolumesValidator) validator.addInitiators(storage,
                        exportMask, volumeURIs);
                volumeValidator.setIgNames(initiatorToIGMap.keySet());
                volumeValidator.validate();
            }

            if (igNames.isEmpty()) {
                ServiceError serviceError = DeviceControllerException.errors.xtremioInitiatorGroupsNotDetected(storage.getNativeGuid());
                taskCompleter.error(dbClient, serviceError);
                return;
            }

            // Small block of declarations/initializations to assist with vblock boot volume check in below block.
            Set<String> vblockConflictIgNames = new HashSet<>();
            Map<URI, Integer> volumeMap = new HashMap<>();
            for (VolumeURIHLU volumeIdHlu : volumeURIHLUs) {
                volumeMap.put(volumeIdHlu.getVolumeURI(), Integer.valueOf(volumeIdHlu.getHLU()));
            }
            
            // Check for volumes which are already mapped to the IG. We do not need to create lun map for them.
            ArrayListMultimap<String, String> volumesToIGMap = ArrayListMultimap.create();
            for (String igName : igNames) {
                List<XtremIOVolume> igVols = XtremIOProvUtils.getInitiatorGroupVolumes(igName, xioClusterName, client);
                for (XtremIOVolume igVolume : igVols) {
                    volumesToIGMap.put(igName, igVolume.getVolInfo().get(1));
                    
                    /**
                     * COP-28674: During Vblock Boot volume export, if existing masking views are found then check for existing volumes
                     * If found throw exception. This condition is valid only for boot volume vblock export.
                     */
                    if (ExportMaskUtils.isVblockHost(initiatorIds, dbClient) && ExportMaskUtils.isBootVolume(dbClient, volumeMap)) {
                        _log.error(String.format("VBlock boot volume Export: found existing IG (%s) with volumes in it on %s.", igName,  
                                storage.getNativeGuid()));
                        vblockConflictIgNames.add(igName);
                    }
                }
            }

            // If we collected any conflicting IG names (because we're about to export a boot volume to a vblock host, and 
            // there are additional volumes also visible to any of the initiators associated with that host), post a verbose
            // error for the customer.
            if (!vblockConflictIgNames.isEmpty()) {
                ServiceError serviceError = DeviceControllerException.errors.existingMaskFoundDuringBootVolumeExportXio(
                        Joiner.on(',').join(vblockConflictIgNames), hostName); 
                taskCompleter.error(dbClient, serviceError);
                return;
            }
            
            InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_052);

            // create Lun Maps
            for (VolumeURIHLU volURIHLU : volumeURIHLUs) {
                BlockObject blockObj = BlockObject.fetch(dbClient, volURIHLU.getVolumeURI());
                String hluValue = volURIHLU.getHLU().equalsIgnoreCase(ExportGroup.LUN_UNASSIGNED_STR) ? "-1" : volURIHLU.getHLU();
                _log.info("HLU value {}", hluValue);
                for (String igName : igNames) {
                    List<String> igVols = volumesToIGMap.get(igName);
                    if (igVols != null && !igVols.contains(blockObj.getDeviceLabel())) {
                        // Create lun map
                        _log.info("Creating Lun Map for  Volume {} using IG {}", blockObj.getDeviceLabel(), igName);
                        client.createLunMap(blockObj.getDeviceLabel(), igName, hluValue, xioClusterName);
                        mappedVolumes.add(blockObj.getId());
                    }
                }
            }

            // post process created lun maps
            for (VolumeURIHLU volURIHLU : volumeURIHLUs) {
                BlockObject blockObj = BlockObject.fetch(dbClient, volURIHLU.getVolumeURI());
                Integer hluNumberFound = 0;
                // get volume/snap details again and populate wwn and hlu
                XtremIOVolume xtremIOVolume = null;
                String deviceName = blockObj.getDeviceLabel();
                xtremIOVolume = XtremIOProvUtils.isVolumeAvailableInArray(client, deviceName, xioClusterName);
                // COP-19828: If we can't find a volume by the given name, try to find a snap with the given name
                if (xtremIOVolume == null) {
                    xtremIOVolume = XtremIOProvUtils.isSnapAvailableInArray(client, deviceName, xioClusterName);
                }

                if (xtremIOVolume != null) {
                    _log.info("Volume lunMap details Found {}", xtremIOVolume.getLunMaps().toString());
                    if (!xtremIOVolume.getWwn().isEmpty()) {
                        blockObj.setWWN(xtremIOVolume.getWwn());
                        blockObj.setNativeId(xtremIOVolume.getWwn());
                        dbClient.updateObject(blockObj);
                    }

                    for (String igName : igNames) {
                        for (List<Object> lunMapEntries : xtremIOVolume.getLunMaps()) {
                            @SuppressWarnings("unchecked")
                            // This can't be null
                            List<Object> igDetails = (List<Object>) lunMapEntries.get(0);
                            if (null == igDetails.get(1) || null == lunMapEntries.get(2)) {
                                _log.warn("IG Name is null in returned lun map response for volume {}", xtremIOVolume.toString());
                                continue;
                            }
                            String igNameToProcess = (String) igDetails.get(1);

                            _log.info("IG Name: {} found in Lun Map", igNameToProcess);
                            if (!igName.equalsIgnoreCase(igNameToProcess)) {
                                _log.info(
                                        "Volume is associated with IG {} which is not in the expected list requested, ignoring..",
                                        igNameToProcess);
                                continue;
                            }

                            Double hluNumber = (Double) lunMapEntries.get(2);
                            _log.info("Found HLU {} for volume {}", hluNumber, blockObj.getDeviceLabel());
                            // for each IG involved, the same volume is visible thro different HLUs.
                            // TODO we might need a list of HLU for each Volume URI
                            hluNumberFound = hluNumber.intValue();
                            exportMask.addVolume(blockObj.getId(), hluNumberFound);
                            exportMask.addToUserCreatedVolumes(blockObj);

                        }
                    }
                }
            }
            InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_053);
            ExportOperationContext.insertContextOperation(taskCompleter,
                    XtremIOExportOperationContext.OPERATION_ADD_VOLUMES_TO_INITIATOR_GROUP,
                    mappedVolumes);
            _log.info("Updated Volumes with HLUs {} after successful export",
                    Joiner.on(",").join(exportMask.getVolumes().entrySet()));
            dbClient.updateObject(exportMask);
            // Test mechanism to invoke a failure. No-op on production systems.
            InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_003);
            InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_002);
            taskCompleter.ready(dbClient);
        } catch (Exception e) {
            _log.error(String.format("Export Operations failed - maskName: %s", exportMask.getId()
                    .toString()), e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(dbClient, serviceError);
        }
    }

    private void deleteInitiatorGroupFolder(XtremIOClient client, String xioClusterName, String clusterName, String hostName,
            StorageSystem system) throws Exception {
        String tempIGFolderName = getInitiatorGroupFolderName(clusterName, hostName, system);
        XtremIOTag igFolder = client.getTagDetails(tempIGFolderName, XTREMIO_ENTITY_TYPE.InitiatorGroup.name(), xioClusterName);

        if (null != igFolder && "0".equalsIgnoreCase(igFolder.getNumberOfDirectObjs())) {
            try {
                _log.info("# of IGs  {} in Folder {}", igFolder.getNumberOfDirectObjs(), clusterName);
                client.deleteTag(tempIGFolderName, XtremIOConstants.XTREMIO_ENTITY_TYPE.InitiatorGroup.name(), xioClusterName);
            } catch (Exception e) {
                if (null != e.getMessage() && !e.getMessage().contains(XtremIOConstants.OBJECT_NOT_FOUND)) {
                    _log.warn("Deleting Initatiator Group Folder {} failed with exception {}", tempIGFolderName, e.getMessage());
                    throw e;
                } else {
                    _log.warn("Initatiator Group Folder {} not found. Might be already deleted.", tempIGFolderName);
                }
            }
        }
    }

    private void deleteInitiatorGroup(ArrayListMultimap<String, Initiator> groupInitiatorsByIG,
            XtremIOClient client, String xioClusterName) throws Exception {
        for (Entry<String, Collection<Initiator>> entry : groupInitiatorsByIG.asMap().entrySet()) {
            String igName = entry.getKey();
            try {
                // find # initiators for this IG
                XtremIOInitiatorGroup ig = client.getInitiatorGroup(igName, xioClusterName);
                if (ig != null) {
                    int numberOfVolumes = Integer.parseInt(ig.getNumberOfVolumes());
                    _log.info("Initiator Group {} left with Volume size {}", igName, numberOfVolumes);
                    if (numberOfVolumes == 0) {
                        // delete Initiator Group
                        client.deleteInitiatorGroup(igName, xioClusterName);
                        // remove export mask from export groip

                    } else {
                        _log.info("Skipping IG Group {} deletion", igName);
                    }
                }
            } catch (Exception e) {
                if (null != e.getMessage() && !e.getMessage().contains(XtremIOConstants.OBJECT_NOT_FOUND)) {
                    _log.warn("Deleting Initatiator Group {} failed with exception {}", igName, e.getMessage());
                    throw e;
                } else {
                    _log.warn("Initatiator Group {} not found. Might be already deleted.", igName);
                }
            }
        }
    }
    
    /**
     * This method provides an easy way to get all the related export mask volumes for an initiator so that
     * validation can be done properly for the XIO IG. 
     * 
     * The main export mask doesn't necessarily tell us all the volumes that are present on the XIO IG. Just the
     * volumes exposed for that single mask.
     * 
     * To find that out if there are more volumes exposed to the XIO IG we need a common initiator to find 
     * if there are any other export masks that have other user added volumes present. If there are, collect 
     * all those related volumes so that they can be used for validation.          
     * 
     * @param initiators The initiator in question
     * @param storageSystemURI URI of the storage system to focus on
     * @return A set of related volumes, empty set if none are found.
     */
    private Set<URI> findAllRelatedExportMaskVolumesForInitiator(List<Initiator> initiators, URI storageSystemURI) {
        Set<URI> volumes = new HashSet<URI>();
        
        if (initiators != null && storageSystemURI != null) {
            for (Initiator init : initiators) {
                // Get all export masks that contain the initiator 
                URIQueryResultList relatedExportMasks = new URIQueryResultList();
                dbClient.queryByConstraint(
                        AlternateIdConstraint.Factory.getExportMaskInitiatorConstraint(init.getId().toString()), 
                        relatedExportMasks);
                
                for (URI relatedExportMaskURI : relatedExportMasks) {
                    // The related export mask contains the initiator in question based on the initial query constraint, 
                    // so if the related export mask is NOT the current export mask and is for the same storage device 
                    // then we need to collect the user added volumes.               
                    ExportMask relatedExportMask = dbClient.queryObject(ExportMask.class, relatedExportMaskURI);                    
                    if (relatedExportMask != null 
                            && storageSystemURI.equals(relatedExportMask.getStorageDevice())) {
                        if (relatedExportMask.getUserAddedVolumes() != null) {
                            // Get the user added volumes from the related export mask and transform them into URIs
                            Collection<URI> vols = Collections2.transform(relatedExportMask.getUserAddedVolumes().values(),
                                    CommonTransformerFunctions.FCTN_STRING_TO_URI);                
                            // Keep track of the user added volumes
                            volumes.addAll(vols);
                        }
                    }                
                }
            }
        }
        
        if (!CollectionUtils.isEmpty(volumes)) {
            _log.info(String.format("Volumes across related export masks for XIO "
                    + "[%s] using initiators [%s] - volumes: [%s]", 
                    storageSystemURI,
                    Joiner.on(',').join(initiators),
                    Joiner.on(',').join(volumes)));
        }
        
        return volumes;
    }

    @Override
    public void updateStorageGroupPolicyAndLimits(StorageSystem storage, ExportMask exportMask,
            List<URI> volumeURIs, VirtualPool newVirtualPool, boolean rollback,
            TaskCompleter taskCompleter) throws Exception {
    }

    @Override
    public Map<URI, Integer> getExportMaskHLUs(StorageSystem storage, ExportMask exportMask) {
        return Collections.emptyMap();
    }
    
    @Override
    public void addPaths(StorageSystem storage, URI exportMask, Map<URI, List<URI>> newPaths, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void removePaths(StorageSystem storage, URI exportMask, Map<URI, List<URI>> adjustedPaths, Map<URI, List<URI>> removePaths, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void updatePerformancePolicy(StorageSystem storageSystem, ExportMask exportMask, List<URI> volumeURIs,
            PerformancePolicy newPerfPolicy, boolean isRollback, TaskCompleter taskCompleter) throws Exception {   
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();        
    }
}
