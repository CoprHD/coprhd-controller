package com.emc.storageos.migrationcontroller;

import static com.emc.storageos.migrationcontroller.MigrationControllerUtils.getDataObject;
import static com.emc.storageos.migrationcontroller.MigrationControllerUtils.getVolumesVarray;

import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.computesystemcontroller.impl.ComputeSystemHelper;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageProtocol;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.util.NetworkLite;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.ControllerLockingService;
import com.emc.storageos.volumecontroller.impl.block.BlockDeviceController;
import com.emc.storageos.volumecontroller.impl.block.ExportWorkflowUtils;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.hostMigrationExportOrchestrationCompleter;
import com.emc.storageos.volumecontroller.placement.BlockStorageScheduler;
import com.emc.storageos.volumecontroller.placement.StoragePortsAssigner;
import com.emc.storageos.volumecontroller.placement.StoragePortsAssignerFactory;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowException;
import com.emc.storageos.workflow.WorkflowService;
import com.google.common.base.Joiner;

public class HostExportManager {
    private static final Logger _log = LoggerFactory.getLogger(HostExportManager.class);
    private static DbClient _dbClient = null;
    private List<URI> exportGroupsCreated;
    private static final String STEP_EXPORT_GROUP = "migrationExportGroup";

    private ExportWorkflowUtils _exportWfUtils;
    private WorkflowService _workflowService;
    private ControllerLockingService _locker;

    private static final String ALPHA_NUMERICS = "[^A-Za-z0-9_]";
    private static final String DASHED_NEWLINE = "---------------------------------%n";
    private static final String MIGRATION_EXPORT_ORCHESTRATOR_WF_NAME = "MIGRATION_EXPORT_ORCHESTRATION_WORKFLOW";

    public static synchronized void setDbClient(DbClient dbClient) {
        if (_dbClient == null) {
            _dbClient = dbClient;
        }
    }

    public void setExportWorkflowUtils(ExportWorkflowUtils exportWorkflowUtils) {
        _exportWfUtils = exportWorkflowUtils;
    }

    public void setWorkflowService(WorkflowService workflowService) {
        this._workflowService = workflowService;
    }
    public HostExportManager() {

    }

    public void setLocker(ControllerLockingService locker) {
        this._locker = locker;
    }

    protected boolean isHostInitiatorConnectedToStorageSystem(URI volumeURI, List<Initiator> hostInitiators) {
        Volume volume = getDataObject(Volume.class, volumeURI, _dbClient);
        URI storageURI = volume.getStorageController();
        StorageSystem storageSystem = getDataObject(StorageSystem.class, storageURI, _dbClient);
        _log.info("Got storage system");

        Map<URI, Volume> volumeMap = new HashMap<URI, Volume>();
        volumeMap.put(volumeURI, volume);
        List<URI> varray = new ArrayList<URI>();
        varray.add(getVolumesVarray(storageSystem, volumeMap.values()));
        for (Initiator hostInitiator : hostInitiators) {
            if (ConnectivityUtil.isInitiatorConnectedToStorageSystem(hostInitiator, storageSystem, varray, _dbClient))
                return true;
        }
        return false;
    }

