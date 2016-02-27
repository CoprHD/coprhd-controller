package com.emc.vipr.client.core;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.alerts.AlertsBulkResponse;
import com.emc.storageos.model.alerts.AlertsCreateResponse;
import com.emc.vipr.client.impl.RestClient;

public class Remediation extends AbstractResources<AlertsCreateResponse>{

    protected final RestClient client;
    public Remediation(RestClient client) {
        super(client, AlertsCreateResponse.class, "/object/remidiation");
        this.client = client;
    }
    
    public BulkIdParam getBulkAlerts() {
        return client.get(BulkIdParam.class, "/object/remidiation", "");
    }
    
    public AlertsBulkResponse getBulkResponse(BulkIdParam bulkIds){
        return client.post(AlertsBulkResponse.class, "/object/remidiation/bulk", bulkIds);
    }
    

}
