/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.resources;


import java.net.URI;
import java.util.List;
import java.util.Map;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.application.VolumeGroupRestRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.google.common.collect.Lists;

import models.datatable.ApplicationSupportDataTable;
import models.datatable.BlockVolumesDataTable.Volume;
import models.datatable.BlockVolumesDataTable;
import play.mvc.With;
import util.AppSupportUtil;
import util.BourneUtil;
import util.datatable.DataTablesSupport;
import controllers.Common;

@With(Common.class)
public class BlockApplications extends ResourceController {

    private static ApplicationSupportDataTable blockApplicationsDataTable = new ApplicationSupportDataTable();
    
    public static void blockApplications() {
        renderArgs.put("dataTable", blockApplicationsDataTable);
        addReferenceData();
        render();
    }
    
    public static void blockApplicationsJson() {
        List<ApplicationSupportDataTable.ApplicationSupport> applications = ApplicationSupportDataTable.fetch();
        renderJSON(DataTablesSupport.createJSON(applications, params));
    }
    
    public static void blockApplicationDetails(String id) {
    	renderArgs.put("dataTable", new VolumeApplicationDataTable());
        VolumeGroupRestRep application =  AppSupportUtil.getApplication(id);
        render(application);
    }
    
    public static void applicationVolumeJson(String id) {
    	List<Volume> volumeDetails = Lists.newArrayList();
    	Map<URI, String> virtualArrays = ResourceUtils.mapNames(BourneUtil.getViprClient().varrays().list());
        Map<URI, String> virtualPools = ResourceUtils.mapNames(BourneUtil.getViprClient().blockVpools().list());
        List<NamedRelatedResourceRep> volumes = AppSupportUtil.getVolumesByApplication(id);
        for (NamedRelatedResourceRep volume : volumes) {
            VolumeRestRep blockVolume = BourneUtil.getViprClient().blockVolumes().get((volume.getId()));
            volumeDetails.add(new Volume(blockVolume, virtualArrays, virtualPools));
        }
        renderJSON(DataTablesSupport.createJSON(volumeDetails, params));
    }
    
    public static class VolumeApplicationDataTable extends BlockVolumesDataTable {
        public VolumeApplicationDataTable() {
            alterColumn("protocols").hidden();
            alterColumn("wwn").hidden();
            addColumn("replicationGroup");
        }
    }
}

