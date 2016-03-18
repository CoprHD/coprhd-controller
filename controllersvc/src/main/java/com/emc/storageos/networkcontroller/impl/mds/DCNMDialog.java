/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.networkcontroller.impl.mds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.cim.CIMArgument;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.cim.UnsignedInteger32;
import javax.security.auth.Subject;
import javax.wbem.CloseableIterator;
import javax.wbem.WBEMException;
import javax.wbem.client.PasswordCredential;
import javax.wbem.client.UserPrincipal;
import javax.wbem.client.WBEMClient;
import javax.wbem.client.WBEMClientConstants;
import javax.wbem.client.WBEMClientFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.cimadapter.connections.cim.CimObjectPathCreator;
import com.emc.storageos.db.client.model.FCEndpoint;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.networkcontroller.BaseSANCIMObject;
import com.emc.storageos.networkcontroller.exceptions.NetworkControllerSessionLockedException;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.smis.CIMArgumentFactory;

public class DCNMDialog extends BaseSANCIMObject {

    private static final String _namespace = "cimv2";
    private static final String _fabric_path = "CISCO_AdminDomain";
    private final String _wwnRegex = "([0-9A-Fa-f][0-9A-Fa-f]){8}";
    private static final int _started = 2;
    private static final int _ended = 3;
    private static final int _notApplicable = 4;
    private static final int _terminated = 4;
    private static final int _noChange = 5;
    private CIMArgumentFactory _cimArgumentFactory = new CIMArgumentFactory();
    private static final Logger _log = LoggerFactory.getLogger(DCNMDialog.class);

    WBEMClient _client = null;

    /**
     * Initialize the client interface.
     * 
     * @param ipaddress
     * @param username
     * @param password
     * @param smisport
     * @return WBEMClient
     */
    public WBEMClient getClient(String ipaddress, String username, String password, Integer smisport) {
        try {
            WBEMClient client = WBEMClientFactory.getClient(WBEMClientConstants.PROTOCOL_CIMXML);
            CIMObjectPath path = CimObjectPathCreator.createInstance("http", ipaddress, smisport.toString(), _namespace, null, null);
            final Subject subject = new Subject();
            subject.getPrincipals().add(new UserPrincipal(username));
            subject.getPrivateCredentials().add(new PasswordCredential(password));
            client.initialize(path, subject, new Locale[] { Locale.US });
            _client = client;
            return client;
        } catch (WBEMException ex) {
            _log.error("Can't open client: WBEMException: " + ex.getLocalizedMessage());
            return null;
        }
    }

    public class FCProtocolEndpoint {
        String wwpn;
        String wwnn;
        Interface iface;            // interface implementing this endpoint
        CIMObjectPath cimPath;
        Map<String, FCProtocolEndpoint> connections = new HashMap<String, FCProtocolEndpoint>(); // key is wwpn

        FCProtocolEndpoint(CIMInstance ins) {
            // DCNM has a wierd format: Name = "200000059B1F06D0\\16846848\\1"
            wwpn = cimStringProperty(ins, "Name").replaceAll("\\\\.*$", "");
            wwnn = cimStringProperty(ins, "SystemName");
            if (wwnn.matches(_wwnRegex) == false) {
                wwnn = "";
            }
            String ifName = null;
            iface = Interface.snToInterface.get(wwpn);
            cimPath = ins.getObjectPath();
        }

