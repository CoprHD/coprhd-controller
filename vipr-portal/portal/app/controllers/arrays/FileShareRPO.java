/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.arrays;



import java.util.List;

import com.emc.storageos.model.vpool.FileReplicationPolicy;
import com.emc.storageos.model.vpool.FileVirtualPoolProtectionParam;
import com.emc.storageos.model.vpool.FileVirtualPoolRestRep;
import com.google.common.collect.Lists;

import models.datatable.VirtualPoolDataTable;
import models.datatable.VirtualPoolDataTable.VirtualPoolInfo;
import models.virtualpool.FileVirtualPoolForm;
import play.mvc.With;


import util.VirtualPoolUtils;
import util.datatable.DataTablesSupport;
import controllers.Common;
import controllers.arrays.FileShareRPO.FileShareDataTable.FileShareInfo;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.ViprResourceController;

@With(Common.class)
@Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
public class FileShareRPO extends ViprResourceController {
    
    public static void list() {
        FileShareDataTable dataTable = new FileShareDataTable();
        render(dataTable);
    }
    
    public static void listJson() {
        List<FileShareInfo> items = Lists.newArrayList();
        List<FileVirtualPoolRestRep> virtualPool = VirtualPoolUtils.getFileVirtualPools();
        for(FileVirtualPoolRestRep vpool: virtualPool) {
            if(!vpool.getFileReplicationType().toString().equals("NONE")){
                items.add(new FileShareInfo(vpool));
            }
        }
        renderJSON(DataTablesSupport.createJSON(items, params));
    }
    
    public static void fileSystemDetails(String id) {
        
    }
    
    public static class FileShareDataTable extends VirtualPoolDataTable {
        public FileShareDataTable() {
            addColumn("rpo");
            alterColumn("description").hidden();
            alterColumn("provisionedAs").hidden();
            alterColumn("storagePoolAssignment").hidden();
            alterColumn("protocols").hidden();
            alterColumn("numPools").hidden();
            alterColumn("numResources").hidden();
        }
        
        public static class FileShareInfo extends VirtualPoolInfo {
            public Long rpoValue;
            public String rpoType;
            private String rpo;

            public FileShareInfo(FileVirtualPoolRestRep pool) {
                super();
                FileReplicationPolicy protection = pool.getProtection().getReplicationParam().getSourcePolicy();
                this.name = pool.getName();
                this.rpoType = protection.getRpoType().toLowerCase();
                this.rpoValue = protection.getRpoValue();
                this.rpo = rpoValue+ " " +rpoType;
                
            }
            
        }
    }
}