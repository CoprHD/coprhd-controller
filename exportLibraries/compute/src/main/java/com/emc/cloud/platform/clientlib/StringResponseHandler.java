/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.cloud.platform.clientlib;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;

class StringHttpResponseHandler implements ResponseHandler<String> {
	public String handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
		if (response.getStatusLine().getStatusCode() != 200) {
			throw new ClientHttpResponseException(response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase());
		}
		HttpEntity entity = response.getEntity();
		InputStream ins = null;
		if (entity != null)	 {
			ins = entity.getContent();
			if (ins == null) return null;
		}
		byte[] bytes = new byte[1024];
		int bytesRead = ins.read(bytes, 0, 1024);
		if (bytesRead < 0) return null;
		String s = new String(bytes);
		s = s.substring(0, bytesRead);
		return s;
	}
}
