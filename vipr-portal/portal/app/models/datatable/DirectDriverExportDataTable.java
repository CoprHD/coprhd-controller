/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;


import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.List;

import com.emc.storageos.model.block.export.ExportBlockParam;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.google.common.collect.Lists;

import util.BourneUtil;
import util.datatable.DataTable;


public class DirectDriverExportDataTable extends DataTable {
    public DirectDriverExportDataTable() {
        addColumn("name");
        addColumn("host");
        sortAll();
    }
    
    public static List<DirectDriverExport> fetch(){
        ViPRCoreClient client = BourneUtil.getViprClient();
        List<DirectDriverExport> result = Lists.newArrayList();
        List<URI> exportList = client.blockExports().listBulkIds();
        for (URI export : exportList) {
            ExportGroupRestRep exports = client.blockExports().get(export);
            if(exports.getVirtualArray() == null ) {
                result.add(new DirectDriverExport(exports));
            }
        }
        return result;
    }
    
    public static class DirectDriverExport {
        public URI id;
        public String name;
        public List<HostRestRep> hosts;
        public List<String> host = Lists.newArrayList();;
        public List<String> volume = Lists.newArrayList();;
        public String hostToAdd;
        public String volumeToAdd;
        public List<ExportBlockParam> volumes;
        
        public DirectDriverExport(ExportGroupRestRep exports) {
            id = exports.getId();
            name = exports.getName();
            hosts = exports.getHosts();
            for(HostRestRep hostrep : hosts) {
                hostToAdd = hostrep.getHostName();
                host.add(hostToAdd);
            }
            volumes = exports.getVolumes();
            for(ExportBlockParam exp:volumes) {
                volumeToAdd = getViprClient().blockVolumes().get(exp.getId()).getName();
                volume.add(volumeToAdd);
            }
            
        }
    }
}