    public boolean exportOrchestrationSteps(HostMigrationDeviceController controller, List<URI> volumeURIs,
            URI hostURI, String taskId) throws ControllerException {
        hostMigrationExportOrchestrationCompleter completer = new hostMigrationExportOrchestrationCompleter(volumeURIs, taskId);
        Workflow workflow = null;
        boolean lockException = false;
        Map<URI, Set<URI>> exportGroupVolumesAdded = new HashMap<URI, Set<URI>>();
        exportGroupsCreated = new ArrayList<URI>();
        
        try {
            // Generate the Workflow.
            workflow = _workflowService.getNewWorkflow(controller,
                    MIGRATION_EXPORT_ORCHESTRATOR_WF_NAME, true, taskId);
            
            String waitFor = null;    // the wait for key returned by previous call

            Host host = getDataObject(Host.class, hostURI, _dbClient);
            List<Initiator> hostInitiators = ComputeSystemHelper.queryInitiators(_dbClient, hostURI);
            //todo: get lock
            //acquireHostLockKeysForExport(taskId,volumeURIs,hostInitiators);
            
            Volume firstVolume = getDataObject(Volume.class, volumeURIs.get(0), _dbClient);
            URI projectURI = firstVolume.getProject().getURI();
            Project project = getDataObject(Project.class, projectURI, _dbClient);
            
            Map<URI, Volume> volumeMap = new HashMap<URI, Volume>();
            Map<URI, StorageSystem> storageSystemMap = new HashMap<URI, StorageSystem>();
            for (URI volumeURI : volumeURIs) {
                Volume volume = getDataObject(Volume.class, volumeURI, _dbClient);
                volumeMap.put(volumeURI, volume);
                StorageSystem storageSystem = getDataObject(StorageSystem.class, volume.getStorageController(), _dbClient);
                storageSystemMap.put(volume.getStorageController(), storageSystem);
            }
            
            // Main processing containers. ExportGroup --> StorageSystem --> Volumes
            // Populate the container for the export workflow step generation
            for (Map.Entry<URI, StorageSystem> storageEntry : storageSystemMap.entrySet()) {
                URI storageSystemURI = storageEntry.getKey();
                StorageSystem storageSystem = storageEntry.getValue();
                URI varrayURI = getVolumesVarray(storageSystem, volumeMap.values());
                _log.info(String.format("Creating ExportGroup for storage system %s (%s) in Virtual Aarray[(%s)]",
                        storageSystem.getLabel(), storageSystemURI, varrayURI));

                if (varrayURI == null) {
                    // For whatever reason, there were no Volumes for this Storage System found, so we
                    // definitely do not want to create anything. Log a warning and continue.
                    _log.warn(String.format("No Volumes for storage system %s (%s), no need to create an ExportGroup.",
                            storageSystem.getLabel(), storageSystemURI));
                    continue;
                }
                VirtualArray virtualVarray =
                        _dbClient.queryObject(VirtualArray.class, varrayURI);
                
                List<URI> initiatorSet = new ArrayList<URI>();
                ExportGroup exportGroup = createExportGroup(project, storageSystem, virtualVarray);

                Map<URI, Set<Initiator>> networkToInitiatorsMap = new HashMap<URI, Set<Initiator>>();

                for (Initiator hostInitiator : hostInitiators) {
                    URI initiatorNetworkURI = getInitiatorNetwork(exportGroup, hostInitiator);
                    if (initiatorNetworkURI != null) {
                        if (networkToInitiatorsMap.get(initiatorNetworkURI) == null) {
                            networkToInitiatorsMap.put(initiatorNetworkURI, new HashSet<Initiator>());
                        }
                        networkToInitiatorsMap.get(initiatorNetworkURI).add(hostInitiator);
                        _log.info(String.format("Initiator [%s] found on network: [%s]",
                                hostInitiator.getInitiatorPort(), initiatorNetworkURI.toASCIIString()));
                    } else {
                        _log.info(String.format("Initiator [%s] was not found on any network. Excluding from automated exports",
                                hostInitiator.getInitiatorPort()));
                    }
                }

                Map<URI, List<StoragePort>> initiatorPortMap = getInitiatorPortsForArray(
                        networkToInitiatorsMap, storageSystemURI, varrayURI);

                for (URI networkURI : initiatorPortMap.keySet()) {
                    for (StoragePort storagePort : initiatorPortMap.get(networkURI)) {
                        _log.info(String.format("Network : [%s] - Port : [%s]", networkURI.toString(), storagePort.getLabel()));
                    }
                }

                int numPaths = computeNumPaths(initiatorPortMap, varrayURI, storageSystem);
                _log.info("Total paths = " + numPaths);

                List<Initiator> initiatorList = new ArrayList<Initiator>();
                for (URI networkURI : networkToInitiatorsMap.keySet()) {
                    if (initiatorPortMap.containsKey(networkURI)) {
                        initiatorList.addAll(networkToInitiatorsMap.get(networkURI));
                    }
                }

                for (Initiator initiator : initiatorList) {
                    initiatorSet.add(initiator.getId());
                }

                ExportGroup exportGroupInDB = exportGroupExistsInDB(exportGroup);
                boolean addExportGroupToDB = false;
                if (exportGroupInDB != null) {
                    exportGroup = exportGroupInDB;
                    // If the export already exists, check to see if any of the volumes have already been exported. No need to
                    // re-export volumes.
                    List<URI> volumesToRemove = new ArrayList<URI>();
                    for (URI volumeURI : volumeURIs) {
                        if (exportGroup.getVolumes() != null
                                && !exportGroup.getVolumes().isEmpty()
                                && exportGroup.getVolumes().containsKey(volumeURI.toString())) {
                            _log.info(String.format("Volume [%s] already exported to export group [%s], " +
                                    "it will be not be re-exported", volumeURI.toString(), exportGroup.getGeneratedName()));
                            volumesToRemove.add(volumeURI);
                        }
                    }
                    // Remove volumes if they have already been exported
                    if (!volumesToRemove.isEmpty()) {
                        volumeURIs.removeAll(volumesToRemove);
                    }
                    // If there are no more volumes to export, skip this one and continue,
                    // nothing else needs to be done here.
                    if (volumeURIs.isEmpty()) {
                        _log.info(String.format("No volumes needed to be exported to export group [%s], continue",
                                exportGroup.getGeneratedName()));
                        continue;
                    }
                }
                else {
                    addExportGroupToDB = true;
                }

                // Add volumes to the export group
                Map<URI, Integer> volumesToAdd = new HashMap<URI, Integer>();
                for (URI volumeID : volumeURIs) {
                    exportGroup.addVolume(volumeID, ExportGroup.LUN_UNASSIGNED);
                    volumesToAdd.put(volumeID, ExportGroup.LUN_UNASSIGNED);
                }

                // Keep track of volumes added to export group
                if (!volumesToAdd.isEmpty()) {
                    exportGroupVolumesAdded.put(exportGroup.getId(), volumesToAdd.keySet());
                }

                exportGroup.addHost(host);

                // Persist the export group
                if (addExportGroupToDB) {
                    exportGroup.addInitiators(initiatorSet);
                    exportGroup.setNumPaths(numPaths);
                    _dbClient.createObject(exportGroup);
                    // Keep track of newly created EGs in case of rollback
                    exportGroupsCreated.add(exportGroup.getId());
                } else {
                    _dbClient.updateObject(exportGroup);
                }

                // If the export group already exists, add the volumes to it, otherwise create a brand new
                // export group.
                StringBuilder buffer = new StringBuilder();
                buffer.append(String.format(DASHED_NEWLINE));
                if (!addExportGroupToDB) {

                    buffer.append(String.format(
                            "Adding volumes to existing Export Group for Storage System [%s], host [%s], Virtual Array [%s]%n",
                            storageSystem.getLabel(), host.getHostName(), virtualVarray.getLabel()));
                    buffer.append(String.format("Export Group name is : [%s]%n", exportGroup.getGeneratedName()));
                    buffer.append(String.format("Export Group will have these volumes added: [%s]%n", Joiner.on(',').join(volumeURIs)));
                    buffer.append(String.format(DASHED_NEWLINE));
                    _log.info(buffer.toString());

                    waitFor = _exportWfUtils.
                            generateExportGroupAddVolumes(workFlow, STEP_EXPORT_GROUP,
                                    waitFor, storageSystemURI,
                                    exportGroup.getId(), volumesToAdd);

                    _log.info("Added Export Group add volumes step in workflow");
                }
                else {
                    buffer.append(String.format("Creating new Export Group for Storage System [%s], host [%s], Virtual Array [%s]%n",
                            storageSystem.getLabel(), host.getHostName(), virtualVarray.getLabel()));
                    buffer.append(String.format("Export Group name is: [%s]%n", exportGroup.getGeneratedName()));
                    buffer.append(String.format("Export Group will have these initiators: [%s]%n", Joiner.on(',').join(initiatorSet)));
                    buffer.append(String.format("Export Group will have these volumes added: [%s]%n", Joiner.on(',').join(volumeURIs)));
                    buffer.append(String.format(DASHED_NEWLINE));
                    _log.info(buffer.toString());

                    String exportStep = workflow.createStepId();
                    initTaskStatus(exportGroup, exportStep, Operation.Status.pending, "create export");

                    waitFor = _exportWfUtils.
                            generateExportGroupCreateWorkflow(workflow,
                                    STEP_EXPORT_GROUP, waitFor,
                                    storageSystemURI, exportGroup.getId(),
                                    volumesToAdd, initiatorSet);

                    _log.info("Added Export Group create step in workflow. New Export Group Id: " + exportGroup.getId());
                }
            }
            String successMessage = "Export orchestration completed successfully";

            // Finish up and execute the plan.
            // The Workflow will handle the TaskCompleter
            Object[] callbackArgs = new Object[] { volumeURIs };
            workflow.executePlan(completer, successMessage, new WorkflowCallback(), callbackArgs, null, null);
            
        }catch (Exception ex) {
            _log.error("Could not create volumes: " + volumeURIs, ex);

            if (workflow != null) {
                _workflowService.releaseAllWorkflowLocks(workflow);
            }
            String opName = ResourceOperationTypeEnum.HOST_MIGRATE_VOLUME.getName();
            ServiceError serviceError = null;
            if (lockException) {
                serviceError = DeviceControllerException.errors.createVolumesAborted(volumeURIs.toString(), ex);
            } else {
                serviceError = DeviceControllerException.errors.createVolumesFailed(
                        volumeURIs.toString(), opName, ex);
            }
            completer.error(_dbClient, _locker, serviceError);
            return false;
        }

        _log.info("End adding host Migration Export Volumes steps.");

        return true;
    }

