/*
 * Copyright (c) 2012-2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.object;

import static com.emc.sa.service.ServiceParams.ACL_TYPE;
import static com.emc.sa.service.ServiceParams.ACL_NAME;
import static com.emc.sa.service.ServiceParams.ACL_DOMAIN;
import static com.emc.sa.service.ServiceParams.ACL_PERMISSION;
import static com.emc.sa.service.ServiceParams.HARD_QUOTA;
import static com.emc.sa.service.ServiceParams.NAME;
import static com.emc.sa.service.ServiceParams.OWNER;
import static com.emc.sa.service.ServiceParams.PROJECT;
import static com.emc.sa.service.ServiceParams.RETENTION;
import static com.emc.sa.service.ServiceParams.SOFT_QUOTA;
import static com.emc.sa.service.ServiceParams.VIRTUAL_ARRAY;
import static com.emc.sa.service.ServiceParams.VIRTUAL_POOL;

import java.net.URI;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("CreateBucket")
public class CreateBucketService extends ViPRService {

    @Param(NAME)
    protected String bucketName;

    @Param(VIRTUAL_POOL)
    protected URI virtualPool;

    @Param(VIRTUAL_ARRAY)
    protected URI virtualArray;

    @Param(PROJECT)
    protected URI project;

    @Param(value = SOFT_QUOTA)
    protected Double softQuota;

    @Param(value = HARD_QUOTA)
    protected Double hardQuota;

    @Param(value = RETENTION)
    protected String retention;

    @Param(value = OWNER, required = false)
    protected String owner;
    
    protected ObjectStorageUtils.ObjectStorageACL objectStorageACL = new ObjectStorageUtils.ObjectStorageACL();
    
    @Param(ACL_TYPE)
    public String aclType;

    @Param(ACL_NAME)
    public String aclName;
    
    @Param(ACL_DOMAIN)
    public String aclDomain;

    @Param(ACL_PERMISSION)
    public List<String> aclPermissions;
    
    protected URI bucketId;
    
    @Override
    public void precheck() throws Exception {
        objectStorageACL.aclType = aclType;
        objectStorageACL.aclName = aclName;
        objectStorageACL.aclDomain = aclDomain;
        objectStorageACL.aclPermission = aclPermissions;
        
        List<String> invalidNames = ObjectStorageUtils.getInvalidObjectACL(objectStorageACL);
        if (!invalidNames.isEmpty()) {
            ExecutionUtils.fail("failTask.CreateBucketACL.invalidName", invalidNames, invalidNames);
        }
    }

    @Override
    public void execute() throws Exception {
        this.bucketId = ObjectStorageUtils.createBucket(bucketName, virtualArray, virtualPool, project, softQuota, hardQuota, retention, owner);
        
        if (!StringUtils.isBlank(objectStorageACL.aclName)) {
            ObjectStorageUtils.setObjectShareACL(bucketId, objectStorageACL);
        }
    }
}
