/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.catalog;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;

import java.net.URI;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import play.data.validation.Max;
import play.data.validation.MaxSize;
import play.data.validation.Min;
import play.data.validation.MinSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.i18n.Messages;
import play.mvc.Controller;
import play.mvc.Util;
import play.mvc.With;
import util.Event;
import util.ExecutionWindowUtils;
import util.MessagesUtils;
import util.OrderUtils;
import util.TimeUtils;
import util.TimeUtils.Duration;

import com.emc.vipr.model.catalog.ExecutionWindowCommonParam;
import com.emc.vipr.model.catalog.ExecutionWindowCreateParam;
import com.emc.vipr.model.catalog.ExecutionWindowRestRep;
import com.emc.vipr.model.catalog.ExecutionWindowUpdateParam;
import com.emc.vipr.model.catalog.OrderRestRep;
import com.google.common.collect.Maps;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.tenant.TenantSelector;
import controllers.util.AbstractRestRepForm;
import controllers.util.Models;

/**
 * @author Chris Dail
 */
@With(Common.class)
@Restrictions({@Restrict("TENANT_ADMIN")})
public class ExecutionWindows extends Controller {
    
    public static void show() {
        Date serverTime = new Date(System.currentTimeMillis());
        TenantSelector.addRenderArgs();
        render(serverTime);
    }

    public static void create(Long start, Long end, @Required Integer timezoneOffsetInMinutes) {
        if (start == null) {
            badRequest("Start not specified");
        }

        String tenantId = Models.currentAdminTenant();

        ExecutionWindowForm executionWindowForm = new ExecutionWindowForm();
        executionWindowForm.tenantId = tenantId;
        executionWindowForm.timezoneOffsetInMinutes = timezoneOffsetInMinutes == null ? 0 : timezoneOffsetInMinutes;
        executionWindowForm.type = ExecutionWindowRestRep.WEEKLY;
        executionWindowForm.updateTimes(start, end);

        addDateTimeRenderArgs();
        
        render("@edit", executionWindowForm);
    }

    public static void edit(@Required String id, @Required Integer timezoneOffsetInMinutes) {
        ExecutionWindowRestRep executionWindow = ExecutionWindowUtils.getExecutionWindow(uri(id));
        if (executionWindow == null) {
            notFound();
        }
        
        ExecutionWindowForm executionWindowForm = new ExecutionWindowForm();
        executionWindowForm.timezoneOffsetInMinutes = timezoneOffsetInMinutes == null ? 0 : timezoneOffsetInMinutes;
        executionWindowForm.readFrom(executionWindow);

        addDateTimeRenderArgs();
        
        render(executionWindowForm);
    }

    public static void save(ExecutionWindowForm executionWindowForm) {
        executionWindowForm.validate("executionWindowForm");
        boolean isOverlapping = isOverlapping(executionWindowForm);
        if (Validation.hasErrors() || isOverlapping) {
            if (isOverlapping) {
                renderArgs.put("error", MessagesUtils.get("executionWindow.overlapping"));
            }            
            response.status = 400;
            addDateTimeRenderArgs();
            params.flash();
            render("@edit", executionWindowForm);
        }
        else {
            ExecutionWindowRestRep executionWindow = executionWindowForm.save();
            renderText(MessagesUtils.get("executionWindow.saved.success", executionWindow.getName()));
        }
    }

    public static void move(@Required String id, @Required Long start, @Required Integer timezoneOffsetInMinutes) {
        ExecutionWindowRestRep executionWindow = ExecutionWindowUtils.getExecutionWindow(uri(id));
        if (executionWindow == null) {
            renderJSON(new CalResponse(false, MessagesUtils.get("executionWindow.notfound", id)));
        }
        
        ExecutionWindowForm executionWindowForm = new ExecutionWindowForm();
        executionWindowForm.timezoneOffsetInMinutes = timezoneOffsetInMinutes == null ? 0 : timezoneOffsetInMinutes;
        executionWindowForm.readFrom(executionWindow);
        executionWindowForm.updateTimes(start, null);
        
        boolean isOverlapping = isOverlapping(executionWindowForm);
        if (isOverlapping) {
            renderJSON(new CalResponse(false, MessagesUtils.get("executionWindow.overlapping", executionWindow.getName())));
        }
        
        executionWindowForm.save();
        renderJSON(new CalResponse(true, MessagesUtils.get("executionWindow.saved.success", executionWindow.getName())));
    }

