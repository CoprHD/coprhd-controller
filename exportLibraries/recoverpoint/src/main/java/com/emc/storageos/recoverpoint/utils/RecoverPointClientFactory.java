/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 **/

package com.emc.storageos.recoverpoint.utils;

import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.fapiclient.ws.FunctionalAPIImpl;
import com.emc.storageos.recoverpoint.exceptions.RecoverPointException;
import com.emc.storageos.recoverpoint.impl.RecoverPointClient;

/**
 * RecoverPoint API client factory
 */
public class RecoverPointClientFactory {

    private static Logger logger = LoggerFactory.getLogger(RecoverPointClientFactory.class);

    // Stores valid Recover Point connections via a hash map of RecoverPointClient objects
    private static ConcurrentMap<String, RecoverPointClient> clientMap = new ConcurrentHashMap<String, RecoverPointClient>();

    /**
     * Static method to manage Recover Point connections via the RecoverPointClient class.
     * 
     * When a valid connection is created the RecoverPointClient object is kept in a ConcurrentHashMap
     * and referenced by a unique key combination comprised of the endpoint + username + password.
     * 
     * If a connection key exists, a handle to an existing RecoverPointClient object will
     * be returned instead of creating a brand new connection. Otherwise, a new connection will be created
     * and stored (if valid).
     * 
     * If the connection info is invalid, an exception will be thrown and the object will not be stored.
     * 
     * The method can potentially be called by multiple threads, therefore synchronization is required
     * to maintain thread safety. ConcurrentHashMap is, by default, thread safe which is why
     * it is being used to store valid connections.
     * 
     * @param protectionSystem for a unique key, if we connect using a secondary address, keep using that address.
     * @param endpoints URI to the RecoverPoint System
     * @param username Username to log into the RecoverPoint System
     * @param password Password to log into the RecoverPoint System
     * 
     * @return RecoverPointClient with a connection to the RPA.
     * @throws RecoverPointException
     */
    public static synchronized RecoverPointClient getClient(URI protectionSystem, List<URI> endpoints, String username, String password)
            throws RecoverPointException {
        logger.info("Attempting to get RecoverPointClient connection...");

        // Throw an exception if null credentials are passed in.
        if (endpoints == null || username == null || password == null) {
            throw RecoverPointException.exceptions.invalidCrendentialsPassedIn(username, password);
        }

        // Unique key to identify RP connections for different Protection Systems
        String key = String.valueOf(protectionSystem) + username + password;

        // See if there is an existing valid RecoverPointClient using the protection system key
        RecoverPointClient existingClient = clientMap.get(key);

        if (existingClient != null) {
            logger.info("Existing RecoverPointClient connection found. Re-using connection: " + existingClient.getEndpoint().toString());
            try {
                // Do a ping check. If this fails, try the other IP addresses.
                existingClient.ping();
                return existingClient;
            } catch (Exception e) {
                logger.error("Received " + e.toString() + ".  Failed to ping Mgmt IP: " + existingClient.getEndpoint().toString() +
                        ", Cause: " + RecoverPointClient.getCause(e));
                // remove the protection system's connection from the factory, and the endpoint from the endpoints list
                clientMap.remove(key);
                endpoints.remove(existingClient.getEndpoint());
            }
        }

        // Now go through the endpoints and try to create a new RP client connection.
        Iterator<URI> endpointIter = endpoints.iterator();
        while (endpointIter.hasNext()) {
            URI endpoint = endpointIter.next();

            // Throw an exception if the endpoint can not be resolved to an ASCII string
            String mgmtIPAddress = endpoint.toASCIIString();
            if (mgmtIPAddress == null) {
                throw RecoverPointException.exceptions.noRecoverPointEndpoint();
            }

            logger.info("Creating new RecoverPointClient connection to: " + mgmtIPAddress);

            // If we don't have an existing RecoverPointClient, create a new one and add it to the client map only
            // if the connection is valid.
            RecoverPointClient newRecoverpointClient = new RecoverPointClient(endpoint, username, password);

            FunctionalAPIImpl impl = null;

            try {
                // Create the connection
                impl = new RecoverPointConnection().connect(endpoint, username, password);

                logger.info("New RecoverPointClient connection created to: " + mgmtIPAddress);

                // Add the new RecoverPointConnection to the RecoverPointClient
                newRecoverpointClient.setFunctionalAPI(impl);

                // We just connected but to be safe, lets do a quick ping to confirm that
                // we can reach the new RecoverPoint client
                newRecoverpointClient.ping();

                // Update the client map
                clientMap.put(key, newRecoverpointClient);

                return newRecoverpointClient;
            } catch (Exception e) {
                logger.error("Received " + e.toString() + ". Failed to create new RP connection: " + endpoint.toString() +
                        ", Cause: " + RecoverPointClient.getCause(e));
                // Remove invalid entry
                clientMap.remove(key);
                if (endpointIter.hasNext()) {
                    logger.info("Trying a different IP address to contact RP...");
                }
                else {
                    throw RecoverPointException.exceptions.failedToPingMgmtIP(mgmtIPAddress, RecoverPointClient.getCause(e));
                }
            }
        }

        // You'll never get here.
        return null;
    }
}
