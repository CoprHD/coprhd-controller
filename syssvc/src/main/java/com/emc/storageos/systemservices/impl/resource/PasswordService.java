/*
 * Copyright (c) 2013-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.resource;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import javax.ws.rs.core.Response.Status;
import javax.servlet.http.HttpServletRequest;

import com.emc.storageos.db.client.model.PasswordHistory;
import com.emc.storageos.model.password.PasswordChangeParam;
import com.emc.storageos.security.password.Constants;
import com.emc.storageos.security.password.InvalidLoginManager;
import com.emc.storageos.security.password.NotificationManager;
import com.emc.storageos.security.password.PasswordUtils;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.security.authentication.InternalLogoutClient;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.BasePermissionsHelper;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.systemservices.impl.util.LocalPasswordHandler;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.security.audit.AuditLogManager;

import static com.emc.storageos.model.property.PropertyConstants.ENCRYPTEDSTRING;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.model.password.PasswordResetParam;
import com.emc.storageos.model.password.PasswordUpdateParam;
import com.emc.storageos.model.password.PasswordValidateParam;
import com.emc.storageos.model.password.SSHKeyUpdateParam;
import com.emc.storageos.model.property.PropertiesMetadata;
import com.emc.storageos.model.property.PropertyMetadata;

/**
 * This REST API allows an authenticated local user to:
 * - making request to change the user's own password.
 * - If the authenticated user is with security admin role,
 * making request to set another local user's password.
 */
@Path("/password")
public class PasswordService {
    // Logger reference.
    private static final Logger _logger = LoggerFactory.getLogger(PasswordService.class);
    private static final String EVENT_SERVICE_TYPE = "password";
    private static final String CRYPT_SHA_512 = "$6$";
    private static SimpleDateFormat _format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private AuditLogManager _auditMgr;

    @Autowired
    private InternalLogoutClient internalLogoutClient;

    @Autowired
    private DbClient _dbClient;

    @Autowired
    protected InvalidLoginManager _invLoginManager;

    @Autowired
    private BasePermissionsHelper _permissionsHelper = null;
    @Context
    SecurityContext _sc;

    @Context
    HttpServletRequest _req;

    private LocalPasswordHandler _passwordHandler;

    private PropertiesMetadata _propertiesMetadata;

    private Map<String, StorageOSUser> _localUsers;

    private NotificationManager _notificationManager;

    // Spring injected property.
    public void setPropertiesMetadata(PropertiesMetadata propertiesMetadata) {
        _propertiesMetadata = propertiesMetadata;
    }

    public void setPasswordHandler(LocalPasswordHandler passwordHandler) {
        _passwordHandler = passwordHandler;
    }

    public void setSecurityContext(SecurityContext sc) {
        _sc = sc;
    }

    public void setAuditLogManager(AuditLogManager auditMgr) {
        _auditMgr = auditMgr;
    }

    public void setLocalUsers(Map<String, StorageOSUser> localUsers) {
        _localUsers = localUsers;
    }

    public void setNotificationManager(NotificationManager notificationManager) {
        _notificationManager = notificationManager;
    }

    /**
     * Return the map of the properties metadata.
     * 
     * @return
     */
    private Map<String, PropertyMetadata> getMetaData() {
        return _propertiesMetadata.getGlobalMetadata();
    }

