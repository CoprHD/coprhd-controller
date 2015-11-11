/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.placement;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.FCEndpoint;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageProtocol;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.networkcontroller.impl.NetworkScheduler;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.util.NetworkLite;

/**
 * This Storage Ports Allocator allocates an arbitrary number of Storage Ports
 * for a Transport Zone, as determined by the num_paths input argument.
 * This code is only applicable for SAN Transport Zones.
 * 
 */
public class StoragePortsAllocator {
    protected static final Logger _log = LoggerFactory
            .getLogger(StoragePortsAllocator.class);
    private static final String DIRECTOR_A = "A";
    private static final String DIRECTOR_B = "B";

    /**
     * The purpose of the PortAllocationContext is to encapsulate all of the
     * data needed about a Network that would normally be fetched from
     * Cassandra. This is done up front (outside of the port selection
     * algorithm) so that it can be replaced with simulated data for testing.
     * 
     * In actual operation, there will be a context structure created for each
     * Network to be processed. It will contain information on all the
     * Storage Ports for a given Storage Array. This data will be added to the
     * context for each port by calling addPort().
     * 
     * The sets _alreadyAllocatedDirectors, _alreadyAllocatedEnginges, etc. are used
     * to pass data between allocation for different networks. This is
     * useful if we are allocating fewer paths per network than
     * directors. In that case, we can ensure that different directors are used
     * for the different networks.
     * 
     */
    public static class PortAllocationContext {
        public PortAllocationContext() {
        }

        public PortAllocationContext(NetworkLite tz, String systemName) {
            _initiatorNetwork = tz;
            _systemName = systemName;
        }

        public PortAllocationContext(NetworkLite iniNet,
                String systemName,
                PortAllocationContext previousContext) {
            _initiatorNetwork = iniNet;
            _systemName = systemName;
            if (previousContext != null) {
                _alreadyAllocatedEngines
                        .addAll(previousContext._alreadyAllocatedEngines);
                _alreadyAllocatedDirectors
                        .addAll(previousContext._alreadyAllocatedDirectors);
                _alreadyAllocatedCpus.addAll(previousContext._alreadyAllocatedCpus);
                _alreadyAllocatedDirectorTypes
                        .addAll(previousContext._alreadyAllocatedDirectorTypes);
                _alreadyAllocatedSwitches
                        .addAll(previousContext._alreadyAllocatedSwitches);
            }
        }

        // Network
        public NetworkLite _initiatorNetwork;

        public String _systemName;

        // The type of the StorageSystem
        public StorageSystem.Type _systemType;

        // This maps the StoragePort URI to the StoragePort
        public Map<URI, StoragePort> _idToStoragePort = new HashMap<URI, StoragePort>();

        // This maps the String port WWN to the StoragePort structure.
        // All the StoragePorts belong to one StorageSystem.
        // Only registered ports are included in the Context structure.
        public Map<String, StoragePort> _addressToStoragePort = new HashMap<String, StoragePort>();

        // Ports arranged by Engine; key is Engine index (0 ... 7)
        public Map<String, Set<StoragePort>> _engineToStoragePortSet = new HashMap<String, Set<StoragePort>>();

        public Map<StoragePort, String> _storagePortToEngine = new HashMap<StoragePort, String>();

        // Ports arranged by Director type; currently only used for vplex
        // key is Director type (A 0r B).
        public Map<String, Set<StoragePort>> _directorTypeToStoragePortSet = new HashMap<String, Set<StoragePort>>();

        public Map<StoragePort, String> _storagePortToDirectorType = new HashMap<StoragePort, String>();

        // Ports arranged by Director; key is Director index
        public Map<String, Set<StoragePort>> _directorToStoragePortSet = new HashMap<String, Set<StoragePort>>();

        public Map<StoragePort, String> _storagePortToDirector = new HashMap<StoragePort, String>();

        // Ports arranged by Vmax Cpu, key is portGroup name (e.g. FA-10E).
        public Map<String, Set<StoragePort>> _cpuToStoragePortSet = new HashMap<String, Set<StoragePort>>();
        public Map<StoragePort, String> _storagePortToCpu = new HashMap<StoragePort, String>();

        // Ports arranged by the SAN switch they are connected to; key is SAN
        // switch name.
        // This map is null if SAN zoning is not enabled or does not know of
        // this TransportZone.
        public Map<String, Set<StoragePort>> _switchNameToStoragePortSet = new HashMap<String, Set<StoragePort>>();

        public Map<StoragePort, String> _storagePortToSwitchName = new HashMap<StoragePort, String>();

        public Map<StoragePort, Long> _storagePortToUsage = new HashMap<StoragePort, Long>();

        // For the case where we are only doing one path in this transport
        // zone, we want to avoid
        // engines / directorTypes / directors / switches already allocated in other transport zones.
        public Set<String> _alreadyAllocatedEngines = new HashSet<String>();
        public Set<String> _alreadyAllocatedDirectorTypes = new HashSet<String>();
        public Set<String> _alreadyAllocatedDirectors = new HashSet<String>();
        public Set<String> _alreadyAllocatedCpus = new HashSet<String>();
        public Set<String> _alreadyAllocatedSwitches = new HashSet<String>();

