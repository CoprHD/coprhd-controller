/*
 * Copyright (c) 2016 Dell EMC Software
 * All Rights Reserved
 */

package com.iwave.ext.windows.winrm;

import org.apache.http.annotation.Immutable;
import org.apache.http.auth.AuthScheme;
import org.apache.http.impl.auth.NTLMSchemeFactory;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;

@SuppressWarnings("deprecation")
@Immutable
public class CustomSPNegoSchemeFactory extends NTLMSchemeFactory {
    public AuthScheme newInstance(final HttpParams params) {
        return new CustomSPNegoScheme();
    }

	@Override
	public AuthScheme create(HttpContext context) {
		return new CustomSPNegoScheme();
	}
}
