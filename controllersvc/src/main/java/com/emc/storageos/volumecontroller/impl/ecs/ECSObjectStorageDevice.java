/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.ecs;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.Bucket;
import com.emc.storageos.db.client.model.ObjectBucketACL;
import com.emc.storageos.db.client.model.ObjectUserSecretKey;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.ecs.api.ECSApi;
import com.emc.storageos.ecs.api.ECSApiFactory;
import com.emc.storageos.ecs.api.ECSBucketACL;
import com.emc.storageos.ecs.api.ECSException;
import com.emc.storageos.ecs.api.UserSecretKeysAddCommandResult;
import com.emc.storageos.ecs.api.UserSecretKeysGetCommandResult;
import com.emc.storageos.model.object.BucketACE;
import com.emc.storageos.model.object.BucketACL;
import com.emc.storageos.model.object.BucketACLUpdateParams;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.ObjectDeviceInputOutput;
import com.emc.storageos.volumecontroller.ObjectStorageDevice;
import com.emc.storageos.volumecontroller.impl.BiosCommandResult;
import com.google.common.collect.Lists;
import com.google.gson.Gson;

/**
 * ECS specific object controller implementation.
 */
public class ECSObjectStorageDevice implements ObjectStorageDevice {
    private Logger _log = LoggerFactory.getLogger(ECSObjectStorageDevice.class);
    private ECSApiFactory ecsApiFactory;
    private DbClient _dbClient;

    /**
     * Set ECS API factory
     * 
     * @param factory
     */
    public void setECSApiFactory(ECSApiFactory factory) {
        _log.info("ECSObjectStorageDevice setECSApiFactory");
        ecsApiFactory = factory;
    }

    public void setDbClient(DbClient dbc) {
        _dbClient = dbc;
    }

    /**
     * Initialize HTTP client
     */
    public void init() {
        _log.info("From ECSObjectStorageDevice:init");

    }

    @Override
    public BiosCommandResult doCreateBucket(StorageSystem storageObj, Bucket bucket, ObjectDeviceInputOutput args, String taskId)
            throws ControllerException {
        ECSApi ecsApi = getAPI(storageObj);
        BiosCommandResult result = null;
        String bktNativeId = null, currentOwner=null;
        try {
            _log.info("Initiated for Bucket creation. Name : {} Namespace : {}", args.getName(), args.getNamespace());
            bktNativeId = ecsApi.createBucket(args.getName(), args.getNamespace(), args.getDevStoragePool());
            ecsApi.updateBucketRetention(args.getName(), args.getNamespace(), args.getRetentionPeriod());
            ecsApi.updateBucketQuota(args.getName(), args.getNamespace(), args.getNotSizeSQ(), args.getBlkSizeHQ());
            currentOwner = ecsApi.getBucketOwner(args.getName(), args.getNamespace());
            
            //ECS throws error if we try to set new owner which is same as current owner
            //This would lead to confusion as if there is an error
            if (!currentOwner.equals(args.getOwner())) {
            	ecsApi.updateBucketOwner(args.getName(), args.getNamespace(), args.getOwner());
            }
            _log.info("Successfully created Bucket. Name : {} Namespace : {}", args.getName(), args.getNamespace());
            bucket.setNativeId(bktNativeId);
            completeTask(bucket.getId(), taskId, "Successfully created Bucket.");
            result = BiosCommandResult.createSuccessfulResult();
        } catch (ECSException e) {
            _log.error("ECSObjectStorageDevice:doCreateBucket failed. Trying to cleanup at source as well.", e);
            bucket.setInactive(true);
            if (null != bktNativeId) {
                try {
                    ecsApi.deleteBucket(args.getName(), args.getNamespace());
                } catch (Exception del) {
                    _log.error("Could not clean up orphan bucket : {} Storage : {} from ECS, Please remove manully",
                            bucket.getLabel(), bucket.getStorageDevice());
                }
            }
            completeTask(bucket.getId(), taskId, e);
            result = BiosCommandResult.createErrorResult(e);
        }
        _dbClient.persistObject(bucket);
        return result;
    }

