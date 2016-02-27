package com.emc.storageos.model.alerts;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.BulkRestRep;

@XmlRootElement(name = "bulk_notifications")
public class AlertsBulkResponse extends BulkRestRep{

    private List<AlertsCreateResponse> alerts;

    @XmlElement(name="notification")
    public List<AlertsCreateResponse> getAlerts() {
        if(null == alerts){
            alerts = new ArrayList<AlertsCreateResponse>();
        }
        return alerts;
    }

    public void setAlerts(List<AlertsCreateResponse> alerts) {
        this.alerts = alerts;
    } 
}
