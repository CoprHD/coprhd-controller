/*
 * Copyright (c) 2013-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.resource;

import static com.emc.storageos.security.password.Constants.PASSWORD_CHANGED_NUMBER;
import static com.emc.storageos.security.password.Constants.PASSWORD_CHANGE_INTERVAL;
import static com.emc.storageos.security.password.Constants.PASSWORD_LOWERCASE_NUMBER;
import static com.emc.storageos.security.password.Constants.PASSWORD_MIN_LENGTH;
import static com.emc.storageos.security.password.Constants.PASSWORD_NUMERIC_NUMBER;
import static com.emc.storageos.security.password.Constants.PASSWORD_PREVENT_DICTIONARY;
import static com.emc.storageos.security.password.Constants.PASSWORD_REPEATING_NUMBER;
import static com.emc.storageos.security.password.Constants.PASSWORD_REUSE_NUMBER;
import static com.emc.storageos.security.password.Constants.PASSWORD_UPPERCASE_NUMBER;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.PropertyInfoExt;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.coordinator.common.impl.ZkConnection;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.model.password.PasswordResetParam;
import com.emc.storageos.model.password.PasswordUpdateParam;
import com.emc.storageos.model.property.PropertiesMetadata;
import com.emc.storageos.model.property.PropertyInfo;
import com.emc.storageos.model.property.PropertyInfoUpdate;
import com.emc.storageos.model.property.PropertyMetadata;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.audit.RecordableAuditLog;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.password.Constants;
import com.emc.storageos.security.password.PasswordUtils;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;
import com.emc.storageos.svcs.errorhandling.resources.ForbiddenException;
import com.emc.storageos.systemservices.exceptions.CoordinatorClientException;
import com.emc.storageos.systemservices.exceptions.LocalRepositoryException;
import com.emc.storageos.systemservices.impl.util.LocalPasswordHandler;
import com.emc.storageos.util.DummyDbClient;

// Requires coordinator to be running on the local host, which build and external servers may not have running, therefore ignoring by default.
// This class can be modified to instantiate coordinator and other dependencies so the test is self-contained.  See DbsvcTestBase and other unit
// tests that accomplish this.
@Ignore
public class PasswordServiceTest {
    private static final Logger log = LoggerFactory.getLogger(PasswordServiceTest.class);

    private DummyConfigService _cfg = new DummyConfigService();
    public PropertyInfoExt _passwordProps = new PropertyInfoExt();
    public PropertiesMetadata _propertiesMetadata = new PropertiesMetadata();
    private DummyEncryptionProvider provider;
    private static final String SYSTEM_ENCPASSWORD_FORMAT = "system_%s_encpassword";  // NOSONAR
                                                                                     // ("squid:S2068 Suppressing sonar violation of hard-coded password")
    Map<String, String> propertiesMap;

    /**
     * local user sysmonitor
     */
    public static final String LOCAL_SYSMON = "sysmonitor";
    /**
     * local user root
     */
    public static final String LOCAL_ROOT = "root";

    /**
     * local user svcuser
     */
    public static final String LOCAL_SVCUSER = "svcuser";

    /**
     * local user proxyuser
     */
    public static final String LOCAL_PROXYUSER = "proxyuser";

    public LocalPasswordHandler getPasswordHandler() {
        LocalPasswordHandler ph = new LocalPasswordHandler();
        initPasswordHandler(ph);
        return ph;
    }

    public DummyLocalPasswordHandler getDummyLocalPasswordHandler() {
        DummyLocalPasswordHandler ph = new DummyLocalPasswordHandler();
        initPasswordHandler(ph);
        return ph;
    }

    private void initPasswordHandler(LocalPasswordHandler ph) {
        Properties properties = generateProperties();

        PasswordUtils.setDefaultProperties(properties);

        PasswordUtils passUtils = new PasswordUtils();
        try {
            CoordinatorClient client = getCoordinatorClient();
            passUtils.setCoordinator(client);
        } catch (Exception e) {
            log.error("Failed to set coordinator client e=", e);
            throw new RuntimeException(e);
        }

        passUtils.setEncryptionProvider(provider);
        passUtils.setDbClient(new DummyDbClient());
        ph.setPasswordUtils(passUtils);

        ph.setConfigService(_cfg);
    }

    private Properties generateProperties() {
        Properties properties = new Properties();
        properties.setProperty(Constants.PASSWORD_EXPIRE_DAYS, "20");
        String encryptPassword = provider.getEncryptedString("changeMe");
        properties.setProperty("system_root_encpassword", encryptPassword);
        properties.setProperty("system_" + LOCAL_PROXYUSER + "_encpassword", encryptPassword);
        properties.setProperty(PASSWORD_MIN_LENGTH, "1");
        properties.setProperty(PASSWORD_LOWERCASE_NUMBER, "1");
        properties.setProperty(PASSWORD_UPPERCASE_NUMBER, "1");
        properties.setProperty(PASSWORD_NUMERIC_NUMBER, "0");
        properties.setProperty(PASSWORD_REPEATING_NUMBER, "1");
        properties.setProperty(PASSWORD_CHANGE_INTERVAL, "1");
        properties.setProperty(PASSWORD_CHANGED_NUMBER, "1");
        properties.setProperty(PASSWORD_REUSE_NUMBER, "1");
        properties.setProperty(PASSWORD_PREVENT_DICTIONARY, "yes");
        return properties;
    }

    public PasswordUpdateParam getDummyPasswordUpdate(String password, String encpassword) {
        PasswordUpdateParam passwordUpdate = new PasswordUpdateParam();
        passwordUpdate.setPassword(password);
        passwordUpdate.setEncPassword(encpassword);
        return passwordUpdate;
    }

    public PasswordResetParam getDummyPasswordReset(String username, String password, String encpassword) {
        PasswordResetParam passwordUpdate = new PasswordResetParam();
        passwordUpdate.setUsername(username);
        passwordUpdate.setPassword(password);
        passwordUpdate.setEncPassword(encpassword);
        return passwordUpdate;
    }

    public Map<String, PropertyMetadata> getPropsMetaData() {
        Map<String, PropertyMetadata> metadata = new TreeMap();
        PropertyMetadata proxyuser_metadata = setPropMetaData("Encrypted password for the 'proxyuser' account",
                "Encrypted (SHA-512) password for the local 'proxyuser' account.",
                "encryptedstring", 255, "Security", true, true, false, true, false, "", true);
        PropertyMetadata sysmonitor_metadata = setPropMetaData("Encrypted password for the 'sysmonitor' account",
                "Encrypted password for the 'sysmonitor' account.",
                "string", 255, "Security", true, true, false, true, false,
                "$6$BIu9aQ6$wBnn9Tn.CUuuoi/JZe.oAOmUDIVCqHpXeem7ZHO5R7dPg2hul8tNCBzwumKrFw8A0qm.LH8YvMJUaN2AL1JVc0", true);
        PropertyMetadata root_metadata = setPropMetaData("Encrypted password for the 'root' account",
                "Encrypted (SHA-512) password for the local 'root' account.",
                "string", 255, "Security", true, true, false, true, false,
                "$6$eBIu9aQ6$wBnn9Tn.CUuuoi/JZe.oAOmUDIVCqHpXeem7ZHO5R7dPg2hul8tNCBzwumKrFw8A0qm.LH8YvMJUaN2AL1JVc0", false);
        PropertyMetadata svcuser_metadata = setPropMetaData("Encrypted password for the 'svcuser' account",
                "Encrypted (SHA-512) password for the local 'svcuser' account.",
                "string", 255, "Security", true, true, false, true, false,
                "$6$eBIu9aQ6$wBnn9Tn.CUuuoi/JZe.oAOmUDIVCqHpXeem7ZHO5R7dPg2hul8tNCBzwumKrFw8A0qm.LH8YvMJUaN2AL1JVc0", false);

        metadata.put("system_proxyuser_encpassword", proxyuser_metadata);
        metadata.put("system_sysmonitor_encpassword", sysmonitor_metadata);
        metadata.put("system_root_encpassword", root_metadata);
        metadata.put("system_svcuser_encpassword", svcuser_metadata);

        return metadata;
    }

    public PropertyMetadata setPropMetaData(String label, String description, String type, int maxLen, String tag, Boolean advanced,
            Boolean userMutable, Boolean userConfigurable, Boolean reconfigRequired, Boolean rebootRequired,
            String value, Boolean controlNodeOnly) {

        PropertyMetadata metaData = new PropertyMetadata();

        metaData.setLabel(label);
        metaData.setDescription(description);
        metaData.setType(type);
        metaData.setMaxLen(maxLen);
        metaData.setTag(tag);
        metaData.setAdvanced(advanced);
        metaData.setUserMutable(userMutable);
        metaData.setUserConfigurable(userConfigurable);
        metaData.setReconfigRequired(reconfigRequired);
        metaData.setRebootRequired(rebootRequired);
        metaData.setValue(value);
        metaData.setControlNodeOnly(controlNodeOnly);

        return metaData;
    }

    @Before
    public void setUp() {
        // fill in the fake ovf repository
        _passwordProps.addProperty(String.format(SYSTEM_ENCPASSWORD_FORMAT, LOCAL_ROOT), "");
        _passwordProps.addProperty(String.format(SYSTEM_ENCPASSWORD_FORMAT, LOCAL_SYSMON), "");
        _passwordProps.addProperty(String.format(SYSTEM_ENCPASSWORD_FORMAT, LOCAL_PROXYUSER), "");
        _passwordProps.addProperty(String.format(SYSTEM_ENCPASSWORD_FORMAT, LOCAL_SVCUSER), "");
        _propertiesMetadata.setMetadata(getPropsMetaData());

        provider = new DummyEncryptionProvider();
        provider.start();
        String encryptPassword = provider.getEncryptedString("changeMe");

        propertiesMap = new HashMap();
        propertiesMap.put("system_root_encpassword", encryptPassword);
        propertiesMap.put("system_proxyuser_encpassword", encryptPassword);
        propertiesMap.put(PASSWORD_MIN_LENGTH, "1");
        propertiesMap.put(PASSWORD_LOWERCASE_NUMBER, "1");
        propertiesMap.put(PASSWORD_UPPERCASE_NUMBER, "1");
        propertiesMap.put(PASSWORD_NUMERIC_NUMBER, "0");
        propertiesMap.put(PASSWORD_REPEATING_NUMBER, "1");
        propertiesMap.put(PASSWORD_CHANGE_INTERVAL, "1");
        propertiesMap.put(PASSWORD_CHANGED_NUMBER, "1");
        propertiesMap.put(PASSWORD_REUSE_NUMBER, "1");
        propertiesMap.put(PASSWORD_PREVENT_DICTIONARY, "yes");

    }

    @Test
    public void testUpdatePassword() {

        PasswordService passwordResource = new PasswordService();
        passwordResource.setPropertiesMetadata(_propertiesMetadata);
        passwordResource.setAuditLogManager(new DummyAuditLogManager());

        PasswordUpdateParam passwordUpdate = getDummyPasswordUpdate("!changeMe3", null);
        passwordUpdate.setOldPassword("changeMe");

        LocalPasswordHandler ph = getDummyLocalPasswordHandler();
        ph.setLocalUsers(createLocalUsers());
        ph.setDbClient(new DummyDbClient());

        ph.setEncryptionProvider(provider);
        passwordResource.setPasswordHandler(ph);

        SecurityContext sc = new DummySecurityContext(LOCAL_ROOT);
        passwordResource.setSecurityContext(sc);

        Response res = passwordResource.updatePassword(null, null, passwordUpdate, false);

        int statusCode = res.getStatus();
        Assert.assertTrue("updatePassword failed with code " + statusCode +
                ": " + res.getEntity().toString(),
                statusCode == Status.OK.getStatusCode());

        sc = new DummySecurityContext(LOCAL_PROXYUSER);
        passwordResource.setSecurityContext(sc);

        res = passwordResource.updatePassword(null, null, passwordUpdate, false);

        statusCode = res.getStatus();
        Assert.assertTrue("updatePassword failed with code " + statusCode +
                ": " + res.getEntity().toString(),
                statusCode == Status.OK.getStatusCode());

    }

    @Test(expected = BadRequestException.class)
    public void testUpdateSamePassword() {

        PasswordService passwordResource = new PasswordService();
        passwordResource.setPropertiesMetadata(_propertiesMetadata);
        passwordResource.setAuditLogManager(new DummyAuditLogManager());

        PasswordUpdateParam passwordUpdate = getDummyPasswordUpdate("ChangeMe", null);
        LocalPasswordHandler ph = new DummyLocalPasswordHandler();
        ph.setLocalUsers(createLocalUsers());
        passwordResource.setPasswordHandler(ph);

        SecurityContext sc = new DummySecurityContext(LOCAL_ROOT);
        passwordResource.setSecurityContext(sc);
        // The following should fail with exception
        Response res = passwordResource.updatePassword(null, null, passwordUpdate, false);

    }

    @Test(expected = ForbiddenException.class)
    public void testUpdatePasswordNoSecurityContext() {

        PasswordService passwordResource = new PasswordService();
        passwordResource.setPropertiesMetadata(_propertiesMetadata);
        passwordResource.setAuditLogManager(new DummyAuditLogManager());

        PasswordUpdateParam passwordUpdate = getDummyPasswordUpdate("!changeme", null);

        LocalPasswordHandler ph = getPasswordHandler();
        passwordResource.setPasswordHandler(ph);

        Response res = passwordResource.updatePassword(null, null, passwordUpdate, false);

        res.getStatus();
    }

    @Test(expected = ForbiddenException.class)
    public void testUpdatePasswordNoPrincipal() {

        PasswordService passwordResource = new PasswordService();
        passwordResource.setPropertiesMetadata(_propertiesMetadata);
        passwordResource.setAuditLogManager(new DummyAuditLogManager());

        PasswordUpdateParam passwordUpdate = getDummyPasswordUpdate("!changeme", null);
        LocalPasswordHandler ph = getPasswordHandler();
        passwordResource.setPasswordHandler(ph);

        SecurityContext sc = new DummySecurityContext("noprincipal");
        passwordResource.setSecurityContext(sc);

        Response res = passwordResource.updatePassword(null, null, passwordUpdate, false);

        res.getStatus();
    }

    @Test(expected = BadRequestException.class)
    public void testUpdatePasswordEmptyParams() {

        PasswordService passwordResource = new PasswordService();
        passwordResource.setPropertiesMetadata(_propertiesMetadata);
        passwordResource.setAuditLogManager(new DummyAuditLogManager());

        PasswordUpdateParam passwordUpdate = getDummyPasswordUpdate("", "");
        LocalPasswordHandler ph = getPasswordHandler();
        ph.setLocalUsers(createLocalUsers());
        passwordResource.setPasswordHandler(ph);

        SecurityContext sc = new DummySecurityContext(LOCAL_ROOT);
        passwordResource.setSecurityContext(sc);

        Response res = passwordResource.updatePassword(null, null, passwordUpdate, false);
    }

    @Test(expected = BadRequestException.class)
    public void testUpdatePasswordTooManyParams() {

        PasswordService passwordResource = new PasswordService();
        passwordResource.setPropertiesMetadata(_propertiesMetadata);
        passwordResource.setAuditLogManager(new DummyAuditLogManager());

        PasswordUpdateParam passwordUpdate = getDummyPasswordUpdate("clearTextPwd", "HashedPassword");
        LocalPasswordHandler ph = getPasswordHandler();
        ph.setLocalUsers(createLocalUsers());
        passwordResource.setPasswordHandler(ph);

        SecurityContext sc = new DummySecurityContext(LOCAL_ROOT);
        passwordResource.setSecurityContext(sc);

        Response res = passwordResource.updatePassword(null, null, passwordUpdate, false);

    }

    @Test
    public void testUpdateUserPassword() {

        PasswordService passwordResource = new PasswordService();
        passwordResource.setPropertiesMetadata(_propertiesMetadata);
        passwordResource.setAuditLogManager(new DummyAuditLogManager());

        PasswordResetParam passwordUpdate = getDummyPasswordReset(LOCAL_ROOT, "!changeMe3", "");
        LocalPasswordHandler ph = getDummyLocalPasswordHandler();
        ph.setLocalUsers(createLocalUsers());
        passwordResource.setPasswordHandler(ph);

        SecurityContext sc = new DummySecurityContext(LOCAL_ROOT);
        passwordResource.setSecurityContext(sc);

        Response res = passwordResource.updateUserPassword(passwordUpdate, false);

        int statusCode = res.getStatus();
        Assert.assertTrue("updatePassword failed with code " + statusCode +
                ": " + res.getEntity().toString(),
                statusCode == Status.OK.getStatusCode());

        sc = new DummySecurityContext(LOCAL_PROXYUSER);
        passwordResource.setSecurityContext(sc);
        passwordUpdate.setUsername(LOCAL_PROXYUSER);
        res = passwordResource.updateUserPassword(passwordUpdate, false);

        statusCode = res.getStatus();
        Assert.assertTrue("updatePassword failed with code " + statusCode +
                ": " + res.getEntity().toString(),
                statusCode == Status.OK.getStatusCode());
    }

    @Test(expected = BadRequestException.class)
    public void testUpdateUserPasswordNonExistingUser() {

        PasswordService passwordResource = new PasswordService();
        passwordResource.setPropertiesMetadata(_propertiesMetadata);
        passwordResource.setAuditLogManager(new DummyAuditLogManager());

        PasswordResetParam passwordUpdate = getDummyPasswordReset("user123", "!changeme", "");
        LocalPasswordHandler ph = getDummyLocalPasswordHandler();
        ph.setLocalUsers(createLocalUsers());
        passwordResource.setPasswordHandler(ph);

        SecurityContext sc = new DummySecurityContext("root");
        passwordResource.setSecurityContext(sc);

        Response res = passwordResource.updateUserPassword(passwordUpdate, false);

    }

    @Test(expected = ForbiddenException.class)
    public void testUpdateUserPasswordNoSecurtyContext() {

        PasswordService passwordResource = new PasswordService();
        passwordResource.setPropertiesMetadata(_propertiesMetadata);
        passwordResource.setAuditLogManager(new DummyAuditLogManager());
        PasswordResetParam passwordUpdate = getDummyPasswordReset("user123", "!changeme", "");

        Response res = passwordResource.updateUserPassword(passwordUpdate, false);

        Assert.assertTrue("Should throw exception, but returned " + res.getStatus(), false);
    }

    @Test(expected = BadRequestException.class)
    public void testUpdateUserPasswordEmptyParams() {

        PasswordService passwordResource = new PasswordService();
        passwordResource.setPropertiesMetadata(_propertiesMetadata);
        passwordResource.setAuditLogManager(new DummyAuditLogManager());

        PasswordResetParam passwordUpdate = getDummyPasswordReset(LOCAL_ROOT, "", "");
        LocalPasswordHandler ph = getPasswordHandler();
        ph.setLocalUsers(createLocalUsers());
        passwordResource.setPasswordHandler(ph);

        SecurityContext sc = new DummySecurityContext(LOCAL_ROOT);
        passwordResource.setSecurityContext(sc);

        Response res = passwordResource.updateUserPassword(passwordUpdate, false);

    }

    @Test(expected = BadRequestException.class)
    public void testUpdateUserPasswordTooManyParams() {

        PasswordService passwordResource = new PasswordService();
        passwordResource.setPropertiesMetadata(_propertiesMetadata);
        passwordResource.setAuditLogManager(new DummyAuditLogManager());

        PasswordResetParam passwordUpdate = getDummyPasswordReset(LOCAL_ROOT, "clearTextPwd", "HashedPassword");
        LocalPasswordHandler ph = getPasswordHandler();
        ph.setLocalUsers(createLocalUsers());
        passwordResource.setPasswordHandler(ph);

        SecurityContext sc = new DummySecurityContext(LOCAL_ROOT);
        passwordResource.setSecurityContext(sc);

        Response res = passwordResource.updateUserPassword(passwordUpdate, false);
    }

    @Test(expected = ForbiddenException.class)
    public void testUpdateUserPasswordNoPrincipal() {

        PasswordService passwordResource = new PasswordService();
        passwordResource.setPropertiesMetadata(_propertiesMetadata);
        passwordResource.setAuditLogManager(new DummyAuditLogManager());
        PasswordResetParam passwordUpdate = getDummyPasswordReset("user123", "!changeme", "");

        SecurityContext sc = new DummySecurityContext("noprincipal");
        passwordResource.setSecurityContext(sc);

        Response res = passwordResource.updateUserPassword(passwordUpdate, false);

        Assert.assertTrue("Should throw exception, but returned " + res.getStatus(), false);
    }

    private class DummyLocalPasswordHandler extends LocalPasswordHandler {

        @Override
        public void setUserPassword(String username, String clearTextPassword, boolean bReset) {

        }

        @Override
        public void setUserEncryptedPassword(String username, String clearTextPassword, boolean bReset) {

        }

        @Override
        public String getUserPassword(String userName) {
            // return a string with prefix '$6$'
            return "$6$eBIu9aQ6$wBnn9Tn.CUuuoi/JZe.oAOmUDIVCqHpXeem7ZHO5R7dPg2hul8tNCBzwumKrFw8A0qm.LH8YvMJUaN2AL1JVc0";
        }
    }

    private class DummySecurityContext implements SecurityContext {

        private DummyPrincipal _principal;

        public DummySecurityContext(String username) {
            if (!username.equalsIgnoreCase("noprincipal")) {
                _principal = new DummyPrincipal(username);
            }
        }

        @Override
        public String getAuthenticationScheme() {
            return null;
        }

        @Override
        public Principal getUserPrincipal() {
            return _principal;
        }

        @Override
        public boolean isSecure() {
            return false;
        }

        @Override
        public boolean isUserInRole(String arg0) {
            return false;
        }

    }

    private class DummyPrincipal extends StorageOSUser {

        public DummyPrincipal(String name) {
            super(name, "");
            super.setIsLocal(true);
        }

    }

    private Map<String, StorageOSUser> createLocalUsers() {

        Map<String, StorageOSUser> locals = new HashMap<String, StorageOSUser>();
        locals.put(LOCAL_ROOT,
                new StorageOSUser(
                        LOCAL_ROOT,
                        ""));
        locals.put(LOCAL_PROXYUSER,
                new StorageOSUser(
                        LOCAL_PROXYUSER,
                        ""));

        return locals;
    }

    private class DummyConfigService extends ConfigService {
        @Override
        public Response setProperties(PropertyInfoUpdate setProperty) throws LocalRepositoryException, CoordinatorClientException,
                URISyntaxException {
            _passwordProps.addProperties(setProperty.getAllProperties());
            return Response.ok().build();
        }
    }

    private class DummyAuditLogManager extends AuditLogManager {

        public void setDbClient(DbClient dbClient) {

        }

        public void recordAuditLogs(RecordableAuditLog... auditlogs) {

        }

        public void recordAuditLog(URI tenantId,
                URI userId,
                String serviceType,
                OperationTypeEnum auditType,
                long timestamp,
                String operationalStatus,
                String operationStage,
                Object... descparams) {

        }
    }

    private CoordinatorClient getCoordinatorClient() throws Exception {
        DummyCoordinatorClient client = new DummyCoordinatorClient();

        ZkConnection zkConn = new ZkConnection();
        List<URI> uris = new ArrayList();
        uris.add(new URI("coordinator://localhost:2181"));
        zkConn.setServer(uris);
        zkConn.setTimeoutMs(10000);
        zkConn.build();

        client.setZkConnection(zkConn);

        return client;
    }

    private class DummyCoordinatorClient extends CoordinatorClientImpl {

        public DummyCoordinatorClient() {
            Properties props = generateProperties();
            setDefaultProperties(props);

            Properties ovfProps = new Properties();
            setOvfProperties(ovfProps);
        }

        public PropertyInfoExt getTargetInfo(final Class clazz) throws CoordinatorException {
            return _passwordProps;
        }

        @Override
        public PropertyInfo getPropertyInfo() {
            PropertyInfo info = new PropertyInfo();
            info.setProperties(propertiesMap);

            return info;
        }
    }
}
