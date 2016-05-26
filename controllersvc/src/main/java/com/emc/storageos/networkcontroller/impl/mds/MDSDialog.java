/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.networkcontroller.impl.mds;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.IntRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.FCEndpoint;
import com.emc.storageos.networkcontroller.SSHDialog;
import com.emc.storageos.networkcontroller.SSHPrompt;
import com.emc.storageos.networkcontroller.SSHSession;
import com.emc.storageos.networkcontroller.exceptions.NetworkDeviceControllerException;
import com.google.common.collect.Sets;

/**
 * This file contains all the SSH dialogs to/from the MDS (or Nexus) Cisco switches.
 * 
 * @author Watson
 *         Generally, methods are named similar to the command(s) they execute.
 *         For example, showZoneset() issues the command "show zoneset..."
 * 
 *         The command strings, and regular expression matching patterns, are externalized to a file
 *         called networksystem-mds-dialog-properties. In this way should a problem arise in the field,
 *         it is possible to patch the regular expressions thereby implementing a work-around by simply
 *         modifying /os/storageos/conf/networksystem-mds-dialog-properties.
 * 
 *         Also, for debugging purposes, it is possible to turn on debugging such that every command sent and
 *         all data received from the device is logged in the controllersvc.log by including the following in your
 *         controllersvc-log4j.properties file (also in /os/storageos/conf) and restarting:
 *         log4j.logger.com.emc.storageos.networkcontroller=DEBUG
 *         (Note this output can be very verbose.)
 * 
 */
public class MDSDialog extends SSHDialog {
    private boolean inConfigMode = false;
    private boolean inSession = false;
    private SSHPrompt lastPrompt = null;

    private static final Logger _log = LoggerFactory.getLogger(MDSDialog.class);
    private final String wwnRegex = "([0-9A-Fa-f][0-9A-Fa-f]:){7}[0-9A-Fa-f][0-9A-Fa-f]";
    private final static Integer sessionLockRetryMax = 5;

    public MDSDialog(SSHSession session, Integer defaultTimeout) {
        super(session, defaultTimeout);
    }

    /**
     * This initializes a new session, gathering the prompts, and setting the terminal length.
     * 
     * @throws NetworkDeviceControllerException
     */
    public void initialize() throws NetworkDeviceControllerException {
        SSHPrompt[] prompts = { SSHPrompt.POUND, SSHPrompt.GREATER_THAN };
        StringBuilder buf = new StringBuilder();
        SSHPrompt got = waitFor(prompts, defaultTimeout, buf, true);
        String[] lines = buf.toString().split("[\n\r]+");
        String lastLine = lines[lines.length - 1];
        String[] groups = new String[2];
        if (match(lastLine, new String[] { MDSDialogProperties.getString("MDSDialog.initalize.match") + got.getRegex() }, groups) == 0) { // \\s*([-_A-Za-z0-9]+)
            devname = groups[0];
        }
        sendWaitFor(MDSDialogProperties.getString("MDSDialog.initialize.termlength.cmd"), 5000, prompts, buf); // terminal length 0\n
        _log.info(buf.toString());
    }

    /**
     * Returns the device type and software version
     * 
     * @return [0] device type, [1] software version
     */
    public String[] showVersion() throws NetworkDeviceControllerException {
        String[] returnVal = new String[2];
        SSHPrompt[] prompts = { SSHPrompt.MDS_POUND, SSHPrompt.MDS_GREATER_THAN };
        StringBuilder buf = new StringBuilder();
        SSHPrompt prompt = sendWaitFor(MDSDialogProperties.getString("MDSDialog.showVersion.cmd"),  // show version\n
                10000, prompts, buf);
        String[] lines = getLines(buf);
        String[] regex = {
                MDSDialogProperties.getString("MDSDialog.showVersion.version.match"), // .*system:\\s+version +(\\S+).*
                MDSDialogProperties.getString("MDSDialog.showVersion.MDS.match"), // .*[Cc][Ii][Ss][Cc][Oo]\\s+(MDS\\s+\\S+).*
                MDSDialogProperties.getString("MDSDialog.showVersion.Nexus.match") // *[Cc][Ii][Ss][Cc][Oo]\\s+(Nexus\\S+).*
        };
        String[] groups = new String[2];
        for (String line : lines) {
            int index = match(line, regex, groups);
            switch (index) {
                case 0:         // software version number
                    returnVal[1] = groups[0];
                    break;
                case 1:         // hardware type
                    returnVal[0] = groups[0];		// MDS
                    break;
                case 2:
                    returnVal[0] = groups[0];		// Nexus
                    break;
            }
        }
        return returnVal;
    }

    /**
     * Returns the system uptime.
     * 
     * @return systme uptime
     * @throws NetworkDeviceControllerException
     */
    public String showSystemUptime() throws NetworkDeviceControllerException {
        SSHPrompt[] prompts = { SSHPrompt.MDS_POUND, SSHPrompt.MDS_GREATER_THAN };
        StringBuilder buf = new StringBuilder();
        String systemUptime = null;
        String[] regex = {
                MDSDialogProperties.getString("MDSDialog.showSystem.systemUptime.match") // System uptime:\\s+(.*)
        };
        SSHPrompt prompt = sendWaitFor(MDSDialogProperties.getString("MDSDialog.showSystem.systemUptime.cmd"),  // show system uptime\n
                10000, prompts, buf);
        String[] lines = getLines(buf);
        String[] groups = new String[1];
        for (String line : lines) {
            if (match(line, regex, groups) == 0) {
                systemUptime = groups[0];
                break;
            }
        }
        return systemUptime;
    }

    /**
     * Generates a set of FCPortConnection entries for the specified vsan id (or all
     * Vsans if vsanId is null).
     * 
     * @param vsanId used to qualify command to one vsan
     * @return list of FCPortConnections
     */
    public List<FCEndpoint> showFcnsDatabase(Integer vsanId) throws NetworkDeviceControllerException {
        Map<Integer, String> vsanToWwns = getVsanWwns(vsanId);
        List<FCEndpoint> connections = new ArrayList<FCEndpoint>();
        SSHPrompt[] prompts = { SSHPrompt.MDS_POUND, SSHPrompt.MDS_GREATER_THAN };
        StringBuilder buf = new StringBuilder();
        String cmd = MDSDialogProperties.getString("MDSDialog.showFcnsDatabase.cmd"); // show fcns database detail
        if (vsanId != null) {
            cmd = cmd + MDSDialogProperties.getString("MDSDialog.showFcnsDatabase.vsan.cmd") + vsanId.toString() + "\n"; // \ vsan
        }
        else {
            cmd = cmd + "\n"; //$NON-NLS-1$
        }
        SSHPrompt prompt = sendWaitFor(cmd, defaultTimeout, prompts, buf);
        String[] lines = getLines(buf);
        String[] regex = {
                // "VSAN:(\\d+)\\w+FCID:(.*)",
                MDSDialogProperties.getString("MDSDialog.showFcnsDatabase.VSAN.match"), // VSAN:(\\d+)\\s+FCID:(0x[0-9a-fA-F:]+)\\s*
                MDSDialogProperties.getString("MDSDialog.showFcnsDatabase.portwwn.match"), // port-wwn[^:]+:([0-9a-fA-F:]+).*
                MDSDialogProperties.getString("MDSDialog.showFcnsDatabase.nodewwn.match"), // node-wwn[^:]+:([0-9a-fA-F:]+).*
                MDSDialogProperties.getString("MDSDialog.showFcnsDatabase.fabricportwwn.match"), // fabric-port-wwn[^:]+:([0-9a-fA-F:]+).*
                MDSDialogProperties.getString("MDSDialog.showFcnsDatabase.ConnectedInterface.match"), // Connected Interface[^:]+:(fc\\S+)
                MDSDialogProperties.getString("MDSDialog.showFcnsDatabase.SwitchName.match"), // Switch Name[^:]+:(.*)
                MDSDialogProperties.getString("MDSDialog.showFcnsDatabase.deviceAlias.match") // \\s*\\[(\\S+)\\]\\s*:q

        };
        String[] groups = new String[10];
        FCEndpoint conn = null;
        for (String line : lines) {
            int index = match(line, regex, groups, Pattern.CASE_INSENSITIVE);
            switch (index) {
                case 0:
                    conn = new FCEndpoint();
                    conn.setFabricId(groups[0]);    // vsan
                    conn.setFcid(groups[1]);        // fcid
                    String fabricWwn = vsanToWwns.get(new Integer(groups[0]));
                    if (fabricWwn != null) {
                        conn.setFabricWwn(fabricWwn);
                    }
                    connections.add(conn);
                    break;
                case 1:
                    conn.setRemotePortName(groups[0]);  // remote wwpn
                    break;
                case 2:
                    conn.setRemoteNodeName(groups[0]);  // remote wwnn
                    break;
                case 3:
                    conn.setSwitchPortName(groups[0]);  // local wwpn
                    break;
                case 4:
                    conn.setSwitchInterface(groups[0]); // switch interface
                    break;
                case 5:
                    conn.setSwitchName(groups[0]);      // switch name
                    break;
                case 6:
                    conn.setRemotePortAlias(groups[0]); // pwwn alias
            }
        }
        return connections;
    }

    /**
     * Issues the "show interface" command and collects in information into a list of
     * interfaces. For now only parses fiber channel interfaces starting with "fc",
     * e.g. fc1/1, fc2/20, ...
     * This method is not currently used.
     * 
     * @return List<Interface>
     */
    public List<Interface> showInterface() throws NetworkDeviceControllerException {
        List<Interface> interfaces = new ArrayList<Interface>();
        SSHPrompt[] prompts = { SSHPrompt.POUND, SSHPrompt.GREATER_THAN };
        StringBuilder buf = new StringBuilder();
        SSHPrompt prompt = sendWaitFor(MDSDialogProperties.getString("MDSDialog.showInterface.cmd"),  // show interface\n
                60000, prompts, buf);
        String[] lines = getLines(buf);
        String[] regex = {
                MDSDialogProperties.getString("MDSDialog.showInterface.interfacename.match"), // (fc\\d+/\\d+) is (\\S+).*
                MDSDialogProperties.getString("MDSDialog.showInterface.Portdescription.match"), // \\s+Port description is (.*)
                MDSDialogProperties.getString("MDSDialog.showInterface.PortWWN.match"), // \\s+Port WWN is ([0-9a-fA-F:]+)\\s*
                MDSDialogProperties.getString("MDSDialog.showInterface.PortmodeFCID.match"), // \\s+Port mode is (\\w*), FCID is
                                                                                             // (0x\\p{XDigit}+)\\s*
                MDSDialogProperties.getString("MDSDialog.showInterface.Portvsan.match"), // \\s+Port vsan is (\\d+)\\s*
                MDSDialogProperties.getString("MDSDialog.showInterface.Portmode.match"), // \\s+Port mode is (\\w*)\\s*
        };
        String[] groups = new String[10];
        Interface intf = null;
        for (String line : lines) {
            int index = match(line, regex, groups);
            switch (index) {
                case 0:
                    intf = new Interface(groups[0]);  // save name
                    intf.setStatus(groups[1]);
                    interfaces.add(intf);
                    break;
                case 1:
                    intf.setDescription(groups[0]);
                    break;
                case 2:
                    intf.setWwpn(groups[0]);
                    break;
                case 3:
                    intf.setMode(groups[0]);
                    intf.setFcid(groups[1]);
                    break;
                case 4:
                    intf.setVsan(groups[0]);
                    break;
                case 5:
                    intf.setMode(groups[0]);
                    break;
            }
        }
        return interfaces;
    }

