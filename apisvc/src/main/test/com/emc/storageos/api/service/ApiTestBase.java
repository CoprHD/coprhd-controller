/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service;

import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.model.auth.ACLEntry;
import com.emc.storageos.model.auth.AuthnCreateParam;
import com.emc.storageos.model.auth.AuthnProviderRestRep;
import com.emc.storageos.model.auth.RoleAssignmentEntry;
import com.emc.storageos.model.errorhandling.ServiceErrorRestRep;
import com.emc.storageos.model.password.PasswordResetParam;
import com.emc.storageos.model.tenant.*;
import com.emc.storageos.model.varray.NetworkRestRep;
import com.emc.storageos.model.vpool.BlockVirtualPoolRestRep;
import com.emc.storageos.model.vpool.FileVirtualPoolRestRep;
import com.emc.storageos.security.authentication.NoAuthHeaderUserFilter;
import com.emc.storageos.security.authorization.PermissionsKey;
import com.emc.storageos.services.util.EnvConfig;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.vipr.model.sys.licensing.License;
import com.emc.vipr.model.sys.licensing.LicenseFeature;
import com.sun.jersey.api.client.*;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.client.filter.ClientFilter;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.api.client.filter.LoggingFilter;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import javax.ws.rs.core.NewCookie;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.*;

import static org.hamcrest.core.AnyOf.anyOf;
import static org.hamcrest.core.Is.is;

/**
 * 
 * Base class for jersey client type tests which includes all the necessary
 * http/https client setups and AD configuration.
 * 
 */
public class ApiTestBase {

    public static final String AD_SERVER1_IP = EnvConfig.get("sanity", "ad1.ip");
    public static final String AD_SERVER1_HOST = EnvConfig.get("sanity", "ad1.hostname");
    public static final String AD_SERVER2_IP = EnvConfig.get("sanity", "ad2.ip");
    public static final String LDAP_SERVER1_IP = EnvConfig.get("sanity", "ldap1.ip");

    // Constants for all local and AD based users and groups, using the .165 AD server.
    protected static final String ROOTTENANT_ATTR = "sanity";
    protected static final String ROOTTENANT_NAME = "Root Provider Tenant";

    protected static final String SYSADMIN = "root";
    protected static final String SYSADMIN_PASS_WORD = "ChangeMe";

    protected static final String SYSMONITOR = "sysmonitor";
    protected static final String SYSMONITOR_PASS_WORD = "ChangeMe1!";

    protected static final String SVCUSER = "svcuser";
    protected static final String SVCUSER_PASS_WORD = "ChangeMe1!";

    protected static final String PROXY_USER = "proxyuser";
    protected static final String PROXY_USER_PWD = "ChangeMe1!";

    protected static final String TENANTADMIN = "sanity_tenant_admin@sanity.local";
    protected static final String ZONEADMIN = "zadmin@sanity.local";
    protected static final String SUPERUSER = "super_sanity@sanity.local";
    protected static final String ROOTTENANTADMIN = "rtadmin@Sanity.Local";
    protected static final String ROOTTENANTADMIN_FORASSIGNMENT = "RTAdmin@sanity.local";
    protected static final String ROOTUSER = "Sanity_User@Sanity.Local";
    protected static final String ROOTUSER2 = "sanity_user2@sanity.local";

    protected static final String ZONEADMINS_GROUP = "ZoneAdmins@sanity.local";
    protected static final String TENANT_ADMINS_GROUP = "RootTenantAdmins@sanity.local";

    // Subtenant1 configuration
    protected static final String SUBTENANT1_ATTR = "SUBTENANT1";

    protected static final String SUBTENANT1_ADMIN = "sTadmin1@sanity.local";
    protected static final String SUBTENANT1_ADMIN2 = "st1admin2@Sanity.Local";

    protected static final String SUBTENANT1_USER = "st1user@sanity.local";
    protected static final String SUBTENANT1_READER = "st1reader@sanity.local";

    // use funky case for these groups to test that role assignments are case-insensitive
    protected static final String SUBTENANT1_ADMINS_GROUP = "sUbTeNaNt1aDmInS@sanity.local";
    protected static final String SUBTENANT1_USERS_GROUP = "subtenant1USERS@sanity.local";

    // Subtenant2 configuration
    protected static final String SUBTENANT2_ATTR = "subtenant2";

