package com.emc.vipr.sanity.setup

import static com.emc.vipr.sanity.setup.Sanity.*
import static com.emc.vipr.sanity.setup.LocalSystem.*
import com.emc.storageos.model.tenant.TenantCreateParam
import com.emc.storageos.model.project.ProjectParam
import com.emc.storageos.model.tenant.UserMappingParam
import com.emc.storageos.model.tenant.UserMappingAttributeParam

class ProjectSetup {
    static String tenantName = hostName
    static String projectName = "sanity"

    static projectId

    static void setup() {
        println "Setting up projects"
        projectId = client.projects().search().byExactName(projectName).first()?.id
        if (projectId == null) {
            projectId = client.projects().create(client.userTenantId, new ProjectParam(projectName)).id
        }
    }
}