    @SuppressWarnings("serial")
    private static class WorkflowCallback implements Workflow.WorkflowCallbackHandler, Serializable {
        @SuppressWarnings("unchecked")
        @Override
        public void workflowComplete(Workflow workflow, Object[] args)
                throws WorkflowException {
            List<URI> volumes = (List<URI>) args[0];
            String msg = BlockDeviceController.getVolumesMsg(_dbClient, volumes);
            _log.info("Processed volumes:\n" + msg);
        }
    }

    /*
     * private Map<URI, Volume> filterVolumeMap(Map<URI, Volume> volumeMap, StorageSystem array) {
     * Map<URI, Volume> filteredMap = new HashMap<>();
     * for (Volume volume : volumeMap.values()) {
     * if (volume.getStorageController().equals(array.getId())) {
     * filteredMap.put(volume.getId(), volume);
     * }
     * }
     * return filteredMap;
     * }
     */

    private ExportGroup createExportGroup(Project project, StorageSystem storageSystem, VirtualArray virtualArray) {
        ExportGroup exportGroup = new ExportGroup();
        exportGroup.setId(URIUtil.createId(ExportGroup.class));
        exportGroup.setLabel("host migration");
        exportGroup.addInternalFlags(Flag.INTERNAL_OBJECT);
        // TODO - For temporary backward compatibility
        String type = "Initiator";
        exportGroup.setType(type);

        exportGroup.setProject(new NamedURI(project.getId(), project.getLabel()));
        exportGroup.setVirtualArray(virtualArray.getId());
        exportGroup.setTenant(new NamedURI(project.getTenantOrg().getURI(), project.getTenantOrg().getName()));

        String generatedName = exportGroup.getLabel() + "_" + project.getLabel() + "_"
                + virtualArray.getLabel() + "_" + storageSystem.getLabel();
        exportGroup.setGeneratedName(generatedName);

        exportGroup.setVolumes(new StringMap());
        exportGroup.setOpStatus(new OpStatusMap());
        exportGroup.setNumPaths(0);

        return exportGroup;
    }
    
