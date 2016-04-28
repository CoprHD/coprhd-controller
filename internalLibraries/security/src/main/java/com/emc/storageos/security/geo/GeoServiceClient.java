/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.geo;

import javax.crypto.SecretKey;

import java.io.InputStream;
import java.io.ObjectInputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.core.MediaType;

import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.geomodel.*;
import com.emc.storageos.model.ipsec.IpsecParam;
import com.emc.storageos.security.geo.exceptions.*;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.WebResource.Builder;
import com.emc.storageos.db.client.constraint.Constraint;
import com.emc.storageos.db.client.constraint.QueryResultList;
import com.emc.storageos.db.client.constraint.ConstraintDescriptor;
import com.emc.storageos.db.client.model.GeoVisibleResource;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.security.SignatureHelper;
import com.emc.storageos.security.authentication.InternalApiSignatureKeyGenerator;
import com.emc.storageos.security.authentication.InternalApiSignatureKeyGenerator.SignatureKeyType;
import com.emc.storageos.security.helpers.BaseServiceClient;
import com.emc.storageos.svcs.errorhandling.resources.UnauthorizedException;
import com.emc.storageos.geomodel.ResourcesResponse;
import com.emc.storageos.geomodel.TokenResponse;
import com.emc.storageos.geomodel.request.TokenKeysRequest;
import com.emc.storageos.model.errorhandling.ServiceErrorRestRep;
import com.emc.storageos.security.authentication.RequestProcessingUtils;
import com.emc.storageos.geomodel.VdcConfigSyncParam;
import com.emc.storageos.geomodel.VdcNodeCheckParam;
import com.emc.storageos.geomodel.VdcNodeCheckResponse;
import com.emc.storageos.geomodel.VdcPreCheckParam;
import com.emc.storageos.geomodel.VdcPreCheckResponse;
import com.emc.storageos.geomodel.VdcPostCheckParam;

public class GeoServiceClient extends BaseServiceClient {
    public static final String INTERVDC_URI = "/intervdc";

    public static final String GEO_VISIBLE = "/geo-visible/";
    public static final String GEOVISIBLE_URI = INTERVDC_URI + GEO_VISIBLE;

    public static final String DEPENDENCIES = "/dependencies/";
    public static final String DEPENDENCIES_URI = INTERVDC_URI + DEPENDENCIES;

    public static final String VDCCONFIG_URI = INTERVDC_URI + "/vdcconfig";
    public static final String TOKEN = INTERVDC_URI + "/token";
    public static final String LOGOUT_TOKEN = TOKEN + "/logout";
    public static final String VDCCONFIG_POSTCHECK_URI = VDCCONFIG_URI + "/postcheck";
    public static final String VDCCONFIG_PRECHECK_URI = VDCCONFIG_URI + "/precheck";
    public static final String VDCCONFIG_PRECHECK2_URI = VDCCONFIG_URI + "/precheck2";
    public static final String VDCCONFIG_NODECHECK_URI = VDCCONFIG_URI + "/nodecheck";
    public static final String VDCCONFIG_NETCHECK_URI = VDCCONFIG_URI + "/natcheck";
    public static final String VDCCONFIG_CERT_URI = VDCCONFIG_URI + "/certs";
    public static final String VDCCONFIG_STABLE_CHECK = VDCCONFIG_URI + "/stablecheck";
    public static final String VERSION_URI = INTERVDC_URI + "/version";
    public static final String VDCCONFIG_RESET_BLACKLIST = VDCCONFIG_URI + "/resetblacklist";
    public static final String INTERVDC_IPSEC_SERVICE = INTERVDC_URI + "/ipsec";
    public static final String INTERVDC_IPSEC_KEY_ROTATION_URI = INTERVDC_IPSEC_SERVICE + "/key";
    public static final String INTERVDC_IPSEC_PROPERTIES_URI = INTERVDC_IPSEC_SERVICE + "/properties";

