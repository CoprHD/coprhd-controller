/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.svcs.errorhandling.exceptions;

import static com.emc.storageos.svcs.errorhandling.resources.ServiceCode.API_ERROR;
import static com.emc.storageos.svcs.errorhandling.resources.ServiceCode.API_METHOD_NOT_SUPPORTED;
import static com.emc.storageos.svcs.errorhandling.resources.ServiceCode.API_NO_PLACEMENT_FOUND;
import static com.emc.storageos.svcs.errorhandling.resources.ServiceCode.API_PARAMETER_INVALID;
import static com.emc.storageos.svcs.errorhandling.resources.ServiceCode.CONTROLLER_ERROR;
import static com.emc.storageos.svcs.errorhandling.resources.ServiceCode.IO_ERROR;
import static com.sun.jersey.api.client.ClientResponse.Status.BAD_REQUEST;

import java.net.URI;

import org.junit.Test;

import com.emc.storageos.svcs.errorhandling.mappers.BaseServiceCodeExceptionTest;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.ForbiddenException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCodeException;

@SuppressWarnings("deprecation")
public class ServiceCodeExceptionTest extends BaseServiceCodeExceptionTest {

    @Test
    public void failedToCreateDir() {
        final String dirName = "dirName";
        final ServiceCodeException exception = new ServiceCodeException(IO_ERROR,
                "Failed to create temp dir: {0}", new Object[] { dirName });
        assertIoError("Failed to create temp dir: " + dirName, exception);
    }

    @Test
    public void keypoolNotFound() {
        final String keyPath = "keyPool/RelativePath";
        final String key = "key";
        final ServiceCodeException exception = new ServiceCodeException(IO_ERROR,
                "Not found, key pool: {0}, key {1}", new Object[] { keyPath, key });
        assertIoError("Not found, key pool: " + keyPath + ", key " + key, exception);
    }

    @Test
    public void nullDirectory() {
        final ServiceCodeException exception = new ServiceCodeException(IO_ERROR,
                "Null directory name", null);
        assertIoError("Null directory name", exception);
    }

    @Test
    public void unableToCreateDir() {
        final String dirPath = "/serverIdDir";
        final ServiceCodeException exception = new ServiceCodeException(IO_ERROR,
                "Unable to create server id directories: {0}", new Object[] { dirPath });
        assertIoError("Unable to create server id directories: " + dirPath, exception);
    }

    @Test
    public void invalidTag() {
        final ServiceCodeException exception = new ServiceCodeException(API_PARAMETER_INVALID,
                "Tag is empty or contains less than 2 characters", null);
        assertApiBadParameters("Tag is empty or contains less than 2 characters", exception);
    }

    @Test
    public void alreadyContainsZone() {
        final URI id = createTestId("TransportZone");
        final ServiceCodeException exception = new ServiceCodeException(API_PARAMETER_INVALID,
                "VirtualArray already contains an IP transport zone: {0}", new Object[] { id });
        assertApiBadParameters("VirtualArray already contains an IP transport zone: " + id,
                exception);
    }

    @Test
    public void missingOrInvalid() {
        final String fieldName = "fieldName";
        final ServiceCodeException exception = new ServiceCodeException(API_PARAMETER_INVALID,
                "Missing or invalid argument: {0}", new Object[] { fieldName });
        assertApiBadParameters("Missing or invalid argument: " + fieldName, exception);
    }

    @Test
    public void duplicateNetworkSystem() {
        final String ipAddress = "ip_address";
        final ServiceCodeException exception = new ServiceCodeException(API_PARAMETER_INVALID,
                "Duplicate NetworkSystem already exists at IP address: {0}",
                new Object[] { ipAddress });
        assertApiBadParameters(
                "Duplicate NetworkSystem already exists at IP address: " + ipAddress, exception);
    }

    @Test
    public void duplicateSMIS() {
        final String ipAddress = "smis_provider_ip";
        final ServiceCodeException exception = new ServiceCodeException(API_PARAMETER_INVALID,
                "Duplicate NetworkSystem SMI-S provider already exists at IP address: {0}",
                new Object[] { ipAddress });
        assertApiBadParameters(
                "Duplicate NetworkSystem SMI-S provider already exists at IP address: " + ipAddress,
                exception);
    }

