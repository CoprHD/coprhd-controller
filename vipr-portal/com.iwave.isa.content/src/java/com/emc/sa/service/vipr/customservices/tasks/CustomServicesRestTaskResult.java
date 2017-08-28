package com.emc.sa.service.vipr.customservices.tasks;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument.Output;


public class CustomServicesRestTaskResult extends CustomServicesTaskResult {


    public CustomServicesRestTaskResult(final List<Output> keys, final Set<Map.Entry<String, List<String>>> headers, final String out, final String err, final int retCode) {
        super(out, err, retCode, parseHeaders(keys, headers));
    }
    
    private static Map<String, List<String>> parseHeaders(final List<Output> keys, final Set<Map.Entry<String, List<String>>> headers) {
        final Map<String, List<String>> out = new HashMap<String, List<String>>();
        if( null != keys && null != headers ) {
            final List<String> outputNames = new ArrayList<String>();        
            for( final Output o : keys ) {
                outputNames.add(o.getName());
            }
            for (final Map.Entry<String, List<String>> entry : headers) {
                if(outputNames.contains(entry.getKey())){
                    out.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return out;
    }
 }

