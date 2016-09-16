/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import java.io.Serializable;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.model.file.ExportRule;
import com.emc.storageos.model.file.FileSystemExportParam;
import com.emc.storageos.model.file.FileExportUpdateParams.ExportOperationErrorType;
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
                exportRuleInfo.setSecurity(rule.getSecFlavor());
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

    private static List<String> getSecFlovorList(String secFlo) {
    	List<String> secTypes = Lists.newArrayList();
    	for (String secType : secFlo.split(",")) {
    		secTypes.add(secType.trim());
    	}
    	return secTypes;
    }
    
    public static List<NFSExportRule> getNFSExportRules(URI id) {
    	List<NFSExportRule> nfsExportRules = Lists.newArrayList();
    	
    	for (ExportRule exportRule : getFSExportRules(id)) {
    		NFSExportRule nfsRule = new NFSExportRule();
    		nfsRule.setAnon(exportRule.getAnon());
    		nfsRule.setComments(exportRule.getComments());
    		nfsRule.setExportPath(exportRule.getExportPath());
    		nfsRule.setMountPoint(exportRule.getMountPoint());
    		nfsRule.setSecFlavor(getSecFlovorList(exportRule.getSecFlavor()));
    		nfsRule.setReadOnlyHosts(exportRule.getReadOnlyHosts());
    		nfsRule.setReadWriteHosts(exportRule.getReadWriteHosts());
    		nfsRule.setRootHosts(exportRule.getRootHosts());
    		
    		nfsExportRules.add(nfsRule);
    	}
    	
    	return nfsExportRules;	
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
        public String security;
        public List<EndpointInfo> endpoints;

        public ExportRuleInfo() {
        }

        public void setAnon(String anon) {
            this.anon = anon;
        }

        public void setSecurity(String security) {
            this.security = security;
        }

        public void setEndpointsInfo(List<EndpointInfo> infos) {
            this.endpoints = infos;
        }
    }
    
    public static class NFSExportRule {
    	private String exportPath;
    	private String anon;
    	private List<String> secFlavor;
    	private Set<String> readOnlyHosts;
    	private Set<String> readWriteHosts;
    	private Set<String> rootHosts;
    	private String mountPoint;
    	private String comments;

    	public String getExportPath() {
    		return exportPath;
    	}

    	public void setExportPath(String exportPath) {
    		this.exportPath = exportPath;
    	}

    	public Set<String> getReadOnlyHosts() {
    		return readOnlyHosts;
    	}

    	public void setReadOnlyHosts(Set<String> readOnlyHosts) {
    		this.readOnlyHosts = readOnlyHosts;
    	}

    	public Set<String> getReadWriteHosts() {
    		return readWriteHosts;
    	}

    	public void setReadWriteHosts(Set<String> readWriteHosts) {
    		this.readWriteHosts = readWriteHosts;
    	}

    	public Set<String> getRootHosts() {
    		return rootHosts;
    	}

    	public void setRootHosts(Set<String> rootHosts) {
    		this.rootHosts = rootHosts;
    	}

    	/**
    	 * Security flavor of an export e.g. sys, krb, krbp or krbi
    	 * 
    	 */
    	public List<String> getSecFlavor() {
    		return secFlavor;
    	}

    	public void setSecFlavor(List<String> secFlavor) {
    		this.secFlavor = secFlavor;
    	}

    	/**
    	 * Anonymous root user mapping e.g. "root", "nobody" or "anyUserName"
    	 * 
    	 */
    	@XmlElement(name = "anon", required = false)
    	public String getAnon() {
    		return anon;
    	}

    	public void setAnon(String anon) {
    		this.anon = anon;
    	}

    	@XmlElement(name = "mountPoint", required = false)
    	public String getMountPoint() {
    		return mountPoint;
    	}

    	public void setMountPoint(String mountPoint) {
    		this.mountPoint = mountPoint;
    	}

    	@XmlElement(name = "comments", required = false)
    	public String getComments() {
    		return comments;
    	}

    	public void setComments(String comments) {
    		this.comments = comments;
    	}
     }
  }
