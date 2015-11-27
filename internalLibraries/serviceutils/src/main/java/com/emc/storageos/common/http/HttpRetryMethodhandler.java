package com.emc.storageos.common.http;

import java.io.IOException;

import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpMethodRetryHandler;

public class HttpRetryMethodhandler implements HttpMethodRetryHandler {

    @Override
    public boolean retryMethod(HttpMethod method, IOException exception, int executionCount) {
        return false;
    }

}