    private static int MAX_RETRIES = 12;
    public static void setMaxRetries(int maxRetries) {
        MAX_RETRIES = maxRetries;
    }

    public final static String VDCOP_LOCK_NAME = "vdcOpLock";
    // An add VDC operation can easily take up to 15 minutes. Use 30 minutes as timeout
    // for now.
    public final static long VDCOP_LOCK_TIMEOUT = (long) 30 * 60 * 1000;

    private SecretKey secretKey;
    private String endPoint;

    final private Logger log = LoggerFactory.getLogger(GeoServiceClient.class);

    public GeoServiceClient() {
        setClientMaxRetries(MAX_RETRIES);
        setDefaultSignatureType(SignatureKeyType.INTERVDC_API);
    }

    @Override
    public void setServer(String server) {
        this.endPoint = server;
        URI serverURI = null;
        if ((!server.contains("://")) && server.contains(":")) {
            if (!server.startsWith("[")) {
                StringBuilder builder = new StringBuilder("[");
                builder.append(server);
                builder.append("]");
                server = builder.toString();
            }
            serverURI = URI.create("https://" + server + ":4443");
        } else {
            serverURI = URI.create(server);
        }

        // TODO: we need to settle on whether the VirtualDataCenter.apiEndpoint
        // holds a host or a full URI. For the moment, this will work around either
        if (serverURI.getScheme() == null) {
            setServiceURI(URI.create("https://" + server + ":4443"));
        } else {
            setServiceURI(serverURI);
        }
        // setServiceURI(URI.create("https://" + server + ":8543"));
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = StringUtils.isNotBlank(secretKey) ?
                this.secretKey = SignatureHelper.createKey(secretKey, InternalApiSignatureKeyGenerator.CURRENT_INTERVDC_API_SIGN_ALGO) :
                null;
    }

    /**
     * Override the default behavior of this single argument
     * version of add signature (which uses the internal key)
     * with one that uses the supplied secret key if it's
     * available.
     */
    @Override
    protected Builder addSignature(WebResource webResource) {
        if (secretKey == null) {
            log.debug("Calling addSignature with null secretKey; local intervdc signing key will be used");
        }
        return super.addSignature(webResource, secretKey);
    }

    /*
     * Get a token/user dao and token keys from a remote vdc.
     * The token can be omitted if keys are passed (in this case the call is used to retrieve updated keys only)
     * The keys can be omitted and only token passed (in this case, the token will be validated, keys are ignored)
     * Keys and token can both be passed (in this case token gets validated and keys get updated)
     * 
     * @return the token and storageosuserdao objects encapsulated in a TokenResponse
     * 
     * @param rawToken the TokenOnWire object to validate
     * 
     * @param firstKeyId 1st key id from the TokenKeyBundle. null == don't send keys. 0 == send keys (forced mismatch)
     * 
     * @param secondKeyId 2nd key id from the TokenKeyBundle
     * 
     * @throws Exception
     */
    public TokenResponse getToken(String rawToken, String firstKeyId, String secondKeyId) throws Exception {
        TokenKeysRequest requestBody = new TokenKeysRequest();
        requestBody.setSecondKeyId(secondKeyId);
        requestBody.setFirstKeyId(firstKeyId);
        requestBody.setRequestingVDC(VdcUtil.getLocalShortVdcId());
        WebResource rRoot = createRequest(TOKEN);
        ClientResponse response = addSignature(rRoot).header(RequestProcessingUtils.AUTH_TOKEN_HEADER, rawToken == null ? "" : rawToken).
                type(MediaType.APPLICATION_XML).post(ClientResponse.class, requestBody);
        int status = response.getStatus();
        if (status != ClientResponse.Status.OK.getStatusCode()) {
            log.error(response.getEntity(ServiceErrorRestRep.class).getDetailedMessage());
            return null;
        } else {
            return response.getEntity(TokenResponse.class);
        }
    }

