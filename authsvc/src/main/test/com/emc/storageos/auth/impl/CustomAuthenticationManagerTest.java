/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.auth.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.emc.storageos.auth.LdapFailureHandler;
import com.emc.storageos.services.util.EnvConfig;
import org.junit.*;
import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.auth.AuthenticationManager.ValidationFailureReason;
import com.emc.storageos.security.password.InvalidLoginManager;
import com.emc.storageos.auth.StorageOSAuthenticationHandler;
import com.emc.storageos.auth.StorageOSPersonAttributeDao;
import com.emc.storageos.auth.TestCoordinator;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.AuthnProvider;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.StorageOSUserDAO;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.server.geo.DbsvcGeoTestBase;
import com.emc.storageos.security.authentication.Base64TokenEncoder;
import com.emc.storageos.security.authentication.TokenKeyGenerator;
import com.emc.storageos.security.authentication.TokenMaxLifeValuesHolder;
import com.emc.storageos.security.authorization.BasePermissionsHelper.UserMapping;
import com.emc.storageos.security.authorization.BasePermissionsHelper.UserMappingAttribute;
import com.emc.storageos.security.resource.UserInfoPage.UserDetails;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * Unit test for custom authentication manager
 */
public class CustomAuthenticationManagerTest extends DbsvcGeoTestBase {

    private static final String LDAP_SERVER_1 = EnvConfig.get("sanity", "authsvc.CustomAuthenticationManagerTest.ldapServerURL1");
    private static final String LDAP_SERVER_2 = EnvConfig.get("sanity", "authsvc.CustomAuthenticationManagerTest.ldapServerURL2");

    private final Logger _log = LoggerFactory.getLogger(CustomAuthenticationManagerTest.class);
    private static final int _INITIAL_HANDLERS = 1;
    private final Base64TokenEncoder _encoder = new Base64TokenEncoder();
    private final TokenMaxLifeValuesHolder _holder = new TokenMaxLifeValuesHolder();
    private final TokenKeyGenerator _tokenKeyGenerator = new TokenKeyGenerator();
    private final CustomAuthenticationManager _authManager = new CustomAuthenticationManager();
    private final CassandraTokenManager _tokenManager = new CassandraTokenManager();
    private DbClient _dbClient;
    private final CoordinatorClient _coordinator = new TestCoordinator();
    private URI _subtenantId;
    private URI _rootTenantId;
    private InvalidLoginManager _invalidLoginManager = new InvalidLoginManager();

