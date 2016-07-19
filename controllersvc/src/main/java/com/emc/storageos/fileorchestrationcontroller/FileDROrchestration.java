/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.fileorchestrationcontroller;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.CifsShareACL;
import com.emc.storageos.db.client.model.FSExportMap;
import com.emc.storageos.db.client.model.FileExport;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.SMBFileShare;
import com.emc.storageos.db.client.model.SMBShareMap;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.model.file.CifsShareACLUpdateParams;
import com.emc.storageos.model.file.ShareACL;
import com.emc.storageos.model.file.ShareACLs;
import com.emc.storageos.volumecontroller.FileSMBShare;
import com.emc.storageos.volumecontroller.FileShareExport;
import com.emc.storageos.volumecontroller.impl.file.MirrorFileFailoverTaskCompleter;
import com.emc.storageos.workflow.Workflow;

public class FileDROrchestration extends FileOrchestrationDeviceController {

    public static String addStepsForFailoverFileSystem(Workflow workflow, URI systemTarget, FileShare sourceFileShare,
            FileShare targetFileShare) {
        String failoverStep = workflow.createStepId();
        MirrorFileFailoverTaskCompleter completer = new MirrorFileFailoverTaskCompleter(sourceFileShare.getId(), targetFileShare.getId(),
                failoverStep);
        String stepDescription = String.format("Failover Source File System %s to Target System.", sourceFileShare.getLabel());
        Object[] args = new Object[] { systemTarget, targetFileShare.getId(), completer };
        String waitForFailover = _fileReplicationDeviceController.createMethod(workflow, null, null,
                FAILOVER_FILE_SYSTEM_METH, failoverStep, stepDescription, systemTarget, args);
        return waitForFailover;
    }

    public static String addStepsToReplicateCIFSShares(Workflow workflow, URI systemTarget, FileShare sourceFileShare,
            FileShare targetFileShare, StoragePort cifsPort, String waitForFailover) {
        String shareStepGroup = "Replicate CIFS Shares To Target Cluster";

        SMBShareMap sourceSMBShareMap = sourceFileShare.getSMBFileShares();
        List<SMBFileShare> sourceSMBShares = new ArrayList<SMBFileShare>(sourceSMBShareMap.values());
        SMBShareMap targetSMBShareMap = targetFileShare.getSMBFileShares();

        if (targetSMBShareMap == null) {
            createCIFSShareOnTarget(workflow, systemTarget, sourceSMBShares, cifsPort, targetFileShare, sourceFileShare,
                    waitForFailover, shareStepGroup);
        } else {
            List<SMBFileShare> targetSMBShares = new ArrayList<SMBFileShare>(targetSMBShareMap.values());

            List<SMBFileShare> targetSMBSharestoDelete = new ArrayList<SMBFileShare>();
            List<SMBFileShare> targetSMBSharestoCreate = new ArrayList<SMBFileShare>();

            List<String> sourceSMBSharesNameList = new ArrayList<String>();
            List<String> targetSMBSharesNameList = new ArrayList<String>();

            for (SMBFileShare sourceSMBShare : sourceSMBShares) {
                sourceSMBSharesNameList.add(sourceSMBShare.getName());
            }
            for (SMBFileShare targetSMBShare : targetSMBShares) {
                targetSMBSharesNameList.add(targetSMBShare.getName());
            }

            for (SMBFileShare sourceSMBShare : sourceSMBShares) {
                if (!targetSMBSharesNameList.contains(sourceSMBShare.getName())) {
                    targetSMBSharestoCreate.add(sourceSMBShare);
                }
            }
            for (SMBFileShare targetSMBShare : targetSMBShares) {
                if (!sourceSMBSharesNameList.contains(targetSMBShare.getName())) {
                    targetSMBSharestoDelete.add(targetSMBShare);
                }
            }
            if (!targetSMBSharestoCreate.isEmpty()) {
                createCIFSShareOnTarget(workflow, systemTarget, targetSMBSharestoCreate, cifsPort, targetFileShare, sourceFileShare,
                        waitForFailover, shareStepGroup);
            }
            if (!targetSMBSharestoDelete.isEmpty()) {
                deleteCIFSShareFromTarget(workflow, systemTarget, targetSMBSharestoDelete, targetFileShare, waitForFailover,
                        shareStepGroup);
            }

            if (targetSMBSharestoCreate.isEmpty() && targetSMBSharestoDelete.isEmpty()) {
                shareStepGroup = null;
            }
        }
        return shareStepGroup;
    }