        /**
         * Find connections to this endpoint. Put them in the connections map.
         * 
         * @param client WBEMClient
         * @param namespace CIM namespace
         */
        void findConnections(WBEMClient client, String namespace) {
            CloseableIterator<CIMInstance> instances = null;
            try {
                connections.clear();
                instances = client.associatorInstances(cimPath,
                        // "CISCO_ActiveConnection", "CISCO_ProtocolEndpoint", null, null, false, false, null);
                        // "CISCO_ActiveConnection", null, null, null, false, false, null);
                        null, "CISCO_ProtocolEndpoint", null, null, false, null);
                while (instances.hasNext()) {
                    CIMInstance instance = instances.next();
                    // Must be of type FC WWPN
                    if (!instance.getProperty("ProtocolIFType").getValue().toString().equals("56")) {
                        continue;
                    }
                    FCProtocolEndpoint ep = new FCProtocolEndpoint(instance);
                    this.connections.put(ep.wwpn, ep);
                }
            } catch (WBEMException ex) {
                _log.error("Can't find FCProtocolEndpoint connections: ", ex);
            } finally {
                if (instances != null) {
                    instances.close();
                }
            }
        }

    };

    /**
     * Given an instance of a CISCO_LogicalFCPort, get the system name housing this port.
     * 
     * @param lportIns
     * @return system name String
     * @throws Exception
     */
    private String getSystemNameFromLogicalPort(CIMInstance lportIns) throws Exception {
        String systemName = null;
        CloseableIterator<CIMInstance> fcportIt = null;
        CloseableIterator<CIMInstance> physcomputerIt = null;
        try {
            fcportIt = _client.associatorInstances(lportIns
                    .getObjectPath(),
                    null, "CISCO_FCPort", null, null, false, null);
            while (fcportIt.hasNext()) {
                CIMInstance fcportIns = fcportIt.next();
                physcomputerIt = _client.associatorInstances(fcportIns.getObjectPath(),
                        null, "CISCO_PhysicalComputerSystem", null, null, false, null);
                while (physcomputerIt.hasNext()) {
                    CIMInstance physins = physcomputerIt.next();
                    systemName = cimStringProperty(physins, "ElementName");
                }
                physcomputerIt.close();
                physcomputerIt = null;
            }
        } finally {
            if (fcportIt != null) {
                fcportIt.close();
            }
            if (physcomputerIt != null) {
                physcomputerIt.close();
            }
        }
        return systemName;
    }

    List<FCEndpoint> getPortConnection() throws Exception {
        List<FCEndpoint> connections = new ArrayList<FCEndpoint>();
        CIMObjectPath path = CimObjectPathCreator.createInstance("CISCO_LogicalComputerSystem", _namespace);
        CloseableIterator<CIMInstance> lcsIt = null;
        CloseableIterator<CIMInstance> lportIt = null;
        CloseableIterator<CIMInstance> pepIt = null;
        try {
            lcsIt = _client.enumerateInstances(path, false, true, true, null);
            while (lcsIt.hasNext()) {
                CIMInstance lcsIns = lcsIt.next();
                // Get the VSAN of this Logical COmputer System
                String fabricId = null;
                String[] identifyingDescriptions = (String[]) lcsIns.getProperty("IdentifyingDescriptions").getValue();
                String[] otherIdentifyingInfo = (String[]) lcsIns.getProperty("OtherIdentifyingInfo").getValue();
                if (identifyingDescriptions.length >= 2 && identifyingDescriptions[1].equals("VsanId")
                        && otherIdentifyingInfo.length >= 2) {
                    fabricId = otherIdentifyingInfo[1];
                }
                // Find the associated CISCO_LogicalFCPort structures
                lportIt = _client.associatorInstances(lcsIns.getObjectPath(),
                        "CISCO_FCPortsInLogicalComputerSystem", "CISCO_LogicalFCPort", null, null, false, null);
                // Iterate through all the ports in this Vsan, finding connections.
                while (lportIt.hasNext()) {
                    CIMInstance lportIns = lportIt.next();
                    _log.debug("logical port: " + cimStringProperty(lportIns, "Name") + " wwpn "
                            + cimStringProperty(lportIns, "PermanentAddress"));

                    String systemName = getSystemNameFromLogicalPort(lportIns);

                    pepIt = _client.associatorInstances(lportIns.getObjectPath(),
                            "CISCO_FCPortSAPImplementation", "CISCO_ProtocolEndPoint", null, null, false, null);
                    while (pepIt.hasNext()) {
                        CIMInstance pepIns = pepIt.next();
                        _log.debug("endpoint: " + cimStringProperty(pepIns, "Name"));
                        FCProtocolEndpoint pep = new FCProtocolEndpoint(pepIns);
                        pep.findConnections(_client, _namespace);
                        for (String key : pep.connections.keySet()) {
                            _log.debug("connection: " + key);
                            FCProtocolEndpoint cep = pep.connections.get(key);
                            FCEndpoint conn = new FCEndpoint();
                            // conn.setFabricId(fabricId)
                            conn.setRemotePortName(formatWWN(cep.wwpn));
                            conn.setLabel(conn.getRemotePortName());
                            conn.setRemoteNodeName(formatWWN(cep.wwnn));
                            conn.setSwitchPortName(formatWWN(cimStringProperty(lportIns, "PermanentAddress")));
                            conn.setSwitchInterface(cimStringProperty(lportIns, "Name"));
                            conn.setFabricId(fabricId);
                            conn.setSwitchName(systemName);
                            connections.add(conn);
                        }
                    }
                    pepIt.close();
                    pepIt = null;
                }
                lportIt.close();
                lportIt = null;
            }
        } finally {
            if (lcsIt != null) {
                lcsIt.close();
            }
            if (lportIt != null) {
                lportIt.close();
            }
            if (pepIt != null) {
                pepIt.close();
            }
        }
        return connections;
    }

