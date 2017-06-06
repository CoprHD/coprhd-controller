/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.db.client.model.DiscoveredDataObject.CompatibilityStatus;
import com.emc.storageos.db.client.model.StoragePort.OperationalStatus;
import com.emc.storageos.model.ports.StoragePortRestRep;
import com.emc.storageos.model.systems.StorageSystemRestRep;

import models.TransportProtocols;
import util.BourneUtil;
import util.datatable.DataTable;

public class StoragePortDataTable extends DataTable {

    public StoragePortDataTable() {

        addColumn("name");
        addColumn("registrationStatus").setRenderFunction("render.registrationStatus");
        addColumn("portGroup");
        addColumn("storageSystem").hidden();
        addColumn("networkIdentifier").setRenderFunction("render.networkIdentifier");
        addColumn("iqn");
        addColumn("alias");
        addColumn("type");
        addColumn("network").hidden();
        addColumn("allocationDisqualified").setRenderFunction("render.allocationDisqualified");
        addColumn("isDRPort").hidden().setRenderFunction("render.boolean");
        addColumn("operationalStatus").setRenderFunction("render.operationalStatus");

        sortAllExcept("id");
        setDefaultSort("name", "asc");
    }

    public static class StoragePortInfo {
        public String id;
        public String name;
        public String portGroup;
        public String storageSystem;
        public String networkIdentifier;
        public String alias;
        public String iqn;
        public String type;
        public String registrationStatus;
        public String operationalStatus;
        public String network;
        public boolean assigned;
        public boolean allocationDisqualified;
        public boolean isDRPort;

        public StoragePortInfo(StoragePortRestRep storagePort) {
            this(storagePort, null);
        }

        public StoragePortInfo(StoragePortRestRep storagePort, StorageSystemRestRep storageSystem) {
            this.id = storagePort.getId().toString();
            this.name = storagePort.getPortName();
            this.portGroup = storagePort.getAdapterName();
            this.storageSystem = storageSystem != null ? storageSystem.getName() : null;

            if (TransportProtocols.isIp(storagePort.getTransportType())) {
                // for IP networks, we want to use the IP address as the network identifier, if we can find it
                String networkID = storagePort.getPortNetworkId();
                this.networkIdentifier = storagePort.getIpAddress();
                if (StringUtils.isEmpty(this.networkIdentifier) && !StringUtils.startsWithIgnoreCase(networkID, "IQN.")) {
                    this.networkIdentifier = networkID;
                } else if (StringUtils.startsWithIgnoreCase(networkID, "IQN.")) {
                    this.iqn = networkID;
                }
            } else {
                this.networkIdentifier = storagePort.getPortNetworkId();
            }
            this.alias = storagePort.getPortAlias();
            this.type = TransportProtocols.getDisplayValue(storagePort.getTransportType());
            this.registrationStatus = storagePort.getRegistrationStatus();

            if (OperationalStatus.OK.name().equals(storagePort.getOperationalStatus())
                    && CompatibilityStatus.UNKNOWN.name().equals(storagePort.getCompatibilityStatus())) {
                this.operationalStatus = OperationalStatus.UNKNOWN.name();
            } else if (OperationalStatus.OK.name().equals(storagePort.getOperationalStatus())
                    && CompatibilityStatus.INCOMPATIBLE.name().equals(storagePort.getCompatibilityStatus())) {
                this.operationalStatus = OperationalStatus.NOT_OK.name();
            } else {
                this.operationalStatus = storagePort.getOperationalStatus();
            }

            this.allocationDisqualified = storagePort.getAllocationDisqualified();
            if (BourneUtil.getViprClient().storagePorts().getTags(storagePort.getId()).contains("dr_port")) {
                isDRPort = true;
            }
        }
    }
}