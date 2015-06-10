/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.blockorchestrationcontroller;

import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.Controller;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.protectioncontroller.impl.recoverpoint.RPDeviceController;
import com.emc.storageos.srdfcontroller.SRDFDeviceController;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.ControllerLockingService;
import com.emc.storageos.volumecontroller.impl.block.BlockDeviceController;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeCreateWorkflowCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeVarrayChangeTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeVpoolChangeTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeWorkflowCompleter;
import com.emc.storageos.vplexcontroller.VPlexDeviceController;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowException;
import com.emc.storageos.workflow.WorkflowService;

public class BlockOrchestrationDeviceController implements BlockOrchestrationController, Controller {
    private static final Logger s_logger = LoggerFactory.getLogger(BlockOrchestrationDeviceController.class);
    private WorkflowService _workflowService;
    private static DbClient s_dbClient;
    private static BlockDeviceController _blockDeviceController;
    private static VPlexDeviceController _vplexDeviceController;
    private static RPDeviceController    _rpDeviceController;
    private static SRDFDeviceController _srdfDeviceController;
    private ControllerLockingService _locker;

    static final String CREATE_VOLUMES_WF_NAME = "CREATE_VOLUMES_WORKFLOW";
    static final String DELETE_VOLUMES_WF_NAME = "DELETE_VOLUMES_WORKFLOW";
    static final String EXPAND_VOLUMES_WF_NAME = "EXPAND_VOLUMES_WORKFLOW";
    static final String CHANGE_VPOOL_WF_NAME = "CHANGE_VPOOL_WORKFLOW";
    static final String CHANGE_VARRAY_WF_NAME = "CHANGE_VARRAY_WORKFLOW";
    
    /* (non-Javadoc)
     * @see com.emc.storageos.blockorchestrationcontroller.BlockOrchestrationController#createVolumes(java.util.List, java.lang.String)
     */
    @Override
    public void createVolumes(List<VolumeDescriptor> volumes, String taskId) throws ControllerException {
        List<URI> volUris = VolumeDescriptor.getVolumeURIs(volumes);
        VolumeCreateWorkflowCompleter completer = new VolumeCreateWorkflowCompleter(volUris, taskId, volumes);
        try {
            // Generate the Workflow.
            Workflow workflow = _workflowService.getNewWorkflow(this,
                    CREATE_VOLUMES_WF_NAME, true, taskId);
            String waitFor = null;    // the wait for key returned by previous call
            
            s_logger.info("Generating steps for create Volume");
            // First, call the BlockDeviceController to add its methods.
            waitFor = _blockDeviceController.addStepsForCreateVolumes(
                    workflow, waitFor, volumes, taskId);
            
            s_logger.info("Checking for SRDF steps");
            // Call the SRDFDeviceController to add its methods if there are SRDF volumes.
            waitFor = _srdfDeviceController.addStepsForCreateVolumes(
                    workflow, waitFor, volumes, taskId);
            
            s_logger.info("Checking for VPLEX steps");
            // Call the VPlexDeviceController to add its methods if there are VPLEX volumes.
            waitFor = _vplexDeviceController.addStepsForCreateVolumes(
                    workflow, waitFor, volumes, taskId);
            
            s_logger.info("Checking for RP steps");
            // Call the RPDeviceController to add its methods if there are RP protections
            waitFor = _rpDeviceController.addStepsForCreateVolumes(
                    workflow, waitFor, volumes, taskId);
            
            // Finish up and execute the plan.
            // The Workflow will handle the TaskCompleter
            String successMessage = "Create volumes successful for: " + volUris.toString();
            Object[] callbackArgs = new Object[] { volUris };
            workflow.executePlan(completer, successMessage, new WorkflowCallback(), callbackArgs, null, null);
        } catch (Exception ex) {
            s_logger.error("Could not create volumes: " + volUris, ex);
            String opName = ResourceOperationTypeEnum.CREATE_BLOCK_VOLUME.getName();
            ServiceError serviceError = DeviceControllerException.errors.createVolumesFailed(
                    volUris.toString(), opName, ex);
            completer.error(s_dbClient, _locker, serviceError);
        }
    }
    
