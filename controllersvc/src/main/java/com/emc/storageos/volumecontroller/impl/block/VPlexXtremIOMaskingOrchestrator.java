/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block;

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

import com.emc.storageos.Controller;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ExportGroup.ExportGroupType;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.exceptions.DeviceControllerExceptions;
import com.emc.storageos.locking.LockTimeoutValue;
import com.emc.storageos.locking.LockType;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.util.NetworkLite;
import com.emc.storageos.volumecontroller.BlockStorageDevice;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerLockingUtil;
import com.emc.storageos.volumecontroller.placement.StoragePortsAllocator;
import com.emc.storageos.volumecontroller.placement.StoragePortsAssigner;
import com.emc.storageos.vplex.api.VPlexApiException;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.Workflow.Method;
import com.emc.storageos.workflow.WorkflowService;
import com.emc.storageos.workflow.WorkflowStepCompleter;

public class VPlexXtremIOMaskingOrchestrator extends XtremIOMaskingOrchestrator implements
        VplexBackEndMaskingOrchestrator, Controller {
    private static final Logger _log = LoggerFactory.getLogger(VPlexXtremIOMaskingOrchestrator.class);
    private boolean simulation = false;
    private static final int XTREMIO_NUM_PORT_GROUP = 1;
    // Set morePortGroups to true for testing only if you want to generate additional port groups with
    // a small number of ports (e.g. two ports per network, which is typical in our Lab)
    // By default it will require two ports per Network for each Port Group which will generate fewer PortGroups
    private static boolean morePortGroups = false;
    BlockDeviceController _blockController = null;
    WorkflowService _workflowService = null;

    public VPlexXtremIOMaskingOrchestrator() {
    }

    public void setBlockDeviceController(BlockDeviceController blockController) {
        this._blockController = blockController;
    }

    public VPlexXtremIOMaskingOrchestrator(DbClient dbClient, BlockDeviceController controller) {
        this._dbClient = dbClient;
        this._blockController = controller;
    }

    @Override
    public Map<URI, ExportMask> readExistingExportMasks(StorageSystem storage,
            BlockStorageDevice device, List<Initiator> initiators) {
        // This will cause the VPlexBackendManager to generate an ExportMask
        // for the first volume and then reuse it by finding it from the
        // database for subsequent volumes.
        // Use this , if you really don't care about the # masks created on
        // theArray
        return new HashMap<URI, ExportMask>();
    }

    @Override
    public ExportMask refreshExportMask(StorageSystem storage, BlockStorageDevice device,
            ExportMask mask) {
        // Use this ,if you really don't care about the details of existing
        // masks on Array.
        return mask;
    }

    @Override
    public void suggestExportMasksForPlacement(
            StorageSystem storage, BlockStorageDevice device, List<Initiator> initiators,
            ExportMaskPlacementDescriptor placementDescriptor) {
        super.suggestExportMasksForPlacement(storage, device, initiators, placementDescriptor);
    }

    // TODO max ports per network?
    static final Integer MAX_PORTS_PER_NETWORK = 24;

    @Override
    public Set<Map<URI, List<List<StoragePort>>>> getPortGroups(
            Map<URI, List<StoragePort>> allocatablePorts, Map<URI, NetworkLite> networkMap,
            URI varrayURI, int nInitiatorGroups) {

        Set<Map<URI, List<List<StoragePort>>>> portGroups = new HashSet<Map<URI, List<List<StoragePort>>>>();


        StringSet netNames = new StringSet();
        // Order the networks from those with fewest ports to those with the most ports.
        List<URI> orderedNetworks = orderNetworksByNumberOfPorts(allocatablePorts);
        for (URI networkURI : orderedNetworks) {
            netNames.add(networkMap.get(networkURI).getLabel());
        }
        _log.info("Calculating PortGroups for Networks: " + netNames.toString());

        Map<URI, List<List<StoragePort>>> useablePorts = new HashMap<URI, List<List<StoragePort>>>();
        Map<URI, List<List<StoragePort>>> allocatablePortsNew = new HashMap<URI, List<List<StoragePort>>>();

        Set<String> eliminatedPorts = new HashSet<String>();
        Set<String> usedPorts = new HashSet<String>();

        boolean allPortsLooped = false;
        do {
            Map<URI, List<StoragePort>> useablePortsSet = getUsablePortsSet(allocatablePorts, orderedNetworks, usedPorts);
            if (useablePortsSet == null) {
                // if requirement not satisfied
                // TODO break / return null ?
            }
            for (URI networkURI : useablePortsSet.keySet()) {
                if (!useablePorts.containsKey(networkURI)) {
                    useablePorts.put(networkURI, new ArrayList<List<StoragePort>>());
                }
                useablePorts.get(networkURI).add(useablePortsSet.get(networkURI));
            }

            allPortsLooped = isAllPortsLooped(orderedNetworks, allocatablePorts, usedPorts);
        } while (!allPortsLooped);



        // If all networks have some ports remaining, use the filtered ports.
        // If not, emit a warning and do not use the filtered port configuration.
        boolean useFilteredPorts = true;
        for (URI networkURI : orderedNetworks) {
            if (useablePorts.get(networkURI).isEmpty()) {
                useFilteredPorts = false;
                break;
            }
        }
        if (useFilteredPorts) {
            _log.info("Using filtered ports: " + usedPorts.toString());
            _log.info("Ports eliminated because of sharing a X-brick with a used port: " + eliminatedPorts.toString());
            allocatablePortsNew = useablePorts;
        } else {
            _log.info("Some networks have zero remaining ports after X-brick filtering, will use duplicate ports on some X-bricks. "
                    + "This is not a recommended configuration.");
            for (URI networkURI : allocatablePorts.keySet()) {
                if (!allocatablePortsNew.containsKey(networkURI)) {
                    allocatablePortsNew.put(networkURI, new ArrayList<List<StoragePort>>());
                }
                allocatablePortsNew.get(networkURI).add(allocatablePorts.get(networkURI));
            }
        }

        // Determine the network with the lowest number of allocatable ports.
        int minPorts = Integer.MAX_VALUE;
        for (URI networkURI : allocatablePorts.keySet()) {
            int numPorts = allocatablePorts.get(networkURI).size();
            if (numPorts > MAX_PORTS_PER_NETWORK) {
                numPorts = MAX_PORTS_PER_NETWORK;
            }
            if (numPorts < minPorts) {
                minPorts = numPorts;
            }
        }

        // Figure out the number of ports in each network per port group (PG).
        // Then figure out the number of port groups to be generated.
        // HEURISTIC:
        // If the smallest network has 1 port, then allow 1 port per Network unless there's only 1 Network.
        // If there is only one network, require two ports per network in the Port Group.
        // If it has 2 or more ports per network, use 2 ports per network per MV.
        // If there are one or two networks, if it has 9 or more ports, use 3 ports per network per MV.
        // If there are one or two networks, if it has 16 or more ports, use 4 ports per network per MV.
        // oneNetwork indicates if there is only one Network available.
        // portsPerPG is the number of ports to be allocated per PortGroup from the
        // network with the fewest ports.
        // numPG is the number of Port Groups that will be configured.
        boolean oneNetwork = allocatablePorts.keySet().size() == 1;
        boolean moreThanTwoNetworks = allocatablePorts.keySet().size() > 2;
        int portsPerNetPerPG = oneNetwork ? 2 : 1;
        // It is best practice if each network has at least two ports, favoring fewer port groups.
        // But if "morePortGroups" is set, will make additional portGroups if there are fewer ports.
        if (morePortGroups) {       // This can be set true for testing environments
            if (minPorts >= 4) {
                portsPerNetPerPG = 2;   // Makes at least two Port Groups if there are two ports
            }
        } else {
            if (minPorts >= 2) {
                portsPerNetPerPG = 2;   // Default is to require at least two ports per Port Group
            }
        }
        if (!moreThanTwoNetworks) {
            if (minPorts >= 9) {
                portsPerNetPerPG = 3;
            }
            if (minPorts >= 16) {
                portsPerNetPerPG = 4;
            }
        }

        /**
         * Number of Port Group for XtremIO is always one.
         * 
         * Single Port Group contains different set of storage ports for each network,
         * each set to be used for each VPLEX director.
         */
        // int numPG = minPorts / portsPerNetPerPG;
        int numPG = XTREMIO_NUM_PORT_GROUP;
        _log.info(String.format("Number of Port Groups %d", numPG));

        // Make a map per Network of number of ports to allocate.
        Map<URI, Integer> portsAllocatedPerNetwork = new HashMap<URI, Integer>();
        for (URI netURI : allocatablePorts.keySet()) {
            Integer nports = allocatablePorts.get(netURI).size() / numPG;

            /*
             * // Don't allow this network to have more than twice the number of
             * // ports from the network with the fewest ports, i.e. do not exceed 2x portsPerPG.
             * if (nports > (2 * portsPerNetPerPG)) {
             * nports = 2 * portsPerNetPerPG;
             * }
             */

            portsAllocatedPerNetwork.put(netURI, nports);
        }

        // TODO number of ports to select - 4

        // Now call the StoragePortsAllocator for each Network, assigning required number of ports.
        StoragePortsAllocator allocator = new StoragePortsAllocator();
        for (int i = 0; i < numPG; i++) {
            Map<URI, List<List<StoragePort>>> portGroup = new HashMap<URI, List<List<StoragePort>>>();
            StringSet portNames = new StringSet();
            for (URI netURI : allocatablePortsNew.keySet()) {
                NetworkLite net = networkMap.get(netURI);
                for (List<StoragePort> portSet : allocatablePortsNew.get(netURI)) {
                    List<StoragePort> allocatedPorts = allocatePorts(allocator, portSet,
                            portSet.size(), net, varrayURI);
                    if (portGroup.get(netURI) == null) {
                        portGroup.put(netURI, new ArrayList<List<StoragePort>>());
                    }
                    portGroup.get(netURI).add(allocatedPorts);
                    portSet.removeAll(allocatedPorts);
                    for (StoragePort port : allocatedPorts) {
                        portNames.add(port.getPortName());
                    }
                }
            }
            portGroups.add(portGroup);
            _log.info(String.format("Port Group %d: %s", i, portNames.toString()));
        }
        return portGroups;
    }

    /**
     * Gets the usable ports set.
     *
     * @param allocatablePorts the allocatable ports
     * @param orderedNetworks the ordered networks
     * @param usedPorts the used ports
     * @return the usable ports set
     */
    private Map<URI, List<StoragePort>> getUsablePortsSet(Map<URI, List<StoragePort>> allocatablePorts, List<URI> orderedNetworks,
            Set<String> usedPorts) {

        Set<String> usedPortsSet = new HashSet<String>();
        Map<URI, List<StoragePort>> useablePorts = new HashMap<URI, List<StoragePort>>();
        // map of selected X-brick to Storage Controllers across all networks
        Map<String, List<String>> xBricksToSelectedSCs = new HashMap<String, List<String>>();
        // map of network to selected X-bricks
        Map<URI, List<String>> networkToSelectedXbricks = new HashMap<URI, List<String>>();

        do {
            int PreviousSize = usedPortsSet.size();
            for (URI networkURI : orderedNetworks) {
                if (!useablePorts.containsKey(networkURI)) {
                    useablePorts.put(networkURI, new ArrayList<StoragePort>());
                }

                StoragePort port = getNetworkPortUniqueXbrick(networkURI, allocatablePorts.get(networkURI),
                        usedPortsSet, networkToSelectedXbricks, xBricksToSelectedSCs);
                if (port != null) {
                    usedPortsSet.add(port.getPortName());
                    useablePorts.get(networkURI).add(port);
                }
            }
            boolean allPortsLooped = isAllPortsLooped(orderedNetworks, allocatablePorts, usedPortsSet);
            if (allPortsLooped) {
                if (usedPortsSet.size() < 2) {
                    return null;  // 0 port groups
                } else if (usedPortsSet.size() >= 2) {
                    break;  // satisfies minimum requirement
                }
            } else {
                // still ports available AND no ports selected in this round,
                // clear the X-bricks map
                if (PreviousSize == usedPortsSet.size()) {
                    xBricksToSelectedSCs.clear();
                    networkToSelectedXbricks.clear();
                }
            }
        } while (usedPortsSet.size() <= 4);
        // add to all usedPorts list
        usedPorts.addAll(usedPortsSet);
        return useablePorts;
    }

    /**
     * Gets the network port unique x brick.
     *
     * @param networkURI the network uri
     * @param storagePorts the storage ports
     * @param usedPorts the used ports
     * @param networkToSelectedXbricks the network to selected xbricks
     * @param xBricksToSelectedSCs the x bricks to selected s cs
     * @return the network port unique x brick
     */
    private StoragePort getNetworkPortUniqueXbrick(URI networkURI, List<StoragePort> storagePorts, Set<String> usedPorts,
            Map<URI, List<String>> networkToSelectedXbricks, Map<String, List<String>> xBricksToSelectedSCs) {
        /**
         * Input:
         * -List of network's storage ports;
         * -X-bricks already chosen for this network;
         * -X-bricks already chosen for all networks with StorageControllers (SC) chosen:
         * 
         * Choose a storage port based on below logic:
         * -See if there is a port from X-brick other than allNetworkXbricks (select different SC for the selected X-brick)
         * -If not, see if there is a port from X-brick other than networkXbricks (select different SC for the selected X-brick)
         */
        StoragePort port = null;
        if (networkToSelectedXbricks.get(networkURI) == null) {
            networkToSelectedXbricks.put(networkURI, new ArrayList<String>());
        }
        for (StoragePort sPort : storagePorts) {
            // Do not choose a port that has already been chosen
            if (!usedPorts.contains(sPort.getPortName())) {
                String[] splitArray = sPort.getPortGroup().split(Constants.HYPEN);
                String xBrick = splitArray[0];
                String sc = splitArray[1];
                // select port from unique X-brick/SC
                if (!xBricksToSelectedSCs.keySet().contains(xBrick)) {
                    port = sPort;
                    addSCToXbrick(xBricksToSelectedSCs, xBrick, sc);
                    networkToSelectedXbricks.get(networkURI).add(xBrick);
                    break;
                }
            }
        }
        if (port == null) {
            for (StoragePort sPort : storagePorts) {
                // Do not choose a port that has already been chosen
                if (!usedPorts.contains(sPort.getPortName())) {
                    String[] splitArray = sPort.getPortGroup().split(Constants.HYPEN);
                    String xBrick = splitArray[0];
                    String sc = splitArray[1];
                    // select port from unique X-brick/SC for this network
                    if (!networkToSelectedXbricks.get(networkURI).contains(xBrick)
                            && (xBricksToSelectedSCs.get(xBrick) == null || !xBricksToSelectedSCs.get(xBrick).contains(sc))) {
                        port = sPort;
                        addSCToXbrick(xBricksToSelectedSCs, xBrick, sc);
                        networkToSelectedXbricks.get(networkURI).add(xBrick);
                        break;
                    }
                }
            }
        }
        return port;
    }

    /**
     * Adds the sc to xbrick.
     *
     * @param xBricksToSelectedSCs the x bricks to selected s cs
     * @param xBrick the x brick
     * @param string the string
     */
    private void addSCToXbrick(Map<String, List<String>> xBricksToSelectedSCs, String xBrick, String storageController) {
        if (xBricksToSelectedSCs.get(xBrick) == null) {
            xBricksToSelectedSCs.put(xBrick, new ArrayList<String>());
        }
        if (!xBricksToSelectedSCs.get(xBrick).contains(storageController)) {
            xBricksToSelectedSCs.get(xBrick).add(storageController);
        }
    }

    /**
     * Checks if is all ports looped.
     *
     * @param orderedNetworks the ordered networks
     * @param allocatablePorts the allocatable ports
     * @param usedPorts the used ports
     * @return true, if is all ports looped
     */
    private boolean isAllPortsLooped(List<URI> orderedNetworks, Map<URI, List<StoragePort>> allocatablePorts, Set<String> usedPorts) {
        for (URI networkURI : orderedNetworks) {
            for (StoragePort port : allocatablePorts.get(networkURI)) {
                if (!usedPorts.contains(port.getPortName())) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Order the networks from those with least ports to those with most ports
     * 
     * @param allocatablePorts -- Map of Network URI to list of ports
     * @return ordered list of Network URIs
     */
    private List<URI> orderNetworksByNumberOfPorts(Map<URI, List<StoragePort>> allocatablePorts) {
        List<URI> orderedNetworks = new ArrayList<URI>();

        Map<Integer, Set<URI>> numPortsToNetworkSet = new HashMap<Integer, Set<URI>>();
        for (URI networkURI : allocatablePorts.keySet()) {
            int numPorts = allocatablePorts.get(networkURI).size();
            if (numPorts > MAX_PORTS_PER_NETWORK) {
                numPorts = MAX_PORTS_PER_NETWORK;
            }
            if (numPortsToNetworkSet.get(numPorts) == null) {
                numPortsToNetworkSet.put(numPorts, new HashSet<URI>());
            }
            numPortsToNetworkSet.get(numPorts).add(networkURI);
        }

        for (Integer numPorts = 1; numPorts <= MAX_PORTS_PER_NETWORK; numPorts++) {
            Set<URI> networkURIs = numPortsToNetworkSet.get(numPorts);
            if (networkURIs == null) {
                continue;
            }
            for (URI networkURI : networkURIs) {
                orderedNetworks.add(networkURI);
            }
        }
        return orderedNetworks;
    }

    private List<StoragePort> allocatePorts(StoragePortsAllocator allocator,
            List<StoragePort> candidatePorts, int portsRequested, NetworkLite net, URI varrayURI) {
        return VPlexBackEndOrchestratorUtil.allocatePorts(allocator, candidatePorts, portsRequested, net, varrayURI,
                simulation, _blockScheduler, _dbClient);
    }

    @Override
    public StringSetMap configureZoning(Map<URI, List<List<StoragePort>>> portGroup,
            Map<String, Map<URI, Set<Initiator>>> initiatorGroup, Map<URI, NetworkLite> networkMap, StoragePortsAssigner assigner) {

        StringSetMap zoningMap = new StringSetMap();
        // Set up a map to track port usage so that we can use all ports more or less equally.
        Map<StoragePort, Integer> portUsage = new HashMap<StoragePort, Integer>();
        // Iterate through each of the directors, matching each of its initiators
        // with one port. This will ensure not to violate four paths per director.
        int directorNumber = 1;
        for (String director : initiatorGroup.keySet()) {
            for (URI networkURI : initiatorGroup.get(director).keySet()) {
                NetworkLite net = networkMap.get(networkURI);
                for (Initiator initiator : initiatorGroup.get(director).get(networkURI)) {
                    // If there are no ports on the initiators network, too bad...
                    if (portGroup.get(networkURI) == null) {
                        _log.info(String.format("%s -> no ports in network",
                                initiator.getInitiatorPort()));
                        continue;
                    }

                    List<StoragePort> portList = getStoragePortSetForDirector(portGroup.get(networkURI), directorNumber);
                    // find a port for the initiator
                    StoragePort storagePort = VPlexBackEndOrchestratorUtil.assignPortToInitiator(assigner,
                            portList, net, initiator, portUsage, null);
                    if (storagePort != null) {
                        _log.info(String.format("%s %s   %s -> %s  %s", director, net.getLabel(),
                                initiator.getInitiatorPort(), storagePort.getPortNetworkId(),
                                storagePort.getPortName()));
                        StringSet ports = new StringSet();
                        ports.add(storagePort.getId().toString());
                        zoningMap.put(initiator.getId().toString(), ports);
                    } else {
                        _log.info(String.format("A port could not be assigned for %s %s   %s", director, net.getLabel(),
                                initiator.getInitiatorPort()));
                    }
                }
            }
            directorNumber++;
        }
        return zoningMap;
    }

    /**
     * Gets the storage port set for director.
     *
     * @param list the list
     * @param directorNumber the director number
     * @return the storage port set for director
     */
    private List<StoragePort> getStoragePortSetForDirector(List<List<StoragePort>> list, int directorNumber) {
        List<StoragePort> portsSetForDirector = null;
        Iterator<List<StoragePort>> itr = list.iterator();
        for (int i = 1; i <= directorNumber; i++) {
            if (!itr.hasNext()) {
                itr = list.iterator();
            }
            portsSetForDirector = itr.next();
        }
        return portsSetForDirector;
    }

    @Override
    public Method createOrAddVolumesToExportMaskMethod(URI arrayURI, URI exportGroupURI, URI exportMaskURI,
            Map<URI, Integer> volumeMap, TaskCompleter completer) {
        return new Workflow.Method("createOrAddVolumesToExportMask", arrayURI,
                exportGroupURI, exportMaskURI, volumeMap, completer);
    }

    @Override
    public void createOrAddVolumesToExportMask(URI arrayURI, URI exportGroupURI, URI exportMaskURI,
            Map<URI, Integer> volumeMap, TaskCompleter completer, String stepId) {
        try {
            WorkflowStepCompleter.stepExecuting(stepId);
            StorageSystem array = _dbClient.queryObject(StorageSystem.class, arrayURI);
            ExportMask exportMask = _dbClient.queryObject(ExportMask.class, exportMaskURI);

            // If the exportMask isn't found, or has been deleted, fail, ask user to retry.
            if (exportMask == null || exportMask.getInactive()) {
                _log.info(String.format("ExportMask %s deleted or inactive, failing", exportMaskURI));
                ServiceError svcerr = VPlexApiException.errors
                        .createBackendExportMaskDeleted(exportMaskURI.toString(), arrayURI.toString());
                WorkflowStepCompleter.stepFailed(stepId, svcerr);
                return;
            }

            // Protect concurrent operations by locking {host, array} dupple.
            // Lock will be released when workflow step completes.
            List<String> lockKeys = ControllerLockingUtil.getHostStorageLockKeys(
                    _dbClient, ExportGroupType.Host,
                    StringSetUtil.stringSetToUriList(exportMask.getInitiators()), arrayURI);
            getWorkflowService().acquireWorkflowStepLocks(stepId, lockKeys, LockTimeoutValue.get(LockType.VPLEX_BACKEND_EXPORT));

            // Refresh the ExportMask
            BlockStorageDevice device = _blockController.getDevice(array.getSystemType());

            if (!exportMask.hasAnyVolumes()) {
                // We are creating this ExportMask on the hardware! (Maybe not
                // the first time though...)
                // Fetch the Initiators
                List<Initiator> initiators = new ArrayList<Initiator>();
                for (String initiatorId : exportMask.getInitiators()) {
                    Initiator initiator = _dbClient.queryObject(Initiator.class,
                            URI.create(initiatorId));
                    if (initiator != null) {
                        initiators.add(initiator);
                    }
                }
                // Fetch the targets
                List<URI> targets = new ArrayList<URI>();
                for (String targetId : exportMask.getStoragePorts()) {
                    targets.add(URI.create(targetId));
                }
                if (volumeMap != null) {
                    for (URI volume : volumeMap.keySet()) {
                        exportMask.addVolume(volume, volumeMap.get(volume));
                    }
                }
                _dbClient.persistObject(exportMask);
                device.doExportGroupCreate(array, exportMask, volumeMap, initiators, targets,
                        completer);
            } else {
                device.doExportAddVolumes(array, exportMask, volumeMap, completer);
            }

        } catch (Exception ex) {
            _log.error("Failed to create or add volumes to export mask for vmax: ", ex);
            VPlexApiException vplexex = DeviceControllerExceptions.vplex
                    .addStepsForCreateVolumesFailed(ex);
            WorkflowStepCompleter.stepFailed(stepId, vplexex);
        }

    }

    @Override
    public Method deleteOrRemoveVolumesFromExportMaskMethod(URI arrayURI,
            URI exportGroupURI, URI exportMaskURI,
            List<URI> volumes, TaskCompleter completer) {
        return new Workflow.Method("deleteOrRemoveVolumesFromExportMask", arrayURI,
                exportGroupURI, exportMaskURI, volumes, completer);
    }

    @Override
    public void deleteOrRemoveVolumesFromExportMask(URI arrayURI, URI exportGroupURI, URI exportMaskURI,
            List<URI> volumes, TaskCompleter completer, String stepId) {
        try {
            WorkflowStepCompleter.stepExecuting(stepId);
            StorageSystem array = _dbClient.queryObject(StorageSystem.class, arrayURI);
            BlockStorageDevice device = _blockController.getDevice(array.getSystemType());
            ExportMask exportMask = _dbClient.queryObject(ExportMask.class, exportMaskURI);

            // If the exportMask isn't found, or has been deleted, nothing to do.
            if (exportMask == null || exportMask.getInactive()) {
                _log.info(String.format("ExportMask %s inactive, returning success", exportMaskURI));
                WorkflowStepCompleter.stepSucceded(stepId);
                return;
            }

            // Protect concurrent operations by locking {host, array} dupple.
            // Lock will be released when workflow step completes.
            List<String> lockKeys = ControllerLockingUtil.getHostStorageLockKeys(
                    _dbClient, ExportGroupType.Host,
                    StringSetUtil.stringSetToUriList(exportMask.getInitiators()), arrayURI);
            getWorkflowService().acquireWorkflowStepLocks(stepId, lockKeys, LockTimeoutValue.get(LockType.VPLEX_BACKEND_EXPORT));

            // Make sure the completer will complete the workflow. This happens
            // on rollback case.
            if (!completer.getOpId().equals(stepId)) {
                completer.setOpId(stepId);
            }

            Set<String> remainingVolumes = new HashSet<String>();
            if (exportMask.getVolumes() != null) {
                remainingVolumes.addAll(exportMask.getVolumes().keySet());
            }
            for (URI volume : volumes) {
                remainingVolumes.remove(volume.toString());
            }
            // If so, delete the ExportMask.
            if (remainingVolumes.isEmpty()
                    && (exportMask.getExistingVolumes() == null || exportMask.getExistingVolumes()
                            .isEmpty())) {
                device.doExportGroupDelete(array, exportMask, completer);
            } else {
                device.doExportRemoveVolumes(array, exportMask, volumes, completer);
            }
        } catch (Exception ex) {
            _log.error("Failed to delete or remove volumes to export mask for vmax: ", ex);
            VPlexApiException vplexex = DeviceControllerExceptions.vplex
                    .addStepsForCreateVolumesFailed(ex);
            WorkflowStepCompleter.stepFailed(stepId, vplexex);
        }
    }

    public void setSimulation(boolean simulation) {
        this.simulation = simulation;
    }

    public WorkflowService getWorkflowService() {
        return _workflowService;
    }

    @Override
    public void setWorkflowService(WorkflowService _workflowService) {
        this._workflowService = _workflowService;
    }
}