    /**
     * Shows the FLOGI database, which are the local logins to this switch.
     * This method is not currently used.
     * 
     * @throws NetworkDeviceControllerException
     */
    public void showFlogiDatabase() throws NetworkDeviceControllerException {
        SSHPrompt[] prompts = { SSHPrompt.POUND, SSHPrompt.GREATER_THAN };
        StringBuilder buf = new StringBuilder();
        SSHPrompt prompt = sendWaitFor(MDSDialogProperties.getString("MDSDialog.showFlogiDatabase.cmd"), 60000, prompts, buf); // show flogi
                                                                                                                               // database\n
        String[] lines = getLines(buf);
        boolean sawHdr = false;
        String[] regex = {
                MDSDialogProperties.getString("MDSDialog.showFlogiDatabase.INTERFACEVSANFCID.match"), // INTERFACE\\s+VSAN\\s+FCID\\s+PORT
                                                                                                      // NAME\\s+NODE NAME\\s*
                MDSDialogProperties.getString("MDSDialog.showFlogiDatabase.interfacename.match"), // (fc\\S+)\\s+(\\d+)\\s+(\\S+)\\s+([0-9a-fA-F:]+)\\s+([0-9a-fA-F:]+)\\s*
                MDSDialogProperties.getString("MDSDialog.showFlogiDatabase.Totalflogi.match") // Total number of flogi.*
        };
        String[] groups = new String[10];
        for (String line : lines) {
            int index = match(line, regex, groups);
            switch (index) {
                case 0:
                    sawHdr = true;
                    break;
                case 1:
                    _log.info(groups[0] + " " + groups[1] + " " + groups[2] + " " + groups[3] + " " + groups[4]);
                    break;
                case 2:
                    sawHdr = false;
                    break;
            }
        }
    }

    /**
     * Returns a map from Vsan ID to the fabric WWN as determined by the principal switch.
     * Note this value is not immutable; if a new principal switch is elected, then the mapping
     * will change.
     * 
     * @param vsanId If non null, only returns result for specificed Vsan. If null, all Vsans.
     * @return Map keyed by Vsan ID to WWN of Fabric
     * @throws NetworkDeviceControllerException
     */
    public Map<Integer, String> getVsanWwns(Integer vsanId) throws NetworkDeviceControllerException {
        Map<Integer, String> vsanToWwns = new HashMap<Integer, String>();
        SSHPrompt[] prompts = { SSHPrompt.POUND, SSHPrompt.GREATER_THAN };
        StringBuilder buf = new StringBuilder();
        String cmd = MDSDialogProperties.getString("MDSDialog.getVsanWwns.showfcdomain.cmd"); // show fcdomain
        if (vsanId != null) {
            cmd = cmd + MDSDialogProperties.getString("MDSDialog.getVsanWwns.vsan.cmd") // \ vsan
                    + vsanId.toString() + "\n";
        } else {
            cmd = cmd + "\n";
        }
        sendWaitFor(cmd, defaultTimeout, prompts, buf);
        String[] lines = getLines(buf);
        String[] regex = {
                MDSDialogProperties.getString("MDSDialog.getVsanWwns.VSAN.match"), // \\s*VSAN\\s+(\\d+).*
                MDSDialogProperties.getString("MDSDialog.getVsanWwns.Runningfabricname.match") // .*Running fabric name:\\s+(
                        + wwnRegex + MDSDialogProperties.getString("MDSDialog.getVsanWwns.Runningfabricname2.match") // ).*
        };
        String[] groups = new String[10];
        Integer vsan = null;

        for (String line : lines) {
            int index = match(line, regex, groups);
            switch (index) {
                case 0:									// "\\s*VSAN\\s+(\\d+).*"
                    vsan = new Integer(groups[0]);
                    break;
                case 1:									// ".*Running fabric name:\\s+(" + wwnRegex + ").*"
                    String wwn = groups[0];
                    if (vsan != null) {
                        vsanToWwns.put(vsan, wwn);
                        vsan = null;
                    } else if (vsanId != null) {		// "show fcdomain vsan 3180" has different format output
                        vsanToWwns.put(vsanId, wwn);
                    }
            }
        }
        return vsanToWwns;
    }

    /**
     * Collect the active Zoneset, and its Zones, members for a specified Vsan ID.
     * 
     * @param vsanId -- Integer vsanId
     * @return active zoneset for given vsanId
     */
    public Zoneset showActiveZoneset(Integer vsanId) throws NetworkDeviceControllerException {
        List<Zoneset> zonesets = showZoneset(vsanId, true, null, false, false);
        return zonesets.isEmpty() ? null : zonesets.get(0);
    }

    /**
     * Collect the Zonesets, and their Zones, members for a specified Vsan ID.
     * 
     * @param vsanId -- Integer vsanId
     * @param activeZonesetOnly - only return active zoneset. Otherwise, return all zonesets
     * @param zoneName - only returns zone with given zoneName. Return all zones, if not specified.
     * @param excludeMembers - true, do not include member with zone. Include members, if not specified.
     * @param excludeAliases - true, do not include aliases with zone. Include aliases, if not specified.
     * @return List<Zoneset> zonesets within that fabric. If zoneName is specified, and there is a match, then only one zone is returned.
     */
    public List<Zoneset> showZoneset(Integer vsanId, boolean activeZonesetOnly, String zoneName, boolean excludeMembers,
    		boolean excludeAliases) throws NetworkDeviceControllerException {
        List<Zoneset> zonesets = new ArrayList<Zoneset>();
        SSHPrompt[] prompts = { SSHPrompt.POUND, SSHPrompt.GREATER_THAN };
        StringBuilder buf = new StringBuilder();

        String zonesetCommand = MDSDialogProperties.getString("MDSDialog.showZoneset.cmd");
        sendWaitFor(zonesetCommand // show zoneset vsan
                + vsanId.toString() + "\n", defaultTimeout, prompts, buf);

        if (buf.toString().indexOf(MDSDialogProperties.getString("MDSDialog.showZoneset.not.configured")) >= 0) {
            throw NetworkDeviceControllerException.exceptions.fabricNotFoundInNetwork(vsanId.toString(), "");
        }

        String[] lines = getLines(buf);
        Zoneset zoneset = null;
        Zone zone = null;
        ZoneMember member = null;
        String[] regex = {
                MDSDialogProperties.getString("MDSDialog.showZoneset.zonesetname.match"), // \\s*zoneset name ([-\\w]+) vsan (\\d+).*
                MDSDialogProperties.getString("MDSDialog.showZoneset.zonename.match"), // \\s*zone name ([-\\w]+) vsan (\\d+).*
                MDSDialogProperties.getString("MDSDialog.showZoneset.pwwn.match"), // \\s*pwwn ([0-9a-fA-F:]+)\\s*(\\[\\S+\\])?\\s*(\\w*)
                                                                                   // ex: pwwn 11:11:12:12:13:13:14:14 [alias] init|target
                MDSDialogProperties.getString("MDSDialog.showZoneset.deviceAlias.match") // \\s*device-alias \\s*(\\S+)\\s*
        };
        String[] groups = new String[10];

        Map<String, String> aliasDatabase = showDeviceAliasDatabase();

        for (String line : lines) {        	
            int index = match(line, regex, groups);
            switch (index) {
                case 0:
                    zoneset = new Zoneset(groups[0]);

                    if (!vsanId.toString().equals(groups[1])) {
                        String message = "VSAN " + vsanId.toString() + " not the expected VSAN " + groups[1];
                        throw NetworkDeviceControllerException.exceptions.mdsUnexpectedDeviceState(message);
                    }
                    zonesets.add(zoneset);
                    break;
                case 1:
                    // if zoneName is not specified, we want all zones in zoneset.
                    // Or, if it zoneName is specified, then only return zone matched the name. Otherwise, ignore it.
                    if (StringUtils.isEmpty(zoneName) || StringUtils.equals(groups[0], zoneName)) {
                        zone = new Zone(groups[0]);
                        zoneset.getZones().add(zone);
                    } else {
                        zone = null;  //
                    }
                    break;
                case 2:
                case 3:
                    // if zone is to be ignored, or members are excluded, break out
                    if (zone == null || excludeMembers) {
                        break;
                    }

                    member = new ZoneMember(ZoneMember.ConnectivityMemberType.WWPN);
                    zone.getMembers().add(member);

                    if (excludeAliases) {
                        _log.info("Excluding aliases while getting zone members");
                    }
                    if (index == 2) {
                        member.setAddress(groups[0]);  // set wwn id

                        // matched "pwwn <wwnid> [alias]" regex, thus
                        // set alias field as well
                        if (!excludeAliases && groups.length >= 2 && groups[1] != null) {
                            member.setAlias(groups[1].replace("[", "").replace("]", ""));
                        }
                    } else if (index == 3) {
                        // matched "device-alias <alias>
                        if (!excludeAliases) {
                            member.setAlias(groups[0]); // set alias
                            member.setAliasType(true); // indicate member type of alias
                    	}
                        String pwwn = getDeviceAliasPwwn(groups[0], aliasDatabase);
                        if (!StringUtils.isEmpty(pwwn)) {
                            member.setAddress(pwwn);
                        }
                    }
                    break;
            }
        }
        if (zonesets.isEmpty()) {
            return zonesets;
        }

        // Now find the active zoneset
        sendWaitFor(MDSDialogProperties.getString("MDSDialog.showZoneset.showzonesetactivevsan.cmd") // show zoneset active vsan
                + vsanId.toString() + "\n", defaultTimeout, prompts, buf);
        lines = getLines(buf);
        String[] regex2 = {
                MDSDialogProperties.getString("MDSDialog.showZoneset.zonesetname2.match"), // \\s*zoneset name ([-\\w]+) vsan (\\d+).*
                MDSDialogProperties.getString("MDSDialog.showZoneset.zonename2.match") // \\s*zone name ([-\\w]+) vsan (\\d+).*
        };

        Zoneset activeZoneset = null;
        for (String line : lines) {       	
            int index = match(line, regex2, groups);
            switch (index) {
                case 0:     // found the active one
                    String activeName = groups[0];
                    for (Zoneset zs : zonesets) {
                        if (zs.getName().equals(activeName)) {
                            activeZoneset = zs;
                            zs.setActive(true);
                        } else {
                            zs.setActive(false);
                        }
                    }
                    break;
                case 1:
                    if (zoneset != null) {		// if there is an active zoneset
                        for (Zone zo : activeZoneset.getZones()) {
                            if (zo.getName().equals(groups[0])) {
                                zo.setActive(true);
                            }
                        }
                    }
            }
        }

        // if only want active zone set, return only active one if found
        if (activeZonesetOnly) {
            zonesets.clear();

            if (activeZoneset != null) {
                zonesets.add(activeZoneset);
            }
        }

        return zonesets;
    }

    /**
     * Collect the Zonesets, and their Zones, members for a specified Vsan ID.
     * 
     * @param vsanId -- Integer vsanId
     * @return a list of Zones
     */
    public List<Zone> showFabricZones(Integer vsanId) throws NetworkDeviceControllerException {
        List<Zone> zones = new ArrayList<Zone>();
        SSHPrompt[] prompts = { SSHPrompt.POUND, SSHPrompt.GREATER_THAN };
        StringBuilder buf = new StringBuilder();
        sendWaitFor(MDSDialogProperties.getString("MDSDialog.showFabricZones.cmd")
                + vsanId.toString() + "\n", defaultTimeout, prompts, buf);
        String[] lines = getLines(buf);
        Zone zone = null;
        String[] regex = {
                MDSDialogProperties.getString("MDSDialog.showZoneset.zonename.match"),
                MDSDialogProperties.getString("MDSDialog.showZoneset.pwwn.match"),
                MDSDialogProperties.getString("MDSDialog.showZoneset.deviceAlias.match")
        };

        Map<String, String> aliasDatabase = showDeviceAliasDatabase();

        String[] groups = new String[10];
        for (String line : lines) {
            ZoneMember member = null;
            int index = match(line, regex, groups);
            switch (index) {
                case 0:
                    if (!vsanId.toString().equals(groups[1])) {
                        String message = "VSAN " + vsanId.toString() + " not the expected VSAN " + groups[1];
                        throw NetworkDeviceControllerException.exceptions.mdsUnexpectedDeviceState(message);
                    }
                    zone = new Zone(groups[0]);
                    zones.add(zone);
                    break;
                case 1:
                case 2:
                    member = new ZoneMember(ZoneMember.ConnectivityMemberType.WWPN);
                    zone.getMembers().add(member);

                    if (index == 1) {
                        member.setAddress(groups[0]);

                        // matched "pwwn <wwnid> [alias]" regex, thus
                        // set alias field as well
                        if (groups.length >= 2 && groups[1] != null) {
                            member.setAlias(groups[1].replace("[", "").replace("]", ""));
                        }
                    } else if (index == 2) {
                        // match "device-alas <alias>" regex
                        member.setAlias(groups[0]);  // device alias
                        member.setAliasType(true);// indicate member is an alias type

                        String pwwn = getDeviceAliasPwwn(groups[0], aliasDatabase);
                        if (!StringUtils.isEmpty(pwwn)) {
                            member.setAddress(pwwn);
                        }
                    }
                    break;
            }
        }
        return zones;
    }