    protected static final String SUBTENANT2_ADMIN = "stadmin2@sanity.local";
    protected static final String SUBTENANT2_ADMIN2 = "st2admin2@sanity.local";
    protected static final String SUBTENANT2_ADMINS_GROUP = "Subtenant2Admins@sanity.local";

    protected static final String SUBTENANT2_USER = "st2user@sanity.local";

    protected static final String SUBTENANT13_USER = "st12user@sanity.local";

    // Subtenant3 Configuration
    protected static final String SUBTENANT3_ATTR = "Test Group";

    protected static final String SUBTENANT3_ADMIN = "testuser@sanity.local";

    // Subtenant cross tenant configuration
    protected static final String CROSS_TENANT_USER = "cross1@sanity.local";
    protected static final String ASUBSETOFUSERS_GROUP = "ASubSetOfUsers@sanity.local";

    // LDAPS Configuration

    protected static final String LDAPS_USER = "user1@secureldap.com";
    protected static final String LDAPS_PASS_WORD = "password";

    protected static final Logger _log = LoggerFactory.getLogger(ApiTest.class);

    protected static final String AD_PASS_WORD = EnvConfig.get("sanity", "ad.manager.password");

    protected static final String LICENSE_FILE = "INCREMENT ViPR_Controller EMCLM 2.0 permanent uncounted " +
            "VENDOR_STRING=CAPACITY=1024;CAPACITY_UNIT=TB;SWID=PXTYD1DZK59Y4C;PLC=VIPR; " +
            "HOSTID=ANY dist_info=\"ACTIVATED TO 49ers Inn\" ISSUER=EMC " +
            "ISSUED=10-Jan-2014 NOTICE=\"ACTIVATED TO License Site Number: " +
            "PTA06JUN20131086059\" SN=2162734 SIGN=\"00EC 6B99 FB75 280D B932 75DD 21D1 EC00 5634 5848 462F 7ACD 0032 5081 2923\"" +
            "\nINCREMENT ViPR_HDFS EMCLM 2.0 permanent uncounted " +
            "VENDOR_STRING=SWID=PXTYD1DZK59Y4C;PLC=VIPR; HOSTID=ANY " +
            "dist_info=\"ACTIVATED TO 49ers Inn\" ISSUER=EMC " +
            "ISSUED=10-Jan-2014 NOTICE=\"ACTIVATED TO License Site Number: " +
            "PTA06JUN20131086059\" SN=2162734 SIGN=\"0073 059F D54D 7CC9 4ADA 0B13 6160 9100 688E 8167 37DA E911 28F2 CC96 798A\"" +
            "\nINCREMENT ViPR_Object EMCLM 2.0 permanent uncounted " +
            "VENDOR_STRING=SWID=PXTYD1DZK59Y4C;PLC=VIPR; HOSTID=ANY " +
            "dist_info=\"ACTIVATED TO 49ers Inn\" ISSUER=EMC " +
            "ISSUED=10-Jan-2014 NOTICE=\"ACTIVATED TO License Site Number: " +
            "PTA06JUN20131086059\" SN=2162734 SIGN=\"000E BA65 2065 4DBD 8888 CAEB 94EE F800 BAF0 FF51 A3F0 1E81 E731 4ECB FACC\"" +
            "\nINCREMENT ViPR_Unstructured EMCLM 2.0 permanent uncounted " +
            "VENDOR_STRING=CAPACITY=1024;CAPACITY_UNIT=TB;SWID=PXTYD1DZK59Y4C;PLC=VIPR; " +
            "HOSTID=ANY dist_info=\"ACTIVATED TO 49ers Inn\" ISSUER=EMC " +
            "ISSUED=10-Jan-2014 NOTICE=\"ACTIVATED TO License Site Number: " +
            "PTA06JUN20131086059\" SN=2162734 SIGN=\"00CF 2080 FDCA D7E1 CA22 5DE7 DA9A 0000 1938 D26C 4AB5 76CB AA43 2662 0FCD\"" +
            "\nINCREMENT ViPR_CAS EMCLM 2.0 permanent uncounted " +
            "VENDOR_STRING=SWID=PXTYD1DZK59Y4C;PLC=VIPR; HOSTID=ANY " +
            "dist_info=\"ACTIVATED TO 49ers Inn\" ISSUER=EMC " +
            "ISSUED=10-Jan-2014 NOTICE=\"ACTIVATED TO License Site Number: " +
            "PTA06JUN20131086059\" SN=2162734 SIGN=\"009E 6082 66B8 3D16 69AC 9C84 8FDD DB00 C762 3EBB 52FC 04C8 72A2 A5A9 4CC8\"";