    /**
     * Get the list of Vsan Ids.
     * 
     * @return List<String>
     * @throws Exception
     */
    public List<String> getFabricIds() throws Exception {
        // A set is used because in the case of a disconnected network the same Vsan can
        // show up twice. Conerted to list at end.
        Set<String> fabricIds = new HashSet<String>();
        List<Zoneset> zonesets = new ArrayList<Zoneset>();
        CIMObjectPath path = CimObjectPathCreator.createInstance("Cisco_Vsan", _namespace);
        CloseableIterator<CIMInstance> vsanIt = null;
        try {
            vsanIt = _client.enumerateInstances(path, false, true, true, null);
            while (vsanIt.hasNext()) {
                String vsanId = null;
                CIMInstance vsanIns = vsanIt.next();
                CIMProperty prop = vsanIns.getProperty("OtherIdentifyingInfo");
                String[] idinfoValue = (String[]) prop.getValue();
                if (idinfoValue.length == 2 && idinfoValue[0].equals("Fabric")) {
                    vsanId = idinfoValue[1];
                }
                if (vsanId != null) {
                    fabricIds.add(vsanId);
                }
            }
        } finally {
            if (vsanIt != null) {
                vsanIt.close();
            }
        }
        List<String> alist = new ArrayList<String>();
        alist.addAll(fabricIds);
        return alist;
    }

    /**
     * Make ZoneMember from ZoneMemberSettingData instance
     * 
     * @param membershipInstance
     * @return ZoneMember
     * @throws WBEMException
     */
    private ZoneMember makeZoneMember(CIMInstance membershipInstance) throws WBEMException {
        String address = cimStringProperty(membershipInstance, "ConnectivityMemberID");
        ZoneMember.ConnectivityMemberType type =
                ZoneMember.ConnectivityMemberType.byValue(cimIntegerProperty(membershipInstance,
                        "ConnectivityMemberType"));
        ZoneMember zm = new ZoneMember(address, type);
        zm.setInstanceID(cimStringProperty(membershipInstance, "InstanceID"));
        zm.setCimObjectPath(membershipInstance.getObjectPath());
        return zm;
    }

