/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.auth.impl;

import com.emc.storageos.auth.SystemPropertyUtil;
import com.emc.storageos.auth.ldap.StorageOSLdapAuthenticationHandler;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.AuthnProvider;
import com.emc.storageos.db.exceptions.DatabaseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ldap.core.DistinguishedName;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;

import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LdapProviderMonitor {

    private static final Logger log = LoggerFactory.getLogger(LdapProviderMonitor.class);
    private static final long MONITOR_INTERVAL_MIN = 10;

    private ImmutableAuthenticationProviders providerList;
    private DbClient dbClient;
    private CoordinatorClient coordinator;

    public LdapProviderMonitor(CoordinatorClient coordinator, DbClient dbClient, ImmutableAuthenticationProviders providerList) {
        this.coordinator = coordinator;
        this.dbClient = dbClient;
        this.providerList = providerList;
    }

    public void start() {
        ScheduledExecutorService scheduleService = Executors.newScheduledThreadPool(1);
        scheduleService.scheduleAtFixedRate(new LdapMonitorWorker(), 0, MONITOR_INTERVAL_MIN, TimeUnit.MINUTES);
    }

    private class LdapMonitorWorker implements Runnable {

        @Override
        public void run() {
            while (true) {
                List<AuthenticationProvider> providers = providerList.getAuthenticationProviders();
                for (AuthenticationProvider provider : providers) {
                    if (!(provider.getHandler() instanceof StorageOSLdapAuthenticationHandler)) { // That's for AD or Ldap
                        continue;
                    }
                    StorageOSLdapAuthenticationHandler handler = (StorageOSLdapAuthenticationHandler) provider.getHandler();
                    LdapServerList ldapServers = handler.getLdapServers();
                    List<LdapOrADServer> disconnectedServers = ldapServers.getDisconnectedServers();

                    AuthnProvider authnProvider = queryAuthnProviderFromDB(handler.getDomains());

                    // Do check.
                    for (LdapOrADServer server : disconnectedServers) {
                        boolean isGood = checkLdapServerConnectivity(authnProvider, server.getContextSource().getUrls()[0]);
                        if (isGood) {
                            ldapServers.updateWithConnected(server);
                        }
                    }
                }
            }
        }
    }

    private boolean checkLdapServerConnectivity(AuthnProvider authnProvider, String serverUrl) {

        int timeout = SystemPropertyUtil.getLdapConnectionTimeout(coordinator);
        LdapContextSource contextSource = ImmutableAuthenticationProviders.createConfiguredLDAPContextSource(coordinator, authnProvider, timeout, serverUrl);

        LdapTemplate template = new LdapTemplate(contextSource);
        template.setIgnorePartialResultException(true);
        try {
            // authenticates manager credentials and performs the look up for the search base
            template.lookup(new DistinguishedName(authnProvider.getSearchBase()));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private AuthnProvider queryAuthnProviderFromDB(Set<String> domains) {
        URIQueryResultList providers = new URIQueryResultList();
        String domain = (String) domains.toArray()[0]; // Must have at lease one
        try {
            dbClient.queryByConstraint(AlternateIdConstraint.Factory.getAuthnProviderDomainConstraint(domain), providers);
            Iterator<URI> it = providers.iterator();
            while (it.hasNext()) {
                URI providerURI = it.next();
                AuthnProvider provider = dbClient.queryObject(AuthnProvider.class, providerURI);
                if (provider != null && provider.getDisable() == false) {
                    return provider;
                }
            }
        } catch (DatabaseException ex) {
            log.error("Could not query for authn providers to check for existing domain {}", domain, ex);
            throw ex;
        }

        return null;
    }
}
