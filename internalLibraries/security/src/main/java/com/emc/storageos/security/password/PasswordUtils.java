/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.security.password;

import com.emc.storageos.coordinator.client.model.PropertyInfoExt;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.impl.EncryptionProviderImpl;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.EncryptionProvider;
import com.emc.storageos.db.client.model.PasswordHistory;
import com.emc.storageos.model.password.PasswordChangeParam;
import com.emc.storageos.model.password.PasswordResetParam;
import com.emc.storageos.model.password.PasswordUpdateParam;
import com.emc.storageos.model.password.PasswordValidateParam;
import com.emc.storageos.model.property.PropertyInfo;
import com.emc.storageos.security.authentication.AuthSvcInternalApiClientIterator;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authentication.SysSvcEndPointLocator;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;
import com.sun.jersey.api.client.ClientResponse;

import org.apache.commons.codec.digest.Crypt;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;

import java.net.URI;
import java.text.MessageFormat;
import java.util.*;

public class PasswordUtils {

    private static final Logger _log = LoggerFactory.getLogger(PasswordUtils.class);

    private EncryptionProvider encryptionProvider;

    private DbClient dbClient;

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    private static CoordinatorClient coordinator;

    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
        EncryptionProviderImpl encryptionProvider1 = new EncryptionProviderImpl();
        encryptionProvider1.setCoordinator(coordinator);
        encryptionProvider1.start();
        this.encryptionProvider = encryptionProvider1;

    }

    public void setEncryptionProvider(EncryptionProvider encryptionProvider) {
        this.encryptionProvider = encryptionProvider;
    }

    private static Properties defaultProperties;

    public synchronized static void setDefaultProperties(Properties defaults) {
        defaultProperties = defaults;
    }

    private Map<String, StorageOSUser> _localUsers;

    public void setLocalUsers(Map<String, StorageOSUser> localUsers) {
        _localUsers = localUsers;
    }

    private static final URI URI_CHANGE_PASSWORD = URI.create("/password/internal/change-password");
    private static final URI URI_VALIDATE_PASSWORD = URI.create("/password/internal/validate-change");
    private static final int MAX_CONFIG_RETRIES = 5;

    /**
     * generate URI for storing user password history in Cassandra.
     * 
     * @param username
     * @return
     */
    public static URI getLocalPasswordHistoryURI(String username) {
        String vdcId = URIUtil.getLocation(PasswordHistory.class);
        URI pwdUri = URI.create(String.format("urn:storageos:%1$s:%2$s:%3$s", PasswordHistory.class.getSimpleName(), username, vdcId));
        return pwdUri;
    }

    /**
     * retrieve user's password history from Cassandra
     * 
     * @param username
     * @return
     */
    public PasswordHistory getPasswordHistory(String username) {
        PasswordHistory lph = dbClient.queryObject(PasswordHistory.class, getLocalPasswordHistoryURI(username));
        return lph;
    }

    /**
     * set all local users' password expire date to the same date, if data is null, remove expire time for all users
     * 
     * this method was used for turn on/off password expire rule
     * 
     * @param date
     */
    public void setExpireDateToAll(Calendar date) {
        for (String user : _localUsers.keySet()) {
            setExpireTimeOfUser(user, date);
        }
    }

    /**
     * update user's expireTime in Cassandra
     * 
     * @param user
     * @param expireTime
     */
    private void setExpireTimeOfUser(String user, Calendar expireTime) {
        Password password = constructUserPassword(user);
        PasswordHistory ph = password.getPasswordHistory();
        ph.setExpireDate(expireTime);
        dbClient.updateAndReindexObject(ph);
        _log.info("set new expire time for user " + user + ": "
                + (expireTime == null ? "null" : expireTime.getTime()));
    }

    /*
     * design goals:
     * 1. every password deserves a whole set of notification procedure before it expires.
     * 2. after the first mail sent, user start aware of their password is about to expire. so after
     * this point, the expire time shouldn't be change any more, even the expire rule changes.
     * 
     * when change expire rule, shorten or extend expire days:
     * 1. For users, whose old password expire time is before grace_point (the point "now + GRACE_DAYS"),
     * will be no change. this is because user has already got their first notification mail. it is
     * important to keep time in notification mails consistent and accurate, this could also
     * avoid password immediate expiration issue.
     * 
     * 2. for old expire time is after grace day, re-calculate new expire time as:
     * new_expire_time = last_change_time + expire_days
     * what the real expire time is, depends on the following:
     * 2.1: if new expire time before grace point, set it to grace point, to fulfill goal 1.
     * 2.2: if new expire time after grace point, set it as its real expire time.
     */
    public void adjustExpireTime(int newDays) {
        for (String user : _localUsers.keySet()) {
            Calendar newExpireDate = calculateExpireDateForUser(user, newDays);
            setExpireTimeOfUser(user, newExpireDate);
        }
    }

    public Calendar calculateExpireDateForUser(String user, int newDays) {
        Calendar gracePoint = Calendar.getInstance();
        gracePoint.add(Calendar.DATE, Constants.GRACE_DAYS);

        Password password = constructUserPassword(user);
        Calendar oldExpireTime = password.getPasswordHistory().getExpireDate();

        if (oldExpireTime != null && oldExpireTime.before(gracePoint)) {
            return oldExpireTime;
        }

        long lastChangeTime = password.getLatestChangedTime();
        long longNewExpireTime = lastChangeTime + dayToMilliSeconds(newDays);
        Calendar newExpireTime = Calendar.getInstance();
        newExpireTime.setTimeInMillis(longNewExpireTime);

        if (newExpireTime.after(gracePoint)) {
            return newExpireTime;
        } else {
            return gracePoint;
        }

    }

    /**
     * check if two passwords match, one parameter is in clear text, the other is encoded.
     * 
     * @param clearTextPassword
     * @param encpassword
     * @return
     */
    public boolean match(String clearTextPassword, String encpassword) {

        // A hashed value will start with the SHA-512 identifier ($6$)
        if (StringUtils.startsWith(encpassword, Constants.CRYPT_SHA_512)) {
            String hashedValue = Crypt.crypt(clearTextPassword, encpassword);
            return encpassword.equals(hashedValue);
        } else {
            String encryptedValue = encryptionProvider.getEncryptedString(clearTextPassword);
            return encpassword.equals(encryptedValue);
        }
    }

    /**
     * get encrypted string
     */
    public String getEncryptedString(String clearText) {
        return encryptionProvider.getEncryptedString(clearText);
    }

    /**
     * get current system properties
     * 
     * @return
     */
    public Map<String, String> getConfigProperties() {
        Map<String, String> mergedProps = new HashMap();
        Set<Map.Entry<Object, Object>> defaults = defaultProperties.entrySet();
        for (Map.Entry<Object, Object> p : defaults) {
            mergedProps.put((String) p.getKey(), (String) p.getValue());
        }

        Map<String, String> overrides = new HashMap();

        try {
            overrides = coordinator.getTargetInfo(PropertyInfoExt.class).getProperties();
        } catch (Exception e) {
            _log.info("Fail to get the cluster information ", e);
        }

        for (Map.Entry<String, String> entry : overrides.entrySet()) {
            mergedProps.put(entry.getKey(), entry.getValue());
        }

        return mergedProps;
    }

    /**
     * get a property from System Properties
     * 
     * @param key
     * @return
     */
    public String getConfigProperty(String key) {
        Map<String, String> configProperties = getConfigProperties();
        return configProperties.get(key);
    }

    /**
     * validate PasswordUpdateParam
     * 
     * @param username
     * @param passwordUpdate
     */
    public void validatePasswordParameter(String username, PasswordUpdateParam passwordUpdate) {
        validatePasswordParameter(username,
                passwordUpdate.getOldPassword(),
                passwordUpdate.getPassword(),
                passwordUpdate.getEncPassword(),
                ValidatorType.UPDATE);
    }

    public void validatePasswordParameter(PasswordResetParam passwordReset) {
        validatePasswordParameter(passwordReset.getUsername(),
                null,
                passwordReset.getPassword(),
                passwordReset.getEncPassword(),
                ValidatorType.RESET);

    }

    public void validatePasswordParameter(PasswordValidateParam passwordValidate) {
        validatePasswordParameter(null,
                null,
                passwordValidate.getPassword(),
                null,
                ValidatorType.VALIDATE_CONTENT);

    }

    public void validatePasswordParameter(PasswordChangeParam passwordChange) {
        validatePasswordParameter(passwordChange.getUsername(),
                passwordChange.getOldPassword(),
                passwordChange.getPassword(),
                null,
                ValidatorType.CHANGE);
    }

    /**
     * validate password APIs input parameters.
     * 
     * @param username
     * @param oldPassword
     * @param password
     * @param encpassword
     * @param type
     */
    private void validatePasswordParameter(String username,
            String oldPassword,
            String password,
            String encpassword,
            ValidatorType type) {

        // one of the parameters must present, but not both
        boolean isPresent = (password != null && !password.isEmpty()) ^
                (encpassword != null && !encpassword.isEmpty());
        if (!isPresent) {
            throw APIException.badRequests.parameterIsNullOrEmpty("password, encpassword");
        }

        // if oldPassword presents, verify it
        if (oldPassword != null && !oldPassword.isEmpty()) {
            if (!match(oldPassword, getUserPassword(username))) {
                throw BadRequestException.badRequests.passwordInvalidOldPassword();
            }
        }

        if (password != null && !password.isEmpty()) {
            PasswordValidator validator = null;
            switch (type) {
                case CHANGE:
                    validator = ValidatorFactory.buildChangeValidator(getConfigProperties(), this);
                    break;
                case RESET:
                    validator = ValidatorFactory.buildResetValidator(getConfigProperties());
                    break;
                case UPDATE:
                    validator = ValidatorFactory.buildUpdateValidator(getConfigProperties(), this);
                    break;
                case VALIDATE_CONTENT:
                    validator = ValidatorFactory.buildContentValidator(getConfigProperties());
                    break;
            }

            Password pw = new Password(username, oldPassword, password);
            if (StringUtils.isNotBlank(username)) {
                pw.setPasswordHistory(getPasswordHistory(username));
            }
            validator.validate(pw);
        }
    }

    /**
     * a wrapper to call change-password internal API or validate-change internal API in PasswordService
     * 
     * bDryRun: if true, call validate-change internal API
     * if false, call change-password internal API
     * 
     * @param passwordChange
     * @param bDryRun
     * @return
     */
    public Response changePassword(PasswordChangeParam passwordChange, boolean bDryRun) {
        SysSvcEndPointLocator sysSvcEndPointLocator = new SysSvcEndPointLocator();
        sysSvcEndPointLocator.setCoordinator(coordinator);

        int attempts = 0;
        ClientResponse response = null;
        while (attempts < MAX_CONFIG_RETRIES) {
            _log.debug("change password attempt {}", ++attempts);
            AuthSvcInternalApiClientIterator sysSvcClientItr =
                    new AuthSvcInternalApiClientIterator(sysSvcEndPointLocator,
                            coordinator);
            try {
                if (sysSvcClientItr.hasNext()) {
                    if (bDryRun) {
                        _log.debug("change password dry run");
                        response = sysSvcClientItr.post(URI_VALIDATE_PASSWORD, passwordChange);
                    } else {
                        response = sysSvcClientItr.put(URI_CHANGE_PASSWORD, passwordChange);
                    }
                    _log.debug("change password response with status: " + response.getStatus());
                    break;
                }
            } catch (Exception exception) {
                // log the exception and retry the request
                _log.warn(exception.getMessage());
                if (attempts == MAX_CONFIG_RETRIES - 1) {
                    throw exception;
                }
            }
        }

        Response.ResponseBuilder b = Response.status(response.getStatus());
        if (!(response.getStatus() == ClientResponse.Status.NO_CONTENT.getStatusCode())) {
            b.entity(response.getEntity(String.class));
        }
        return b.build();
    }

    /**
     * get user's encpassword from system properties
     * 
     * @param username
     * @return
     */
    public String getUserPassword(String username) {
        PropertyInfo props = null;
        try {
            props = coordinator.getPropertyInfo();
        } catch (CoordinatorException e) {
            _log.error("Access local user properties failed", e);
            return null;
        }

        if (props == null) {
            _log.error("Access local user properties failed");
            return null;
        }
        String encpassword = props.getProperty(
                "system_" + username + "_encpassword");

        if (StringUtils.isBlank(encpassword)) {
            _log.error("No password set for user {} ", username);
            return null;
        }

        return encpassword;
    }

    /**
     * construct a Password object which only contains its password history information
     * 
     * @param username
     * @return
     */
    public Password constructUserPassword(String username) {
        Password password = new Password(username, null, null);
        PasswordHistory ph = getPasswordHistory(username);
        password.setPasswordHistory(ph);
        return password;
    }

    public static long dayToMilliSeconds(long days) {
        return days * 24 * 60 * 60 * 1000;
    }

    private enum ValidatorType {
        UPDATE,
        RESET,
        CHANGE,
        VALIDATE_CONTENT
    }

    /**
     * update user's password expire date.
     * 
     * if it is not reset by securityAdmin, also add the password in user's password history
     * 
     * @param username
     * @param hashedPassword
     */
    public void updatePasswordHistory(String username, String hashedPassword, Calendar expireTime, boolean bReset) {
        PasswordHistory lph = getPasswordHistory(username);
        boolean isNew = false;
        if (lph == null) {
            isNew = true;
            lph = new PasswordHistory();
            lph.setId(getLocalPasswordHistoryURI(username));
        }

        Calendar now = Calendar.getInstance();
        if (!bReset) {
            lph.getUserPasswordHash().put(hashedPassword, now.getTimeInMillis());
        }
        lph.setExpireDate(expireTime);

        if (isNew) {
            dbClient.createObject(lph);
        } else {
            dbClient.updateAndReindexObject(lph);
        }
    }

    /**
     * get the days after Epoch: Jan 01, 1970
     * 
     * @param date
     * @return
     */
    public static int getDaysAfterEpoch(Calendar date) {
        if (date == null) {
            return 0;
        }

        Long diff = (date.getTimeInMillis() / (24 * 60 * 60 * 1000));
        return diff.intValue();
    }

    /**
     * prompt string list for password rules which turned on.
     * 
     * this is used to provide help infomation for UI changePassword.html.
     */
    public List<String> getPasswordChangePromptRules() {
        List<String> promptRules = new ArrayList<String>();
        Map<String, String> properties = getConfigProperties();
        for (int i = 0; i < Constants.PASSWORD_CHANGE_PROMPT.length; i++) {
            String key = Constants.PASSWORD_CHANGE_PROMPT[i][0];
            String value = properties.get(key);
            if (NumberUtils.toInt(value) != 0) {
                promptRules.add(MessageFormat.format(Constants.PASSWORD_CHANGE_PROMPT[i][1], value));
            }
        }

        return promptRules;
    }

    /**
     * check if a user is a local user.
     * 
     * @param username
     * @return
     */
    public boolean isLocalUser(String username) {
        return _localUsers.keySet().contains(username);
    }

}