    @Test
    public void nameExists() {
        final ServiceCodeException exception = new ServiceCodeException(API_PARAMETER_INVALID,
                "A NetworkSystem with the same name already exists.", null);
        assertApiBadParameters("A NetworkSystem with the same name already exists.", exception);
    }

    @Test
    public void endpointNotAdded() {
        final String discoveredEndpoints = "discoveredEndpoints";
        final ServiceCodeException exception = new ServiceCodeException(API_PARAMETER_INVALID,
                "Endpoints {0} cannot be added because "
                        + "they were discovered automatically in different transport zones",
                new Object[] { discoveredEndpoints });
        assertApiBadParameters(
                "Endpoints "
                        + discoveredEndpoints
                        + " cannot be added because they were discovered automatically in different transport zones",
                exception);
    }

    @Test
    public void IllegalId() {
        final String uri = "uri";
        final ServiceCodeException exception = new ServiceCodeException(API_PARAMETER_INVALID,
                "Illegal URI: {0}", new Object[] { uri });
        assertApiBadParameters("Illegal URI: " + uri, exception);
    }

    @Test
    public void invalidACL() {
        final String acl = "ace";
        final ServiceCodeException exception = new ServiceCodeException(API_PARAMETER_INVALID,
                "invalid acl: {0}", new Object[] { acl });
        assertApiBadParameters("invalid acl: " + acl, exception);
    }

    @Test
    public void invalidRole() {
        final String role = "role";
        final ServiceCodeException exception = new ServiceCodeException(API_PARAMETER_INVALID,
                "invalid role: {0}", new Object[] { role });
        assertApiBadParameters("invalid role: " + role, exception);
    }

    @Test
    public void cannotRegisterType() {
        final String type = "systemType";
        final ServiceCodeException exception = new ServiceCodeException(API_PARAMETER_INVALID,
                "Cannot register systems with type: {0}", new Object[] { type });
        assertApiBadParameters("Cannot register systems with type: " + type, exception);
    }

    @Test
    public void parentNotRoot() {
        final ServiceCodeException exception = new ServiceCodeException(API_PARAMETER_INVALID,
                "Parent tenant is not root tenant", null);
        assertApiBadParameters("Parent tenant is not root tenant", exception);
    }

    @Test
    public void rootNoDelete() {
        final ServiceCodeException exception = new ServiceCodeException(API_PARAMETER_INVALID,
                "Root tenant can not be deleted", null);
        assertApiBadParameters("Root tenant can not be deleted", exception);
    }

    @Test
    public void unableToCreateMashaller() {
        final String type = "type";
        final ServiceCodeException exception = new ServiceCodeException(API_PARAMETER_INVALID,
                "Unable to create a mashaller for mediatype: {0}", new Object[] { type });
        assertApiBadParameters("Unable to create a mashaller for mediatype: " + type, exception);
    }

    @Test
    public void markedForDeletion() {
        final URI id = createTestId("AuthnProvider");
        final ServiceCodeException exception = new ServiceCodeException(API_PARAMETER_INVALID,
                "AuthnProvider {0} is marked for deletion", new Object[] { id });
        assertApiBadParameters("AuthnProvider " + id + " is marked for deletion", exception);
    }

    @Test
    public void invalidInput() {
        final int lineNumber = 10;
        final int columnNumber = 5;
        final ServiceCodeException exception = new ServiceCodeException(API_PARAMETER_INVALID,
                "Invalid input at line {0}, column {1}", new Object[] { lineNumber, columnNumber });
        assertApiBadParameters("Invalid input at line " + lineNumber + ", column " + columnNumber,
                exception);
    }

    @Test
    public void noTenant() {
        final String userName = "userName";

        final ForbiddenException exception = APIException.forbidden
                .userDoesNotMapToAnyTenancy(userName);
        assertApiInsufficientPermissions("User " + userName + " does not map to any tenancy",
                exception);
    }

    @Test
    public void internalAPI() {
        final ForbiddenException exception = APIException.forbidden
                .internalAPICannotBeUsed();
        assertApiInsufficientPermissions("Internal api can not be used with public resources",
                exception);
    }

    @Test
    public void invalidContext() {
        final ForbiddenException exception = APIException.forbidden
                .invalidSecurityContext();
        assertApiInsufficientPermissions("Invalid security context", exception);
    }

