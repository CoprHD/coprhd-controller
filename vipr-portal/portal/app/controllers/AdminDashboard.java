/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers;

import static util.BourneUtil.getSysClient;

import java.util.Date;
import java.util.Map;

import jobs.PoweroffJob;
import jobs.RebootNodeJob;

import org.joda.time.DateTime;

import play.Logger;
import play.data.validation.Required;
import play.i18n.Messages;
import play.libs.F.Promise;
import play.mvc.Controller;
import play.mvc.With;
import util.AdminDashboardUtils;
import util.LicenseUtils;
import util.MessagesUtils;

import com.emc.vipr.model.sys.licensing.License;
import com.emc.vipr.model.sys.recovery.DbRepairStatus;
import com.google.common.collect.Maps;

import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;

@With(Common.class)
public class AdminDashboard extends Controller {
    @Restrictions({ @Restrict("SYSTEM_MONITOR"), @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
    public static void dashboard() {
        render();
    }

    @Restrictions({ @Restrict("SYSTEM_MONITOR"), @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
    public static void version() {
        License license = AdminDashboardUtils.getLicense();
        Map<String, Promise<?>> promises = Maps.newHashMap();
        promises.put("clusterInfo", AdminDashboardUtils.clusterInfo());
        trySetRenderArgs(promises);
        // Add lastUpdated render args after promises are redeemed
        Date clusterInfoLastUpdated = AdminDashboardUtils.getClusterInfoLastUpdated();
        render(license, clusterInfoLastUpdated);
    }

    @Restrictions({ @Restrict("SYSTEM_MONITOR"), @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
    public static void health() {
        Map<String, Promise<?>> promises = Maps.newHashMap();
        promises.put("nodeHealthList", AdminDashboardUtils.nodeHealthList());
        promises.put("clusterInfo", AdminDashboardUtils.clusterInfo());
        trySetRenderArgs(promises);
        // Add lastUpdated render args after promises are redeemed
        renderArgs.put("nodeHealthListLastUpdated", AdminDashboardUtils.getNodeHealthListLastUpdated());
        render();
    }

    @Restrictions({ @Restrict("SYSTEM_MONITOR"), @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
    public static void dbStatus() {
        DbRepairStatus dbstatus = AdminDashboardUtils.gethealthdb();
        if (dbstatus.getLastCompletionTime() != null) {
            DateTime endTime = new DateTime(dbstatus.getLastCompletionTime().getTime());
            renderArgs.put("endTime", endTime);
        }
        if (dbstatus.getStartTime() != null) {
            DateTime startTime = new DateTime(dbstatus.getStartTime().getTime());
            renderArgs.put("startTime", startTime);
        }
        render(dbstatus);
    }

    @Restrictions({ @Restrict("SYSTEM_MONITOR"), @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
    public static void physicalAssets() {
        Map<String, Promise<?>> promises = Maps.newHashMap();
        promises.put("storageArrayCount", AdminDashboardUtils.storageArrayCount());
        promises.put("smisProviderCount", AdminDashboardUtils.storageProviderCount());
        promises.put("dataProtectionSystemCount", AdminDashboardUtils.dataProtectionSystemCount());
        promises.put("fabricManagerCount", AdminDashboardUtils.fabricManagerCount());
        promises.put("computeSystemCount", AdminDashboardUtils.computeSystemCount());
        promises.put("computeImageCount", AdminDashboardUtils.computeImageCount());
        promises.put("computeImageServerCount", AdminDashboardUtils.computeImageServerCount());
        promises.put("networksCount", AdminDashboardUtils.networksCount());
        promises.put("hostCount", AdminDashboardUtils.hostCount());
        promises.put("vcenterCount", AdminDashboardUtils.vCenterCount());
        promises.put("clusterCount", AdminDashboardUtils.clusterCount());

        trySetRenderArgs(promises);
        // Last updated must be set after evaluating promises
        renderArgs.put("storageArrayCountLastUpdated", AdminDashboardUtils.getStorageArrayCountLastUpdated());
        render();
    }

    @Restrictions({ @Restrict("SYSTEM_MONITOR"), @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
    public static void virtualAssets() {
        Map<String, Promise<?>> promises = Maps.newHashMap();
        promises.put("virtualStorageArrayCount", AdminDashboardUtils.virutalStorageArrayCount());
        if (LicenseUtils.isControllerLicensed()) {
            promises.put("blockVirtualPoolCount", AdminDashboardUtils.blockVirtualPoolCount());
            promises.put("fileVirtualPoolCount", AdminDashboardUtils.fileVirtualPoolCount());
            promises.put("objectVirtualPoolCount", AdminDashboardUtils.objectVirtualPoolCount());
            promises.put("computeVirtualPoolCount", AdminDashboardUtils.computeVirtualPoolCount());
        }

        trySetRenderArgs(promises);
        // Last updated must be set after evaluating promises
        renderArgs.put("virtualStorageArrayCountLastUpdated", AdminDashboardUtils.getVirtualStorageArrayCountLastUpdated());
        render();
    }

    @Restrictions({ @Restrict("SYSTEM_MONITOR"), @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
    public static void capacity() {
        Map<String, Promise<?>> promises = Maps.newHashMap();
        promises.put("storageStats", AdminDashboardUtils.storageStats());
        trySetRenderArgs(promises);
        render();
    }

    @Restrictions({ @Restrict("SECURITY_ADMIN"), @Restrict("RESTRICTED_SECURITY_ADMIN") })
    public static void nodeReboot(@Required String nodeId) {
        new RebootNodeJob(getSysClient(), nodeId).in(3);
        flash.success(Messages.get("adminDashboard.nodeRebooting", nodeId));
        Maintenance.maintenance(Common.reverseRoute(AdminDashboard.class, "dashboard"));
    }

    @Restrictions({ @Restrict("SECURITY_ADMIN"), @Restrict("RESTRICTED_SECURITY_ADMIN") })
    public static void clusterPoweroff() {
        new PoweroffJob(getSysClient()).in(3);
        flash.success(MessagesUtils.get("adminDashboard.clusterPoweroff.description"));
        flash("isShuttingDown", true);
        Maintenance.maintenance(Common.reverseRoute(AdminDashboard.class, "dashboard"));
    }

    /**
     * Tries to set a number of render arguments.
     * 
     * @param promises
     *            the map or key to promise.
     */
    private static void trySetRenderArgs(Map<String, Promise<?>> promises) {
        for (Map.Entry<String, Promise<?>> entry : promises.entrySet()) {
            trySetRenderArg(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Tries to set a render argument, ignoring any errors that may occur.
     * 
     * @param name
     *            the name of the render argument.
     * @param promise
     *            the promise to retrieve the value of the promise.
     */
    private static void trySetRenderArg(String name, Promise<?> promise) {
        try {
            Object value = await(promise);
            renderArgs.put(name, value);
        } catch (Exception e) {
            Throwable cause = Common.unwrap(e);
            String message = Common.getUserMessage(cause);
            renderArgs.put(name + "_error", message);
            Logger.warn(cause, "Could not set renderArg '%s'", name);
        }
    }
}