    /**
     * Make a Zone structure from a CIMInstance.
     * 
     * @param zoneInstance
     * @return Zone
     */
    private Zone makeZone(CIMInstance zoneInstance) throws WBEMException {
        String name = cimStringProperty(zoneInstance, "ElementName");
        Zone zn = new Zone(name);
        zn.setCimObjectPath(zoneInstance.getObjectPath());
        zn.setInstanceID(cimStringProperty(zoneInstance, "InstanceID"));
        zn.setActive(cimBooleanProperty(zoneInstance, "Active"));

        CloseableIterator<CIMInstance> zms = null;
        try {
            zms = _client.associatorInstances(((CIMObjectPath) zn.getCimObjectPath()),
                    "CISCO_ElementSettingData", "CISCO_ZoneMemberSettingData", null,
                    null, false, null);
            while (zms.hasNext()) {
                CIMInstance zm = zms.next();
                ZoneMember member = makeZoneMember(zm);
                zn.getMembers().add(member);
            }
        } finally {
            if (zms != null) {
                zms.close();
            }
        }
        return zn;
    }

    /**
     * Make a zoneset object from a CIMInstance for the Zoneset
     * Calls makeZone().
     * 
     * @param zonesetInstance
     * @return Zoneset
     */
    private Zoneset makeZoneset(CIMInstance zonesetInstance) throws WBEMException {
        String name = cimStringProperty(zonesetInstance, "ElementName");
        Zoneset zs = new Zoneset(name);
        zs.setCimObjectPath(zonesetInstance.getObjectPath());
        zs.setInstanceID(cimStringProperty(zonesetInstance, "InstanceID"));
        zs.setDescription(cimStringProperty(zonesetInstance, "Description"));
        zs.setActive(cimBooleanProperty(zonesetInstance, "Active"));

        CloseableIterator<CIMInstance> zns = null;
        try {
            zns = _client.associatorInstances(((CIMObjectPath) zs.getCimObjectPath()),
                    "CIM_MemberOfCollection", "CISCO_Zone", null, null, false,
                    null);
            while (zns.hasNext()) {
                CIMInstance zn = zns.next();
                Zone zone = makeZone(zn);
                zs.getZones().add(zone);
            }
        } finally {
            if (zns != null) {
                zns.close();
            }
        }
        return zs;
    }

    /**
     * Returns the zonesets in a VSAN as given by its CIMInstance.
     * This generates the nested objects Zone and ZoneMember in the Zonesets.
     * 
     * @param vsanIns CIMInstance of vsan
     * @return List<Zoneset>
     * @throws javax.wbem.WBEMException
     */
    private List<Zoneset> getZonesetsInVsan(CIMInstance vsanIns) throws WBEMException {
        List<Zoneset> inactiveZonesets = new ArrayList<Zoneset>();
        Zoneset activeZoneset = null;

        // Iterate through the zonesets.
        CloseableIterator<CIMInstance> zstIt = null;
        try {
            zstIt = _client.associatorInstances(vsanIns.getObjectPath(),
                    "CIM_HostedCollection", "CISCO_Zoneset", null, null, false, null);
            while (zstIt.hasNext()) {
                CIMInstance zsIns = zstIt.next();
                Zoneset zs = makeZoneset(zsIns);
                _log.debug("zoneset: " + zs.name);
                if (zs.active == true) {
                    activeZoneset = zs;
                } else {
                    inactiveZonesets.add(zs);
                }
            }
        } catch (WBEMException ex) {
            // Problem where iterator isn't returned in associators();
        } finally {
            if (zstIt != null) {
                zstIt.close();
            }
        }

        List<Zoneset> zonesets = new ArrayList<Zoneset>();
        if (activeZoneset != null) {
            zonesets.add(activeZoneset);
        }
        zonesets.addAll(inactiveZonesets);

        return zonesets;
    }

