/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import models.security.UserInfo;

import org.apache.commons.collections.map.MultiKeyMap;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpStatus;

import play.Logger;
import play.Play;
import play.cache.Cache;
import play.data.validation.Validation;
import play.exceptions.UnexpectedException;
import play.mvc.Before;
import play.mvc.Catch;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Router;
import play.mvc.Util;
import play.mvc.With;
import util.BourneUtil;
import util.DisasterRecoveryUtils;
import util.LicenseUtils;
import util.MessagesUtils;
import util.VirtualDataCenterUtils;

import com.emc.storageos.model.vdc.VirtualDataCenterRestRep;
import com.emc.storageos.services.util.SecurityUtils;
import com.emc.vipr.client.exceptions.ViPRHttpException;
import com.emc.vipr.model.sys.ClusterInfo;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import controllers.deadbolt.Deadbolt;
import controllers.deadbolt.Unrestricted;
import controllers.security.Security;
import controllers.util.FlashException;

/**
 * Common controller interceptor to add some renderArgs used
 * in the main layout. This should be used @With(Common.class)
 * in all controllers that render using the main layout.
 * 
 * This also contains some utilities to provide access to user and other
 * data from controllers.
 * 
 * @author Chris Dail
 */
@With(Deadbolt.class)
public class Common extends Controller {
    // Common Arg constants
    public static final String USER = "currentUser";
    public static final String VDCS = "vdcs";
    public static final String TOKEN = "token";
    public static final String AUTHENTICITY_TOKEN = "authenticityToken";
    public static final String NOTIFICATIONS = "notifications";
    public static final String REFERRER = "referrer";
    public static final String ANGULAR_RENDER_ARGS = "angularRenderArgs";

    public static final String CACHE_EXPR = "2min";

    public static final String PATH_SANITIZER = "pathSanitizer";
    private static final MultiKeyMap XSS_SANITIZERS = new MultiKeyMap() {
        {
            put("/orders/submitOrder","mountPoint", PATH_SANITIZER);
            put("/orders/submitOrder","mountPath", PATH_SANITIZER);

            put("/ldap/save","ldapSources.managerDn", PATH_SANITIZER);
            put("/usergroup/save","userGroup.name", PATH_SANITIZER);

            put("/config/passwords","user", PATH_SANITIZER);
            put("/customConfigs/preview", "value", PATH_SANITIZER);
        }
    };

    @Before(priority = 0)
    @Unrestricted
    public static void checkSetup() {
        if (StringUtils.isNotBlank(Security.getAuthToken())) {
            if (!Setup.isInitialSetupComplete()) {
                if (Security.isApiRequest()) {
                    error(MessagesUtils.get("setup.notLicensed.message"));
                }
                Logger.info("Running Setup ...");
                Setup.index();
            }
            else if (!LicenseUtils.isLicensed(true)) {
                if (Security.isApiRequest()) {
                    error(MessagesUtils.get("setup.notLicensed.message"));
                }
                Logger.info("Not licensed");
                Setup.license();
            }
        }
    }

    @Before(priority = 0)
    public static void csrfCheck() {
        boolean isPost = StringUtils.equals(request.method, "POST");
        boolean isFormEncoded = StringUtils.equals(request.contentType, "application/x-www-form-urlencoded");
        boolean isApiRequest = StringUtils.startsWith(request.path, "/api/");
        if (isPost && isFormEncoded && !isApiRequest) {
            String authenticityToken = SecurityUtils.stripXSS(params.get("authenticityToken"));
            if (authenticityToken == null) {
                Logger.warn("No authenticity token from %s for request: %s", request.remoteAddress, request.url);
            }
            checkAuthenticity();
        }
    }

    @Before(priority = 0)
    public static void xssCheck() {
        for (String param : params.all().keySet()) {
            // skip xss sanitation for fields which name contains password
            if (param.toLowerCase().contains("password") || param.toLowerCase().contains("username")) {
                Logger.debug("skip sanitation for " + param);
                return;
            }

            String[] data = params.getAll(param);
            if ((data != null) && (data.length > 0)) {
                String sanitizer = (String)XSS_SANITIZERS.get(request.path, param);
                Logger.debug("Cleaning data for [ %s ] [ %s ]", request.path, param);
                String[] cleanValues = new String[data.length];
                for (int i = 0; i < data.length; ++i) {
                    if (PATH_SANITIZER.equals(sanitizer)){
                        cleanValues[i] = SecurityUtils.stripPathXSS(data[i]);
                    } else {
                        cleanValues[i] = SecurityUtils.stripXSS(data[i]);
                    }
                    params.put(param, cleanValues);
                }
            }
        }
    }

    @Before(priority = 5)
    public static void addCommonRenderArgs() {
        // Set cache control. We don't want caching of our dynamic pages
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        UserInfo userInfo = Security.getUserInfo();

        angularRenderArgs().put(USER, userInfo);
        angularRenderArgs().put(AUTHENTICITY_TOKEN, SecurityUtils.stripXSS(session.getAuthenticityToken()));
        renderArgs.put(USER, userInfo);
        renderArgs.put(TOKEN, Security.getAuthToken());
        renderArgs.put(VDCS, getVDCs());

        // Notifications are only shown for tenant approvers
        if (Security.isTenantApprover() && DisasterRecoveryUtils.isActiveSite()) {
            renderArgs.put(NOTIFICATIONS, Notifications.getNotifications());
        }
        addReferrer();
    }

