/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.properties;

import org.apache.commons.lang.StringUtils;
import play.data.validation.Validation;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SupportPropertyPage extends CustomPropertyPage {
    private Property encrypt;
    private Property transport;
    private Property ftpsHostname;
    private Property ftpsPort;
    private Property smtpEmcTo;
    private Property smtpTo;

    public SupportPropertyPage(Map<String, Property> properties) {
        super("ConnectEMC");
        setRenderTemplate("supportPage.html");
        encrypt = addCustomProperty(properties, "system_connectemc_encrypt");
        transport = addCustomProperty(properties, "system_connectemc_transport");
        ftpsHostname = addCustomProperty(properties, "system_connectemc_ftps_hostname");
        ftpsPort = addCustomProperty(properties, "system_connectemc_ftps_port");
        smtpEmcTo = addCustomProperty(properties, "system_connectemc_smtp_emcto");
        smtpTo = addCustomProperty(properties, "system_connectemc_smtp_to");
    }
    
    @Override
    protected void validate(List<Property> props, Map<String, String> values) {
        super.validate(props, values);
        String transport = values.get("system_connectemc_transport");
        String hostname = values.get("system_connectemc_ftps_hostname");
        if (StringUtils.equals(transport, "FTPS") && StringUtils.isBlank(hostname)) {
            Validation.addError(ftpsHostname.getName(), "configProperties.ftps.cannot.be.blank");
        }
    }

    public Property getEncrypt() {
        return encrypt;
    }

    public Property getTransport() {
        return transport;
    }

    public Property getFtpsHostname() {
        return ftpsHostname;
    }

    public Property getFtpsPort() {
        return ftpsPort;
    }

    public Property getSmtpEmcTo() {
        return smtpEmcTo;
    }

    public Property getSmtpTo() {
        return smtpTo;
    }

    public static Set<String> getAllProperties () {
        Set<String> properties = new HashSet<String>();
        properties.add("system_connectemc_encrypt");
        properties.add("system_connectemc_transport");
        properties.add("system_connectemc_ftps_hostname");
        properties.add("system_connectemc_ftps_port");
        properties.add("system_connectemc_smtp_emcto");
        properties.add("system_connectemc_smtp_to");

        return properties;
    }
}
