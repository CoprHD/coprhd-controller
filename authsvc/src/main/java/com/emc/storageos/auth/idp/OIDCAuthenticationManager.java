/*
 *
 *  * Copyright (c) 2016 EMC Corporation
 *  * All Rights Reserved
 *
 */

package com.emc.storageos.auth.idp;

import com.emc.storageos.auth.impl.AuthenticationProvider;
import com.emc.storageos.auth.impl.ImmutableAuthenticationProviders;
import com.emc.storageos.auth.impl.TenantMapper;
import com.emc.storageos.auth.service.impl.resource.AuthenticationResource;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.AuthnProvider;
import com.emc.storageos.db.client.model.StorageOSUserDAO;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.security.authorization.BasePermissionsHelper;
import com.emc.storageos.security.keystore.impl.CoordinatorConfigStoringHelper;
import com.emc.storageos.security.keystore.impl.KeyStoreUtil;
import com.emc.storageos.security.ssl.ViPRSSLSocketFactory;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.oauth2.sdk.*;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponse;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponseParser;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;

/**
 *
 */
public class OIDCAuthenticationManager {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationResource.class);

    private DbClient dbClient;
    private CoordinatorClient coordinator;
    private BasePermissionsHelper permissionsHelper;

    private ImmutableAuthenticationProviders authProviders;

    public OIDCAuthenticationManager(CoordinatorClient coordinator, DbClient dbClient, ImmutableAuthenticationProviders authProviders) {
        this.coordinator = coordinator;
        this.dbClient = dbClient;
        this.permissionsHelper = new BasePermissionsHelper(dbClient);
        this.authProviders = authProviders;
        HTTPRequest.setDefaultSSLSocketFactory(new ViPRSSLSocketFactory(coordinator));
        HTTPRequest.setDefaultHostnameVerifier(new CustomHostnameVerifier());
        log.info("Set default ssl socket factory to vipr's");
    }

    public URI buildAuthenticationRequestInURI(String resourceURI) throws Exception {
        State state = new State(resourceURI);
        AuthenticationRequest req = new AuthenticationRequest(
                null, // url to provider
                new ResponseType(ResponseType.Value.CODE),
                Scope.parse("openid email profile address"),
                new ClientID(getOidcAuthProvider().getOidcClientId()),
                URI.create(getOidcAuthProvider().getOidcCallBackUrl()), // redirect uri
                state,
                null // nonce
        );

        return buildAuthLocation(req);
    }

    public StorageOSUserDAO authenticate(String code) {
        try {
            String idToken = requestToken(code);
            validateToken(idToken);
            return populateUserInfo(idToken);
        } catch (Exception e) {
            throw new RuntimeException("Fail to authenticate code", e);
        }
    }

    private URI buildAuthLocation(AuthenticationRequest req) throws Exception {
        return new URI(String.format("%s?%s", getOidcAuthProvider().getOidcAuthorizeUrl(), req.toQueryString()));
    }

    private StorageOSUserDAO populateUserInfo(String idToken) throws Exception {

        JSONObject jsonIdToken = JWSObject.parse(idToken).getPayload().toJSONObject();
        String sub = (String) jsonIdToken.get("sub");
        JSONArray groups = (JSONArray) jsonIdToken.get("http://www.watch4net.com/openid/roles");
        log.info("the user info: {}, {}", sub, groups.toString());

        StorageOSUserDAO userInfo = new StorageOSUserDAO();
        userInfo.setUserName(sub);
        userInfo.setGroups( getGroupSet(groups) );

        userInfo.setTenantId( findTenant(userInfo) );

        return userInfo;
    }

    private String findTenant(StorageOSUserDAO userInfo) {
        Map<URI, List<BasePermissionsHelper.UserMapping>> tenantToMappingMap =
                permissionsHelper.getAllUserMappingsForDomain(getOidcAuthProvider().getDomains());

        // Figure out tenant id

        // Dont support user attributes in this IDP case for now. So pass an empty attribute map here
        Map<String, List<String>> userMappingAttributes = new HashMap<String, List<String>>();
        Map<URI, BasePermissionsHelper.UserMapping> tenants =
                TenantMapper.mapUserToTenant(getOidcAuthProvider().getDomains(), userInfo, userMappingAttributes, tenantToMappingMap, dbClient);

        if (null == tenants || tenants.isEmpty()) {
            log.error("User {} did not match any tenant", userInfo.getUserName());
            throw APIException.forbidden.userDoesNotMapToAnyTenancy(userInfo.getUserName());
        }

        if (tenants.keySet().size() > 1) {
            log.error("User {} mapped to tenants {}", userInfo.getUserName(), tenants.keySet().toArray());
            throw APIException.forbidden.userBelongsToMultiTenancy(userInfo.getUserName(), tenantName(tenants.keySet()));
        }

        return tenants.keySet().iterator().next().toString();
    }

    private StringSet getGroupSet(JSONArray groups) {
        if (groups == null) {
            return null;
        }

        StringSet groupSet = new StringSet();

        for (Object grpDN: groups) {
            try {
                groupSet.add(upn((String) grpDN));
            } catch (InvalidNameException e) { // Ingore invalid dn returned
                log.warn("Invalid group dn {}", grpDN, e);
            }
        }
        return groupSet;
    }

    private String upn(String grpDN) throws InvalidNameException {
        String baseName = null;
        String domainName = null;
        List<String> dcs = new ArrayList<String>();
        int cnCursor = 0; // only need first cn

        LdapName dn = new LdapName(grpDN);
        for (Rdn rdn : dn.getRdns()) {
            if (rdn.getType().equalsIgnoreCase("CN") && cnCursor <= 0) {
                baseName = (String) rdn.getValue();
            } else if (rdn.getType().equalsIgnoreCase("DC")) {
                dcs.add(rdn.getValue().toString());
            }
        }

        if (dcs.size() > 0) {
            domainName = StringUtils.join(dcs, ",");
        }

        return (domainName == null) ? baseName : String.format("%s@%s", baseName, domainName);
    }

    private List<String> tenantName(Set<URI> uris) {
        List<String> tenantNames = new ArrayList<>();
        for (URI tId : uris) {
            TenantOrg t = dbClient.queryObject(TenantOrg.class, tId);
            tenantNames.add(t.getLabel());
        }

        return tenantNames;
    }

    private void validateToken(String idToken) throws Exception {
        // load JWKS. TODO: store key locally
        JWKSet jwkeys = JWKSet.load(new URL(getOidcAuthProvider().getJwksUrl()));
        jwkeys.getKeys().get(0);

        // Verify id token
        JWSObject jwsIDToken = JWSObject.parse(idToken);
        JWSVerifier verifier = new RSASSAVerifier((RSAKey) jwkeys.getKeys().get(0));
        log.info("id token verify is {}", jwsIDToken.verify(verifier));
    }

    private String requestToken(String code) throws Exception {
        TokenRequest tokenReq = new TokenRequest(
                URI.create(getOidcAuthProvider().getOidcTokenUrl()),
                new ClientSecretBasic(new ClientID(getOidcAuthProvider().getOidcClientId()), new Secret("anysecret")),
                new AuthorizationCodeGrant(new AuthorizationCode(code), URI.create( getOidcAuthProvider().getOidcCallBackUrl() ) ) );

        log.info("Requesting token for code {}", code);
        HTTPRequest httpRequest = tokenReq.toHTTPRequest();
        log.info("Default SSLSocketFactory is {}, {}", httpRequest.getDefaultSSLSocketFactory(), httpRequest.getDefaultHostnameVerifier());
        HTTPResponse tokenHTTPResp = httpRequest.send();
        log.info("Token response is {}", tokenHTTPResp.getContent());
        TokenResponse tokenResponse = OIDCTokenResponseParser.parse(tokenHTTPResp);

        if (tokenResponse instanceof TokenErrorResponse) {
            ErrorObject error = ((TokenErrorResponse) tokenResponse).getErrorObject();
            throw APIException.internalServerErrors.failToRequestIdToken(error.getDescription());
        }

        return ( (OIDCTokenResponse) tokenResponse).getOIDCTokens().toString();
    }

    public AuthnProvider getOidcAuthProvider() {
        for (AuthenticationProvider provider : authProviders.getAuthenticationProviders()) {
            if (provider.getProviderConfig() == null) { // local auth
                continue;
            }
            if ( provider.getProviderConfig().getMode().equalsIgnoreCase( AuthnProvider.ProvidersType.oidc.name() ) ) {
                return provider.getProviderConfig();
            }
        }
        throw new RuntimeException("No OIDC provider is found");
    }

    public void verifyAuthModeForOIDC() {
        // TODO, throw exception if not oidc mode
    }

    public class CustomHostnameVerifier implements HostnameVerifier {

        @Override
        public boolean verify(String s, SSLSession sslSession) {
            log.info("Entering CustomHostnameVerifier. Host name is {} and name from session is {}", s, sslSession.getPeerHost());
            if (KeyStoreUtil.getAcceptAllCerts(new CoordinatorConfigStoringHelper(coordinator))) {
                log.info("The system is set to accept all. Ingore host name verifying");
                return true;
            }
            return false;
        }
    }
}
