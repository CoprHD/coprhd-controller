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
import com.emc.storageos.db.client.model.ObjectBucketACL;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.model.object.BucketACE;
import com.emc.storageos.model.object.BucketACL;
import com.emc.storageos.model.object.BucketACLUpdateParams;
import com.emc.storageos.model.object.BucketACLUpdateParams.BucketACLOperationErrorType;
import com.emc.storageos.model.object.BucketACLUpdateParams.BucketACLOperationType;
import com.emc.storageos.model.object.BucketACLUpdateParams.BucketPermissions;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

public class BucketACLUtility {

    private final static Logger _log = LoggerFactory
            .getLogger(BucketACLUtility.class);

    private DbClient dbClient;
    private String bucketName;
    private URI bucketId;

    private List<String> usersGroupsCustomGroups;

    public static final String REQUEST_PARAM_PERMISSIONS = "permissions";           // NOSONAR
    // ("Suppressing Sonar violation for variable name should be in camel case")
    public static final String REQUEST_PARAM_USER = "user";                       // NOSONAR
    // ("Suppressing Sonar violation for variable name should be in camel case")
    public static final String REQUEST_PARAM_GROUP = "group";                     // NOSONAR
    // ("Suppressing Sonar violation for variable name should be in camel case")
    public static final String REQUEST_PARAM_CUSTOM_GROUP = "customgroup";        // NOSONAR

    // ("Suppressing Sonar violation for variable name should be in camel case")

    public BucketACLUtility(DbClient dbClient, String bucketName, URI bucketId) {
        super();
        this.dbClient = dbClient;
        this.bucketName = bucketName;
        this.bucketId = bucketId;
        this.usersGroupsCustomGroups = new ArrayList<String>();
    }

    public void verifyBucketACL(BucketACLUpdateParams param) {

        BucketACL bucketAcl = null;

        // Add Payload
        bucketAcl = param.getAclToAdd();
        validateBucketACL(bucketAcl, BucketACLOperationType.ADD);
        reportErrors(param, BucketACLOperationType.ADD);

        // Modify Payload
        bucketAcl = param.getAclToModify();
        validateBucketACL(bucketAcl, BucketACLOperationType.MODIFY);
        reportErrors(param, BucketACLOperationType.MODIFY);

        // Delete Payload
        bucketAcl = param.getAclToDelete();
        validateBucketACL(bucketAcl, BucketACLOperationType.DELETE);
        reportErrors(param, BucketACLOperationType.DELETE);

    }