        /**
         * Add a StoragePort to the context for this TransportZone
         * 
         * @param port -- The StoragePort structure
         * @param haDomain - The StorageHADomain structure
         * @param arrayType - StorageSystem.type enumeration
         * @param switchName - String -if null, not used
         * @param usage -- Integer count of Initiators using this port
         */
        public void addPort(StoragePort port, StorageHADomain haDomain,
                StorageSystem.Type arrayType, String switchName, Long usage) {
            _systemType = arrayType;
            _idToStoragePort.put(port.getId(), port);
            _addressToStoragePort.put(port.getPortNetworkId(), port);
            String engine = getEngine(port, haDomain, arrayType);
            if (engine != null) {
                if (_engineToStoragePortSet.get(engine) == null) {
                    _engineToStoragePortSet.put(engine,
                            new HashSet<StoragePort>());
                }
                _engineToStoragePortSet.get(engine).add(port);
                _storagePortToEngine.put(port, engine);
            }
            String directorType = getDirectorType(arrayType, haDomain);
            if (directorType != null) {
                if (_directorTypeToStoragePortSet.get(directorType) == null) {
                    _directorTypeToStoragePortSet.put(directorType,
                            new HashSet<StoragePort>());
                }
                _directorTypeToStoragePortSet.get(directorType).add(port);
                _storagePortToDirectorType.put(port, directorType);
            }
            String director = getDirector(port, haDomain);
            if (director != null) {
                if (_directorToStoragePortSet.get(director) == null) {
                    _directorToStoragePortSet.put(director,
                            new HashSet<StoragePort>());
                }
                _directorToStoragePortSet.get(director).add(port);
                _storagePortToDirector.put(port, director);
            }
            String cpu = getCpu(port, haDomain, arrayType);
            if (cpu != null) {
                if (_cpuToStoragePortSet.get(cpu) == null) {
                    _cpuToStoragePortSet.put(cpu, new HashSet<StoragePort>());
                }
                _cpuToStoragePortSet.get(cpu).add(port);
                _storagePortToCpu.put(port, cpu);
            }
            if (switchName != null) {
                if (_switchNameToStoragePortSet.get(switchName) == null) {
                    _switchNameToStoragePortSet.put(switchName,
                            new HashSet<StoragePort>());
                }
                _switchNameToStoragePortSet.get(switchName).add(port);
                _storagePortToSwitchName.put(port, switchName);
            }
            _storagePortToUsage.put(port, usage);
        }

        /**
         * Allocates existing ports to the already allocated context (only).
         * These ports may be from different Networks and are not necessarily
         * part of the pool of ports we can allocate from.
         * 
         * @param port
         * @param haDomain
         * @param arrayType
         * @param switchName
         */
        public void addPortToAlreadyAllocatedContext(StoragePort port,
                StorageHADomain haDomain, StorageSystem.Type arrayType,
                String switchName) {
            String engine = getEngine(port, haDomain, arrayType);
            if (engine != null) {
                _alreadyAllocatedEngines.add(engine);
            }
            String directorType = getDirectorType(arrayType, haDomain);
            if (directorType != null) {
                _alreadyAllocatedDirectorTypes.add(directorType);
            }
            String director = getDirector(port, haDomain);
            if (director != null) {
                _alreadyAllocatedDirectors.add(director);
            }
            String cpu = getCpu(port, haDomain, arrayType);
            if (cpu != null) {
                _alreadyAllocatedCpus.add(cpu);
            }
            if (switchName != null) {
                _alreadyAllocatedSwitches.add(switchName);
            }
        }

        /**
         * Copy the context that should be forwarded from a network that was
         * previously allocated to a network that is now being allocated.
         * 
         * @param previous
         */
        public void copyPreviousNetworkContext(PortAllocationContext previous) {
            _alreadyAllocatedDirectors = previous._alreadyAllocatedDirectors;
            _alreadyAllocatedCpus = previous._alreadyAllocatedCpus;
            _alreadyAllocatedDirectorTypes = previous._alreadyAllocatedDirectorTypes;
            ;
            _alreadyAllocatedEngines = previous._alreadyAllocatedEngines;
            _alreadyAllocatedSwitches = previous._alreadyAllocatedSwitches;
        }

        public void reinitialize() {
            _alreadyAllocatedEngines.clear();
            _alreadyAllocatedDirectorTypes.clear();
            _alreadyAllocatedDirectors.clear();
            _alreadyAllocatedCpus.clear();
            _alreadyAllocatedSwitches.clear();
            _previousRule17 = null;
        }

        /**
         * Variables related to rule17 state. Not to be set by callers.
         */
        public boolean _disableRule17 = false;        // Set to disable rule17
        public String _previousRule17;              // To be used only by the filterRule17 code code.

    }

    private static PortAllocationContext contextPrototype = new PortAllocationContext();

    /**
     * Allow over-riding of the PortAllocationContext prototype.
     * 
     * @param contextPrototype
     */
    public static void setContextPrototype(PortAllocationContext contextPrototype) {
        StoragePortsAllocator.contextPrototype = contextPrototype;
    }