    /**
     * Get corresponded pwwn for the given device alias
     * 
     * @param deviceAlias
     * @param aliasDatabase - device alias database. If null, get it from switch
     * @return pwwn
     */
    public String getDeviceAliasPwwn(String deviceAlias, Map<String, String> aliasDatabase) {
        Map<String, String> myAliasDatabase = aliasDatabase;
        if (myAliasDatabase == null) {
            myAliasDatabase = showDeviceAliasDatabase();
        }
        return StringUtils.isEmpty(deviceAlias) ? null : myAliasDatabase.get(deviceAlias);
    }

    /**
     * Query device aliases via "show device-alias database" cli
     * 
     * @return
     * @throws NetworkDeviceControllerException
     */
    public Map<String, String> showDeviceAliasDatabase() throws NetworkDeviceControllerException {
        return showDeviceAliasDatabase(false);
    }

    /**
     * Query device aliases via "show device-alias database" cli, and include pending change if <code>includePending</code> is specified
     * 
     * @param includePending true to include pending change
     * @return
     * @throws NetworkDeviceControllerException
     */
    public Map<String, String> showDeviceAliasDatabase(boolean includePending) throws NetworkDeviceControllerException {
        String[] regex = {
                MDSDialogProperties.getString("MDSDialog.deviceAliasName.match") // device-alias name \\s(\\S+)\\s*pwwn \\s*(\\S+)\\s*
        };

        Map<String, String> deviceAliasMap = new HashMap<String, String>();
        SSHPrompt[] prompts = { SSHPrompt.POUND, SSHPrompt.GREATER_THAN };
        StringBuilder buf = new StringBuilder();
        sendWaitFor(MDSDialogProperties.getString("MDSDialog.showDeviceAlias.cmd"), defaultTimeout, prompts, buf); // show device-alias
                                                                                                                   // database\n

        // get pending database as well
        if (includePending) {
            StringBuilder buf2 = new StringBuilder();
            sendWaitFor(MDSDialogProperties.getString("MDSDialog.showDeviceAlias.pending.cmd"), defaultTimeout, prompts, buf2); // show
                                                                                                                                // device-alias
                                                                                                                                // pending\n
            buf.append(buf2.toString());
        }

        String[] groups = new String[10];
        String[] lines = getLines(buf);
        for (String line : lines) {
            int index = match(line, regex, groups);
            switch (index) {
                case 0:
                    // Save the device alias
                    String deviceAlias = groups[0];
                    String pwwn = groups[1];
                    if (deviceAlias != null) {
                        deviceAliasMap.put(deviceAlias, pwwn);
                    }
                    deviceAlias = null;
                    break;
            }
        }
        return deviceAliasMap;

    }

    /**
     * Returns a map of vsan id to Vsan object using "show vsans" and
     * calling show zonesets and show zones.
     * 
     * @param includeZonesets If true, the Vsan structures include zonesets and their descendents.
     * @return a Map of Vsan ID to Vsan structure
     */
    public Map<Integer, Vsan> showVsan(boolean includeZonesets) throws NetworkDeviceControllerException {
        Map<Integer, Vsan> vsans = new HashMap<Integer, Vsan>();
        SSHPrompt[] prompts = { SSHPrompt.POUND, SSHPrompt.GREATER_THAN };
        StringBuilder buf = new StringBuilder();
        sendWaitFor(MDSDialogProperties.getString("MDSDialog.showVsan.cmd"), defaultTimeout, prompts, buf); // show vsan\n
        String[] lines = getLines(buf);
        Vsan vsan = null;
        Integer vsanId = null;
        String[] regex = {
                MDSDialogProperties.getString("MDSDialog.showVsan.vsan.match"), // vsan\\s+(\\d+).*
                MDSDialogProperties.getString("MDSDialog.showVsan.namestate.match") // \\s+name:\\s*(\\w+|\\w[\\w\\s]*\\w)\\s+state:(\\w+).*
        };
        String[] groups = new String[10];
        for (String line : lines) {
            int index = match(line, regex, groups);
            switch (index) {
                case 0:
                    // Save the vsan id.
                    vsanId = new Integer(groups[0]);
                    break;
                case 1:
                    String vsanName = groups[0];
                    vsan = new Vsan(vsanId.toString(), vsanName);
                    vsans.put(vsanId, vsan);
                    if (includeZonesets) {
                        List<Zoneset> zonesets = showZoneset(vsanId, false, null, false, false);
                        for (Zoneset zs : zonesets) {
                            if (zs.getActive()) {
                                vsan.setActiveZoneset(zs);
                            } else {
                                vsan.getInactiveZonesets().add(zs);
                            }
                        }
                    }
                    vsanId = null;
                    break;
            }
        }
        return vsans;
    }

    /**
     * Put in terminal config mode.
     */
    public void config() throws NetworkDeviceControllerException {
        if (inConfigMode == true) {
            return;
        }
        SSHPrompt[] prompts = { SSHPrompt.MDS_CONFIG };
        StringBuilder buf = new StringBuilder();
        lastPrompt = sendWaitFor(MDSDialogProperties.getString("MDSDialog.config.configterminal.cmd"), defaultTimeout, prompts, buf); // config
                                                                                                                                      // terminal\n
        if (lastPrompt != SSHPrompt.MDS_CONFIG) {
            throw NetworkDeviceControllerException.exceptions.mdsUnexpectedLastPrompt(lastPrompt.toString(),
                    SSHPrompt.MDS_CONFIG.toString());
        }
        inConfigMode = true;
    }

    /**
     * Put in terminal device alias database mode
     */
    public void deviceAliasConfig() throws NetworkDeviceControllerException {
        SSHPrompt[] prompts = { SSHPrompt.MDS_CONFIG_DEVICE_ALIAS };
        StringBuilder buf = new StringBuilder();
        lastPrompt = sendWaitFor(MDSDialogProperties.getString("MDSDialog.config.deviceAlias.cmd"), defaultTimeout, prompts, buf); // config
                                                                                                                                   // terminal\n
        if (lastPrompt != SSHPrompt.MDS_CONFIG_DEVICE_ALIAS) {
            throw NetworkDeviceControllerException.exceptions.mdsUnexpectedLastPrompt(lastPrompt.toString(),
                    SSHPrompt.MDS_CONFIG_DEVICE_ALIAS.toString());
        }
    }

    /**
     * commit device alias configuration
     * 
     * @throws NetworkDeviceControllerException
     */
    public void deviceAliasCommit() throws NetworkDeviceControllerException {
        if (lastPrompt != SSHPrompt.MDS_CONFIG_DEVICE_ALIAS) {
            throw NetworkDeviceControllerException.exceptions.mdsUnexpectedLastPrompt(lastPrompt.toString(),
                    SSHPrompt.MDS_CONFIG_DEVICE_ALIAS.toString());
        }
        SSHPrompt[] prompts = { SSHPrompt.MDS_CONFIG };
        StringBuilder buf = new StringBuilder();
        String payload = MDSDialogProperties.getString("MDSDialog.config.deviceAlias.commit.cmd"); // device-alias commit
        boolean retryNeeded = true;
        for (int retryCount = 0; retryCount < sessionLockRetryMax && retryNeeded; retryCount++) {
            retryNeeded = false;

            lastPrompt = sendWaitFor(payload, defaultTimeout, prompts, buf);
            String[] lines = getLines(buf);
            String waitReason = "";
            for (String line : lines) {
                // retry again if device alias database is locked or busy
                if (line.indexOf(MDSDialogProperties.getString("MDSDialog.deviceAlias.busy")) >= 0 ||
                        line.indexOf(MDSDialogProperties.getString("MDSDialog.deviceAlias.locked")) >= 0) {
                    retryNeeded = true;
                    waitReason = line;
                    break;
                }
            }

            // if retry needed, sleep for a bit (defaultTimeout), and retry again
            if (retryNeeded) {
                if (retryCount + 1 >= sessionLockRetryMax) {
                    // exceed retry, throw exception
                    _log.error("Devias alias database is busy or locked, gave up after " + sessionLockRetryMax + " retries!");
                    throw NetworkDeviceControllerException.exceptions.deviceAliasDatabaseLockedOrBusy(retryCount + 1);
                } else {
                    _log.info("Lock/Busy msg: " + waitReason);
                    _log.info("Devias alias database is busy or locked, will retry after " + defaultTimeout / 1000 + " seconds...");
                    try {
                        Thread.sleep(defaultTimeout);
                    } catch (InterruptedException ex) {
                        _log.warn(ex.getLocalizedMessage());
                    }
                }
            }
        }
    }

    /**
     * abort device alias configuration
     * 
     * @throws NetworkDeviceControllerException
     */
    public void deviceAliasAbort() throws NetworkDeviceControllerException {
        if (lastPrompt != SSHPrompt.MDS_CONFIG_DEVICE_ALIAS && lastPrompt != SSHPrompt.MDS_CONFIG) {
            throw NetworkDeviceControllerException.exceptions.mdsUnexpectedLastPrompt(lastPrompt.toString(),
                    SSHPrompt.MDS_CONFIG.toString());
        }
        SSHPrompt[] prompts = { SSHPrompt.MDS_CONFIG };
        StringBuilder buf = new StringBuilder();
        String payload = MDSDialogProperties.getString("MDSDialog.config.deviceAlias.abort.cmd"); // device-alias abort
        lastPrompt = sendWaitFor(payload, defaultTimeout, prompts, buf);
    }

    /**
     * Configure a device alias
     * 
     * @param alias
     * @param pwwn wwn maps to given alias
     * @param remove delete alias if true, other create the alias
     * @throws NetworkDeviceControllerException
     */
    public void deviceAliasName(String alias, String pwwn, boolean remove) throws NetworkDeviceControllerException {
        if (lastPrompt != SSHPrompt.MDS_CONFIG_DEVICE_ALIAS) {
            throw NetworkDeviceControllerException.exceptions.mdsUnexpectedLastPrompt(lastPrompt.toString(),
                    SSHPrompt.MDS_CONFIG_DEVICE_ALIAS.toString());
        }

        String invalidCommand = MDSDialogProperties.getString("MDSDialog.invalidCommand");
        String illegalName = MDSDialogProperties.getString("MDSDialog.deviceAlias.illegal.name");
        String notPresent = MDSDialogProperties.getString("MDSDialog.not.present");
        String alreadyPresent = MDSDialogProperties.getString("MDSDialog.already.present");

        SSHPrompt[] prompts = { SSHPrompt.MDS_CONFIG_DEVICE_ALIAS };
        StringBuilder buf = new StringBuilder();
        String payload = MessageFormat.format(MDSDialogProperties.getString("MDSDialog.config.deviceAliasName.cmd"), alias, pwwn);
        if (remove) {
            payload = MDSDialogProperties.getString("MDSDialog.zoneNameVsan.no.cmd") + " " + payload;
        }
        lastPrompt = sendWaitFor(payload, defaultTimeout, prompts, buf);
        String[] lines = getLines(buf);
        for (String line : lines) {
            // throw exception only when trying to get into config mode, but not found
            if (line.indexOf(invalidCommand) >= 0 ||
                    ((!remove && line.indexOf(illegalName) >= 0) ||
                            line.indexOf(notPresent) >= 0 ||
                    line.indexOf(alreadyPresent) >= 0)) {
                throw new NetworkDeviceControllerException(line + ": " + alias);
            }
        }
    }