    private URI getInitiatorNetwork(ExportGroup exportGroup, Initiator initiator) throws InternalException {
        _log.info(String.format("Export(%s), Initiator: p(%s), port(%s)",
                exportGroup.getLabel(), initiator.getProtocol(), initiator.getInitiatorPort()));

        NetworkLite net = BlockStorageScheduler.lookupNetworkLite(_dbClient, StorageProtocol.block2Transport(initiator.getProtocol()),
                initiator.getInitiatorPort());

        // If this port is unplugged or in a network we don't know about or in a network that is unregistered, then we can't use it.
        if (net == null || RegistrationStatus.UNREGISTERED.toString().equalsIgnoreCase(net.getRegistrationStatus())) {
            return null;
        }

        return net.getId();
    }

    private Map<URI, List<StoragePort>> getInitiatorPortsForArray(Map<URI, Set<Initiator>> networkToInitiatorMap,
            URI arrayURI, URI varray) throws MigrationControllerException {

        Map<URI, List<StoragePort>> initiatorMap = new HashMap<URI, List<StoragePort>>();

        // Then get the front end ports on the Storage array.
        Map<URI, List<StoragePort>> arrayTargetMap = ConnectivityUtil.getStoragePortsOfType(_dbClient,
                arrayURI, StoragePort.PortType.frontend);

        // Eliminate any storage ports that are not explicitly assigned
        // or implicitly connected to the passed varray.
        Set<URI> arrayTargetNetworks = new HashSet<URI>();
        arrayTargetNetworks.addAll(arrayTargetMap.keySet());
        Iterator<URI> arrayTargetNetworksIter = arrayTargetNetworks.iterator();
        while (arrayTargetNetworksIter.hasNext()) {
            URI networkURI = arrayTargetNetworksIter.next();
            Iterator<StoragePort> targetStoragePortsIter = arrayTargetMap.get(networkURI).iterator();
            while (targetStoragePortsIter.hasNext()) {
                StoragePort targetStoragePort = targetStoragePortsIter.next();
                StringSet taggedVArraysForPort = targetStoragePort.getTaggedVirtualArrays();
                if ((taggedVArraysForPort == null) || (!taggedVArraysForPort.contains(varray.toString()))) {
                    targetStoragePortsIter.remove();
                }
            }

            // If the entry for this network is now empty then
            // remove the entry from the target storage port map.
            if (arrayTargetMap.get(networkURI).isEmpty()) {
                arrayTargetMap.remove(networkURI);
            }
        }

        // Get all the ports corresponding to the network that the host initiators are in.
        // we will use all available ports
        for (URI initiatorNetworkURI : networkToInitiatorMap.keySet()) {
            if (arrayTargetMap.keySet().contains(initiatorNetworkURI)) {
                initiatorMap.put(initiatorNetworkURI, arrayTargetMap.get(initiatorNetworkURI));
            }
        }

        // If there are no initiator ports, fail the operation, because we cannot zone.
        if (initiatorMap.isEmpty()) {
            Set<Initiator> initiatorSet = networkToInitiatorMap.get(networkToInitiatorMap.keySet().iterator().next());
            String hostName = initiatorSet.iterator().next().getHostName();
            throw MigrationControllerException.exceptions.getInitiatorPortsForArrayFailed(hostName,
                    arrayURI.toString());
        }

        return initiatorMap;
    }