    /**
     * Get a new PortAllocationContext using the prototype.
     * If this interface is used, the PortAllocationContext can be over-ridden for test purposes.
     * 
     * @param network -- Network Lite
     * @param systemName -- String systemName for diagnostic messages
     * @return
     */
    public static PortAllocationContext getPortAllocationContext(NetworkLite network, String systemName,
            PortAllocationContext previousContext) {
        try {
            PortAllocationContext context = contextPrototype.getClass().newInstance();
            context._initiatorNetwork = network;
            context._systemName = systemName;
            return context;
        } catch (Exception ex) {
            return new PortAllocationContext(network, systemName, previousContext);
        }
    }

    /**
     * Returns the Engine index. Use for VMAX/HDS/VPLEX only.
     * For HDS, ViPR treats controllers as engines and provides redundancy at engine level.
     * For XtremIO, ViPR treats X-bricks as engines and provides redundancy at engine level.
     * 
     * @param port
     * @param haDomain
     * @param arrayType
     * @return
     */
    static String getEngine(StoragePort port, StorageHADomain haDomain, StorageSystem.Type arrayType) {
        if (arrayType == StorageSystem.Type.vmax) {
            Integer slotNumber = new Integer(haDomain.getSlotNumber());
            Integer engine = (slotNumber - 1) / 2;
            return engine.toString();
        } else if (arrayType == StorageSystem.Type.vplex) {
            // In case of vplex slot numbers are like (0,1),(8,9),(10,11)
            Integer slotNumber = new Integer(haDomain.getSlotNumber());
            Integer engine = (slotNumber) / 2;
            return engine.toString();
        } else if (arrayType == StorageSystem.Type.hds) {
            // For HDS, controllers are being treated as Engines in ViPR.
            return haDomain.getSlotNumber();
        } else if (arrayType == StorageSystem.Type.xtremio) {
            // For XtremIO, X-bricks are being treated as Engines in ViPR.
            // X-brick has 2 Storage controllers: X1-SC1, X1-SC2
            return haDomain.getAdapterName().split(Constants.HYPHEN)[0];
        } else {// not a VMAX or Vplex or HDS or XtremIO, so it has no engines
            return null;
        }
    }

    /**
     * Get the Vmax CPU, which is the same as the portGroupName() or the haDomain.adapterName()
     * 
     * @param port
     * @param haDomain
     * @param arrayType
     * @return String representing the cpu identifier on a VMAX, null if not VMAX.
     */
    static String getCpu(StoragePort port, StorageHADomain haDomain, StorageSystem.Type arrayType) {
        if (arrayType == StorageSystem.Type.vmax) {
            return port.getPortGroup();
        } else {
            return null;
        }
    }

    /**
     * Returns the index of the director.
     * 
     * @param port
     * @return
     */
    static String getDirector(StoragePort port, StorageHADomain haDomain) {
        if (haDomain != null) {
            return haDomain.getSlotNumber();
        }
        return null;
    }

    /**
     * Returns the director type A or B for the storage port.
     * 
     * @param arrayType
     * @param haDomain
     * @return
     */
    static String getDirectorType(StorageSystem.Type arrayType, StorageHADomain haDomain) {
        String directorType = null;
        if (arrayType == StorageSystem.Type.vplex) {
            if (haDomain.getName().endsWith(DIRECTOR_A)) {
                directorType = DIRECTOR_A;
            } else if (haDomain.getName().endsWith(DIRECTOR_B)) {
                directorType = DIRECTOR_B;
            }
        }
        return directorType;
    }

    /**
     * Scan the available ports to make sure each has an entry in the
     * _storagePortToSwitchname map, which indicates a switch saw it as
     * connected. If it has no connections, remove it from consideration.
     * 
     * @param context
     */
    private void checkForUnconnectedPorts(PortAllocationContext context) {
        Set<String> removedPorts = new HashSet<String>();
        for (StoragePort port : context._addressToStoragePort.values()) {
            if (context._storagePortToSwitchName.containsKey(port) == false) {
                _log.info(String
                        .format("Port %s address (%s) is not currently connected to SAN switch;"
                                + " removed from consideration for allocation",
                                port.getPortName(), port.getPortNetworkId()));
                removedPorts.add(port.getPortNetworkId());
            }
        }
        for (String key : removedPorts) {
            context._addressToStoragePort.remove(key);
        }
    }

