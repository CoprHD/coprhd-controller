/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.util;

import java.security.SecureRandom;
import java.util.Calendar;
import java.util.Map;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.model.property.PropertyInfoRestRep;
import com.emc.storageos.security.password.Constants;
import com.emc.storageos.security.password.PasswordUtils;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

import org.apache.commons.codec.digest.Crypt;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.systemservices.exceptions.CoordinatorClientException;
import com.emc.storageos.systemservices.exceptions.LocalRepositoryException;
import com.emc.storageos.systemservices.impl.resource.ConfigService;
import com.emc.storageos.model.property.PropertyInfoUpdate;
import com.emc.storageos.model.property.PropertyInfo.PropCategory;
import com.emc.storageos.db.client.model.EncryptionProvider;

public class LocalPasswordHandler {

    private static final Logger _log = LoggerFactory.getLogger(LocalPasswordHandler.class);

    private static final String SYSTEM_ENCPASSWORD_FORMAT = "system_%s_encpassword"; // NOSONAR
                                                                                     // ("squid:S2068 Suppressing sonar violation of hard-coded password")
    private static final String SYSTEM_AUTHORIZEDKEY2_FORMAT = "system_%s_authorizedkeys2";

    private static final String CRYPT_SHA_512 = "$6$";

    private Map<String, StorageOSUser> _localUsers;

    private ConfigService _cfg;

    private EncryptionProvider _encryptionProvider;

    public void setEncryptionProvider(EncryptionProvider encryptionProvider) {
        _encryptionProvider = encryptionProvider;
    }

    public void setConfigService(ConfigService cfg) {
        _cfg = cfg;
    }

    public void setLocalUsers(Map<String, StorageOSUser> localUsers) {
        _localUsers = localUsers;
    }

    private DbClient _dbClient;

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    private PasswordUtils _passwordUtils;

    public void setPasswordUtils(PasswordUtils passwordUtils) {
        _passwordUtils = passwordUtils;
    }

    public PasswordUtils getPasswordUtils() {
        return _passwordUtils;
    }

    /**
     * Check whether the local user name exists in configuration.
     * 
     * @param username the local user name
     * @return true if user exists, else false
     */
    public boolean checkUserExists(String username) {

        boolean ret = false;

        if (_localUsers.get(username) != null) {
            _log.debug("user {} exists locally, roles={}", username,
                    _localUsers.get(username).getRoles());
            ret = true;
        } else {
            _log.debug("user {} does not exist locally", username);
        }

        return ret;
    }

    /**
     * Get the local user's hashed password
     * 
     * @param username the local user name
     * @throws CoordinatorClientException
     * @throws LocalRepositoryException
     */
    public String getUserPassword(String username)
            throws CoordinatorClientException, LocalRepositoryException {
        return getPassword(username);
    }

    private String getPassword(String username)
            throws CoordinatorClientException, LocalRepositoryException {

        PropertyInfoRestRep props = null;
        try {
            props = _cfg.getProperties(PropCategory.CONFIG.toString());
        } catch (Exception e) {
            throw APIException.internalServerErrors.getObjectFromError("password", "coordinator", e);
        }

        String propertyKey = String.format(SYSTEM_ENCPASSWORD_FORMAT, username);

        String oldPassword = props.getProperty(propertyKey);
        if (oldPassword == null) {
            _log.error("password not found for " + username);
            return "";
        }

        return oldPassword;
    }

    /**
     * Set the local user's password
     * 
     * @param username the local user name
     * @param clearTextPassword the local user's password in clear text
     * @throws CoordinatorClientException
     * @throws LocalRepositoryException
     */
    public void setUserPassword(String username, String clearTextPassword, boolean bReset)
            throws CoordinatorClientException, LocalRepositoryException {
        if (clearTextPassword == null || clearTextPassword.isEmpty()) {
            throw APIException.badRequests.parameterIsNullOrEmpty("Password");
        }

        String salt = generateSalt();
        String hashed = Crypt.crypt(clearTextPassword, CRYPT_SHA_512 + salt);

        _log.debug("New hashedSaltedPassword: {}", hashed);

        setUserHashedPassword(username, hashed, bReset);
    }

    /**
     * Calls EncryptionProvider to encrypt a string and returns a Base64 encoded string representing the encrypted data.
     * 
     * @param input the string to encrypt and get Base64 . encoded string
     * @return the encrypted (and Base64 encoded) string.
     */
    public String getEncryptedString(String input) {
        return _encryptionProvider.getEncryptedString(input);
    }

    /**
     * Set the local user's encrypted password
     * 
     * @param username the local user name
     * @param password the local user's password already encrypted by caller
     * @throws CoordinatorClientException
     * @throws LocalRepositoryException
     */
    public void setUserEncryptedPassword(String username, String password, boolean bReset)
            throws CoordinatorClientException, LocalRepositoryException {
        if (password == null || password.isEmpty()) {
            throw APIException.badRequests.parameterIsNullOrEmpty("password");
        }
        updateUserPasswordProperty(username, password, bReset);
    }

