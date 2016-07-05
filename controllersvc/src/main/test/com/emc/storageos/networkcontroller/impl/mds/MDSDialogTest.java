/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.networkcontroller.impl.mds;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.FCEndpoint;
import com.emc.storageos.networkcontroller.SSHSession;
import com.emc.storageos.networkcontroller.exceptions.NetworkDeviceControllerException;
import com.emc.storageos.volumecontroller.ControllerException;

public class MDSDialogTest {

    private static final String UNIT_TEST_CONFIG_FILE = "sanity";
    private static final String ipaddress = "10.247.96.81" ; //EnvConfig.get(UNIT_TEST_CONFIG_FILE, "brocade.host.ipaddress");
    private static final String username = "admin"; //EnvConfig.get(UNIT_TEST_CONFIG_FILE, "brocade.host.username");
    private static final String password = "Bourn3!!" ; //EnvConfig.get(UNIT_TEST_CONFIG_FILE, "brocade.host.password");
    static String vsanId = "55";
    static Integer sshport = 22;
    

    private static final Logger _log = LoggerFactory.getLogger(MDSDialogTest.class);

    /**
     * @param args
     *            Working directory:
     *            ${workspace_loc:shared-fczoning-ad/controllersvc/src/main/test/com/emc/storageos/networkcontroller/impl/mds}
     *            Eclipse launch XML:
     * 
     * 
     * 
     */
    public static void main(String[] args) {
        Properties properties = new Properties();
        // PropertyConfigurator.configure("log4j.properties");
        _log.info("Beginning logging");
        // try {
        // properties.load(new FileInputStream("properties.xml"));
        // ipaddress = properties.getProperty("ipaddress");
        // sshport = new Integer(properties.getProperty("sshport"));
        // username = properties.getProperty("username");
        // password = properties.getProperty("password");
        // vsanId = properties.getProperty("vsanId");
        // } catch (Exception ex) {
        // System.out.println("Couldn't load properties.xml file");
        // }
        SSHSession sshs = null;
        try {
            sshs = new SSHSession();
            sshs.connect(ipaddress, sshport, username, password);
            MDSDialog dialog = new MDSDialog(sshs, null);
            dialog.initialize();
            String[] versions = dialog.showVersion();
            if (versions[0] != null) {
                System.out.println("Hardware: " + versions[0]);
            }
            if (versions[1] != null) {
                System.out.println("Software: " + versions[1]);
            }

            Map<Integer, Set<String>> peerDevicesMap = dialog.showTopology();
            System.out.println("Peer Devices: ");
            for (Entry<Integer, Set<String>> entry : peerDevicesMap.entrySet()) {
                _log.info(String.format("...Vsan: %s, Peer devices: %s%n", entry.getKey(), entry.getValue()));
            }

            testNonIvr(dialog);

            testIvr(dialog);
        } catch (Exception ex) {
            _log.error(ex.getMessage(), ex);
        } finally {
            if (sshs != null) {
                sshs.disconnect();
            }
        }

    }

    static void printVsan(MDSDialog dialog, Integer vsanId) throws ControllerException {
        Zoneset activeZoneset = dialog.showActiveZoneset(vsanId);
        if (activeZoneset != null) {
            _log.info("Active zoneset for vsan: {}", vsanId);
            activeZoneset.print();
        }

        _log.info("Inactive zonesets for vsan: {}", vsanId);
        List<Zoneset> zonesets = dialog.showZoneset(vsanId, false, null, false, false);
        for (Zoneset zs : zonesets) {
            if (!zs.getActive() == true) {
                zs.print();
            }
        }
    }

