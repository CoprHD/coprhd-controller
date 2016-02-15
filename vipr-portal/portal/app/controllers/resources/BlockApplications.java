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
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.application.VolumeGroupCopySetList;
import com.emc.storageos.model.application.VolumeGroupRestRep;
import com.emc.storageos.model.block.NamedVolumesList;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.google.common.collect.Lists;

import models.datatable.ApplicationSupportDataTable;
import models.datatable.BlockVolumesDataTable.Volume;
import models.datatable.BlockVolumesDataTable;
import controllers.resources.BlockApplications.CloneApplicationDataTable.Clone;
import play.mvc.With;
import util.AppSupportUtil;
import util.BourneUtil;
import util.datatable.DataTablesSupport;
import controllers.Common;
import util.datatable.DataTable;
import static com.emc.vipr.client.core.util.ResourceUtils.uri;

@With(Common.class)
public class BlockApplications extends ResourceController {

    private static ApplicationSupportDataTable blockApplicationsDataTable = new ApplicationSupportDataTable();
    private static Map<URI, String> virtualArrays = ResourceUtils.mapNames(BourneUtil.getViprClient().varrays().list());
    private static Map<URI, String> virtualPools = ResourceUtils.mapNames(BourneUtil.getViprClient().blockVpools().list());

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
        renderArgs.put("cloneTable", new CloneApplicationDataTable());
        Set<String> clonesSet = AppSupportUtil.getFullCopySetsByApplication(id);
        VolumeGroupRestRep application = AppSupportUtil.getApplication(id);
        render(application,clonesSet);
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
        render(application, copySet);
    }

    public static void getAssociatedVolumesJSON(String copySet, String id) {
        List<Volume> volumeDetails = Lists.newArrayList();
//        List<NamedRelatedResourceRep> volumeDetailClone = AppSupportUtil.getVolumeGroupFullCopiesForSet(id,copySet);
//        for(NamedRelatedResourceRep volume:volumeDetailClone) {
//            VolumeRestRep blockVolume = BourneUtil.getViprClient().blockVolumes().get(volume.getId());
//            volumeDetails.add(new Volume(blockVolume, virtualArrays, virtualPools));
//        }
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

    public static class CloneApplicationDataTable extends DataTable {
        public CloneApplicationDataTable() {
            addColumn("cloneGroups").setRenderFunction("renderCloneLink");
            sortAll();
        }

        // Suppressing Sonar violation of need for accessor methods.
        @SuppressWarnings("ClassVariableVisibilityCheck")
        public static class Clone {
            public Set<String> cloneGroups;

            public Clone(Set<String> clonesSet) {
                cloneGroups = clonesSet;
            }
        }
    }
}
