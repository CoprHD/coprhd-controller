/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers;

import com.emc.storageos.security.password.Constants;
import com.emc.storageos.systemservices.impl.validate.PropertiesConfigurationValidator;
import com.emc.vipr.client.exceptions.ServiceErrorException;
import com.emc.vipr.client.exceptions.ViPRException;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.net.InetAddresses;

import controllers.deadbolt.Deadbolt;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.infra.ConfigProperties;
import controllers.security.Security;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

import play.Logger;
import play.Play;
import play.data.validation.MinSize;
import play.data.validation.Required;
import play.data.validation.Valid;
import play.data.validation.Validation;
import play.mvc.Catch;
import play.mvc.Controller;
import play.mvc.Util;
import play.mvc.With;
import util.*;
import util.validation.HostNameOrIpAddressCheck;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static controllers.Common.flashException;

@With(Deadbolt.class)
public class Setup extends Controller {
    private static boolean isComplete = false;

    @Catch(ServiceErrorException.class)
    public static void jerseyException(Throwable e) {
        flashException(e);
        protectPasswords();
        params.flash();

        if (StringUtils.equals(request.actionMethod, "upload")) {
            license();
        }
        else {
            index();
        }
    }

    /**
     * Determines if initial setup has been completed.
     * 
     * @return true if initial setup is complete.
     */
    public static boolean isInitialSetupComplete() {
        if (isComplete) {
            return true;
        }
        isComplete = SetupUtils.isSetupComplete();
        return isComplete;
    }

    /**
     * Determines if the product is licensed.
     * 
     * @return true if the product is licensed.
     */
    public static boolean isLicensed() {
        if (SetupUtils.isOssBuild()) {
            return true;
        }
        return LicenseUtils.isLicensed(false);
    }

    /**
     * Checks to see if setup is complete, and it not locks out non-admin users by redirecting to a static page instead
     * of showing a forbidden error.
     */
    private static void checkCompleteAndLicensed() {
        // If setup is complete, redirect
        if (isInitialSetupComplete() && isLicensed()) {
            complete();
        }
        // If this user does not have the right roles, redirect to a static page
        if (Security.isSystemAdminOrRestrictedSystemAdmin() == false &&
                Security.isSecurityAdminOrRestrictedSecurityAdmin() == false) {
            notLicensed();
        }
        if (!Common.isClusterStable()) {
            Maintenance.maintenance(request.url);
        }
    }

    /**
     * Redirects to the admin dashboard if setup is complete.
     */
    private static void complete() {
        Dashboard.index();
    }

    /**
     * Renders a page showing that the product is not licensed. This will be shown to non-admin users if setup has not
     * been completed.
     */
    public static void notLicensed() {
        render();
    }

    /**
     * Displays the initial setup screen. If initial setup is already complete, this will forward to the license page.
     */
    public static void index() {
        checkCompleteAndLicensed();
        if (!isLicensed()) {
            license();
        }

        SetupForm setup = new SetupForm();
        setup.loadDefaults();
        render(setup);
    }

    /**
     * Saves the initial setup form. This is restricted to admin users.
     * 
     * @param setup
     *            the initial setup form.
     */
    @Restrictions({ @Restrict({ "SYSTEM_ADMIN", "SECURITY_ADMIN" }), @Restrict({ "RESTRICTED_SYSTEM_ADMIN", "RESTRICTED_SECURITY_ADMIN" }) })
    public static
            void save(@Valid SetupForm setup) {
        checkCompleteAndLicensed();
        setup.validate();
        if (Validation.hasErrors()) {
            protectPasswords();
            params.flash();
            Validation.keep();
            index();
        }

        Map<String, String> properties = getUpdatedProperties(setup);
        completeInitialSetup(properties);
    }

    /**
     * Completes initial setup with the given configuration properties.
     * 
     * @param properties
     *            the properties to update.
     */
    private static void completeInitialSetup(Map<String, String> properties) {
        SetupUtils.markSetupComplete();
        ConfigPropertyUtils.rotateIpsecKey(BourneUtil.getSysClient());
        ConfigPropertyUtils.saveProperties(BourneUtil.getSysClient(), properties);
        complete();
    }

