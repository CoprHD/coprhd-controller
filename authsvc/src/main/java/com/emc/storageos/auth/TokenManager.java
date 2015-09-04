/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 *  software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of 
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.auth;

import java.util.List;

import com.emc.storageos.db.client.model.StorageOSUserDAO;

/**
 * Interface for token management (creation and deletion)
 */

public interface TokenManager {
    /**
     * Get token life time in secs
     * 
     * @return
     */
    public int getMaxTokenLifeTimeInSecs();

    /**
     * 
     * Create a new token for the passed in user object
     *
     * @param user
     * @return
     */
    public String getToken(StorageOSUserDAO user);

    /**
     * 
     * Create a new proxy token for the passed in user object
     *
     * @param user
     * @return
     */
    public String getProxyToken(StorageOSUserDAO user);

    /**
     * 
     * Delete the passed in token
     *
     * @param token
     */
    public void deleteToken(String token);

    /**
     * Find all tokens corresponding to the passed in username
     * and delete them.
     * 
     * @param userName
     * @param includeProxyTokens
     */
    public void deleteAllTokensForUser(String userName, boolean includeProxyTokens);

    /**
     * Gets all records for the specified user
     * 
     * @param userName
     */
    public List<StorageOSUserDAO> getUserRecords(final String userName);

    /**
     * Updates the list of user dao records with the specified user dao object.
     * 
     * @param userDAO the DAO to update from
     * @param userRecords the records to update
     * @return the last StorageOSUserDAO that was updated
     */
    public StorageOSUserDAO updateDBWithUser(final StorageOSUserDAO userDAO, final List<StorageOSUserDAO> userRecords);

}
