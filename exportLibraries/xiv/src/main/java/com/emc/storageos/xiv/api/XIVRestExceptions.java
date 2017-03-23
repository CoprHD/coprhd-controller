/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.xiv.api;

import java.net.URI;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

@MessageBundle
public interface XIVRestExceptions {

    @DeclareServiceCode(ServiceCode.CONTROLLER_UNEXPECTED_VOLUME)
    public XIVRestException notAVolumeOrBlocksnapshotUri(URI uri);

    @DeclareServiceCode(ServiceCode.XIV_COMMAND_FAILURE)
    public XIVRestException methodFailed(final String methodName, final Throwable cause);

    @DeclareServiceCode(ServiceCode.XIV_REST_REQUEST_FAILURE)
    public XIVRestException xivRestRequestFailure(String uri, String message);

    @DeclareServiceCode(ServiceCode.XIV_REST_RESPONSE_JSON_PARSER_FAILURE)
    public XIVRestException jsonParserFailure(String json);

    @DeclareServiceCode(ServiceCode.XIV_REST_CREATE_FAILURE)
    public XIVRestException clusterCreationFailure(String xivSystem, String clusterName, String message);

    @DeclareServiceCode(ServiceCode.XIV_REST_DELETE_FAILURE)
    public XIVRestException clusterDeleteFailure(String xivSystem, String clusterName, String message);

    @DeclareServiceCode(ServiceCode.XIV_REST_CREATE_FAILURE)
    public XIVRestException hostCreationFailure(String xivSystem, String hostName, String message);

    @DeclareServiceCode(ServiceCode.XIV_REST_DELETE_FAILURE)
    public XIVRestException hostDeleteFailure(String xivSystem, String hostName, String message);

    @DeclareServiceCode(ServiceCode.XIV_REST_CREATE_FAILURE)
    public XIVRestException hostPortCreationFailure(String xivSystem, String hostName, String port, String message);

    @DeclareServiceCode(ServiceCode.XIV_REST_DELETE_FAILURE)
    public XIVRestException hostPortDeleteFailure(String xivSystem, String hostName, String port, String message);

    @DeclareServiceCode(ServiceCode.XIV_REST_CREATE_FAILURE)
    public XIVRestException volumeExportToClusterFailure(String xivSystem, String clusterName, String volumeName, String message);

    @DeclareServiceCode(ServiceCode.XIV_REST_DELETE_FAILURE)
    public XIVRestException volumeUnExportToClusterFailure(String xivSystem, String clusterName, String volumeName, String message);

    @DeclareServiceCode(ServiceCode.XIV_REST_DELETE_FAILURE)
    public XIVRestException instanceUnavailableForDelete(String xivSystem, String instanceType, String instanceName);

    @DeclareServiceCode(ServiceCode.XIV_REST_HOST_PARTOF_CLUSTER)
    public XIVRestException hostPartofCluster(String xivSystem, String hostName, String clusterName);

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
    
    @DeclareServiceCode(ServiceCode.XIV_REST_REQUEST_FAILURE)
    public XIVRestException errorInHSMHostConfiguration(final String message);

}
