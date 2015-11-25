/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.security;

import java.util.Calendar;
import java.util.Map;

import com.emc.storageos.services.util.PlatformUtils;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.model.PropertyInfoExt;
import com.emc.storageos.model.property.PropertiesMetadata;
import com.emc.storageos.model.property.PropertyInfoRestRep;
import com.emc.storageos.model.property.PropertyMetadata;
import com.emc.storageos.security.keystore.impl.CoordinatorConfigStoringHelper;
import com.emc.storageos.security.ssh.PEMUtil;
import com.emc.storageos.security.ssh.SSHKeyPair;
import com.emc.storageos.security.ssh.SSHKeyPairGenerator;
import com.emc.storageos.security.ssh.SSHParam;
import com.emc.storageos.systemservices.impl.property.PropertyManager;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;
import com.emc.storageos.systemservices.impl.validate.PropertiesConfigurationValidator;

/**
 * This class generates ssh host and user keys and ssh config files (ssh_known_hosts or authorizedkeys2)
 * and store them into system properties
 */
public class SshConfigurator {

    private static final Logger log = LoggerFactory.getLogger(SshConfigurator.class);

    private static final String SSH_LOCK = "sshLock";
    private static final String[] SSH_USERS = { "root", "svcuser", "storageos" };

    // System property keys
    private static final String SSH_CONFIG_VERSION = "ssh_config_version";

    // All generated ssh staff will be stored and be dumped to system properties together
    private PropertyInfoExt sshProps;

    @Autowired
    private PropertiesConfigurationValidator validator;

    private CoordinatorClientExt coordinator;

    @Autowired
    private PropertyManager propertyManager;

    private InterProcessLock sshLock;

    public void run() throws Exception {
        log.info("Checking if need to sync SSH configuration ...");

        if (!PlatformUtils.isAppliance()) {
            log.info("This is not a ViPR appliance so skip ssh configuration.");
            return;
        }

        sshProps = coordinator.getTargetInfo(PropertyInfoExt.class);

        CoordinatorConfigStoringHelper coordinatorHelper =
                new CoordinatorConfigStoringHelper(coordinator.getCoordinatorClient());

        // This lock and sshKeyGenRequired together to ensure only one node regenerates keys.
        sshLock = coordinatorHelper.acquireLock(SSH_LOCK);

        try {
            if (!sshKeyGenRequired()) {
                log.info("Real SSH keys are already in place. No need to regenerate.");
                return;
            }

            // Go here if regeneration required
            doRun();
            log.info("SSH configuration is synced");
        } finally {
            coordinatorHelper.releaseLock(sshLock);
        }
    }

    /**
     * return true if current version equal to 0 (default version)
     * 
     * @return
     */
    private boolean sshKeyGenRequired() throws Exception {
        String currentVersion = getCurrentVersion();
        if (currentVersion == null) {
            return true;
        }
        return getCurrentVersion().equals(getDefaultVersion());
    }

    private String getCurrentVersion() throws Exception {
        Map<String, String> curProps = coordinator.getTargetInfo(PropertyInfoExt.class).getProperties();
        return curProps.get(SSH_CONFIG_VERSION);
    }

    private String getDefaultVersion() {
        Map<String, PropertyMetadata> metadata = PropertiesMetadata.getGlobalMetadata();
        PropertyMetadata prop = metadata.get(SSH_CONFIG_VERSION);
        return prop.getDefaultValue();
    }

    private void doRun() throws Exception {

        log.info("Configuring SSH ...");

        // generate host keys /etc/ssh/ssh_host_*_key and save them to system properties
        genSshHostKeys();

        // generate user keys <user home>/.ssh/id_* and save them to system properties
        // now the users are root, storageos, svcuser
        for (String user : SSH_USERS) {
            genSshUserKeys(user);
        }

        saveToSystemProperties();
        log.info("Configured SSH successfully.");
    }

