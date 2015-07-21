/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.host.InitiatorRestRep;
import com.emc.storageos.model.host.IpInterfaceRestRep;
import com.emc.storageos.model.ports.StoragePortRestRep;
import com.emc.storageos.model.systems.StorageSystemRestRep;
import com.emc.vipr.client.core.util.CachedResources;
import models.TransportProtocols;
import org.apache.commons.lang.StringUtils;
import play.Logger;
import util.datatable.DataTable;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import static com.emc.vipr.client.core.util.ResourceUtils.name;
import static com.emc.vipr.client.core.util.ResourceUtils.stringId;

public class NetworkEndpointDataTable extends DataTable {
    public NetworkEndpointDataTable() {
        addColumn("identifier");
        addColumn("alias");
        addColumn("ipAddress");
        addColumn("name");
        addColumn("portGroup");
        addColumn("storageSystem");
        addColumn("host");
        addColumn("discovered").setRenderFunction("render.boolean");
        setDefaultSort("identifier", "asc");
        sortAll();
    }

    public static NetworkEndpointDataTable createDataTable(String type) {
        NetworkEndpointDataTable dataTable = new NetworkEndpointDataTable();
        if (TransportProtocols.isFc(type)) {
            dataTable.alterColumn("ipAddress").hidden().setSearchable(false);
        }
        return dataTable;
    }

    public static class EndpointInfo {
        public String id;
        public String identifier;
        public String alias;
        public String ipAddress;
        public String name;
        public String storageSystem;
        public String host;
        public boolean discovered;
        public String portGroup;
        
        public EndpointInfo(StoragePortRestRep storagePort, CachedResources<StorageSystemRestRep> storageSystems) {
            this.id = stringId(storagePort);
            this.name = storagePort.getPortName();
            this.identifier = storagePort.getPortNetworkId();
            this.alias = storagePort.getPortAlias();
            this.ipAddress = storagePort.getIpAddress();
            this.portGroup = storagePort.getAdapterName();
            this.storageSystem = name(storageSystems.get(storagePort.getStorageDevice()));
        }

        public EndpointInfo(InitiatorRestRep initiator, CachedResources<HostRestRep> hosts) {
            this.id = stringId(initiator);
            this.name = initiator.getName();
            this.identifier = initiator.getInitiatorPort();
            if (StringUtils.isNotBlank(initiator.getHostName())) {
                this.host = initiator.getHostName();
            }
            else {
                this.host = name(hosts.get(initiator.getHost()));
            }
        }

        public EndpointInfo(IpInterfaceRestRep ipInterface, CachedResources<HostRestRep> hosts) {
            this.id = stringId(ipInterface);
            this.name = ipInterface.getName();
            this.identifier = ipInterface.getIpAddress();
            this.host = name(hosts.get(ipInterface.getHost()));
        }

        public EndpointInfo(String endpoint) {
            try {
                this.id = URLEncoder.encode(endpoint, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                this.id = endpoint;
                Logger.error("Could not encode endpoint: '" + endpoint + "'", e);
            }
            this.identifier = endpoint;
        }
    }
}
