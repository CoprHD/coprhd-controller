/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.svcs.errorhandling.resources;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;

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
    public ForbiddenException userCannotUseProxyTokens(final String user);

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    public ForbiddenException invalidSecurityContext();

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    public ForbiddenException insufficientPermissionsForUser(final String user);

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    public ForbiddenException securityAdminCantDropHisOwnSecurityAdminRole(final String user);

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    public ForbiddenException failedReadingTenantRoles(final Throwable cause);

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    public ForbiddenException failedReadingProjectACLs(final Throwable cause);

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    public ForbiddenException userNotPermittedToLogoutAnotherUser(final String userName);

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    public ForbiddenException userDoesNotMapToAnyTenancy(final String userName);

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    public ForbiddenException internalAPICannotBeUsed();

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    public ForbiddenException onlySecurityAdminsCanGetSecrets();

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    public ForbiddenException onlySecurityAdminsCanModifyUserMapping();

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    public ForbiddenException onlyAdminsCanProvisionFileSystems(final String... roles);

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    public ForbiddenException onlyAdminsCanDeactivateFileSystems(final String... roles);

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    public ForbiddenException nonLocalUserNotAllowed();

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    public ForbiddenException insufficientPermissionWhileSearchingZoneLevelResource(
            String user);

    @DeclareServiceCode(ServiceCode.LICENSE_OPERATION_FORBIDDEN)
    public ForbiddenException licenseNotFound(final String type);

    @DeclareServiceCode(ServiceCode.LICENSE_OPERATION_FORBIDDEN)
    public ForbiddenException noLicenseFound();

    @DeclareServiceCode(ServiceCode.LICENSE_OPERATION_FORBIDDEN)
    public ForbiddenException permissionDeniedForTrialLicense(final String type);

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    public ForbiddenException tenantCannotAccessVirtualArray(final String virtualArray);

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    public ForbiddenException tenantCannotAccessVirtualPool(final String virtualPool);

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    public ForbiddenException tenantCannotAccessComputeVirtualPool(final String computeVirtualPool);

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    public ForbiddenException
            specifiedOwnerIsNotValidForProjectTenant(final String cause);

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    public ForbiddenException onlyAdminsCanReleaseFileSystems(final String... roles);

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    public ForbiddenException onlyAdminsCanUndoReleasedFileSystems(final String... roles);

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    public ForbiddenException onlyPreviouslyReleasedFileSystemsCanBeUndone();

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    public ForbiddenException localUsersNotAllowedForSingleSignOn(final String userName);

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    public ForbiddenException userBelongsToMultiTenancy(String userName, List<String> strings);

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    public ForbiddenException resourceDoesNotBelongToAnyTenant(final String resource, final String name);

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    public ForbiddenException tenantAdminCannotDeleteVcenter(final String tenantAdminName, final String vCenterName);

    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    public ForbiddenException tenantAdminCannotModifyCascadeTenancy(final String tenantAdminName, final String vCenterName);
    
    @DeclareServiceCode(ServiceCode.SECURITY_INSUFFICIENT_PERMISSIONS)
    public ForbiddenException onlySystemAdminsCanOverrideVpoolPathParameters(final String exportGroupName);
}
