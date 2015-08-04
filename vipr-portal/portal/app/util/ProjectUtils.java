/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.List;

import com.emc.storageos.model.auth.ACLAssignmentChanges;
import com.emc.storageos.model.auth.ACLEntry;
import com.emc.storageos.model.project.ProjectParam;
import com.emc.storageos.model.project.ProjectRestRep;
import com.emc.storageos.model.project.ProjectUpdateParam;
import com.emc.storageos.model.quota.QuotaInfo;
import com.emc.storageos.model.quota.QuotaUpdateParam;
import com.emc.vipr.client.exceptions.ViPRHttpException;

public class ProjectUtils {
    public static ProjectRestRep getProject(String id) {
        try {
            return getProject(uri(id));
        } catch (ViPRHttpException e) {
            if (e.getHttpCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    public static ProjectRestRep getProject(URI id) {
        try {
            return (id != null) ? getViprClient().projects().get(id) : null;
        } catch (ViPRHttpException e) {
            if (e.getHttpCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    public static List<ProjectRestRep> getProjects(String tenantId) {
        return getViprClient().projects().getByTenant(uri(tenantId));
    }

    public static List<ProjectRestRep> getProjects(List<URI> ids) {
        return getViprClient().projects().getByIds(ids);
    }

    public static void deactivate(URI id) {
        getViprClient().projects().deactivate(id);
    }

    public static ProjectRestRep create(String tenantId, ProjectParam group) {
        return getViprClient().projects().create(uri(tenantId), group);
    }

    public static void update(String id, ProjectUpdateParam group) {
        getViprClient().projects().update(uri(id), group);
    }

    public static QuotaInfo getQuota(String id) {
        return getViprClient().projects().getQuota(uri(id));
    }

    public static QuotaInfo updateQuota(String id, boolean enable, Long sizeInGB) {
        if (enable) {
            return enableQuota(id, sizeInGB);
        }
        else {
            return disableQuota(id);
        }
    }

    public static QuotaInfo enableQuota(String id, Long sizeInGB) {
        return getViprClient().projects().updateQuota(uri(id), new QuotaUpdateParam(true, sizeInGB));
    }

    public static QuotaInfo disableQuota(String id) {
        return getViprClient().projects().updateQuota(uri(id), new QuotaUpdateParam(false, null));
    }

    public static List<ACLEntry> getACLs(String id) {
        return getViprClient().projects().getACLs(uri(id));
    }

    public static List<ACLEntry> updateACLs(String id, ACLAssignmentChanges changes) {
        return getViprClient().projects().updateACLs(uri(id), changes);

    }
}
