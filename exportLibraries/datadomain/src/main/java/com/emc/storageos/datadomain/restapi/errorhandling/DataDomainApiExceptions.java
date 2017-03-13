/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.datadomain.restapi.errorhandling;

import com.emc.storageos.datadomain.restapi.errorhandling.DataDomainApiException;
import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

import java.net.URI;

/**
 * This interface holds all the methods used to create {@link VPlexApiException}s
 * <p/>
 * Remember to add the English message associated to the method in VPlexApiExceptions.properties and use the annotation
 * {@link com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode} to set the service code associated to this error condition.
 * You may need to create a new service code if there is no an existing one suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */
@MessageBundle
public interface DataDomainApiExceptions {

    @DeclareServiceCode(ServiceCode.DATADOMAIN_API_ERROR)
    DataDomainApiException authenticationFailure(String dataDomainURI);

    @DeclareServiceCode(ServiceCode.DATADOMAIN_API_ERROR)
    DataDomainApiException connectionFailure(String dataDomainURI);

    @DeclareServiceCode(ServiceCode.DATADOMAIN_API_ERROR)
    DataDomainApiException jsonWriterReaderException(Throwable cause);

    @DeclareServiceCode(ServiceCode.DATADOMAIN_API_ERROR)
            DataDomainApiException failedToFindManagementSystem(String systemNo);

    @DeclareServiceCode(ServiceCode.DATADOMAIN_API_ERROR)
    DataDomainApiException failedResponseFromDataDomainMsg(URI uri, int status, String msg, int ddCode);

    @DeclareServiceCode(ServiceCode.DATADOMAIN_API_ERROR)
    DataDomainApiException failedResponseFromDataDomain(URI uri, int status);

    @DeclareServiceCode(ServiceCode.DATADOMAIN_API_ERROR)
    DataDomainApiException failedDataDomainDiscover(String system, Throwable cause);

    @DeclareServiceCode(ServiceCode.DATADOMAIN_API_ERROR)
    DataDomainApiException failedProcessExportOption(String option);

    @DeclareServiceCode(ServiceCode.DATADOMAIN_API_ERROR)
    DataDomainApiException failedExportPathDoesNotExist(String exportPath);

    @DeclareServiceCode(ServiceCode.DATADOMAIN_API_ERROR)
    DataDomainApiException failedToAddExportClients(final String exportPath);

    @DeclareServiceCode(ServiceCode.DATADOMAIN_API_ERROR)
    DataDomainApiException failedToCreateExport(final String exportPath);

    @DeclareServiceCode(ServiceCode.DATADOMAIN_API_ERROR)
    DataDomainApiException failedToDeleteExportClients(final String exportPath);

    @DeclareServiceCode(ServiceCode.DATADOMAIN_API_ERROR)
    DataDomainApiException failedToDeleteExport(String message);

    @DeclareServiceCode(ServiceCode.DATADOMAIN_API_ERROR)
    DataDomainApiException failedSharePathDoesNotExist(String sharePath);

    @DeclareServiceCode(ServiceCode.DATADOMAIN_API_ERROR)
    DataDomainApiException unsupportedVersion(String version);

    @DeclareServiceCode(ServiceCode.DATADOMAIN_API_ERROR)
    DataDomainApiException statsCollectionFailed(String message);

    @DeclareServiceCode(ServiceCode.DATADOMAIN_API_ERROR)
    DataDomainApiException updateExportFailedNoExistingExport(final String FsId);

    @DeclareServiceCode(ServiceCode.DATADOMAIN_API_ERROR)
    DataDomainApiException deleteExportRulesFailedNoExistingExport(final String FsId);

    @DeclareServiceCode(ServiceCode.DATADOMAIN_API_ERROR)
    DataDomainApiException scanFailedIncompatibleDdmc(final String version,
            final String minVersion);

    @DeclareServiceCode(ServiceCode.DATADOMAIN_API_ERROR)
    DataDomainApiException opFailedProviderUnreachable(final String op,
            final String provider);

    @DeclareServiceCode(ServiceCode.DATADOMAIN_API_ERROR)
    DataDomainApiException connectStorageFailed(final String system);

}
