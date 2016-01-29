/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.isilon.restapi;

import java.net.URI;

import javax.ws.rs.core.Response.StatusType;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * This interface holds all the methods used to create {@link IsilonException}s
 * <p/>
 * Remember to add the English message associated to the method in IsilonExceptions.properties and use the annotation
 * {@link DeclareServiceCode} to set the service code associated to this error condition. You may need to create a new service code if there
 * is no an existing one suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */
@MessageBundle
public interface IsilonExceptions {
    @DeclareServiceCode(ServiceCode.ISILON_CONNECTION_ERROR)
    public IsilonException errorCreatingServerURL(final String host, final int port, final Throwable e);

    @DeclareServiceCode(ServiceCode.ISILON_CONNECTION_ERROR)
    public IsilonException unableToConnect(final URI baseUrl, final Throwable e);

    @DeclareServiceCode(ServiceCode.ISILON_CONNECTION_ERROR)
    public IsilonException unableToConnect(final URI baseUrl);

    @DeclareServiceCode(ServiceCode.ISILON_DIR_ERROR)
    public IsilonException existsDirFailed(final String fspath, StatusType resp,
            final Throwable e);

    @DeclareServiceCode(ServiceCode.ISILON_DIR_ERROR)
    public IsilonException createDirFailed(final String fspath, StatusType resp,
            final Throwable e);

    @DeclareServiceCode(ServiceCode.ISILON_ERROR)
    public IsilonException invalidParameters();

    @DeclareServiceCode(ServiceCode.ISILON_ERROR)
    public IsilonException expandFsFailedinvalidParameters(final String path,
            final Long hardLimit);

    @DeclareServiceCode(ServiceCode.ISILON_INFO_ERROR)
    public IsilonException unableToGetIsilonClusterInfo(final String msg, final Throwable cause);

    @DeclareServiceCode(ServiceCode.ISILON_INFO_ERROR)
    public IsilonException unableToGetIsilonClusterConfig(final int status);

    @DeclareServiceCode(ServiceCode.ISILON_INFO_ERROR)
    public IsilonException unableToGetIsilonClusterConfig(final String clientResp, final Throwable cause);

    @DeclareServiceCode(ServiceCode.ISILON_INFO_ERROR)
    public IsilonException unableToGetSubDirectoryList(final String clientResp, final Throwable cause);

    @DeclareServiceCode(ServiceCode.ISILON_DIR_ERROR)
    public IsilonException deleteDirFailedOnIsilonArray(final String msg, final Throwable cause);

    @DeclareServiceCode(ServiceCode.ISILON_RESOURCE_ERROR)
    public IsilonException listResourcesFailedOnIsilonArray(final String key, final String response,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.ISILON_RESOURCE_ERROR)
    public IsilonException createSnapshotScheduleError(final String key, final String response);

    @DeclareServiceCode(ServiceCode.ISILON_RESOURCE_ERROR)
    public IsilonException deletePolicyFailedOnIsilonArray(final String uri, final String response, final Throwable cause);

    @DeclareServiceCode(ServiceCode.ISILON_RESOURCE_ERROR)
    public IsilonException createResourceFailedOnIsilonArray(final String key, final String response,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.ISILON_RESOURCE_ERROR)
    public IsilonException deleteResourceFailedOnIsilonArray(final String key, final String id,
            final String response, final Throwable cause);

    @DeclareServiceCode(ServiceCode.ISILON_RESOURCE_ERROR)
    public IsilonException getResourceFailedOnIsilonArray(final String key, final String length);

    @DeclareServiceCode(ServiceCode.ISILON_RESOURCE_ERROR)
    public IsilonException getResourceFailedOnIsilonArrayExc(final String key, final String id,
            final String response, final Throwable cause);

    @DeclareServiceCode(ServiceCode.ISILON_RESOURCE_ERROR)
    public IsilonException modifyResourceFailedOnIsilonArray(final String key, final String id,
            final String response, final Throwable cause);

    @DeclareServiceCode(ServiceCode.ISILON_INFO_ERROR)
    public IsilonException getStorageConnectionInfoFailedOnIsilonArray(final int status);

    @DeclareServiceCode(ServiceCode.ISILON_INFO_ERROR)
    public IsilonException getStorageConnectionInfoFailedOnIsilonArrayExc(final String response, final Throwable cause);

    @DeclareServiceCode(ServiceCode.ISILON_STATS_ERROR)
    public IsilonException getCurrentStatisticsFailedOnIsilonArray(final int status);

    @DeclareServiceCode(ServiceCode.ISILON_STATS_ERROR)
    public IsilonException getCurrentStatisticsFailedOnIsilonArrayErr(final String key, final String error);

    @DeclareServiceCode(ServiceCode.ISILON_STATS_ERROR)
    public IsilonException getCurrentStatisticsFailedOnIsilonArrayExc(final String response, final Throwable cause);

    @DeclareServiceCode(ServiceCode.ISILON_STATS_ERROR)
    public IsilonException getStatisticsHistoryFailedOnIsilonArray(final int status);

    @DeclareServiceCode(ServiceCode.ISILON_STATS_ERROR)
    public IsilonException getStatisticsHistoryFailedOnIsilonArrayExc(final String response, final Throwable cause);

    @DeclareServiceCode(ServiceCode.ISILON_STATS_ERROR)
    public IsilonException getStatisticsProtocolFailedOnIsilonArray(final int status);

    @DeclareServiceCode(ServiceCode.ISILON_STATS_ERROR)
    public IsilonException getStatisticsProtocolFailedOnIsilonArrayExc(final String response, final Throwable cause);

    @DeclareServiceCode(ServiceCode.ISILON_ERROR)
    public IsilonException processErrorResponseFromIsilon(final String opKey, final String objKey,
            final int httpStatus, final URI baseUrl);

    @DeclareServiceCode(ServiceCode.ISILON_ERROR)
    public IsilonException processErrorResponseFromIsilonMsg(final String opKey, final String objKey,
            final int httpStatus, final URI baseUrl, final String errorEntity);

}