    /**
     * Rename the current alias to new alias
     * 
     * @param currentAlias
     * @param newAlias
     * @throws NetworkDeviceControllerException
     */
    public void deviceAliasRename(String currentAlias, String newAlias) throws NetworkDeviceControllerException {
        if (lastPrompt != SSHPrompt.MDS_CONFIG_DEVICE_ALIAS) {
            throw NetworkDeviceControllerException.exceptions.mdsUnexpectedLastPrompt(lastPrompt.toString(),
                    SSHPrompt.MDS_CONFIG_DEVICE_ALIAS.toString());
        }

        String invalidCommand = MDSDialogProperties.getString("MDSDialog.invalidCommand");
        String illegalName = MDSDialogProperties.getString("MDSDialog.deviceAlias.illegal.name");
        String notPresent = MDSDialogProperties.getString("MDSDialog.not.present");
        String alreadyPresent = MDSDialogProperties.getString("MDSDialog.already.present");
        String alreadyInUse = MDSDialogProperties.getString("MDSDialog.already.in.use");
        String notExists = MDSDialogProperties.getString("MDSDialog.not.existing");

        SSHPrompt[] prompts = { SSHPrompt.MDS_CONFIG_DEVICE_ALIAS };
        StringBuilder buf = new StringBuilder();
        String payload = MessageFormat.format(MDSDialogProperties.getString("MDSDialog.config.deviceAliasRename.cmd"), currentAlias,
                newAlias);
        lastPrompt = sendWaitFor(payload, defaultTimeout, prompts, buf);
        String[] lines = getLines(buf);
        for (String line : lines) {
            // throw exception only when trying to get into config mode, but not found
            if (line.indexOf(invalidCommand) >= 0 ||
                    line.indexOf(illegalName) >= 0 ||
                    line.indexOf(notPresent) >= 0 ||
                    line.indexOf(alreadyInUse) >= 0 ||
                    line.indexOf(notExists) >= 0 ||
                    line.indexOf(alreadyPresent) >= 0) {
                throw new NetworkDeviceControllerException(line + " - " + "Failed to rename alias: " + currentAlias + " / " + newAlias);
            }
        }
    }

    /**
     * End config mode.
     */
    public void endConfig() throws NetworkDeviceControllerException {
        if (inConfigMode == false) {
            return;
        }
        SSHPrompt[] prompts = { SSHPrompt.MDS_POUND, SSHPrompt.MDS_GREATER_THAN };
        StringBuilder buf = new StringBuilder();
        lastPrompt = sendWaitFor(MDSDialogProperties.getString("MDSDialog.endConfig.end.cmd"), defaultTimeout, prompts, buf); // end\n
        inConfigMode = false;
    }

    /**
     * Exits from an inner config mode to config mode.
     */
    public void exitToConfig() throws NetworkDeviceControllerException {
        if (!inConfigMode) {
            throw NetworkDeviceControllerException.exceptions.mdsDeviceNotInConfigMode();
        }
        SSHPrompt[] prompts = { SSHPrompt.MDS_CONFIG };
        StringBuilder buf = new StringBuilder();
        lastPrompt = sendWaitFor(MDSDialogProperties.getString("MDSDialog.exitToConfig.exit.cmd"), defaultTimeout, prompts, buf); // exit\n
    }

    // /**
    // * Returns a boolean indicating if enhanced zoning is enabled.
    // * @param vsanId
    // * @return
    // * @throws NetworkControllerException
    // */
    // public boolean isEnhancedZoningEnabled(Integer vsanId) throws NetworkControllerException {
    // assert(inConfigMode == false);
    // SSHPrompt[] prompts = { SSHPrompt.MDS_POUND };
    // StringBuilder buf = new StringBuilder();
    // String payload = MessageFormat.format("show zone status vsan {0}\n", vsanId.toString());
    // lastPrompt = sendWaitFor(payload, defaultTimeout, prompts, buf);
    // String[] lines = getLines(buf);
    // String[] regex = {
    // "\\s*mode:\\s+(\\w+).*"
    // };
    // String[] groups = new String[10];
    // boolean enhanced = false;
    // for (String line : lines) {
    // int index = match(line, regex, groups);
    // switch(index) {
    // case 0:
    // if (groups[0].equals("enhanced")) {
    // enhanced = true;
    // }
    // }
    // }
    // return enhanced;
    // }

    /**
     * Returns a boolean indicating if enhanced zoning is enabled.
     * Also logs a message if enhanced zoning is not enabled.
     * 
     * @param vsanId
     * @return
     * @throws NetworkDeviceControllerException
     */
    public boolean isSessionInProgress(Integer vsanId) throws NetworkDeviceControllerException {
        if (inConfigMode) {
            throw NetworkDeviceControllerException.exceptions.mdsDeviceInConfigMode();
        }
        SSHPrompt[] prompts = { SSHPrompt.MDS_POUND };
        StringBuilder buf = new StringBuilder();
        String payload = MessageFormat.format(MDSDialogProperties.getString("MDSDialog.isSessionInProgress.showzonestatus.cmd"),
                vsanId.toString()); // show zone status vsan {0}\n
        lastPrompt = sendWaitFor(payload, defaultTimeout, prompts, buf);
        String[] lines = getLines(buf);
        String[] regex = {
                MDSDialogProperties.getString("MDSDialog.isSessionInProgress.session.match"), // \\s*session:\\s+(\\w+).*
                MDSDialogProperties.getString("MDSDialog.isSessionInProgress.mode.match") // \\s*mode:\\s+(\\w+).*
        };
        String[] groups = new String[10];
        boolean session = false;
        boolean enhanced = false;
        for (String line : lines) {
            int index = match(line, regex, groups);
            switch (index) {
                case 0:
                    if (false == groups[0].equals(MDSDialogProperties.getString("MDSDialog.isSessionInProgress.none"))) { // none
                        session = true;
                    }
                case 1:
                    if (groups[0].equals(MDSDialogProperties.getString("MDSDialog.isSessionInProgress.enhanced"))) { // enhanced
                        enhanced = true;
                    }
            }
        }
        if (!enhanced) {
            _log.warn("Enhanced zoning not enabled: " + vsanId);
        }
        return session;
    }

    /**
     * Scans the lines looking for evidence that an Enhanced zone session was created.
     * 
     * @param lines String[]
     * @param retryCount indicates how many retries have already been tried
     * @return true if a retry is needed because the lock is busy
     * 
     */
    private boolean checkForZoneSession(String[] lines, Integer retryCount, boolean forIvr)
            throws NetworkDeviceControllerException {

        String busyKey = "MDSDialog.checkForEnhancedZoneSession.busy";
        String createdKey = "MDSDialog.checkForEnhancedZoneSession.created";
        String pendingKey = "MDSDialog.checkForEnhancedZoneSession.pending";
        
        if (forIvr) {
            busyKey = "MDSDialog.ivr.checkForEnhancedZoneSession.busy";
            createdKey = "MDSDialog.ivr.checkForEnhancedZoneSession.created";
            pendingKey = "MDSDialog.ivr.checkForEnhancedZoneSession.pending";
        }
        
        for (String s : lines) {
        	_log.debug("line : {}", s);
            if (s.startsWith(MDSDialogProperties.getString(createdKey))) { // Enhanced zone session has been created
                inSession = true;
                return false;
            }
            if (s.startsWith(MDSDialogProperties.getString(busyKey))) { // Lock is currently busy
                if ((retryCount + 1) < sessionLockRetryMax) {
                    _log.info("Zone session lock is busy, will retry after " + defaultTimeout / 1000 + " seconds...");
                    try {
                        Thread.sleep(defaultTimeout);
                    } catch (InterruptedException ex) {
                        _log.warn(ex.getLocalizedMessage());
                    }
                    return true;
                }
                _log.error("Zone session lock is busy, gave up after " + sessionLockRetryMax + " retries!");
                throw NetworkDeviceControllerException.exceptions.zoneSessionLocked(retryCount + 1);
            }
            
            if (s.contains(MDSDialogProperties.getString(pendingKey))) {            	
            	   if ((retryCount + 1) < sessionLockRetryMax) {
                       _log.info("There is a pending session, will retry after " + defaultTimeout / 1000 + " seconds...");
                       try {
                           Thread.sleep(defaultTimeout);
                       } catch (InterruptedException ex) {
                           _log.warn(ex.getLocalizedMessage());
                       }
                       return true;
                   }
                   _log.error("There is a pending session still, gave up after " + sessionLockRetryMax + " retries!");
                   throw NetworkDeviceControllerException.exceptions.timeoutWaitingOnPendingActions();
            }
        }
        return false;
    }

    /**
     * Scans the lines looking for evidence that an Enhanced zone session was created.
     * 
     * @param lines String[]
     * @param retryCount indicates how many retries have already been tried
     * @return true if a retry is needed because the lock is busy
     */
    private boolean checkForEnhancedZoneSession(String[] lines, Integer retryCount)
            throws NetworkDeviceControllerException {
        return checkForZoneSession(lines, retryCount, false);
    }

    /**
     * Scans the lines looking for evidence that ivr zone in session
     * 
     * @param lines String[]
     * @param retryCount indicates how many retries have already been tried
     * @return true if a retry is needed because the lock is busy
     */
    private boolean checkForIvrZoneSession(String[] lines, Integer retryCount)
            throws NetworkDeviceControllerException {
        return checkForZoneSession(lines, retryCount, true);
    }

    /**
     * zone name {zoneName} vsan {vsanId}
     * no zone name {zoneName} vsan {vsanId}
     * 
     * @param zoneName
     * @param vsanId
     * @param no -- makes no version of command
     * @throws NetworkDeviceControllerException
     */
    public void zoneNameVsan(String zoneName, Integer vsanId, boolean no) throws NetworkDeviceControllerException {
        if (!inConfigMode) {
            throw NetworkDeviceControllerException.exceptions.mdsDeviceNotInConfigMode();
        }
        if (lastPrompt != SSHPrompt.MDS_CONFIG) {
            throw NetworkDeviceControllerException.exceptions.mdsUnexpectedLastPrompt(lastPrompt.toString(),
                    SSHPrompt.MDS_CONFIG.toString());
        }
        String noString = no ? MDSDialogProperties.getString("MDSDialog.zoneNameVsan.no.cmd") : ""; // no
        SSHPrompt[] prompts = { SSHPrompt.MDS_CONFIG_ZONE, SSHPrompt.MDS_CONFIG };
        SSHPrompt[] noPrompts = { SSHPrompt.MDS_CONFIG };
        StringBuilder buf = new StringBuilder();
        boolean retryNeeded = true;
        for (int retryCount = 0; retryCount < sessionLockRetryMax && retryNeeded; retryCount++) {
            String payload = MessageFormat.format(MDSDialogProperties.getString("MDSDialog.zoneNameVsan.cmd"), zoneName, vsanId.toString(),
                    noString); // {2}zone name {0} vsan {1}\n
            lastPrompt = sendWaitFor(payload, defaultTimeout, (no ? noPrompts : prompts), buf);
            String[] lines = getLines(buf);
            retryNeeded = checkForEnhancedZoneSession(lines, retryCount);
        }
        if ((no == false) && (lastPrompt != SSHPrompt.MDS_CONFIG_ZONE)) {
            throw NetworkDeviceControllerException.exceptions.mdsUnexpectedLastPrompt(lastPrompt.toString(),
                    SSHPrompt.MDS_CONFIG_ZONE.toString());
        }
    }

    /**
     * member pwwn {pwwn}
     * 
     * @param pwwn
     * @throws NetworkDeviceControllerException
     */
    public void zoneMemberPwwn(String pwwn) throws NetworkDeviceControllerException {
        zoneMemberPwwn(pwwn, false); // add zone
    }

    /**
     * member pwwn {pwwn}
     * 
     * @param pwwn
     * @param remove - delete member pwwn from zone
     * @throws NetworkDeviceControllerException
     */
    public void zoneMemberPwwn(String pwwn, boolean remove) throws NetworkDeviceControllerException {
        zoneAddRemoveMember(pwwn, false, remove);
    }

    /**
     * member alias {alias}
     * 
     * @param alias
     * @throws NetworkDeviceControllerException
     */
    public void zoneMemberAlias(String alias) throws NetworkDeviceControllerException {
        zoneAddRemoveMember(alias, true, false);
    }

