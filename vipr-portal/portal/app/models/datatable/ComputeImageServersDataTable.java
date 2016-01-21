/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import util.datatable.DataTable;

import com.emc.storageos.model.compute.ComputeImageServerRestRep;

public class ComputeImageServersDataTable extends DataTable {

    public ComputeImageServersDataTable() {
        addColumn("name").setRenderFunction("renderLink");
        addColumn("imageServerIp");
        addColumn("osInstallNetworkAddress");
        addColumn("computeImageServerStatus");
        sortAll();
        setDefaultSort("name", "asc");
    }

    public static class ComputeImageServerInfo {
        public String id;
        public String imageServerIp;
        public String name;
        public String osInstallNetworkAddress;
        public Integer osInstallTimeOut;
        public String password;
        public String computeImageServerStatus;
        public String tftpBootDir;
        public String userName;

        public ComputeImageServerInfo() {
        }

        public ComputeImageServerInfo(ComputeImageServerRestRep computeImageServer) {
            this.id = computeImageServer.getId().toString();
            this.name = computeImageServer.getName();
            this.imageServerIp = computeImageServer.getImageServerIp();
            this.computeImageServerStatus = computeImageServer.getComputeImageServerStatus();
            this.tftpBootDir = computeImageServer.getTftpBootDir();
            this.osInstallNetworkAddress = computeImageServer.getImageServerSecondIp();
        }
    }
}
