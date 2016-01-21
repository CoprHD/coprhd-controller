/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import play.mvc.With;
import util.datatable.DataTable;
import controllers.Common;
import controllers.resources.FileSnapshots.SnapshotShareACLForm;
import controllers.resources.FileSystems.ShareACLForm;

@With(Common.class)
public class ShareACLDataTable extends DataTable {

    public ShareACLDataTable() {
        addColumn("name").setRenderFunction("renderLink");
        addColumn("domain");
        addColumn("type");
        addColumn("permission");
        sortAll();
        setDefaultSortField("name");
    }

    public static class AclInfo {

        public String id;
        public String name;
        public String domain;
        public String type;
        public String permission;
        public String fileSystem;
        public String shareName;

        public AclInfo(String name, String type, String permission, String fileSystem, String shareName, String domain) {
            this.name = name;
            this.domain = domain;
            this.type = type;
            this.permission = permission;
            this.shareName = shareName;
            this.fileSystem = fileSystem;
            id = ShareACLForm.createId(this.name, this.type, this.fileSystem, this.shareName, this.domain, this.permission);
        }
    }
    
    public static class SnapshotAclInfo {

        public String id;
        public String name;
        public String type;
        public String permission;
        public String snapshotId;
        public String shareName;
        public String domain;

        public SnapshotAclInfo(String name, String type, String permission, String snapshotId, String shareName, String domain) {
            this.name = name;
            this.type = type;
            this.permission = permission;
            this.shareName = shareName;
            this.snapshotId = snapshotId;
            this.domain = domain;
            id = SnapshotShareACLForm.createId(this.name, this.type, this.snapshotId, this.shareName, this.domain);
        }
    }

}
