package com.emc.storageos.networkcontroller.impl.mds;

import java.util.ArrayList;
import java.util.List;

import javax.cim.CIMInstance;

import com.emc.storageos.services.util.EnvConfig;


public class DCNMDialogTest {

    static final Integer sshport = 22;

    private static final String UNIT_TEST_CONFIG_FILE = "sanity";

    private static final String ipaddress = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "brocade.smis.host.ipaddress");
    private static final String providerPortStr = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "brocade.smis.host.port");
    private static final int smisport = Integer.parseInt(providerPortStr);
    private static final String username = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "brocade.smis.host.username");
    private static final String password = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "brocade.smis.host.password");
    private static final String providerUseSsl = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "brocade.smis.usessl");
    private static final String namespace = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "brocade.smis.namespace");
    private static boolean isProviderSslEnabled = Boolean.parseBoolean(providerUseSsl);

    public static void main(String[] args) throws Exception {

        DCNMDialog dialog = new DCNMDialog();
        dialog.getClient(ipaddress, username, password, smisport);
        dialog.getZoneService("3180");
        List<String> vsanIds = dialog.getFabricIds();
        for (String vsan : vsanIds) {
            System.out.println("vsan: " + vsan);
        }
        // List<FCPortConnection> connections = dialog.getPortConnection();
        // for (FCPortConnection conn: connections) {
        // String x = MessageFormat.format("{0} {1} {2} {3} {4} {5}",
        // conn.getFabricId(), conn.getRemotePortName(),
        // conn.getRemoteNodeName(),
        // conn.getSwitchInterface(), conn.getSwitchName());
        // System.out.println(x);
        // }
        List<Zoneset> zonesets = dialog.getZonesets(3181);
        for (Zoneset zs : zonesets) {
            System.out.println("Zoneset: " + zs.getName());
            zs.print();
        }
        zonesets = dialog.getZonesets(3180);
        for (Zoneset zs : zonesets) {
            System.out.println("Zoneset: " + zs.getName());
            zs.print();
        }

        CIMInstance fabricIns = dialog.getFabricInstance("3180");

        Zone zn = new Zone("test_zone");
        List<Zone> zones = new ArrayList<Zone>();
        zones.add(zn);

        dialog.addZonesStrategy(zones, new Integer("3181"));
    }

}
