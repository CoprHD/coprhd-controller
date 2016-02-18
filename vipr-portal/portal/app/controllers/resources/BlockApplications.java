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
import com.emc.storageos.model.block.BlockSnapshotSessionRestRep;
import com.emc.storageos.model.block.BlockSnapshotRestRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.google.common.collect.Lists;

import models.datatable.ApplicationSupportDataTable;
import models.datatable.BlockSnapshotSessionsDataTable;
import models.datatable.BlockSnapshotSessionsDataTable.BlockSnapshotSession;
import models.datatable.BlockSnapshotsDataTable;
import models.datatable.BlockVolumesDataTable.Volume;
import models.datatable.BlockVolumesDataTable;

import models.datatable.BlockSnapshotsDataTable.BlockSnapshot;

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
        VolumeGroupRestRep application = AppSupportUtil.getApplication(id);
        Set<String> sessionSet = AppSupportUtil.getVolumeGroupSnapsetSessionSets(id);
        Set<String> clonesSet = AppSupportUtil.getFullCopySetsByApplication(id);
        Set<String> snapSets = AppSupportUtil.getVolumeGroupSnapshotSets(id);
        render(application, clonesSet, sessionSet, snapSets);
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
        String copyLabel = id + "~~~" + copySet;
        render(application, copyLabel, copySet);
    }

    public static void getAssociatedVolumesJSON(String copyLabel) {
        List<Volume> volumeDetails = Lists.newArrayList();
        String[] copySets = copyLabel.split("~~~");
        List<NamedRelatedResourceRep> volumeDetailClone = AppSupportUtil.getVolumeGroupFullCopiesForSet(copySets[0], copySets[1]);
        for (NamedRelatedResourceRep volume : volumeDetailClone) {
            VolumeRestRep blockVolume = BourneUtil.getViprClient().blockVolumes().get(volume.getId());
            volumeDetails.add(new Volume(blockVolume, virtualArrays, virtualPools));
        }
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

    public static void getAssociatedSnapSession(String id, String sessionSet) {
        renderArgs.put("dataTable", new BlockSnapshotSessionsDataTable());
        VolumeGroupRestRep application = AppSupportUtil.getApplication(id);
        String sessionLabel = id + "~~~" + sessionSet;
        render(application, sessionLabel, sessionSet);
    }
    
    public static void getAssociatedSnapSessionJSON(String sessionLabel) {
        String[] sessionSet = sessionLabel.split("~~~");
        List<BlockSnapshotSession> sessionDetails = Lists.newArrayList();
        List<NamedRelatedResourceRep> sessionSetDetails = AppSupportUtil.getVolumeGroupSnapshotSessionsByCopySet(sessionSet[0],
                sessionSet[1]);
        for (NamedRelatedResourceRep snapSession : sessionSetDetails) {
            BlockSnapshotSessionRestRep snapshotSession = BourneUtil.getViprClient().blockSnapshotSessions().get(snapSession.getId());
            sessionDetails.add(new BlockSnapshotSession(snapshotSession));
        }
        renderJSON(DataTablesSupport.createJSON(sessionDetails, params));
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
