/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils.attrmatchers;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.plugins.common.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DiscoveredSystemObject;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageProtocol;
import com.emc.storageos.db.client.model.StorageProtocol.Block;
import com.emc.storageos.db.client.model.StorageProtocol.Transport;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.volumecontroller.AttributeMatcher;
import com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor.PortMetricsProcessor;
import com.google.common.collect.Sets;

/**
 * This matcher checks that if the VirtualPool.num_paths attribute is set,
 * then a StoragePool is only matched if the array containing it has at least
 * as many "usable" ports of the designated transport type(s) as the num_paths variable.
 * 
 * This is a somewhat inexact test. We cannot guarantee that an Export will succeed
 * even with this check, because we don't know the distribution of initiators in the
 * Export to Networks. On the other hand, this check can cause Pools to be excldued
 * that could be used in certain circumstances because the user actually used fewer
 * initiator than num_paths.
 * 
 * What this matcher guarantees is that there are at least as many "usable" ports
 * on the array as max_paths. "Usable" is defined in a comment below.
 * 
 * @author watson
 * 
 */
public class NumPathsMatcher extends AttributeMatcher {
    private static final Logger _logger = LoggerFactory
            .getLogger(NumPathsMatcher.class);

    private static volatile PortMetricsProcessor _portMetricsProcessor;

    public void setPortMetricsProcessor(PortMetricsProcessor portMetricsProcessor) {
        if (_portMetricsProcessor == null) {
            _portMetricsProcessor = portMetricsProcessor;
        }
    }

    @Override
    protected boolean isAttributeOn(Map<String, Object> attributeMap) {
        return (null != attributeMap && attributeMap.containsKey(Attributes.max_paths
                .toString()));
    }

    /**
     * Filters out all pools hosted by arrays that have fewer "usable" ports of the
     * designated transport type(s) than the num_paths attribute.
     */
    @Override
    protected List<StoragePool> matchStoragePoolsWithAttributeOn(
            List<StoragePool> allPools, Map<String, Object> attributeMap) {
        boolean checkIP = false;
        boolean checkFC = false;
        // If not a block vpool, then everything matches.
        if (false == attributeMap.get(Attributes.vpool_type.toString())
                .equals(VirtualPool.Type.block.name())) {
            return allPools;
        }
        // If Vplex high availability is used, then do not filter on maxPaths because
        // the VPlex itself contains no pools, and the underlying array pools should match
        // regardless of the maxPath settings.
        Object highAvailabilityType = attributeMap.get(Attributes.high_availability_type.toString());
        if (highAvailabilityType != null
                && NullColumnValueGetter.isNotNullValue(highAvailabilityType.toString())) {
            return allPools;
        }
        // If protocols is not specified, can't determine which type of ports to check.
        Set<String> protocols = (Set<String>) attributeMap.get(Attributes.protocols.toString());
        Set<String> vArrays = (Set<String>) attributeMap.get(Attributes.varrays.toString());
        if (protocols != null && protocols.contains(Block.FC.name())) {
            checkFC = true;
        }
        if (protocols != null && protocols.contains(Block.iSCSI.name())) {
            checkIP = true;
        }
        Integer maxPaths = (Integer) attributeMap.get(Attributes.max_paths.toString());
        Map<URI, Integer> cachedUsableFCPorts = new HashMap<URI, Integer>();
        Map<URI, Integer> cachedUsableIPPorts = new HashMap<URI, Integer>();
        Map<URI, Integer> cachedUsableFCHADomains = new HashMap<URI, Integer>();
        Map<URI, Integer> cachedUsableIPHADomains = new HashMap<URI, Integer>();
        List<StoragePool> matchedPools = new ArrayList<StoragePool>();
        Map<URI, StorageSystem> storageSystemMap = new HashMap<URI, StorageSystem>();
        for (StoragePool pool : allPools) {
            URI dev = pool.getStorageDevice();
            StorageSystem system = getStorageSystem(storageSystemMap, pool);
            if (checkFC) {
                if (numberOfUsablePorts(dev, Transport.FC, vArrays, cachedUsableFCPorts, cachedUsableFCHADomains)
                < maxPaths) {
                    _logger.info("NumPathsMatcher disqualified pool: " + pool.getNativeGuid()
                            + " max_paths: " + maxPaths
                            + " because insufficient FC ports");
                    continue;
                }
                // If we need two or more paths, must have at least two HA Domains
                if (!system.getIsDriverManaged()
                        && !system.getSystemType().equals(DiscoveredSystemObject.Type.scaleio.name())
                        && !system.getSystemType().equals(DiscoveredSystemObject.Type.xtremio.name())
                        && !system.getSystemType().equals(DiscoveredSystemObject.Type.ceph.name())) {
                    if (maxPaths >= 2 && cachedUsableFCHADomains.get(dev) < 2) {
                        _logger.info("NumPathsMatcher disqualified pool: " + pool.getNativeGuid() + " max_paths: " + maxPaths
                                + " because insufficient FC cpus (StorageHADomains)");
                        continue;
                    }
                }
            }
            if (checkIP) {
                if (numberOfUsablePorts(dev, Transport.IP, vArrays, cachedUsableIPPorts, cachedUsableIPHADomains)
                < maxPaths) {
                    _logger.info("NumPathsMatcher disqualified pool: " + pool.getNativeGuid()
                            + " max_paths: " + maxPaths
                            + " because insufficient IP ports");
                    continue;
                }
                // If we need two or more paths, must have at least two HA Domains
                if (!system.getSystemType().equals(DiscoveredSystemObject.Type.scaleio.name())
                        && !system.getSystemType().equals(DiscoveredSystemObject.Type.xtremio.name())
                        && !system.getSystemType().equals(DiscoveredSystemObject.Type.ceph.name())) {
                    if (maxPaths >= 2 && cachedUsableIPHADomains.get(dev) < 2) {
                        _logger.info("NumPathsMatcher disqualified pool: " + pool.getNativeGuid() + " max_paths: " + maxPaths
                                + " because insufficient IP cpus (StorageHADomains)");
                        continue;
                    }
                }
            }
            matchedPools.add(pool);
        }
        _logger.info("NumPathsMatcher maxPaths: " + maxPaths + " passed " + matchedPools.size() + " pools");
        return matchedPools;
    }

