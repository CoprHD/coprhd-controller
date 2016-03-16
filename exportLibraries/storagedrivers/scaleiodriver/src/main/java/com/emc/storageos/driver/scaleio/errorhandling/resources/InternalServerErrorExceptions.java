/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.scaleio.errorhandling.resources;

import com.emc.storageos.driver.scaleio.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.driver.scaleio.errorhandling.annotations.MessageBundle;

import java.net.URI;

/**
 * This interface holds all the methods used to create an error condition that
 * will be associated with an HTTP status of Internal Server Error (500)
 * <p/>
 * Remember to add the English message associated to the method in InternalServerErrorExceptions.properties and use the annotation
 * {@link DeclareServiceCode} to set the service code associated to this error condition. You may need to create a new service code if there
 * is no an existing one suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */
@MessageBundle
public interface InternalServerErrorExceptions {

    @DeclareServiceCode(ServiceCode.SYS_RELEASE_LOCK_ERROR)
    InternalServerErrorException releaseLockFailure(final String lock);

    @DeclareServiceCode(ServiceCode.SYS_RELEASE_LOCK_ERROR)
    InternalServerErrorException releaseLockFailure(final String lock, final Throwable cause);

    @DeclareServiceCode(ServiceCode.SYS_IS_NULL_OR_EMPTY)
    InternalServerErrorException targetIsNullOrEmpty(final String target);

    @DeclareServiceCode(ServiceCode.SYS_IO_WRITE_ERROR)
    InternalServerErrorException ioWriteError(final String file);

    @DeclareServiceCode(ServiceCode.SYS_CREATE_OBJECT_ERROR)
    InternalServerErrorException createObjectError(final String object, final Throwable cause);

    @DeclareServiceCode(ServiceCode.SYS_GET_OBJECT_ERROR)
    InternalServerErrorException getObjectFromError(final String object, final String from, final Throwable cause);

    @DeclareServiceCode(ServiceCode.SYS_INTERNAL_ERROR)
    InternalServerErrorException licenseInfoNotFoundForType(final String type);

    @DeclareServiceCode(ServiceCode.SYS_INTERNAL_ERROR)
    InternalServerErrorException processLicenseError(final String cause);

    @DeclareServiceCode(ServiceCode.SYS_GET_OBJECT_ERROR)
    InternalServerErrorException getObjectError(final String object, final Throwable cause);

    @DeclareServiceCode(ServiceCode.SYS_SET_OBJECT_ERROR)
    InternalServerErrorException setObjectToError(final String object, final String to, final Throwable cause);

    @DeclareServiceCode(ServiceCode.SYS_UPDATE_OBJECT_ERROR)
    InternalServerErrorException updateObjectError(final String object, final Throwable cause);

    @DeclareServiceCode(ServiceCode.SYS_UPLOAD_INSTALL_ERROR)
    InternalServerErrorException uploadInstallError(final Throwable cause);

    @DeclareServiceCode(ServiceCode.SYS_WAKEUP_ERROR)
    InternalServerErrorException poweroffWakeupError(final Throwable cause);

    @DeclareServiceCode(ServiceCode.SYS_POWEROFF_ERROR)
    InternalServerErrorException poweroffError(final String nodeId, final Throwable cause);

    @DeclareServiceCode(ServiceCode.SYS_INITIALIZE_SSL_CONTENT_ERROR)
    InternalServerErrorException initializeSSLContentError();

    @DeclareServiceCode(ServiceCode.SYS_NO_NODES_AVAILABLE)
    InternalServerErrorException noNodeAvailableError(final String action);

    @DeclareServiceCode(ServiceCode.SYS_INVALID_OBJECT)
    InternalServerErrorException invalidObject(final String object);

    @DeclareServiceCode(ServiceCode.SYS_DOWNLOAD_IMAGE_ERROR)
    InternalServerErrorException downloadUpgradeImageError(final Throwable cause);

    @DeclareServiceCode(ServiceCode.SYS_INTERNAL_SYS_CLIENT_ERROR)
    InternalServerErrorException sysClientError(final String action);

    @DeclareServiceCode(ServiceCode.SYS_INTERNAL_LOCAL_REPO_ERROR)
    InternalServerErrorException localRepoError(final String action);

    @DeclareServiceCode(ServiceCode.SYS_INTERNAL_SERVICE_RESTART)
    InternalServerErrorException serviceRestartError(final String service, final String vm);

    @DeclareServiceCode(ServiceCode.SYS_INTERNAL_SYS_CLIENT_ERROR)
    InternalServerErrorException sendEventError(final String message);

