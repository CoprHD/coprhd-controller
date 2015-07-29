/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.geo.service;

import java.util.ArrayList;
import java.util.Collections;

import javax.ws.rs.core.MediaType;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.emc.storageos.api.service.ApiTestBase;
import com.emc.storageos.model.auth.RoleAssignmentChanges;
import com.emc.storageos.model.auth.RoleAssignmentEntry;
import com.emc.storageos.services.util.EnvConfig;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.representation.Form;

/**
 * Main SSO test suite
 * Requirements to run these tests:
 * APP_HOST_NAMES env var is set to the VIP of VDC1
 * REMOTE_VDC_VIP env var is set to the VIP of VDC2
 * VDC1 and VDC2 must already be licensed, linked, and AD provider added.
 * Default passwords for local users must be set to ChangeMe, including proxyuser.
 * 
 */
public class SSOTest extends ApiTestBase {
    private String remoteVDCVIP;
    private boolean runExtendedTests = false;

    @Before
    public void setup() {
        initLoadBalancer(true);
        String remoteVDCVIPvar = System.getenv("REMOTE_VDC_VIP");
        if (remoteVDCVIPvar == null || remoteVDCVIPvar.equals("")) {
            Assert.fail("Missing remove VDC vip");
        }
        String remoteVDCTemplate = "https://%1$s:4443";
        remoteVDCVIP = String.format(remoteVDCTemplate, remoteVDCVIPvar);
        String ext = System.getenv("RUN_EXTENDED_TESTS");
        if (ext != null && ext.equalsIgnoreCase("true")) {
            runExtendedTests = true;
        }

    }

    /*
     * The test will connect to VDC1, obtain a token, use it on VDC2.
     * Logout the token on VDC1. Result should be that token is gone from both VDCs.
     */
    @Test
    public void loginLogoutFromOriginator() throws Exception {
        // get a token from VDC1, for an AD user.
        BalancedWebResource rAdmin = createHttpsClient(ZONEADMIN, AD_PASS_WORD, baseUrls, true);
        _savedTokens.remove(ZONEADMIN);
        ClientResponse resp = rAdmin.path("/tenant").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        String tokenFromVDC1 = (String) _savedTokens.get(ZONEADMIN);
        Assert.assertNotNull(tokenFromVDC1);

        // just verify on vdc1 the token we got is good against itself (vdc1)
        BalancedWebResource rAdminNoCreds = createHttpsClient("", "", baseUrls, false);
        resp = rAdminNoCreds.path("/tenant").header(AUTH_TOKEN_HEADER, tokenFromVDC1).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        BalancedWebResource rAdminVDC2NoCreds = createHttpsClient("", "", Collections.singletonList(remoteVDCVIP), false);
        resp = rAdminVDC2NoCreds.path("/tenant").header(AUTH_TOKEN_HEADER, tokenFromVDC1).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        // delete the token from vdc1 (originator of the token)
        resp = rAdminNoCreds.path("/logout").header(AUTH_TOKEN_HEADER, tokenFromVDC1).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        // verify using the token against itself results in 401. Token is gone. Need to reauthenticate.
        resp = rAdminNoCreds.path("/tenant").header(AUTH_TOKEN_HEADER, tokenFromVDC1).get(ClientResponse.class);
        Assert.assertEquals(401, resp.getStatus());

        // now try again to access the tenant resource on vdc2 with the same vdc1 token.
        // This should be rejected too. (vdc1 notified vdc2 of the token deletion).
        resp = rAdminVDC2NoCreds.path("/tenant").header(AUTH_TOKEN_HEADER, tokenFromVDC1).get(ClientResponse.class);
        Assert.assertEquals(401, resp.getStatus());
    }

