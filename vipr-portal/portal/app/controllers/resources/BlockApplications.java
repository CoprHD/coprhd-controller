/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.resources;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private static Map<URI, String> virtualArrays = ResourceUtils.mapNames(BourneUtil.getViprClient().varrays().list());
    private static Map<URI, String> virtualPools = ResourceUtils.mapNames(BourneUtil.getViprClient().blockVpools().list());
    public static final String ACTIVE_COPY = "copySet";

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
        Set<String> clonesSet = AppSupportUtil.getFullCopySetsByApplication(id);
        VolumeGroupRestRep application = AppSupportUtil.getApplication(id);
        render(application, clonesSet);
    }

    public static void applicationVolumeJson(String id) {
        List<Volume> volumeDetails = Lists.newArrayList();
        List<NamedRelatedResourceRep> volumes = AppSupportUtil.getVolumesByApplication(id);
        for (NamedRelatedResourceRep volume : volumes) {
            VolumeRestRep blockVolume = BourneUtil.getViprClient().blockVolumes().get((volume.getId()));
            volumeDetails.add(new Volume(blockVolume, virtualArrays, virtualPools));
        }
        renderJSON(DataTablesSupport.createJSON(volumeDetails, params));
    }

    public static void getAssociatedVolumes(String id, String copySet) {
        renderArgs.put("dataTable", new VolumeApplicationDataTable());
        VolumeGroupRestRep application = AppSupportUtil.getApplication(id);
        String copyLabel = id + "," + copySet;
        render(application, copyLabel, copySet);
    }

    public static void getAssociatedVolumesJSON(String copyLabel) {
        List<Volume> volumeDetails = Lists.newArrayList();
        String[] copySets = copyLabel.split(",");
        List<NamedRelatedResourceRep> volumeDetailClone = AppSupportUtil.getVolumeGroupFullCopiesForSet(copySets[0], copySets[1]);
        for (NamedRelatedResourceRep volume : volumeDetailClone) {
            VolumeRestRep blockVolume = BourneUtil.getViprClient().blockVolumes().get(volume.getId());
            volumeDetails.add(new Volume(blockVolume, virtualArrays, virtualPools));
        }
        renderJSON(DataTablesSupport.createJSON(volumeDetails, params));
    }

    public static class VolumeApplicationDataTable extends BlockVolumesDataTable {
        public VolumeApplicationDataTable() {
            alterColumn("protocols").hidden();
            alterColumn("wwn").hidden();
            addColumn("replicationGroup");
            sortAll();
        }
    }
}
