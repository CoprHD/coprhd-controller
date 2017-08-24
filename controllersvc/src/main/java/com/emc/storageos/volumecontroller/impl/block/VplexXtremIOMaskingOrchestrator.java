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
import com.emc.storageos.db.client.model.StorageProtocol;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.exceptions.DeviceControllerExceptions;
import com.emc.storageos.locking.LockTimeoutValue;
import com.emc.storageos.locking.LockType;
import com.emc.storageos.networkcontroller.impl.NetworkScheduler;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.util.NetworkLite;
import com.emc.storageos.volumecontroller.BlockStorageDevice;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerLockingUtil;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskOnlyRemoveVolumeCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportTaskCompleter;
import com.emc.storageos.volumecontroller.placement.PlacementUtils;
import com.emc.storageos.volumecontroller.placement.StoragePortsAllocator;
import com.emc.storageos.volumecontroller.placement.StoragePortsAllocator.PortAllocationContext;
import com.emc.storageos.volumecontroller.placement.StoragePortsAssigner;
import com.emc.storageos.vplex.api.VPlexApiException;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.Workflow.Method;
import com.emc.storageos.workflow.WorkflowService;
import com.emc.storageos.workflow.WorkflowStepCompleter;

public class VplexXtremIOMaskingOrchestrator extends XtremIOMaskingOrchestrator implements
        VplexBackEndMaskingOrchestrator, Controller {
    private static final Logger _log = LoggerFactory.getLogger(VplexXtremIOMaskingOrchestrator.class);
    private boolean simulation = false;
    private static final int XTREMIO_NUM_PORT_GROUP = 1;
    private static final int MAXIMUM_NUMBER_OF_STORAGE_PORTS_PER_SET = 4;
    private static final int REQUIRED_MINIMUM_NUMBER_OF_STORAGE_PORTS_PER_SET = 2;
    private static final int DEFAULT_NUMBER_OF_PATHS_PER_VPLEX_DIRECTOR = 4;
    private static final int MINIMUM_NUMBER_OF_PATHS_PER_VPLEX_DIRECTOR = 2;
    private int vplexDirectorCount;
    private int xtremIOXbricksCount;
    BlockDeviceController _blockController = null;
    WorkflowService _workflowService = null;

    public VplexXtremIOMaskingOrchestrator() {
    }

    public void setBlockDeviceController(BlockDeviceController blockController) {
        this._blockController = blockController;
    }

    public VplexXtremIOMaskingOrchestrator(DbClient dbClient, BlockDeviceController controller) {
        this._dbClient = dbClient;
        this._blockController = controller;
    }

    public void setVplexDirectorCount(int vplexDirectorCount) {
        this.vplexDirectorCount = vplexDirectorCount;
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
        super.refreshExportMask(storage, device, mask);
        return mask;
    }

    @Override
    public void suggestExportMasksForPlacement(
            StorageSystem storage, BlockStorageDevice device, List<Initiator> initiators,
            ExportMaskPlacementDescriptor placementDescriptor) {
        super.suggestExportMasksForPlacement(storage, device, initiators, placementDescriptor);
    }

    @Override
    public Set<Map<URI, List<List<StoragePort>>>> getPortGroups(StorageSystem array,
            Map<URI, List<StoragePort>> allocatablePorts, Map<URI, NetworkLite> networkMap,
            URI varrayURI, int nInitiatorGroups, Map<URI, Map<String, Integer>> switchToPortNumber,
            Map<URI, PortAllocationContext> contextMap, StringBuilder errorMessages) {
        /**
         * Number of Port Group for XtremIO is always one.
         * - If multiple port groups, each VPLEX Director's initiators will be mapped to multiple ports
         *
         * Single Port Group contains different set of storage ports for each network,
         * so that each VPLEX director's initiators will map to different port set.
         *
         * why allocatePorts() not required:
         * allocatePorts() would return required number of storage ports from a network from unique X-bricks.
         * But we need to select storage ports uniquely across X-bricks & StorageControllers and we need
         * to make use of all storage ports.
         *
         */
        Set<Map<URI, List<List<StoragePort>>>> portGroups = new HashSet<Map<URI, List<List<StoragePort>>>>();

        StringSet netNames = new StringSet();
        // Order the networks from those with fewest ports to those with the most ports.
        List<URI> orderedNetworks = orderNetworksByNumberOfPorts(allocatablePorts);
        for (URI networkURI : orderedNetworks) {
            netNames.add(networkMap.get(networkURI).getLabel());
        }
        _log.info("Calculating PortGroups for Networks: {}", netNames.toString());

        StoragePortsAllocator allocator = new StoragePortsAllocator();
        // Determine if we should check connectivity from the varray.auto_san_zoning
        boolean sanZoningEnabled = false;
        if (!simulation) {
            VirtualArray varray = _dbClient.queryObject(VirtualArray.class, varrayURI);
            if (varray != null && NetworkScheduler.isZoningRequired(_dbClient, varray)) {
                sanZoningEnabled = true;
            }
        }

        /**
         * Till all storage ports been processed:
         * -- get a set of 4 storage ports selected equally across networks
         * -- add this set into network to port List map (each port set within a network will be mapped for different
         * directors)
         */
        Map<URI, List<List<StoragePort>>> useablePorts = new HashMap<URI, List<List<StoragePort>>>();
        Set<String> usedPorts = new HashSet<String>();

        // map of selected X-brick to Storage Controllers across all networks
        Map<String, List<String>> xBricksToSelectedSCs = new HashMap<String, List<String>>();
        // map of network to selected X-bricks
        Map<URI, List<String>> networkToSelectedXbricks = new HashMap<URI, List<String>>();
        do {
            Map<URI, List<StoragePort>> useablePortsSet = getUsablePortsSet(allocatablePorts, orderedNetworks, usedPorts,
                    xBricksToSelectedSCs, networkToSelectedXbricks, networkMap, allocator, sanZoningEnabled, switchToPortNumber,
                    contextMap);
            if (useablePortsSet == null) {
                // if requirement not satisfied
                break;
            }
            for (URI networkURI : useablePortsSet.keySet()) {
                if (!useablePorts.containsKey(networkURI)) {
                    useablePorts.put(networkURI, new ArrayList<List<StoragePort>>());
                }
                useablePorts.get(networkURI).add(useablePortsSet.get(networkURI));
            }
        } while (!isAllPortsLooped(orderedNetworks, allocatablePorts, usedPorts));

        int numPG = XTREMIO_NUM_PORT_GROUP;
        _log.info(String.format("Number of Port Groups: %d", numPG));
        portGroups.add(useablePorts);
        _log.info("Selected network to ports set: {}", useablePorts.entrySet());

        // get number of X-bricks from selected ports
        xtremIOXbricksCount = getXbricksCount(useablePorts);

        return portGroups;
    }

    /**
     * Returns a Set of Storage Ports selected equally across networks. Minimum of 2 and maximum of 4 storage ports.
     * It returns null when all storage ports have been processed and the minimum requirement is not met.
     *
     * @param allocatablePorts
     *            the allocatable ports
     * @param orderedNetworks
     *            the ordered networks
     * @param usedPorts
     *            the used ports
     * @param networkToSelectedXbricks
     * @param xBricksToSelectedSCs
     * @param networkMap
     * @param allocator
     *            Storage Ports Allocator
     * @param sanZoningEnabled
     *            on vArray
     * @return the usable ports set
     */
    private Map<URI, List<StoragePort>> getUsablePortsSet(Map<URI, List<StoragePort>> allocatablePorts, List<URI> orderedNetworks,
            Set<String> usedPorts, Map<String, List<String>> xBricksToSelectedSCs, Map<URI, List<String>> networkToSelectedXbricks,
            Map<URI, NetworkLite> networkMap, StoragePortsAllocator allocator, boolean sanZoningEnabled,
            Map<URI, Map<String, Integer>> switchToPortNumber,
            Map<URI, PortAllocationContext> contextMap) {

        Map<URI, List<StoragePort>> useablePorts = new HashMap<URI, List<StoragePort>>();
        Set<String> usedPortsSet = new HashSet<String>();
        Set<String> portsSelected = new HashSet<String>(usedPorts);

        do {
            int previousSize = usedPortsSet.size();
            Iterator<URI> networkItr = orderedNetworks.iterator();
            while (networkItr.hasNext() && usedPortsSet.size() < MAXIMUM_NUMBER_OF_STORAGE_PORTS_PER_SET) {
                URI networkURI = networkItr.next();
                _log.debug(String.format("network: %s, xBricksToSelectedSCs: %s, networkToSelectedXbricks: %s",
                        networkURI, xBricksToSelectedSCs.entrySet(), networkToSelectedXbricks.get(networkURI)));
                NetworkLite net = networkMap.get(networkURI);
                // Determine if we should check connectivity from the Network's varray.auto_san_zoning
                boolean checkConnectivity = sanZoningEnabled && !StorageProtocol.Transport.IP.name().equals(net.getTransportType());
                Map<String, Integer> switchNumberMap = null;
                PortAllocationContext context = null;
                if (switchToPortNumber != null) {
                    switchNumberMap = switchToPortNumber.get(networkURI);
                }
                if (contextMap != null) {
                    context = contextMap.get(networkURI);
                }
                StoragePort port = getNetworkPortUniqueXbrick(networkURI, allocatablePorts.get(networkURI),
                        portsSelected, networkToSelectedXbricks, xBricksToSelectedSCs, allocator, checkConnectivity,
                        switchNumberMap, context);
                _log.debug("Port selected {} for network {}", port != null ? port.getPortName() : null, networkURI);
                if (port != null) {
                    usedPortsSet.add(port.getPortName());
                    portsSelected.add(port.getPortName());
                    if (!useablePorts.containsKey(networkURI)) {
                        useablePorts.put(networkURI, new ArrayList<StoragePort>());
                    }
                    useablePorts.get(networkURI).add(port);
                }
            }

            // If No ports have been selected in this round, then clear the X-bricks map
            if (previousSize == usedPortsSet.size()) {
                xBricksToSelectedSCs.clear();
                networkToSelectedXbricks.clear();
            }
            _log.debug("Ports selected so far : {}", usedPortsSet);
        } while (usedPortsSet.size() < MAXIMUM_NUMBER_OF_STORAGE_PORTS_PER_SET
                && !isAllPortsLooped(orderedNetworks, allocatablePorts, portsSelected));
        _log.info("Set Done: Ports selected in this set: {}", usedPortsSet);

        if (usedPortsSet.size() < REQUIRED_MINIMUM_NUMBER_OF_STORAGE_PORTS_PER_SET) {
            return null; // requirement not met
        }
        // if usedPortsSet.size() >= 2, satisfies minimum requirement, min 2 paths

        // add to all usedPorts list
        usedPorts.addAll(usedPortsSet);
        return useablePorts;
    }

    /**
     * Gets a storage port for the given network from Unique X-brick/SC.
     *
     * @param networkURI
     *            the network uri
     * @param storagePorts
     *            the storage ports
     * @param usedPorts
     *            the used ports
     * @param networkToSelectedXbricks
     *            the network to selected x-bricks
     * @param xBricksToSelectedSCs
     *            the x-bricks to selected SCs
     * @param allocator
     *            Storage Port Allocator
     * @param checkConnectivity
     * @return the network port unique x brick
     */
    private StoragePort getNetworkPortUniqueXbrick(URI networkURI, List<StoragePort> storagePorts, Set<String> usedPorts,
            Map<URI, List<String>> networkToSelectedXbricks, Map<String, List<String>> xBricksToSelectedSCs,
            StoragePortsAllocator allocator, boolean checkConnectivity, Map<String, Integer> switchNumberMap,
            PortAllocationContext context) {
        /**
         * Input:
         * -List of network's storage ports;
         * -X-bricks already chosen for this network;
         * -X-bricks already chosen for all networks with StorageControllers (SC) chosen:
         *
         * Choose a storage port based on below logic:
         * -See if there is a port from X-brick other than allNetworkXbricks (select different SC for the selected
         * X-brick)
         * -If not, see if there is a port from X-brick other than networkXbricks (select different SC for the selected
         * X-brick)
         */
        StoragePort port = null;
        if (networkToSelectedXbricks.get(networkURI) == null) {
            networkToSelectedXbricks.put(networkURI, new ArrayList<String>());
        }
        boolean isSwitchAffinity = false;
        Map<StoragePort, String> portSwitchMap = null;
        if (switchNumberMap != null) {
            // switch affinity is on
            isSwitchAffinity = true;
            if (!simulation) {
                portSwitchMap = getPortSwitchMap(storagePorts);
            } else if (simulation && context != null) {
                portSwitchMap = allocator.getPortSwitchMap(context);
            }
            
        }
        List<StoragePort> uniqueXBrickPorts = new ArrayList<StoragePort>();
        for (StoragePort sPort : storagePorts) {
            // Do not choose a port that has already been chosen
            if (!usedPorts.contains(sPort.getPortName()) && isPortConnected(allocator, sPort, checkConnectivity)) {
                String[] splitArray = sPort.getPortGroup().split(Constants.HYPHEN);
                String xBrick = splitArray[0];
                String sc = splitArray[1];
                // select port from unique X-brick/SC
                if (!xBricksToSelectedSCs.containsKey(xBrick)) {
                    if (isSwitchAffinity) {
                        uniqueXBrickPorts.add(sPort);
                    } else {
                        port = sPort;
                        addSCToXbrick(xBricksToSelectedSCs, xBrick, sc);
                        networkToSelectedXbricks.get(networkURI).add(xBrick);
                        break;
                    }
                }
            }
        }
        if (!uniqueXBrickPorts.isEmpty()) {
            port = getSwitchAffinityPort(uniqueXBrickPorts, switchNumberMap, portSwitchMap, networkToSelectedXbricks, 
                    xBricksToSelectedSCs, networkURI);  
        } 
        if (port == null) {
            uniqueXBrickPorts.clear();
            for (StoragePort sPort : storagePorts) {
                // Do not choose a port that has already been chosen
                if (!usedPorts.contains(sPort.getPortName()) && isPortConnected(allocator, sPort, checkConnectivity)) {
                    String[] splitArray = sPort.getPortGroup().split(Constants.HYPHEN);
                    String xBrick = splitArray[0];
                    String sc = splitArray[1];
                    // select port from unique X-brick/SC for this network
                    if (!networkToSelectedXbricks.get(networkURI).contains(xBrick)
                            && (xBricksToSelectedSCs.get(xBrick) == null || !xBricksToSelectedSCs.get(xBrick).contains(sc))) {
                        if (isSwitchAffinity) {
                            uniqueXBrickPorts.add(sPort);
                        } else {
                            port = sPort;
                            addSCToXbrick(xBricksToSelectedSCs, xBrick, sc);
                            networkToSelectedXbricks.get(networkURI).add(xBrick);
                            break;
                        }
                    }
                }
            }
            if (!uniqueXBrickPorts.isEmpty()) {
                port = getSwitchAffinityPort(uniqueXBrickPorts, switchNumberMap, portSwitchMap, networkToSelectedXbricks, 
                        xBricksToSelectedSCs, networkURI);  
            } 
        }
        return port;
    }

    /**
     * Checks if storage port is connected depends on Network's varray.auto_san_zoning.
     *
     */
    private boolean isPortConnected(StoragePortsAllocator allocator, StoragePort sPort, boolean checkConnectivity) {
        if (checkConnectivity && (PlacementUtils.getSwitchName(sPort, _dbClient) == null)) {
            return false;
        }
        return true;
    }

    /**
     * Adds the selected Storage controller to X-brick in the xBricksToSelectedSCs map.
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
     * Checks if is all given ports have been processed or selected.
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
     * Order the networks from those with least ports to those with most ports.
     *
     * @param allocatablePorts
     *            -- Map of Network URI to list of ports
     * @return ordered list of Network URIs
     */
    private List<URI> orderNetworksByNumberOfPorts(Map<URI, List<StoragePort>> allocatablePorts) {
        List<URI> orderedNetworks = new ArrayList<URI>();

        Map<Integer, Set<URI>> numPortsToNetworkSet = new HashMap<Integer, Set<URI>>();
        for (URI networkURI : allocatablePorts.keySet()) {
            int numPorts = allocatablePorts.get(networkURI).size();
            if (numPortsToNetworkSet.get(numPorts) == null) {
                numPortsToNetworkSet.put(numPorts, new HashSet<URI>());
            }
            numPortsToNetworkSet.get(numPorts).add(networkURI);
        }

        for (Set<URI> networkURIs : numPortsToNetworkSet.values()) {
            orderedNetworks.addAll(networkURIs);
        }
        return orderedNetworks;
    }

    /**
     * Gets the number of X-bricks from the selected ports.
     *
     * @param useablePorts
     *            the port groups
     * @return the xbricks count
     */
    private int getXbricksCount(Map<URI, List<List<StoragePort>>> useablePorts) {
        Set<String> xBricks = new HashSet<String>();
        for (List<List<StoragePort>> portSet : useablePorts.values()) {
            for (List<StoragePort> ports : portSet) {
                for (StoragePort port : ports) {
                    xBricks.add(port.getPortGroup().split(Constants.HYPHEN)[0]);
                }
            }
        }
        return xBricks.size();
    }

    private List<StoragePort> allocatePorts(StoragePortsAllocator allocator,
            List<StoragePort> candidatePorts, int portsRequested, NetworkLite net, URI varrayURI, 
            Map<String, Integer> switchToPortNumber, PortAllocationContext context) {
        return VPlexBackEndOrchestratorUtil.allocatePorts(allocator, candidatePorts, portsRequested, net, varrayURI,
                simulation, _blockScheduler, _dbClient, switchToPortNumber, context);
    }

    @Override
    public StringSetMap configureZoning(Map<URI, List<List<StoragePort>>> portGroup,
            Map<String, Map<URI, Set<Initiator>>> initiatorGroup, Map<URI, NetworkLite> networkMap, 
            StoragePortsAssigner assigner, Map<URI, String> initiatorSwitchMap,
            Map<URI, Map<String, List<StoragePort>>> switchStoragePortsMap,
            Map<URI, String> portSwitchMap) {

        StringSetMap zoningMap = new StringSetMap();
        // Set up a map to track port usage so that we can use all ports more or less equally.
        Map<StoragePort, Integer> portUsage = new HashMap<StoragePort, Integer>();
        // Iterate through each of the directors, matching each of its initiators
        // with one port. This will ensure not to violate four paths per director.

        // select number of paths per VPLEX director
        // if X-bricks count is less than director count, choose only 2 initiators from each director
        // leaving other initiators for future scale of X-bricks
        int pathsPerDirector = DEFAULT_NUMBER_OF_PATHS_PER_VPLEX_DIRECTOR; // default 4 initiators in director
        if (xtremIOXbricksCount < vplexDirectorCount) {
            pathsPerDirector = MINIMUM_NUMBER_OF_PATHS_PER_VPLEX_DIRECTOR;
        }
        _log.info(String.format("VPLEX Directors: %s, X-bricks: %s, Number of paths per VPLEX Director: %s", vplexDirectorCount,
                xtremIOXbricksCount, pathsPerDirector));

        boolean isSwitchAffinity = false;
        if (initiatorSwitchMap != null && !initiatorSwitchMap.isEmpty() &&
                switchStoragePortsMap != null && !switchStoragePortsMap.isEmpty()) {
            isSwitchAffinity = true;
        }

        int directorNumber = 1;
        for (String director : initiatorGroup.keySet()) {
            // split initiators across networks depending on number of paths per director
            int numberOfNetworksForDirector = initiatorGroup.get(director).keySet().size();
            int initiatorsPerNetworkForDirector = pathsPerDirector / numberOfNetworksForDirector;
            _log.info("Number of Initiators that must be chosen per network for a director: {}", initiatorsPerNetworkForDirector);

            for (URI networkURI : initiatorGroup.get(director).keySet()) {
                int numberOfInitiatorsPerNetwork = 0;
                NetworkLite net = networkMap.get(networkURI);
                for (Initiator initiator : initiatorGroup.get(director).get(networkURI)) {
                    // If there are no ports on the initiators network, too bad...
                    if (portGroup.get(networkURI) == null) {
                        _log.info(String.format("%s -> no ports in network",
                                initiator.getInitiatorPort()));
                        continue;
                    }
                    // if desired number of initiator paths chosen for network
                    if (numberOfInitiatorsPerNetwork >= initiatorsPerNetworkForDirector) {
                        _log.info(String.format("Maximum paths per network %s (%d) reached for Director %s",
                                net.getLabel(), numberOfInitiatorsPerNetwork, director));
                        break;
                    }
                    
                    List<StoragePort> assignablePorts = null;
                    if (isSwitchAffinity) {
                        // find the ports with the same switch as the initiator
                        String switchName = initiatorSwitchMap.get(initiator.getId());
                        if (!switchName.equals(NullColumnValueGetter.getNullStr())) {
                            Map<String, List<StoragePort>>switchMap = switchStoragePortsMap.get(networkURI);
                            if (switchMap != null) {
                                List<StoragePort> switchPorts = switchMap.get(switchName);
                                if (switchPorts != null && !switchPorts.isEmpty()) {
                                    _log.info(String.format("Found the same switch ports, switch is %s", switchName));
                                    assignablePorts = switchPorts;
                                }
                            }
                        }
                    }
                    
                    List<StoragePort> portList = getStoragePortSetForDirector(portGroup.get(networkURI), directorNumber);
                    if (assignablePorts != null) {
                        assignablePorts.retainAll(portList);
                    }
                    
                    if (assignablePorts == null || assignablePorts.isEmpty()) {
                        assignablePorts = portList;
                    }
                    // find a port for the initiator
                    StoragePort storagePort = VPlexBackEndOrchestratorUtil.assignPortToInitiator(assigner,
                            assignablePorts, net, initiator, portUsage, null);
                    if (storagePort != null) {
                        _log.info(String.format("%s %s   %s %s -> %s  %s %s", director, net.getLabel(),
                                initiator.getInitiatorPort(), initiatorSwitchMap.get(initiator.getId()), storagePort.getPortNetworkId(),
                                storagePort.getPortName(), portSwitchMap.get(storagePort.getId())));
                        StringSet ports = new StringSet();
                        ports.add(storagePort.getId().toString());
                        zoningMap.put(initiator.getId().toString(), ports);
                        numberOfInitiatorsPerNetwork++;
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
     * @param list
     *            the list
     * @param directorNumber
     *            the director number
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
            Map<URI, Integer> volumeMap, List<URI> initiatorURIs, TaskCompleter completer) {
        return new Workflow.Method("createOrAddVolumesToExportMask", arrayURI,
                exportGroupURI, exportMaskURI, volumeMap, initiatorURIs, completer);
    }

    @Override
    public void createOrAddVolumesToExportMask(URI arrayURI, URI exportGroupURI, URI exportMaskURI,
            Map<URI, Integer> volumeMap, List<URI> initiatorURIs, TaskCompleter completer, String stepId) {
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
                device.doExportCreate(array, exportMask, volumeMap, initiators, targets,
                        completer);
            } else {
                List<Initiator> initiators = new ArrayList<Initiator>();
                for (String initiatorId : exportMask.getInitiators()) {
                    Initiator initiator = _dbClient.queryObject(Initiator.class,
                            URI.create(initiatorId));
                    if (initiator != null) {
                        initiators.add(initiator);
                    }
                }
                device.doExportAddVolumes(array, exportMask, initiators, volumeMap, completer);
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
            List<URI> volumes, List<URI> initiatorURIs) {
        return new Workflow.Method("deleteOrRemoveVolumesFromExportMask", arrayURI,
                exportGroupURI, exportMaskURI, volumes, initiatorURIs);
    }

    @Override
    public void deleteOrRemoveVolumesFromExportMask(URI arrayURI, URI exportGroupURI, URI exportMaskURI,
            List<URI> volumes, List<URI> initiatorURIs, String stepId) {
        ExportTaskCompleter completer = null;
        try {
            completer = new ExportMaskOnlyRemoveVolumeCompleter(exportGroupURI,
                    exportMaskURI, volumes, stepId);
            WorkflowStepCompleter.stepExecuting(stepId);
            StorageSystem array = _dbClient.queryObject(StorageSystem.class, arrayURI);
            BlockStorageDevice device = _blockController.getDevice(array.getSystemType());
            ExportMask exportMask = _dbClient.queryObject(ExportMask.class, exportMaskURI);

            // If the exportMask isn't found, or has been deleted, nothing to do.
            if (exportMask == null || exportMask.getInactive()) {
                _log.info(String.format("ExportMask %s inactive, returning success", exportMaskURI));
                completer.ready(_dbClient);
                return;
            }

            // Protect concurrent operations by locking {host, array} dupple.
            // Lock will be released when workflow step completes.
            List<String> lockKeys = ControllerLockingUtil.getHostStorageLockKeys(
                    _dbClient, ExportGroupType.Host,
                    StringSetUtil.stringSetToUriList(exportMask.getInitiators()), arrayURI);
            getWorkflowService().acquireWorkflowStepLocks(stepId, lockKeys, LockTimeoutValue.get(LockType.VPLEX_BACKEND_EXPORT));

            // Determine if we're deleting the last volume in the mask.
            StringMap maskVolumesMap = exportMask.getVolumes();
            Set<String> remainingVolumes = new HashSet<String>();
            List<URI> passedVolumesInMask = new ArrayList<>(volumes);
            if (maskVolumesMap != null) {
                remainingVolumes.addAll(maskVolumesMap.keySet());
            }
            for (URI volume : volumes) {
                remainingVolumes.remove(volume.toString());
                
                // Remove any volumes from the volume list that are no longer
                // in the export mask. When a failure occurs removing a backend
                // volume from a mask, the rollback method will try and remove it
                // again. However, in the case of a distributed volume, one side
                // may have succeeded, so we will try and remove it again. Previously,
                // this was not a problem. However, new validation exists at the
                // block level that checks to make sure the volume to remove is
                // actually in the mask, which now causes a failure when you remove
                // it a second time. So, we check here and remove any volumes that
                // are not in the mask to handle this condition.
                if ((maskVolumesMap != null) && (!maskVolumesMap.keySet().contains(volume.toString()))){
                    passedVolumesInMask.remove(volume);
                }
            }
            
            // None of the volumes is in the export mask, so we are done.
            if (passedVolumesInMask.isEmpty()) {
                _log.info("None of these volumes {} are in export mask {}", volumes, exportMask.forDisplay());
                completer.ready(_dbClient);
                return;
            }

            // If it is last volume and there are no existing volumes, delete the ExportMask.
            if (remainingVolumes.isEmpty()
                    && !exportMask.hasAnyExistingVolumes()) {
                device.doExportDelete(array, exportMask, passedVolumesInMask, initiatorURIs, completer);
            } else {
                List<Initiator> initiators = null;
                if (initiatorURIs != null && !initiatorURIs.isEmpty()) {
                    initiators = _dbClient.queryObject(Initiator.class, initiatorURIs);
                }
                device.doExportRemoveVolumes(array, exportMask, passedVolumesInMask, initiators, completer);
            }
            completer.ready(_dbClient);
        } catch (Exception ex) {
            _log.error("Failed to delete or remove volumes to export mask for vmax: ", ex);
            VPlexApiException vplexex = DeviceControllerExceptions.vplex
                    .addStepsForCreateVolumesFailed(ex);
            completer.error(_dbClient, vplexex);
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
    
    /**
     * Get storage port to switch name map
     *  
     * @param storagePorts all storage ports
     * @return the map of storage port to switch name
     */
    private Map<StoragePort, String> getPortSwitchMap(List<StoragePort> storagePorts) {
        Map<StoragePort, String> result = new HashMap<StoragePort, String>();
        for (StoragePort port: storagePorts) {
            String switchName = PlacementUtils.getSwitchName(port, _dbClient);
            if (switchName != null && !switchName.isEmpty()) {
                result.put(port, switchName);
            }
        }
        return result;
    }
    
    /**
     * Get the first storage port whose switch name exists in the switchNumberMap if possible, otherwise, return the first
     * port in the input port list.
     * 
     * @param ports ports to be selected
     * @param switchNumberMap switch name to number of ports map
     * @param portSwitchMap port to switch name map
     * @param networkToSelectedXbricks will be updated after the port is selected
     * @param xBricksToSelectedSCs will be updated after the port is selected
     * @return
     */
    private StoragePort getSwitchAffinityPort(List<StoragePort>ports, Map<String, Integer>switchNumberMap, 
            Map<StoragePort, String> portSwitchMap, Map<URI, List<String>> networkToSelectedXbricks, 
            Map<String, List<String>> xBricksToSelectedSCs, URI netURI) {
        StoragePort result = null;
        if (portSwitchMap == null || switchNumberMap == null || ports == null || ports.isEmpty()) {
            return result;
        }
        for (StoragePort port : ports) {
            String switchName = portSwitchMap.get(port);
            if (switchName == null) {
                continue;
            }
            Integer count = switchNumberMap.get(switchName);
            if (count != null && count > 0) {
                result  = port;
                // update the switchNumberMap
                if (count > 1) {
                    switchNumberMap.put(switchName, count--);
                } else {
                    switchNumberMap.remove(switchName);
                }
                break;
            }
        }
        if (result == null) {
            result = ports.get(0);
            _log.info(String.format("Not found the same switch port: %s", result.getPortName()));
        }
        String[] splitArray = result.getPortGroup().split(Constants.HYPHEN);
        String xBrick = splitArray[0];
        String sc = splitArray[1];
        addSCToXbrick(xBricksToSelectedSCs, xBrick, sc);
        networkToSelectedXbricks.get(netURI).add(xBrick);
        
        return result;
    }
    
}