    public static void resize(@Required String id, @Required Long start, @Required Long end, @Required Integer timezoneOffsetInMinutes) {
        ExecutionWindowRestRep executionWindow = ExecutionWindowUtils.getExecutionWindow(uri(id));
        if (executionWindow == null) {
            renderJSON(new CalResponse(false, MessagesUtils.get("executionWindow.notfound", id)));
        }
        
        ExecutionWindowForm executionWindowForm = new ExecutionWindowForm();
        executionWindowForm.timezoneOffsetInMinutes = timezoneOffsetInMinutes == null ? 0 : timezoneOffsetInMinutes;
        executionWindowForm.readFrom(executionWindow);
        executionWindowForm.updateTimes(start, end);
        
        boolean isOverlapping = isOverlapping(executionWindowForm);
        if (isOverlapping) {
            renderJSON(new CalResponse(false, MessagesUtils.get("executionWindow.overlapping", executionWindow.getName())));
        }        
        
        executionWindowForm.save();   
        renderJSON(new CalResponse(true, MessagesUtils.get("executionWindow.saved.success", executionWindow.getName())));
    }

    public static void delete(ExecutionWindowForm executionWindowForm) {
        ExecutionWindowRestRep executionWindow = ExecutionWindowUtils.getExecutionWindow(uri(executionWindowForm.id));
        if (executionWindow == null) {
            notFound();
        }        
        
        List<OrderRestRep> scheduledOrders = OrderUtils.getScheduledOrdersByExecutionWindow(uri(executionWindowForm.id));
        if (Validation.hasErrors() || !scheduledOrders.isEmpty()) {
            if (!scheduledOrders.isEmpty()) {
                renderArgs.put("error", MessagesUtils.get("executionWindow.deleted.containsScheduledOrders", scheduledOrders.size()));
            }
            response.status = 400;

            executionWindowForm.readFrom(executionWindow);

            addDateTimeRenderArgs();            
            
            render("@edit", executionWindowForm);
        }
        
        ExecutionWindowUtils.deleteExecutionWindow(executionWindow);

        renderText(MessagesUtils.get("executionWindow.deleted.success", executionWindow.getName()));
    }

    public static void events(int timezoneOffsetInMinutes, String start, String end) {
        List<ExecutionWindowRestRep> executionWindows = ExecutionWindowUtils.getExecutionWindows(uri(Models.currentAdminTenant()));//NOSONAR
        DateTimeZone tz = TimeUtils.getLocalTimeZone(timezoneOffsetInMinutes);
        DateTimeFormatter formatter = ISODateTimeFormat.date().withZone(tz);
        DateTime startDateTime = DateTime.parse(start, formatter);
        DateTime endDateTime = DateTime.parse(end, formatter);
        List<Event> events = ExecutionWindowUtils.asEvents(executionWindows, startDateTime, endDateTime, tz);
        renderJSON(events);
    }

    @Util
    public static boolean isOverlapping(ExecutionWindowForm executionWindowForm) {
        if (executionWindowForm.length == null) {
            return false;
        }
        ExecutionWindowRestRep tempExecutionWindow = new ExecutionWindowRestRep();
        tempExecutionWindow.setName(Messages.get("ExecutionWindows.tempWindowLabel"));
        executionWindowForm.writeTo(tempExecutionWindow);

        return ExecutionWindowUtils.isOverlapping(tempExecutionWindow);
    }
    
