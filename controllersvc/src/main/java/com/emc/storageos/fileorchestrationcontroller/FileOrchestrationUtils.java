/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.fileorchestrationcontroller;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.CifsShareACL;
import com.emc.storageos.db.client.model.FileExport;
import com.emc.storageos.db.client.model.FileExportRule;
import com.emc.storageos.db.client.model.FilePolicy;
import com.emc.storageos.db.client.model.FilePolicy.FilePolicyApplyLevel;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.NASServer;
import com.emc.storageos.db.client.model.NFSShareACL;
import com.emc.storageos.db.client.model.PhysicalNAS;
import com.emc.storageos.db.client.model.PolicyStorageResource;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.model.file.ExportRule;
import com.emc.storageos.model.file.FileNfsACLUpdateParams;
import com.emc.storageos.model.file.NfsACE;
import com.emc.storageos.model.file.ShareACL;
import com.emc.storageos.volumecontroller.FileControllerConstants;

/**
 * File orchestration Utility Class
 * 
 * @author Mudit Jain
 */

public final class FileOrchestrationUtils {
    private static final Logger _log = LoggerFactory.getLogger(FileOrchestrationUtils.class);

    private FileOrchestrationUtils() {

    }

    /**
     * This method generates export map for the file system export rules.
     * 
     * @param fs File System Object
     * @param dbClient
     * @return
     */
    public static HashMap<String, List<ExportRule>> getFSExportRuleMap(FileShare fs, DbClient dbClient) {
        ContainmentConstraint containmentConstraint = ContainmentConstraint.Factory.getFileExportRulesConstraint(fs.getId());
        List<FileExportRule> fileExportRules = CustomQueryUtility.queryActiveResourcesByConstraint(dbClient, FileExportRule.class,
                containmentConstraint);

        HashMap<String, List<ExportRule>> exportRulesMap = new HashMap<String, List<ExportRule>>();
        for (FileExportRule fileExportRule : fileExportRules) {
            if (exportRulesMap.get(fileExportRule.getExportPath()) == null) {
                List<ExportRule> exportRules = new ArrayList<ExportRule>();
                ExportRule exportRule = convertFileExportRuleToExportRule(fileExportRule);
                exportRules.add(exportRule);
                exportRulesMap.put(fileExportRule.getExportPath(), exportRules);
            } else {
                List<ExportRule> exportRules = exportRulesMap.get(fileExportRule.getExportPath());
                ExportRule exportRule = convertFileExportRuleToExportRule(fileExportRule);
                exportRules.add(exportRule);
            }
        }
        return exportRulesMap;
    }

    /**
     * 
     * @param fileExportRule
     * @return ExportRule
     */
    public static ExportRule convertFileExportRuleToExportRule(FileExportRule fileExportRule) {
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
        return exportRule;
    }

    /**
     * 
     * @param exportRules
     * @return HashMap<String, ExportRule>
     */
    public static HashMap<String, ExportRule> getExportRuleSecFlvMap(List<ExportRule> exportRules) {
        HashMap<String, ExportRule> exportRulesMap = new HashMap<String, ExportRule>();
        for (ExportRule exportRule : exportRules) {
            exportRulesMap.put(exportRule.getSecFlavor(), exportRule);
        }
        return exportRulesMap;
    }

    /**
     * 
     * @param fileExports
     * @return fileExportMap
     */
    public static HashMap<String, FileExport> getFileExportMap(List<FileExport> fileExports) {
        HashMap<String, FileExport> fileExportMap = new HashMap<String, FileExport>();
        for (FileExport fileExport : fileExports) {
            fileExportMap.put(fileExport.getPath(), fileExport);
        }
        return fileExportMap;
    }

    /**
     * This method checks for export rules that has to added on target file system
     * 
     * @param sourceFileShare
     * @param targetFileShare
     * @param sourceExportRuleMap
     * @param targetExportRuleMap
     * @param exportRulesToAdd
     */
    public static void checkForExportRuleToAdd(FileShare sourceFileShare, FileShare targetFileShare,
            HashMap<String, ExportRule> sourceExportRuleMap,
            HashMap<String, ExportRule> targetExportRuleMap, List<ExportRule> exportRulesToAdd) {

        for (String secFlavour : sourceExportRuleMap.keySet()) {
            if (!targetExportRuleMap.containsKey(secFlavour)) {
                ExportRule sourceExportRule = sourceExportRuleMap.get(secFlavour);
                ExportRule exportRule = new ExportRule();
                exportRule.setFsID(targetFileShare.getId());

                if (sourceExportRule.getExportPath().equals(sourceFileShare.getPath())) {
                    exportRule.setExportPath(targetFileShare.getPath());
                } else {
                    ArrayList<String> subdirName = new ArrayList<String>();
                    subdirName.add(sourceExportRule.getExportPath().split(sourceFileShare.getPath())[1]);
                    exportRule.setExportPath(targetFileShare.getPath() + subdirName.get(0));
                }
                exportRule.setAnon(sourceExportRule.getAnon());
                exportRule.setReadOnlyHosts(sourceExportRule.getReadOnlyHosts());
                exportRule.setRootHosts(sourceExportRule.getRootHosts());
                exportRule.setReadWriteHosts(sourceExportRule.getReadWriteHosts());
                exportRule.setSecFlavor(sourceExportRule.getSecFlavor());
                exportRulesToAdd.add(exportRule);
            }
        }
    }

