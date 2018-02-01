package com.emc.sa.service.vipr.customservices.tasks;


import java.util.List;
import java.util.Map;
import java.util.Set;


public class CustomServicesRestTaskResult extends CustomServicesTaskResult {

    private final Set<Map.Entry<String, List<String>>> headers;

    public CustomServicesRestTaskResult(final Set<Map.Entry<String, List<String>>> headers, final String out, final String err, final int retCode) {
        super(out, err, retCode, null);
        this.headers = headers;
    }

    public Set<Map.Entry<String, List<String>>> getHeaders() {
        return headers;
    }
}