    /**
     * Returns the Cisco_Vsan Instance for a specified Vsan.
     * 
     * @param desiredVsan
     * @return
     * @throws WBEMException
     */
    private CIMInstance getVsanInstance(Integer desiredVsan) throws WBEMException {
        CIMObjectPath path = CimObjectPathCreator.createInstance("Cisco_Vsan", _namespace);
        CloseableIterator<CIMInstance> vsanIt = null;
        try {
            vsanIt = _client.enumerateInstances(path,
                    false, true, true, null);
            while (vsanIt.hasNext()) {
                CIMInstance vsanIns = null;
                String vsanId = null;
                vsanIns = vsanIt.next();

                CIMProperty prop = vsanIns.getProperty("OtherIdentifyingInfo");
                String[] idinfoValue = (String[]) prop.getValue();
                if (idinfoValue.length == 2 && idinfoValue[0].equals("Fabric")) {
                    vsanId = idinfoValue[1];
                    if (desiredVsan.toString().equals(vsanId)) {
                        return vsanIns;
                    }
                }
            }
        } finally {
            if (vsanIt != null) {
                vsanIt.close();
            }
        }
        return null; // not found
    }

    /**
     * Returns all zonesets in the desiredVsan. Active zoneset is at the front of the returned list.
     * This generates the nested objects Zone and ZoneMember in the Zonesets.
     * 
     * @param desiredVsan Integer
     * @return List<Zoneset>
     * @throws WBEMException
     */
    public List<Zoneset> getZonesets(Integer desiredVsan) throws WBEMException {
        List<Zoneset> zonesets = new ArrayList<Zoneset>();
        CIMObjectPath path = CimObjectPathCreator.createInstance("Cisco_Vsan", _namespace);
        CloseableIterator<CIMInstance> vsanIt = null;
        try {
            vsanIt = _client.enumerateInstances(path, false, true, true, null);
            while (vsanIt.hasNext()) {
                CIMInstance vsanIns = null;
                String vsanId = null;
                vsanIns = vsanIt.next();

                CIMProperty prop = vsanIns.getProperty("OtherIdentifyingInfo");
                String[] idinfoValue = (String[]) prop.getValue();
                if (idinfoValue.length == 2 && idinfoValue[0].equals("Fabric")) {
                    vsanId = idinfoValue[1];
                    if (desiredVsan.toString().equals(vsanId)) {
                        List<Zoneset> znsets = getZonesetsInVsan(vsanIns);
                        zonesets.addAll(znsets);
                    }
                }
            }
        } finally {
            if (vsanIt != null) {
                vsanIt.close();
            }
        }
        return zonesets;
    }

    /**
     * @param client
     * @param zonesetService -- Instance of the ZonesetService
     * @return true iff session is started
     * @throws NetworkControllerSessionLockedException
     * @throws WBEMException
     */
    @SuppressWarnings("rawtypes")
    public boolean startSession(WBEMClient client, CIMInstance zonesetService)
            throws NetworkControllerSessionLockedException, WBEMException {
        UnsignedInteger32 result = null;
        try {
            int sessionState = cimIntegerProperty(zonesetService,
                    "SessionState");
            int RequestedSessionState = cimIntegerProperty(zonesetService,
                    "RequestedSessionState");
            if (sessionState == _notApplicable) {
                // no session control implemented by this agent
                return true;
            }
            // if (sessionState != _ended || RequestedSessionState != _noChange) {
            // _log.error("Zone session is locked by another user or agent.");
            // throw new NetworkControllerSessionLockedException(
            // "Zone session is locked by another user or agent.");
            // }
            CIMArgument[] inargs = new CIMArgument[] { _cimArgumentFactory
                    .uint16("RequestedSessionState", _started) };
            result = (UnsignedInteger32) client.invokeMethod(
                    zonesetService.getObjectPath(), "SessionControl", inargs,
                    new CIMArgument[1]);
            _log.info("Start session returned code: " + result.intValue());
            return (result.intValue() == 0 || result.intValue() == 32772);
        } catch (WBEMException ex) {
            _log.error("Encountered an exception while trying to start a zone session."
                    + ex.getLocalizedMessage());
            throw ex;
        }
    }