    // Adds choices for dropdowns to the renderArgs
    private static void addDateTimeRenderArgs() {
        // Days of the Week
        Map<String, String> daysOfWeek = Maps.newLinkedHashMap();
        for (int i = 1; i <= 7; i++) {
            String num = String.valueOf(i);
            daysOfWeek.put(num, MessagesUtils.get("datetime.daysOfWeek." + num));
        }
        renderArgs.put("daysOfWeek", daysOfWeek);

        // Days of the Month
        Map<String, String> daysOfMonth = Maps.newLinkedHashMap();
        for (int i = 1; i <= 31; i++) {
            String num = String.valueOf(i);
            daysOfMonth.put(num, num);
        }
        daysOfMonth.put(ExecutionWindowForm.LAST_DAY_OF_MONTH, MessagesUtils.get("datetime.dayOfMonth.L"));
        renderArgs.put("daysOfMonth", daysOfMonth);

        // Hours
        Map<String, String> hoursOfDay = Maps.newLinkedHashMap();
        for (int i = 0; i <= 23; i++) {
            String num = String.valueOf(i);
            hoursOfDay.put(num, MessagesUtils.get("datetime.hours." + num));
        }
        renderArgs.put("hoursOfDay", hoursOfDay);

        // Time Durations
        Map<String, String> timeDuration = Maps.newLinkedHashMap();
        timeDuration.put(ExecutionWindowRestRep.MINUTES, MessagesUtils.get("datetime.units.min"));
        timeDuration.put(ExecutionWindowRestRep.HOURS, MessagesUtils.get("datetime.units.h"));
        timeDuration.put(ExecutionWindowRestRep.DAYS, MessagesUtils.get("datetime.units.d"));
        renderArgs.put("timeDuration", timeDuration);

        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        TimeZone tz = c.getTimeZone();
        renderArgs.put("serverTimeZone", tz.getDisplayName(tz.inDaylightTime(new Date()), TimeZone.SHORT));
        renderArgs.put("serverTimeZoneOffset", tz.getOffset(new Date().getTime()) / (60 * 1000));
    }

    //
    public static class CalResponse {
        public boolean success;
        public String message;

        public CalResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public CalResponse() {
        }
    }
    
    public static class ExecutionWindowForm extends AbstractRestRepForm<ExecutionWindowRestRep> {

        private static final int MAX_DAYS = 1;
        private static final int MAX_HOURS = 23;
        private static final int MIN_MINUTES = 30;
        private static final int MAX_MINUTES = (23 * 60) + 59;
        
        public static final String LAST_DAY_OF_MONTH = "L";
        
        @Required
        @MaxSize(128)
        @MinSize(2)
        public String name;

        public String tenantId;
        
        @Required
        public Integer hourOfDay;
        
        @Required
        @Min(1)
        public Integer length;
        
        @Required
        public String lengthType;
        
        @Required
        public String type;
        
        @Min(1)
        @Max(7)
        public Integer dayOfWeek;

        public String dayOfMonth;

        @Required
        public Integer timezoneOffsetInMinutes;
        
        public ExecutionWindowForm() {
        }

        @Override
        protected void doReadFrom(ExecutionWindowRestRep model) {
            name = model.getName();
            tenantId = model.getTenant().getId().toString();
            length = model.getExecutionWindowLength();
            lengthType = model.getExecutionWindowLengthType();
            type = model.getExecutionWindowType();
            
            if (model.getHourOfDayInUTC() != null) {
                hourOfDay = TimeUtils.getLocalHourOfDay(model.getHourOfDayInUTC(), timezoneOffsetInMinutes);
                if (model.getDayOfWeek() != null) {
                    dayOfWeek = TimeUtils.getLocalDayOfWeek(model.getDayOfWeek(), model.getHourOfDayInUTC(),
                            timezoneOffsetInMinutes);
                }
            }
            if (model.getLastDayOfMonth() != null && model.getLastDayOfMonth().booleanValue() == true) {
                dayOfMonth = LAST_DAY_OF_MONTH;
            }
            else if (model.getDayOfMonth() != null) {
            	dayOfMonth =  Integer.toString(TimeUtils.getLocalDayOfMonth(
            	        model.getDayOfMonth(), model.getHourOfDayInUTC(), timezoneOffsetInMinutes));
            }
        }
        