    /**
     * Sends requests to a VDC to logout token(s)/username. This covers 4 use cases
     * 1. single token logout: the token was created by this vdc, and this is a request to a vdc who has a copy of this token to delete it
     * 2. single token logout: the token was not created by this vdc, and this is a request to its originator to delete the master copy of
     * it
     * 3. force=true (all tokens of current user)
     * 4. username=username (all tokens for a given username)
     * 
     * @param rawToken
     * @param username optional, defines which username for which to delete all tokens
     * @param force, if true, deletes all tokens of current user, else only deletes provided token
     * @return ClientResponse
     * @throws Exception
     */
    public ClientResponse logoutToken(String rawToken, String username, boolean force) throws Exception {
        WebResource rRoot = createRequest(LOGOUT_TOKEN).queryParam("force", force ? "true" : "false");
        if (StringUtils.isNotBlank(username)) {
            rRoot = rRoot.queryParam("username", username);
        }
        log.info("GeoServiceClient logout request: " + rRoot.getURI());
        return addSignature(rRoot).header(RequestProcessingUtils.AUTH_TOKEN_HEADER, rawToken).
                type(MediaType.APPLICATION_XML).post(ClientResponse.class);
    }

    /**
     * Get the GeoVisible resource IDs from the geosvc
     * 
     * @param clazz the resource type to be queried
     * @param activeOnly
     * @return the ResourceIDsRespons response
     * @throws Exception
     */
    public <T extends GeoVisibleResource> Iterator<URI> queryByType(Class<T> clazz, boolean activeOnly) throws Exception {
        WebResource rRoot = createRequest(GEOVISIBLE_URI + clazz.getName())
                .queryParam("active_only", Boolean.toString(activeOnly));
        rRoot.accept(MediaType.APPLICATION_OCTET_STREAM);

        ClientResponse resp = addSignature(rRoot).get(ClientResponse.class);

        InputStream input = resp.getEntityInputStream();
        ObjectInputStream objInputStream = new ObjectInputStream(input);
        @SuppressWarnings("rawtypes")
        ResourcesResponse resources = (ResourcesResponse) objInputStream.readObject();
        @SuppressWarnings("unchecked")
        List<URI> ids = resources.getObjects();

        return ids.iterator();
    }

    /**
     * Query the GeoVisible resource IDs from the geosvc
     * 
     * @param clazz the resource type to be queried
     * @param activeOnly
     * @param startId where the query starts, if it's null, start from the beginning
     * @param maxCount if maxCount>0, the max number of IDs returned, otherwise use the default setting on the server side
     * @return the ResourceIDsRespons response
     * @throws Exception
     */
    public <T extends GeoVisibleResource> List<URI> queryByType(Class<T> clazz, boolean activeOnly, URI startId, int maxCount)
            throws Exception {
        WebResource rRoot = createRequest(GEOVISIBLE_URI + clazz.getName())
                .queryParam("active_only", Boolean.toString(activeOnly));

        if (startId != null) {
            rRoot = rRoot.queryParam("start_id", startId.toString());
        }

        if (maxCount > 0) {
            rRoot = rRoot.queryParam("max_count", Integer.toString(maxCount));
        }

        rRoot.accept(MediaType.APPLICATION_OCTET_STREAM);

        ClientResponse resp = addSignature(rRoot).get(ClientResponse.class);

        InputStream input = resp.getEntityInputStream();
        ObjectInputStream objInputStream = new ObjectInputStream(input);
        @SuppressWarnings("rawtypes")
        ResourcesResponse resources = (ResourcesResponse) objInputStream.readObject();
        @SuppressWarnings("unchecked")
        List<URI> ids = resources.getObjects();

        return ids;
    }