    public static void createCIFSShareOnTarget(Workflow workflow, URI systemTarget, List<SMBFileShare> smbShares, StoragePort cifsPort,
            FileShare targetFileShare, FileShare sourceFileShare, String waitForFailover, String shareStepGroup) {

        for (SMBFileShare smbShare : smbShares) {
            FileSMBShare fileSMBShare = new FileSMBShare(smbShare);
            fileSMBShare.setStoragePortName(cifsPort.getPortName());
            fileSMBShare.setStoragePortNetworkId(cifsPort.getPortNetworkId());
            if (fileSMBShare.isSubDirPath()) {
                fileSMBShare.setPath(targetFileShare.getPath() + fileSMBShare.getPath().split(sourceFileShare.getPath())[1]);
            } else {
                fileSMBShare.setPath(targetFileShare.getPath());
            }
            String shareCreationStep = workflow.createStepId();
            String stepDescription = String.format("Creating Source File System CIFS Share : %s on Target System.",
                    fileSMBShare.getName());
            Object[] args = new Object[] { systemTarget, targetFileShare.getId(), fileSMBShare };
            _fileDeviceController.createMethod(workflow, shareStepGroup, waitForFailover,
                    CREATE_FILESYSTEM_SHARE_METHOD, shareCreationStep, stepDescription, systemTarget, args);
        }
    }

    public static void deleteCIFSShareFromTarget(Workflow workflow, URI systemTarget, List<SMBFileShare> smbShares,
            FileShare targetFileShare, String waitForFailover, String shareStepGroup) {
        for (SMBFileShare smbShare : smbShares) {
            FileSMBShare fileSMBShare = new FileSMBShare(smbShare);
            String stepDescription = "Deleting Target File System CIFS Share: " + fileSMBShare.getName();
            String sharedeleteStep = workflow.createStepId();
            Object[] args = new Object[] { systemTarget, targetFileShare.getId(), fileSMBShare };
            _fileDeviceController.createMethod(workflow, shareStepGroup, waitForFailover, DELETE_FILESYSTEM_SHARE_METHOD, sharedeleteStep,
                    stepDescription, systemTarget, args);
        }
    }