    /**
     * Skips initial setup, advancing to the license screen. This is only available in DEV mode.
     */
    public static void skip() {
        if (!Play.mode.isDev()) {
            forbidden();
        }
        checkCompleteAndLicensed();
        SetupUtils.markSetupComplete();
        license();
    }

    public static String getPasswordValidPromptRule() {
        String promptString = PasswordUtil.getPasswordValidPromptRules(Constants.PASSWORD_VALID_PROMPT);
        return promptString;
    }

    /**
     * Validate passwords dynamically from the initial setup form, rendering the result as JSON.
     * 
     * @param setup
     *            the initial setup form.
     */
    @Restrictions({ @Restrict({ "SECURITY_ADMIN" }), @Restrict({ "RESTRICTED_SECURITY_ADMIN" }) })
    public static void validatePasswordDynamic(String password, String fieldName) {
        boolean passed = true;
        if (fieldName.contains("root")) {
            fieldName = "setup.rootPassword";
        }
        if (fieldName.contains("system")) {
            fieldName = "setup.systemPasswords";
        }
        if (PasswordUtil.isNotValid(password)) {
            Validation.addError(fieldName + ".value", "setup.password.notValid");
            passed = false;
        }
        if (passed) {
            String validation = PasswordUtil.validatePassword(password);
            if (StringUtils.isNotBlank(validation)) {
                Validation.addError(fieldName + ".value", validation);
            }
        }

        if (Validation.hasErrors()) {
            renderJSON(ValidationResponse.collectErrors());
        }
        else {
            renderJSON(ValidationResponse.valid());
        }
    }

    /**
     * Validate passwords from the initial setup form, rendering the result as JSON.
     * 
     * @param setup
     *            the initial setup form.
     */
    @Restrictions({ @Restrict({ "SYSTEM_ADMIN", "SECURITY_ADMIN" }), @Restrict({ "RESTRICTED_SYSTEM_ADMIN", "RESTRICTED_SECURITY_ADMIN" }) })
    public static
            void validatePasswords(SetupForm setup) {
        setup.validatePasswords();
        if (Validation.hasErrors()) {
            renderJSON(ValidationResponse.collectErrors());
        }
        else {
            renderJSON(ValidationResponse.valid());
        }
    }

    /**
     * Tests the SMTP settings from the initial setup form, rendering the result as JSON.
     * 
     * @param setup
     *            the initial setup form.
     */
    @Restrictions({ @Restrict({ "SYSTEM_ADMIN", "SECURITY_ADMIN" }), @Restrict({ "RESTRICTED_SYSTEM_ADMIN", "RESTRICTED_SECURITY_ADMIN" }) })
    public static
            void testSmtpSettings(SetupForm setup) {
        setup.validateSmtp();
        Validation.required("setup.smtpTo", setup.smtpTo);
        Validation.email("setup.smtpTo", setup.smtpTo);
        if (Validation.hasErrors()) {
            renderJSON(ValidationResponse.collectErrors());
        }

        MailSettingsValidator.Settings settings = new MailSettingsValidator.Settings();

        if (StringUtils.isNotEmpty(setup.nameservers) && !InetAddresses.isInetAddress(setup.smtpServer)) {
            Set<String> ips = Sets.newHashSet(setup.nameservers.split(","));

            try {
                settings.server = DnsUtils.getHostIpAddress(ips, setup.smtpServer).getHostAddress();
            } catch (ViPRException e) {
                renderJSON(ValidationResponse.invalid(e.getMessage()));
            }
        }
        else {
            settings.server = setup.smtpServer;
        }
        settings.port = ConfigProperties.getPort(setup.smtpPort, setup.smtpEnableTls);
        settings.username = setup.smtpUsername;
        settings.password = PasswordUtil.decryptedValue(setup.smtpPassword);
        settings.channel = StringUtils.equals("yes", setup.smtpEnableTls) ? "starttls" : "clear";
        settings.authType = setup.smtpAuthType;
        settings.fromAddress = setup.smtpFrom;

        try {
            MailSettingsValidator.validate(settings, setup.smtpTo);
        } catch (RuntimeException e) {
            Logger.error(e, "Failed to send email");
            Validation.addError(null, "setup.testEmail.failure", e.getMessage());
            if (StringUtils.isEmpty(setup.nameservers)) {
                Validation.addError(null, "setup.smtpServer.invalidEmptyNameserver");
            }
        }

        if (Validation.hasErrors()) {
            renderJSON(ValidationResponse.collectErrors());
        }
        else {
            renderJSON(ValidationResponse.valid(MessagesUtils.get("setup.testEmail.success")));
        }
    }

