/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.api.utils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.conn.util.InetAddressUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.joda.time.DateTimeZone;

import com.emc.sa.descriptor.ServiceField;
import com.emc.storageos.db.client.model.uimodels.ExecutionWindow;
import com.emc.storageos.db.client.model.uimodels.ExecutionWindowLengthType;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.vipr.model.catalog.ExecutionWindowCommonParam;
import com.google.common.collect.Lists;

public class ValidationUtils {

    private static final Pattern EMAIL_PATTERN = Pattern
            .compile("[\\w!#$%&'*+/=?^_`{|}~-]+(?:\\.[\\w!#$%&'*+/=?^_`{|}~-]+)*@(?:[\\w](?:[\\w-]*[\\w])?\\.)+[a-zA-Z0-9](?:[\\w-]*[\\w])?");
    private static final Pattern NAME_PART_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_\\-]*");

    /** The regular expression for numbers. */
    private static final String INTEGER_REGEX = "[\\-]?\\b\\d+\\b";
    private static final String NUMBER_REGEX = "[-+]?[0-9]*\\.?[0-9]+";

    public static final String DAILY = "DAILY";
    public static final String MONTHLY = "MONTHLY";
    public static final String WEEKLY = "WEEKLY";

    public static final String DAYS = "DAYS";
    public static final String HOURS = "HOURS";
    public static final String MINUTES = "MINUTES";

    private static final int MAX_DAYS = 1;
    private static final int MAX_HOURS = 23;
    private static final int MIN_MINUTES = 30;
    private static final int MAX_MINUTES = (23 * 60) + 59;

    public static final int MAX_EVENTS = 25;
    public static final int TIME_RANGE_PADDING_IN_HOURS = 2;

    public static final long MILLIS_IN_SECOND = 1000;
    public static final long SECONDS_IN_HOUR = 3600;
    public static final long SECONDS_IN_DAY = 3600 * 24;
    public static final long SECONDS_IN_MIN = 60;

    public static boolean isValidEmail(String value) {
        if (StringUtils.isBlank(value)) {
            return true;
        }
        return EMAIL_PATTERN.matcher(value.toString()).matches();
    }

    public static boolean isValidHostNameOrIp(String value) {
        if (isValidIp(value)) {
            return true;
        }
        if (isValidHostName(value)) {
            return true;
        }
        return false;
    }

    public static boolean isValidIp(String value) {
        return validateInetAddress(value);
    }

    public static boolean validateInetAddress(final String address) {

        try {
            InetAddress.getByName(address);
        } catch (UnknownHostException e) {
            return false;
        }

        return true;

    }

    public static boolean isInetAddressFormat(String address) {
        return InetAddressUtils.isIPv4Address(address) || InetAddressUtils.isIPv6Address(address);
    }

    public static boolean isValidHostName(String value) {
        try {
            String[] parts = value.split("\\.");
            for (int i = 0; i < parts.length; i++) {
                if (!NAME_PART_PATTERN.matcher(parts[i]).matches()) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean hasValidPort(String endpoint) {
        try {
            if (endpoint != null && !endpoint.isEmpty()) {
                if (endpoint.contains("]:")) {
                    String port = StringUtils.substringAfter(endpoint, "]:");
                    if (!StringUtils.isNumeric(port)) {
                        return false;
                    }
                } else if (endpoint.contains(":") &&
                        StringUtils.countMatches(endpoint, ":") == 1) {
                    String port = StringUtils.substringAfter(endpoint, ":");
                    if (!StringUtils.isNumeric(port)) {
                        return false;
                    }
                }
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String trimPortFromEndpoint(String endpoint) {
        if (endpoint != null && !endpoint.isEmpty()) {
            if (endpoint.contains("]:")) {
                endpoint = StringUtils.substringBefore(endpoint, "]:");
                endpoint = StringUtils.substringAfter(endpoint, "[");
            } else if (endpoint.contains(":") && StringUtils.countMatches(endpoint, ":") == 1) {
                endpoint = StringUtils.substringBefore(endpoint, ":");
            }
        }
        return endpoint;
    }

    public static void validateField(Integer storageSize, ServiceField field, String value) {
        if (field.isRequired()) {
            validateRequiredField(field, value);
        }

        if (ServiceField.TYPE_NUMBER.equals(field.getType())) {
            validateIntegerField(field, value);
        }
        else if (ServiceField.TYPE_TEXT.equals(field.getType())) {
            validateTextField(field, value);
        }
        else if (ServiceField.TYPE_STORAGE_SIZE.equals(field.getType())) {
            validateStorageSizeField(storageSize, field, value);
        }
        else if (ServiceField.TYPE_BOOLEAN.equals(field.getType())) {
            validateBooleanField(field, value);
        }
    }

    private static void validateRequiredField(ServiceField field, String value) {
        if (value == null || value.isEmpty()) {
            throw APIException.badRequests.serviceFieldRequired(field.getName());
        }
    }

    /**
     * Validates a number field.
     * 
     * @param service
     *            the catalog service.
     * @param field
     *            the field to validate.
     * @param value
     *            the field value.
     */
    private static void validateNumberField(ServiceField field, String value) {
        if (StringUtils.isNotBlank(value)) {
            validateNumber(field.getName(), value);
            if (new Integer(value) < field.getValidation().getMin()) {
                throw APIException.badRequests.serviceFieldBelowMin(field.getName());
            }

            if (new Integer(value) > field.getValidation().getMax()) {
                throw APIException.badRequests.serviceFieldAboveMax(field.getName());
            }
        }
    }

    /**
     * Validates an integer field.
     * 
     * @param service
     *            the catalog service.
     * @param field
     *            the field to validate.
     * @param value
     *            the field value.
     */
    private static void validateIntegerField(ServiceField field, String value) {
        if (StringUtils.isNotBlank(value)) {
            validateInteger(field.getName(), value);
            if (field.getValidation().getMin() != null && new Integer(value) < field.getValidation().getMin()) {
                throw APIException.badRequests.serviceFieldBelowMin(field.getName());
            }

            if (field.getValidation().getMax() != null && new Integer(value) > field.getValidation().getMax()) {
                throw APIException.badRequests.serviceFieldAboveMax(field.getName());
            }
        }
    }

    /**
     * Validates a text field.
     * 
     * @param service
     *            the catalog service.
     * @param field
     *            the field to validate.
     * @param value
     *            the field value.
     */
    private static void validateTextField(ServiceField field, String value) {
        if (StringUtils.isNotBlank(value)) {

            try {
                validateRegex(field.getName(), value, field.getValidation().getRegEx());
            } catch (Exception e) {
                throw APIException.badRequests.serviceFieldNonText(field.getName());
            }
            if (field.getValidation().getMin() != null && value.length() < field.getValidation().getMin()) {
                throw APIException.badRequests.serviceFieldBelowMinLength(field.getName());
            }

            if (field.getValidation().getMax() != null && value.length() > field.getValidation().getMax()) {
                throw APIException.badRequests.serviceFieldBeyondMaxLength(field.getName());
            }
        }
    }

    /**
     * Validates a storage size field.
     * 
     * @param service
     *            the catalog service.
     * @param field
     *            the field to validate.
     * @param value
     *            the field value.
     */
    private static void validateStorageSizeField(Integer storageSize, ServiceField field, String value) {
        validateNumber(field.getName(), value);
        int min = Math.max(0, field.getValidation().getMin());
        if (Float.valueOf(value) < min) {
            throw APIException.badRequests.serviceFieldBelowMin(field.getName());
        }
        boolean hasMaxSize = (storageSize != null) && (storageSize >= 1);
        if (hasMaxSize) {
            if (Float.valueOf(value) > storageSize) {
                throw APIException.badRequests.serviceFieldAboveMax(field.getName());
            }
        }
    }

    private static void validateBooleanField(ServiceField field, String value) {
        if (StringUtils.isNotBlank(value)) {
            try {
                validateRegex(field.getName(), value, field.getValidation().getRegEx());
            } catch (Exception e) {
                throw APIException.badRequests.serviceFieldNonBoolean(field.getName());
            }
        }
    }

    /**
     * Validates a value as a number.
     * 
     * @param fieldName
     *            the name of the field.
     * @param value
     *            the value to validate.
     */
    private static void validateNumber(String fieldName, String value) {
        try {
            validateRegex(fieldName, value, NUMBER_REGEX);
        } catch (Exception e) {
            throw APIException.badRequests.serviceFieldNonNumeric(fieldName);
        }
    }

    /**
     * Validates a value as a float.
     * 
     * @param fieldName
     *            the name of the field.
     * @param value
     *            the value to validate.
     */
    private static void validateInteger(String fieldName, String value) {
        try {
            validateRegex(fieldName, value, INTEGER_REGEX);
        } catch (Exception e) {
            throw APIException.badRequests.serviceFieldNonInteger(fieldName);
        }

    }

    /**
     * Validates a field using a regular expression.
     * 
     * @param fieldName
     *            the name of the field.
     * @param value
     *            the value to validate.
     * @param pattern
     *            the regular expression pattern.
     * @param errorMessage
     *            the error message to display if the value does not match, if blank a default message is used.
     */
    private static void validateRegex(String fieldName, String value, String pattern) throws Exception {
        if (!matches(pattern, value)) {
            throw new Exception();
        }
    }

    private static boolean matches(String pattern, String value) {
        // Null value should be considered matching. If it is required, should be picked up by required field
        if (value == null) {
            return true;
        }

        if (StringUtils.isNotBlank(pattern)) {
            return Pattern.matches(pattern, value);
        }
        else {
            return true;
        }
    }

    public static void validateExecutionWindow(ExecutionWindowCommonParam input) {

        if (input.getExecutionWindowLength() != null) {
            if (MINUTES.equals(input.getExecutionWindowLengthType())) {
                if (input.getExecutionWindowLength() < MIN_MINUTES) {
                    throw APIException.badRequests.executionWindowLengthBelowMin(
                            input.getExecutionWindowLength().toString());
                }
                if (input.getExecutionWindowLength() > MAX_MINUTES) {
                    throw APIException.badRequests.executionWindowLengthAboveMax(
                            input.getExecutionWindowLength().toString());
                }
            }
            else if (HOURS.equals(input.getExecutionWindowLengthType())) {
                if (input.getExecutionWindowLength() > MAX_HOURS) {
                    throw APIException.badRequests.executionWindowLengthAboveMax(
                            input.getExecutionWindowLength().toString());
                }
            }
            else if (DAYS.equals(input.getExecutionWindowLengthType())) {
                if (input.getExecutionWindowLength() > MAX_DAYS) {
                    throw APIException.badRequests.executionWindowLengthAboveMax(
                            input.getExecutionWindowLength().toString());
                }
            }
        }

    }

    public static boolean isOverlapping(ExecutionWindow newExecutionWindow, List<ExecutionWindow> existingWindows) {
        DateTimeZone tz = DateTimeZone.UTC;
        DateTime startOfWeek = getStartOfWeek(tz);
        DateTime endDateTime = startOfWeek.plusDays(31);

        List<Event> events = asEvents(existingWindows, startOfWeek, endDateTime, tz);
        List<Event> newEvents = asEvents(newExecutionWindow, startOfWeek, endDateTime, tz, 365);
        for (Event event : events) {
            for (Event newEvent : newEvents) {
                if (isOverlapping(event, newEvent)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static DateTime getStartOfWeek(DateTimeZone tz) {
        DateTime startOfWeek = new DateTime(tz);
        startOfWeek = startOfWeek.withDayOfWeek(DateTimeConstants.MONDAY);
        startOfWeek = startOfWeek.withMillisOfDay(0);
        startOfWeek = startOfWeek.withZone(DateTimeZone.UTC);
        return startOfWeek;
    }

    private static boolean isOverlapping(Event left, Event right) {
        return (StringUtils.equals(left.id, right.id) == false) &&
                (left.startMillis < right.endMillis) &&
                (right.startMillis < left.endMillis);
    }

    public static List<Event> asEvents(List<ExecutionWindow> executionWindows, DateTime start, DateTime end, DateTimeZone tz) {
        return asEvents(executionWindows, start, end, tz, MAX_EVENTS);
    }

    public static List<Event> asEvents(List<ExecutionWindow> executionWindows, DateTime start, DateTime end,
            DateTimeZone tz, long maxNumberOfEvents) {
        List<Event> events = Lists.newArrayList();
        if (executionWindows != null) {
            for (ExecutionWindow executionWindow : executionWindows) {
                events.addAll(asEvents(executionWindow, start, end, tz, maxNumberOfEvents));
            }
        }
        return events;
    }

    public static long toMillis(long duration, String unit) {
        ExecutionWindowLengthType lengthType = ExecutionWindowLengthType.valueOf(unit);
        if (ExecutionWindowLengthType.DAYS.equals(lengthType)) {
            return duration * SECONDS_IN_DAY * MILLIS_IN_SECOND;
        }
        else if (ExecutionWindowLengthType.HOURS.equals(lengthType)) {
            return duration * SECONDS_IN_HOUR * MILLIS_IN_SECOND;
        }
        return duration * SECONDS_IN_MIN * MILLIS_IN_SECOND;
    }

    private static List<Event> asEvents(ExecutionWindow executionWindow, DateTime start, DateTime end, DateTimeZone tz,
            long maxNumberOfEvents) {

        long lengthInMillis = toMillis(executionWindow.getExecutionWindowLength(), executionWindow.getExecutionWindowLengthType());
        List<Event> events = Lists.newArrayList();

        DateTime indexDate = start.minusHours(TIME_RANGE_PADDING_IN_HOURS);
        DateTime paddedEnd = end.plusHours(TIME_RANGE_PADDING_IN_HOURS);
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
                events.add(new Event(id, executionWindow.getLabel(), nextDate, nextEndDate, tz));
            }

            indexDate = indexDate.plusDays(1);
        }

        return events;
    }

    private static boolean isScheduled(DateTime indexDate, ExecutionWindow executionWindow) {
        if (indexDate != null && executionWindow != null) {
            if (DAILY.equals(executionWindow.getExecutionWindowType())) {
                return true;
            }
            else if (WEEKLY.equals(executionWindow.getExecutionWindowType())) {
                Integer dayOfWeek = executionWindow.getDayOfWeek();
                if (dayOfWeek != null && indexDate.getDayOfWeek() == dayOfWeek) {
                    return true;
                }
            }
            else if (MONTHLY.equals(executionWindow.getExecutionWindowType())) {
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
}
