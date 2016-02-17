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
import com.emc.storageos.model.application.VolumeGroupRestRep;
import com.emc.storageos.model.block.BlockSnapshotRestRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.google.common.collect.Lists;

import models.datatable.ApplicationSupportDataTable;
import models.datatable.BlockSnapshotsDataTable;
import models.datatable.BlockVolumesDataTable.Volume;
import models.datatable.BlockVolumesDataTable;
import controllers.resources.BlockApplications.CloneApplicationDataTable.Clone;
import models.datatable.BlockSnapshotsDataTable.BlockSnapshot;
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
        VolumeGroupRestRep application = AppSupportUtil.getApplication(id);
        Set<String> snapSets = AppSupportUtil.getVolumeGroupSnapshotSets(id);
        render(application, snapSets);
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

    public static void applicationCloneJson(String id) {
        List<Clone> cloneDetails = Lists.newArrayList();
        List<NamedRelatedResourceRep> clones = AppSupportUtil.getFullCopiesByApplication(id);
        for (NamedRelatedResourceRep clone : clones) {
            VolumeRestRep blockVolume = BourneUtil.getViprClient().blockVolumes().get((clone.getId()));
            cloneDetails.add(new Clone(blockVolume));
        }
        renderJSON(DataTablesSupport.createJSON(cloneDetails, params));
    }

    public static void getAssociatedVolume(String id, String applicationId) {
        render(id, applicationId);
    }

    public static void getAssociatedVolumes(String id, String applicationId) {
        renderArgs.put("dataTable", new VolumeApplicationDataTable());
        VolumeRestRep clone = BourneUtil.getViprClient().blockVolumes().get(uri(id));
        VolumeGroupRestRep application = AppSupportUtil.getApplication(applicationId);
        render(clone, application);
    }

    public static void getAssociatedVolumesJSON(String id) {
        VolumeRestRep clone = BourneUtil.getViprClient().blockVolumes().get(uri(id));
        URI associatedVolumeId = clone.getProtection().getFullCopyRep().getAssociatedSourceVolume().getId();
        VolumeRestRep volumes = BourneUtil.getViprClient().blockVolumes().get(associatedVolumeId);
        List<Volume> volumeDetails = Lists.newArrayList();
        volumeDetails.add(new Volume(volumes, virtualArrays, virtualPools));
        renderJSON(DataTablesSupport.createJSON(volumeDetails, params));
    }

    public static void getAssociatedSnapshots(String id, String snapSet) {
        renderArgs.put("dataTable", new BlockSnapshotsDataTable());
        VolumeGroupRestRep application = AppSupportUtil.getApplication(id);
        String snapLabel = id + "~~~" + snapSet;
        render(application,snapLabel,snapSet);
    }

    public static void getAssociatedSnapshotsJSON(String snapLabel) {
        String[] snapSets = snapLabel.split("~~~");
        List<BlockSnapshot> snapShotDetails = Lists.newArrayList();
        List<NamedRelatedResourceRep> snapsetDetails = AppSupportUtil.getVolumeGroupSnapshotsForSet(snapSets[0], snapSets[1]);
        for (NamedRelatedResourceRep snapShot : snapsetDetails) {
            BlockSnapshotRestRep blockSnapshot = BourneUtil.getViprClient().blockSnapshots().get(snapShot.getId());
            snapShotDetails.add(new BlockSnapshot(blockSnapshot));
        }
        renderJSON(DataTablesSupport.createJSON(snapShotDetails, params));
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
            addColumn("name");
            addColumn("volumes").setRenderFunction("renderLink");
            addColumn("size");
            addColumn("status");
            addColumn("protocol");
            sortAll();
        }

        // Suppressing Sonar violation of need for accessor methods.
        @SuppressWarnings("ClassVariableVisibilityCheck")
        public static class Clone {
            public URI id;
            public String name;
            public String size;
            public String status;
            public Set<String> protocol;
            public String volumes;
            public URI refs;

            public Clone(VolumeRestRep volume) {
                id = volume.getId();
                name = volume.getName();
                size = volume.getProvisionedCapacity();
                status = volume.getProtection().getFullCopyRep().getReplicaState();
                protocol = volume.getProtocols();
                refs = volume.getProtection().getFullCopyRep().getAssociatedSourceVolume().getId();
                volumes = BourneUtil.getViprClient().blockVolumes().get(refs).getName();
            }
        }
    }
}
