/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.auth;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.NewCookie;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.emc.storageos.model.auth.AuthnUpdateParam;
import com.emc.storageos.model.auth.RoleAssignmentChanges;
import com.emc.storageos.model.auth.RoleAssignmentEntry;
import com.emc.storageos.model.tenant.TenantResponse;
import com.sun.jersey.api.client.*;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.client.filter.ClientFilter;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.api.client.filter.LoggingFilter;
import com.emc.storageos.api.service.ApiTestBase;
import com.emc.storageos.model.password.PasswordResetParam;
import com.emc.storageos.services.util.EnvConfig;

/**
 * Basic unit tests for authentication service
 */
public class AuthSvcTests extends ApiTestBase {
    private static final String PASSWORD = EnvConfig.get("sanity", "authsvc.AuthSvcTests.password");
    private static final String USER_NAME = EnvConfig.get("sanity", "authsvc.AuthSvcTests.username");
    public static String LOCATION_HEADER = "Location";
    protected static String baseApiServiceURL;
    protected static String baseAuthServiceURL;
    protected static boolean runLongTests = false;
    protected static boolean runProxyTokenTests = false;
    protected static int timeToWaitInMinutes = 1; // 1 minute
    protected static boolean initDone = false;

    /**
     * Conveninence method to create a Client, and add authentication info
     * if desired. If addAuthFilter is set to true, credentials will be added
     * to a Basic Auth filter, and 302 will be followed manually, adding the auth token
     * on the final redirect to the service location. If addAuthFilter is set to
     * false, a regular 302 follow up will be done, no headers or basic auth will be
     * added.
     * 
     * @throws NoSuchAlgorithmException
     */
    protected Client createHttpsClient(final String username, final String password,
            boolean addAuthFilters) throws NoSuchAlgorithmException {

        // Disable server certificate validation as we are using
        // self-signed certificate
        disableCertificateValidation();
        final ClientConfig config = new DefaultClientConfig();

        final Client c = Client.create(config);
        c.addFilter(new LoggingFilter());
        if (addAuthFilters) {
            c.setFollowRedirects(false); // do a "modified 302" below with copying the header
            c.addFilter(new HTTPBasicAuthFilter(username, password));
            c.addFilter(new ClientFilter() {
                @Override
                public ClientResponse handle(ClientRequest request) throws ClientHandlerException {
                    if (_savedTokens.containsKey(username)) {
                        ArrayList<Object> token = new ArrayList<Object>();
                        token.add(_savedTokens.get(username));
                        request.getHeaders().put(AUTH_TOKEN_HEADER, token);
                    }
                    ClientResponse response = getNext().handle(request);
                    if (response.getHeaders() != null && response.getHeaders().get(AUTH_TOKEN_HEADER) != null) {
                        _savedTokens.put(username, response.getHeaders().getFirst(AUTH_TOKEN_HEADER));
                    }
                    if (response.getHeaders() != null
                            && response.getHeaders().get(AUTH_PROXY_TOKEN_HEADER) != null) {
                        _savedProxyTokens.put(username,
                                response.getHeaders().getFirst(AUTH_PROXY_TOKEN_HEADER));
                    }
                    if (response.getStatus() == 302) {
                        WebResource wb = c.resource(response.getLocation());
                        response = wb.header(AUTH_TOKEN_HEADER, _savedTokens.get(username)).
                                get(ClientResponse.class);
                    }
                    return response;
                }
            });
        } else {
            // no auth filter, and do a regular 302 follow up, don't copy any auth token.
            c.setFollowRedirects(true);
        }
        return c;
    }