    /**
     * End a session, commit if commit == true
     * 
     * @param client
     * @param zonesetService
     * @param commit
     * @return true iff worked
     * @throws WBEMException
     */
    @SuppressWarnings("rawtypes")
    public boolean endSession(WBEMClient client, CIMInstance zonesetService,
            boolean commit) throws WBEMException {
        UnsignedInteger32 result = null;
        try {
            int sessionState = cimIntegerProperty(zonesetService,
                    "SessionState");
            if (sessionState == _notApplicable) {
                // no session control implemented by this agent
                return true;
            }
            // if (sessionState != _started) {
            // // no session control implemented by this agent
            // return true;
            // }
            int endMode = commit ? _ended : _terminated;
            CIMArgument[] inargs = new CIMArgument[] { _cimArgumentFactory
                    .uint16("RequestedSessionState", endMode) };
            result = (UnsignedInteger32) client.invokeMethod(
                    zonesetService.getObjectPath(), "SessionControl", inargs,
                    new CIMArgument[1]);
            _log.info("End session returned code: " + result.intValue());
            return result.intValue() == 0;
        } catch (WBEMException ex) {
            _log.error("Encountered an exception while trying to start a zone session."
                    + ex.getLocalizedMessage());
            throw ex;
        }
    }

    @SuppressWarnings("rawtypes")
    public CIMObjectPath addZone(WBEMClient client,
            CIMInstance zonesetServiceIns, CIMObjectPath zonesetPath,
            String zoneName) throws WBEMException {
        CIMObjectPath zonePath = null;
        CIMArgument[] outargs = new CIMArgument[1];
        CIMArgument[] inargs = new CIMArgument[3];
        inargs[0] = _cimArgumentFactory.string("ZoneName", zoneName);
        inargs[1] = _cimArgumentFactory.uint16("ZoneType", 2);
        // TODO - I am not sure what subtype is yet, I need to find out.
        inargs[2] = _cimArgumentFactory.uint16("ZoneSubType", 1);
        UnsignedInteger32 result = (UnsignedInteger32) client.invokeMethod(
                zonesetServiceIns.getObjectPath(), "CreateZone", inargs,
                outargs);
        if (result.intValue() == 0 && outargs.length > 0) {
            zonePath = (CIMObjectPath) outargs[0].getValue();
        }
        if (zonePath != null) {
            // add zone to zoneset
            outargs = new CIMArgument[1];
            inargs = new CIMArgument[2];
            inargs[0] = _cimArgumentFactory.reference("ZoneSet", zonesetPath);
            inargs[1] = _cimArgumentFactory.reference("Zone", zonePath);
            result = (UnsignedInteger32) client.invokeMethod(
                    zonesetServiceIns.getObjectPath(), "AddZone", inargs, outargs);
            if (result.intValue() != 0) {
                // TODO - I need to add more error handling
                zonePath = null;
            }
        }
        return zonePath;
    }

    @SuppressWarnings("rawtypes")
    public CIMObjectPath addZoneMember(WBEMClient client,
            CIMInstance zonesetServiceIns, CIMObjectPath zonePath, String wwn)
            throws WBEMException {
        CIMObjectPath zoneMemberPath = null;
        CIMArgument[] outargs = new CIMArgument[1];
        CIMArgument[] inargs = new CIMArgument[3];
        inargs[0] = _cimArgumentFactory.uint16("ConnectivityMemberType", 2);
        inargs[1] = _cimArgumentFactory.string("ConnectivityMemberID", wwn);
        inargs[2] = _cimArgumentFactory.reference("SystemSpecificCollection",
                zonePath);
        UnsignedInteger32 result = (UnsignedInteger32) client.invokeMethod(
                zonesetServiceIns.getObjectPath(),
                "CreateZoneMembershipSettingData", inargs, outargs);
        if (result.intValue() == 0 && outargs.length > 0) {
            zoneMemberPath = (CIMObjectPath) outargs[0].getValue();
        }
        return zoneMemberPath;
    }