    static void testNonIvr(MDSDialog dialog) throws Exception {
        Map<Integer, String> vsanToWwns = dialog.getVsanWwns(null);
        for (Integer vsan : vsanToWwns.keySet()) {
            String wwn = vsanToWwns.get(vsan);
            _log.info("Vsan {} WWN {}", vsan, wwn);
        }
        boolean isSessionInProgress = dialog.isSessionInProgress(3178);
        isSessionInProgress = dialog.isSessionInProgress(new Integer(vsanId));
        if (isSessionInProgress) {
            throw new Exception("Session in progress vsan: " + vsanId);
        }
        // dialog.showInterface();
        // dialog.showFlogiDatabase();
        List<FCEndpoint> connections = dialog.showFcnsDatabase(null);
        for (FCEndpoint cn : connections) {
            String msg = MessageFormat.format("connection: {0} {1}:{2} {3} remote {4} {5} fabric {6}",
                    cn.getFabricId(), cn.getSwitchName(), cn.getSwitchInterface(), cn.getSwitchPortName(),
                    cn.getRemotePortName(), cn.getRemotePortName(), cn.getFabricWwn());
            _log.info(msg);
        }

        dialog.showVsan(true);
        // Print vsans, zonesets, zones, zone members
        for (String key : Vsan.vsanIdToVsan.keySet()) {
            Vsan v = Vsan.vsanIdToVsan.get(key);
            System.out.println("VSAN: " + v.vsanName);
            v.print();
        }
       
        testZoning(dialog, new Integer(vsanId));

    }

    static void testIvr(MDSDialog dialog) throws ControllerException {
        if (dialog.isIvrEnabled()) {
            String switchWwn = dialog.showSwitchWwn();
            _log.info("Switch WWN: {}", switchWwn);

            List<IvrVsanConfiguration> ivrVsansList = dialog.showIvrVsanTopology();
            for (IvrVsanConfiguration ivrVsans : ivrVsansList) {
                _log.info(String.format("%s%n", ivrVsans.toString()));
            }

            testIvrZoneset(dialog);

            testIvrZone(dialog);

            _log.info("---------------- Active Ivr Zoneset ----------------%n");
            IvrZoneset ivrZoneset = dialog.showActiveIvrZoneset();
            printIvrZoneset(ivrZoneset);

        } else {
            _log.info("Switch {} is not ivr enabled", ipaddress);
        }
    }

    static void configIvrZoneSet(MDSDialog dialog, String zonesetName, boolean bRemove) {
        dialog.config();
        dialog.ivrZonesetName("test-zoneset-abc", false, bRemove);
        if (!bRemove) {
            try {
                dialog.ivrZonesetMember("test-non-exist-zone", bRemove);
            } catch (Exception e) {
                _log.error(e.getMessage(), e);
            }
        }
        dialog.ivrCommit();
        dialog.endConfig();
    }

    static void configIvrZone(MDSDialog dialog, String zoneName, boolean bRemove) {
        dialog.config();
        if (!bRemove) {
            dialog.ivrZoneName("test-zone-abc", bRemove);
            dialog.ivrZoneMember("11:11:11:11:11:11:11:11", 3180, bRemove);
        } else {
            dialog.ivrZoneName("test-zone-abc", false);
            dialog.ivrZoneMember("11:11:11:11:11:11:11:11", 3180, bRemove);
            dialog.ivrZoneName("test-zone-abc", bRemove);
        }
        dialog.ivrCommit();
        dialog.endConfig();
    }

    static void testIvrZoneset(MDSDialog dialog) {
        // create ivr zone set
        configIvrZoneSet(dialog, "test-zoneset-abc", false);

        // show zone set after create
        _log.info("---------------- Ivr Zoneset after created ----------------%n");
        for (IvrZoneset ivrZoneset : dialog.showIvrZonesets(false)) {
            printIvrZoneset(ivrZoneset);
        }

        // remove ivr zone set
        configIvrZoneSet(dialog, "test-zoneset-abc", true);

        // show zone set after create
        _log.info("---------------- Ivr Zoneset after removed ----------------%n");
        for (IvrZoneset ivrZoneset : dialog.showIvrZonesets(false)) {
            printIvrZoneset(ivrZoneset);
        }
    }

    static void testIvrZone(MDSDialog dialog) {
        // create ivr zone set
        configIvrZone(dialog, "test-zoneset-abc", false);

        // show zone set after create
        _log.info("---------------- Ivr Zone after created ----------------%n");
        for (IvrZone ivrZone : dialog.showIvrZones(false)) {
            printIvrZone(ivrZone);
        }

        // remove ivr zone set
        configIvrZone(dialog, "test-zoneset-abc", true);

        // show zone set after create
        _log.info("---------------- Ivr Zone after removed ----------------%n");
        for (IvrZone ivrZone : dialog.showIvrZones(false)) {
            printIvrZone(ivrZone);
        }
    }