    /*
     * The test will connect to VDC1, obtain a token, use it on VDC2.
     * Logout the token on VDC2. Result should be that token is gone from both VDCs.
     */
    @Test
    public void loginLogoutFromBorrower() throws Exception {
        // get a token from VDC1, for an AD user.
        BalancedWebResource rAdmin = createHttpsClient(ZONEADMIN, AD_PASS_WORD, baseUrls, true);
        _savedTokens.remove(ZONEADMIN);
        ClientResponse resp = rAdmin.path("/tenant").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        String tokenFromVDC1 = (String) _savedTokens.get(ZONEADMIN);
        Assert.assertNotNull(tokenFromVDC1);

        // just verify on vdc1 the token we got is good against itself (vdc1)
        BalancedWebResource rAdminNoCreds = createHttpsClient("", "", baseUrls, false);
        resp = rAdminNoCreds.path("/tenant").header(AUTH_TOKEN_HEADER, tokenFromVDC1).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        BalancedWebResource rAdminVDC2NoCreds = createHttpsClient("", "", Collections.singletonList(remoteVDCVIP), false);
        resp = rAdminVDC2NoCreds.path("/tenant").header(AUTH_TOKEN_HEADER, tokenFromVDC1).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        // delete the token from vdc2 (borrower of the token)
        resp = rAdminVDC2NoCreds.path("/logout").header(AUTH_TOKEN_HEADER, tokenFromVDC1).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        // verify using the token against originator (vdc1) results in 401. Vdc2 notified originator vdc1 of deletion.
        resp = rAdminNoCreds.path("/tenant").header(AUTH_TOKEN_HEADER, tokenFromVDC1).get(ClientResponse.class);
        Assert.assertEquals(401, resp.getStatus());

        // now try again to access the tenant resource on vdc2 with the same vdc1 token.
        // This should be rejected too.
        resp = rAdminVDC2NoCreds.path("/tenant").header(AUTH_TOKEN_HEADER, tokenFromVDC1).get(ClientResponse.class);
        Assert.assertEquals(401, resp.getStatus());
    }

    /*
     * The test will connect to VDC1, obtain 2 tokens for a given user, use them on VDC2.
     * Logout 1 of the tokens on VDC2 with the force option. Result should be that both tokens
     * are gone from both VDCs.
     */
    @Test
    public void loginLogoutForce() throws Exception {
        // get a token from VDC1, for an AD user.
        BalancedWebResource rAdmin = createHttpsClient(ZONEADMIN, AD_PASS_WORD, baseUrls, true);
        _savedTokens.remove(ZONEADMIN);
        ClientResponse resp = rAdmin.path("/tenant").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        String token1FromVDC1 = (String) _savedTokens.get(ZONEADMIN);
        Assert.assertNotNull(token1FromVDC1);

        _savedTokens.remove(ZONEADMIN);
        resp = rAdmin.path("/tenant").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        String token2FromVDC1 = (String) _savedTokens.get(ZONEADMIN);
        Assert.assertNotNull(token2FromVDC1);

        // just verify on vdc1 that both tokens we got are good against itself (vdc1)
        BalancedWebResource rAdminNoCreds = createHttpsClient("", "", baseUrls, false);
        resp = rAdminNoCreds.path("/tenant").header(AUTH_TOKEN_HEADER, token1FromVDC1).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        resp = rAdminNoCreds.path("/tenant").header(AUTH_TOKEN_HEADER, token2FromVDC1).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        // use both tokens on VDC2
        BalancedWebResource rAdminVDC2NoCreds = createHttpsClient("", "", Collections.singletonList(remoteVDCVIP), false);
        resp = rAdminVDC2NoCreds.path("/tenant").header(AUTH_TOKEN_HEADER, token1FromVDC1).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        resp = rAdminVDC2NoCreds.path("/tenant").header(AUTH_TOKEN_HEADER, token2FromVDC1).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        // delete one of the tokens from vdc2, using the force flag
        resp = rAdminVDC2NoCreds.path("/logout").queryParam("force", "true").header(AUTH_TOKEN_HEADER, token2FromVDC1)
                .get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        // verify using both tokens on both VDCs all result in 401
        resp = rAdminNoCreds.path("/tenant").header(AUTH_TOKEN_HEADER, token1FromVDC1).get(ClientResponse.class);
        Assert.assertEquals(401, resp.getStatus());
        resp = rAdminNoCreds.path("/tenant").header(AUTH_TOKEN_HEADER, token2FromVDC1).get(ClientResponse.class);
        Assert.assertEquals(401, resp.getStatus());
        resp = rAdminVDC2NoCreds.path("/tenant").header(AUTH_TOKEN_HEADER, token1FromVDC1).get(ClientResponse.class);
        Assert.assertEquals(401, resp.getStatus());
        resp = rAdminVDC2NoCreds.path("/tenant").header(AUTH_TOKEN_HEADER, token2FromVDC1).get(ClientResponse.class);
        Assert.assertEquals(401, resp.getStatus());
    }

