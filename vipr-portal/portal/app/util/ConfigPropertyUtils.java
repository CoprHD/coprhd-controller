/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import static util.BourneUtil.getSysClient;

import java.util.Map;

import com.emc.vipr.client.ViPRCoreClient;
import org.apache.commons.lang.StringUtils;

import play.Logger;
import play.Play;
import play.libs.Mail;
import play.mvc.Util;
import plugin.StorageOsPlugin;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.model.property.PropertiesMetadata;
import com.emc.storageos.model.property.PropertyInfoUpdate;
import com.emc.vipr.client.ViPRSystemClient;
import com.google.common.collect.Maps;

public class ConfigPropertyUtils {
    public static final String ZERO_PORT = "0";
    public static final String DEFAULT_SMTP_TLS_PORT = "465";
    public static final String DEFAULT_SMTP_PORT = "25";
    public static final String SMTP_PROPERTY_PREFIX = "system_connectemc_smtp_";

    public static PropertiesMetadata getPropertiesMetadata() {
        return getSysClient().config().getPropMetadata();
    }

    public static Map<String, String> getProperties() {
        return getSysClient().config().getProperties().getProperties();
    }

    public static Map<String, String> getPropertiesFromCoordinator() {
        // Only do this if we have coordinator available
        if (StorageOsPlugin.isEnabled()) {
            CoordinatorClient coordinatorClient = StorageOsPlugin.getInstance().getCoordinatorClient();
            com.emc.storageos.model.property.PropertyInfo propertyInfo = coordinatorClient.getPropertyInfo();
            if (propertyInfo != null) {
                return propertyInfo.getAllProperties();
            }
        }
        return Maps.newHashMap();
    }

    public static void saveProperties(Map<String, String> updatedProperties) {
        saveProperties(getSysClient(), updatedProperties);
    }

    public static void saveProperties(ViPRSystemClient client, Map<String, String> updatedProperties) {
        if (Logger.isDebugEnabled()) {
            Logger.debug("Saving properties");
            for (Map.Entry<String, String> entry : updatedProperties.entrySet()) {
                Logger.debug(" %s = %s", entry.getKey(), entry.getValue());
            }
        }

        PropertyInfoUpdate propertyInfoUpdate = new PropertyInfoUpdate();
        propertyInfoUpdate.addProperties(updatedProperties);

        client.config().setProperties(propertyInfoUpdate);
        // Reload the SMTP settings into play if any of the properties have changed
        if (containsSmtpSettings(updatedProperties)) {
            loadSmtpSettingsIntoPlay();
        }
    }

    public static String rotateIpsecKey(ViPRSystemClient client) {
        return client.ipsec().rotateIpsecKey();
    }

    public static void loadCoordinatorProperties() {
        Map<String, String> properties = getPropertiesFromCoordinator();
        loadSmtpSettingsIntoPlay(properties);
        loadApplicationBaseUrl(properties);
    }

    public static void loadApplicationBaseUrl(Map<String, String> properties) {
        String applicationHost = getApplicationHost(properties);
        if (StringUtils.isNotBlank(applicationHost)) {
            String applicationBaseUrl = String.format("https://%s/", applicationHost);
            Play.configuration.setProperty("application.baseUrl", applicationBaseUrl);
        }

        Logger.debug("  application.baseUrl = %s", Play.configuration.getProperty("application.baseUrl"));
    }

    /**
     * Gets the host name or IP for the application from the coordinator properties. This will use the virtual IP
     * if available, otherwise it uses the standalone network IP.
     * 
     * @param properties the coordinator properties.
     * @return the application host.
     */
    private static String getApplicationHost(Map<String, String> properties) {
        // TODO: confirm that using these properties make sense, they seem like they should work.
        String virtualIp = properties.get(ConfigProperty.NETWORK_VIRTUAL_IP);
        String standaloneIp = properties.get(ConfigProperty.NETWORK_STANDALONE_IP);
        if (StringUtils.isNotBlank(virtualIp) && !StringUtils.equals(virtualIp, "0.0.0.0")) {
            return virtualIp;
        }
        else if (StringUtils.isNotBlank(standaloneIp)) {
            return standaloneIp;
        }
        else {
            return null;
        }
    }

