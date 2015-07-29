/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.security;

import controllers.deadbolt.*;
import models.deadbolt.RoleHolder;
import play.mvc.Controller;
import play.mvc.Util;
import util.BourneUtil;

import static com.emc.vipr.client.impl.Constants.AUTH_TOKEN_KEY;

/**
 * Dummy deadbolt handler to fake authentication if Bourne Auth SVC is not running locally.
 * 
 * @author Chris Dail
 */
public class DummyDeadboltHandler extends Controller implements DeadboltHandler {

    @Override
    public void beforeRoleCheck() {
        // Ensure the cookie is still good
        try {
            Security.getUserInfo();
        } catch (Exception e) {
            removeResponseCookie(AUTH_TOKEN_KEY);
            removeRequestCookie(AUTH_TOKEN_KEY);
        }

        if (Security.getAuthToken() == null) {
            // Allow the username/password to be overridden for testing
            String username = System.getProperty("viprUsername", "root");
            String password = System.getProperty("viprPassword", "Changeme1!");

            String token = BourneUtil.getViprClient().auth().login(username, password);
            response.setCookie(AUTH_TOKEN_KEY, token, "14d");
            // This won't be in the current request. Fake it so the auth token is picked up by the security module
            request.cookies.put(AUTH_TOKEN_KEY, response.cookies.get(AUTH_TOKEN_KEY));
        }
    }

    public RoleHolder getRoleHolder() {
        return Security.getUserInfo();
    }

    public void onAccessFailure(String controllerClassName) {
        forbidden();
    }

    public ExternalizedRestrictionsAccessor getExternalizedRestrictionsAccessor() {
        return null;
    }

    public RestrictedResourcesHandler getRestrictedResourcesHandler() {
        return null;
    }

    /***
     * Removes the session cookie from the response by
     * setting the cookie value with "" and path with "/".
     * We could have used play framework's Http.Response.removeCookie()
     * only but the reason for not using that is,
     * Http.Response.removeCookie() sets the HttpOnly and secure
     * attributes of the cookie to false and that could lead to
     * XSS.
     * 
     * @param name of the cookie to be removed from the response.
     */
    @Util
    private static void removeResponseCookie(String name) {
        response.setCookie(name, "", null, "/", 0, true, true);
    }

    /***
     * Removes the session cookie from the request by
     * setting the cookie value with "" and path with "/".
     * 
     * @param name of the cookie to be removed from the request.
     */
    @Util
    private static void removeRequestCookie(String name) {
        request.cookies.remove(name);
    }
}
