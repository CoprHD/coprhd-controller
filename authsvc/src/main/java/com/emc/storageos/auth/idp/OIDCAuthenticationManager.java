/*
 *
 *  * Copyright (c) 2016 EMC Corporation
 *  * All Rights Reserved
 *
 */

package com.emc.storageos.auth.idp;

import com.emc.storageos.auth.impl.TenantMapper;
import com.emc.storageos.auth.service.impl.resource.AuthenticationResource;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.AuthnProvider;
import com.emc.storageos.db.client.model.StorageOSUserDAO;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.security.authorization.BasePermissionsHelper;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.oauth2.sdk.*;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.OIDCAccessTokenResponse;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponseParser;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
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

    private DbClient _dbClient;
    private AuthnProvider authnProvider;
    private BasePermissionsHelper permissionsHelper;

    public OIDCAuthenticationManager(BasePermissionsHelper permissionsHelper, DbClient dbClient, AuthnProvider authnProvider) {
        this._dbClient = dbClient;
        this.permissionsHelper = permissionsHelper;
        this.authnProvider = authnProvider;
    }

    public URI buildAuthenticationRequestInURI(String resourceURI) throws Exception {
        State state = new State(resourceURI);
        AuthenticationRequest req = new AuthenticationRequest(
                null, // url to provider
                new ResponseType(ResponseType.Value.CODE),
                Scope.parse("openid email profile address"),
                new ClientID(getAuthnProvider().getOidcClientId()),
                URI.create(getAuthnProvider().getOidcCallBackUrl()), // redirect uri
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
        return URI.create(String.format("%s?%s", getAuthnProvider().getOidcAuthorizeUrl(), req.toQueryString()));
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
                permissionsHelper.getAllUserMappingsForDomain(authnProvider.getDomains());

        // Figure out tenant id

        // Dont support user attributes in this IDP case for now. So pass an empty attribute map here
        Map<String, List<String>> userMappingAttributes = new HashMap<String, List<String>>();
        Map<URI, BasePermissionsHelper.UserMapping> tenants =
                TenantMapper.mapUserToTenant(authnProvider.getDomains(), userInfo, userMappingAttributes, tenantToMappingMap, _dbClient);

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
            groupSet.add(upn((String) grpDN));
        }
        return groupSet;
    }

    private String upn(String grpDN) {
        String baseName = null;
        String domainName = null;
        List<String> dcs = new ArrayList<String>();
        try {
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
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> tenantName(Set<URI> uris) {
        List<String> tenantNames = new ArrayList<>();
        for (URI tId : uris) {
            TenantOrg t = _dbClient.queryObject(TenantOrg.class, tId);
            tenantNames.add(t.getLabel());
        }

        return tenantNames;
    }

    private void validateToken(String idToken) throws Exception {
        // load JWKS. TODO: store key locally
        JWKSet jwkeys = JWKSet.load(new URL(getAuthnProvider().getJwksUrl()));
        jwkeys.getKeys().get(0);

        // Verify id token
        JWSObject jwsIDToken = JWSObject.parse(idToken);
        JWSVerifier verifier = new RSASSAVerifier((RSAKey) jwkeys.getKeys().get(0));
        log.info("id token verify is {}", jwsIDToken.verify(verifier));
    }

    private String requestToken(String code) throws Exception {
        TokenRequest tokenReq = new TokenRequest(
                URI.create(getAuthnProvider().getOidcTokenUrl()),
                new ClientSecretBasic(new ClientID(getAuthnProvider().getOidcClientId()), new Secret("anysecret")),
                new AuthorizationCodeGrant(new AuthorizationCode(code), URI.create( getAuthnProvider().getOidcCallBackUrl() ) ) );

        log.info("Requesting token for code {}", code);
        HTTPResponse tokenHTTPResp = tokenReq.toHTTPRequest().send();
        log.info("Token response is {}", tokenHTTPResp.getContent());
        TokenResponse tokenResponse = OIDCTokenResponseParser.parse(tokenHTTPResp);

        if (tokenResponse instanceof TokenErrorResponse) {
            ErrorObject error = ((TokenErrorResponse) tokenResponse).getErrorObject();
            throw new RuntimeException(error.getDescription());
        }

        return ((OIDCAccessTokenResponse) tokenResponse).getIDTokenString();
    }

    public AuthnProvider getAuthnProvider() {
        return authnProvider;
    }
}
