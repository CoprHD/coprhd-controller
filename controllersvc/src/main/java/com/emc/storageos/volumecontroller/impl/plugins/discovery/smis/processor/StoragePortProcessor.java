/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StorageHADomain.HADomainType;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
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
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.UnsignedInteger16;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Processor responsible for handling Provider response data and creates Physical StoragePorts
 */
public class StoragePortProcessor extends StorageProcessor {
    private Logger _logger = LoggerFactory.getLogger(StoragePortProcessor.class);
    private static final String PORTNAME = "EMCPortName";
    private static final String DEVICEID = "DeviceID";
    private static final String SPEED = "Speed";
    private static final String SYSTEMNAME = "SystemName";
    private static final String LINKTECHNOLOGY = "LinkTechnology";
    private static final String USAGERESTRICTION = "UsageRestriction";
    private static final String FC = "FC";
    private static final String IP = "IP";
    private static final String ISCSI = "iSCSI";
    private static final String OPERATIONALSTATUS = "OperationalStatus";
    private static final String EMC_PORT_ATTRIBUTES = "EMCPortAttributes";
    private static final String EMCADAPTERNUMBER = "EMCAdapterNumber";
    private static final UnsignedInteger16 EMC_PORT_ATTRIBUTE_ACLX_FLAG = new UnsignedInteger16(1);
    private AccessProfile profile = null;
    private List<StoragePort> _newPortList = null;
    private List<StoragePort> _updatePortList = null;
    private DbClient _dbClient;
    private static final int GB = 1024 * 1024 * 1024;
    private List<Object> args;
    private static final UnsignedInteger16 ok_code = new UnsignedInteger16(2);
    private static final UnsignedInteger16 stopped_code = new UnsignedInteger16(10);

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        try {
            final Iterator<CIMInstance> it = (Iterator<CIMInstance>) resultObj;
            profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
            Set<String> protocols = (Set<String>) keyMap.get(Constants.PROTOCOLS);
            _newPortList = new ArrayList<StoragePort>();
            _updatePortList = new ArrayList<StoragePort>();
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            CoordinatorClient coordinator = (CoordinatorClient) keyMap.get(Constants.COORDINATOR_CLIENT);
            Map<URI, StoragePool> poolsToMatchWithVpool = (Map<URI, StoragePool>) keyMap.get(Constants.MODIFIED_STORAGEPOOLS);
            Set<URI> systemsToRunRPConnectivity = (HashSet<URI>) keyMap.get(Constants.SYSTEMS_RUN_RP_CONNECTIVITY);
            StorageSystem device = _dbClient.queryObject(StorageSystem.class, profile.getSystemId());
            CIMObjectPath storageAdapterPath = getObjectPathfromCIMArgument(args);
            Iterable<String> adapterItr = Splitter.on(Constants.PATH_DELIMITER_PATTERN)
                    .limit(3).split(storageAdapterPath.getKey(Constants.NAME).getValue().toString());
            String adapterNativeGuid = NativeGUIDGenerator.generateNativeGuid(device,
                    Iterables.getLast(adapterItr),
                    NativeGUIDGenerator.ADAPTER);
            StorageHADomain haDomain = getStorageAdapter(_dbClient, adapterNativeGuid);
            if (null == haDomain) {
                _logger.info("Adapter Not found");
                return;
            }

            while (it.hasNext()) {
                CIMInstance portInstance = null;
                StoragePort port = null;
                try {
                    portInstance = it.next();

                    // skip back end ports other than RDF Ports
                    if (!HADomainType.REMOTE.name().equalsIgnoreCase(
                            haDomain.getAdapterType())
                            && "3".equalsIgnoreCase(getCIMPropertyValue(portInstance, USAGERESTRICTION))) {
                        continue;
                    }
                    // only if its an EthernetPort, as protocolEnd point needs
                    // to run only for Ethernet
                    // Ports , because SCSI address we don't have it in
                    // CIM_LogicalPort Class
                    // 2 - Ethernet Port 4 - FC Port
                    if ("2".equalsIgnoreCase(getCIMPropertyValue(portInstance, LINKTECHNOLOGY))) {
                        port = createStoragePort(null, portInstance, profile, haDomain, false, IP, device);
                        checkProtocolAlreadyExists(protocols, ISCSI);
                        String deviceId = getCIMPropertyValue(portInstance, DEVICEID);
                        /*
                         * For SMI-S 8.x, While getting the iSCSI Port details, we use SystemName property
                         * (Ex. SYMMETRIX-+-<<SERIAL>>-+-SE-1G-+-0)
                         * Where this call just add the deviceId to the KeyMap (i.e SE-1G-+-0).
                         * Hence manually constructing the key.
                         */
                        if (device.getUsingSmis80()) {
                            String systemName = getCIMPropertyValue(portInstance, SYSTEM_NAME);
                            StringBuffer deviceIdStrBuf = new StringBuffer(systemName);
                            if (device.checkIfVmax3()) {
                                deviceIdStrBuf.append(Constants.SMIS80_DELIMITER).append(getCIMPropertyValue(portInstance, EMCADAPTERNUMBER));
                            }
                            else {
                                deviceIdStrBuf.append(Constants.SMIS80_DELIMITER).append(deviceId);
                            }
                            deviceId = deviceIdStrBuf.toString();
                        }
                        _logger.debug("Adding iSCSI Port instance {} to keyMap", deviceId);
                        keyMap.put(deviceId, port);
                        addPath(keyMap, operation.getResult(), portInstance.getObjectPath());
                    } else if ("4".equalsIgnoreCase(getCIMPropertyValue(portInstance, LINKTECHNOLOGY))) {
                        port = checkStoragePortExistsInDB(portInstance, device, _dbClient);
                        checkProtocolAlreadyExists(protocols, FC);
                        createStoragePort(port, portInstance, profile, haDomain, true, FC, device);
                    } else {
                        _logger.debug("Unsupported Port : {}", getCIMPropertyValue(portInstance, DEVICEID));
                    }

                } catch (Exception e) {
                    _logger.warn("Port Discovery failed for {}",
                            getCIMPropertyValue(portInstance, DEVICEID), e);
                }
            }
            _dbClient.createObject(_newPortList);
            _dbClient.persistObject(_updatePortList);

            // ports used later to run Transport Zone connectivity
            List<List<StoragePort>> portsUsedToRunTZoneConnectivity = (List<List<StoragePort>>) keyMap.get(Constants.STORAGE_PORTS);
            portsUsedToRunTZoneConnectivity.add(_newPortList);
            portsUsedToRunTZoneConnectivity.add(_updatePortList);

            List<StoragePool> modifiedPools = StoragePoolAssociationHelper.getStoragePoolsFromPorts(_dbClient, _newPortList,
                    _updatePortList);
            for (StoragePool pool : modifiedPools) {
                // pool matcher will be invoked on this pool
                if (!poolsToMatchWithVpool.containsKey(pool.getId())) {
                    poolsToMatchWithVpool.put(pool.getId(), pool);
                }
            }

            // Systems used to run RP connectivity later after runing pool matcher
            systemsToRunRPConnectivity.addAll(StoragePoolAssociationHelper.getStorageSytemsFromPorts(_newPortList, null));
            systemsToRunRPConnectivity.addAll(StoragePoolAssociationHelper.getStorageSytemsFromPorts(_updatePortList, null));

            // discovered ports used later to check for not visible ports
            List<StoragePort> discoveredPorts = (List<StoragePort>) keyMap.get(Constants.DISCOVERED_PORTS);
            discoveredPorts.addAll(_newPortList);
            discoveredPorts.addAll(_updatePortList);

            _logger.debug("# Pools used in invoking PoolMatcher during StoragePortProcessor {}",
                    Joiner.on("\t").join(poolsToMatchWithVpool.keySet()));
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
    private void checkProtocolAlreadyExists(Set<String> protocols, String protocolType) {
        if (!protocols.contains(protocolType)) {
            protocols.add(protocolType);
        }
    }

    /**
     * create StoragePort Record, if not present already, else update only the properties.
     * 
     * @param port
     * @param portInstance
     * @throws URISyntaxException
     * @throws IOException
     */
    private StoragePort createStoragePort(
            StoragePort port, CIMInstance portInstance, AccessProfile profile, StorageHADomain haDomain,
            boolean flag, String transportType, StorageSystem device) throws URISyntaxException, IOException {
        boolean newPort = false;
        if (null == port) {
            newPort = true;
            port = new StoragePort();
            port.setId(URIUtil.createId(StoragePort.class));
            // if true, then its FC Port or else its Ethernet, PORTID
            // or ethernet will be updated later in ProtocolEndPoint Processor
            if (flag) {
                port.setPortNetworkId(WWNUtility.getWWNWithColons(getCIMPropertyValue(portInstance, PORTID)));
            }
            port.setStorageDevice(profile.getSystemId());
            String portNativeGuid = NativeGUIDGenerator.generateNativeGuid(_dbClient, port);
            port.setNativeGuid(portNativeGuid);
            port.setLabel(portNativeGuid);
            port.setPortGroup(haDomain.getAdapterName());
            port.setStorageHADomain(haDomain.getId());

        }
        setPortType(port, portInstance);
        port.setTransportType(transportType);
        port.setPortName(getCIMPropertyValue(portInstance, PORTNAME));
        port.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());
        port.setDiscoveryStatus(DiscoveredDataObject.DiscoveryStatus.VISIBLE.name());

        UnsignedInteger16[] operationalStatusCodes = (UnsignedInteger16[]) portInstance.getPropertyValue(OPERATIONALSTATUS);
        OperationalStatus operationalStatus = getPortOperationalStatus(operationalStatusCodes);
        if (OperationalStatus.NOT_OK.equals(operationalStatus)) {
            _logger.info("StoragePort {} operationalStatus is NOT_OK. operationalStatusCodes collected from SMI-S :{}"
                    , port.getId(), operationalStatusCodes);
        } else {
            _logger.debug("operationalStatusCodes :{}", operationalStatusCodes);

            // there can be multiple statuses. {OK, Online}, {OK, Stopped}
            if (operationalStatusCodes != null && operationalStatusCodes.length > 1 &&
                    Arrays.asList(operationalStatusCodes).contains(stopped_code)) {
                _logger.info("StoragePort {} operational status is {OK, Stopped}. operationalStatusCodes :{}",
                        port.getId(), operationalStatusCodes);
            }
        }
        port.setOperationalStatus(operationalStatus.name());
        String portSpeed = getCIMPropertyValue(portInstance, SPEED);
        if (null != portSpeed) {
            // SMI returns port speed in bits per sec ?? Is this always true?
            Long portSpeedInBitsPerSec = Long.parseLong(portSpeed);
            Long portSpeedInGbps = portSpeedInBitsPerSec / GB;
            port.setPortSpeed(portSpeedInGbps);
        }
        setCompatibilityByACLXFlag(device, portInstance, port);
        if (flag) {
            if (newPort) {
                _logger.info("Creating port - {}:{}", port.getLabel(), port.getNativeGuid());
                _newPortList.add(port);
            }
            else {
                _logger.info("Updating port - {}:{}", port.getLabel(), port.getNativeGuid());
                _updatePortList.add(port);
            }
        }
        ;
        return port;
    }

