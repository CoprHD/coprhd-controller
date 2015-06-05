/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package models.datatable;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import models.ComputeSystemTypes;
import models.VlanListTypes;

import org.apache.commons.lang.StringUtils;

import play.i18n.Messages;

import util.datatable.DataTable;

import com.emc.storageos.model.compute.ComputeSystemRestRep;


public class ComputeSystemsDataTable extends DataTable {

    public ComputeSystemsDataTable() {
        addColumn("name").setRenderFunction("renderLink");
        addColumn("ipAddress");
        addColumn("systemType");
        addColumn("version");
        addColumn("osInstallNetworkDisplay");
        addColumn("port").hidden();
        addColumn("userName").hidden();
        ComputeSystemsInfo.addDiscoveryColumns(this);
        addColumn("registrationStatus").setRenderFunction("render.registrationStatus");
        sortAll();
        setDefaultSort("name", "asc");
    }

    public static class ComputeSystemsInfo extends DiscoveredSystemInfo {
        public String id;
        public String name;
        public String ipAddress;
        public String userName;
        public String systemType;
        public String version;
        public String osInstallNetworkDisplay;
        public Integer port;
        public String registrationStatus;


        public ComputeSystemsInfo() {
        }

        public ComputeSystemsInfo(ComputeSystemRestRep computeSystem) {
            super(computeSystem);
            this.id = computeSystem.getId().toString();
            this.name = computeSystem.getName();
            this.ipAddress = computeSystem.getIpAddress();
            this.version = computeSystem.getVersion();
            this.osInstallNetworkDisplay = computeSystem.getOsInstallNetwork();
            if (StringUtils.isBlank(this.osInstallNetworkDisplay)) {
                this.osInstallNetworkDisplay = VlanListTypes.NO_OSINSTALL_NONE;
            } 
            else if (StringUtils.isNotBlank(this.osInstallNetworkDisplay) && StringUtils.isNotBlank(computeSystem.getVlans())) {
                boolean found = false;
                //verify the vlan is good
                List<String> vlanList = new ArrayList<String>(Arrays.asList(computeSystem.getVlans().split(",")));
                for (String vlan : vlanList) {
                    if (vlan.equals(computeSystem.getOsInstallNetwork())) {
                        found = true;
                        break;
                     }
                }
                if (!found) {
                    this.osInstallNetworkDisplay = Messages.get("computeSystem.invalidOsInstallNetwork", computeSystem.getOsInstallNetwork());
                }
            }
            this.port = computeSystem.getPortNumber();
            this.userName = computeSystem.getUsername();           
            this.systemType = ComputeSystemTypes.getDisplayValue(computeSystem.getSystemType());
            this.registrationStatus = computeSystem.getRegistrationStatus();
        }
    }
}
