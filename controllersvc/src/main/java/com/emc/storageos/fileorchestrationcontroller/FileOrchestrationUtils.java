/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.fileorchestrationcontroller;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.CifsShareACL;
import com.emc.storageos.db.client.model.FileExportRule;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.model.file.ExportRule;
import com.emc.storageos.model.file.ShareACL;

/**
 * File orchestration Utility Class
 * 
 * @author Mudit Jain
 */

public class FileOrchestrationUtils {

    public static HashMap<String, List<ExportRule>> getFSExportRuleMap(FileShare fs, DbClient s_dbClient) {
        ContainmentConstraint containmentConstraint = ContainmentConstraint.Factory.getFileExportRulesConstraint(fs.getId());
        List<FileExportRule> fileExportRules = CustomQueryUtility.queryActiveResourcesByConstraint(s_dbClient, FileExportRule.class,
                containmentConstraint);
        Set<String> exportPaths = new HashSet<String>();

        for (FileExportRule fileExportRule : fileExportRules) {
            exportPaths.add(fileExportRule.getExportPath());
        }

        HashMap<String, List<ExportRule>> exportRulesMap = new HashMap<String, List<ExportRule>>();

        for (String exportPath : exportPaths) {
            List<ExportRule> exportRules = new ArrayList<ExportRule>();
            for (FileExportRule fileExportRule : fileExportRules) {
                if (fileExportRule.getExportPath().equals(exportPath)) {
                    ExportRule exportRule = new ExportRule();
                    exportRule.setAnon(fileExportRule.getAnon());
                    exportRule.setExportPath(fileExportRule.getExportPath());
                    exportRule.setFsID(fileExportRule.getFileSystemId());
                    exportRule.setMountPoint(fileExportRule.getMountPoint());
                    exportRule.setReadOnlyHosts(fileExportRule.getReadOnlyHosts());
                    exportRule.setReadWriteHosts(fileExportRule.getReadWriteHosts());
                    exportRule.setRootHosts(fileExportRule.getRootHosts());
                    exportRule.setSecFlavor(fileExportRule.getSecFlavor());
                    exportRule.setSnapShotID(fileExportRule.getSnapshotId());
                    exportRule.setDeviceExportId(fileExportRule.getDeviceExportId());
                    exportRules.add(exportRule);
                }
            }
            exportRulesMap.put(exportPath, exportRules);
        }
        return exportRulesMap;
    }

    public static void checkForExportRuleToAdd(FileShare sourceFileShare, FileShare targetFileShare, List<ExportRule> sourceExportRules,
            List<ExportRule> targetExportRules, List<ExportRule> exportRulesToAdd) {
        for (ExportRule sourceExportRule : sourceExportRules) {
            boolean isSecFlvPresentOnTarget = false;
            if (sourceExportRule.getExportPath().equals(sourceFileShare.getPath())) {
                // Not the sub directory Export Rule
                for (ExportRule targetExportRule : targetExportRules) {
                    if (targetExportRule.getExportPath().equals(targetFileShare.getPath()) &&
                            targetExportRule.getSecFlavor().equals(sourceExportRule.getSecFlavor())) {
                        isSecFlvPresentOnTarget = true;
                    }
                }
                if (!isSecFlvPresentOnTarget) {
                    ExportRule exportRule = new ExportRule();
                    exportRule.setFsID(targetFileShare.getId());
                    exportRule.setExportPath(targetFileShare.getPath());
                    exportRule.setAnon(sourceExportRule.getAnon());
                    exportRule.setReadOnlyHosts(sourceExportRule.getReadOnlyHosts());
                    exportRule.setRootHosts(sourceExportRule.getRootHosts());
                    exportRule.setReadWriteHosts(sourceExportRule.getReadWriteHosts());
                    exportRule.setSecFlavor(sourceExportRule.getSecFlavor());
                    exportRulesToAdd.add(exportRule);
                }

            } else {
                // Sub directory Export Rule
                for (ExportRule targetExportRule : targetExportRules) {
                    if (!targetExportRule.getExportPath().equals(targetFileShare.getPath()) &&
                            targetExportRule.getSecFlavor().equals(sourceExportRule.getSecFlavor())) {
                        isSecFlvPresentOnTarget = true;
                    }
                }
                if (!isSecFlvPresentOnTarget) {
                    ExportRule exportRule = new ExportRule();
                    exportRule.setFsID(targetFileShare.getId());
                    ArrayList<String> subdirName = new ArrayList<String>();
                    subdirName.add(sourceExportRule.getExportPath().split(sourceFileShare.getPath())[1]);
                    exportRule.setExportPath(targetFileShare.getPath() + subdirName.get(0));
                    exportRule.setAnon(sourceExportRule.getAnon());
                    exportRule.setReadOnlyHosts(sourceExportRule.getReadOnlyHosts());
                    exportRule.setRootHosts(sourceExportRule.getRootHosts());
                    exportRule.setReadWriteHosts(sourceExportRule.getReadWriteHosts());
                    exportRule.setSecFlavor(sourceExportRule.getSecFlavor());
                    exportRulesToAdd.add(exportRule);
                }
            }
        }
    }