    private ExportGroup exportGroupExistsInDB(ExportGroup exportGroupToFind) throws InternalException {
        // Query for all existing Export Groups, a little expensive.
        List<URI> allActiveExportGroups = _dbClient.queryByType(ExportGroup.class, true);
        for (URI exportGroupURI : allActiveExportGroups) {
            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
            // Sometimes the URI is still in the DB, but the object isn't is marked for deletion so
            // we need to check to see if it's active as well if the names match. Also make sure
            // it's for the same project.
            if (exportGroup != null
                    && !exportGroup.getInactive()
                    && exportGroup.getProject().getURI().equals(exportGroupToFind.getProject().getURI())) {
                // Ensure backwards compatibility by formatting the existing generated name to the same as the
                // potential new one.
                // We're looking for a format of:
                // rpSystem.getNativeGuid() + "_" + storageSystem.getLabel() + "_" + rpSiteName + "_" + varray.getLabel()
                // and replacing all non alpha-numerics with "" (except "_").
                String generatedName = exportGroup.getGeneratedName().trim().replaceAll(ALPHA_NUMERICS, "");
                if (generatedName.equals(exportGroupToFind.getGeneratedName())) {
                    _log.info("Export Group already exists in database.");
                    return exportGroup;
                }
            }
        }
        _log.info("Export Group does NOT already exist in database.");
        return null;
    }

    private Integer computeNumPaths(Map<URI, List<StoragePort>> initiatorPortMap, URI varray, StorageSystem array) {
        // Get the number of ports per path.
        StoragePortsAssigner assigner = StoragePortsAssignerFactory.getAssigner(array.getSystemType());
        int portsPerPath = assigner.getNumberOfPortsPerPath();
        // Get the array's front end ports for this varray only
        Map<URI, List<StoragePort>> arrayTargetMap = ConnectivityUtil.getStoragePortsOfTypeAndVArray(_dbClient,
                array.getId(), StoragePort.PortType.frontend, varray);

        int numPaths = 0;
        for (URI networkURI : initiatorPortMap.keySet()) {
            if (arrayTargetMap.get(networkURI) != null) {
                int pathsInNetwork = arrayTargetMap.get(networkURI).size() / portsPerPath;
                int initiatorsInNetwork = initiatorPortMap.get(networkURI).size();
                if (pathsInNetwork > initiatorsInNetwork) {
                    pathsInNetwork = initiatorsInNetwork;
                }
                _log.info(String.format("Network %s has %s paths", networkURI, pathsInNetwork));
                numPaths += pathsInNetwork;
            } else {
                _log.info(String.format("Storage Array %s has no ports in Network %s",
                        array.getNativeGuid(), networkURI));
            }
        }
        return numPaths;
    }

    private void initTaskStatus(ExportGroup exportGroup, String task, Operation.Status status, String message) {
        if (exportGroup.getOpStatus() == null) {
            exportGroup.setOpStatus(new OpStatusMap());
        }
        final Operation op = new Operation();
        if (status == Operation.Status.ready) {
            op.ready();
        }
        exportGroup.getOpStatus().put(task, op);
    }
}