    /**
     * Allocates one or more Storage Ports for a single Transport Zone. Attempts
     * to maximize redundancy and load balance by choosing the lowest used port.
     * 
     * @param portsRequested
     *            -- The number of paths to be allocated for this network
     * @param context
     *            -- The contextual structure, which contains the Storage Ports
     *            for this Storage Array that are in the Transport Zone, as
     *            pre-processed by PortAllocationContext.addPort().
     * 
     *            The contextual structure also contains the sets of directors
     *            and switches that have been previously allocated in other
     *            transport zones, as well as any ports that were allocated on
     *            an earlier call to allocatePortsForTransportZone. This
     *            re-entrancy allows one to up the num_paths and recompute, thus
     *            adding more ports, if desired.
     * @param checkConnectivity
     *            -- If true, don't allocate ports that are not present in our Endpoints
     *            received from the SAN switches
     * @param previouslyAllocatedPorts
     *            -- A collection of ports that were previously allocated and count towards the
     *            number of ports requested.
     * @param allowFewerPorts
     *            -- If true, do not fail if fewer ports can be allocated than requested.
     * @return
     * @throws DeviceControllerException if not enough ports are allocated
     */
    public List<StoragePort> allocatePortsForNetwork(int portsRequested,
            PortAllocationContext context, boolean checkConnectivity,
            Collection<StoragePort> previouslyAllocatedPorts, boolean allowFewerPorts)
            throws PlacementException {
        List<StoragePort> allocatedStoragePorts = new ArrayList<StoragePort>();

        _log.info(String.format(
                "Attempting to allocate %d storage ports for Initiator Network: %s",
                new Integer(portsRequested), context._initiatorNetwork.getLabel()));

        if (checkConnectivity) {
            checkForUnconnectedPorts(context);
        }

        // This is for adding additional ports to existing allocations.
        // WWPN of ports which have already been allocated
        Set<String> allocatedPorts = new HashSet<String>();
        // engines which have already been allocated
        Set<String> allocatedEngines = new HashSet<String>();
        // director type which have already been allocated
        Set<String> allocatedDirectorTypes = new HashSet<String>();
        // directors which have already been allocated
        Set<String> allocatedDirectors = new HashSet<String>();
        // cpus (applies only to Vmax) that have already been allocated
        Set<String> allocatedCpus = new HashSet<String>();
        // SAN switches which have already been allocated
        Set<String> allocatedSwitches = new HashSet<String>();
        StoragePort allocatedPort = null;

        if (previouslyAllocatedPorts != null) {
            for (StoragePort port : previouslyAllocatedPorts) {
                allocatedPort = port;
                _log.info(String.format(
                        "Previously allocated port %s (%s) (may be reused)",
                        port.getPortName(), port.getPortNetworkId()));
                allocatePort(port, allocatedPorts, allocatedEngines,
                        allocatedDirectorTypes, allocatedDirectors, allocatedCpus,
                        allocatedSwitches, allocatedStoragePorts, context);
            }
            // Set allocatedPort to null so as not to initially trigger rule17
            allocatedPort = null;
        }
        // If we are allocating fewer paths than we have directors,
        // then try not to overlap directors with the already allocated
        // transport zones. We do not do this if we've already allocated ports
        // previously, because we match with those ports instead.
        else if (portsRequested < context._directorToStoragePortSet.size()
                && previouslyAllocatedPorts == null) {
            allocatedEngines.addAll(context._alreadyAllocatedEngines);
            _log.info("Already allocated engines: " + context._alreadyAllocatedEngines.toString());
            allocatedDirectorTypes.addAll(context._alreadyAllocatedDirectorTypes);
            _log.info("Already allocated director types: " + context._alreadyAllocatedDirectorTypes.toString());
            allocatedDirectors.addAll(context._alreadyAllocatedDirectors);
            _log.info("Already allocated directors: " + context._alreadyAllocatedDirectors.toString());
            allocatedSwitches.addAll(context._alreadyAllocatedSwitches);
            _log.info("Already allocated switches: " + context._alreadyAllocatedSwitches.toString());
        }

        // Loop for the number of paths desired, (starting with any previously
        // allocated ports)
        // allocating a port.
        for (int nAllocatedPaths = allocatedPorts.size(); nAllocatedPaths < portsRequested; nAllocatedPaths++) {

            // Make a set of the candidate Storage Ports, minus the ones that
            // have
            // already been allocated as given by their WWPN values
            Map<String, StoragePort> candidateMap = new HashMap<String, StoragePort>(
                    context._addressToStoragePort);
            for (String wwpn : allocatedPorts) {
                candidateMap.remove(wwpn);
            }
            Set<StoragePort> candidates = new HashSet<StoragePort>(
                    candidateMap.values());
            if (candidates.isEmpty()) {
                _log.warn(String
                        .format("Cannot allocate any more ports; have already allocated %s ports",
                                allocatedStoragePorts.size()));
                if (allocatedStoragePorts.size() < portsRequested && allowFewerPorts == false) {
                    throw PlacementException.exceptions
                            .cannotAllocateRequestedPorts(context._initiatorNetwork.getLabel(), // [hala] Change exception text to say
                                                                                                // initiator network
                                    context._systemName, portsRequested, allocatedStoragePorts.size(),
                                    context._addressToStoragePort.keySet().size());
                }
                break;
            }

            /*
             * The following filtering steps are organized from highest priority
             * to least high priority. Each filter step removes candidates that
             * belong to an entity (engine, director, cpu, SAN switch) that have
             * already been used. Each filter will recycle again through the
             * available entities after it has already allocated Storage Ports
             * belonging to the available entities. So for example, the first
             * filter will guarantee cycling through each of the engines, and
             * after using them all will cycle through them again (not
             * necessarily in the same order subsequent passes.)
             */

            // Invoke the rule17Filter if desired.
            candidates = filterRule17(candidates, allocatedPort, allocatedPorts,
                    allocatedDirectors, context);

            // See if there are any ports that can be allocated on a different
            // type
            candidates = filterCandidates(candidates, allocatedDirectorTypes,
                    context._directorTypeToStoragePortSet);

            // See if there are any ports that can be allocated on a different
            // engine
            candidates = filterCandidates(candidates, allocatedEngines,
                    context._engineToStoragePortSet);

            // See if there are any ports that can be allocated on a different
            // director
            candidates = filterCandidates(candidates, allocatedDirectors,
                    context._directorToStoragePortSet);

            // Try to allocate ports on different VMAX cpus.
            candidates = filterCandidates(candidates, allocatedCpus,
                    context._cpuToStoragePortSet);

            // See if there are any ports that can be allocated that are
            // connected to different SAN switches
            candidates = filterCandidates(candidates, allocatedSwitches,
                    context._switchNameToStoragePortSet);

            // Choose the final allocated port.
            // The choice is made based on choosing one of the ports with minimum usage metric.
            allocatedPort = chooseCandidate(candidates, context._storagePortToUsage);
            allocatePort(allocatedPort, allocatedPorts, allocatedEngines, allocatedDirectorTypes,
                    allocatedDirectors, allocatedCpus, allocatedSwitches, allocatedStoragePorts,
                    context);
            String director = context._storagePortToDirector.get(allocatedPort);
            _log.info(String.format("Allocated port %s WWPN %s director %s",
                    allocatedPort.getPortName(),
                    allocatedPort.getPortNetworkId(),
                    director));
        }
        return allocatedStoragePorts;
    }