    protected Client createCookieHttpsClient(final String username, final String password)
            throws NoSuchAlgorithmException {

        // Disable server certificate validation as we are using
        // self-signed certificate
        disableCertificateValidation();
        final ClientConfig config = new DefaultClientConfig();

        final Client c = Client.create(config);
        c.addFilter(new LoggingFilter());
        c.setFollowRedirects(false);
        c.addFilter(new HTTPBasicAuthFilter(username, password));
        c.addFilter(new ClientFilter() {
            private ArrayList<Object> cookies;

            private ArrayList<Object> getCookiesToSet() {
                if (cookies != null && !cookies.isEmpty()) {
                    ArrayList<Object> cookiesToSet = new ArrayList<Object>();
                    StringBuilder cookieToAdd = new StringBuilder();
                    for (Object cookieRaw : cookies) {
                        NewCookie cookie = (NewCookie) cookieRaw;
                        cookieToAdd.append(cookie.getName());
                        cookieToAdd.append("=");
                        cookieToAdd.append(cookie.getValue());
                        cookieToAdd.append("; ");
                    }
                    cookiesToSet.add(cookieToAdd);
                    return cookiesToSet;
                }
                return null;
            }

            @Override
            public ClientResponse handle(ClientRequest request) throws ClientHandlerException {
                ArrayList<Object> cookiesToSet = getCookiesToSet();
                if (cookiesToSet != null) {
                    request.getHeaders().put("Cookie", cookiesToSet);
                }
                ClientResponse response = getNext().handle(request);
                if (response.getCookies() != null) {
                    if (cookies == null) {
                        cookies = new ArrayList<Object>();
                    }
                    // simple addAll just for illustration (should probably check for duplicates and expired cookies)
                    cookies.addAll(response.getCookies());
                }
                if (response.getStatus() == 302) {
                    WebResource wb = c.resource(response.getLocation());
                    cookiesToSet = getCookiesToSet();
                    if (cookiesToSet != null) {
                        response = wb.header("Cookie", cookiesToSet).get(ClientResponse.class);
                    } else {
                        response = wb.get(ClientResponse.class);
                    }
                }
                return response;
            }
        });
        return c;
    }

    @Before
    public synchronized void setup() throws Exception {
        initLoadBalancer(true);
        baseAuthServiceURL = baseUrls.get(0);
        baseApiServiceURL = baseUrls.get(0);
        if (!initDone) { // only do this once
            setupLicenseAndInitialPasswords();
            initDone = true;
        }
    }