    private static final String _adManagerPassword = EnvConfig.get("sanity", "ad.manager.password");

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        _dbClient = getDbClient();
        cleanupProviders();
        _tokenManager.setCoordinator(_coordinator);
        _tokenManager.setDbClient(_dbClient);
        _encoder.setCoordinator(_coordinator);
        _tokenKeyGenerator.setTokenMaxLifeValuesHolder(_holder);
        _encoder.setTokenKeyGenerator(_tokenKeyGenerator);
        _encoder.managerInit();
        _tokenManager.setTokenEncoder(_encoder);
        _authManager.setDbClient(_dbClient);
        _authManager.setCoordinator(_coordinator);
        _authManager.setLocalAuthenticationProvider(new AuthenticationProvider(new TestStorageOSAuthenticationHandler(),
                new TestStorageOSPersonAttributeDao()));
        _authManager.setTokenManager(_tokenManager);
        // get root tenant, save root tenant id
        URIQueryResultList tenants = new URIQueryResultList();
        _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getTenantOrgSubTenantConstraint(URI.create(TenantOrg.NO_PARENT)),
                tenants);
        _rootTenantId = tenants.iterator().next();
        // get subtenants and delete them
        tenants = new URIQueryResultList();
        _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getTenantOrgSubTenantConstraint(URI.create(TenantOrg.PROVIDER_TENANT_ORG)),
                tenants);
        // cleanup subtenants. It's ok to use removeObject, we are not creating any dependant projects or
        // resources in these tests.
        List<TenantOrg> deleteTenants = _dbClient.queryObject(TenantOrg.class, tenants);
        for (TenantOrg tenant : deleteTenants) {
            _dbClient.removeObject(tenant);
        }
        _authManager.init();
        _invalidLoginManager.setCoordinator(_coordinator);
        _invalidLoginManager.init();

    }

    @After
    public void shutdown() {
        _invalidLoginManager.shutdown();
    }

    private void createADLDAPProviders() throws Exception {
        // Create the a good authConfig
        AuthnProvider adAuthConfig = new AuthnProvider();
        adAuthConfig.setId(URIUtil.createId(AuthnProvider.class));
        adAuthConfig.setMode("ad");
        StringSet adDomains = new StringSet();
        adDomains.add("sanity.local");
        adAuthConfig.setDomains(adDomains);
        adAuthConfig.setManagerDN("CN=Administrator,CN=Users,DC=sanity,DC=local");
        adAuthConfig.setManagerPassword(_adManagerPassword);
        StringSet adUrls = new StringSet();
        adUrls.add(LDAP_SERVER_1);
        adAuthConfig.setServerUrls(adUrls);
        adAuthConfig.setSearchBase("CN=Users,DC=sanity,DC=local");
        adAuthConfig.setSearchFilter("userPrincipalName=%u");
        adAuthConfig.setGroupAttribute("CN");
        adAuthConfig.setLastModified(System.currentTimeMillis());
        _log.info("adding new provider");
        _dbClient.createObject(adAuthConfig);

        // Create an LDAP auth config
        AuthnProvider ldapAuthConfig = new AuthnProvider();
        ldapAuthConfig.setId(URIUtil.createId(AuthnProvider.class));
        ldapAuthConfig.setMode("ldap");
        StringSet ldapDomains = new StringSet();
        ldapDomains.add("root.com");
        ldapAuthConfig.setDomains(ldapDomains);
        ldapAuthConfig.setManagerDN("cn=Manager,dc=root,dc=com");
        ldapAuthConfig.setManagerPassword("secret");
        StringSet ldapURLs = new StringSet();
        ldapURLs.add(LDAP_SERVER_2);
        ldapAuthConfig.setServerUrls(ldapURLs);
        ldapAuthConfig.setSearchBase("ou=People,dc=root,dc=com");
        ldapAuthConfig.setSearchFilter("(uid=%U)");
        ldapAuthConfig.setLastModified(System.currentTimeMillis());
        _dbClient.createObject(ldapAuthConfig);
        reloadConfig(true);
    }

    private void reloadConfig(boolean force) throws Exception {
        _log.info("triggering reload");
        _authManager.reload();
        Thread.sleep(3 * 1000);
        _log.info("reload wait done");
    }

    @Test
    public void testreload() throws Exception {
        List<AuthenticationProvider> authProvidersList = _authManager.getAuthenticationProviders();
        Assert.assertEquals(_INITIAL_HANDLERS, authProvidersList.size());

        // Create the a good authConfig
        AuthnProvider adAuthConfig = new AuthnProvider();
        adAuthConfig.setId(URIUtil.createId(AuthnProvider.class));
        adAuthConfig.setMode("ad");
        StringSet adDomains = new StringSet();
        adDomains.add("sanity.local");
        adAuthConfig.setDomains(adDomains);
        adAuthConfig.setManagerDN("CN=Administrator,CN=Users,DC=sanity,DC=local");
        adAuthConfig.setManagerPassword(_adManagerPassword);
        StringSet adUrls = new StringSet();
        adUrls.add(LDAP_SERVER_2);
        adAuthConfig.setServerUrls(adUrls);
        adAuthConfig.setSearchBase("CN=Users,DC=sanity,DC=local");
        adAuthConfig.setSearchFilter("userPrincipalName=%u");
        adAuthConfig.setGroupAttribute("CN");
        adAuthConfig.setLastModified(System.currentTimeMillis());
        _log.info("adding new provider");
        _dbClient.createObject(adAuthConfig);
        // force db error
        _dbClient.stop();
        _log.info("dbclient stopped");
        reloadConfig(true);
        _log.info("sleep for dbclient timeout");
        authProvidersList = _authManager.getAuthenticationProviders();
        Assert.assertEquals(_INITIAL_HANDLERS, authProvidersList.size());
        // Looks like astyannax upgrade introduced a longer (5min) timeout on dbclient connections
        // we need to investigate that further ... for now, increasing this timeout for this test to continue
        Thread.sleep(5 * 60 * 1000);
        _log.info("restarting dbclient");
        _dbClient = getDbClient();
        _authManager.setDbClient(_dbClient);
        // wait for dbclient to come up
        Thread.sleep(60 * 1000);
        // The AD auth handler should now be in the list
        authProvidersList = _authManager.getAuthenticationProviders();
        Assert.assertEquals(_INITIAL_HANDLERS + 1, authProvidersList.size());

        // Create the authConfig with some unknown mode
        StringSet domains = new StringSet();
        domains.add("somedomain");
        StringSet urls = new StringSet();
        urls.add("ldap://somehost");

        // Create the authConfig with a null manager dn
        AuthnProvider badManagerAuthConfig = new AuthnProvider();
        badManagerAuthConfig.setMode("ad");
        badManagerAuthConfig.setId(URIUtil.createId(AuthnProvider.class));
        badManagerAuthConfig.setDomains(domains);
        badManagerAuthConfig.setManagerDN(null);
        badManagerAuthConfig.setManagerPassword(_adManagerPassword);

        badManagerAuthConfig.setServerUrls(urls);
        badManagerAuthConfig.setSearchBase("CN=Users,DC=sanity,DC=local");
        badManagerAuthConfig.setSearchFilter("sAMAccountName=%U");
        badManagerAuthConfig.setGroupAttribute("CN");
        badManagerAuthConfig.setLastModified(System.currentTimeMillis());
        _dbClient.createObject(badManagerAuthConfig);

        reloadConfig(true);
        // The null manager should not have been added to the list
        authProvidersList = _authManager.getAuthenticationProviders();
        Assert.assertEquals(_INITIAL_HANDLERS + 1, authProvidersList.size());
        _dbClient.removeObject(badManagerAuthConfig);
        _authManager.reload();

        // Create the authConfig with a null password
        AuthnProvider badPasswordAuthConfig = new AuthnProvider();
        badPasswordAuthConfig.setMode("ad");
        badPasswordAuthConfig.setId(URIUtil.createId(AuthnProvider.class));
        badPasswordAuthConfig.setDomains(domains);
        badPasswordAuthConfig.setManagerDN("CN=Users,DC=sanity,DC=local");
        badPasswordAuthConfig.setManagerPassword(null);

        badPasswordAuthConfig.setServerUrls(urls);
        badPasswordAuthConfig.setSearchBase("CN=Users,DC=sanity,DC=local");
        badPasswordAuthConfig.setSearchFilter("sAMAccountName=%U");
        badPasswordAuthConfig.setGroupAttribute("CN");
        badPasswordAuthConfig.setLastModified(System.currentTimeMillis());
        _dbClient.createObject(badPasswordAuthConfig);

        reloadConfig(true);
        // The null password should not have been added to the list
        authProvidersList = _authManager.getAuthenticationProviders();
        Assert.assertEquals(_INITIAL_HANDLERS + 1, authProvidersList.size());
        _dbClient.removeObject(badPasswordAuthConfig);
        _authManager.reload();

        // Create the authConfig with no URLs
        AuthnProvider noUrlsAuthConfig = new AuthnProvider();
        noUrlsAuthConfig.setId(URIUtil.createId(AuthnProvider.class));
        noUrlsAuthConfig.setMode("ad");
        noUrlsAuthConfig.setDomains(domains);
        noUrlsAuthConfig.setManagerDN("CN=Users,DC=sanity,DC=local");
        noUrlsAuthConfig.setManagerPassword("P@ssword");
        noUrlsAuthConfig.setSearchBase("CN=Users,DC=sanity,DC=local");
        noUrlsAuthConfig.setSearchFilter("sAMAccountName=%U");
        noUrlsAuthConfig.setGroupAttribute("CN");
        noUrlsAuthConfig.setLastModified(System.currentTimeMillis());
        _dbClient.createObject(noUrlsAuthConfig);

        reloadConfig(true);
        // The no URLs config should not have been added to the list
        authProvidersList = _authManager.getAuthenticationProviders();
        Assert.assertEquals(_INITIAL_HANDLERS + 1, authProvidersList.size());
        _dbClient.removeObject(noUrlsAuthConfig);
        _authManager.reload();

        // Create the authConfig with a null search base
        AuthnProvider nullSearchBaseAuthConfig = new AuthnProvider();
        nullSearchBaseAuthConfig.setId(URIUtil.createId(AuthnProvider.class));
        nullSearchBaseAuthConfig.setMode("ad");
        nullSearchBaseAuthConfig.setDomains(domains);
        nullSearchBaseAuthConfig.setManagerDN("CN=Users,DC=sanity,DC=local");
        nullSearchBaseAuthConfig.setManagerPassword("P@ssword");

        nullSearchBaseAuthConfig.setServerUrls(urls);
        nullSearchBaseAuthConfig.setSearchBase(null);
        nullSearchBaseAuthConfig.setSearchFilter("sAMAccountName=%U");
        nullSearchBaseAuthConfig.setGroupAttribute("CN");
        nullSearchBaseAuthConfig.setLastModified(System.currentTimeMillis());
        _dbClient.createObject(nullSearchBaseAuthConfig);

        reloadConfig(true);
        // The null search base should not have been added to the list
        authProvidersList = _authManager.getAuthenticationProviders();
        Assert.assertEquals(_INITIAL_HANDLERS + 1, authProvidersList.size());
        _dbClient.removeObject(nullSearchBaseAuthConfig);
        _authManager.reload();

        // Create the authConfig with a null filter
        AuthnProvider nullFilterAuthConfig = new AuthnProvider();
        nullFilterAuthConfig.setId(URIUtil.createId(AuthnProvider.class));
        nullFilterAuthConfig.setMode("ad");
        nullFilterAuthConfig.setDomains(domains);
        nullFilterAuthConfig.setManagerDN("CN=Users,DC=sanity,DC=local");
        nullFilterAuthConfig.setManagerPassword("P@ssword");
        nullFilterAuthConfig.setServerUrls(urls);
        nullFilterAuthConfig.setSearchBase("CN=Users,DC=sanity,DC=local");
        nullFilterAuthConfig.setSearchFilter(null);
        nullFilterAuthConfig.setGroupAttribute("CN");
        nullFilterAuthConfig.setLastModified(System.currentTimeMillis());
        _dbClient.createObject(nullFilterAuthConfig);

        reloadConfig(true);
        // The null search base should not have been added to the list
        authProvidersList = _authManager.getAuthenticationProviders();
        Assert.assertEquals(_INITIAL_HANDLERS + 1, authProvidersList.size());
        _dbClient.removeObject(nullFilterAuthConfig);
        reloadConfig(true);

        // Create the authConfig with a missing search_scope (should be ok, will defaut to one level)
        AuthnProvider nullScope = new AuthnProvider();
        nullScope.setId(URIUtil.createId(AuthnProvider.class));
        nullScope.setMode("ad");
        nullScope.setDomains(domains);
        nullScope.setManagerDN("CN=Users,DC=sanity,DC=local");
        nullScope.setManagerPassword("P@ssword");
        nullScope.setServerUrls(urls);
        nullScope.setSearchBase("CN=Users,DC=sanity,DC=local");
        nullScope.setSearchFilter("sAMAccountName=%U");
        nullScope.setGroupAttribute(null);
        _dbClient.createObject(nullScope);

        reloadConfig(true);
        // The null scope config should still be added to the list
        authProvidersList = _authManager.getAuthenticationProviders();
        Assert.assertEquals(_INITIAL_HANDLERS + 2, authProvidersList.size());

        // Create the authConfig with a bad search_scope (should be ok, will default to onelevel)
        AuthnProvider badScope = new AuthnProvider();
        badScope.setId(URIUtil.createId(AuthnProvider.class));
        badScope.setMode("ad");
        badScope.setDomains(domains);
        badScope.setManagerDN("CN=Users,DC=sanity,DC=local");
        badScope.setManagerPassword("P@ssword");
        badScope.setServerUrls(urls);
        badScope.setSearchBase("CN=Users,DC=sanity,DC=local");
        badScope.setSearchFilter("sAMAccountName=%U");
        badScope.setSearchScope("bad");
        badScope.setGroupAttribute(null);
        _dbClient.createObject(badScope);

        reloadConfig(true);
        // The null scope config should still be added to the list
        authProvidersList = _authManager.getAuthenticationProviders();
        Assert.assertEquals(_INITIAL_HANDLERS + 3, authProvidersList.size());

        // Create the authConfig with a good search_scope
        AuthnProvider goodScope = new AuthnProvider();
        goodScope.setId(URIUtil.createId(AuthnProvider.class));
        goodScope.setMode("ad");
        goodScope.setDomains(domains);
        goodScope.setManagerDN("CN=Users,DC=sanity,DC=local");
        goodScope.setManagerPassword("P@ssword");
        goodScope.setServerUrls(urls);
        goodScope.setSearchBase("CN=Users,DC=sanity,DC=local");
        goodScope.setSearchFilter("sAMAccountName=%U");
        goodScope.setSearchScope(AuthnProvider.SearchScope.SUBTREE.toString());
        goodScope.setGroupAttribute(null);
        _dbClient.createObject(goodScope);

        reloadConfig(true);
        // The null scope config should still be added to the list
        authProvidersList = _authManager.getAuthenticationProviders();
        Assert.assertEquals(_INITIAL_HANDLERS + 4, authProvidersList.size());

        // Create the authConfig with a null group Attribute
        AuthnProvider nullGroupAttribute = new AuthnProvider();
        nullGroupAttribute.setId(URIUtil.createId(AuthnProvider.class));
        nullGroupAttribute.setMode("ad");
        nullGroupAttribute.setDomains(domains);
        nullGroupAttribute.setManagerDN("CN=Users,DC=sanity,DC=local");
        nullGroupAttribute.setManagerPassword("P@ssword");

        nullGroupAttribute.setServerUrls(urls);
        nullGroupAttribute.setSearchBase("CN=Users,DC=sanity,DC=local");
        nullGroupAttribute.setSearchFilter("sAMAccountName=%U");
        nullGroupAttribute.setGroupAttribute(null);
        nullGroupAttribute.setLastModified(System.currentTimeMillis());
        _dbClient.createObject(nullGroupAttribute);

        reloadConfig(true);
        // The null group attribute config should still be added to the list
        authProvidersList = _authManager.getAuthenticationProviders();
        Assert.assertEquals(_INITIAL_HANDLERS + 5, authProvidersList.size());

        // Create an LDAP auth config
        AuthnProvider ldapAuthConfig = new AuthnProvider();
        ldapAuthConfig.setId(URIUtil.createId(AuthnProvider.class));
        ldapAuthConfig.setMode("ldap");
        StringSet ldapDomains = new StringSet();
        ldapDomains.add("root.com");
        ldapAuthConfig.setDomains(ldapDomains);
        ldapAuthConfig.setManagerDN("cn=Manager,dc=root,dc=com");
        ldapAuthConfig.setManagerPassword("secret");
        StringSet ldapURLs = new StringSet();
        ldapURLs.add(LDAP_SERVER_1);
        ldapAuthConfig.setServerUrls(ldapURLs);
        ldapAuthConfig.setSearchBase("ou=People,dc=root,dc=com");
        ldapAuthConfig.setSearchFilter("(uid=%U)");
        ldapAuthConfig.setLastModified(System.currentTimeMillis());
        _dbClient.createObject(ldapAuthConfig);

        reloadConfig(true);
        // The ldap auth handler should be on the list
        authProvidersList = _authManager.getAuthenticationProviders();
        Assert.assertEquals(_INITIAL_HANDLERS + 6, authProvidersList.size());

        // Disable a config and make sure it goes away
        ldapAuthConfig.setDisable(true);
        ldapAuthConfig.setLastModified(System.currentTimeMillis());
        _dbClient.persistObject(ldapAuthConfig);

        reloadConfig(true);
        // The ldap auth handler should not be on the list
        authProvidersList = _authManager.getAuthenticationProviders();
        Assert.assertEquals(_INITIAL_HANDLERS + 5, authProvidersList.size());

        // enable th config and make sure it comes back
        ldapAuthConfig.setDisable(false);
        ldapAuthConfig.setLastModified(System.currentTimeMillis());
        _dbClient.persistObject(ldapAuthConfig);

        reloadConfig(true);
        // The ldap auth handler should be on the list
        authProvidersList = _authManager.getAuthenticationProviders();
        Assert.assertEquals(_INITIAL_HANDLERS + 6, authProvidersList.size());

        // Delete it and verify that it is gone
        _dbClient.removeObject(ldapAuthConfig);

        reloadConfig(true);
        // The ldap auth handler should be on the list
        authProvidersList = _authManager.getAuthenticationProviders();
        Assert.assertEquals(_INITIAL_HANDLERS + 5, authProvidersList.size());

        // Add it back. Later tests use it
        ldapAuthConfig.setId(URIUtil.createId(AuthnProvider.class));
        ldapAuthConfig.setDisable(false);
        ldapAuthConfig.setInactive(false);
        ldapAuthConfig.setLastModified(System.currentTimeMillis());
        _dbClient.persistObject(ldapAuthConfig);

        reloadConfig(true);
        // The ldap auth handler should be on the list
        authProvidersList = _authManager.getAuthenticationProviders();
        Assert.assertEquals(_INITIAL_HANDLERS + 6, authProvidersList.size());
    }

    @Test
    public void testAuthentication() throws Exception {
        createADLDAPProviders();
        UsernamePasswordCredentials sanityUserCreds = new UsernamePasswordCredentials("sanity_user@sanity.local", "P@ssw0rd");
        Assert.assertNotNull(_authManager.authenticate(sanityUserCreds));

        UsernamePasswordCredentials ldapUserCreds = new UsernamePasswordCredentials("user@root.com", "password");
        Assert.assertNotNull(_authManager.authenticate(ldapUserCreds));

        UsernamePasswordCredentials badDomainUserCreds = new UsernamePasswordCredentials("sanity_user@baddomain", "P@ssw0rd");
        Assert.assertNull(_authManager.authenticate(badDomainUserCreds));

        UsernamePasswordCredentials noDomainUserCreds = new UsernamePasswordCredentials("sanity_user", "P@ssw0rd");
        Assert.assertNull(_authManager.authenticate(noDomainUserCreds));

        UsernamePasswordCredentials badUserUserCreds = new UsernamePasswordCredentials("sanity_user@root.com", "P@ssw0rd");
        Assert.assertNull(_authManager.authenticate(badUserUserCreds));

        UsernamePasswordCredentials badPasswordUserCreds = new UsernamePasswordCredentials("sanity_user@sanity.local", "badpassword");
        Assert.assertNull(_authManager.authenticate(badPasswordUserCreds));

        UsernamePasswordCredentials emptyUsernameUserCreds = new UsernamePasswordCredentials("", "P@ssw0rd");
        Assert.assertNull(_authManager.authenticate(emptyUsernameUserCreds));

        UsernamePasswordCredentials emptyPasswordUserCreds = new UsernamePasswordCredentials("sanity_user@sanity.local", "");
        Assert.assertNull(_authManager.authenticate(emptyPasswordUserCreds));

        UsernamePasswordCredentials nullPasswordUserCreds = new UsernamePasswordCredentials("sanity_user@sanity.local", null);
        Assert.assertNull(_authManager.authenticate(nullPasswordUserCreds));

        UserMapping tenantMapping = new UserMapping();
        UserMappingAttribute tenantAttr = new UserMappingAttribute();
        tenantAttr.setKey("o");
        tenantAttr.setValues(Collections.singletonList("sales"));
        tenantMapping.setAttributes(Collections.singletonList(tenantAttr));
        tenantMapping.setDomain("root.com");
        UserMapping tenantMapping2 = new UserMapping();
        tenantMapping2.setGroups(Collections.singletonList("Test Group"));
        tenantMapping2.setDomain("sanity.local");

        StringSetMap mappings = new StringSetMap();

        mappings.put(tenantMapping.getDomain(), tenantMapping.toString());
        mappings.put(tenantMapping2.getDomain(), tenantMapping2.toString());

        _subtenantId = URIUtil.createId(TenantOrg.class);
        TenantOrg subtenant = new TenantOrg();
        subtenant.setLabel("subtenant");
        subtenant.setDescription("auth subtenant");
        subtenant.setId(_subtenantId);
        subtenant.setParentTenant(new NamedURI(_rootTenantId, "subtenant"));
        subtenant.setUserMappings(mappings);
        _dbClient.persistObject(subtenant);

        StorageOSUserDAO user = _authManager.authenticate(sanityUserCreds);
        Assert.assertEquals(_rootTenantId.toString(), user.getTenantId());

        // this user has the o=sales attribute so should be in the subtenant
        user = _authManager.authenticate(ldapUserCreds);
        Assert.assertEquals(_subtenantId.toString(), user.getTenantId());

        // this user is in the group Test Group so should be in the subtenant
        UsernamePasswordCredentials groupUserCreds = new UsernamePasswordCredentials("testuser@sanity.local", "P@ssw0rd");
        user = _authManager.authenticate(groupUserCreds);
        Assert.assertEquals(_subtenantId.toString(), user.getTenantId());

        // Create the a good authConfig with whitelist values
        AuthnProvider adAuthConfig = new AuthnProvider();
        adAuthConfig.setId(URIUtil.createId(AuthnProvider.class));
        adAuthConfig.setMode("ad");
        StringSet adDomains = new StringSet();
        adDomains.add("whitelist1");
        adDomains.add("whitelist2");
        adAuthConfig.setDomains(adDomains);
        adAuthConfig.setManagerDN("CN=Administrator,CN=Users,DC=sanity,DC=local");
        adAuthConfig.setManagerPassword(_adManagerPassword);
        StringSet adUrls = new StringSet();
        adUrls.add(LDAP_SERVER_2);
        adAuthConfig.setServerUrls(adUrls);
        adAuthConfig.setSearchBase("CN=Users,DC=sanity,DC=local");
        adAuthConfig.setSearchFilter("sAMAccountName=%U");
        adAuthConfig.setGroupAttribute("CN");
        StringSet whitelistValues = new StringSet();
        whitelistValues.add("*Users*");
        whitelistValues.add("ProjectAdmins");
        adAuthConfig.setGroupWhitelistValues(whitelistValues);
        adAuthConfig.setLastModified(System.currentTimeMillis());
        _dbClient.createObject(adAuthConfig);
        reloadConfig(true);
        // Login the user the user that is in the group "Test Group" but it is not in the whitelist in
        // the auth config so the user should end up in the root tenant
        UsernamePasswordCredentials whitelist1GroupUserCreds = new UsernamePasswordCredentials("testuser@whitelist1", "P@ssw0rd");
        user = _authManager.authenticate(whitelist1GroupUserCreds);
        Assert.assertEquals(_rootTenantId.toString(), user.getTenantId());
        // log the same user in to the other domain to make sure it is mapped to the same domain
        UsernamePasswordCredentials whitelist2GroupUserCreds = new UsernamePasswordCredentials("testuser@whitelist2", "P@ssw0rd");
        user = _authManager.authenticate(whitelist2GroupUserCreds);
        Assert.assertEquals(_rootTenantId.toString(), user.getTenantId());

        ValidationFailureReason[] failureReason = new ValidationFailureReason[1];
        _authManager.validateUser("sanity_user@sanity.local",
                _rootTenantId.toString(), null);

        thrown.expect(APIException.class);
        _authManager.validateUser("sanity_user@sanity.local",
                _subtenantId.toString(), null);

        _authManager.validateUser("sanity_user@sanity.local",
                _subtenantId.toString(), _rootTenantId.toString());

        _authManager.validateUser("user2@root.com",
                _rootTenantId.toString(), null);

        thrown.expect(APIException.class);
        _authManager.validateUser("user2@root.com",
                _subtenantId.toString(), null);

        _authManager.validateUser("user2@root.com",
                _subtenantId.toString(), _rootTenantId.toString());

        _authManager.validateUser("user@root.com",
                _subtenantId.toString(), null);

        thrown.expect(APIException.class);
        _authManager.validateUser("user@root.com",
                _rootTenantId.toString(), null);

        _authManager.validateUser("testuser@sanity.local",
                _subtenantId.toString(), null);

        thrown.expect(APIException.class);
        _authManager.validateUser("testuser@sanity.local",
                _rootTenantId.toString(), null);

        thrown.expect(APIException.class);
        _authManager.validateUser("testuser", _rootTenantId.toString(),
                null);

        thrown.expect(APIException.class);
        _authManager.validateUser("testuser", _subtenantId.toString(), null);

        Assert.assertTrue(_authManager.isGroupValid("Test Group@sanity.local",
                failureReason));
        Assert.assertFalse(_authManager.isGroupValid("Test Group@whitelist1",
                failureReason));
        Assert.assertEquals(failureReason[0],
                ValidationFailureReason.USER_OR_GROUP_NOT_FOUND_FOR_TENANT);
        Assert.assertFalse(_authManager.isGroupValid("Test Group@whitelist2",
                failureReason));
        Assert.assertTrue(_authManager.isGroupValid("Domain Users@whitelist2",
                failureReason));
        Assert.assertTrue(_authManager.isGroupValid("ProjectAdmins@whitelist1",
                failureReason));
        Assert.assertFalse(_authManager.isGroupValid("Test Group", failureReason));

        // Create the a good authConfig with the sid group attribute
        AuthnProvider sidAuthConfig = new AuthnProvider();
        sidAuthConfig.setId(URIUtil.createId(AuthnProvider.class));
        sidAuthConfig.setMode("ad");
        StringSet sidDomains = new StringSet();
        sidDomains.add("sidtest");
        sidAuthConfig.setDomains(sidDomains);
        sidAuthConfig.setManagerDN("CN=Administrator,CN=Users,DC=sanity,DC=local");
        sidAuthConfig.setManagerPassword(_adManagerPassword);
        StringSet sidUrls = new StringSet();
        sidUrls.add(LDAP_SERVER_2);
        sidAuthConfig.setServerUrls(sidUrls);
        sidAuthConfig.setSearchBase("CN=Users,DC=sanity,DC=local");
        sidAuthConfig.setSearchFilter("sAMAccountName=%U");
        sidAuthConfig.setGroupAttribute("objectSid");
        StringSet sidWhitelistValues = new StringSet();
        // Domain users ends in -513
        sidWhitelistValues.add("*-513");
        // Test group SID
        sidWhitelistValues.add("S-1-5-21-2759885641-1951973838-595118951-1135");
        sidAuthConfig.setGroupWhitelistValues(sidWhitelistValues);
        sidAuthConfig.setLastModified(System.currentTimeMillis());
        _dbClient.createObject(sidAuthConfig);
        reloadConfig(true);

        // Create a subtenant using the sid of Domain users from '@sidtest'
        // for mapping
        UserMapping sidGroupMapping = new UserMapping();
        sidGroupMapping.setDomain("sidtest");
        sidGroupMapping.setGroups(Collections.singletonList("S-1-5-21-2759885641-1951973838-595118951-513"));
        StringSetMap sidTestMappings = new StringSetMap();
        sidTestMappings.put(sidGroupMapping.getDomain(), sidGroupMapping.toString());
        URI subtenant2Id = URIUtil.createId(TenantOrg.class);
        TenantOrg subtenant2 = new TenantOrg();
        subtenant2.setLabel("subtenant2");
        subtenant2.setDescription("auth subtenant2");
        subtenant2.setId(subtenant2Id);
        subtenant2.setParentTenant(new NamedURI(_rootTenantId, "subtenant2"));
        subtenant2.setUserMappings(sidTestMappings);
        _dbClient.persistObject(subtenant2);

        // login the sanity_user (sanity_user@sanity.local) and verify that the user is in the
        // root tenant still despite being in 'Domain Users' group because it is a different domain
        user = _authManager.authenticate(sanityUserCreds);
        Assert.assertEquals(_rootTenantId.toString(), user.getTenantId());

        // Now try sanity_user@sidtest and the user should be in subtenant2
        UsernamePasswordCredentials sidTestUserCreds = new UsernamePasswordCredentials("sanity_user@sidtest", "P@ssw0rd");
        user = _authManager.authenticate(sidTestUserCreds);
        Assert.assertEquals(subtenant2Id.toString(), user.getTenantId());

        _authManager.validateUser("sanity_user@sidtest",
                subtenant2Id.toString(), null);
        _authManager.validateUser("testuser@sidtest",
                subtenant2Id.toString(), null);
        _authManager.validateUser("baduser@sidtest",
                subtenant2Id.toString(), null);

        // Test group
        Assert.assertTrue(_authManager.isGroupValid(
                "S-1-5-21-2759885641-1951973838-595118951-1135@sidtest", failureReason));
        // Domain Users
        Assert.assertTrue(_authManager.isGroupValid(
                "S-1-5-21-2759885641-1951973838-595118951-513@sidtest", failureReason));
        // non-existent group
        Assert.assertFalse(_authManager.isGroupValid(
                "S-2-2-21-2759885641-1951973838-595118951-513@sidtest", failureReason));
        // non-whitelist group (ProjectAdmins)
        Assert.assertFalse(_authManager.isGroupValid(
                "S-1-5-21-2759885641-1951973838-595118951-1111@sidtest", failureReason));

        // Create an config with a bad URL
        AuthnProvider ldapAuthConfig = new AuthnProvider();
        ldapAuthConfig.setId(URIUtil.createId(AuthnProvider.class));
        ldapAuthConfig.setMode("ldap");
        StringSet ldapDomains = new StringSet();
        ldapDomains.add("badurl.com");
        ldapAuthConfig.setDomains(ldapDomains);
        ldapAuthConfig.setManagerDN("cn=Manager,dc=root,dc=com");
        ldapAuthConfig.setManagerPassword("secret");
        StringSet ldapURLs = new StringSet();
        ldapURLs.add("ldap://xxx");
        ldapAuthConfig.setServerUrls(ldapURLs);
        ldapAuthConfig.setSearchBase("ou=People,dc=root,dc=com");
        ldapAuthConfig.setSearchFilter("(uid=%U)");
        _dbClient.createObject(ldapAuthConfig);

        UsernamePasswordCredentials badURLUserCreds = new UsernamePasswordCredentials("user@badurl.com", "password");

        // Check that authentication and validation operations fail
        // but do not throw connection exceptions
        user = _authManager.authenticate(badURLUserCreds);
        Assert.assertNull(user);

        thrown.expect(APIException.class);
        _authManager.validateUser("user@badurl.com",
                subtenant2Id.toString(), null);

        Assert.assertFalse(_authManager.isGroupValid("group@badurl.com", failureReason));

        cleanupProviders();

    }

    private void cleanupProviders() throws Exception {
        List<AuthnProvider> providers =
                _dbClient.queryObject(AuthnProvider.class,
                        _dbClient.queryByType(AuthnProvider.class, true));
        _dbClient.markForDeletion(providers);
        reloadConfig(true);
    }

    @Test
    public void testGetUserGroups() throws Exception {
        cleanupProviders();
        AuthnProvider authConfig = createValidAuthProviderInDB();
        final String DOMAIN_USERS_GROUP = "Domain Users@sanity.local";
        final String OUTER_GROUP = "OuterGroup@sanity.local";
        final String INNER_GROUP = "InsideGroup@sanity.local";

        // look for a user with an unsupported domain
        String principalSearchFailedFormat =
                "Search for %s failed for this tenant, or could not be found for this tenant.";
        String user = "invaliduser@invalidDomain.com";
        UserDetails userDetails = null;
        try {
            userDetails = _authManager.getUserDetails(user);
            Assert.assertNull(userDetails);
        } catch (SecurityException e) {
            Assert.fail("Got a SecurityException when BadRequestException was expected. Details: "
                    + e.getLocalizedMessage());
        } catch (BadRequestException e) {
            assertServiceError(HttpStatus.SC_BAD_REQUEST, ServiceCode.API_BAD_REQUEST,
                    String.format(principalSearchFailedFormat, user), e);
        } catch (Exception e) {
            Assert.fail("Got a " + e.getClass().toString()
                    + "when BadRequestException was expected. Details: "
                    + e.getLocalizedMessage());
        }

        // look for a user that doesn't exist
        user = "iShouldntExistAnywhereInTheWholeWideWorld@sanity.local";
        try {
            _authManager.getUserDetails(user);
            Assert.assertNull(userDetails);
        } catch (SecurityException e) {
            Assert.fail("Got a SecurityException when BadRequestException was expected. Details: "
                    + e.getLocalizedMessage());
        } catch (BadRequestException e) {
            assertServiceError(HttpStatus.SC_BAD_REQUEST, ServiceCode.API_BAD_REQUEST,
                    String.format(principalSearchFailedFormat, user), e);
        } catch (Exception e) {
            Assert.fail("Got a " + e.getClass().toString()
                    + "when BadRequestException was expected. Details: "
                    + e.getLocalizedMessage());
        }

        // look for a user that does exist
        user = "userGroupsTestUser@sanity.local";
        try {
            userDetails = _authManager.getUserDetails(user);
            Assert.assertNotNull(userDetails);
            Assert.assertEquals(3, userDetails.getUserGroupList().size());
            Assert.assertTrue(
                    "user is supposed to be part of the root tenant " + _rootTenantId
                            + "but is actually in tenant" + userDetails.getTenant(),
                    _rootTenantId.toString().equals(userDetails.getTenant()));

            boolean isDomainUser = false;
            boolean isInsideGroup = false;
            boolean isOuterGroup = false;
            for (String groupName : userDetails.getUserGroupList()) {
                if (groupName.equalsIgnoreCase(DOMAIN_USERS_GROUP)) {
                    isDomainUser = true;
                } else if (groupName.equalsIgnoreCase(INNER_GROUP)) {
                    isInsideGroup = true;
                } else if (groupName.equalsIgnoreCase(OUTER_GROUP)) {
                    isOuterGroup = true;
                }
            }
            Assert.assertTrue("isDomainUser = " + isDomainUser + ", isInsideGroup = "
                    + isInsideGroup + ", isOuterGroup = " + isOuterGroup, isDomainUser
                    && isInsideGroup && isOuterGroup);

        } catch (SecurityException e) {
            Assert.fail("Got a SecurityException. Details: " + e.getLocalizedMessage());
        } catch (BadRequestException e) {
            Assert.fail("Got a BadRequestException. Details: " + e.getLocalizedMessage());
        } catch (Exception e) {
            Assert.fail("Got a " + e.getClass().toString() + ". Details: "
                    + e.getLocalizedMessage());
        }

        // now test the returned user has the right tenant- it should now be mapped to the
        // subtenant
        UserMapping tenantMapping = new UserMapping();
        tenantMapping.setDomain("sanity.local");
        tenantMapping.setGroups(Collections.singletonList(OUTER_GROUP.split("@")[0]));

        StringSetMap mappings = new StringSetMap();

        mappings.put(tenantMapping.getDomain(), tenantMapping.toString());

        URI subtenantId = URIUtil.createId(TenantOrg.class);
        TenantOrg subtenant = new TenantOrg();
        subtenant.setLabel("subtenant for user groups test");
        subtenant.setDescription("auth subtenan1t");
        subtenant.setId(subtenantId);
        subtenant.setParentTenant(new NamedURI(_rootTenantId, "subtenant"));
        subtenant.setUserMappings(mappings);
        _dbClient.persistObject(subtenant);

        try {
            userDetails = _authManager.getUserDetails(user);
            Assert.assertNotNull(userDetails);
            Assert.assertEquals(3, userDetails.getUserGroupList().size());

            boolean isDomainUser = false;
            boolean isInsideGroup = false;
            boolean isOuterGroup = false;
            for (String groupName : userDetails.getUserGroupList()) {
                if (groupName.equalsIgnoreCase(DOMAIN_USERS_GROUP)) {
                    isDomainUser = true;
                } else if (groupName.equalsIgnoreCase(INNER_GROUP)) {
                    isInsideGroup = true;
                } else if (groupName.equalsIgnoreCase(OUTER_GROUP)) {
                    isOuterGroup = true;
                }
            }
            Assert.assertTrue("isDomainUser = " + isDomainUser + ", isInsideGroup = "
                    + isInsideGroup + ", isOuterGroup = " + isOuterGroup, isDomainUser
                    && isInsideGroup && isOuterGroup);

            Assert.assertTrue(
                    "user is supposed to be part of the subtenant " + subtenantId
                            + " but is actually in tenant " + userDetails.getTenant()
                            + " (root tenant is " + _rootTenantId + " )", subtenantId
                            .toString().equals(userDetails.getTenant()));

        } catch (SecurityException e) {
            Assert.fail("Got a SecurityException. Details: " + e.getLocalizedMessage());
        } catch (BadRequestException e) {
            Assert.fail("Got a BadRequestException. Details: " + e.getLocalizedMessage());
        } catch (Exception e) {
            Assert.fail("Got a " + e.getClass().toString() + ". Details: "
                    + e.getLocalizedMessage());
        }
    }

    @Test
    public void testUserRefresh() throws Exception {
        AuthnProvider authConfig = createValidAuthProviderInDB();

        // First try to refresh a user that does not exist in the DB- Should fail with a
        // BadRequestException, where the message says that the parameter is not valid
        String userName =
                "iShouldntExistAnywhereInTheWholeWideWorld@sanity.local".toLowerCase();
        boolean exceptionWasCaught = false;
        try {
            _authManager.refreshUser(userName);
        } catch (SecurityException e) {
            // should not get here.
            Assert.fail("Got a securityExcpetion instead of BadRequestException, message is "
                    + e.getLocalizedMessage());
        } catch (APIException e) {
            // this is what is expected
            String errorMessage = "Invalid value " + userName + " for parameter username";
            assertServiceError(HttpStatus.SC_BAD_REQUEST,
                    ServiceCode.API_PARAMETER_INVALID, errorMessage, e);
            exceptionWasCaught = true;
        } finally {
            Assert.assertTrue(
                    "Refresh user call for a user that does not exist in DB did not throw an exception",
                    exceptionWasCaught);
        }

        // try to refresh a user that doesn't exist in ldap, but exists in the DB- should
        // fail with a BadRequestException- Search for {0} failed for this tenant, or
        // could not be found for this tenant. make sure the user gets deleted
        StorageOSUserDAO userDAO = new StorageOSUserDAO();
        userDAO.setId(URIUtil.createId(StorageOSUserDAO.class));
        userDAO.setUserName(userName);
        _dbClient.createObject(userDAO);
        exceptionWasCaught = false;
        try {
            _authManager.refreshUser(userName);
        } catch (SecurityException e) {
            Assert.fail("Got a securityExcpetion instead of BadRequestException, message is "
                    + e.getLocalizedMessage());
        } catch (APIException e) {
            String errorMessage =
                    "Search for "
                            + userName
                            + " failed for this tenant, or could not be found for this tenant.";
            assertServiceError(HttpStatus.SC_BAD_REQUEST, ServiceCode.API_BAD_REQUEST,
                    errorMessage, e);
            exceptionWasCaught = true;
        } finally {
            Assert.assertTrue(
                    "Refresh user call for a user that does not exist in LDAP did not throw an exception",
                    exceptionWasCaught);
        }
        StorageOSUserDAO userDAOAfterRefresh =
                _dbClient.queryObject(StorageOSUserDAO.class, userDAO.getId());
        if (userDAOAfterRefresh != null) {
            Assert.assertTrue(userDAOAfterRefresh.getInactive());
        }

        // disable the authProvider and refresh a user- should fail with a
        // BadRequestException - Search for {0} failed for this tenant, or
        // could not be found for this tenant. make sure the user gets deleted
        cleanupProviders();
        userName = "sanity_user@sanity.local".toLowerCase();
        userDAO = new StorageOSUserDAO();
        userDAO.setId(URIUtil.createId(StorageOSUserDAO.class));
        userDAO.setUserName(userName);
        _dbClient.createObject(userDAO);
        exceptionWasCaught = false;
        try {
            _authManager.refreshUser(userName);
        } catch (SecurityException e) {
            Assert.fail("Got a securityExcpetion instead of BadRequestException, message is "
                    + e.getLocalizedMessage());
        } catch (APIException e) {
            String errorMessage =
                    "Search for "
                            + userName
                            + " failed for this tenant, or could not be found for this tenant.";
            assertServiceError(HttpStatus.SC_BAD_REQUEST, ServiceCode.API_BAD_REQUEST,
                    errorMessage, e);
            exceptionWasCaught = true;
        } finally {
            Assert.assertTrue(
                    "Refresh user call for a user who is not supported by any authentication handler did not throw an exception",
                    exceptionWasCaught);
        }

        userDAOAfterRefresh =
                _dbClient.queryObject(StorageOSUserDAO.class, userDAO.getId());
        if (userDAOAfterRefresh != null) {
            Assert.assertTrue(userDAOAfterRefresh.getInactive());
        }

        // enable the authProvider and test user refresh - should not throw
        authConfig = createValidAuthProviderInDB();
        userDAO = new StorageOSUserDAO();
        userDAO.setId(URIUtil.createId(StorageOSUserDAO.class));
        userDAO.setUserName(userName);
        _dbClient.createObject(userDAO);
        try {
            // refresh the user
            _authManager.refreshUser(userName);
        } catch (SecurityException e) {
            Assert.fail("Got a FatalSecurityException, message is "
                    + e.getLocalizedMessage());
        } catch (APIException e) {
            Assert.fail("Got a BadRequestException, message is "
                    + e.getLocalizedMessage());
        }
        userDAOAfterRefresh =
                _dbClient.queryObject(StorageOSUserDAO.class, userDAO.getId());
        Assert.assertNotNull(userDAOAfterRefresh.getTenantId());
        Assert.assertTrue("sanity_user@sanity.local is supposed to be mapped to root tenant",
                _rootTenantId.toString().equals(userDAOAfterRefresh.getTenantId()));
    }

    private AuthnProvider createValidAuthProviderInDB() throws Exception {
        // Create the a good authConfig
        AuthnProvider authConfig = new AuthnProvider();
        authConfig.setId(URIUtil.createId(AuthnProvider.class));
        authConfig.setMode("ad");
        StringSet domains = new StringSet();
        domains.add("sanity.local");
        authConfig.setDomains(domains);
        authConfig.setManagerDN("CN=Administrator,CN=Users,DC=sanity,DC=local");
        authConfig.setManagerPassword(_adManagerPassword);
        StringSet urls = new StringSet();
        urls.add(LDAP_SERVER_2);
        authConfig.setServerUrls(urls);
        authConfig.setSearchBase("CN=Users,DC=sanity,DC=local");
        authConfig.setSearchFilter("sAMAccountName=%U");
        authConfig.setGroupAttribute("CN");
        authConfig.setLastModified(System.currentTimeMillis());
        _dbClient.createObject(authConfig);
        reloadConfig(true);
        return authConfig;
    }

    private void assertServiceError(final int expectedStatusCode,
            final ServiceCode expectedServiceCode, final String expectedMessage,
            final APIException actualError) {
        Assert.assertEquals(actualError.getLocalizedMessage(), expectedStatusCode,
                actualError.getStatus().getStatusCode());
        Assert.assertEquals(actualError.getLocalizedMessage(), expectedServiceCode,
                actualError.getServiceCode());
        Assert.assertEquals(actualError.getLocalizedMessage(), expectedMessage,
                actualError.getLocalizedMessage());
    }

    @Test
    public void testThreadSafety() throws Exception {
        createADLDAPProviders();
        final UsernamePasswordCredentials sanityUserCreds =
                new UsernamePasswordCredentials("sanity_user@sanity.local", "P@ssw0rd");
        final UsernamePasswordCredentials ldapUserCreds =
                new UsernamePasswordCredentials("user@root.com", "password");
        final UsernamePasswordCredentials rootUserCreds =
                new UsernamePasswordCredentials("root", "ChangeMe");

        Callable<Boolean> reload = new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                reloadConfig(true);
                return true;
            }
        };

        Callable<Boolean> authenticateSanity = new Callable<Boolean>() {
            @Override
            public Boolean call() {
                _authManager.authenticate(sanityUserCreds);
                return true;
            }
        };

        Callable<Boolean> authenticateLdap = new Callable<Boolean>() {
            @Override
            public Boolean call() {
                _authManager.authenticate(ldapUserCreds);
                return true;
            }
        };

        Callable<Boolean> authenticateRoot = new Callable<Boolean>() {
            @Override
            public Boolean call() {
                _authManager.authenticate(rootUserCreds);
                return true;
            }
        };
        List<Callable<Boolean>> allTasks = new ArrayList<Callable<Boolean>>();
        List<Callable<Boolean>> reloadTasks = Collections.nCopies(100, reload);
        allTasks.addAll(reloadTasks);
        List<Callable<Boolean>> authenticateSanityTasks =
                Collections.nCopies(100, authenticateSanity);
        allTasks.addAll(authenticateSanityTasks);
        List<Callable<Boolean>> authenticateLdapTasks =
                Collections.nCopies(100, authenticateLdap);
        allTasks.addAll(authenticateLdapTasks);
        List<Callable<Boolean>> authenticateRootTasks =
                Collections.nCopies(100, authenticateRoot);
        allTasks.addAll(authenticateRootTasks);

        ExecutorService executorService = Executors.newFixedThreadPool(allTasks.size());
        List<Future<Boolean>> futures = executorService.invokeAll(allTasks);
        for (Future<Boolean> future : futures) {
            Assert.assertTrue(future.get());
        }

    }

    @Test
    public void testAutoUpdate() throws Exception {

        // _authManager.setCoordinator(_coordinator);
        _authManager.setProviderUpdateCheckMinutes(1);
        // wait for initial load
        Thread.sleep(2 * 1000);
        int authProvidersSize = _authManager.getAuthenticationProviders().size();
        // Create the a good authConfig
        AuthnProvider updateTestConfig = new AuthnProvider();
        updateTestConfig.setId(URIUtil.createId(AuthnProvider.class));
        updateTestConfig.setMode("ad");
        StringSet adDomains = new StringSet();
        adDomains.add("auto-update");
        updateTestConfig.setDomains(adDomains);
        updateTestConfig.setManagerDN("CN=Administrator,CN=Users,DC=sanity,DC=local");
        updateTestConfig.setManagerPassword(_adManagerPassword);
        StringSet adUrls = new StringSet();
        adUrls.add(LDAP_SERVER_2);
        updateTestConfig.setServerUrls(adUrls);
        updateTestConfig.setSearchBase("CN=Users,DC=sanity,DC=local");
        updateTestConfig.setSearchFilter("userPrincipalName=%u");
        updateTestConfig.setGroupAttribute("CN");
        updateTestConfig.setLastModified(System.currentTimeMillis());
        _dbClient.createObject(updateTestConfig);
        // Wait one minute and the providers should be updated automatically
        Thread.sleep(61 * 1000);
        Assert.assertEquals(authProvidersSize + 1, _authManager
                .getAuthenticationProviders().size());

        // Disable the provider and wait a minute. nothing will change because we did not
        // update the time
        updateTestConfig.setDisable(true);
        _dbClient.persistObject(updateTestConfig);
        Thread.sleep(61 * 1000);
        Assert.assertEquals(authProvidersSize + 1, _authManager
                .getAuthenticationProviders().size());
        // now, update the time, and wait for reload
        updateTestConfig.setLastModified(System.currentTimeMillis());
        _dbClient.persistObject(updateTestConfig);
        Thread.sleep(61 * 1000);
        Assert.assertEquals(authProvidersSize, _authManager.getAuthenticationProviders()
                .size());

    }

    private class TestStorageOSAuthenticationHandler implements StorageOSAuthenticationHandler {

        @Override
        public boolean authenticate(Credentials credentials) {
            if (UsernamePasswordCredentials.class.isAssignableFrom(credentials.getClass())) {
                return ((UsernamePasswordCredentials) credentials).getUserName().equals("root")
                        && ((UsernamePasswordCredentials) credentials).getPassword().equals("ChangeMe");
            }
            return false;
        }

        @Override
        public boolean supports(Credentials credentials) {
            if (UsernamePasswordCredentials.class.isAssignableFrom(credentials.getClass())) {
                return ((UsernamePasswordCredentials) credentials).getUserName().equals("root");
            }
            return false;
        }

        @Override
        public void setFailureHandler(LdapFailureHandler failureHandler) {

        }

    }

    private class TestStorageOSPersonAttributeDao implements StorageOSPersonAttributeDao {

        @Override
        public boolean isGroupValid(String groupId,
                ValidationFailureReason[] failureReason) {
            return false;
        }

        @Override
        public void validateUser(String userId, String tenantId, String altTenantId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StorageOSUserDAO getStorageOSUser(Credentials credentials,
                ValidationFailureReason[] failureReason) {
            StorageOSUserDAO user = new StorageOSUserDAO();
            user.setLabel("root");
            user.setIsLocal(true);
            return user;
        }

        @Override
        public StorageOSUserDAO getStorageOSUser(Credentials credentials) {
            return getStorageOSUser(credentials, null);
        }

        @Override
        public Map<URI, UserMapping> getUserTenants(String username) {
            return null;
        }

        @Override
        public Map<URI, UserMapping> peekUserTenants(String username, URI uri, List<UserMapping> userMappings) {
            return null;
        }

        @Override
        public void setFailureHandler(LdapFailureHandler failureHandler) {
            
        }

    }

    @Test
    public void testInvalidLogin() throws Exception {
        _log.info("testInvalidLogin started");
        _invalidLoginManager.markErrorLogin("1.2.3.4");
        _invalidLoginManager.markErrorLogin("1.2.3.4");
        Assert.assertFalse(_invalidLoginManager.isTheClientIPBlocked("1.2.3.4"));
        _invalidLoginManager.markErrorLogin("1.2.3.4");
        _invalidLoginManager.markErrorLogin("1.2.3.4");
        _invalidLoginManager.markErrorLogin("1.2.3.4");
        Assert.assertTrue(_invalidLoginManager.isTheClientIPBlocked("1.2.3.4"));
        Thread.sleep(120 * 1000);
        Assert.assertFalse(_invalidLoginManager.isTheClientIPBlocked("1.2.3.4"));
        _log.info("testInvalidLogin done");
    }
}
