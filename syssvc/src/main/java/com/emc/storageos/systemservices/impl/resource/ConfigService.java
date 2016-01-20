/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.resource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import com.emc.storageos.security.ipsec.IPsecConfig;
import com.emc.storageos.security.password.InvalidLoginManager;
import com.emc.storageos.model.auth.LoginFailedIPList;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.model.PropertyInfoExt;
import com.emc.storageos.coordinator.client.model.RepositoryInfo;
import com.emc.storageos.model.property.PropertiesMetadata;
import com.emc.storageos.model.property.PropertyInfoRestRep;
import com.emc.storageos.model.property.PropertyInfoUpdate;
import com.emc.storageos.model.property.PropertyList;
import com.emc.storageos.model.property.PropertyMetadata;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.systemservices.exceptions.CoordinatorClientException;
import com.emc.storageos.systemservices.impl.iso.CreateISO;
import com.emc.storageos.systemservices.impl.property.PropertyManager;
import com.emc.storageos.systemservices.impl.propertyhandler.PropertyHandlers;
import com.emc.storageos.systemservices.impl.upgrade.ClusterAddressPoller;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;
import com.emc.storageos.model.property.PropertyInfo.PropCategory;
import com.emc.storageos.systemservices.impl.validate.PropertiesConfigurationValidator;
import com.emc.vipr.model.sys.ClusterInfo;
import com.emc.vipr.model.sys.eventhandler.ConnectEmcEmail;
import com.emc.vipr.model.sys.eventhandler.ConnectEmcFtps;

import static com.emc.storageos.coordinator.client.model.Constants.HIDDEN_TEXT_MASK;
import static com.emc.storageos.systemservices.mapper.ClusterInfoMapper.toClusterResponse;

@Path("/config/")
public class ConfigService {
    // keys used in returning properties
    public static final String VERSION = "-clusterversion";
    public static final String IPSEC_KEY = "-ipsec_key";
    public static final Map<String, String> propertyToParameters = new HashMap() {
        {
            put("node_count", "-nodecount");

            put("network_vip", "-vip");
            put("network_1_ipaddr", "-ipaddr_1");
            put("network_2_ipaddr", "-ipaddr_2");
            put("network_3_ipaddr", "-ipaddr_3");
            put("network_4_ipaddr", "-ipaddr_4");
            put("network_5_ipaddr", "-ipaddr_5");
            put("network_gateway", "-gateway");
            put("network_netmask", "-netmask");

            put("network_vip6", "-vip6");
            put("network_1_ipaddr6", "-ipaddr6_1");
            put("network_2_ipaddr6", "-ipaddr6_2");
            put("network_3_ipaddr6", "-ipaddr6_3");
            put("network_4_ipaddr6", "-ipaddr6_4");
            put("network_5_ipaddr6", "-ipaddr6_5");
            put("network_gateway6", "-gateway6");
            put("network_prefix_length", "-ipv6prefixlength");
        }
    };

    public static final String MODE = "mode";
    public static final String NODE_ID = "node_id";

    @Autowired
    private AuditLogManager _auditMgr;

    @Autowired
    private ClusterAddressPoller clusterPoller;

    @Autowired
    private PropertyManager propertyManager;

    @Autowired
    protected InvalidLoginManager _invLoginManager;

    private IPsecConfig ipsecConfig;

    public static final String CERTIFICATE_VERSION = "certificate_version";
    private static final Logger _log = LoggerFactory.getLogger(ConfigService.class);
    private static final String EVENT_SERVICE_TYPE = "config";
    private CoordinatorClientExt _coordinator = null;
    private PropertiesMetadata _propsMetadata = null;
    private PropertiesConfigurationValidator _propertiesConfigurationValidator;
    private Properties defaultProperties;
    private Properties ovfProperties;
    private PropertyHandlers _propertyHandlers;

    @Context
    protected SecurityContext sc;

    /**
     * Get StorageOSUser from the security context
     * 
     * @return
     */
    protected StorageOSUser getUserFromContext() {
        if (!hasValidUserInContext()) {
            throw APIException.forbidden.invalidSecurityContext();
        }
        return (StorageOSUser) sc.getUserPrincipal();
    }

    /**
     * Determine if the security context has a valid StorageOSUser object
     * 
     * @return true if the StorageOSUser is present
     */
    protected boolean hasValidUserInContext() {
        if ((sc != null) && (sc.getUserPrincipal() instanceof StorageOSUser)) {
            return true;
        } else {
            return false;
        }
    }