    protected BalancedWebResource rSys, rMon, rZAdmin, rZAdminGr, rTAdmin, rTAdminGr,
            rSTAdmin1, rSTAdminGr1, rSTAdmin2, rSTAdminGr2,
            rProjRead, rProjUserGr, rUnAuth, rRootUser2, rSTAdmin3, rProxyUser, rLdaps,
            rST13User, rSTCross, rST2User;

    protected Map<String, List<ProjectEntry>> expectedProjListResults = new HashMap<String, List<ProjectEntry>>();
    protected URI rootTenantId, subtenant1Id, subtenant2Id, subtenant3Id;
    protected URI _nh, _testProject, _fs, _volume, _group;
    protected FileVirtualPoolRestRep _cosFile;
    protected BlockVirtualPoolRestRep _cosBlock;
    protected NetworkRestRep _iptzone;
    protected NetworkRestRep _fctzone;
    protected String _projectsUrlFormat = "/tenants/%s/projects";
    protected String _projectUrl = "/projects/%s";
    protected String _projectUrlDelete = "/projects/%s/deactivate";
    protected String _projectAclUrl = _projectUrl + "/acl";
    protected String _blockCosAclUrl = "/block/vpools/%s/acl";
    protected String _fileCosAclUrl = "/file/vpools/%s/acl";

    protected static volatile List<String> baseUrls;

    /**
     * Class to encapsulate a list of WebResources
     */
    public class BalancedWebResource {
        private int index = 0;
        private final List<WebResource> _hosts;

        public BalancedWebResource() {
            index = 0;
            _hosts = new LinkedList<WebResource>();

        }

        public void addWebResource(WebResource w) {
            _hosts.add(w);
        }

        public WebResource getNextHost() {
            if (index == _hosts.size() - 1) {
                index = 0;
                return _hosts.get(_hosts.size() - 1);
            } else {
                return _hosts.get(index++);
            }
        }

        public WebResource path(String p) {
            return getNextHost().path(p);
        }

    }

    /**
     * initialize the list of hosts based on the APP_HOST_NAMES env. variable
     * 
     * @param isHttps: true for https urls, false otherwise
     */
    protected void initLoadBalancer(boolean isHttps) {
        String baseServiceURLTemplate = new String();
        if (isHttps) {
            baseServiceURLTemplate = "https://%1$s:4443";
        } else {
            baseServiceURLTemplate = "http://%1$s:8080";
        }
        String hostName = System.getenv("APP_HOST_NAMES");
        String hostNames[] = hostName.split(",");
        baseUrls = new LinkedList<String>();
        for (String h : hostNames) {
            String disp = String.format(baseServiceURLTemplate, h);
            baseUrls.add(disp);
        }
    }

    /**
     * Update tenant attributes for the root tenant
     */
    protected void updateRootTenantAttrs() {
        TenantResponse tenantResp = rSys.path("/tenant").get(TenantResponse.class);
        rootTenantId = tenantResp.getTenant();
        /*
         * PUT the ou=sanity attribute mapping into the root tenant attributes
         */
        TenantUpdateParam tenantUpdate = new TenantUpdateParam();
        tenantUpdate.setUserMappingChanges(new UserMappingChanges());
        tenantUpdate.getUserMappingChanges().setAdd(new ArrayList<UserMappingParam>());
        UserMappingParam rootMapping = new UserMappingParam();
        rootMapping.setDomain("SANITY.local");
        UserMappingAttributeParam rootAttr = new UserMappingAttributeParam();
        rootAttr.setKey("ou");
        rootAttr.setValues(Collections.singletonList(ROOTTENANT_ATTR));
        rootMapping.setAttributes(Collections.singletonList(rootAttr));
        tenantUpdate.getUserMappingChanges().getAdd().add(rootMapping);
        tenantUpdate.setLabel(ROOTTENANT_NAME); // TODO: FIX: not sure why name is required for update
        ClientResponse resp = rSys.path("/tenants/" + rootTenantId.toString()).put(ClientResponse.class, tenantUpdate);
        Assert.assertEquals(200, resp.getStatus());
    }

    /**
     * Add in the main AD configuration that will be used for the entire test suite
     */
    protected URI _goodADConfig = null;