    /**
     * Handles the book-keeping of allocating a port.
     * Called in two places - once for ports already allocated, and
     * once for ports that are being allocated.
     * 
     * @param allocatedPort
     * @param allocatedPorts
     * @param allocatedEngines
     * @param allocatedDirectors
     * @param allocatedCpus
     * @param allocatedSwitches
     * @param allocatedStoragePorts
     * @param context
     */
    private void allocatePort(StoragePort allocatedPort,
            Set<String> allocatedPorts,
            Set<String> allocatedEngines,
            Set<String> allocatedDirectorTypes,
            Set<String> allocatedDirectors,
            Set<String> allocatedCpus,
            Set<String> allocatedSwitches,
            List<StoragePort> allocatedStoragePorts,
            PortAllocationContext context) {
        allocatedPorts.add(allocatedPort.getPortNetworkId());
        allocatedStoragePorts.add(allocatedPort);
        String engine = context._storagePortToEngine.get(allocatedPort);
        if (engine != null) {
            allocatedEngines.add(engine);
            context._alreadyAllocatedEngines.add(engine);
        }
        String directorType = context._storagePortToDirectorType.get(allocatedPort);
        if (directorType != null) {
            allocatedDirectorTypes.add(directorType);
            context._alreadyAllocatedDirectorTypes.add(directorType);
        }
        String director = context._storagePortToDirector.get(allocatedPort);
        if (director != null) {
            allocatedDirectors.add(director);
            context._alreadyAllocatedDirectors.add(director);
        }
        String cpu = context._storagePortToCpu.get(allocatedPort);
        if (cpu != null) {
            allocatedCpus.add(cpu);
            context._alreadyAllocatedCpus.add(cpu);
        }
        if (context._storagePortToSwitchName.get(allocatedPort) != null) {
            allocatedSwitches.add(context._storagePortToSwitchName
                    .get(allocatedPort));
            context._alreadyAllocatedSwitches
                    .add(context._storagePortToSwitchName
                            .get(allocatedPort));
        }
    }

    /**
     * Filter the set of candidates based on already used entities of some type.
     * The currently entity types are engines, directors, and sanSwitches. The
     * filter is only applied if the resultant set is not empty (meaning there
     * are still remaining ports to be selected after the filter is supplied).
     * Once a port has been selected that represents each of the available
     * entities, the allocatedEntitySet is cleared, which results in cycling
     * through the entities again.
     * 
     * @param candidates
     *            - The candidate StoragePort set which will be updated if after
     *            the filter is applied there are remaining StoragePorts to be
     *            selected. Otherwise the candidates are not updated.
     * @param allocatedEntitySet
     *            - A set containing the keys of the already "allocated" or
     *            "used" entities- engines, directors, sanSwitches, etc. These
     *            keys match the keys in the contextEntityMap.
     * @param contextEntityMap
     *            - A map containing a key mapped to a set of StoragePorts
     *            belonging to the entity described by the key. For example, the
     *            key might be the director name, and the set would contain all
     *            the Storage Ports hosted by that director. Similarly, a key
     *            might be a SAN switch name, and the set would contain all the
     *            Storage Ports connected to that SAN switch.
     * @return Updated set of candidates that has been filtered.
     */
    private Set<StoragePort> filterCandidates(Set<StoragePort> candidates,
            Set<String> allocatedEntitySet,
            Map<String, Set<StoragePort>> contextEntityMap) {
        if (false == contextEntityMap.isEmpty()) {
            Map<String, Set<StoragePort>> newEntityMap = removeStoragePortSets(
                    allocatedEntitySet, contextEntityMap);
            if (newEntityMap.isEmpty() == false) {
                Set<StoragePort> newEngineSet = andStoragePortSets(candidates,
                        reduceStoragePortMap(newEntityMap));
                if (newEngineSet.isEmpty() == false) {
                    candidates = newEngineSet;
                }
            } else {
                _log.debug("Used all entities: " + allocatedEntitySet.toString());
                allocatedEntitySet.clear();
            }
        }
        return candidates;
    }

