/*
 * Copyright 2013 EMC Corporation
 * Copyright 2016 Intel Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.emc.storageos.svcs.errorhandling.resources;

import java.net.URI;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;

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
    public InternalServerErrorException releaseLockFailure(final String lock);

    @DeclareServiceCode(ServiceCode.SYS_RELEASE_LOCK_ERROR)
    public InternalServerErrorException releaseLockFailure(final String lock, final Throwable cause);

    @DeclareServiceCode(ServiceCode.SYS_IS_NULL_OR_EMPTY)
    public InternalServerErrorException targetIsNullOrEmpty(final String target);

    @DeclareServiceCode(ServiceCode.SYS_IO_WRITE_ERROR)
    public InternalServerErrorException ioWriteError(final String file);

    @DeclareServiceCode(ServiceCode.SYS_CREATE_OBJECT_ERROR)
    public InternalServerErrorException createObjectError(final String object, final Throwable cause);

    @DeclareServiceCode(ServiceCode.SYS_GET_OBJECT_ERROR)
    public InternalServerErrorException getObjectFromError(final String object, final String from, final Throwable cause);

    @DeclareServiceCode(ServiceCode.SYS_INTERNAL_ERROR)
    public InternalServerErrorException licenseInfoNotFoundForType(final String type);

    @DeclareServiceCode(ServiceCode.SYS_INTERNAL_ERROR)
    public InternalServerErrorException processLicenseError(final String cause);

    @DeclareServiceCode(ServiceCode.SYS_GET_OBJECT_ERROR)
    public InternalServerErrorException getObjectError(final String object, final Throwable cause);

    @DeclareServiceCode(ServiceCode.SYS_SET_OBJECT_ERROR)
    public InternalServerErrorException setObjectToError(final String object, final String to, final Throwable cause);

    @DeclareServiceCode(ServiceCode.SYS_UPDATE_OBJECT_ERROR)
    public InternalServerErrorException updateObjectError(final String object, final Throwable cause);

    @DeclareServiceCode(ServiceCode.SYS_UPLOAD_INSTALL_ERROR)
    public InternalServerErrorException uploadInstallError(final Throwable cause);

    @DeclareServiceCode(ServiceCode.SYS_WAKEUP_ERROR)
    public InternalServerErrorException poweroffWakeupError(final Throwable cause);

    @DeclareServiceCode(ServiceCode.SYS_POWEROFF_ERROR)
    public InternalServerErrorException poweroffError(final String nodeId, final Throwable cause);

    @DeclareServiceCode(ServiceCode.SYS_INITIALIZE_SSL_CONTENT_ERROR)
    public InternalServerErrorException initializeSSLContentError();

    @DeclareServiceCode(ServiceCode.SYS_NO_NODES_AVAILABLE)
    public InternalServerErrorException noNodeAvailableError(final String action);

    @DeclareServiceCode(ServiceCode.SYS_INVALID_OBJECT)
    public InternalServerErrorException invalidObject(final String object);

    @DeclareServiceCode(ServiceCode.SYS_DOWNLOAD_IMAGE_ERROR)
    public InternalServerErrorException downloadUpgradeImageError(final Throwable cause);

    @DeclareServiceCode(ServiceCode.SYS_INTERNAL_SYS_CLIENT_ERROR)
    public InternalServerErrorException sysClientError(final String action);

    @DeclareServiceCode(ServiceCode.SYS_INTERNAL_LOCAL_REPO_ERROR)
    public InternalServerErrorException localRepoError(final String action);

    @DeclareServiceCode(ServiceCode.SYS_INTERNAL_SERVICE_RESTART)
    public InternalServerErrorException serviceRestartError(final String service, final String vm);

    @DeclareServiceCode(ServiceCode.SYS_INTERNAL_SYS_CLIENT_ERROR)
    public InternalServerErrorException sendEventError(final String message);

    @DeclareServiceCode(ServiceCode.SYS_INTERNAL_SYS_CLIENT_ERROR)
    public InternalServerErrorException attachmentSizeError(final long currentSize,
            final long maxSize);

    @DeclareServiceCode(ServiceCode.SYS_INTERNAL_ERROR)
    public InternalServerErrorException logCollectionTimeout(final long maxTimeMins);

    @DeclareServiceCode(ServiceCode.API_INTERNAL_SERVER_ERROR)
    public InternalServerErrorException genericApisvcError(final String message, final Throwable cause);

    @DeclareServiceCode(ServiceCode.API_JAXB_CONTEXT_ERROR)
    public InternalServerErrorException jaxbContextError(final String message, final Throwable cause);

    @DeclareServiceCode(ServiceCode.API_AUDIT_LOG_ERROR)
    public InternalServerErrorException auditLogNoDb();

    @DeclareServiceCode(ServiceCode.API_RP_VOLUME_DELETE_ERROR)
    public InternalServerErrorException unableToDeleteRpVolume(final URI uri);

    @DeclareServiceCode(ServiceCode.API_AUDIT_LOG_ERROR)
    public InternalServerErrorException noAuditLogRetriever();

    @DeclareServiceCode(ServiceCode.API_METERING_STAT_ERROR)
    public InternalServerErrorException noMeteringStats();

    @DeclareServiceCode(ServiceCode.API_METERING_STAT_ERROR)
    public InternalServerErrorException meteringStatsError(final String message, final Throwable cause);

    @DeclareServiceCode(ServiceCode.API_EVENT_RETRIEVER_ERROR)
    public InternalServerErrorException noEventRetriever();

    @DeclareServiceCode(ServiceCode.API_EVENT_RETRIEVER_ERROR)
    public InternalServerErrorException eventRetrieverError(final String message, final Throwable cause);

    @DeclareServiceCode(ServiceCode.API_EVENT_RETRIEVER_ERROR)
    public InternalServerErrorException noDBClient();

    @DeclareServiceCode(ServiceCode.API_INGESTION_ERROR)
    public InternalServerErrorException objectAlreadyExported(final String parameter, final URI uri);

    @DeclareServiceCode(ServiceCode.API_INGESTION_ERROR)
    public InternalServerErrorException objectAlreadyAddedToConsistencyGroup(final String parameter, final URI uri);

    @DeclareServiceCode(ServiceCode.API_INGESTION_ERROR)
    public InternalServerErrorException partialUnManagedObjectDiscovery(final String parameter, final URI uri);

    @DeclareServiceCode(ServiceCode.API_INGESTION_ERROR)
    public InternalServerErrorException objectHasReplicas(final String parameter, final URI uri);

    @DeclareServiceCode(ServiceCode.API_INGESTION_ERROR)
    public InternalServerErrorException storagePoolError(final String pool, final String parameter, final URI uri);

    @DeclareServiceCode(ServiceCode.API_INGESTION_ERROR)
    public InternalServerErrorException noStoragePool(final String pool, final String parameter, final URI uri);

    @DeclareServiceCode(ServiceCode.API_INGESTION_ERROR)
    public InternalServerErrorException noMatchingVplexVirtualPool(final String name, final URI uri);

    @DeclareServiceCode(ServiceCode.API_INGESTION_ERROR)
    public InternalServerErrorException storagePoolNotMatchingVirtualPool(final String parameter, final URI uri);

    @DeclareServiceCode(ServiceCode.API_INGESTION_ERROR)
    public InternalServerErrorException virtualPoolNotMatchingStoragePool(final URI poolUri, final String parameter, final URI uri,
            final String uris);

    @DeclareServiceCode(ServiceCode.API_INGESTION_ERROR)
    public InternalServerErrorException storagePoolNotMatchingVirtualPoolNicer(final String storagePool,
            final String type, final String volume);

    @DeclareServiceCode(ServiceCode.API_INGESTION_ERROR)
    public InternalServerErrorException virtualPoolNotMatchingStoragePoolNicer(final String virtualPool,
            final String type, final String volume, final String vpoolList);

    @DeclareServiceCode(ServiceCode.API_INGESTION_ERROR)
    public InternalServerErrorException objectAlreadyManaged(final String parameter, final String guid);

    @DeclareServiceCode(ServiceCode.API_INGESTION_ERROR)
    public InternalServerErrorException virtualPoolNotMatchingVArray(final URI uri);

    @DeclareServiceCode(ServiceCode.API_INTERNAL_SERVER_ERROR)
    public InternalServerErrorException noAssociatedQosForVirtualPool(final URI uri);

    @DeclareServiceCode(ServiceCode.API_INGESTION_ERROR)
    public InternalServerErrorException noVolumesIngested();

    @DeclareServiceCode(ServiceCode.UNFORSEEN_ERROR)
    public InternalServerErrorException generalExceptionCreatingDataStore(String name, Exception e);

    @DeclareServiceCode(ServiceCode.API_RP_VOLUME_CREATE_ERROR)
    public InternalServerErrorException noMatchingAllocationCapacityFound();

    @DeclareServiceCode(ServiceCode.API_NOT_INITIALIZED)
    public InternalServerErrorException apiNotInitialized(String apiNames, Object value);

    @DeclareServiceCode(ServiceCode.DOWNLOAD_ERROR)
    public InternalServerErrorException checksumError();

    @DeclareServiceCode(ServiceCode.UNFORSEEN_ERROR)
    public InternalServerErrorException noAssociatedVolumesForVPLEXVolume(final String id);

    @DeclareServiceCode(ServiceCode.UNFORSEEN_ERROR)
    public InternalServerErrorException noSourceVolumeForVPLEXVolumeSnapshot(final String id);

    @DeclareServiceCode(ServiceCode.UNFORSEEN_ERROR)
    public InternalServerErrorException noVplexClusterInfoForVarray(final String vararyId, final String vplexStorageSytsemId);

    @DeclareServiceCode(ServiceCode.UNFORSEEN_ERROR)
    public InternalServerErrorException capacityComputationFailed();

    @DeclareServiceCode(ServiceCode.OBJ_SYSVARRAY_NOT_DEFINED)
    public InternalServerErrorException systemVArrayNotDefined();

    @DeclareServiceCode(ServiceCode.UNFORSEEN_ERROR)
    public InternalServerErrorException unexpectedErrorDuringVarrayChange(Exception e);

    @DeclareServiceCode(ServiceCode.SECURITY_AUTH_TIMEOUT)
    public InternalServerErrorException authTimeout();

    @DeclareServiceCode(ServiceCode.SYS_RECOVERY_TRIGGER_FAILED)
    public InternalServerErrorException triggerRecoveryFailed(String errMsg);

    @DeclareServiceCode(ServiceCode.SYS_RECOVERY_REPAIR_FAILED)
    public InternalServerErrorException nodeRepairFailed();

    @DeclareServiceCode(ServiceCode.SYS_RECOVERY_REBUILD_FAILED)
    public InternalServerErrorException nodeRebuildFailed();

    @DeclareServiceCode(ServiceCode.SYS_RECOVERY_ADD_LISTENER_FAILED)
    public InternalServerErrorException addListenerFailed();

    @DeclareServiceCode(ServiceCode.SYS_RECOVERY_GET_LOCK_FAILED)
    public InternalServerErrorException getLockFailed();

    @DeclareServiceCode(ServiceCode.SYS_RECOVERY_NEW_NODE_FAILURE)
    public InternalServerErrorException newNodeFailureInNodeRecovery(final String nodes);

    @DeclareServiceCode(ServiceCode.SYS_BACKUP_LIST_EXTERNAL_FAILED)
    public InternalServerErrorException listExternalBackupFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.SYS_BACKUP_QUERY_EXTERNAL_FAILED)
    public InternalServerErrorException queryExternalBackupFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.SYS_IPRECONFIG_TRIGGER_FAILED)
    public InternalServerErrorException triggerIpReconfigFailed(String errmsg);

    @DeclareServiceCode(ServiceCode.SYS_DR_ADD_STANDBY_PRECHECK_FAILED)
    public InternalServerErrorException addStandbyPrecheckFailed(String errMsg);

    @DeclareServiceCode(ServiceCode.SYS_DR_NAT_CHECK_FAILED)
    public InternalServerErrorException invalidNatCheckCall(String clientIP, String directClientIp);

    @DeclareServiceCode(ServiceCode.SYS_DR_CREATE_VIPR_CLIENT_FAILED)
    public InternalServerErrorException failToCreateViPRClient();

    @DeclareServiceCode(ServiceCode.SYS_DR_ADD_STANDBY_FAILED)
    public InternalServerErrorException addStandbyFailed(String errMsg);

    @DeclareServiceCode(ServiceCode.SYS_DR_ADD_STANDBY_TIMEOUT)
    public InternalServerErrorException addStandbyFailedTimeout(final long timeoutValue);

    @DeclareServiceCode(ServiceCode.SYS_DR_CONFIG_STANDBY_FAILED)
    public InternalServerErrorException configStandbyFailed(String errMsg);

    @DeclareServiceCode(ServiceCode.SYS_DR_REMOVE_STANDBY_PRECHECK_FAILED)
    public InternalServerErrorException removeStandbyPrecheckFailed(String siteNames, String errMsg);

    @DeclareServiceCode(ServiceCode.SYS_DR_REMOVE_STANDBY_FAILED)
    public InternalServerErrorException removeStandbyFailed(final String siteNames, String errMsg);

    @DeclareServiceCode(ServiceCode.SYS_DR_REMOVE_STANDBY_RECONFIG_FAILED)
    public InternalServerErrorException removeStandbyReconfigFailed(String errMsg);

    @DeclareServiceCode(ServiceCode.SYS_DR_REMOVE_STANDBY_FAILED)
    InternalServerErrorException removeStandbyFailedTimeout(final long timeoutValue);

    @DeclareServiceCode(ServiceCode.SYS_DR_PAUSE_STANDBY_FAILED)
    public InternalServerErrorException pauseStandbyFailed(final String siteName, String errMsg);

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
    public InternalServerErrorException resumeStandbyFailed(final String siteName, String errMsg);

    @DeclareServiceCode(ServiceCode.SYS_DR_RETRY_STANDBY_OP_FAILED)
    public InternalServerErrorException retryStandbyOpFailed(final String siteName, String errMsg);

    @DeclareServiceCode(ServiceCode.SYS_DR_RESUME_STANDBY_TIMEOUT)
    public InternalServerErrorException resumeStandbyFailedTimeout(final long timeoutValue);

    @DeclareServiceCode(ServiceCode.SYS_DR_DATA_SYNC_TIMEOUT)
    public InternalServerErrorException dataSyncFailedTimeout(final long timeoutValue);

    @DeclareServiceCode(ServiceCode.SYS_DR_SWITCHOVER_PRECHECK_FAILED)
    public InternalServerErrorException switchoverPrecheckFailed(final String siteName, String errMsg);

    @DeclareServiceCode(ServiceCode.SYS_DR_SWITCHOVER_FAILED)
    public InternalServerErrorException switchoverFailed(String primaryName, String standbyName, String errMsg);

    @DeclareServiceCode(ServiceCode.SYS_DR_SWITCHOVER_ACTIVE_FAILED_TIMEOUT)
    public InternalServerErrorException switchoverActiveFailedTimeout(String siteName, int timeoutValue);

    @DeclareServiceCode(ServiceCode.SYS_DR_SWITCHOVER_STANDBY_FAILED_TIMEOUT)
    public InternalServerErrorException switchoverStandbyFailedTimeout(String siteName, int timeoutValue);
    
    @DeclareServiceCode(ServiceCode.SYS_DR_FAILOVER_FAILED_TIMEOUT)
    public InternalServerErrorException failoverFailedTimeout(String siteName, int timeoutValue);

    @DeclareServiceCode(ServiceCode.SYS_DR_ACQUIRE_OPERATION_LOCK_FAILED)
    public InternalServerErrorException failToAcquireDROperationLock();

    @DeclareServiceCode(ServiceCode.SYS_DR_CONCURRENT_OPERATION_NOT_ALLOWED)
    public InternalServerErrorException concurrentDROperationNotAllowed(String sitedName, String state);

    @DeclareServiceCode(ServiceCode.SYS_DR_CONCURRENT_OPERATION_NOT_ALLOWED)
    public InternalServerErrorException concurrentRemoveDROperationNotAllowed(String sitedName, String state);

    @DeclareServiceCode(ServiceCode.UNFORSEEN_ERROR)
    public InternalServerErrorException unexpectedErrorVolumePlacement(Exception ex);

    @DeclareServiceCode(ServiceCode.UNFORSEEN_ERROR)
    public InternalServerErrorException unexpectedErrorExportGroupPlacement(Exception ex);

    @DeclareServiceCode(ServiceCode.SYS_DR_FAILOVER_FAILED)
    public InternalServerErrorException failoverFailed(String siteName, String errMsg);

    @DeclareServiceCode(ServiceCode.SYS_DR_FAILOVER_PRECHECK_FAILED)
    public InternalServerErrorException failoverPrecheckFailed(final String siteName, String errMsg);

    @DeclareServiceCode(ServiceCode.SYS_DR_FAILOVER_RECONFIG_FAIL)
    public InternalServerErrorException failoverReconfigFailed(String errMsg);

    @DeclareServiceCode(ServiceCode.SYS_DR_UPDATE_SITE_FAILED)
    public InternalServerErrorException updateSiteFailed(String siteName, String errMsg);

    @DeclareServiceCode(ServiceCode.API_INGESTION_ERROR)
    public InternalServerErrorException ingestNotAllowedNonRPVolume(final String vpoolLabel, final String volumeLabel);

    @DeclareServiceCode(ServiceCode.SYS_DR_UPGRADE_NOT_ALLOWED)
    public InternalServerErrorException upgradeNotAllowedWithoutPausedSite();
}