    public static void addStepsToReplicateCIFSShareACLs(Workflow workflow, URI systemTarget, FileShare sourceFileShare,
            FileShare targetFileShare, String waitForShareStepGroup) {
        CifsShareACLUpdateParams params;
        SMBShareMap sourceSMBShareMap = sourceFileShare.getSMBFileShares();
        List<SMBFileShare> sourceSMBShares = new ArrayList<SMBFileShare>(sourceSMBShareMap.values());

        for (SMBFileShare sourceSMBShare : sourceSMBShares) {
            List<ShareACL> sourceShareACLs = queryShareACLs(sourceSMBShare.getName(), sourceFileShare.getId());
            List<ShareACL> targetShareACLs = queryShareACLs(sourceSMBShare.getName(), targetFileShare.getId());

            if (sourceShareACLs != null && !sourceShareACLs.isEmpty()) {
                if (targetShareACLs.isEmpty()) {
                    params = new CifsShareACLUpdateParams();
                    ShareACLs shareACLs = new ShareACLs();
                    shareACLs.setShareACLs(sourceShareACLs);
                    params.setAclsToAdd(shareACLs);

                    String stepDescription = String.format("Replicating Source File System CIFS Share : %s ACLs : %s On Target Cluster",
                            sourceSMBShare.getName(), params.toString());
                    String shareACLUpdateStep = workflow.createStepId();
                    Object[] args = new Object[] { systemTarget, targetFileShare.getId(), sourceSMBShare.getName(), params };
                    _fileDeviceController.createMethod(workflow, null, waitForShareStepGroup,
                            UPDATE_FILESYSTEM_SHARE_ACLS_METHOD, shareACLUpdateStep, stepDescription, systemTarget, args);
                } else {

                    List<ShareACL> shareACLsToAdd = new ArrayList<ShareACL>();
                    List<ShareACL> shareACLsToDelete = new ArrayList<ShareACL>();
                    List<ShareACL> shareACLsToModify = new ArrayList<ShareACL>();

                    List<String> sourceShareACLsNameList = new ArrayList<String>();
                    List<String> targetShareACLsNameList = new ArrayList<String>();

                    for (ShareACL sourceShareACL : sourceShareACLs) {
                        if (sourceShareACL.getUser() != null && !sourceShareACL.getUser().isEmpty()) {
                            sourceShareACLsNameList.add(sourceShareACL.getUser());
                        } else {
                            sourceShareACLsNameList.add(sourceShareACL.getGroup());
                        }
                    }

                    for (ShareACL targetShareACL : targetShareACLs) {
                        if (targetShareACL.getUser() != null && !targetShareACL.getUser().isEmpty()) {
                            targetShareACLsNameList.add(targetShareACL.getUser());
                        } else {
                            targetShareACLsNameList.add(targetShareACL.getGroup());
                        }
                    }

                    // ACLs To Add
                    for (ShareACL sourceShareACL : sourceShareACLs) {
                        if (sourceShareACL.getUser() != null && !sourceShareACL.getUser().isEmpty()
                                && !targetShareACLsNameList.contains(sourceShareACL.getUser())) {
                            ShareACL shareACL = sourceShareACL;
                            shareACL.setFileSystemId(targetFileShare.getId());
                            shareACLsToAdd.add(shareACL);

                        } else if (sourceShareACL.getGroup() != null && !sourceShareACL.getGroup().isEmpty()
                                && !targetShareACLsNameList.contains(sourceShareACL.getGroup())) {
                            ShareACL shareACL = sourceShareACL;
                            shareACL.setFileSystemId(targetFileShare.getId());
                            shareACLsToAdd.add(shareACL);
                        }
                    }

                    // ACLs To Delete
                    for (ShareACL targetShareACL : targetShareACLs) {
                        if (targetShareACL.getUser() != null && !targetShareACL.getUser().isEmpty()
                                && !sourceShareACLsNameList.contains(targetShareACL.getUser())) {
                            shareACLsToDelete.add(targetShareACL);

                        } else if (targetShareACL.getGroup() != null && !targetShareACL.getGroup().isEmpty()
                                && !sourceShareACLsNameList.contains(targetShareACL.getGroup())) {
                            shareACLsToDelete.add(targetShareACL);
                        }
                    }

                    // ACLs to Modify
                    targetShareACLs.removeAll(shareACLsToDelete);
                    sourceShareACLs.removeAll(shareACLsToAdd);
                    for (ShareACL sourceShareACL : sourceShareACLs) {
                        for (ShareACL targetShareACL : targetShareACLs) {

                            if (targetShareACL.getUser() != null && !targetShareACL.getUser().isEmpty()
                                    && targetShareACL.getUser().equals(sourceShareACL.getUser())
                                    && !targetShareACL.getPermission().equals(sourceShareACL.getPermission())) {
                                ShareACL shareACL = targetShareACL;
                                shareACL.setPermission(sourceShareACL.getPermission());
                                shareACLsToModify.add(shareACL);

                            } else if ((targetShareACL.getGroup() != null && !targetShareACL.getGroup().isEmpty())
                                    && targetShareACL.getGroup().equals(sourceShareACL.getGroup())
                                    && !targetShareACL.getPermission().equals(sourceShareACL.getPermission())) {
                                ShareACL shareACL = targetShareACL;
                                shareACL.setPermission(sourceShareACL.getPermission());
                                shareACLsToModify.add(shareACL);
                            }
                        }
                    }

                    params = new CifsShareACLUpdateParams();

                    if (!shareACLsToAdd.isEmpty()) {
                        ShareACLs addShareACLs = new ShareACLs();
                        addShareACLs.setShareACLs(shareACLsToAdd);
                        params.setAclsToAdd(addShareACLs);
                    }
                    if (!shareACLsToDelete.isEmpty()) {
                        ShareACLs deleteShareACLs = new ShareACLs();
                        deleteShareACLs.setShareACLs(shareACLsToDelete);
                        params.setAclsToDelete(deleteShareACLs);
                    }
                    if (!shareACLsToModify.isEmpty()) {
                        ShareACLs modifyShareACLs = new ShareACLs();
                        modifyShareACLs.setShareACLs(shareACLsToModify);
                        params.setAclsToModify(modifyShareACLs);
                    }

                    String stepDescription = String.format(
                            "Replicating Source File System CIFS Share ACLs On Target Cluster, CIFS Share : %s, ACLs details: %s",
                            sourceSMBShare.getName(), params.toString());
                    String shareACLUpdateStep = workflow.createStepId();
                    Object[] args = new Object[] { systemTarget, targetFileShare.getId(), sourceSMBShare.getName(), params };
                    _fileDeviceController.createMethod(workflow, null, waitForShareStepGroup,
                            UPDATE_FILESYSTEM_SHARE_ACLS_METHOD, shareACLUpdateStep, stepDescription, systemTarget, args);
                }
            }
        }
    }

    public static String addStepsToReplicateNFSExports(Workflow workflow, URI systemTarget, FileShare targetFileShare,
            FSExportMap nfsExportMap, StoragePort nfsPort, String waitForFailover) {
        String waitForExport = null;
        s_logger.info("Step 3 : Replicate source NFS exports to target Cluster");

        List<FileExport> nfsExports = new ArrayList<FileExport>(nfsExportMap.values());

        for (FileExport nfsExport : nfsExports) {
            String exportCreationStep = workflow.createStepId();
            FileShareExport fileNFSExport = new FileShareExport(nfsExport.getClients(), nfsExport.getSecurityType(),
                    nfsExport.getPermissions(), nfsExport.getRootUserMapping(),
                    nfsExport.getProtocol(), nfsPort.getPortName(), nfsPort.getPortNetworkId(), targetFileShare.getPath(),
                    targetFileShare.getMountPath(), nfsExport.getSubDirectory(),
                    nfsExport.getComments());
            String stepDescription = "Replicating Source File System NFS Export:" + nfsExport.getMountPath() + " To Target Cluster";
            // waitForExport = _fileDeviceController.addStepsForCreatingNFSExport(workflow, systemTarget, targetFileShare.getId(),
            // Arrays.asList(fileNFSExport), waitForFailover, exportCreationStep, stepDescription);
        }
        return waitForExport;
    }

    public static List<ShareACL> queryShareACLs(String shareName, URI fs) {

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