    /**
     * Given a map of String keys to Sets of StoragePorts, remove all the
     * entries in the map whoose key matches one of the removal keys. Return a
     * new copy of the revised map.
     * 
     * @param removalKeys
     * @param oldMap
     * @return
     */
    private Map<String, Set<StoragePort>> removeStoragePortSets(
            Set<String> removalKeys, Map<String, Set<StoragePort>> oldMap) {
        HashMap<String, Set<StoragePort>> newMap = new HashMap<String, Set<StoragePort>>();
        newMap.putAll(oldMap);
        for (String key : removalKeys) {
            newMap.remove(key);
        }
        return newMap;
    }

    /**
     * Reduces a Map containing Sets of StoragePorts to a single Set of Storage
     * Ports by including the ports in all the values of the map.
     * 
     * @param map
     * @return Set<StoragePort>
     */
    private Set<StoragePort> reduceStoragePortMap(
            Map<String, Set<StoragePort>> map) {
        Set<StoragePort> set = new HashSet<StoragePort>();
        for (Set<StoragePort> aSet : map.values()) {
            set.addAll(aSet);
        }
        return set;
    }

    /**
     * Logical AND of a and b
     * 
     * @param a
     * @param b
     * @return
     */
    private Set<StoragePort> andStoragePortSets(Set<StoragePort> a,
            Set<StoragePort> b) {
        Set<StoragePort> result = new HashSet<StoragePort>();
        for (StoragePort port : a) {
            if (b.contains(port)) {
                result.add(port);
            }
        }
        return result;
    }

    /**
     * Returns a AND (NOT b)
     * 
     * @param a Set<StoragePort>
     * @param b Set<String> set of StoragePort network ids
     * @return
     */
    private Set<StoragePort> andNotStoragePorts(Set<StoragePort> a,
            Set<String> b) {
        Set<StoragePort> result = new HashSet<StoragePort>();
        for (StoragePort port : a) {
            if (!b.contains(port.getPortNetworkId())) {
                result.add(port);
            }
        }
        return result;
    }

    static final Integer i17 = new Integer(17);     // directors sum to 17

    /**
     * Filters ports that only belong to directors that are paired to other directors
     * that maintain the "rule of 17". From https://community.emc.com/thread/3627?start=0&tstart=0:
     * "Best practice is to follow 'rule of 17' and connect your host to two FA's
     * that combined are equal to 17. That will ensure that the FA connected to the host,
     * will reside in two different power zones within the DMX."
     * 
     * There are three cases --
     * 1. First port to be allocated (i.e. no previously allocated port).
     * In this case, we limit ports from directors to those
     * that have a paired director summing to 17 if possible.
     * 2. We have allocated a previous port on the last iteration that
     * was the beginning of a pair. Choose StoragePorts from the paired
     * director.
     * 3. We just finished up a pair. We want to start a new pair, that
     * ideally should be from currently unused directors.
     * 
     * @param candidates -- The incoming candidate list of Storage Ports.
     * @param allocatedPort -- The previously allocated port.
     * @param allocatedPorts -- The set of all allocated ports.
     * @param context The PortAllocationContext containing all the maps
     *            indicating which ports belong to what director, etc.
     * @return Set<StoragePort> new candidates
     */
    private Set<StoragePort> filterRule17(Set<StoragePort> candidates,
            StoragePort allocatedPort, Set<String> allocatedPorts,
            Set<String> allocatedDirectors,
            PortAllocationContext context) {
        // We only use rule 17 for VMAX systems.
        if (context._systemType != StorageSystem.Type.vmax) {
            return candidates;
        }
        // It can be disabled.
        if (context._disableRule17) {
            return candidates;
        }
        // We ensure all directors have a rule17 pair for rule17 to be enabled.
        Set<String> rule17Directors = getRule17Directors(context);
        Set<String> unpairedDirectors = new HashSet<String>();
        for (String directorKey : context._directorToStoragePortSet.keySet()) {
            if (!rule17Directors.contains(directorKey)) {
                unpairedDirectors.add(directorKey);
            }
        }
        if (!unpairedDirectors.isEmpty()) {
            _log.info("Disabling rule17 because the following directors are unpaired: " + unpairedDirectors.toString());
            context._disableRule17 = true;
            return candidates;
        }
        if (allocatedPort == null) {
            // First case... we have not previously allocated a port.
            // Filter all ports non directors not paired to 17.
            // If there are any ports remaining, use those.
            if (usedAllRule17Directors(allocatedDirectors, context)) {
                _log.debug("rule17 clearing all allocated directors");
                allocatedDirectors.clear();
            }
            _log.debug("allocated directors: " + allocatedDirectors);

            context._previousRule17 = null;  // no previous pair
            Set<StoragePort> newCandidates =
                    andNotStoragePorts(getAllRule17Ports(candidates, context), allocatedPorts);
            if (newCandidates.isEmpty() == false) {
                // If the previous network left used only one director, try to pair with it.
                if (allocatedDirectors.size() == 1) {
                    for (String director : allocatedDirectors) {
                        Integer directorNumber = new Integer(director);
                        Integer pairDirector = i17 - directorNumber;
                        if (context._directorToStoragePortSet.get(pairDirector.toString()) != null) {
                            _log.info("rule 17 pair directors: " + directorNumber + " " + pairDirector);
                            candidates = context._directorToStoragePortSet.get(pairDirector.toString());
                            context._previousRule17 = pairDirector.toString();
                            return candidates;
                        }
                    }
                }
                _log.info("returning initial rule17 ports: " + portsToString(newCandidates));
                return newCandidates;
            }
            else {
                _log.info("No pairs of director numbers == 17; ignoring VMAX rule17");
                context._disableRule17 = true;
            }
        } else {
            // A port was previously allocated, but not paired.
            // Get the pair director that for the director that was last used.
            // We can tell it was not paired because context._previousRule17 is null.
            if (context._previousRule17 == null) {
                String director = context._storagePortToDirector.get(allocatedPort);
                Integer directorNumber = new Integer(director);
                Integer pairDirector = i17 - directorNumber;
                if (context._directorToStoragePortSet.get(pairDirector.toString()) != null) {
                    _log.info("rule 17 pair directors: " + directorNumber + " " + pairDirector);
                    Set<StoragePort> newCandidates =
                            andNotStoragePorts(
                                    context._directorToStoragePortSet.get(pairDirector.toString()),
                                    allocatedPorts);
                    if (newCandidates.isEmpty() == false) {
                        candidates = newCandidates;
                        context._previousRule17 = pairDirector.toString();
                    }
                }
            } else {
                // The previous port was the completion of a pair.
                // Start looking for a new port in the rule 17 paired directors.
                // If all director pairs have been used, extend the search to non-paired directors.
                context._previousRule17 = null;
                // We just allocated the second in the pair.
                if (usedAllRule17Directors(allocatedDirectors, context)) {
                    _log.info("Allocated all ports from each rule-17 director. Relaxing rule so as to use other directors");
                    _log.info("canidates: " + portsToString(candidates));
                    return candidates;
                }
                // Now we want to allow all paired ports on unused directors.
                Set<StoragePort> rule17Ports =
                        andNotStoragePorts(getAllRule17Ports(candidates, context), allocatedPorts);
                if (rule17Ports.isEmpty() == false) {
                    // Now filter out all ports on used directors if possible.
                    candidates = filterCandidates(rule17Ports, allocatedDirectors,
                            context._directorToStoragePortSet);
                    _log.info("filtered rule17 ports: " + portsToString(candidates));
                } else {
                    _log.info("all rule 17 ports used, candidates: " + portsToString(candidates));
                }
            }
        }
        return candidates;
    }

