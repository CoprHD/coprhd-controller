/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.VirtualDataCenterMapper.map;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.Principal;
import java.security.cert.*;
import java.security.interfaces.RSAPrivateKey;
import java.util.*;

import javax.crypto.SecretKey;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.emc.storageos.db.client.model.*;
import com.emc.storageos.security.helpers.SecurityUtil;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.emc.storageos.api.mapper.TaskMapper;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.VirtualDataCenter.ConnectionStatus;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.auth.PrincipalsToValidate;
import com.emc.storageos.model.auth.RoleAssignmentChanges;
import com.emc.storageos.model.auth.RoleAssignmentEntry;
import com.emc.storageos.model.auth.RoleAssignments;
import com.emc.storageos.model.vdc.VirtualDataCenterAddParam;
import com.emc.storageos.model.vdc.VirtualDataCenterList;
import com.emc.storageos.model.vdc.VirtualDataCenterModifyParam;
import com.emc.storageos.model.vdc.VirtualDataCenterRestRep;
import com.emc.storageos.model.vdc.VirtualDataCenterSecretKeyRestRep;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authentication.InternalApiSignatureKeyGenerator;
import com.emc.storageos.security.authentication.InternalApiSignatureKeyGenerator.SignatureKeyType;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.PermissionsKey;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.security.exceptions.SecurityException;
import com.emc.storageos.security.geo.GeoServiceHelper;
import com.emc.storageos.security.geo.GeoServiceJob;
import com.emc.storageos.security.geo.GeoServiceJob.JobType;
import com.emc.storageos.security.keystore.impl.CertificateVersionHelper;
import com.emc.storageos.security.keystore.impl.CoordinatorConfigStoringHelper;
import com.emc.storageos.security.keystore.impl.KeyCertificateAlgorithmValuesHolder;
import com.emc.storageos.security.keystore.impl.KeyCertificateEntry;
import com.emc.storageos.security.keystore.impl.KeyCertificatePairGenerator;
import com.emc.storageos.security.keystore.impl.KeyStoreUtil;
import com.emc.storageos.security.keystore.impl.KeystoreEngine;
import com.emc.storageos.security.validator.Validator;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.vipr.model.keystore.CertificateChain;
import com.emc.vipr.model.keystore.KeyAndCertificateChain;
import com.emc.vipr.model.keystore.RotateKeyAndCertParam;

/**
 * Resource for VirtualDataCenter manipulation
 */