        @Override
        public void validate(String fieldName) {
            super.validate(fieldName);

            // Execution Window Name Unique Check
            if (name != null) {
                name = name.trim();
                if (StringUtils.isNotBlank(name)) {
                    ExecutionWindowRestRep existingExecutionWindow = ExecutionWindowUtils.getExecutionWindow(name, uri(Models.currentAdminTenant()));
                    if (existingExecutionWindow != null && (StringUtils.isBlank(id) || (StringUtils.isNotBlank(id) && existingExecutionWindow.getId().equals(URI.create(id))) == false)) {
                        Validation.addError(fieldName + ".name",
                                MessagesUtils.get("execWindow.name.notUnique"));                    
                    }
                }
            }

            if (!dayOfMonth.equals(LAST_DAY_OF_MONTH)) {
                if (!StringUtils.isNumeric(dayOfMonth)) {
                    Validation.addError("executionWindow.dayOfMonth", MessagesUtils.get("execWindow.dayOfMonthNumeric"));
                } else {
                    int dayOfMonthLocal = Integer.valueOf(dayOfMonth);
                    Validation.min("executionWindow.dayOfMonth", dayOfMonthLocal, 1);
                    Validation.max("executionWindow.dayOfMonth", dayOfMonthLocal, 31);
                }
            }

            if (StringUtils.isNotBlank(params.get("executionWindowForm.length")) &&
            		!StringUtils.isNumeric(params.get("executionWindowForm.length"))) 
            {
                Validation.addError(fieldName + ".length",
                        MessagesUtils.get("validation.invalid"));                   
            }
            else {
                if (this.length != null) {
                    if (ExecutionWindowRestRep.MINUTES.equals(lengthType)) {
                        if (length.intValue() < MIN_MINUTES) {
                            Validation.addError(fieldName + ".length",
                                    MessagesUtils.get("execWindow.length.min", MIN_MINUTES));                         
                        }                       
                        if (length.intValue() > MAX_MINUTES) {
                            Validation.addError(fieldName + ".length",
                                    MessagesUtils.get("execWindow.length.max", MAX_MINUTES));                         
                        }                
                    }
                    else if (ExecutionWindowRestRep.HOURS.equals(lengthType)) {
                        if (length.intValue() > MAX_HOURS) {
                            Validation.addError(fieldName + ".length",
                                    MessagesUtils.get("execWindow.length.max", MAX_HOURS));                    
                        }                
                    }
                    else if (ExecutionWindowRestRep.DAYS.equals(lengthType)) {
                        if (length.intValue() > MAX_DAYS) {
                            Validation.addError(fieldName + ".length",
                                    MessagesUtils.get("execWindow.length.max", MAX_DAYS));
                        }
                    }
                    else {
                        Validation.addError("execWindow.lengthType", MessagesUtils.get("execWindow.lengthType"));
                    }
                }
            }
                       
        }   

        public void setDurationMillis(String durationMillis) {
            setDurationMillis(Long.parseLong(durationMillis));
        }

        public void setDurationMillis(long durationMillis) {
            Duration d = TimeUtils.toDuration(durationMillis);
            length = (int) d.duration;
            lengthType = d.unit.name();
        }

        public String getDurationMillis() {
            return String.valueOf(TimeUtils.toMillis(length, lengthType));
        }

        public void updateTimes(Long start, Long end) {
            
            DateTime startDateTime = new DateTime(start);
            startDateTime = startDateTime.withZone(TimeUtils.getLocalTimeZone(timezoneOffsetInMinutes));
            
            hourOfDay = startDateTime.getHourOfDay();
            dayOfWeek = startDateTime.getDayOfWeek();
            dayOfMonth = String.valueOf(startDateTime.getDayOfMonth());
            if (end != null) {
                long durationMillis = (end - start);
                setDurationMillis(durationMillis);
            }

        }

