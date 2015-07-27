/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import static com.emc.sa.util.ResourceType.VOLUME;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import util.BourneUtil;
import util.datatable.DataTable;

import com.emc.sa.util.ResourceType;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.block.export.ExportBlockParam;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.google.common.collect.Lists;

import controllers.resources.BlockVolumes;

public class BlockExportGroupVolumesDataTable extends DataTable {
	
    public BlockExportGroupVolumesDataTable() {
        addColumn("name").setRenderFunction("renderLink");
        addColumn("capacity");
        addColumn("lun");
        addColumn("actions").setRenderFunction("renderVolumeActions");
        sortAll();
        setDefaultSort("name", "asc");
        this.setServerSide(true);
    }    	
	
    public static List<Volume> fetch(URI exportGroupID) {
        if (exportGroupID == null) {
            return Collections.emptyList();
        }
        
        ViPRCoreClient client =  BourneUtil.getViprClient();
        
        ExportGroupRestRep exportGroup = client.blockExports().get(exportGroupID);
        List<Volume> volumes = Lists.newArrayList();
        for(ExportBlockParam exportBlockParam : exportGroup.getVolumes()){
            if (ResourceType.isType(VOLUME, exportBlockParam.getId())) { 
                volumes.add(new Volume(client.blockVolumes().get(exportBlockParam.getId()),exportBlockParam));
            }
        }
        return volumes;
    }
    
    public static class Volume {
        public String rowLink;
        public URI id;
        public String name;
        public String capacity;
        public Integer lun;
        
        public Volume(VolumeRestRep volume, ExportBlockParam export) {
            this.id = volume.getId();
            this.name = volume.getName();
            this.capacity = volume.getCapacity();
            this.lun = export.getLun();
            this.rowLink = createLink(BlockVolumes.class, "volume", "volumeId", id);
        }
        
    }
    
}
