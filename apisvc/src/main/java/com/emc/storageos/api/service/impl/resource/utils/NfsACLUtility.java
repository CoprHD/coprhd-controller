/*
 * Copyright (c) 2012-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.FileNfsACL;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.model.file.NfsACL;
import com.emc.storageos.model.file.NfsACLUpdateParams;
import com.emc.storageos.model.file.NfsACLUpdateParams.NfsACLOperationErrorType;
import com.emc.storageos.model.file.NfsACLUpdateParams.NfsACLOperationType;
import com.emc.storageos.model.file.NfsACLUpdateParams.NfsPermission;
import com.emc.storageos.model.file.NfsACLs;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.FileControllerConstants;

public class NfsACLUtility {

    private final static Logger _log = LoggerFactory
            .getLogger(NfsACLUtility.class);

    private DbClient dbClient;
    private FileShare fs;
    private Snapshot snapshot;
    private String fileSystemPath;
    private String missingRequestParameterErrorString;
    private List<String> userGroupList;
    public static final String REQUEST_PARAM_PERMISSION_TYPE = "permission_type"; // NOSONAR
                                                                                  // ("Suppressing Sonar violation for variable name should be in camel case")
    public static final String REQUEST_PARAM_PERMISSION = "permission";			  // NOSONAR
    // ("Suppressing Sonar violation for variable name should be in camel case")
    public static final String REQUEST_PARAM_USER = "user";						  // NOSONAR
    // ("Suppressing Sonar violation for variable name should be in camel case")
    public static final String REQUEST_PARAM_GROUP = "group";					  // NOSONAR

    // ("Suppressing Sonar violation for variable name should be in camel case")

    public NfsACLUtility(DbClient dbClient, FileShare fs, Snapshot snapshot,
            String fileSystemPath) {
        super();
        this.dbClient = dbClient;
        this.fs = fs;
        this.snapshot = snapshot;
        this.fileSystemPath = fileSystemPath;
        this.userGroupList = new ArrayList<String>();
    }

    public void verifyNfsACLs(NfsACLUpdateParams param) {

        NfsACLs nfsAcls = null;

        // Add Payload
        nfsAcls = param.getAclsToAdd();
        validateNfsACLs(nfsAcls, NfsACLOperationType.ADD);
        reportErrors(param, NfsACLOperationType.ADD);

        // Modify Payload
        nfsAcls = param.getAclsToModify();
        validateNfsACLs(nfsAcls, NfsACLOperationType.MODIFY);
        reportErrors(param, NfsACLOperationType.MODIFY);

        // Delete Payload
        nfsAcls = param.getAclsToDelete();
        validateNfsACLs(nfsAcls, NfsACLOperationType.DELETE);
        reportErrors(param, NfsACLOperationType.DELETE);

    }

    private void reportErrors(NfsACLUpdateParams param,
            NfsACLOperationType type) {

        _log.info("Report errors if found");

        switch (type) {
            case ADD: {
                reportAddErrors(param);
                break;
            }
            case MODIFY: {
                reportModifyErrors(param);
                break;
            }
            case DELETE: {
                reportDeleteErrors(param);
                break;
            }
        }

    }

    private void reportDeleteErrors(NfsACLUpdateParams param) {

        String opName = NfsACLOperationType.DELETE.name();
        // Report Add ACL Errors
        NfsACLs nfsAcls = param.getAclsToDelete();
        if (nfsAcls == null || nfsAcls.getNfsACLs().isEmpty()) {
            return;
        }

        List<NfsACL> nfsAclList = nfsAcls.getNfsACLs();
        for (NfsACL acl : nfsAclList) {
            if (!acl.canProceedToNextStep()) {
                NfsACLOperationErrorType error = acl.getErrorType();

                switch (error) {

                /*
                 * case SNAPSHOT_EXPORT_SHOULD_BE_READ_ONLY: { throw
                 * APIException.badRequests.snapshotExportPermissionReadOnly();
                 * }
                 */

                    case USER_AND_GROUP_PROVIDED: {
                        throw APIException.badRequests.bothUserAndGroupInACLFound(
                                acl.getUser(), acl.getGroup());
                    }

                    case USER_OR_GROUP_NOT_PROVIDED: {

                        throw APIException.badRequests
                                .missingUserOrGroupInACE(opName);
                    }

                    case MULTIPLE_ACES_WITH_SAME_USER_OR_GROUP: {

                        String userOrGroup = acl.getUser() == null ? acl.getGroup()
                                : acl.getUser();

                        throw APIException.badRequests
                                .multipleACLsWithUserOrGroupFound(opName,
                                        userOrGroup);
                    }

                    case MULTIPLE_DOMAINS_FOUND: {
                        String domain1 = acl.getDomain();
                        String userOrGroup = acl.getUser() == null ? acl.getGroup()
                                : acl.getUser();
                        String domain2 = userOrGroup.substring(0, userOrGroup.indexOf("\\"));
                        throw APIException.badRequests.multipleDomainsFound(opName, domain1, domain2);
                    }

                    case ACL_NOT_FOUND: {
                        throw APIException.badRequests.nfsACLNotFoundFound(
                                opName, acl.toString());
                    }

                    default:
                        break;
                }

            }
        }

    }

    private void reportModifyErrors(NfsACLUpdateParams param) {

        String opName = NfsACLOperationType.MODIFY.name();
        // Report Add ACL Errors
        NfsACLs nfsAcls = param.getAclsToModify();
        if (nfsAcls == null || nfsAcls.getNfsACLs().isEmpty()) {
            return;
        }

        List<NfsACL> nfsAclList = nfsAcls.getNfsACLs();
        for (NfsACL acl : nfsAclList) {
            if (!acl.canProceedToNextStep()) {
                NfsACLOperationErrorType error = acl.getErrorType();

                switch (error) {

                    case SNAPSHOT_FS_SHOULD_BE_READ_ONLY: {
                        throw APIException.badRequests
                                .snapshotFileCifsPermissionReadOnly();
                    }

                    case INVALID_PERMISSION: {
                        if (acl.getPermission() != null) {
                            throw APIException.badRequests
                                    .invalidPermissionForACL(acl.getPermission());
                        } else {
                            throw APIException.badRequests.missingValueInACE(
                                    opName, REQUEST_PARAM_PERMISSION);
                        }
                    }

                    case USER_AND_GROUP_PROVIDED: {
                        throw APIException.badRequests.bothUserAndGroupInACLFound(
                                acl.getUser(), acl.getGroup());
                    }

                    case USER_OR_GROUP_NOT_PROVIDED: {

                        throw APIException.badRequests
                                .missingUserOrGroupInACE(opName);
                    }

                    case MULTIPLE_ACES_WITH_SAME_USER_OR_GROUP: {

                        String userOrGroup = acl.getUser() == null ? acl.getGroup()
                                : acl.getUser();

                        throw APIException.badRequests
                                .multipleACLsWithUserOrGroupFound(opName,
                                        userOrGroup);
                    }

                    case ACL_NOT_FOUND: {
                        throw APIException.badRequests.nfsACLNotFoundFound(
                                opName, acl.toString());
                    }

                    case MULTIPLE_DOMAINS_FOUND: {
                        String domain1 = acl.getDomain();
                        String userOrGroup = acl.getUser() == null ? acl.getGroup()
                                : acl.getUser();
                        String domain2 = userOrGroup.substring(0, userOrGroup.indexOf("\\"));
                        throw APIException.badRequests.multipleDomainsFound(opName, domain1, domain2);
                    }

                    case ACL_EXISTS:
                    default:
                        break;
                }

            }
        }

    }

    private void reportAddErrors(NfsACLUpdateParams param) {

        String opName = NfsACLOperationType.ADD.name();
        // Report Add ACL Errors
        NfsACLs nfsAcls = param.getAclsToAdd();
        if (nfsAcls == null || nfsAcls.getNfsACLs().isEmpty()) {
            return;
        }

        List<NfsACL> nfsAclList = nfsAcls.getNfsACLs();
        for (NfsACL acl : nfsAclList) {
            if (!acl.canProceedToNextStep()) {
                NfsACLOperationErrorType error = acl.getErrorType();

                switch (error) {

                    case SNAPSHOT_FS_SHOULD_BE_READ_ONLY: {
                        throw APIException.badRequests
                                .snapshotFileCifsPermissionReadOnly();
                    }

                    case INVALID_PERMISSION: {
                        if (acl.getPermission() != null) {
                            throw APIException.badRequests
                                    .invalidPermissionForACL(acl.getPermission());
                        } else {
                            throw APIException.badRequests.missingValueInACE(
                                    opName, REQUEST_PARAM_PERMISSION);
                        }
                    }

                    case USER_AND_GROUP_PROVIDED: {
                        throw APIException.badRequests.bothUserAndGroupInACLFound(
                                acl.getUser(), acl.getGroup());
                    }

                    case USER_OR_GROUP_NOT_PROVIDED: {

                        throw APIException.badRequests
                                .missingUserOrGroupInACE(opName);
                    }

                    case MULTIPLE_ACES_WITH_SAME_USER_OR_GROUP: {

                        String userOrGroup = acl.getUser() == null ? acl.getGroup()
                                : acl.getUser();

                        throw APIException.badRequests
                                .multipleACLsWithUserOrGroupFound(opName,
                                        userOrGroup);
                    }

                    case MULTIPLE_DOMAINS_FOUND: {
                        String domain1 = acl.getDomain();
                        String userOrGroup = acl.getUser() == null ? acl.getGroup()
                                : acl.getUser();
                        String domain2 = userOrGroup.substring(0, userOrGroup.indexOf("\\"));
                        throw APIException.badRequests.multipleDomainsFound(opName, domain1, domain2);
                    }

                    case ACL_EXISTS: {
                        throw APIException.badRequests.nfsACLAlreadyExists(
                                opName, acl.toString());
                    }
                    // case ACL_NOT_FOUND:
                    default:
                        break;
                }

            }
        }

    }

    private void validateNfsACLs(NfsACLs nfsAcls,
            NfsACLOperationType type) {

        if (nfsAcls == null) {
            _log.info("Missing ACLs - Ignoring the operation type {} ",
                    type.name());
            missingRequestParameterErrorString = "Missing ACLs - Ignoring the operation type "
                    + type.name();
            return;
        }

        switch (type) {
            case ADD: {
                verifyAddNfsACLs(nfsAcls.getNfsACLs());

                break;
            }
            case MODIFY: {
                verifyModifyNfsACLs(nfsAcls.getNfsACLs());

                break;
            }
            case DELETE: {
                verifyDeleteNfsACLs(nfsAcls.getNfsACLs());
                break;
            }
        }

    }

    private void verifyDeleteNfsACLs(List<NfsACL> nfsAclList) {
        if (nfsAclList == null) {
            return;
        }
        _log.info("Number of NFS ACL(s) to delete {} ", nfsAclList.size());

        for (NfsACL acl : nfsAclList) {
            acl.proceedToNextStep();
            _log.info("Verifying ACL {}", acl.toString());

            // Are there same user or group found in other acls. If so, report
            // error
            verifyUserGroup(acl);

            if (!acl.canProceedToNextStep()) {
                break;
            }

            // Verify with existing ACL
            FileNfsACL dbNfsAcl = getExistingACL(acl);

            // If same acl exists, allow to modify
            if (dbNfsAcl != null) {
                _log.info("Existing ACL found in delete request: {}",
                        dbNfsAcl);
                acl.proceedToNextStep();

            } else {
                // If not found, don't allow to proceed further
                if (acl.canProceedToNextStep()) {
                    _log.error("No existing ACL found in DB to delete {}", acl);
                    acl.cancelNextStep(NfsACLOperationErrorType.ACL_NOT_FOUND);
                }
            }
        }
    }

    private void verifyModifyNfsACLs(List<NfsACL> nfsAclList) {
        if (nfsAclList == null) {
            return;
        }
        _log.info("Number of NFS ACL(s) to modify {} ", nfsAclList.size());

        for (NfsACL acl : nfsAclList) {
            acl.proceedToNextStep();
            _log.info("Verifying ACL {}", acl.toString());

            // Are there same user or group found in other acls. If so, report
            // error
            verifyUserGroup(acl);

            if (!acl.canProceedToNextStep()) {
                break;
            }

            validatePermissions(acl);

            if (!acl.canProceedToNextStep()) {
                break;
            }
            // Verify with existing ACL
            FileNfsACL dbNfsAcl = getExistingACL(acl);

            // If same acl exists, allow to modify
            if (dbNfsAcl != null) {
                _log.info("Existing ACL in modify request: {}", dbNfsAcl);
                acl.proceedToNextStep();

            } else {
                // If not found, don't allow to proceed further
                if (acl.canProceedToNextStep()) {
                    _log.error("No existing ACL found in DB to modify {}", acl);
                    acl.cancelNextStep(NfsACLOperationErrorType.ACL_NOT_FOUND);
                }
            }
        }
    }

    private void verifyAddNfsACLs(List<NfsACL> nfsAclList) {
        if (nfsAclList == null) {
            return;
        }
        _log.info("Number of NFS ACL(s) to add {} ", nfsAclList.size());

        for (NfsACL acl : nfsAclList) {
            acl.proceedToNextStep();
            _log.info("Verifying ACL {}", acl.toString());

            // Are there same user or group found in other acls. If so, report
            // error
            verifyUserGroup(acl);

            if (!acl.canProceedToNextStep()) {
                break;
            }

            validatePermissions(acl);

            if (!acl.canProceedToNextStep()) {
                break;
            }
            // Verify with existing ACL
            FileNfsACL dbNfsAcl = getExistingACL(acl);

            // If same acl exists, don't allow to add again.
            if (dbNfsAcl != null) {
                _log.error(
                        "Duplicate ACL in add request. User/group in ACL for NFS already exists: {}",
                        dbNfsAcl);
                acl.cancelNextStep(NfsACLOperationErrorType.ACL_EXISTS);
                break;
            }
            // If not found proceed for further verifications.
            else {
                if (acl.canProceedToNextStep()) {
                    _log.info("No existing ACL found in DB {}", acl);
                }
            }
        }
    }

    public List<NfsACL> queryExistingNfsACLs() {

        List<NfsACL> aclList = new ArrayList<NfsACL>();

        List<FileNfsACL> dbNfsAclList = queryDBSFileNfsACLs();

        if (dbNfsAclList != null) {
            Iterator<FileNfsACL> fileNfsAclIter = dbNfsAclList.iterator();
            while (fileNfsAclIter.hasNext()) {

                FileNfsACL dbNfsAcl = fileNfsAclIter.next();
                if (fileSystemPath.equals(dbNfsAcl.getFileSystemPath())) {
                    NfsACL acl = new NfsACL();
                    acl.setFileSystemPath(fileSystemPath);
                    acl.setDomain(dbNfsAcl.getDomain());
                    acl.setUser(dbNfsAcl.getUser());
                    acl.setGroup(dbNfsAcl.getGroup());
                    acl.setPermission(dbNfsAcl.getPermission());
                    if (this.fs != null) {
                        acl.setFileSystemId(this.fs.getId());
                    } else {
                        acl.setSnapshotId(this.snapshot.getId());
                    }

                    aclList.add(acl);

                }
            }
        }

        return aclList;

    }

    private List<FileNfsACL> queryDBSFileNfsACLs() {

        try {
            ContainmentConstraint containmentConstraint = null;

            if (this.fs != null) {
                _log.info(
                        "Querying DB for Nfs ACLs of fs{} of filesystemId {} ",
                        this.fileSystemPath, fs.getId());
                containmentConstraint = ContainmentConstraint.Factory
                        .getFileNfsAclsConstraint(this.fs.getId());
            } else {
                // Snapshot
                _log.info(
                        "Querying DB for Nfs ACLs of fs {} of snapshotId {} ",
                        this.fileSystemPath, this.snapshot.getId());
                containmentConstraint = ContainmentConstraint.Factory
                        .getSnapshotNfsAclsConstraint(this.snapshot
                                .getId());
            }

            List<FileNfsACL> nfsAclList = CustomQueryUtility
                    .queryActiveResourcesByConstraint(this.dbClient,
                            FileNfsACL.class, containmentConstraint);

            return nfsAclList;

        } catch (Exception e) {
            _log.error("Error while querying DB for ACL of a NFS {}", e);
        }

        return null;

    }

    private FileNfsACL getExistingACL(NfsACL requestAcl) {
        FileNfsACL acl = null;

        String domainOfReqAce = requestAcl.getDomain();

        if (domainOfReqAce == null) {
            domainOfReqAce = "";
        }

        String userOrGroup = requestAcl.getUser() == null ? requestAcl
                .getGroup() : requestAcl.getUser();
        // Construct ACL Index
        StringBuffer aclIndex = new StringBuffer();
        aclIndex.append(this.fs == null ? this.snapshot.getId().toString()
                : this.fs.getId().toString());
        aclIndex.append(this.fileSystemPath).append(domainOfReqAce)
                .append(userOrGroup);

        acl = this.queryACLByIndex(aclIndex.toString());

        return acl;
    }

    private FileNfsACL queryACLByIndex(String index) {

        _log.info("Querying ACL in DB by alternate Id: {}", index);

        URIQueryResultList result = new URIQueryResultList();
        FileNfsACL acl = null;

        if (this.fs != null) {
            dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getFileSystemNfsACLConstraint(index), result);
        } else {
            dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getSnapshotNfsACLConstraint(index), result);
        }

        Iterator<URI> it = result.iterator();
        while (it.hasNext()) {
            if (result.iterator().hasNext()) {
                acl = dbClient.queryObject(FileNfsACL.class, it.next());
                if (acl != null && !acl.getInactive()) {
                    _log.info("Existing ACE found in DB: {}", acl);
                    break;
                }
            }
        }

        return acl;
    }

    private void validatePermissions(NfsACL acl) {

        if (acl == null) {
            return;
        }

        String permissionValue = acl.getPermission();
        try {
            NfsPermission permission = NfsPermission
                    .valueOf(permissionValue.toUpperCase());
            if (permission != null) {
                acl.setPermission(getFormattedPermissionText(permission));
                acl.proceedToNextStep();
            }
        } catch (Exception e) {
            _log.error("Invalid value for permission: {}", permissionValue);
            acl.cancelNextStep(NfsACLOperationErrorType.INVALID_PERMISSION);
            return;
        }

        if (this.snapshot != null) {
            // Snapshot fs permission must be read only
            if (!NfsPermission.READ.name().equalsIgnoreCase(
                    acl.getPermission())) {
                _log.error("Snapshot permission should be read only");
                acl.cancelNextStep(NfsACLOperationErrorType.SNAPSHOT_FS_SHOULD_BE_READ_ONLY);
            }
        }

    }

    private void verifyUserGroup(NfsACL acl) {

        String userOrGroup = null;

        if (acl == null) {
            return;
        }

        if (acl.getUser() == null && acl.getGroup() == null) {
            acl.cancelNextStep(NfsACLOperationErrorType.USER_OR_GROUP_NOT_PROVIDED);
            _log.error("User or group is missing.");
        } else if (acl.getUser() != null && acl.getGroup() != null) {
            acl.cancelNextStep(NfsACLOperationErrorType.USER_AND_GROUP_PROVIDED);
            _log.error("Either user or group should be provided. Never both.");
        } else {
            String domain = acl.getDomain();
            if (domain == null) {
                domain = "";
            }

            domain = domain.toLowerCase();
            if (acl.getUser() != null) {
                userOrGroup = domain + acl.getUser().toLowerCase();
            } else {
                userOrGroup = domain + acl.getGroup().toLowerCase();
            }
        }

        if (userOrGroup != null) {

            if (!userGroupList.contains(userOrGroup)) {
                userGroupList.add(userOrGroup);
            } else {
                acl.cancelNextStep(NfsACLOperationErrorType.MULTIPLE_ACES_WITH_SAME_USER_OR_GROUP);
                _log.error("There are multiple ACEs with same user or group.");
            }
        }
        // below code is to validate domain
        if (acl.getDomain() != null && acl.getUser() != null) {

            if (acl.getUser().contains("\\")) {
                acl.cancelNextStep(NfsACLOperationErrorType.MULTIPLE_DOMAINS_FOUND);
                _log.error("Multiple Domains found. Please provide either in user or in domain field.");

            }
        }
        if (acl.getDomain() != null && acl.getGroup() != null) {

            if (acl.getGroup().contains("\\")) {
                acl.cancelNextStep(NfsACLOperationErrorType.MULTIPLE_DOMAINS_FOUND);
                _log.error("Multiple Domains found. Please provide either in group or in domain field.");

            }
        }

    }

    private String getFormattedPermissionText(NfsPermission permission) {
        String permissionText = null;

        switch (permission) {
            case READ:
                permissionText = FileControllerConstants.NFS_FILE_PERMISSION_READ;
                break;
            case CHANGE:
                permissionText = FileControllerConstants.NFS_FILE_PERMISSION_CHANGE;
                break;
            case FULLCONTROL:
                permissionText = FileControllerConstants.NFS_FILE_PERMISSION_FULLCONTROL;
                break;
        }
        return permissionText;
    }

    public static void checkForUpdateCifsACLOperationOnStorage(
            String storageSystemType, String operation) {

        StorageSystem.Type storageSystemEnum = Enum.valueOf(
                StorageSystem.Type.class, storageSystemType);

        switch (storageSystemEnum) {
            case vnxe:
            case vnxfile:
            case datadomain:
                throw APIException.badRequests.operationNotSupportedForSystemType(
                        operation, storageSystemType);
        }

    }

}
