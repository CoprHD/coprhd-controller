/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.xiv.api;

import java.net.URI;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

@MessageBundle
public interface XIVRestExceptions {

    @DeclareServiceCode(ServiceCode.CONTROLLER_UNEXPECTED_VOLUME)
    public Exception notAVolumeOrBlocksnapshotUri(URI uri);

    @DeclareServiceCode(ServiceCode.XIV_COMMAND_FAILURE)
    public ServiceError methodFailed(final String methodName, final Throwable cause);

    @DeclareServiceCode(ServiceCode.XIV_REST_REQUEST_FAILURE)
    public Exception xivRestRequestFailure(String uri, int status);

    @DeclareServiceCode(ServiceCode.XIV_REST_RESPONSE_JSON_PARSER_FAILURE)
    public Exception jsonParserFailure(String json);

    @DeclareServiceCode(ServiceCode.XIV_REST_CREATE_FAILURE)
    public Exception clusterCreationFailure(String xivSystem, String clusterName, String message);

    @DeclareServiceCode(ServiceCode.XIV_REST_DELETE_FAILURE)
    public Exception clusterDeleteFailure(String xivSystem, String clusterName, String message);

    @DeclareServiceCode(ServiceCode.XIV_REST_CREATE_FAILURE)
    public Exception hostCreationFailure(String xivSystem, String hostName, String message);

    @DeclareServiceCode(ServiceCode.XIV_REST_DELETE_FAILURE)
    public Exception hostDeleteFailure(String xivSystem, String hostName, String message);

    @DeclareServiceCode(ServiceCode.XIV_REST_CREATE_FAILURE)
    public Exception hostPortCreationFailure(String xivSystem, String hostName, String port, String message);

    @DeclareServiceCode(ServiceCode.XIV_REST_DELETE_FAILURE)
    public Exception hostPortDeleteFailure(String xivSystem, String hostName, String port, String message);

    @DeclareServiceCode(ServiceCode.XIV_REST_CREATE_FAILURE)
    public Exception volumeExportToClusterFailure(String xivSystem, String clusterName, String volumeName, String message);

    @DeclareServiceCode(ServiceCode.XIV_REST_DELETE_FAILURE)
    public Exception volumeUnExportToClusterFailure(String xivSystem, String clusterName, String volumeName, String message);

    @DeclareServiceCode(ServiceCode.XIV_REST_DELETE_FAILURE)
    public Exception instanceUnavailableForDelete(String xivSystem, String instanceType, String instanceName);

    @DeclareServiceCode(ServiceCode.XIV_REST_HOST_PARTOF_CLUSTER)
    public Exception hostPartofCluster(String xivSystem, String hostName, String clusterName);

    @DeclareServiceCode(ServiceCode.XIV_COMMAND_FAILURE)
    public XIVRestException authenticationFailure(String uri);

    @DeclareServiceCode(ServiceCode.XIV_COMMAND_FAILURE)
    public XIVRestException resourceNotFound(String uri);

    @DeclareServiceCode(ServiceCode.XIV_COMMAND_FAILURE)
    public XIVRestException internalError(String uri);

    @DeclareServiceCode(ServiceCode.XIV_COMMAND_FAILURE)
    public XIVRestException refreshExistingMaskFailure(final String message);

    @DeclareServiceCode(ServiceCode.XIV_COMMAND_FAILURE)
    public XIVRestException queryExistingMasksFailure(final String message, final Throwable cause);

}