    /**
     * Get the GeoVisible resource with given id
     * 
     * @param clazz the resource type to be queried
     * @param id the resource ID
     * @return the Resource
     * @throws Exception
     */
    public <T extends GeoVisibleResource> T queryObject(Class<T> clazz, URI id) throws Exception {
        WebResource rRoot = createRequest(GEOVISIBLE_URI + clazz.getName() + "/object/" + id);
        rRoot.accept(MediaType.APPLICATION_OCTET_STREAM);

        ClientResponse resp = addSignature(rRoot).get(ClientResponse.class);
        InputStream input = resp.getEntityInputStream();
        ObjectInputStream objInputStream = new ObjectInputStream(input);

        @SuppressWarnings("unchecked")
        T obj = (T) objInputStream.readObject();

        return obj;
    }

    /**
     * Get the GeoVisible resources with given ids
     * 
     * @param clazz the resource type to be queried
     * @param ids List of the resource IDs
     * @return list of resources
     * @throws Exception
     */
    public <T extends GeoVisibleResource> Iterator<T> queryObjects(Class<T> clazz, List<URI> ids) throws Exception {
        BulkIdParam param = new BulkIdParam();
        param.setIds(ids);

        WebResource rRoot = createRequest(GEOVISIBLE_URI + clazz.getName() + "/objects");

        rRoot.accept(MediaType.APPLICATION_OCTET_STREAM);

        ClientResponse resp = addSignature(rRoot).post(ClientResponse.class, param);
        InputStream input = resp.getEntityInputStream();
        ObjectInputStream objInputStream = new ObjectInputStream(input);

        @SuppressWarnings("unchecked")
        ResourcesResponse<T> resources = (ResourcesResponse<T>) objInputStream.readObject();
        List<T> list = resources.getObjects();

        return list.iterator();
    }

    /**
     * Get the GeoVisible resources specific field with given ids
     * 
     * @param clazz the resource type to be queried
     * @param fieldName the feild to be get
     * @param ids List of the resource IDs
     * @return list of resources
     * @throws Exception
     */
    public <T extends GeoVisibleResource> Iterator<T> queryObjectsField(Class<T> clazz, String fieldName, List<URI> ids)
            throws Exception {
        BulkIdParam param = new BulkIdParam();
        param.setIds(ids);

        WebResource rRoot = createRequest(GEOVISIBLE_URI + clazz.getName() + "/field/" + fieldName);

        rRoot.accept(MediaType.APPLICATION_OCTET_STREAM);

        ClientResponse resp = addSignature(rRoot).post(ClientResponse.class, param);
        InputStream input = resp.getEntityInputStream();

        ObjectInputStream objInputStream = new ObjectInputStream(input);
        @SuppressWarnings("unchecked")
        ResourcesResponse<T> resources = (ResourcesResponse<T>) objInputStream.readObject();
        List<T> list = resources.getObjects();

        return list.iterator();
    }

