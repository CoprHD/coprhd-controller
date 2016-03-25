/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.security;


import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.security.ipsec.IPsecConfig;
import com.emc.storageos.svcs.errorhandling.resources.ServiceUnavailableException;
import com.emc.storageos.systemservices.impl.ipsec.IPsecManager;
import com.emc.storageos.systemservices.impl.property.Notifier;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;
import com.emc.storageos.systemservices.impl.upgrade.LocalRepository;
import com.sun.corba.se.spi.resolver.LocalResolver;
import org.apache.commons.lang.StringUtils;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class SecretsInit implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(SecretsInit.class);

    private IPsecManager ipsecMgr;
    private CoordinatorClientExt coordinator;
    private IPsecConfig ipsecConfig;
    private DrUtil drUtil;
    private String ipsecLock = "ipseclock";

    private int IPSEC_ROTATION_RETRY_INTERVAL = 10;  //seconds

    @Override
    public void run() {
        boolean ipsecInitDone = false;
        boolean dhInitDone = false;

        while (true) {
            if (!ipsecInitDone) {
                ipsecInitDone = rotateIPsecKey();
            }

            if (!dhInitDone) {
                dhInitDone = genDHParam();
            }

            if (ipsecInitDone && dhInitDone) {
                return;
            }

            try {
                log.info("sleep for " + IPSEC_ROTATION_RETRY_INTERVAL + " seconds before retrying ipsec rotation.");
                Thread.sleep(IPSEC_ROTATION_RETRY_INTERVAL * 1000);
            } catch (InterruptedException iex) {
                log.warn("interrupted ipsec initial ");
            }
        }
    }

    private boolean genDHParam() {
        LocalRepository localRepository = LocalRepository.getInstance();
        try {
            localRepository.genDHParam();

            log.info("Reconfiguring SSL related config files");
            localRepository.reconfigProperties("ssl");

            log.info("Invoking SSL notifier");
            Notifier.getInstance("ssl").doNotify();
        } catch (Exception e) {
            log.warn("Failed to generate dhparam.", e);
            return false;
        }
        return true;
    }

    private boolean rotateIPsecKey() {
        InterProcessLock lock = null;
        try {
            if (drUtil.isMultivdc()) {
                log.info("Skip ipsec key initial rotation for multi-vdc configuration");
                return true;
            }

            if (drUtil.isMultisite()) {
                log.info("Skip ipsec key initial rotation for multi-site DR configuration");
                return true;
            }

            String preSharedKey = ipsecConfig.getPreSharedKeyFromZK();
            if (!StringUtils.isBlank(preSharedKey)) {
                log.info("IPsec key has been initialized");
                return true;
            }

            lock = coordinator.getCoordinatorClient().getSiteLocalLock(ipsecLock);
            lock.acquire();
            log.info("Acquired the lock {}", ipsecLock);
            preSharedKey = ipsecConfig.getPreSharedKeyFromZK();
            if (!StringUtils.isBlank(preSharedKey)) {
                log.info("IPsec key has been initialized. No need to regenerate it");
                return true;
            }

            if (drUtil.isAllSitesStable()) {
                log.info("No pre shared key in zk, generate a new key");
                ipsecMgr.rotateKey(true);
                return true;
            }

            return false;
        } catch (ServiceUnavailableException suex) {
            log.warn("cluster is not stable currently.");
            return false;
        } catch (Exception ex) {
            log.warn("error when run ipsec initial rotation: ", ex);
            return false;
        } finally {
            try {
                if (lock != null) {
                    lock.release();
                    log.info("Released the lock {}", ipsecLock);
                }
            } catch (Exception ex) {
                log.warn("error in releasing the lock {}", ipsecLock);
            }
        }
    }


    public IPsecConfig getIpsecConfig() {
        return ipsecConfig;
    }

    public void setIpsecConfig(IPsecConfig ipsecConfig) {
        this.ipsecConfig = ipsecConfig;
    }

    public CoordinatorClientExt getCoordinator() {
        return coordinator;
    }

    public void setCoordinator(CoordinatorClientExt coordinator) {
        this.coordinator = coordinator;
    }

    public IPsecManager getIpsecMgr() {
        return ipsecMgr;
    }

    public void setIpsecMgr(IPsecManager ipsecMgr) {
        this.ipsecMgr = ipsecMgr;
    }

    public DrUtil getDrUtil() {
        return drUtil;
    }

    public void setDrUtil(DrUtil drUtil) {
        this.drUtil = drUtil;
    }
    
}
