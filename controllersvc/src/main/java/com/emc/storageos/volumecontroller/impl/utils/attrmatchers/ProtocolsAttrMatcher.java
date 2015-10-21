/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils.attrmatchers;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageProtocol;
import com.emc.storageos.db.client.model.StorageProtocol.File;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DiscoveryStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.StorageProtocol.Block;
import com.emc.storageos.db.client.model.StorageProtocol.Transport;
import com.emc.storageos.db.client.model.VirtualPool.SystemType;
import com.emc.storageos.db.client.util.EndpointUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.valid.Endpoint;
import com.emc.storageos.volumecontroller.AttributeMatcher;
import com.google.common.base.Joiner;
import com.google.common.collect.Sets;

/**
 * ProtocolAttrMatcher is responsible to match all the pool protocols with
 * the given protocols.
 * 
 */
public class ProtocolsAttrMatcher extends AttributeMatcher {

    private static final Logger _logger = LoggerFactory
            .getLogger(ProtocolsAttrMatcher.class);

    @Override
    public List<StoragePool> matchStoragePoolsWithAttributeOn(List<StoragePool> allPools, Map<String, Object> attributeMap) {
        List<StoragePool> matchedPools = new ArrayList<StoragePool>();
        @SuppressWarnings("unchecked")
        Set<String> protocolsRequested = (Set<String>) attributeMap.get(Attributes.protocols.toString());
        _logger.info("Pools Matching Protocols Started {}, {} :", protocolsRequested,
                Joiner.on("\t").join(getNativeGuidFromPools(allPools)));
        Iterator<StoragePool> poolIterator = allPools.iterator();
        while (poolIterator.hasNext()) {
            StoragePool pool = poolIterator.next();
            boolean isPoolMatched = false;
            if (null != pool.getProtocols()) {
                Set<String> protocolsNotMatched = Sets.difference(protocolsRequested, pool.getProtocols());
                if (protocolsNotMatched.isEmpty() ||
                        isFilePoolMatchedRequestedProtocols(pool, protocolsRequested)) {
                    matchedPools.add(pool);
                    isPoolMatched = true;
                }
            }
            if (!isPoolMatched) {
                _logger.info("Ignoring pool {} id: {} as it doesn't support protocols.", pool.getNativeGuid(), pool.getNativeGuid());
            }
        }
        getNetworkMatchingPoolsForVnxe(matchedPools, protocolsRequested, attributeMap);
        _logger.info("Pools Matching Protocols Ended: {}", Joiner.on("\t").join(getNativeGuidFromPools(matchedPools)));
        return matchedPools;
    }

    @Override
    protected boolean isAttributeOn(Map<String, Object> attributeMap) {
        // ProtocolsAttrMatcher will be executed only if there are protocols.
        return attributeMap != null && attributeMap.containsKey(Attributes.protocols.toString());
    }

    @Override
    public Map<String, Set<String>> getAvailableAttribute(List<StoragePool> neighborhoodPools,
            URI varrayId) {
        try {
            Map<String, Set<String>> availableAttrMap = new HashMap<String, Set<String>>(1);
            Set<String> availableAttrValues = new HashSet<String>();
            Map<URI, Set<String>> arrayProtocolsMap = new HashMap<URI, Set<String>>();
            for (StoragePool pool : neighborhoodPools) {
                StringSet protocols = pool.getProtocols();
                if (null != protocols && !protocols.isEmpty()) {
                    URI arrayUri = pool.getStorageDevice();
                    Set<String> existProtocols = arrayProtocolsMap.get(arrayUri);
                    if (existProtocols != null) {
                        existProtocols.addAll(protocols);
                    } else {
                        arrayProtocolsMap.put(arrayUri, protocols);
                    }

                }
            }
            if (!arrayProtocolsMap.isEmpty()) {
                availableAttrValues = getNetworkSupportedProtocols(arrayProtocolsMap, varrayId);
            }
            if (!availableAttrValues.isEmpty()) {
                availableAttrMap.put(Attributes.protocols.toString(), availableAttrValues);
                return availableAttrMap;
            }
        } catch (Exception e) {
            _logger.error("Exception occurred while getting available attributes using ProtocolsMatcher.", e);
        }
        return Collections.emptyMap();
    }