    /**
     * Takes a set of StoragePorts and returns a String containing the port names for printing.
     * 
     * @param list Set<StoragePort>
     * @return String
     */
    private String portsToString(Set<StoragePort> list) {
        StringBuilder buf = new StringBuilder();
        for (StoragePort port : list) {
            buf.append(port.getPortName() + ", ");
        }
        return buf.toString();
    }

    /**
     * Returns all the rule-17 paired candidate ports.
     * 
     * @param candidates Original list of candidates.
     * @param context Port AllocationContext
     * @return Set<StoragePort> new candidates based on pairing
     */
    private Set<StoragePort> getAllRule17Ports(Set<StoragePort> candidates, PortAllocationContext context) {
        Set<StoragePort> newCandidates = new HashSet<StoragePort>();
        for (String director : getRule17Directors(context)) {
            for (StoragePort port : context._directorToStoragePortSet.get(director)) {
                newCandidates.add(port);
            }
        }
        return newCandidates;
    }

    /**
     * Returns all the directors that can be paired with another director to equal 17.
     * This constitutes all the directors usable under rule17.
     * 
     * @param context
     * @return Set<String> set of rule17 paired directors
     */
    Set<String> getRule17Directors(PortAllocationContext context) {
        Set<String> rule17Directors = new HashSet<String>();
        for (String director : context._directorToStoragePortSet.keySet()) {
            Integer directorNumber = new Integer(director);
            Integer pairDirector = i17 - directorNumber;
            if (context._directorToStoragePortSet.get(pairDirector.toString()) != null) {
                rule17Directors.add(director);
            }
        }
        return rule17Directors;
    }

    /**
     * Returns true if already used all the Rule17 directors.
     * 
     * @param allocatedDirectors
     * @param context
     * @return true if all the Rule17 directors have been used.
     */
    private boolean usedAllRule17Directors(Set<String> allocatedDirectors, PortAllocationContext context) {
        Set<String> rule17Directors = getRule17Directors(context);
        for (String director : allocatedDirectors) {
            rule17Directors.remove(director);
        }
        return rule17Directors.isEmpty();
    }

