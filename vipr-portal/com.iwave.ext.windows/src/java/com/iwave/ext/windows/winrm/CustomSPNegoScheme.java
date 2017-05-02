/*
 * Copyright (c) 2016 Dell EMC Software
 * All Rights Reserved
 */

package com.iwave.ext.windows.winrm;

import java.util.Locale;

import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.Credentials;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.impl.auth.NTLMScheme;
import org.apache.http.message.BasicHeader;

public class CustomSPNegoScheme extends NTLMScheme {
    @Override
    public String getSchemeName() {
        return AuthSchemes.SPNEGO;
    }

    @Override
    public Header authenticate(Credentials credentials, HttpRequest request) throws AuthenticationException {
        Header header = super.authenticate(credentials, request);
        // org.apache.http.impl.auth.NTLMScheme.authenticate(Credentials, HttpRequest) doesn't use
        // com.watch4net.apg.NtlmSPNEGOScheme.getSchemeName(), so we have to do it ourselves...
        return new BasicHeader(header.getName(), header.getValue().replace(AuthSchemes.NTLM,
                getSchemeName().toUpperCase(Locale.ENGLISH)));
    }
}