    /**
     * Determines if the properties contain any SMTP settings.
     * 
     * @param properties the properties.
     * @return true if SMTP settings are in the properties.
     */
    public static boolean containsSmtpSettings(Map<String, String> properties) {
        for (String key : properties.keySet()) {
            if (key.startsWith(SMTP_PROPERTY_PREFIX)) {
                return true;
            }
        }
        return false;
    }

    public static void loadSmtpSettingsIntoPlay() {
        loadSmtpSettingsIntoPlay(getPropertiesFromCoordinator());
    }

    public static void loadSmtpSettingsIntoPlay(Map<String, String> properties) {
        Logger.info("Loading SMTP Settings into Play");

        // Clearing mail setting so they get re-created
        Mail.session = null;

        // enable emails in the portal (could be made configurable in ui later on)
        setPlayProperty("mailer.enabled", "true");

        if (Play.mode == Play.Mode.DEV) {
            setPlayProperty("mail.debug", "true");
        }
        else {
            setPlayProperty("mail.debug", "false");
        }

        String smtpServer = properties.get(ConfigProperty.SMTP_SERVER);
        setPlayProperty("mail.smtp.host", smtpServer);

        String smtpPort = properties.get(ConfigProperty.SMTP_PORT);
        boolean enableTls = StringUtils.equalsIgnoreCase("yes", properties.get(ConfigProperty.SMTP_ENABLE_TLS));
        if (enableTls) {
            setPlayProperty("mail.smtp.channel", "starttls");
            smtpPort = defaultPort(smtpPort, DEFAULT_SMTP_TLS_PORT);
        }
        else {
            smtpPort = defaultPort(smtpPort, DEFAULT_SMTP_PORT);
        }
        setPlayProperty("mail.smtp.port", smtpPort);

        String fromAddress = properties.get(ConfigProperty.SMTP_FROM_ADDRESS);
        setPlayProperty("mail.smtp.from", fromAddress);

        String smtpAuthType = properties.get(ConfigProperty.SMTP_AUTH_TYPE);
        if (StringUtils.isNotBlank(smtpAuthType) && !StringUtils.equalsIgnoreCase(smtpAuthType, "none")) {
            setPlayProperty("mail.smtp.auth", "true");

            String username = properties.get(ConfigProperty.SMTP_USERNAME);
            setPlayProperty("mail.smtp.user", username);

            String password = properties.get(ConfigProperty.SMTP_PASSWORD);
            setPlayProperty("mail.smtp.pass", password);
        }

        Logger.debug("  mail.debug        = %s", Play.configuration.getProperty("mail.debug"));
        Logger.debug("  mail.smtp.host    = %s", Play.configuration.getProperty("mail.smtp.host"));
        Logger.debug("  mail.smtp.port    = %s", Play.configuration.getProperty("mail.smtp.port"));
        Logger.debug("  mail.smtp.channel = %s", Play.configuration.getProperty("mail.smtp.channel"));
        Logger.debug("  mail.smtp.auth    = %s", Play.configuration.getProperty("mail.smtp.auth"));
        Logger.debug("  mail.smtp.user    = %s", Play.configuration.getProperty("mail.smtp.user"));
        Logger.debug("  mail.smtp.from    = %s", Play.configuration.getProperty("mail.smtp.from"));
    }

    @Util
    public static String defaultPort(String port, String defaultPort) {
        if (StringUtils.isBlank(port) || port.equalsIgnoreCase(ZERO_PORT)) {
            return defaultPort;
        }
        return port;
    }

    private static void setPlayProperty(String name, String value) {
        if (StringUtils.isNotBlank(value)) {
            Play.configuration.setProperty(name, value);
        }
        else {
            Play.configuration.remove(name);
        }
    }
}
