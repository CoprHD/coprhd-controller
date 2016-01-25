/*
 * Copyright (c) 2012-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.emc.storageos.db.client.model.DiscoveredDataObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.CifsShareACL;
import com.emc.storageos.db.client.model.FileObject;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.SMBFileShare;
import com.emc.storageos.db.client.model.SMBShareMap;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.model.file.CifsShareACLUpdateParams;
import com.emc.storageos.model.file.CifsShareACLUpdateParams.ShareACLOperationErrorType;
import com.emc.storageos.model.file.CifsShareACLUpdateParams.ShareACLOperationType;
import com.emc.storageos.model.file.CifsShareACLUpdateParams.SharePermission;
import com.emc.storageos.model.file.ShareACL;
import com.emc.storageos.model.file.ShareACLs;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.FileControllerConstants;

public class CifsShareUtility {

    private final static Logger _log = LoggerFactory
            .getLogger(CifsShareUtility.class);

    private DbClient dbClient;
    private FileShare fs;
    private Snapshot snapshot;
    private String shareName;
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

    public CifsShareUtility(DbClient dbClient, FileShare fs, Snapshot snapshot,
            String shareName) {
        super();
        this.dbClient = dbClient;
        this.fs = fs;
        this.snapshot = snapshot;
        this.shareName = shareName;
        this.userGroupList = new ArrayList<String>();
    }

    public void verifyShareACLs(CifsShareACLUpdateParams param) {

        ShareACLs shareAcls = null;

        // Add Payload
        shareAcls = param.getAclsToAdd();
        validateShareACLs(shareAcls, ShareACLOperationType.ADD);
        reportErrors(param, ShareACLOperationType.ADD);

        // Modify Payload
        shareAcls = param.getAclsToModify();
        validateShareACLs(shareAcls, ShareACLOperationType.MODIFY);
        reportErrors(param, ShareACLOperationType.MODIFY);

        // Delete Payload
        shareAcls = param.getAclsToDelete();
        validateShareACLs(shareAcls, ShareACLOperationType.DELETE);
        reportErrors(param, ShareACLOperationType.DELETE);

    }

    private void reportErrors(CifsShareACLUpdateParams param,
            ShareACLOperationType type) {

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

    private void reportDeleteErrors(CifsShareACLUpdateParams param) {

        String opName = ShareACLOperationType.DELETE.name();
        // Report Add ACL Errors
        ShareACLs shareAcls = param.getAclsToDelete();
        if (shareAcls == null || shareAcls.getShareACLs().isEmpty()) {
            return;
        }

        List<ShareACL> shareAclList = shareAcls.getShareACLs();
        for (ShareACL acl : shareAclList) {
            if (!acl.canProceedToNextStep()) {
                ShareACLOperationErrorType error = acl.getErrorType();

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
                        throw APIException.badRequests.shareACLNotFoundFound(
                                opName, acl.toString());
                    }

                    default:
                        break;
                }

            }
        }

    }

    private void reportModifyErrors(CifsShareACLUpdateParams param) {

        String opName = ShareACLOperationType.MODIFY.name();
        // Report Add ACL Errors
        ShareACLs shareAcls = param.getAclsToModify();
        if (shareAcls == null || shareAcls.getShareACLs().isEmpty()) {
            return;
        }

        List<ShareACL> shareAclList = shareAcls.getShareACLs();
        for (ShareACL acl : shareAclList) {
            if (!acl.canProceedToNextStep()) {
                ShareACLOperationErrorType error = acl.getErrorType();

                switch (error) {

                    case SNAPSHOT_SHARE_SHOULD_BE_READ_ONLY: {
                        throw APIException.badRequests
                                .snapshotSMBSharePermissionReadOnly();
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
                        throw APIException.badRequests.shareACLNotFoundFound(
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

    private void reportAddErrors(CifsShareACLUpdateParams param) {

        String opName = ShareACLOperationType.ADD.name();
        // Report Add ACL Errors
        ShareACLs shareAcls = param.getAclsToAdd();
        if (shareAcls == null || shareAcls.getShareACLs().isEmpty()) {
            return;
        }

        List<ShareACL> shareAclList = shareAcls.getShareACLs();
        for (ShareACL acl : shareAclList) {
            if (!acl.canProceedToNextStep()) {
                ShareACLOperationErrorType error = acl.getErrorType();

                switch (error) {

                    case SNAPSHOT_SHARE_SHOULD_BE_READ_ONLY: {
                        throw APIException.badRequests
                                .snapshotSMBSharePermissionReadOnly();
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
                        throw APIException.badRequests.shareACLAlreadyExists(
                                opName, acl.toString());
                    }
                    // case ACL_NOT_FOUND:
                    default:
                        break;
                }

            }
        }

    }

    private void validateShareACLs(ShareACLs shareAcls,
            ShareACLOperationType type) {

        if (shareAcls == null) {
            _log.info("Missing ACLs - Ignoring the operation type {} ",
                    type.name());
            missingRequestParameterErrorString = "Missing ACLs - Ignoring the operation type "
                    + type.name();
            return;
        }

        switch (type) {
            case ADD: {
                verifyAddShareACLs(shareAcls.getShareACLs());

                break;
            }
            case MODIFY: {
                verifyModifyShareACLs(shareAcls.getShareACLs());

                break;
            }
            case DELETE: {
                verifyDeleteShareACLs(shareAcls.getShareACLs());
                break;
            }
        }

    }

    private void verifyDeleteShareACLs(List<ShareACL> shareAclList) {
        if (shareAclList == null) {
            return;
        }
        _log.info("Number of share ACL(s) to delete {} ", shareAclList.size());

        for (ShareACL acl : shareAclList) {
            acl.proceedToNextStep();
            _log.info("Verifying ACL {}", acl.toString());

            // Are there same user or group found in other acls. If so, report
            // error
            verifyUserGroup(acl);

            if (!acl.canProceedToNextStep()) {
                break;
            }

            // Verify with existing ACL
            CifsShareACL dbShareAcl = getExistingACL(acl);

            // If same acl exists, allow to modify
            if (dbShareAcl != null) {
                _log.info("Existing ACL found in delete request: {}",
                        dbShareAcl);
                acl.proceedToNextStep();

            } else {
                // If not found, don't allow to proceed further
                if (acl.canProceedToNextStep()) {
                    _log.error("No existing ACL found in DB to delete {}", acl);
                    acl.cancelNextStep(ShareACLOperationErrorType.ACL_NOT_FOUND);
                }
            }
        }
    }

    private void verifyModifyShareACLs(List<ShareACL> shareAclList) {
        if (shareAclList == null) {
            return;
        }
        _log.info("Number of share ACL(s) to modify {} ", shareAclList.size());

        for (ShareACL acl : shareAclList) {
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
            CifsShareACL dbShareAcl = getExistingACL(acl);

            // If same acl exists, allow to modify
            if (dbShareAcl != null) {
                _log.info("Existing ACL in modify request: {}", dbShareAcl);
                acl.proceedToNextStep();

            } else {
                // If not found, don't allow to proceed further
                if (acl.canProceedToNextStep()) {
                    _log.error("No existing ACL found in DB to modify {}", acl);
                    acl.cancelNextStep(ShareACLOperationErrorType.ACL_NOT_FOUND);
                }
            }
        }
    }

    private void verifyAddShareACLs(List<ShareACL> shareAclList) {
        if (shareAclList == null) {
            return;
        }
        _log.info("Number of share ACL(s) to add {} ", shareAclList.size());

        for (ShareACL acl : shareAclList) {
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
            CifsShareACL dbShareAcl = getExistingACL(acl);

            // If same acl exists, don't allow to add again.
            if (dbShareAcl != null) {
                _log.error(
                        "Duplicate ACL in add request. User/group in ACL for share already exists: {}",
                        dbShareAcl);
                acl.cancelNextStep(ShareACLOperationErrorType.ACL_EXISTS);
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

    public List<ShareACL> queryExistingShareACLs() {

        List<ShareACL> aclList = new ArrayList<ShareACL>();

        List<CifsShareACL> dbShareAclList = queryDBShareACLs();

        if (dbShareAclList != null) {
            Iterator<CifsShareACL> shareAclIter = dbShareAclList.iterator();
            while (shareAclIter.hasNext()) {

                CifsShareACL dbShareAcl = shareAclIter.next();
                if (shareName.equals(dbShareAcl.getShareName())) {
                    ShareACL acl = new ShareACL();
                    acl.setShareName(shareName);
                    acl.setDomain(dbShareAcl.getDomain());
                    acl.setUser(dbShareAcl.getUser());
                    acl.setGroup(dbShareAcl.getGroup());
                    acl.setPermission(dbShareAcl.getPermission());
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

    private List<CifsShareACL> queryDBShareACLs() {

        try {
            ContainmentConstraint containmentConstraint = null;

            if (this.fs != null) {
                _log.info(
                        "Querying DB for Share ACLs of share {} of filesystemId {} ",
                        this.shareName, fs.getId());
                containmentConstraint = ContainmentConstraint.Factory
                        .getFileCifsShareAclsConstraint(this.fs.getId());
            } else {
                // Snapshot
                _log.info(
                        "Querying DB for Share ACLs of share {} of snapshotId {} ",
                        this.shareName, this.snapshot.getId());
                containmentConstraint = ContainmentConstraint.Factory
                        .getSnapshotCifsShareAclsConstraint(this.snapshot
                                .getId());
            }

            List<CifsShareACL> shareAclList = CustomQueryUtility
                    .queryActiveResourcesByConstraint(this.dbClient,
                            CifsShareACL.class, containmentConstraint);

            return shareAclList;

        } catch (Exception e) {
            _log.error("Error while querying DB for ACL of a share {}", e);
        }

        return null;

    }

    private CifsShareACL getExistingACL(ShareACL requestAcl) {
        CifsShareACL acl = null;

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
        aclIndex.append(this.shareName).append(domainOfReqAce)
                .append(userOrGroup);

        acl = this.queryACLByIndex(aclIndex.toString());

        return acl;
    }

    private CifsShareACL queryACLByIndex(String index) {

        _log.info("Querying ACL in DB by alternate Id: {}", index);

        URIQueryResultList result = new URIQueryResultList();
        CifsShareACL acl = null;

        if (this.fs != null) {
            dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getFileSystemShareACLConstraint(index), result);
        } else {
            dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getSnapshotShareACLConstraint(index), result);
        }

        Iterator<URI> it = result.iterator();
        while (it.hasNext()) {
            if (result.iterator().hasNext()) {
                acl = dbClient.queryObject(CifsShareACL.class, it.next());
                if (acl != null && !acl.getInactive()) {
                    _log.info("Existing ACE found in DB: {}", acl);
                    break;
                }
            }
        }

        return acl;
    }

    private void validatePermissions(ShareACL acl) {

        if (acl == null) {
            return;
        }
        /*
         * String permissionTypeValue = acl.getPermissionType(); try {
         * SharePermissionType permissionType = SharePermissionType
         * .valueOf(permissionTypeValue.toUpperCase()); if (permissionType !=
         * null) { acl.proceedToNextStep(); } } catch (Exception e) {
         * _log.error("Invalid value for permission type: {}",
         * permissionTypeValue);
         * acl.cancelNextStep(ShareACLOperationErrorType.INVALID_PERMISSION_TYPE
         * ); return; }
         */
        String permissionValue = acl.getPermission();
        try {
            SharePermission permission = SharePermission
                    .valueOf(permissionValue.toUpperCase());
            if (permission != null) {
                acl.setPermission(getFormattedPermissionText(permission));
                acl.proceedToNextStep();
            }
        } catch (Exception e) {
            _log.error("Invalid value for permission: {}", permissionValue);
            acl.cancelNextStep(ShareACLOperationErrorType.INVALID_PERMISSION);
            return;
        }

        if (this.snapshot != null) {
            // Snapshot share permission must be read only
            if (!SharePermission.READ.name().equalsIgnoreCase(
                    acl.getPermission())) {
                _log.error("Snapshot permission should be read only");
                acl.cancelNextStep(ShareACLOperationErrorType.SNAPSHOT_SHARE_SHOULD_BE_READ_ONLY);
            }
        }

    }

    private void verifyUserGroup(ShareACL acl) {

        String userOrGroup = null;

        if (acl == null) {
            return;
        }

        if (acl.getUser() == null && acl.getGroup() == null) {
            acl.cancelNextStep(ShareACLOperationErrorType.USER_OR_GROUP_NOT_PROVIDED);
            _log.error("User or group is missing.");
        } else if (acl.getUser() != null && acl.getGroup() != null) {
            acl.cancelNextStep(ShareACLOperationErrorType.USER_AND_GROUP_PROVIDED);
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
                acl.cancelNextStep(ShareACLOperationErrorType.MULTIPLE_ACES_WITH_SAME_USER_OR_GROUP);
                _log.error("There are multiple ACEs with same user or group.");
            }
        }
        // below code is to validate domain
        if (acl.getDomain() != null && acl.getUser() != null) {

            if (acl.getUser().contains("\\")) {
                acl.cancelNextStep(ShareACLOperationErrorType.MULTIPLE_DOMAINS_FOUND);
                _log.error("Multiple Domains found. Please provide either in user or in domain field.");

            }
        }
        if (acl.getDomain() != null && acl.getGroup() != null) {

            if (acl.getGroup().contains("\\")) {
                acl.cancelNextStep(ShareACLOperationErrorType.MULTIPLE_DOMAINS_FOUND);
                _log.error("Multiple Domains found. Please provide either in group or in domain field.");

            }
        }

    }

    private String getFormattedPermissionText(SharePermission permission) {
        String permissionText = null;

        switch (permission) {
            case READ:
                permissionText = FileControllerConstants.CIFS_SHARE_PERMISSION_READ;
                break;
            case CHANGE:
                permissionText = FileControllerConstants.CIFS_SHARE_PERMISSION_CHANGE;
                break;
            case FULLCONTROL:
                permissionText = FileControllerConstants.CIFS_SHARE_PERMISSION_FULLCONTROL;
                break;
        }
        return permissionText;
    }

    public static void checkForUpdateShareACLOperationOnStorage(
            String storageType, String operation) {

        StorageSystem.Type storageSystemType = StorageSystem.Type.valueOf(storageType);

        if (storageSystemType.equals(DiscoveredDataObject.Type.vnxe) || storageSystemType.equals(DiscoveredDataObject.Type.vnxfile)
                || storageSystemType.equals(DiscoveredDataObject.Type.datadomain)) {
                throw APIException.badRequests.operationNotSupportedForSystemType(
                        operation, storageType);
        }
    }

    /**
     * Checks whether share exists in the file object
     * 
     * @param fileObject fileshare or snapshot object
     * @param shareName name of the share
     * @return true if share exists in the SMBShareMap, false otherwise
     */
    public static boolean doesShareExist(FileObject fileObject, String shareName) {

        SMBShareMap existingShares = fileObject.getSMBFileShares();
        if (existingShares != null && !existingShares.isEmpty()) {
            SMBFileShare existingShare = existingShares.get(shareName);
            if (existingShare != null) {
                _log.info("CIFS share: {}, exists in ", shareName, fileObject.getId());
                return true;
            }
        }
        _log.info("CIFS share: {}, does not exist in {}", shareName, fileObject.getId());
        return false;
    }
}