    /*
     * The test will connect to VDC1 as user1, obtain 2 tokens for this user, use them on VDC2.
     * With a separate security admin connection, execute /logout with username parameter pointing to
     * the user that obtained 2 tokens. Result is these 2 tokens should be unsusable in either VDC.
     */
    @Test
    public void loginLogoutOtherUser() throws Exception {
        // get a token from VDC1, for an AD user.
        BalancedWebResource rAdmin = createHttpsClient(ZONEADMIN, AD_PASS_WORD, baseUrls, true);
        _savedTokens.remove(ZONEADMIN);
        ClientResponse resp = rAdmin.path("/tenant").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        String token1FromVDC1 = (String) _savedTokens.get(ZONEADMIN);
        Assert.assertNotNull(token1FromVDC1);

        _savedTokens.remove(ZONEADMIN);
        resp = rAdmin.path("/tenant").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        String token2FromVDC1 = (String) _savedTokens.get(ZONEADMIN);
        Assert.assertNotNull(token2FromVDC1);

        // just verify on vdc1 that both tokens we got are good against itself (vdc1)
        BalancedWebResource rAdminNoCreds = createHttpsClient("", "", baseUrls, false);
        resp = rAdminNoCreds.path("/tenant").header(AUTH_TOKEN_HEADER, token1FromVDC1).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        resp = rAdminNoCreds.path("/tenant").header(AUTH_TOKEN_HEADER, token2FromVDC1).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        // use both tokens on VDC2
        BalancedWebResource rAdminVDC2NoCreds = createHttpsClient("", "", Collections.singletonList(remoteVDCVIP), false);
        resp = rAdminVDC2NoCreds.path("/tenant").header(AUTH_TOKEN_HEADER, token1FromVDC1).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        resp = rAdminVDC2NoCreds.path("/tenant").header(AUTH_TOKEN_HEADER, token2FromVDC1).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        // login as root, grant security admin to SUPERUSER, on both VDCs.
        BalancedWebResource rootVDC1 = createHttpsClient(SYSADMIN, SYSADMIN_PASS_WORD, baseUrls, true);
        BalancedWebResource rootVDC1UnAuth = createHttpsClient("", "", baseUrls, false);
        rootVDC1.path("/tenant").get(ClientResponse.class);
        String rootVDC1Token = (String) _savedTokens.get(SYSADMIN);
        RoleAssignmentChanges changes = new RoleAssignmentChanges();
        RoleAssignmentEntry entry1 = new RoleAssignmentEntry();
        entry1.setSubjectId(SUPERUSER);
        entry1.getRoles().add("SECURITY_ADMIN");
        changes.setAdd(new ArrayList<RoleAssignmentEntry>());
        changes.getAdd().add(entry1);
        resp = rootVDC1.path("/vdc/role-assignments").put(ClientResponse.class, changes);
        Assert.assertEquals(200, resp.getStatus());

        _savedTokens.remove(SYSADMIN);

        BalancedWebResource rootVDC2 = createHttpsClient(SYSADMIN, SYSADMIN_PASS_WORD, Collections.singletonList(remoteVDCVIP), true);
        BalancedWebResource rootVDC2UnAuth = createHttpsClient("", "", Collections.singletonList(remoteVDCVIP), false);
        rootVDC2.path("/tenant").get(ClientResponse.class);
        String rootVDC2Token = (String) _savedTokens.get(SYSADMIN);
        resp = rootVDC2.path("/vdc/role-assignments").put(ClientResponse.class, changes);
        Assert.assertEquals(200, resp.getStatus());

        _savedTokens.remove(SYSADMIN); // blank out root's token from vdc2

        // verify that root himself cannot logout other users anymore directly unless the user
        // he is logging out is a local user
        resp = rootVDC1UnAuth.path("/logout").queryParam("username", ZONEADMIN).header(AUTH_TOKEN_HEADER, rootVDC1Token)
                .get(ClientResponse.class);
        Assert.assertEquals(403, resp.getStatus());
        BalancedWebResource svcUser = createHttpsClient(SVCUSER, SVCUSER_PASS_WORD, baseUrls, true);
        svcUser.path("/tenant").get(ClientResponse.class);
        String svcUserToken = (String) _savedTokens.get(SVCUSER);
        resp = rootVDC1UnAuth.path("/logout").queryParam("username", SVCUSER).header(AUTH_TOKEN_HEADER, rootVDC1Token)
                .get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        BalancedWebResource svcUserUnAuth = createHttpsClient("", "", baseUrls, false);
        resp = svcUserUnAuth.path("/tenant").header(AUTH_TOKEN_HEADER, svcUserToken).get(ClientResponse.class);
        Assert.assertEquals(401, resp.getStatus());

        // With a sec admin connection, logoutwith username=zadmin@sanity.local
        // delete one of the tokens from vdc1, using the force flag
        BalancedWebResource secAdminAD = createHttpsClient(SUPERUSER, AD_PASS_WORD, baseUrls, true);
        secAdminAD.path("/tenant").get(ClientResponse.class);
        resp = secAdminAD.path("/logout").queryParam("username", ZONEADMIN).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        // verify using both tokens on both VDCs all result in 401
        resp = rAdminNoCreds.path("/tenant").header(AUTH_TOKEN_HEADER, token1FromVDC1).get(ClientResponse.class);
        Assert.assertEquals(401, resp.getStatus());
        resp = rAdminNoCreds.path("/tenant").header(AUTH_TOKEN_HEADER, token2FromVDC1).get(ClientResponse.class);
        Assert.assertEquals(401, resp.getStatus());
        resp = rAdminVDC2NoCreds.path("/tenant").header(AUTH_TOKEN_HEADER, token1FromVDC1).get(ClientResponse.class);
        Assert.assertEquals(401, resp.getStatus());
        resp = rAdminVDC2NoCreds.path("/tenant").header(AUTH_TOKEN_HEADER, token2FromVDC1).get(ClientResponse.class);
        Assert.assertEquals(401, resp.getStatus());

        // Connected as svcuser on vdc1 and the same on vdc2.
        // Using a sec admin user, do a logout?username=svcuser on vdc1. This should not
        // propagate to vdc2 because svcuser is a local user.
        _savedTokens.remove(SVCUSER);
        svcUser = createHttpsClient(SVCUSER, SVCUSER_PASS_WORD, baseUrls, true);
        svcUser.path("/tenant").get(ClientResponse.class);
        String svcUserTokenVDC1 = (String) _savedTokens.get(SVCUSER);
        _savedTokens.remove(SVCUSER);
        BalancedWebResource svcUserVDC2 = createHttpsClient(SVCUSER, SVCUSER_PASS_WORD, Collections.singletonList(remoteVDCVIP), true);
        svcUserVDC2.path("/tenant").get(ClientResponse.class);
        String svcUserTokenVDC2 = (String) _savedTokens.get(SVCUSER);
        resp = secAdminAD.path("/logout").queryParam("username", SVCUSER).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        // svcuser on vdc 1 should be logged out.
        resp = svcUserUnAuth.path("/tenant").header(AUTH_TOKEN_HEADER, svcUserTokenVDC1).get(ClientResponse.class);
        Assert.assertEquals(401, resp.getStatus());
        // svc user on vdc 2 should be logged out.
        BalancedWebResource svcUserVDC2UnAuth = createHttpsClient("", "", Collections.singletonList(remoteVDCVIP), false);
        resp = svcUserVDC2UnAuth.path("/tenant").header(AUTH_TOKEN_HEADER, svcUserTokenVDC2).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
    }