    private void genSshUserKeys(String user) throws Exception {

        // For rsa
        SSHKeyPair rsaKeyPair = SSHKeyPairGenerator.getInstance(SSHParam.KeyAlgo.RSA).generate();

        String rsaPrivKey = PEMUtil.encodePrivateKey(rsaKeyPair.getPrivateKey());
        saveToResultSet(userIdPropName(user, SSHParam.KeyAlgo.RSA), rsaPrivKey);

        // For dsa
        SSHKeyPair dsaKeyPair = SSHKeyPairGenerator.getInstance(SSHParam.KeyAlgo.DSA).generate();

        String dsaPrivKey = PEMUtil.encodePrivateKey(dsaKeyPair.getPrivateKey());
        saveToResultSet(userIdPropName(user, SSHParam.KeyAlgo.DSA), dsaPrivKey);

        // For ec keys. Note the ECDSA is not supported by JDK.
        try {
            SSHKeyPair ecKeyPair = SSHKeyPairGenerator.getInstance(SSHParam.KeyAlgo.ECDSA).generate();

            String ecPrivKey = PEMUtil.encodePrivateKey(ecKeyPair.getPrivateKey());
            saveToResultSet(userIdPropName(user, SSHParam.KeyAlgo.ECDSA), ecPrivKey);
        } catch (com.emc.storageos.security.exceptions.SecurityException e) {
            log.info("ECDSA keys are not supported. Skipping the key generation.");
        }

        log.info("SSH user keys are generated successfully.");
    }

    private void genSshHostKeys() throws Exception {

        // For rsa
        SSHKeyPair rsaKeyPair = SSHKeyPairGenerator.getInstance(SSHParam.KeyAlgo.RSA).generate();

        String rsaPrivKey = PEMUtil.encodePrivateKey(rsaKeyPair.getPrivateKey());
        saveToResultSet(hostPropName(SSHParam.KeyAlgo.RSA), rsaPrivKey);

        // For dsa
        SSHKeyPair dsaKeyPair = SSHKeyPairGenerator.getInstance(SSHParam.KeyAlgo.DSA).generate();

        String dsaPrivKey = PEMUtil.encodePrivateKey(dsaKeyPair.getPrivateKey());
        saveToResultSet(hostPropName(SSHParam.KeyAlgo.DSA), dsaPrivKey);

        // For ec keys
        try {
            SSHKeyPair ecKeyPair = SSHKeyPairGenerator.getInstance(SSHParam.KeyAlgo.ECDSA).generate();

            String ecPrivKey = PEMUtil.encodePrivateKey(ecKeyPair.getPrivateKey());
            saveToResultSet(hostPropName(SSHParam.KeyAlgo.ECDSA), ecPrivKey);
        } catch (com.emc.storageos.security.exceptions.SecurityException e) {
            log.info("ECDSA is not supported. Skipping the key generation.");
        }

        log.info("SSH host keys are generated successfully.");
    }

    private void saveToResultSet(String key, String val) {
        sshProps.addProperty(key, validator.getValidPropValue(key, val, false));
    }

    private void saveToSystemProperties() throws Exception {
        // Before saving, update version
        String sshVersion = Long.toString(Calendar.getInstance().getTimeInMillis());
        sshProps.addProperty(SSH_CONFIG_VERSION, sshVersion);

        // Update config version as well
        sshProps.addProperty(PropertyInfoRestRep.CONFIG_VERSION, sshVersion);

        // Set to false to bypass the stability check of system. There should no contention with UpgradeManager
        coordinator.setTargetInfo(sshProps, false);

        log.info("All ssh configurations are saved to system properties successfully");
    }

    public static String hostPropName(SSHParam.KeyAlgo algo) {
        return String.format("ssh_host_%s_key", algo.name().toLowerCase());
    }

    public static String userIdPropName(String user, SSHParam.KeyAlgo algo) {
        return String.format("%s_id_%s", user, algo.name().toLowerCase());
    }

    public void setCoordinator(CoordinatorClientExt coordinator) {
        this.coordinator = coordinator;
    }
}