    public void setProxy(CoordinatorClientExt proxy) {
        _coordinator = proxy;
    }

    public void setPropsMetadata(PropertiesMetadata propsMetadata) {
        _propsMetadata = propsMetadata;
    }

    public void setPropertiesConfigurationValidator(PropertiesConfigurationValidator
            propertiesConfigurationValidator) {
        _propertiesConfigurationValidator = propertiesConfigurationValidator;
    }

    public void setDefaultProperties(Properties defaults) {
        defaultProperties = defaults;
    }

    public void setPropertyHandlers(PropertyHandlers propertyHandlers) {
        _propertyHandlers = propertyHandlers;
    }

    public void setIpsecConfig(IPsecConfig ipsecConfig) {
        this.ipsecConfig = ipsecConfig;
    }


    /**
     * Get properties defaults
     * 
     * @return map containing key, value pair
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Map<String, String> getPropertiesDefaults() {
        return (Map) defaultProperties;
    }

    public void setOvfProperties(Properties ovfProps) {
        ovfProperties = ovfProps;
    }

    /**
     * Get ovf properties
     * 
     * @return map containing key, value pair
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    // this can't be named after the default getter, since the return type is different with
    // the argument type of the setter
            public
            Map<String, String> getPropertiesOvf() {
        return (Map) ovfProperties;
    }

    /**
     * Get config properties
     * 
     * @return map containing key, value pair
     */
    public Map<String, String> getConfigProperties() {
        Map<String, String> mergedProps = mergeProps(getPropertiesDefaults(), getMutatedProps());
        return mergedProps;
    }

    /**
     * Get obsolete properties
     * 
     * @return map containing key, value pair
     */
    public Map<String, String> getObsoleteProperties() {
        Map<String, String> obsoletes = new HashMap<String, String>();
        Map<String, String> overrides = new HashMap<String, String>();
        overrides = getMutatedProps();
        Set<String> obsoleteKeys = overrides.keySet();
        obsoleteKeys.removeAll(defaultProperties.keySet());
        for (String obsoleteKey : obsoleteKeys) {
            obsoletes.put(obsoleteKey, overrides.get(obsoleteKey));
        }

        return obsoletes;
    }