    @DeclareServiceCode(ServiceCode.SYS_INTERNAL_SYS_CLIENT_ERROR)
    InternalServerErrorException attachmentSizeError(final long currentSize,
                                                     final long maxSize);

    @DeclareServiceCode(ServiceCode.SYS_INTERNAL_ERROR)
    InternalServerErrorException logCollectionTimeout(final long maxTimeMins);

    @DeclareServiceCode(ServiceCode.API_INTERNAL_SERVER_ERROR)
    InternalServerErrorException genericApisvcError(final String message, final Throwable cause);

    @DeclareServiceCode(ServiceCode.API_JAXB_CONTEXT_ERROR)
    InternalServerErrorException jaxbContextError(final String message, final Throwable cause);

    @DeclareServiceCode(ServiceCode.API_AUDIT_LOG_ERROR)
    InternalServerErrorException auditLogNoDb();

    @DeclareServiceCode(ServiceCode.API_RP_VOLUME_DELETE_ERROR)
    InternalServerErrorException unableToDeleteRpVolume(final URI uri);

    @DeclareServiceCode(ServiceCode.API_AUDIT_LOG_ERROR)
    InternalServerErrorException noAuditLogRetriever();

    @DeclareServiceCode(ServiceCode.API_METERING_STAT_ERROR)
    InternalServerErrorException noMeteringStats();

    @DeclareServiceCode(ServiceCode.API_METERING_STAT_ERROR)
    InternalServerErrorException meteringStatsError(final String message, final Throwable cause);

    @DeclareServiceCode(ServiceCode.API_EVENT_RETRIEVER_ERROR)
    InternalServerErrorException noEventRetriever();

    @DeclareServiceCode(ServiceCode.API_EVENT_RETRIEVER_ERROR)
    InternalServerErrorException eventRetrieverError(final String message, final Throwable cause);

    @DeclareServiceCode(ServiceCode.API_EVENT_RETRIEVER_ERROR)
    InternalServerErrorException noDBClient();

    @DeclareServiceCode(ServiceCode.API_INGESTION_ERROR)
    InternalServerErrorException objectAlreadyExported(final String parameter, final URI uri);

    @DeclareServiceCode(ServiceCode.API_INGESTION_ERROR)
    InternalServerErrorException objectAlreadyAddedToConsistencyGroup(final String parameter, final URI uri);

    @DeclareServiceCode(ServiceCode.API_INGESTION_ERROR)
    InternalServerErrorException partialUnManagedObjectDiscovery(final String parameter, final URI uri);

    @DeclareServiceCode(ServiceCode.API_INGESTION_ERROR)
    InternalServerErrorException objectHasReplicas(final String parameter, final URI uri);

    @DeclareServiceCode(ServiceCode.API_INGESTION_ERROR)
    InternalServerErrorException storagePoolError(final String pool, final String parameter, final URI uri);

    @DeclareServiceCode(ServiceCode.API_INGESTION_ERROR)
    InternalServerErrorException noStoragePool(final String pool, final String parameter, final URI uri);

    @DeclareServiceCode(ServiceCode.API_INGESTION_ERROR)
    InternalServerErrorException noMatchingVplexVirtualPool(final String name, final URI uri);

    @DeclareServiceCode(ServiceCode.API_INGESTION_ERROR)
    InternalServerErrorException storagePoolNotMatchingVirtualPool(final String parameter, final URI uri);

    @DeclareServiceCode(ServiceCode.API_INGESTION_ERROR)
    InternalServerErrorException virtualPoolNotMatchingStoragePool(final URI poolUri, final String parameter, final URI uri,
                                                                   final String uris);

    @DeclareServiceCode(ServiceCode.API_INGESTION_ERROR)
    InternalServerErrorException storagePoolNotMatchingVirtualPoolNicer(final String storagePool,
                                                                        final String type, final String volume);

    @DeclareServiceCode(ServiceCode.API_INGESTION_ERROR)
    InternalServerErrorException virtualPoolNotMatchingStoragePoolNicer(final String virtualPool,
                                                                        final String storagePool, final String type, final String volume, final String vpoolList);

    @DeclareServiceCode(ServiceCode.API_INGESTION_ERROR)
    InternalServerErrorException objectAlreadyManaged(final String parameter, final String guid);

    @DeclareServiceCode(ServiceCode.API_INGESTION_ERROR)
    InternalServerErrorException virtualPoolNotMatchingVArray(final URI uri);

    @DeclareServiceCode(ServiceCode.API_INGESTION_ERROR)
    InternalServerErrorException noVolumesIngested();

