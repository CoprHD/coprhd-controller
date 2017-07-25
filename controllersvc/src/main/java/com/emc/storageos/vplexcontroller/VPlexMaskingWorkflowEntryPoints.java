package com.emc.storageos.vplexcontroller;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.Controller;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.networkcontroller.impl.NetworkDeviceController;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskAddInitiatorCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskAddVolumeCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskCreateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskDeleteCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskRemoveInitiatorCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskRemoveVolumeCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportTaskCompleter;
import com.emc.storageos.volumecontroller.impl.utils.ExportOperationContext;
import com.emc.storageos.volumecontroller.impl.utils.ExportOperationContext.ExportOperationContextOperation;
import com.emc.storageos.volumecontroller.placement.BlockStorageScheduler;
import com.emc.storageos.vplex.api.VPlexApiException;
import com.emc.storageos.vplexcontroller.utils.VPlexControllerUtils;
import com.emc.storageos.vplexcontroller.utils.VplexExportOperationContext;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowException;
import com.emc.storageos.workflow.WorkflowService;
import com.emc.storageos.workflow.WorkflowStepCompleter;
import com.google.common.base.Joiner;

public class VPlexMaskingWorkflowEntryPoints  implements Controller{
	
	private static final Logger _log = LoggerFactory.getLogger(VPlexMaskingWorkflowEntryPoints.class);
    private static volatile String _beanName;
    private NetworkDeviceController _networkDeviceController;
    private DbClient _dbClient;
    private BlockStorageScheduler _blockScheduler;
    private VPlexExportOperations vplexExportOperationsHelper;
	
    public void setName(String beanName) {
        _beanName = beanName;
    }
    
    public void setDbClient(DbClient dbc) {
        _dbClient = dbc;
    }

    public void setNetworkDeviceController(NetworkDeviceController networkDeviceController) {
        _networkDeviceController = networkDeviceController;
    }

    public void setBlockScheduler(BlockStorageScheduler storageScheduler) {
        _blockScheduler = storageScheduler;
    }
    
    public void setVplexExportOperations(VPlexExportOperations exportOperationsHelper) {
    	vplexExportOperationsHelper = exportOperationsHelper;
    }
    
	public static VPlexMaskingWorkflowEntryPoints getInstance() {
        return (VPlexMaskingWorkflowEntryPoints) ControllerServiceImpl.getBean(_beanName);
    }
	
