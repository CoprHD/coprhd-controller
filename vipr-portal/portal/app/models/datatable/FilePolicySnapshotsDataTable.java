/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import java.text.SimpleDateFormat;
import java.util.Date;

import play.mvc.With;
import util.datatable.DataTable;

import com.emc.storageos.model.file.ScheduleSnapshotRestRep;

import controllers.Common;

@With(Common.class)
public class FilePolicySnapshotsDataTable extends DataTable {

    public static final String DATE_FORMAT = "d MMM yyyy HH:mm Z";

    public FilePolicySnapshotsDataTable() {
        addColumn("id").hidden();
        addColumn("name");
        addColumn("created");
        addColumn("expires");
        sortAll();
        setDefaultSort("name", "desc");

    }

    public static class FileSnapshot {

        public String id;
        public String name;
        public String created;
        public String expires;

        public FileSnapshot(ScheduleSnapshotRestRep snap) {
            SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
            this.created = sdf.format(new Date(Long.parseLong(snap.getCreated())));
            this.expires = sdf.format(new Date(Long.parseLong(snap.getExpires())));
            this.id = snap.getId().toString();
            this.name = snap.getName();

        }
    }
}
