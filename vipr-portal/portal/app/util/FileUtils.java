/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.model.file.ExportRule;
import com.emc.storageos.model.file.FileSystemExportParam;
import com.emc.storageos.volumecontroller.FileShareExport;
import com.emc.vipr.client.ViPRCoreClient;
import com.google.common.collect.Lists;

public class FileUtils {

    public static List<ExportRule> getFSExportRules(URI id) {
        ViPRCoreClient client = BourneUtil.getViprClient();
        List<ExportRule> rules = Lists.newArrayList();

        if (URIUtil.isType(id, Snapshot.class)) {
            rules = client.fileSnapshots().getExport(id, true, "");
        } else if (URIUtil.isType(id, FileShare.class)) {
            rules = client.fileSystems().getExport(id, true, "");
        }

        return rules;
    }

    public static String findSubDirectory(String fsPath, String exportPath) {
        if (exportPath.length() > fsPath.length()) {
            return exportPath.substring(fsPath.length() + 1);
        } else {
            return "";
        }
    }

    public static ExportRuleInfo getFSExportRulesInfo(URI id, String exportPath, String security) {
        String path = StringUtils.defaultString(exportPath);
        String sec = StringUtils.defaultString(security);
        List<ExportRule> rules = getFSExportRules(id);
        List<EndpointInfo> infos = Lists.newArrayList();
        ExportRuleInfo exportRuleInfo = new ExportRuleInfo();
        for (ExportRule rule : rules) {
            if ((path.isEmpty() || rule.getExportPath().equals(path)) && (sec.isEmpty() || rule.getSecFlavor().equals(sec))) {
                if (rule.getReadWriteHosts() != null && !rule.getReadWriteHosts().isEmpty()) {
                    for (String endpoint : rule.getReadWriteHosts()) {
                        infos.add(new EndpointInfo(endpoint, FileShareExport.Permissions.rw.name()));
                    }
                }
                if (rule.getReadOnlyHosts() != null && !rule.getReadOnlyHosts().isEmpty()) {
                    for (String endpoint : rule.getReadOnlyHosts()) {
                        infos.add(new EndpointInfo(endpoint, FileShareExport.Permissions.ro.name()));
                    }
                }
                if (rule.getRootHosts() != null && !rule.getRootHosts().isEmpty()) {
                    for (String endpoint : rule.getRootHosts()) {
                        infos.add(new EndpointInfo(endpoint, FileShareExport.Permissions.root.name()));
                    }
                }
                exportRuleInfo.setAnon(rule.getAnon());
                exportRuleInfo.setSecurity(getSecurityFlavorList(rule.getSecFlavor()));
                exportRuleInfo.setEndpointsInfo(infos);
            }
        }
        return exportRuleInfo;
    }

    public static List<FileSystemExportParam> getExports(URI id) {
        ViPRCoreClient client = BourneUtil.getViprClient();
        return client.fileSystems().getExports(id);
    }

    public static Set<String> buildEndpointList(List<String> endpoints) {
        Set<String> endpointList = new HashSet<String>();
        for (String endpoint : endpoints) {
            if (!StringUtils.isEmpty(endpoint)) {
                endpointList.add(endpoint);
            }
        }
        return endpointList;
    }

    private static List<String> getSecurityFlavorList(String secFlo) {
    	List<String> secTypes = Lists.newArrayList();
    	for (String secType : secFlo.split(",")) {
    		secTypes.add(secType.trim());
    	}
    	return secTypes;
    }

    public static class EndpointInfo {
        public String endpoint;
        public String permission;

        public EndpointInfo(String endpoint, String permission) {
            this.endpoint = endpoint;
            this.permission = permission;
        }
    }

    public static class ExportRuleInfo {
        public String anon;
        public List<String> security;
        public List<EndpointInfo> endpoints;

        public ExportRuleInfo() {
        }

        public void setAnon(String anon) {
            this.anon = anon;
        }

        public void setSecurity(List<String> security) {
            this.security = security;
        }

        public void setEndpointsInfo(List<EndpointInfo> infos) {
            this.endpoints = infos;
        }
    }
  }