    @Test
    public void coreTests() throws Exception {

        String runLongTestsStr = System.getenv("RUN_LONG_TESTS");
        runLongTests = (runLongTestsStr != null && runLongTestsStr.equalsIgnoreCase("true")) ? true : false;
        String runProxyTokenTestsStr = System.getenv("RUN_PROXY_TOKEN_EXPIRY_TESTS");
        runProxyTokenTests = (runProxyTokenTestsStr != null && runProxyTokenTestsStr.equalsIgnoreCase("true")) ? true : false;

        // TOKEN TESTS
        WebResource rRoot = createHttpsClient(USER_NAME, PASSWORD, true).resource(baseAuthServiceURL);
        WebResource rSysmonitor = createHttpsClient("sysmonitor", PASSWORD, true).resource(baseAuthServiceURL);

        ClientResponse resp = rRoot.path("/login").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        Assert.assertNotNull(_savedTokens.get(USER_NAME));
        String token1 = (String) _savedTokens.get(USER_NAME);
        WebResource rRootNoHandler = createHttpsClient(USER_NAME, PASSWORD, false).resource(baseAuthServiceURL);
        resp = rRootNoHandler.path("/login").get(ClientResponse.class);
        Assert.assertEquals(401, resp.getStatus());
        _savedTokens.remove(USER_NAME);
        resp = rRootNoHandler.path("/login").header(AUTH_TOKEN_HEADER, token1).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        resp = rRootNoHandler.path("/logout").header(AUTH_TOKEN_HEADER, token1).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        String expiredToken = token1;
        _savedTokens.remove(USER_NAME);
        resp = rRoot.path("/login").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        String token2 = (String) _savedTokens.get(USER_NAME);
        Assert.assertFalse(token1.equals(token2));

        // bad token and no credentials
        resp = rRootNoHandler.path("/login").header(AUTH_TOKEN_HEADER, "bad-token").get(ClientResponse.class);
        Assert.assertEquals(401, resp.getStatus());
        // no credentials and expired token
        resp = rRootNoHandler.path("/login").header(AUTH_TOKEN_HEADER, expiredToken).get(ClientResponse.class);
        Assert.assertEquals(401, resp.getStatus());

        // Authenticate three times, save distinct tokens. Then call logout with the force flag.
        // After that, none of the tokens should work.
        resp = rRoot.path("/login").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        Assert.assertNotNull(_savedTokens.get(USER_NAME));
        String multiTokenTestToken1 = (String) _savedTokens.get(USER_NAME);
        _savedTokens.remove(USER_NAME);
        resp = rRoot.path("/login").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        Assert.assertNotNull(_savedTokens.get(USER_NAME));
        String multiTokenTestToken2 = (String) _savedTokens.get(USER_NAME);
        _savedTokens.remove(USER_NAME);
        resp = rRoot.path("/login").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        Assert.assertNotNull(_savedTokens.get(USER_NAME));
        String multiTokenTestToken3 = (String) _savedTokens.get(USER_NAME);
        _savedTokens.remove(USER_NAME);

        // verify we have 3 distinct tokens. Three logins with the same user,
        // should result in 3 distinct tokens.
        Assert.assertFalse(multiTokenTestToken1.equals(multiTokenTestToken2));
        Assert.assertFalse(multiTokenTestToken2.equals(multiTokenTestToken3));
        resp = rSysmonitor.path("/login").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        // Try to logout root as the sysmonitor user verify it fails with 403
        resp = rSysmonitor.path("/logout").queryParam("username", USER_NAME).get(ClientResponse.class);
        Assert.assertEquals(403, resp.getStatus());
        // All tokens should still be good
        resp = rRootNoHandler.path("/login").header(AUTH_TOKEN_HEADER, multiTokenTestToken1).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        resp = rRootNoHandler.path("/login").header(AUTH_TOKEN_HEADER, multiTokenTestToken2).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        resp = rRootNoHandler.path("/login").header(AUTH_TOKEN_HEADER, multiTokenTestToken3).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        resp = rRootNoHandler.path("/logout").header(AUTH_TOKEN_HEADER, multiTokenTestToken1).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        // All tokens except the one used in logout should still be good
        resp = rRootNoHandler.path("/login").header(AUTH_TOKEN_HEADER, multiTokenTestToken1).get(ClientResponse.class);
        Assert.assertEquals(401, resp.getStatus());
        resp = rRootNoHandler.path("/login").header(AUTH_TOKEN_HEADER, multiTokenTestToken2).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        resp = rRootNoHandler.path("/login").header(AUTH_TOKEN_HEADER, multiTokenTestToken3).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        // Now call force logout using one of the remaining tokens. This should invalidate
        // itself and the other remaining one.
        resp = rRootNoHandler.path("/logout").queryParam("force", "true").
                header(AUTH_TOKEN_HEADER, multiTokenTestToken2).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        // All tokens should now be expired
        resp = rRootNoHandler.path("/login").header(AUTH_TOKEN_HEADER, multiTokenTestToken2).get(ClientResponse.class);
        Assert.assertEquals(401, resp.getStatus());
        resp = rRootNoHandler.path("/login").header(AUTH_TOKEN_HEADER, multiTokenTestToken3).get(ClientResponse.class);
        Assert.assertEquals(401, resp.getStatus());

        // Login the sysmonitor user a few times and save the tokens
        resp = rSysmonitor.path("/login").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        Assert.assertNotNull(_savedTokens.get("sysmonitor"));
        String secadminLogoutTestToken1 = (String) _savedTokens.get("sysmonitor");
        _savedTokens.remove("sysmonitor");
        resp = rSysmonitor.path("/login").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        Assert.assertNotNull(_savedTokens.get("sysmonitor"));
        String secadminLogoutTestToken2 = (String) _savedTokens.get("sysmonitor");
        _savedTokens.remove("sysmonitor");
        resp = rSysmonitor.path("/login").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        Assert.assertNotNull(_savedTokens.get("sysmonitor"));
        String secadminLogoutTestToken3 = (String) _savedTokens.get("sysmonitor");

        resp = rRoot.path("/login").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        String rootToken = (String) _savedTokens.get(USER_NAME);
        resp = rRoot.path("/logout").queryParam("username", "sysmonitor").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        // root token should still be valid
        resp = rRootNoHandler.path("/login").header(AUTH_TOKEN_HEADER, rootToken).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        // All sysmonitor tokens should now be expired
        WebResource rSysMonitorNoHandler = createHttpsClient("sysmonitor", PASSWORD, false).resource(baseAuthServiceURL);
        resp = rSysMonitorNoHandler.path("/login").header(AUTH_TOKEN_HEADER, secadminLogoutTestToken1).get(ClientResponse.class);
        Assert.assertEquals(401, resp.getStatus());
        resp = rSysMonitorNoHandler.path("/login").header(AUTH_TOKEN_HEADER, secadminLogoutTestToken2).get(ClientResponse.class);
        Assert.assertEquals(401, resp.getStatus());
        resp = rSysMonitorNoHandler.path("/login").header(AUTH_TOKEN_HEADER, secadminLogoutTestToken3).get(ClientResponse.class);
        Assert.assertEquals(401, resp.getStatus());

        // TOKEN TESTS WITH ACCESS TO API RESOURCE USING AUTO REDIRECT
        _savedTokens.clear();
        // Access api resource directly with credentials
        WebResource rApiUser = createHttpsClient(USER_NAME, PASSWORD, true).resource(baseApiServiceURL);
        WebResource rAuthUser = createHttpsClient(USER_NAME, PASSWORD, true).resource(baseAuthServiceURL);
        resp = rApiUser.path("/tenant").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        // logout
        String apiUserToken = (String) _savedTokens.get(USER_NAME);
        resp = rAuthUser.path("/logout").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        // Login first, get a token, then use it to access the api resource separately
        _savedTokens.clear();
        resp = rAuthUser.path("/login").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        apiUserToken = (String) _savedTokens.get(USER_NAME);
        rApiUser = createHttpsClient("", "", false).resource(baseApiServiceURL);
        resp = rApiUser.path("/tenant").header(AUTH_TOKEN_HEADER, apiUserToken).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        // API access with no credentials or token
        resp = rApiUser.path("/tenant").get(ClientResponse.class);
        Assert.assertEquals(401, resp.getStatus());

        // COOKIE TESTS
        rRoot = createCookieHttpsClient(USER_NAME, PASSWORD).resource(baseAuthServiceURL);
        resp = rRoot.path("/login").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        Assert.assertNull(resp.getHeaders().getFirst(HttpHeaders.SET_COOKIE));

        // formlogin tests
        // Making sure it can filter the cross site scripting attack by not appending source string if source string contains script tag
        WebResource rRootNoRedirect = createHttpsClient("", "", false).resource(baseAuthServiceURL);
        resp = rRootNoRedirect.path("/formlogin").queryParam("service", "someService")
                .queryParam("source", "\"><sCrIpT>alert(14035908.687)</ScRiPt><form \"").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        String entity = resp.getEntity(String.class);
        Assert.assertTrue(entity.contains("action="));
        Assert.assertFalse(entity.contains("\"><sCrIpT>alert(14035908.687)</ScRiPt><form \""));

        // get formlogin page from apisvc by using ?using-formlogin
        // TODO: fix this test once apisvc/login?using-formlogin is working again
        // (it is currently broken)
        /*
         * WebResource formRes = createHttpsClient("", "", false).resource(baseApiServiceURL);
         * resp = formRes.path("/login").queryParam("using-cookies", "true")
         * .queryParam("using-formlogin", "true")
         * .queryParam("service", "someService")
         * .get(ClientResponse.class);
         * Assert.assertEquals(200, resp.getStatus());
         * String entity = resp.getEntity(String.class);
         * Assert.assertTrue(entity.contains("form action="));
         */

        rRoot = createCookieHttpsClient(USER_NAME, PASSWORD).resource(baseAuthServiceURL);
        resp = rRoot.path("/login").queryParam("using-cookies", "true").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        Assert.assertNotNull(resp.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
        String tokenCookie = resp.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        Assert.assertTrue(tokenCookie.startsWith(AUTH_TOKEN_HEADER));

        rRoot = createCookieHttpsClient(USER_NAME, PASSWORD).resource(baseApiServiceURL);
        resp = rRoot.path("/tenant").queryParam("using-cookies", "true").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        resp = rRoot.path("/logout").queryParam("force", "true").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        resp = rRoot.path("/tenant").get(ClientResponse.class);
        Assert.assertEquals(401, resp.getStatus());

        // using-cookies default test
        rRoot = createCookieHttpsClient(USER_NAME, PASSWORD).resource(baseApiServiceURL);
        resp = rRoot.path("/login").queryParam("using-cookies", "").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        resp = rRoot.path("/logout").queryParam("force", "true").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        resp = rRoot.path("/tenant").get(ClientResponse.class);
        Assert.assertEquals(401, resp.getStatus());

        rRoot = createCookieHttpsClient(USER_NAME, PASSWORD).resource(baseApiServiceURL);
        resp = rRoot.path("/tenant").get(ClientResponse.class);
        Assert.assertEquals(401, resp.getStatus());
        rRoot = createCookieHttpsClient(USER_NAME, PASSWORD).resource(baseApiServiceURL);
        resp = rRoot.path("/tenant").queryParam("using-cookies", "false").get(ClientResponse.class);
        Assert.assertEquals(401, resp.getStatus());

        if (runLongTests) {
            // test limits on number of tokens per user
            // delete all tokens for root first
            rRoot = createCookieHttpsClient(USER_NAME, PASSWORD).resource(baseApiServiceURL);
            resp = rRoot.path("/tenant").queryParam("using-cookies", "true").get(ClientResponse.class);
            Assert.assertEquals(200, resp.getStatus());
            resp = rRoot.path("/logout").queryParam("force", "true").get(ClientResponse.class);
            Assert.assertEquals(200, resp.getStatus());

            rRoot = createHttpsClient(USER_NAME, PASSWORD, true).resource(baseApiServiceURL);
            _savedTokens.remove(USER_NAME);
            resp = rRoot.path("/login").get(ClientResponse.class);
            Assert.assertEquals(200, resp.getStatus());
            Assert.assertNotNull(_savedTokens.get(USER_NAME));
            rootToken = (String) _savedTokens.get(USER_NAME);

            for (int i = 0; i < 99; i++) {
                rRoot = createCookieHttpsClient(USER_NAME, PASSWORD).resource(baseApiServiceURL);
                resp = rRoot.path("/tenant").queryParam("using-cookies", "true").get(ClientResponse.class);
                Assert.assertEquals(200, resp.getStatus());
            }
            rRoot = createCookieHttpsClient(USER_NAME, PASSWORD).resource(baseApiServiceURL);
            resp = rRoot.path("/tenant").queryParam("using-cookies", "true").get(ClientResponse.class);
            Assert.assertEquals(401, resp.getStatus());
            String error = resp.getEntity(String.class);
            Assert.assertTrue(error, error.contains("Max number of tokens exceeded for this user"));

            // logout all
            rRoot = createHttpsClient("", "", false).resource(baseApiServiceURL);
            resp = rRoot.path("/logout").queryParam("force", "true")
                    .header(AUTH_TOKEN_HEADER, rootToken).get(ClientResponse.class);
            Assert.assertEquals(200, resp.getStatus());

            // new login
            rRoot = createCookieHttpsClient(USER_NAME, PASSWORD).resource(baseApiServiceURL);
            resp = rRoot.path("/tenant").queryParam("using-cookies", "true").get(ClientResponse.class);
            Assert.assertEquals(200, resp.getStatus());

            // test limit, same as above, but for proxyuser. proxyuser can have 1000 tokens.
            // test limits on number of tokens per user
            // delete all tokens for root first
            WebResource rProxyUser = createCookieHttpsClient("proxyuser", PASSWORD).resource(baseApiServiceURL);
            resp = rProxyUser.path("/tenant").queryParam("using-cookies", "true").get(ClientResponse.class);
            Assert.assertEquals(200, resp.getStatus());
            resp = rProxyUser.path("/logout").queryParam("force", "true").get(ClientResponse.class);
            Assert.assertEquals(200, resp.getStatus());

            rProxyUser = createHttpsClient("proxyuser", PASSWORD, true).resource(baseApiServiceURL);
            _savedTokens.remove("proxyuser");
            resp = rProxyUser.path("/login").get(ClientResponse.class);
            Assert.assertEquals(200, resp.getStatus());
            Assert.assertNotNull(_savedTokens.get("proxyuser"));
            String proxyUserToken = (String) _savedTokens.get("proxyuser");

            for (int i = 0; i < 999; i++) {
                rProxyUser = createCookieHttpsClient("proxyuser", PASSWORD).resource(baseApiServiceURL);
                resp = rProxyUser.path("/tenant").queryParam("using-cookies", "true").get(ClientResponse.class);
                Assert.assertEquals(200, resp.getStatus());
            }
            rProxyUser = createCookieHttpsClient("proxyuser", PASSWORD).resource(baseApiServiceURL);
            resp = rProxyUser.path("/tenant").queryParam("using-cookies", "true").get(ClientResponse.class);
            Assert.assertEquals(401, resp.getStatus());
            Assert.assertTrue(resp.getEntity(String.class).contains("Max number of tokens exceeded for this user"));
            // logout all
            rProxyUser = createHttpsClient("proxyuser", PASSWORD, false).resource(baseApiServiceURL);
            resp = rProxyUser.path("/logout").queryParam("force", "true")
                    .header(AUTH_TOKEN_HEADER, proxyUserToken).get(ClientResponse.class);
            Assert.assertEquals(200, resp.getStatus());
        }
        if (runProxyTokenTests) {
            runProxyTokenExpiryTest();
        }

    }

    private void runProxyTokenExpiryTest() throws Exception {

        try {
            String timeToWaitInMinsStr =
                    System.getenv("TIME_TO_WAIT_IN_MINUTES_SET_IN_SECURITY_MODULE_XML");
            int timeToWaitInMinutes = Integer.parseInt(timeToWaitInMinsStr);
        } catch (Exception e) {
            timeToWaitInMinutes = 1;
        }

        WebResource rRoot =
                createHttpsClient(SYSADMIN, SYSADMIN_PASS_WORD, true).resource(baseAuthServiceURL);
        rRoot.path("/login").get(ClientResponse.class);

        // post authProvider
        updateADConfig();

        // login with a user from ldap
        WebResource rSanityUser =
                createHttpsClient(ROOTUSER, AD_PASS_WORD, true).resource(
                        baseAuthServiceURL);
        rSanityUser.path("/login").get(ClientResponse.class);
        TenantResponse tenant = rSanityUser.path("/tenant").get(TenantResponse.class);

        // make the user a tenant_admin
        RoleAssignmentChanges changes = new RoleAssignmentChanges();
        RoleAssignmentEntry addTenantAdmin = new RoleAssignmentEntry();
        addTenantAdmin.setSubjectId(ROOTUSER);
        addTenantAdmin.getRoles().add("TENANT_ADMIN");
        changes.setAdd(new ArrayList<RoleAssignmentEntry>());
        changes.getAdd().add(addTenantAdmin);
        rRoot.path("/tenants/" + tenant.getTenant() + "/role-assignments").put(changes);

        // create a proxy token for that user
        ClientResponse resp = rSanityUser.path("/proxytoken").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        String proxyToken = (String) _savedProxyTokens.get(ROOTUSER);
        Assert.assertNotNull(proxyToken);

        // logon with proxyuser
        WebResource rProxy =
                createHttpsClient(PROXY_USER, PROXY_USER_PWD, true).resource(
                        baseApiServiceURL);
        rProxy.path("/login").get(ClientResponse.class);

        // try to get sanity user's tenant as proxy user with proxy token
        // should get a 200
        resp =
                rProxy.path("/tenants/" + tenant.getTenant()).header(AUTH_PROXY_TOKEN_HEADER, proxyToken)
                        .get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        // wait x amount of time for token to expire
        Thread.sleep(timeToWaitInMinutes * 60 * 1000);

        // try to get sanity user's tenant as proxy user with proxy token
        // should get a 200 again
        resp =
                rProxy.path("/tenants/" + tenant.getTenant()).header(AUTH_PROXY_TOKEN_HEADER, proxyToken)
                        .get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        // do a put on the authprovider so it is disabled
        AuthnUpdateParam updateParam = new AuthnUpdateParam();
        updateParam.setDisable(true);
        rRoot.path("/vdc/admin/authnproviders/" + _goodADConfig).put(updateParam);

        // wait x amount of time for token to expire
        Thread.sleep(timeToWaitInMinutes * 60 * 1000);

        // try to get the tenant with proxy user using the proxy token
        // should fail with a 401
        resp =
                rProxy.path("/tenants/" + tenant.getTenant()).header(AUTH_PROXY_TOKEN_HEADER, proxyToken)
                        .get(ClientResponse.class);
        Assert.assertEquals(401, resp.getStatus());

    }

    @Test
    public void passwordManipulationTests() throws Exception {
        // TEST 1: change root's password with the logout option.
        // 1. login with root, get 3 tokens.
        // 2. change root's password with logout option. Make sure all 3 tokens are gone
        WebResource rRoot = createHttpsClient(USER_NAME, PASSWORD, true).resource(baseAuthServiceURL);
        _savedTokens.remove(USER_NAME);
        ClientResponse resp = rRoot.path("/login").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        String rootToken1 = (String) _savedTokens.get(USER_NAME);
        _savedTokens.remove(USER_NAME);
        resp = rRoot.path("/login").get(ClientResponse.class);
        String rootToken2 = (String) _savedTokens.get(USER_NAME);
        _savedTokens.remove(USER_NAME);
        resp = rRoot.path("/login").get(ClientResponse.class);
        String rootToken3 = (String) _savedTokens.get(USER_NAME);
        _savedTokens.remove(USER_NAME);

        resetPassword(USER_NAME, "newp", null, true);

        rRoot = createHttpsClient("", "", false).resource(baseAuthServiceURL);
        resp = rRoot.path("/tenant").header(AUTH_TOKEN_HEADER, rootToken1).get(ClientResponse.class);
        Assert.assertEquals(401, resp.getStatus());
        resp = rRoot.path("/tenant").header(AUTH_TOKEN_HEADER, rootToken2).get(ClientResponse.class);
        Assert.assertEquals(401, resp.getStatus());
        resp = rRoot.path("/tenant").header(AUTH_TOKEN_HEADER, rootToken3).get(ClientResponse.class);
        Assert.assertEquals(401, resp.getStatus());
        // reset root's password to the original
        resetPassword(USER_NAME, PASSWORD, "newp", true);

        // TEST 2: change proxy user's password. Make sure root's token is not wiped out.
        // Only proxyuser's.
        // 1. Login with proxyuser, 3 times.
        // 2. have root logout proxyuser
        _savedTokens.remove("proxyuser");
        WebResource rProxy = createHttpsClient("proxyuser", PASSWORD, true).resource(baseAuthServiceURL);
        resp = rProxy.path("/login").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        String proxyToken1 = (String) _savedTokens.get("proxyuser");
        _savedTokens.remove("proxyuser");
        resp = rProxy.path("/login").get(ClientResponse.class);
        String proxyToken2 = (String) _savedTokens.get("proxyuser");
        _savedTokens.remove("proxyuser");
        resp = rProxy.path("/login").get(ClientResponse.class);
        String proxyToken3 = (String) _savedTokens.get("proxyuser");
        _savedTokens.remove("proxyuser");

        String rootResetToken = resetPassword("proxyuser", "newp", null, true);

        rProxy = createHttpsClient("", "", false).resource(baseAuthServiceURL);
        resp = rProxy.path("/tenant").header(AUTH_TOKEN_HEADER, proxyToken1).get(ClientResponse.class);
        Assert.assertEquals(401, resp.getStatus());
        resp = rProxy.path("/tenant").header(AUTH_TOKEN_HEADER, proxyToken2).get(ClientResponse.class);
        Assert.assertEquals(401, resp.getStatus());
        resp = rProxy.path("/tenant").header(AUTH_TOKEN_HEADER, proxyToken3).get(ClientResponse.class);
        Assert.assertEquals(401, resp.getStatus());
        resp = rProxy.path("/tenant").header(AUTH_TOKEN_HEADER, rootResetToken).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        // reset proxyuser password to the original
        resetPassword("proxyuser", PASSWORD, null, true);

        // TEST 3: Login root three times. Change its password but with logout=false. None of the
        // tokens should have been killed.
        rRoot = createHttpsClient(USER_NAME, PASSWORD, true).resource(baseAuthServiceURL);
        _savedTokens.remove(USER_NAME);
        resp = rRoot.path("/login").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        rootToken1 = (String) _savedTokens.get(USER_NAME);
        _savedTokens.remove(USER_NAME);
        resp = rRoot.path("/login").get(ClientResponse.class);
        rootToken2 = (String) _savedTokens.get(USER_NAME);
        _savedTokens.remove(USER_NAME);
        resp = rRoot.path("/login").get(ClientResponse.class);
        rootToken3 = (String) _savedTokens.get(USER_NAME);
        _savedTokens.remove(USER_NAME);

        resetPassword(USER_NAME, "newp", null, false);

        rRoot = createHttpsClient("", "", false).resource(baseAuthServiceURL);
        resp = rRoot.path("/tenant").header(AUTH_TOKEN_HEADER, rootToken1).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        resp = rRoot.path("/tenant").header(AUTH_TOKEN_HEADER, rootToken2).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        resp = rRoot.path("/tenant").header(AUTH_TOKEN_HEADER, rootToken3).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        // reset root's password to the original and kill the previous sessions
        resetPassword(USER_NAME, PASSWORD, "newp", true);
    }

    /**
     * Routine to reset a user's password, with various parameters to account for different
     * scenarios
     * 
     * @param user user for which to change the password
     * @param newPassword new password for the user
     * @param rootAltPasswd if the password change is happening after root's password was already changed,
     *            provide the current password for the root user, otherwise ChangeMe is assumed.
     * @param logout true if the sessions of the user need to be terminated as part of the password reset
     * @return the auth token of the root user's connection that did the reset
     * @throws NoSuchAlgorithmException
     */
    private String resetPassword(String user, String newPassword, String rootAltPasswd,
            boolean logout) throws NoSuchAlgorithmException {
        PasswordResetParam passwordReset = new PasswordResetParam();
        passwordReset.setPassword(newPassword);
        passwordReset.setUsername(user);
        ClientResponse resp;
        _savedTokens.remove(USER_NAME);
        WebResource rRoot = createHttpsClient(USER_NAME, rootAltPasswd == null ? PASSWORD : rootAltPasswd, true).
                resource(baseAuthServiceURL);
        resp = rRoot.path("/login").get(ClientResponse.class);
        String rootResetToken = (String) _savedTokens.get(USER_NAME);
        Assert.assertEquals(200, resp.getStatus());
        resp = rRoot.path("/password/reset/").queryParam("logout_user", Boolean.toString(logout)).
                put(ClientResponse.class, passwordReset);
        Assert.assertEquals(200, resp.getStatus());

        // if the user whose password was just changed was root, then you need to
        // relogin with the new password otherwise the cluster check below will fail with 401
        if (user.equals(USER_NAME)) {
            _savedTokens.remove(USER_NAME);
            rRoot = createHttpsClient(USER_NAME, newPassword, true).resource(baseAuthServiceURL);
            resp = rRoot.path("/login").get(ClientResponse.class);
            rootResetToken = (String) _savedTokens.get(USER_NAME);
            Assert.assertEquals(200, resp.getStatus());
        }

        String info = "";
        Boolean notStable = true;
        while (notStable) {
            try {
                Thread.sleep(2000);
                System.out.println("Waiting for stable cluster state.");
            } catch (InterruptedException e) {
                // Empty on purpose
            }
            resp = rRoot.path("/upgrade/cluster-state").get(ClientResponse.class);
            info = resp.getEntity(String.class);
            if (info.contains("<cluster_state>STABLE</cluster_state>")) {
                notStable = false;
                System.out.println("Cluster state is stable.");
            }
        }
        return rootResetToken;
    }
}