    /**
     * Returns the number of usable storage ports.
     * Also generates the number of distinct StorageHADomains in the cachedUsableHADomains map.
     * To be "usable", a port must be:
     * 1. Not inactive (or null)
     * 2. Registered
     * 3. Must be front-end port
     * 4. Must be associated with a Network
     * 5. OperationalStatus must not be NOT_OK
     * 6. Port must not be over a ceiling value
     * 
     * @param storageDeviceURI -- device URI
     * @param transportType -- FC or IP
     * @param vArrays
     * @param cachedUsablePorts A cache of device URI to number of usable ports of specified transportType
     * @param cachedUsableHADomains -- A cache of device URI to number of HA domains available
     * @return number of usable ports
     */
    private int numberOfUsablePorts(URI storageDeviceURI,
            StorageProtocol.Transport transportType,
            Set<String> vArrays, Map<URI, Integer> cachedUsablePorts,
            Map<URI, Integer> cachedUsableHADomains) {
        Integer usable = cachedUsablePorts.get(storageDeviceURI);
        if (usable != null) {
            return usable;
        } else {
            usable = 0;
        }
        StorageSystem storageDevice = _objectCache.queryObject(StorageSystem.class, storageDeviceURI);
        if (storageDevice == null || storageDevice.getInactive() == true) {
            cachedUsablePorts.put(storageDeviceURI, new Integer(0));
            cachedUsableHADomains.put(storageDeviceURI, new Integer(0));
            return 0;
        }

        Set<URI> haDomains = new HashSet<URI>();
        URIQueryResultList storagePortURIs = new URIQueryResultList();
        _objectCache.getDbClient().queryByConstraint(
                ContainmentConstraint.Factory.getStorageDeviceStoragePortConstraint(
                        storageDeviceURI),
                storagePortURIs);
        List<StoragePort> storagePorts = _objectCache.queryObject(StoragePort.class,
                storagePortURIs);

        // CTRL-10769. If ports part of selected vArrays are of RDF type only, skip that system's pools.
        boolean nonFrontendPortFound = false;
        boolean atLeastOneFrontEndPortFound = false;
        for (StoragePort storagePort : storagePorts) {
            if (transportType.name().equals(storagePort.getTransportType())
                    && vArrays != null && storagePort.getTaggedVirtualArrays() != null
                    && !Sets.intersection(vArrays, storagePort.getTaggedVirtualArrays()).isEmpty()) {
                if (!StoragePort.PortType.frontend.name().equals(storagePort.getPortType())) {
                    nonFrontendPortFound = true;
                } else {
                    atLeastOneFrontEndPortFound = true;
                    break;
                }
            }
        }

        if (nonFrontendPortFound && !atLeastOneFrontEndPortFound) {
            cachedUsablePorts.put(storageDeviceURI, new Integer(0));
            cachedUsableHADomains.put(storageDeviceURI, new Integer(0));
            return 0;
        }

        for (StoragePort storagePort : storagePorts) {
            // must not be null or incompatible or inactive
            _logger.debug("Checking port: " + storagePort.getNativeGuid());
            if (transportType.name().equals(storagePort.getTransportType()) &&
                    _portMetricsProcessor.isPortUsable(storagePort) &&
                    !_portMetricsProcessor.isPortOverCeiling(storagePort, storageDevice, false)) {
                haDomains.add(storagePort.getStorageHADomain());
                usable++;
            }
        }

        _logger.info("System: " + storageDevice.getNativeGuid() + " transport: " + transportType
                + " usable: " + usable + " haDomains" + haDomains.size());
        cachedUsablePorts.put(storageDeviceURI, usable);
        cachedUsableHADomains.put(storageDeviceURI, haDomains.size());
        return usable;
    }
}