    protected void updateADConfig() {
        if (rSys == null) {  // rSys may get nulled out between
            // Tests depending on what Junit feels like doing that day
            try {
                rSys = createHttpsClient(SYSADMIN, SYSADMIN_PASS_WORD, baseUrls);
                rSys.path("/tenant").get(String.class);
            } catch (Exception e) {
                Assert.fail();
            }
        }

        AuthnCreateParam param = new AuthnCreateParam();
        param.setLabel("ad apitest config good");
        param.setDescription("ad configuration created by ApiTest.java");
        param.setDisable(false);
        // Put spaces in the doman to verify it does not cause a problem
        param.getDomains().add(" SANITY.LOCAL ");
        param.setGroupAttribute("CN");
        param.getGroupWhitelistValues().add("*Admins*");
        param.getGroupWhitelistValues().add("*Test*");
        param.getGroupWhitelistValues().add("*Users*");
        param.setManagerDn("CN=Administrator,CN=Users,DC=sanity,DC=local");
        param.setManagerPassword("P@ssw0rd");
        param.setSearchBase("DC=sanity,DC=local");
        param.setSearchFilter("userPrincipalName=%u");
        param.getServerUrls().add("ldap://" + AD_SERVER1_IP);
        param.setMode("ad");
        param.setSearchScope("SUBTREE");
        try {
            AuthnProviderRestRep authnResp = rSys.path("/vdc/admin/authnproviders").post(AuthnProviderRestRep.class, param);
            Assert.assertNotNull(authnResp);
            _goodADConfig = authnResp.getId();
        } catch (UniformInterfaceException e) {
            if (e.getResponse().getStatus() != 400) {
                Assert.fail();
            }
        } catch (Exception e) {
            Assert.fail();
        }
    }

    protected boolean isControllerLicensed() {
        License license = rSys.path("/license").get(License.class);
        List<LicenseFeature> features = license.getLicenseFeatures();
        if (features == null) {
            return false;
        }

        for (LicenseFeature feature : features) {
            if (feature.getModelId().startsWith("ViPR_Controller") && feature.isLicensed()) {
                return true;
            }
        }
        return false;
    }

    protected void addControllerLicense() {
        License license = new License();
        license.setLicenseText(LICENSE_FILE);
        ClientResponse resp = rSys.path("/license").post(ClientResponse.class, license);
        Assert.assertEquals(200, resp.getStatus());
        Assert.assertTrue(isControllerLicensed());
    }

    BalancedWebResource getHttpsClient(String userName, String password) throws NoSuchAlgorithmException {
        return createHttpsClient(userName, password, baseUrls);
    }

    /**
     * initialize http resources
     */
    protected void setupHttpResources() {
        initLoadBalancer(false);
        rSys = createHttpClient(SYSADMIN, "LOCAL_STORAGEOS_USER=true", "", baseUrls);
        rProxyUser = createHttpClient(PROXY_USER, "LOCAL_STORAGEOS_USER=true", "", baseUrls);
        rMon = createHttpClient(SYSMONITOR, "LOCAL_STORAGEOS_USER=true", "", baseUrls);
        updateADConfig();
        updateRootTenantAttrs();

        rZAdmin = createHttpClient(ZONEADMIN, "ou=" + ROOTTENANT_ATTR, "", baseUrls);
        rZAdminGr = createHttpClient(SUPERUSER, "ou=" + ROOTTENANT_ATTR, ZONEADMINS_GROUP, baseUrls);

        rTAdmin = createHttpClient(ROOTTENANTADMIN, "ou=" + ROOTTENANT_ATTR, "", baseUrls);
        rTAdminGr = createHttpClient(TENANTADMIN, "ou=" + ROOTTENANT_ATTR, TENANT_ADMINS_GROUP, baseUrls);

        rSTAdmin1 = createHttpClient(SUBTENANT1_ADMIN, "department=" + SUBTENANT1_ATTR, "", baseUrls);
        rSTAdminGr1 = createHttpClient(SUBTENANT1_ADMIN2, "department=" + SUBTENANT1_ATTR, SUBTENANT1_ADMINS_GROUP, baseUrls);

        rSTAdmin2 = createHttpClient(SUBTENANT2_ADMIN, "company=" + SUBTENANT2_ATTR, "", baseUrls);
        rSTAdminGr2 = createHttpClient(SUBTENANT2_ADMIN2, "company=" + SUBTENANT2_ATTR, SUBTENANT2_ADMINS_GROUP, baseUrls);

        rProjRead = createHttpClient(SUBTENANT1_READER, "company=" + SUBTENANT1_ATTR, "", baseUrls);
        rProjUserGr = createHttpClient(SUBTENANT1_USER, "company=" + SUBTENANT1_ATTR, SUBTENANT1_USERS_GROUP + ",Dummy", baseUrls);

        rUnAuth = createHttpClient(ROOTUSER, "ou=" + ROOTTENANT_ATTR, "", baseUrls);
        rRootUser2 = createHttpClient(ROOTUSER2, "ou=" + ROOTTENANT_ATTR, "", baseUrls);

        rSTAdmin3 = createHttpClient(SUBTENANT3_ADMIN, "group=" + SUBTENANT3_ATTR, "", baseUrls);

        rLdaps = createHttpClient(LDAPS_USER, "postalCode=01748", "", baseUrls);
        rST13User = createHttpClient(SUBTENANT13_USER, "company=" + SUBTENANT1_ATTR, "group=" + SUBTENANT3_ATTR, baseUrls);

        rSTCross = createHttpClient(CROSS_TENANT_USER, "company=crosstenant", "", baseUrls);

    }

