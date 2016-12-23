package com.emc.storageos.volumecontroller.adapters;

import java.util.List;

import com.emc.aix.AixSystem;
import com.emc.hpux.HpuxSystem;
import com.emc.storageos.computesystemcontroller.impl.adapter.EsxHostDiscoveryAdapter;
import com.emc.storageos.computesystemcontroller.impl.adapter.VcenterDiscoveryAdapter;
import com.emc.storageos.computesystemcontroller.impl.adapter.WindowsHostDiscoveryAdapter;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Host.HostType;
import com.emc.storageos.db.client.model.Vcenter;
import com.iwave.ext.command.HostRescanAdapter;
import com.iwave.ext.linux.LinuxSystemCLI;
import com.iwave.ext.vmware.HostStorageAPI;
import com.iwave.ext.vmware.VCenterAPI;
import com.vmware.vim25.mo.HostSystem;

public class TestRescanAdapter {

    public static void main(String[] args) throws Exception {
        testHost(HostType.Linux.name(), "losat102.lss.emc.com", "root", "dangerous", 22);
        testHost(HostType.HPUX.name(), "lglal017.lss.emc.com", "root", "dangerous", 22);
        testHost(HostType.Windows.name(), "lglw7155.lss.emc.com", "Administrator", "dangerous", 5985, false);
        testHost(HostType.Esx.name(), "lglw7135.lss.emc.com", null, null, 0);
        testHost(HostType.AIX.name(), "lglbg019.lss.emc.com", "root", "dangerous", 22);
    }

    public static void testHost(String type, String hostname, String username, String password, int port) throws Exception {
        testHost(type, hostname, username, password, port, false);
    }

    public static void testHost(String type, String hostname, String username, String password, int port, boolean useSsl) throws Exception {
        Host host = new Host();
        host.setType(type);
        host.setHostName(hostname);
        host.setUsername(username);
        host.setPassword(password);
        host.setPortNumber(port);
        host.setUseSSL(useSsl);

        HostRescanAdapter hostAdapter = getRescanAdapter(host);

        System.out.println("Rescanning " + type);
        hostAdapter.rescan();
    }

    DbClient _dbClient = null;

    public static HostRescanAdapter getRescanAdapter(Host host) {

        if (host.getType().equalsIgnoreCase(HostType.Linux.name())) {
            return new LinuxSystemCLI(host.getHostName(), host.getPortNumber(), host.getUsername(), host.getPassword());
        } else if (host.getType().equalsIgnoreCase(HostType.AIX.name())) {
            return new AixSystem(host.getHostName(), host.getPortNumber(), host.getUsername(), host.getPassword());
        } else if (host.getType().equalsIgnoreCase(HostType.HPUX.name())) {
            return new HpuxSystem(host.getHostName(), host.getPortNumber(), host.getUsername(), host.getPassword());
        } else if (host.getType().equalsIgnoreCase(HostType.Windows.name())) {
            // TODO: initialize kerberos
            // List<AuthnProvider> authProviders = new ArrayList<AuthnProvider>();
            // Iterables.addAll(authProviders, _dbClient.qu().of(AuthnProvider.class).findAll(true));
            // KerberosUtil.initializeKerberos(authProviders);
            return WindowsHostDiscoveryAdapter.createWindowsSystem(host);
        } else if (host.getType().equalsIgnoreCase(HostType.Esx.name())) {
            if (host.getUsername() != null && host.getPassword() != null) {
                VCenterAPI vcenterAPI = EsxHostDiscoveryAdapter.createVCenterAPI(host);
                List<HostSystem> hostSystems = vcenterAPI.listAllHostSystems();
                if (hostSystems != null && !hostSystems.isEmpty()) {
                    return new HostStorageAPI(hostSystems.get(0));
                } else {
                    return null;
                }
            } else {
               //TODO: query the host's vcenter datacenter and vcenter here
                Vcenter vcenter = new Vcenter();
                vcenter.setIpAddress("lglw8117.lss.emc.com");
                vcenter.setUsername("root");
                vcenter.setPassword("vmware");

                VCenterAPI vCenterAPI = VcenterDiscoveryAdapter.createVCenterAPI(vcenter);
                // TODO lookup datacenter name based on dbclient query
                String datacenterName = "Bourne-4";
                HostSystem hostSystem = vCenterAPI.findHostSystem(datacenterName, host.getHostName());
                if (hostSystem != null) {
                    return new HostStorageAPI(hostSystem);
                } else {
                    return null;
                }
            }
        } else {
            return null;
        }
    }

}