@Path("/vdc")
@DefaultPermissions(readRoles = { Role.SECURITY_ADMIN, Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
        writeRoles = { Role.SECURITY_ADMIN })
public class VirtualDataCenterService extends TaskResourceService {

    @Autowired
    @Qualifier("keyGenerator")
    InternalApiSignatureKeyGenerator apiSignatureGenerator;

    @Autowired
    private Service service;

    @Autowired
    private DrUtil drUtil;
    
    private Map<String, StorageOSUser> _localUsers;

    public void setLocalUsers(Map<String, StorageOSUser> localUsers) {
        _localUsers = localUsers;
    }

    private static final String EVENT_SERVICE_TYPE = "vdc";
    private static final String ROOT = "root";
    private static final Logger _log = LoggerFactory.getLogger(VirtualDataCenterService.class);

    // for retrieving subject alternative names from certificate
    private static final int SUBALTNAME_DNSNAME = 2;
    private static final int SUBALTNAME_IPADDRESS = 7;
    private static final String VERSION_PART_SEPERATOR = "\\.";

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        // vdc is not a tenant resource
        return null;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.VDC;
    }

    private CoordinatorClient coordinator;

    @Override
    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }

    private CoordinatorConfigStoringHelper coordConfigStoringHelper;

    public void setCoordConfigStoringHelper(CoordinatorConfigStoringHelper coordConfigStoringHelper) {
        this.coordConfigStoringHelper = coordConfigStoringHelper;
    }

    protected GeoServiceHelper _geoHelper;

    public void setGeoServiceHelper(GeoServiceHelper geohelper) {
        _geoHelper = geohelper;
    }

    private KeyCertificatePairGenerator generator;

    private KeyStore viprKeyStore;

    private KeyStore getKeyStore() {
        if (viprKeyStore == null) {
            try {
                viprKeyStore = KeyStoreUtil.getViPRKeystore(coordinator);
            } catch (Exception e) {
                _log.error("Failed to load the VIPR keystore", e);
                throw new IllegalStateException(e);
            }
        }
        return viprKeyStore;
    }

    protected CertificateVersionHelper certificateVersionHelper;

    public void setCertificateVersionHelper(
            CertificateVersionHelper certificateVersionHelper) {
        this.certificateVersionHelper = certificateVersionHelper;
    }

    @Override
    protected VirtualDataCenter queryResource(URI id) {
        ArgValidator.checkUri(id);
        VirtualDataCenter vdc = _dbClient.queryObject(VirtualDataCenter.class, id);
        ArgValidator.checkEntityNotNull(vdc, id, isIdEmbeddedInURL(id));
        return vdc;
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SECURITY_ADMIN }, blockProxies = true)
    public TaskResourceRep addVirtualDataCenter(VirtualDataCenterAddParam param) {
        blockRoot();
        ArgValidator.checkFieldNotEmpty(param.getApiEndpoint(), "api_endpoint");
        ArgValidator.checkFieldNotEmpty(param.getSecretKey(), "secret_key");
        ArgValidator.checkFieldNotEmpty(param.getName(), "name");
        checkForDuplicateName(param.getName(), VirtualDataCenter.class);
        if (service.getId().endsWith("standalone")) {
            throw new IllegalStateException("standalone VDCs cannot be connected into a geo system");
        }
        
        List<Site> drSitesInCurrentVdc = drUtil.listSites();
        if (drSitesInCurrentVdc.size() > 1) {
            throw APIException.badRequests.notAllowedToAddVdcInDRConfig();
        }

        ArgValidator.checkFieldNotEmpty(param.getCertificateChain(), "certificate_chain");
        verifyVdcCert(param.getCertificateChain(), param.getApiEndpoint(), true);

        // TODO: We need a way to reject this if another "add" is already
        // in progress so that we only have one new VDC being synced at a time

        VirtualDataCenter localVDC = VdcUtil.getLocalVdc();
        Properties vdcInfo = prepareVdcOpParam(localVDC.getId(), param);
        auditOp(OperationTypeEnum.ADD_VDC, true, null, param.getApiEndpoint(), param.getApiEndpoint());
        return enqueueJob(localVDC, JobType.VDC_CONNECT_JOB, Arrays.asList(new Object[] { vdcInfo }));
    }

    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public VirtualDataCenterList getVirtualDataCenters() {
        VirtualDataCenterList vdcList = new VirtualDataCenterList();

        List<URI> ids = _dbClient.queryByType(VirtualDataCenter.class, true);
        Iterator<VirtualDataCenter> iter = _dbClient.queryIterativeObjects(VirtualDataCenter.class, ids);
        while (iter.hasNext()) {
            vdcList.getVirtualDataCenters().add(toNamedRelatedResource(iter.next()));
        }
        return vdcList;
    }

    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    public VirtualDataCenterRestRep getVirtualDataCenter(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, VirtualDataCenter.class, "id");
        VirtualDataCenter vdc = queryResource(id);

        VirtualDataCenterRestRep restRep = map(vdc);
        return restRep;
    }

    @PUT
    @Path("/{id}")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN }, blockProxies = true)
    public TaskResourceRep updateVirtualDataCenter(@PathParam("id") URI id, VirtualDataCenterModifyParam param) {
        ArgValidator.checkFieldUriType(id, VirtualDataCenter.class, "id");

        String localVdcId = VdcUtil.getLocalVdc().getId().toString();
        if (!id.toString().equalsIgnoreCase(localVdcId)) {
            blockRoot();
        }
        if (StringUtils.isNotEmpty(param.getApiEndpoint())) {
            throw APIException.badRequests.parameterNotSupportedFor(
                    "virtual ip", "update");
        }

        if (StringUtils.isNotEmpty(param.getSecretKey())) {
            throw APIException.badRequests.parameterNotSupportedFor(
                    "secretkey", "update");
        }

        if (param.getRotateKeyCert() != null) {
            throw APIException.badRequests.parameterNotSupportedFor(
                    "key and certification", "update");
        }

        VirtualDataCenter vdc = queryResource(id);

        if (StringUtils.isNotEmpty(param.getName()) && !param.getName().equals(vdc.getLabel())) {
            checkForDuplicateName(param.getName(), VirtualDataCenter.class);
        }

        /*
         * If vdc is in failed state:
         * CONNECT_FAILED
         * REMOVE_FAILED
         * 
         * Stop this request. (CTRL-3883)
         */
        // CTRL-3883 update should fail if previous op failed
        ConnectionStatus status = vdc.getConnectionStatus();
        _log.info("Updating VDC {}, connection status {}", vdc.getShortId(), status);
        if (status.equals(VirtualDataCenter.ConnectionStatus.CONNECT_FAILED) ||
                status.equals(VirtualDataCenter.ConnectionStatus.REMOVE_FAILED)) {
            _log.error("Cannot update VDC {} if it's in a failed state. Mannual VDC recovery required", vdc.getShortId());
            throw APIException.methodNotAllowed.notSupported();
        }

        List<Object> params = new ArrayList<>();
        List<Object> list = null;
        Certificate[] certchain = null;
        if (param.getRotateKeyCert() != null && param.getRotateKeyCert() == true) {
            list = prepareKeyCert(param.getKeyCertChain());
            certchain = (Certificate[]) list.get(2);
        }
        params.add(modifyVirtualDataCenterInfo(VdcUtil.getLocalVdc(), vdc, param, certchain));
        if (list != null) {
            params.addAll(list);
        }
        auditOp(OperationTypeEnum.UPDATE_VDC, true, null, id.toString());

        return enqueueJob(vdc, JobType.VDC_UPDATE_JOB, params);
    }

    @DELETE
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SECURITY_ADMIN }, blockProxies = true)
    public TaskResourceRep removeVirtualDataCenter(@PathParam("id") URI id) {
        blockRoot();
        ArgValidator.checkFieldUriType(id, VirtualDataCenter.class, "id");
        VirtualDataCenter vdc = queryResource(id);
        ConnectionStatus status = vdc.getConnectionStatus();
        if (BooleanUtils.isTrue(vdc.getLocal())) {
            throw APIException.badRequests.cantChangeConnectionStatusOfLocalVDC();
        }
        if (status.equals(ConnectionStatus.CONNECT_FAILED)) {
            _log.error("Cannot delete {}. Mannual VDC recovery required", vdc.getShortId());
            throw APIException.methodNotAllowed.notSupported();
        }
        // TODO Need to check that VDC to be removed is not "local VDC".
        // We can not remove local VDC.

        // TODO: Are there more pre-checks we want to do synchronously?

        auditOp(OperationTypeEnum.REMOVE_VDC, true, null, vdc.getLabel(), vdc.getApiEndpoint());
        return enqueueJob(vdc, JobType.VDC_REMOVE_JOB);
    }

    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/disconnect")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SECURITY_ADMIN }, blockProxies = true)
    public TaskResourceRep disconnectVirtualDataCenter(@PathParam("id") URI id) {
        blockRoot();
        ArgValidator.checkFieldUriType(id, VirtualDataCenter.class, "id");
        VirtualDataCenter vdc = queryResource(id);
        if (BooleanUtils.isTrue(vdc.getLocal())) {
            throw APIException.badRequests.cantChangeConnectionStatusOfLocalVDC();
        }

        // TODO: Are there more pre-checks we want to do synchronously?

        auditOp(OperationTypeEnum.DISCONNECT_VDC, true, null, id.toString());

        return enqueueJob(vdc, JobType.VDC_DISCONNECT_JOB);
    }

    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/reconnect")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SECURITY_ADMIN }, blockProxies = true)
    public TaskResourceRep reconnectVirtualDataCenter(@PathParam("id") URI id) {
        blockRoot();
        ArgValidator.checkFieldUriType(id, VirtualDataCenter.class, "id");
        VirtualDataCenter vdc = queryResource(id);

        if (BooleanUtils.isTrue(vdc.getLocal())) {
            throw APIException.badRequests.cantChangeConnectionStatusOfLocalVDC();
        }

        // TODO: Are there more pre-checks we want to do synchronously?

        auditOp(OperationTypeEnum.RECONNECT_VDC, true, null, id.toString());

        return enqueueJob(vdc, JobType.VDC_RECONNECT_JOB);
    }

    /**
     * Get vdc role assignments
     * 
     * @return Role assignment details
     * @brief List vdc role assignments
     */
    @Path("/role-assignments")
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN })
    public RoleAssignments getRoleAssignments() {
        VirtualDataCenter localVdc = VdcUtil.getLocalVdc();
        return getRoleAssignmentsResponse(localVdc);
    }

    /**
     * Retrieves the vdc's secret key. If it doesn't exist,
     * it gets generated
     * 
     * @return VDC secret key
     * @brief Retrieves the vdc's secret key
     */
    @Path("/secret-key")
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN })
    public VirtualDataCenterSecretKeyRestRep getVDCSecretKey() {
        SecretKey key = apiSignatureGenerator.getSignatureKey(SignatureKeyType.INTERVDC_API);
        VirtualDataCenterSecretKeyRestRep resp = new VirtualDataCenterSecretKeyRestRep();
        resp.setSecretKey(new String(Base64.encodeBase64(key.getEncoded()), Charset.forName("UTF-8")));
        return resp;
    }

    /**
     * Add or remove individual role assignments. Request body must include at least one add or remove operation.
     * 
     * @param changes Role assignment changes
     * @return No data returned in response body
     * @brief Add or remove role assignments
     */
    @Path("/role-assignments")
    @PUT
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN }, blockProxies = true)
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public RoleAssignments updateRoleAssignments(RoleAssignmentChanges changes) {
        VirtualDataCenter localVdc = VdcUtil.getLocalVdc();
        TenantOrg rootTenant = _permissionsHelper.getRootTenant();
        _permissionsHelper.updateRoleAssignments(localVdc, changes,
                new ZoneRoleInputFilter(rootTenant));

        validateVdcRoleAssignmentChange(localVdc);
        _dbClient.updateAndReindexObject(localVdc);

        _auditMgr.recordAuditLog(localVdc.getId(),
                URI.create(getUserFromContext().getName()),
                EVENT_SERVICE_TYPE, OperationTypeEnum.MODIFY_ZONE_ROLES,
                System.currentTimeMillis(), AuditLogManager.AUDITLOG_SUCCESS,
                null, localVdc.getId().toString(), localVdc.getLabel(), changes);

        return getRoleAssignmentsResponse(localVdc);
    }

    /**
     * restrict SecurityAdmin from dropping his own SECURITY_ADMIN role.
     * 
     * @param vdc vdc to be persisted with the new role change
     */
    private void validateVdcRoleAssignmentChange(VirtualDataCenter vdc) {
        StorageOSUser user = (StorageOSUser) sc.getUserPrincipal();

        // return if user is a local user
        if (_localUsers.keySet().contains(user.getName())) {
            return;
        }

        if (!user.getRoles().contains(Role.SECURITY_ADMIN.name())) {
            throw APIException.forbidden.insufficientPermissionsForUser(user.getName());
        }

        // populate vdc roles to the cloned user after vdc role-assignment change.
        // then do the check
        StorageOSUser tempUser = user.clone();
        tempUser.setRoles(new StringSet());
        _permissionsHelper.populateZoneRoles(tempUser, vdc);
        _log.info(tempUser.toString());
        if (!tempUser.getRoles().contains(Role.SECURITY_ADMIN.name())) {
            throw APIException.forbidden.securityAdminCantDropHisOwnSecurityAdminRole(user.getName());
        }
    }

    /**
     * prepare the vdc to fulfill the requirement of being able to add other vdc in this one.
     * tasks are:
     * 1. remove root's roles from all tenants
     * 2. remove root's ownership from all projects
     * 
     * @return http response
     * @brief prepare vdc by removing root's tenant roles and project ownerships
     */
    @Path("/prepare-vdc")
    @POST
    @CheckPermission(roles = { Role.SECURITY_ADMIN }, blockProxies = true)
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public Response prepareLocalVdc() {
        // remove root's role from all tenants
        try {
            _permissionsHelper.removeRootRoleAssignmentOnTenantAndProject();
        } catch (DatabaseException dbe) {
            throw InternalServerErrorException.internalServerErrors.
                    genericApisvcError("Fail to remove root's roles and project ownerships.", dbe);
        }
        return Response.ok().build();
    }

    /**
     * Get the certificate chain being used by ViPR
     * 
     * @brief Get the certificate chain being used by ViPR
     * @prereq none
     */
    @Path("/keystore")
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public CertificateChain getCertificateChain() {
        CertificateChain chain = new CertificateChain();
        try {
            Certificate[] certChain = null;
            certChain =
                    getKeyStore().getCertificateChain(
                            KeystoreEngine.ViPR_KEY_AND_CERTIFICATE_ALIAS);
            chain.setChain(KeyCertificatePairGenerator
                    .getCertificateChainAsString(certChain));
            return chain;
        } catch (KeyStoreException e) {
            _log.error(e.getMessage(), e);
            throw new IllegalStateException(e);
        } catch (CertificateEncodingException e) {
            throw SecurityException.fatals.couldNotParseCertificateToString(e);
        }
    }

    /**
     * Rotate the VIPR key and certificate chain.
     * 
     * @param rotateKeyAndCertParam
     * @return the new certificate chain being used by ViPR
     * @brief Rotate the VIPR key and certificate chain to a new system self-signed or a specified input.
     */
    @Path("/keystore")
    @PUT
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN }, blockProxies = true)
    public CertificateChain setKeyCertificatePair(RotateKeyAndCertParam rotateKeyAndCertParam) {

        // Do Not support keystore rotation in multiple-vdcs env
        if (!VdcUtil.isLocalVdcSingleSite()) {
            throw APIException.methodNotAllowed.rotateKeyCertInMultiVdcsIsNotAllowed();
        }

        if (!coordinator.isClusterUpgradable()) {
            throw SecurityException.retryables.updatingKeystoreWhileClusterIsUnstable();
        }

        Boolean selfsigned = rotateKeyAndCertParam.getSystemSelfSigned();

        byte[] key = null;
        Certificate[] chain = null;
        RSAPrivateKey rsaPrivateKey = null;

        try {
            if (selfsigned != null && selfsigned) {
                KeyCertificateEntry pair = getGenerator().generateKeyCertificatePair();

                // key is needed to clear
                key = pair.getKey();
                chain = pair.getCertificateChain();
            } else {
                KeyAndCertificateChain newKey = rotateKeyAndCertParam.getKeyCertChain();
                if (newKey == null || StringUtils.isBlank(newKey.getCertificateChain())
                        || StringUtils.isBlank(newKey.getPrivateKey())) {
                    throw APIException.badRequests.requiredParameterMissingOrEmpty("key_and_certificate");
                }

                try {
                    chain =
                            KeyCertificatePairGenerator.getCertificateChainFromString(newKey
                                    .getCertificateChain());

                    if (ArrayUtils.isEmpty(chain)) {
                        throw APIException.badRequests.failedToLoadCertificateFromString(newKey
                                .getCertificateChain());
                    }
                    X509Certificate cert = (X509Certificate) chain[0];
                    cert.checkValidity();

                    key = SecurityUtil.loadPrivateKeyFromPEMString(newKey.getPrivateKey());
                    rsaPrivateKey = (RSAPrivateKey) KeyCertificatePairGenerator.loadPrivateKeyFromBytes(key);

                    int keyLength = rsaPrivateKey.getModulus().bitLength();
                    if (keyLength < KeyCertificateAlgorithmValuesHolder.FIPS_MINIMAL_KEY_SIZE) {
                        throw APIException.badRequests
                                .invalidParameterBelowMinimum(
                                        "private_key",
                                        keyLength,
                                        KeyCertificateAlgorithmValuesHolder.FIPS_MINIMAL_KEY_SIZE,
                                        "bits");
                    }

                    KeyCertificatePairGenerator.validateKeyAndCertPairing(rsaPrivateKey, chain);

                    Certificate prevCert = null;
                    try {
                        prevCert =
                                getKeyStore().getCertificate(
                                        KeystoreEngine.ViPR_KEY_AND_CERTIFICATE_ALIAS);
                        if (cert.equals(prevCert)) {
                            throw APIException.badRequests.newCertificateMustBeSpecified();
                        }
                    } catch (KeyStoreException e) {
                        _log.error("failed to get previous certificate", e);
                    }

                    selfsigned = Boolean.FALSE;
                } catch (CertificateExpiredException | CertificateNotYetValidException e) {
                    throw APIException.badRequests.invalidField("key_and_certificate",
                            chain[0].toString());
                } catch (CertificateException e) {
                    throw APIException.badRequests.failedToLoadCertificateFromString(
                            newKey.getCertificateChain(), e);
                } catch (Exception e) {
                    throw APIException.badRequests.failedToLoadKeyFromString(e);
                }
            }

            Boolean selfSignedPrevious =
                    KeyStoreUtil.isSelfGeneratedCertificate(coordConfigStoringHelper);

            // This has to be done before the set keys entry call
            KeyStoreUtil.setSelfGeneratedCertificate(coordConfigStoringHelper, selfsigned);

            try {
                getKeyStore().setKeyEntry(KeystoreEngine.ViPR_KEY_AND_CERTIFICATE_ALIAS, key, chain);
            } catch (KeyStoreException e) {
                _log.error("failed to rotate key and certificate chain.");
                KeyStoreUtil.setSelfGeneratedCertificate(coordConfigStoringHelper,
                        selfSignedPrevious);
                throw SecurityException.fatals.failedToUpdateKeyCertificateEntry(e);
            }

            if (!certificateVersionHelper.updateCertificateVersion()) {
                _log.error("failed to update version for new key and certificate chain.");
                throw SecurityException.fatals.failedToUpdateKeyCertificateEntry();
            }
            return getCertificateChain();
        } finally {
            if (key != null) {
                // SensitiveData.clear(key);
                SecurityUtil.clearSensitiveData(key);
            }
            if (rsaPrivateKey != null) {
                // SensitiveData.clear(rsaPrivateKey);
                SecurityUtil.clearSensitiveData(rsaPrivateKey);
            }
        }
    }

    /***
     * Checks if the all the VDCs in the federation are in the
     * same expected version or not.
     * 
     * @usage \vdc\admin\check-compatibility?expect_verion=2.3
     * @param expectVersion
     * @return true if all the VDCs in the federation are in
     *         the expect_version otherwise false.
     */
    @GET
    @Path("check-compatibility")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public Response checkGeoVersionCompatibility(@QueryParam("expect_version") String expectVersion) {
        if (!isValidateVersion(expectVersion)) {
            _log.warn("invalid Geo version {} : only support major and minor ", expectVersion);
            throw APIException.badRequests.invalidParameter("invalid Geo version {} : only support major and minor ", expectVersion);
        }

        Boolean versionSupported = this._dbClient.checkGeoCompatible(expectVersion);
        return Response.ok(versionSupported.toString(), MediaType.APPLICATION_OCTET_STREAM).build();
    }

    /**
     * Check if the setup is geo-distributed multi-VDC
     * 
     * @usage \vdc\admin\check-geo-distributed
     * @return true if the setup is geo-distributed VDC
     */
    @GET
    @Path("check-geo-distributed")
    public Response checkGeoSetup() {
        Boolean isGeo = false;
        List<URI> ids = _dbClient.queryByType(VirtualDataCenter.class, true);
        Iterator<VirtualDataCenter> iter = _dbClient.queryIterativeObjects(VirtualDataCenter.class, ids);
        while (iter.hasNext()) {
            VirtualDataCenter vdc = iter.next();
            if (!vdc.getLocal()) {
                if ((vdc.getConnectionStatus() == VirtualDataCenter.ConnectionStatus.ISOLATED)
                        || vdc.getRepStatus() == VirtualDataCenter.GeoReplicationStatus.REP_NONE) {
                    continue;
                }
                isGeo = true;
            }
        }
        return Response.ok(isGeo.toString(), MediaType.APPLICATION_OCTET_STREAM).build();
    }

    private static boolean isValidateVersion(String expectVersion) {
        if (StringUtils.isBlank(expectVersion)) {
            return false;
        }
        if (expectVersion.split(VERSION_PART_SEPERATOR).length != 2) {
            return false;
        }

        return true;
    }

    private Properties prepareVdcOpParam(URI localVdcId,
            VirtualDataCenterAddParam param) throws DatabaseException {

        Properties taskProperties = new Properties();
        taskProperties.setProperty(GeoServiceJob.LOCAL_VDC_ID, localVdcId.toString());
        taskProperties.setProperty(GeoServiceJob.VDC_NAME, param.getName());
        taskProperties.setProperty(GeoServiceJob.VDC_API_ENDPOINT, param.getApiEndpoint());
        taskProperties.setProperty(GeoServiceJob.VDC_SECRETE_KEY, param.getSecretKey());
        String description = param.getDescription();
        if (description != null) {
            taskProperties.setProperty(GeoServiceJob.VDC_DESCRIPTION, param.getDescription());
        }
        String geoCommandEndpoint = param.getGeoCommandEndpoint();
        if (geoCommandEndpoint != null) {
            taskProperties.setProperty(GeoServiceJob.VDC_GEOCOMMAND_ENDPOINT, param.getGeoCommandEndpoint());
        }
        String geoDataEndpoint = param.getGeoDataEndpoint();
        if (geoDataEndpoint != null) {
            taskProperties.setProperty(GeoServiceJob.VDC_GEODATA_ENDPOINT, param.getGeoDataEndpoint());
        }
        taskProperties.setProperty(GeoServiceJob.VDC_CERTIFICATE_CHAIN, param.getCertificateChain());
        String vdcShortId = _geoHelper.createMonoVdcId();
        taskProperties.setProperty(GeoServiceJob.VDC_SHORT_ID, vdcShortId);
        taskProperties.setProperty(GeoServiceJob.OPERATED_VDC_ID, URIUtil.createVirtualDataCenterId(vdcShortId).toString());

        return taskProperties;
    }

    private Properties modifyVirtualDataCenterInfo(VirtualDataCenter localVdc, VirtualDataCenter vdc,
            VirtualDataCenterModifyParam param, Certificate[] certchain) throws DatabaseException {

        Properties taskProperties = new Properties();
        taskProperties.setProperty(GeoServiceJob.LOCAL_VDC_ID, localVdc.getId().toString());
        taskProperties.setProperty(GeoServiceJob.OPERATED_VDC_ID, vdc.getId().toString());

        if (StringUtils.isNotEmpty(param.getName())) {
            taskProperties.setProperty(GeoServiceJob.VDC_NAME, param.getName());

            /*
             * TODO: it is part of change vip flow
             * if (StringUtils.isNotEmpty(param.getApiEndpoint()))
             * taskProperties.setProperty(GeoServiceJob.VDC_API_ENDPOINT,param.getApiEndpoint);
             * vdc.setLabel(param.getApiEndpoint());
             */

            /*
             * TODO: wait for security to support key rotation
             * if (StringUtils.isNotEmpty(param.getSecretKey()))
             * taskProperties.setProperty(GeoServiceJob.VDC_SECRETE_KEY,param.getSecretKey());
             */
        }

        if (StringUtils.isNotEmpty(param.getDescription())) {
            taskProperties.setProperty(GeoServiceJob.VDC_DESCRIPTION, param.getDescription());
        }

        if (StringUtils.isNotEmpty(param.getGeoCommandEndpoint())) {
            taskProperties.setProperty(GeoServiceJob.VDC_GEOCOMMAND_ENDPOINT, param.getGeoCommandEndpoint());
        }

        if (StringUtils.isNotEmpty(param.getGeoDataEndpoint())) {
            taskProperties.setProperty(GeoServiceJob.VDC_GEODATA_ENDPOINT, param.getGeoDataEndpoint());
        }

        if (certchain != null) {
            try {
                String certChain = KeyCertificatePairGenerator.getCertificateChainAsString(certchain);
                taskProperties.setProperty(GeoServiceJob.VDC_CERTIFICATE_CHAIN, certChain);
            } catch (CertificateEncodingException e) {
                throw APIException.badRequests.failedToLoadKeyFromString(e);
            }
        }

        return taskProperties;
    }

    /**
     * Add a job to the async queue and return a rest representation for the task
     * 
     * @param vdc the vdc to operate on
     * @param jobType the operation to perform
     * @return the task representation
     */
    private TaskResourceRep enqueueJob(VirtualDataCenter vdc, JobType jobType) {
        return enqueueJob(vdc, jobType, null);
    }

    private TaskResourceRep enqueueJob(VirtualDataCenter vdc, JobType jobType, List<Object> params) {
        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(VirtualDataCenter.class, vdc.getId(),
                taskId, jobType.toResourceOperationType());

        // add to the job queue
        try {
            GeoServiceJob job = new GeoServiceJob(vdc, taskId, jobType, params);
            _geoHelper.enqueueJob(job);
        } catch (Exception ex) {
            _log.error("Exception occurred while enqueue job on due to:", ex);
            ServiceCoded coded = ServiceError.buildServiceError(
                    ServiceCode.COORDINATOR_UNABLE_TO_QUEUE_JOB, ex.getMessage());
            op.error(coded);
        }
        return TaskMapper.toTask(vdc, taskId, op);
    }

    private class ZoneRoleInputFilter extends PermissionsHelper.RoleInputFilter {
        public ZoneRoleInputFilter(TenantOrg tenant) {
            super(tenant);
        }

        @Override
        public boolean isValidRole(String ace) {
            return _permissionsHelper.isExternalRoleZoneLevel(ace);
        }

        @Override
        public StringSetMap convertFromRolesRemove(List<RoleAssignmentEntry> remove) {
            return convertFromRolesNoLocalUsers(remove);
        }

        @Override
        protected void validatePrincipals() {
            StringBuilder error = new StringBuilder();
            PrincipalsToValidate principalsToValidate = new PrincipalsToValidate();
            principalsToValidate.setTenantId(_tenant.getId().toString());
            principalsToValidate.setGroups(_groups);
            principalsToValidate.setUsers(_users);

            if (!Validator.validatePrincipals(principalsToValidate, error)) {
                throw APIException.badRequests.invalidRoleAssignments(error.toString());
            }
        }

        @Override
        protected void addPrincipalToList(PermissionsKey key,
                RoleAssignmentEntry roleAssignment) {
            switch (key.getType()) {
                case GROUP:
                    _groups.add(key.getValue());
                    break;
                case SID:
                    _users.add(key.getValue());
                    break;
                case TENANT:
                default:
                    break;
            }
        }

        @Override
        public StringSetMap convertFromRolesAdd(List<RoleAssignmentEntry> add,
                boolean validate) {
            StringSetMap returnedStringSetMap = convertFromRolesNoLocalUsers(add);
            if (validate) {
                validatePrincipals();
            }
            return returnedStringSetMap;
        }
    }

    private RoleAssignments getRoleAssignmentsResponse(VirtualDataCenter vdc) {
        RoleAssignments roleAssignmentsResponse = new RoleAssignments();
        roleAssignmentsResponse.setAssignments(
                _permissionsHelper.convertToRoleAssignments(vdc.getRoleAssignments(), true));
        roleAssignmentsResponse.setSelfLink(getCurrentSelfLink());
        return roleAssignmentsResponse;
    }

    private KeyCertificatePairGenerator getGenerator() {
        if (generator == null) {
            generator = new KeyCertificatePairGenerator();
            generator.setKeyCertificateAlgorithmValuesHolder(new KeyCertificateAlgorithmValuesHolder(coordinator));
        }
        return generator;
    }

    private List<Object> prepareKeyCert(KeyAndCertificateChain newKey) {
        Boolean selfsigned = null;
        byte[] key = null;
        Certificate[] chain = null;

        if (newKey != null) {
            try {
                chain =
                        KeyCertificatePairGenerator.getCertificateChainFromString(newKey
                                .getCertificateChain());
                if (ArrayUtils.isEmpty(chain)) {
                    throw APIException.badRequests.failedToLoadCertificateFromString(newKey
                            .getCertificateChain());
                }

                key = SecurityUtil.loadPrivateKeyFromPEMString(newKey.getPrivateKey());
                selfsigned = Boolean.FALSE;
            } catch (CertificateException e) {
                throw APIException.badRequests.failedToLoadCertificateFromString(
                        newKey.getCertificateChain(), e);
            } catch (Exception e) {
                throw APIException.badRequests.failedToLoadKeyFromString(e);
            }
        } else {
            KeyCertificateEntry pair = getGenerator().generateKeyCertificatePair();
            selfsigned = Boolean.TRUE;
            key = pair.getKey();
            chain = pair.getCertificateChain();
        }

        List<Object> list = new ArrayList<Object>();
        list.add(selfsigned);
        list.add(key);
        list.add(chain);

        return list;
    }

    public static Certificate verifyVdcCert(String certstr, String apiEndpoint, Boolean certchain) {
        // verify vdc cert
        _log.info("Verifying certificate ...");
        Certificate cert = null;
        try {
            if (certchain) {
                Certificate[] chain = null;
                chain = KeyCertificatePairGenerator.getCertificateChainFromString(certstr);
                if (!ArrayUtils.isEmpty(chain)) {
                    cert = chain[0];
                }
            } else {
                cert = KeyCertificatePairGenerator.getCertificateFromString(certstr);
            }

            if (cert == null) {
                throw APIException.badRequests.failedToLoadCertificateFromString(certstr);
            }

            validateEndpoint((X509Certificate) cert, apiEndpoint);

        } catch (CertificateException e) {
            _log.error(e.getMessage(), e);
        }
        return cert;
    }

    private void blockRoot() {
        Principal principal = sc.getUserPrincipal();
        if (!(principal instanceof StorageOSUser)) {
            throw APIException.forbidden.invalidSecurityContext();
        }
        StorageOSUser user = (StorageOSUser) principal;

        if (user.getName().equalsIgnoreCase(ROOT)) {
            throw APIException.forbidden.insufficientPermissionsForUser(ROOT);
        }
    }

    /**
     * check if given endpoint is valid by comparing ips in endpoint against subject names in certificate
     * 
     * @param x509Certificate
     * @param endpoint
     */
    private static void validateEndpoint(X509Certificate x509Certificate, String endpoint) {
        Set<String> ipsOfEndpoint = new HashSet<String>();
        Set<String> subjectIpsInCert = new HashSet<String>();

        // retrieves IPs from endpoint, endpoint could be provided as hostname or IP
        try {
            for (InetAddress addr : InetAddress.getAllByName(endpoint)) {
                ipsOfEndpoint.add(addr.getHostAddress());
            }
        } catch (UnknownHostException uhe) {
            _log.error(uhe.getMessage());
        }

        // retrieves IPs from certificate's subject alternative names
        Collection altNames = null;
        try {
            altNames = x509Certificate.getSubjectAlternativeNames();
        } catch (CertificateParsingException cpe) {
            _log.error(cpe.getMessage());
        }

        if (altNames != null) {
            Iterator itAltNames = altNames.iterator();
            while (itAltNames.hasNext()) {
                List extensionEntry = (List) itAltNames.next();
                Integer nameType = (Integer) extensionEntry.get(0);
                if (nameType == SUBALTNAME_DNSNAME || nameType == SUBALTNAME_IPADDRESS) {
                    String name = (String) extensionEntry.get(1);
                    try {
                        for (InetAddress addr : InetAddress.getAllByName(name)) {
                            subjectIpsInCert.add(addr.getHostAddress());
                        }
                    } catch (UnknownHostException uhe) {
                        _log.error(uhe.getMessage());
                    }
                }
            }
        }

        // check at least one IP in both subjectIpsInCert and ipsOfEndpoint
        if (ipsOfEndpoint.isEmpty() || subjectIpsInCert.isEmpty()) {
            throw APIException.badRequests.apiEndpointNotMatchCertificate(endpoint);
        }

        boolean bFound = false;
        for (String ip : ipsOfEndpoint) {
            if (subjectIpsInCert.contains(ip)) {
                bFound = true;
                break;
            }
        }

        if (!bFound) {
            throw APIException.badRequests.apiEndpointNotMatchCertificate(endpoint);
        }
    }
}
