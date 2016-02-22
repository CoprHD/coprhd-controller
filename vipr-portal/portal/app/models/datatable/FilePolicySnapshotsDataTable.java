/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import play.mvc.With;
import util.datatable.DataTable;
import controllers.Common;

@With(Common.class)
public class FilePolicySnapshotsDataTable extends DataTable {
    public FilePolicySnapshotsDataTable() {
        addColumn("id");
        addColumn("name");
        sortAll();
        setDefaultSort("name", "asc");

    }

    /*
     * public static List<FileSnapshot> fetch(URI fileSystemId, URI policyId) {
     * if (fileSystemId == null || policyId == null) {
     * return Collections.EMPTY_LIST;
     * }
     * 
     * ViPRCoreClient client = getViprClient();
     * SnapshotList fileSnapshots = client.fileSystems().getFilePolicySnapshots(fileSystemId, policyId);
     * 
     * List<FileSnapshot> results = Lists.newArrayList();
     * for (NamedRelatedResourceRep fileSnapshot : fileSnapshots.getSnapList()) {
     * results.add(new FileSnapshot(fileSnapshot));
     * }
     * return results;
     * }
     */
    public static class FileSnapshot {
        public String id;
        public String name;

        public FileSnapshot(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