    /**
     * Query the GeoVisible resources by conditions
     * 
     * @param constraint the query condition
     * @param result the query result
     * @return list of resources
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    public <T> void queryByConstraint(Constraint constraint, QueryResultList<T> result) throws Exception {
        ConstraintDescriptor constrainDescriptor = constraint.toConstraintDescriptor();

        WebResource rRoot = createRequest(GEOVISIBLE_URI + "constraint/" + result.getClass().getName());

        rRoot.accept(MediaType.APPLICATION_OCTET_STREAM);

        ClientResponse resp = addSignature(rRoot).post(ClientResponse.class, constrainDescriptor);
        InputStream input = resp.getEntityInputStream();

        ObjectInputStream objInputStream = new ObjectInputStream(input);
        ResourcesResponse<?> resources = (ResourcesResponse<?>) objInputStream.readObject();
        List<Object> queryResult = (List<Object>) resources.getObjects();
        List<T> ret = new ArrayList<T>();

        for (Object obj : queryResult) {
            ret.add((T) obj);
        }

        result.setResult(ret.iterator());
    }

    /**
     * Query the GeoVisible resources by conditions
     * 
     * @param constraint the query condition
     * @param result the query result
     * @param startId where the query starts, if it's null, query starts at the beginning
     * @param maxCount the max number of resources returned
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    public <T> void queryByConstraint(Constraint constraint, QueryResultList<T> result, URI startId, int maxCount) throws Exception {
        ConstraintDescriptor constrainDescriptor = constraint.toConstraintDescriptor();

        WebResource rRoot = createRequest(GEOVISIBLE_URI + "constraint/" + result.getClass().getName());

        if (startId != null) {
            rRoot = rRoot.queryParam("start_id", startId.toString());
        }

        if (maxCount > 0) {
            rRoot = rRoot.queryParam("max_count", Integer.toString(maxCount));
        }

        rRoot.accept(MediaType.APPLICATION_OCTET_STREAM);

        ClientResponse resp = addSignature(rRoot).post(ClientResponse.class, constrainDescriptor);
        InputStream input = resp.getEntityInputStream();

        ObjectInputStream objInputStream = new ObjectInputStream(input);
        ResourcesResponse<?> resources = (ResourcesResponse<?>) objInputStream.readObject();
        List<Object> queryResult = (List<Object>) resources.getObjects();
        List<T> ret = new ArrayList<T>();

        for (Object obj : queryResult) {
            ret.add((T) obj);
        }

        result.setResult(ret.iterator());
    }

    /**
     * Send the VDC config list to a remote VDC.
     * 
     * @param vdcConfigList the merged list of VDC config
     * @throws Exception
     */
    public void syncVdcConfig(VdcConfigSyncParam vdcConfigList, String vdcName) throws GeoException {
        WebResource rRoot = createRequest(VDCCONFIG_URI);
        rRoot.accept(MediaType.APPLICATION_XML);

        try {
            addSignature(rRoot).put(vdcConfigList);
        } catch (UnauthorizedException e) {
            log.error("Failed to sync VDC : 401 Unauthorized, " + vdcName, e);
            throw GeoException.fatals.remoteVdcAuthorizationFailed(vdcName, e);
        } catch (GeoException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to sync VDC : " + vdcName, e);
            throw GeoException.fatals.failedToSyncConfigurationForVdc(vdcName, e);
        }
    }

    /**
     * Retrieve the VDC config info from a remote VDC.
     * 1. For adding a new vdc, the target vdc should be in ISOLATED status and is a fresh installation.
     * 2. For updating an existing vdc, the target vdc should be in CONNECTED status.
     * 
     * @param checkParam
     * @throws Exception
     */
    public VdcPreCheckResponse syncVdcConfigPreCheck(VdcPreCheckParam checkParam, String vdcName) throws GeoException {
        WebResource rRoot;
        rRoot = createRequest(VDCCONFIG_PRECHECK_URI);
        rRoot.accept(MediaType.APPLICATION_XML);
        try {
            return addSignature(rRoot).post(VdcPreCheckResponse.class, checkParam);
        } catch (UnauthorizedException e) {
            throw GeoException.fatals.remoteVdcAuthorizationFailed(vdcName, e);
        } catch (GeoException e) {
            throw e;
        } catch (Exception e) {
            throw GeoException.fatals.failedToSendPreCheckRequest(vdcName, e);
        }
    }

    /**
     * Check the VDC from a remote VDC.
     * 1. For disconnecting a vdc, check if there is another vdc that is under disconnecting.
     * 
     * @param checkParam
     * @throws Exception
     */
    public VdcPreCheckResponse2 syncVdcConfigPreCheck(VdcPreCheckParam2 checkParam, String vdcName) {
        WebResource rRoot;
        rRoot = createRequest(VDCCONFIG_PRECHECK2_URI);
        rRoot.accept(MediaType.APPLICATION_XML);
        try {
            return addSignature(rRoot).post(VdcPreCheckResponse2.class, checkParam);
        } catch (UnauthorizedException e) {
            throw GeoException.fatals.remoteVdcAuthorizationFailed(vdcName, e);
        } catch (GeoException e) {
            throw e;
        } catch (Exception e) {
            throw GeoException.fatals.failedToSendPreCheckRequest(vdcName, e);
        }
    }

