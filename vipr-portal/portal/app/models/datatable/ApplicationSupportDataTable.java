/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.application.VolumeGroupRestRep;
import com.google.common.collect.Lists;

import util.AppSupportUtil;
import util.datatable.DataTable;

/**
 * @author Ramya 
 */

public class ApplicationSupportDataTable extends DataTable {
    
	private static Set<String> roles = new HashSet(Arrays.asList("COPY"));

    public ApplicationSupportDataTable() {
        addColumn("name").setRenderFunction("renderLink");
        addColumn("description");
        setDefaultSort("name", "asc");
        setRowCallback("createRowLink");
    }
    
    public static List<ApplicationSupport> fetch() {
        List<ApplicationSupport> results = Lists.newArrayList();
		for (NamedRelatedResourceRep applications : AppSupportUtil
				.getApplications()) {
			VolumeGroupRestRep application = AppSupportUtil
					.getApplication(applications.getId().toString());
			if (application.getRoles().equals(roles)) {
				results.add(new ApplicationSupport(application));
			}

		}
        return results;
    }
    
    public static class ApplicationSupport {
        public String id;
        public String name;
        public String description;
        
        public ApplicationSupport(VolumeGroupRestRep application) {
            id = application.getId().toString();
            name = application.getName();
            description = application.getDescription();
        }
    }
}
