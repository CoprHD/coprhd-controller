/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */

package com.iwave.ext.netapp;

import java.util.ArrayList;
import java.util.List;

import netapp.manage.NaElement;
import netapp.manage.NaServer;

import org.apache.log4j.Logger;

public class VFiler {
    private final Logger log = Logger.getLogger(getClass());

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

            // DNS servers!!
            List<NameServerInfo> dnsServers = new ArrayList<NameServerInfo>();
            for (NaElement dns : (List<NaElement>) filerInfo.getChildByName("dns-info").getChildren()) {
                NameServerInfo dnsInfo = new NameServerInfo();
                dnsInfo.setName(dns.getChildContent("dns-domain-name"));
                List<VFNetInfo> servers = new ArrayList<VFNetInfo>();
                for (NaElement server : (List<NaElement>) dns.getChildByName("dns-servers").getChildren()) {
                    VFNetInfo vfNetInfo = new VFNetInfo();
                    vfNetInfo.setIpAddress(server.getChildContent("ipaddress"));
                    vfNetInfo.setNetInterface(server.getChildContent("interface"));
                    vfNetInfo.setNetMask(server.getChildContent("netmask"));
                    servers.add(vfNetInfo);
                }
                dnsInfo.setNameServers(servers);
                dnsServers.add(dnsInfo);
            }
            info.setDnsServers(dnsServers);

            // NIS servers!!
            List<NameServerInfo> nisServers = new ArrayList<NameServerInfo>();
            for (NaElement nis : (List<NaElement>) filerInfo.getChildByName("nis-info").getChildren()) {
                NameServerInfo nisInfo = new NameServerInfo();
                nisInfo.setName(nis.getChildContent("nis-domain-name"));
                List<VFNetInfo> servers = new ArrayList<VFNetInfo>();
                for (NaElement server : (List<NaElement>) nis.getChildByName("nis-servers").getChildren()) {
                    VFNetInfo vfNetInfo = new VFNetInfo();
                    vfNetInfo.setIpAddress(server.getChildContent("ipaddress"));
                    vfNetInfo.setNetInterface(server.getChildContent("interface"));
                    vfNetInfo.setNetMask(server.getChildContent("netmask"));
                    servers.add(vfNetInfo);
                }
                nisInfo.setNameServers(servers);
                nisServers.add(nisInfo);
            }
            info.setNisServers(nisServers);

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
}