    private void setCompatibilityByACLXFlag(StorageSystem storageSystem, CIMInstance portInstance, StoragePort port) {
        Object portAttributesValue = portInstance.getPropertyValue(EMC_PORT_ATTRIBUTES);
        if (portAttributesValue != null && storageSystem.checkIfVmax3()) {
            boolean foundACLXFlag = false;
            UnsignedInteger16[] portAttributes = (UnsignedInteger16[]) portAttributesValue;
            for (UnsignedInteger16 portAttribute : portAttributes) {
                if (portAttribute.equals(EMC_PORT_ATTRIBUTE_ACLX_FLAG)) {
                    foundACLXFlag = true;
                    break;
                }
            }
            // VMAX GIGE ports do not report EMC_PORT_ATTRIBUTE_ACLX_FLAG
            // If there is Virtual ProtocolEndpoint associated to this physical port, consider masking is enabled...
            if ("2".equalsIgnoreCase(getCIMPropertyValue(portInstance, LINKTECHNOLOGY))) {
                foundACLXFlag = true;
            }
            String compatibilityStatus = (foundACLXFlag) ? DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name() :
                    DiscoveredDataObject.CompatibilityStatus.INCOMPATIBLE.name();
            _logger.info(String.format("setCompatibilityByACLXFlag(%s) = %s",
                    port.getNativeGuid(), compatibilityStatus));
            port.setCompatibilityStatus(compatibilityStatus);
        }
    }