    @DeclareServiceCode(ServiceCode.UNFORSEEN_ERROR)
    InternalServerErrorException generalExceptionCreatingDataStore(String name, Exception e);

    @DeclareServiceCode(ServiceCode.API_RP_VOLUME_CREATE_ERROR)
    InternalServerErrorException noMatchingAllocationCapacityFound();

    @DeclareServiceCode(ServiceCode.API_NOT_INITIALIZED)
    InternalServerErrorException apiNotInitialized(String apiNames, Object value);

    @DeclareServiceCode(ServiceCode.DOWNLOAD_ERROR)
    InternalServerErrorException checksumError();

    @DeclareServiceCode(ServiceCode.UNFORSEEN_ERROR)
    InternalServerErrorException noAssociatedVolumesForVPLEXVolume(final String id);

    @DeclareServiceCode(ServiceCode.UNFORSEEN_ERROR)
    InternalServerErrorException noSourceVolumeForVPLEXVolumeSnapshot(final String id);

    @DeclareServiceCode(ServiceCode.UNFORSEEN_ERROR)
    InternalServerErrorException noVplexClusterInfoForVarray(final String vararyId, final String vplexStorageSytsemId);

    @DeclareServiceCode(ServiceCode.UNFORSEEN_ERROR)
    InternalServerErrorException capacityComputationFailed();

    @DeclareServiceCode(ServiceCode.OBJ_SYSVARRAY_NOT_DEFINED)
    InternalServerErrorException systemVArrayNotDefined();

    @DeclareServiceCode(ServiceCode.UNFORSEEN_ERROR)
    InternalServerErrorException unexpectedErrorDuringVarrayChange(Exception e);

    @DeclareServiceCode(ServiceCode.SECURITY_AUTH_TIMEOUT)
    InternalServerErrorException authTimeout();

    @DeclareServiceCode(ServiceCode.SYS_RECOVERY_TRIGGER_FAILED)
    InternalServerErrorException triggerRecoveryFailed(String errMsg);

    @DeclareServiceCode(ServiceCode.SYS_RECOVERY_REPAIR_FAILED)
    InternalServerErrorException nodeRepairFailed();

    @DeclareServiceCode(ServiceCode.SYS_RECOVERY_REBUILD_FAILED)
    InternalServerErrorException nodeRebuildFailed();

    @DeclareServiceCode(ServiceCode.SYS_RECOVERY_ADD_LISTENER_FAILED)
    InternalServerErrorException addListenerFailed();

    @DeclareServiceCode(ServiceCode.SYS_RECOVERY_GET_LOCK_FAILED)
    InternalServerErrorException getLockFailed();

    @DeclareServiceCode(ServiceCode.SYS_RECOVERY_NEW_NODE_FAILURE)
    InternalServerErrorException newNodeFailureInNodeRecovery(final String nodes);

    @DeclareServiceCode(ServiceCode.SYS_IPRECONFIG_TRIGGER_FAILED)
    InternalServerErrorException triggerIpReconfigFailed(String errmsg);
    
    @DeclareServiceCode(ServiceCode.SYS_DR_ADD_STANDBY_PRECHECK_FAILED)
    InternalServerErrorException addStandbyPrecheckFailed(String errMsg);
    
    @DeclareServiceCode(ServiceCode.SYS_DR_NAT_CHECK_FAILED)
    InternalServerErrorException invalidNatCheckCall(String clientIP, String directClientIp);

    @DeclareServiceCode(ServiceCode.SYS_DR_CREATE_VIPR_CLIENT_FAILED)
    InternalServerErrorException failToCreateViPRClient();

    @DeclareServiceCode(ServiceCode.SYS_DR_ADD_STANDBY_FAILED)
    InternalServerErrorException addStandbyFailed(String errMsg);
    
    @DeclareServiceCode(ServiceCode.SYS_DR_ADD_STANDBY_TIMEOUT)
    InternalServerErrorException addStandbyFailedTimeout(final long timeoutValue);
    
    @DeclareServiceCode(ServiceCode.SYS_DR_CONFIG_STANDBY_FAILED)
    InternalServerErrorException configStandbyFailed(String errMsg);
    
    @DeclareServiceCode(ServiceCode.SYS_DR_REMOVE_STANDBY_PRECHECK_FAILED)
    InternalServerErrorException removeStandbyPrecheckFailed(String siteNames, String errMsg);
    
    @DeclareServiceCode(ServiceCode.SYS_DR_REMOVE_STANDBY_FAILED)
    InternalServerErrorException removeStandbyFailed(final String siteNames, String errMsg);
    
