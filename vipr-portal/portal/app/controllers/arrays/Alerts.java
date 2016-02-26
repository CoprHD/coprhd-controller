package controllers.arrays;

import java.util.List;

import com.google.common.collect.Lists;

import models.datatable.AlertsDataTable;



import play.mvc.Controller;
import play.mvc.With;
import util.datatable.DataTablesSupport;
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
        AlertsDataTable.Alerts testAlert = new AlertsDataTable.Alerts("myId","testAlert","blah,blah,blah","Volume");
        AlertsDataTable.Alerts testAlert2 = new AlertsDataTable.Alerts("myId","testAlert2","blah,blah,blah","Volume");
        AlertsDataTable.Alerts testAlert3 = new AlertsDataTable.Alerts("myId","testAlert4","blah,blah,blah","Volume");
        alerts.add(testAlert);
        alerts.add(testAlert2);
        alerts.add(testAlert3);
        renderJSON(DataTablesSupport.createJSON(alerts, params));
    }

}
