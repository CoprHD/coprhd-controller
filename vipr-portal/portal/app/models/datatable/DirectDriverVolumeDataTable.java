/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.List;

import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.google.common.collect.Lists;

import util.BourneUtil;
import util.datatable.DataTable;


public class DirectDriverVolumeDataTable extends DataTable {
    public DirectDriverVolumeDataTable() {
        addColumn("name");
        addColumn("storage");
        addColumn("pool");
        addColumn("size");
        sortAll();
    }
    
    public static List<DirectDriverVolume> fetch(){
        ViPRCoreClient client = BourneUtil.getViprClient();
        List<DirectDriverVolume> result = Lists.newArrayList();
        List<URI> volumeList = client.blockVolumes().listBulkIds();
        for (URI volume : volumeList) {
            VolumeRestRep volumes = client.blockVolumes().get(volume);
            if(volumes.getVirtualArray() == null || volumes.getProject() == null) {
                result.add(new DirectDriverVolume(volumes));
            }
        }
        return result;
    }
    
    public static class DirectDriverVolume {
        public URI id;
        public String name;
        public String storage;
        public String pool;
        public String size;
        
        public DirectDriverVolume(VolumeRestRep volumes) {
            id = volumes.getId();
            name = volumes.getName();
            storage = getViprClient().storageSystems().get(volumes.getStorageController()).getName();
            pool = getViprClient().storagePools().get(volumes.getPool().getId()).getName();
            size = volumes.getProvisionedCapacity();
        }
    }
}