    @Util
    public static Map<String, Object> angularRenderArgs() {
        @SuppressWarnings("unchecked")
        Map<String, Object> scope = (Map<String, Object>) renderArgs.get(ANGULAR_RENDER_ARGS);

        if (scope == null) {
            scope = new HashMap<String, Object>();
            renderArgs.put(ANGULAR_RENDER_ARGS, scope);
        }

        return scope;
    }

    @Catch(ViPRHttpException.class)
    public static void jerseyException(Throwable e) {
        handleExpiredToken(e);
    }

    /**
     * Extremely low priority exception handler that will automatically
     * flashException and redirect for action methods decorated with a
     * FlashException annotation. Rethrow if the annotation is
     * absent.
     */
    @Catch(value = Exception.class, priority = Integer.MAX_VALUE)
    public static void flashExceptionHandler(Exception e) throws Exception {
        FlashException handler = getActionAnnotation(FlashException.class);
        if (handler != null) {
            flashException(e);
            if (handler.keep()) {
                params.flash();
                Validation.keep();
            }

            String action = handler.value();
            String[] referrer = handler.referrer();
            if (!action.isEmpty()) {
                if (!action.contains(".")) {
                    action = getControllerClass().getName() + "." + action;
                }
                redirect(action);
            } else if (referrer != null && referrer.length > 0) {
                Http.Header headerReferrer = request.headers.get("referer");
                if (headerReferrer != null && StringUtils.isNotBlank(headerReferrer.value())) {
                    Pattern p = Pattern.compile(StringUtils.join(referrer, "|"), Pattern.CASE_INSENSITIVE);
                    Matcher m = p.matcher(headerReferrer.value());
                    if (m.find()) {
                        redirectToReferrer();
                    } else {
                        Logger.error(String.format("The redirect page is not valid base on the FlashException referrer restriction: %s",
                                referrer.toString()));
                    }
                } else {
                    Logger.error("Unable to redirect. No referrer available in request header");
                }
            } else {
                redirectToReferrer();
            }
        }
    }

    /**
     * Redirects back to the request header referrer
     * with params.flash() and Validation.keep().
     */
    @Util
    public static void handleError() {
        params.flash();
        Validation.keep();
        redirectToReferrer();
    }

    private static void redirectToReferrer() {
        Http.Header referrer = request.headers.get("referer");
        if (referrer != null && StringUtils.isNotBlank(referrer.value())) {
            redirect(referrer.value());
        } else {
            String msg = "Unable to redirect. No referrer available in request header";
            Logger.error(msg);
            throw new RuntimeException(msg);
        }
    }

    @Util
    public static void handleExpiredToken(Throwable throwable) {
        if (throwable instanceof ViPRHttpException) {
            ViPRHttpException ve = (ViPRHttpException) throwable;
            if (ve.getHttpCode() == HttpStatus.SC_UNAUTHORIZED) {
                Logger.info("Clearing auth token");
                // Auth token may have expired
                Security.clearAuthToken();
                Security.redirectToAuthPage();
            }
        }
    }

    @Util
    public static boolean hasRequestMethod(String... methods) {
        return Sets.newHashSet(methods).contains(request.actionMethod);
    }