    /*
     * initialize https resources
     */
    @SuppressWarnings("unchecked")
    protected void setupHttpsResources() throws NoSuchAlgorithmException {
        initLoadBalancer(true);

        setupLicenseAndInitialPasswords();

        rProxyUser = createHttpsClient(PROXY_USER, PROXY_USER_PWD, baseUrls);
        rProxyUser.path("/tenant").get(String.class);

        rMon = createHttpsClient(SYSMONITOR, SYSMONITOR_PASS_WORD, baseUrls);

        updateADConfig();
        updateRootTenantAttrs();

        rZAdmin = createHttpsClient(ZONEADMIN, AD_PASS_WORD, baseUrls);
        ClientResponse r = rZAdmin.path("/tenant").get(ClientResponse.class);

        rZAdminGr = createHttpsClient(SUPERUSER, AD_PASS_WORD, baseUrls);
        rZAdminGr.path("/tenant").get(String.class);

        rTAdmin = createHttpsClient(ROOTTENANTADMIN, AD_PASS_WORD, baseUrls);
        rTAdmin.path("/tenant").get(String.class);
        rTAdminGr = createHttpsClient(TENANTADMIN, AD_PASS_WORD, baseUrls);
        rTAdminGr.path("/tenant").get(String.class);

        rSTAdmin1 = createHttpsClient(SUBTENANT1_ADMIN, AD_PASS_WORD, baseUrls);
        rSTAdminGr1 = createHttpsClient(SUBTENANT1_ADMIN2, AD_PASS_WORD, baseUrls);

        rSTAdmin2 = createHttpsClient(SUBTENANT2_ADMIN, AD_PASS_WORD, baseUrls);
        rSTAdminGr2 = createHttpsClient(SUBTENANT2_ADMIN2, AD_PASS_WORD, baseUrls);

        rST2User = createHttpsClient(SUBTENANT2_USER, AD_PASS_WORD, baseUrls);

        rProjRead = createHttpsClient(SUBTENANT1_READER, AD_PASS_WORD, baseUrls);
        rProjUserGr = createHttpsClient(SUBTENANT1_USER, AD_PASS_WORD, baseUrls);

        rUnAuth = createHttpsClient(ROOTUSER, AD_PASS_WORD, baseUrls);
        rRootUser2 = createHttpsClient(ROOTUSER2, AD_PASS_WORD, baseUrls);

        rSTAdmin3 = createHttpsClient(SUBTENANT3_ADMIN, AD_PASS_WORD, baseUrls);

        rLdaps = createHttpsClient(LDAPS_USER, LDAPS_PASS_WORD, baseUrls);

        rST13User = createHttpsClient(SUBTENANT13_USER, AD_PASS_WORD, baseUrls);

        rSTCross = createHttpsClient(CROSS_TENANT_USER, AD_PASS_WORD, baseUrls);
    }

    public void logoutUser(BalancedWebResource resource) {
        resource.path("/logout")
                .queryParam("force", "true")
                .queryParam("proxytokens", "true")
                .get(ClientResponse.class);
    }