    /**
     * member alias {alias}
     * 
     * @param alias
     * @param remove delete member alias from zone
     * @throws NetworkDeviceControllerException
     */
    public void zoneMemberAlias(String alias, boolean remove) throws NetworkDeviceControllerException {
        zoneAddRemoveMember(alias, true, remove);
    }

    /**
     * Add zone "member device-alias {alias}" if useAlias is set to true, other
     * uses "member pwwn {pwwn}"
     * 
     * @param address
     * @param useAlias
     * @throws NetworkDeviceControllerException
     */
    private void zoneAddRemoveMember(String address, boolean useAlias, boolean remove) throws NetworkDeviceControllerException {
        String mdsCommand = "";

        if (!inConfigMode) {
            throw NetworkDeviceControllerException.exceptions.mdsDeviceNotInConfigMode();
        }
        if (lastPrompt != SSHPrompt.MDS_CONFIG_ZONE) {
            throw NetworkDeviceControllerException.exceptions.mdsUnexpectedLastPrompt(lastPrompt.toString(),
                    SSHPrompt.MDS_CONFIG_ZONE.toString());
        }

        if (useAlias) { // add memeber via device-alias
            mdsCommand = MDSDialogProperties.getString("MDSDialog.zoneMemberAlias.cmd");
        } else { // add member via pwwn
            if (!address.matches(wwnRegex)) {
                String message = "port wwn " + address + " is not formatted correctly";
                throw NetworkDeviceControllerException.exceptions.mdsUnexpectedDeviceState(message);
            }
            mdsCommand = MDSDialogProperties.getString("MDSDialog.zoneMemberPwwn.cmd");
        }

        if (remove) {
            mdsCommand = MDSDialogProperties.getString("MDSDialog.zonesetActivate.no.cmd") + " " + mdsCommand;
        }

        SSHPrompt[] prompts = { SSHPrompt.MDS_CONFIG_ZONE };
        StringBuilder buf = new StringBuilder();
        boolean retryNeeded = true;
        for (int retryCount = 0; retryCount < sessionLockRetryMax && retryNeeded; retryCount++) {
            String payload = MessageFormat.format(mdsCommand, address); // =member pwwn {0}\n
            lastPrompt = sendWaitFor(payload, defaultTimeout, prompts, buf);
            String[] lines = getLines(buf);
            for (String line : lines) {
                // add mode - throw exception if alias is not found
                if (!remove) {
                    if (line.indexOf(MDSDialogProperties.getString("MDSDialog.not.present")) >= 0) {
                        throw new NetworkDeviceControllerException(line + ": " + address);
                    }
                }
            }
            retryNeeded = checkForEnhancedZoneSession(lines, retryCount);
        }
    }

    /**
     * zoneset name {zonesetName} vsan {vsanId}
     * 
     * @param zonesetName
     * @param vsanId
     * @throws NetworkDeviceControllerException
     */
    public void zonesetNameVsan(String zonesetName, Integer vsanId) throws NetworkDeviceControllerException {
        if (!inConfigMode) {
            throw NetworkDeviceControllerException.exceptions.mdsDeviceNotInConfigMode();
        }
        if (lastPrompt != SSHPrompt.MDS_CONFIG) {
            throw NetworkDeviceControllerException.exceptions.mdsUnexpectedLastPrompt(lastPrompt.toString(),
                    SSHPrompt.MDS_CONFIG.toString());
        }
        SSHPrompt[] prompts = { SSHPrompt.MDS_CONFIG_ZONESET, SSHPrompt.MDS_CONFIG };
        StringBuilder buf = new StringBuilder();
        boolean retryNeeded = true;
        for (int retryCount = 0; retryCount < sessionLockRetryMax && retryNeeded; retryCount++) {
            String payload = MessageFormat.format(MDSDialogProperties.getString("MDSDialog.zonesetNameVsan.cmd"), zonesetName,
                    vsanId.toString()); // zoneset name {0} vsan {1}\n
            lastPrompt = sendWaitFor(payload, defaultTimeout, prompts, buf);
            String[] lines = getLines(buf);
            retryNeeded = checkForEnhancedZoneSession(lines, retryCount);
        }
        if (lastPrompt != SSHPrompt.MDS_CONFIG_ZONESET) {
            throw NetworkDeviceControllerException.exceptions.mdsUnexpectedLastPrompt(lastPrompt.toString(),
                    SSHPrompt.MDS_CONFIG_ZONESET.toString());
        }
    }

    /**
     * Makes a zoneset member.
     * member {zoneName}
     * 
     * @param zoneName
     * @throws NetworkDeviceControllerException
     */
    public void zonesetMember(String zoneName) throws NetworkDeviceControllerException {
        if (!inConfigMode) {
            throw NetworkDeviceControllerException.exceptions.mdsDeviceNotInConfigMode();
        }
        if (lastPrompt != SSHPrompt.MDS_CONFIG_ZONESET) {
            throw NetworkDeviceControllerException.exceptions.mdsUnexpectedLastPrompt(lastPrompt.toString(),
                    SSHPrompt.MDS_CONFIG_ZONESET.toString());
        }
        SSHPrompt[] prompts = { SSHPrompt.MDS_CONFIG_ZONESET };
        StringBuilder buf = new StringBuilder();
        boolean retryNeeded = true;
        for (int retryCount = 0; retryCount < sessionLockRetryMax && retryNeeded; retryCount++) {
            String payload = MessageFormat.format(MDSDialogProperties.getString("MDSDialog.zonesetMember.member.cmd"), zoneName); // member
                                                                                                                                  // {0}\n
            lastPrompt = sendWaitFor(payload, defaultTimeout, prompts, buf);
            String[] lines = getLines(buf);
            retryNeeded = checkForEnhancedZoneSession(lines, retryCount);
        }
    }

    /**
     * zoneset activate name {zonesetName} vsan {vsanId}
     * no zoneset activate name {zonesetName} vsan {vsanId}
     * 
     * @param zonesetName
     * @param vsanId
     * @param no (boolean) if true, issues "no" form of this command
     * @throws NetworkDeviceControllerException
     */
    public void zonesetActivate(String zonesetName, Integer vsanId, boolean no) throws NetworkDeviceControllerException {
        if (!inConfigMode) {
            throw NetworkDeviceControllerException.exceptions.mdsDeviceNotInConfigMode();
        }
        if (lastPrompt != SSHPrompt.MDS_CONFIG) {
            throw NetworkDeviceControllerException.exceptions.mdsUnexpectedLastPrompt(lastPrompt.toString(),
                    SSHPrompt.MDS_CONFIG.toString());
        }
        String noString = no ? MDSDialogProperties.getString("MDSDialog.zonesetActivate.no.cmd") : ""; // no
        SSHPrompt[] prompts = { SSHPrompt.MDS_CONFIG, SSHPrompt.MDS_CONTINUE_QUERY };
        StringBuilder buf = new StringBuilder();
        boolean retryNeeded = true;
        for (int retryCount = 0; retryCount < sessionLockRetryMax && retryNeeded; retryCount++) {
            String payload = MessageFormat.format(MDSDialogProperties.getString("MDSDialog.zonesetActivate.cmd"), zonesetName,
                    vsanId.toString(), noString); // {2}zoneset activate name {0} vsan {1}\n
            lastPrompt = sendWaitFor(payload, defaultTimeout, prompts, buf);

            // error, throw exception. Otherwise check for enhance zone session
            if (buf.toString().indexOf(MDSDialogProperties.getString("MDSDialog.zonesetActivate.no.zone.members")) >= 0) {
                throw new NetworkDeviceControllerException("Activate zoneset/vsan: " + zonesetName + "/" + vsanId
                        + " failed.  One or more zone do not have members");
            } else {

                // check for user input
                if (lastPrompt == SSHPrompt.MDS_CONTINUE_QUERY) {
                    payload = MDSDialogProperties.getString("MDSDialog.zonesetActivate.continue.y.cmd");  // y\n
                    SSHPrompt[] prompts2 = { SSHPrompt.MDS_CONFIG };
                    buf = new StringBuilder();
                    lastPrompt = sendWaitFor(payload, defaultTimeout, prompts2, buf);
                }

                String[] lines = getLines(buf);
                retryNeeded = checkForEnhancedZoneSession(lines, retryCount);
            }
        }
    }

    /**
     * zone commit vsan {vsanId}
     * (commits the session)
     * 
     * @param vsanId
     * @throws NetworkDeviceControllerException
     */
    public void zoneCommit(Integer vsanId) throws NetworkDeviceControllerException {
        if (!inConfigMode) {
            throw NetworkDeviceControllerException.exceptions.mdsDeviceNotInConfigMode();
        }
        if (lastPrompt != SSHPrompt.MDS_CONFIG) {
            throw NetworkDeviceControllerException.exceptions.mdsUnexpectedLastPrompt(lastPrompt.toString(),
                    SSHPrompt.MDS_CONFIG.toString());
        }
        SSHPrompt[] prompts = { SSHPrompt.MDS_CONFIG };
        StringBuilder buf = new StringBuilder();
        String payload = MessageFormat.format(MDSDialogProperties.getString("MDSDialog.zoneCommit.zonecommitvsan.cmd"), vsanId.toString()); // zone
                                                                                                                                            // commit
                                                                                                                                            // vsan
                                                                                                                                            // {0}\n
        lastPrompt = sendWaitFor(payload, defaultTimeout, prompts, buf);
        inSession = false;
    }

    /**
     * no zone commit vsan {vsanId}
     * (aborts the session commit)
     * 
     * @param vsanId
     * @throws NetworkDeviceControllerException
     */
    public void noZoneCommit(Integer vsanId) throws NetworkDeviceControllerException {
        if (!inConfigMode) {
            throw NetworkDeviceControllerException.exceptions.mdsDeviceNotInConfigMode();
        }
        if (lastPrompt != SSHPrompt.MDS_CONFIG) {
            throw NetworkDeviceControllerException.exceptions.mdsUnexpectedLastPrompt(lastPrompt.toString(),
                    SSHPrompt.MDS_CONFIG.toString());
        }
        SSHPrompt[] prompts = { SSHPrompt.MDS_CONFIG };
        StringBuilder buf = new StringBuilder();
        String payload = MessageFormat.format(MDSDialogProperties.getString("MDSDialog.noZoneCommit.nozonecommitvsan.cmd"),
                vsanId.toString()); // no zone commit vsan {0}\n
        lastPrompt = sendWaitFor(payload, defaultTimeout, prompts, buf);
        inSession = false;
    }

    /**
     * Waits for a zone commit to complete. Checks for in-progress and failed status.
     * 
     * @param vsanId
     * @throws NetworkDeviceControllerException
     */
    public void waitForZoneCommit(Integer vsanId) throws NetworkDeviceControllerException {
        _log.info(MessageFormat.format("Host: {0}, Port: {1} -BEGIN waitForZoneCommit",
                new Object[] { getSession().getSession().getHost(), getSession().getSession().getPort() }));

        SSHPrompt[] prompts = { SSHPrompt.MDS_POUND, SSHPrompt.MDS_CONFIG };
        StringBuilder buf = new StringBuilder();
        String payload = MessageFormat.format(MDSDialogProperties.getString("MDSDialog.waitForZoneCommit.showzonestatusvsan.cmd"),
                vsanId.toString()); // show zone status vsan {0}\n
        String[] regex = {
                MDSDialogProperties.getString("MDSDialog.waitForZoneCommit.inprogress.match"), // ^Status:\\s+Commit in progress.*
                MDSDialogProperties.getString("MDSDialog.waitForZoneCommit.complete.match"), // ^Status:\\s+Commit complete.*
                MDSDialogProperties.getString("MDSDialog.waitForZoneCommit.failed.match") // ^Status:\\s+Operation failed.*
        };
        String[] groups = new String[2];

        /*
         * compute retry attempts based on the configured timeout.
         * will retry in every SLEEP_TIME_PER_RETRY until exceeded the timeout value
         * Add one more attempt to ensure timeout value is reached
         */
        int retryAttempts = defaultTimeout / MDSDialogProperties.SLEEP_TIME_PER_RETRY + 1;

        boolean completed = false;
        for (int retrys = 0; !completed && retrys < retryAttempts; retrys++) {
            try {
                Thread.sleep(MDSDialogProperties.SLEEP_TIME_PER_RETRY);
            } catch (InterruptedException ex) {
                _log.warn(ex.getLocalizedMessage());
            }
            lastPrompt = sendWaitFor(payload, defaultTimeout, prompts, buf);
            String[] lines = getLines(buf);
            for (String line : lines) {
                int index = match(line, regex, groups);
                switch (index) {
                    case 0:
                        completed = false;
                        break;
                    case 1:
                        completed = true;
                        break;
                    case 2:
                        throw new NetworkDeviceControllerException("Zone Commit failed: " + line);
                }

            }
        }

        _log.info(MessageFormat.format("Host: {0}, Port: {1} -END waitForZoneCommit",
                new Object[] { getSession().getSession().getHost(), getSession().getSession().getPort() }));
    }

