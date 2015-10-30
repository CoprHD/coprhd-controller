/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.migrationtool;

import javax.cim.CIMObjectPath;
import javax.security.auth.Subject;
import javax.wbem.WBEMException;
import javax.wbem.client.PasswordCredential;
import javax.wbem.client.UserPrincipal;
import javax.wbem.client.WBEMClient;
import javax.wbem.client.WBEMClientFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.impl.DbClientImpl;

public abstract class Executor {

    private static final Logger log = LoggerFactory.getLogger(Executor.class);

    DbClientImpl _dbClient = null;

    public abstract boolean execute(String providerID);

    public DbClientImpl getDbClient() {
        return _dbClient;
    }

    public void setDbClient(DbClientImpl dbClient) {
        this._dbClient = dbClient;
    }

    public void stop() {
        if (_dbClient != null) {
            _dbClient.stop();
        }
    }

    public void start() {
        try {
            log.info("Initializing db client ...");
            _dbClient.start();
        } catch (Exception e) {
            System.err.println("Caught Exception: " + e);
            log.error("Caught Exception: ", e);
        }
    }

    public WBEMClient getCimClient(String ipaddress, String portNumber, String username, String password, boolean useSSL)
            throws IllegalArgumentException, WBEMException {
        WBEMClient client = WBEMClientFactory.getClient("CIM-XML");
        String protocol = useSSL ? "https" : "http";
        Subject subject = new Subject();
        subject.getPrincipals().add(new UserPrincipal(username));
        subject.getPrivateCredentials().add(new PasswordCredential(password));
        CIMObjectPath path = new CIMObjectPath(protocol,
                ipaddress, portNumber, null, null, null);
        client.initialize(path, subject, null);
        return client;
    }

}
