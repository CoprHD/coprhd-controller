/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.properties;

import java.util.Map;
import util.ConfigProperty;

public class SmtpPropertyPage extends CustomPropertyPage {
    private Property server;
    private Property port;
    private Property username;
    private Property password;
    private Property enableTls;
    private Property authType;
    private Property fromAddress;

    public SmtpPropertyPage(Map<String, Property> properties) {
        super("Email");
        setRenderTemplate("smtpPage.html");
        server = addCustomProperty(properties, ConfigProperty.SMTP_SERVER);
        port = addCustomProperty(properties, ConfigProperty.SMTP_PORT);
        username = addCustomProperty(properties, ConfigProperty.SMTP_USERNAME);
        password = addCustomPasswordProperty(properties, ConfigProperty.SMTP_PASSWORD);
        enableTls = addCustomProperty(properties, ConfigProperty.SMTP_ENABLE_TLS);
        authType = addCustomProperty(properties, ConfigProperty.SMTP_AUTH_TYPE);
        fromAddress = addCustomProperty(properties, ConfigProperty.SMTP_FROM_ADDRESS);
    }

    public Property getServer() {
        return server;
    }

    public Property getPort() {
        return port;
    }

    public Property getUsername() {
        return username;
    }

    public Property getPassword() {
        return password;
    }

    public Property getEnableTls() {
        return enableTls;
    }

    public Property getAuthType() {
        return authType;
    }

    public Property getFromAddress() {
        return fromAddress;
    }
}
