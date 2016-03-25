/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.security;

import com.emc.vipr.client.exceptions.ViPRHttpException;
import com.google.common.collect.Lists;

import controllers.Common;
import controllers.Dashboard;
import controllers.Maintenance;
import controllers.deadbolt.ExternalizedRestrictionsAccessor;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.RestrictedResourcesHandler;
import controllers.deadbolt.Restrictions;
import models.deadbolt.Role;
import models.deadbolt.RoleHolder;

import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpStatus;

import play.Logger;
import play.mvc.Controller;
import play.mvc.Http;
import util.MessagesUtils;

import java.util.Collection;
import java.util.List;

/**
 * Handler for authorization through the deadbolt module.
 * 
 * @author Chris Dail
 */
public class StorageOSDeadboltHandler extends Controller implements controllers.deadbolt.DeadboltHandler {

    @Override
    public void beforeRoleCheck() {
        if (Security.getAuthToken() == null) {
            // Check to see if we are redirected from the auth page.
            if (request.params._contains("auth-redirected")) {
                Security.noCookies();
            }
            Security.redirectToAuthPage();
        }

        try {
            Security.getUserInfo();
        } catch (ViPRHttpException e) {
            // NGinx Throws a 503 if the API service is unavailable
            if (e.getHttpCode() == HttpStatus.SC_BAD_GATEWAY || e.getHttpCode() == HttpStatus.SC_SERVICE_UNAVAILABLE) {
                Maintenance.maintenance(Common.reverseRoute(Dashboard.class, "index"), null);
            }
            else {
                Logger.info("Error retrieving user info. Session may have expired. CODE: %s, REASON: %s", e.getHttpCode(), e.getMessage());
                if (e.getHttpCode() == HttpStatus.SC_UNAUTHORIZED) {
                    Logger.info("Clearing auth token");
                    // Auth token may have expired
                    Security.clearAuthToken();
                }
                Security.redirectToAuthPage();
            }
        } catch (Exception e) {
            Logger.warn(e, "Error retrieving user info. Session may have expired");
            Security.clearAuthToken();
            Security.redirectToAuthPage();
        }
    }

    public RoleHolder getRoleHolder() {
        return Security.getUserInfo();
    }

    public void onAccessFailure(String controllerClassName) {
        String user = Security.getUserInfo().getCommonName();
        List<String> roleSets = getRoleSetsFromController();
        Collection<? extends Role> computedRoles = Security.getUserInfo().getRoles();
        String path = Http.Request.current().path;

        Logger.info("User '%s' with roles (%s) does not have one of the required roles (%s) for %s", user, computedRoles, roleSets, path);
        forbidden(MessagesUtils.get("StorageOSDeadboltHandler.requiredRoles", roleSets, computedRoles));
    }

    public ExternalizedRestrictionsAccessor getExternalizedRestrictionsAccessor() {
        return null;
    }

    public RestrictedResourcesHandler getRestrictedResourcesHandler() {
        return null;
    }

    /*
     * Query the roles required by the deadbolt annotations. This is used for logging purposes to show what the controller
     * requires.
     */
    private static List<String> getRoleSetsFromController() {
        List<String> roleSets = Lists.newArrayList();
        Restrictions restrictions = getActionAnnotation(Restrictions.class);
        if (restrictions == null) {
            restrictions = getControllerInheritedAnnotation(Restrictions.class);
        }

        if (restrictions != null) {
            Restrict[] restrictArray = restrictions.value();
            for (int i = 0; i < restrictArray.length; i++) {
                roleSets.add(StringUtils.join(restrictArray[i].value(), " & "));
            }
        }

        Restrict restrict = getActionAnnotation(Restrict.class);
        if (restrict == null) {
            restrict = getControllerInheritedAnnotation(Restrict.class);
        }

        if (restrict != null) {
            roleSets.add(StringUtils.join(restrict.value(), " & "));
        }

        return roleSets;
    }
}