    @Override
    public BiosCommandResult doUpdateBucket(StorageSystem storageObj, Bucket bucket, Long softQuota, Long hardQuota,
            Integer retention,
            String taskId) {
        // Update Quota
        ECSApi objectAPI = getAPI(storageObj);
        try {
            objectAPI.updateBucketQuota(bucket.getName(), bucket.getNamespace(), softQuota, hardQuota);
            bucket.setHardQuota(hardQuota);
            bucket.setSoftQuota(softQuota);
        } catch (ECSException e) {
            _log.error("Quota Update for Bucket : {} failed.", bucket.getName(), e);
            completeTask(bucket.getId(), taskId, e);
            return BiosCommandResult.createErrorResult(e);
        }

        // Update Retention
        try {
            objectAPI.updateBucketRetention(bucket.getName(), bucket.getNamespace(), retention);
            bucket.setRetention(retention);
        } catch (ECSException e) {
            _log.error("Retention Update for Bucket : {} failed.", bucket.getName(), e);
            completeTask(bucket.getId(), taskId, e);
            return BiosCommandResult.createErrorResult(e);
        }

        _dbClient.persistObject(bucket);
        completeTask(bucket.getId(), taskId, "Successfully updated Bucket.");
        return BiosCommandResult.createSuccessfulResult();
    }

    @Override
    public BiosCommandResult doDeleteBucket(StorageSystem storageObj, Bucket bucket, final String taskId) {
        BiosCommandResult result;
        try {
            ECSApi objectAPI = getAPI(storageObj);
            objectAPI.deleteBucket(bucket.getName(), bucket.getNamespace());
            // Deleting the ACL for bucket if any
            List<ObjectBucketACL> aclToDelete = queryDbBucketACL(bucket);
            if (aclToDelete != null && !aclToDelete.isEmpty()) {
                for (ObjectBucketACL ace : aclToDelete) {
                    ObjectBucketACL dbBucketAcl = new ObjectBucketACL();

                    if (ace != null) {
                        dbBucketAcl.setId(ace.getId());
                        dbBucketAcl.setInactive(true);
                        _log.info("Marking acl inactive in DB: {}", dbBucketAcl);
                        _dbClient.updateObject(dbBucketAcl);
                    }
                }
            }
            bucket.setInactive(true);
            _dbClient.persistObject(bucket);
            result = BiosCommandResult.createSuccessfulResult();
            completeTask(bucket.getId(), taskId, "Bucket deleted successfully!");
        } catch (ECSException e) {
            _log.error("Delete Bucket : {} failed.", bucket.getName(), e);
            result = BiosCommandResult.createErrorResult(e);
            completeTask(bucket.getId(), taskId, e);
        }
        return result;
    }
    
    private List<ObjectBucketACL> queryDbBucketACL(Bucket bucket) {

        try {
            ContainmentConstraint containmentConstraint = null;

            _log.info("Querying DB for ACL of bucket {} ",
                    bucket.getName());
            containmentConstraint = ContainmentConstraint.Factory
                    .getBucketAclsConstraint(bucket.getId());

            List<ObjectBucketACL> dbBucketBucketAcl = CustomQueryUtility
                    .queryActiveResourcesByConstraint(_dbClient,
                            ObjectBucketACL.class, containmentConstraint);

            return dbBucketBucketAcl;

        } catch (Exception e) {
            _log.error("Error while querying DB for ACL of a bucket {}", e);
        }

        return null;

    }
    
