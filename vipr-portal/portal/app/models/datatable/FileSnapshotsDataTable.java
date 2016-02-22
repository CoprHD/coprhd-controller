/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import util.datatable.DataTable;

import com.emc.storageos.model.file.FileSnapshotRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.google.common.collect.Lists;

import controllers.resources.FileSnapshots;

public class FileSnapshotsDataTable extends DataTable {

    public FileSnapshotsDataTable() {
        addColumn("fileSystemId").hidden();
        addColumn("name");
        addColumn("policyId");
        sortAll();
        setDefaultSort("name", "asc");
        setRowCallback("createRowLink");
    }

    public static List<FileSnapshot> fetch(URI projectId) {
        if (projectId == null) {
            return Collections.EMPTY_LIST;
        }

        ViPRCoreClient client = getViprClient();
        List<FileSnapshotRestRep> fileSnapshots = client.fileSnapshots().findByProject(projectId);

        List<FileSnapshot> results = Lists.newArrayList();
        for (FileSnapshotRestRep fileSnapshot : fileSnapshots) {
            results.add(new FileSnapshot(fileSnapshot));
        }
        return results;
    }

    public static class FileSnapshot {
        public String rowLink;
        public URI id;
        public String name;

        public FileSnapshot(FileSnapshotRestRep fileSnapshot) {
            id = fileSnapshot.getId();
            rowLink = createLink(FileSnapshots.class, "snapshot", "snapshotId", id);
            name = fileSnapshot.getName();
        }
    }
}
