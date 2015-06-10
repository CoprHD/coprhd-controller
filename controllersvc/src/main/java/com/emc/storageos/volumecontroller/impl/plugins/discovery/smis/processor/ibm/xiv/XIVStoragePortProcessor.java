/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.ibm.xiv;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.cim.CIMInstance;
import javax.cim.UnsignedInteger16;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageHADomain.HADomainType;
import com.emc.storageos.db.client.model.StoragePort.OperationalStatus;
import com.emc.storageos.db.client.model.StoragePort.PortType;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.util.WWNUtility;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.StoragePoolAssociationHelper;
import com.emc.storageos.volumecontroller.impl.StoragePortAssociationHelper;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.StoragePortProcessor;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.StorageProcessor;
import com.google.common.base.Joiner;

/**
 * Processor responsible for handling Provider response data and creates
 * Physical StoragePorts
 */
public class XIVStoragePortProcessor extends StorageProcessor {
    private Logger _logger = LoggerFactory
            .getLogger(XIVStoragePortProcessor.class);

    private static final String PORTNAME = "ElementName";
    private static final String DEVICEID = "DeviceID";
    private static final String SPEED = "Speed";
    private static final String LINKTECHNOLOGY = "LinkTechnology";
    private static final String USAGERESTRICTION = "UsageRestriction";
    private static final String FC = "FC";
    private static final String IP = "IP";
    private static final String ISCSI = "iSCSI";
    private static final String OPERATIONALSTATUS = "OperationalStatus";
    private static final String IDENTIFYING_INFO = "OtherIdentifyingInfo";
    private static final String IPPORT_CLASS_NAME = "IBMTSDS_EthernetPort";    
    private static final int GB = 1024 * 1024 * 1024;
    
    private AccessProfile _profile = null;
    private List<StoragePort> _newPortList = null;
    private List<StoragePort> _updatePortList = null;
    private DbClient _dbClient = null;

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public void processResult(Operation operation, Object resultObj,
            Map<String, Object> keyMap) throws BaseCollectionException {
        try {
            final Iterator<CIMInstance> it = (Iterator<CIMInstance>) resultObj;
            _profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
            Set<String> protocols = (Set<String>) keyMap
                    .get(Constants.PROTOCOLS);
            _newPortList = new ArrayList<StoragePort>();
            _updatePortList = new ArrayList<StoragePort>();
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            Map<URI,StoragePool> poolsToMatchWithVpool = (Map<URI, StoragePool>) keyMap.get(Constants.MODIFIED_STORAGEPOOLS);
            StorageSystem device = _dbClient.queryObject(StorageSystem.class,
                    _profile.getSystemId());

            while (it.hasNext()) {
                CIMInstance portInstance = null;
                StoragePort port = null;
                try {
                    portInstance = it.next();
                    String protocol = getCIMPropertyValue(portInstance,
                            LINKTECHNOLOGY);
                    String className = portInstance.getClassName();
                    // FC Port - 4
                    if ("4".equals(protocol)) {
                        port = checkStoragePortExistsInDB(portInstance, device,
                                _dbClient);
                        checkProtocolAlreadyExists(protocols, FC);
                        createStoragePort(port, portInstance, _profile, true,
                                FC, device);
                    } else if (IPPORT_CLASS_NAME.equals(className)) {
                        port = createStoragePort(null, portInstance, _profile,
                                false, IP, device);
                        checkProtocolAlreadyExists(protocols, ISCSI);                        
                        keyMap.put(portInstance.getObjectPath().toString(), port);
                        addPath(keyMap, operation.get_result(),
                                portInstance.getObjectPath());
                    } else {
                        _logger.debug("Unsupported Port : {}",
                                getCIMPropertyValue(portInstance, DEVICEID));
                    }
                } catch (Exception e) {
                    _logger.warn("Port Discovery failed for {}",
                            getCIMPropertyValue(portInstance, DEVICEID), e);
                }
            }

            _dbClient.createObject(_newPortList);
            _dbClient.persistObject(_updatePortList);

            //ports used later to run Transport Zone connectivity
            List<List<StoragePort>> portsUsedToRunTZoneConnectivity = (List<List<StoragePort>>) keyMap.get(Constants.STORAGE_PORTS);
            portsUsedToRunTZoneConnectivity.add(_newPortList);
            portsUsedToRunTZoneConnectivity.add(_updatePortList);
            
            List<StoragePool> modifiedPools = StoragePoolAssociationHelper.getStoragePoolsFromPorts(_dbClient, _newPortList, _updatePortList);
            for (StoragePool pool : modifiedPools) {
                // pool matcher will be invoked on this pool
                if (!poolsToMatchWithVpool.containsKey(pool.getId())) {
                    poolsToMatchWithVpool.put(pool.getId(), pool);
                }
            }
            
            _logger.debug("# Pools used in invoking PoolMatcher during StoragePortProcessor {}", Joiner.on("\t").join(poolsToMatchWithVpool.keySet()));
            
            //discovered ports used later to check for not visible ports
            List<StoragePort> discoveredPorts = (List<StoragePort>) keyMap.get(Constants.DISCOVERED_PORTS);
            discoveredPorts.addAll(_newPortList);
            discoveredPorts.addAll(_updatePortList);

            // if the port's end point is in a transport zone, associate the the
            // port to the
            // transport zone. Also update the storage pools and varray
            // association if needed.
            StoragePortAssociationHelper.updatePortAssociations(_newPortList,
                    _dbClient);
            StoragePortAssociationHelper.updatePortAssociations(
                    _updatePortList, _dbClient);
        } catch (Exception e) {
            _logger.error("Port Discovery failed -->{}", getMessage(e));
        } finally {
            _newPortList = null;
            _updatePortList = null;
        }
    }