    /**
     * Gets a user printable message for an exception.
     * 
     * @param throwable Exception to get the message for
     */
    @Util
    public static String getUserMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null) {
            message = throwable.getClass().getName();
        }
        return message;
    }

    /**
     * This method captures the user printable message for an exception. It logs it to the log file and
     * adds the message to the flash error for display. For AJAX requests, the message is rendered as an error.
     * 
     * @param throwable Exception to get the message for
     */
    @Util
    public static void flashException(Throwable throwable) {
        // Check for logout
        handleExpiredToken(throwable);

        String message = getUserMessage(throwable);
        Logger.error(throwable, message);
        if (request.isAjax()) {
            error(503, message);
        }
        flash.error(MessagesUtils.escape(message));
    }

    /**
     * Gets the referrer URL. If there is a flash scope value, it takes precedence over a query parameter.
     * 
     * @return the referrer URL.
     */
    @Util
    public static String getReferrer() {
        String referrerFlash = flash.get(REFERRER);
        String referrerParam = params.get(REFERRER);
        String referrer = null;
        if (StringUtils.isNotBlank(referrerFlash)) {
            referrer = referrerFlash;
        }
        else if (StringUtils.isNotBlank(referrerParam)) {
            referrer = referrerParam;
        }

        if (referrer != null) {
            return toSafeRedirectURL(referrer);
        }
        else {
            return null;
        }
    }

    @Util
    public static String toSafeRedirectURL(String url) {
        String cleanUrl = "";
        try {
            // Remove Host and port from referrer
            URI uriObject = new URI(url);
            cleanUrl += uriObject.getPath();

            String query = uriObject.getQuery();
            if (!StringUtils.isBlank(query)) {
                cleanUrl += "?" + query;
            }
        } catch (URISyntaxException ignore) {
            Logger.error(ignore.getMessage());
        }

        return cleanUrl;
    }

    /**
     * Sets the referrer URL into the flash scope before a redirect.
     * 
     * @param url the referrer URL.
     */
    @Util
    public static void setReferrer(String url) {
        if (StringUtils.isNotBlank(url)) {
            flash.put(REFERRER, url);
        }
    }

    /**
     * Adds the current referrer to the render args.
     */
    @Util
    public static void addReferrer() {
        String referrer = getReferrer();
        if (StringUtils.isNotBlank(referrer)) {
            renderArgs.put(REFERRER, referrer);
        }
    }

    /**
     * Redirects back to the referrer, if set.
     */
    @Util
    public static void backToReferrer() {
        String referrer = getReferrer();
        if (StringUtils.isNotBlank(referrer)) {
            redirect(referrer);
        }
    }

    @Util
    public static String reverseRoute(Class<? extends Controller> controllerClass, String action) {
        return reverseRoute(controllerClass.getName() + "." + action);
    }

    @Util
    public static String reverseRoute(Class<? extends Controller> controllerClass, String action, String key,
            Object value) {
        return reverseRoute(controllerClass.getName() + "." + action, key, value);
    }

    @Util
    public static String reverseRoute(Class<? extends Controller> controllerClass, String action,
            Map<String, Object> args) {
        return reverseRoute(controllerClass.getName() + "." + action, args);
    }

    private static String reverseRoute(String action, String key, Object value) {
        Map<String, Object> args = Maps.newHashMap();
        args.put(key, value);
        return reverseRoute(action, args);
    }

    private static String reverseRoute(String action, Map<String, Object> args) {
        return Router.reverse(action, args).url;
    }

    private static String reverseRoute(String action) {
        return Router.reverse(action).url;
    }

    @Util
    public static ClusterInfo getClusterInfo() {
        ClusterInfo clusterInfo = null;
        try {
            clusterInfo = BourneUtil.getSysClient().upgrade().getClusterInfo();
        } catch (ViPRHttpException e) {
            Logger.error(e, "Failed to get cluster state");
            error(e.getHttpCode(), e.getMessage());
        }
        return clusterInfo;
    }

    /**
     * Gets the clusterInfo if the user has access
     * 
     * @return the cluster info, or null
     */
    @Util
    public static ClusterInfo getClusterInfoWithRoleCheck() {
        if (Security.isTenantAdmin() || Security.isSystemMonitor()
                || Security.isSystemAdminOrRestrictedSystemAdmin()
                || Security.isSecurityAdminOrRestrictedSecurityAdmin()) {
            return getClusterInfo();
        } else {
            return null;
        }
    }

    public static void clusterInfoWithRoleCheckJson() {
        renderJSON(getClusterInfoWithRoleCheck());
    }

    /**
     * Determines if the cluster is in a stable state.
     * 
     * @return
     */
    @Util
    public static boolean isClusterStable() {
        if (Play.mode.isDev()) {
            return true;
        }
        return StringUtils.equalsIgnoreCase("STABLE", getClusterInfo().getCurrentState());
    }

    @Util
    public static List<VirtualDataCenterRestRep> getVDCs() {
        List<VirtualDataCenterRestRep> vdcs = Cache.get(VDCS, List.class);
        if (vdcs == null) {
            try {
                vdcs = VirtualDataCenterUtils.getAllVDCs();
            }
            // If there is an exception, return an empty list. We don't want an exception getting VDCS to prevent all
            // pages from loading
            catch (Exception e) {
                Logger.error(e, "Unable to retrieve VDCs");
                return Lists.newArrayList();
            }
            Cache.set(VDCS, vdcs, CACHE_EXPR);
        }
        return vdcs;
    }

    @Util
    public static Throwable unwrap(Throwable t) {
        Throwable cause = t;
        while ((cause instanceof UnexpectedException) || (cause instanceof ExecutionException)) {
            cause = cause.getCause();
        }
        return cause != null ? cause : t;
    }

    @Util
    public static void copyRenderArgsToAngular() {
        copyRenderArgsToAngular(USER, NOTIFICATIONS, TOKEN, VDCS);
    }

    @Util
    public static void copyRenderArgsToAngular(String... exclude) {
        angularRenderArgs().putAll(renderArgs.data);
        angularRenderArgs().remove(ANGULAR_RENDER_ARGS);
        angularRenderArgs().keySet().removeAll(Arrays.asList(exclude));
    }

    @Util
    public static void flashParamsExcept(String... paramNames) {
        Set<String> names = Sets.newHashSet(params.all().keySet());
        names.removeAll(Arrays.asList(paramNames));
        String[] array = new String[names.size()];
        params.flash(names.toArray(array));
    }

    public static void redirectTo(String action) {
        redirect(action);
    }
}