    /**
     * check all nodes are visible
     * 
     * @param checkParam
     * @throws Exception
     */
    public VdcNodeCheckResponse vdcNodeCheck(VdcNodeCheckParam checkParam) {
        WebResource rRoot;
        rRoot = createRequest(VDCCONFIG_NODECHECK_URI);
        rRoot.accept(MediaType.APPLICATION_XML);
        try {
            return addSignature(rRoot).post(VdcNodeCheckResponse.class, checkParam);
        } catch (UnauthorizedException e) {
            throw GeoException.fatals.remoteVdcAuthorizationFailed(endPoint, e);
        } catch (GeoException e) {
            throw e;
        } catch (Exception e) {
            throw GeoException.fatals.unableConnect(endPoint, e);
        }
    }

    public VdcNatCheckResponse vdcNatCheck(VdcNatCheckParam checkParam) {
        WebResource rRoot = createRequest(VDCCONFIG_NETCHECK_URI);
        rRoot.accept(MediaType.APPLICATION_XML);

        try {
            return addSignature(rRoot).post(VdcNatCheckResponse.class, checkParam);
        } catch (UnauthorizedException e) {
            log.error("Failed to perform NAT check", e);
            throw GeoException.fatals.remoteVdcAuthorizationFailed(endPoint, e);
        } catch (GeoException e) {
            log.error("Failed to perform NAT check", e);
            throw e;
        } catch (Exception e) {
            log.error("Failed to perform NAT check", e);
            throw GeoException.fatals.unableConnect(endPoint, e);
        }
    }

    /**
     * Post steps after syncing the VDC config list to a remote VDC.
     * 
     * @param checkParam the list to be checked
     * @throws Exception
     */
    public void syncVdcConfigPostCheck(VdcPostCheckParam checkParam, String vdcName) {
        WebResource rRoot = createRequest(VDCCONFIG_POSTCHECK_URI);
        rRoot.accept(MediaType.APPLICATION_XML);
        try {
            addSignature(rRoot).post(checkParam);
        } catch (UnauthorizedException e) {
            throw GeoException.fatals.remoteVdcAuthorizationFailed(vdcName, e);
        } catch (GeoException e) {
            throw e;
        } catch (Exception e) {
            throw GeoException.fatals.failedToSedPostCheckRequest(vdcName, e);
        }
    }

    /**
     * Query the dependencies of a DataObject resource
     * 
     * @param clazz the resource type to be queried
     * @param id the resource object ID
     * @param activeOnly
     * @return the type name of the dependency, otherwise return null
     * @throws Exception
     */
    public <T extends DataObject> String checkDependencies(Class<T> clazz, URI id, boolean activeOnly) {
        WebResource rRoot = createRequest(DEPENDENCIES_URI + clazz.getName() + "/" + id)
                .queryParam("active_only", Boolean.toString(activeOnly));
        rRoot.accept(MediaType.APPLICATION_XML);
        try {
            return addSignature(rRoot).get(String.class);
        } catch (UnauthorizedException e) {
            throw GeoException.fatals.remoteVdcAuthorizationFailed(endPoint, e);
        } catch (GeoException e) {
            throw e;
        } catch (Exception e) {
            throw GeoException.fatals.unableConnect(endPoint, e);
        }

    }

    /**
     * Send all the VDC certs list to a remote VDC.
     * 
     * @param vdcCertListParam all the VDCs' certs
     * @throws Exception
     */
    public void syncVdcCerts(VdcCertListParam vdcCertListParam, String VdcName) {
        WebResource rRoot = createRequest(VDCCONFIG_CERT_URI);
        rRoot.accept(MediaType.APPLICATION_XML);
        try {
            addSignature(rRoot).post(vdcCertListParam);
        } catch (UnauthorizedException e) {
            throw GeoException.fatals.remoteVdcAuthorizationFailed(VdcName, e);
        } catch (GeoException e) {
            throw e;
        } catch (Exception e) {
            throw GeoException.fatals.connectVdcSyncCertFail(VdcName, e);
        }

    }