    @Override
    public BiosCommandResult doUpdateBucketACL(StorageSystem storageObj, Bucket bucket, ObjectDeviceInputOutput objectArgs,
            BucketACLUpdateParams param,
            String taskId) throws ControllerException {
        List<BucketACE> aclToAdd = objectArgs.getBucketAclToAdd();
        List<BucketACE> aclToModify = objectArgs.getBucketAclToModify();
        List<BucketACE> aclToDelete = objectArgs.getBucketAclToDelete();
        // Get existing Acl for the Bucket
        List<BucketACE> aclsToProcess = objectArgs.getExistingBucketAcl();

        aclsToProcess.addAll(aclToAdd);
        // Process ACLs to modify
        for (BucketACE existingAce : aclsToProcess) {
            String domainOfExistingAce = existingAce.getDomain();
            if (domainOfExistingAce == null) {
                domainOfExistingAce = "";
            }
            for (BucketACE aceToModify : aclToModify) {
                String domainOfmodifiedAce = aceToModify.getDomain();
                if (domainOfmodifiedAce == null) {
                    domainOfmodifiedAce = "";
                }

                if (aceToModify.getUser() != null && existingAce.getUser() != null) {
                    if (domainOfExistingAce.concat(existingAce.getUser()).equalsIgnoreCase(
                            domainOfmodifiedAce.concat(aceToModify.getUser()))) {

                        existingAce.setPermissions(aceToModify.getPermissions());
                    }
                }

                if (aceToModify.getGroup() != null && existingAce.getGroup() != null) {
                    if (domainOfExistingAce.concat(existingAce.getGroup()).equalsIgnoreCase(
                            domainOfmodifiedAce.concat(aceToModify.getGroup()))) {
                        existingAce.setPermissions(aceToModify.getPermissions());
                    }
                }

                if (aceToModify.getCustomGroup() != null && existingAce.getCustomGroup() != null) {
                    if (domainOfExistingAce.concat(existingAce.getCustomGroup()).equalsIgnoreCase(
                            domainOfmodifiedAce.concat(aceToModify.getCustomGroup()))) {
                        existingAce.setPermissions(aceToModify.getPermissions());
                    }
                }
            }
        }

        // Process ACLs to delete
        for (BucketACE aceToDelete : aclToDelete) {

            String domainOfDeleteAce = aceToDelete.getDomain();
            if (domainOfDeleteAce == null) {
                domainOfDeleteAce = "";
            }

            for (Iterator<BucketACE> iterator = aclsToProcess.iterator(); iterator.hasNext();) {
                BucketACE existingAcl = iterator.next();

                String domainOfExistingAce = existingAcl.getDomain();
                if (domainOfExistingAce == null) {
                    domainOfExistingAce = "";
                }

                if (aceToDelete.getUser() != null
                        && existingAcl.getUser() != null) {
                    if (domainOfDeleteAce.concat(aceToDelete.getUser())
                            .equalsIgnoreCase(domainOfExistingAce.concat(existingAcl.getUser()))) {
                        iterator.remove();
                    }
                }

                if (aceToDelete.getGroup() != null
                        && existingAcl.getGroup() != null) {
                    if (domainOfDeleteAce.concat(aceToDelete.getGroup())
                            .equalsIgnoreCase(domainOfExistingAce.concat(existingAcl.getGroup()))) {
                        iterator.remove();
                    }
                }

                if (aceToDelete.getCustomGroup() != null
                        && existingAcl.getCustomGroup() != null) {
                    if (domainOfDeleteAce.concat(aceToDelete.getCustomGroup())
                            .equalsIgnoreCase(domainOfExistingAce.concat(existingAcl.getCustomGroup()))) {
                        iterator.remove();
                    }
                }
            }
        }

        ECSApi objectAPI = getAPI(storageObj);
        try {
            String payload = toJsonString(objectArgs, aclsToProcess);
            objectAPI.updateBucketACL(objectArgs.getName(), payload);
            updateBucketACLInDB(param, objectArgs, bucket);

        } catch (ECSException e) {
            _log.error("ACL Update for Bucket : {} failed.", objectArgs.getName(), e);
            completeTask(bucket.getId(), taskId, e);
            return BiosCommandResult.createErrorResult(e);
        }

        completeTask(bucket.getId(), taskId, "Successfully updated Bucket ACL.");
        return BiosCommandResult.createSuccessfulResult();
    }

