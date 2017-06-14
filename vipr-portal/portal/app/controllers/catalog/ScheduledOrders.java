/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.catalog;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static controllers.Common.angularRenderArgs;
import static controllers.Common.backToReferrer;
import static util.BourneUtil.getCatalogClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import models.datatable.ScheduledOrdersDataTable;
import models.datatable.ScheduledOrdersDataTable.ScheduledOrderInfo;

import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import play.Logger;
import play.data.binding.As;
import play.data.validation.Validation;
import play.mvc.Controller;
import play.mvc.Util;
import play.mvc.With;
import util.CatalogServiceUtils;
import util.ExecutionWindowUtils;
import util.MessagesUtils;
import util.OrderUtils;
import util.TimeUtils;
import util.datatable.DataTableParams;
import util.datatable.DataTablesSupport;

import com.emc.vipr.model.catalog.ExecutionWindowRestRep;
import com.emc.vipr.model.catalog.OrderRestRep;
import com.emc.vipr.model.catalog.ScheduleCycleType;
import com.emc.vipr.model.catalog.ScheduleInfo;
import com.emc.vipr.model.catalog.ScheduledEventUpdateParam;
import com.google.common.collect.Lists;

import controllers.Common;
import controllers.catalog.Orders.OrderDetails;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.tenant.TenantSelector;
import controllers.util.FlashException;
import controllers.util.Models;

@With(Common.class)
@Restrictions({ @Restrict("TENANT_ADMIN") })
public class ScheduledOrders extends Controller {
    protected static final String SAVED = "ScheduledOrder.save.success";
    protected static final String CANCELLED = "ScheduledOrder.cancel.success";
    protected static final String DEACTIVATED = "ScheduledOrder.deactivate.success";
    
    @Util
    public static void addNextExecutionWindow() {
        Calendar now = Calendar.getInstance();
        ExecutionWindowRestRep nextWindow = ExecutionWindowUtils.getNextExecutionWindow(now);
        if (nextWindow != null) {
            Calendar nextWindowTime = ExecutionWindowUtils.calculateNextWindowTime(now, nextWindow);
            renderArgs.put("nextWindowName", nextWindow.getName());
            renderArgs.put("nextWindowTime", nextWindowTime.getTime());
        }
    }

    public static void list() {
        addNextExecutionWindow();
        renderArgs.put("dataTable", new ScheduledOrdersDataTable());
        TenantSelector.addRenderArgs();
        render();
    }

    public static void listJson() {
        DataTableParams dataTableParams = DataTablesSupport.createParams(params);
        ScheduledOrdersDataTable dataTable = new ScheduledOrdersDataTable();
        renderJSON(DataTablesSupport.createJSON(dataTable.fetchData(dataTableParams), params));
    }

    public static void itemsJson(@As(",") String[] ids) {
        List<ScheduledOrderInfo> results = Lists.newArrayList();
        if (ids != null && ids.length > 0) {
            for (String id : ids) {
                if (StringUtils.isNotBlank(id)) {
                    OrderRestRep order = OrderUtils.getOrder(uri(id));
                    if (order != null) {
                        Models.checkAccess(order.getTenant());
                        results.add(new ScheduledOrderInfo(order));
                    }
                }
            }
        }
        renderJSON(DataTablesSupport.toJson(results));
    }

    public static void cancel(@As(",") String[] ids) {
        if ((ids != null) && (ids.length > 0)) {
            Logger.info("Cancel: " + StringUtils.join(ids, ", "));

            for (String orderId : ids) {
                if (StringUtils.isNotBlank(orderId)) {
                    OrderRestRep order = OrderUtils.getOrder(uri(orderId));
                    URI scheduledEventId = order.getScheduledEventId();
                    if (scheduledEventId == null) {
                        OrderUtils.cancelOrder(uri(orderId));
                    } else {
                        getCatalogClient().orders().cancelScheduledEvent(scheduledEventId);
                    }
                }
            }
        }
        list();
    }

    public static void showOrder(String orderId) {
        redirect("Orders.receipt", orderId);
    }
    
    @FlashException("list")
    public static void edit(String id) {
        OrderDetails details = new OrderDetails(id);
        details.catalogService = CatalogServiceUtils.getCatalogService(details.order.getCatalogService());
        
        ScheduleEventForm scheduleEventForm = new ScheduleEventForm(details);
        angularRenderArgs().put("scheduler", scheduleEventForm);
        render(scheduleEventForm, details);
    }
    
    @FlashException(keep = true, referrer = { "edit" })
    public static void save(ScheduleEventForm scheduler) {
        scheduler.validate("scheduler");
        if (Validation.hasErrors()) {
            Common.handleError();
        }
        
        scheduler.save();
        flash.success(MessagesUtils.get(SAVED));
        backToReferrer();
        list();
    }
    
    @FlashException(keep = true)
    public static void deactivate(String id) {
        getCatalogClient().orders().deactivateScheduledEvent(uri(id));
        flash.success(MessagesUtils.get(DEACTIVATED));
        backToReferrer();
        list();
    }
    