    /*
     * The test will connect to VDC1 as local user, obtain a token, try to use it on VDC2.
     * Should fail as local users cannot do SSO.
     */
    @Test
    public void localUserSSODenied() throws Exception {
        BalancedWebResource rRoot = createHttpsClient(EnvConfig.get("sanity", "geosvc.SSOTest.localUserSSODenied.username"),
                EnvConfig.get("sanity", "geosvc.SSOTest.localUserSSODenied.password"), baseUrls, true);
        _savedTokens.remove(EnvConfig.get("sanity", "geosvc.SSOTest.localUserSSODenied.username"));
        ClientResponse resp = rRoot.path("/tenant").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        String tokenFromVDC1 = (String) _savedTokens.get("root");
        Assert.assertNotNull(tokenFromVDC1);

        // just verify on vdc1 the token we got is good against vdc1
        resp = rRoot.path("/tenant").header(AUTH_TOKEN_HEADER, tokenFromVDC1).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        // use vdc1's token in vdc2's login resource with no other credentials. Verify this is rejected (local user).
        BalancedWebResource rRootVDC2NoCreds = createHttpsClient("", "", Collections.singletonList(remoteVDCVIP), false);
        resp = rRootVDC2NoCreds.path("/tenant").header(AUTH_TOKEN_HEADER, tokenFromVDC1).get(ClientResponse.class);
        Assert.assertEquals(401, resp.getStatus());
    }