    @Override
    public BiosCommandResult doDeleteBucketACL(StorageSystem storageObj, Bucket bucket, ObjectDeviceInputOutput objectArgs, String taskId)
            throws ControllerException {

        ECSApi objectAPI = getAPI(storageObj);
        BucketACLUpdateParams param = new BucketACLUpdateParams();
        BucketACL aclForDeletion = new BucketACL();
        aclForDeletion.setBucketACL(objectArgs.getBucketAclToDelete());
        param.setAclToDelete(aclForDeletion);
        try {
            String payload = "{\"bucket\":\"" + objectArgs.getName() + "\",\"namespace\":\"" + objectArgs.getNamespace()
                    + "\",\"acl\":{}}\"";
            objectAPI.updateBucketACL(objectArgs.getName(), payload);
            updateBucketACLInDB(param, objectArgs, bucket);

        } catch (ECSException e) {
            _log.error("Delete ACL for Bucket : {} failed.", objectArgs.getName(), e);
            completeTask(bucket.getId(), taskId, e);
            return BiosCommandResult.createErrorResult(e);
        }

        completeTask(bucket.getId(), taskId, "Successfully updated Bucket ACL.");
        return BiosCommandResult.createSuccessfulResult();
    }

    @SuppressWarnings("deprecation")
    private void updateBucketACLInDB(BucketACLUpdateParams param, ObjectDeviceInputOutput args, Bucket bucket) {

        try {
            // Create new Acl
            if (param.getAclToAdd() != null) {
                List<BucketACE> aclToAdd = param.getAclToAdd().getBucketACL();

                if (aclToAdd != null && !aclToAdd.isEmpty()) {
                    for (BucketACE ace : aclToAdd) {
                        ObjectBucketACL dbBucketAcl = new ObjectBucketACL();
                        dbBucketAcl.setId(URIUtil.createId(ObjectBucketACL.class));
                        copyToPersistBucketACL(ace, dbBucketAcl, args, bucket.getId());
                        _log.info("Storing new acl in DB: {}", dbBucketAcl);
                        _dbClient.createObject(dbBucketAcl);
                    }
                }
            }

            // Modify existing Acl
            if (param.getAclToModify() != null) {
                List<BucketACE> aclToModify = param.getAclToModify().getBucketACL();

                if (aclToModify != null && !aclToModify.isEmpty()) {
                    for (BucketACE ace : aclToModify) {
                        ObjectBucketACL dbBucketAcl = new ObjectBucketACL();
                        copyToPersistBucketACL(ace, dbBucketAcl, args, bucket.getId());
                        ObjectBucketACL dbBucketAclTemp = getExistingBucketAclFromDB(dbBucketAcl);
                        if (dbBucketAclTemp != null) {
                            dbBucketAcl.setId(dbBucketAclTemp.getId());
                            _log.info("Modifying acl in DB: {}", dbBucketAcl);
                            _dbClient.updateObject(dbBucketAcl);
                        }
                    }
                }
            }

            // Delete existing Acl
            if (param.getAclToDelete() != null) {
                List<BucketACE> aclToDelete = param.getAclToDelete().getBucketACL();

                if (aclToDelete != null && !aclToDelete.isEmpty()) {
                    for (BucketACE ace : aclToDelete) {
                        ObjectBucketACL dbBucketAcl = new ObjectBucketACL();
                        copyToPersistBucketACL(ace, dbBucketAcl, args, bucket.getId());
                        ObjectBucketACL dbBuckeAclTemp = getExistingBucketAclFromDB(dbBucketAcl);
                        if (dbBuckeAclTemp != null) {
                            dbBucketAcl.setId(dbBuckeAclTemp.getId());
                            dbBucketAcl.setInactive(true);
                            _log.info("Marking acl inactive in DB: {}", dbBucketAcl);
                            _dbClient.updateObject(dbBucketAcl);
                        }
                    }
                }
            }
        }

        catch (Exception e) {
            _log.error("Error While executing CRUD Operations {}", e);
        }
    }

