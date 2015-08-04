/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.winrm;

import org.apache.http.annotation.Immutable;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthSchemeFactory;
import org.apache.http.params.HttpParams;

@Immutable
public class CustomSPNegoSchemeFactory implements AuthSchemeFactory {
    public AuthScheme newInstance(final HttpParams params) {
        return new CustomSPNegoScheme();
    }
}
