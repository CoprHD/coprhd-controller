/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.properties;

import java.util.Map;

public class SecurityPropertyPage extends CustomPropertyPage {

    private Property ldapConnectionTimeout;
    private Property firewallEnabled;
    private Property sslCertificate;
    private Property rootSshKeys;
    private Property rootEncPassword;
    private Property svcuserSshKeys;
    private Property svcuserEncPassword;
    private Property proxyuserEncPassword;
    private Property sysmonitorEncPassword;
    private Property authLoginAttempts;
    private Property authLockoutTime;
    private Property tokenLifeTime;
    private Property tokenIdleTime;

    public SecurityPropertyPage(Map<String, Property> properties) {
        super("Security");
        setRenderTemplate("securityPage.html");
        ldapConnectionTimeout = addCustomProperty(properties, "ldap_connection_timeout");
        firewallEnabled = addCustomProperty(properties, "system_enable_firewall");
        sslCertificate = addCustomProperty(properties, "system_ssl_cert_pem");
        rootSshKeys = addCustomProperty(properties, "system_root_authorizedkeys2");
        rootEncPassword = addCustomProperty(properties, "system_root_encpassword");
        svcuserSshKeys = addCustomProperty(properties, "system_svcuser_authorizedkeys2");
        svcuserEncPassword = addCustomProperty(properties, "system_svcuser_encpassword");
        proxyuserEncPassword = addCustomProperty(properties, "system_proxyuser_encpassword");
        sysmonitorEncPassword = addCustomProperty(properties, "system_sysmonitor_encpassword");
        authLoginAttempts = addCustomProperty(properties, "max_auth_login_attempts");
        authLockoutTime = addCustomProperty(properties, "auth_lockout_time_in_minutes");
        tokenLifeTime = addCustomProperty(properties, "token_life_time_in_minutes");
        tokenIdleTime = addCustomProperty(properties, "token_idle_time_in_minutes");
    }

    public Property getLdapConnectionTimeout() {
        return ldapConnectionTimeout;
    }

    public Property getFirewallEnabled() {
        return firewallEnabled;
    }

    public Property getSslCertificate() {
        return sslCertificate;
    }

    public Property getRootSshKeys() {
        return rootSshKeys;
    }

    public Property getRootEncPassword() {
        return rootEncPassword;
    }

    public Property getSvcuserSshKeys() {
        return svcuserSshKeys;
    }

    public Property getSvcuserEncPassword() {
        return svcuserEncPassword;
    }

    public Property getProxyuserEncPassword() {
        return proxyuserEncPassword;
    }

    public Property getSysmonitorEncPassword() {
        return sysmonitorEncPassword;
    }

    public Property getAuthLoginAttempts() {
        return authLoginAttempts;
    }

    public Property getAuthLockoutTime() {
        return authLockoutTime;
    }

    public Property getTokenLifeTime() {
        return tokenLifeTime;
    }

    public Property getTokenIdleTime() {
        return tokenIdleTime;
    }
}
