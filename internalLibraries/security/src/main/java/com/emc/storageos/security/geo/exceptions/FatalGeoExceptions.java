/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.security.geo.exceptions;

import com.emc.storageos.security.geo.exceptions.FatalGeoException;
import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

import java.net.URI;

@MessageBundle
public interface FatalGeoExceptions {

    @DeclareServiceCode(ServiceCode.GEOSVC_VDC_CONNECT_ERROR)
    public FatalGeoException unableConnect(String endpoint, Throwable cause);

    @DeclareServiceCode(ServiceCode.GEOSVC_VDC_VERSION_INCOMPATIBLE)
    public FatalGeoException vdcVersionCheckFail(URI vdcId);

    @DeclareServiceCode(ServiceCode.GEOSVC_CONNECTVDC_INVALID_STATUS)
    public FatalGeoException connectVdcInvalidStatus(String errMsg);

    @DeclareServiceCode(ServiceCode.GEOSVC_DISCONNECTVDC_INVALID_STATUS)
    public FatalGeoException disconnectVdcInvalidStatus(String vdcName);

    @DeclareServiceCode(ServiceCode.GEOSVC_DISCONNECTVDC_STILL_REACHABLE)
    public FatalGeoException disconnectVdcStillReachable(String shortVdcId);

    @DeclareServiceCode(ServiceCode.GEOSVC_DISCONNECTVDC_CONCURRENT)
    public FatalGeoException disconnectVdcConcurrentCheckFail(String vdcName);

    @DeclareServiceCode(ServiceCode.GEOSVC_DISCONNECTVDC_FAILED)
    public FatalGeoException disconnectVdcFailed(URI vdcId, Throwable cause);

    @DeclareServiceCode(ServiceCode.GEOSVC_REMOTEVDC_EXCEPTION)
    public FatalGeoException disconnectRemoteSyncFailed(String vdcId, String cause);

    @DeclareServiceCode(ServiceCode.GEOSVC_GEODB_CONFIG_FAILED)
    public FatalGeoException vdcStrategyFailed(Throwable cause);

    @DeclareServiceCode(ServiceCode.GEOSVC_DISCONNECTVDC_FAILED)
    public FatalGeoException disconnectVdcRemoveDBNodesFailed(Throwable cause);

    @DeclareServiceCode(ServiceCode.GEOSVC_RECONNECTVDC_INVALID_STATUS)
    public FatalGeoException reconnectVdcInvalidStatus(String vdcName);

    @DeclareServiceCode(ServiceCode.GEOSVC_RECONNECTVDC_UNREACHABLE)
    public FatalGeoException reconnectVdcUnreachable(String msg);

    @DeclareServiceCode(ServiceCode.GEOSVC_RECONNECTVDC_UNREACHABLE)
    public FatalGeoException reconnectVdcIncompatible();

    @DeclareServiceCode(ServiceCode.GEOSVC_REMOTEVDC_EXCEPTION)
    public FatalGeoException reconnectRemoteSyncFailed(String vdcId, String cause);

    // Acquire global lock for vdc operation fail
    @DeclareServiceCode(ServiceCode.GEOSVC_ACQUIRED_LOCK_FAIL)
    public FatalGeoException acquireLockFail(String vdcShortId, String errMsg);

    // TODO remove all remaining places where this message is used
    @DeclareServiceCode(ServiceCode.GEOSVC_PRECHECK_ERROR)
    public FatalGeoException connectVdcPrecheckFail(String vdcShortId, String errMsg);

    // Merge all vdc config info failed
    @DeclareServiceCode(ServiceCode.GEOSVC_GEODB_CONFIG_FAILED)
    public FatalGeoException mergeConfigFail();

    // Sync new cert failed
    @DeclareServiceCode(ServiceCode.GEOSVC_CONNECTVDC_SYNC_CERT_ERROR)
    public FatalGeoException connectVdcSyncCertFail(String vdcName, Throwable cause);

    // Gen cert chain failed
    @DeclareServiceCode(ServiceCode.GEOSVC_CONNECTVDC_GEN_CERT_CHAIN_ERROR)
    public FatalGeoException connectVdcGenCertChainFail(Throwable cause);

