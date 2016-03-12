/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.scaleio.errorhandling.resources;

import com.emc.storageos.driver.scaleio.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.driver.scaleio.errorhandling.annotations.MessageBundle;

import java.util.List;

/**
 * This interface holds all the methods used to create an error condition that
 * will be associated with an HTTP status of Forbidden (403)
 * <p/>
 * Remember to add the English message associated to the method in ForbiddenExceptions.properties and use the annotation
 * {@link DeclareServiceCode} to set the service code associated to this error condition. You may need to create a new service code if there
 * is no an existing one suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */
@MessageBundle
public interface ForbiddenExceptions {

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    ForbiddenException userCannotUseProxyTokens(final String user);

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    ForbiddenException invalidSecurityContext();

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    ForbiddenException insufficientPermissionsForUser(final String user);

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    ForbiddenException securityAdminCantDropHisOwnSecurityAdminRole(final String user);

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    ForbiddenException failedReadingTenantRoles(final Throwable cause);

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    ForbiddenException failedReadingProjectACLs(final Throwable cause);

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    ForbiddenException userNotPermittedToLogoutAnotherUser(final String userName);

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    ForbiddenException userDoesNotMapToAnyTenancy(final String userName);

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    ForbiddenException internalAPICannotBeUsed();

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    ForbiddenException onlySecurityAdminsCanGetSecrets();

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    ForbiddenException onlySecurityAdminsCanModifyUserMapping();

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    ForbiddenException onlyAdminsCanProvisionFileSystems(final String... roles);

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    ForbiddenException onlyAdminsCanDeactivateFileSystems(final String... roles);

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    ForbiddenException nonLocalUserNotAllowed();

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    ForbiddenException insufficientPermissionWhileSearchingZoneLevelResource(
            String user);

    @DeclareServiceCode(ServiceCode.LICENSE_OPERATION_FORBIDDEN)
    ForbiddenException licenseNotFound(final String type);

    @DeclareServiceCode(ServiceCode.LICENSE_OPERATION_FORBIDDEN)
    ForbiddenException noLicenseFound();

    @DeclareServiceCode(ServiceCode.LICENSE_OPERATION_FORBIDDEN)
    ForbiddenException permissionDeniedForTrialLicense(final String type);
    
    @DeclareServiceCode(ServiceCode.API_BAD_REQUEST)
    ForbiddenException disallowOperationOnDrStandby(final String activeSiteIp);

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    ForbiddenException tenantCannotAccessVirtualArray(final String virtualArray);

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    ForbiddenException tenantCannotAccessVirtualPool(final String virtualPool);

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    ForbiddenException tenantCannotAccessComputeVirtualPool(final String computeVirtualPool);

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    ForbiddenException
            specifiedOwnerIsNotValidForProjectTenant(final String cause);

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    ForbiddenException onlyAdminsCanReleaseFileSystems(final String... roles);

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    ForbiddenException onlyAdminsCanUndoReleasedFileSystems(final String... roles);

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    ForbiddenException onlyPreviouslyReleasedFileSystemsCanBeUndone();

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    ForbiddenException localUsersNotAllowedForSingleSignOn(final String userName);

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    ForbiddenException userBelongsToMultiTenancy(String userName, List<String> strings);

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    ForbiddenException resourceDoesNotBelongToAnyTenant(final String resource, final String name);

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    ForbiddenException tenantAdminCannotDeleteVcenter(final String tenantAdminName, final String vCenterName);

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    ForbiddenException tenantAdminCannotModifyCascadeTenancy(final String tenantAdminName, final String vCenterName);
    
    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    ForbiddenException onlySystemAdminsCanOverrideVpoolPathParameters(final String exportGroupName);
}