    public static void checkForExportRuleToDelete(FileShare sourceFileShare, FileShare targetFileShare, List<ExportRule> sourceExportRules,
            List<ExportRule> targetExportRules, List<ExportRule> exportRulesToDelete) {
        for (ExportRule targetExportRule : targetExportRules) {
            boolean isSecFlvPresentOnSource = false;
            if (targetExportRule.getExportPath().equals(targetFileShare.getPath())) {
                // Not the sub directory Export Rule
                for (ExportRule sourceExportRule : sourceExportRules) {
                    if (sourceExportRule.getExportPath().equals(sourceFileShare.getPath()) &&
                            sourceExportRule.getSecFlavor().equals(targetExportRule.getSecFlavor())) {
                        isSecFlvPresentOnSource = true;
                    }
                }
                if (!isSecFlvPresentOnSource) {
                    exportRulesToDelete.add(targetExportRule);
                }

            } else {
                // Sub directory Export Rule
                for (ExportRule sourceExportRule : sourceExportRules) {
                    if (!sourceExportRule.getExportPath().equals(sourceFileShare.getPath()) &&
                            sourceExportRule.getSecFlavor().equals(targetExportRule.getSecFlavor())) {
                        isSecFlvPresentOnSource = true;
                    }
                }
                if (!isSecFlvPresentOnSource) {
                    exportRulesToDelete.add(targetExportRule);
                }
            }
        }
    }

    public static void checkForExportRuleToModify(FileShare sourceFileShare, FileShare targetFileShare, List<ExportRule> sourceExportRules,
            List<ExportRule> targetExportRules, List<ExportRule> exportRulesToModify) {
        for (ExportRule sourceExportRule : sourceExportRules) {
            if (sourceExportRule.getExportPath().equals(sourceFileShare.getPath())) {
                // Not the sub directory Export Rule
                for (ExportRule targetExportRule : targetExportRules) {
                    if (targetExportRule.getExportPath().equals(targetFileShare.getPath()) &&
                            targetExportRule.getSecFlavor().equals(sourceExportRule.getSecFlavor())) {
                        checkForExportRuleEndpointsToModify(sourceExportRule, targetExportRule, exportRulesToModify);
                    }
                }
            } else {
                // Sub directory Export Rule
                for (ExportRule targetExportRule : targetExportRules) {
                    if (!targetExportRule.getExportPath().equals(targetFileShare.getPath()) &&
                            targetExportRule.getSecFlavor().equals(sourceExportRule.getSecFlavor())) {
                        checkForExportRuleEndpointsToModify(sourceExportRule, targetExportRule, exportRulesToModify);
                    }
                }
            }
        }
    }

    public static void checkForExportRuleEndpointsToModify(ExportRule sourceExportRule, ExportRule targetExportRule,
            List<ExportRule> exportRulesToModify) {
        boolean isExportRuleToModify = false;

        if (sourceExportRule.getReadWriteHosts() == null && targetExportRule.getReadWriteHosts() == null) {
            // Both Source and Target export rule don't have any read-write host...
        } else if ((sourceExportRule.getReadWriteHosts() == null && targetExportRule.getReadWriteHosts() != null)
                || (!sourceExportRule.getReadWriteHosts().equals(targetExportRule.getReadWriteHosts()))) {
            isExportRuleToModify = true;
            targetExportRule.setReadWriteHosts(sourceExportRule.getReadWriteHosts());
        }

        if (sourceExportRule.getReadOnlyHosts() == null && targetExportRule.getReadOnlyHosts() == null) {
            // Both Source and Target export rule don't have any read-only host...
        } else if ((sourceExportRule.getReadOnlyHosts() == null && targetExportRule.getReadOnlyHosts() != null)
                || (!sourceExportRule.getReadOnlyHosts().equals(targetExportRule.getReadOnlyHosts()))) {
            isExportRuleToModify = true;
            targetExportRule.setReadOnlyHosts(sourceExportRule.getReadOnlyHosts());
        }

        if (sourceExportRule.getRootHosts() == null && targetExportRule.getRootHosts() == null) {
            // Both Source and Target export rule don't have any root host...
        } else if ((sourceExportRule.getRootHosts() == null && targetExportRule.getRootHosts() != null)
                || (!sourceExportRule.getRootHosts().equals(targetExportRule.getRootHosts()))) {
            isExportRuleToModify = true;
            targetExportRule.setRootHosts(sourceExportRule.getRootHosts());
        }

        if (sourceExportRule.getAnon() != null && !sourceExportRule.getAnon().equals(targetExportRule.getAnon())) {
            isExportRuleToModify = true;
            targetExportRule.setAnon(sourceExportRule.getAnon());
        }
        if (isExportRuleToModify) {
            exportRulesToModify.add(targetExportRule);
        }
    }

    public static List<ShareACL> queryShareACLs(String shareName, URI fs, DbClient s_dbClient) {

        List<ShareACL> aclList = new ArrayList<ShareACL>();
        ContainmentConstraint containmentConstraint = ContainmentConstraint.Factory.getFileCifsShareAclsConstraint(fs);
        List<CifsShareACL> shareAclList = CustomQueryUtility.queryActiveResourcesByConstraint(s_dbClient, CifsShareACL.class,
                containmentConstraint);

        if (shareAclList != null) {
            Iterator<CifsShareACL> shareAclIter = shareAclList.iterator();
            while (shareAclIter.hasNext()) {

                CifsShareACL dbShareAcl = shareAclIter.next();
                if (shareName.equals(dbShareAcl.getShareName())) {
                    ShareACL acl = new ShareACL();
                    acl.setShareName(shareName);
                    acl.setDomain(dbShareAcl.getDomain());
                    acl.setUser(dbShareAcl.getUser());
                    acl.setGroup(dbShareAcl.getGroup());
                    acl.setPermission(dbShareAcl.getPermission());
                    acl.setFileSystemId(fs);
                    aclList.add(acl);
                }
            }
        }
        return aclList;
    }
}