    /**
     * Set the local user's hashed password
     * 
     * @param username the local user name
     * @param hashedPassword the local user's password already hashed by caller
     * @throws CoordinatorClientException
     * @throws LocalRepositoryException
     */
    public void setUserHashedPassword(String username, String hashedPassword, boolean bReset)
            throws CoordinatorClientException, LocalRepositoryException {
        if (hashedPassword == null || hashedPassword.isEmpty()) {
            throw APIException.badRequests.parameterIsNullOrEmpty("Password");
        }
        updateUserPasswordProperty(username, hashedPassword, bReset);
    }

    /**
     * Set the local user's authorizedkey2
     * 
     * @param username the local user name
     * @param authorizedkey2 the local user's SSH authorizedkey2
     * @throws CoordinatorClientException
     * @throws LocalRepositoryException
     */
    public void setUserAuthorizedkey2(String username, String authorizedkey2)
            throws CoordinatorClientException, LocalRepositoryException {

        updateProperty(String.format(SYSTEM_AUTHORIZEDKEY2_FORMAT, username),
                authorizedkey2);
    }

    public void updateProperty(String key, String value)
            throws CoordinatorClientException, LocalRepositoryException {

        PropertyInfoUpdate props = new PropertyInfoUpdate();

        props.addProperty(key, value);

        _log.info("Calling ConfigService to update property: ", key);

        try {
            _cfg.setProperties(props);
        } catch (Exception e) {
            throw APIException.internalServerErrors.updateObjectError("properties", e);
        }
    }

    /**
     * when updating localuser's encpassword property, this method should be call instead of
     * updateProperty method.
     * 
     * it will update encpassword property, it also update user's expiry_date property and
     * user's password history.
     * 
     * expiry_date system properties is for generate /etc/shadow file to block ssh login after
     * user's password expired.
     * 
     * @param username
     * @param value
     * @throws CoordinatorClientException
     * @throws LocalRepositoryException
     */
    private void updateUserPasswordProperty(String username, String value, boolean bReset)
            throws CoordinatorClientException, LocalRepositoryException {

        String encpasswordProperty = String.format(SYSTEM_ENCPASSWORD_FORMAT, username);
        PropertyInfoUpdate props = new PropertyInfoUpdate();
        props.addProperty(encpasswordProperty, value);

        Calendar newExpireTime = getExpireTimeFromNow();
        if (username.equals("root") || username.equals("svcuser")) {
            // add expiry_date system property
            String configExpireDays = getPasswordUtils().getConfigProperty(Constants.PASSWORD_EXPIRE_DAYS);
            int intConfigExpireDays = NumberUtils.toInt(configExpireDays);

            int daysAfterEpoch = 0;
            if (intConfigExpireDays != 0) {
                daysAfterEpoch = PasswordUtils.getDaysAfterEpoch(newExpireTime);
            }

            String expirydaysProperty = String.format(Constants.SYSTEM_PASSWORD_EXPIRY_FORMAT, username);
            _log.info("updating " + expirydaysProperty + " to " + daysAfterEpoch);
            props.addProperty(expirydaysProperty, String.valueOf(daysAfterEpoch));
        }

        try {
            _cfg.setProperties(props);
            if (username.equals("proxyuser")) {
                value = _passwordUtils.getEncryptedString(value);
            }
            _passwordUtils.updatePasswordHistory(username, value, newExpireTime, bReset);
        } catch (Exception e) {
            throw APIException.internalServerErrors.updateObjectError("properties", e);
        }
    }

    private Calendar getExpireTimeFromNow() {
        String configExpireDays = _passwordUtils.getConfigProperty(Constants.PASSWORD_EXPIRE_DAYS);
        int intConfigExpireDays = NumberUtils.toInt(configExpireDays);

        Calendar expireTime = Calendar.getInstance();
        expireTime.add(Calendar.DATE, intConfigExpireDays);
        return expireTime;
    }

    /**
     * Generate a random salt
     */
    static private String generateSalt() {

        // number of Base64 characters for salt is dependent on the number of salt bytes
        final int SALT_LENGTH = 16;

        // valid chars as part of salt acceptable by commons-codec
        final String SALT_BASE_CHARS =
                "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ./";
        // create the salt of random bytes
        SecureRandom random = new SecureRandom();

        StringBuilder salt = new StringBuilder(SALT_LENGTH);
        for (int i = 0; i < SALT_LENGTH; i++) {
            salt.append(SALT_BASE_CHARS.charAt(
                    random.nextInt(SALT_BASE_CHARS.length())));
        }
        return salt.toString();
    }

}