    /**
     * Returns the ZoneSetServiceInstance used for invoking methods.
     * 
     * @param fabricId
     * @return
     * @throws javax.wbem.WBEMException
     */
    @SuppressWarnings("unchecked")
    public CIMObjectPath getZoneService(String fabricId) throws WBEMException {
        CIMInstance vsanins = getVsanInstance(new Integer(fabricId));
        CloseableIterator<CIMObjectPath> rfnmIt = null;
        CloseableIterator<CIMInstance> zsIt = null;
        try {
            rfnmIt = _client.referenceNames(vsanins
                    .getObjectPath(), "CISCO_ZoneServiceInVsan", null);
            while (rfnmIt.hasNext()) {
                CIMObjectPath rfnmins = rfnmIt.next();
                _log.info(rfnmins.toString());
            }

            zsIt = _client.associatorInstances(vsanins.getObjectPath(),
                    "CISCO_ZoneServiceInVsan", "CISCO_ZoneService", null, null, false, null);
            while (zsIt.hasNext()) {
                CIMInstance zsins = zsIt.next();
                _log.info(zsins.toString());
            }
        } finally {
            if (rfnmIt != null) {
                rfnmIt.close();
            }
            if (zsIt != null) {
                zsIt.close();
            }
        }
        return null;
    }

    public CIMInstance getFabricInstance(String fabricId)
            throws WBEMException {
        CIMObjectPath path = CimObjectPathCreator.createInstance(_fabric_path, _namespace);
        CloseableIterator<CIMInstance> fabricIter = null;
        try {
            fabricIter = _client.enumerateInstances(path, false, true, true, null);
            while (fabricIter.hasNext()) {
                CIMInstance fabricIns = fabricIter.next();
                if (fabricId.equals(cimStringProperty(fabricIns, "ElementName"))) {
                    return fabricIns;
                }
            }
        } finally {
            if (fabricIter != null) {
                fabricIter.close();
            }
        }
        return null;
    }

    /**
     * Given a dialog, add one or more zones to the active zoneset of the specified vsan.
     * This method is callable from with Bourne or from MDSDialogTest for stand-alone testing.
     * For now the only type of zone members supported are pwwn.
     * 
     * @param zones - List of zones to be created. Zone names will be overwritten.
     * @param vsanId - Integer vsanId
     * @return list of zone names that were added
     * @throws ControllerException
     */
    public List<String> addZonesStrategy(List<Zone> zones, Integer vsanId)
            throws Exception {
        List<String> addedZoneNames = new ArrayList<String>();
        boolean inSession = false;
        boolean commit = true;
        CIMObjectPath zonesetServicePath = getZoneService(vsanId.toString());
        if (zonesetServicePath == null) {
            throw new DeviceControllerException("Couldn't locate ZoneSetService vsan: " + vsanId);
        }
        CIMInstance zonesetService = _client.getInstance(zonesetServicePath, false, false, null);

        try {
            // Start a session.
            inSession = startSession(_client, zonesetService);

            // Get the existing zones in the active zoneset.
            // XXX FIXME: Need to account for creating the zoneset if none exists.
            List<Zoneset> zonesets = getZonesets(vsanId);
            if (zonesets == null || zonesets.isEmpty()) {
                throw new DeviceControllerException("no zonesets");
            }
            Zoneset activeZoneset = zonesets.get(0);
            if (activeZoneset.getActive() != true) {
                throw new DeviceControllerException("no active zoneset");
            }

            for (Zone zone : zones) {
                CIMObjectPath zonePath = addZone(_client, zonesetService,
                        ((CIMObjectPath) activeZoneset.getCimObjectPath()), zone.getName());
            }

        } finally {
            // Commit session.
            if (inSession) {
                endSession(_client, zonesetService, commit);
            }
        }
        return addedZoneNames;
    }
}
