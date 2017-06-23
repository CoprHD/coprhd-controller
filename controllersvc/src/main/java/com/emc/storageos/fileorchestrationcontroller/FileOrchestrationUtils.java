/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.fileorchestrationcontroller;

import java.net.URI;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.CifsShareACL;
import com.emc.storageos.db.client.model.FileExport;
import com.emc.storageos.db.client.model.FileExportRule;
import com.emc.storageos.db.client.model.FilePolicy;
import com.emc.storageos.db.client.model.FilePolicy.FilePolicyApplyLevel;
import com.emc.storageos.db.client.model.FilePolicy.FilePolicyType;
import com.emc.storageos.db.client.model.FileReplicaPolicyTarget;
import com.emc.storageos.db.client.model.FileReplicaPolicyTargetMap;
import com.emc.storageos.db.client.model.FileReplicationTopology;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.FileShare.PersonalityTypes;
import com.emc.storageos.db.client.model.NASServer;
import com.emc.storageos.db.client.model.NFSShareACL;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.PhysicalNAS;
import com.emc.storageos.db.client.model.PolicyStorageResource;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.ScopedLabel;
import com.emc.storageos.db.client.model.ScopedLabelSet;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualNAS;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.file.ExportRule;
import com.emc.storageos.model.file.FileNfsACLUpdateParams;
import com.emc.storageos.model.file.NfsACE;
import com.emc.storageos.model.file.ShareACL;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.FileControllerConstants;
import com.emc.storageos.volumecontroller.FileDeviceInputOutput;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;

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
     * Return list of policies to be applied at vpool
     * 
     * @param dbClient
     * @param vpool
     * @param storageSystem
     * @return
     */
    public static List<FilePolicy> getAllVpoolLevelPolices(DbClient dbClient, VirtualPool vpool, URI storageSystem, URI nasServer) {
        List<FilePolicy> filePoliciesToCreate = new ArrayList<FilePolicy>();
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
                                && strRes.getNasServer().toString().equalsIgnoreCase(nasServer.toString())) {
                            _log.info("File Policy {} is already exists for vpool {} , storage system {} and nas server {}",
                                    filePolicy.getFilePolicyName(), vpool.getLabel(), storageSystem.toString(), strRes);
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
     * Return list of policies to be applied at project
     * 
     * @param dbClient
     * @param project
     * @param storageSystem
     * @return
     */

    public static List<FilePolicy> getAllProjectLevelPolices(DbClient dbClient, Project project, VirtualPool vpool,
            URI storageSystem, URI nasServer) {
        List<FilePolicy> filePoliciesToCreate = new ArrayList<FilePolicy>();
        StringSet fileProjectPolicies = project.getFilePolicies();

        if (fileProjectPolicies != null && !fileProjectPolicies.isEmpty()) {
            for (String fileProjectPolicy : fileProjectPolicies) {
                FilePolicy filePolicy = dbClient.queryObject(FilePolicy.class, URIUtil.uri(fileProjectPolicy));
                if (NullColumnValueGetter.isNullURI(filePolicy.getFilePolicyVpool())
                        || !filePolicy.getFilePolicyVpool().toString().equals(vpool.getId().toString())) {
                    continue;
                }
                filePoliciesToCreate.add(filePolicy);
                StringSet policyStrRes = filePolicy.getPolicyStorageResources();
                if (policyStrRes != null && !policyStrRes.isEmpty()) {
                    for (String policyStrRe : policyStrRes) {
                        PolicyStorageResource strRes = dbClient.queryObject(PolicyStorageResource.class, URIUtil.uri(policyStrRe));
                        if (strRes != null && strRes.getAppliedAt().toString().equals(project.getId().toString())
                                && strRes.getStorageSystem().toString().equals(storageSystem.toString())
                                && strRes.getNasServer().toString().equalsIgnoreCase(nasServer.toString())) {
                            _log.info("File Policy {} is already exists for project {} , storage system {} and nas server {}",
                                    filePolicy.getFilePolicyName(), project.getLabel(), storageSystem.toString(), strRes);
                            filePoliciesToCreate.remove(filePolicy);
                            break;
                        }
                    }
                }
            }
        }
        return filePoliciesToCreate;
    }

    private static Boolean isvPoolPolicyAppliedOnStorageSystem(DbClient dbClient, StorageSystem system, NASServer nasServer,
            VirtualPool vpool, FilePolicy policy) {

        StringSet policyResources = policy.getPolicyStorageResources();
        if (policyResources != null && !policyResources.isEmpty()) {
            for (String strPolicyRes : policyResources) {
                PolicyStorageResource policyRes = dbClient.queryObject(PolicyStorageResource.class, URIUtil.uri(strPolicyRes));
                if (policyRes.getAppliedAt().toString().equals(vpool.getId().toString())
                        && policyRes.getStorageSystem().toString().equals(system.getId().toString())
                        && policyRes.getNasServer().toString().equalsIgnoreCase(nasServer.getId().toString())) {
                    _log.info("File Policy {} exists already for vpool {} , storage system {}", policy.getFilePolicyName(),
                            vpool.getLabel(), system.getLabel());
                    return true;
                }
            }
        }

        return false;
    }

    private static Boolean isProjectPolicyAppliedOnStorageSystem(DbClient dbClient, StorageSystem system, NASServer nasServer,
            Project project, FilePolicy policy) {

        StringSet policyResources = policy.getPolicyStorageResources();
        if (policyResources != null && !policyResources.isEmpty()) {
            for (String strPolicyRes : policyResources) {
                PolicyStorageResource policyRes = dbClient.queryObject(PolicyStorageResource.class, URIUtil.uri(strPolicyRes));
                if (policyRes.getAppliedAt().toString().equals(project.getId().toString())
                        && policyRes.getStorageSystem().toString().equals(system.getId().toString())
                        && policyRes.getNasServer().toString().equalsIgnoreCase(nasServer.getId().toString())) {
                    _log.info("File Policy {} exists already for project {} , storage system {}", policy.getFilePolicyName(),
                            project.getLabel(), system.getLabel());
                    return true;
                }
            }
        }

        return false;
    }

    private static Boolean isFSPolicyAppliedOnStorageSystem(DbClient dbClient, StorageSystem system, NASServer nasServer,
            FileShare fs, FilePolicy policy) {

        StringSet policyResources = policy.getPolicyStorageResources();
        if (policyResources != null && !policyResources.isEmpty()) {
            for (String strPolicyRes : policyResources) {
                PolicyStorageResource policyRes = dbClient.queryObject(PolicyStorageResource.class, URIUtil.uri(strPolicyRes));
                if (policyRes.getAppliedAt().toString().equals(fs.getId().toString())
                        && policyRes.getStorageSystem().toString().equals(system.getId().toString())
                        && policyRes.getNasServer().toString().equalsIgnoreCase(nasServer.getId().toString())) {
                    _log.info("File Policy {} exists already for file system {} , storage system {}", policy.getFilePolicyName(),
                            fs.getLabel(), system.getLabel());
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Gives list of replication policies assigned at vpool/project/fs levels
     * 
     * @param dbClient
     * @param vpool
     * @param project
     * @param fs
     * @return
     */
    public static List<FilePolicy> getReplicationPolices(DbClient dbClient, VirtualPool vpool, Project project, FileShare fs) {
        List<FilePolicy> replicationPolicies = new ArrayList<FilePolicy>();

        StringSet filePolicies = new StringSet();

        // vPool policies
        if (vpool.getFilePolicies() != null && !vpool.getFilePolicies().isEmpty()) {
            filePolicies.addAll(vpool.getFilePolicies());
        }
        // Project policies
        if (project.getFilePolicies() != null && !project.getFilePolicies().isEmpty()) {
            for (String strPolicy : project.getFilePolicies()) {
                FilePolicy policy = dbClient.queryObject(FilePolicy.class, URI.create(strPolicy));
                if (!NullColumnValueGetter.isNullURI(policy.getFilePolicyVpool())
                        && policy.getFilePolicyVpool().toString().equalsIgnoreCase(vpool.getId().toString())) {
                    filePolicies.add(policy.getId().toString());
                }

            }
        }
        // fs policies
        if (fs != null && fs.getFilePolicies() != null && !fs.getFilePolicies().isEmpty()) {
            for (String strPolicy : fs.getFilePolicies()) {
                FilePolicy policy = dbClient.queryObject(FilePolicy.class, URI.create(strPolicy));
                if (!NullColumnValueGetter.isNullURI(policy.getFilePolicyVpool())
                        && policy.getFilePolicyVpool().toString().equalsIgnoreCase(vpool.getId().toString())) {
                    filePolicies.add(policy.getId().toString());
                }

            }
        }

        if (filePolicies != null && !filePolicies.isEmpty()) {
            for (String strPolicy : filePolicies) {
                FilePolicy filePolicy = dbClient.queryObject(FilePolicy.class, URIUtil.uri(strPolicy));

                if (FilePolicyType.file_replication.name().equalsIgnoreCase(filePolicy.getFilePolicyType())) {
                    replicationPolicies.add(filePolicy);
                }
            }
        } else {
            if (fs != null) {
                _log.info("No replication policy assigned to vpool {} , project {} and fs {}", vpool.getLabel(), project.getLabel(),
                        fs.getLabel());
            } else {
                _log.info("No replication policy assigned to vpool {} and project {} ", vpool.getLabel(), project.getLabel());
            }
        }

        return replicationPolicies;
    }

    /**
     * Get the set of file policy storage resource for given policy
     * 
     * @param dbClient
     * @param policy
     * @return
     *
     */
    public static List<PolicyStorageResource> getFilePolicyStorageResources(DbClient dbClient, VirtualPool vpool, Project project,
            FileShare fs) {

        // Get the replication policies for vpool/project/fs!!
        List<PolicyStorageResource> policyStorageResources = new ArrayList<PolicyStorageResource>();
        List<FilePolicy> replicationPolicies = getReplicationPolices(dbClient, vpool, project, fs);
        if (replicationPolicies != null && !replicationPolicies.isEmpty()) {
            if (replicationPolicies.size() > 1) {
                _log.error("More than one replication policy could not be applied accross vpool/project/fs");
                throw APIException.badRequests.moreThanOneReplicationPolicySpecified();
            } else {
                FilePolicy policy = replicationPolicies.get(0);
                for (PolicyStorageResource strRes : getFilePolicyStorageResources(dbClient, policy)) {
                    if (strRes != null) {
                        if (FilePolicyApplyLevel.project.name().equalsIgnoreCase(policy.getApplyAt())
                                && strRes.getAppliedAt().toString().equals(project.getId().toString())) {
                            policyStorageResources.add(strRes);
                        } else if (FilePolicyApplyLevel.vpool.name().equalsIgnoreCase(policy.getApplyAt())
                                && strRes.getAppliedAt().toString().equals(vpool.getId().toString())) {
                            policyStorageResources.add(strRes);
                        }
                    }
                }
            }
        }
        return policyStorageResources;
    }

    /**
     * Verify the replication policy was applied at given level
     * 
     * @param dbClient
     * @param system
     * @param nasServer
     * @param vpool
     * @param project
     * @param fs
     * @return
     */
    public static Boolean isReplicationPolicyExistsOnTarget(DbClient dbClient, StorageSystem system,
            VirtualPool vpool, Project project, FileShare fs) {
        if (fs.getPersonality() != null && fs.getPersonality().equalsIgnoreCase(PersonalityTypes.TARGET.name())) {
            List<FilePolicy> replicationPolicies = getReplicationPolices(dbClient, vpool, project, fs);
            if (replicationPolicies != null && !replicationPolicies.isEmpty()) {
                if (replicationPolicies.size() > 1) {
                    _log.warn("More than one replication policy found {}", replicationPolicies.size());
                } else {
                    FilePolicy replPolicy = replicationPolicies.get(0);

                    FileShare sourceFS = dbClient.queryObject(FileShare.class, fs.getParentFileShare().getURI());
                    StorageSystem sourceStorage = dbClient.queryObject(StorageSystem.class, sourceFS.getStorageDevice());
                    NASServer nasServer = null;
                    if (sourceFS != null && sourceFS.getVirtualNAS() != null) {
                        nasServer = dbClient.queryObject(VirtualNAS.class, sourceFS.getVirtualNAS());
                    } else {
                        // Get the physical NAS for the storage system!!
                        nasServer = FileOrchestrationUtils.getSystemPhysicalNAS(dbClient, sourceStorage);
                    }
                    if (replPolicy.getApplyAt().equalsIgnoreCase(FilePolicyApplyLevel.vpool.name())) {
                        return isvPoolPolicyAppliedOnStorageSystem(dbClient, sourceStorage, nasServer,
                                vpool, replPolicy);
                    } else if (replPolicy.getApplyAt().equalsIgnoreCase(FilePolicyApplyLevel.project.name())) {
                        return isProjectPolicyAppliedOnStorageSystem(dbClient, sourceStorage, nasServer,
                                project, replPolicy);
                    } else if (replPolicy.getApplyAt().equalsIgnoreCase(FilePolicyApplyLevel.file_system.name())) {
                        FileShare fsParent = dbClient.queryObject(FileShare.class, fs.getParentFileShare());
                        return isFSPolicyAppliedOnStorageSystem(dbClient, sourceStorage, nasServer,
                                fsParent, replPolicy);
                    }
                }
            }
        } else if (fs.getPersonality().equalsIgnoreCase(PersonalityTypes.TEMP_TARGET.name())) {
            // Higher order FS failover
            return true;
        }
        return false;
    }

    /**
     * Verify the file system is a primary fs or file system with no replication.
     * 
     * @param fs
     * @return
     */
    public static Boolean isPrimaryFileSystemOrNormalFileSystem(FileShare fs) {
        if (fs.getPersonality() == null
                || fs.getPersonality().equalsIgnoreCase(PersonalityTypes.SOURCE.name())
                || fs.getPersonality().equalsIgnoreCase(PersonalityTypes.TEMP_TARGET.name())) {
            return true;
        }
        return false;
    }

    /**
     * Get the physical nas server for storage system
     * 
     * @param dbClient
     * @param system
     * @return
     */
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

    /**
     * Get the storage ports of storage system
     * 
     * @param dbClient
     * @param system
     * @return
     */
    public static List<StoragePort> getStorageSystemPorts(DbClient dbClient, StorageSystem system) {
        List<StoragePort> ports = new ArrayList<StoragePort>();
        // Update the port metrics calculations. This makes the UI display up-to-date when ports shown.
        URIQueryResultList storagePortURIs = new URIQueryResultList();
        dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getStorageDeviceStoragePortConstraint(system.getId()),
                storagePortURIs);
        List<StoragePort> storagePorts = dbClient.queryObject(StoragePort.class, storagePortURIs);
        for (StoragePort port : storagePorts) {
            if (!port.getInactive()) {
                ports.add(port);
            }
        }
        return ports;
    }

    private static void setPolicyStorageAppliedAt(FilePolicy filePolicy, FileDeviceInputOutput args,
            PolicyStorageResource policyStorageResource) {
        FilePolicyApplyLevel applyLevel = FilePolicyApplyLevel.valueOf(filePolicy.getApplyAt());
        switch (applyLevel) {
            case vpool:
                policyStorageResource.setAppliedAt(args.getVPool().getId());
                break;
            case project:
                policyStorageResource.setAppliedAt(args.getProject().getId());
                break;
            case file_system:
                policyStorageResource.setAppliedAt(args.getFileObj().getId());
        }
    }

    /**
     * Find the file storage resource by Native ID
     * 
     * @param dbClient
     *
     * @param system
     *            storage system
     * @param filePolicy
     *            file policy
     * @param args
     * 
     * @param path
     *            storage path
     * @return policy storage resource
     */
    public static PolicyStorageResource findPolicyStorageResourceByNativeId(DbClient dbClient, StorageSystem system, FilePolicy filePolicy,
            FileDeviceInputOutput args, String path) {
        URIQueryResultList results = new URIQueryResultList();
        PolicyStorageResource storageRes = null;

        NASServer nasServer = null;
        if (args.getvNAS() != null) {
            nasServer = args.getvNAS();
        } else {
            // Get the physical NAS for the storage system!!
            PhysicalNAS pNAS = getSystemPhysicalNAS(dbClient, system);
            if (pNAS != null) {
                nasServer = pNAS;
            } else {
                _log.error("Unable to find physical NAS on storage system {}", system.getLabel());
                return null;
            }
        }

        // Set storage port details to vNas
        String nasNativeGuid = NativeGUIDGenerator.generateNativeGuidForFilePolicyResource(system,
                nasServer.getNasName(), filePolicy.getFilePolicyType(), path, NativeGUIDGenerator.FILE_STORAGE_RESOURCE);

        dbClient.queryByConstraint(
                AlternateIdConstraint.Factory.getPolicyStorageResourceByNativeGuidConstraint(nasNativeGuid),
                results);
        Iterator<URI> iter = results.iterator();
        PolicyStorageResource tmpStorageres = null;
        while (iter.hasNext()) {
            tmpStorageres = dbClient.queryObject(PolicyStorageResource.class, iter.next());

            if (tmpStorageres != null && !tmpStorageres.getInactive()) {
                storageRes = tmpStorageres;
                _log.info("found File policy storage resource for {}",
                        tmpStorageres.getNativeGuid() + ":" + tmpStorageres.getFilePolicyId());
                break;
            }
        }
        return storageRes;
    }

    private static String stripSpecialCharacters(String label) {
        return label.replaceAll("[^\\dA-Za-z ]", "").replaceAll("\\s+", "_");
    }

    /**
     * 
     * @param clusterName Isilon cluster name
     * @param filePolicy the file policy template
     * @param fileShare the file share
     * @param args FileDeviceInputOutput
     * @return the generated policy name
     */
    public static String generateNameForSnapshotIQPolicy(String clusterName, FilePolicy filePolicy,
            FileShare fileShare, FileDeviceInputOutput args) {

        String devPolicyName = null;
        String policyName = stripSpecialCharacters(filePolicy.getFilePolicyName());
        VirtualNAS vNAS = args.getvNAS();

        String clusterNameWithoutSpecialCharacters = stripSpecialCharacters(clusterName);

        FilePolicyApplyLevel applyLevel = FilePolicyApplyLevel.valueOf(filePolicy.getApplyAt());
        switch (applyLevel) {
            case vpool:
                if (vNAS != null) {
                    devPolicyName = String.format("%1$s_%2$s_%3$s_%4$s", clusterNameWithoutSpecialCharacters,
                            args.getVNASNameWithNoSpecialCharacters(), args.getVPoolNameWithNoSpecialCharacters(), policyName);
                } else {
                    devPolicyName = String.format("%1$s_%2$s_%3$s", clusterNameWithoutSpecialCharacters,
                            args.getVPoolNameWithNoSpecialCharacters(), policyName);
                }
                break;
            case project:
                if (vNAS != null) {
                    devPolicyName = String.format("%1$s_%2$s_%3$s_%4$s_%5$s", clusterNameWithoutSpecialCharacters,
                            args.getVNASNameWithNoSpecialCharacters(), args.getVPoolNameWithNoSpecialCharacters(),
                            args.getProjectNameWithNoSpecialCharacters(), policyName);
                } else {
                    devPolicyName = String.format("%1$s_%2$s_%3$s_%4$s", clusterNameWithoutSpecialCharacters,
                            args.getVPoolNameWithNoSpecialCharacters(), args.getProjectNameWithNoSpecialCharacters(), policyName);
                }
                break;
            case file_system:
                String fileShareName = stripSpecialCharacters(fileShare.getName());
                if (vNAS != null) {
                    devPolicyName = String.format("%1$s_%2$s_%3$s_%4$s_%5$s_%6$s", clusterNameWithoutSpecialCharacters,
                            args.getVNASNameWithNoSpecialCharacters(), args.getVPoolNameWithNoSpecialCharacters(),
                            args.getProjectNameWithNoSpecialCharacters(), fileShareName, policyName);
                } else {
                    devPolicyName = String.format("%1$s_%2$s_%3$s_%4$s_%5$s", clusterNameWithoutSpecialCharacters,
                            args.getVPoolNameWithNoSpecialCharacters(), args.getProjectNameWithNoSpecialCharacters(),
                            fileShareName, policyName);
                }
                break;
        }
        return devPolicyName;
    }

    /**
     * 
     * @param sourceFilerName the Isilon source cluster name
     * @param targetFilerName the Isilon target cluster name
     * @param filePolicy the file policy template
     * @param fileShare the file share
     * @param args FileDeviceInputOutput
     * @return the generated policy name
     */
    public static String generateNameForSyncIQPolicy(String sourceFilerName, String targetFilerName, FilePolicy filePolicy,
            FileShare fileShare, FileDeviceInputOutput args) {

        String devPolicyName = null;
        String policyName = stripSpecialCharacters(filePolicy.getFilePolicyName());
        VirtualNAS vNAS = args.getvNAS();

        String sourceClusterName = stripSpecialCharacters(sourceFilerName);
        String targetClusterName = stripSpecialCharacters(targetFilerName);

        FilePolicyApplyLevel applyLevel = FilePolicyApplyLevel.valueOf(filePolicy.getApplyAt());
        switch (applyLevel) {
            case vpool:
                if (vNAS != null) {
                    devPolicyName = String.format("%1$s_to_%2$s_%3$s_%4$s_%5$s", sourceClusterName, targetClusterName,
                            args.getVNASNameWithNoSpecialCharacters(), args.getVPoolNameWithNoSpecialCharacters(), policyName);
                } else {
                    devPolicyName = String.format("%1$s_to_%2$s_%3$s_%4$s", sourceClusterName, targetClusterName,
                            args.getVPoolNameWithNoSpecialCharacters(), policyName);
                }
                break;
            case project:
                if (vNAS != null) {
                    devPolicyName = String.format("%1$s_to_%2$s_%3$s_%4$s_%5$s_%6$s", sourceClusterName, targetClusterName,
                            args.getVNASNameWithNoSpecialCharacters(), args.getVPoolNameWithNoSpecialCharacters(),
                            args.getProjectNameWithNoSpecialCharacters(), policyName);
                } else {
                    devPolicyName = String.format("%1$s_to_%2$s_%3$s_%4$s_%5$s", sourceClusterName, targetClusterName,
                            args.getVPoolNameWithNoSpecialCharacters(), args.getProjectNameWithNoSpecialCharacters(), policyName);
                }
                break;
            case file_system:
                String fileShareName = stripSpecialCharacters(fileShare.getName());
                if (vNAS != null) {
                    devPolicyName = String.format("%1$s_to_%2$s_%3$s_%4$s_%5$s_%6$s_%7$s", sourceClusterName, targetClusterName,
                            args.getVNASNameWithNoSpecialCharacters(), args.getVPoolNameWithNoSpecialCharacters(),
                            args.getProjectNameWithNoSpecialCharacters(), fileShareName, policyName);
                } else {
                    devPolicyName = String.format("%1$s_%2$s_%3$s_%4$s_%5$s_%6$s", sourceClusterName, targetClusterName,
                            args.getVPoolNameWithNoSpecialCharacters(), args.getProjectNameWithNoSpecialCharacters(),
                            fileShareName, policyName);
                }
                break;
        }
        return devPolicyName;
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

    /**
     * Remove replication topology info from policy
     * if no assigned resources with the policy
     * 
     * @param filePolicy the file policy template
     * @param dbClient
     */
    public static void removeTopologyInfo(FilePolicy filePolicy, DbClient dbClient) {
        // If no other resources are assigned to replication policy
        // Remove the replication topology from the policy
        if (filePolicy.getFilePolicyType().equalsIgnoreCase(FilePolicyType.file_replication.name())
                && (filePolicy.getAssignedResources() == null || filePolicy.getAssignedResources().isEmpty())) {
            if (filePolicy.getReplicationTopologies() != null && !filePolicy.getReplicationTopologies().isEmpty()) {
                for (String uriTopology : filePolicy.getReplicationTopologies()) {
                    FileReplicationTopology topology = dbClient.queryObject(FileReplicationTopology.class,
                            URI.create(uriTopology));
                    if (topology != null) {
                        topology.setInactive(true);
                        filePolicy.removeReplicationTopology(uriTopology);
                        dbClient.updateObject(topology);
                    }
                }
                _log.info("Removed replication topology from policy {}", filePolicy.getFilePolicyName());
            }

        }
        dbClient.updateObject(filePolicy);
    }

    /**
     * Create/Update the File policy resource
     * 
     * @param dbClient
     * @param system
     * @param filePolicy
     * @param args
     * @param sourcePath
     * @return
     *
     */
    public static PolicyStorageResource updatePolicyStorageResource(DbClient dbClient, StorageSystem system, FilePolicy filePolicy,
            FileDeviceInputOutput args, String sourcePath, String policyNativeId,
            StorageSystem targetSystem, NASServer targetNasServer, String targetPath) {
        PolicyStorageResource policyStorageResource = new PolicyStorageResource();

        policyStorageResource.setId(URIUtil.createId(PolicyStorageResource.class));
        policyStorageResource.setFilePolicyId(filePolicy.getId());
        policyStorageResource.setStorageSystem(system.getId());
        policyStorageResource.setPolicyNativeId(policyNativeId);
        policyStorageResource.setResourcePath(sourcePath);
        NASServer nasServer = null;
        if (args.getvNAS() != null) {
            nasServer = args.getvNAS();
        } else {
            // Get the physical NAS for the storage system!!
            PhysicalNAS pNAS = getSystemPhysicalNAS(dbClient, system);
            if (pNAS != null) {
                nasServer = pNAS;
            }
        }
        policyStorageResource.setNasServer(nasServer.getId());
        setPolicyStorageAppliedAt(filePolicy, args, policyStorageResource);
        policyStorageResource.setNativeGuid(NativeGUIDGenerator.generateNativeGuidForFilePolicyResource(system,
                nasServer.getNasName(), filePolicy.getFilePolicyType(), sourcePath, NativeGUIDGenerator.FILE_STORAGE_RESOURCE));

        if (filePolicy.getFilePolicyType().equalsIgnoreCase(FilePolicy.FilePolicyType.file_replication.name())) {
            // Update the target resource details!!!
            FileReplicaPolicyTargetMap fileReplicaPolicyTargetMap = new FileReplicaPolicyTargetMap();
            FileReplicaPolicyTarget target = new FileReplicaPolicyTarget();

            if (targetNasServer != null) {
                target.setNasServer(targetNasServer.getId().toString());
            } else {
                PhysicalNAS pNAS = FileOrchestrationUtils.getSystemPhysicalNAS(dbClient, targetSystem);
                if (pNAS != null) {
                    target.setNasServer(pNAS.getId().toString());
                }
            }

            target.setAppliedAt(filePolicy.getApplyAt());
            target.setStorageSystem(targetSystem.getId().toString());
            target.setPath(targetPath);
            String key = target.getFileTargetReplicaKey();
            fileReplicaPolicyTargetMap.put(key, target);
            policyStorageResource.setFileReplicaPolicyTargetMap(fileReplicaPolicyTargetMap);
        }

        dbClient.createObject(policyStorageResource);

        filePolicy.addPolicyStorageResources(policyStorageResource.getId());
        dbClient.updateObject(filePolicy);

        _log.info("PolicyStorageResource object created successfully for {} ",
                system.getLabel() + policyStorageResource.getAppliedAt());
        return policyStorageResource;
    }

    /**
     * Get the target host for replication
     * 
     * @param dbClient
     * @param targetFS
     * @return
     *
     */
    public static String getTargetHostPortForReplication(DbClient dbClient, FileShare targetFS) {

        return getTargetHostPortForReplication(dbClient, targetFS.getStorageDevice(),
                targetFS.getVirtualArray(), targetFS.getVirtualNAS());

    }

    public static String getTargetHostPortForReplication(DbClient dbClient, URI targetStorageSystemURI, URI targetVarrayURI,
            URI targetVNasURI) {

        StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class, targetStorageSystemURI);
        String targetHost = targetSystem.getIpAddress();

        StringSet targetNasVarraySet = null;

        StringSet targetStoragePortSet = null;

        if (targetVNasURI != null) {
            VirtualNAS targetVNas = dbClient.queryObject(VirtualNAS.class, targetVNasURI);
            targetStoragePortSet = targetVNas.getStoragePorts();
            targetNasVarraySet = targetVNas.getTaggedVirtualArrays();
        } else {
            PhysicalNAS pNAS = FileOrchestrationUtils.getSystemPhysicalNAS(dbClient, targetSystem);
            targetStoragePortSet = pNAS.getStoragePorts();
            targetNasVarraySet = pNAS.getTaggedVirtualArrays();
        }

        List<String> drPorts = new ArrayList<String>();
        for (String nasPort : targetStoragePortSet) {

            StoragePort port = dbClient.queryObject(StoragePort.class, URI.create(nasPort));

            if (port != null && !port.getInactive()) {

                StringSet varraySet = port.getTaggedVirtualArrays();
                if (varraySet == null || !varraySet.contains(targetVarrayURI.toString())) {
                    continue;
                }
                if (targetNasVarraySet != null) {
                    if (!targetNasVarraySet.contains(targetVarrayURI.toString())) {
                        continue;
                    }
                }
                targetHost = port.getPortNetworkId();

                // iterate until dr port found!!
                if (port.getTag() != null) {
                    ScopedLabelSet portTagSet = port.getTag();
                    if (portTagSet != null && !portTagSet.isEmpty()) {
                        for (ScopedLabel tag : portTagSet) {
                            if ("dr_port".equals(tag.getLabel())) {
                                _log.info("DR port {} found from storage system {} for replication", port.getPortNetworkId(),
                                        targetSystem.getLabel());
                                drPorts.add(port.getPortNetworkId());
                            }
                        }

                    }
                }
            }
        }
        if (!drPorts.isEmpty()) {
            Collections.shuffle(drPorts);
            return drPorts.get(0);
        }
        return targetHost;
    }

    /**
     * Get the list of virtual nas servers from storage system which are part of vpool and project
     * 
     * @param dbClient
     * @param storageSystemURI
     * @param vpoolURI
     * @param projectURI
     * @return
     *
     */
    public static List<URI> getVNASServersOfStorageSystemAndVarrayOfVpool(DbClient dbClient, URI storageSystemURI, URI vpoolURI,
            URI projectURI) {
        VirtualPool vpool = dbClient.queryObject(VirtualPool.class, vpoolURI);
        Project project = null;
        if (projectURI != null) {
            project = dbClient.queryObject(Project.class, projectURI);
        }
        StringSet varraySet = vpool.getVirtualArrays();
        URIQueryResultList vNasURIs = new URIQueryResultList();
        List<URI> vNASURIList = new ArrayList<URI>();
        dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getStorageDeviceVirtualNasConstraint(storageSystemURI),
                vNasURIs);
        Iterator<URI> vNasIter = vNasURIs.iterator();
        while (vNasIter.hasNext()) {
            URI vNasURI = vNasIter.next();
            VirtualNAS vNas = dbClient.queryObject(VirtualNAS.class,
                    vNasURI);
            if (vNas != null && !vNas.getInactive()) {
                // Dont pick the other project nas servers!!!
                if (project != null && vNas.getAssociatedProjects() != null && !vNas.getAssociatedProjects().isEmpty()) {
                    if (!vNas.getAssociatedProjects().contains(project.getId().toString())) {
                        _log.info("vNas server {} assigned to other project, so ignoring this vNas server", vNas.getNasName());
                        continue;
                    }
                }
                StringSet vNASVarraySet = vNas.getAssignedVirtualArrays();
                if (varraySet != null && !varraySet.isEmpty() && vNASVarraySet != null) {

                    vNASVarraySet.retainAll(varraySet);
                    if (!vNASVarraySet.isEmpty()) {
                        vNASURIList.add(vNas.getId());
                    }
                }
            }
        }

        return vNASURIList;
    }

    /**
     * Get the set of file policy storage resource for given policy
     * 
     * @param dbClient
     * @param policy
     * @return
     *
     */
    public static List<PolicyStorageResource> getFilePolicyStorageResources(DbClient dbClient, FilePolicy policy) {
        URIQueryResultList policyResourceURIs = new URIQueryResultList();
        dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getFilePolicyStorageResourceConstraint(policy.getId()),
                policyResourceURIs);
        List<PolicyStorageResource> policyStorageResources = new ArrayList<PolicyStorageResource>();
        Iterator<URI> policyStorageResIter = policyResourceURIs.iterator();
        while (policyStorageResIter.hasNext()) {
            PolicyStorageResource policyStorageRes = dbClient.queryObject(PolicyStorageResource.class, policyStorageResIter.next());
            if (policyStorageRes != null && !policyStorageRes.getInactive()) {
                policyStorageResources.add(policyStorageRes);
            }
        }
        return policyStorageResources;
    }

    public static FilePolicy getFilePolicyFromFS(DbClient _dbClient, VirtualPool sourceVP, Project sourceProject) {
        FilePolicy fp = null;
        if (sourceVP.getFilePolicies() != null && !sourceVP.getFilePolicies().isEmpty()) {
            for (String policy : sourceVP.getFilePolicies()) {
                fp = _dbClient.queryObject(FilePolicy.class, URI.create(policy));
                _log.info("_csm SourceFilePolicy from vPool :: {} ", fp.toString());
            }
        } else if (sourceProject.getFilePolicies() != null && !sourceProject.getFilePolicies().isEmpty()) {
            for (String policy : sourceProject.getFilePolicies()) {
                fp = _dbClient.queryObject(FilePolicy.class, URI.create(policy));
                _log.info("_csm sourceFilePolicy from project :: {}", fp.getFilePolicyName());
            }
        }
        return fp;
    }

    /**
     * create ViPR fileShare object for tempTargetFS and needed to create TaskCompleters
     * 
     * @param _dbClient
     * @param sourceURI
     * @param tempTargetPath
     * @param targetSystem
     * @return
     */
    public static URI createFsObj(DbClient _dbClient, URI targetURI) {
        FileShare targetFS = _dbClient.queryObject(FileShare.class, targetURI);
        FileShare tempFs = new FileShare();
        String tempTargetPath = "/ifs/" + targetFS.getLabel() + "TempDir";
        String tempFsName = targetFS.getLabel() + "_Temp";
        tempFs.setId(URIUtil.createId(FileShare.class));
        tempFs.setName(tempFsName);
        tempFs.setLabel(tempFsName);
        tempFs.setMountPath(tempTargetPath);
        tempFs.setNativeId(tempTargetPath);
        tempFs.setPath(tempTargetPath);
        tempFs.setPersonality(FileShare.PersonalityTypes.TEMP_TARGET.name());
        tempFs.setParentFileShare(new NamedURI(targetFS.getId(), targetFS.getLabel()));
        tempFs.setCapacity(targetFS.getCapacity());
        tempFs.setCreationTime(Calendar.getInstance());
        tempFs.setInactive(false);
        tempFs.setProtocol(targetFS.getProtocol());
        tempFs.setStorageDevice(targetFS.getStorageDevice());
        tempFs.setPool(targetFS.getPool());
        tempFs.setVirtualArray(targetFS.getVirtualArray());
	tempFs.setVirtualPool(targetFS.getVirtualPool());
        tempFs.setProject(targetFS.getProject());
        tempFs.setTenant(targetFS.getTenant());
        // TODO: add storagePort
        // tempFs.setStoragePort(storagePort);
        tempFs.setThinlyProvisioned(targetFS.getThinlyProvisioned());
        tempFs.setSMBFileShares(targetFS.getSMBFileShares());
        tempFs.setFsExports(targetFS.getFsExports());

        _dbClient.createObject(tempFs);
        _log.info("TempTargetFS created {}", tempFs);

        // update the targeFS with its MirrorFs info; needed in #Step:3
        updateTargetFsWithMirrorFsInfo(_dbClient, targetFS, tempFs);
        _log.info("Updated the targetFileShare : {} with MirrorFs info : {}", targetFS.getLabel(),
                targetFS.getMirrorfsTargets());

        return tempFs.getId();
    }

    public static FilePolicy createFpObj(DbClient s_dbClient, FileShare targetFs, FileShare tempTargetFs, FilePolicy sourcePolicy) {
        FilePolicy repPolicy = new FilePolicy();
        String polName = sourcePolicy.getLabel() + "_" + targetFs.getLabel() + "_tempPolicy";
        VirtualPool targetVp = s_dbClient.queryObject(VirtualPool.class, targetFs.getVirtualPool());
        repPolicy.setId(URIUtil.createId(FilePolicy.class));
        repPolicy.setFilePolicyDescription("Replication Policy between FS : " + targetFs.getLabel() + " and " + tempTargetFs.getLabel());
        repPolicy.setLabel(polName);
        repPolicy.setFilePolicyName(polName);
        repPolicy.setLabel(polName);
        repPolicy.setFilePolicyType(FilePolicyType.file_replication.name());
        repPolicy.setApplyAt(FilePolicyApplyLevel.file_system.name());
        repPolicy.setFileReplicationCopyMode(FilePolicy.FileReplicationCopyMode.SYNC.name());
        repPolicy.setPriority(FilePolicy.FilePolicyPriority.Normal.toString());
        repPolicy.setFileReplicationType(FilePolicy.FileReplicationType.LOCAL.name());

        FileReplicationTopology frp = new FileReplicationTopology();
        // frp.set

        return null;
    }

    // public static FilePolicy generateSyncPolicyHigherOrderFsFailover(DbClient s_dbClient, FileShare targetFileShare,
    // FileShare tempFileShare, FilePolicy sourceFilePolicy) {
    // // TODO Auto-generated method stub
    // return null;
    // }

    public static String generateTempSyncIQNameHigherOrderFsFailover(DbClient s_dbClient, FileShare targetFileShare,
            FileShare tempFileShare, FilePolicy sourceFilePolicy) {
        String syncPolicyName = targetFileShare.getLabel() + "_" + tempFileShare.getLabel() + "_LocalReplication";
        return syncPolicyName;
    }

    /**
     * FS FailOver at higher order : Update the targetFS with MirrorFs Info i.e, tempFSId
     * 
     * @param s_dbClient
     * @param targetFileShare
     * @param tempFileShare
     */
    public static void updateTargetFsWithMirrorFsInfo(DbClient s_dbClient, FileShare targetFileShare, FileShare tempFileShare) {
        if (targetFileShare != null) {
            if (targetFileShare.getMirrorfsTargets() == null) {
                targetFileShare.setMirrorfsTargets(new StringSet());
            }
            targetFileShare.getMirrorfsTargets().add(tempFileShare.getId().toString());
            s_dbClient.updateObject(targetFileShare);
        }
    }

    /**
     * check whether the policy is applied at vpool/ project level
     * 
     * @param fp
     * @return
     */
    public static boolean isHigherOrderFsFailover(FilePolicy fp) {
        if (fp.getApplyAt().toString().equalsIgnoreCase(FilePolicy.FilePolicyApplyLevel.vpool.name())
                || fp.getApplyAt().toString().equalsIgnoreCase(FilePolicy.FilePolicyApplyLevel.project.name())) {
            return true;
        }
        return false;
    }

}
