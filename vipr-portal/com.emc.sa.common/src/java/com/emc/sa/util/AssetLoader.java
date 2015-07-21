/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

import com.emc.sa.model.dao.ModelClient;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.HostInterface.Protocol;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Vcenter;
import com.emc.storageos.util.SanUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class AssetLoader {

    private ModelClient client;

    public AssetLoader(ModelClient client) {
        this.client = client;
    }

    public void loadAssets(URL resource) throws IOException {
        loadAssets(null, resource);
    }

    public void loadAssets(File f) throws IOException {
        loadAssets(null, f);
    }

    public void loadAssets(InputStream in) throws IOException {
        loadAssets(null, in);
    }    
    
    public void loadAssets(String tenant, URL resource) throws IOException {
        Assets assets = readAssets(resource.openStream());
        saveAssets(tenant, assets);
    }

    public void loadAssets(String tenant, File f) throws IOException {
        Assets assets = readAssets(new FileInputStream(f));
        saveAssets(tenant, assets);
    }

    public void loadAssets(String tenant, InputStream in) throws IOException {
        Assets assets = readAssets(in);
        saveAssets(tenant, assets);
    }

    protected Assets readAssets(InputStream in) throws IOException {
        try {
            Gson gson = new GsonBuilder().create();
            Assets assets = gson.fromJson(new InputStreamReader(in), Assets.class);
            return assets;
        }
        finally {
            IOUtils.closeQuietly(in);
        }
    }

    protected void saveAssets(String tenant, Assets assets) throws IOException {
        if (assets.hosts != null) {
            for (HostDef host : assets.hosts) {
                saveHost(tenant, host);
            }
        }
        if (assets.vcenters != null) {
            for (VCenterDef vcenter : assets.vcenters) {
                saveVCenter(tenant, vcenter);
            }
        }
    }

    public Host saveHost(String tenant, HostDef def) {
        Host host = new Host();
        host.setTenant(URI.create(tenant));
        host.setLabel(def.name);
        host.setHostName(def.hostname);
        host.setPortNumber(def.port);
        host.setType(def.os);
        host.setUsername(def.username);
        host.setPassword(def.password);
        client.save(host);
        if (def.wwns != null) {
            int i = 0;
            for (String wwn : def.wwns) {
                Initiator initiator = new Initiator();
                initiator.setHost(host.getId());
                initiator.setLabel("P" + i);
                if (StringUtils.startsWith(wwn, "iqn.")) {
                    initiator.setInitiatorNode("");
                    initiator.setInitiatorPort(wwn);
                    initiator.setProtocol(Protocol.iSCSI.name());
                }
                else {
                    String node = SanUtils.getNodeName(wwn);
                    String port = SanUtils.getPortName(wwn);

                    initiator.setInitiatorNode(node);
                    initiator.setInitiatorPort(port);
                    initiator.setProtocol(Protocol.FC.name());
                }
                client.save(initiator);
            }
        }
        return host;
    }

    public Vcenter saveVCenter(String tenant, VCenterDef def) {
        Vcenter vcenter = new Vcenter();
        vcenter.setTenant(URI.create(tenant));
        vcenter.setLabel(def.name);
        vcenter.setIpAddress(def.hostname);
        vcenter.setPortNumber(def.port);
        vcenter.setUseSSL(def.useHttps);
        vcenter.setUsername(def.username);
        vcenter.setPassword(def.password);
        client.save(vcenter);
        return vcenter;
    }


    public static class Assets {
        public List<HostDef> hosts;
        public List<VCenterDef> vcenters;
    }

    public static class HostDef {
        public String os;
        public String name;
        public String hostname;
        public Integer port;
        public String username;
        public String password;
        public String[] wwns;
    }

    public static class VCenterDef {
        public String name;
        public String hostname;
        public Integer port;
        public String username;
        public String password;
        public boolean useHttps = true;
    }
}