    /**
     * Get the name of a SAN switch that is connected to this StoragePort.
     * Return null if no SAN switches are connected to this StoragePort.
     * 
     * @param port
     * @param dbClient
     * @return
     */
    public String getSwitchName(StoragePort port, DbClient dbClient) {
        URIQueryResultList uriList = new URIQueryResultList();
        dbClient.queryByConstraint(
                AlternateIdConstraint.Factory
                        .getFCEndpointRemotePortNameConstraint(port
                                .getPortNetworkId()), uriList);
        for (URI uri : uriList) {
            FCEndpoint endpoint = dbClient.queryObject(FCEndpoint.class, uri);
            if (endpoint.getSwitchName() != null) {
                // Return the switch name if it is known.
                if (endpoint.getAwolCount() == 0) {
                    return endpoint.getSwitchName();
                }
            }
        }
        return null;
    }

    PortAllocationContext context = null;

    /**
     * This code is only called in the kernel code path, not in the testing code
     * path.
     * 
     * @param dbClient
     *            -- The Cassandra client.
     * @param spList
     *            -- A list of StoragePorts in this Transport Zone
     * @param net
     *            The Network itself.
     * @param varrayURI
     *            The URI of a virtual array to which the network is assigned.
     * @param numPorts
     *            The number of ports requested to be allocated.
     * @param allowFewerPorts
     *            If set, allow fewer ports to be allocated than numPorts requested.
     * @return
     * @throws PlacementException if not enough ports are allocated
     */
    public List<StoragePort> selectStoragePorts(DbClient dbClient,
            Map<StoragePort, Long> sportMap, NetworkLite net, URI varrayURI, Integer numPorts,
            Set<StoragePort> previouslyAllocatedPorts, boolean allowFewerPorts) throws PlacementException {

        if (numPorts == null || numPorts <= 0)
        {
            numPorts = 2; // Default value if too low
        }
        // Determine if we should check connectivity from the Network's varray.auto_san_zoning
        boolean checkConnectivity = false;
        VirtualArray varray = dbClient.queryObject(VirtualArray.class, varrayURI);
        if (varray != null && NetworkScheduler.isZoningRequired(dbClient, varray) &&
                !net.getTransportType().equals(StorageProtocol.Transport.IP.name())) {
            checkConnectivity = true;
        }
        // Find all the StoragePorts in the StorageArray
        StoragePortsAllocator allocator = new StoragePortsAllocator();
        PortAllocationContext ctx = null;
        for (StoragePort sp : sportMap.keySet()) {
            StorageHADomain haDomain = null;
            if (sp.getStorageHADomain() != null) {
                haDomain = dbClient.queryObject(StorageHADomain.class, sp.getStorageHADomain());
            }
            StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, sp.getStorageDevice());
            String switchName = getSwitchName(sp, dbClient);
            if (ctx == null) {
                // Initialize context with Network, StorageSystem name, previous context.
                ctx = new PortAllocationContext(net, storageSystem.getNativeGuid(), context);
            }
            Long usage = sportMap.get(sp);
            ctx.addPort(sp, haDomain, StorageSystem.Type.valueOf(storageSystem.getSystemType()),
                    switchName, usage);
        }
        List<StoragePort> portUris = allocator.allocatePortsForNetwork(numPorts,
                ctx, checkConnectivity, previouslyAllocatedPorts, allowFewerPorts);
        context = ctx; // save context for next TZ
        return portUris;
    }

    /**
     * Adds a list of Storage Ports to the context _alreadyAllocatedXXX sets.
     * This will carry state from an a previous allocation in one network to
     * a new allocation for Vpool update in another network.
     * Does nothing if previouslyAllocatedPorts is null or an empty set.
     * 
     * @param dbClient
     * @param net
     * @param previouslyAllocatedPorts
     */
    public void addPortsToAlreadyAllocatedContext(DbClient dbClient, NetworkLite net,
            Set<StoragePort> previouslyAllocatedPorts) {
        if (previouslyAllocatedPorts == null || previouslyAllocatedPorts.isEmpty()) {
            return;
        }
        for (StoragePort sp : previouslyAllocatedPorts) {
            StorageHADomain haDomain = null;
            if (null != sp.getStorageHADomain()) {
                haDomain = dbClient.queryObject(StorageHADomain.class, sp.getStorageHADomain());
            }
            StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, sp.getStorageDevice());
            String switchName = getSwitchName(sp, dbClient);
            if (context == null) {
                context = new PortAllocationContext(net, storageSystem.getNativeGuid());
            }
            context.addPortToAlreadyAllocatedContext(sp, haDomain,
                    StorageSystem.Type.valueOf(storageSystem.getSystemType()), switchName);
        }
    }

    /**
     * Choose one of the ports with minimum usage from the candidate list.
     * 
     * @param candidates - set of candidates for allocation
     * @param usageMap -- Map of port to usage (greater number is higher usage)
     * @return chosen port
     */
    private StoragePort chooseCandidate(Set<StoragePort> candidates, Map<StoragePort, Long> usageMap) {
        StoragePort chosenPort = null;
        long minUsage = Long.MAX_VALUE;
        for (StoragePort sp : candidates) {
            Long usage = usageMap.get(sp);
            _log.debug(String.format("Port %s usage %d", sp.getPortName(), usage));
            if (usage < minUsage) {
                minUsage = usage;
                chosenPort = sp;
            }
        }
        return chosenPort;
    }

    public PortAllocationContext getContext() {
        return context;
    }

    public void setContext(PortAllocationContext context) {
        this.context = context;
    }
}