        @Override
        protected ExecutionWindowRestRep doCreate() {
            
            ExecutionWindowRestRep executionWindow = null;
                    
            try {
                ExecutionWindowCreateParam createParam = new ExecutionWindowCreateParam();
                
                createParam.setTenant(uri(this.tenantId));
                writeCommon(createParam);
                
                executionWindow = ExecutionWindowUtils.createExecutionWindow(createParam);
                
            } catch(Exception e) {
                flash.error(e.getMessage());
                Common.handleError();
            }
            
            return executionWindow;
        }

        @Override
        protected ExecutionWindowRestRep doUpdate() {
            
            ExecutionWindowRestRep executionWindow = null;
            
            try {
                ExecutionWindowUpdateParam updateParam = new ExecutionWindowUpdateParam();
                
                writeCommon(updateParam);
                
                executionWindow = ExecutionWindowUtils.updateExecutionWindow(uri(this.id), updateParam);
            
            } catch(Exception e) {
                flash.error(e.getMessage());
                Common.handleError();
            } 
            
            return executionWindow;
        }
        
        private void writeCommon(ExecutionWindowCommonParam commonParam) {
            commonParam.setName(name);
            commonParam.setExecutionWindowLength(length);
            commonParam.setExecutionWindowLengthType(lengthType);
            commonParam.setExecutionWindowType(type);
            
            commonParam.setHourOfDayInUTC(TimeUtils.getUTCHourOfDay(hourOfDay, timezoneOffsetInMinutes));
            commonParam.setDayOfWeek(TimeUtils.getUTCDayOfWeek(dayOfWeek, hourOfDay, timezoneOffsetInMinutes));
            if (StringUtils.isNotBlank(dayOfMonth)) {
                if (dayOfMonth.equals(LAST_DAY_OF_MONTH)) {
                    commonParam.setLastDayOfMonth(Boolean.TRUE);
                    commonParam.setDayOfMonth(null);
                }
                else {
                    commonParam.setLastDayOfMonth(Boolean.FALSE);
                    Integer dayOfMonthInLocal = (Integer) ConvertUtils.convert(dayOfMonth, Integer.class); 
                    if (dayOfMonthInLocal != null) {
                        commonParam.setDayOfMonth(TimeUtils.getUTCDayOfMonth(dayOfMonthInLocal, hourOfDay, timezoneOffsetInMinutes));
                    }
                }
            }
            else {
                commonParam.setLastDayOfMonth(Boolean.FALSE);
                commonParam.setDayOfMonth(null);
            }            
        }
        
        public void writeTo(ExecutionWindowRestRep executionWindowRestRep) {
            executionWindowRestRep.setId(uri(this.id));
            executionWindowRestRep.setName(name);
            executionWindowRestRep.setExecutionWindowLength(length);
            executionWindowRestRep.setExecutionWindowLengthType(lengthType);
            executionWindowRestRep.setExecutionWindowType(type);
            
            executionWindowRestRep.setHourOfDayInUTC(TimeUtils.getUTCHourOfDay(hourOfDay, timezoneOffsetInMinutes));
            executionWindowRestRep.setDayOfWeek(TimeUtils.getUTCDayOfWeek(dayOfWeek, hourOfDay, timezoneOffsetInMinutes));
            if (StringUtils.isNotBlank(dayOfMonth)) {
                if (dayOfMonth.equals(LAST_DAY_OF_MONTH)) {
                    executionWindowRestRep.setLastDayOfMonth(Boolean.TRUE);
                    executionWindowRestRep.setDayOfMonth(null);
                }
                else {
                    executionWindowRestRep.setLastDayOfMonth(Boolean.FALSE);
                    Integer dayOfMonthInLocal = (Integer) ConvertUtils.convert(dayOfMonth, Integer.class); 
                    if (dayOfMonthInLocal != null) {
                        executionWindowRestRep.setDayOfMonth(TimeUtils.getUTCDayOfMonth(dayOfMonthInLocal, hourOfDay, timezoneOffsetInMinutes));
                    }
                }
            }
            else {
                executionWindowRestRep.setLastDayOfMonth(Boolean.FALSE);
                executionWindowRestRep.setDayOfMonth(null);
            }                      
        }

    }

}

