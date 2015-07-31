/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import util.datatable.DataTable;

import com.emc.storageos.model.project.ProjectRestRep;

public class ProjectsDataTable extends DataTable {

    public ProjectsDataTable() {
        addColumn("name").setRenderFunction("renderLink");
        addColumn("owner");
        sortAll();
        setDefaultSortField("name");
    }

    public static class Project {
        public String id;
        public String name;
        public String owner;

        public Project(ProjectRestRep project) {
            id = project.getId().toString();
            name = project.getName();
            owner = project.getOwner();
        }
    }
}
