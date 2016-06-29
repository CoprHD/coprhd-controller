/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.security;

import com.emc.storageos.auth.saml.SAMLUtil;
import com.emc.vipr.client.exceptions.ViPRHttpException;
import com.google.common.collect.Lists;
import controllers.deadbolt.Deadbolt;
import models.security.UserInfo;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpStatus;
import play.Logger;
import play.Play;
import play.cache.Cache;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Util;
import util.BourneUtil;
import util.MessagesUtils;

import java.net.URLEncoder;
import java.util.List;

import static com.emc.vipr.client.impl.Constants.AUTH_PORTAL_TOKEN_KEY;
import static com.emc.vipr.client.impl.Constants.AUTH_TOKEN_KEY;
import static util.BourneUtil.getViprClient;

/**
 * @author Chris Dail
 */
public class Security extends Controller {
    public static final String SYSTEM_AUDITOR = "SYSTEM_AUDITOR";
    public static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";
    public static final String SYSTEM_MONITOR = "SYSTEM_MONITOR";
    public static final String TENANT_ADMIN = "TENANT_ADMIN";
    public static final String TENANT_APPROVER = "TENANT_APPROVER";
    public static final String SECURITY_ADMIN = "SECURITY_ADMIN";
    public static final String PROJECT_ADMIN = "PROJECT_ADMIN";
    public static final String RESTRICTED_SYSTEM_ADMIN = "RESTRICTED_SYSTEM_ADMIN";
    public static final String RESTRICTED_SECURITY_ADMIN = "RESTRICTED_SECURITY_ADMIN";

    // These are Portal only Roles!
    // Members who have admin over any tenant will get the TENANT_ADMIN role,
    // but some actions are limited to the admin of the root tenant, or
    // someone who has admin on the tenant that they belong to,
    public static final String ROOT_TENANT_ADMIN = "ROOT_TENANT_ADMIN";
    public static String HOME_TENANT_ADMIN = "HOME_TENANT_ADMIN";

    public static final String CACHE_EXPR = "1min";

    @Util
    public static UserInfo getUserInfo() {
        String key = getUserInfoCacheKey();
        UserInfo user = (UserInfo) Cache.get(key);
        if (user == null) {
            user = new UserInfo(getViprClient().getUserInfo());
            Cache.set(key, user, CACHE_EXPR);
        }
        return user;
    }

    @Util
    public static void clearUserInfo() {
        Cache.delete(getUserInfoCacheKey());
    }

    @Util
    private static String getUserInfoCacheKey() {
        return "userInfo." + getAuthToken();
    }

    @Util
    public static boolean isApiRequest() {
        return request != null && ("json".equalsIgnoreCase(request.format) || "xml".equalsIgnoreCase(request.format));
    }

    @Util
    public static String getAuthToken() {
        // Look in cookies
        Http.Cookie cookie = request.cookies.get(AUTH_PORTAL_TOKEN_KEY);
        if (cookie != null) {
            return cookie.value;
        }
        cookie = request.cookies.get(AUTH_TOKEN_KEY);
        if (cookie != null) {
            return cookie.value;
        }

        // Look In Headers
        Http.Header header = request.headers.get(AUTH_PORTAL_TOKEN_KEY.toLowerCase());
        if (header != null) {
            return header.value();
        }

        header = request.headers.get(AUTH_TOKEN_KEY.toLowerCase());
        if (header != null) {
            return header.value();
        }

        return null;
    }

    @Util
    public static String getProxyAuthToken() {
        String authToken = getAuthToken();
        if (authToken == null) {
            return null;
        }
        return BourneUtil.getViprClient().auth().proxyToken();
    }

    @Util
    public static boolean isRootTenantAdmin() {
        return hasRoles(ROOT_TENANT_ADMIN);
    }

    @Util
    public static boolean isHomeTenantAdmin() {
        return hasRoles(HOME_TENANT_ADMIN);
    }

    @Util
    public static boolean isSystemAdmin() {
        return hasRoles(SYSTEM_ADMIN);
    }

    @Util
    public static boolean isSystemAuditor() {
        return hasRoles(SYSTEM_AUDITOR);
    }

    @Util
    public static boolean isSystemMonitor() {
        return hasRoles(SYSTEM_MONITOR);
    }

    @Util
    public static boolean isTenantAdmin() {
        return hasRoles(TENANT_ADMIN);
    }

    @Util
    public static boolean isTenantApprover() {
        return hasRoles(TENANT_APPROVER);
    }

    @Util
    public static boolean isProjectAdmin() {
        return hasRoles(PROJECT_ADMIN);
    }