    @Test
    public void insufficientPermissions() {
        final String user = "user";
        final ForbiddenException exception = APIException.forbidden
                .insufficientPermissionsForUser(user);
        assertApiInsufficientPermissions("Insufficient permissions for user " + user + ".",
                exception);
    }

    @Test
    public void onlyAdmin() {
        final ForbiddenException exception = APIException.forbidden
                .onlyAdminsCanProvisionFileSystems("system_admin", "tenant_admin");
        assertApiInsufficientPermissions(
                "Only [system_admin, tenant_admin] can provision file systems for object",
                exception);
    }

    @Test
    public void readingRoles() {
        final ForbiddenException exception = APIException.forbidden
                .failedReadingTenantRoles(null);
        assertApiInsufficientPermissions("Failed reading tenant roles", exception);
    }

    @Test
    public void readingACLs() {
        final ForbiddenException exception = APIException.forbidden
                .failedReadingProjectACLs(null);
        assertApiInsufficientPermissions("Failed reading project ACLs", exception);
    }

    @Test
    public void notLogout() {
        final String userName = "userName";
        final ForbiddenException exception = APIException.forbidden
                .userNotPermittedToLogoutAnotherUser(userName);
        assertApiInsufficientPermissions("User " + userName
                + " not permitted to logout another user", exception);
    }

    private void assertApiNoPlacementFound(final String message,
            final ServiceCodeException exception) {
        assertException(message, API_NO_PLACEMENT_FOUND.getCode(),
                "Unable to find a suitable placement to handle the request", BAD_REQUEST.getStatusCode(), exception);
    }

    @Test
    public void noPoolFound() {
        final URI cosId = createTestId("CoS");
        final URI neighborhoodId = createTestId("Neighbohood");
        final ServiceCodeException exception = new ServiceCodeException(API_NO_PLACEMENT_FOUND,
                "No matching storage pool found using vpool {0} and VirtualArray {1}", new Object[] {
                        cosId, neighborhoodId });
        assertApiNoPlacementFound("No matching storage pool found using vpool " + cosId
                + " and VirtualArray " + neighborhoodId, exception);
    }

    @Test
    public void noPoolFoundException() {
        final Exception e = createException();
        final URI cosId = createTestId("CoS");
        final URI neighborhoodId = createTestId("Neighbohood");
        final ServiceCodeException exception = new ServiceCodeException(
                API_NO_PLACEMENT_FOUND,
                e,
                "No matching storage pool found using vpool {0} and VirtualArray {1}. Caused by: {2}",
                new Object[] { cosId, neighborhoodId, e.getMessage() });
        assertApiNoPlacementFound("No matching storage pool found using vpool " + cosId
                + " and VirtualArray " + neighborhoodId + ". Caused by: " + EXCEPTION_MESSAGE,
                exception);
    }

    @Test
    public void noProtectionPoolFound() {
        final String label = "protectionNeighborhoodLabel";
        final ServiceCodeException exception = new ServiceCodeException(API_NO_PLACEMENT_FOUND,
                "Could not find a Storage pool for Protection VirtualArray: {0}",
                new Object[] { label });
        assertApiNoPlacementFound("Could not find a Storage pool for Protection VirtualArray: "
                + label, exception);
    }

    @Test
    public void notSupportedVPlex() {
        final ServiceCodeException exception = new ServiceCodeException(API_METHOD_NOT_SUPPORTED,
                "Not Supported for VPlex volumes", null);
        assertApiMethodNotAllowed("Not Supported for VPlex volumes", exception);
    }

    @Test
    public void notSupported() {
        final ServiceCodeException exception = new ServiceCodeException(API_METHOD_NOT_SUPPORTED,
                "Operation not supported", null);
        assertApiMethodNotAllowed("Operation not supported", exception);
    }

    @Test
    public void noDelete() {
        final URI id = createTestId("Volume");
        final ServiceCodeException exception = new ServiceCodeException(API_ERROR,
                "Attempt to delete a protected volume that is not the source volume: {0}",
                new Object[] { id });
        assertApiError("Attempt to delete a protected volume that is not the source volume: " + id,
                exception);
    }

    @Test
    public void snapshotFailed() {
        final ServiceCodeException exception = new ServiceCodeException(CONTROLLER_ERROR,
                "Non-protected volume or snapshot add volume failed", null);
        assertControllerError("Non-protected volume or snapshot add volume failed", exception);
    }
}