    /**
     * Returns if the storage pool's protocol matches requested protocols when
     * the storagePool's protocol is set to NFS_OR_CIFS
     * 
     * @param pool storagePool
     * @param protocolsRequested requested protocols
     * @return if storagePool's protocol matches the requested protocols
     */
    private boolean isFilePoolMatchedRequestedProtocols(StoragePool pool,
            Set<String> protocolsRequested) {

        boolean isMatched = false;
        StringSet poolProtocols = pool.getProtocols();
        if (poolProtocols.contains(StorageProtocol.File.NFS_OR_CIFS.name())
                && protocolsRequested.size() == 1) {
            Iterator<String> it = protocolsRequested.iterator();
            String protocol = it.next();
            if (protocol.equalsIgnoreCase(StorageProtocol.File.NFS.name())
                    || protocol.equalsIgnoreCase(StorageProtocol.File.CIFS.name())
                    || protocol.equalsIgnoreCase(StorageProtocol.File.NFSv4.name()) ) {
                isMatched = true;
            }
        }
        return isMatched;
    }

    /**
     * Get supported protocols by the storage pools and storage ports in the varray
     * 
     * @param arrayProtocolsMap supported pool protocols per array
     * @param varrayId
     * @return the set of protocols supported
     */
    private Set<String> getNetworkSupportedProtocols(Map<URI, Set<String>> arrayProtocolsMap,
            URI varrayId) {
        Set<String> protocols = new HashSet<String>();
        for (Map.Entry<URI, Set<String>> entry : arrayProtocolsMap.entrySet()) {
            URI arrayUri = entry.getKey();
            Set<String> poolProtocols = entry.getValue();
            if (poolProtocols.contains(File.NFS_OR_CIFS.name())) {
                poolProtocols.remove(File.NFS_OR_CIFS.name());
                poolProtocols.add(File.NFS.name());
                poolProtocols.add(File.CIFS.name());
            }
            Set<String> portProtocols = new HashSet<String>();
            URIQueryResultList storagePortURIs = new URIQueryResultList();
            _objectCache.getDbClient().queryByConstraint(
                    ContainmentConstraint.Factory.getStorageDeviceStoragePortConstraint(arrayUri),
                    storagePortURIs);
            Iterator<URI> storagePortsIter = storagePortURIs.iterator();
            while (storagePortsIter.hasNext()) {
                URI storagePortURI = storagePortsIter.next();
                StoragePort storagePort = _objectCache.queryObject(StoragePort.class,
                        storagePortURI);
                // only usable storage port will be checked
                Set<String> varrays = new HashSet<String>();
                varrays.add(varrayId.toString());
                if (!isPortUsable(storagePort, varrays)) {
                    continue;
                }
                portProtocols.addAll(transport2Protocol(storagePort.getTransportType()));
                if (portProtocols.containsAll(poolProtocols)) {
                    break;
                }

            }
            poolProtocols.retainAll(portProtocols);

            protocols.addAll(poolProtocols);
        }

        return protocols;
    }

    /**
     * Convert storage port transport type to related protocols.
     * 
     * @param transportType
     * @return
     */
    private Set<String> transport2Protocol(String transport) {
        Set<String> protocols = new HashSet<String>();
        if (transport == null) {
            return protocols;
        }
        if (Transport.IP.name().equals(transport)) {
            protocols.add(Block.iSCSI.name());
            protocols.add(File.NFS.name());
            protocols.add(File.CIFS.name());
            protocols.add(File.NFSv4.name());
        }
        if (Transport.Ethernet.name().equals(transport)) {
            protocols.add(Block.FCoE.name());
            return protocols;
        }
        if (Transport.FC.name().equals(transport)) {
            protocols.add(Block.FC.name());
            return protocols;
        }
        if (Transport.ScaleIO.name().equals(transport)) {
            protocols.add(Block.ScaleIO.name());
            return protocols;
        }
        return protocols;
    }

