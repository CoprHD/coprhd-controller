/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import static util.BourneUtil.getCatalogClient;

import java.net.URI;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;

import controllers.util.Models;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.joda.time.DateTimeZone;

import play.Logger;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.vipr.client.ViPRCatalogClient2;
import com.emc.vipr.model.catalog.ExecutionWindowCreateParam;
import com.emc.vipr.model.catalog.ExecutionWindowRestRep;
import com.emc.vipr.model.catalog.ExecutionWindowUpdateParam;
import com.google.common.collect.Lists;

public class ExecutionWindowUtils {

    public static final int MAX_EVENTS = 25;
    public static final int TIME_RANGE_PADDING_IN_HOURS = 2;

    public static boolean isAnyExecutionWindowActive() {
        List<ExecutionWindowRestRep> windows = getExecutionWindows();
        for (ExecutionWindowRestRep window : windows) {
            if (isExecutionWindowActive(window)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isExecutionWindowActive(ExecutionWindowRestRep executionWindow) {
        if (executionWindow != null) {
            executionWindow.isActive(Calendar.getInstance());
        }
        return false;
    }

    public static ExecutionWindowRestRep getActiveOrNextExecutionWindow() {
        return getActiveOrNextExecutionWindow(Calendar.getInstance());
    }

    public static ExecutionWindowRestRep getActiveOrNextExecutionWindow(Calendar time) {
        List<ExecutionWindowRestRep> windows = ExecutionWindowUtils.getExecutionWindows();

        // Check active windows
        ExecutionWindowRestRep activeWindow = getActiveExecutionWindow(windows, time);
        if (activeWindow != null) {
            return activeWindow;
        }
        else {
            // Check for next window
            return getNextExecutionWindow(windows, time);
        }
    }

    public static ExecutionWindowRestRep getNextExecutionWindow(Calendar time) {
        return getNextExecutionWindow(ExecutionWindowUtils.getExecutionWindows( URI.create(Models.currentAdminTenant()) ), time);
    }

    public static ExecutionWindowRestRep getActiveExecutionWindow(Collection<ExecutionWindowRestRep> windows, Calendar time) {
        for (ExecutionWindowRestRep window : windows) {
            if (window.isActive(time)) {
                return window;
            }
        }
        return null;
    }

    public static ExecutionWindowRestRep getNextExecutionWindow(Collection<ExecutionWindowRestRep> windows, Calendar time) {
        Calendar nextWindowTime = null;
        ExecutionWindowRestRep nextWindow = null;
        for (ExecutionWindowRestRep window : windows) {
            Calendar windowTime = calculateNextWindowTime(time, window);
            if (nextWindowTime == null || nextWindowTime.after(windowTime)) {
                nextWindowTime = windowTime;
                nextWindow = window;
            }
        }
        return nextWindow;
    }

    public static Calendar calculateNextWindowTime(ExecutionWindowRestRep window) {
        return calculateNextWindowTime(Calendar.getInstance(), window);
    }

    public static Calendar calculateNextWindowTime(Calendar time, ExecutionWindowRestRep window) {
        return window.calculateNext(time);
    }

    public static boolean isOverlapping(ExecutionWindowRestRep newExecutionWindow) {
        DateTimeZone tz = DateTimeZone.UTC;
        List<ExecutionWindowRestRep> executionWindows = ExecutionWindowUtils.getExecutionWindows();
        DateTime startOfWeek = getStartOfWeek(tz);
        DateTime endDateTime = startOfWeek.plusDays(31);

        List<Event> events = asEvents(executionWindows, startOfWeek, endDateTime, tz);
        List<Event> newEvents = asEvents(newExecutionWindow, startOfWeek, endDateTime, tz, 365);
        for (Event event : events) {
            for (Event newEvent : newEvents) {
                if (isOverlapping(event, newEvent)) {
                    if (Logger.isDebugEnabled()) {
                        Logger.debug("Overlapping events: " + event + " and " + event);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isOverlapping(Event left, Event right) {
        return (StringUtils.equals(left.id, right.id) == false) &&
                (left.startMillis < right.endMillis) &&
                (right.startMillis < left.endMillis);
    }

    public static DateTime getStartOfWeek(DateTimeZone tz) {
        DateTime startOfWeek = new DateTime(tz);
        startOfWeek = startOfWeek.withDayOfWeek(DateTimeConstants.MONDAY);
        startOfWeek = startOfWeek.withMillisOfDay(0);
        startOfWeek = startOfWeek.withZone(DateTimeZone.UTC);
        return startOfWeek;
    }

    public static List<Event> asEvents(List<ExecutionWindowRestRep> executionWindows, DateTime start, DateTime end, DateTimeZone tz) {
        return asEvents(executionWindows, start, end, tz, MAX_EVENTS);
    }

    public static List<Event> asEvents(List<ExecutionWindowRestRep> executionWindows, DateTime start, DateTime end,
            DateTimeZone tz, long maxNumberOfEvents) {
        List<Event> events = Lists.newArrayList();
        if (executionWindows != null) {
            for (ExecutionWindowRestRep executionWindow : executionWindows) {
                events.addAll(asEvents(executionWindow, start, end, tz, maxNumberOfEvents));
            }
        }
        return events;
    }

    private static List<Event> asEvents(ExecutionWindowRestRep executionWindow, DateTime start, DateTime end, DateTimeZone tz,
            long maxNumberOfEvents) {

        long lengthInMillis = TimeUtils
                .toMillis(executionWindow.getExecutionWindowLength(), executionWindow.getExecutionWindowLengthType());
        List<Event> events = Lists.newArrayList();

        DateTime indexDate = start.withZone(DateTimeZone.UTC).minusHours(TIME_RANGE_PADDING_IN_HOURS);
        DateTime paddedEnd = end.withZone(DateTimeZone.UTC).plusHours(TIME_RANGE_PADDING_IN_HOURS);
        while (indexDate.isBefore(paddedEnd.getMillis())) {
            // Potentially some CRON expressions could fire a LOT. Max out
            // after a certain number of events per job
            if (events.size() > maxNumberOfEvents) {
                break;
            }

            if (isScheduled(indexDate, executionWindow)) {
                int hourOfDay = executionWindow.getHourOfDayInUTC();
                int minute = 0;
                if (executionWindow.getMinuteOfHourInUTC() != null) {
                    minute = executionWindow.getMinuteOfHourInUTC();
                }

                DateTime nextDate = indexDate.withHourOfDay(hourOfDay);
                nextDate = nextDate.withMinuteOfHour(minute);
                nextDate = nextDate.withSecondOfMinute(0);
                nextDate = nextDate.withMillisOfSecond(0);

                DateTime nextEndDate = nextDate.plusMillis((int) lengthInMillis);
                String id = null;
                if (executionWindow.getId() != null) {
                    id = executionWindow.getId().toString();
                }
                events.add(new Event(id, executionWindow.getName(), nextDate, nextEndDate, tz));
            }

            indexDate = indexDate.plusDays(1);
        }

        return events;
    }

    private static boolean isScheduled(DateTime indexDate, ExecutionWindowRestRep executionWindow) {
        if (indexDate != null && executionWindow != null) {
            if (ExecutionWindowRestRep.DAILY.equals(executionWindow.getExecutionWindowType())) {
                return true;
            }
            else if (ExecutionWindowRestRep.WEEKLY.equals(executionWindow.getExecutionWindowType())) {
                Integer dayOfWeek = executionWindow.getDayOfWeek();
                if (dayOfWeek != null && indexDate.getDayOfWeek() == dayOfWeek) {
                    return true;
                }
            }
            else if (ExecutionWindowRestRep.MONTHLY.equals(executionWindow.getExecutionWindowType())) {
                Integer dayOfMonth = executionWindow.getDayOfMonth();
                Boolean lastDayOfMonth = executionWindow.getLastDayOfMonth();
                if (lastDayOfMonth != null && lastDayOfMonth.booleanValue() == true) {
                    if (isLastDayOfMonth(indexDate.getDayOfMonth(), indexDate)) {
                        return true;
                    }
                }
                else if (dayOfMonth != null && indexDate.getDayOfMonth() == dayOfMonth) {
                    return true;
                }
                else if (isLastDayOfMonth(dayOfMonth, indexDate)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isLastDayOfMonth(Integer dayOfMonth, DateTime date) {
        if (dayOfMonth != null && date != null) {
            // Is last day of month?
            if (date.dayOfMonth().get() == date.dayOfMonth().getMaximumValue()) {
                // Is value store for day of month greater than or equal to current day
                if (dayOfMonth >= date.dayOfMonth().getMaximumValue()) {
                    return true;
                }
            }
        }
        return false;
    }

    public static ExecutionWindowRestRep getExecutionWindow(RelatedResourceRep rep) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        return catalog.executionWindows().get(rep);
    }

    public static ExecutionWindowRestRep getExecutionWindow(String name, URI tenantId) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        List<ExecutionWindowRestRep> executionWindows = catalog.executionWindows().getByTenant(tenantId);
        for (ExecutionWindowRestRep executionWindow : executionWindows) {
            if (name.equals(executionWindow.getName())) {
                return executionWindow;
            }
        }
        return null;
    }

    public static ExecutionWindowRestRep getExecutionWindow(URI executionWindowId) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        return catalog.executionWindows().get(executionWindowId);
    }

    public static List<ExecutionWindowRestRep> getExecutionWindows(URI tenantId) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        return catalog.executionWindows().getByTenant(tenantId);
    }

    public static List<ExecutionWindowRestRep> getExecutionWindows() {
        ViPRCatalogClient2 catalog = getCatalogClient();
        List<NamedRelatedResourceRep> reps = catalog.executionWindows().listByUserTenant();
        return catalog.executionWindows().getByRefs(reps);
    }

    public static void deleteExecutionWindow(ExecutionWindowRestRep executionWindow) {
        if (executionWindow != null) {
            ViPRCatalogClient2 catalog = getCatalogClient();
            catalog.executionWindows().deactivate(executionWindow.getId());
        }
    }

    public static ExecutionWindowRestRep createExecutionWindow(ExecutionWindowCreateParam createParam) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        return catalog.executionWindows().create(createParam);
    }

    public static ExecutionWindowRestRep updateExecutionWindow(URI id, ExecutionWindowUpdateParam updateParam) {
        ViPRCatalogClient2 catalog = getCatalogClient();
        return catalog.executionWindows().update(id, updateParam);
    }

}
