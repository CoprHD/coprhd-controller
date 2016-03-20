/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.resources;

import static com.emc.vipr.client.core.util.ResourceUtils.id;

import java.net.URI;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import play.mvc.Controller;
import play.mvc.Util;
import util.ProjectUtils;

import com.emc.sa.util.ResourceType;
import com.emc.storageos.model.project.ProjectRestRep;

import controllers.tenant.TenantSelector;
import controllers.util.Models;

public class ResourceController extends Controller {

    public static final String ACTIVE_PROJECT_ID = "activeProjectId";

    protected static void addReferenceData() {
        TenantSelector.addRenderArgs();
        String tenantId = Models.currentAdminTenant();
        renderArgs.put("projects", getProjects(tenantId));
        getActiveProjectId(); // called here to make sure active project id is init
    }

    @Util
    public static List<ProjectRestRep> getProjects(String tenantId) {
        List<ProjectRestRep> projects = ProjectUtils.getProjects(tenantId);
        Collections.sort(projects, new Comparator<ProjectRestRep>() {
            @Override
            public int compare(ProjectRestRep proj1, ProjectRestRep proj2)
            {
                return proj1.getName().compareTo(proj2.getName());
            }
        });
        return projects;
    }

    
    @Util
    public static String getActiveProjectId() {
        String tenantId = Models.currentAdminTenant();
        String activeProjectId = session.get(ACTIVE_PROJECT_ID);
        if (validateActiveProjectId(activeProjectId) == false) {
            List<ProjectRestRep> projects = getProjects(tenantId);
            if (!projects.isEmpty()) {
                activeProjectId = id(projects.get(0)).toString();
                setActiveProjectId(activeProjectId);
            }
        }
        return activeProjectId;
    }

    @Util
    private static boolean validateActiveProjectId(String activeProjectId) {
        if (StringUtils.isNotBlank(activeProjectId)) {
            URI activeProjectIdUri = URI.create(activeProjectId);
            String tenantId = Models.currentAdminTenant();
            List<ProjectRestRep> projects = getProjects(tenantId);
            for (ProjectRestRep project : projects) {
                if (project.getId().equals(activeProjectIdUri)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Util
    public static void setActiveProjectId(String activeProjectId) {
            session.put(ACTIVE_PROJECT_ID, activeProjectId);
    }

}