    public static class ScheduleEventForm {
        public String id;
        public String name;
        public String startDate;
        public String startTime;
        public Integer recurrence;
        public Integer rangeOfRecurrence;
        public String cycleType;
        public Integer cycleFrequency;
        public Integer dayOfMonth;
        public Integer dayOfWeek;
        public Boolean recurringAllowed;
        public Integer maxNumOfCopies;
        public Integer currentTimezoneOffsetInMins;
        
        public ScheduleEventForm(OrderDetails details) {
            recurringAllowed = details.catalogService.isRecurringAllowed();
            if (details.order.getScheduledEventId() != null) {
                id = details.getScheduledEvent().getId().toString();
                ScheduleInfo schedulerInfo = details.getScheduledEvent().getScheduleInfo();
                startDate = schedulerInfo.getStartDate();
                startTime = String.format("%02d:%02d", schedulerInfo.getHourOfDay(), schedulerInfo.getMinuteOfHour());
                recurrence = schedulerInfo.getReoccurrence();
                if (recurrence > 1) {
                    rangeOfRecurrence = recurrence;
                    recurrence = -1;
                } else {
                    rangeOfRecurrence = 10;
                }
                
                cycleType = schedulerInfo.getCycleType().toString();
                cycleFrequency = schedulerInfo.getCycleFrequency();
                if (schedulerInfo.getCycleType() == ScheduleCycleType.MONTHLY) {
                    dayOfMonth = Integer.parseInt(schedulerInfo.getSectionsInCycle().get(0));
                    dayOfWeek = 1;
                } else if (schedulerInfo.getCycleType() == ScheduleCycleType.WEEKLY) {
                    dayOfWeek = Integer.parseInt(schedulerInfo.getSectionsInCycle().get(0));
                    dayOfMonth = 1;
                } else {
                    dayOfMonth = 1;
                    dayOfWeek = 1;
                }
                String additionalScheduleInfo = details.scheduledEvent.getOrderCreateParam().getAdditionalScheduleInfo();
                if (additionalScheduleInfo != null) {
                    maxNumOfCopies = Integer.parseInt(additionalScheduleInfo);
                }
            }
        }
        public ScheduleEventForm() {
            
        }
        public void validate(String fieldName) {
            Validation.valid(fieldName, this);
        }
        
        public void save() {
            ScheduledEventUpdateParam update = new ScheduledEventUpdateParam();
            // See ScheduleInfo know detailed information of each fields and
            // the expected default values
            ScheduleInfo scheduleInfo = new ScheduleInfo();
            if (cycleFrequency != null) {
                scheduleInfo.setCycleFrequency(cycleFrequency);
            } else {
                scheduleInfo.setCycleFrequency(1);
            }
            if (cycleType != null) {
                ScheduleCycleType cycleTypeEnum = ScheduleCycleType.valueOf(cycleType);
                scheduleInfo.setCycleType(cycleTypeEnum);
                List<String> sectionsInCycle = new ArrayList<String>();
                if (cycleTypeEnum == ScheduleCycleType.WEEKLY) {
                    sectionsInCycle.add(String.valueOf(dayOfWeek));
                } else if(cycleTypeEnum == ScheduleCycleType.MONTHLY) {
                    sectionsInCycle.add(String.valueOf(dayOfMonth));
                }
                scheduleInfo.setSectionsInCycle(sectionsInCycle);
            } else {
                scheduleInfo.setCycleType(ScheduleCycleType.DAILY);
            }
            
            String isoDateTimeStr = String.format("%sT%s", startDate, startTime);
            DateTimeFormatter format = ISODateTimeFormat.localDateOptionalTimeParser().withZone(TimeUtils.getLocalTimeZone(currentTimezoneOffsetInMins));
            DateTime startDateTime = DateTime.parse(isoDateTimeStr, format);
            startDateTime = startDateTime.withZone(DateTimeZone.UTC);
            scheduleInfo.setHourOfDay(startDateTime.getHourOfDay());
            scheduleInfo.setMinuteOfHour(startDateTime.getMinuteOfHour());
            scheduleInfo.setStartDate(String.format("%d-%02d-%02d", startDateTime.getYear(), startDateTime.getMonthOfYear(), startDateTime.getDayOfMonth()));

            if (recurrence != null) {
                if (recurrence == -1) {
                    recurrence = rangeOfRecurrence;
                }
                scheduleInfo.setReoccurrence(recurrence);
            } else {
                scheduleInfo.setReoccurrence(1);
            }

            scheduleInfo.setDurationLength(3600);
            update.setScheduleInfo(scheduleInfo);
            if (maxNumOfCopies != null) {
                update.setAdditionalScheduleInfo(String.valueOf(maxNumOfCopies));
            }
            
            getCatalogClient().orders().updateScheduledEvent(uri(id), update);
        }
    }
}
