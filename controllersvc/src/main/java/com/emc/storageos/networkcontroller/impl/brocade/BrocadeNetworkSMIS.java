/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.networkcontroller.impl.brocade;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.cim.CIMArgument;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.cim.UnsignedInteger32;
import javax.wbem.CloseableIterator;
import javax.wbem.WBEMException;
import javax.wbem.client.WBEMClient;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;
import org.joda.time.format.DateTimeFormat;

import com.emc.storageos.cimadapter.connections.cim.CimObjectPathCreator;
import com.emc.storageos.db.client.model.FCEndpoint;
import com.emc.storageos.db.client.util.EndpointUtility;
import com.emc.storageos.model.valid.Endpoint.EndpointType;
import com.emc.storageos.networkcontroller.BaseSANCIMObject;
import com.emc.storageos.networkcontroller.SSHDialog;
import com.emc.storageos.networkcontroller.exceptions.NetworkControllerSessionLockedException;
import com.emc.storageos.networkcontroller.exceptions.NetworkDeviceControllerException;
import com.emc.storageos.networkcontroller.impl.mds.Zone;
import com.emc.storageos.networkcontroller.impl.mds.ZoneMember;
import com.emc.storageos.networkcontroller.impl.mds.ZoneMember.ConnectivityMemberType;
import com.emc.storageos.networkcontroller.impl.mds.ZoneWwnAlias;
import com.emc.storageos.networkcontroller.impl.mds.Zoneset;
import com.emc.storageos.volumecontroller.impl.smis.CIMArgumentFactory;
import com.emc.storageos.volumecontroller.impl.smis.CIMPropertyFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BrocadeNetworkSMIS extends BaseSANCIMObject {

    private final Integer defaultTimeout = 5 * 60000; // in milliseconds
    private static final String _namespace = "root/brocade1";
    private static final String _fabric_path = "Brocade_Fabric";
    private static final String _zoneset_fabric_path = "Brocade_ZoneSetInFabric";
    private static final String _zoneset_name = "Brocade_ZoneSet";
    private static final String _agent_name = "Brocade_Agent";
    private static final String _name = "Name";
    private static final String _element_name = "ElementName";
    private static final String _CollectionAlias = "CollectionAlias";
    private static final String _Brocade_PhysicalSwitch = "Brocade_PhysicalComputerSystem";
    private static final String _Brocade_SwitchPCS = "Brocade_SwitchInPCS";
    private static final String _Brocade_TopologyViewInFabric = "Brocade_TopologyViewInFabric";
    private static final String _Brocade_ZoneInZoneSet = "Brocade_ZoneInZoneSet";
    private static final String _Brocade_ZoneInFabric = "Brocade_ZoneInFabric";
    private static final String _Brocade_Zone = "Brocade_Zone";
    private static final String _Brocade_ZoneAlias = "Brocade_ZoneAlias";
    private static final String _Brocade_ZoneAliasInZone = "Brocade_ZoneAliasInZone";
    private static final String _Brocade_ZoneAliasInFabric = "Brocade_ZoneAliasInFabric";
    private static final String _Brocade_TopologyView = "Brocade_TopologyView";
    private static final String _Brocade_ZoneMembershipSettingDataInZone = "Brocade_ZoneMembershipSettingDataInZone";
    private static final String _Brocade_ZoneMembershipSettingDataInZoneAlias = "Brocade_ZoneMembershipSettingDataInZoneAlias";
    private static final String _Brocade_ZoneMembershipSettingData = "Brocade_ZoneMembershipSettingData";
    private static final String _Brocade_ZoneServiceInFabric = "Brocade_ZoneServiceInFabric";
    private static final String _ActivateZoneSet = "ActivateZoneSet";
    private static final String _Brocade_ZoneService = "Brocade_ZoneService";
    private static final String _ConnectivityMemberID = "ConnectivityMemberID";
    private static final String _ConnectivityMemberType = "ConnectivityMemberType";
    private static final String _AntecedentFCPortType = "AntecedentFCPortType";
    private static final String _DependentFCPortWWN = "DependentFCPortWWN";
    private static final String _DependentElementWWN = "DependentElementWWN";
    private static final String _AntecedentFCPortWWN = "AntecedentFCPortWWN";
    private static final String _AntecedentFCPortElementName = "AntecedentFCPortElementName";
    private static final String _AntecedentElementWWN = "AntecedentElementWWN";
    private static final String _AntecedentSystem = "AntecedentSystem";
    private static final String _Parent = "Antecedent";
    private static final String _Child = "Dependent";
    private static final String _GroupComponent = "GroupComponent";
    private static final String _PartComponent = "PartComponent";
    private static final String _SystemSpecificCollection = "SystemSpecificCollection";
    private static final String _CreateZoneMembershipSettingData = "CreateZoneMembershipSettingData";
    private static final String _AddZoneAlias = "AddZoneAlias";
    private static final String _SessionState = "SessionState";
    private static final String _requestedSessionState = "requestedSessionState";
    private static final String _SessionControl = "SessionControl";
    private static final String _StartTime = "StartTime";
    private static final String _ManagementServerVersion = "ManagementServerVersion";
    private static final String _XlatePhantomPort = "Xlate Phantom Port";
    private static final String _ZoneSet = "ZoneSet";
    private static final String _Zone = "Zone";
    private static final String _ZoneAlias = "ZoneAlias";
    private static final String _ZoneSetName = "ZoneSetName";
    private static final String _CreateZoneSet = "CreateZoneSet";
    private static final String _ZoneName = "ZoneName";
    private static final String _ZoneType = "ZoneType";
    private static final String _ZoneSubType = "ZoneSubType";
    private static final String _CreateZone = "CreateZone";
    private static final String _CreateZoneAlias = "CreateZoneAlias";
    private static final String _ManagedElement = "ManagedElement";
    private static final String _SettingData = "SettingData";
    private static final String _Activate = "Activate";
    private static final String _active = "Active";
    private static final String _ZMType_Wwn = "5";
    private static final int _started = 2;
    private static final int _ended = 3;
    private static final int _notApplicable = 4;
    private static final int _terminated = 4;
    private static final int _noChange = 5;
    private static final int _clientPort = 15;
    private CIMArgumentFactory _cimArgumentFactory = new CIMArgumentFactory();
    private CIMPropertyFactory _cimProperty = new CIMPropertyFactory();

    private static final Logger _log = LoggerFactory.getLogger(BrocadeNetworkSMIS.class);

    public static String getNamespace() {
        return _namespace;
    }

    /**
     * Returns a list of fabric names.
     * 
     * @param client
     *            - WBEM Client
     * @return List<String> containing fabric names
     * @throws javax.wbem.WBEMException
     */
    public List<String> getFabricIds(WBEMClient client) throws WBEMException {
        List<String> fabricNames = new ArrayList<String>();
        CIMObjectPath path = CimObjectPathCreator.createInstance(_fabric_path, _namespace);
        CloseableIterator<CIMInstance> it = null;
        try {
            it = client.enumerateInstances(path,
                    false, true, true, null);
            while (it.hasNext()) {
                CIMInstance ins = it.next();
                String wwnName = cimStringProperty(ins, _name);
                String fabricName = cimStringProperty(ins, _element_name);
                _log.info("Fabric: " + fabricName + " (" + wwnName + ")");
                fabricNames.add(fabricName);
            }
        } finally {
            if (it != null) {
                it.close();
            }
        }
        return fabricNames;
    }

    /**
     * Returns a list of fabric names.
     * 
     * @param client
     *            - WBEM Client
     * @return Map<String, String> containing fabric WWN-to-fabric-name
     * @throws javax.wbem.WBEMException
     */
    public Map<String, String> getFabricIdsMap(WBEMClient client) throws WBEMException {
        Map<String, String> fabricNames = new HashMap<String, String>();
        CIMObjectPath path = CimObjectPathCreator.createInstance(_fabric_path, _namespace);
        CloseableIterator<CIMInstance> it = null;
        try {
            it = client.enumerateInstances(path,
                    false, true, true, null);
            while (it.hasNext()) {
                CIMInstance ins = it.next();
                String wwnName = formatWWN(cimStringProperty(ins, _name)).toUpperCase();
                String fabricName = cimStringProperty(ins, _element_name);
                _log.debug("Fabric: " + fabricName + " (" + wwnName + ")");
                fabricNames.put(wwnName, fabricName);

            }
        } finally {
            if (it != null) {
                it.close();
            }
        }
        return fabricNames;
    }

    /**
     * Returns a map of fabric CIMObjectPath to its WWN.
     * 
     * @param client
     *            - WBEM Client
     * @return Map<String, String> containing fabric CIMObjectPath to its WWN
     * @throws javax.wbem.WBEMException
     */
    public Map<String, String> getFabricPathToWwnMap(WBEMClient client) throws WBEMException {
        Map<String, String> fabricPaths = new HashMap<String, String>();
        CIMObjectPath path = CimObjectPathCreator.createInstance(_fabric_path, _namespace);
        CloseableIterator<CIMInstance> it = null;
        try {
            it = client.enumerateInstances(path,
                    false, true, true, null);
            while (it.hasNext()) {
                CIMInstance ins = it.next();
                String wwnName = formatWWN(cimStringProperty(ins, _name)).toUpperCase();
                _log.debug("Fabric: " + ins.getObjectPath() + " (" + wwnName + ")");
                fabricPaths.put(ins.getObjectPath().toString(), wwnName);
            }
        } finally {
            if (it != null) {
                it.close();
            }
        }
        return fabricPaths;
    }

    /**
     * Given the fabricId, get its wwn
     * 
     * @param client an instance of WBEMClient
     * @param fabricId the fabricId (or vsan id)
     * @return the fabric formatted WWN
     * @throws WBEMException
     */
    public String getFabricWwn(WBEMClient client, String fabricId) throws WBEMException {
        String fabricWwn = null;
        Map<String, String> map = getFabricIdsMap(client);
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(fabricId)) {
                fabricWwn = entry.getKey();
                break;
            }
        }

        // failed to get fabric's wwn for given fabricId. Throw exception here,
        // which will be handle and wrapped in NetworkDeviceControllerException in catch block.
        if (fabricWwn == null) {
            throw NetworkDeviceControllerException.exceptions.fabricNotFoundInNetwork(fabricId, "");
        }

        return fabricWwn;
    }

    /**
     * Get the list of port connections.
     * 
     * @param client
     *            WBEMClient
     * @param routedConnections IN/OUT parameter to get the routed endpoints map of Fabric-WWN-to-endpoints-WWN
     * @return List<FCPortConnection>
     * @throws javax.wbem.WBEMException
     */
    @SuppressWarnings("unchecked")
    public List<FCEndpoint> getPortConnections(WBEMClient client,
            Map<String, Set<String>> routedConnections,
            boolean discoverEndpointsByFabric) throws WBEMException {
        Map<String, FCEndpoint> portConnections = null;
        Map<String, String> deviceNameCache = new HashMap<String, String>();
        Map<String, String> fabricsByIds = getFabricIdsMap(client);
        Map<String, String> logicalToPhysicalSwitchMap =
                getLogicalToPhysicalSwitcheMap(client);

        if (discoverEndpointsByFabric) {
            portConnections = getFCEndpointsByFabric(client, routedConnections, fabricsByIds, deviceNameCache,
                    logicalToPhysicalSwitchMap);
        } else {
            portConnections = getFCEndpointsByTopologyViewInFabric(client, routedConnections, fabricsByIds,
                    deviceNameCache, logicalToPhysicalSwitchMap);
        }
        if (portConnections != null) {
            addEndpointAliasName(client, portConnections);
            return new ArrayList<FCEndpoint>(portConnections.values());
        } else {
            return new ArrayList<FCEndpoint>();
        }
    }

    /**
     * Get FCEndpoints instances by getting TopologyView instances by fabric. This method
     * of getting TopologyView instances can be slow in some environments thus the need
     * for the alternate function {@link #getFCEndpointsByFabric(WBEMClient, Map, Map, Map, Map)} which is faster but requires more
     * memory. The user can select the function best suitable
     * using config item controller_ns_brocade_discovery_by_fabric_association
     * 
     * @param client
     *            WBEMClient
     * @param routedConnections
     *            IN/OUT - A map where routed endpoints will be stored
     * @param fabricsByIds
     *            a map of fabric name to fabric WWN
     * @param deviceNameCache
     *            a map to cache switch names
     * @param logicalToPhysicalSwitchMap
     *            a map to cache logical switches and their container physical switch
     * @return a map of endpoint Wwn to its FCEndpoint instance
     * @throws WBEMException
     */
    private Map<String, FCEndpoint> getFCEndpointsByFabric(WBEMClient client,
            Map<String, Set<String>> routedConnections,
            Map<String, String> fabricsByIds,
            Map<String, String> deviceNameCache,
            Map<String, String> logicalToPhysicalSwitchMap) throws WBEMException {
        long start = System.currentTimeMillis();
        _log.info("Getting topology by fabric");
        Map<String, FCEndpoint> portConnections = new HashMap<String, FCEndpoint>();
        CIMObjectPath path = CimObjectPathCreator.createInstance(_fabric_path, _namespace);
        CloseableIterator<CIMInstance> it = null;
        try {
            it = client.enumerateInstances(path,
                    false, true, true, null);
            while (it.hasNext()) {
                CIMInstance ins = it.next();
                String fabricWwn = formatWWN(cimStringProperty(ins, _name));
                String fabricName = cimStringProperty(ins, _element_name);
                _log.info("Fabric: " + fabricName + " (" + fabricWwn + ")");
                CloseableIterator<CIMInstance> topit = client.associatorInstances(
                        ins.getObjectPath(), _Brocade_TopologyViewInFabric,
                        _Brocade_TopologyView, null, null, false, null);
                try {
                    CIMInstance topins = null;
                    while (topit.hasNext()) {
                        topins = topit.next();
                        _log.debug(topins.toString());
                        processTopologyViewInstance(client, topins,
                                portConnections, routedConnections,
                                fabricsByIds.get(fabricWwn), fabricWwn,
                                deviceNameCache, logicalToPhysicalSwitchMap);
                    }
                } finally {
                    if (topit != null) {
                        topit.close();
                    }
                }
            }
        } finally {
            if (it != null) {
                it.close();
            }
        }
        _log.info("Getting topology by fabric took " + (System.currentTimeMillis() - start));
        return portConnections;
    }

    /**
     * Get FCEndpoints instances by getting all instances of TopologyView to get all endpoints
     * and TopologyViewInfabric to sort them into their fabrics.
     * <p>
     * This method of getting TopologyView instances requires more memory that the alternate
     * {@link #getFCEndpointsByTopologyViewInFabric(WBEMClient, Map, Map, Map, Map)} which can be slow. The user can select the function best suitable using
     * config item controller_ns_brocade_discovery_by_fabric_association
     * 
     * @param client
     *            WBEMClient
     * @param routedConnections
     *            IN/OUT - A map where routed endpoints will be stored
     * @param fabricsByIds
     *            a map of fabric name to fabric WWN
     * @param deviceNameCache
     *            a map to cache switch names
     * @param logicalToPhysicalSwitchMap
     *            a map to cache logical switches and their container physical switch
     * @return a map of fabric Wwn to the TopologyView instances
     * @throws WBEMException
     */
    private Map<String, FCEndpoint> getFCEndpointsByTopologyViewInFabric(
            WBEMClient client,
            Map<String, Set<String>> routedConnections,
            Map<String, String> fabricsByIds,
            Map<String, String> deviceNameCache,
            Map<String, String> logicalToPhysicalSwitchMap) throws WBEMException {
        long start = System.currentTimeMillis();
        _log.info("Getting topology by TopologyViewInFabric associations");
        Map<String, FCEndpoint> portConnections = new HashMap<String, FCEndpoint>();

        // Get a map of fabric CIMObjectPath-to-WWN
        Map<String, String> fabricPathToWwn = getFabricPathToWwnMap(client);

        // Get all the TopologyViewInFabric instances and store them in a map of
        // TopologyView CIMObjectPath to Fabric CIMObjectPath
        Map<String, String> topInsToFabricPath = new HashMap<String, String>();
        CIMObjectPath assnPath = CimObjectPathCreator.createInstance(_Brocade_TopologyViewInFabric, _namespace);
        CloseableIterator<CIMObjectPath> assnIt = null;
        try {
            assnIt = client.enumerateInstanceNames(assnPath);
            while (assnIt.hasNext()) {
                CIMObjectPath assn = assnIt.next();
                _log.debug(assn.toString());
                // get the path of the TopologyView instance
                String compPath = assn.getKeyValue(_PartComponent).toString();
                // Trim the switch name when the path has /switchName/root/brocade/....
                compPath = compPath.substring(compPath.indexOf("/root"));
                // get the path of the fabric
                String grpPath = assn.getKeyValue(_GroupComponent).toString();
                // Trim the switch name when the path has /switchName/root/brocade/....
                grpPath = grpPath.substring(grpPath.indexOf("/root"));
                _log.debug("PartComponent " + compPath + " GroupComponent " + grpPath);
                topInsToFabricPath.put(compPath, grpPath);
            }
        } finally {
            if (assnIt != null) {
                assnIt.close();
            }
        }

        // Get the topology instances and map them to their fabrics
        CIMObjectPath path = CimObjectPathCreator.createInstance(_Brocade_TopologyView, _namespace);
        CloseableIterator<CIMInstance> it = null;
        try {
            it = client.enumerateInstances(path,
                    false, true, true, null);
            while (it.hasNext()) {
                CIMInstance topins = it.next();
                _log.debug(topins.toString());
                String fabricPath = topInsToFabricPath.get(topins.getObjectPath().toString());
                String fabricWwn = fabricPathToWwn.get(fabricPath);
                if (fabricWwn != null) {
                    processTopologyViewInstance(client, topins,
                            portConnections, routedConnections,
                            fabricsByIds.get(fabricWwn), fabricWwn,
                            deviceNameCache, logicalToPhysicalSwitchMap);
                }
            }
        } finally {
            if (it != null) {
                it.close();
            }
        }
        _log.info("Getting topology by TopologyViewInFabric associations took " + (System.currentTimeMillis() - start));
        return portConnections;
    }

    /**
     * generate port connections from the Topology details
     * @param client
     *            WBEMClient
     * @param topins
     *            toplogy instance
     * @param portConnections
     *            port connections
     * @param routedConnections
     *            routed connections
     * @param fabricName
     *            fabric name
     * @param fabricWwn
     *            fabric WWN
     * @param deviceNameCache
     *            a map to cache switch names
     * @param logicalToPhysicalSwitchMap
     *            a map to cache logical switches and container physical switch
     * @throws WBEMException
     */
    private void processTopologyViewInstance(WBEMClient client, CIMInstance topins,
            Map<String, FCEndpoint> portConnections,
            Map<String, Set<String>> routedConnections,
            String fabricName, String fabricWwn,
            Map<String, String> deviceNameCache,
            Map<String, String> logicalToPhysicalSwitchMap) throws WBEMException {
        if (_XlatePhantomPort.equals(cimStringProperty(topins,
                _AntecedentFCPortElementName))) {
            Set<String> fabricRoutedEndpoints = routedConnections.get(fabricWwn);
            if (fabricRoutedEndpoints == null) {
                fabricRoutedEndpoints = new HashSet<String>();
                routedConnections.put(fabricWwn, fabricRoutedEndpoints);
            }
            fabricRoutedEndpoints.add(formatWWN(cimStringProperty(topins,
                    _DependentFCPortWWN)));
            return; // if this is a routed endpoint, collect and move on
        }
        // skip things that are not fiber links e.g. eport links
        if (cimIntegerProperty(topins, _AntecedentFCPortType) != _clientPort) {
            return;
        }
        String remotePortName = formatWWN(cimStringProperty(topins,
                _DependentFCPortWWN));
        String remoteNodeName = formatWWN(cimStringProperty(topins,
                _DependentElementWWN));
        String switchPortName = formatWWN(cimStringProperty(topins,
                _AntecedentFCPortWWN));
        String switchInterfaceName = cimStringProperty(topins,
                _AntecedentFCPortElementName);
        String switchWwn = formatWWN(cimStringProperty(topins,
                _AntecedentElementWWN));
        String switchName = switchWwn;
        if (deviceNameCache.get(switchWwn) != null) {
            switchName = deviceNameCache.get(switchWwn);
        } else {
            CIMProperty switchPathProperty = topins
                    .getProperty(_AntecedentSystem);
            CIMObjectPath switchPath = (CIMObjectPath) switchPathProperty
                    .getValue();
            CloseableIterator<CIMInstance> switchIt = client
                    .enumerateInstances(switchPath, false, true, true,
                            null);
            while (switchIt.hasNext()) {
                CIMInstance swins = switchIt.next();
                String namex = formatWWN(cimStringProperty(swins,
                        _name));
                String enamex = cimStringProperty(swins, _element_name);
                if (namex.equals(switchWwn)) {
                    switchName = enamex;
                    deviceNameCache.put(switchWwn, switchName);
                }
            }
        }
        //Get the Physcial Switch Name for the Logical Switch
        String physicalSwitchName = logicalToPhysicalSwitchMap.get(switchName);
        _log.info("Switch Name : {} Physical SwitchName {}", switchName, physicalSwitchName);
        if(physicalSwitchName != null ) {
            switchName = physicalSwitchName;
        }
        FCEndpoint conn = new FCEndpoint();
        conn.setFabricId(fabricName);
        conn.setRemotePortName(remotePortName);
        conn.setRemoteNodeName(remoteNodeName);
        conn.setSwitchPortName(switchPortName);
        conn.setSwitchInterface(switchInterfaceName);
        conn.setSwitchName(switchName);
        conn.setFabricWwn(fabricWwn);
        portConnections.put(remotePortName, conn);
    }

    /**
     * Update the FCEndpoints with aliases from the alias database.
     * This function uses the associations to improve performance and parses the
     * alias name and member address from the paths:
     * Brocade_ZoneMembershipSettingDataInZoneAlias {
     * ManagedElement =
     * "root/brocade1:Brocade_ZoneAlias.InstanceID=\"NAME=Hala_REST_API_ZONE_12;ACTIVE=false;FABRIC=10000027F858F6C0;CLASSNAME=Brocade_ZoneAlias\""
     * ;
     * SettingData =
     * "root/brocade1:Brocade_ZoneMembershipSettingData.InstanceID=\"NAME=2010101010101010;ZMTYPE=5;ACTIVE=false;FABRIC=10000027F858F6C0;CLASSNAME=Brocade_ZoneMembershipSettingData\""
     * ;
     * };
     * 
     * @param client
     * @param fabricEps
     * @throws WBEMException
     */
    private void addEndpointAliasName(WBEMClient client,
            Map<String, FCEndpoint> fabricEps) {
        CloseableIterator<CIMInstance> it = null;
        long start = System.currentTimeMillis();
        int count = 0;
        try {
            CIMObjectPath path = CimObjectPathCreator.createInstance(_Brocade_ZoneMembershipSettingDataInZoneAlias, _namespace);
            it = client.enumerateInstances(path, false, true, true, null);

            CIMInstance ins = null;
            FCEndpoint ep;
            String aliasPath = null;
            String memberPath = null;
            String wwn = null;
            while (it.hasNext()) {
                count++;
                ins = it.next();
                _log.debug(ins.toString());
                aliasPath = cimStringProperty(ins, _ManagedElement);
                memberPath = cimStringProperty(ins, _SettingData);
                wwn = formatWWN(getPropertyValueFromString(memberPath, SmisConstants.CP_NSNAME));
                ep = fabricEps.get(wwn);
                if (ep != null) {
                    ep.setRemotePortAlias(getPropertyValueFromString(aliasPath, SmisConstants.CP_NSNAME));
                    fabricEps.remove(ep);
                    _log.debug("added alias " + ep.getRemotePortAlias() + " to " + ep.getRemotePortName());
                }
            }
        } catch (Exception ex) {
            _log.warn("An exception was encountered while updating the endpoint aliases. " +
                    "Discovery will proceed. The exception is: " + ex.getMessage());
        } finally {
            if (it != null) {
                it.close();
            }
        }
        _log.info("Processing " + count + " aliases took " + (start - System.currentTimeMillis()));
    }

    /**
     * Gets the list of aliases for a given fabric
     * This function uses the associations to improve performance and parses the
     * alias name and member address from the paths:
     * Brocade_ZoneMembershipSettingDataInZoneAlias {
     * ManagedElement =
     * "root/brocade1:Brocade_ZoneAlias.InstanceID=\"NAME=Hala_REST_API_ZONE_12;ACTIVE=false;FABRIC=10000027F858F6C0;CLASSNAME=Brocade_ZoneAlias\""
     * ;
     * SettingData =
     * "root/brocade1:Brocade_ZoneMembershipSettingData.InstanceID=\"NAME=2010101010101010;ZMTYPE=5;ACTIVE=false;FABRIC=10000027F858F6C0;CLASSNAME=Brocade_ZoneMembershipSettingData\""
     * ;
     * };
     * 
     * @param client an instance of WBEMClient
     * @param fabricId the name of the fabric
     * @param fabricWwn the WWN of the fabric
     * @throws WBEMException
     */
    public List<ZoneWwnAlias> getAliases(WBEMClient client,
            String fabricId, String fabricWwn) throws WBEMException {
        Map<String, ZoneWwnAlias> aliases = new HashMap<String, ZoneWwnAlias>();
        if (fabricWwn == null) {
            fabricWwn = getFabricWwn(client, fabricId);
        }
        fabricWwn = fabricWwn.replaceAll(":", "");
        CloseableIterator<CIMInstance> it = null;
        try {
            CIMObjectPath path = CimObjectPathCreator.createInstance(_Brocade_ZoneMembershipSettingDataInZoneAlias, _namespace);
            it = client.enumerateInstances(path, false, true, true, null);

            CIMInstance ins = null;
            String aliasPath = null;
            String memberPath = null;
            String wwn = null;
            ZoneWwnAlias alias = null;
            while (it.hasNext()) {
                ins = it.next();
                _log.debug(ins.toString());
                aliasPath = cimStringProperty(ins, _ManagedElement);
                memberPath = cimStringProperty(ins, _SettingData);
                wwn = getPropertyValueFromString(memberPath, SmisConstants.CP_FABRIC);
                if (StringUtils.equalsIgnoreCase(wwn, fabricWwn)) {
                    try {
                        alias = new ZoneWwnAlias();
                        alias.setAddress(formatWWN(getPropertyValueFromString(memberPath, SmisConstants.CP_NSNAME)));
                        alias.setName(getPropertyValueFromString(aliasPath, SmisConstants.CP_NSNAME));
                        // Use a map to handle the unsupported scenario when an alias has more than one member
                        // in this code the alias will arbitrarily have the the last members discovered.
                        // this is needed to ensure code dependent on this code does not receive duplicates
                        aliases.put(alias.getName(), alias);
                        _log.debug("Retreived alias name " + alias.getName() + " and address " + alias.getAddress());
                    } catch (Exception e){
                        //if the WWN is not a valid one and setAddress will throw an error
                        //Catch it , log it , skip it it and move forward with processing the rest of the data
                        _log.warn("An exception was encountered while processing " + wwn + " may be it is malformed "
                                + e.getMessage());
                    }
                }
            }
        } catch (Exception ex) {
            _log.warn("An exception was encountered while getting the aliases for " +
                    "fabric: " + fabricId + ". The exception is: " + ex.getMessage());
        } finally {
            if (it != null) {
                it.close();
            }
        }
        return new ArrayList<ZoneWwnAlias>(aliases.values());
    }

    /**
     * Creates an instance of {@link ZoneWwnAlias} from a {@link CIMInstance} after it retrieves
     * the alias member. Note, the alias is expected to have a single member.
     * 
     * @param client and instance of WBEMClient
     * @param ins the alias WBEMClient instance
     * @param addMember if true also retrieve the alias WWN member
     * @return an instance of {@link ZoneWwnAlias}
     * @throws WBEMException
     */
    private ZoneWwnAlias getAliasFromInstance(WBEMClient client, CIMInstance ins, boolean addMember) throws WBEMException {
        ZoneWwnAlias alias = new ZoneWwnAlias();
        alias.setName(cimStringProperty(ins, _CollectionAlias));
        alias.setCimObjectPath(ins.getObjectPath());
        if (addMember) {
            List<ZoneMember> members = getZoneOrAliasMembers(client, ins.getObjectPath(), true);
            for (ZoneMember member : members) {
                alias.setAddress(EndpointUtility.changeCase(member.getAddress()));
                alias.setCimMemberPath(member.getCimObjectPath());
                break; // there should only be 1 member
            }
        }
        return alias;
    }

    /**
     * Scans the topology view for a fabric to get any endpoints that are routed.
     * 
     * @param client and instance of WBEMClient
     * @param fabricId the fabric Id
     * @param fabricWwn the fabric WWN
     * @return a list of endpoints that are routed to the fabric
     * @throws WBEMException
     */
    public Set<String> getRoutedEndpoints(WBEMClient client, String fabricId, String fabricWwn)
            throws WBEMException {
        Set<String> routedConnections = new HashSet<String>();
        CIMInstance ins = getFabricInstance(client, fabricId, fabricWwn);
        CloseableIterator<CIMInstance> topit = null;
        try {
            topit = client.associatorInstances(
                    ins.getObjectPath(), _Brocade_TopologyViewInFabric,
                    _Brocade_TopologyView, null, null, false, null);
            while (topit.hasNext()) {
                CIMInstance topins = topit.next();
                if (_XlatePhantomPort.equals(cimStringProperty(topins,
                        _AntecedentFCPortElementName))) { // Looking specifically for routed connection
                    routedConnections.add(formatWWN(cimStringProperty(topins,
                            _DependentFCPortWWN)));
                }
            }
        } finally {
            if (topit != null) {
                topit.close();
            }
        }
        return routedConnections;
    }

    @SuppressWarnings("unchecked")
    public List<Zoneset> getZoneSets(WBEMClient client, String fabricId, String fabricWwn, String zoneName, boolean excludeMembers,
    		boolean excludeAliases) throws WBEMException {
        List<Zoneset> zonesets = new ArrayList<Zoneset>();

        fabricWwn = StringUtils.isEmpty(fabricWwn) ? getFabricWwn(client, fabricId) : fabricWwn;
        CIMInstance fabricIns = getFabricInstance(client, fabricId, fabricWwn);

        if (fabricIns != null) {
            CloseableIterator<CIMInstance> zonesetItr = null;
            try {
                CIMInstance activeZonesetIns = getActiveZonesetInstance(client, fabricId, fabricWwn);
                if (activeZonesetIns != null) {
                    Zoneset activeZoneset = new Zoneset(cimStringProperty(activeZonesetIns, _element_name));
                    activeZoneset.setActive(cimBooleanProperty(activeZonesetIns, _active));

                    // if zoneName not specified, get all zones in the zoneset.
                    if (StringUtils.isEmpty(zoneName)) {
                        activeZoneset.setZones(getZonesetZones(client, activeZonesetIns.getObjectPath(), !excludeMembers, !excludeAliases));
                    } else {
                        // looking for a zone with given zoneName
                        Zone zone = getZone(client, zoneName, fabricWwn, true, !excludeMembers, !excludeAliases);
                        if (zone != null) {
                            activeZoneset.getZones().add(zone);
                        }
                    }

                    // get pending active zoneset and consolidate with active zoneset
                    zonesetItr = client.associatorInstances(
                            fabricIns.getObjectPath(), _zoneset_fabric_path,
                            _zoneset_name, null, null, false, null);
                    while (zonesetItr.hasNext()) {
                        CIMInstance zonesetIns = zonesetItr.next();
                        Zoneset zoneset = new Zoneset(cimStringProperty(zonesetIns, _element_name));
                        zoneset.setActive(cimBooleanProperty(zonesetIns, _active));

                        if (!zoneset.getActive() && StringUtils.equals(zoneset.getName(), activeZoneset.getName())) {
                            // found a pending active zoneset, consolidate its zones into active zoneset
                            if (StringUtils.isEmpty(zoneName)) {
                                zoneset.setZones(getZonesetZones(client, zonesetIns.getObjectPath(), !excludeMembers, !excludeAliases));

                                // consolidate active and pending zones in the zoneset
                                List<String> activeZoneNames = new ArrayList<String>();
                                for (Zone zone : activeZoneset.getZones()) {
                                    activeZoneNames.add(zone.getName());
                                }

                                // get zones that are not yet active in zoneset, then
                                // append to zoneset
                                List<Zone> inactiveZones = new ArrayList<Zone>();
                                for (Zone zone : zoneset.getZones()) {
                                    if (!activeZoneNames.contains(zone.getName())) {
                                        inactiveZones.add(zone);
                                    }
                                }
                                activeZoneset.getZones().addAll(inactiveZones);

                            } else if (activeZoneset.getZones().isEmpty()) {
                                // if specified zone was not found in active zone set, look into pending zoneset
                                Zone zone = getZone(client, zoneName, fabricWwn, true, !excludeMembers, !excludeAliases);
                                if (zone != null) {
                                    zoneset.getZones().add(zone);
                                }
                            }
                            break;
                        }
                    }
                    if (activeZoneset != null) {
                        zonesets.add(activeZoneset);
                    }
                }

            } finally {
                if (zonesetItr != null) {
                    zonesetItr.close();
                }
            }
        }
        return zonesets;
    }

    @SuppressWarnings("unchecked")
    public CIMObjectPath getZoneSetPath(WBEMClient client, String fabricId, String fabricWwn, String name)
            throws WBEMException {
        CIMInstance fabricIns = getFabricInstance(client, fabricId, fabricWwn);
        if (fabricIns != null) {
            CloseableIterator<CIMInstance> zonesetItr = null;
            try {
                zonesetItr = client.associatorInstances(
                        fabricIns.getObjectPath(), _zoneset_fabric_path,
                        _zoneset_name, null, null, false, null);

                while (zonesetItr.hasNext()) {
                    CIMInstance zonesetIns = zonesetItr.next();
                    if (cimStringProperty(zonesetIns,
                            _element_name).equals(name)) {
                        return zonesetIns.getObjectPath();
                    }
                }
            } finally {
                if (zonesetItr != null) {
                    zonesetItr.close();
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public List<Zone> getZonesetZones(WBEMClient client,
            CIMObjectPath zonesetPath, boolean includeMembers, boolean includeAliases) throws WBEMException {
        List<Zone> zones = new ArrayList<Zone>();
        if (zonesetPath != null) {
            CloseableIterator<CIMInstance> zoneItr = null;
            try {
                zoneItr = client.associatorInstances(
                        zonesetPath, _Brocade_ZoneInZoneSet, _Brocade_Zone, null,
                        null, false, null);

                while (zoneItr.hasNext()) {
                    Zone zone = getZoneFromZoneInstance(client, zoneItr.next(), includeMembers, includeAliases);
                    zones.add(zone);
                }
            } finally {
                if (zoneItr != null) {
                    zoneItr.close();
                }
            }
        }
        return zones;
    }

    /**
     * Checks to see if the zoneset has a number of zones that is greater than the count parameter.
     * This function is used when deleting zones to check if the zoneset will become
     * empty.
     * 
     * @param client and instance of client
     * @param zonesetPath the cim path to the zoneset
     * @param removeCount the count of zones that will be removed
     * @return true if the zoneset will have remaining zones after the number of zones is removed.
     * @throws WBEMException
     */
    public boolean zonesetHasMore(WBEMClient client, CIMObjectPath zonesetPath, int removeCount) throws WBEMException {
        CloseableIterator<CIMObjectPath> zoneItr = null;
        int count = 0;
        try {
            zoneItr = client.associatorNames(
                    zonesetPath, _Brocade_ZoneInZoneSet, _Brocade_Zone, null, null);

            while (zoneItr.hasNext()) {
                if (zoneItr.next() != null) {
                    count++;
                }
                if (count > removeCount) {
                    return true;
                }
            }
        } finally {
            if (zoneItr != null) {
                zoneItr.close();
            }
        }

        return false;
    }

    /**
     * Checks to see if the zoneset has become empty.
     * 
     * @param client and instance of client
     * @param zonesetPath the cim path to the zoneset
     * @return true if the zoneset has 0 zones.
     * @throws WBEMException
     */
    public boolean isEmptyZoneset(WBEMClient client, CIMObjectPath zonesetPath) throws WBEMException {
        return !zonesetHasMore(client, zonesetPath, 0);
    }

    @SuppressWarnings("unchecked")
    public List<Zone> getFabricZones(WBEMClient client, String fabricId, String fabricWwn) throws WBEMException {
        List<Zone> zones = new ArrayList<Zone>();
        CIMInstance fabricIns = getFabricInstance(client, fabricId, fabricWwn);
        if (fabricIns != null) {
            CloseableIterator<CIMInstance> zoneItr = null;
            try {
                zoneItr = client.associatorInstances(
                        fabricIns.getObjectPath(), _Brocade_ZoneInFabric, _Brocade_Zone, null,
                        null, false, null);

                while (zoneItr.hasNext()) {
                    Zone zone = getZoneFromZoneInstance(client, zoneItr.next(), false, false);
                    zones.add(zone);
                }
            } finally {
                if (zoneItr != null) {
                    zoneItr.close();
                }
            }
        }
        return zones;
    }

    public void removeZone(WBEMClient client, Zone zone) throws WBEMException {
        client.deleteInstance((CIMObjectPath) zone.getCimObjectPath());
    }

    /**
     * Removes a WWN that is a member of an alias or a zone, or remove an alias that
     * is a member of a zone.
     * 
     * @param client and instance of WBEMClient with an open session to the network
     *            system.
     * @param member the CIM path to the zone or alias member
     * @param zoneOrAlias the entity whose membership is being modified which can be
     *            an alias or a zone. If it is a zone, this function can support adding a member
     *            of type WWN or alias. If it is an alias, this function will support WWN members only.
     * @param alias true if the member being added is of type alias, in which case the
     *            entity modified should be of a zone.
     * @throws WBEMException
     */
    public void removeZoneOrAliasMember(WBEMClient client, CIMObjectPath member,
            CIMObjectPath zoneOrAlias, boolean alias) throws WBEMException {
        CloseableIterator<CIMObjectPath> zoneItr = null;
        try {
            if (alias) {
                zoneItr = client.referenceNames(zoneOrAlias, _Brocade_ZoneAliasInZone, null);
                while (zoneItr.hasNext()) {
                    CIMObjectPath associationPath = zoneItr.next();
                    if (member.equals(associationPath.getKeyValue("Member"))) {
                        client.deleteInstance(associationPath);
                        _log.info("Zone member of type alias : " + associationPath + " was removed.");
                        break;
                    }
                }
            } else {
                if (zoneOrAlias.getObjectName().equals(_Brocade_Zone)) {
                    zoneItr = client.referenceNames(zoneOrAlias, _Brocade_ZoneMembershipSettingDataInZone, null);
                } else {  // zone alias
                    zoneItr = client.referenceNames(zoneOrAlias, _Brocade_ZoneMembershipSettingDataInZoneAlias, null);
                }
                while (zoneItr.hasNext()) {
                    CIMObjectPath associationPath = zoneItr.next();
                    if (member.equals(associationPath.getKeyValue("SettingData"))) {
                        client.deleteInstance(associationPath);
                        _log.info("Zone or alias member: " + associationPath + " was removed.");
                        break;
                    }
                }
            }
        } finally {
            if (zoneItr != null) {
                zoneItr.close();
            }
        }
    }

    /**
     * Get the zone aliases in the zone.
     * 
     * @param client instance of WBEMClient
     * @param path the zone path
     * @return the list of aliases by name and WWN (assuming only 1 member in an alias)
     */
    public List<ZoneMember> getZoneAliases(WBEMClient client,
            CIMObjectPath path) {
        List<ZoneMember> members = new ArrayList<ZoneMember>();
        CloseableIterator<CIMInstance> zoneItr = null;
        CIMInstance ins;
        try {
            String name = getPropertyValueFromInstanceId(path, SmisConstants.CP_NSNAME);
            String fabric = getPropertyValueFromInstanceId(path, SmisConstants.CP_FABRIC);
            // for some reason, zone aliases can be obtained from inactive zones only
            path = getZonePath(name, fabric, false);
            zoneItr = client.associatorInstances(
                    path, _Brocade_ZoneAliasInZone,
                    _Brocade_ZoneAlias, null, null, false,
                    null);
            while (zoneItr.hasNext()) {
                ins = zoneItr.next();
                members.addAll(getZoneOrAliasMembers(client, ins.getObjectPath(), true));
            }
        } catch (Exception ex) {
            // Do not fail because of problem getting aliases
            _log.info("Failed to get aliases for zone " + path.getObjectName()
                    + " with error: " + ex.getMessage());
        } finally {
            if (zoneItr != null) {
                zoneItr.close();
            }
        }
        return members;
    }

    /**
     * Get the zone aliases in the zone.
     * 
     * @param client instance of WBEMClient
     * @param path the zone path
     * @param includeAliases if true, the aliases are retrieved and populated in the zone.
     * @return the list of aliases by name and WWN (asuming only 1 member in an alias)
     */
    public List<ZoneMember> getZoneMembers(WBEMClient client,
            CIMObjectPath path, boolean includeAliases) throws WBEMException {
        List<ZoneMember> aliasMembers = null;
        List<ZoneMember> wwnMembers = getZoneOrAliasMembers(client, path, false);
        if (includeAliases) {
            aliasMembers = getZoneAliases(client, path);
    	} else {
            _log.info("Excluding aliases while getting zone members");
            aliasMembers = wwnMembers;
            return aliasMembers;
    	}
        boolean found = false;
        for (ZoneMember wwnMember : wwnMembers) {
            found = false;
            for (ZoneMember aliasMember : aliasMembers) {
                if (wwnMember.getAddress().equals(aliasMember.getAddress())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                aliasMembers.add(wwnMember);
            }
        }
        return aliasMembers;
    }

    /**
     * This function gets the member of a zone or an alias.
     * 
     * @param client
     * @param path the path to the zone or alias
     * @param alias true if the object is of type alias
     * @return an instance of zonemember that has the WWN of the
     *         zone or alias member
     * @throws WBEMException
     */
    @SuppressWarnings("unchecked")
    public List<ZoneMember> getZoneOrAliasMembers(WBEMClient client,
            CIMObjectPath path, boolean alias) throws WBEMException {
        List<ZoneMember> members = new ArrayList<ZoneMember>();
        if (path != null) {
            CloseableIterator<CIMInstance> zoneItr = null;
            try {
                String association = alias ? _Brocade_ZoneMembershipSettingDataInZoneAlias :
                        _Brocade_ZoneMembershipSettingDataInZone;
                zoneItr = client.associatorInstances(
                        path, association,
                        _Brocade_ZoneMembershipSettingData, null, null, false,
                        null);
                while (zoneItr.hasNext()) {
                    CIMInstance memberIns = zoneItr.next();
                    String address = cimStringProperty(memberIns,
                            _ConnectivityMemberID);
                    ConnectivityMemberType type = ConnectivityMemberType
                            .byValue(cimIntegerProperty(memberIns,
                                    _ConnectivityMemberType));
                    if (type == ConnectivityMemberType.WWPN ||          // temporarily allow portgroup because of brocade issues
                            type == ConnectivityMemberType.PORTGROUP) { // where type is port group when wwnn=wwpn
                        address = formatWWN(address);
                    }
                    ZoneMember zoneMember = new ZoneMember(address, type);
                    zoneMember.setCimObjectPath(memberIns.getObjectPath()); // This is the path of the association object
                    if (alias) {
                        zoneMember.setAlias(getPropertyValueFromInstanceId(path, SmisConstants.CP_NSNAME));
                        zoneMember.setAliasType(true);
                        zoneMember.setCimAliasPath(path); // this is the path of the alias object
                    }
                    members.add(zoneMember);
                }
            } finally {
                if (zoneItr != null) {
                    zoneItr.close();
                }
            }
        }
        return members;
    }

    @SuppressWarnings("unchecked")
    public CIMInstance getActiveZonesetInstance(WBEMClient client,
            String fabricId, String fabricWwn) throws WBEMException {
        CIMInstance fabricIns = getFabricInstance(client, fabricId, fabricWwn);
        if (fabricIns != null) {
            CloseableIterator<CIMInstance> zonesetItr = null;
            try {
                zonesetItr = client.associatorInstances(
                        fabricIns.getObjectPath(), _zoneset_fabric_path,
                        _zoneset_name, null, null, false, null);
                while (zonesetItr.hasNext()) {
                    CIMInstance zonesetIns = zonesetItr.next();
                    if (cimBooleanProperty(zonesetIns, _active)) {
                        return zonesetIns;
                    }
                }
            } finally {
                if (zonesetItr != null) {
                    zonesetItr.close();
                }
            }
        }
        return null;
    }

    /**
     * Get all zonesets in a given fabric, if zoneset name is NOT specified. If
     * zoneset name is specified, only get that particular zoneset of given name
     * 
     * @param client
     * @param fabricId
     * @param fabricWwn
     * @param zonesetName
     * @return
     * @throws WBEMException
     */
    @SuppressWarnings({ "unchecked" })
    private List<CIMInstance> getZoneSetsForFabric(WBEMClient client,
            String fabricId, String fabricWwn, String zonesetName) throws WBEMException {
        List<CIMInstance> zonesets = new ArrayList<CIMInstance>();
        CIMInstance fabricIns = getFabricInstance(client, fabricId, fabricWwn);
        if (fabricIns != null) {
            CloseableIterator<CIMInstance> zonesetItr = null;
            try {
                zonesetItr = client.associatorInstances(
                        fabricIns.getObjectPath(), _zoneset_fabric_path,
                        _zoneset_name, null, null, false, null);
                while (zonesetItr.hasNext()) {
                    CIMInstance zonesetInstance = zonesetItr.next();
                    if (StringUtils.isEmpty(zonesetName)) {
                        zonesets.add(zonesetInstance);
                    } else {
                        String instName = cimStringProperty(zonesetInstance, _element_name);
                        if (zonesetName.equals(instName)) {
                            zonesets.add(zonesetInstance);
                            break;
                        }
                    }
                }
            } finally {
                if (zonesetItr != null) {
                    zonesetItr.close();
                }
            }
        }
        return zonesets;
    }

    /**
     * Get the zoneset instance of given zonesetName.
     * 
     * @param client
     * @param fabricId
     * @param fabricWwn
     * @param zonesetName
     * @return
     * @throws WBEMException
     */
    public CIMInstance getZoneset(WBEMClient client, String fabricId, String fabricWwn, String zonesetName) throws WBEMException {
        CIMInstance zonesetIns = null;
        List<CIMInstance> zonesets = getZoneSetsForFabric(client, fabricId, fabricWwn, zonesetName);
        if (!zonesets.isEmpty()) {
            zonesetIns = zonesets.get(0);
        }
        return zonesetIns;
    }

    /**
     * Returns a fabric instance. If the fabricWwn is supplied, we use that if possible, otherwise
     * we fall back to matching on the fabricId.
     * 
     * @param client
     * @param fabricId
     * @param fabricWwn
     * @return
     * @throws javax.wbem.WBEMException
     */
    private CIMInstance getFabricInstance(WBEMClient client, String fabricId, String fabricWwn)
            throws WBEMException {
        if (fabricWwn == null) {
            fabricWwn = "";      // avoids null check below
        } else {
            fabricWwn = fabricWwn.replaceAll(":", "");
        }
        CIMInstance nameMatchInstance = null;
        int numberOfNameMatches = 0;
        CIMObjectPath path = CimObjectPathCreator.createInstance(_fabric_path, _namespace);
        CloseableIterator<CIMInstance> fabricIter = null;
        try {
            fabricIter = client.enumerateInstances(
                    path, false, true, true, null);
            while (fabricIter.hasNext()) {
                CIMInstance fabricIns = fabricIter.next();
                // Match on WWN is preferred
                if (fabricWwn.equalsIgnoreCase(cimStringProperty(fabricIns, _name))) {
                    return fabricIns;
                }
                // Match on elementName may be used, but keep searching
                if (fabricId.equals(cimStringProperty(fabricIns, _element_name))) {
                    nameMatchInstance = fabricIns;
                    numberOfNameMatches++;
                }
            }
        } finally {
            if (fabricIter != null) {
                fabricIter.close();
            }
        }
        if (numberOfNameMatches > 1) {
            throw new WBEMException("Duplicate matches on fabricId: " + fabricId);
        } else if (numberOfNameMatches == 0) {
            // log warn that fabric id is not found in a network system
            _log.warn(NetworkDeviceControllerException.exceptions.fabricNotFoundInNetwork(fabricId, "").getMessage());
        }

        return nameMatchInstance;
    }

    @SuppressWarnings("unchecked")
    public CIMInstance getZoneServiceInstance(WBEMClient client, String fabricId, String fabricWwn)
            throws WBEMException {
        CIMInstance fabricIns = getFabricInstance(client, fabricId, fabricWwn);

        if (fabricIns != null) {
            CloseableIterator<CIMInstance> zoneServices = null;
            try {
                zoneServices = client.associatorInstances(
                        fabricIns.getObjectPath(), _Brocade_ZoneServiceInFabric,
                        _Brocade_ZoneService, null, null, false, null);
                // there is only one instance
                return zoneServices == null ? null : zoneServices.next();
            } finally {
                if (zoneServices != null) {
                    zoneServices.close();
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public boolean activateZoneSet(WBEMClient client,
            CIMInstance zoneServiceIns, CIMObjectPath zonesetObjectPath, boolean activate)
            throws WBEMException {
        CIMArgument[] inargs = new CIMArgument[2];
        inargs[0] = _cimArgumentFactory.reference(_ZoneSet, zonesetObjectPath);
        inargs[1] = _cimArgumentFactory.bool(_Activate, activate);
        CIMArgument[] outargs = new CIMArgument[1];
        UnsignedInteger32 result = (UnsignedInteger32) client.invokeMethod(
                zoneServiceIns.getObjectPath(), _ActivateZoneSet, inargs,
                outargs);
        return result.intValue() == 0;
    }

    public CIMObjectPath getShadowZonesetPath(WBEMClient client, String fabricId, String fabricWwn,
            CIMInstance zonesetIns) throws WBEMException {
        if (zonesetIns != null) {
            List<CIMInstance> zonesets = getZoneSetsForFabric(client, fabricId, fabricWwn, null);
            for (CIMInstance shadowZonesetIns : zonesets) {
                if (!cimBooleanProperty(shadowZonesetIns, _active)
                        && cimStringProperty(shadowZonesetIns,
                                _element_name)
                                .equals(cimStringProperty(zonesetIns,
                                        _element_name))) {
                    return shadowZonesetIns.getObjectPath();
                }
            }
        }
        return null;
    }

    @SuppressWarnings("rawtypes")
    public CIMObjectPath createZoneSet(WBEMClient client,
            CIMInstance zoneServiceIns, String zoneSetName)
            throws WBEMException {
        CIMArgument[] inargs = new CIMArgument[1];
        inargs[0] = _cimArgumentFactory.string(
                _ZoneSetName, zoneSetName);
        CIMArgument[] outargs = new CIMArgument[1];
        UnsignedInteger32 result = (UnsignedInteger32) client.invokeMethod(
                zoneServiceIns.getObjectPath(), _CreateZoneSet, inargs,
                outargs);
        if (result.intValue() == 0 && outargs.length > 0) {
            return (CIMObjectPath) outargs[0].getValue();
        }
        return null;
    }

    @SuppressWarnings("rawtypes")
    public CIMObjectPath addZone(WBEMClient client, CIMInstance zoneServiceIns,
            CIMObjectPath zonesetPath, String zoneName, String fabricId, String fabricWwn) throws WBEMException {
        CIMObjectPath zonePath = null;
        CIMArgument[] outargs = new CIMArgument[1];
        CIMArgument[] inargs = new CIMArgument[3];
        inargs[0] = _cimArgumentFactory.string(_ZoneName, zoneName);
        inargs[1] = _cimArgumentFactory.uint16(_ZoneType, 2);
        inargs[2] = _cimArgumentFactory.uint16(_ZoneSubType, 1);
        _log.info("Creating zone: " + zoneName);

        UnsignedInteger32 result = new UnsignedInteger32(Integer.MAX_VALUE);
        int count = 0;
        while (result.intValue() != 0 && count < 2) {
            result = (UnsignedInteger32) client.invokeMethod(
                    zoneServiceIns.getObjectPath(), _CreateZone, inargs, outargs);
            if (result.intValue() == 0 && outargs[0].getValue() != null) {
                zonePath = (CIMObjectPath) outargs[0].getValue();
                _log.info("Created zone: " + zoneName + " with path " + zonePath);
                break;
            } else if (result.intValue() == 32770) {
                _log.info("Created zone: " + zoneName + " failed with result.intvalue() 32770: " +
                        "Transaction Not Started. Retry to get a new session lock.");
                endSession(client, zoneServiceIns, false);
                zoneServiceIns = startSession(client, fabricId, fabricWwn);
                count++;
            } else {
                _log.info("Created zone failed: " + zoneName + " with result.intValue() "
                        + result.intValue());
                break;
            }
        }
        if (zonePath == null) {
            if (count >= 2) {
                _log.info("Failed to create zone " + zoneName + ". The maximum number of retries ("
                        + (count + 1) + ") was reached without successfully starting a transaction.");
            }
        } else {
            // add zone to zoneset
            outargs = new CIMArgument[1];
            inargs = new CIMArgument[2];
            inargs[0] = _cimArgumentFactory.reference(_ZoneSet, zonesetPath);
            inargs[1] = _cimArgumentFactory.reference(_Zone, zonePath);
            _log.info("Adding zone: " + zoneName + " to zoneset " + zonesetPath);
            result = (UnsignedInteger32) client.invokeMethod(
                    zoneServiceIns.getObjectPath(), "AddZone", inargs, outargs);
            if (result.intValue() != 0) {
                _log.info("Failed to add zone: " + zoneName + " to zoneset " + zonesetPath);
                _log.info("result.intValue(): " + result.intValue());
                zonePath = null;
            }
        }
        return zonePath;
    }

    /**
     * Create an instance of an alias object on the switch. The alias object can have
     * a single member that has to be of type WWN.
     * <p>
     * This function will retry if the alias creation fails because the session is lost or timed out. The total number of tries is set to 2.
     * 
     * @param client an instance of WBEMClient with an open session to SMI provider
     * @param zoneServiceIns an instance of zone service for the fabric
     * @param fabricId the fabric name
     * @param fabricWwn the fabric WWN
     * @param alias the object containing the attributes of the alias to be created
     * @throws WBEMException
     */
    @SuppressWarnings("rawtypes")
    public CIMObjectPath addZoneAlias(WBEMClient client, CIMInstance zoneServiceIns,
            String fabricId, String fabricWwn, ZoneWwnAlias alias) throws WBEMException {
        CIMObjectPath aliasPath = null;
        CIMArgument[] outargs = new CIMArgument[1];
        CIMArgument[] inargs = new CIMArgument[1];
        inargs[0] = _cimArgumentFactory.string(_CollectionAlias, alias.getName());
        _log.info("Creating alias: " + alias.getName());

        UnsignedInteger32 result = new UnsignedInteger32(Integer.MAX_VALUE);
        int count = 0;
        while (result.intValue() != 0 && count < 2) {
            result = (UnsignedInteger32) client.invokeMethod(
                    zoneServiceIns.getObjectPath(), _CreateZoneAlias, inargs, outargs);
            if (result.intValue() == 0 && outargs[0].getValue() != null) {
                aliasPath = (CIMObjectPath) outargs[0].getValue();
                _log.info("Created alias: " + alias.getName() + " with path " + aliasPath);
                break;
            } else if (result.intValue() == 32770) { // session timed out or was stolen
                _log.info("Created alias: " + alias.getName() + " failed with result.intvalue() 32770: " +
                        "Transaction Not Started. Retry to get a new session lock.");
                endSession(client, zoneServiceIns, false);
                zoneServiceIns = startSession(client, fabricId, fabricWwn);
                count++;
            } else {
                throw new NetworkDeviceControllerException("Created alias failed: " + alias.getName() + " with result.intValue() "
                        + result.intValue());
            }
        }
        if (aliasPath == null) {
            if (count >= 2) {
                _log.info("Failed to create alias " + alias.getName() + ". The maximum number of retries ("
                        + (count + 1) + ") was reached without successfully starting a transaction.");
            }
        } else {
            // add WWN as alias member to alias
            addZoneOrAliasMember(client, zoneServiceIns, fabricWwn, aliasPath, alias.getAddress());
        }
        return aliasPath;
    }

    /**
     * Add a member to a zone or alias. The member can be a WWN or an alias
     * name when the entity modified is a zone. The member can only be a WWN when
     * the entity modified is an alias.
     * 
     * @param client an instance of WBEMClient with an open session to SMI provider
     * @param zoneServiceIns an instance of zone service for the fabric
     * @param fabricWwn the fabric WWN
     * @param zonePath the CIM path of the zone object
     * @param member to be added to the zone or alias.
     * @return the CIM path to the newly created alias
     * @throws WBEMException
     */
    @SuppressWarnings("rawtypes")
    public boolean addZoneOrAliasMember(WBEMClient client,
            CIMInstance zoneServiceIns, String fabricWwn, CIMObjectPath zonePath, String member)
            throws WBEMException {
        CIMArgument[] outargs = new CIMArgument[1];
        CIMArgument[] inargs = null;
        UnsignedInteger32 result = null;
        if (EndpointUtility.isValidEndpoint(member, EndpointType.WWN)) {
            _log.info("Add zone or alias member of type wwn " + member);
            inargs = new CIMArgument[3];
            inargs[0] = _cimArgumentFactory.uint16(_ConnectivityMemberType, 2);
            inargs[1] = _cimArgumentFactory.string(_ConnectivityMemberID, member.replaceAll(":", ""));
            inargs[2] = _cimArgumentFactory.reference(_SystemSpecificCollection,
                    zonePath);
            result = (UnsignedInteger32) client.invokeMethod(
                    zoneServiceIns.getObjectPath(),
                    _CreateZoneMembershipSettingData, inargs, outargs);
        } else {
            _log.info("Add zoneor alias  member of type alias " + member);
            inargs = new CIMArgument[2];
            inargs[0] = _cimArgumentFactory.reference(_Zone, zonePath);
            CIMObjectPath aliasPath = getZoneAliasPath(member, fabricWwn);
            inargs[1] = _cimArgumentFactory.reference(_ZoneAlias, aliasPath);
            result = (UnsignedInteger32) client.invokeMethod(
                    zoneServiceIns.getObjectPath(),
                    _AddZoneAlias, inargs, outargs);
        }
        _log.info("Add zone or alias member returned code " + result.toString());
        // 0 means success and 8 means member already exists
        return result.intValue() == 0 || result.intValue() == 8;
    }

    /**
     * Given an initiator WWN, find all the zones this initiator is in.
     * 
     * @param client an instance of WBEMClient with an open session to SMI provider
     * @param fabricWwn the WWN of the fabric
     * @param endpointWwn the WWN of the initiator
     * @param cachedZones A cache of zones already retrieved from the network system.
     * @return a list of zones in the initiator is in any zones, otherwise, an empty list
     * @throws WBEMException
     */
    public List<Zone> getEndpointZones(WBEMClient client,
            String fabricWwn, String endpointWwn, Map<CIMObjectPath, Zone> cachedZones) throws WBEMException {
        List<Zone> zones = new ArrayList<Zone>();
        CIMInstance memberIns = getZoneMemberInstance(client, endpointWwn, _ZMType_Wwn, fabricWwn, true);
        // if we find an instance, this means the initiator is in some zones
        if (memberIns != null) {
            // get the zones
            CloseableIterator<CIMInstance> zoneItr = null;
            Zone zone = null;
            try {
                zoneItr = client.associatorInstances(
                        memberIns.getObjectPath(), _Brocade_ZoneMembershipSettingDataInZone,
                        _Brocade_Zone, null, null, false, null);
                while (zoneItr.hasNext()) {
                    CIMInstance zoneIns = zoneItr.next();
                    zone = cachedZones.get(zoneIns.getObjectPath());
                    if (zone == null) {
                        zone = getZoneFromZoneInstance(client, zoneIns, true, false);
                        cachedZones.put(zoneIns.getObjectPath(), zone);
                    }
                    zones.add(zone);
                    _log.info("Found zone " + zone.getName());
                }
            } catch (WBEMException ex) {
                _log.error("Encountered an exception while trying to get zones for initiator."
                        + ex.getMessage(), ex);
                throw ex;
            } finally {
                if (zoneItr != null) {
                    zoneItr.close();
                }
            }

        }
        return zones;
    }

    @SuppressWarnings("rawtypes")
    public CIMInstance startSession(WBEMClient client, String fabricId, String fabricWwn)
            throws NetworkControllerSessionLockedException, WBEMException {
        UnsignedInteger32 result = null;
        CIMInstance zoneService;
        try {
            zoneService = getZoneServiceInstance(client, fabricId, fabricWwn);
            int sessionState = cimIntegerProperty(zoneService, _SessionState);
            int requestedSessionState = cimIntegerProperty(zoneService,
                    _requestedSessionState);
            _log.debug("Entering startSession: " + zoneService.toString());
            if (sessionState == _notApplicable) {
                // no session control implemented by this agent
                return zoneService;
            }
            if (sessionState != _ended || requestedSessionState != _noChange) {
                _log.info("Zone session is locked by another user or another call. We will try again.");
                zoneService = waitForSession(client, fabricId, fabricWwn);
            }
            CIMArgument[] inargs = new CIMArgument[] { _cimArgumentFactory
                    .uint16(_requestedSessionState, _started) };
            result = (UnsignedInteger32) client.invokeMethod(
                    zoneService.getObjectPath(), _SessionControl, inargs,
                    new CIMArgument[1]);
            _log.info("Start session returned code: " + result.intValue());
            if (result.intValue() == 0 || result.intValue() == 32772) {
                zoneService = getZoneServiceInstance(client, fabricId, fabricWwn);
                _log.debug("Leaving startSession: " + zoneService.toString());
                return zoneService;
            }
            return null;
        } catch (WBEMException ex) {
            _log.error("Encountered an exception while trying to start a zone session."
                    + ex.getMessage(), ex);
            throw ex;
        }
    }

    @SuppressWarnings("rawtypes")
    public boolean endSession(WBEMClient client, CIMInstance zoneService,
            boolean commit) throws WBEMException {
        UnsignedInteger32 result = null;
        try {
            int sessionState = cimIntegerProperty(zoneService, _SessionState);
            if (sessionState == _notApplicable) {
                // no session control implemented by this agent
                return true;
            }
            int endMode = commit ? _ended : _terminated;
            CIMArgument[] inargs = new CIMArgument[] { _cimArgumentFactory
                    .uint16(_requestedSessionState, endMode) };
            result = (UnsignedInteger32) client.invokeMethod(
                    zoneService.getObjectPath(), _SessionControl, inargs,
                    new CIMArgument[1]);
            _log.info("End session returned code: " + result.intValue());
            return result.intValue() == 0;
        } catch (WBEMException ex) {
            _log.error("Encountered an exception while trying to end a zone session."
                    + ex.getMessage(), ex);
            throw ex;
        }
    }

    private CIMInstance waitForSession(WBEMClient client, String fabricId, String fabricWwn)
            throws NetworkControllerSessionLockedException, WBEMException {
        CIMInstance zoneService = null;
        int session;
        int reqSession;
        long startTime = System.currentTimeMillis();
        do {
            try {
                Thread.sleep(defaultTimeout / 5);
            } catch (InterruptedException ex) {
                _log.warn(ex.getLocalizedMessage());
            }
            zoneService = getZoneServiceInstance(client, fabricId, fabricWwn);
            session = cimIntegerProperty(zoneService, _SessionState);
            reqSession = cimIntegerProperty(zoneService, _requestedSessionState);
            _log.info("In waitForSession: session is " + session + " and requested session is " + reqSession);
            if (session == _ended && reqSession == _noChange) {
                _log.info("Zone session lock released. Trying to start session after waiting " + (System.currentTimeMillis() - startTime));
                return zoneService;
            }
        } while (System.currentTimeMillis() - startTime < defaultTimeout);
        throw NetworkDeviceControllerException.exceptions.fabricSessionLocked();
    }

    /**
     * Returns the system uptime.
     * 
     * @param client - WBEMClient
     * @return system uptime
     * @throws WBEMException
     */
    public String getUptime(WBEMClient client) throws WBEMException {
        String startTime = null;
        CIMObjectPath path = CimObjectPathCreator.createInstance(_agent_name, _namespace);
        CloseableIterator<CIMInstance> it = null;
        try {
            it = client.enumerateInstances(path,
                    false, true, true, null);
            while (it.hasNext()) {
                CIMInstance ins = it.next();
                startTime = cimStringProperty(ins, _StartTime);
            }
        } finally {
            if (it != null) {
                it.close();
            }
        }
        return startTime == null ? null : getUptimeFormatted(startTime);
    }

    /**
     * Returns the system software version.
     * 
     * @param client - WBEMClient
     * @return system software version
     * @throws WBEMException
     */
    public String getVersion(WBEMClient client) throws WBEMException {
        String version = null;
        CIMObjectPath path = CimObjectPathCreator.createInstance(_agent_name, _namespace);
        CloseableIterator<CIMInstance> it = null;
        try {
            it = client.enumerateInstances(path,
                    false, true, true, null);
            while (it.hasNext()) {
                CIMInstance ins = it.next();
                version = cimStringProperty(ins, _ManagementServerVersion);
            }
        } finally {
            if (it != null) {
                it.close();
            }
        }
        return version;
    }

    /**
     * Return uptime with days, hours, minutes, and seconds
     * 
     * @param date - format of "Jan 1, 2013 12:00:00 AM EST" or "Jan 1, 2013 12:00:00 AM America/New_York"
     * @return uptime
     */
    private String getUptimeFormatted(String date) {
        try {
            DateTime dateTime = parseDateString(date);
            Duration duration = new Duration(dateTime, DateTime.now());
            return getUptimeFormatted(duration);
        } catch (Exception e) {
            _log.error(e.getMessage(), e);
        }
        return null;
    }

    /**
     * Returns uptime with days, hours, minutes, and seconds
     * 
     * @param duration - Duration
     * @return uptime
     */
    private String getUptimeFormatted(Duration duration) {
        int days, hours, minutes, seconds;
        days = duration.toStandardDays().getDays();
        duration = duration.minus(duration.toStandardDays().toStandardDuration());
        hours = duration.toStandardHours().getHours();
        duration = duration.minus(duration.toStandardHours().toStandardDuration());
        minutes = duration.toStandardMinutes().getMinutes();
        duration = duration.minus(duration.toStandardMinutes().toStandardDuration());
        seconds = duration.toStandardSeconds().getSeconds();
        return String.format("%d days, %d hours, %d minutes, %d seconds", days, hours, minutes, seconds);
    }

    /**
     * Returns DateTime object of parsed dateStamp parameter
     * 
     * @param dateStamp - format of "Jan 1, 2013 12:00:00 AM EST" or "Jan 1, 2013 12:00:00 AM America/New_York"
     * @return DateTime of parsed dateStamp parameter
     * @throws Exception if dateStamp can't be parsed
     */
    private DateTime parseDateString(String dateStamp) throws Exception {
        /* matches datestamps of the form "Jan 31, 2013 4:45:00 PM EST" and "Jan 31, 2013 4:45:00 PM America/New_York */
        final String[] regex = { "([A-Za-z]{3} [0-9]{1,2}, [0-9]{4} [0-9]{1,2}:[0-9]{2}:[0-9]{2} [A-Z]{2}) (.+)" };
        final String datePattern = "MMM dd, YYYY hh:mm:ss aa";
        String[] groups = new String[2];
        int index = SSHDialog.match(dateStamp, regex, groups);

        if (index != 0) {
            throw new Exception("Unable to parse date and timezone: " + dateStamp);
        }

        String dateTime = groups[0];
        String timeZone = groups[1];

        return DateTime.parse(dateTime, DateTimeFormat.forPattern(datePattern)
                .withZone(DateTimeZone.forTimeZone(TimeZone.getTimeZone(timeZone))));
    }

    /**
     * Create a zone alias path using the alias name and its fabric WWN. An example
     * of path instance is
     * InstanceID = "NAME=lglap026_2;ACTIVE=false;FABRIC=10000027F86AE4E7;CLASSNAME=Brocade_ZoneAlias"
     * 
     * @param aliasName the alias name
     * @param fabricWwn the alias fabric
     * @return an instance of CIMObjectPath that can be used to find the zone alias.
     */
    public CIMObjectPath getZoneAliasPath(String aliasName, String fabricWwn) {
        CIMProperty[] groupKeys = { _cimProperty.string(
                SmisConstants.CP_INSTANCE_ID,
                SmisConstants.CP_NSNAME
                        + SmisConstants.PATH_VAL_SEP
                        + aliasName
                        + SmisConstants.PATH_PROP_SEP
                        + SmisConstants.CP_ACTIVE
                        + SmisConstants.PATH_VAL_SEP
                        + "false"
                        + SmisConstants.PATH_PROP_SEP
                        + SmisConstants.CP_FABRIC
                        + SmisConstants.PATH_VAL_SEP
                        + fabricWwn.replaceAll(":", "")
                        + SmisConstants.PATH_PROP_SEP
                        + SmisConstants.CP_CLASSNAME
                        + SmisConstants.PATH_VAL_SEP
                        + _Brocade_ZoneAlias)
        };
        return CimObjectPathCreator.createInstance(_Brocade_ZoneAlias,
                _namespace, groupKeys);
    }

    /**
     * Creates a zone path using the zone name and the WWN of its fabrics. An example
     * of path instance id is
     * InstanceID = "NAME=z_lglap026_VMAXe_424_2;ACTIVE=false;FABRIC=10000027F86AE4E7;CLASSNAME=Brocade_Zone";
     * 
     * @param zoneName the name of the zone
     * @param fabricWwn the WWN of the zone's fabric
     * @param active whether the active or inactive zone sought
     * @return an instance of CIMObjectPath that can be used to find the zone.
     */
    public CIMObjectPath getZonePath(String zoneName, String fabricWwn,
            Boolean active) {
        if (fabricWwn == null)
        {
            fabricWwn = "";      // avoids null check below
        }

        CIMProperty[] groupKeys = { _cimProperty.string(
                SmisConstants.CP_INSTANCE_ID,
                SmisConstants.CP_NSNAME
                        + SmisConstants.PATH_VAL_SEP
                        + zoneName
                        + SmisConstants.PATH_PROP_SEP
                        + SmisConstants.CP_ACTIVE
                        + SmisConstants.PATH_VAL_SEP
                        + active.toString()
                        + SmisConstants.PATH_PROP_SEP
                        + SmisConstants.CP_FABRIC
                        + SmisConstants.PATH_VAL_SEP
                        + fabricWwn.replaceAll(":", "")
                        + SmisConstants.PATH_PROP_SEP
                        + SmisConstants.CP_CLASSNAME
                        + SmisConstants.PATH_VAL_SEP
                        + _Brocade_Zone)
        };
        return CimObjectPathCreator.createInstance(_Brocade_Zone, _namespace,
                groupKeys);
    }

    /**
     * Creates a zone member path using the initiator WWN and the WWN of its fabrics. An example
     * of path instance id is
     * InstanceID = "NAME=50060169472025F6;ZMTYPE=5;ACTIVE=false;FABRIC=10000027F86AE4E7;CLASSNAME=Brocade_ZoneMembershipSettingData";
     * 
     * @param memberWwn the initiator WWN
     * @param zmType the zone member type, for now expecting only 5 which is Wwn
     * @param fabricWwn the WWN of the zone's fabric
     * @param active whether the active or inactive member is sought
     * @return an instance of CIMObjectPath that can be used to find the zone.
     */
    public CIMObjectPath getZoneMemberPath(String memberWwn, String zmType, String fabricWwn,
            Boolean active) {
        if (fabricWwn == null) {
            fabricWwn = "";
        }

        CIMProperty[] groupKeys = { _cimProperty.string(
                SmisConstants.CP_INSTANCE_ID,
                SmisConstants.CP_NSNAME
                        + SmisConstants.PATH_VAL_SEP
                        + memberWwn.replaceAll(":", "")
                        + SmisConstants.PATH_PROP_SEP
                        + SmisConstants.CP_ZMTYPE
                        + SmisConstants.PATH_VAL_SEP
                        + zmType
                        + SmisConstants.PATH_PROP_SEP
                        + SmisConstants.CP_ACTIVE
                        + SmisConstants.PATH_VAL_SEP
                        + active.toString()
                        + SmisConstants.PATH_PROP_SEP
                        + SmisConstants.CP_FABRIC
                        + SmisConstants.PATH_VAL_SEP
                        + fabricWwn.replaceAll(":", "")
                        + SmisConstants.PATH_PROP_SEP
                        + SmisConstants.CP_CLASSNAME
                        + SmisConstants.PATH_VAL_SEP
                        + _Brocade_ZoneMembershipSettingData)
        };
        return CimObjectPathCreator.createInstance(_Brocade_ZoneMembershipSettingData, _namespace,
                groupKeys);
    }

    /**
     * Gets the zone instance using its name and the WWN of its fabric. It constructs a path
     * object which is used to find the zone instance.
     * 
     * @param client an instance of WBEMClient
     * @param zoneName the name of the zone
     * @param fabricWwn the WWN of the zone's fabric
     * @param active whether the active or inactive zone sought
     * @return an instance of CIMInstance for the zone.
     */
    public CIMInstance getZoneInstance(WBEMClient client, String zoneName, String fabricWwn, Boolean active) {
        CIMObjectPath path = getZonePath(zoneName, fabricWwn, active);
        try {
            return client.getInstance(path, false, false, null);
        } catch (WBEMException ex) {
            // we can get an exception if the zone does not exists.
            _log.info("Did not find zone " + zoneName + " in fabric " + fabricWwn
                    + ". Provider message: " + ex.getMessage());
        }
        return null;
    }

    /**
     * Gets the zone instance using its name and the WWN of its fabric. It constructs a path
     * object which is used to find the zone instance.
     * 
     * @param client an instance of WBEMClient
     * @param memberWwn the WWN of the zone member
     * @param zmType the zone membership type, for now always 5
     * @param fabricWwn the WWN of the zone's fabric
     * @param active whether the active or inactive zone member is sought
     * @return an instance of CIMInstance for the zone member.
     */
    public CIMInstance getZoneMemberInstance(WBEMClient client, String memberWwn, String zmType, String fabricWwn,
            Boolean active) throws WBEMException {
        CIMObjectPath path = getZoneMemberPath(memberWwn, zmType, fabricWwn, active);
        try {
            return client.getInstance(path, false, false, null);
        } catch (WBEMException ex) {
            if (ex.getID() == WBEMException.CIM_ERR_NOT_FOUND) {
                // we can get an exception if the WWN is not in a zone.
                _log.info("Did not find zone member for WWN " + memberWwn + " in fabric " + fabricWwn
                        + ". Provider message: " + ex.getMessage());
            } else {
                throw ex;
            }
        }
        return null;
    }

    /**
     * Gets the zones instances using name and the WWN of the fabric. It constructs a path
     * object which is used to find each zone instance and then creates an instance of Zone.
     * 
     * @param client an instance of WBEMClient
     * @param names the names of the zones
     * @param fabricWwn the WWN of the zone's fabric
     * @param active whether the active or inactive zone sought
     * @param includeMembers if the zone members should be retrieved or just the zone.
     * @param includeAliases if true, the aliases are retrieved and populated in the zone.
     * @return A map of zone name to zone instance.
     * @throws WBEMException
     */
    public Map<String, Zone> getZones(WBEMClient client, List<String> names, String fabricWwn,
            Boolean active, boolean includeMembers, boolean includeAliases) throws WBEMException {
        Map<String, Zone> zones = new HashMap<String, Zone>();
        if (names != null) {
            for (String name : names) {
                zones.put(name, getZone(client, name, fabricWwn, active, includeMembers, includeAliases));
            }
        }
        return zones;
    }

    /**
     * Gets the zone instance using its name and the WWN of its fabric. It constructs a path
     * object which is used to find the zone instance and then creates in instane Zone.
     * 
     * @param client an instance of WBEMClient
     * @param zoneName the name of the zone
     * @param fabricWwn the WWN of the zone's fabric
     * @param active whether the active or inactive zone sought
     * @param includeMembers if the zone members should be retrieved or just the zone.
     * @param includeAliases if true, the aliases are retrieved and populated in the zone.
     * @return and instance of {@link #_Zone}. Null if the some was not found in the switch.
     * @throws WBEMException
     */
    public Zone getZone(WBEMClient client, String zoneName, String fabricWwn,
            Boolean active, boolean includeMembers, boolean includeAliases) throws WBEMException {
        CIMInstance zoneIns = getZoneInstance(client, zoneName, fabricWwn,
                active);
        if (zoneIns != null) {
            return getZoneFromZoneInstance(client, zoneIns, includeMembers, includeAliases);
        }
        return null;
    }

    /**
     * Creates an instance of {@link #_Zone}
     * 
     * @param client an instance of WBEMClient
     * @param zoneIns an instance of CIMInstance
     * @param includeMembers if true, the members are retrieved and populated in the zone.
     * @param includeAliases if true, the aliases are retrieved and populated in the zone.
     * @return and instance of {@link #_Zone}.
     * @throws WBEMException
     */
    private Zone getZoneFromZoneInstance(WBEMClient client,
            CIMInstance zoneIns, boolean includeMembers, boolean includeAliases) throws WBEMException {
        Zone zone = new Zone(cimStringProperty(zoneIns, _element_name));
        zone.setActive(cimBooleanProperty(zoneIns, _active));
        zone.setCimObjectPath(zoneIns.getObjectPath());
        if (includeMembers) {
            zone.setMembers(getZoneMembers(client,
                    zoneIns.getObjectPath(), includeAliases));
        }
        return zone;
    }

    /**
     * Gets the zone alias instance using its name and the WWN of its fabric. It constructs a path
     * object which is used to find the alias instance and then creates an instance of {@link ZoneWwnAlias} .
     * 
     * @param client an instance of WBEMClient
     * @param aliasName the name of the zone
     * @param fabricWwn the WWN of the zone's fabric
     * @param addMember TODO
     * @return an instance of {@link ZoneWwnAlias}. Null if the name was not found in the switch.
     * @throws WBEMException
     */
    public ZoneWwnAlias getAlias(WBEMClient client, String aliasName, String fabricWwn, boolean addMember) throws WBEMException {
        CIMObjectPath path = getZoneAliasPath(aliasName, fabricWwn);
        try {
            CIMInstance aliasIns = client.getInstance(path, false, false, null);
            if (aliasIns != null) {
                return getAliasFromInstance(client, aliasIns, true);
            }
        } catch (WBEMException ex) {
            // alias not found - Just ignore
        }
        return null;
    }

    /**
     * A utility function to get a specific property from a path instance id. An example
     * of path instance id is
     * InstanceID = "NAME=z_lglap026_VMAXe_424_2;ACTIVE=false;FABRIC=10000027F86AE4E7;CLASSNAME=Brocade_Zone";
     * 
     * @param path an instance of CIMObjectPath
     * @param property the name of the property for example "NAME" or "CLASSNAME"
     * @return the string value of the property of found, otherwise an empty string.
     */
    private String getPropertyValueFromInstanceId(CIMObjectPath path,
            String property) {
        String[] props = path.getKeyValue(SmisConstants.CP_INSTANCE_ID)
                .toString().split(SmisConstants.PATH_PROP_SEP);
        for (String prop : props) {
            if (prop.startsWith(property)
                    && prop.indexOf(SmisConstants.PATH_VAL_SEP) == property
                            .length()) {
                return prop.substring(property.length()
                        + SmisConstants.PATH_VAL_SEP.length());
            }
        }
        return "";
    }

    /**
     * Gets the property value from a CIM path string. An example of a CIM path
     * //10.247.99.250/root/brocade1:Brocade_Fabric.CreationClassName="Brocade_Fabric",Name="10000027F858F6C0"
     * 
     * @param path
     * @param property
     * @return
     */
    private String getPropertyValueFromPath(String path,
            String property) {
        Pattern p = Pattern.compile(".*\\b" + property + "=\"([^\"]+)[\"].*", 0);
        Matcher m = p.matcher(path);
        if (m.matches()) {
            return m.group(1);
        }
        return "";
    }

    /**
     * Gets the property value from a CIM path string. An example of a CIM path
     * ManagedElement =
     * "root/brocade1:Brocade_ZoneAlias.InstanceID=\"NAME=Hala_REST_API_ZONE_12;ACTIVE=false;FABRIC=10000027F858F6C0;CLASSNAME=Brocade_ZoneAlias\""
     * ;
     * 
     * @param path
     * @param property
     * @return
     */
    private String getPropertyValueFromString(String path,
            String property) {
        Pattern p = Pattern.compile(".*\\b" + property + "=([^;\"]+)[;|\"].*", 0);
        Matcher m = p.matcher(path);
        if (m.matches()) {
            return m.group(1);
        }
        return "";
    }

    /**
     * Calls delete on any instance
     * 
     * @param client
     * @param path
     * @throws WBEMException
     */
    public void removeInstance(WBEMClient client, CIMObjectPath path) throws WBEMException {
        _log.info("Removing instance " + path);
        client.deleteInstance(path);
    }

    /**
     * get Logical switches to physical switch map
     * @param client an instance of WBEMClient
     * @return a map of logical to physical switch name
     * @throws WBEMException
     */
    public HashMap<String, String> getLogicalToPhysicalSwitcheMap(WBEMClient client) throws WBEMException {
        HashMap<String, String> switchMap  = new HashMap<>();
        CIMObjectPath path = new CIMObjectPath(null, null, null, _namespace, _Brocade_SwitchPCS, null);

        CloseableIterator<CIMInstance> it = null;
        try {
            it = client.enumerateInstances(path, false, true, true, null);

            while (it.hasNext()) {
                CIMInstance ins = it.next();
                String parentName = cimStringProperty(ins, _Parent);
                String childName = cimStringProperty(ins, _Child);
                _log.debug("Brocade Switch: Parent :  {} - Child : {} )", parentName, childName);

                CIMProperty[] props = ins.getProperties();
                for(int i=0; i < props.length; i++) {
                    _log.debug("Switch property : " + props[i].getName() + ": value : " + props[i].getValue());
                }

                CIMInstance parentObject = client.getInstance(new CIMObjectPath(parentName), true, true, null);
                CIMInstance childObject = client.getInstance(new CIMObjectPath(childName), true, true, null);

                parentName = cimStringProperty(parentObject, _element_name);
                childName = cimStringProperty(childObject, _element_name);
                switchMap.put(childName, parentName);
                _log.info("Brocade Switch: Logical Switch : " + childName + " In (" + parentName + ")");
            }
        } finally {
            if (it != null) {
                it.close();
            }
        }
        return switchMap;
    }

}