    /**
     * This method checks for export rules that has to deleted on target file system
     * 
     * @param sourceExportRuleMap
     * @param targetExportRuleMap
     * @param exportRulesToDelete
     */
    public static void checkForExportRuleToDelete(HashMap<String, ExportRule> sourceExportRuleMap,
            HashMap<String, ExportRule> targetExportRuleMap, List<ExportRule> exportRulesToDelete) {

        for (String secFlavour : targetExportRuleMap.keySet()) {
            if (!sourceExportRuleMap.containsKey(secFlavour)) {
                exportRulesToDelete.add(targetExportRuleMap.get(secFlavour));
            }
        }
    }

    /**
     * This method checks for export rules that has to modified on target file system
     * 
     * @param sourceExportRuleMap
     * @param targetExportRuleMap
     * @param exportRulesToModify
     */
    public static void checkForExportRuleToModify(HashMap<String, ExportRule> sourceExportRuleMap,
            HashMap<String, ExportRule> targetExportRuleMap, List<ExportRule> exportRulesToModify) {

        for (String secFlavour : sourceExportRuleMap.keySet()) {
            if (targetExportRuleMap.get(secFlavour) != null) {
                boolean isExportRuleToModify = false;
                ExportRule sourceExportRule = sourceExportRuleMap.get(secFlavour);
                ExportRule targetExportRule = targetExportRuleMap.get(secFlavour);

                // Check for RW Hosts
                if (isEndPointsDifferent(sourceExportRule.getReadWriteHosts(), targetExportRule.getReadWriteHosts())) {
                    isExportRuleToModify = true;
                    targetExportRule.setReadWriteHosts(sourceExportRule.getReadWriteHosts());
                }
                // Check for RO Hosts
                if (isEndPointsDifferent(sourceExportRule.getReadOnlyHosts(), targetExportRule.getReadOnlyHosts())) {
                    isExportRuleToModify = true;
                    targetExportRule.setReadOnlyHosts(sourceExportRule.getReadOnlyHosts());
                }
                // Check for Root Hosts
                if (isEndPointsDifferent(sourceExportRule.getRootHosts(), targetExportRule.getRootHosts())) {
                    isExportRuleToModify = true;
                    targetExportRule.setRootHosts(sourceExportRule.getRootHosts());
                }
                // Check for Anon
                if (sourceExportRule.getAnon() != null && !sourceExportRule.getAnon().equals(targetExportRule.getAnon())) {
                    isExportRuleToModify = true;
                    targetExportRule.setAnon(sourceExportRule.getAnon());
                }
                if (isExportRuleToModify) {
                    exportRulesToModify.add(targetExportRule);
                }
            }
        }
    }

    private static boolean isEndPointsDifferent(Set<String> sourceEndPoints, Set<String> targetEndPoints) {

        if (sourceEndPoints == null && targetEndPoints == null) {
            return false;
        }
        if (sourceEndPoints == null && targetEndPoints != null) {
            return true;
        }
        if (sourceEndPoints != null && !sourceEndPoints.equals(targetEndPoints)) {
            return true;
        }
        return false;
    }