	public void registerInitiators(List<URI> initiatorUris, URI vplexURI, String vplexClusterName, String stepId) 
			throws ControllerException {
		String call = String.format("registerInitiators([%s], %s)",
                initiatorUris != null ? Joiner.on(',').join(initiatorUris) : "No Initiators",
                vplexClusterName);
        try { 
        	_log.info(String.format("%s start", call));
        	WorkflowStepCompleter.stepExecuting(stepId);        	
            vplexExportOperationsHelper.registerInitiators(initiatorUris, vplexURI, vplexClusterName);     
            _log.info(String.format("%s end", call));
        } catch (final Exception e) {
            _log.info(call + " Encountered an exception", e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(stepId, serviceError);
        }
	}
	
	public void createStorageView(URI vplexURI, URI exportURI, URI exportMaskURI, Map<URI, Integer> blockObjectMap,
            List<URI> initiators, List<URI> targets, String stepId) throws ControllerException {
		
		String call = String.format("createStorageView(%s, %s, %s, [%s], [%s], [%s])",
				vplexURI.toString(),
				exportURI.toString(),
				exportMaskURI.toString(),
				blockObjectMap != null ? Joiner.on(',').withKeyValueSeparator("=").join(blockObjectMap) : "No Block Objects",
				initiators != null ? Joiner.on(',').join(initiators) : "No Initiators",
				targets != null ? Joiner.on(',').join(targets) : "No targets");
		
		try {
			_log.info(String.format("%s start", call));
            WorkflowStepCompleter.stepExecuting(stepId);           
            ExportMaskCreateCompleter exportTaskCompleter = new ExportMaskCreateCompleter(exportURI, exportMaskURI, initiators, blockObjectMap,
            		stepId);
            vplexExportOperationsHelper.createStorageView(vplexURI, exportURI, exportMaskURI, blockObjectMap, initiators, targets, exportTaskCompleter);
            _log.info(String.format("%s end", call));           
		} catch (final Exception e) {
            _log.info(call + " Encountered an exception", e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(stepId, serviceError);
        }
            
	}
	
	public void rollbackCreateStorageView(URI vplexURI, URI exportGroupURI, URI exportMaskURI, String stepId)
            throws ControllerException {
		String call = String.format("rollbackCreateStorageView(%s, %s, %s)",
				vplexURI.toString(),
				exportGroupURI.toString(),
				exportMaskURI.toString());
		
		ExportMaskDeleteCompleter completer = null;
        try {
        	_log.info(String.format("%s start", call));
            WorkflowStepCompleter.stepExecuting(stepId);
            completer = new ExportMaskDeleteCompleter(exportGroupURI, exportMaskURI, stepId);
            completer.setRollingBack(true);            
            vplexExportOperationsHelper.deleteStorageView(vplexURI, exportGroupURI, exportMaskURI, completer, stepId);
            _log.info(String.format("%s end", call));
        } catch (final DeviceControllerException ex) {
        	_log.error(call + " Encountered an exception", ex);
            VPlexControllerUtils.failStep(completer, stepId, ex, _dbClient);
        } catch (final Exception ex) {
        	_log.error(call + " Encountered an exception", ex);
            ServiceError svcError = VPlexApiException.errors.deleteStorageViewFailed(ex);
            VPlexControllerUtils.failStep(completer, stepId, svcError, _dbClient);
        }
	}
	
	
	public void deleteStorageView(URI vplexURI, URI exportGroupURI, URI exportMaskURI, String stepId)
            throws ControllerException {
		
		String call = String.format("deleteStorageView(%s, %s, %s)",
				vplexURI.toString(),
				exportGroupURI.toString(),
				exportMaskURI.toString());
		ExportMaskDeleteCompleter completer = null;
        try {
        	_log.info(String.format("%s start", call));
            WorkflowStepCompleter.stepExecuting(stepId);
            completer = new ExportMaskDeleteCompleter(exportGroupURI, exportMaskURI, stepId);
            completer.setRollingBack(false);            
            vplexExportOperationsHelper.deleteStorageView(vplexURI, exportGroupURI, exportMaskURI, completer, stepId);
            _log.info(String.format("%s end", call));
        } catch (final DeviceControllerException ex) {
        	_log.error(call + " Encountered an exception", ex);
            VPlexControllerUtils.failStep(completer, stepId, ex, _dbClient);
        } catch (final Exception ex) {
        	_log.error(call + " Encountered an exception", ex);
            ServiceError svcError = VPlexApiException.errors.deleteStorageViewFailed(ex);
            VPlexControllerUtils.failStep(completer, stepId, svcError, _dbClient);
        }
            
	}
	
	public void storageViewAddInitiators(URI vplexURI, URI exportURI, URI maskURI,
            List<URI> initiatorURIs,
            List<URI> targetURIs, boolean sharedExportMask,
            ExportMaskAddInitiatorCompleter completer,
            String stepId) throws ControllerException {
		
		String call = String.format("storageViewAddInitiators(%s, %s, %s, [%s], [%s], %s, %s)",
				vplexURI.toString(),
				exportURI.toString(),
				maskURI.toString(),
				initiatorURIs != null ? Joiner.on(',').join(initiatorURIs) : "No Initiators",
				targetURIs != null ? Joiner.on(',').join(targetURIs) : "No targets",
				sharedExportMask, completer.getOpId());
		
		try {
        	_log.info(String.format("%s start", call));        	
            WorkflowStepCompleter.stepExecuting(stepId);            
            vplexExportOperationsHelper.storageViewAddInitiators(vplexURI, exportURI, maskURI, initiatorURIs, targetURIs, sharedExportMask, completer, stepId);            
            _log.info(String.format("%s end", call));
        } catch (VPlexApiException vae) {
            _log.error("VPlexApiException adding initiator to Storage View: " + vae.getMessage(), vae);
            VPlexControllerUtils.failStep(completer, stepId, vae, _dbClient);
        } catch (Exception ex) {
            _log.error("Exception adding initiator to Storage View: " + ex.getMessage(), ex);
            String opName = ResourceOperationTypeEnum.ADD_STORAGE_VIEW_INITIATOR.getName();
            ServiceError serviceError = VPlexApiException.errors.storageViewAddInitiatorFailed(opName, ex);
            VPlexControllerUtils.failStep(completer, stepId, serviceError, _dbClient);
        }
		
	}
	
	/**
     * Rollback entry point. This is a wrapper around the storageViewRemoveInitiators
     * operation, which requires that we perform rollback of only the operations we successfully performed 
     *
     * @param vplexURI
     *            [in] - StorageSystem URI
     * @param exportGroupURI
     *            [in] - ExportGroup URI
     * @param exportMaskURI
     *            [in] - ExportMask URI
     * @param initiatorURIs
     *            [in] - List of Initiator URIs
     * @param targetURIs
     *            [in] - List of storage port URIs
     * @param rollbackContextKey
     *            [in] - context token
     * @param token
     *            [in] - String token generated by the rollback processing
     * @throws ControllerException
     */
	public void storageViewAddInitiatorsRollback(URI vplexURI, URI exportGroupURI,
            URI exportMaskURI, List<URI> initiatorURIs, List<URI> targetURIs,
            String rollbackContextKey, String token) throws ControllerException {
		
		String call = String.format("storageViewAddInitiatorsRollback(%s, %s, %s, [%s], [%s], %s)",
				vplexURI.toString(),
				exportGroupURI.toString(),
				exportMaskURI.toString(),
				initiatorURIs != null ? Joiner.on(',').join(initiatorURIs) : "No Initiators",
				targetURIs != null ? Joiner.on(',').join(targetURIs) : "No targets",
				rollbackContextKey);
		
		ExportTaskCompleter taskCompleter = new ExportMaskRemoveInitiatorCompleter(exportGroupURI, exportMaskURI,
                initiatorURIs, token);
		try {
			_log.info(String.format("%s start", call));        	
            WorkflowStepCompleter.stepExecuting(token);
            
	        // Take the context of the step in flight and feed it into our current step
	        // in order to only perform rollback of operations we successfully performed.
	        List<URI> initiatorIdsToProcess = new ArrayList<>(initiatorURIs);        
	        try {
	        	ExportOperationContext context = (ExportOperationContext) WorkflowService.getInstance().loadStepData(rollbackContextKey);
	            if (context != null) {
	                // a non-null context means this step is running as part of a rollback.
	                List<URI> addedInitiators = new ArrayList<>();
	                if (context.getOperations() != null) {
	                    _log.info("Handling removeInitiators as a result of rollback");
	                    ListIterator<ExportOperationContextOperation> li = context.getOperations()
	                            .listIterator(context.getOperations().size());
	                    while (li.hasPrevious()) {
	                        ExportOperationContextOperation operation = (ExportOperationContextOperation) li.previous();
	                        if (operation != null
	                                && VplexExportOperationContext.OPERATION_ADD_INITIATORS_TO_STORAGE_VIEW
	                                        .equals(operation.getOperation())) {
	                            addedInitiators = (List<URI>) operation.getArgs().get(0);
	                            _log.info("Removing initiators {} as part of rollback", Joiner.on(',').join(addedInitiators));
	                        }
	                    }
	
	                    if (addedInitiators == null || addedInitiators.isEmpty()) {
	                        _log.info("There was no context found for add initiator. So there is nothing to rollback.");
	                        WorkflowStepCompleter.stepSucceded(token);
	                        return;
	                    }
	                }
	                // Change the list of initiators to process to the list 
	                // that successfully were added during addInitiators.
	                initiatorIdsToProcess.clear();
	                initiatorIdsToProcess.addAll(addedInitiators);
	            }
	            
	        } catch (ClassCastException e) {
	            _log.info("Step {} has stored step data other than ExportOperationContext. Exception: {}", token, e);
	        }
	        
	        vplexExportOperationsHelper.storageViewRemoveInitiators(vplexURI, exportGroupURI, exportMaskURI, initiatorIdsToProcess, 
	        		targetURIs, taskCompleter, token);
	        
	        _log.info(String.format("%s end", call));
		} catch (VPlexApiException vae) {
            _log.error("Exception removing initiator from Storage View: " + vae.getMessage(), vae);
            VPlexControllerUtils.failStep(taskCompleter, token, vae, _dbClient);
        } catch (Exception ex) {
            _log.error("Exception removing initiator from Storage View: " + ex.getMessage(), ex);
            String opName = ResourceOperationTypeEnum.DELETE_STORAGE_VIEW_INITIATOR.getName();
            ServiceError serviceError = VPlexApiException.errors.storageViewRemoveInitiatorFailed(opName, ex);
            VPlexControllerUtils.failStep(taskCompleter, token, serviceError, _dbClient);
        }                
	}
	
	public void storageViewRemoveInitiators(URI vplexURI, URI exportGroupURI, URI exportMaskURI, List<URI> initiatorURIs, 
			List<URI> targetURIs, String token) throws ControllerException {
		
		String call = String.format("storageViewRemoveInitiators(%s, %s, %s, [%s], [%s])",
				vplexURI.toString(),
				exportGroupURI.toString(),
				exportMaskURI.toString(),
				initiatorURIs != null ? Joiner.on(',').join(initiatorURIs) : "No Initiators",
				targetURIs != null ? Joiner.on(',').join(targetURIs) : "No targets");
		
		ExportTaskCompleter taskCompleter = new ExportMaskRemoveInitiatorCompleter(exportGroupURI, exportMaskURI,
                initiatorURIs, token);
		try {
			_log.info(String.format("%s start", call));        	
            WorkflowStepCompleter.stepExecuting(token);            
            vplexExportOperationsHelper.storageViewRemoveInitiators(vplexURI, exportGroupURI, exportMaskURI, initiatorURIs, 
	        		targetURIs, taskCompleter, token);           
            _log.info(String.format("%s end", call));
		} catch (VPlexApiException vae) {
            _log.error("Exception removing initiator from Storage View: " + vae.getMessage(), vae);
            VPlexControllerUtils.failStep(taskCompleter, token, vae, _dbClient);
        } catch (Exception ex) {
            _log.error("Exception removing initiator from Storage View: " + ex.getMessage(), ex);
            String opName = ResourceOperationTypeEnum.DELETE_STORAGE_VIEW_INITIATOR.getName();
            ServiceError serviceError = VPlexApiException.errors.storageViewRemoveInitiatorFailed(opName, ex);
            VPlexControllerUtils.failStep(taskCompleter, token, serviceError, _dbClient);
        }
		
	}
	
	public void storageViewAddVolumes(URI vplexURI, URI exportGroupURI, URI exportMaskURI,
            Map<URI, Integer> volumeMap, String opId) throws ControllerException {
		
		String call = String.format("storageViewAddVolumes(%s, %s, %s, [%s])",
				vplexURI.toString(),
				exportGroupURI.toString(),
				exportMaskURI.toString(),
				volumeMap != null ? Joiner.on(',').withKeyValueSeparator("=").join(volumeMap) : "No volumes");
		String volListStr = "";
		ExportMaskAddVolumeCompleter completer = new ExportMaskAddVolumeCompleter(exportGroupURI, exportMaskURI, volumeMap, opId);
		
		try {
			_log.info(String.format("%s start", call));
			WorkflowStepCompleter.stepExecuting(opId);
			volListStr = Joiner.on(',').join(volumeMap.keySet());           
            vplexExportOperationsHelper.storageViewAddVolumes(vplexURI, exportGroupURI, exportMaskURI, volumeMap, completer, opId);            
            _log.info(String.format("%s end", call));
            
		} catch (VPlexApiException vae) {
            String message = String.format("Failed to add Volumes %s to ExportMask %s",
                    volListStr, exportMaskURI);
            _log.error(message, vae);
            VPlexControllerUtils.failStep(completer, opId, vae, _dbClient);
        } catch (Exception ex) {
            String message = String.format("Failed to add Volumes %s to ExportMask %s",
                    volListStr, exportMaskURI);
            _log.error(message, ex);
            String opName = ResourceOperationTypeEnum.ADD_EXPORT_VOLUME.getName();
            ServiceError serviceError = VPlexApiException.errors.exportGroupAddVolumesFailed(
                    volListStr, exportGroupURI.toString(), opName, ex);
            VPlexControllerUtils.failStep(completer, opId, serviceError, _dbClient);
        }
		
	}
	
	public void storageViewAddVolumesRollback(URI vplexURI, URI exportGroupURI,
            URI exportMaskURI, List<URI> volumeURIs,
            String rollbackContextKey, String token) throws ControllerException {
		
		TaskCompleter taskCompleter = new ExportMaskRemoveVolumeCompleter(exportGroupURI, exportMaskURI,
                volumeURIs, token);
		
		String call = String.format("storageViewAddVolumesRollback(%s, %s, %s, [%s])",
				vplexURI.toString(),
				exportGroupURI.toString(),
				exportMaskURI.toString(),
				volumeURIs != null ? Joiner.on(',').join(volumeURIs) : "No Volumes");
        // Take the context of the step in flight and feed it into our current step
        // in order to only perform rollback of operations we successfully performed.
        try {
        	_log.info(String.format("%s start", call));       	
        	WorkflowStepCompleter.stepExecuting(token);
        	
            ExportMask mask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
            List<URI> volumeIdsToProcess = new ArrayList<>(volumeURIs);

            // get the context from the task completer, in case this is a rollback.
            ExportOperationContext context = (ExportOperationContext) WorkflowService.getInstance().loadStepData(rollbackContextKey);
                
            if (context != null) {
                // a non-null context means this step is running as part of a rollback.
                List<URI> addedVolumes = new ArrayList<>();
                if (context.getOperations() != null) {
                    _log.info("Handling removeVolumes as a result of rollback");
                    ListIterator<ExportOperationContextOperation> li = context.getOperations()
                            .listIterator(context.getOperations().size());
                    while (li.hasPrevious()) {
                        ExportOperationContextOperation operation = (ExportOperationContextOperation) li.previous();
                        if (operation != null
                                && VplexExportOperationContext.OPERATION_ADD_VOLUMES_TO_STORAGE_VIEW.equals(operation.getOperation())) {
                            addedVolumes = (List<URI>) operation.getArgs().get(0);
                            _log.info("Removing volumes {} as part of rollback", Joiner.on(',').join(addedVolumes));
                        }
                    }

                    if (addedVolumes == null || addedVolumes.isEmpty()) {
                        _log.info("There was no context found for add volumes. So there is nothing to rollback.");
                        WorkflowStepCompleter.stepSucceded(token);
                        return;
                    }
                }
                // change the list of initiators to process to the 
                // list that successfully were added during addInitiators.
                volumeIdsToProcess.clear();
                volumeIdsToProcess.addAll(addedVolumes);
            }
            
            vplexExportOperationsHelper.storageViewRemoveVolumes(vplexURI, mask, volumeIdsToProcess, taskCompleter, token);            
            _log.info(String.format("%s end", call));
            
        } catch (Exception e) {
            String message = String.format("Failed to remove Volume(s) %s on rollback from ExportGroup %s",
                    Joiner.on(",").join(volumeURIs), exportGroupURI);
            _log.error(message, e);
            String opName = ResourceOperationTypeEnum.DELETE_EXPORT_VOLUME.getName();
            ServiceError serviceError = VPlexApiException.errors.exportGroupRemoveVolumesFailed(
                    Joiner.on(",").join(volumeURIs), exportGroupURI.toString(), opName, e);
            taskCompleter.error(_dbClient, serviceError);
        }
	}

	public void storageViewRemoveVolumes(URI vplexURI, URI exportGroupURI, URI exportMaskURI, List<URI> volumeURIs,
            String token) throws ControllerException {
		
		TaskCompleter taskCompleter = new ExportMaskRemoveVolumeCompleter(exportGroupURI, exportMaskURI,
                volumeURIs, token);
		
		String call = String.format("storageViewRemoveVolumes(%s, %s, %s, [%s])",
				vplexURI.toString(),
				exportGroupURI.toString(),
				exportMaskURI.toString(),
				volumeURIs != null ? Joiner.on(',').join(volumeURIs) : "No Volumes");
		
		ExportMask mask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
        
		try {
			_log.info(String.format("%s start", call));
        	WorkflowStepCompleter.stepExecuting(token);
            vplexExportOperationsHelper.storageViewRemoveVolumes(vplexURI, mask, volumeURIs, taskCompleter, token);           
            _log.info(String.format("%s end", call));
		} catch (VPlexApiException vae) {
            _log.error("Exception removing volumes from Storage View: " + vae.getMessage(), vae);
            VPlexControllerUtils.failStep(taskCompleter, token, vae, _dbClient);
        } catch (Exception ex) {
            _log.error("Exception removing volumes from Storage View: " + ex.getMessage(), ex);
            String opName = ResourceOperationTypeEnum.REMOVE_STORAGE_VIEW_VOLUME.getName();
            ServiceError serviceError = VPlexApiException.errors.storageViewRemoveVolumeFailed(mask.getMaskName(), opName, ex);
            VPlexControllerUtils.failStep(taskCompleter, token, serviceError, _dbClient);
        }
	
	}
	
	public void storageViewAddStoragePorts(URI vplexURI, URI exportURI, URI maskURI, List<URI> targetURIs, String stepId) 
			throws ControllerException {
		
		String call = String.format("storageViewAddStoragePorts(%s, %s, %s, [%s])",
				vplexURI.toString(),
				exportURI.toString(),
				maskURI.toString(),
				targetURIs != null ? Joiner.on(',').join(targetURIs) : "No Storage Ports");
		
		ExportMaskAddInitiatorCompleter completer = new ExportMaskAddInitiatorCompleter(exportURI, maskURI,
                new ArrayList<URI>(), targetURIs, stepId);		
		try {
			_log.info(String.format("%s start", call));			
            WorkflowStepCompleter.stepExecuting(stepId);
            vplexExportOperationsHelper.storageViewAddStoragePorts(vplexURI, exportURI, maskURI, targetURIs, completer, stepId);
            _log.info(String.format("%s end", call));
		} catch (VPlexApiException vae) {
            _log.error("VPlexApiException adding storagePorts to Storage View: " + vae.getMessage(), vae);
            VPlexControllerUtils.failStep(completer, stepId, vae, _dbClient);
        } catch (Exception ex) {
            _log.error("Exception adding storagePorts to Storage View: " + ex.getMessage(), ex);
            String opName = ResourceOperationTypeEnum.ADD_STORAGE_VIEW_STORAGEPORTS.getName();
            ServiceError serviceError = VPlexApiException.errors.storageViewAddStoragePortFailed(opName, ex);
            VPlexControllerUtils.failStep(completer, stepId, serviceError, _dbClient);
        }
		
	}
	
	public void storageViewRemoveStoragePorts(URI vplexURI, URI exportURI, URI maskURI, List<URI> targetURIs, String stepId) 
			throws ControllerException {
		
		String call = String.format("storageViewRemoveStoragePorts(%s, %s, %s, [%s])",
				vplexURI.toString(),
				exportURI.toString(),
				maskURI.toString(),
				targetURIs != null ? Joiner.on(',').join(targetURIs) : "No Storage Ports");
		
		TaskCompleter taskCompleter = new ExportMaskRemoveInitiatorCompleter(exportURI, maskURI, new ArrayList<URI>(), stepId);
		
		try {
			_log.info(String.format("%s start", call));			
            WorkflowStepCompleter.stepExecuting(stepId);
            vplexExportOperationsHelper.storageViewRemoveStoragePorts(vplexURI, exportURI, maskURI, targetURIs, taskCompleter, stepId);
            _log.info(String.format("%s end", call));
		} catch (VPlexApiException vae) {
            _log.error("Exception removing storage ports from Storage View: " + vae.getMessage(), vae);
            VPlexControllerUtils.failStep(taskCompleter, stepId, vae, _dbClient);
        } catch (Exception ex) {
            _log.error("Exception removing storage ports from Storage View: " + ex.getMessage(), ex);
            String opName = ResourceOperationTypeEnum.DELETE_STORAGE_VIEW_STORAGEPORTS.getName();
            ServiceError serviceError = VPlexApiException.errors.storageViewRemoveStoragePortFailed(opName, ex);
            VPlexControllerUtils.failStep(taskCompleter, stepId, serviceError, _dbClient);
        }
	}

    public void rollbackMethodNull(String stepId) throws ControllerException {
        WorkflowStepCompleter.stepSucceded(stepId);
    }
}