    private void copyToPersistBucketACL(BucketACE ace, ObjectBucketACL dbBucketAcl, ObjectDeviceInputOutput args, URI bucketId) {

        dbBucketAcl.setNamespace(args.getNamespace());
        dbBucketAcl.setBucketId(bucketId);

        if (ace.getUser() != null) {
            dbBucketAcl.setUser(ace.getUser());
        }
        if (ace.getGroup() != null) {
            dbBucketAcl.setGroup(ace.getGroup());
        }
        if (ace.getCustomGroup() != null) {
            dbBucketAcl.setCustomGroup(ace.getCustomGroup());
        }
        if (ace.getDomain() != null) {
            dbBucketAcl.setDomain(ace.getDomain());
        }

        if (ace.getPermissions() != null) {
            dbBucketAcl.setPermissions(ace.getPermissions());
        }
        dbBucketAcl.setBucketName(args.getName());

    }

    private ObjectBucketACL getExistingBucketAclFromDB(ObjectBucketACL dbBucketAcl) {

        ObjectBucketACL acl = null;
        String index = null;
        URIQueryResultList result = new URIQueryResultList();

        index = dbBucketAcl.getBucketACLIndex();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getBucketACLConstraint(index), result);

        Iterator<URI> it = result.iterator();
        while (it.hasNext()) {
            acl = _dbClient.queryObject(ObjectBucketACL.class, it.next());
            if (acl != null && !acl.getInactive()) {
                _log.info("Existing ACE found in DB: {}", acl);
                return acl;
            }
        }

