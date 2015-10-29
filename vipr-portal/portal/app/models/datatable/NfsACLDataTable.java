/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import play.mvc.With;
import util.datatable.DataTable;
import controllers.Common;
import controllers.resources.FileSystems.NfsACLForm;

@With(Common.class)
public class NfsACLDataTable extends DataTable {

    public NfsACLDataTable() {
        addColumn("name").setRenderFunction("renderLink");
        addColumn("domain");
        addColumn("type");
        addColumn("permissions");
        sortAll();
        setDefaultSortField("name");
    }

    public static class NfsAclInfo {

        public String id;
        public String name;
        public String domain;
        public String type;
        public String permissions;
        public String fileSystem;
        public String fsMountPath;
        public String subDir;
        public String permissionType;

        public NfsAclInfo(String name, String type, String permissions,
                String fileSystem, String subDir, String domain,
                String fsMountPath, String permissionType) {
            this.name = name;
            this.domain = domain;
            this.type = type;
            this.permissions = permissions.replaceAll(",", "/");
            this.subDir = subDir;
            this.fileSystem = fileSystem;
            this.fsMountPath = fsMountPath;
            this.id = NfsACLForm.createId(this.name, this.type,
                    this.fileSystem, this.subDir, this.domain,
                    this.permissions, this.fsMountPath, this.permissionType);
        }
    }

}