    @Util
    public static boolean isSecurityAdmin() {
        return hasRoles(SECURITY_ADMIN);
    }

    @Util
    public static boolean isRestrictedSecurityAdmin() {
        return hasRoles(RESTRICTED_SECURITY_ADMIN);
    }

    @Util
    public static boolean isRestrictedSystemAdmin() {
        return hasRoles(RESTRICTED_SYSTEM_ADMIN);
    }

    @Util
    public static boolean isSecurityAdminOrRestrictedSecurityAdmin() {
        return isSecurityAdmin() || isRestrictedSecurityAdmin();
    }

    @Util
    public static boolean isLocalUser() {
        return !getUserInfo().getCommonName().contains("@");
    }

    @Util
    public static boolean isSystemAdminOrRestrictedSystemAdmin() {
        return isSystemAdmin() || isRestrictedSystemAdmin();
    }

    @Util
    public static void clearAuthToken() {
        clearUserInfo();
        removeResponseCookie(AUTH_PORTAL_TOKEN_KEY);
        removeResponseCookie(AUTH_TOKEN_KEY);
    }

    @Util
    static void clearSession() {
        session.clear();
    }

    /**
     * Returns true if user has all of the roles specified.
     */
    public static boolean hasRoles(String... roles) {
        return hasRoles(Lists.newArrayList(roles));
    }

    public static boolean hasOneOfRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return true;
        }
        for (String role : roles) {
            if (hasRoles(role)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if the user has any of the roles specified.
     * 
     * @param roles
     *            the roles to check.
     * @return true if the user has any of the roles.
     */
    public static boolean hasAnyRole(String... roles) {
        for (String role : roles) {
            if (hasRoles(role)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasRoles(List<String> roles) {
        try {
            return Deadbolt.hasRoles(roles);
        }
        // I'm not sure why deadbolt throws this
        catch (Throwable t) { // NOSONAR
                              // ("Suppressing Sonar violation Catch Exception instead of Throwable as the above method throws Throwable ")
            throw new RuntimeException(t);
        }
    }

    // This is a special login page to display the no-cookies error.
    // If there are actually no cookies, we cannot take advantage of the flash scope on redirect because it uses cookies
    public static void noCookies() {
        flash.error(MessagesUtils.get("security.noCookies"));
        render("@nologin");
    }

    // Sign out
    public static void logout() {
        try {
            getViprClient().auth().logout();
        } catch (Exception e) {
            Logger.warn(e, "Error logging out");
        }
        clearAuthToken();
        clearSession();
        render("@nologin");
    }

    /**
     * AJAXable method that can be used to determine if the user is authenticated.
     */
    public static void authenticated() {
        if (StringUtils.isBlank(getAuthToken())) {
            renderJSON(false);
        }
        try {
            UserInfo user = getUserInfo();
            renderJSON(user != null);
        } catch (ViPRHttpException e) {
            Logger.error(e, "HTTP Error: %s %s", e.getHttpCode(), e.getMessage());
            if (e.getHttpCode() == HttpStatus.SC_UNAUTHORIZED) {
                renderJSON(false);
            }
            // Propogate other errors (502, 503 most importantly)
            error(e.getHttpCode(), e.getMessage());
        } catch (Exception e) {
            Logger.error(e, "Error getting user info");
            renderJSON("error");
        }
    }

    @Util
    public static void redirectToAuthPage() {
        if (Security.isApiRequest()) {
            // Redirecting to the apisvc login page will fail in most browsers due to the same origin policy.
            // Return a 401 and let the client handle it.
            error(401, "Unauthorized");
        }
        else {
            try {
                String base = request.getBase();
                String path = "GET".equalsIgnoreCase(request.method) ? request.url : Play.ctxPath + "/";
                String service = URLEncoder.encode(base + path, "UTF-8");
                String authSvcPort = Play.configuration.getProperty("authsvc.port");
                String url = null;
                // if parameters contain "using-idp", re-direct to Idp
                if (request.params._contains("using-idp")) {
                    url = SAMLUtil.generateSAMLRequest();
                    //url = String.format("http://lglw9040.lss.emc.com:8080/openam/SSORedirect/metaAlias/idp?SAMLRequest=%s", samlrequest);
                    Logger.info("Redirecting to IDP login page %s", url);
                } else {
                    url = String.format("https://%s:%s/formlogin?service=%s&src=portal", request.domain, authSvcPort, service);
                    Logger.info("No cookie detected. Redirecting to login page %s", url);
                }
                redirect(url);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
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
}