    protected void tearDownHttpsResources() {
        logoutUser(rProxyUser);
        logoutUser(rMon);
        logoutUser(rZAdmin);
        logoutUser(rZAdminGr);
        logoutUser(rTAdmin);
        logoutUser(rTAdminGr);
        logoutUser(rSTAdmin1);
        logoutUser(rSTAdminGr1);
        logoutUser(rSTAdmin2);
        logoutUser(rSTAdminGr2);
        logoutUser(rST2User);
        logoutUser(rProjRead);
        logoutUser(rProjUserGr);
        logoutUser(rUnAuth);
        logoutUser(rRootUser2);
        logoutUser(rSTAdmin3);
        logoutUser(rLdaps);
        logoutUser(rST13User);
        logoutUser(rSTCross);
        logoutUser(rSys);
    }

    protected void setupLicenseAndInitialPasswords() throws NoSuchAlgorithmException {
        rSys = createHttpsClient(SYSADMIN, SYSADMIN_PASS_WORD, baseUrls);
        rSys.path("/tenant").get(String.class);

        // Initialize proxyuser password to ChangeMe
        String usernames[] = { "sysmonitor", "proxyuser" };
        String pass_word = "ChangeMe1!";
        ClientResponse resp = null;

        for (String username : usernames) {
            PasswordResetParam params = new PasswordResetParam();
            params.setUsername(username);
            params.setPassword(pass_word);
            resp = rSys.path("/password/reset").
                    put(ClientResponse.class, params);
            Assert.assertThat(resp.getStatus(), anyOf(is(200), is(400)));

            waitForClusterToBeStable();
        }

        if (!isControllerLicensed()) {
            addControllerLicense();
        }
    }

    /**
     * waits until the cluster is stable
     */
    protected void waitForClusterToBeStable() {
        ClientResponse resp;
        String info = "";
        Boolean notStable = true;

        while (notStable) {
            try {
                Thread.sleep(2000);
                System.out.println("Waiting for stable cluster state.");
            } catch (InterruptedException e) {
                _log.error(e.getMessage(), e);
            }

            try {
                resp = rSys.path("/upgrade/cluster-state").get(ClientResponse.class);
            } catch (ClientHandlerException e) {
                _log.warn(
                        "Caught ClientHandlerException while waiting for cluster to be stable. Continuing...",
                        e);
                continue;
            }
            info = resp.getEntity(String.class);
            if (info.contains("<cluster_state>STABLE</cluster_state>")) {
                notStable = false;
                System.out.println("Cluster state is stable.");
            }

        }
        boolean apiSvcUp = false;
        while (!apiSvcUp) {
            resp = rSys.path("/tenant").get(ClientResponse.class);
            if (resp.getStatus() != 503) {
                apiSvcUp = true;
            } else {
                try {
                    Thread.sleep(2000);
                    System.out.println("Waiting apisvc to be up");
                } catch (InterruptedException e) {
                    _log.error(e.getMessage(), e);
                }
            }
        }
    }

    /**
     * create the httpclient, returns a BalancedWebResource that can be used the same
     * way a WebResource is.
     */
    protected BalancedWebResource createHttpClient(final String username, final String attribute, final String groups,
            List<String> hostNames) {
        BalancedWebResource lbw = new BalancedWebResource();
        for (String h : hostNames) {
            final ClientConfig config = new DefaultClientConfig();
            final Client c = Client.create(config);
            c.addFilter(new ClientFilter() {
                @Override
                public ClientResponse handle(ClientRequest request) throws ClientHandlerException {
                    ArrayList<Object> headerValue = new ArrayList<Object>();
                    String userAndAttribute = username;
                    if (!attribute.isEmpty()) {
                        userAndAttribute += "," + attribute;
                    }
                    headerValue.add(userAndAttribute + ";" + groups);
                    request.getHeaders().put(NoAuthHeaderUserFilter.USER_INFO_HEADER_TAG, headerValue);
                    return getNext().handle(request);
                }
            });
            lbw.addWebResource(c.resource(h));
        }
        return lbw;
    }

    public static String AUTH_TOKEN_HEADER = "X-SDS-AUTH-TOKEN";
    public static String AUTH_PROXY_TOKEN_HEADER = "X-SDS-AUTH-PROXY-TOKEN";
    protected Map<String, Object> _savedTokens = new HashMap<String, Object>();
    protected Map<String, Object> _savedProxyTokens = new HashMap<String, Object>();

