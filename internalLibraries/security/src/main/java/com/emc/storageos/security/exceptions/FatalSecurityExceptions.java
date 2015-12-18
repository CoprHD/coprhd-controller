/*
 * Copyright (c) 2013-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.security.exceptions;

import java.net.URI;
import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * This interface holds all the methods used to create an error condition in the
 * synchronous aspect of the controller that will be associated with an HTTP
 * status of 500
 * <p/>
 * Remember to add the English message associated to the method in FatalSecurityExceptions.properties and use the annotation
 * {@link DeclareServiceCode} to set the service code associated to this error condition. You may need to create a new service code if there
 * is no an existing one suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */
@MessageBundle
public interface FatalSecurityExceptions {

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException failedReadingTenantRoles(final Throwable cause);

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException failedGettingTenant(final Throwable cause);

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException tenantUserMappingQueryFailed();

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException permissionsIndexQueryFailed();

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException tenantQueryFailed(final String tenantName);

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException tenantQueryFailed(final String tenantName,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException rootTenantQueryReturnedDuplicates();

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException keyIDCouldNotBeMatchedToSecretKey(final String keyID);

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException failedToGetSecretKey(final Throwable cause);

    @DeclareServiceCode(ServiceCode.SECURITY_PARAMETER_MISSING)
    public FatalSecurityException theParametersAreNotValid(final String name);

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException coordinatorNotInitialized();

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException failedToInitializeClientRequestHelper(final String uri, final String result);

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException couldNotConstructUserObjectFromRequest();

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException couldNotAcquireLockForUser(final String userName);

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException databseExceptionDuringTokenDeletion(final String token,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException exceptionDuringTokenDeletionForUser(
            final String userName, final Throwable cause);

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException exceptionFromContextSourceInitializationForProvider(
            final URI providerID, final Throwable cause);

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException noSuchAlgorithmException(final String algorithm,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException unsupportedOperation();

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException communicationToLDAPResourceFailed();

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException ldapManagerAuthenticationFailed();

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException couldNotAcquireLockTokenCaching();

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException couldNotAcquireRequestedTokenMapCaching();

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException failedToRefreshUser(final String details);

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException distributedDataManagerNotInitialized();

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException failedToCreateCertificate(final Throwable e);

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException failedToLoadPrivateKey(final Throwable e);

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException failedToLoadPublicKey(final Throwable e);

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException failedToReadTrustedCertificates(final Throwable e);

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException failedToInitializedKeystoreNeedDistKeystoreParams();

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException failedToUpdateTrustedCertificates(final Throwable e);

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException failedToReadKeyCertificateEntry(final Throwable e);

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException failedToUpdateKeyCertificateEntry(final Throwable e);

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException failedToUpdateKeyCertificateEntry();

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException viprKeyCertificateEntryCannotBeDeleted();

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException canOnlyUpdateViPRKeyCertificate();

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException certificateMustBeX509();

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException failedToGetKeyCertificate();

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException couldNotParseCertificateToString(final Throwable e);

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException cannotSetTrustedCertificateWithViPRAlias();

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException failedToSetTruststoreSettings(final Throwable e);

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException failedToSetTruststoreSettings();

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException failedRebootAfterKeystoreChange();

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException failedToGetUserKeyPair(final String user,
            final Throwable e);

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException failedToGetUserKeyPair(final String user);

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException failToSaveKeypairToKeyStore(final Throwable e);

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException failToDumpECKeys(final Throwable e);

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException failToDumpDSAKeys(final Throwable e);

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException failToDumpRSAKeys(final Throwable e);

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException failToDoBase64Encode(final Throwable e);

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    public FatalSecurityException failToDoBase64Decode(final Throwable e);

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    FatalSecurityException notSupportAlgorithm(String algo);

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    FatalSecurityException failToGenerateKeypair(String algo, final Throwable e);

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    FatalSecurityException failToChangeIPsecStatus(String status);

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    FatalSecurityException failToNotifyChange(Exception e);

    @DeclareServiceCode(ServiceCode.SECURITY_ERROR)
    FatalSecurityException failToRotateIPsecKey(Exception e);
}