    /**
     * Does a local copy running-config to startup-config.
     * 
     * @throws NetworkDeviceControllerException
     */
    public void copyRunningConfigToStartup() throws NetworkDeviceControllerException {
        _log.info(MessageFormat.format("Host: {0}, Port: {1} -BEGIN copyRunningConfigToStartup",
                new Object[] { getSession().getSession().getHost(), getSession().getSession().getPort() }));

        if (!inConfigMode) {
            throw NetworkDeviceControllerException.exceptions.mdsDeviceNotInConfigMode();
        }
        if (lastPrompt != SSHPrompt.MDS_CONFIG) {
            throw NetworkDeviceControllerException.exceptions.mdsUnexpectedLastPrompt(lastPrompt.toString(),
                    SSHPrompt.MDS_CONFIG.toString());
        }
        SSHPrompt[] prompts = { SSHPrompt.MDS_CONFIG };
        StringBuilder buf = new StringBuilder();
        String payload = MDSDialogProperties.getString("MDSDialog.copyRunningConfigToStartup.cmd"); // copy running-config startup-config\n
        lastPrompt = sendWaitFor(payload, defaultTimeout, prompts, buf);
        String[] lines = getLines(buf);

        _log.info(MessageFormat.format("Host: {0}, Port: {1} -END copyRunningConfigToStartup",
                new Object[] { getSession().getSession().getHost(), getSession().getSession().getPort() }));

    }

    /**
     * Does a fabric wide copy running-configuration to startup-configuration.
     * If for some reason this fails, then tries a local copy running-config startup-configuration as a fallback.
     * 
     * @throws NetworkDeviceControllerException
     */
    public void copyRunningConfigToStartupFabric() throws NetworkDeviceControllerException {
        _log.info(MessageFormat.format("Host: {0}, Port: {1} -BEGIN copyRunningConfigToStartupFabric",
                new Object[] { getSession().getSession().getHost(), getSession().getSession().getPort() }));

        if (!inConfigMode) {
            throw NetworkDeviceControllerException.exceptions.mdsDeviceNotInConfigMode();
        }
        if (lastPrompt != SSHPrompt.MDS_CONFIG) {
            throw NetworkDeviceControllerException.exceptions.mdsUnexpectedLastPrompt(lastPrompt.toString(),
                    SSHPrompt.MDS_CONFIG.toString());
        }
        SSHPrompt[] prompts = { SSHPrompt.MDS_CONFIG, SSHPrompt.MDS_CONTINUE_QUERY };
        StringBuilder buf = new StringBuilder();
        String payload = MDSDialogProperties.getString("MDSDialog.copyRunningConfigToStartupFabric.cmd"); // copy running-config
                                                                                                          // startup-cnofig fabric\n
        lastPrompt = sendWaitFor(payload, defaultTimeout, prompts, buf);
        if (lastPrompt == SSHPrompt.MDS_CONTINUE_QUERY) {
            payload = MDSDialogProperties.getString("MDSDialog.copyRunningConfigToStartupFabric.y.cmd");  // y\n
            SSHPrompt[] prompts2 = { SSHPrompt.MDS_CONFIG };
            buf = new StringBuilder();
            lastPrompt = sendWaitFor(payload, defaultTimeout, prompts2, buf);
            String[] lines = getLines(buf);
            String[] regex = {
                    MDSDialogProperties.getString("MDSDialog.copyRunningConfigToStartupFabric.100Percent.match"),  // .*100%.*
            };
            String[] groups = new String[2];
            boolean done = false;
            for (String line : lines) {
                int index = match(line, regex, groups);
                switch (index) {
                    case 0:				// .*100%.*
                        done = true;
                        break;
                }
            }
            if (!done) {
                _log.error("Copy running-config to startup-config fabric did not complete... trying non-fabric version");
                copyRunningConfigToStartup();
            }
        }

        _log.info(MessageFormat.format("Host: {0}, Port: {1} -END copyRunningConfigToStartupFabric",
                new Object[] { getSession().getSession().getHost(), getSession().getSession().getPort() }));
    }

    public boolean isInConfigMode() {
        return inConfigMode;
    }

    public boolean isInSession() {
        return inSession;
    }

    public SSHPrompt getLastPrompt() {
        return lastPrompt;
    }

    /**
     * Check if auto routing is enabled for the device. It is enabled if ivr, its distributing mode, and
     * auto topology are enabled
     * 
     * @return
     */
    public boolean isIvrEnabled() throws NetworkDeviceControllerException {

        boolean ivrEnabled = false;
        boolean ivrDistributeEnabled = false;
        ;
        boolean ivrVsanTopologyActive = false;

        SSHPrompt[] prompts = { SSHPrompt.MDS_POUND, SSHPrompt.MDS_CONFIG, SSHPrompt.MDS_CONFIG_IVR_ZONE, SSHPrompt.MDS_CONFIG_IVR_ZONESET };
        StringBuilder buf = new StringBuilder();
        sendWaitFor(MDSDialogProperties.getString("MDSDialog.ivr.show.cmd"), 10000, prompts, buf);  // show ivr\n
        String[] lines = getLines(buf);
        for (String line : lines) {
            if (line.indexOf(MDSDialogProperties.getString("MDSDialog.ivr.enabled")) >= 0) {
                ivrEnabled = true;
            } else if (line.indexOf(MDSDialogProperties.getString("MDSDialog.ivr.distribute.enabled")) >= 0) {
                ivrDistributeEnabled = true;
            } else if (line.indexOf(MDSDialogProperties.getString("MDSDialog.ivr.auto.topology.enabled")) >= 0) {
                ivrVsanTopologyActive = true;
            }

            if (ivrEnabled && ivrVsanTopologyActive && ivrDistributeEnabled) {
                break;
            }
        }
        return ivrEnabled && ivrVsanTopologyActive && ivrDistributeEnabled;
    }

    /**
     * ivr zoneset name {zonesetName}
     * 
     * @param zonesetName
     * @param activate make zoneset active
     * @param isRemove remove zoneset
     * @throws NetworkDeviceControllerException
     */
    public void ivrZoneName(String zoneName, boolean isRemove) throws NetworkDeviceControllerException {
        ivrZoneName(false, zoneName, false, isRemove);

    }

    /**
     * Create/remove an ivr zoneset
     * 
     * @param zonesetName
     * @param isActivate
     * @param isRemove
     * @throws NetworkDeviceControllerException
     */
    public void ivrZonesetName(String zonesetName, boolean isActivate, boolean isRemove) throws NetworkDeviceControllerException {
        ivrZoneName(true, zonesetName, isActivate, isRemove);
    }

    /**
     * Configure ivr zone or zone set depended on isZoneset flag.
     * 
     * @param isZoneset indicate to config ivr zoneset, otherwise ivr zone
     * @param zoneName
     * @param isActivate make zoneset active
     * @param isRemove remove zoneset
     * @throws NetworkDeviceControllerException
     */
    private void ivrZoneName(boolean isZoneset, String zoneName, boolean isActivate, boolean isRemove)
            throws NetworkDeviceControllerException {
        _log.info(MessageFormat.format("Host: {0}, Port: {1} -BEGIN Configure {2}: {3} - Remove {4}",
                new Object[] { getSession().getSession().getHost(), getSession().getSession().getPort(), isZoneset ? "zoneset" : "zone",
                        zoneName, isRemove }));

        if (!inConfigMode) {
            throw NetworkDeviceControllerException.exceptions.mdsDeviceNotInConfigMode();
        }
        SSHPrompt[] promptsToCheck = { SSHPrompt.MDS_CONFIG, SSHPrompt.MDS_CONFIG_IVR_ZONE, SSHPrompt.MDS_CONFIG_IVR_ZONESET };
        if (!Arrays.asList(promptsToCheck).contains(lastPrompt)) {
            String message = Arrays.asList(promptsToCheck).toString();
            throw NetworkDeviceControllerException.exceptions.mdsUnexpectedLastPrompt(lastPrompt.toString(), message);
        }

        // cannot be activate and remove at the same time
        if (isActivate && isRemove) {
            String message = "cannot be activate and remove at the same time";
            throw NetworkDeviceControllerException.exceptions.mdsUnexpectedDeviceState(message);
        }

        String ivrZoneNameResourceKey = "MDSDialog.ivr.zoneName.cmd";

        if (isZoneset) {
            // only zoneset can be activate
            ivrZoneNameResourceKey = isActivate ? "MDSDialog.ivr.zonesetName.activate.cmd" : "MDSDialog.ivr.zonesetName.cmd";
        }

        SSHPrompt[] prompts = { isZoneset ? SSHPrompt.MDS_CONFIG_IVR_ZONESET : SSHPrompt.MDS_CONFIG_IVR_ZONE, SSHPrompt.MDS_CONFIG };

        String noString = isRemove ? MDSDialogProperties.getString("MDSDialog.zoneNameVsan.no.cmd") : ""; // no
        StringBuilder buf = new StringBuilder();

        String payload = MessageFormat.format(noString + MDSDialogProperties.getString(ivrZoneNameResourceKey), zoneName);

        boolean retryNeeded = true;
        boolean error = false;
        String errorMessage = MDSDialogProperties.getString("MDSDialog.ivr.waitForZoneset.activate.error.atLeast2Members");
        for (int retryCount = 0; retryCount < sessionLockRetryMax && retryNeeded; retryCount++) {
            lastPrompt = sendWaitFor(payload, defaultTimeout, prompts, buf);
            String[] lines = getLines(buf);
            retryNeeded = checkForIvrZoneSession(lines, retryCount);

            if (isActivate && isZoneset) {
                for (String line : lines) {
                    error = line.indexOf(errorMessage) >= 0;
                    if (error) {
                        break;
                    }
                }
            }
        }

        // verify for appropriate prompt
        if (isZoneset) {
            SSHPrompt[] morePromptsToCheck = { SSHPrompt.MDS_CONFIG_IVR_ZONESET, SSHPrompt.MDS_CONFIG };
            if (!Arrays.asList(morePromptsToCheck).contains(lastPrompt)) {
                String message = Arrays.asList(morePromptsToCheck).toString();
                throw NetworkDeviceControllerException.exceptions.mdsUnexpectedLastPrompt(lastPrompt.toString(), message);
            }
        } else {
            SSHPrompt[] morePromptsToCheck = { SSHPrompt.MDS_CONFIG_IVR_ZONE, SSHPrompt.MDS_CONFIG };
            if (!Arrays.asList(morePromptsToCheck).contains(lastPrompt)) {
                String message = Arrays.asList(morePromptsToCheck).toString();
                throw NetworkDeviceControllerException.exceptions.mdsUnexpectedLastPrompt(lastPrompt.toString(), message);
            }
        }

        if (error) {
            throw new NetworkDeviceControllerException(errorMessage + ": " + zoneName);
        }

        _log.info(MessageFormat.format("Host: {0}, Port: {1} -END Configure {2}: {3} - Remove {4}",
                new Object[] { getSession().getSession().getHost(), getSession().getSession().getPort(), isZoneset ? "zoneset" : "zone",
                        zoneName, isRemove }));
    }

