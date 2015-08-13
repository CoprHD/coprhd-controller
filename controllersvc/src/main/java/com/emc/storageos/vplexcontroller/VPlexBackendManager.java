/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplexcontroller;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageProtocol.Transport;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.VirtualPool.SystemType;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.db.client.util.WWNUtility;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.networkcontroller.impl.NetworkDeviceController;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.util.NetworkLite;
import com.emc.storageos.util.NetworkUtil;
import com.emc.storageos.volumecontroller.BlockStorageDevice;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.block.AbstractDefaultMaskingOrchestrator;
import com.emc.storageos.volumecontroller.impl.block.BlockDeviceController;
import com.emc.storageos.volumecontroller.impl.block.ExportMaskPlacementDescriptor;
import com.emc.storageos.volumecontroller.impl.block.VPlexHDSMaskingOrchestrator;
import com.emc.storageos.volumecontroller.impl.block.VPlexVmaxMaskingOrchestrator;
import com.emc.storageos.volumecontroller.impl.block.VPlexVnxMaskingOrchestrator;
import com.emc.storageos.volumecontroller.impl.block.VplexBackEndMaskingOrchestrator;
import com.emc.storageos.volumecontroller.impl.block.VplexXtremIOMaskingOrchestrator;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskAddVolumeCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskOnlyRemoveVolumeCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportTaskCompleter;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.volumecontroller.placement.BlockStorageScheduler;
import com.emc.storageos.volumecontroller.placement.StoragePortsAssigner;
import com.emc.storageos.volumecontroller.placement.StoragePortsAssignerFactory;
import com.emc.storageos.vplex.api.VPlexApiException;
import com.emc.storageos.workflow.Workflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class VPlexBackendManager {
    private DbClient _dbClient;
    private BlockDeviceController _blockDeviceController;
    private VPlexDeviceController _vplexDeviceController;
    private BlockStorageScheduler _blockStorageScheduler;
    private NetworkDeviceController _networkDeviceController;
    private VPlexApiLockManager _vplexApiLockManager;
    private URI _projectURI, _tenantURI;
    private static final Logger _log = LoggerFactory.getLogger(VPlexBackendManager.class);
    private static final int initiatorLimit = 32;    // maximum initiators to an array
    private static final URI nullURI = NullColumnValueGetter.getNullURI();
    private static int NUMBER_INITIATORS_IN_DIRECTOR = 4;
    private static int MAX_CHARS_IN_VPLEX_NAME = 32;
    private static final long MAX_LOCK_WAIT_SECONDS = 3600;

    private static final String ZONING_STEP = "zoning";
    private static final String EXPORT_STEP = AbstractDefaultMaskingOrchestrator.EXPORT_GROUP_MASKING_TASK;

    // Initiator id key to Initiator object
    private Map<String, Initiator> _idToInitiatorMap = new HashMap<String, Initiator>();
    // Port wwn (no colons) of Initiator to Initiator object
    private Map<String, Initiator> _portWwnToInitiatorMap = new HashMap<String, Initiator>();
    // Port wwn of VPLEX port to VPlex cluster identifier "1" or "2"
    private Map<String, String> _portWwnToClusterMap = new HashMap<String, String>();
    private static final String EXPORT_NAME_PREFIX = "VPlex_%s_%s";

    private Map<String, Initiator> idToInitiatorMap = new HashMap<String, Initiator>();
    private Map<String, Initiator> portWwnToInitiatorMap = new HashMap<String, Initiator>();
    private Map<String, String> portWwnToClusterMap = new HashMap<String, String>();

    // Map of network URI to back-end Storage Ports on the VPlex that serve as Initiators.
    // This is specific to the varray that is being used.
    // Key is network URi. Value is list of VPlex back-end ports.
    private Map<URI, List<StoragePort>> _initiatorPortMap = new HashMap<URI, List<StoragePort>>();
    // Map of director name to Initiator ids
    private Map<String, Set<String>> _directorToInitiatorIds = new HashMap<String, Set<String>>();
    private Map<String, URI> _portWwnToNetwork = new HashMap<String, URI>();
    private Map<URI, NetworkLite> _networkMap = new HashMap<URI, NetworkLite>();
    private List<Initiator> _initiators = new ArrayList<Initiator>();
    private Map<String, URI> _initiatorIdToNetwork = new HashMap<String, URI>();
    private String _cluster = null;

    /**
     * This variant of the constructor should only be used for simulator testing to
     * call the getInitiatorGroups() method.
     */
    public VPlexBackendManager() {

    }

    /**
     * This class is not used as a bean. Rather when VPlexDeviceController needs to adjust the
     * back ExportMasks on an array, it instantiates an instance of this class to do the work.
     * 
     * @param dbClient
     * @param vplexDeviceController
     * @param blockDeviceController
     * @param blockStorageScheduler
     * @param networkDeviceController
     * @param projectURI
     * @param tenantURI
     * @param vplexApiLockManager
     */
    public VPlexBackendManager(DbClient dbClient, VPlexDeviceController vplexDeviceController,
            BlockDeviceController blockDeviceController,
            BlockStorageScheduler blockStorageScheduler, NetworkDeviceController networkDeviceController,
            URI projectURI, URI tenantURI, VPlexApiLockManager vplexApiLockManager) {
        this._dbClient = dbClient;
        this._vplexDeviceController = vplexDeviceController;
        this._blockDeviceController = blockDeviceController;
        this._blockStorageScheduler = blockStorageScheduler;
        this._networkDeviceController = networkDeviceController;
        this._projectURI = projectURI;
        this._tenantURI = tenantURI;
        this._vplexApiLockManager = vplexApiLockManager;
    }

    /**
     * Find the orchestrator to use for a specific array type.
     * 
     * @param system StorageSystem (array) - used to determine type of array
     * @return VPlexBackEndMaskingOrchestrator
     * @throws DeviceControllerException
     */
    private VplexBackEndMaskingOrchestrator getOrch(StorageSystem system) throws DeviceControllerException {
        if (system.getSystemType().equals(SystemType.vmax.name())) {
            return new VPlexVmaxMaskingOrchestrator(_dbClient, _blockDeviceController);
        }
        if (system.getSystemType().equals(SystemType.vnxblock.name())) {
            return new VPlexVnxMaskingOrchestrator(_dbClient, _blockDeviceController);
        }
        if (system.getSystemType().equals(SystemType.xtremio.name())) {
            return new VplexXtremIOMaskingOrchestrator(_dbClient, _blockDeviceController);
        }
        if (system.getSystemType().equals(SystemType.hds.name())) {
            return new VPlexHDSMaskingOrchestrator(_dbClient, _blockDeviceController);
        }
        throw DeviceControllerException.exceptions.unsupportedVPlexArray(
                system.getSystemType(), system.getLabel());
    }

    /**
     * Build all the data structures needed for analysis.
     * These are saved in class instance variables starting with underscore.
     * 
     * @param vplex -- VPlex StorageSystem
     * @param array -- VMAX/VNX/... StorageSystem
     * @param varrayURI -- varray URI
     */
    private void buildDataStructures(StorageSystem vplex, StorageSystem array, URI varrayURI) {

        // The map of Port WWN to cluster id will be used for validation
        _portWwnToClusterMap = getPortIdToClusterMap(vplex);

        // Get the initiator port map for this VPLEX and storage system
        // for this volume's virtual array.
        _initiatorPortMap = getInitiatorPortsForArray(
                vplex.getId(), array.getId(), varrayURI);
        _portWwnToNetwork = getPortWwnToNetwork(_initiatorPortMap);
        populateNetworkMap(_initiatorPortMap.keySet(), _networkMap);

        // If there are no networks that can be used, error.
        if (_initiatorPortMap.isEmpty()) {
            throw DeviceControllerException.exceptions
                    .noNetworksConnectingVPlexToArray(vplex.getNativeGuid(),
                            array.getNativeGuid());
        }

        // Generate maps of wwn to initiators.

        for (URI networkURI : _initiatorPortMap.keySet()) {
            List<StoragePort> ports = _initiatorPortMap.get(networkURI);
            for (StoragePort port : ports) {
                Initiator initiator = ExportUtils.getInitiator(port.getPortNetworkId(), _dbClient);
                if (initiator != null && !initiator.getInactive()
                        && initiator.getRegistrationStatus().equals(RegistrationStatus.REGISTERED.name())) {
                    _initiators.add(initiator);
                    _idToInitiatorMap.put(initiator.getId().toString(), initiator);
                    _portWwnToInitiatorMap.put(
                            Initiator.normalizePort(initiator.getInitiatorPort()), initiator);
                    _initiatorIdToNetwork.put(initiator.getId().toString(), networkURI);
                }
            }
        }
        _directorToInitiatorIds = getDirectorToInitiatorIds(_initiatorPortMap);
    }

    /**
     * Choose one of the existing Export Masks (on VMAX: masking views) if possible in
     * which to place the volume to be exported to the VPlex. Otherwise ExportMask(s)
     * will be generated and one will be chosen from the generated set.
     * 
     * @param vplex
     *            [IN] - VPlex storage system
     * @param array
     *            [IN] - Storage Array storage system
     * @param varrayURI
     *            [IN] - Virtual array
     * @param volumeMap
     *            [IN] - Map of URI to their corresponding Volume object
     * @return ExportMaskPlacementDescriptor - data structure that will indicate the mapping of ExportMasks to
     *         ExportGroups and ExportMasks to Volumes.
     * @throws ControllerException
     */
    public ExportMaskPlacementDescriptor chooseBackendExportMask(StorageSystem vplex, StorageSystem array, URI varrayURI,
            Map<URI, Volume> volumeMap)
            throws ControllerException {
        _log.info(String.format("Searching for existing ExportMasks between Vplex %s (%s) and Array %s (%s) in Varray %s",
                vplex.getLabel(), vplex.getNativeGuid(), array.getLabel(), array.getNativeGuid(), varrayURI));
        long startTime = System.currentTimeMillis();
        // Build the data structures used for analysis and validation.
        buildDataStructures(vplex, array, varrayURI);

        VplexBackEndMaskingOrchestrator vplexBackendOrchestrator = getOrch(array);
        BlockStorageDevice storageDevice = _blockDeviceController.getDevice(array.getSystemType());

        // Lock operation.
        String lockName = _vplexApiLockManager.getLockName(vplex.getId(), _cluster, array.getId());
        boolean lockAcquired = false;

        try {
            if (_vplexApiLockManager != null) {
                lockAcquired = _vplexApiLockManager.acquireLock(lockName, MAX_LOCK_WAIT_SECONDS);
                if (!lockAcquired) {
                    _log.info("Timed out waiting on lock- PROCEEDING ANYWAY!");
                }
            }

            // Initialize the placement data structure
            ExportMaskPlacementDescriptor placementDescriptor = ExportMaskPlacementDescriptor.create(_tenantURI, _projectURI, vplex,
                    array, varrayURI, volumeMap, _idToInitiatorMap.values());

            // VplexBackEndMaskingOrchestrator#suggestExportMasksForPlacement should fill in the rest of the
            // placement data structures, such that decisions on how to reuse the ExportMasks can be done here.
            vplexBackendOrchestrator.suggestExportMasksForPlacement(array, storageDevice, _initiators, placementDescriptor);

            // Check if any export mask got renamed, if they did, change the name in the ViPR DB
            checkForRenamedExportMasks(placementDescriptor.getMasks());

            // Apply the filters that will remove any ExportMasks that do not fit the expected VPlex masking paradigm
            Set<URI> invalidMasks = filterExportMasksByVPlexRequirements(vplex, array, varrayURI, placementDescriptor);

            // Check to see if there are any available ExportMasks that can be used.
            // If not, we will attempt to generate some.
            if (!placementDescriptor.hasMasks()) {
                createVPlexBackendExportMasks(vplex, array, varrayURI, placementDescriptor, invalidMasks);
            }

            // At this point, we have:
            //
            // a). Requested that the backend StorageArray provide us with a list of ExportMasks that can support the initiators + volumes.
            // b). Process the suggested ExportMasks in case they had their names changed
            // c). Filtered out any ExportMasks that do not fit the VPlex masking paradigm
            // OR
            // d). Created a new ExportMask to support the initiators + volumes
            //
            // We will now run the final placement based on a strategy determined by looking at the placementDescriptor
            VPlexBackendPlacementStrategyFactory.create(_dbClient, placementDescriptor).execute();
            long elapsed = System.currentTimeMillis() - startTime;
            _log.info(String.format("PlacementDescriptor processing took %f seconds", (double) elapsed / (double) 1000));
            _log.debug("PlacementDescriptor was created:%n%s", placementDescriptor.toString());
            return placementDescriptor;

        } finally {
            if (lockAcquired) {
                _vplexApiLockManager.releaseLock(lockName);
            }
        }
    }

    /**
     * Validates that an ExportMask can be used.
     * There are comments for each rule that is validated below.
     * 
     * @param vplex
     * @param array
     * @param map of Network to Vplex StoragePort list.
     * @param mask
     * @param invalidMaskscontains
     * @param returns true if passed validation
     */
    private boolean validateExportMask(StorageSystem vplex, StorageSystem array, URI varrayURI,
            Map<URI, List<StoragePort>> initiatorPortMap, ExportMask mask, Set<URI> invalidMasks) {

        boolean passed = true;

        // Rule 1. An Export Mask must have at least two initiators from each director.
        // This is a warning if the ExportMask is non-ViPR.
        for (String director : _directorToInitiatorIds.keySet()) {
            int portsInDirector = 0;
            for (String initiatorId : _directorToInitiatorIds.get(director)) {
                Initiator initiator = _idToInitiatorMap.get(initiatorId);
                String initiatorPortWwn = Initiator.normalizePort(initiator.getInitiatorPort());
                if (mask.hasExistingInitiator(initiatorPortWwn)) {
                    portsInDirector++;
                } else if (mask.hasUserInitiator(initiatorPortWwn)) {
                    portsInDirector++;
                }
            }
            if (portsInDirector < 2) {
                if (mask.getCreatedBySystem()) {    // ViPR created
                    _log.info(String.format(
                            "ExportMask %s disqualified because it only has %d back-end ports from %s (requires two)",
                            mask.getMaskName(), portsInDirector, director));
                    invalidMasks.add(mask.getId());
                    passed = false;
                } else {    // non ViPR created
                    _log.info(String.format(
                            "Warning: ExportMask %s only has %d back-end ports from %s (should have at least two)",
                            mask.getMaskName(), portsInDirector, director));
                }
            }
        }

        // Rule 2. The Export Mask should have at least two ports. Four are recommended.
        Set<String> usablePorts = new StringSet();
        if (mask.getStoragePorts() != null) {
            for (String portId : mask.getStoragePorts()) {
                StoragePort port = _dbClient.queryObject(StoragePort.class, URI.create(portId));
                if (port == null || port.getInactive()
                        || NullColumnValueGetter.isNullURI(port.getNetwork())) {
                    continue;
                }
                // Validate port network overlaps Initiators and port is tagged for Varray
                StringSet taggedVarrays = port.getTaggedVirtualArrays();
                if (ConnectivityUtil.checkNetworkConnectedToAtLeastOneNetwork(port.getNetwork(), initiatorPortMap.keySet(), _dbClient)
                        && taggedVarrays != null && taggedVarrays.contains(varrayURI.toString())) {
                    usablePorts.add(port.getLabel());
                }
            }
        }

        if (usablePorts.size() < 2) {
            _log.info(String.format("ExportMask %s disqualified because it has less than two usable target ports;"
                    + " usable ports: %s", mask.getMaskName(), usablePorts.toString()));
            passed = false;
        }
        else if (usablePorts.size() < 4) {  // This is a warning
            _log.info(String.format("Warning: ExportMask %s has only %d usable target ports (best practice is at least four);"
                    + " usable ports: %s", mask.getMaskName(), usablePorts.size(), usablePorts.toString()));
        }

        // Rule 3. No mixing of WWNs from both VPLEX clusters.
        // Add the clusters for all existingInitiators to the sets computed from initiators above.
        Set<String> clusters = new HashSet<String>();
        for (String portWwn : _portWwnToClusterMap.keySet()) {
            if (mask.hasExistingInitiator(portWwn) || mask.hasUserInitiator(portWwn)) {
                clusters.add(_portWwnToClusterMap.get(portWwn));
            }
        }
        if (clusters.size() > 1) {
            _log.info(String.format("ExportMask %s disqualified because it contains wwns from both Vplex clusters",
                    mask.getMaskName()));
            passed = false;
        }

        // Rule 4. The ExportMask name should not have NO_VIPR in it.
        if (mask.getMaskName().toUpperCase().contains(ExportUtils.NO_VIPR)) {
            _log.info(String.format("ExportMask %s disqualified because the name contains %s (in upper or lower case) to exclude it",
                    mask.getMaskName(), ExportUtils.NO_VIPR));
            passed = false;
        }

        int volumeCount = (mask.getVolumes() != null) ? mask.getVolumes().size() : 0;
        if (mask.getExistingVolumes() != null) {
            volumeCount += mask.getExistingVolumes().keySet().size();
        }
        if (passed) {
            _log.info(String.format("Validation of ExportMask %s passed; it has %d volumes",
                    mask.getMaskName(), volumeCount));
        } else {
            invalidMasks.add(mask.getId());
        }
        return passed;
    }

    /**
     * Returns the Storage Ports on a VPlex that should be used for a particular
     * storage array. This is done by finding ports in the VPlex and array that have
     * common Networks. Returns a map of NetworkURI to List<StoragePort>.
     * 
     * @param vplexURI The URI of a VPLEX storage system
     * @param arrayURI The URI of a connected backend storage system.
     * @param varrayURI The URI of the virtual array.
     * 
     * @return Map<URI, List<StoragePort>> A map of Network URI to a List<StoragePort>
     */
    Map<URI, List<StoragePort>> getInitiatorPortsForArray(URI vplexURI,
            URI arrayURI, URI varray) throws ControllerException {

        Map<URI, List<StoragePort>> initiatorMap = new HashMap<URI, List<StoragePort>>();

        // First, determine what Transport Zones contain VPlex backend initiators.
        Map<URI, List<StoragePort>> vplexInitiatorMap = ConnectivityUtil.getStoragePortsOfType(_dbClient,
                vplexURI, StoragePort.PortType.backend);

        // Eliminate any VPLEX storage ports that are not explicitly assigned
        // or implicitly connected to the passed varray.
        Set<URI> vplexInitiatorNetworks = new HashSet<URI>();
        vplexInitiatorNetworks.addAll(vplexInitiatorMap.keySet());
        Iterator<URI> vplexInitiatorNetworksIter = vplexInitiatorNetworks.iterator();
        while (vplexInitiatorNetworksIter.hasNext()) {
            URI networkURI = vplexInitiatorNetworksIter.next();
            Iterator<StoragePort> initiatorStoragePortsIter = vplexInitiatorMap.get(networkURI).iterator();
            while (initiatorStoragePortsIter.hasNext()) {
                StoragePort initiatorStoragePort = initiatorStoragePortsIter.next();
                StringSet taggedVArraysForPort = initiatorStoragePort.getTaggedVirtualArrays();
                if ((taggedVArraysForPort == null) || (!taggedVArraysForPort.contains(varray.toString()))) {
                    initiatorStoragePortsIter.remove();
                }
            }

            // If the entry for this network is now empty then
            // remove the entry from the VPLEX initiator port map.
            if (vplexInitiatorMap.get(networkURI).isEmpty()) {
                vplexInitiatorMap.remove(networkURI);
            }
        }

        // Check to see that there are not ports remaining from both clusters.
        Set<String> clusterSet = new HashSet<String>();
        for (URI networkURI : vplexInitiatorMap.keySet()) {
            for (StoragePort port : vplexInitiatorMap.get(networkURI)) {
                String cluster = ConnectivityUtil.getVplexClusterOfPort(port);
                clusterSet.add(cluster);
            }
        }
        if (clusterSet.size() > 1) {
            _log.error(String.format(
                    "Invalid network configuration: Vplex %s has back-end ports from more than one cluster in Varray %s",
                    vplexURI, varray));
            throw DeviceControllerException.exceptions.vplexVarrayMixedClusters(
                    varray.toString(), vplexURI.toString());
        }
        _cluster = clusterSet.iterator().next(); // Record our current cluster.

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

        // Determine which vplex networks overlap the storage array networks.
        // Add the corresponding ports to the list.
        int initiatorCount = 0;
        outter: for (URI networkURI : vplexInitiatorMap.keySet()) {
            if (ConnectivityUtil.checkNetworkConnectedToAtLeastOneNetwork(
                    networkURI, arrayTargetMap.keySet(), _dbClient)) {
                initiatorMap.put(networkURI, new ArrayList<StoragePort>());
                for (StoragePort port : vplexInitiatorMap.get(networkURI)) {
                    initiatorMap.get(networkURI).add(port);
                    if (++initiatorCount >= initiatorLimit) {
                        break outter;
                    }
                }
            }
        }

        // If there are no initiator ports, fail the operation, because we cannot zone.
        if (initiatorMap.isEmpty()) {
            throw VPlexApiException.exceptions.getInitiatorPortsForArrayFailed(vplexURI.toString(),
                    arrayURI.toString());
        }

        return initiatorMap;
    }

    /**
     * Compute the number of paths to use on the back end array.
     * This is done on a per Network basis and then summed together.
     * Within each Network, we determine the number of ports available, and then
     * convert to paths. Currently we don't allocate more paths than initiators.
     * 
     * @param initiatorPortMap -- used to determine networks and initiator counts
     * @param varray -- only Networks in the specified varray are considered
     * @param array -- StorageSystem -- used to determine available ports
     * @return
     */
    private Integer computeNumPaths(Map<URI, List<StoragePort>> initiatorPortMap, URI varray, StorageSystem array) {
        // Get the number of ports per path.
        StoragePortsAssigner assigner = StoragePortsAssignerFactory.getAssigner(array.getSystemType());
        int portsPerPath = assigner.getNumberOfPortsPerPath();
        // Get the array's front end ports
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

    /**
     * Create an ExportGroup.
     * 
     * @param vplex -- StoageSystem of VPLEX
     * @param array -- StorageSystem of array
     * @param initiatorPortMap -- Map of NetworkURI to List<StoragePort> list of VPLEX back-end ports.
     * @param virtualArrayURI -- URI of Virtual Array
     * @param projectURI -- URI of special project for VPLEX. The ExportGroup is put there.
     * @param tenantURI -- URI of tenant for VPLEX project.
     * @return newly created ExportGroup persisted in DB.
     */
    ExportGroup createExportGroup(StorageSystem vplex,
            StorageSystem array, Map<URI, List<StoragePort>> initiatorPortMap,
            URI virtualArrayURI, URI projectURI, URI tenantURI) {

        // Add the initiators into the ExportGroup.
        // Make a combined list of all ports.
        List<StoragePort> backEndPorts = new ArrayList<StoragePort>();
        for (List<StoragePort> ports : initiatorPortMap.values()) {
            backEndPorts.addAll(ports);
        }
        // Note that the backEndPorts could be present as Initiators in many ExportGroups.
        Set<Initiator> initiators = new HashSet<Initiator>();
        for (StoragePort port : backEndPorts) {
            Initiator initiator = ExportUtils.getInitiator(port.getPortNetworkId(), _dbClient);
            if (initiator == null || initiator.getInactive()) {
                _log.info("Did not find initiator for VPLEX back-end port: " + port.getPortNetworkId());
                continue;
            }
            initiators.add(initiator);
        }
        int numPaths = computeNumPaths(initiatorPortMap, virtualArrayURI, array);
        return ExportUtils.createVplexExportGroup(_dbClient, vplex, array, initiators,
                virtualArrayURI, projectURI, tenantURI, numPaths, null);

    }

    /**
     * Remove a list of volumes from the ExportGroup specified.
     * 
     * @param workflow = Workflow steps are to be added to
     * @param waitFor - Wait for completion of this workflow step
     * @param storage - Storage SclusterUnknownystem
     * @param exportGroupURI- Export Group to be processed
     * @param blockObjectList - list of volumes or snapshot (URIs)
     * @return true if any steps added to Workflow
     * @throws DeviceControllerException
     */
    public boolean addWorkflowStepsToRemoveBackendVolumes(Workflow workflow,
            String waitFor, StorageSystem storage,
            URI exportGroupURI, List<URI> blockObjectList)
            throws DeviceControllerException {
        ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
        boolean stepsAdded = false;

        // Read all the ExportMasks
        Map<String, ExportMask> exportMasks = new HashMap<String, ExportMask>();
        Map<String, List<URI>> maskToVolumes = new HashMap<String, List<URI>>();
        for (String maskId : exportGroup.getExportMasks()) {
            ExportMask mask = _dbClient.queryObject(ExportMask.class, URI.create(maskId));
            if (mask == null || mask.getInactive()) {
                continue;
            }
            exportMasks.put(maskId, mask);
            maskToVolumes.put(maskId, new ArrayList<URI>());
        }

        // Determine the ExportMasks in use for each of the volumes.
        // Put this information in the maskToVolumes map.
        for (URI blockObjectURI : blockObjectList) {
            for (ExportMask mask : exportMasks.values()) {
                if (mask.hasVolume(blockObjectURI)) {
                    maskToVolumes.get(mask.getId().toString()).add(blockObjectURI);
                }
            }
        }

        // Now process each Export Mask.
        String previousStepId = waitFor;
        for (ExportMask mask : exportMasks.values()) {
            List<URI> volumes = maskToVolumes.get(mask.getId().toString());
            previousStepId = waitFor;

            // Verify the ExportMask is present on the system, or check if it was renamed
            verifyExportMaskOnSystem(mask, storage);

            if (mask.getCreatedBySystem()) {
                _log.info(String.format("Generating unzoning step for ExportMask %s", mask.getMaskName()));
                // Since this mask was created by the system, we want to unzone it.
                List<URI> maskURIs = new ArrayList<URI>();
                maskURIs.add(mask.getId());
                Workflow.Method zoneRemoveMethod = _networkDeviceController
                        .zoneExportRemoveVolumesMethod(exportGroup.getId(), maskURIs, volumes);
                previousStepId = workflow.createStep(ZONING_STEP,
                        String.format("Removing zones for ExportMask %s", mask.getMaskName()),
                        previousStepId, nullURI, "network-system",
                        _networkDeviceController.getClass(),
                        zoneRemoveMethod, zoneRemoveMethod, null);
            } else {
                _log.info(String.format("ExportMask %s not created by ViPR; no unzoning step", mask.getMaskName()));
            }

            String stepId = workflow.createStepId();
            ExportTaskCompleter exportTaskCompleter;
            // If this ViPR instance created the ExportMask, we may want to use it again,
            // so do not delete it from the database. Otherwise we will delete it if empty.
            exportTaskCompleter = new ExportMaskOnlyRemoveVolumeCompleter(exportGroup.getId(),
                    mask.getId(), volumes, stepId);
            VplexBackEndMaskingOrchestrator orca = getOrch(storage);
            Workflow.Method removeVolumesMethod = orca.deleteOrRemoveVolumesFromExportMaskMethod(
                    storage.getId(), exportGroup.getId(), mask.getId(), volumes, exportTaskCompleter);
            workflow.createStep(EXPORT_STEP,
                    String.format("Removing volume from ExportMask %s", mask.getMaskName()),
                    previousStepId, storage.getId(), storage.getSystemType(), orca.getClass(),
                    removeVolumesMethod, removeVolumesMethod, stepId);
            _log.info(String.format("Generated remove volume from ExportMask %s for volumes %s",
                    mask.getMaskName(), volumes));
            stepsAdded = true;
        }
        return stepsAdded;
    }

    /**
     * Returns a map of port wwn to cluster id ("1" or "2") for vplex ports
     * 
     * @param vplex StorageSystem
     * @return Map of port wwn to cluster id
     */
    private Map<String, String> getPortIdToClusterMap(StorageSystem vplex) {
        Map<String, String> portIdToClusterMap = new HashMap<String, String>();
        List<StoragePort> ports = ConnectivityUtil.getStoragePortsForSystem(_dbClient, vplex.getId());
        for (StoragePort port : ports) {
            portIdToClusterMap.put(WWNUtility.getUpperWWNWithNoColons(port.getPortNetworkId()),
                    ConnectivityUtil.getVplexClusterOfPort(port));
        }
        return portIdToClusterMap;
    }

    /**
     * Gets a mapping of director to the back-end StoragePorts on that director (initiators).
     * 
     * @param initiatorPortMap Map of network URI to list of back-end ports in that Network
     * @return map of director name to Set of initiator ids representing Initiators from that director.
     */
    private Map<String, Set<String>> getDirectorToInitiatorIds(Map<URI, List<StoragePort>> initiatorPortMap) {
        Map<String, Set<String>> directorToInitiatorIds = new HashMap<String, Set<String>>();
        for (URI networkURI : initiatorPortMap.keySet()) {
            List<StoragePort> portsInNetwork = initiatorPortMap.get(networkURI);
            for (StoragePort port : portsInNetwork) {
                String director = port.getPortGroup();
                Initiator initiator = _portWwnToInitiatorMap.get(
                        WWNUtility.getUpperWWNWithNoColons(port.getPortNetworkId()));
                if (initiator != null) {
                    if (!directorToInitiatorIds.containsKey(director)) {
                        directorToInitiatorIds.put(director, new HashSet<String>());
                    }
                    directorToInitiatorIds.get(director).add(initiator.getId().toString());
                }
            }
        }
        return directorToInitiatorIds;
    }

    /**
     * Populates the Network Lite structures
     * 
     * @param initiatorMap
     * @param networkMap
     */
    private void populateNetworkMap(
            Set<URI> networks, Map<URI, NetworkLite> networkMap) {
        for (URI networkURI : networks) {
            NetworkLite lite = NetworkUtil.getNetworkLite(networkURI, _dbClient);
            networkMap.put(networkURI, lite);
        }
    }

    /**
     * Returns a map of port WWN to Network URI
     * 
     * @param initiatorPortMap -- Map of URI to List<StoragePort>
     * @return Map<String, URI> where key is wwn (no colons) and value is network URI
     */
    public Map<String, URI> getPortWwnToNetwork(Map<URI, List<StoragePort>> initiatorPortMap) {
        Map<String, URI> portWwnToNetwork = new HashMap<String, URI>();
        for (URI networkURI : initiatorPortMap.keySet()) {
            for (StoragePort port : initiatorPortMap.get(networkURI)) {
                String portWwn = WWNUtility.getUpperWWNWithNoColons(port.getPortNetworkId());
                portWwnToNetwork.put(portWwn, networkURI);
            }
        }
        return portWwnToNetwork;
    }

    /**
     * Returns a list of all possible allocatable ports on an array for a given set of Networks.
     * 
     * @param arrayURI -- URI of storage array (VMAX, VNX, ...)
     * @param networkURI -- Set<URI> of networks
     * @param varray -- URI of varray
     * @return
     */
    public Map<URI, List<StoragePort>> getAllocatablePorts(URI arrayURI, Set<URI> networkURIs, URI varray) {
        Map<URI, List<StoragePort>> networkToPortsMap = new HashMap<URI, List<StoragePort>>();
        for (URI networkURI : networkURIs) {
            List<StoragePort> ports = _blockStorageScheduler.selectStoragePorts(arrayURI, networkURI, varray);
            networkToPortsMap.put(networkURI, ports);
        }
        return networkToPortsMap;
    }

    public ExportMask generateExportMask(URI arrayURI, String namePrefix,
            Map<URI, List<StoragePort>> portGroup,
            Map<String, Map<URI, Set<Initiator>>> initiatorGroup,
            StringSetMap zoningMap) {

        // Generate one ExportMask per PortGroup.
        ExportMask exportMask = new ExportMask();
        URI uri = URIUtil.createId(ExportMask.class);
        exportMask.setCreatedBySystem(true);
        exportMask.setId(uri);
        exportMask.setStorageDevice(arrayURI);
        String maskName = namePrefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        exportMask.setMaskName(maskName);
        exportMask.setZoningMap(zoningMap);
        // Get the Initiator Group and add it to the mask.
        for (String director : initiatorGroup.keySet()) {
            for (URI networkURI : initiatorGroup.get(director).keySet()) {
                for (Initiator initiator : initiatorGroup.get(director).get(networkURI)) {
                    exportMask.addInitiator(initiator);
                    exportMask.addToUserCreatedInitiators(initiator);
                }
            }
        }
        // Add all the ports in the Port Group
        for (URI networkURI : portGroup.keySet()) {
            for (StoragePort port : portGroup.get(networkURI)) {
                exportMask.addTarget(port.getId());
            }
        }
        // Add the mask to the result
        return exportMask;
    }

    /**
     * Add steps to generate the Workflow to add a volume to the VPLEX backend.
     * The VNX is special, we do zoning after masking.
     * For all the other arrays, we do zoning, then masking.
     * 
     * @param workflow
     * @param dependantStepId
     * @param exportGroup
     * @param exportMask
     * @param volumeMap
     * @param varrayURI
     * @param vplex
     * @param array
     * @return String stepId of last added step
     */
    public String addWorkflowStepsToAddBackendVolumes(
            Workflow workflow, String dependantStepId,
            ExportGroup exportGroup, ExportMask exportMask,
            Map<URI, Volume> volumeMap,
            URI varrayURI,
            StorageSystem vplex, StorageSystem array) {

        // Determine if VNX so can order VNX zoning after masking
        boolean isVNX = (array.getSystemType().equals(DiscoveredDataObject.Type.vnxblock.name())
                || array.getSystemType().equals(DiscoveredDataObject.Type.vnxe.name()));

        Map<URI, Integer> volumeLunIdMap = createVolumeMap(array.getId(),
                exportGroup, volumeMap);
        _dbClient.persistObject(exportGroup);
        String zoningStep = null;
        String maskStepId = workflow.createStepId();
        ExportMaskAddVolumeCompleter createCompleter = new ExportMaskAddVolumeCompleter(
                exportGroup.getId(), exportMask.getId(), volumeLunIdMap, maskStepId);
        List<URI> volumeList = new ArrayList<URI>();
        volumeList.addAll(volumeLunIdMap.keySet());
        ExportTaskCompleter rollbackCompleter =
                new ExportMaskOnlyRemoveVolumeCompleter(exportGroup.getId(),
                        exportMask.getId(), volumeList, maskStepId);

        String previousStepId = dependantStepId;
        if (exportMask.getCreatedBySystem()) {
            _log.info(String.format("Creating zone references for Backend ExportMask %s",
                    exportMask.getMaskName()));
            List<URI> maskURIs = new ArrayList<URI>();
            HashSet<URI> volumes = new HashSet<URI>(volumeLunIdMap.keySet());
            maskURIs.add(exportMask.getId());
            Workflow.Method zoneCreateMethod = _networkDeviceController
                    .zoneExportAddVolumesMethod(exportGroup.getId(), maskURIs, volumes);
            Workflow.Method zoneDeleteMethod = _networkDeviceController
                    .zoneExportRemoveVolumesMethod(exportGroup.getId(), maskURIs, volumes);
            zoningStep = workflow.createStep(ZONING_STEP,
                    String.format("Adding zones for ExportMask %s", exportMask.getMaskName()),
                    (isVNX ? maskStepId : previousStepId), nullURI, "network-system",
                    _networkDeviceController.getClass(),
                    zoneCreateMethod, zoneDeleteMethod, null);
            if (!isVNX) {
                previousStepId = zoningStep;
            }
        }

        VplexBackEndMaskingOrchestrator orca = getOrch(array);
        Workflow.Method updateMaskMethod = orca.createOrAddVolumesToExportMaskMethod(
                array.getId(), exportGroup.getId(), exportMask.getId(), volumeLunIdMap, createCompleter);
        Workflow.Method rollbackMaskMethod = orca.deleteOrRemoveVolumesFromExportMaskMethod(
                array.getId(), exportGroup.getId(), exportMask.getId(), volumeList, rollbackCompleter);
        workflow.createStep(EXPORT_STEP, "createOrAddVolumesToExportMask: " + exportMask.getMaskName(),
                previousStepId, array.getId(), array.getSystemType(), orca.getClass(),
                updateMaskMethod, rollbackMaskMethod, maskStepId);
        _log.info(String.format(
                "VPLEX ExportGroup %s (%s) vplex %s varray %s",
                exportGroup.getLabel(), exportGroup.getId(), vplex.getId(),
                exportGroup.getVirtualArray()));
        return (isVNX && zoningStep != null) ? zoningStep : maskStepId;
    }

    /**
     * Return the cluster name for the VPlex. This will be used in the Masking Views, IGs, etc.
     * The clusterName will not be longer than 36 characters long.
     * 
     * @param vplex - Storage System
     * @return vplex cluster name
     */
    private String getClusterName(StorageSystem vplex) {
        String clusterName;
        // If we have two clusters, break the GUID up so can retrieve last four digits
        // of each cluster's serial number.
        // The GUID is formated like VPLEX+clus1sn:clus2sn, where clus1sn is the serial
        // serial number of the first cluster, clus2sn is the secial number of the second.
        // If there is no 2nd cluster, it will be VPLEX+clus1sn.
        // The format of the serial numbers is pretty free form, sometimes like FNM00114300288,
        // but I've seen other instances where it seems to be over-ridden like XX_YY_ZZ.
        // So the final will catch something totally unexpected.
        String[] guidComponents = vplex.getNativeGuid().split("[+:]");
        if (guidComponents.length >= 3 && guidComponents[1].length() >= 4 && guidComponents[2].length() >= 4) {
            clusterName = "VPLEX_"
                    + guidComponents[1].substring(guidComponents[1].length() - 4) + "_"
                    + guidComponents[2].substring(guidComponents[2].length() - 4);
        } else if (guidComponents.length == 2 && guidComponents[1].length() >= 4) {
            clusterName = "VPLEX_" + guidComponents[1].substring(guidComponents[1].length() - 4);
        } else {
            // Otherwise, just use the entire GUID
            clusterName = vplex.getNativeGuid();
        }
        // Translate all special characters to underscore
        clusterName = clusterName.replaceAll("[^A-Za-z0-9]", "_");
        if (clusterName.length() > MAX_CHARS_IN_VPLEX_NAME) {
            // Truncate if longer than MAX_CHARS_IN_VPLEX_NAME
            clusterName = clusterName.substring(0, MAX_CHARS_IN_VPLEX_NAME);
        }
        // Add cluster designation _CL1 or _CL2. (Four characters appended.)
        clusterName = clusterName + "_CL" + _cluster;
        return clusterName;
    }

    public Map<ExportMask, ExportGroup> generateExportMasks(
            URI varrayURI, StorageSystem vplex, StorageSystem array) {
        // Build the data structures used for analysis and validation.
        buildDataStructures(vplex, array, varrayURI);

        // Assign initiators to hosts
        String clusterName = getClusterName(vplex);
        Set<Map<String, Map<URI, Set<Initiator>>>> initiatorGroups =
                getInitiatorGroups(clusterName, _directorToInitiatorIds, _initiatorIdToNetwork, _idToInitiatorMap,
                        array.getSystemType().equals(SystemType.vnxblock.name()), false);

        // First we must determine the Initiator Groups and PortGroups to be used.
        VplexBackEndMaskingOrchestrator orca = getOrch(array);
        Map<URI, List<StoragePort>> allocatablePorts =
                getAllocatablePorts(array.getId(), _networkMap.keySet(), varrayURI);
        Set<Map<URI, List<StoragePort>>> portGroups =
                orca.getPortGroups(allocatablePorts, _networkMap, varrayURI, initiatorGroups.size());

        // Now generate the Masking Views that will be needed.
        Map<ExportMask, ExportGroup> exportMasksMap = new HashMap<ExportMask, ExportGroup>();
        Iterator<Map<String, Map<URI, Set<Initiator>>>> igIterator = initiatorGroups.iterator();
        for (Map<URI, List<StoragePort>> portGroup : portGroups) {
            String maskName = clusterName.replaceAll("[^A-Za-z0-9_]", "_");
            _log.info("Generating ExportMask: " + maskName);
            if (!igIterator.hasNext()) {
                igIterator = initiatorGroups.iterator();
            }
            Map<String, Map<URI, Set<Initiator>>> initiatorGroup = igIterator.next();
            StringSetMap zoningMap = orca.configureZoning(portGroup, initiatorGroup, _networkMap);
            ExportMask exportMask = generateExportMask(array.getId(), maskName, portGroup, initiatorGroup, zoningMap);

            // Set a flag indicating that we do not want to remove zoningMap entries
            StringSetMap map = new StringSetMap();
            StringSet values = new StringSet();
            values.add(Boolean.TRUE.toString());
            map.put(ExportMask.DeviceDataMapKeys.ImmutableZoningMap.name(), values);
            if (array.getSystemType().equals(SystemType.vmax.name())) {
                // If VMAX, set consisteLUNs = false
                values = new StringSet();
                values.add(Boolean.FALSE.toString());
                map.put(ExportMask.DeviceDataMapKeys.VMAXConsistentLUNs.name(), values);
            }
            exportMask.addDeviceDataMap(map);

            // Create an ExportGroup for the ExportMask.
            List<Initiator> initiators = new ArrayList<Initiator>();
            for (String director : initiatorGroup.keySet()) {
                for (URI networkURI : initiatorGroup.get(director).keySet()) {
                    for (Initiator initiator : initiatorGroup.get(director).get(networkURI)) {
                        initiators.add(initiator);
                    }
                }
            }
            _dbClient.createObject(exportMask);
            ExportGroup exportGroup = ExportUtils.createVplexExportGroup(_dbClient, vplex, array, initiators, varrayURI, _projectURI,
                    _tenantURI, 0, exportMask);
            exportMasksMap.put(exportMask, exportGroup);
        }
        return exportMasksMap;
    }

    /**
     * 
     * @param exportGroup
     * @param volumeMap
     * @return
     */
    private Map<URI, Integer> createVolumeMap(URI storageSystemURI, ExportGroup exportGroup,
            Map<URI, Volume> volumeMap) {
        Map<URI, Integer> volumeLunIdMap = new HashMap<URI, Integer>();
        Iterator<URI> volumeIter = volumeMap.keySet().iterator();
        while (volumeIter.hasNext()) {
            URI volumeURI = volumeIter.next();
            Volume volume = volumeMap.get(volumeURI);
            if (volume.getStorageController().toString().equals(storageSystemURI.toString())) {
                volumeLunIdMap.put(volumeURI, ExportGroup.LUN_UNASSIGNED);
                exportGroup.addVolume(volumeURI, ExportGroup.LUN_UNASSIGNED);
            }
        }
        return volumeLunIdMap;
    }

    /**
     * Searches for any ExportGroups containing one or more of the specified initiators.
     * The ExportMask must have createdBySystem() == true (meaning created by this ViPR instance).
     * 
     * @param array -- Storage Array mask will be on
     * @param initiators -- Initiators contained in the ExportMask (partial match)
     * @param empty -- If true, only returns empty ExportMasks
     * @return
     */
    public Map<ExportMask, ExportGroup> searchDbForExportMasks(
            StorageSystem array, List<Initiator> initiators,
            boolean empty) {
        Map<ExportMask, ExportGroup> returnedMasks = new HashMap<ExportMask, ExportGroup>();
        Map<URI, ExportGroup> exportGroups = new HashMap<URI, ExportGroup>();
        // Find all the ExportGroups.
        for (Initiator initiator : initiators) {
            List<ExportGroup> groups = ExportUtils.getInitiatorExportGroups(initiator, _dbClient);
            for (ExportGroup group : groups) {
                if (!exportGroups.containsKey(group.getId())) {
                    exportGroups.put(group.getId(), group);
                }
            }
        }
        // Look at all the Export Masks in those groups for the given array that we created.
        for (ExportGroup group : exportGroups.values()) {
            List<ExportMask> masks = ExportMaskUtils.getExportMasks(_dbClient, group, array.getId());
            for (ExportMask mask : masks) {
                if (mask.getInactive() || mask.getCreatedBySystem() == false) {
                    continue;
                }
                if (empty == false || (group.getVolumes() == null || group.getVolumes().isEmpty())) {
                    returnedMasks.put(mask, group);
                }
            }
        }
        return returnedMasks;
    }

    /**
     * Assign the Initiators to Initiator Groups. As a side effect of doing this,
     * each Initiator is assigned a hostName and a clusterName that will be unchanging.
     * 
     * @param clusterName -- name of the VPLEX cluster
     * @param directorToInitiatorIds
     * @param initiatorIdToNetwork
     * @param idToInitiatorMap
     * @param dualIG - If true, generates dual IGs
     */
    public Set<Map<String, Map<URI, Set<Initiator>>>>
            getInitiatorGroups(String clusterName, Map<String, Set<String>> directorToInitiatorIds,
                    Map<String, URI> initiatorIdToNetwork, Map<String, Initiator> idToInitiatorMap,
                    boolean dualIG, boolean simulatorMode) {

        Set<Map<String, Map<URI, Set<Initiator>>>> initiatorGroups =
                new HashSet<Map<String, Map<URI, Set<Initiator>>>>();
        Map<String, Map<URI, Set<Initiator>>> ig1 = new HashMap<String, Map<URI, Set<Initiator>>>();
        Map<String, Map<URI, Set<Initiator>>> ig2 = new HashMap<String, Map<URI, Set<Initiator>>>();

        // Make a pass through the directors, looking for ABAB pattern to Networks
        for (String director : directorToInitiatorIds.keySet()) {
            String[] initiators = new String[NUMBER_INITIATORS_IN_DIRECTOR];
            Set<String> initiatorIds = directorToInitiatorIds.get(director);
            if (initiatorIds.size() != NUMBER_INITIATORS_IN_DIRECTOR) {
                _log.info("Not all VPlex back-end ports present on director: " + director);
                dualIG = false;
            }
            for (String initiatorId : initiatorIds) {
                Initiator initiator = idToInitiatorMap.get(initiatorId);
                String portWwn = initiator.getInitiatorPort();
                int portWwnLength = portWwn.length();
                // Turn the last character of the port WWN into an index
                Integer index = new Integer(portWwn.substring(portWwnLength - 1));
                initiators[index] = initiatorId;
            }
            // Check for the ABAB pattern in Networks
            if (initiators[0] == null || initiators[1] == null) {
                dualIG = false;
            } else if (initiatorIdToNetwork.get(initiators[0]).equals(initiatorIdToNetwork.get(initiators[1]))) {
                _log.info("Initiators 10 and 11 are on same network director: " + director);
                dualIG = false;
            }
            if (initiators[2] == null || initiators[3] == null) {
                dualIG = false;
            } else if (initiatorIdToNetwork.get(initiators[2]).equals(initiatorIdToNetwork.get(initiators[3]))) {
                _log.info("Initiators 12 and 13 are on same network director: " + director);
                dualIG = false;
            }
        }

        // Now set the Host name in the Initiators according to one IG or two.
        // If there are two IGs, the names are _IG1 and IG2. If only one its just _IG.
        // The Initiators are also put in initiatorGroups that are returned.
        String.format("Configuring all VPlex back-end ports as %s Initiator Groups", (dualIG ? "two" : "one"));
        for (String director : directorToInitiatorIds.keySet()) {
            ig1.put(director, new HashMap<URI, Set<Initiator>>());
            ig2.put(director, new HashMap<URI, Set<Initiator>>());
            Set<String> initiatorIds = directorToInitiatorIds.get(director);
            for (String initiatorId : initiatorIds) {
                Initiator initiator = idToInitiatorMap.get(initiatorId);
                URI netURI = initiatorIdToNetwork.get(initiatorId);
                String portWwn = Initiator.normalizePort(initiator.getInitiatorPort());
                int portWwnLength = portWwn.length();
                // Turn the last character of the port WWN into an index
                Integer index = new Integer(portWwn.substring(portWwnLength - 1));

                if (dualIG && index >= NUMBER_INITIATORS_IN_DIRECTOR / 2) {
                    initiator.setHostName(clusterName + "_IG2");
                    initiator.setClusterName(clusterName);
                    if (ig2.get(director).get(netURI) == null) {
                        ig2.get(director).put(netURI, new HashSet<Initiator>());
                    }
                    ig2.get(director).get(netURI).add(initiator);
                } else {
                    initiator.setHostName(clusterName + "_IG1");
                    initiator.setClusterName(clusterName);
                    if (ig1.get(director).get(netURI) == null) {
                        ig1.get(director).put(netURI, new HashSet<Initiator>());
                    }
                    ig1.get(director).get(netURI).add(initiator);
                }
                if (!simulatorMode) {
                    _dbClient.updateAndReindexObject(initiator);
                }
            }
        }
        // Make the return set.
        initiatorGroups.add(ig1);
        if (dualIG) {
            initiatorGroups.add(ig2);
        }
        return initiatorGroups;
    }

    /**
     * Search through the set of existing ExportMasks looking for those that may have been renamed.
     * Renamed export masks are determined by matching a StoragePort between the two masks and at least
     * one of the existingVolumes from the new mask being in the existingVolumes or userAddedVolumes of
     * the old mask.
     * 
     * @param maskSet -- A map of ExportMask URI to ExportMask
     * @return Map<URI, ExportMask> -- Returns an updated MaskSet that may contain renamed Masks instead of those
     *         read off the array
     */
    private Map<URI, ExportMask> checkForRenamedExportMasks(Map<URI, ExportMask> maskSet) {
        Map<URI, ExportMask> result = new HashMap<URI, ExportMask>();
        for (ExportMask mask : maskSet.values()) {
            // We match on either existingVolumes keyset or userAddedVolumes
            StringSet existingVolumes = StringSetUtil.getStringSetFromStringMapKeySet(
                    mask.getExistingVolumes());
            // Get the list of ExportMasks that match on a port with this mask
            ExportMask match = null;
            outer: for (String portId : mask.getStoragePorts()) {
                URIQueryResultList queryResult = new URIQueryResultList();
                _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getExportMasksByPort(portId), queryResult);
                for (URI uri : queryResult) {
                    if (uri.equals(mask.getId())) {
                        continue;  // Don't match on same mask
                    }
                    ExportMask dbMask = _dbClient.queryObject(ExportMask.class, uri);
                    if (dbMask == null || dbMask.getInactive()) {
                        continue;
                    }
                    // Look for a match between existing volumes on the hardware mask
                    // vs. existingVolumes or userAddedVolumes on the db mask.
                    StringSet extVols = StringSetUtil.getStringSetFromStringMapKeySet(
                            dbMask.getExistingVolumes());
                    StringSet userVols = StringSetUtil.getStringSetFromStringMapKeySet(
                            dbMask.getUserAddedVolumes());
                    if (StringSetUtil.hasIntersection(existingVolumes, extVols)
                            || StringSetUtil.hasIntersection(existingVolumes, userVols)) {
                        _log.info(String.format("ExportMask %s (%s) has been renamed to %s (%s)",
                                dbMask.getMaskName(), dbMask.getId().toString(), mask.getMaskName(), mask.getId().toString()));
                        dbMask.setMaskName(mask.getMaskName());
                        _dbClient.updateAndReindexObject(dbMask);
                        ;
                        _dbClient.markForDeletion(mask);
                        ;
                        result.put(dbMask.getId(), dbMask);
                        match = dbMask;
                        break outer;
                    }
                }
            }
            if (match == null) {
                // Add in the original mask if no match.
                result.put(mask.getId(), mask);
            }
        }
        return result;
    }

    /**
     * Verify that an ExportMask that is going to be used is on the StorageSystem.
     * 
     * @param mask
     * @param array
     */
    private void verifyExportMaskOnSystem(ExportMask mask, StorageSystem array) {
        VplexBackEndMaskingOrchestrator maskingOrchestrator = getOrch(array);
        BlockStorageDevice storageDevice = _blockDeviceController.getDevice(array.getSystemType());
        // Make a list of Initiators used by the ExportMask. These could be initiators
        // explicitly named in the Export Mask, or Initiators that match addresses in the existingInitiators
        // fields. (The later occurs for externally created ExportMasks.)
        List<Initiator> initiators = new ArrayList<Initiator>();
        initiators.addAll(ExportMaskUtils.getInitiatorsForExportMask(_dbClient, mask, Transport.FC));
        if (initiators.isEmpty()) {
            initiators.addAll(ExportMaskUtils.getExistingInitiatorsForExportMask(_dbClient, mask, Transport.FC));
        }
        Map<URI, ExportMask> maskSet = maskingOrchestrator.readExistingExportMasks(array, storageDevice, initiators);

        if (maskSet.containsKey(mask.getId())) {
            _log.info(String.format("Verified ExportMask %s present on %s", mask.getMaskName(), array.getNativeGuid()));
            return;
        }

        // We need to check and see if the ExportMask has been renamed. The admin may have renamed it to add "NO_VIPR" so we
        // will not create any more volumes on it. However, we still want to be able to delete volumes that we created on the mask.
        // The call to checkForRenamedExportMasks will look to see if we had any ExportMasks that were renamed, and if so,
        // update the names of the corresponding ExportMasks in our database.
        _log.info(String.format("ExportMask %s not present on %s; checking if renamed...", mask.getMaskName(), array.getNativeGuid()));
        checkForRenamedExportMasks(maskSet);
    }

    /**
     * Routine will examine the placement descriptor to filter out any of the suggested ExportMasks that do not meet the VPlex requirements.
     *
     * @param vplex [IN] - VPlex StorageSystem
     * @param array [IN] - Backend StorageSystem
     * @param varrayURI [IN] - VirtualArray tying together VPlex initiators with backend array
     * @param placementDescriptor [IN/OUT] - Placement data structure. This should have initial placement suggestions based on the backend
     *            analysis. It will be further refined based on VPlex requirements.
     * @return Set of ExportMask URIs that did not meet the selection criteria
     */
    private Set<URI> filterExportMasksByVPlexRequirements(StorageSystem vplex, StorageSystem array, URI varrayURI,
            ExportMaskPlacementDescriptor placementDescriptor) {
        // The main inputs to this function come from within the placementDescriptor. The expectation is that the call
        // to get the suggested ExportMasks to reuse has already been called
        Map<URI, ExportMask> maskSet = placementDescriptor.getMasks();
        Map<URI, Volume> volumeMap = placementDescriptor.getVolumesToPlace();

        // Collect the potential ExportMasks in the maskSet, which is a map of URI to ExportMask.
        // Collect the URIs of invalid Masks.
        Set<URI> invalidMasks = new HashSet<>();

        // Validate the Export Masks. The two booleans below indicate whether we found
        // Externally Created Export Masks, and/or ViPR created Masks (by this ViPR instance).
        boolean externallyCreatedMasks = false;
        boolean viprCreatedMasks = false;

        for (ExportMask mask : maskSet.values()) {
            _log.info(String.format("Validating ExportMask %s (%s) %s", mask.getMaskName(), mask.getId(),
                    (mask.getCreatedBySystem() ? "ViPR created" : "Externally created")));
            if (validateExportMask(vplex, array, varrayURI, _initiatorPortMap, mask, invalidMasks)) {
                if (mask.getCreatedBySystem()) {
                    viprCreatedMasks = true;
                } else {
                    externallyCreatedMasks = true;
                }
            }
        }
        for (URI invalidMask : invalidMasks) {
            placementDescriptor.invalidateExportMask(invalidMask);
        }

        // If there were no externallyCreatedMasks, or we have already created masks,
        // search for ones that may not have yet been instantiated on the device.
        // These will be in the database with zero volumes. Naturally, having the lowest
        // volume count, they would be likely choices.
        if (!externallyCreatedMasks || viprCreatedMasks) {
            Map<ExportMask, ExportGroup> uninitializedMasks = searchDbForExportMasks(array, _initiators, false);
            // Add these into contention for lowest volume count.
            for (ExportMask mask : uninitializedMasks.keySet()) {
                _log.info(String.format("Validating uninitialized ViPR ExportMask %s (%s)",
                        mask.getMaskName(), mask.getId()));
                if (validateExportMask(vplex, array, varrayURI, _initiatorPortMap, mask, invalidMasks)) {
                    maskSet.put(mask.getId(), mask);
                    placementDescriptor.placeVolumes(mask.getId(), volumeMap);
                }
            }
        }

        return invalidMasks;
    }

    /**
     * Generate an ExportMask for the VPlex-to-Backend array to hold the volumes.
     * TODO: Need to make sure that we properly place volumes in a new ExportMask for those that did not match any ExportMasks
     * 
     * @param vplex
     * @param array
     * @param varrayURI
     * @param placementDescriptor
     * @param invalidMasks
     * @throws VPlexApiException
     */
    private void createVPlexBackendExportMasks(StorageSystem vplex, StorageSystem array, URI varrayURI,
            ExportMaskPlacementDescriptor placementDescriptor, Set<URI> invalidMasks) throws VPlexApiException {
        // When the system has to create the ExportMask, we should try to place the volumes all within a single mask
        placementDescriptor.setPlacementHint(ExportMaskPlacementDescriptor.PlacementHint.VOLUMES_TO_SINGLE_MASK);
        Map<URI, ExportMask> maskSet = placementDescriptor.getMasks();
        Map<URI, Volume> volumeMap = placementDescriptor.getVolumesToPlace();

        if (!invalidMasks.isEmpty()) {
            _log.info(String.format("Found %d existing export masks, but all failed validation",
                    invalidMasks.size()));
        } else {
            _log.info("Did not find any existing export masks");
        }
        _log.info("Attempting to generate ExportMasks...");
        Map<ExportMask, ExportGroup> generatedMasks = generateExportMasks(varrayURI, vplex, array);
        if (generatedMasks.isEmpty()) {
            _log.info("Unable to generate any ExportMasks");
            throw VPlexApiException.exceptions.couldNotGenerateArrayExportMask(
                    vplex.getNativeGuid(), array.getNativeGuid(), _cluster);
        }
        // Validate the generated masks too.
        for (ExportMask mask : generatedMasks.keySet()) {
            _log.info(String.format("Validating generated ViPR Export Mask %s (%s)",
                    mask.getMaskName(), mask.getId()));
            if (validateExportMask(vplex, array, varrayURI, _initiatorPortMap, mask, invalidMasks)) {
                maskSet.put(mask.getId(), mask);
                // This is a generated ExportMask. The default Volume placement for something that ViPR
                // creates will be to place everything in a single ExportMask (VOLUMES_TO_SINGLE_MASK).
                placementDescriptor.placeVolumes(mask.getId(), volumeMap);
            }
        }

        if (maskSet.isEmpty()) {
            _log.info("Unable to find or create any suitable ExportMasks");
            throw VPlexApiException.exceptions.couldNotFindValidArrayExportMask(
                    vplex.getNativeGuid(), array.getNativeGuid(), _cluster);
        }
    }
}
