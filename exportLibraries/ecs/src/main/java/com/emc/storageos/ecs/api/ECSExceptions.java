/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.ecs.api;

import java.net.URI;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

@MessageBundle
public interface ECSExceptions {

    @DeclareServiceCode(ServiceCode.ECS_RETURN_PARAM_ERROR)
    public ECSException invalidBaseURI(final String ip, final String port);

    @DeclareServiceCode(ServiceCode.ECS_CONNECTION_ERROR)
    public ECSException unableToConnect(final URI baseUrl, final int status);

    @DeclareServiceCode(ServiceCode.ECS_RETURN_PARAM_ERROR)
    public ECSException invalidReturnParameters(final URI baseUrl);

    @DeclareServiceCode(ServiceCode.ECS_LOGINVALIDATE_ERROR)
    public ECSException isSystemAdminFailed(final URI baseUrl, final int status);

    @DeclareServiceCode(ServiceCode.ECS_STORAGEPOOL_ERROR)
    public ECSException storageAccessFailed(final URI baseUrl, final int status, final String info);

    @DeclareServiceCode(ServiceCode.ECS_STATS_ERROR)
    public ECSException getStoragePoolsFailed(final String response, final Throwable cause);

    @DeclareServiceCode(ServiceCode.ECS_STATS_ERROR)
    public ECSException createBucketFailed(final String response, final Throwable cause);

    @DeclareServiceCode(ServiceCode.ECS_NON_SYSTEM_ADMIN_ERROR)
    public ECSException discoverFailed(final String response);

    @DeclareServiceCode(ServiceCode.ECS_CONNECTION_ERROR)
    public ECSException errorCreatingServerURL(final String host, final int port, final Throwable e);

    @DeclareServiceCode(ServiceCode.ECS_BUCKET_UPDATE_ERROR)
    public ECSException bucketUpdateFailed(final String bucketName, final String attributeType, final String message);

    @DeclareServiceCode(ServiceCode.ECS_BUCKET_DELETE_ERROR)
    public ECSException bucketDeleteFailed(final String bucketName, final String info);

    @DeclareServiceCode(ServiceCode.ECS_BUCKET_GET_OWNER_ERROR)
    public ECSException getBucketOwnerFailed(final String bucketName, final String info);
    
    @DeclareServiceCode(ServiceCode.ECS_BUCKET_ACL_ERROR)
    public ECSException bucketACLUpdateFailed(final String bucketName, final String message);
    
    @DeclareServiceCode(ServiceCode.ECS_GET_NAMESPACES_ERROR)
    public ECSException getNamespacesFailedAry(final String info);

    @DeclareServiceCode(ServiceCode.ECS_GET_NAMESPACES_ERROR)
    public ECSException getNamespacesFailedExc(final Throwable e);
    
    @DeclareServiceCode(ServiceCode.ECS_GET_NAMESPACE_DETAILS_ERROR)
    public ECSException getNamespaceDetailsFailedAry(final String info);

    @DeclareServiceCode(ServiceCode.ECS_GET_NAMESPACE_DETAILS_ERROR)

    public ECSException getNamespaceDetailsFailed(final String namespace, final Throwable e);
    
    @DeclareServiceCode(ServiceCode.ECS_BUCKET_ACL_ERROR)
    public ECSException getBucketACLFailed(final String bucketName, final String message);

    public ECSException getNamespaceDetailsFailedExc(final String namespace, final Throwable e);
    
    @DeclareServiceCode(ServiceCode.ECS_GET_USER_SECRET_KEYS_ERROR)
    public ECSException getUserSecretKeysFailedAry(final String info);

    @DeclareServiceCode(ServiceCode.ECS_GET_USER_SECRET_KEYS_ERROR)
    public ECSException getUserSecretKeysFailedExc(final String user, final Throwable e);

    @DeclareServiceCode(ServiceCode.ECS_ADD_USER_SECRET_KEYS_ERROR)
    public ECSException addUserSecretKeysFailedAry(final String info);

    @DeclareServiceCode(ServiceCode.ECS_ADD_USER_SECRET_KEYS_ERROR)
    public ECSException addUserSecretKeysFailedExc(final String user, final Throwable e);


}