    /**
     * Change an authenticated local user's own password and logs out the user's tokens.
     * This interface accepts a clear test password or a
     * password already hashed by the caller.
     * If both form fields are specified, bad request will be returned.
     * 
     * @brief Change your password
     * @param logout Optional. If set to false, will not logout user sessions.
     * @prereq none
     * @throws APIException
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public Response updatePassword(@Context HttpServletRequest httpRequest,
            @Context HttpServletResponse servletResponse,
            PasswordUpdateParam passwordUpdate,
            @DefaultValue("true") @QueryParam("logout_user") boolean logout) {

        checkPasswordParameter(httpRequest, passwordUpdate, true);

        String username = _sc.getUserPrincipal().getName();
        String clientIP = _invLoginManager.getClientIP(httpRequest);
        _logger.info("update Password for user {}", username);
        setUserPassword(username, passwordUpdate.getPassword(), passwordUpdate.getEncPassword(), false);
        auditPassword(OperationTypeEnum.CHANGE_LOCAL_AUTHUSER_PASSWORD,
                AuditLogManager.AUDITLOG_SUCCESS, null, username);
        _invLoginManager.removeInvalidRecord(clientIP);

        if (logout && !internalLogoutClient.logoutUser(null, _req)) {
            _logger.error("Password changed but unable to logout user active sessions.");
        }

        return Response.ok("Password Changed for " +
                _sc.getUserPrincipal().getName() + "\n").build();
    }

    private void checkPasswordParameter(HttpServletRequest httpRequest,
            PasswordUpdateParam passwordUpdate, boolean bEnableBlock) {
        checkSecurityContext();
        String username = _sc.getUserPrincipal().getName();
        if (!((StorageOSUser) _sc.getUserPrincipal()).isLocal()) {
            throw APIException.forbidden.nonLocalUserNotAllowed();
        }

        String clientIP = _invLoginManager.getClientIP(httpRequest);
        _logger.debug("Client IP: {}", clientIP);
        if (_invLoginManager.isTheClientIPBlocked(clientIP)) {
            _logger.error("The client IP is blocked for too many invalid login attempts: " + clientIP);
            throw APIException.unauthorized.
                    exceedingErrorLoginLimit(_invLoginManager.getMaxAuthnLoginAttemtsCount(),
                            _invLoginManager.getTimeLeftToUnblock(clientIP));
        }

        if (StringUtils.isEmpty(passwordUpdate.getOldPassword())) {
            if (bEnableBlock) {
                _invLoginManager.markErrorLogin(clientIP);
            }
            throw BadRequestException.badRequests.passwordInvalidOldPassword();
        }

        try {
            _passwordHandler.getPasswordUtils().validatePasswordParameter(username, passwordUpdate);
        } catch (BadRequestException badRequestException) {
            if (bEnableBlock && badRequestException.getMessage().contains(_invLoginManager.OLD_PASSWORD_INVALID_ERROR)) {
                _invLoginManager.markErrorLogin(clientIP);
            }
            throw badRequestException;
        }
    }

    /**
     * Change a given local user's password and logs out his auth tokens.
     * The authenticated caller must have SEC_ADMIN role.
     * 
     * @brief Change the password of a given user
     * @param logout Optional. If set to false, will not logout user sessions.
     * @prereq none
     * @throws APIException
     */
    @PUT
    @Path("/reset")
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN })
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public Response updateUserPassword(PasswordResetParam passwordReset, @DefaultValue("true") @QueryParam("logout_user") boolean logout) {
        checkSecurityContext();
        String principal = _sc.getUserPrincipal().getName();

        String username = passwordReset.getUsername();
        if (!_passwordHandler.checkUserExists(username)) {
            throw APIException.badRequests.parameterIsNotValid("username");
        }
        _passwordHandler.getPasswordUtils().validatePasswordParameter(passwordReset);

        _logger.info("reset password for user {}", username);
        setUserPassword(username, passwordReset.getPassword(), passwordReset.getEncPassword(), true);

        if (logout && !internalLogoutClient.
                logoutUser(!principal.equalsIgnoreCase(username) ? username : null, _req)) {
            _logger.error("Password reset but unable to logout user active sessions.");
        }
        auditPassword(OperationTypeEnum.RESET_LOCAL_USER_PASSWORD,
                AuditLogManager.AUDITLOG_SUCCESS, null, principal, username);
        return Response.ok("Password Reset posted by " + principal +
                " for " + username + "\n").build();
    }

    /**
     * Check to see if a proposed password satisfies ViPR's password content rules
     * 
     * The authenticated caller must have SEC_ADMIN role.
     * 
     * @brief Validate a proposed password for a user
     * @prereq none
     * @throws APIException
     */
    @POST
    @Path("/validate")
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN })
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public Response validateUserPassword(PasswordValidateParam passwordParam) {
        checkSecurityContext();
        _passwordHandler.getPasswordUtils().validatePasswordParameter(passwordParam);
        return Response.noContent().build();
    }

    /**
     * Check to see if a proposed password update parameter satisfies ViPR's password content rules
     * 
     * The authenticated caller must be local user to change their own password.
     * 
     * @brief Validate a proposed password for a user by
     * @prereq none
     * @throws APIException
     */
    @POST
    @Path("/validate-update")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public Response validateUserPasswordForUpdate(
            @Context HttpServletRequest httpRequest,
            PasswordUpdateParam passwordParam) {
        checkPasswordParameter(httpRequest, passwordParam, false);
        return Response.noContent().build();
    }

    /**
     * get user's expire time,
     * 
     * for internal use
     */
    @GET
    @Path("/expire")
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN })
    public Response getUserPasswordExpireTime(@QueryParam("username") String username) {
        if (username == null || !_localUsers.containsKey(username)) {
            throw APIException.badRequests.parameterIsNotValid("username");
        }

        PasswordHistory ph = _passwordHandler.getPasswordUtils().getPasswordHistory(username);
        Calendar expireTime = ph.getExpireDate();
        if (expireTime != null) {
            return Response.ok(_format.format(expireTime.getTime())).build();
        } else {
            return Response.ok("no expire time set for the user").build();
        }
    }

    /**
     * update user's expire time, format for expire_time "yyyy-MM-dd HH:mm:ss"
     * 
     * for internal use
     */
    @PUT
    @Path("/expire")
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN })
    public Response setUserPasswordExpireTime(
            @QueryParam("username") String username,
            @QueryParam("expire_time") String expireTime) {
        if (username == null || !_localUsers.containsKey(username)) {
            throw APIException.badRequests.invalidParameter("username", username);
        }

        if (expireTime == null) {
            throw APIException.badRequests.invalidParameter("expire_time", expireTime);
        }

        Date date = null;
        try {
            date = _format.parse(expireTime);
        } catch (ParseException e) {
            throw APIException.badRequests.invalidParameterWithCause("expire_time", expireTime, e);
        }

        PasswordHistory ph = _passwordHandler.getPasswordUtils().getPasswordHistory(username);
        Calendar newExpireTime = Calendar.getInstance();
        newExpireTime.setTime(date);
        ph.setExpireDate(newExpireTime);
        _dbClient.updateAndReindexObject(ph);

        // update system_root_expiry_date / system_svc_expiry_date, if needed
        int daysAfterEpoch = PasswordUtils.getDaysAfterEpoch(newExpireTime);
        if (username.equals("root")) {
            _passwordHandler.updateProperty(Constants.ROOT_EXPIRY_DAYS, String.valueOf(daysAfterEpoch));
        } else if (username.equals("svcuser")) {
            _passwordHandler.updateProperty(Constants.SVCUSER_EXPIRY_DAYS, String.valueOf(daysAfterEpoch));
        }

        return Response.ok("set " + username + "'s password expire time to " + newExpireTime.getTime()).build();
    }

    /**
     * run mail notifier on-demand, it will send mail to users whose password to be expired.
     * 
     * for internal use
     * 
     * @return
     */
    @POST
    @Path("/run-notifier")
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN })
    public Response runMailNotifier() {
        _notificationManager.runMailNotifierNow();
        return Response.ok().build();
    }

    /**
     * internal call to change user's password without login
     */
    @PUT
    @Path("/internal/change-password")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public Response changeUserPassword(PasswordChangeParam passwordChange) {
        checkPasswordParameter(passwordChange);

        String username = passwordChange.getUsername();
        _logger.info("change password for user {}", username);
        setUserPassword(username, passwordChange.getPassword(), null, false);

        auditPassword(OperationTypeEnum.CHANGE_LOCAL_AUTHUSER_PASSWORD,
                AuditLogManager.AUDITLOG_SUCCESS, null, null, username);
        return Response.ok("Password changed for " + username).build();
    }

    private void checkPasswordParameter(PasswordChangeParam passwordChange) {
        String username = passwordChange.getUsername();
        if (!_passwordHandler.checkUserExists(username)) {
            throw APIException.badRequests.parameterIsNotValid("username");
        }

        if (passwordChange.getOldPassword() == null || passwordChange.getOldPassword().isEmpty()) {
            throw BadRequestException.badRequests.passwordInvalidOldPassword();
        }

        _passwordHandler.getPasswordUtils().validatePasswordParameter(passwordChange);
    }

    /**
     * Check to see if a proposed password update parameter satisfies ViPR's password content rules
     * 
     * The authenticated caller must be local user to change their own password.
     * 
     * @brief Validate a proposed password for a user by
     * @prereq none
     * @throws APIException
     */
    @POST
    @Path("/internal/validate-change")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public Response validateUserPasswordForChange(
            PasswordChangeParam passwordParam) {
        checkPasswordParameter(passwordParam);
        return Response.noContent().build();
    }

    /**
     * Called by update password methods to set user's password
     * 
     * @param username
     * @param passwd
     * @param encpasswd
     */

    private void setUserPassword(String username, String passwd, String encpasswd, boolean bReset) {

        PropertyMetadata metaData = null;
        try {
            final String key = "system_" + username + "_encpassword";
            Map<String, PropertyMetadata> metadataMap = getMetaData();
            metaData = metadataMap.get(key);

        } catch (Exception e) {
            _logger.error("resetPassword", e);
            throw APIException.internalServerErrors.updateObjectError("password", e);
        }

        if (metaData == null) {
            throw APIException.badRequests.parameterIsNotValid("username");
        }

        if (ENCRYPTEDSTRING.equalsIgnoreCase(metaData.getType())) {
            _passwordHandler.setUserEncryptedPassword(username, passwd, bReset);
        } else if (passwd != null && !passwd.isEmpty()) {
            _passwordHandler.setUserPassword(username, passwd, bReset);
        } else {
            _passwordHandler.setUserHashedPassword(username, encpasswd, bReset);
        }
    }

    /**
     * Change an authenticated local user's SSH authorizedkey2.
     * This interface accepts the user's SSH authorizedkey2
     * 
     * @brief Change SSH authorizedkey2 of local user
     * @prereq none
     * @throws APIException
     */
    @PUT
    @Path("/authorizedkey2")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public Response updateAuthorizedkey2(SSHKeyUpdateParam sshkey) {
        checkSecurityContext();
        String authorizedkey2 = sshkey.getSshKey();
        if (!((StorageOSUser) _sc.getUserPrincipal()).isLocal()) {
            throw APIException.forbidden.nonLocalUserNotAllowed();
        } else {

            if ((authorizedkey2 == null) || authorizedkey2.isEmpty()) {
                throw new WebApplicationException(Response.status(Status.BAD_REQUEST).
                        entity("Bad form paramters\n").build());
            }

            String username = _sc.getUserPrincipal().getName();
            _logger.info("update authorizedkey2 for user {}", username);

            try {
                _passwordHandler.setUserAuthorizedkey2(username, authorizedkey2);
            } catch (Exception e) {
                _logger.error("updateAuthorizedkey2", e);
                throw APIException.internalServerErrors.updateObjectError("authorized key", e);
            }

            auditPassword(OperationTypeEnum.CHANGE_LOCAL_AUTHUSER_AUTHKEY,
                    AuditLogManager.AUDITLOG_SUCCESS, null, username);
            return Response.ok("Authorized Key Changed for = " +
                    _sc.getUserPrincipal().getName() + "\n").build();
        }
    }

    private void checkSecurityContext() {
        if (_sc == null || _sc.getUserPrincipal() == null) {
            throw APIException.forbidden.invalidSecurityContext();
        }
    }

    /**
     * Record audit log for password service
     * 
     * @param auditType Type of AuditLog
     * @param operationalStatus Status of operation
     * @param description Description for the AuditLog
     * @param descparams Description paramters
     */
    public void auditPassword(OperationTypeEnum auditType,
            String operationalStatus,
            String description,
            Object... descparams) {

        _auditMgr.recordAuditLog(null, null,
                EVENT_SERVICE_TYPE,
                auditType,
                System.currentTimeMillis(),
                operationalStatus,
                description,
                descparams);
    }
}
