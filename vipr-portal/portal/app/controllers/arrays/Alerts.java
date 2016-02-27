package controllers.arrays;

import java.util.List;

import models.datatable.AlertsDataTable;
import play.mvc.Controller;
import play.mvc.With;
import util.BourneUtil;
import util.datatable.DataTablesSupport;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.alerts.AlertsBulkResponse;
import com.emc.storageos.model.alerts.AlertsCreateResponse;
import com.google.common.collect.Lists;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;

@With(Common.class)
@Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
public class Alerts extends Controller {
    
    public static void list() {
        AlertsDataTable dataTable = new AlertsDataTable();
        render(dataTable);
    }
    
    public static void listJson() {
        List<AlertsDataTable.Alerts> alerts = Lists.newArrayList();
        BulkIdParam bulkIds = BourneUtil.getViprClient().remediation().getBulkAlerts();
        AlertsBulkResponse bulkResponse = BourneUtil.getViprClient().remediation().getBulkResponse(bulkIds);
        List<AlertsCreateResponse> alertRestRep = bulkResponse.getAlerts();
        for(AlertsCreateResponse resp : alertRestRep){
            AlertsDataTable.Alerts alert = new AlertsDataTable.Alerts(resp.getAffectedResourceID(), resp.getAffectedResourceName(),
                    resp.getProblemDescription(),resp.getDeviceType(),resp.getSeverity(),resp.getState());

            alerts.add(alert);
        }
        renderJSON(DataTablesSupport.createJSON(alerts, params));
    }

}