    /**
     * Displays the restarting screen.
     */
    @Util
    private static void restarting() {
        flash.success(MessagesUtils.get("setup.waitStable.description"));
        Maintenance.maintenance(Common.reverseRoute(Setup.class, "index"));
    }

    /**
     * Displays the license upload screen.
     */
    public static void license() {
        checkCompleteAndLicensed();
        if (Common.isClusterStable()) {
            render();
        }
        else {
            restarting();
        }
    }

    /**
     * Renders the cluster state as JSON.
     */
    @Restrictions({ @Restrict({ "SYSTEM_ADMIN", "SECURITY_ADMIN" }), @Restrict({ "RESTRICTED_SYSTEM_ADMIN", "RESTRICTED_SECURITY_ADMIN" }) })
    public static
            void clusterState() {
        renderJSON(Common.getClusterInfo());
    }

    /**
     * Uploads a license file. This is restricted to admin users.
     * 
     * @param licenseFile
     *            the license file.
     */
    @Restrictions({ @Restrict({ "SYSTEM_ADMIN", "SECURITY_ADMIN" }), @Restrict({ "RESTRICTED_SYSTEM_ADMIN", "RESTRICTED_SECURITY_ADMIN" }) })
    public static
            void upload(@Required File licenseFile) {
        if (Validation.hasErrors()) {
            params.flash();
            Validation.keep();
            license();
        }
        try {
            String license = FileUtils.readFileToString(licenseFile);
            if (StringUtils.isBlank(license)) {
                Logger.error("License file is empty");
                Validation.addError("setup.licenseFile", MessagesUtils.get("license.uploadFailed"));
                params.flash();
                Validation.keep();
                license();
            }
            LicenseUtils.updateLicenseText(license);
            index();
        } catch (IOException e) {
            Validation.addError("setup.licenseFile", MessagesUtils.get("license.uploadFailed"));
            Logger.error(e, "Failed to read license file");
            Validation.keep();
            license();
        }
    }

    private static Map<String, String> getProperties() {
        Map<String, String> properties = (Map<String, String>) request.args.get("properties");
        if (properties == null) {
            properties = ConfigPropertyUtils.getPropertiesFromCoordinator();
            request.args.put("properties", properties);
        }
        return properties;
    }