    /*
     * The test will connect to VDC1 as local user, obtain a proxytoken, try to use it on VDC2.
     * Should fail as proxytokens are not allowed for SSO.
     */
    @Test
    public void proxyTokenDenied() throws Exception {
        // connect to vdc1 as zoneadmin user.
        BalancedWebResource rAdmin = createHttpsClient(ZONEADMIN, AD_PASS_WORD, baseUrls, true);
        _savedTokens.remove(ZONEADMIN);
        ClientResponse resp = rAdmin.path("/tenant").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        String tokenFromVDC1 = (String) _savedTokens.get(ZONEADMIN);
        Assert.assertNotNull(tokenFromVDC1);

        // get a proxy token
        resp = rAdmin.path("/proxytoken").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        String proxyToken = (String) _savedProxyTokens.get(ZONEADMIN);
        Assert.assertNotNull(proxyToken);

        // connect as proxyuser on vdc1
        BalancedWebResource rProxyUserVDC1 = createHttpsClient(EnvConfig.get("sanity", "geosvc.SSOTest.proxyTokenDenied.vdc1Username"),
                EnvConfig.get("sanity", "geosvc.SSOTest.proxyTokenDenied.vdc1password"), baseUrls, true);
        resp = rProxyUserVDC1.path("/tenant").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        // try to use the proxytoken generated on vdc1 in vdc1.
        resp = rProxyUserVDC1.path("/tenant").header(ApiTestBase.AUTH_PROXY_TOKEN_HEADER, proxyToken)
                .get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        // connect as proxyuser on vdc2
        BalancedWebResource rProxyUserVDC2 = createHttpsClient(EnvConfig.get("sanity", "geosvc.SSOTest.proxyTokenDenied.vdc2Username"),
                EnvConfig.get("sanity", "geosvc.SSOTest.proxyTokenDenied.vdc2password"), Collections.singletonList(remoteVDCVIP), true);
        resp = rProxyUserVDC2.path("/tenant").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        // try to use the proxytoken generated on vdc1.
        resp = rProxyUserVDC2.path("/tenant").header(ApiTestBase.AUTH_PROXY_TOKEN_HEADER, proxyToken)
                .get(ClientResponse.class);
        Assert.assertEquals(401, resp.getStatus());
    }