        return null;
    }

    private String toJsonString(ObjectDeviceInputOutput objectArgs, List<BucketACE> aclsToProcess) {
        ECSBucketACL ecsBucketAcl = new ECSBucketACL();

        List<ECSBucketACL.UserAcl> user_acl = Lists.newArrayList();
        List<ECSBucketACL.GroupAcl> group_acl = Lists.newArrayList();
        List<ECSBucketACL.CustomGroupAcl> customgroup_acl = Lists.newArrayList();
        String PERMISSION_DELEMITER = "\\|";

        for (BucketACE aceToAdd : aclsToProcess) {
            ECSBucketACL.UserAcl userAcl = ecsBucketAcl.new UserAcl();
            ECSBucketACL.GroupAcl groupAcl = ecsBucketAcl.new GroupAcl();
            ECSBucketACL.CustomGroupAcl customgroupAcl = ecsBucketAcl.new CustomGroupAcl();

            String type = "user";
            String userOrGroupOrCustomgroup = aceToAdd.getUser();
            if (userOrGroupOrCustomgroup == null) {
                userOrGroupOrCustomgroup = aceToAdd.getGroup() != null ? aceToAdd.getGroup() : aceToAdd.getCustomGroup();
                type = aceToAdd.getGroup() != null ? "group" : "customgroup";
            }
            if (aceToAdd.getDomain() != null && !aceToAdd.getDomain().isEmpty()) {
                userOrGroupOrCustomgroup = aceToAdd.getDomain() + "\\" + userOrGroupOrCustomgroup;
            }

            switch (type) {
                case "user":
                    userAcl.setUser(userOrGroupOrCustomgroup);
                    if (aceToAdd.getPermissions() != null) {
                        userAcl.setPermission(aceToAdd.getPermissions().split(PERMISSION_DELEMITER));
                    }
                    user_acl.add(userAcl);
                    break;
                case "group":
                    groupAcl.setGroup(userOrGroupOrCustomgroup);
                    if (aceToAdd.getPermissions() != null) {
                        groupAcl.setPermission(aceToAdd.getPermissions().split(PERMISSION_DELEMITER));
                    }
                    group_acl.add(groupAcl);
                    break;
                case "customgroup":
                    customgroupAcl.setCustomgroup(userOrGroupOrCustomgroup);
                    if (aceToAdd.getPermissions() != null) {
                        customgroupAcl.setPermission(aceToAdd.getPermissions().split(PERMISSION_DELEMITER));
                    }
                    customgroup_acl.add(customgroupAcl);
                    break;

            }

        }

        ecsBucketAcl.setBucket(objectArgs.getName());
        ecsBucketAcl.setNamespace(objectArgs.getNamespace());
        ECSBucketACL.Acl acl = ecsBucketAcl.new Acl();
        if (!user_acl.isEmpty()) {
            acl.setUserAcl(user_acl);
        }
        if (!group_acl.isEmpty()) {
            acl.setGroupAcl(group_acl);
        }
        if (!customgroup_acl.isEmpty()) {
            acl.setCustomgroupAcl(customgroup_acl);
        }
        ecsBucketAcl.setAcl(acl);

        return new Gson().toJson(ecsBucketAcl);
    }
    
    @Override
    public ObjectUserSecretKey doGetUserSecretKeys(StorageSystem storageObj, String userId) throws InternalException {
        ECSApi ecsApi = getAPI(storageObj);
        ObjectUserSecretKey secretKey = new ObjectUserSecretKey();

        try {
            UserSecretKeysGetCommandResult secretKeyRes = ecsApi.getUserSecretKeys(userId);
            secretKey.setSecret_key_1(secretKeyRes.getSecret_key_1());
            secretKey.setSecret_key_1_expiry_timestamp(secretKeyRes.getKey_expiry_timestamp_1());
            secretKey.setSecret_key_2(secretKeyRes.getSecret_key_2());
            secretKey.setSecret_key_2_expiry_timestamp(secretKeyRes.getKey_expiry_timestamp_2());
        } catch (Exception e) {
           _log.error("ECSObjectStorageDevice:doGetUserSecretKey failed.");
        }
        return secretKey;
    }

    @Override
    public ObjectUserSecretKey doAddUserSecretKey(StorageSystem storageObj, String userId, String secretKey) throws InternalException {
        ECSApi ecsApi = getAPI(storageObj);
        ObjectUserSecretKey secretKeyRes = new ObjectUserSecretKey();
        
        try {
            UserSecretKeysAddCommandResult cmdRes = ecsApi.addUserSecretKey(userId, secretKey);
            secretKeyRes.setSecret_key_1(cmdRes.getSecret_key()); 
            secretKeyRes.setSecret_key_1_expiry_timestamp(cmdRes.getKey_expiry_timestamp());
            return secretKeyRes;
        } catch (Exception e) {
            _log.error("ECSObjectStorageDevice:doAddUserSecretKey failed");
            throw e;
        }
    }


    private ECSApi getAPI(StorageSystem storageObj) throws ControllerException {
        ECSApi objectAPI = null;
        URI deviceURI = null;
        try {
            deviceURI = new URI("https", null, storageObj.getIpAddress(), storageObj.getPortNumber(), "/", null, null);
        } catch (URISyntaxException e) {
            throw ECSException.exceptions.invalidReturnParameters(storageObj.getId());
        }
        if (storageObj.getUsername() != null && !storageObj.getUsername().isEmpty()) {
            objectAPI = ecsApiFactory.getRESTClient(deviceURI, storageObj.getUsername(), storageObj.getPassword());
        } else {
            objectAPI = ecsApiFactory.getRESTClient(deviceURI);
        }
        return objectAPI;
    }

    private void completeTask(final URI bucketID, final String taskID, ECSException error) {
        BucketOperationTaskCompleter completer = new BucketOperationTaskCompleter(Bucket.class, bucketID, taskID);
        completer.error(_dbClient, error);
    }

    private void completeTask(final URI bucketID, final String taskID, final String message) {
        BucketOperationTaskCompleter completer = new BucketOperationTaskCompleter(Bucket.class, bucketID, taskID);
        completer.statusReady(_dbClient, message);
    }

}