/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.migrationtool;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.security.auth.Subject;
import javax.wbem.CloseableIterator;
import javax.wbem.WBEMException;
import javax.wbem.client.PasswordCredential;
import javax.wbem.client.UserPrincipal;
import javax.wbem.client.WBEMClient;
import javax.wbem.client.WBEMClientFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.cimadapter.connections.cim.CimConstants;
import com.emc.storageos.cimadapter.connections.cim.CimObjectPathCreator;

public class CGRelationshipEstablisher extends Executor {
    private static final Logger log = LoggerFactory.getLogger(CGRelationshipEstablisher.class);

    // A reference to the CIM client.
    private WBEMClient cimClient;

    private static final boolean DEBUG = false;
    private String host;
    private String user;
    private String pass;
    private String port;
    private boolean useSSL;

    @Override
    public boolean execute() {
        log.info("Start");
        try {
            createCimClient();
            getAllReplicationGroup();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        } finally {
            try {
                if (cimClient != null) {
                    cimClient.close();
                }
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
        return false;
    }

    private void getAllReplicationGroup() throws WBEMException {
        log.info("Started collecting Replication Group object Path from SMIS");
        CIMObjectPath replicationGroupPath = CimObjectPathCreator.createInstance(
                "CIM_ReplicationGroup",
                Constants.ROOT_EMC_NAMESPACE);
        log.debug("replicationGroupPath :{}", replicationGroupPath);
        CloseableIterator<CIMObjectPath> replicationGroupInstanceItr = getEnumerateInstanceNames(replicationGroupPath);

        while (replicationGroupInstanceItr.hasNext()) {
            CIMObjectPath replicationGrpPath = replicationGroupInstanceItr.next();
            log.info(replicationGrpPath.toString());
        }
    }

    public void createCimClient() throws Exception {
        try {
            log.info("Start create CIM client");
            cimClient = WBEMClientFactory.getClient("CIM-XML");
            String protocol = useSSL ? CimConstants.SECURE_PROTOCOL : CimConstants.DEFAULT_PROTOCOL;
            Subject subject = new Subject();
            subject.getPrincipals().add(new UserPrincipal(user));
            subject.getPrivateCredentials().add(new PasswordCredential(pass));
            CIMObjectPath path = new CIMObjectPath(protocol,
                    host, port, null, null, null);
            cimClient.initialize(path, subject, null);
            log.info("CIM Client for SMI communication has been created");
        } catch (Exception e) {
            log.error("Could not establish connection for {}", host, e);
            cimClient.close();
            throw e;
        }
    }

    public CloseableIterator<CIMObjectPath> getEnumerateInstanceNames(CIMObjectPath path) throws WBEMException {
        return getCimClient().enumerateInstanceNames(path);
    }

    public CloseableIterator<CIMInstance> getEnumerateInstances(CIMObjectPath path, String[] prop) throws WBEMException {
        return getCimClient().enumerateInstances(path, true, false, false, prop);
    }

    public CloseableIterator<CIMObjectPath> getReferenceNames(CIMObjectPath path,
                    String resultClass, String role)
                    throws WBEMException {
        return getCimClient().referenceNames(path, resultClass, role);
    }

    private void createGroupSync() {
        /*
         * inArgs = helper.getCreateGroupReplicaFromElementSynchronizationsForSRDFInputArguments(srcCGPath,
         * tgtCGPath, elementSynchronizations);
         * helper.invokeMethod(systemWithCg, srcRepSvcPath,
         * SmisConstants.CREATE_GROUP_REPLICA_FROM_ELEMENT_SYNCHRONIZATIONS, inArgs, outArgs);
         */
    }

    /**
     * @return the _cimClient
     */
    public WBEMClient getCimClient() {
        return cimClient;
    }

    /**
     * @return the host
     */
    public String getHost() {
        return host;
    }

    /**
     * @param host the host to set
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * @return the user
     */
    public String getUser() {
        return user;
    }

    /**
     * @param user the user to set
     */
    public void setUser(String user) {
        this.user = user;
    }

    /**
     * @return the pass
     */
    public String getPass() {
        return pass;
    }

    /**
     * @param pass the pass to set
     */
    public void setPass(String pass) {
        this.pass = pass;
    }

    /**
     * @return the port
     */
    public String getPort() {
        return port;
    }

    /**
     * @param port the port to set
     */
    public void setPort(String port) {
        this.port = port;
    }

    /**
     * @return the useSSL
     */
    public boolean isUseSSL() {
        return useSSL;
    }

    /**
     * @param useSSL the useSSL to set
     */
    public void setUseSSL(boolean useSSL) {
        this.useSSL = useSSL;
    }

}
