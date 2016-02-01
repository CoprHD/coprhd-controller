/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

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
        addColumn("title").setRenderFunction("renderLink");
        addColumn("description");
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
        public String id;
        public String name;
        public String description;
        
        public ApplicationSupport(NamedRelatedResourceRep application) {
            id = application.getId().toString();
            name = application.getName();
            description = AppSupportUtil.getApplication(id).getDescription();
        }
    }
}
