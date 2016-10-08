/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hds;

import java.net.URI;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * This interface holds all the methods used to create {@link HDSException}s
 * <p/>
 * Remember to add the English message associated to the method in HDSExceptions.properties and use the annotation
 * {@link DeclareServiceCode} to set the service code associated to this error condition. You may need to create a new service code if there
 * is no an existing one suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */
@MessageBundle
public interface HDSExceptions {
    // TODO needs to migrate all excepions to properties file.
    @DeclareServiceCode(ServiceCode.HDS_INVALID_RESPONSE)
    public HDSException invalidResponseFromHDS(final String message);

    @DeclareServiceCode(ServiceCode.HDS_VOLUME_CREATION_FAILED)
    public HDSException notAbleToCreateVolume(final int errorCode, final String errorDescription);

    @DeclareServiceCode(ServiceCode.HDS_VOLUME_DELETION_FAILED)
    public HDSException notAbleToDeleteVolume(final int errorCode, final String errorDescription);

    @DeclareServiceCode(ServiceCode.HDS_VOLUME_INFO_FAILED)
    public HDSException notAbleToGetVolumeInfo(final String message);

    @DeclareServiceCode(ServiceCode.HDS_NOT_ABLE_TO_ADD_INITIATOR)
    public HDSException notAbleToAddInitiatorToHostStorageDomain(final String initiatorType, final String hostStorageDomainId,
            final String systemId);

    @DeclareServiceCode(ServiceCode.HDS_NOT_ABLE_TO_ADD_INITIATOR)
    public HDSException notAbleToAddInitiatorsToHostStorageDomain(final String systemId);

    @DeclareServiceCode(ServiceCode.ERROR_RESPONSE_RECEIVED)
    public HDSException errorResponseReceived(final int errorCode, final String errorDescription);

    @DeclareServiceCode(ServiceCode.HDS_NOT_ABLE_TO_ADD_HSD)
    public HDSException notAbleToAddHSD(final String systemId);

    @DeclareServiceCode(ServiceCode.HDS_NOT_ABLE_TO_ADD_VOLUME_TO_HSD)
    public HDSException notAbleToAddVolumeToHSD(final String hostStorageDomainId, final String systemId);

    @DeclareServiceCode(ServiceCode.HDS_NOT_ABLE_TO_GET_FREE_LUN_INFO)
    public HDSException notAbleToGetFreeLunInfoForHSD(final String hostStorageDomainId, final String systemId);

    @DeclareServiceCode(ServiceCode.HDS_NOT_ABLE_TO_GET_FREE_LUN_INFO)
    public HDSException notAbleToGetHostInfoForHSD();

    @DeclareServiceCode(ServiceCode.HDS_NOT_ABLE_ADD_HOST)
    public HDSException notAbleToAddHostToDeviceManager(final String hostName);

    @DeclareServiceCode(ServiceCode.HDS_ASYNC_TASK_INVALID_RESPONSE)
    public HDSException asyncTaskInvalidResponse(int responseStatus);

    @DeclareServiceCode(ServiceCode.HDS_ASYNC_TASK_MAXIMUM_RETRIES_EXCEED)
    public HDSException asyncTaskMaximumRetriesExceed(final String messageId);

    @DeclareServiceCode(ServiceCode.HDS_ASYNC_TASK_WITH_ERROR_RESPONSE)
    public HDSException asyncTaskFailedWithErrorResponse(final String messageId, final String errorDescription, final int errorCode);

    @DeclareServiceCode(ServiceCode.HDS_ASYNC_TASK_WITH_ERROR_RESPONSE)
    public HDSException asyncTaskFailedForMetaVolume(URI storageSystemURI);

    @DeclareServiceCode(ServiceCode.HDS_ASYNC_TASK_WITH_ERROR_RESPONSE)
    public HDSException asyncTaskFailedWithErrorResponseWithoutErrorCode(final String messageId, final String errorDescription);

    @DeclareServiceCode(ServiceCode.HDS_ASYNC_TASK_WITH_ERROR_RESPONSE)
    public HDSException asyncTaskFailedTimeout(final long miiliseconds);

    @DeclareServiceCode(ServiceCode.HDS_ASYNC_TASK_WITH_ERROR_RESPONSE)
    public HDSException asyncTaskFailed(final String cause);

    @DeclareServiceCode(ServiceCode.HDS_COMMAND_ERROR)
    public HDSException refreshExistingMaskFailure(final String maskName);