    private void reportErrors(BucketACLUpdateParams param,
            BucketACLOperationType type) {

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

    private void reportDeleteErrors(BucketACLUpdateParams param) {

        String opName = BucketACLOperationType.DELETE.name();
        // Report Add ACL Errors
        BucketACL bucketACL = param.getAclToDelete();
        if (bucketACL == null || bucketACL.getBucketACL().isEmpty()) {
            return;
        }

        List<BucketACE> bucketACELits = bucketACL.getBucketACL();
        for (BucketACE bucketACE : bucketACELits) {
            if (!bucketACE.canProceedToNextStep()) {
                BucketACLOperationErrorType error = bucketACE.getErrorType();

                switch (error) {

                    case USER_AND_GROUP_AND_CUSTOMGROUP_PROVIDED: {
                        throw APIException.badRequests.userGroupAndCustomGroupInACLFound(
                                bucketACE.getUser(), bucketACE.getGroup(), bucketACE.getCustomGroup());
                    }

                    case USER_OR_GROUP_OR_CUSTOMGROUP_NOT_PROVIDED: {

                        throw APIException.badRequests
                                .missingUserOrGroupOrCustomGroupInACE(opName);
                    }

                    case MULTIPLE_ACES_WITH_SAME_USER_OR_GROUP_CUSTOMGROUP: {

                        String userOrGroupOrCustomgroup = bucketACE.getUser();

                        if (userOrGroupOrCustomgroup == null) {
                            userOrGroupOrCustomgroup = bucketACE.getGroup() != null ? bucketACE.getGroup()
                                    : bucketACE.getCustomGroup();
                        }

                        throw APIException.badRequests
                                .multipleACLsWithUserOrGroupOrCustomGroupFound(opName,
                                        userOrGroupOrCustomgroup);
                    }

                    case MULTIPLE_DOMAINS_FOUND: {
                        String domain1 = bucketACE.getDomain();
                        String userOrGroupOrCustomgroup = bucketACE.getUser();
                        if (userOrGroupOrCustomgroup == null) {
                            userOrGroupOrCustomgroup = bucketACE.getGroup() != null ? bucketACE.getGroup()
                                    : bucketACE.getCustomGroup();
                        }
                        String domain2 = userOrGroupOrCustomgroup.substring(0, userOrGroupOrCustomgroup.indexOf("\\"));
                        throw APIException.badRequests.multipleDomainsFound(opName, domain1, domain2);
                    }

                    case ACL_NOT_FOUND: {
                        throw APIException.badRequests.bucketACLNotFoundFound(
                                opName, bucketACE.toString());
                    }

                    default:
                        break;
                }

            }
        }

    }

    private void reportModifyErrors(BucketACLUpdateParams param) {

        String opName = BucketACLOperationType.MODIFY.name();
        // Report Add ACL Errors
        BucketACL bucketACL = param.getAclToModify();
        if (bucketACL == null || bucketACL.getBucketACL().isEmpty()) {
            return;
        }

        List<BucketACE> bucketACEList = bucketACL.getBucketACL();
        for (BucketACE bucketACE : bucketACEList) {
            if (!bucketACE.canProceedToNextStep()) {
                BucketACLOperationErrorType error = bucketACE.getErrorType();

                switch (error) {

                    case INVALID_PERMISSIONS: {
                        if (bucketACE.getPermissions() != null) {
                            throw APIException.badRequests
                                    .invalidPermissionForBucketACL(bucketACE.getPermissions());
                        } else {
                            throw APIException.badRequests.missingValueInACE(
                                    opName, REQUEST_PARAM_PERMISSIONS);
                        }
                    }

                    case USER_AND_GROUP_AND_CUSTOMGROUP_PROVIDED: {
                        throw APIException.badRequests.userGroupAndCustomGroupInACLFound(
                                bucketACE.getUser(), bucketACE.getGroup(), bucketACE.getCustomGroup());
                    }

                    case USER_OR_GROUP_OR_CUSTOMGROUP_NOT_PROVIDED: {

                        throw APIException.badRequests
                                .missingUserOrGroupOrCustomGroupInACE(opName);
                    }

                    case MULTIPLE_ACES_WITH_SAME_USER_OR_GROUP_CUSTOMGROUP: {

                        String userOrGroupOrCustomgroup = bucketACE.getUser();

                        if (userOrGroupOrCustomgroup == null) {
                            userOrGroupOrCustomgroup = bucketACE.getGroup() != null ? bucketACE.getGroup()
                                    : bucketACE.getCustomGroup();
                        }

                        throw APIException.badRequests
                                .multipleACLsWithUserOrGroupOrCustomGroupFound(opName,
                                        userOrGroupOrCustomgroup);
                    }

                    case ACL_NOT_FOUND: {
                        throw APIException.badRequests.bucketACLNotFoundFound(
                                opName, bucketACE.toString());
                    }

                    case MULTIPLE_DOMAINS_FOUND: {
                        String domain1 = bucketACE.getDomain();
                        String userOrGroupOrCustomgroup = bucketACE.getUser();
                        if (userOrGroupOrCustomgroup == null) {
                            userOrGroupOrCustomgroup = bucketACE.getGroup() != null ? bucketACE.getGroup()
                                    : bucketACE.getCustomGroup();
                        }
                        String domain2 = userOrGroupOrCustomgroup.substring(0, userOrGroupOrCustomgroup.indexOf("\\"));
                        throw APIException.badRequests.multipleDomainsFound(opName, domain1, domain2);
                    }

                    case ACL_EXISTS:
                    default:
                        break;
                }

            }
        }

    }

    private void reportAddErrors(BucketACLUpdateParams param) {

        String opName = BucketACLOperationType.ADD.name();
        // Report Add ACL Errors
        BucketACL bucketAcl = param.getAclToAdd();
        if (bucketAcl == null || bucketAcl.getBucketACL().isEmpty()) {
            return;
        }

        List<BucketACE> bucketACEList = bucketAcl.getBucketACL();
        for (BucketACE bucketACE : bucketACEList) {
            if (!bucketACE.canProceedToNextStep()) {
                BucketACLOperationErrorType error = bucketACE.getErrorType();

                switch (error) {

                    case INVALID_PERMISSIONS: {
                        if (bucketACE.getPermissions() != null) {
                            throw APIException.badRequests
                                    .invalidPermissionForBucketACL(bucketACE.getPermissions());
                        } else {
                            throw APIException.badRequests.missingValueInACE(
                                    opName, REQUEST_PARAM_PERMISSIONS);
                        }
                    }

                    case USER_AND_GROUP_AND_CUSTOMGROUP_PROVIDED: {
                        throw APIException.badRequests.userGroupAndCustomGroupInACLFound(
                                bucketACE.getUser(), bucketACE.getGroup(), bucketACE.getCustomGroup());
                    }

                    case USER_OR_GROUP_OR_CUSTOMGROUP_NOT_PROVIDED: {

                        throw APIException.badRequests
                                .missingUserOrGroupOrCustomGroupInACE(opName);
                    }

                    case MULTIPLE_ACES_WITH_SAME_USER_OR_GROUP_CUSTOMGROUP: {

                        String userOrGroupOrCustomgroup = bucketACE.getUser();

                        if (userOrGroupOrCustomgroup == null) {
                            userOrGroupOrCustomgroup = bucketACE.getGroup() != null ? bucketACE.getGroup()
                                    : bucketACE.getCustomGroup();
                        }

                        throw APIException.badRequests
                                .multipleACLsWithUserOrGroupOrCustomGroupFound(opName,
                                        userOrGroupOrCustomgroup);
                    }

                    case MULTIPLE_DOMAINS_FOUND: {
                        String domain1 = bucketACE.getDomain();
                        String userOrGroupOrCustomgroup = bucketACE.getUser();
                        if (userOrGroupOrCustomgroup == null) {
                            userOrGroupOrCustomgroup = bucketACE.getGroup() != null ? bucketACE.getGroup()
                                    : bucketACE.getCustomGroup();
                        }
                        String domain2 = userOrGroupOrCustomgroup.substring(0, userOrGroupOrCustomgroup.indexOf("\\"));
                        throw APIException.badRequests.multipleDomainsFound(opName, domain1, domain2);
                    }

                    case ACL_EXISTS: {
                        throw APIException.badRequests.bucketACLAlreadyExists(
                                opName, bucketACE.toString());
                    }
                    // case ACL_NOT_FOUND:
                    default:
                        break;
                }

            }
        }

    }

    private void validateBucketACL(BucketACL bucketACL,
            BucketACLOperationType type) {

        if (bucketACL == null) {
            _log.info("Missing ACLs - Ignoring the operation type {} ",
                    type.name());
            return;
        }

        switch (type) {
            case ADD: {
                verifyAddBucketACL(bucketACL.getBucketACL());

                break;
            }
            case MODIFY: {
                verifyModifyBucketACL(bucketACL.getBucketACL());

                break;
            }
            case DELETE: {
                verifyDeleteBucketACL(bucketACL.getBucketACL());
                break;
            }
        }

    }

    private void verifyAddBucketACL(List<BucketACE> bucketACEList) {
        if (bucketACEList == null) {
            return;
        }
        _log.info("Number of bucket ACE(s) to add {} ", bucketACEList.size());

        for (BucketACE ace : bucketACEList) {
            ace.proceedToNextStep();
            _log.info("Verifying ACL {}", ace.toString());

            // Are there same user or group found in other acls. If so, report
            // error
            verifyUserGroupCustomgroup(ace);

            if (!ace.canProceedToNextStep()) {
                break;
            }

            validatePermissions(ace);

            if (!ace.canProceedToNextStep()) {
                break;
            }
            // Verify with existing ACL
            ObjectBucketACL dbBucketAcl = getExistingACL(ace);

            // If same acl exists, don't allow to add again.
            if (dbBucketAcl != null) {
                _log.error(
                        "Duplicate ACL in add request. User/group in ACL for bucket already exists: {}",
                        dbBucketAcl);
                ace.cancelNextStep(BucketACLOperationErrorType.ACL_EXISTS);
                break;
            }
            // If not found proceed for further verifications.
            else {
                if (ace.canProceedToNextStep()) {
                    _log.info("No existing ACL found in DB {}", ace);
                }
            }
        }
    }

    private void verifyModifyBucketACL(List<BucketACE> bucketACEList) {
        if (bucketACEList == null) {
            return;
        }
        _log.info("Number of bucket ACE(s) to modify {} ", bucketACEList.size());

        for (BucketACE ace : bucketACEList) {
            ace.proceedToNextStep();
            _log.info("Verifying ACL {}", ace.toString());

            // Are there same user or group found in other acls. If so, report
            // error
            verifyUserGroupCustomgroup(ace);

            if (!ace.canProceedToNextStep()) {
                break;
            }

            validatePermissions(ace);

            if (!ace.canProceedToNextStep()) {
                break;
            }
            // Verify with existing ACL
            ObjectBucketACL dbBucketAcl = getExistingACL(ace);

            // If same acl exists, allow to modify
            if (dbBucketAcl != null) {
                _log.info("Existing ACL in modify request: {}", dbBucketAcl);
                ace.proceedToNextStep();

            } else {
                // If not found, don't allow to proceed further
                if (ace.canProceedToNextStep()) {
                    _log.error("No existing ACL found in DB to modify {}", ace);
                    ace.cancelNextStep(BucketACLOperationErrorType.ACL_NOT_FOUND);
                }
            }
        }
    }

    private void verifyDeleteBucketACL(List<BucketACE> bucketACEList) {
        if (bucketACEList == null) {
            return;
        }
        _log.info("Number of bucket ACE(s) to delete {} ", bucketACEList.size());

        for (BucketACE ace : bucketACEList) {
            ace.proceedToNextStep();
            _log.info("Verifying ACL {}", ace.toString());

            // Are there same user or group found in other acls. If so, report
            // error
            verifyUserGroupCustomgroup(ace);

            if (!ace.canProceedToNextStep()) {
                break;
            }

            // Verify with existing ACL
            ObjectBucketACL dbBucketAcl = getExistingACL(ace);

            // If same acl exists, allow to modify
            if (dbBucketAcl != null) {
                _log.info("Existing ACL found in delete request: {}",
                        dbBucketAcl);
                ace.proceedToNextStep();

            } else {
                // If not found, don't allow to proceed further
                if (ace.canProceedToNextStep()) {
                    _log.error("No existing ACL found in DB to delete {}", ace);
                    ace.cancelNextStep(BucketACLOperationErrorType.ACL_NOT_FOUND);
                }
            }
        }
    }

    public List<BucketACE> queryExistingBucketACL() {

        List<BucketACE> bucketACEList = new ArrayList<BucketACE>();

        List<ObjectBucketACL> dbBucketACL = queryDbBucketACL();

        if (dbBucketACL != null) {
            Iterator<ObjectBucketACL> dbAclIterator = dbBucketACL.iterator();
            while (dbAclIterator.hasNext()) {

                ObjectBucketACL dbBucketAce = dbAclIterator.next();
                if (bucketId.equals(dbBucketAce.getBucketId())) {
                    BucketACE ace = new BucketACE();
                    ace.setBucketName(dbBucketAce.getBucketName());
                    ace.setDomain(dbBucketAce.getDomain());
                    ace.setUser(dbBucketAce.getUser());
                    ace.setGroup(dbBucketAce.getGroup());
                    ace.setPermissions(dbBucketAce.getPermissions());
                    ace.setCustomGroup(dbBucketAce.getCustomGroup());
                    ace.setNamespace(dbBucketAce.getNamespace());

                    bucketACEList.add(ace);

                }
            }
        }

        return bucketACEList;

    }

    private List<ObjectBucketACL> queryDbBucketACL() {

        try {
            ContainmentConstraint containmentConstraint = null;

            _log.info("Querying DB for ACL of bucket {} ",
                    this.bucketName);
            containmentConstraint = ContainmentConstraint.Factory
                    .getBucketAclsConstraint(this.bucketId);

            List<ObjectBucketACL> dbBucketBucketAcl = CustomQueryUtility
                    .queryActiveResourcesByConstraint(this.dbClient,
                            ObjectBucketACL.class, containmentConstraint);

            return dbBucketBucketAcl;

        } catch (Exception e) {
            _log.error("Error while querying DB for ACL of a bucket {}", e);
        }

        return null;

    }

    private ObjectBucketACL getExistingACL(BucketACE requestAcl) {
        ObjectBucketACL acl = null;

        String domainOfReqAce = requestAcl.getDomain();

        if (domainOfReqAce == null) {
            domainOfReqAce = "";
        }

        String userOrGroupOrCustomGroup = requestAcl.getUser();

        if (userOrGroupOrCustomGroup == null) {
            userOrGroupOrCustomGroup = requestAcl.getGroup() != null ? requestAcl
                    .getGroup() : requestAcl.getCustomGroup();
        }

        // Construct ACL Index
        StringBuffer aclIndex = new StringBuffer();
        aclIndex.append(this.bucketId).append(domainOfReqAce).append(userOrGroupOrCustomGroup.toLowerCase());

        acl = this.queryACLByIndex(aclIndex.toString());

        return acl;
    }

    private ObjectBucketACL queryACLByIndex(String index) {

        _log.info("Querying ACL in DB by alternate Id: {}", index);

        URIQueryResultList result = new URIQueryResultList();
        ObjectBucketACL acl = null;
        dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getBucketACLConstraint(index), result);

        Iterator<URI> it = result.iterator();
        while (it.hasNext()) {
            if (result.iterator().hasNext()) {
                acl = dbClient.queryObject(ObjectBucketACL.class, it.next());
                if (acl != null && !acl.getInactive()) {
                    _log.info("Existing ACE found in DB: {}", acl);
                    break;
                }
            }
        }

        return acl;
    }

