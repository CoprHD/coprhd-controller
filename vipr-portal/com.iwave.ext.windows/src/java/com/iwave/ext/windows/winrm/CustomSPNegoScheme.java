/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.winrm;

import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.AppConfigurationEntry.LoginModuleControlFlag;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.auth.SPNegoScheme;
import org.apache.http.protocol.HttpContext;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;

import com.google.common.collect.Maps;

public class CustomSPNegoScheme extends SPNegoScheme {
    public static final String SPNEGO_OID = "1.3.6.1.5.5.2";

    private LoginContext loginContext;
    private GSSCredential credential;

    public CustomSPNegoScheme() {
        super(true);
    }

    protected void login(Credentials credentials) throws AuthenticationException {
        if (!(credentials instanceof UsernamePasswordCredentials)) {
            return;
        }

        try {
            Subject subject = null;
            CallbackHandler callback = new UsernamePasswordCallbackHandler(
                    (UsernamePasswordCredentials) credentials);
            Configuration configuration = new KerberosConfiguration();

            loginContext = new LoginContext("spnego", subject, callback, configuration);
            loginContext.login();
        } catch (LoginException e) {
            loginContext = null;
            // Log message only shows anthentication exception message, so use the message of the LoginException
            String message = (e.getMessage() != null) ? e.getMessage() : "Login failed";
            throw new AuthenticationException(message, e);
        }

        try {
            credential = createCredential(loginContext.getSubject());
        } catch (PrivilegedActionException e) {
            logout();
            throw new AuthenticationException("Failed to create credential", e);
        } catch (RuntimeException e) {
            logout();
            throw new AuthenticationException("Failed to create credential", e);
        }
    }

    protected GSSCredential createCredential(Subject subject) throws PrivilegedActionException {
        PrivilegedExceptionAction<GSSCredential> action = new PrivilegedExceptionAction<GSSCredential>() {
            public GSSCredential run() throws GSSException {
                return getManager().createCredential(null, GSSCredential.DEFAULT_LIFETIME,
                        new Oid(SPNEGO_OID), GSSCredential.INITIATE_ONLY);
            }
        };

        return Subject.doAs(subject, action);
    }

    protected void logout() {
        if (credential != null) {
            try {
                credential.dispose();
            } catch (GSSException e) {
            }
            credential = null;
        }
        if (loginContext != null) {
            try {
                loginContext.logout();
            } catch (LoginException e) {
            }
            loginContext = null;
        }
    }

    protected GSSCredential getCredential() {
        return credential;
    }

    @Override
    public Header authenticate(Credentials credentials, HttpRequest request, HttpContext context)
            throws AuthenticationException {
        login(credentials);
        try {
            return super.authenticate(credentials, request, context);
        } finally {
            logout();
        }
    }

    @Override
    protected byte[] generateGSSToken(final byte[] input, final Oid oid, final String authServer)
            throws GSSException {
        byte[] token = input;
        if (token == null) {
            token = new byte[0];
        }
        GSSManager manager = getManager();
        GSSName serverName = manager.createName("HTTP@" + authServer, GSSName.NT_HOSTBASED_SERVICE);
        GSSCredential credential = getCredential();
        GSSContext gssContext = manager.createContext(serverName.canonicalize(oid), oid,
                credential, GSSContext.DEFAULT_LIFETIME);
        try {
            gssContext.requestMutualAuth(true);
            gssContext.requestCredDeleg(true);
            return gssContext.initSecContext(token, 0, token.length);
        } finally {
            gssContext.dispose();
        }
    }

    /**
     * Callback handler for providing username and password from credentials.
     * 
     * @author jonnymiller
     */
    protected static class UsernamePasswordCallbackHandler implements CallbackHandler {
        private UsernamePasswordCredentials credentials;

        public UsernamePasswordCallbackHandler(UsernamePasswordCredentials credentials) {
            this.credentials = credentials;
        }

        @Override
        public void handle(final Callback[] callback) {
            for (int i = 0; i < callback.length; i++) {
                if (callback[i] instanceof NameCallback) {
                    final NameCallback nameCallback = (NameCallback) callback[i];
                    nameCallback.setName(credentials.getUserName());
                }
                else if (callback[i] instanceof PasswordCallback) {
                    final PasswordCallback passCallback = (PasswordCallback) callback[i];
                    passCallback.setPassword(credentials.getPassword().toCharArray());
                }
            }
        }
    }

    /**
     * Kerberos configuration.
     * 
     * @author jonnymiller
     */
    protected static class KerberosConfiguration extends Configuration {
        private static final String KERBEROS_LOGIN_MODULE = "com.sun.security.auth.module.Krb5LoginModule";

        @Override
        public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
            Map<String, String> options = Maps.newHashMap();
            options.put("client", "true");
            options.put("useTicketCache", "false");
            options.put("useKeyTab", "false");
            options.put("refreshKrb5Config", "true");
            options.put("debug", "false");
            return new AppConfigurationEntry[] { new AppConfigurationEntry(KERBEROS_LOGIN_MODULE,
                    LoginModuleControlFlag.REQUIRED, options) };
        }
    }
}
