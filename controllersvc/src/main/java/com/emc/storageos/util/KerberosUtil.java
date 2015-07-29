/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.util;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.emc.storageos.db.client.model.AuthnProvider;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.iwave.ext.kerberos.KerberosConfig;
import com.iwave.ext.kerberos.Krb5ConfBuilder;

public class KerberosUtil {
    private static Logger log = Logger.getLogger(KerberosUtil.class);

    public static final void initializeKerberos(List<AuthnProvider> authProviders) {
        try {
            String krb5Config = generateKerberosConfigFile(authProviders);
            KerberosConfig.getInstance().initialize(krb5Config);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize Kerberos", e);
        }
    }

    public static String generateKerberosConfigFile(List<AuthnProvider> authProviders) {
        Map<String, List<String>> domainToKDCs = Maps.newHashMap();
        log.debug("Generating kerberos config file from all " + authProviders.size() + " authentication providers");

        for (AuthnProvider authProvider : authProviders) {
            for (String domain : authProvider.getDomains()) {
                List<String> kdcAddresses = Lists.newArrayList();

                for (String kdcAddress : authProvider.getServerUrls()) {
                    try {
                        URI uri = new URI(kdcAddress);
                        kdcAddresses.add(uri.getHost());
                    } catch (URISyntaxException e) {
                        log.error("Error processing AD " + domain + " address " + kdcAddress, e);
                    }
                }

                domainToKDCs.put(domain, kdcAddresses);
            }
        }

        return Krb5ConfBuilder.build(domainToKDCs);
    }
}