    private static Map<String, String> getUpdatedProperties(SetupForm setup) {
        Map<String, String> properties = Maps.newHashMap();
        // Network
        properties.put(ConfigProperty.NAMESERVERS, setup.nameservers);
        properties.put(ConfigProperty.NTPSERVERS, setup.ntpservers);

        // Passwords
        properties.put(ConfigProperty.ROOT_PASSWORD, setup.rootPassword.hashedValue());
        // Use the same password for proxyuser, svcuser, sysmonitor
        properties.put(ConfigProperty.PROXYUSER_PASSWORD, PasswordUtil.decryptedValue(setup.systemPasswords.value));
        properties.put(ConfigProperty.SVCUSER_PASSWORD, setup.systemPasswords.hashedValue());
        properties.put(ConfigProperty.SYSMONITOR_PASSWORD, setup.systemPasswords.hashedValue());

        // SMTP settings
        if (StringUtils.isNotBlank(setup.smtpServer)) {
            properties.put(ConfigProperty.SMTP_SERVER, setup.smtpServer);
            properties.put(ConfigProperty.SMTP_ENABLE_TLS, setup.smtpEnableTls);
            properties.put(ConfigProperty.SMTP_FROM_ADDRESS, setup.smtpFrom);
            properties.put(ConfigProperty.SMTP_AUTH_TYPE, setup.smtpAuthType);

            if (!StringUtils.equalsIgnoreCase(setup.smtpAuthType, "None")) {
                properties.put(ConfigProperty.SMTP_USERNAME, setup.smtpUsername);
                properties.put(ConfigProperty.SMTP_PASSWORD, PasswordUtil.decryptedValue(setup.smtpPassword));
            }
        }

        if (!SetupUtils.isOssBuild()) {
            // ConnectEMC settings
            properties.put(ConfigProperty.CONNECTEMC_TRANSPORT, setup.connectEmcTransport);
            if (!StringUtils.equalsIgnoreCase(setup.connectEmcTransport, "None")) {
                properties.put(ConfigProperty.CONNECTEMC_NOTIFY_EMAIL, setup.connectEmcNotifyEmail);
            }
        }
        return properties;
    }

    /**
     * Protects any passwords on the page by blanking them in case of an error, or encrypting them so they can be
     * redisplayed so a user doesn't have to retype them.
     */
    private static void protectPasswords() {
        String[] fields = { "setup.rootPassword", "setup.systemPasswords" };
        for (String field : fields) {
            protectPassword(field);
        }

        protectField("setup.smtpPassword");
    }

    /**
     * Protects a password field by erasing it if there are validation errors, or encrypting it if there aren't.
     * 
     * @param field
     *            the base password field name.
     */
    private static void protectPassword(String field) {
        String passwordFieldName = field + ".value";
        String confirmFieldName = field + ".confirm";

        if (Validation.hasError(passwordFieldName) || Validation.hasError(confirmFieldName)) {
            params.remove(passwordFieldName);
            params.remove(confirmFieldName);
        }
        else {
            protectField(passwordFieldName);
            protectField(confirmFieldName);
        }
    }

    private static void protectField(String field) {
        String value = params.get(field);
        if (Validation.hasError(field)) {
            params.remove(field);
        }
        else if (StringUtils.isNotBlank(value)) {
            params.put(field, PasswordUtil.encryptedValue(value));
        }
    }

    public static class Password {
        @Required
        @MinSize(8)
        public String value;
        @Required
        @MinSize(8)
        public String confirm;

        public String hashedValue() {
            String decrypted = PasswordUtil.decryptedValue(value);
            return PasswordUtil.generateHash(decrypted);
        }

        public void validate(String fieldName) {

            // If local validations have passed, validate against remote api
            if (localValidation(fieldName)) {
                remoteValidation(fieldName);
            }

        }

        private boolean localValidation(String fieldName) {
            boolean passed = true;

            if (PasswordUtil.isNotValid(value)) {
                Validation.addError(fieldName + ".value", "setup.password.notValid");
                passed = false;
            }
            if (PasswordUtil.isNotValid(confirm)) {
                Validation.addError(fieldName + ".confirm", "setup.password.notValid");
                passed = false;
            }

            value = PasswordUtil.decryptedValue(value);
            confirm = PasswordUtil.decryptedValue(confirm);
            Validation.valid(fieldName, this);
            if (!StringUtils.equals(value, confirm)) {
                Validation.addError(fieldName + ".value", "setup.password.notEqual");
                passed = false;
            }

            return passed;
        }

        private void remoteValidation(String fieldName) {
            String validation = PasswordUtil.validatePassword(value);
            if (StringUtils.isNotBlank(validation)) {
                Validation.addError(fieldName + ".value", validation);
            }
        }
    }

    public static class SetupForm {
        /* Network Page */
        @Required
        public String nameservers;
        @Required
        public String ntpservers;

        /* Passwords page */
        public Password rootPassword = new Password();
        public Password systemPasswords = new Password();

