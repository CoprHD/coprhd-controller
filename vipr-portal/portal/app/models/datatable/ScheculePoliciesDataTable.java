/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import java.util.Map;

import com.emc.storageos.db.client.model.FilePolicy.FilePolicyApplyLevel;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.file.policy.FilePolicyRestRep;
import com.google.common.collect.Maps;

import util.datatable.DataTable;

public class ScheculePoliciesDataTable extends DataTable {

    public ScheculePoliciesDataTable() {
        addColumn("policyName").setRenderFunction("renderLink");
        addColumn("policyType");
        addColumn("appliedAt");
        addColumn("vPools");
        addColumn("projects");
        addColumn("priority");
        addColumn("description");
        sortAll();
        setDefaultSortField("policyName");
    }

    public static class ScheculePolicy {
        public String id;
        public String policyName;
        public String policyType;
        public String appliedAt;
        public String vPools;
        public String projects;
        public String priority;
        public String description;

        public ScheculePolicy(FilePolicyRestRep policy) {
            id = policy.getId().toString();
            policyName = policy.getName();
            policyType = getTranslatedPolicyType(policy.getType());
            appliedAt = policy.getAppliedAt();
            vPools = policy.getVpool().getName();
            projects = getProjectsFromPolicy(policy);
            priority = "";
            description = policy.getDescription();
        }

        private String getProjectsFromPolicy(FilePolicyRestRep policy) {
            StringBuffer projects = new StringBuffer();
            boolean first = true;
            if (FilePolicyApplyLevel.project.name().equals(policy.getAppliedAt())) {
                for (NamedRelatedResourceRep proj : policy.getAssignedResources()) {
                    if (first) {
                        projects.append(proj.getName());
                        first = false;
                    } else {
                        projects.append(",").append(proj.getName());
                    }
                }
            }
            return projects.toString();
        }

        private String getTranslatedPolicyType(String key) {
            Map<String, String> translatedProlicyType = Maps.newLinkedHashMap();
            translatedProlicyType.put("file_snapshot", "File Snapshot");
            return translatedProlicyType.get(key);
        }
    }

    public static class FileProtectionPolicy {
        public String id;
        public String policyName;
        public String policyType;

        public FileProtectionPolicy(FilePolicyRestRep policy) {
            id = policy.getId().toString();
            policyName = policy.getName();
            policyType = getTranslatedPolicyType(policy.getType());

        }

        private String getTranslatedPolicyType(String key) {
            Map<String, String> translatedProlicyType = Maps.newLinkedHashMap();
            if (key != null && key.contains("snapshot")) {
                translatedProlicyType.put("file_snapshot", "Snapshot");
            } else {
                translatedProlicyType.put("file_replication", "Replication");
            }
            return translatedProlicyType.get(key);
        }
    }

}
