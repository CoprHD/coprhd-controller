/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */

package com.iwave.ext.netapp;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;

import netapp.manage.NaElement;
import netapp.manage.NaServer;

public class VFiler {
    private Logger log = Logger.getLogger(getClass());

    private String name = "";
    private NaServer server = null;
    private String CREATE_VFILER = "Create Vfiler";

    public VFiler(NaServer server, String name) {
        this.name = name;
        this.server = server;
    }

    public List<VFilerInfo> listVFilers(boolean listAll) {
        ArrayList<VFilerInfo> vFilers = new ArrayList<VFilerInfo>();

        NaElement elem = new NaElement("vfiler-list-info");
        if (!listAll) {
            elem.addNewChild("vfiler", name);
        }

        NaElement result = null;
        try {
            result = server.invokeElem(elem).getChildByName("vfilers");
        } catch (Exception e) {
            // If MultiStore not enabled, then this is the expected behavior.
            String msg = "No vFiler information returned from array.";
            log.info(msg);
            throw new NetAppException(msg, e);
        }

        for (NaElement filerInfo : (List<NaElement>) result.getChildren()) {
            VFilerInfo info = new VFilerInfo();
            info.setName(filerInfo.getChildContent("name"));
            info.setIpspace(filerInfo.getChildContent("ipspace"));

            List<VFNetInfo> netInfo = new ArrayList<VFNetInfo>();
            for (NaElement vfnet : (List<NaElement>) filerInfo.getChildByName("vfnets").getChildren()) {
                VFNetInfo vfNetInfo = new VFNetInfo();
                vfNetInfo.setIpAddress(vfnet.getChildContent("ipaddress"));
                vfNetInfo.setNetInterface(vfnet.getChildContent("interface"));
                netInfo.add(vfNetInfo);
            }

            info.setInterfaces(netInfo);
            vFilers.add(info);
        }

        return vFilers;
    }

    boolean addStorage(String storagePath, String vFilerName) {
        NaElement elem = new NaElement("vfiler-add-storage");
        elem.addNewChild("storage-path", storagePath);
        elem.addNewChild("vfiler", vFilerName);

        try {
            server.invokeElem(elem);
        } catch (Exception e) {
            String msg = "Failed to add new volume: " + storagePath;
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }

        return true;
    }

    boolean removeStorage(String storagePath, String vFilerName) {
        NaElement elem = new NaElement("vfiler-remove-storage");
        elem.addNewChild("storage-path", storagePath);
        elem.addNewChild("vfiler", vFilerName);

        try {
            server.invokeElem(elem);
        } catch (Exception e) {
            String msg = "Failed to remove new volume: " + storagePath;
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }

        return true;
    }