    @DeclareServiceCode(ServiceCode.HDS_COMMAND_ERROR)
    public HDSException queryExistingMasksFailure(final String exceptionMessage);

    @DeclareServiceCode(ServiceCode.HDS_HSD_ALREADY_EXISTS_WITH_SAME_INITIATORS)
    public HDSException hsdAlreadyExistsForSameInitiators(String message);

    @DeclareServiceCode(ServiceCode.HDS_RESPONSE_PARSING_FAILED)
    public HDSException unableToParseResponse();

    @DeclareServiceCode(ServiceCode.HDS_SCAN_FAILED)
    public HDSException scanFailed(Throwable ex);

    @DeclareServiceCode(ServiceCode.HDS_FAILED_TO_REGISTER_HOST)
    public HDSException unableToRegisterHost();

    @DeclareServiceCode(ServiceCode.HDS_FAILED_TO_GET_HOST_ONFO)
    public HDSException unableToGetHostsInfo(String message);

    @DeclareServiceCode(ServiceCode.HDS_UNSUPPORTED_HOST_WITH_BOTH_FC_ISCSI_INITIATORS)
    public HDSException unsupportedConfigurationFoundInHost();

    @DeclareServiceCode(ServiceCode.UNABLE_TO_GENERATE_INPUT_XML)
    public HDSException unableToGenerateInputXmlForGivenRequest(String message);

    @DeclareServiceCode(ServiceCode.UNABLE_TO_GENERATE_INPUT_XML_DUE_TO_NO_OPERATIONS)
    public HDSException unableToGenerateInputXmlDueToNoOperations();

    @DeclareServiceCode(ServiceCode.UNABLE_TO_GENERATE_INPUT_XML_DUE_TO_UNSUPPORTED_MODEL)
    public HDSException unableToGenerateInputXmlDueToUnSupportedModelFound();

    @DeclareServiceCode(ServiceCode.HDS_UNSUPPORTED_OPERATION)
    public HDSException unsupportedOperationOnThisModel();

    @DeclareServiceCode(ServiceCode.UNABLE_TO_PROCESS_REQUEST_DUE_TO_UNAVAILABLE_FREE_LUNS)
    public HDSException unableToProcessRequestDueToUnavailableFreeLUNs();

    @DeclareServiceCode(ServiceCode.HDS_REPLICATION_CONFIGURATION_PROBLEM)
    public HDSException replicationGroupNotAvailable();

    @DeclareServiceCode(ServiceCode.HDS_REPLICATION_CONFIGURATION_PROBLEM)
    public HDSException notAbleToCreateShadowImagePair();

    @DeclareServiceCode(ServiceCode.HDS_REPLICATION_CONFIGURATION_PROBLEM)
    public HDSException snapshotGroupNotAvailable(String systemNativeGuid);

    @DeclareServiceCode(ServiceCode.HDS_REPLICATION_CONFIGURATION_PROBLEM)
    public HDSException thinImagePoolNotAvailable(String systemNativeGuid);

    @DeclareServiceCode(ServiceCode.HDS_REPLICATION_CONFIGURATION_PROBLEM)
    public HDSException notEnoughFreeCapacityOnthinImagePool(String systemNativeGuid);

    @DeclareServiceCode(ServiceCode.HDS_REPLICATION_CONFIGURATION_PROBLEM)
    public HDSException notAbleToCreateThinImagePair();

    @DeclareServiceCode(ServiceCode.HDS_REPLICATION_CONFIGURATION_PROBLEM)
    public HDSException notAbleToCreateThinImagePairError(final int errorCode, final String errorDescription);

    @DeclareServiceCode(ServiceCode.HDS_REPLICATION_CONFIGURATION_PROBLEM)
    public HDSException notAbleToCreateSnapshot(final int errorCode, final String errorDescription);

    @DeclareServiceCode(ServiceCode.HDS_REPLICATION_CONFIGURATION_PROBLEM)
    public HDSException notAbleToDeleteSnapshot(final int errorCode, final String errorDescription);

    @DeclareServiceCode(ServiceCode.HDS_EXPORT_GROUP_UPDATE_FAILURE)
    public HDSException notAbleToFindHostStorageDomain(final String hsdId);

    @DeclareServiceCode(ServiceCode.HDS_REPLICATION_CONFIGURATION_PROBLEM)
    public HDSException nullAsyncTaskIdForDeleteSnapshot(final String snapshotId);
}