    /**
     * Create an ivr zoneset, or activate an existing ivr zoneset
     * 
     * @param zonesetName
     * @param activate
     * @throws NetworkDeviceControllerException
     */
    public void ivrZonesetName(String zonesetName, boolean activate) throws NetworkDeviceControllerException {
        ivrZoneName(true, zonesetName, activate, false);
    }

    /**
     * member pwwn {pwwn} vsan {vsanId}
     * 
     * @param pwwn
     * @param vsanId
     * @throws NetworkDeviceControllerException
     */
    public void ivrZoneMember(String pwwn, Integer vsanId, boolean isRemove) throws NetworkDeviceControllerException {
        _log.info(MessageFormat.format("Host: {0}, Port: {1} - BEGIN  Add or remove ivrZoneMember: pwwn {2} vsan {3} - Remove {4}",
                new Object[] { getSession().getSession().getHost(), getSession().getSession().getPort(), pwwn, vsanId, isRemove }));

        SSHPrompt[] prompts = { SSHPrompt.MDS_CONFIG_IVR_ZONE };
        if (!inConfigMode) {
            throw NetworkDeviceControllerException.exceptions.mdsDeviceNotInConfigMode();
        }
        if (!Arrays.asList(prompts).contains(lastPrompt)) {
            String message = Arrays.asList(prompts).toString();
            throw NetworkDeviceControllerException.exceptions.mdsUnexpectedLastPrompt(lastPrompt.toString(), message);
        }

        if (!pwwn.matches(wwnRegex)) {
            String message = "port wwn " + pwwn + " is not formatted correctly";
            throw NetworkDeviceControllerException.exceptions.mdsUnexpectedDeviceState(message);
        }
        if (!isIvrVsan(vsanId)) {
            String message = "VSAN " + vsanId.toString() + " is not an IVR VSAN.";
            throw NetworkDeviceControllerException.exceptions.mdsUnexpectedDeviceState(message);
        }

        String noString = isRemove ? MDSDialogProperties.getString("MDSDialog.zoneNameVsan.no.cmd") : ""; // no
        StringBuilder buf = new StringBuilder();
        String payload = MessageFormat.format(noString + MDSDialogProperties.getString("MDSDialog.ivr.zoneMember.cmd"), pwwn,
                vsanId.toString()); // =member pwwn {0} vsan {0}\n

        boolean retryNeeded = true;
        for (int retryCount = 0; retryCount < sessionLockRetryMax && retryNeeded; retryCount++) {
            lastPrompt = sendWaitFor(payload, defaultTimeout, prompts, buf);
            String[] lines = getLines(buf);
            retryNeeded = checkForEnhancedZoneSession(lines, retryCount);
        }

        _log.info(MessageFormat.format("Host: {0}, Port: {1} - END - Add or remove ivrZoneMember: pwwn {2} vsan {3} - Remove {4}",
                new Object[] { getSession().getSession().getHost(), getSession().getSession().getPort(), pwwn, vsanId, isRemove }));
    }

    /**
     * member {ivrZoneName}
     * 
     * @param pwwn
     * @param vsanId
     * @throws NetworkDeviceControllerException
     */
    public void ivrZonesetMember(String ivrZonename, boolean isRemove) throws NetworkDeviceControllerException {
        _log.info(MessageFormat.format("Host: {0}, Port: {1} - Add or remove ivrZonesetMember: {2} - Remove {3}",
                new Object[] { getSession().getSession().getHost(), getSession().getSession().getPort(), ivrZonename, isRemove }));

        SSHPrompt[] prompts = { SSHPrompt.MDS_CONFIG_IVR_ZONESET };
        if (!inConfigMode) {
            throw NetworkDeviceControllerException.exceptions.mdsDeviceNotInConfigMode();
        }
        if (!Arrays.asList(prompts).contains(lastPrompt)) {
            String message = Arrays.asList(prompts).toString();
            throw NetworkDeviceControllerException.exceptions.mdsUnexpectedLastPrompt(lastPrompt.toString(), message);
        }

        String noString = isRemove ? MDSDialogProperties.getString("MDSDialog.zoneNameVsan.no.cmd") : ""; // no
        StringBuilder buf = new StringBuilder();
        String payload = MessageFormat.format(noString + MDSDialogProperties.getString("MDSDialog.ivr.zonesetMember.cmd"), ivrZonename); // =member
                                                                                                                                         // {zonename}

        boolean retryNeeded = true;
        for (int retryCount = 0; retryCount < sessionLockRetryMax && retryNeeded; retryCount++) {
            lastPrompt = sendWaitFor(payload, defaultTimeout, prompts, buf);
            String[] lines = getLines(buf);
            for (String line : lines) {
                // throw exception only when trying to get into config mode, but not found
                if (line.indexOf(MDSDialogProperties.getString("MDSDialog.ivr.zone.not.found")) >= 0 && !isRemove) {
                    throw new NetworkDeviceControllerException(line + ": " + ivrZonename);
                }

            }
            retryNeeded = checkForEnhancedZoneSession(lines, retryCount);
        }
    }

    /**
     * Commit ivr changes
     * 
     * @throws NetworkDeviceControllerException
     */
    public void ivrCommit() throws NetworkDeviceControllerException {
        _log.info(MessageFormat.format("Host: {0}, Port: {1} -BEGIN ivrCommit",
                new Object[] { getSession().getSession().getHost(), getSession().getSession().getPort() }));

        SSHPrompt[] prompts = { SSHPrompt.MDS_CONFIG, SSHPrompt.MDS_CONFIG_IVR_ZONE, SSHPrompt.MDS_CONFIG_IVR_ZONESET };
        if (!inConfigMode) {
            throw NetworkDeviceControllerException.exceptions.mdsDeviceNotInConfigMode();
        }
        if (!Arrays.asList(prompts).contains(lastPrompt)) {
            String message = Arrays.asList(prompts).toString();
            throw NetworkDeviceControllerException.exceptions.mdsUnexpectedLastPrompt(lastPrompt.toString(), message);
        }
        StringBuilder buf = new StringBuilder();
        String payload = MDSDialogProperties.getString("MDSDialog.ivr.commit.cmd"); // zone commit vsan {0}\n
        lastPrompt = sendWaitFor(payload, defaultTimeout, prompts, buf);
        inSession = false;

        _log.info(MessageFormat.format("Host: {0}, Port: {1} -END ivrCommit",
                new Object[] { getSession().getSession().getHost(), getSession().getSession().getPort() }));

    }

    /**
     * Wait for activation finish processing
     * 
     * @throws NetworkDeviceControllerException
     */
    public void waitForIvrZonesetActivate() throws NetworkDeviceControllerException {
        _log.info(MessageFormat.format("Host: {0}, Port: {1} -BEGIN waitForIvrZonesetActivate",
                new Object[] { getSession().getSession().getHost(), getSession().getSession().getPort() }));

        _log.info("Waiting for ivr zoneset to activate");
        SSHPrompt[] prompts = { SSHPrompt.MDS_POUND, SSHPrompt.MDS_GREATER_THAN, SSHPrompt.MDS_CONFIG, SSHPrompt.MDS_CONFIG_IVR_ZONE,
                SSHPrompt.MDS_CONFIG_IVR_ZONESET };
        StringBuilder buf = new StringBuilder();
        String payload = MDSDialogProperties.getString("MDSDialog.ivr.show.zoneset.status.cmd"); // show zone status vsan {0}\n
        String[] regex = {
                MDSDialogProperties.getString("MDSDialog.ivr.waitForZoneset.activate.inprogress.match"), // ^State:\\s+activating*
                MDSDialogProperties.getString("MDSDialog.ivr.waitForZoneset.activate.success.match"), // ^State:\\s+activation success*
        };
        String[] groups = new String[2];

        boolean completed = false;
        for (int i = 0; i < defaultTimeout && completed == false; i += 1000) {
            try {
                Thread.sleep(1000);         // sleep one second
            } catch (InterruptedException ex) {
                _log.warn(ex.getLocalizedMessage());
            }
            lastPrompt = sendWaitFor(payload, defaultTimeout, prompts, buf);
            String[] lines = getLines(buf);
            for (String line : lines) {
                int index = match(line, regex, groups);
                switch (index) {
                    case 0:
                        completed = false;
                        break;
                    case 1:
                        completed = true;
                        break;
                    case 2:
                        throw new NetworkDeviceControllerException("ivr zoneset activate Commit failed: " + line);
                }
                if (completed) {
                    break;
                }
            }

        }
        _log.info(MessageFormat.format("Host: {0}, Port: {1} -DONE waitForIvrZonesetActivate",
                new Object[] { getSession().getSession().getHost(), getSession().getSession().getPort() }));
    }

    /**
     * Abort ivr changes
     * 
     * @throws NetworkDeviceControllerException
     */
    public void ivrAbort() throws NetworkDeviceControllerException {
        SSHPrompt[] prompts = { SSHPrompt.MDS_CONFIG, SSHPrompt.MDS_CONFIG_IVR_ZONE, SSHPrompt.MDS_CONFIG_IVR_ZONESET };
        if (!inConfigMode) {
            throw NetworkDeviceControllerException.exceptions.mdsDeviceNotInConfigMode();
        }
        if (!Arrays.asList(prompts).contains(lastPrompt)) {
            String message = Arrays.asList(prompts).toString();
            throw NetworkDeviceControllerException.exceptions.mdsUnexpectedLastPrompt(lastPrompt.toString(), message);
        }
        StringBuilder buf = new StringBuilder();
        String payload = MDSDialogProperties.getString("MDSDialog.ivr.abort.cmd"); // zone commit vsan {0}\n
        lastPrompt = sendWaitFor(payload, defaultTimeout, prompts, buf);
        inSession = false;
    }

    /**
     * Collect the active ivr zoneset, and its zones, members
     * 
     * @return a ivr zoneset
     */
    public IvrZoneset showActiveIvrZoneset() throws NetworkDeviceControllerException {
        List<IvrZoneset> zonesets = showIvrZonesets(true);
        return zonesets.isEmpty() ? null : zonesets.get(0);
    }

    /**
     * Collect the active ivr zoneset, and its zones, members
     * 
     * @return a ivr zoneset
     */
    public List<IvrZoneset> showIvrZonesets(boolean active) throws NetworkDeviceControllerException {
        List<IvrZoneset> zonesets = new ArrayList<IvrZoneset>();
        SSHPrompt[] prompts = { SSHPrompt.POUND, SSHPrompt.GREATER_THAN, SSHPrompt.MDS_CONFIG, SSHPrompt.MDS_CONFIG_IVR_ZONE,
                SSHPrompt.MDS_CONFIG_IVR_ZONESET };
        StringBuilder buf = new StringBuilder();

        String cmdKey = active ? "MDSDialog.ivr.show.zoneset.active.cmd" : "MDSDialog.ivr.show.zoneset.cmd";

        sendWaitFor(MDSDialogProperties.getString(cmdKey), defaultTimeout, prompts, buf);
        String[] lines = getLines(buf);
        String[] regex = {
                MDSDialogProperties.getString("MDSDialog.ivr.showZoneset.zoneset.name.match"),
                MDSDialogProperties.getString("MDSDialog.ivr.showZoneset.zone.name.match"),
                MDSDialogProperties.getString("MDSDialog.ivr.showZoneset.zone.member.match")
        };

        IvrZoneset zoneset = null;
        IvrZone zone = null;
        IvrZoneMember member = null;
        String[] groups = new String[10];
        for (String line : lines) {
            line = line.replace('*', ' ');  // remove un-need * char
            int index = match(line, regex, groups);
            switch (index) {
                case 0:
                    zoneset = new IvrZoneset(groups[0]);
                    zonesets.add(zoneset);
                    zoneset.setActive(active);
                    break;
                case 1:
                    zone = new IvrZone(groups[0]);
                    zone.setActive(active);
                    zoneset.getZones().add(zone);
                    break;
                case 2:
                    member = new IvrZoneMember(groups[0] + groups[2], Integer.valueOf(groups[3]));
                    zone.getMembers().add(member);
                    break;
            }
        }

        return zonesets;
    }

