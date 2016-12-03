package com.emc.storageos.volumecontroller.impl.plugins;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServiceOptions {
    
    private Map<String, Object> serviceParameters ;
    
    public  enum serviceParameters {
        EXPORTMASKS;
    }
    
    

    public Map<String, Object> getServiceParameters() {
        return serviceParameters;
    }

    public void setServiceParameters(Map<String, Object> serviceParameters) {
        this.serviceParameters = serviceParameters;
    }
    
    public void addServiceParameter(String serviceParamer, Object value) {
        if (null == serviceParameters) {
            serviceParameters = new HashMap<String, Object>();
        }
        serviceParameters.put(serviceParamer, value);
    }
    
}