    @DeclareServiceCode(ServiceCode.GEOSVC_WRONG_STATE)
    public FatalGeoException connectVDCLocalMultipleVDC(String id);

    // Sync vdc config to all vdc failed
    @DeclareServiceCode(ServiceCode.GEOSVC_GEODB_CONFIG_FAILED)
    public FatalGeoException syncConfigFail(Throwable cause);

    // Add vdc post check failed
    @DeclareServiceCode(ServiceCode.GEOSVC_POSTCHECK_ERROR)
    public FatalGeoException connectVdcPostCheckFail(Throwable cause);

    // Update vdc connection status failed
    @DeclareServiceCode(ServiceCode.GEOSVC_CONNECTVDC_STATUS_UPDATE_ERROR)
    public FatalGeoException connectVdcStatusUpdateFail(Throwable cause);

    // Fail to remove root's roles and project ownerships
    @DeclareServiceCode(ServiceCode.GEOSVC_CONNECTVDC_REMOVE_ROOT_ROLES_ERROR)
    public FatalGeoException connectVdcRemoveRootRolesFailed(Throwable cause);
    
    // Remove vdc precheck failed, do not meet the requirement
    @DeclareServiceCode(ServiceCode.GEOSVC_PRECHECK_ERROR)
    public FatalGeoException removeVdcPrecheckFail(String vdcName, String errMsg);

    // Remove vdc sync config failed 
    @DeclareServiceCode(ServiceCode.GEOSVC_REMOVEVDC_SYNC_CONFIG_ERROR)
    public FatalGeoException removeVdcSyncConfigFail(Throwable cause);

    // Remove vdc post check failed 
    @DeclareServiceCode(ServiceCode.GEOSVC_POSTCHECK_ERROR)
    public FatalGeoException removeVdcPostcheckFail(Throwable cause);

    @DeclareServiceCode(ServiceCode.GEOSVC_REMOVEVDC_INVALID_STATUS)
    public FatalGeoException removeVdcInvalidStatus(String errMsg);

    @DeclareServiceCode(ServiceCode.GEOSVC_UPDATEVDC_INVALID_STATUS)
    public FatalGeoException updateVdcInvalidStatus(String errMsg);

    // Update vdc precheck failed, do not meet the requirement
    @DeclareServiceCode(ServiceCode.GEOSVC_PRECHECK_ERROR)
    public FatalGeoException updateVdcPrecheckFail(String errMsg);

    @DeclareServiceCode(ServiceCode.GEOSVC_UNSTABLE_VDC_ERROR)
    public FatalGeoException unstableVdcFailure(String vdcName);

    @DeclareServiceCode(ServiceCode.GEOSVC_VESION_ERROR)
    public FatalGeoException vdcVersionCheckFail(String vdcName);

    @DeclareServiceCode(ServiceCode.GEOSVC_WRONG_STATE)
    public FatalGeoException vdcWrongStatus(String status);

    @DeclareServiceCode(ServiceCode.GEOSVC_GEODB_CONFIG_FAILED)
    public FatalGeoException failedToSyncConfigurationForVdc(String vdcName, Throwable cause);

    @DeclareServiceCode(ServiceCode.GEOSVC_PRECHECK_ERROR)
    public FatalGeoException failedToSendPreCheckRequest(String vdcName, Throwable cause);

    @DeclareServiceCode(ServiceCode.GEOSVC_SECURITY_ERROR)
    public FatalGeoException remoteVdcAuthorizationFailed(String vdcName, Throwable cause);

    @DeclareServiceCode(ServiceCode.GEOSVC_PRECHECK_ERROR)
    public FatalGeoException failedToSedPostCheckRequest(String vdcName, Throwable cause);

    @DeclareServiceCode(ServiceCode.GEOSVC_SECURITY_ERROR)
    public FatalGeoException keyStoreFailure(String vdcName, Throwable cause);

    @DeclareServiceCode(ServiceCode.GEOSVC_GEODB_CONFIG_FAILED)
    public FatalGeoException syncBadAPIVDC(String vdcName);

