/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import java.security.KeyStore;

import javax.net.ssl.SSLSocketFactory;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.UnhandledException;

import play.Logger;
import play.Play;
import play.mvc.Http;
import plugin.StorageOsPlugin;

import com.emc.storageos.model.property.PropertyInfo;
import com.emc.storageos.security.keystore.impl.KeyStoreUtil;
import com.emc.storageos.security.ssl.ViPRSSLSocketFactory;
import com.emc.storageos.security.ssl.ViPRX509TrustManager;
import com.emc.vipr.client.ClientConfig;
import com.emc.vipr.client.ViPRCatalogClient2;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.ViPRSystemClient;
import com.emc.vipr.client.impl.SSLUtil;

import controllers.security.Security;

/**
 * Utility for retrieving a bourne client from the VDC specific policy.
 * 
 * @author Chris Dail
 */
public class BourneUtil {
    public static final int INTERNAL_API_PORT = 8443;
    public static final int INTERNAL_SYS_PORT = 9993;
    public static final int INTERNAL_OBJ_PORT = 9011;

    private static final int MINUTES_IN_MS = 60 * 1000;

    private static KeyStore KEYSTORE = null;
    private static ViPRX509TrustManager TRUST_MANAGER = null;
    private static SSLSocketFactory SOCKET_FACTORY = null;

    public static synchronized KeyStore getKeyStore() {
        if (StorageOsPlugin.isEnabled() && (KEYSTORE == null)) {
            try {
                KEYSTORE = KeyStoreUtil.getViPRKeystore(StorageOsPlugin.getInstance().getCoordinatorClient());
            } catch (Exception e) {
                throw new UnhandledException(e);
            }
        }
        return KEYSTORE;
    }

    public static synchronized ViPRX509TrustManager getTrustManager() {
        if (StorageOsPlugin.isEnabled() && (TRUST_MANAGER == null)) {
            TRUST_MANAGER = new ViPRX509TrustManager(StorageOsPlugin.getInstance().getCoordinatorClient());
        }
        return TRUST_MANAGER;
    }

    public static synchronized SSLSocketFactory getSocketFactory() {
        if (StorageOsPlugin.isEnabled() && (SOCKET_FACTORY == null)) {
            SOCKET_FACTORY = new ViPRSSLSocketFactory(StorageOsPlugin.getInstance().getCoordinatorClient());
        }
        return SOCKET_FACTORY;
    }

    private static <T> T getRequestArg(String key) {
        Http.Request request = Http.Request.current();
        if ((request != null) && request.args.containsKey(key)) {
            return (T) request.args.get(key);
        }
        else {
            return null;
        }
    }

    private static void setRequestArg(String key, Object value) {
        Http.Request request = Http.Request.current();
        if (request != null) {
            request.args.put(key, value);
        }
        else {
            Logger.error("Not within a request, cannot not save %s as %s", value, key);
        }
    }

    private static ClientConfig getBaseClientConfig() {
        ClientConfig config = new ClientConfig();
        config.setHost(getViprHost());
        config.setRequestLoggingEnabled(isConfigPropertySet("storageos.api.debugging"));

		// Client timeout
		PropertyInfo propInfo = null;
		if(!Play.mode.isDev()) {
			propInfo = StorageOsPlugin.getInstance().getCoordinatorClient().getPropertyInfo();
		}
		String timeoutProperty = null;
		int timeout = 5;
		if (propInfo != null) {
			timeoutProperty = propInfo.getProperty("portal_service_timeout");
		}

		if (timeoutProperty != null) {
			timeout = Integer.parseInt(timeoutProperty);
		} else {
			timeout = Integer.parseInt(Play.configuration.getProperty("vipr.client.timeout.minutes", "5"));
		}

        config.setReadTimeout(timeout * MINUTES_IN_MS);
        config.setConnectionTimeout(timeout * MINUTES_IN_MS);

        // setup socketfactory, unless we're in portal only mode

        if (StorageOsPlugin.isEnabled()) {
            config.setSocketFactory(getSocketFactory());
            config.setHostnameVerifier(SSLUtil.getNullHostnameVerifier());
        }
        else {
            config.setIgnoreCertificates(true);
        }
        return config;
    }

    private static ClientConfig getClientConfig() {
        if (isConfigPropertySet("disable.nginx")) {
            return getBaseClientConfig().withPort(INTERNAL_API_PORT);
        }
        return getBaseClientConfig();
    }

    private static ClientConfig getSysConfig() {
        if (isConfigPropertySet("disable.nginx")) {
            return getBaseClientConfig().withPort(INTERNAL_SYS_PORT);
        }
        return getBaseClientConfig();
    }

    public static ViPRCoreClient getViprClient() {
        String authToken = Security.getAuthToken();
        String key = String.format("ViPRCoreClient.%s", authToken);
        ViPRCoreClient client = getRequestArg(key);
        if (client == null) {
            Logger.debug("Creating new ViPRCoreClient");
            client = new ViPRCoreClient(getClientConfig()).withAuthToken(authToken);
            setRequestArg(key, client);
        }
        else {
            Logger.debug("Returning cached ViPRCoreClient");
        }
        return client;
    }

    public static ViPRCatalogClient2 getCatalogClient() {
        String authToken = Security.getAuthToken();
        String key = String.format("ViPRCatalogClient.%s", authToken);
        ViPRCatalogClient2 client = getRequestArg(key);
        if (client == null) {
            Logger.debug("Creating new ViPRCatalogClient");
            client = new ViPRCatalogClient2(getClientConfig()).withAuthToken(authToken);
            setRequestArg(key, client);
        }
        else {
            Logger.debug("Returning cached ViPRCatalogClient");
        }
        return client;
    }

    public static ViPRSystemClient getSysClient() {
        String authToken = Security.getAuthToken();
        String key = String.format("ViPRSystemClient.%s", authToken);
        ViPRSystemClient client = getRequestArg(key);
        if (client == null) {
            Logger.debug("Creating new ViPRSystemClient");
            client = new ViPRSystemClient(getSysConfig()).withAuthToken(authToken);
            setRequestArg(key, client);
        }
        else {
            Logger.debug("Returning cached ViPRSystemClient");
        }
        return client;
    }

    private static String getViprHost() {
        String networkIp = Play.configuration.getProperty("vipr.networkip");
        String virtualIp = Play.configuration.getProperty("vipr.virtualip");

        String host = "localhost";
        if (StringUtils.isNotBlank(virtualIp)) {
            host = virtualIp;
        }
        else if (StringUtils.isNotBlank(networkIp)) {
            host = networkIp;
        }
        return host;
    }

    private static boolean isConfigPropertySet(String key) {
        return "true".equalsIgnoreCase(Play.configuration.getProperty(key));
    }

    /**
     * Gets the root of the SYS API URL.
     * 
     * @return the root API URL.
     */
    public static String getSysApiUrl() {
        ClientConfig config = getSysConfig();
        return String.format("%s://%s:%d/", config.getProtocol(), config.getHost(), config.getPort());
    }

    public static String getVersion() {
        return StorageOsPlugin.getInstance().getVersion();
    }
}