    /**
     * create the httpsclient, returns a BalancedWebResource that can be used the same
     * way a WebResource is.
     */
    protected BalancedWebResource createHttpsClient(final String username, final String password, List<String> hosts)
            throws NoSuchAlgorithmException {
        return createHttpsClient(username, password, hosts, true);
    }

    /**
     * Use this when you need to toggle authfilters on and off, to test more specific behavior with tokens and
     * no credentials
     */

    protected String _lastUsedAuthTokenCookie = null;

    protected BalancedWebResource createHttpsClient(final String username, final String password, List<String> hosts,
            boolean addAuthFilters) throws NoSuchAlgorithmException {
        String verb = System.getenv("API_TEST_VERBOSE");
        boolean verbose = false;
        if (verb != null && verb.equalsIgnoreCase("true")) {
            verbose = true;
        }

        // Disable server certificate validation as we are using
        // self-signed certificate
        disableCertificateValidation();

        BalancedWebResource lbw = new BalancedWebResource();
        for (String h : hosts) {
            final ClientConfig config = new DefaultClientConfig();

            final Client c = Client.create(config);

            if (verbose) {
                c.addFilter(new LoggingFilter());
            }
            if (addAuthFilters) {
                c.setFollowRedirects(false);
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

                        // save cookies for post request processing. Not being used in request itself.
                        // this only used to inspect cookies after the fact.
                        if (null == _lastUsedAuthTokenCookie) {
                            System.out.println("Cookie was null");
                            List<NewCookie> allCookies = response.getCookies();
                            System.out.println("Cookie list size:" + allCookies.size());
                            for (NewCookie ck : allCookies) {
                                System.out.print("Cookie name " + ck.getName());
                                if (ck.getName().equals(AUTH_TOKEN_HEADER)) {
                                    _lastUsedAuthTokenCookie = ck.getValue();
                                    break;
                                }
                            }
                        }

                        if (response.getHeaders() != null && response.getHeaders().get(AUTH_TOKEN_HEADER) != null) {
                            _savedTokens.put(username, response.getHeaders().getFirst(AUTH_TOKEN_HEADER));
                        }
                        if (response.getHeaders() != null && response.getHeaders().get(AUTH_PROXY_TOKEN_HEADER) != null) {
                            _savedProxyTokens.put(username, response.getHeaders().getFirst(AUTH_PROXY_TOKEN_HEADER));
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
                c.setFollowRedirects(true);
            }
            lbw.addWebResource(c.resource(h));
        }
        return lbw;
    }

    /**
     * Use this client if you want to use cookies instead of the http headers for holding the
     * auth token
     * */
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

    public static void disableCertificateValidation() {
        // Create a trust manager that does not validate certificate chains
        final TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                    @Override
                    public void checkClientTrusted(final X509Certificate[] certs, final String authType) {
                    }

                    @Override
                    public void checkServerTrusted(final X509Certificate[] certs, final String authType) {
                    }
                }
        };

        // Ignore differences between given hostname and certificate hostname
        final HostnameVerifier hv = new HostnameVerifier() {
            @Override
            public boolean verify(final String hostname, final SSLSession session) {
                return true;
            }
        };

