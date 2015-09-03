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
        sortAll();
        setDefaultSort("name", "asc");
    }

    public static class ComputeImageServerInfo {
        public String id;
        public String name;
        public String userName;
        public String password;
        public String tftpbootDir;
        public String osInstallNetworkAddress;
        public String imageServerIp;
        public Integer osInstallTimeOut;

        public ComputeImageServerInfo() {
        }

        public ComputeImageServerInfo(ComputeImageServerRestRep computeImageServer) {
            this.id = computeImageServer.getId().toString();
            this.name = computeImageServer.getName();
            this.imageServerIp = computeImageServer.getImageServerIp();
            this.tftpbootDir = computeImageServer.getTftpbootDir();
            this.osInstallNetworkAddress = computeImageServer.getImageServerSecondIp();
        }
    }
}