    static void printIvrZoneset(IvrZoneset ivrZoneset) {
        if (ivrZoneset == null) {
            return;
        }

        _log.info("--Ivr Zoneset: %s%n", ivrZoneset.getName());
        for (IvrZone ivrZone : ivrZoneset.getZones()) {
            printIvrZone(ivrZone);
        }
    }

    static void printIvrZone(IvrZone ivrZone) {
        _log.info("-----Ivr Zone: %s%n", ivrZone.getName());
        for (IvrZoneMember ivrZoneMember : ivrZone.getMembers()) {
            _log.info("----------pwwn: {},  vsan: {}%n", ivrZoneMember.getPwwn(), ivrZoneMember.getVsanId());
        }
    }

    static List<Zone> zonesToTest = new ArrayList<Zone>();

    static void testZoning(MDSDialog dialog, Integer vsanId) throws ControllerException {
        dialog.config();
        dialog.deviceAliasConfig();

        // Make a bunch of zones to test
        for (Integer i = 0; i < 100; i++) {
            Zone z = new Zone("z" + i.toString());
            Integer lastByte = i + 16;
            ZoneMember m1 = new ZoneMember("10:00:00:00:00:FF:FF:" + Integer.toHexString(lastByte).toUpperCase(),
                    ZoneMember.ConnectivityMemberType.WWPN);
            m1.setAlias("device-alias_10_" + Integer.toHexString(lastByte).toUpperCase());
            ZoneMember m2 = new ZoneMember("50:00:00:00:00:FF:FF:" + Integer.toHexString(lastByte).toUpperCase(),
                    ZoneMember.ConnectivityMemberType.WWPN);
            m2.setAlias("device-alias_50_" + Integer.toHexString(lastByte).toUpperCase());

            z.getMembers().add(m1);
            z.getMembers().add(m2);
            zonesToTest.add(z);

            try {
                dialog.deviceAliasName(m1.getAlias(), m1.getAddress(), false);
            } catch (NetworkDeviceControllerException ex) {
                if (ex.getMessage().indexOf("already present") >= 0) {
                    continue;
                } else {
                    throw ex;
                }
            }

            try {
                dialog.deviceAliasName(m2.getAlias(), m2.getAddress(), false);
            } catch (NetworkDeviceControllerException ex) {
                if (ex.getMessage().indexOf("already present") >= 0) {
                    continue;
                } else {
                    throw ex;
                }
            }

        }
        dialog.deviceAliasCommit();
        dialog.endConfig();

        // Loop, timing differing number of zones
        for (Integer i = 4; i < 50; i += 5) {
            List<Zone> zoneArgs = new ArrayList<Zone>();
            for (int j = 0; j <= i; j++) {
                zoneArgs.add(zonesToTest.get(j));
            }

            _log.info("Creating zones:");
            MdsNetworkSystemDevice device = new MdsNetworkSystemDevice();
            device.addZonesStrategy(dialog, zoneArgs, vsanId, false);
            printVsan(dialog, vsanId);

            _log.info("Removing zones:");
            device.removeZonesStrategy(dialog, zoneArgs, vsanId, false);
            printVsan(dialog, vsanId);
        }

        dialog.config();
        dialog.deviceAliasConfig();
        for (Zone zone : zonesToTest) {
            for (ZoneMember zoneMember : zone.getMembers()) {
                if (zoneMember.getAlias() != null) {
                    try {
                        dialog.deviceAliasName(zoneMember.getAlias(), zoneMember.getAddress(), true);
                    } catch (NetworkDeviceControllerException ex) {
                        if (ex.getMessage().indexOf("not present") >= 0) {
                            continue;
                        } else {
                            throw ex;
                        }
                    }
                }
            }
        }
        dialog.deviceAliasCommit();
        dialog.endConfig();
    }
}