    /**
     * Verify whether protocolType already exists or not. If it doesn't exist
     * then add.
     * 
     * @param protocols
     * @param protocolType
     */
    private void checkProtocolAlreadyExists(Set<String> protocols,
            String protocolType) {
        if (!protocols.contains(protocolType)) {
            protocols.add(protocolType);
        }
    }

    /**
     * create StoragePort Record, if not present already, else update only the
     * properties.
     * 
     * @param port
     * @param portInstance
     * @param profile
     * @param isFCPort
     * @param transportType
     * @param device
     * 
     * @throws URISyntaxException
     * @throws IOException
     * 
     * @return StoragePort
     */
    private StoragePort createStoragePort(StoragePort port,
            CIMInstance portInstance, AccessProfile profile, boolean isFCPort,
            String transportType, StorageSystem device)
            throws URISyntaxException, IOException {

        if (null == port) {
            port = new StoragePort();
            port.setId(URIUtil.createId(StoragePort.class));

            // Ethernet will be updated later in ProtocolEndPoint Processor
            if (isFCPort) {
                port.setPortNetworkId(WWNUtility
                        .getWWNWithColons(getCIMPropertyValue(portInstance,
                                PORTID)));
                _newPortList.add(port);
            }

            port.setStorageDevice(profile.getSystemId());
            String portNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                    _dbClient, port);
            port.setNativeGuid(portNativeGuid);
            port.setLabel(portNativeGuid);
        } else {
            if (isFCPort) {
                _updatePortList.add(port);
            }
        }

        setPortType(port, portInstance);
        port.setTransportType(transportType);
    
        String[] identifiers = getCIMPropertyArrayValue(portInstance, IDENTIFYING_INFO);
        String moduleName = null;
        if (isFCPort) {
            moduleName = identifiers[0];
            String portName = getCIMPropertyValue(portInstance, PORTNAME);
            port.setPortName(portName);
        } else {
            moduleName = identifiers[1];
            port.setPortName(identifiers[1] + ":" + identifiers[0]);
            
            // port type is not set for ethernet port in SMI
            if (port.getPortType().equals(StoragePort.PortType.Unknown.name())) {
                port.setPortType(StoragePort.PortType.frontend.name());
            }
        }
        
        port.setPortGroup(moduleName);
        StorageHADomain adapter = getStorageAdapter(moduleName, device);
        port.setStorageHADomain(adapter.getId());

        port.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE
                .name());
        port.setDiscoveryStatus(DiscoveredDataObject.DiscoveryStatus.VISIBLE.name());

        UnsignedInteger16[] operationalStatusCodes = (UnsignedInteger16[]) portInstance
                .getPropertyValue(OPERATIONALSTATUS);
        OperationalStatus operationalStatus = StoragePortProcessor.getPortOperationalStatus(operationalStatusCodes);
        if (OperationalStatus.NOT_OK.equals(operationalStatus)) {
            _logger.info(
                    "StoragePort {} operationalStatus is NOT_OK. operationalStatusCodes collected from SMI-S :{}",
                    port.getId(), operationalStatusCodes);
        } else {
            _logger.debug("operationalStatusCodes: {}", operationalStatusCodes);
        }

        port.setOperationalStatus(operationalStatus.name());

        String portSpeed = getCIMPropertyValue(portInstance, SPEED);
        if (null != portSpeed) {
            // SMI returns port speed in bits per sec
            Long portSpeedInBitsPerSec = Long.parseLong(portSpeed);
            Long portSpeedInGbps = portSpeedInBitsPerSec / GB;
            port.setPortSpeed(portSpeedInGbps);
        }
        
        return port;
    }

    private void setPortType(StoragePort port, CIMInstance portInstance) {
        String portType = getCIMPropertyValue(portInstance, USAGERESTRICTION);
        if ("2".equalsIgnoreCase(portType)) {
            port.setPortType(PortType.frontend.name());
        } else if ("3".equalsIgnoreCase(portType)) {
            port.setPortType(PortType.backend.name());
        } else {
            port.setPortType(PortType.Unknown.name());
        }
    }

    /**
     * Returns StorageAdapter instance. Create one, if not present already
     *
     * @param name adapter (module) name
     * @param device StorageSystem
     * @return instance of StorageHADomain
     * @throws URISyntaxException
     * @throws IOException
     */
    private StorageHADomain getStorageAdapter(String name, StorageSystem device)
            throws URISyntaxException, IOException {
        StorageHADomain adapter = null;
        String nativeGuid = NativeGUIDGenerator.generateNativeGuid(
                device, name, NativeGUIDGenerator.ADAPTER);
        @SuppressWarnings("deprecation")
        List<URI> adapterURIs = _dbClient
                .queryByConstraint(AlternateIdConstraint.Factory
                        .getStorageHADomainByNativeGuidConstraint(nativeGuid));
        if (!adapterURIs.isEmpty()) {
            adapter = _dbClient.queryObject(StorageHADomain.class,
                    adapterURIs.get(0));
        }

        if (adapter == null) {
            adapter = new StorageHADomain();
            adapter.setId(URIUtil.createId(StorageHADomain.class));
            adapter.setStorageDeviceURI(device.getId());
            adapter.setName(name);
            adapter.setAdapterName(name);
            adapter.setNativeGuid(nativeGuid);
            adapter.setAdapterType(HADomainType.FRONTEND.name());
            adapter.setVirtual(true);
            // note serial number and number of ports is not set
            adapter.setProtocol("3"); // 3 is "mixed", to be consistent with EMC provider
            // slot number is used in port selection
            adapter.setSlotNumber(name.split(Pattern.quote(Constants.COLON))[2]); // e.g., name="1:Module:6", 6 is the module
            _dbClient.createObject(adapter);
        }

        return adapter;
    }
}