        /* SMTP page */
        public String smtpServer;
        public String smtpPort;
        public String smtpAuthType;
        public String smtpFrom;
        public String smtpUsername;
        public String smtpPassword;
        public String smtpEnableTls;
        // For test email settings
        public String smtpTo;

        public String fieldName;

        /* ConnectEMC page */
        public String connectEmcTransport;
        public String connectEmcNotifyEmail;

        public void loadDefaults() {
            Map<String, String> properties = getProperties();

            nameservers = properties.get(ConfigProperty.NAMESERVERS);
            ntpservers = properties.get(ConfigProperty.NTPSERVERS);

            smtpServer = properties.get(ConfigProperty.SMTP_SERVER);
            smtpPort = properties.get(ConfigProperty.SMTP_PORT);
            smtpAuthType = properties.get(ConfigProperty.SMTP_AUTH_TYPE);
            smtpUsername = properties.get(ConfigProperty.SMTP_USERNAME);
            smtpEnableTls = properties.get(ConfigProperty.SMTP_ENABLE_TLS);
            smtpFrom = properties.get(ConfigProperty.SMTP_FROM_ADDRESS);

            connectEmcTransport = properties.get(ConfigProperty.CONNECTEMC_TRANSPORT);
            connectEmcNotifyEmail = properties.get(ConfigProperty.CONNECTEMC_NOTIFY_EMAIL);

        }

        public void validate() {
            if (!PropertiesConfigurationValidator.validateIpList(nameservers)) {
                Validation.addError("setup.nameservers", "configProperties.error.iplist");
            }
            if (!PropertiesConfigurationValidator.validateIpList(ntpservers)) {
                Validation.addError("setup.ntpservers", "configProperties.error.iplist");
            }

            validatePasswords();

            if (StringUtils.isNotBlank(smtpServer) || StringUtils.equalsIgnoreCase(connectEmcTransport, "SMTP")) {
                validateSmtp();
            }

            if (!SetupUtils.isOssBuild()) {
                Validation.required("setup.connectEmcTransport", connectEmcTransport);

                if (!StringUtils.equalsIgnoreCase(connectEmcTransport, "None")) {
                    Validation.required("setup.connectEmcNotifyEmail", connectEmcNotifyEmail);
                    Validation.email("setup.connectEmcNotifyEmail", connectEmcNotifyEmail);
                }
            }
        }

        public void validatePasswords() {
            rootPassword.validate("setup.rootPassword");
            systemPasswords.validate("setup.systemPasswords");
        }

        public void validateSmtp() {
            Validation.required("setup.smtpServer", smtpServer);

            if (HostNameOrIpAddressCheck.isValidHostName(smtpServer)) {
                if (PropertiesConfigurationValidator.validateIpList(nameservers)) {
                    Set<String> ips = Sets.newHashSet(nameservers.split(","));
                    if (!DnsUtils.validateHostname(ips, smtpServer)) {
                        Validation.addError("setup.smtpServer", "setup.smtpServer.invalidSmtpServer");
                    }
                }
                else if (StringUtils.isNotEmpty(nameservers)) {
                    Validation.addError("setup.nameservers", "setup.smtpServer.invalidNameserver", nameservers);
                }
            }

            if (!HostNameOrIpAddressCheck.isValidHostNameOrIp(smtpServer)) {
                Validation.addError("setup.smtpServer", "setup.smtpServer.invalid");
            }
            if (!StringUtils.isNumeric(smtpPort)) {
                Validation.addError("setup.smtpServer", "setup.smtpServer.invalidPort");
            }

            Validation.required("setup.smtpFrom", smtpFrom);
            Validation.email("setup.smtpFrom", smtpFrom);

            if (StringUtils.isNotBlank(smtpAuthType) && !StringUtils.equalsIgnoreCase(smtpAuthType, "None")) {
                Validation.required("setup.smtpUsername", smtpUsername);
                Validation.required("setup.smtpPassword", smtpPassword);

                if (PasswordUtil.isNotValid(smtpPassword)) {
                    Validation.addError("setup.smtpPassword", "setup.password.notValid");
                }
            }
        }
    }

}