    boolean createVFiler(String vFilerName, List<String> ipAddresses, List<String> storageUnits, String ipSpace) {

        NaElement elem = new NaElement("vfiler-create");
        NaElement ipAddsElem = new NaElement("ip-addresses");
        NaElement storageUnitsElem = new NaElement("storage-units");
        elem.addChildElem(ipAddsElem);
        for (String ipAddress : ipAddresses) {
            ipAddsElem.addNewChild("ip-address", ipAddress);
        }
        elem.addChildElem(storageUnitsElem);
        for (String storageUnit : storageUnits) {
            storageUnitsElem.addNewChild("storage-unit", storageUnit);
        }
        elem.addNewChild("vfiler", vFilerName);

        try {
            server.invokeElem(elem);
        } catch (Exception e) {
            String msg = "Failed to create new vFiler: " + vFilerName;
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
        return true;
    }

    boolean allowProtocols(String vFilerName, Set<String> protocols, String opType) {

        Set<String> existingProtocols = new HashSet<String>();
        NaElement getProtocols = new NaElement("vfiler-get-allowed-protocols");
        getProtocols.addNewChild("vfiler", vFilerName);
        NaElement result = null;
        try {
            result = server.invokeElem(getProtocols).getChildByName("allowed-protocols");
            for (NaElement protocolInfo : (List<NaElement>) result.getChildren()) {
                existingProtocols.add(protocolInfo.getChildContent("protocol"));
            }
            if (CREATE_VFILER.equalsIgnoreCase(opType)) {
                existingProtocols.removeAll(protocols);
                for (String protocol : existingProtocols) {
                    removeProtocols(vFilerName, protocol);
                }
            } else {
                protocols.removeAll(existingProtocols);
                for (String protocol : protocols) {
                    addProtocols(vFilerName, protocol);
                }
            }
        } catch (Exception e) {
            String msg = "Failed to add protocols to vFiler: " + vFilerName;
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
        return true;
    }

    boolean disallowProtocols(String vFilerName, Set<String> protocols) {

        Set<String> existingProtocols = new HashSet<String>();
        NaElement getProtocols = new NaElement("vfiler-get-allowed-protocols");
        getProtocols.addNewChild("vfiler", vFilerName);
        NaElement result = null;
        try {
            result = server.invokeElem(getProtocols).getChildByName("allowed-protocols");
            for (NaElement protocolInfo : (List<NaElement>) result.getChildren()) {
                existingProtocols.add(protocolInfo.getChildContent("protocol"));
            }
            for (String protocol : protocols) {
                if (existingProtocols.contains(protocol)) {
                    removeProtocols(vFilerName, protocol);
                }
            }
        } catch (Exception e) {
            String msg = "Failed to remove protocols from vFiler: " + vFilerName;
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
        return true;
    }

    private void addProtocols(String vFilerName, String protocol) {
        NaElement allowProtocol = new NaElement("vfiler-allow-protocol");
        allowProtocol.addNewChild("vfiler", vFilerName);
        allowProtocol.addNewChild("protocol", protocol.toLowerCase());
        try {
            server.invokeElem(allowProtocol);
        } catch (Exception e) {
            String msg = "Failed to update protocols to vFiler: " + vFilerName;
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
    }

    private void removeProtocols(String vFilerName, String protocol) {
        NaElement disallowProtocol = new NaElement("vfiler-disallow-protocol");
        disallowProtocol.addNewChild("vfiler", vFilerName);
        disallowProtocol.addNewChild("protocol", protocol.toLowerCase());
        try {
            server.invokeElem(disallowProtocol);
        } catch (Exception e) {
            String msg = "Failed to remove protocols from vFiler: " + vFilerName;
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
    }

    boolean setupVFiler(String vFilerName, String adminHostIp, String adminHostName, String dnsDomain, List<String> dnsServers,
            String nisDomain, List<String> nisServers,
            String password) {

        NaElement elem = new NaElement("vfiler-setup");
        NaElement adminHostElem = new NaElement("adminhost");
        NaElement dnsServersElem = new NaElement("dnsservers");
        NaElement nisServersElem = new NaElement("nisservers");

        elem.addNewChild("vfiler", vFilerName);
        elem.addChildElem(adminHostElem);
        adminHostElem.addNewChild("ipaddress", adminHostIp);
        adminHostElem.addNewChild("name", adminHostName);
        elem.addNewChild("dnsdomain", dnsDomain);
        elem.addChildElem(dnsServersElem);
        for (String dnsServer : dnsServers) {
            NaElement dnsServerInfo = new NaElement("dnsserver-info");
            dnsServerInfo.addNewChild("ipaddress", dnsServer);
            dnsServersElem.addChildElem(dnsServerInfo);
        }
        elem.addNewChild("nisdomain", nisDomain);
        elem.addChildElem(nisServersElem);
        for (String nisServer : nisServers) {
            NaElement nisServerInfo = new NaElement("nisserver-info");
            nisServerInfo.addNewChild("ipaddress", nisServer);
            nisServersElem.addChildElem(nisServerInfo);
        }
        elem.addNewChild("password", password);

        try {
            server.invokeElem(elem);
        } catch (Exception e) {
            String msg = "Failed to setup new vFiler: " + vFilerName;
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
        return true;
    }

    boolean stopVFiler(String vFilerName) {

        NaElement elem = new NaElement("vfiler-stop");
        elem.addNewChild("vfiler", vFilerName);
        try {
            server.invokeElem(elem);
        } catch (Exception e) {
            String msg = "Failed to stop vFiler: " + vFilerName;
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
        return true;
    }

    boolean destroyVFiler(String vFilerName) {

        NaElement elem = new NaElement("vfiler-destroy");
        elem.addNewChild("vfiler", vFilerName);
        try {
            server.invokeElem(elem);
        } catch (Exception e) {
            String msg = "Failed to destroy vFiler: " + vFilerName;
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
        return true;
    }

    boolean addIpAddress(String ipAddress, String vFilerName) {
        NaElement elem = new NaElement("vfiler-add-ipaddress");
        elem.addNewChild("ipaddress", ipAddress);
        elem.addNewChild("vfiler", vFilerName);

        try {
            server.invokeElem(elem);
        } catch (Exception e) {
            String msg = "Failed to add new ip address: " + ipAddress + " to VFiler: " + vFilerName;
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }

        return true;
    }

    boolean removeIpAddress(String ipAddress, String vFilerName) {
        NaElement elem = new NaElement("vfiler-remove-ipaddress");
        elem.addNewChild("ipaddress", ipAddress);
        elem.addNewChild("vfiler", vFilerName);

        try {
            server.invokeElem(elem);
        } catch (Exception e) {
            String msg = "Failed to remove ip address: " + ipAddress + " from VFiler: " + vFilerName;
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }

        return true;
    }

    boolean startCifsService(String vFilerName) {
        NaElement elem = new NaElement("cifs-setup");
        elem.addNewChild("auth-type", "ad");
        elem.addNewChild("domain-name", "provisioning.bourne.local");
        elem.addNewChild("login-password", "Dangerous2");
        elem.addNewChild("login-user", "Administrator");
        elem.addNewChild("security-style", "ntfs");
        elem.addNewChild("server-name", "test");
        elem.addNewChild("site-name", "default-first-site-name");

        try {
            server.invokeElem(elem);
        } catch (Exception e) {
            String msg = "Failed to start cifs service on VFiler: " + vFilerName;
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }

        return true;
    }
}