    @DeclareServiceCode(ServiceCode.GEOSVC_FEDERATION_UNSTABLE)
    public FatalGeoException geoOperationDetected(String msg);

    @DeclareServiceCode(ServiceCode.GEOSVC_FEDERATION_UNSTABLE)
    public FatalGeoException vdcNotStable(final String unstableVdcId);

    @DeclareServiceCode(ServiceCode.GEOSVC_FEDERATION_UNSTABLE)
    public FatalGeoException vdcNotConnected(final String notConnectedVdcId);

    @DeclareServiceCode(ServiceCode.GEOSVC_FEDERATION_UNSTABLE)
    public FatalGeoException vdcNotReachable(final String unreachableVdcId);

    @DeclareServiceCode(ServiceCode.GEOSVC_ACQUIRED_LOCK_FAIL)
    public FatalGeoException accessGlobalLockFail();

    @DeclareServiceCode(ServiceCode.GEOSVC_VDC_VERSION_INCOMPATIBLE)
    public FatalGeoException versionIsNotUpgradableInGeo(final String target);

    @DeclareServiceCode(ServiceCode.GEOSVC_INTERNAL_ERROR)
    public FatalGeoException cannotPerformOperation(final String vdc, final String reason);

    @DeclareServiceCode(ServiceCode.GEOSVC_INVALID_ENDPOINT)
    public FatalGeoException invalidFQDNEndPoint(final String vdcName, final String ip);

    @DeclareServiceCode(ServiceCode.GEOSVC_INVALID_ENDPOINT)
    public FatalGeoException wrongIPSpecification(final String vdcName);

    @DeclareServiceCode(ServiceCode.GEOSVC_WRONG_STATE)
    public FatalGeoException remoteVDCContainData();

    @DeclareServiceCode(ServiceCode.GEOSVC_VDC_VERSION_INCOMPATIBLE)
    public FatalGeoException remoteVDCIncompatibleVersion();
    
    @DeclareServiceCode(ServiceCode.GEOSVC_VDC_VERSION_INCOMPATIBLE)
    public FatalGeoException remoteVDCInLowerVersion();
    
    @DeclareServiceCode(ServiceCode.GEOSVC_VDC_VERSION_INCOMPATIBLE)
    public FatalGeoException hasTripleVDCVersionsInFederation();

    @DeclareServiceCode(ServiceCode.GEOSVC_CONNECTIVITY_ERROR)
    public FatalGeoException failedToCheckConnectivity(String msg);

    @DeclareServiceCode(ServiceCode.GEOSVC_CONNECTIVITY_ERROR)
    public FatalGeoException failedRemoveNodesFromBlackList(String vdcName, String remoteVdc, Throwable cause);

    @DeclareServiceCode(ServiceCode.GEOSVC_REMOTEVDC_EXCEPTION)
    public FatalGeoException remoteVDCException(int status, String remoteFailure);

    @DeclareServiceCode(ServiceCode.GEOSVC_WRONG_STATE)
    public FatalGeoException remoteFreshVDCWrongStatus(URI id);

    @DeclareServiceCode(ServiceCode.GEOSVC_WRONG_STATE)
    public FatalGeoException remoteVDCWrongOperationStatus(URI id, String operation);

    @DeclareServiceCode(ServiceCode.GEOSVC_WRONG_STATE)
    public FatalGeoException remoteVDCWrongStandaloneInstall();

    @DeclareServiceCode(ServiceCode.GEOSVC_WRONG_STATE)
    public FatalGeoException remoteVDCFailedToGetVersion(URI id);

    @DeclareServiceCode(ServiceCode.GEOSVC_WRONG_STATE)
    public FatalGeoException remoteVDCGeoEncryptionMissing();

    @DeclareServiceCode(ServiceCode.GEOSVC_WRONG_STATE)
    public FatalGeoException remoteInitiatorIpError();

    @DeclareServiceCode(ServiceCode.GEOSVC_WRONG_STATE)
    public FatalGeoException failedToFindLocalVDC();

    @DeclareServiceCode(ServiceCode.GEOSVC_WRONG_STATE)
    public FatalGeoException invalidNatCheckCall(String clientIP, String directClientIp);
}
