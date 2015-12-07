/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import java.net.URI;
import java.util.List;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.google.common.collect.Lists;

import util.AppSupportUtil;
import util.datatable.DataTable;

/**
 * @author Ramya 
 */

public class ApplicationSupportDataTable extends DataTable {
    
    public ApplicationSupportDataTable() {
        addColumn("id").hidden();
        addColumn("name").setRenderFunction("renderLink");
        setDefaultSort("name", "asc");
        setRowCallback("createRowLink");
    }
    
    public static List<ApplicationSupport> fetch() {
        List<ApplicationSupport> results = Lists.newArrayList();
        for (NamedRelatedResourceRep application : AppSupportUtil.getApplications()) {
            results.add(new ApplicationSupport(application));
        }
        return results;
    }
    
    public static class ApplicationSupport {
        public URI id;
        public String name;
        
        public ApplicationSupport(NamedRelatedResourceRep application) {
            id = application.getId();
            name = application.getName();
        }
    }
}
