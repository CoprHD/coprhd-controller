/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import java.net.URI;
import java.util.List;
import java.util.Map;

import models.HostTypes;

import org.apache.commons.lang.StringUtils;

import util.datatable.DataTable;

import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.host.vcenter.VcenterDataCenterRestRep;
import com.google.common.collect.Lists;

import controllers.compute.Hosts;

public class HostDataTable extends DataTable {

    private static List<String> stripFromVersion = Lists.newArrayList("Microsoft Windows ", "Server ", " Enterprise");

    public HostDataTable() {
        addColumn("name").setRenderFunction("renderLink");
        addColumn("hostname");
        addColumn("type").setRenderFunction("render.operatingSystem");
        addColumn("computeElement");
        addColumn("serviceProfile");
        addColumn("version").hidden();
        addColumn("cluster").hidden();
        addColumn("discoverable").setRenderFunction("render.boolean");
        HostInfo.addDiscoveryColumns(this);
        sortAll();
        setDefaultSort("name", "asc");
    }

    public static class HostInfo extends DiscoveredSystemInfo {

        public static final String ESX_HOST_LABEL_FORMAT = "%2$s [%1$s]";
        public static final String ESX_CLUSTER_LABEL_FORMAT = "%2$s [%1$s]";

        public String id;
        public String rowLink;
        public String name;
        public String hostname;
        public String type;
        public String version;
        public boolean discoverable;
        public String cluster;
        public String serviceProfile;
        public String computeElement;

        public HostInfo() {
        }

        public HostInfo(HostRestRep host, Map<URI, String> clusterMap, Map<URI, VcenterDataCenterRestRep> vcenterDataCenters) {
            this(host, clusterMap, getVcenterDataCenterName(host, vcenterDataCenters));
        }

        public HostInfo(HostRestRep host, Map<URI, String> clusterMap, String vcenterDataCenterName) {
            super(host);
            this.id = host.getId().toString();
            this.rowLink = createLink(Hosts.class, "edit", "id", id);
            this.name = getHostLabel(host, vcenterDataCenterName);
            this.hostname = host.getHostName();

            if (HostTypes.isOther(host.getType())) {
                if (host.getPortNumber() != null && host.getPortNumber() > 1) {
                    this.hostname += ":" + host.getPortNumber();
                }
            }
            this.version = prettifyVersion(host.getOsVersion());
            this.type = host.getType();
            this.discoverable = host.getDiscoverable() == null ? true : host.getDiscoverable();
            if (host.getCluster() != null) {
                this.cluster = clusterMap.get(host.getCluster().getId());
            }
            else {
                this.cluster = "";
            }
            if (host.getProvisioningJobStatus() != null) {
                // substitute status for display
                this.discoveryStatus = host.getProvisioningJobStatus();
            }
            this.serviceProfile = host.getServiceProfileName();
            this.computeElement = host.getComputeElementName();
        }

        public String getHostLabel(HostRestRep host, String vcenterDataCenterName) {
            if (StringUtils.isNotBlank(vcenterDataCenterName)) {
                return vcenterDataCenterName + "/" + host.getName();
            }
            return host.getName();
        }

        public boolean isVcenterHost(HostRestRep host) {
            return host.getvCenterDataCenter() != null;
        }

        private static String getVcenterDataCenterName(HostRestRep host, Map<URI, VcenterDataCenterRestRep> vcenterDataCenters) {
            if (host != null && vcenterDataCenters != null && host.getvCenterDataCenter() != null) {
                VcenterDataCenterRestRep vcenterDataCenter = vcenterDataCenters.get(host.getvCenterDataCenter().getId());
                if (vcenterDataCenter != null) {
                    return vcenterDataCenter.getName();
                }
            }
            return null;
        }
    }

    // OS Version for Windows is quite long. Remove product name
    private static String prettifyVersion(String string) {
        if (StringUtils.isNotBlank(string)) {
            for (String strip : stripFromVersion) {
                string = string.replaceAll(strip, "");
            }
        }
        return string;
    }
}
