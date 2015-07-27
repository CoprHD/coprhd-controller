/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.system;

import static com.emc.vipr.client.system.impl.PathConstants.PASSWORD_URL;
import static com.emc.vipr.client.system.impl.PathConstants.UPDATE_AUTH_KEY_URL;
import static com.emc.vipr.client.system.impl.PathConstants.UPDATE_PASSWORD_URL;
import static com.emc.vipr.client.system.impl.PathConstants.VALIDATE_PASSWORD_URL;
import static com.emc.vipr.client.system.impl.PathConstants.VALIDATE_PASSWORD_UPDATE_URL;

import com.emc.storageos.model.password.PasswordResetParam;
import com.emc.storageos.model.password.PasswordUpdateParam;
import com.emc.storageos.model.password.PasswordValidateParam;
import com.emc.storageos.model.password.SSHKeyUpdateParam;
import com.emc.vipr.client.impl.RestClient;

public class Password {
	private RestClient client;
	
	public Password(RestClient client) {
        this.client = client;
    }
	
	/**
     * Change an authenticated local user's own password. Accepts a clear test
     * password or a password already hashed by the caller. If both form fields
     * are specified, bad request will be returned.  User is automatically logged
     * out after password reset.
     * <p>
     * API Call: PUT /password
	 * 
	 * @param password The clear text or encrypted password
	 * @param encrypted If true, the supplied password is already hashed
     * @param oldPassword the previous password in clear text
	 */
	public void update(String oldPassword, String password, boolean encrypted) {
        update(oldPassword, password, encrypted, true);
	}

    /**
     * Change an authenticated local user's own password. Accepts a clear test
     * password or a password already hashed by the caller. If both form fields
     * are specified, bad request will be returned.
     * <p>
     * API Call: PUT /password
     *
     * @param password The clear text or encrypted password
     * @param encrypted If true, the supplied password is already hashed
     * @param logoutUser If true, logout the user after updating the password
     */
    public void update(String oldPassword, String password, boolean encrypted, boolean logoutUser) {
        PasswordUpdateParam param = new PasswordUpdateParam();
        param.setOldPassword(oldPassword);
        if (encrypted) {
            param.setEncPassword(password);
        }
        else {
            param.setPassword(password);
        }
        client.putURI(String.class, param, client.uriBuilder(PASSWORD_URL).queryParam("logout_user", logoutUser).build());
    }

    /**
     * Change a given local user's password. The authenticated caller must have
     * SEC_ADMIN role. User is automatically logged out after password reset.
     * <p>
     * API Call: PUT /password/reset
     *
     * @param username The local user name
     * @param password Clear text or encrypted password
     * @param encrypted If true, the provided password is encrypted
     */
    public void reset(String username, String password, boolean encrypted) {
        reset(username, password, encrypted, true);
    }

    /**
     * Change a given local user's password. The authenticated caller must have
     * SEC_ADMIN role.
     * <p>
     * API Call: PUT /password/reset
     *
     * @param username The local user name
     * @param password Clear text or encrypted password
     * @param encrypted If true, the provided password is encrypted
     * @param logoutUser If true, logout the user after updating the password
     */
    public void reset(String username, String password, boolean encrypted, boolean logoutUser) {
        PasswordResetParam param = new PasswordResetParam();
        param.setUsername(username);
        if (encrypted) {
            param.setEncPassword(password);
        }
        else {
            param.setPassword(password);
        }
        client.putURI(String.class, param, client.uriBuilder(UPDATE_PASSWORD_URL).queryParam("logout_user", logoutUser).build());
    }

    /**
     * Change an authenticated local user's SSH authorizedkey2. This interface accepts
     * the user's SSH authorizedkey2
     * <p>
     * API Call: PUT /password/authorizedkey2
	 *
	 * @param sshKey the SSH key
	 * @throws Exception
	 */
	public void updateAuthorizedKey2(String sshKey) throws Exception {
        SSHKeyUpdateParam key = new SSHKeyUpdateParam();
        key.setSshKey(sshKey);
        client.put(String.class, key, UPDATE_AUTH_KEY_URL);
	}
	
    /**
     * Validate a password.  If validation passes, it will return an http 204 status code (no content).  
     * If validation fails, it will throw a ServiceErrorException with an http 400 status code (bad parameters).  
     * 
     * <p>
     * API Call: POST /password/validate
	 *
	 * @param password plaintext password to validate
	 * @throws Exception
	 */
	public void validate(String password) throws Exception {
        PasswordValidateParam input = new PasswordValidateParam();
        input.setPassword(password);
        client.post(input, VALIDATE_PASSWORD_URL);
	}

    /**
     * an authenticated local user validates its proposed password change.
     *   If validation passes, it will return an http 204 status code (no content).
     *   If validation fails, it will throw a ServiceErrorException with an http 400 status code (bad parameters).
     *
     * <p>
     * API Call: POST /password/validate-update
     */
    public void validateUpdate(String oldPassword, String password) throws Exception {
        PasswordUpdateParam input = new PasswordUpdateParam();
        input.setOldPassword(oldPassword);
        input.setPassword(password);
        client.post(input, VALIDATE_PASSWORD_UPDATE_URL);
    }
}