    /**
     * Get system configuration properties
     * 
     * @brief Get system properties
     * @prereq none
     * @param category - type of properties to return: all, config, ovf, mutated, secrets (require SecurityAdmin role)
     *            or obsolete
     * @return Properties Information if success. Error response, if error./**
     */
    @GET
    @Path("properties/")
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.SYSTEM_MONITOR })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public PropertyInfoRestRep getProperties(@DefaultValue("all") @QueryParam("category") String category) throws Exception {
        switch (PropCategory.valueOf(category.toUpperCase())) {
            case ALL:
                return getTargetPropsCommon();
            case CONFIG:
                return new PropertyInfoRestRep(getConfigProperties());
            case OVF:
                return new PropertyInfoRestRep(getPropertiesOvf());
            case REDEPLOY:
                Map<String, String> props = getPropertiesOvf();

                props.remove(MODE);
                props.remove(NODE_ID);

                Map<String, String> clusterInfo = new HashMap();
                Set<Map.Entry<String, String>> ovfProps = props.entrySet();
                for (Map.Entry<String, String> ovfProp : ovfProps) {
                    String parameter = propertyToParameters.get(ovfProp.getKey());
                    if (parameter == null) {
                        continue;
                    }
                    clusterInfo.put(parameter, ovfProp.getValue());
                }

                // Add ipsec key
                clusterInfo.put(IPSEC_KEY, ipsecConfig.getPreSharedKey());

                // Add version info
                RepositoryInfo info = _coordinator.getTargetInfo(RepositoryInfo.class);
                clusterInfo.put(VERSION, info.getCurrentVersion().toString());

                _log.info("clusterInfo={}", clusterInfo);
                return new PropertyInfoRestRep(clusterInfo);
            case MUTATED:
                return new PropertyInfoRestRep(getMutatedProps());
            case SECRETS:
                StorageOSUser user = getUserFromContext();
                if (!user.getRoles().contains(Role.SECURITY_ADMIN.toString())) {
                    throw APIException.forbidden.onlySecurityAdminsCanGetSecrets();
                }
                return getTargetPropsCommon(false);
            case OBSOLETE:
                return new PropertyInfoRestRep(getObsoleteProperties());
            default:
                throw APIException.badRequests.invalidParameter("category", category);
        }
    }

    /**
     * Update system configuration properties
     * 
     * @brief Update system properties
     * @param setProperty Property's key value pair.
     * @prereq Cluster state should be STABLE
     * @return Cluster information
     */
    @PUT
    @Path("properties/")
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN })
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public Response setProperties(PropertyInfoUpdate setProperty)
            throws Exception {
        PropertyInfoRestRep targetPropInfo = getTargetPropsCommon();

        _log.info("setProperties(): {}", setProperty);

        PropertyInfoRestRep updateProps = getUpdateProps(setProperty, targetPropInfo.getAllProperties());

        return updatePropertiesCommon(updateProps, null);
    }

    /**
     * Update system configuration properties
     * 
     * @brief Update system properties
     * @prereq Cluster state should be STABLE
     * @return Cluster information
     */
    @PUT
    @Path("internal/certificate-version")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public Response incrementCertificateVersion() throws Exception {
        PropertyInfoRestRep targetPropInfo = getTargetPropsCommon();
        String versionStr = targetPropInfo.getProperty(CERTIFICATE_VERSION);
        Integer version = new Integer(versionStr);

        PropertyInfoUpdate setProperty = new PropertyInfoUpdate();
        setProperty.addProperty(CERTIFICATE_VERSION, (++version).toString());

        _log.info("setProperties(): {}", setProperty);

        PropertyInfoRestRep updateProps =
                getUpdateProps(setProperty, targetPropInfo.getAllProperties());

        return updatePropertiesCommon(updateProps, null);
    }

    /**
     * Internal api to get system configuration properties. The api could be by data node.
     * 
     * @brief Get system properties
     * @prereq none
     * @param category - type properties to return: config, ovf, or obsolete
     * @return Properties Information if success. Error response, if error./**
     */
    @POST
    @Path("internal/properties/")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public PropertyInfoRestRep getInternalProperties(String category) throws Exception {
        return getProperties(category);
    }

    /**
     * Show metadata of system configuration properties
     * 
     * @brief Show properties metadata
     * @prereq none
     * @return Properties Metadata if success. Error response, if error.
     */
    @GET
    @Path("properties/metadata/")
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.SYSTEM_MONITOR })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public PropertiesMetadata getPropMetadata() throws Exception {
        if (_propsMetadata == null) {
            throw APIException.internalServerErrors.targetIsNullOrEmpty("Property metadata");
        }
        return _propsMetadata;
    }

    /**
     * Configure ConnectEMC FTPS transport related properties
     * 
     * @brief Configure ConnectEMC FTPS properties
     * @prereq Cluster state should be STABLE
     * @return ConnectEMC FTPS related properties
     */
    @POST
    @Path("connectemc/ftps/")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public Response configureConnectEmcFtpsParams(ConnectEmcFtps ftpsParams)
            throws Exception {
        PropertyInfoUpdate ext = ConfigService.ConfigureConnectEmc.configureFtps(ftpsParams);
        PropertyInfoRestRep targetPropInfo = getTargetPropsCommon();
        PropertyInfoRestRep updateProps = getUpdateProps(ext, targetPropInfo.getAllProperties());
        return updatePropertiesCommon(updateProps, null);
    }

    /**
     * Configure ConnectEMC SMTP/Email transport related properties
     * 
     * @brief Configure ConnectEMC SMTP/Email properties
     * @prereq Cluster state should be STABLE
     * @return Properties related to ConnectEMC Email
     */
    @POST
    @Path("connectemc/email/")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public Response configureConnectEmcEmailParams(ConnectEmcEmail emailParams)
            throws Exception {
        PropertyInfoRestRep targetPropInfo = getTargetPropsCommon();
        PropertyInfoUpdate ext = ConfigService.ConfigureConnectEmc.configureEmail(emailParams);
        PropertyInfoRestRep updateProps = getUpdateProps(ext, targetPropInfo.getAllProperties());
        return updatePropertiesCommon(updateProps, null);
    }

    /**
     * Reset configuration properties to their default values. Properties with
     * no default values will remain unchanged
     * 
     * @brief Reset system properties
     * @param propertyList property list
     * @prereq Cluster state should be STABLE
     * @return Cluster information
     */
    @POST
    @Path("properties/reset/")
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN })
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public Response resetProps(PropertyList propertyList, @QueryParam("removeObsolete") String forceRemove)
            throws Exception {
        // get metadata
        Map<String, PropertyMetadata> metadata = PropertiesMetadata.getGlobalMetadata();
        // get target property set
        PropertyInfoRestRep targetPropInfo = getTargetPropsCommon();
        // get reset property set
        PropertyInfoRestRep resetProps = getResetProps(propertyList, targetPropInfo.getAllProperties(), metadata);
        // get obsolete property set
        List<String> obsoleteProps = isSet(forceRemove) ? getObsoleteProps(targetPropInfo.getAllProperties(), metadata) : null;
        // update properties with default value
        return updatePropertiesCommon(resetProps, obsoleteProps);
    }

    // Keep this library for now.
    private Response getVisiblePropertiesISOCommon() throws Exception {
        _log.info("getVisiblePropertiesISO(): going to fetch ISO information");
        PropertyInfoRestRep propertyInfo = new PropertyInfoRestRep(getTargetPropsCommon().getProperties());

        if (propertyInfo.getAllProperties() == null || propertyInfo.getAllProperties().size() == 0) {
            _log.error("getVisiblePropertiesISO(): No properties found");
            throw APIException.internalServerErrors.targetIsNullOrEmpty("Properties");
        }

        InputStream isoStream = new ByteArrayInputStream(CreateISO.getBytes
                (getPropertiesOvf(), getMutatedProps()));

        return Response.ok(isoStream).header("content-disposition", "attachment; filename = config.iso").build();
    }

    /**
     * Get target properties
     * 
     * @return target properties
     */
    private PropertyInfoRestRep getTargetPropsCommon() {
        return getTargetPropsCommon(true);
    }

    /**
     * Get target properties
     * 
     * @param maskSecretsProperties whether secrets properties should be masked out
     * @return target properties
     */
    private PropertyInfoRestRep getTargetPropsCommon(boolean maskSecretsProperties) {
        PropertyInfoExt targetPropInfo = new PropertyInfoExt();
        try {
            targetPropInfo.setProperties(mergeProps(getPropertiesDefaults(), getMutatedProps(maskSecretsProperties)));
            targetPropInfo.getProperties().putAll(getPropertiesOvf());
        } catch (Exception e) {
            throw APIException.internalServerErrors.getObjectFromError("target property", "coordinator", e);
        }

        return new PropertyInfoRestRep(targetPropInfo.getAllProperties());
    }

    /*
     * Get mutated props
     * 
     * @return mutated properties from coordinator
     */
    private Map<String, String> getMutatedProps() {
        return getMutatedProps(true);
    }

    /*
     * Get mutated props
     * 
     * @param maskSecretsProperties whether secrets properties should be masked out
     * 
     * @return mutated properties from coordinator
     */
    private Map<String, String> getMutatedProps(boolean maskSecretsProperties) {
        Map<String, String> overrides;
        try {
            overrides = _coordinator.getTargetProperties().getProperties();
        } catch (Exception e) {
            _log.info("Fail to get the cluster information ", e);
            return new TreeMap<>();
        }

        if (!maskSecretsProperties) {
            return overrides;
        }

        // Mask out the secrets properties
        Map<String, String> ret = new TreeMap<>();
        for (Map.Entry<String, String> entry : overrides.entrySet()) {
            if (PropertyInfoExt.isSecretProperty(entry.getKey())) {
                ret.put(entry.getKey(), HIDDEN_TEXT_MASK);
            } else {
                ret.put(entry.getKey(), entry.getValue());
            }
        }

        return ret;
    }

    /**
     * Merge properties
     * 
     * @param defaultProps
     * @param overrideProps
     * @return map containing key, value pair
     */
    public static Map<String, String> mergeProps(Map<String, String> defaultProps, Map<String, String> overrideProps) {
        Map<String, String> mergedProps = new HashMap<String, String>(defaultProps);
        for (Map.Entry<String, String> entry : overrideProps.entrySet()) {
            mergedProps.put(entry.getKey(), entry.getValue());
        }
        return mergedProps;
    }

    /**
     * Common method called by setProps and resetProps
     * Callers should prepare valid updateProps and deleteProps,
     * by verifying against validateAndUpdateProperties
     * before call this method
     * 
     * @param updateProps update properties' keys and values
     * @param deleteKeys delete properties' keys
     * @throws Exception
     * @throws CoordinatorClientException
     */
    private Response updatePropertiesCommon(PropertyInfoRestRep updateProps, List<String> deleteKeys)
            throws Exception {
        // validate
        if (!_coordinator.isClusterUpgradable()) {
            throw APIException.serviceUnavailable.clusterStateNotStable();
        }

        StringBuilder propChanges = new StringBuilder();

        // get current property set
        PropertyInfoRestRep currentProps = _coordinator.getTargetProperties();
        PropertyInfoRestRep oldProps = new PropertyInfoRestRep();
        oldProps.addProperties(currentProps.getAllProperties());

        boolean doSetTarget = false;

        // remove properties
        if (deleteKeys != null && !deleteKeys.isEmpty()) {
            doSetTarget = true;
            for (String key : deleteKeys) {
                currentProps.removeProperty(key);
                propChanges.append(key);
                propChanges.append(" deleted");
            }
            currentProps.removeProperties(deleteKeys);
        }

        // update properties, increment config_version
        if (updateProps != null && !updateProps.isEmpty()) {
            doSetTarget = true;
            currentProps.addProperties(updateProps.getAllProperties());
            String configVersion = System.currentTimeMillis() + "";
            currentProps.addProperty(PropertyInfoRestRep.CONFIG_VERSION, configVersion);
            if (propChanges.length() > 0) {
                propChanges.append(",");
            }
            propChanges.append(PropertyInfoRestRep.CONFIG_VERSION);
            propChanges.append("=");
            propChanges.append(configVersion);
        }

        if (doSetTarget) {

            // perform before handlers
            _propertyHandlers.before(oldProps, currentProps);
            _coordinator.setTargetProperties(currentProps.getAllProperties());
            for (Map.Entry<String, String> entry : updateProps.getAllProperties().entrySet()) {
                if (propChanges.length() > 0) {
                    propChanges.append(",");
                }
                propChanges.append(entry.getKey());
                propChanges.append("=");
                // Hide encrypted string in audit log
                if (PropertyInfoExt.isEncryptedProperty(entry.getKey())) {
                    propChanges.append(HIDDEN_TEXT_MASK);
                } else {
                    propChanges.append(entry.getValue());
                }
            }
            auditConfig(OperationTypeEnum.UPDATE_SYSTEM_PROPERTY,
                    AuditLogManager.AUDITLOG_SUCCESS, null, propChanges.toString());

            // perform after handlers
            _propertyHandlers.after(oldProps, currentProps);

        }

        ClusterInfo clusterInfo = _coordinator.getClusterInfo();
        if (clusterInfo == null) {
            throw APIException.internalServerErrors.targetIsNullOrEmpty("Cluster information");
        }
        return toClusterResponse(clusterInfo);
    }

    /**
     * Validate the properties being submitted.
     * 
     * @param propsMap properties to be validated
     * @param bReset indicate the property change request is called from resetting path or updating path.
     *            the value validation difference between resetting path and updating path is:
     *            resetting path takes null and empty string as valid.
     */
    private void validateAndUpdateProperties(Map<String, String> propsMap, boolean bReset) {

        for (Map.Entry<String, String> prop : propsMap.entrySet()) {
            if (prop.getKey() == null || prop.getKey().isEmpty()) {
                throw APIException.badRequests.propertyIsNullOrEmpty();
            }

            // Get and update valid property value
            String validPropVal = _propertiesConfigurationValidator
                    .getValidPropValue(prop.getKey(), prop.getValue(), true, bReset);
            propsMap.put(prop.getKey(), validPropVal);
        }
    }

    private boolean isSet(final String force) {
        return "1".equals(force);
    }

    /**
     * Get update property set, which values are different from target
     * 
     * @param propsToUpdate property set to update in request
     * @param targetProps target properties
     * @return update property set
     */
    private PropertyInfoRestRep getUpdateProps(final PropertyInfoUpdate propsToUpdate,
            final Map<String, String> targetProps) {
        // validate the changed property against it's metadata to ensure property
        // integrity.
        validateAndUpdateProperties(propsToUpdate.getAllProperties(), false);

        PropertyInfoRestRep updateProps = new PropertyInfoRestRep();

        for (Map.Entry<String, String> entry : propsToUpdate.getAllProperties().entrySet()) {
            final String key = entry.getKey();
            final String value = entry.getValue();

            if (targetProps.containsKey(key) && !targetProps.get(key).equals(value)) {
                updateProps.addProperty(key, value);
            } else if (!targetProps.containsKey(key)) {
            	updateProps.addProperty(key, value);
            }
        }

        return updateProps;
    }

    /**
     * Get reset properties based on properties specified in request,
     * current target property and metadata
     * This method iterates the target property set.
     * If a property is also in metadata
     * either it is contained keysToReset if not null or keysToReset is null (meaning,
     * reset all)
     * compare its value with default, if different, put it in resetProps
     * 
     * @param keysToReset property set to delete in request
     * @param targetProps target properties
     * @param metadata metadata
     * @return reset property set with value set as defaults
     */
    private PropertyInfoRestRep getResetProps(final PropertyList keysToReset,
            final Map<String, String> targetProps,
            final Map<String, PropertyMetadata> metadata) {
        // validate if properties exist in metadata
        if (keysToReset != null && keysToReset.getPropertyList() != null) {
            for (String key : keysToReset.getPropertyList()) {
                if (!targetProps.containsKey(key) || !metadata.containsKey(key)) {
                    throw APIException.badRequests.propertyIsNotValid(key);
                }
            }
        }

        PropertyInfoRestRep resetProps = new PropertyInfoRestRep();

        for (Map.Entry<String, String> entry : targetProps.entrySet()) {
            final String key = entry.getKey();
            final String value = entry.getValue();

            if (metadata.containsKey(key)) {
                // property exist both in target and metadata
                if (keysToReset == null || keysToReset.getPropertyList() == null || keysToReset.getPropertyList().contains(key)) {
                    // property also shows in request list and current value is not equal to default
                    final String defaultValue = metadata.get(key).getDefaultValue();
                    if (defaultValue != null && !value.equals(defaultValue)) {
                        // property has a default value and current value is not equal to default,
                        // reset it to default
                        resetProps.addProperty(key, defaultValue);
                    }
                }
            }
        }

        // validate the changed property against it's metadata to ensure property
        // integrity.
        validateAndUpdateProperties(resetProps.getAllProperties(), true);
        return resetProps;
    }

    /**
     * Get obsolete property list
     * This method compares each property in target with metadata, if not found,
     * add into obsolete property list.
     * 
     * @param targetProps current target property
     * @param metadata metadata
     * @return obsolete property list
     */
    private List<String> getObsoleteProps(final Map<String, String> targetProps,
            final Map<String, PropertyMetadata> metadata) {
        List<String> obsoleteProps = new ArrayList<String>();

        for (Map.Entry<String, String> entry : targetProps.entrySet()) {
            final String key = entry.getKey();
            if (!metadata.containsKey(key)) {
                // metadata doesn't contain the property in target, must be an obsolete property
                obsoleteProps.add(key);
            }
        }

        return obsoleteProps;
    }

    /**
     * Private static class for the purpose of grouping/validating Connect EMC
     * transport configuration properties
     * for Email and FTPS.
     */
    public static class ConfigureConnectEmc {

        static final String FTPS_TRANSPORT = "FTPS";
        static final String SMTP_TRANSPORT = "SMTP";
        static final String YES_VALUE = "Yes";

        /**
         * Build the FTPS Transport Configuration section
         * 
         * @param ftps
         * @return
         */
        public static PropertyInfoUpdate configureFtps(ConnectEmcFtps ftps) {

            PropertyInfoUpdate propInfo = new PropertyInfoUpdate();

            // This property will cause genconfig to generate the ftps
            // ConnectEMC_config.xml file.
            propInfo.addProperty("system_connectemc_transport", FTPS_TRANSPORT);

            // Do not set any properties to NULL value as this causes exceptions
            // in property update processing.
            if (ftps.getSafeEncryption() != null) {
                propInfo.addProperty("system_connectemc_encrypt", (ftps.getSafeEncryption()));
            }

            if (ftps.getHostName() != null) {
                propInfo.addProperty("system_connectemc_ftps_hostname", ftps.getHostName());
            }

            if (ftps.getEmailServer() != null) {
                propInfo.addProperty("system_connectemc_smtp_server", ftps.getEmailServer());
            }

            if (ftps.getNotifyEmailAddress() != null) {
                propInfo.addProperty("system_connectemc_smtp_to", ftps.getNotifyEmailAddress());
            }

            if (ftps.getEmailSender() != null) {
                propInfo.addProperty("system_connectemc_smtp_from", ftps.getEmailSender());
            }

            return propInfo;
        }

        /**
         * Build the Primary Email Transport Configuration section
         * 
         * @param email
         * @throws APIException Username and Password required when Auth Type is set
         * @return
         */
        public static PropertyInfoUpdate configureEmail(ConnectEmcEmail email) {

            PropertyInfoUpdate propInfo = new PropertyInfoUpdate();

            // This property will cause genconfig to generate the email ConnectEMC_config.xml file.
            propInfo.addProperty("system_connectemc_transport", SMTP_TRANSPORT);

            // Do not set any properties to NULL value as this causes exceptions in property update processing.
            if (email.getSafeEncryption() != null) {
                propInfo.addProperty("system_connectemc_encrypt", (email.getSafeEncryption()));
            }

            if (email.getEmailServer() != null) {
                propInfo.addProperty("system_connectemc_smtp_server", email.getEmailServer());
            }

            if (email.getPort() != null) {
                propInfo.addProperty("system_connectemc_smtp_port", email.getPort());
            }

            if (email.getPrimaryEmailAddress() != null) {
                propInfo.addProperty("system_connectemc_smtp_emcto", email.getPrimaryEmailAddress());
            }

            if (email.getNotifyEmailAddress() != null) {
                propInfo.addProperty("system_connectemc_smtp_to", email.getNotifyEmailAddress());
            }

            if (email.getEmailSender() != null) {
                propInfo.addProperty("system_connectemc_smtp_from", email.getEmailSender());
            }

            if (email.getStartTls() != null) {
                propInfo.addProperty("system_connectemc_smtp_enabletls", (email.getStartTls()));
            }

            if (email.getSmtpAuthType() != null) {
                propInfo.addProperty("system_connectemc_smtp_authtype", email.getSmtpAuthType());
            }

            // If auth type set, we need username and password.
            if (!isEmpty(email.getSmtpAuthType())) {
                if (!isEmpty(email.getUserName()) && !isEmpty(email.getPassword())) {
                    // required fields for auth type.
                    propInfo.addProperty("system_connectemc_smtp_username", email.getUserName());
                    propInfo.addProperty("system_connectemc_smtp_password", email.getPassword());
                    propInfo.addProperty("system_connectemc_smtp_enabletlscert", email.getEnableTlsCert());
                } else {
                    throw APIException.badRequests.configEmailError();
                }
            }

            return propInfo;
        }

        /**
         * Convenience method for determining that a field has no data (i.e. null or
         * spaces))
         * 
         * @param field
         * @return
         */
        private static boolean isEmpty(String field) {
            if (field == null || field.isEmpty()) {
                return true;
            }

            return false;
        }
    }

    /**
     * Record audit log for config service
     * 
     * @param auditType Type of AuditLog
     * @param operationalStatus Status of operation
     * @param description Description for the AuditLog
     * @param descparams Description paramters
     */
    public void auditConfig(OperationTypeEnum auditType,
            String operationalStatus,
            String description,
            Object... descparams) {

        _auditMgr.recordAuditLog(null, null,
                EVENT_SERVICE_TYPE,
                auditType,
                System.currentTimeMillis(),
                operationalStatus,
                description,
                descparams);
    }


    /**
     * remove specified IP from login-failed-ip list.
     *
     * @param ip
     * @return
     */
    @DELETE
    @Path("/login-failed-ips/{ip}")
    @CheckPermission( roles = {Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN})
    public Response removeLoginFailedIP(@PathParam("ip") String ip) {
        if (StringUtils.isEmpty(ip)) {
            throw APIException.badRequests.propertyIsNullOrEmpty();
        }

        if (!_invLoginManager.isClientIPExist(ip)) {
            throw APIException.badRequests.clientIpNotExist();
        }

        _invLoginManager.removeInvalidRecord(ip);

        return Response.ok().build();
    }

    /**
     * list client IPs which have failed login attempts recently.
     *
     * @return
     */
    @GET
    @Path("/login-failed-ips")
    @CheckPermission( roles = {Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN})
    public LoginFailedIPList getLoginFailedIPs() {
        LoginFailedIPList response = new LoginFailedIPList();
        response.setLockoutTimeInMinutes(_invLoginManager.getMaxAuthnLoginAttemtsLifeTimeInMins());
        response.setMaxLoginAttempts(_invLoginManager.getMaxAuthnLoginAttemtsCount());
        response.setInvalidLoginsList(_invLoginManager.listBlockedIPs());
        return response;
    }
}
