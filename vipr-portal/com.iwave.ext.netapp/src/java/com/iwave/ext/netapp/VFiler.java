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

    boolean allowProtocols(String vFilerName, Set<String> protocols) {

        Set<String> existingProtocols = new HashSet<String>();
        NaElement getProtocols = new NaElement("vfiler-get-allowed-protocols");
        getProtocols.addNewChild("vfiler", vFilerName);
        NaElement result = null;
        try {
            result = server.invokeElem(getProtocols).getChildByName("allowed-protocols");
            for (NaElement protocolInfo : (List<NaElement>) result.getChildren()) {
                existingProtocols.add(protocolInfo.getChildContent("protocol"));
            }
            existingProtocols.removeAll(protocols);
            for (String protocol : existingProtocols) {
                NaElement disallowProtocol = new NaElement("vfiler-disallow-protocol");
                disallowProtocol.addNewChild("vfiler", vFilerName);
                disallowProtocol.addNewChild("protocol", protocol.toLowerCase());
                server.invokeElem(disallowProtocol);
            }
        } catch (Exception e) {
            String msg = "Failed to add protocols to vFiler: " + vFilerName;
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
        return true;
    }

    boolean setupVFiler(String vFilerName, String dnsDomain, List<String> dnsServers, String nisDomain, List<String> nisServers,
            String password) {

        NaElement elem = new NaElement("vfiler-setup");
        NaElement dnsServersElem = new NaElement("dnsservers");
        NaElement dnsServerInfo = new NaElement("dnsserver-info");
        NaElement nisServersElem = new NaElement("nisservers");
        NaElement nisServerInfo = new NaElement("nisserver-info");
        elem.addNewChild("vfiler", vFilerName);
        elem.addNewChild("dnsdomain", dnsDomain);
        elem.addChildElem(dnsServersElem);
        dnsServersElem.addChildElem(dnsServerInfo);
        for (String dnsServer : dnsServers) {
            dnsServerInfo.addNewChild("ipaddress", dnsServer);
        }
        elem.addNewChild("nisdomain", nisDomain);
        elem.addChildElem(nisServersElem);
        nisServersElem.addChildElem(nisServerInfo);
        for (String nisServer : nisServers) {
            nisServerInfo.addNewChild("ipaddress", nisServer);
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
}
