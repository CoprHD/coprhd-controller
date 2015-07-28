/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers;

import static controllers.security.Security.isSecurityAdmin;
import static controllers.security.Security.isSystemAdminOrRestrictedSystemAdmin;
import static controllers.security.Security.isSystemAuditor;
import static controllers.security.Security.isSystemMonitor;
import static controllers.security.Security.isTenantAdmin;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import models.datatable.RecentOrdersDataTable;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.With;
import util.CatalogServiceUtils;
import util.datatable.DataTableParams;
import util.datatable.DataTablesSupport;

import com.emc.vipr.model.catalog.CatalogServiceRestRep;
import com.google.common.collect.Lists;

import controllers.auth.LDAPsources;
import controllers.catalog.Orders;
import controllers.catalog.ServiceCatalog;
import controllers.compute.Hosts;
import controllers.infra.AuditLog;
import controllers.security.Security;
import controllers.util.Models;

@With(Common.class)
public class Dashboard extends Controller {

    private static final int ORDER_FETCH_LIMIT = 5;

    public static void index() {

        if (isSystemAdminOrRestrictedSystemAdmin() || isSystemMonitor()) {
            AdminDashboard.dashboard();
        }
        else if (isSecurityAdmin()) {
            LDAPsources.list();
        }
        else if (isSystemAuditor()) {
            AuditLog.list();
        }
        else if (isTenantAdmin()) {
            Hosts.list();
        }

        recentActivity();
    }

    public static void recentActivity() {
        List<ServiceCatalog.ServiceDef> services = Lists.newArrayList();

        Http.Cookie recentCookie = request.cookies.get(Orders.RECENT_ACTIVITIES);

        List<String> ids = Lists.newArrayList();
        List<String> removedIds = Lists.newArrayList();

        if (recentCookie != null && recentCookie.value != null) {
            ids.addAll(Arrays.asList(recentCookie.value.split(",")));
            for (String serviceId : ids) {
                CatalogServiceRestRep service = CatalogServiceUtils.getCatalogService(uri(serviceId));
                if (service != null) {
                    services.add(ServiceCatalog.createService(service, ""));
                } else {
                    removedIds.add(serviceId);
                }
            }

            // handle services that has been deleted by removing them from the cookie
            if (!removedIds.isEmpty()) {
                ids.removeAll(removedIds);
                response.setCookie(Orders.RECENT_ACTIVITIES, StringUtils.join(ids, ","));
            }
        }

        DashboardOrdersDataTable dataTable = new DashboardOrdersDataTable();
        render(dataTable, services);
    }

    public static void listJson() {
        DataTableParams dataTableParams = DataTablesSupport.createParams(params);
        DashboardOrdersDataTable dataTable = new DashboardOrdersDataTable();
        renderJSON(DataTablesSupport.createJSON(dataTable.fetchData(dataTableParams), params));
    }

    public static class DashboardOrdersDataTable extends RecentOrdersDataTable {
        public DashboardOrdersDataTable() {
            super(Models.currentTenant());
            alterColumn("createdDate").setRenderFunction("render.relativeDate").setCssClass("createdRelativeDate");
            setMaxOrders(ORDER_FETCH_LIMIT);
            setUserInfo(Security.getUserInfo());
        }
    }
}