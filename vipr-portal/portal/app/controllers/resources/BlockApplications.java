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
import com.emc.storageos.model.block.BlockSnapshotSessionRestRep;
import com.emc.storageos.model.block.BlockSnapshotRestRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.google.common.collect.Lists;

import models.datatable.ApplicationSnapSessionDataTable;
import models.datatable.ApplicationSnapSessionDataTable.ApplicationSnapshotSession;
import models.datatable.ApplicationSnapSetDataTable;
import models.datatable.ApplicationSnapSetDataTable.ApplicationSnapSets;
import models.datatable.ApplicationSnapshotDataTable;
import models.datatable.ApplicationSnapshotSetDataTable;
import models.datatable.ApplicationSnapshotSetDataTable.ApplicationSnapshotSets;
import models.datatable.ApplicationSupportDataTable;
import models.datatable.BlockSnapshotSessionsDataTable.BlockSnapshotSession;
import models.datatable.ApplicationSnapshotDataTable.ApplicationSnapshots;
import models.datatable.BlockVolumesDataTable;
import models.datatable.ApplicationFullCopySetsDataTable;
import models.datatable.ApplicationFullCopySetsDataTable.ApplicationFullCopySets;
import play.mvc.With;
import util.AppSupportUtil;
import util.BourneUtil;
import util.datatable.DataTablesSupport;
import controllers.Common;
import controllers.resources.BlockApplications.VolumeApplicationDataTable.VolumeApplication;

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
        renderArgs.put("cloneDataTable", new ApplicationFullCopySetsDataTable());
        renderArgs.put("snapshotDataTable", new ApplicationSnapshotSetDataTable());
        renderArgs.put("snapsessionDataTable", new ApplicationSnapSetDataTable());
        VolumeGroupRestRep application = AppSupportUtil.getApplication(id);
        render(application);
    }

	public static void applicationCloneJson(String id) {
		List<ApplicationFullCopySets> cloneDetails = Lists.newArrayList();
		Set<String> clonesSet = AppSupportUtil.getFullCopySetsByApplication(id);
		for (String clone : clonesSet) {
			List<NamedRelatedResourceRep> volumeDetailClone = AppSupportUtil
					.getVolumeGroupFullCopiesForSet(id, clone);
			cloneDetails.add(new ApplicationFullCopySets(clone,
					volumeDetailClone));
		}
		renderJSON(DataTablesSupport.createJSON(cloneDetails, params));
	}
    
	public static void applicationSnapshotJson(String id) {
		List<ApplicationSnapshotSets> snapDetails = Lists.newArrayList();
		Set<String> snapSets = AppSupportUtil.getVolumeGroupSnapshotSets(id);
		for (String snap : snapSets) {
			List<NamedRelatedResourceRep> snapshots = AppSupportUtil
					.getVolumeGroupSnapshotsForSet(id, snap);
			snapDetails.add(new ApplicationSnapshotSets(snap, snapshots));
		}
		renderJSON(DataTablesSupport.createJSON(snapDetails, params));
	}
    
	public static void applicationSnapSessionJson(String id) {
		List<ApplicationSnapSets> snapDetails = Lists.newArrayList();
		Set<String> sessionSet = AppSupportUtil
				.getVolumeGroupSnapsetSessionSets(id);
		for (String snap : sessionSet) {
			List<NamedRelatedResourceRep> snapshots = AppSupportUtil
					.getVolumeGroupSnapshotSessionsByCopySet(id, snap);
			snapDetails.add(new ApplicationSnapSets(snap, snapshots));
		}
		renderJSON(DataTablesSupport.createJSON(snapDetails, params));
	}
    
    public static void applicationVolumeJson(String id) {
        List<VolumeApplication> volumeDetails = Lists.newArrayList();
        List<NamedRelatedResourceRep> volumes = AppSupportUtil.getVolumesByApplication(id);
        for (NamedRelatedResourceRep volume : volumes) {
            VolumeRestRep blockVolume = BourneUtil.getViprClient().blockVolumes().get((volume.getId()));
            volumeDetails.add(new VolumeApplication(blockVolume));
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
        List<VolumeApplication> volumeDetails = Lists.newArrayList();
        String[] copySets = copyLabel.split("~~~");
        List<NamedRelatedResourceRep> volumeDetailClone = AppSupportUtil.getVolumeGroupFullCopiesForSet(copySets[0], copySets[1]);
        for (NamedRelatedResourceRep volume : volumeDetailClone) {
            VolumeRestRep blockVolume = BourneUtil.getViprClient().blockVolumes().get(volume.getId());
            volumeDetails.add(new VolumeApplication(blockVolume));
        }
        renderJSON(DataTablesSupport.createJSON(volumeDetails, params));
    }

    public static void getAssociatedSnapshots(String id, String snapSet) {
        renderArgs.put("dataTable", new ApplicationSnapshotDataTable());
        VolumeGroupRestRep application = AppSupportUtil.getApplication(id);
        String snapLabel = id + "~~~" + snapSet;
        render(application,snapLabel,snapSet);
    }

    public static void getAssociatedSnapshotsJSON(String snapLabel) {
        String[] snapSets = snapLabel.split("~~~");
        List<ApplicationSnapshots> snapShotDetails = Lists.newArrayList();
        List<NamedRelatedResourceRep> snapsetDetails = AppSupportUtil.getVolumeGroupSnapshotsForSet(snapSets[0], snapSets[1]);
        for (NamedRelatedResourceRep snapShot : snapsetDetails) {
            BlockSnapshotRestRep blockSnapshot = BourneUtil.getViprClient().blockSnapshots().get(snapShot.getId());
            snapShotDetails.add(new ApplicationSnapshots(blockSnapshot));
        }
        renderJSON(DataTablesSupport.createJSON(snapShotDetails, params));
    }

    public static void getAssociatedSnapSession(String id, String sessionSet) {
        renderArgs.put("dataTable", new ApplicationSnapSessionDataTable());
        VolumeGroupRestRep application = AppSupportUtil.getApplication(id);
        String sessionLabel = id + "~~~" + sessionSet;
        render(application, sessionLabel, sessionSet);
    }
    
    public static void getAssociatedSnapSessionJSON(String sessionLabel) {
        String[] sessionSet = sessionLabel.split("~~~");
        List<ApplicationSnapshotSession> sessionDetails = Lists.newArrayList();
        List<NamedRelatedResourceRep> sessionSetDetails = AppSupportUtil.getVolumeGroupSnapshotSessionsByCopySet(sessionSet[0],
                sessionSet[1]);
        for (NamedRelatedResourceRep snapSession : sessionSetDetails) {
            BlockSnapshotSessionRestRep snapshotSession = BourneUtil.getViprClient().blockSnapshotSessions().get(snapSession.getId());
            sessionDetails.add(new ApplicationSnapshotSession(snapshotSession));
        }
        renderJSON(DataTablesSupport.createJSON(sessionDetails, params));
    }
    
    public static class VolumeApplicationDataTable extends BlockVolumesDataTable {
        public VolumeApplicationDataTable() {
            alterColumn("protocols").hidden();
            alterColumn("wwn").hidden();
            addColumn("associatedVolumeRG");
            sortAll();
        }

        public static class VolumeApplication extends Volume {
            public RelatedResourceRep associatedSourceVolume;
            public VolumeRestRep associatedVolume;
            public String associatedVolumeRG;

            public VolumeApplication(VolumeRestRep volume) {
                super(volume, virtualArrays, virtualPools);
                if (volume.getProtection() != null && volume.getProtection().getFullCopyRep() != null) {
                    associatedSourceVolume = volume.getProtection().getFullCopyRep().getAssociatedSourceVolume();
                }
                if (associatedSourceVolume != null) {
                    associatedVolume = BourneUtil.getViprClient().blockVolumes().get(associatedSourceVolume.getId());
                    associatedVolumeRG = associatedVolume.getReplicationGroupInstance();
                } else {
                    associatedVolumeRG = volume.getReplicationGroupInstance();
                }
            }
        }
    }
}