    /**
     * Get the version of the ViPR software running on the remote VDC (e.g. vipr-1.0.0.1.1)
     * 
     * @return the version
     */
    public String getViPRVersion() {
        WebResource rRoot = createRequest(VERSION_URI);
        try {
            return addSignature(rRoot).accept(MediaType.TEXT_PLAIN).get(String.class);
        } catch (UnauthorizedException e) {
            throw GeoException.fatals.unableConnect(endPoint, e);
        } catch (GeoException e) {
            throw e;
        } catch (Exception e) {
            throw GeoException.fatals.unableConnect(endPoint, e);
        }
    }

    /**
     * Check whether the target VDC is stable or not
     * 
     * @return true if the VDC is stable, flase otherwise
     */
    public boolean isVdcStable() {
        WebResource rRoot = createRequest(VDCCONFIG_STABLE_CHECK);
        try {
            String ret = addSignature(rRoot).accept(MediaType.TEXT_PLAIN).get(String.class);
            return Boolean.valueOf(ret);
        } catch (UnauthorizedException e) {
            throw GeoException.fatals.unableConnect(endPoint, e);
        } catch (GeoException e) {
            throw e;
        } catch (Exception e) {
            throw GeoException.fatals.unableConnect(endPoint, e);
        }

    }

    /**
     * Reset geodb blacklist for given vdc short id
     */
    public void resetBlacklist(String vdcShortId) {
        WebResource rRoot = createRequest(VDCCONFIG_RESET_BLACKLIST).queryParam("vdc_short_id", vdcShortId);
        rRoot.accept(MediaType.APPLICATION_XML);
        try {
            addSignature(rRoot).post(ClientResponse.class);
        } catch (UnauthorizedException e) {
            throw GeoException.fatals.unableConnect(endPoint, e);
        } catch (GeoException e) {
            throw e;
        } catch (Exception e) {
            throw GeoException.fatals.unableConnect(endPoint, e);
        }

    }

    public void changeIpsecStatus(String peerVdcId, String status, String vdcConfigVersion) {
        WebResource rRoot = createRequest(INTERVDC_IPSEC_SERVICE)
                .queryParam("status",status)
                .queryParam("vdc_config_version", vdcConfigVersion);
        rRoot.accept(MediaType.APPLICATION_XML);
        try {
            addSignature(rRoot).post();
        } catch (UnauthorizedException e) {
            throw GeoException.fatals.remoteVdcAuthorizationFailed(peerVdcId, e);
        } catch (GeoException e) {
            throw e;
        } catch (Exception e) {
            throw GeoException.fatals.failedToSedPostCheckRequest(peerVdcId, e);
        }

    }

    public void rotateIpsecKey(String peerVdcId, IpsecParam ipsecParam) {
        WebResource rRoot = createRequest(INTERVDC_IPSEC_KEY_ROTATION_URI);
        rRoot.accept(MediaType.APPLICATION_XML);
        try {
            addSignature(rRoot).post(ipsecParam);
        } catch (UnauthorizedException e) {
            throw GeoException.fatals.remoteVdcAuthorizationFailed(peerVdcId, e);
        } catch (GeoException e) {
            throw e;
        } catch (Exception e) {
            throw GeoException.fatals.failedToSedPostCheckRequest(peerVdcId, e);
        }
    }

    /**
     * retrieve ipsec related properties from remote vdc.
     */
    public VdcIpsecPropertiesResponse getIpsecProperties() {
        WebResource rRoot = createRequest(INTERVDC_IPSEC_PROPERTIES_URI);
        try {
            return addSignature(rRoot).accept(MediaType.APPLICATION_XML).get(VdcIpsecPropertiesResponse.class);
        } catch (UnauthorizedException e) {
            throw GeoException.fatals.unableConnect(endPoint, e);
        } catch (GeoException e) {
            throw e;
        } catch (Exception e) {
            throw GeoException.fatals.unableConnect(endPoint, e);
        }
    }
}