    @DeclareServiceCode(ServiceCode.SYS_DR_REMOVE_STANDBY_RECONFIG_FAILED)
    InternalServerErrorException removeStandbyReconfigFailed(String errMsg);

    @DeclareServiceCode(ServiceCode.SYS_DR_PAUSE_STANDBY_FAILED)
    InternalServerErrorException pauseStandbyFailed(final String siteName, String errMsg);

    @DeclareServiceCode(ServiceCode.SYS_DR_PAUSE_STANDBY_TIMEOUT)
    InternalServerErrorException pauseStandbyFailedTimeout(final long timeoutValue);

    @DeclareServiceCode(ServiceCode.SYS_DR_PAUSE_STANDBY_PRECHECK_FAILED)
    InternalServerErrorException pauseStandbyPrecheckFailed(String siteId, String errMsg);

    @DeclareServiceCode(ServiceCode.SYS_DR_PAUSE_STANDBY_RECONFIG_FAILED)
    InternalServerErrorException pauseStandbyReconfigFailed(String errMsg);

    @DeclareServiceCode(ServiceCode.SYS_DR_RESUME_STANDBY_PRECHECK_FAILED)
    InternalServerErrorException resumeStandbyPrecheckFailed(String siteId, String errMsg);

    @DeclareServiceCode(ServiceCode.SYS_DR_RESUME_STANDBY_RECONFIG_FAILED)
    InternalServerErrorException resumeStandbyReconfigFailed(String errMsg);

    @DeclareServiceCode(ServiceCode.SYS_DR_RESUME_STANDBY_FAILED)
    InternalServerErrorException resumeStandbyFailed(final String siteName, String errMsg);

    @DeclareServiceCode(ServiceCode.SYS_DR_RESUME_STANDBY_TIMEOUT)
    InternalServerErrorException resumeStandbyFailedTimeout(final long timeoutValue);

    @DeclareServiceCode(ServiceCode.SYS_DR_DATA_SYNC_TIMEOUT)
    InternalServerErrorException dataSyncFailedTimeout(final long timeoutValue);
    
    @DeclareServiceCode(ServiceCode.SYS_DR_SWITCHOVER_PRECHECK_FAILED)
    InternalServerErrorException switchoverPrecheckFailed(final String siteName, String errMsg);

    @DeclareServiceCode(ServiceCode.SYS_DR_SWITCHOVER_FAILED)
    InternalServerErrorException switchoverFailed(String primaryName, String standbyName, String errMsg);

    @DeclareServiceCode(ServiceCode.SYS_DR_SWITCHOVER_ACTIVE_FAILED_TIMEOUT)
    InternalServerErrorException switchoverActiveFailedTimeout(String siteName, int timeoutValue);

    @DeclareServiceCode(ServiceCode.SYS_DR_SWITCHOVER_STANDBY_FAILED_TIMEOUT)
    InternalServerErrorException switchoverStandbyFailedTimeout(String siteName, int timeoutValue);

    @DeclareServiceCode(ServiceCode.SYS_DR_ACQUIRE_OPERATION_LOCK_FAILED)
    InternalServerErrorException failToAcquireDROperationLock();

    @DeclareServiceCode(ServiceCode.SYS_DR_CONCURRENT_OPERATION_NOT_ALLOWED)
    InternalServerErrorException concurrentDROperationNotAllowed(String sitedName, String state);

    @DeclareServiceCode(ServiceCode.UNFORSEEN_ERROR)
    InternalServerErrorException unexpectedErrorVolumePlacement(Exception ex);

    @DeclareServiceCode(ServiceCode.UNFORSEEN_ERROR)
    InternalServerErrorException unexpectedErrorExportGroupPlacement(Exception ex);
    
    @DeclareServiceCode(ServiceCode.SYS_DR_FAILOVER_FAILED)
    InternalServerErrorException failoverFailed(String siteName, String errMsg);
    
    @DeclareServiceCode(ServiceCode.SYS_DR_FAILOVER_PRECHECK_FAILED)
    InternalServerErrorException failoverPrecheckFailed(final String siteName, String errMsg);
    
    @DeclareServiceCode(ServiceCode.SYS_DR_FAILOVER_RECONFIG_FAIL)
    InternalServerErrorException failoverReconfigFailed(String errMsg);
    
    @DeclareServiceCode(ServiceCode.SYS_DR_UPDATE_SITE_FAILED)
    InternalServerErrorException updateSiteFailed(String siteName, String errMsg);
}