    private void validatePermissions(BucketACE bucketACE) {

        if (bucketACE == null) {
            return;
        }

        String permissionsValue = bucketACE.getPermissions();
        String[] permissionsArray = permissionsValue.split("\\|");

        for (String permission : permissionsArray) {
            if (isValidEnum(permission, BucketPermissions.class)) {
                bucketACE.proceedToNextStep();
            } else {
                _log.error("Invalid value for permission: {}", permissionsValue);
                bucketACE.cancelNextStep(BucketACLOperationErrorType.INVALID_PERMISSIONS);
                return;
            }
        }

    }

    /**
     * Check the provided String value is a valid type of enum or not
     * 
     * @param value String need to be checked
     * @param enumClass the enum class for which it need to be checked.
     * @return true/false
     */
    public <T extends Enum<T>> boolean isValidEnum(String value, Class<T> enumClass) {
        for (T e : enumClass.getEnumConstants()) {
            if (e.name().equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private void verifyUserGroupCustomgroup(BucketACE bucketACE) {

        String userOrGroupOrCustomgroup = null;

        if (bucketACE == null) {
            return;
        }

        if (bucketACE.getUser() == null && bucketACE.getGroup() == null && bucketACE.getCustomGroup() == null) {
            bucketACE.cancelNextStep(BucketACLOperationErrorType.USER_OR_GROUP_OR_CUSTOMGROUP_NOT_PROVIDED);
            _log.error("User or Group or Customgroup is missing.");
        } else if (bucketACE.getUser() != null && bucketACE.getGroup() != null && bucketACE.getCustomGroup() != null) {
            bucketACE.cancelNextStep(BucketACLOperationErrorType.USER_AND_GROUP_AND_CUSTOMGROUP_PROVIDED);
            _log.error("Either user or group or customgroup should be provided. Never all of them.");
        } else {
            String domain = bucketACE.getDomain();
            if (domain == null) {
                domain = "";
            }

            domain = domain.toLowerCase();
            if (bucketACE.getUser() != null) {
                userOrGroupOrCustomgroup = domain + bucketACE.getUser().toLowerCase();
            } else if (bucketACE.getGroup() != null) {
                userOrGroupOrCustomgroup = domain + bucketACE.getGroup().toLowerCase();
            } else {
                userOrGroupOrCustomgroup = domain + bucketACE.getCustomGroup().toLowerCase();
            }
        }

        if (userOrGroupOrCustomgroup != null) {

            if (!usersGroupsCustomGroups.contains(userOrGroupOrCustomgroup)) {
                usersGroupsCustomGroups.add(userOrGroupOrCustomgroup);
            } else {
                bucketACE.cancelNextStep(BucketACLOperationErrorType.MULTIPLE_ACES_WITH_SAME_USER_OR_GROUP_CUSTOMGROUP);
                _log.error("There are multiple ACEs with same user or group or customgroup.");
            }
        }
        // below code is to validate domain
        if (bucketACE.getDomain() != null && bucketACE.getUser() != null) {

            if (bucketACE.getUser().contains("\\")) {
                bucketACE.cancelNextStep(BucketACLOperationErrorType.MULTIPLE_DOMAINS_FOUND);
                _log.error("Multiple Domains found. Please provide either in user or in domain field.");

            }
        }
        if (bucketACE.getDomain() != null && bucketACE.getGroup() != null) {

            if (bucketACE.getGroup().contains("\\")) {
                bucketACE.cancelNextStep(BucketACLOperationErrorType.MULTIPLE_DOMAINS_FOUND);
                _log.error("Multiple Domains found. Please provide either in group or in domain field.");

            }
        }
        if (bucketACE.getDomain() != null && bucketACE.getCustomGroup() != null) {

            if (bucketACE.getCustomGroup().contains("\\")) {
                bucketACE.cancelNextStep(BucketACLOperationErrorType.MULTIPLE_DOMAINS_FOUND);
                _log.error("Multiple Domains found. Please provide either in customgroup or in domain field.");

            }
        }

    }

}
