/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.security.ipsec;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.security.keystore.impl.CoordinatorConfigStoringHelper;
import org.apache.commons.codec.binary.StringUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.jsoup.helper.StringUtil;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/**
 * The class to read and write IPsec Configurations to ZK.
 */
public class IPsecConfig {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(IPsecConfig.class);

    private static final String IPSEC_CONFIG_LOCK = "IPsecConfigLock";
    private static final String IPSEC_CONFIG_KIND = "ipsec";
    private static final String IPSEC_CONFIG_ID = "ipsec";
    private static final String IPSEC_PSK_KEY = "ipsec_key";
    private static final int KEY_LENGHT = 64;

    // Properties injected by spring
    private CoordinatorClient coordinator;
    private String defaultPskFile;

    private CoordinatorConfigStoringHelper coordinatorHelper;

    /**
     * Get pre-shared key of the current site.
     * @return
     * @throws Exception
     */
    public String getPreSharedKey() throws Exception {
        String preSharedKey = getCoordinatorHelper().readConfig(IPSEC_CONFIG_KIND, IPSEC_CONFIG_ID, IPSEC_PSK_KEY);
        if (StringUtil.isBlank(preSharedKey)) {
            log.info("No pre shared key in zk, loading from file ...");
            preSharedKey = loadDefaultIpsecKeyFromFile();
        }
        return preSharedKey;
    }

    /**
     * write pre-shared key to ZK.
     * @param preSharedKey
     * @throws Exception
     */
    public void setPreSharedKey(String preSharedKey) throws Exception {
        getCoordinatorHelper().createOrUpdateConfig(preSharedKey, IPSEC_CONFIG_LOCK, IPSEC_CONFIG_KIND, IPSEC_CONFIG_ID, IPSEC_PSK_KEY);
    }

    /**
     * generate a 64-byte key for IPsec
     * @return
     */
    public String generateKey() {
        return RandomStringUtils.random(KEY_LENGHT, true, true);
    }


    private String loadDefaultIpsecKeyFromFile() throws Exception {
        BufferedReader in = new BufferedReader(new FileReader(new File(defaultPskFile)));
        try {
            String key = in.readLine();
            return key;
        } finally {
            in.close();
        }
    }

    private CoordinatorConfigStoringHelper getCoordinatorHelper() {
        if (coordinatorHelper == null) {
            coordinatorHelper = new CoordinatorConfigStoringHelper(coordinator);
        }
        return coordinatorHelper;
    }

    /**
     * Spring inject method
     * @param coordinator
     */
    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }

    /**
     * Spring inject method
     * @param defaultPskFile
     */
    public void setDefaultPskFile(String defaultPskFile) {
        this.defaultPskFile = defaultPskFile;
    }
}