        // Install the all-trusting trust manager
        try {
            final SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(hv);
        } catch (final Exception e) {
            _log.error(e.getMessage(), e);
        }
    }

    /**
     * Some utilities structures
     * 
     * 
     * 
     */
    protected static class ListElement {
        @XmlElement
        public URI id;
        @XmlElement
        public String name;
    }

    @XmlRootElement(name = "subtenants")
    protected static class TenantList {
        public TenantList() {
            _subtenants = new ArrayList<ListElement>();
        }

        @XmlElement(name = "tenant")
        public List<ListElement> _subtenants;
    }

    @XmlRootElement(name = "projects")
    public static class ProjectList {

        @XmlElement(name = "project")
        public List<ProjectEntry> _projects = new ArrayList<ProjectEntry>();
    }

    @XmlRootElement(name = "tenant_project")
    public static class ProjectEntry implements Comparable {
        @XmlElement
        public URI id;

        @XmlElement
        public String name;

        public ProjectEntry() {
        }

        public ProjectEntry(URI i, String n) {
            id = i;
            name = n;
        }

        public ProjectEntry(ProjectEntry el) {
            id = el.id;
            name = el.name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ProjectEntry that = (ProjectEntry) o;
            if (id != null ? !id.equals(that.id) : that.id != null) {
                return false;
            }
            if (name != null ? !name.equals(that.name) : that.name != null) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int result = id != null ? id.hashCode() : 0;
            result = 31 * result + (name != null ? name.hashCode() : 0);
            return result;
        }

        @Override
        public int compareTo(Object o) {
            if (!(o instanceof ProjectEntry)) {
                throw new ClassCastException();
            }
            ProjectEntry e = (ProjectEntry) o;
            return name.compareTo(e.name);
        }
    }

    /**
     * Utilities conversion methods
     * 
     * 
     * 
     */
    protected StringSetMap convertRolesToMap(List<RoleAssignmentEntry> entries) {
        StringSetMap assignments = new StringSetMap();
        if (entries != null && !entries.isEmpty()) {
            for (RoleAssignmentEntry roleAssignment : entries) {
                PermissionsKey key;
                if (roleAssignment.getGroup() != null) {
                    key = new PermissionsKey(PermissionsKey.Type.GROUP, roleAssignment.getGroup());
                } else if (roleAssignment.getSubjectId() != null) {
                    key = new PermissionsKey(PermissionsKey.Type.SID, roleAssignment.getSubjectId());
                } else {
                    continue;
                }
                for (String role : roleAssignment.getRoles()) {
                    assignments.put(key.toString(), role);
                }
            }
        }
        return assignments;
    }

    protected StringSetMap convertAclsToMap(List<ACLEntry> entries) {
        StringSetMap assignments = new StringSetMap();
        if (entries != null && !entries.isEmpty()) {
            for (ACLEntry acl : entries) {
                PermissionsKey key;
                if (acl.getGroup() != null) {
                    key = new PermissionsKey(PermissionsKey.Type.GROUP, acl.getGroup());
                } else if (acl.getSubjectId() != null) {
                    key = new PermissionsKey(PermissionsKey.Type.SID, acl.getSubjectId());
                } else if (acl.getTenant() != null) {
                    key = new PermissionsKey(PermissionsKey.Type.TENANT, acl.getTenant());
                } else {
                    continue;
                }
                for (String role : acl.getAces()) {
                    assignments.put(key.toString(), role.toUpperCase());
                }
            }
        }
        return assignments;
    }

    protected boolean checkEqualsRoles(List<RoleAssignmentEntry> expected,
            List<RoleAssignmentEntry> got) {
        if (expected != null && got != null) {
            if (expected.size() != got.size()) {
                return false;
            }
            if (!convertRolesToMap(expected).equals(convertRolesToMap(got))) {
                return false;
            }
            return true;
        }
        return false;
    }

    protected boolean checkEqualsAcls(List<ACLEntry> expected,
            List<ACLEntry> got) {
        if (expected != null && got != null) {
            if (expected.size() != got.size()) {
                return false;
            }
            if (!convertAclsToMap(expected).equals(convertAclsToMap(got))) {
                return false;
            }
            return true;
        }
        return false;
    }

    protected boolean checkEqualsList(List<ProjectEntry> gotElements, List<ProjectEntry> expected) {
        if (expected != null && gotElements != null) {
            if (expected.size() != gotElements.size()) {
                return false;
            }
            Collections.sort(gotElements);
            Collections.sort(expected);
            if (!gotElements.equals(expected)) {
                return false;
            }
            return true;
        }
        return false;
    }

    protected void assertExpectedError(final ClientResponse actualResponse,
            final int expectedStatusCode, final ServiceCode expectedServiceCode,
            final String expectedMessage) {
        Assert.assertEquals(expectedStatusCode, actualResponse.getStatus());
        try {
            final ServiceErrorRestRep error =
                    actualResponse.getEntity(ServiceErrorRestRep.class);
            assertServiceError(expectedServiceCode.getCode(),
                    expectedServiceCode.getSummary(), expectedMessage, error);
        } catch (final ClientHandlerException e) {
            Assert.fail("Expected a ServiceError object");
        }
    }

    private void assertServiceError(final int expectedServiceCode,
            final String expectedDescription, final String expectedMessage,
            final ServiceErrorRestRep actualError) {
        Assert.assertEquals(expectedServiceCode, actualError.getCode());
        Assert.assertEquals(expectedDescription, actualError.getCodeDescription());
        Assert.assertEquals(expectedMessage, actualError.getDetailedMessage());
    }

}