    @SuppressWarnings("serial")
    private static class WorkflowCallback implements Workflow.WorkflowCallbackHandler, Serializable {
        @SuppressWarnings("unchecked")
        @Override
        public void workflowComplete(Workflow workflow, Object[] args)
                throws WorkflowException {
            List<URI> volumes = (List<URI>) args[0];
            String msg = BlockDeviceController.getVolumesMsg(s_dbClient, volumes);
            s_logger.info("Processed volumes:\n" + msg);
        }
    }
    
    /* (non-Javadoc)
     * @see com.emc.storageos.blockorchestrationcontroller.BlockOrchestrationController#deleteVolumes(java.util.List, java.lang.String)
     */
    @Override
    public void deleteVolumes(List<VolumeDescriptor> volumes, String taskId) throws ControllerException {
        List<URI> volUris = VolumeDescriptor.getVolumeURIs(volumes);
        VolumeWorkflowCompleter completer = new VolumeWorkflowCompleter(volUris, taskId);
        
        try {
            // Generate the Workflow.
            Workflow workflow = _workflowService.getNewWorkflow(this,
                    DELETE_VOLUMES_WF_NAME, true, taskId);
            String waitFor = null;    // the wait for key returned by previous call
            
            // Call the RPDeviceController to add its methods if there are RP protections.
            waitFor = _rpDeviceController.addStepsForDeleteVolumes(
                    workflow, waitFor, volumes, taskId);
            
            // Call the VPlexDeviceController to add its methods if there are VPLEX volumes.
            waitFor = _vplexDeviceController.addStepsForDeleteVolumes(
                    workflow, waitFor, volumes, taskId);
            
            // Call the SRDFDeviceController to add its methods if there are SRDF volumes.
            waitFor = _srdfDeviceController.addStepsForDeleteVolumes(
                    workflow, waitFor, volumes, taskId);

            // Next, call the BlockDeviceController to add its methods.
            waitFor = _blockDeviceController.addStepsForDeleteVolumes(
                    workflow, waitFor, volumes, taskId);

            // Call the VPlexDeviceController to add its post-delete methods.
            waitFor = _vplexDeviceController.addStepsForPostDeleteVolumes(
                    workflow, waitFor, volumes, taskId, completer);

            // Last, call the RPDeviceController to add its post-delete methods.
            waitFor = _rpDeviceController.addStepsForPostDeleteVolumes(
                    workflow, waitFor, volumes, taskId, completer);

            // Finish up and execute the plan.
            // The Workflow will handle the TaskCompleter
            String successMessage = "Delete volumes successful for: " + volUris.toString();
            Object[] callbackArgs = new Object[] { volUris };
            workflow.executePlan(completer, successMessage, new WorkflowCallback(), callbackArgs, null, null);
        } catch (Exception ex) {
            s_logger.error("Could not delete volumes: " + volUris, ex);
            String opName = ResourceOperationTypeEnum.DELETE_BLOCK_VOLUME.getName();
            ServiceError serviceError = DeviceControllerException.errors.deleteVolumesFailed(
                    volUris.toString(), opName, ex);
            completer.error(s_dbClient, _locker, serviceError);
        }
    }

    
    /* (non-Javadoc)
     * @see com.emc.storageos.blockorchestrationcontroller.BlockOrchestrationController#expandVolume(java.net.URI, long, java.lang.String)
     */
    @Override
    public void expandVolume(List<VolumeDescriptor> volumes, String taskId) throws ControllerException {       
    	 List<URI> volUris = VolumeDescriptor.getVolumeURIs(volumes);
         VolumeWorkflowCompleter completer = new VolumeWorkflowCompleter(volUris, taskId);       
        try {
            // Generate the Workflow.
            Workflow workflow = _workflowService.getNewWorkflow(this,
            		EXPAND_VOLUMES_WF_NAME, true, taskId);
            String waitFor = null;    // the wait for key returned by previous call
            
            // First, call the RP controller to add methods for RP CG delete
            waitFor = _rpDeviceController.addPreVolumeExpandSteps(
                    workflow, volumes, taskId); 
            
            // Call the BlockDeviceController to add its methods if there are block or VPLEX backend volumes.
            waitFor = _blockDeviceController.addStepsForExpandVolume(
                    workflow, waitFor, volumes, taskId);

            // Call the VPlexDeviceController to add its methods if there are VPLEX volumes.
            waitFor = _vplexDeviceController.addStepsForExpandVolume(
                    workflow, waitFor, volumes, taskId);
                        
            // Call the RPDeviceController to add its methods for post volume expand ie. recreate RPCG
            waitFor = _rpDeviceController.addPostVolumeExpandSteps(
                    workflow, waitFor, volumes, taskId);
                        
            // Finish up and execute the plan.
            // The Workflow will handle the TaskCompleter
            String successMessage = "Expand volume successful for: " + volUris.toString();
            Object[] callbackArgs = new Object[] { new ArrayList<URI>( volUris ) };
            workflow.executePlan(completer, successMessage, new WorkflowCallback(), callbackArgs, null, null);
        } catch (Exception ex) {
            s_logger.error("Could not expand volume: " + volUris,toString(), ex);
            String opName = ResourceOperationTypeEnum.EXPAND_BLOCK_VOLUME.getName();
            ServiceError serviceError = DeviceControllerException.errors.expandVolumeFailed(volUris.toString(), opName, ex);
            completer.error(s_dbClient, _locker, serviceError);
        }
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.blockorchestrationcontroller.BlockOrchestrationController#changeVirtualPool(java.util.List, java.lang.String)
     */
    @Override
    public void changeVirtualPool(List<VolumeDescriptor> volumes, String taskId)
            throws ControllerException {
        Map<URI, URI> changeVpoolVolsMap = VolumeDescriptor.getAllVirtualPoolChangeSourceVolumes(volumes); 
        List<URI> volURIs = VolumeDescriptor.getVolumeURIs(volumes);
        List<URI> migrationURIs = new ArrayList<URI>();
        for (VolumeDescriptor desc : volumes) {
            URI migrationURI = desc.getMigrationId();
            if (!NullColumnValueGetter.isNullURI(migrationURI)) {
                migrationURIs.add(migrationURI);
            }
        }
        
        VolumeVpoolChangeTaskCompleter completer = new VolumeVpoolChangeTaskCompleter(
            volURIs, migrationURIs, changeVpoolVolsMap, taskId);
        
        try {            
            // Generate the Workflow.
            Workflow workflow = _workflowService.getNewWorkflow(this,
                    CHANGE_VPOOL_WF_NAME, true, taskId);
            String waitFor = null;    // the wait for key returned by previous call
            
            // Check to see if we need to add any expand steps before we perform the change vpool
            // operation. This can occur for a RP or RP+VPLEX/MetroPoint change vpool operation
            // due to the fact that we may be using mixed backend arrays for source/target(s).
            // RP needs to have all source/target(s) provisioned capacities to be the same to properly
            // protect the volumes. Capacity adjustments (if any) would have been made in the API. We 
            // just need to follow through with the expand if required.
            waitFor = addRPChangeVpoolExpandSteps(workflow, waitFor, volumes, taskId);
            
            // Mainly for RP+VPLEX as a change vpool would require new volumes (source-journal, target(s), 
            // target-journal) to be created.
            waitFor = _blockDeviceController.addStepsForCreateVolumes(
                    workflow, waitFor, volumes, taskId);
            
            // Call the VPlexDeviceController to add change virtual pool steps.
            waitFor = _vplexDeviceController.addStepsForChangeVirtualPool(
                    workflow, waitFor, volumes, taskId);
            
            // Last, call the RPDeviceController to add change virtual pool steps.
            waitFor = _rpDeviceController.addStepsForChangeVirtualPool(
                    workflow, waitFor, volumes, taskId);

            // Finish up and execute the plan.
            // The Workflow will handle the TaskCompleter
            String successMessage = "Change Virtual Pool suceeded for volumes: " + volURIs.toString();
            Object[] callbackArgs = new Object[] { volURIs };
            workflow.executePlan(completer, successMessage, new WorkflowCallback(), callbackArgs, null, null);
        } catch (Exception ex) {
            s_logger.error("Could not change Virtual Pool for volumes: " + volURIs, ex);
            String opName = ResourceOperationTypeEnum.CHANGE_BLOCK_VOLUME_VPOOL.getName();
            ServiceError serviceError = DeviceControllerException.errors.changeVirtualPoolFailed(
                    volURIs.toString(), opName, ex);
            completer.error(s_dbClient, _locker, serviceError);
        }
    }
 
    @Override
    public void changeVirtualArray(List<VolumeDescriptor> volumeDescriptors, String taskId)
        throws ControllerException {
        
        // The descriptors that contain descriptor parameters
        // specifying the new target varray are the volumes being
        // moved to the new virtual array.
        List<URI> changeVArrayVolURIList = new ArrayList<URI>();
        List<URI> migrationURIs = new ArrayList<URI>();
        for (VolumeDescriptor volumeDescriptor : volumeDescriptors) {
            Map<String, Object> descrParams = volumeDescriptor.getParameters();
            if ((descrParams != null) && (!descrParams.isEmpty())) {
                changeVArrayVolURIList.add(volumeDescriptor.getVolumeURI());
            }
            URI migrationURI = volumeDescriptor.getMigrationId();
            if (!NullColumnValueGetter.isNullURI(migrationURI)) {
                migrationURIs.add(migrationURI);
            }
        }
        
        // Create a completer that will update the task status for these 
        // volumes and associated migrations when the workflow completes.
        VolumeVarrayChangeTaskCompleter completer = new VolumeVarrayChangeTaskCompleter(
            VolumeDescriptor.getVolumeURIs(volumeDescriptors), migrationURIs, taskId);

        try {            
            // Generate the Workflow.
            String waitFor = null;
            Workflow workflow = _workflowService.getNewWorkflow(this,
                CHANGE_VARRAY_WF_NAME, true, taskId);
            
            // First, call the BlockDeviceController to add its steps.
            // This will create the migration target volumes.
            waitFor = _blockDeviceController.addStepsForCreateVolumes(workflow, waitFor,
                volumeDescriptors, taskId);
            
            // Then call the VPlexDeviceController to add change virtual array steps.
            waitFor = _vplexDeviceController.addStepsForChangeVirtualArray(workflow,
                waitFor, volumeDescriptors, taskId);
            
            // Finish up and execute the plan.
            // The Workflow will handle the TaskCompleter
            String successMessage = String.format(
                "Change virtual array suceeded for volumes: &s", changeVArrayVolURIList);
            Object[] callbackArgs = new Object[] { changeVArrayVolURIList };
            workflow.executePlan(completer, successMessage, new WorkflowCallback(), callbackArgs, null, null);
        } catch (Exception ex) {
            s_logger.error("Could not change virtual array for volumes: " + changeVArrayVolURIList, ex);
            String opName = ResourceOperationTypeEnum.CHANGE_BLOCK_VOLUME_VARRAY.getName();
            ServiceError serviceError = DeviceControllerException.errors
                .changeVirtualArrayFailed(changeVArrayVolURIList.toString(), opName, ex);
            completer.error(s_dbClient, _locker, serviceError);
        }
    }
    
    /**
     * Determine if there are any expand steps needed for the change vpool operation. Mainly
     * needed for RP change vpool. The existing volume(s) may need to be expanded to match the
     * all volumes being protected by RP. RP requires all source/target volumes have the same
     * capacities otherwise the RP CG creation will fail. The API takes care of the calculation to
     * increase capacity, this method fulfills the expand request by adding expand steps if needed. 
     * 
     * @param workflow The current workflow
     * @param waitFor The previous operation to wait for 
     * @param volumeDescriptors All the volume descriptors
     * @param taskId The current task id
     * @return The previous operation id
     */
    private String addRPChangeVpoolExpandSteps(Workflow workflow,
            String waitFor, List<VolumeDescriptor> volumeDescriptors, String taskId) {
        // Check to see if this is an RP change vpool request
        List<VolumeDescriptor> rpExistingVolumeDescs = VolumeDescriptor.filterByType(volumeDescriptors,
                                                    new VolumeDescriptor.Type[] { VolumeDescriptor.Type.RP_EXISTING_SOURCE },
                                                    new VolumeDescriptor.Type[] {});
        // If there are no RP_EXISTING_SOURCE descriptors, just return
        if (rpExistingVolumeDescs == null || rpExistingVolumeDescs.isEmpty()) {
            return waitFor;
        }
        
        // Generic descriptor list
        List<VolumeDescriptor> descriptors = new ArrayList<VolumeDescriptor>();
        descriptors.addAll(rpExistingVolumeDescs);            
        
        // Grab any migration descriptors if they exist, we'll use them later if needed.
        List<VolumeDescriptor> migrateDescriptors = VolumeDescriptor.filterByType(volumeDescriptors,
                new VolumeDescriptor.Type[] { VolumeDescriptor.Type.VPLEX_MIGRATE_VOLUME }, null );                
        
        // If there are any migration descriptors, blockDeviceController will use them to filter out
        // any VPLEX backing volumes that do not require an expand.
        if (migrateDescriptors != null && !migrateDescriptors.isEmpty()) {   
            descriptors.addAll(migrateDescriptors);
        }
        
        for (VolumeDescriptor rpExistingVolumeDesc : rpExistingVolumeDescs) {
            // Get the existing source volume
            Volume rpExistingSource = s_dbClient.queryObject(Volume.class, rpExistingVolumeDesc.getVolumeURI());
            
            s_logger.info("RP change vpool operation, check to see if we need to perform an expand on volume [{}].", 
                    rpExistingSource.getLabel());
            
            // Check to see if this is a RP+VPLEX/MetroPoint change vpool request        
            boolean isRPVPlex = (rpExistingSource.getAssociatedVolumes() != null 
                                   && !rpExistingSource.getAssociatedVolumes().isEmpty());
            
            // Flag to see if an expand is necessary
            boolean performExpand = false;
            
            // Check to see if the capacity and provisioned capacity of the volumes are different. If they are
            // we need to perform and expand.
            // If this is RP+VPLEX or MetroPoint check the backend volumes otherwise, the volume itself is OK to check.  
            // NOTE: We could be expanding a backing volume that may be deleted by migration after, however in this case
            // better safe than sorry because we could have the case where 1 backing volume is migrated and the
            // other is not.
            if (isRPVPlex) {
                for (String volId : rpExistingSource.getAssociatedVolumes()) {                   
                    URI volURI = URI.create(volId);
                    Volume associatedVolume = s_dbClient.queryObject(Volume.class, volURI);
                    // Perform an expand if the capacity and provisioned capacity are different
                    if (associatedVolume.getCapacity().longValue() > associatedVolume.getProvisionedCapacity()) {
                        performExpand = true;
                        break;
                    }
                }            
            }
            else {
                // Perform an expand if the capacity and provisioned capacity are different
                performExpand = (rpExistingSource.getCapacity().longValue() > rpExistingSource.getProvisionedCapacity());
            }
    
            // Check to see if we need to perform an expand
            if (performExpand) {
                s_logger.info(String.format("Volume capacity size is different from provisioned capacity size, " +
                                                "adding expand volume steps for volume [%s].",
                                                rpExistingSource.getLabel()));
                                                
                s_logger.info("Adding Block expand steps.");
                // Call the BlockDeviceController to add its methods if there are block or VPLEX backend volumes.
                waitFor = _blockDeviceController.addStepsForExpandVolume(
                            workflow, waitFor, descriptors, taskId);
                
                if (isRPVPlex) {
                    s_logger.info("Adding VPLEX expand steps.");
                    // Call the VPlexDeviceController to add its methods if there are VPLEX volumes.
                    waitFor = _vplexDeviceController.addStepsForExpandVolume(
                                workflow, waitFor, descriptors, taskId);
                }
            }
            else {
                s_logger.info(String.format("No expand steps required for volume [%s].",
                        rpExistingSource.getLabel()));
            }
        }
        
        return waitFor;
    }

    public WorkflowService getWorkflowService() {
        return _workflowService;
    }

    public void setWorkflowService(WorkflowService workflowService) {
        this._workflowService = workflowService;
    }

    public DbClient getDbClient() {
        return s_dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        this.s_dbClient = dbClient;
    }

    public void setLocker(ControllerLockingService locker) {
    	this._locker = locker;
    }
    
    public BlockDeviceController getBlockDeviceController() {
        return _blockDeviceController;
    }

    public void setBlockDeviceController(BlockDeviceController blockDeviceController) {
        this._blockDeviceController = blockDeviceController;
    }

    public static VPlexDeviceController getVplexDeviceController() {
        return _vplexDeviceController;
    }

    public static void setVplexDeviceController(VPlexDeviceController vplexDeviceController) {
        BlockOrchestrationDeviceController._vplexDeviceController = vplexDeviceController;
    }

    public static RPDeviceController getRpDeviceController() {
        return _rpDeviceController;
    }

    public static void setRpDeviceController(RPDeviceController rpDeviceController) {
        BlockOrchestrationDeviceController._rpDeviceController = rpDeviceController;
    }

    public static SRDFDeviceController getSrdfDeviceController() {
        return _srdfDeviceController;
    }

    public static void setSrdfDeviceController(SRDFDeviceController srdfDeviceController) {
        BlockOrchestrationDeviceController._srdfDeviceController = srdfDeviceController;
    }
}