    /**
     * This method queries ACLs for File System share.
     * 
     * @param shareName Name of the share.
     * @param fs URI of the file system.
     * @param dbClient
     * @return ListShareACL
     */
    public static List<ShareACL> queryShareACLs(String shareName, URI fs, DbClient dbClient) {

        List<ShareACL> aclList = new ArrayList<ShareACL>();
        ContainmentConstraint containmentConstraint = ContainmentConstraint.Factory.getFileCifsShareAclsConstraint(fs);
        List<CifsShareACL> shareAclList = CustomQueryUtility.queryActiveResourcesByConstraint(dbClient, CifsShareACL.class,
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

    /**
     * This method generates map for the share ACLs with user/group as key.
     * 
     * @param shareACLs
     * @return
     */
    public static HashMap<String, ShareACL> getShareACLMap(List<ShareACL> shareACLs) {
        HashMap<String, ShareACL> shareACLMap = new HashMap<String, ShareACL>();
        for (ShareACL shareACL : shareACLs) {
            if (shareACL.getUser() != null && !shareACL.getUser().isEmpty()) {
                shareACLMap.put(shareACL.getUser(), shareACL);
            } else {
                shareACLMap.put(shareACL.getGroup(), shareACL);
            }
        }
        return shareACLMap;
    }

    public static HashMap<String, NfsACE> getUserToNFSACEMap(List<NfsACE> nfsACL) {
        HashMap<String, NfsACE> aclMap = new HashMap<String, NfsACE>();
        if (nfsACL != null && !nfsACL.isEmpty()) {
            String user = null;
            String domain = null;
            for (NfsACE ace : nfsACL) {
                domain = ace.getDomain();
                user = ace.getUser();
                user = domain == null ? "null+" + user : domain + "+" + user;
                if (user != null && !user.isEmpty()) {
                    aclMap.put(user, ace);
                }
            }
        }
        return aclMap;
    }

    public static Map<String, List<NfsACE>> queryNFSACL(FileShare fs, DbClient dbClient) {

        Map<String, List<NfsACE>> map = new HashMap<String, List<NfsACE>>();
        ContainmentConstraint containmentConstraint = ContainmentConstraint.Factory.getFileNfsAclsConstraint(fs.getId());
        List<NFSShareACL> nfsAclList = CustomQueryUtility
                .queryActiveResourcesByConstraint(dbClient, NFSShareACL.class, containmentConstraint);

        if (nfsAclList != null) {
            Iterator<NFSShareACL> aclIter = nfsAclList.iterator();
            while (aclIter.hasNext()) {
                NFSShareACL dbNFSAcl = aclIter.next();
                String fsPath = dbNFSAcl.getFileSystemPath();
                NfsACE ace = convertNFSShareACLToNfsACE(dbNFSAcl);
                if (map.get(fsPath) == null) {
                    List<NfsACE> acl = new ArrayList<NfsACE>();
                    acl.add(ace);
                    map.put(fsPath, acl);
                } else {
                    map.get(fsPath).add(ace);
                }
            }
        }
        return map;
    }

    public static NfsACE convertNFSShareACLToNfsACE(NFSShareACL dbNFSAcl) {
        NfsACE dest = new NfsACE();

        dest.setDomain(dbNFSAcl.getDomain());
        dest.setPermissions(dbNFSAcl.getPermissions());
        dest.setPermissionType(FileControllerConstants.NFS_FILE_PERMISSION_TYPE_ALLOW);
        if (dbNFSAcl.getPermissionType() != null && !dbNFSAcl.getPermissionType().isEmpty()) {
            dest.setPermissionType(dbNFSAcl.getPermissionType());
        }
        dest.setType("user");
        if (dbNFSAcl.getType() != null && !dbNFSAcl.getType().isEmpty()) {
            dest.setType(dbNFSAcl.getType());
        }
        dest.setUser(dbNFSAcl.getUser());
        return dest;
    }

    public static FileNfsACLUpdateParams getFileNfsACLUpdateParamWithSubDir(String fsPath, FileShare fs) {
        FileNfsACLUpdateParams params = new FileNfsACLUpdateParams();
        if (!fsPath.equals(fs.getPath())) {
            // Sub directory NFS ACL
            String subDir = fsPath.split(fs.getPath())[1];
            params.setSubDir(subDir.substring(1));
        }
        return params;
    }

    /**
     * 
     * @param dbClient
     * @param vpool
     * @param storageSystem
     * @return
     */
    public static List<FilePolicy> getAllVpoolLevelPolices(DbClient dbClient, VirtualPool vpool, NASServer nasServer, URI storageSystem) {
        List<FilePolicy> filePoliciesToCreate = new ArrayList<>();
        StringSet fileVpoolPolicies = vpool.getFilePolicies();

        if (fileVpoolPolicies != null && !fileVpoolPolicies.isEmpty()) {
            for (String fileVpoolPolicy : fileVpoolPolicies) {
                FilePolicy filePolicy = dbClient.queryObject(FilePolicy.class, URIUtil.uri(fileVpoolPolicy));
                filePoliciesToCreate.add(filePolicy);
                StringSet policyStrRes = filePolicy.getPolicyStorageResources();
                if (policyStrRes != null && !policyStrRes.isEmpty()) {
                    for (String policyStrRe : policyStrRes) {
                        PolicyStorageResource strRes = dbClient.queryObject(PolicyStorageResource.class, URIUtil.uri(policyStrRe));
                        if (strRes.getAppliedAt().toString().equals(vpool.getId().toString())
                                && strRes.getStorageSystem().toString().equals(storageSystem.toString())
                                && strRes.getNasServer().toString().equalsIgnoreCase(nasServer.getId().toString())) {
                            _log.info("File Policy {} is already for vpool {} , storage system {}", filePolicy.getFilePolicyName(),
                                    vpool.getLabel(), storageSystem.toString());
                            filePoliciesToCreate.remove(filePolicy);
                            break;
                        }
                    }
                }
            }
        }
        return filePoliciesToCreate;
    }

    /**
     * 
     * @param dbClient
     * @param project
     * @param storageSystem
     * @return
     */
    public static List<FilePolicy> getAllProjectLevelPolices(DbClient dbClient, Project project, VirtualPool vpool, NASServer nasServer,
            URI storageSystem) {
        List<FilePolicy> filePoliciesToCreate = new ArrayList<>();
        StringSet fileProjectPolicies = project.getFilePolicies();

        if (fileProjectPolicies != null && !fileProjectPolicies.isEmpty()) {
            for (String fileProjectPolicy : fileProjectPolicies) {
                FilePolicy filePolicy = dbClient.queryObject(FilePolicy.class, URIUtil.uri(fileProjectPolicy));
                if (!filePolicy.getFilePolicyVpool().toString().equals(vpool.getId().toString())) {
                    continue;
                }
                filePoliciesToCreate.add(filePolicy);
                StringSet policyStrRes = filePolicy.getPolicyStorageResources();
                if (policyStrRes != null && !policyStrRes.isEmpty()) {
                    for (String policyStrRe : policyStrRes) {
                        PolicyStorageResource strRes = dbClient.queryObject(PolicyStorageResource.class, URIUtil.uri(policyStrRe));
                        if (strRes.getAppliedAt().toString().equals(project.getId().toString())
                                && strRes.getStorageSystem().toString().equals(storageSystem.toString())
                                && strRes.getNasServer().toString().equalsIgnoreCase(nasServer.getId().toString())) {
                            _log.info("File Policy {} is already for project {} , storage system {}", filePolicy.getFilePolicyName(),
                                    project.getLabel(), storageSystem.toString());
                            filePoliciesToCreate.remove(filePolicy);
                            break;
                        }
                    }
                }
            }
        }
        return filePoliciesToCreate;
    }

    public static void updateUnAssignedResource(FilePolicy filePolicy, PolicyStorageResource policyRes, DbClient dbClient) {
        FilePolicyApplyLevel applyLevel = FilePolicyApplyLevel.valueOf(filePolicy.getApplyAt());
        switch (applyLevel) {
            case vpool:
                VirtualPool vpool = dbClient.queryObject(VirtualPool.class, policyRes.getAppliedAt());
                vpool.removeFilePolicy(filePolicy.getId());
                dbClient.updateObject(vpool);
                break;
            case project:
                Project project = dbClient.queryObject(Project.class, policyRes.getAppliedAt());
                project.removeFilePolicy(project, filePolicy.getId());
                dbClient.updateObject(project);
                break;
            case file_system:
                FileShare fs = dbClient.queryObject(FileShare.class, policyRes.getAppliedAt());
                fs.removeFilePolicy(filePolicy.getId());
                dbClient.updateObject(fs);
                break;
            default:
                _log.error("Not a valid policy apply level: " + applyLevel);
        }
    }

    public static void updateUnAssignedResource(FilePolicy filePolicy, URI unassignRes, DbClient dbClient) {
        FilePolicyApplyLevel applyLevel = FilePolicyApplyLevel.valueOf(filePolicy.getApplyAt());
        switch (applyLevel) {
            case vpool:
                VirtualPool vpool = dbClient.queryObject(VirtualPool.class, unassignRes);
                vpool.removeFilePolicy(filePolicy.getId());
                dbClient.updateObject(vpool);
                break;
            case project:
                Project project = dbClient.queryObject(Project.class, unassignRes);
                project.removeFilePolicy(project, filePolicy.getId());
                dbClient.updateObject(project);
                break;
            case file_system:
                FileShare fs = dbClient.queryObject(FileShare.class, unassignRes);
                fs.removeFilePolicy(filePolicy.getId());
                dbClient.updateObject(fs);
                break;
            default:
                _log.error("Not a valid policy apply level: " + applyLevel);
        }
    }

    public static PhysicalNAS getSystemPhysicalNAS(DbClient dbClient, StorageSystem system) {
        List<URI> nasServers = dbClient.queryByType(PhysicalNAS.class, true);
        List<PhysicalNAS> phyNasServers = dbClient.queryObject(PhysicalNAS.class, nasServers);
        for (PhysicalNAS nasServer : phyNasServers) {
            if (nasServer.getStorageDeviceURI().toString().equalsIgnoreCase(system.getId().toString())) {
                return nasServer;
            }
        }
        return null;
    }
}
