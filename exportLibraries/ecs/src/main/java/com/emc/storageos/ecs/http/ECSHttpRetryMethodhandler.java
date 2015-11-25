package com.emc.storageos.ecs.http;

import java.io.IOException;

import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpMethodRetryHandler;

public class ECSHttpRetryMethodhandler implements HttpMethodRetryHandler {

    @Override
    public boolean retryMethod(HttpMethod method, IOException exception, int executionCount) {
        return false;
    }

}