    /**
     * Get ivr vsan topology of the switch
     * 
     * @return
     * @throws NetworkDeviceControllerException
     */
    public List<IvrVsanConfiguration> showIvrVsanTopology() throws NetworkDeviceControllerException {
        List<IvrVsanConfiguration> ivrVsans = new ArrayList<IvrVsanConfiguration>();

        SSHPrompt[] prompts = { SSHPrompt.POUND, SSHPrompt.GREATER_THAN, SSHPrompt.MDS_CONFIG, SSHPrompt.MDS_CONFIG_IVR_ZONE,
                SSHPrompt.MDS_CONFIG_IVR_ZONESET };
        StringBuilder buf = new StringBuilder();
        sendWaitFor(MDSDialogProperties.getString("MDSDialog.ivr.vsan.topology.cmd"), defaultTimeout, prompts, buf);
        String[] lines = getLines(buf);
        String[] regex = {
                MDSDialogProperties.getString("MDSDialog.ivr.showTopology.wwn.match")
        };

        String[] groups = new String[10];
        for (String line : lines) {
            int index = match(line, regex, groups);
            switch (index) {
                case 0:
                    IvrVsanConfiguration ivrVsan = new IvrVsanConfiguration();
                    ivrVsan.setSwitchWwn(groups[0] + groups[2]);

                    // local switch is indicated by present of * character
                    ivrVsan.setLocalSwitch("*".equalsIgnoreCase(groups[3]));

                    try {
                        // get the first vsan in the line
                        int vsanId = Integer.valueOf(groups[4]);

                        // if there is vsan present in the line, split the line
                        // and get all ivr vsans via StringSplit
                        if (vsanId > 0) {
                            String vsansText = line.substring(line.indexOf(groups[4], line.indexOf(ivrVsan.getSwitchWwn())
                                    + ivrVsan.getSwitchWwn().length()));

                            String[] vsans = vsansText.split(",");
                            for (String vsan : vsans) {
                                // get vsan range
                                if (vsan.indexOf('-') > 0) {
                                    String[] range = vsan.split("-");
                                    ivrVsan.getVsansRanges().add(
                                            new IntRange(Integer.valueOf(range[0].trim()), Integer.valueOf(range[1].trim())));
                                } else {
                                    ivrVsan.getVsans().add(Integer.valueOf(vsan.trim()));
                                }

                            }
                        }
                    } catch (Exception e) {
                        // not ivr vsan
                    }

                    ivrVsans.add(ivrVsan);
                    break;
            }
        }
        return ivrVsans;
    }

    /**
     * Get switch 's wwn
     * 
     * @return
     * @throws NetworkDeviceControllerException
     */
    public String showSwitchWwn() throws NetworkDeviceControllerException {
        SSHPrompt[] prompts = { SSHPrompt.POUND, SSHPrompt.GREATER_THAN, SSHPrompt.MDS_CONFIG, SSHPrompt.MDS_CONFIG_IVR_ZONE,
                SSHPrompt.MDS_CONFIG_IVR_ZONESET };
        StringBuilder buf = new StringBuilder();
        sendWaitFor(MDSDialogProperties.getString("MDSDialog.show.wwn.switch.cmd"), defaultTimeout, prompts, buf);
        String[] lines = getLines(buf);
        String[] regex = {
                MDSDialogProperties.getString("MDSDialog.show.wwn.switch.match"),
        };

        String switchWwn = null;
        String[] groups = new String[10];
        for (String line : lines) {
            int index = match(line, regex, groups);
            switch (index) {
                case 0:
                    switchWwn = groups[0];
                    break;
            }

            if (switchWwn != null) {
                break;
            }
        }
        return switchWwn;
    }

    /**
     * Get switch ivr zones
     * 
     * @return
     * @throws NetworkDeviceControllerException
     */
    public List<IvrZone> showIvrZones(boolean active) throws NetworkDeviceControllerException {
        List<IvrZone> zones = new ArrayList<IvrZone>();
        SSHPrompt[] prompts = { SSHPrompt.POUND, SSHPrompt.GREATER_THAN };
        StringBuilder buf = new StringBuilder();

        String cmdKey = active ? "MDSDialog.ivr.show.zone.active.cmd" : "MDSDialog.ivr.show.zone.cmd";
        sendWaitFor(MDSDialogProperties.getString(cmdKey), defaultTimeout, prompts, buf);
        String[] lines = getLines(buf);
        IvrZone zone = null;
        IvrZoneMember member = null;
        String[] regex = {
                MDSDialogProperties.getString("MDSDialog.ivr.showZoneset.zone.name.match"),
                MDSDialogProperties.getString("MDSDialog.ivr.showZoneset.zone.member.match")
        };
        String[] groups = new String[10];
        for (String line : lines) {
            int index = match(line, regex, groups);
            switch (index) {
                case 0:
                    zone = new IvrZone(groups[0]);
                    zones.add(zone);
                    break;
                case 1:
                    member = new IvrZoneMember(groups[0] + groups[2], Integer.valueOf(groups[3]));
                    zone.getMembers().add(member);
                    break;
            }
        }
        return zones;
    }

    /**
     * Collect the active ivr zone
     * 
     * @return a ivr zoneset
     */
    public List<IvrZone> showActiveIvrZone() throws NetworkDeviceControllerException {
        return showIvrZones(true);
    }

    /**
     * Check if given vsan is an ivr vsan
     * 
     * @param vsanId
     * @return
     * @throws NetworkDeviceControllerException
     */
    private boolean isIvrVsan(int vsanId) throws NetworkDeviceControllerException {
        List<IvrVsanConfiguration> ivrVsansList = showIvrVsanTopology();

        for (IvrVsanConfiguration ivrVsans : ivrVsansList) {
            if (ivrVsans.isIvrVsan(vsanId)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Return map vsan 's peer devices' ip address
     * 
     * @return a Map of Vsan ID to set of peer devices ( identify by ip address )
     */
    public Map<Integer, Set<String>> showTopology() throws NetworkDeviceControllerException {
        Map<Integer, Set<String>> peerDevicesMap = new HashMap<Integer, Set<String>>();
        SSHPrompt[] prompts = { SSHPrompt.POUND, SSHPrompt.GREATER_THAN };
        StringBuilder buf = new StringBuilder();
        sendWaitFor(MDSDialogProperties.getString("MDSDialog.show.topology.cmd"), 10000, prompts, buf); // show topology\n
        String[] lines = getLines(buf);
        Integer vsanId = null;
        String[] regex = {
                MDSDialogProperties.getString("MDSDialog.show.topology.vsan.match"), // FC Topology for VSAN\\s+(\\d+).*
                MDSDialogProperties.getString("MDSDialog.show.topology.peer.ip.match") // .*\\s+(([0-9]{1,3}\\.){3})([0-9]{1,3}).*
        };
        String[] groups = new String[10];
        for (String line : lines) {
            int index = match(line, regex, groups);
            switch (index) {
                case 0:
                    // Save the vsan id.
                    vsanId = new Integer(groups[0]);
                    break;
                case 1:
                    Set<String> peerDevicesIpAddr = peerDevicesMap.get(vsanId);
                    if (peerDevicesIpAddr == null) {
                        peerDevicesIpAddr = Sets.newHashSet();
                        peerDevicesMap.put(vsanId, peerDevicesIpAddr);
                    }

                    String peerDevice = groups[0] + groups[2];
                    peerDevicesIpAddr.add(peerDevice);
                    break;
            }
        }
        return peerDevicesMap;
    }

    /**
     * Get list of zone names where the given pwwn belongs to
     * 
     * @param pwwn
     * @param vsanId
     * @param activeOnly only list names of active zones
     * @return
     * @throws NetworkDeviceControllerException
     */
    public Set<String> showZoneNamesForPwwn(String pwwn, Integer vsanId, boolean activeOnly) throws NetworkDeviceControllerException {
        Set<String> zoneNames = Sets.newHashSet();
        SSHPrompt[] prompts = { SSHPrompt.POUND, SSHPrompt.GREATER_THAN };
        StringBuilder buf = new StringBuilder();

        String cmdKey = activeOnly ? "MDSDialog.showZone.pwwn.active.cmd" : "MDSDialog.showZone.pwwn.cmd";
        String payload = MessageFormat.format(MDSDialogProperties.getString(cmdKey), pwwn, vsanId.toString());
        sendWaitFor(payload, defaultTimeout, prompts, buf);
        String[] lines = getLines(buf);
        String[] regex = {
                // regex expression is \\s*zone\\s+([-\\w]+)
                MDSDialogProperties.getString("MDSDialog.zoneName.match"),
        };
        String[] groups = new String[10];

        for (String line : lines) {
            int index = match(line, regex, groups);
            switch (index) {
                case 0:
                    zoneNames.add(groups[0]);
                    break;
            }
        }
        return zoneNames;
    }

    /**
     * Get Zone and its members for given zone name
     * 
     * @param zoneName zone name
     * @return
     * @throws NetworkDeviceControllerException
     */
    public Zone showZone(String zoneName) throws NetworkDeviceControllerException {
        return showZone(zoneName, null, true);
    }

    /**
     * Get list of zones for given zone names
     * 
     * @param zoneNames
     * @param excludeAliases
     * @return
     */
    public List<Zone> showZones(Collection<String> zoneNames, boolean excludeAliases) {
        List<Zone> zones = new ArrayList<Zone>();
        if (zoneNames != null && !zoneNames.isEmpty()) {
            Map<String, String> aliasDatabase = showDeviceAliasDatabase();
            for (String zoneName : zoneNames) {
                Zone zone = showZone(zoneName, aliasDatabase, excludeAliases);
                zones.add(zone);
            }
        }
        return zones;
    }

    /**
     * Get Zone and its members for given zone name. Besure to resolve device alias if present.
     * 
     * @param zoneName
     * @param aliasDatabase
     * @param excludeAliases
     * @return
     * @throws NetworkDeviceControllerException
     */
    private Zone showZone(String zoneName, Map<String, String> aliasDatabase, boolean excludeAliases)
            throws NetworkDeviceControllerException {
        Zone zone = new Zone(zoneName);
        SSHPrompt[] prompts = { SSHPrompt.POUND, SSHPrompt.GREATER_THAN };
        StringBuilder buf = new StringBuilder();

        String payload = MessageFormat.format(MDSDialogProperties.getString("MDSDialog.showZone.name.cmd"), zoneName);
        sendWaitFor(payload, defaultTimeout, prompts, buf);

        String[] lines = getLines(buf);
        ZoneMember member = null;
        String[] regex = {
                MDSDialogProperties.getString("MDSDialog.showZoneset.pwwn.match"), // \\s*pwwn ([0-9a-fA-F:]+)\\s*(\\[\\S+\\])?
                MDSDialogProperties.getString("MDSDialog.showZoneset.deviceAlias.match") // \\s*device-alias \\s*(\\S+)\\s*
        };
        String[] groups = new String[10];

        Map<String, String> myAliasDatabase = aliasDatabase == null ? showDeviceAliasDatabase() : aliasDatabase;

        if (excludeAliases) {
            _log.info("Excluding aliases while getting zone members");
        }
        for (String line : lines) {
            int index = match(line, regex, groups);
            member = new ZoneMember(ZoneMember.ConnectivityMemberType.WWPN);
            switch (index) {
                case 0:
                    member.setAddress(groups[0]);  // set wwn id
                    // matched "pwwn <wwnid> [alias]" regex, thus
                    // set alias field as well
                    if (!excludeAliases && groups.length >= 2 && groups[1] != null) {
                        member.setAlias(groups[1].replace("[", "").replace("]", ""));
                    }
                    zone.getMembers().add(member);
                    break;
                case 1:
                    // matched "device-alias <alias>
                    if (!excludeAliases) {
                        member.setAlias(groups[0]); // set alias
                        member.setAliasType(true); // indicate member type of alias
                    }
                    String pwwn = getDeviceAliasPwwn(groups[0], myAliasDatabase);
                    if (!StringUtils.isEmpty(pwwn)) {
                        member.setAddress(pwwn);
                    }
                    zone.getMembers().add(member);
                    break;
            }
        }
        return zone;
    }
}
