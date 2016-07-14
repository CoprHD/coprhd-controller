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

public class FileFailoverOrchestration extends FileOrchestrationDeviceController {

    public static String failoverFileSystem(Workflow workflow, URI systemTarget, FileShare sourceFileShare, FileShare targetFileShare) {
        s_logger.info("Step 1:- Failover FileSystem to target Cluster");
        String failoverStep = workflow.createStepId();
        MirrorFileFailoverTaskCompleter completer = new MirrorFileFailoverTaskCompleter(sourceFileShare.getId(), targetFileShare.getId(),
                failoverStep);
        String stepDescription = "Failover Source File System:" + sourceFileShare.getLabel() + " To Target Cluster";
        Object[] args = new Object[] { systemTarget, targetFileShare.getId(), completer };
        String waitForFailover = _fileReplicationDeviceController.createMethod(workflow, null, null,
                FAILOVER_FILE_SYSTEM_METH, failoverStep, stepDescription, systemTarget, args);
        return waitForFailover;
    }

    public static String replicateCIFSsharesToTarget(Workflow workflow, URI systemTarget, FileShare sourceFileShare,
            FileShare targetFileShare, SMBShareMap sourceSMBShareMap, StoragePort cifsPort, String waitForFailover) {
        String shareStepGroup = "Replicate CIFS Shares To Target Cluster";
        s_logger.info("Step 2 : Replicate source CIFS shares and ACLs to target Cluster");

        List<SMBFileShare> sourceSMBShares = new ArrayList<SMBFileShare>(sourceSMBShareMap.values());
        SMBShareMap targetSMBShareMap = targetFileShare.getSMBFileShares();

        if (targetSMBShareMap == null) {
            createCIFSShareOnTarget(workflow, systemTarget, sourceSMBShares, cifsPort, targetFileShare, sourceFileShare,
                    waitForFailover, shareStepGroup);
        } else {
            List<SMBFileShare> targetSMBShares = new ArrayList<SMBFileShare>(targetSMBShareMap.values());
            List<SMBFileShare> targetSMBSharestoDelete = new ArrayList<SMBFileShare>();
            List<SMBFileShare> targetSMBSharestoCreate = new ArrayList<SMBFileShare>();

            for (SMBFileShare sourceSMBShare : sourceSMBShares) {
                if (!targetSMBShares.contains(sourceSMBShare)) {
                    targetSMBSharestoCreate.add(sourceSMBShare);
                }
            }
            for (SMBFileShare targetSMBShare : targetSMBShares) {
                if (!sourceSMBShares.contains(targetSMBShare)) {
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
            String stepDescription = "Creating Source File System CIFS Share:" + fileSMBShare.getName() + " On Target Cluster";
            Object[] args = new Object[] { systemTarget, targetFileShare.getId(), fileSMBShare };
            String waitForShare = _fileDeviceController.createMethod(workflow, shareStepGroup, waitForFailover,
                    CREATE_FILESYSTEM_SHARE_METHOD, shareCreationStep, stepDescription, systemTarget, args);

            List<ShareACL> shareACLsList = queryShareACLs(smbShare.getName(), sourceFileShare.getId());
            CifsShareACLUpdateParams params = new CifsShareACLUpdateParams();
            ShareACLs shareACLs = new ShareACLs();
            shareACLs.setShareACLs(shareACLsList);
            params.setAclsToAdd(shareACLs);

            stepDescription = "Adding Source File System Share ACLs : " + fileSMBShare.getName() + " To Target Cluster";
            String shareACLUpdateStep = workflow.createStepId();
            Object[] updateACLargs = new Object[] { systemTarget, targetFileShare.getId(), smbShare.getName(), params };
            waitForShare = _fileDeviceController.createMethod(workflow, shareStepGroup, waitForShare, UPDATE_FILESYSTEM_SHARE_ACLS_METHOD,
                    shareACLUpdateStep, stepDescription, systemTarget, updateACLargs);
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

    public static String replicateNFSExportsToTarget(Workflow workflow, URI systemTarget, FileShare targetFileShare,
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
        ContainmentConstraint containmentConstraint = ContainmentConstraint.Factory
                .getFileCifsShareAclsConstraint(fs);
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