/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

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

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DiscoveryStatus;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;

public class VplexExportGroupServiceApiImpl extends
AbstractExportGroupServiceApiImpl {
    private static final Logger _log = LoggerFactory
            .getLogger(VplexExportGroupServiceApiImpl.class);

    /**
     * Validate varray ports during Export Group Create. If varray
     * contains ports from both Vplex Cluster 1 and Cluster 2 thats an invalid
     * network configuration. It could be one or more network within a varray.
     * If initiators of a host are in two networks then vplex storage ports from 
     * both networks are taken into account to check if ports belong to both
     * Vplex Cluster 1 and Cluster 2.
     * 
     * @param storageSystemURIs vplex storageSystem URIs
     * @param varray source VirtualArray
     * @param allHosts 
     * @throws InternalException
     */
    @Override
    public void validateVarrayStoragePorts(Set<URI> storageSystemURIs,
            VirtualArray varray, List<URI> allHosts)
                    throws InternalException {
        try {
            // Get VirtualArray Storage ports by Network.
            Map<Network, Set<StoragePort>> networkToPortsMap = getVirtualArrayTaggedPortsByNework(varray
                    .getId());
            
            Map<URI, Set<URI>> vplexCluster1ports = new HashMap<URI, Set<URI>>();
            Map<URI, Set<URI>> vplexCluster2ports = new HashMap<URI, Set<URI>>();
            Map<URI, StorageSystem> storageSystems = new HashMap<URI, StorageSystem>();

            // Iterate over all vplex storage systems and add results to above three maps
            // Separate cluster1 and cluster 2 ports of the provided vplex storageSystemURIs
            for (URI uri : storageSystemURIs) {
                StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class,
                        uri);
                URIQueryResultList storagePortURIs = new URIQueryResultList();
                _dbClient.queryByConstraint(ContainmentConstraint.Factory
                        .getStorageDeviceStoragePortConstraint(storageSystem.getId()),
                        storagePortURIs);
                final String cluster1 = "1";
                final String cluster2 = "2";
                Set<URI> cluster1StoragePorts = new HashSet<URI>();
                Set<URI> cluster2StoragePorts = new HashSet<URI>();
                Iterator<URI> storagePortsIter = storagePortURIs.iterator();
                while (storagePortsIter.hasNext()) {
                    URI storagePortURI = storagePortsIter.next();
                    StoragePort storagePort = _dbClient.queryObject(StoragePort.class,
                            storagePortURI);
                    if (storagePort != null
                    		&& !storagePort.getInactive()
                    		&& storagePort.getRegistrationStatus().equals(
                    				DiscoveredDataObject.RegistrationStatus.REGISTERED.name())
                            && !storagePort.getDiscoveryStatus().equalsIgnoreCase(
                                    DiscoveryStatus.NOTVISIBLE.name())) {
                    	//Port Group has value like director-1-1-A, first number
                    	//after director- in this string determines vplex cluster
                    	if (storagePort.getPortGroup() != null) {
                    		String[] tokens = storagePort.getPortGroup().split("-");
                    		if (cluster1.equals(tokens[1])) {
                    			cluster1StoragePorts.add(storagePort.getId());
                    		} else if (cluster2.equals(tokens[1])) {
                    			cluster2StoragePorts.add(storagePort.getId());
                    		} else {
                    			_log.warn("Could not determine cluster for storageport:"
                    					+ storagePort.getPortNetworkId() + " "
                    					+ storagePort.getId() + " Port group is:"
                    					+ storagePort.getPortGroup());
                    		}
                    	} else {
                    		_log.warn("Could not determine cluster for storageport:"
                    				+ storagePort.getPortNetworkId() + " " + storagePort.getId());
                    	}
                    }
                }
                vplexCluster1ports.put(uri, cluster1StoragePorts);
                vplexCluster2ports.put(uri, cluster2StoragePorts);
                storageSystems.put(uri, storageSystem);
            }

            for (URI hostUri : allHosts) {
                Map<URI, StoragePort> networkStoragePortsForHost = getNetworkTaggedPortsForHost(
                        hostUri, networkToPortsMap);
                // Validate if storage ports seen by the host belong to both the
                // clusters of the vplex.
                for (URI uri : storageSystemURIs) {

                    Set<URI> intersection1 = new HashSet<URI>(networkStoragePortsForHost.keySet());
                    Set<URI> intersection2 = new HashSet<URI>(networkStoragePortsForHost.keySet());

                    intersection1.retainAll(vplexCluster1ports.get(uri));
                    intersection2.retainAll(vplexCluster2ports.get(uri));
                    
                    // Ports should only be either in intersection1 or intersection2,
                    //if we have ports in both then its a mix ports from cluster 1 and cluster 2
                    if (intersection1.size() != 0 && intersection2.size() != 0) {
                        Map<URI, String> cluster1Ports = new HashMap<URI, String>();
                        Map<URI, String> cluster2Ports = new HashMap<URI, String>();
                        // Get port information so that we can print in log cluster 1 and cluster 2 ports 
                        //which belong in the same varray
                        for (URI uriIntersection1 : intersection1) {
                            if (networkStoragePortsForHost.get(uriIntersection1) != null) {
                                cluster1Ports.put(uriIntersection1, 
                                        networkStoragePortsForHost.get(uriIntersection1).getPortNetworkId());
                            }
                        }
                        for (URI uriIntersection2 : intersection2) {
                            if (networkStoragePortsForHost.get(uriIntersection2) != null) {
                                cluster2Ports.put(uriIntersection2,
                                        networkStoragePortsForHost.get(uriIntersection2).getPortNetworkId());
                            }
                        }
                        Host host = _dbClient.queryObject(Host.class, hostUri);
                        _log.error("Varray "
                                + varray.getLabel()
                                + " has storageports from Cluster 1 and Cluster 2 of the Vplex "
                                + storageSystems.get(uri).getLabel() + " "
                                + storageSystems.get(uri).getId().toString()
                                + ". This is detected for the host " + host.getHostName()
                                + "\n Cluster 1 storageports in varray are"
                                + cluster1Ports
                                + "\n Cluster 2 storageports in varray are"
                                + cluster2Ports);
                        throw APIException.badRequests
                        .invalidVarrayNetworkConfiguration(varray.getLabel(),storageSystems.get(uri).getLabel());
                    }
                }
            }
            _log.info("Done validating vplex cluster 1 and 2 ports for the Varray:" + varray.getLabel());
        } catch (InternalException ex) {
            _log.error(ex.getLocalizedMessage());
            throw (ex);
        }
    }

    /**
     * This method returns all StoragePorts from networks containing initiators
     * of a host.
     * 
     * @param hostUri
     * @param networkToPortsMap map of network to storageport set
     */
    private Map<URI, StoragePort> getNetworkTaggedPortsForHost(URI hostUri,
            Map<Network, Set<StoragePort>> networkToPortsMap) {
        URIQueryResultList initiatorURIs = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getContainedObjectsConstraint(hostUri, Initiator.class, "host"),
                initiatorURIs);
        Map<URI, StoragePort> networkStoragePorts = new HashMap<URI, StoragePort>();
        Set<StoragePort> storagePorts = new HashSet<StoragePort>();
        Iterator<URI> initiatorURIsIter = initiatorURIs.iterator();
        while (initiatorURIsIter.hasNext()) {
            URI initiatorURI = initiatorURIs.iterator().next();
            Initiator initiator = _dbClient.queryObject(Initiator.class, initiatorURI);
            if (!initiator.getInactive() && initiator.getRegistrationStatus().equals(
                    DiscoveredDataObject.RegistrationStatus.REGISTERED.name())) {
                for (Map.Entry<Network, Set<StoragePort>> entry : networkToPortsMap
                        .entrySet()) {
                    if (entry.getKey().retrieveEndpoints().contains(initiator.getInitiatorPort())) {
                        storagePorts.addAll(entry.getValue());
                    }
                }
            }
        }
        for (StoragePort storagePort : storagePorts) {
            networkStoragePorts.put(storagePort.getId(), storagePort);
        }
        return networkStoragePorts;
    }

    /**
     * This methods looks for tagged virtual array ports if it belongs to a
     * network then adds it to a map.
     * 
     * @param varray virtual array uri
     * @return a map of network to storageport set
     */
    private Map<Network, Set<StoragePort>> getVirtualArrayTaggedPortsByNework(URI varray) {
        Map<URI, Set<StoragePort>> registeredNetworkStoragePorts = new HashMap<URI, Set<StoragePort>>();

        URIQueryResultList storagePortURIs = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getVirtualArrayStoragePortsConstraint(varray.toString()),
                storagePortURIs);
        Iterator<URI> storagePortURIsIter = storagePortURIs.iterator();

        while (storagePortURIsIter.hasNext()) {
            URI storagePortURI = storagePortURIsIter.next();
            StoragePort storagePort = _dbClient.queryObject(StoragePort.class,
                    storagePortURI);

            if (storagePort != null
                    && !storagePort.getInactive()
                    && !NullColumnValueGetter.isNullURI(storagePort.getNetwork())
                    && storagePort.getRegistrationStatus().equals(
                            DiscoveredDataObject.RegistrationStatus.REGISTERED.name())
                    && !storagePort.getDiscoveryStatus().equalsIgnoreCase(
                            DiscoveryStatus.NOTVISIBLE.name())) {
                if (registeredNetworkStoragePorts.get(storagePort.getNetwork()) == null) {
                    Set<StoragePort> storageports = new HashSet<StoragePort>();
                    storageports.add(storagePort);
                    registeredNetworkStoragePorts.put(storagePort.getNetwork(),
                            storageports);
                } else {
                    registeredNetworkStoragePorts.get(storagePort.getNetwork()).add(
                            storagePort);
                }
            }
        }
        Map<Network, Set<StoragePort>> networkToStoragePortsMap = new HashMap<Network, Set<StoragePort>>();
        for (Map.Entry<URI, Set<StoragePort>> entry : registeredNetworkStoragePorts
                .entrySet()) {
            Network network = _dbClient.queryObject(Network.class, entry.getKey());
            networkToStoragePortsMap.put(network, entry.getValue());
        }
        return networkToStoragePortsMap;
    }
    
    /**
     * Determines the virtual arrays associated with a VPLEX volume.
     * 
     * @param volume The VPLEX volume
     * @param vplexSystem The VPLEX storage system
     * @param dbClient A reference to the database client.
     * 
     * @return A list of URIs of the virtual array URIs.
     */
    public static List<URI> getVolumeVirtualArrays(Volume volume,
        StorageSystem vplexSystem, DbClient dbClient) {
       List<URI> varrayURIs = new ArrayList<URI>();
       // For volumes created in ViPR we could use the varrays specified
       // for the associated backend volumes. However, for ingested 
       // volumes, the associated volumes will not be set. We know
       // one varray is the varray for the VPLEX volume itself, so
       // add it to the list.
       varrayURIs.add(volume.getVirtualArray());
       
       // If the volume is a distributed volume, then the other varray
       // is the HA varray specified in the vpool for the volume.
       String volumeId = volume.getId().toString();
       URI vpoolURI = volume.getVirtualPool();
       if (vpoolURI != null) {
           VirtualPool vpool = dbClient.queryObject(VirtualPool.class, vpoolURI);
           if (vpool != null) {
               String vplexHA = vpool.getHighAvailability();
               if (VirtualPool.HighAvailabilityType.vplex_distributed.name().equals(vplexHA)) {
                   StringMap haVarrayMap = vpool.getHaVarrayVpoolMap();
                   if ((haVarrayMap != null) && (!haVarrayMap.isEmpty())) {
                       varrayURIs.add(URI.create(haVarrayMap.keySet().iterator().next()));
                   } else {
                       _log.error("HA varray not set in vpool {} for VPLEX volume {}", vpoolURI, volumeId);                       
                   }
               }
           } else {
               _log.error("Could not find virtual pool {} for VPLEX volume {}", vpoolURI, volumeId);               
           }
       } else {
           _log.error("Virtual pool is not set for VPLEX volume {}", volumeId);
       }
       
       return varrayURIs;
    }
}