    private void setPortType(StoragePort port, CIMInstance portInstance) {
        if ("2".equalsIgnoreCase(getCIMPropertyValue(portInstance, USAGERESTRICTION))) {
            port.setPortType(PortType.frontend.toString());
        } else if ("3".equalsIgnoreCase(getCIMPropertyValue(portInstance, USAGERESTRICTION))) {
            port.setPortType(PortType.rdf.toString());
        } else {
            port.setPortType(PortType.Unknown.toString());
        }

    }

    /**
     * Returns operationalStatus based on the given operationalCodes collected from smisProvider.
     * OpertaionalCode 2 means Ok.
     * ValueMap("0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, .., 0x8000.."),
     * Values(
     * "Unknown, Other, OK, Degraded, Stressed, Predictive Failure, Error, Non-Recoverable Error, Starting, Stopping, Stopped, In Service, No Contact, Lost Communication, Aborted, Dormant, Supporting Entity in Error, Completed, Power Mode, Relocating, DMTF Reserved, Vendor Reserved"
     * )]
     * 
     * @param operationalCodes
     * @return
     */
    public static OperationalStatus getPortOperationalStatus(UnsignedInteger16[] operationalCodes) {
        OperationalStatus result = StoragePort.OperationalStatus.NOT_OK;

        // operationalStatusCodes may have multiple statuses ({OK, Online}, {OK, Stopped}, {Stopped, OK})
        if (operationalCodes != null && Arrays.asList(operationalCodes).contains(ok_code)) {
            result = StoragePort.OperationalStatus.OK;
        }
        return result;
    }

    private void setStorageAdapterReference(StoragePort port,
            CIMInstance portInstance, StorageSystem device) {
        String adapterName = null;
        try {
            adapterName = portInstance.getObjectPath().getKey(SYSTEMNAME)
                    .getValue().toString();
            Iterable<String> adapterItr = Splitter.on(Constants.PATH_DELIMITER_PATTERN)
                    .limit(3).split(adapterName);
            URIQueryResultList result = new URIQueryResultList();

            _dbClient
                    .queryByConstraint(
                            AlternateIdConstraint.Factory
                                    .getStorageHADomainByNativeGuidConstraint(NativeGUIDGenerator
                                            .generateNativeGuid(device,
                                                    Iterables.getLast(adapterItr),
                                                    NativeGUIDGenerator.ADAPTER)),
                            result);
            if (result.iterator().hasNext()) {
                URI portGroup = result.iterator().next();
                port.setStorageHADomain(portGroup);
                StorageHADomain haDomain = _dbClient.queryObject(
                        StorageHADomain.class, portGroup);
                port.setPortGroup(haDomain.getAdapterName());
            }
        } catch (Exception e) {
            _logger.warn("Storage Port not found : {}", adapterName);
        }
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        args = inputArgs;
    }
}
