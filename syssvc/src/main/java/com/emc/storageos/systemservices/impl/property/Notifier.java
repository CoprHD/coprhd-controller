/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.systemservices.impl.property;

import com.emc.storageos.systemservices.exceptions.SysClientException;
import com.emc.storageos.systemservices.impl.client.SysClientFactory;
import com.emc.storageos.systemservices.impl.jobs.backupscheduler.BackupScheduler;
import com.emc.storageos.systemservices.impl.upgrade.LocalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Notifier instances trigger notify processes to reload the updated properties once
 * a relevant property update is made.
 */
public abstract class Notifier {
    private static final Logger log = LoggerFactory.getLogger(Notifier.class);
    private static Map<String, Notifier> notifierMap = new HashMap<>();
    protected static final LocalRepository repository = LocalRepository.getInstance();

    private static final String CONNECTEMC_NOTIFIER = "connectemc";
    private static final String NTP_NOTIFIER = "ntp";
    private static final String SSH_NOTIFIER = "ssh";
    private static final String SSL_NOTIFIER = "ssl";
    private static final String DNS_NOTIFIER = "dns";
    private static final String SSH_AUTH_KEY_NOTIFIER = "ssh_auth_key";
    private static final String PASSWORD_NOTIFIER = "password";
    private static final String BACKUPSCHEDULER_NOTIFIER = "backupscheduler";
    private static final String UPGRADE_NOTIFIER = "upgrade";

    public abstract void doNotify() throws Exception;

    public static Notifier getInstance(String notifierType) {
        if (notifierType == null)
            return null;

        if (notifierMap.containsKey(notifierType))
            return notifierMap.get(notifierType);

        Notifier notifier = null;
        switch (notifierType) {
            case CONNECTEMC_NOTIFIER:
            case NTP_NOTIFIER:
            case SSH_NOTIFIER:
            case SSL_NOTIFIER:
                notifier = new NonStorageosSvcNotifier(notifierType);
                break;
            case DNS_NOTIFIER:
            case PASSWORD_NOTIFIER:
            case SSH_AUTH_KEY_NOTIFIER:
                notifier = new NoopNotifier();
                break;
            case BACKUPSCHEDULER_NOTIFIER:
                notifier = BackupScheduler.getSingletonInstance();
                break;
            case UPGRADE_NOTIFIER:
                notifier = new UpgradeNotifier();
                break;
            default:
                log.error("Unsupported notifier type {}", notifierType);
                return null;
        }
        notifierMap.put(notifierType, notifier);
        return notifier;
    }

    public static class NoopNotifier extends Notifier {
        @Override
        public void doNotify() throws Exception {
        }
    }

    public static class UpgradeNotifier extends Notifier {
        static private final int DEFAULT_SVC_PORT = 9998;

        @Override
        public void doNotify() throws Exception {
            try {
                SysClientFactory.getSysClient(URI.create(String.format(SysClientFactory.BASE_URL_FORMAT,
                        "localhost", DEFAULT_SVC_PORT)))
                        .post(SysClientFactory.URI_WAKEUP_UPGRADE_MANAGER, null, null);
            } catch (SysClientException e) {
                log.error("Error waking up Upgrade Manager on node: {} Cause: {}", "localhost", e.getMessage());
            }
        }
    }

    /**
     * This class notifies a non-storageos owned service daemon to reload its config
     * after /etc/genconfig regenerates it. Need to leverage systool in this case.
     */
    public static class NonStorageosSvcNotifier extends Notifier {
        private final String svcName;

        NonStorageosSvcNotifier(final String svcName) {
            this.svcName = svcName;
        }

        @Override
        public void doNotify() throws Exception {
            repository.reload(svcName);
        }
    }
}
