/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.auth.local;

import com.emc.storageos.auth.LdapFailureHandler;
import com.emc.storageos.auth.StorageOSAuthenticationHandler;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.model.property.PropertyInfo;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.security.authentication.StorageOSUser;

import org.apache.commons.codec.digest.Crypt;
import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import com.emc.storageos.db.client.model.EncryptionProvider;

/**
 * Class to handle authentication using basic username password credentials
 * stored in the database.
 */
public class StorageOSLocalAuthenticationHandler implements StorageOSAuthenticationHandler {
    private static final Logger _log = LoggerFactory.getLogger(StorageOSLocalAuthenticationHandler.class);

    // A reference to the coordinator client for retrieving/updating local user
    private CoordinatorClient _coordinatorClient;

    private Map<String, StorageOSUser> _localUsers;

    private EncryptionProvider _encryptionProvider;

    private static final String CRYPT_SHA_512 = "$6$";

    public void setEncryptionProvider(EncryptionProvider encryptionProvider) {
        _encryptionProvider = encryptionProvider;
    }

    public void setLocalUsers(final Map<String, StorageOSUser> localUsers) {
        _localUsers = localUsers;
    }

    public void setCoordinatorClient(final CoordinatorClient coordinatorClient) {
        _coordinatorClient = coordinatorClient;
    }

    public StorageOSLocalAuthenticationHandler() {
    }

    /**
     * Verify that the user's password matches with the hashed and salted value stored.
     * Return false if user is not found.
     * 
     * @param username the user
     * @param clearTextPassword the clear text password
     * @return true if user's password matches, otherwise false.
     */
    public boolean verifyUserPassword(final String username, final String clearTextPassword) {

        if (clearTextPassword == null || clearTextPassword.isEmpty()) {
            _log.error("Login with blank password is not allowed");
            return false;
        }

        String encpassword = null;
        PropertyInfo props = null;
        try {
            props = _coordinatorClient.getPropertyInfo();
        } catch (CoordinatorException e) {
            _log.error("Access local user properties failed", e);
            return false;
        }

        if (props == null) {
            _log.error("Access local user properties failed");
            return false;
        }
        encpassword = props.getProperty(
                "system_" + username + "_encpassword");
        if (StringUtils.isBlank(encpassword)) {
            _log.error("No password set for user {} ", username);
            return false;
        }

        // A hashed value will start with the SHA-512 identifier ($6$)
        if (StringUtils.startsWith(encpassword, CRYPT_SHA_512)) {
            // Hash the clear text password and compare against the stored value
            String hashedValue = Crypt.crypt(clearTextPassword, encpassword);
            return encpassword.equals(hashedValue);
        } else {
            // Encrypt the clear text password and compare against the stored value
            String encryptedValue = encrypt(clearTextPassword);
            return encpassword.equals(encryptedValue);
        }
    }

    /**
     * Calls EncryptionProvider to encrypt a string and returns a Base64 encoded string representing the encrypted data.
     * 
     * @param s the string to encrypt.
     * @return the encrypted (and Base64 encoded) string.
     */
    private String encrypt(String s) {
        return _encryptionProvider.getEncryptedString(s);
    }

    @Override
    public boolean authenticate(final Credentials credentials) {
        UsernamePasswordCredentials creds = (UsernamePasswordCredentials) credentials;
        return verifyUserPassword(creds.getUserName(), creds.getPassword());
    }

    public boolean exists(String username) {
        return _localUsers.containsKey(username);
    }

    @Override
    public boolean supports(final Credentials credentials) {
        return credentials != null
                && (UsernamePasswordCredentials.class.isAssignableFrom(credentials.getClass()))
                && exists(((UsernamePasswordCredentials) (credentials)).getUserName());
    }

    @Override
    public void setFailureHandler(LdapFailureHandler failureHandler) {
        return;
    }
}
