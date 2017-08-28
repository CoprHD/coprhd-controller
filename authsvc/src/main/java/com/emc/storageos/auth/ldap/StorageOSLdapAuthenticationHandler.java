/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.auth.ldap;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ldap.AuthenticationException;
import org.springframework.ldap.CommunicationException;
import org.springframework.ldap.InvalidNameException;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.NameClassPairCallbackHandler;
import org.springframework.ldap.core.SearchExecutor;

import com.emc.storageos.auth.impl.LdapFailureHandler;
import com.emc.storageos.auth.impl.LdapOrADServer;
import com.emc.storageos.auth.impl.LdapServerList;
import com.emc.storageos.auth.StorageOSAuthenticationHandler;
import com.emc.storageos.services.util.AlertsLogger;
import com.emc.storageos.svcs.errorhandling.resources.UnauthorizedException;

/**
 * Authentication handler for LDAP providers
 */
public class StorageOSLdapAuthenticationHandler implements
        StorageOSAuthenticationHandler {

    private Logger _log = LoggerFactory.getLogger(StorageOSLdapAuthenticationHandler.class);
    private AlertsLogger _alertLog = AlertsLogger.getAlertsLogger();
    private Set<String> _domains;
    private String _rawFilter;
    private String _searchBase;
    private int _scope = SearchControls.SUBTREE_SCOPE;
    private int _timeLimit = 1000;
    private long _countLimit = 1000;
    private LdapFailureHandler _failureHandler = new LdapFailureHandler();
    private LdapServerList _ldapServers;

    public StorageOSLdapAuthenticationHandler() {
        super();
    }

    /*
     * @see com.emc.storageos.auth.StorageOSAuthenticationHandler#authenticate(org.apache.commons.httpclient.Credentials)
     */
    @Override
    public boolean authenticate(final Credentials credentials) {
        UsernamePasswordCredentials usernamePasswordCredentials = (UsernamePasswordCredentials) credentials;
        if (null == usernamePasswordCredentials.getUserName()
                || usernamePasswordCredentials.getUserName().isEmpty()
                || null == usernamePasswordCredentials.getPassword()
                || usernamePasswordCredentials.getPassword().isEmpty()) {
            _log.error("Illegal credentials username or password cannot be null or empty");
            return false;
        }

        return doAuthentication(usernamePasswordCredentials);
    }

    /**
     * Do authentication to ad/ldap. Will try all context sources building from URLs, in input order.
     * If any one has connection failure, move that to the end and try next one until all.
     * @param credentials
     * @return
     */
    private boolean doAuthentication(UsernamePasswordCredentials credentials) {
        List<LdapOrADServer> connectedServers = _ldapServers.getConnectedServers();
        for (LdapOrADServer server : connectedServers) {
            try {
                return doAuthenticationOverSingleServer(server, credentials);
            } catch (CommunicationException e) { // Continue only if communicate issue happens on last server
                _failureHandler.handle(_ldapServers, server);
                _alertLog.error(MessageFormat.format("Connection to LDAP server {0} failed for domain(s) {1}. {2}",
                        Arrays.toString(server.getContextSource().getUrls()), _domains, e.getMessage()));
            }
        }

        throw UnauthorizedException.unauthorized.ldapCommunicationException();
    }

    private boolean doAuthenticationOverSingleServer(LdapOrADServer server, UsernamePasswordCredentials usernamePasswordCredentials) {
        _log.info("Do authentication to the server {}", server.getContextSource().getUrls()[0]);

        String password = usernamePasswordCredentials.getPassword();

        List<String> dns = new ArrayList<String>();
        final String filter = LdapFilterUtil.getPersonFilterWithValues(_rawFilter, usernamePasswordCredentials.getUserName());
        _log.debug("Filter for authentication is {}", filter);

        LdapTemplate ldapTemplate = new LdapTemplate(server.getContextSource());
        ldapTemplate.setIgnorePartialResultException(true); // To avoid the exceptions due to referrals returned
        try {
            ldapTemplate.search(new StorageOSSearchExecutor(filter), new StorageOSNameClassPairCallbackHandler(dns));
        } catch (CommunicationException e) {
            _log.warn("Connection to LDAP server {} failed", Arrays.toString(server.getContextSource().getUrls()));
            throw e;
        } catch (AuthenticationException e) {
            _alertLog
                    .error(MessageFormat
                            .format("Manager bind failed during search for user {0} in domain(s) {1}.  Check manager DN and password. {2}. "
                                            +
                                            "Note that any change to the manager DN username or password in the authentication provider must be manually changed in ViPR.",
                                    usernamePasswordCredentials.getUserName(), _domains, e.getMessage()));
            throw UnauthorizedException.unauthorized.managerBindFailed();
        } catch (InvalidNameException e) {
            _alertLog.error(MessageFormat.format(
                    "Search failed because the search path provided is syntactically invalid for user {0}. {1}",
                    usernamePasswordCredentials.getUserName(), e.getMessage()));
            throw UnauthorizedException.unauthorized.userSearchFailed();
        } catch (Exception e) {
            _alertLog.error(MessageFormat.format(
                    "Search or bind failed.  An exception was thrown while trying to authenticate user {0}. {1}",
                    usernamePasswordCredentials.getUserName(), e.getMessage()));
            throw UnauthorizedException.unauthorized.bindSearchGenericException();
        }
        if (dns.isEmpty()) {
            _log.info("Search for " + filter + " returned 0 results.");
            return false;
        }
        if (dns.size() > 1) {
            _log.warn("Search for " + filter + " returned multiple results, which is not allowed.");
            return false;
        }

        try {
            DirContext test = server.getContextSource().getContext(dns.get(0), password);
            if (test != null) {
                try {
                    test.close();
                } catch (NamingException e) {
                    _log.error("Failed to close test context", e);
                }
                _log.info("Authenticate user {} against server {} successfully", usernamePasswordCredentials.getUserName(), server.getContextSource().getUrls()[0]);
                return true;
            }
        } catch (AuthenticationException e) {
            _log.warn("Failed to authenticate user {}", usernamePasswordCredentials.getUserName());
            return false;
        } catch (CommunicationException e) {
            _alertLog.error(MessageFormat.format("Connection to LDAP server {0} failed for domain(s) {1}. {2}",
                    Arrays.toString(server.getContextSource().getUrls()), _domains, e.getMessage()));
            throw e;
        } catch (Exception e) {
            _alertLog.error(MessageFormat.format("Second bind failed.  An exception was thrown while trying to authenticate user {0}. {1}",
                    usernamePasswordCredentials.getUserName(), e.getMessage()));
            throw UnauthorizedException.unauthorized.bindSearchGenericException();
        }
        return false;
    }

    /*
     * @see com.emc.storageos.auth.StorageOSAuthenticationHandler#supports(org.apache.commons.httpclient.Credentials)
     */
    @Override
    public boolean supports(final Credentials credentials) {
        if (null != credentials && credentials.getClass().isAssignableFrom(UsernamePasswordCredentials.class)) {
            String username = ((UsernamePasswordCredentials) credentials).getUserName();
            if (null != username) {
                String[] usernameParts = username.split("@");
                return usernameParts.length > 1 && _domains.contains(usernameParts[1].toLowerCase());
            }
        }
        return false;
    }

    /**
     * @see com.emc.storageos.auth.StorageOSAuthenticationHandler#setFailureHandler(LdapFailureHandler)
     * @param failureHandler
     */
    @Override
    public void setFailureHandler(LdapFailureHandler failureHandler) {
        this._failureHandler = failureHandler;
    }

    private SearchControls getSearchControls() {
        SearchControls searchControls = new SearchControls();
        searchControls.setSearchScope(_scope);
        searchControls.setReturningAttributes(new String[0]);
        searchControls.setTimeLimit(_timeLimit);
        searchControls.setCountLimit(_countLimit);
        return searchControls;
    }

    public void setDomains(final Set<String> stringSet) {
        _domains = stringSet;
    }

    public Set<String> getDomains() {
        return _domains;
    }

    public void setFilter(final String filter) {
        _rawFilter = filter;
    }

    public void setSearchBase(final String searchBase) {
        _searchBase = searchBase;
    }

    public void setLdapServers(LdapServerList ldapServers) {
        _ldapServers = ldapServers;
    }

    public LdapServerList getLdapServers() {
        return _ldapServers;
    }

    private class StorageOSSearchExecutor implements SearchExecutor {
        private String _filter;

        public StorageOSSearchExecutor(String filter) {
            _filter = filter;
        }

        @Override
        public NamingEnumeration<SearchResult> executeSearch(DirContext context) throws NamingException {
            return context.search(_searchBase, _filter, getSearchControls());
        }
    }

    private class StorageOSNameClassPairCallbackHandler implements NameClassPairCallbackHandler {
        private List<String> _dns;

        public StorageOSNameClassPairCallbackHandler(List<String> dns) {
            super();
            _dns = dns;
        }

        @Override
        public void handleNameClassPair(NameClassPair nameClassPair) {
            _dns.add(nameClassPair.getNameInNamespace());
        }
    }
}