    /**
     * VNXe supports both of file and block, and storage pools supports both block and file too.
     * This method will check on the network storage ports type to match up the requested protocols.
     * e.g. if the requested protocols are for file, and there is no file storage ports
     * in the networks, it would remove the storage pools from the matchedPools list.
     * 
     * @param matchedPools
     * @param protocolRequested
     * @param attributeMap
     * @return
     */
    private List<StoragePool> getNetworkMatchingPoolsForVnxe(List<StoragePool> matchedPools,
            Set<String> protocolRequested, Map<String, Object> attributeMap) {
        Map<URI, Set<StoragePool>> arrayPoolMap = new HashMap<URI, Set<StoragePool>>();
        for (StoragePool pool : matchedPools) {
            URI arrayId = pool.getStorageDevice();
            if (arrayId == null) {
                continue;
            }
            StorageSystem storageSystem = _objectCache.queryObject(StorageSystem.class,
                    arrayId);
            if (storageSystem == null) {
                continue;
            }
            String systemType = storageSystem.getSystemType();
            if (systemType != null && systemType.equalsIgnoreCase(SystemType.vnxe.name())) {
                // only check on VNXe pools
                Set<StoragePool> arrayPools = arrayPoolMap.get(arrayId);
                if (arrayPools != null) {
                    arrayPools.add(pool);
                } else {
                    Set<StoragePool> pools = new HashSet<StoragePool>();
                    pools.add(pool);
                    arrayPoolMap.put(arrayId, pools);
                }
            }
        }
        if (arrayPoolMap.isEmpty()) {
            return matchedPools;
        }
        @SuppressWarnings("unchecked")
        Set<String> vArrays = (Set<String>) attributeMap.get(Attributes.varrays.toString());
        _logger.info("Checking for VNXe matching pools.");
        for (Map.Entry<URI, Set<StoragePool>> entry : arrayPoolMap.entrySet()) {
            URI arrayUri = entry.getKey();
            URIQueryResultList storagePortURIs = new URIQueryResultList();
            _objectCache.getDbClient().queryByConstraint(
                    ContainmentConstraint.Factory.getStorageDeviceStoragePortConstraint(arrayUri),
                    storagePortURIs);
            Iterator<URI> storagePortsIter = storagePortURIs.iterator();
            boolean isMatching = false;
            while (storagePortsIter.hasNext()) {
                URI storagePortURI = storagePortsIter.next();
                StoragePort storagePort = _objectCache.queryObject(StoragePort.class,
                        storagePortURI);
                // only usable storage port will be checked
                if (!isPortUsable(storagePort, vArrays)) {
                    continue;
                }
                String tranportType = storagePort.getTransportType();
                String endpoint = storagePort.getPortNetworkId();
                if (protocolRequested.contains(Block.FC.name())) {
                    if (tranportType.equalsIgnoreCase(Transport.FC.name()) &&
                            EndpointUtility.isValidEndpoint(endpoint, Endpoint.EndpointType.WWN)) {
                        isMatching = true;
                        break;
                    }
                } else if (protocolRequested.contains(Block.iSCSI.name())) {
                    if (tranportType.equalsIgnoreCase(Transport.IP.name()) &&
                            EndpointUtility.isValidEndpoint(endpoint, Endpoint.EndpointType.SAN)) {
                        isMatching = true;
                        break;
                    }
                } else if (protocolRequested.contains(File.CIFS.name()) ||
                        protocolRequested.contains(File.NFS.name())) {
                    if (tranportType.equalsIgnoreCase(Transport.IP.name()) &&
                            EndpointUtility.isValidEndpoint(endpoint, Endpoint.EndpointType.IP)) {
                        isMatching = true;
                        break;
                    }
                }
            }
            if (!isMatching) {
                _logger.info("Removing all storage pools of the array: {} from the matching pools.", arrayUri);
                matchedPools.removeAll(arrayPoolMap.get(arrayUri));
            }
        }

        return matchedPools;

    }

    /**
     * Check if the storagePort belongs to any of the varrays, and usable
     * 
     * @param storagePort
     * @param varrays
     * @return
     */
    private boolean isPortUsable(StoragePort storagePort, Set<String> varrays) {
        boolean isUsable = true;
        if (storagePort == null ||
                storagePort.getInactive() ||
                storagePort.getTaggedVirtualArrays() == null ||
                NullColumnValueGetter.isNullURI(storagePort.getNetwork()) ||
                !RegistrationStatus.REGISTERED.toString()
                        .equalsIgnoreCase(storagePort.getRegistrationStatus()) ||
                (StoragePort.OperationalStatus.valueOf(storagePort.getOperationalStatus()))
                        .equals(StoragePort.OperationalStatus.NOT_OK) ||
                !DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name()
                        .equals(storagePort.getCompatibilityStatus()) ||
                !DiscoveryStatus.VISIBLE.name().equals(storagePort.getDiscoveryStatus())) {

            isUsable = false;
        } else {
            StringSet portVarrays = storagePort.getTaggedVirtualArrays();
            portVarrays.retainAll(varrays);
            if (portVarrays.isEmpty()) {
                // the storage port does not belongs to any varrays
                isUsable = false;
            }
        }
        return isUsable;
    }
}