    /*
     * This test obtains a valid token from VDC1.
     * - It will try to access the tenant resource of VDC1 by going through the formlogin resource with service= and the token
     * as form parameter. This is not really a real use case (one would normally pass credentials to formlogin of its own VDC)
     * but it's to make sure there are no ill effects if one did that.
     * - More importantly, try the same thing but against VDC2. So access VDC2's tenant resource through service= redirect
     * on the formlogin with no credentials, and the token from VDC1. This is to exercise the SSO functionality from the UI
     * point of view.
     */
    @Test
    public void SSOformLogin() throws Exception {
        BalancedWebResource rAdmin = createHttpsClient(ZONEADMIN, AD_PASS_WORD, baseUrls, true);
        _savedTokens.remove(ZONEADMIN);
        ClientResponse resp = rAdmin.path("/tenant").get(ClientResponse.class);
        String vdc1Token = (String) _savedTokens.get(ZONEADMIN);
        _savedTokens.remove(ZONEADMIN);

        Form formLogin = new Form();
        formLogin.add("auth-token", vdc1Token);
        BalancedWebResource rAnonVDC1 = createHttpsClient("", "", baseUrls, true);
        resp = rAnonVDC1.path("/formlogin").queryParam("service", baseUrls.get(0) + "/tenant").type(MediaType.APPLICATION_FORM_URLENCODED).
                post(ClientResponse.class, formLogin);
        Assert.assertEquals(200, resp.getStatus());

        _savedTokens.remove(ZONEADMIN);

        _lastUsedAuthTokenCookie = null;

        BalancedWebResource rAnonVDC2 = createHttpsClient("", "", Collections.singletonList(remoteVDCVIP), true);
        resp = rAnonVDC2.path("/formlogin").queryParam("service", remoteVDCVIP + "/tenant").type(MediaType.APPLICATION_FORM_URLENCODED).
                post(ClientResponse.class, formLogin);
        Assert.assertEquals(200, resp.getStatus());
        // check that vdc2 sent us a new cookie and its value is equal to the vdc1 token
        Assert.assertNotNull(_lastUsedAuthTokenCookie);

        Assert.assertEquals(vdc1Token, _lastUsedAuthTokenCookie);
    }

    // ------------------------------------------------------------------------------------
    // Extended tests, require modification on the appliance and additional env variables

    /*
     * Requirement for these tests:
     * Go to both VDC1 and VDC2 appliances.
     * Edit auth-conf.xml, edit this bean:
     * "tokenMaxLifeValuesHolder" and add these properties :
     * <bean id="tokenMaxLifeValuesHolder" class="com.emc.storageos.security.authentication.TokenMaxLifeValuesHolder">
     * <property name="maxTokenLifeTimeInMins" value="3" />
     * </bean>
     * Restart storageos.
     * Then run this test with RUN_EXTENDED_TESTS=true
     */

    /*
     * Connects to vdc1, verify that using a token which life's is less than 10 minutes
     * cannot be used on vdc2 once life time has expired (showing max life time takes
     * precedence over cache life)
     */
    @Test
    public void cacheTest() throws Exception {
        if (!runExtendedTests) {
            return;
        }

        int maxLife = 4;
        // get a token from VDC1, for an AD user.
        BalancedWebResource rAdmin = createHttpsClient(ZONEADMIN, AD_PASS_WORD, baseUrls, true);
        _savedTokens.remove(ZONEADMIN);
        ClientResponse resp = rAdmin.path("/tenant").get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());
        String tokenFromVDC1 = (String) _savedTokens.get(ZONEADMIN);
        Assert.assertNotNull(tokenFromVDC1);

        // just verify on vdc1 the token we got is good against itself (vdc1)
        BalancedWebResource rAdminNoCreds = createHttpsClient("", "", baseUrls, false);
        resp = rAdminNoCreds.path("/tenant").header(AUTH_TOKEN_HEADER, tokenFromVDC1).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        BalancedWebResource rAdminVDC2NoCreds = createHttpsClient("", "", Collections.singletonList(remoteVDCVIP), false);
        resp = rAdminVDC2NoCreds.path("/tenant").header(AUTH_TOKEN_HEADER, tokenFromVDC1).get(ClientResponse.class);
        Assert.assertEquals(200, resp.getStatus());

        // After a time longer than the max life of the token (artificially set to 1 minute in this test),
        // the token in VDC1 and VDC2 should both be unusable. Even though cache life is 10 minutes, max life should
        // take precedence.
        Thread.sleep(((maxLife) * 1000 * 60));
        resp = rAdminNoCreds.path("/tenant").header(AUTH_TOKEN_HEADER, tokenFromVDC1).get(ClientResponse.class);
        Assert.assertEquals(401, resp.getStatus());
        resp = rAdminVDC2NoCreds.path("/tenant").header(AUTH_TOKEN_HEADER, tokenFromVDC1).get(ClientResponse.class);
        Assert.assertEquals(401, resp.getStatus());
    }
}
