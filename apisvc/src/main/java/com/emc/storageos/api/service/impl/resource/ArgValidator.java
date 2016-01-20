/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.apache.http.conn.util.InetAddressUtils;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.SchedulePolicy;
import com.emc.storageos.db.client.model.SchedulePolicy.ScheduleFrequency;
import com.emc.storageos.model.file.ScheduleSnapshotExpireParam;
import com.emc.storageos.model.schedulepolicy.SchedulePolicyParam;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCodeException;

/**
 * Utility functions for validating arguments
 */
public class ArgValidator {

    private static final String ALPHA_NUMERIC_PATTERN = "^[a-zA-Z0-9]+$";
    private static final Pattern patternAlphanumeric = Pattern.compile(ALPHA_NUMERIC_PATTERN);

    /**
     * Checks input URI and throws APIException.badRequests.invalidURI if
     * validation fails
     * 
     * @param uri the URN to check
     */
    public static void checkUri(final URI uri) {
        if (!URIUtil.isValid(uri)) {
            throw APIException.badRequests.invalidURI(uri);
        }
    }

    /**
     * Validates that the uri supplied is not null, is of the urn:storageos:
     * scheme and represents an object of specific type.
     * 
     * @param uri the URN to check
     * @param type the DataObject class that the URI must represent
     * @param fieldName the name of the field where the uri originated
     */
    public static void checkFieldUriType(final URI uri, final Class<? extends DataObject> type, final String fieldName) {
        checkFieldNotNull(uri, fieldName);

        if (!URIUtil.isValid(uri)) {
            throw APIException.badRequests.invalidParameterURIInvalid(fieldName, uri);
        }

        if (!URIUtil.isType(uri, type)) {
            throw APIException.badRequests.invalidParameterURIWrongType(fieldName, uri, type.getSimpleName());
        }
    }

    public static void checkUrl(final String url, final String fieldName) {
        try {
            new URL(url);// NOSONAR("We are creating an URL object to validate the url string")
        } catch (MalformedURLException e) {
            throw APIException.badRequests.invalidUrl(fieldName, url);
        }
    }

    /**
     * Validates that the value supplied is not null.
     * 
     * @param value the value to check
     * @param fieldName the name of the field where the value originated
     */
    public static void checkFieldNotNull(final Object value, final String fieldName) {
        checkField(value != null, fieldName);
    }

    /**
     * Validates that the value supplied is not an empty string.
     * 
     * @param value the value to check
     * @param fieldName the name of the field where the value originated
     */
    public static void checkFieldNotEmpty(final String value, final String fieldName) {
        checkField(StringUtils.isNotEmpty(value), fieldName);
    }

    /**
     * Validates that the value supplied is not null, and not an empty collection
     * 
     * @param value the value to check
     * @param fieldName the name of the field where the value originated
     */
    public static void checkFieldNotEmpty(final Collection<?> value, final String fieldName) {
        checkField(value != null && !value.isEmpty(), fieldName);
    }

    /**
     * Validates that the value supplied is not null, and not an empty map
     * 
     * @param value the value to check
     * @param fieldName the name of the field where the value originated
     */
    public static void checkFieldNotEmpty(final Map<?, ?> value, final String fieldName) {
        checkField(value != null && !value.isEmpty(), fieldName);
    }

    /**
     * Fires APIException.badRequests.requiredParameterMissingOrEmpty if given
     * condition is false for a field
     * 
     * @param condition The condition to check. If false, it will throw a
     *            ServiceCodeException
     * @param fieldName The field name to validate
     */
    private static void checkField(final boolean condition, final String fieldName) {
        if (!condition) {
            throw APIException.badRequests.requiredParameterMissingOrEmpty(fieldName);
        }
    }

    /**
     * Validates that the value supplied is not null, and matches one of the
     * expected values
     * 
     * @param value the value to check
     * @param fieldName the name of the field where the value originated
     * @param expected the set of Enum values to allow
     */
    public static <E extends Enum<E>> void checkFieldForValueFromEnum(final E value, final String fieldName,
            final EnumSet<E> expected) {
        checkFieldNotNull(value, fieldName);
        checkFieldValueFromEnum(value.name(), fieldName, expected);
    }

    /**
     * Validates that the value supplied matches one of the expected values' names
     * 
     * @param value the value to check
     * @param fieldName the name of the field where the value originated
     * @param expected the set of Enum values to allow
     */
    public static void checkFieldValueFromEnum(final String value, final String fieldName,
            final EnumSet<?> expected) {
        for (Enum<?> e : expected) {
            if (e.name().equals(value)) {
                return;
            }
        }
        throw APIException.badRequests.invalidParameterValueWithExpected(fieldName, value,
                expected.toArray());
    }

    /**
     * Validates that the value supplied matches one of the expected values' names
     * 
     * @param value the value to check
     * @param fieldName the name of the field where the value originated
     * @param enumType the enum class the value is expected to be in
     */
    public static void checkFieldValueFromEnum(final String value, final String fieldName,
            Class<? extends Enum> enumType) {
        if (value != null) {
            checkFieldValueFromEnum(value, fieldName, EnumSet.allOf(enumType));
        }
    }

    public static void checkReference(final Class<? extends DataObject> type, final URI id, final String depedency) {
        if (depedency != null) {
            if (depedency.length() == 0) {
                throw APIException.badRequests.resourceCannotBeDeleteDueToUnreachableVdc();
            } else {
                throw APIException.badRequests.resourceHasActiveReferencesWithType(type.getSimpleName(), id, depedency);
            }
        }
    }

    /**
     * Validates that the value supplied matches one of the expected values
     * 
     * @param value the value to check
     * @param fieldName the name of the field where the value originated
     * @param expected the set of values to allow ignoring case
     */
    public static void checkFieldValueFromArray(final Object value, final String fieldName,
            final Object... expected) {
        for (Object entry : expected) {
            if (entry.equals(value)) {
                return;
            }
        }
        throw APIException.badRequests.invalidParameterValueWithExpected(fieldName, value,
                expected);
    }

    /**
     * Validates that the value supplied matches one of the expected values ignoring case
     * 
     * @param value the value to check
     * @param fieldName the name of the field where the value originated
     * @param expected the set of values to allow ignoring case
     */
    public static void checkFieldValueFromArrayIgnoreCase(final String value, final String fieldName,
            final String... expected) {
        for (String entry : expected) {
            if (entry.equalsIgnoreCase(value)) {
                return;
            }
        }
        throw APIException.badRequests.invalidParameterValueWithExpected(fieldName, value,
                expected);
    }

    /**
     * Validates that a condition has passed, providing original and expected
     * values for a clear error message
     * 
     * @param condition the result of checking the value, if false then an exception will be thrown
     * @param fieldName the name of the field where the value originated
     * @param value the value that was checked, used for the error message presentation
     * @param expected the set of allowable values, used for the error message presentation
     */
    public static void checkFieldValueWithExpected(final boolean condition, final String fieldName,
            final Object value, final Object... expected) {
        if (!condition) {
            throw APIException.badRequests.invalidParameterValueWithExpected(fieldName, value,
                    expected);
        }
    }

    /**
     * Validates that a condition has passed, providing original and expected
     * values for a clear error message
     * 
     * @param condition the result of checking the value, if false then an exception will be thrown
     * @param fieldName the name of the field where the value originated
     * @param value the value that was checked, used for the error message presentation
     * @param expected the set of allowable values, used for the error message presentation
     */
    public static void checkFieldValueWithExpected(final boolean condition, final String fieldName,
            final Object value, final Collection<Object> expected) {
        if (!condition) {
            throw APIException.badRequests.invalidParameterValueWithExpected(fieldName, value,
                    expected);
        }
    }

    /**
     * Validates that a named field contains a valid IPv4 or IPv6 address
     * 
     * @param ip a string representation of an IPv4 or IPv6 address
     * @param fieldName the name of the field where the value originated
     */
    public static void checkFieldValidIP(final String ip, final String fieldName) {
        checkFieldNotEmpty(ip, fieldName);
        if (!isValidIPV4(ip) && !isValidIPV6(ip)) {
            throw APIException.badRequests.invalidParameterInvalidIP(fieldName, ip);
        }
    }

    /**
     * Validates that a named field contains a valid IPv4 address
     * 
     * @param ip a string representation of an IPv4 address
     * @param fieldName the name of the field where the value originated
     */
    public static void checkFieldValidIPV4(final String ip, final String fieldName) {
        checkFieldNotEmpty(ip, fieldName);
        if (!isValidIPV4(ip)) {
            throw APIException.badRequests.invalidParameterInvalidIPV4(fieldName, ip);
        }
    }

    /**
     * Validates that a named field contains a valid IPv6 address
     * 
     * @param ip a string representation of an IPv6 address
     * @param fieldName the name of the field where the value originated
     */
    public static void checkFieldValidIPV6(final String ip, final String fieldName) {
        checkFieldNotEmpty(ip, fieldName);
        if (!isValidIPV6(ip)) {
            throw APIException.badRequests.invalidParameterInvalidIPV6(fieldName, ip);
        }
    }

    /**
     * Validates that the supplied DataObject is not null. Null entities will
     * result in a 404 Not Found exception being thrown if idEmbeddedInURL is
     * true, or a 400 Bad Request otherwise.
     * 
     * @param object the DataObject instance to verify
     * @param id the id of the null object, used for the error message presentation
     * @param idEmbeddedInURL true if and only if the id was supplied in the URL
     */
    public static void checkEntityNotNull(final DataObject object, final URI id,
            boolean idEmbeddedInURL) {
        if (object == null) {
            if (idEmbeddedInURL) {
                throw APIException.notFound.unableToFindEntityInURL(id);
            } else {
                throw APIException.badRequests.unableToFindEntity(id);
            }
        }
    }

    /**
     * Validates that the supplied DataObject is not null AND is not inactive.
     * Null or inactive entities will result in a 404 Not Found exception
     * being thrown if idEmbeddedInURL is true, or a 400 Bad Request otherwise.
     * 
     * @param object the DataObject instance to verify
     * @param id the id of the null object, used for the error message presentation
     * @param idEmbeddedInURL true if and only if the id was supplied in the URL
     */
    public static void checkEntity(final DataObject object, final URI id,
            final boolean idEmbeddedInURL) {
        checkEntityNotNull(object, id, idEmbeddedInURL);

        if (object.getInactive()) {
            if (idEmbeddedInURL) {
                throw APIException.notFound.entityInURLIsInactive(id);
            } else {
                throw APIException.badRequests.entityInRequestIsInactive(id);
            }
        }
    }

    /**
     * Validates that the supplied DataObject is not null AND (conditionally)
     * is not inactive. Failing entities will result in a 404 Not Found
     * exception being thrown if idEmbeddedInURL is true, or a 400 Bad Request
     * otherwise.
     * 
     * @param object the DataObject instance to verify
     * @param id the id of the null object, used for the error message presentation
     * @param idEmbeddedInURL true if and only if the id was supplied in the URL
     * @param checkInactive true if and only if an active DataObject is required
     */
    public static void checkEntity(final DataObject object, final URI id,
            final boolean idEmbeddedInURL, final boolean checkInactive) {
        checkEntityNotNull(object, id, idEmbeddedInURL);

        if (checkInactive && object.getInactive()) {
            if (idEmbeddedInURL) {
                throw APIException.notFound.entityInURLIsInactive(id);
            } else {
                throw APIException.badRequests.entityInRequestIsInactive(id);
            }
        }
    }

    /**
     * Validates IPV4 address using regex for the given ipAddress
     * 
     * @param ipAddress IP Address
     * @return {@link Boolean} status flag
     */
    private static Boolean isValidIPV4(final String ipAddress) {
        boolean status = false;
        if (StringUtils.isNotEmpty(ipAddress)) {
            status = InetAddressUtils.isIPv4Address(ipAddress);
        }
        return status;
    }

    /**
     * Validates IPV6 address using regex for the given ipAddress
     * 
     * @param ipAddress IP Address
     * @return {@link Boolean} status flag
     */
    private static Boolean isValidIPV6(final String ipAddress) {
        boolean status = false;
        if (StringUtils.isNotEmpty(ipAddress)) {
            status = InetAddressUtils.isIPv6Address(ipAddress);
        }
        return status;
    }

    /**
     * Validates if a label contains only alphanumeric values
     * 
     * @param label Label to validate
     * @return {@link Boolean} status flag
     */
    private static Boolean isAlphanumeric(final String label) {
        boolean status = false;
        if (StringUtils.isNotEmpty(label)) {
            Matcher matcher = patternAlphanumeric.matcher(label);
            status = matcher.matches();
        }
        return status;
    }

    public static void checkFsName(final String fsName, final String fieldName) {
        checkFieldNotEmpty(fsName, fieldName);
        if (!isAlphanumeric(fsName)) {
            throw APIException.badRequests.invalidFileshareName(fsName);
        }
    }

    public static void checkQuotaDirName(final String quotaDirName, final String fieldName) {
        checkFieldNotEmpty(quotaDirName, fieldName);
        if (!isAlphanumeric(quotaDirName)) {
            throw APIException.badRequests.invalidQuotaDirName(quotaDirName);
        }
    }

    /**
     * Validates that a named field is of minimum or greater value.
     * 
     * @param value the suppled number to check
     * @param minimum the minimum acceptable value
     * @param fieldName the name of the field where the value originated
     */
    public static void checkFieldMinimum(final long value, final long minimum, final String fieldName) {
        checkFieldMinimum(value, minimum, "", fieldName);
    }

    /**
     * Validates that a named field is of minimum or greater value.
     * 
     * @param value the suppled number to check
     * @param minimum the minimum acceptable value
     * @param units the units that the value represents, used for error message presentation
     * @param fieldName the name of the field where the value originated
     */
    public static void checkFieldMinimum(final long value, final long minimum, final String units, final String fieldName) {
        if (value < minimum) {
            throw APIException.badRequests.invalidParameterBelowMinimum(fieldName, value, minimum, units);
        }
    }

    /**
     * Validates that a named field is of maximum or lesser value.
     * 
     * @param value the suppled number to check
     * @param maximum the maximum acceptable value
     * @param fieldName the name of the field where the value originated
     */
    public static void checkFieldMaximum(final long value, final long maximum, final String fieldName) {
        checkFieldMaximum(value, maximum, "", fieldName);
    }

    /**
     * Validates that a named field is of maximum or lesser value.
     * 
     * @param value the suppled number to check
     * @param maximum the maximum acceptable value
     * @param units the units that the value represents, used for error message presentation
     * @param fieldName the name of the field where the value originated
     */
    public static void checkFieldMaximum(final long value, final long maximum, final String units, final String fieldName) {
        if (value > maximum) {
            throw APIException.badRequests.invalidParameterAboveMaximum(fieldName, value, maximum, units);
        }
    }

    /**
     * Validates that a named field is within the inclusive range specified.
     * 
     * @param value the suppled number to check
     * @param minimum the minimum acceptable value
     * @param maximum the maximum acceptable value
     * @param fieldName the name of the field where the value originated
     */
    public static void checkFieldRange(final long value, final long minimum, final long maximum, final String fieldName) {
        checkFieldRange(value, minimum, maximum, "", fieldName);
    }

    /**
     * Validates that a named field is within the inclusive range specified.
     * 
     * @param value the suppled number to check
     * @param minimum the minimum acceptable value
     * @param maximum the maximum acceptable value
     * @param units the units that the value represents, used for error message presentation
     * @param fieldName the name of the field where the value originated
     */
    public static void
            checkFieldRange(final long value, final long minimum, final long maximum, final String units, final String fieldName) {
        if (value < minimum || value > maximum) {
            throw APIException.badRequests.parameterNotWithinRange(fieldName, value, minimum, maximum, units);
        }
    }

    /**
     * Validates that a named field is of maximum or lesser value.
     * 
     * @param value the suppled value to check
     * @param maximum the maximum acceptable value length
     * @param fieldName the name of the field where the value originated
     */
    public static void checkFieldLengthMaximum(final String value, final long maximum, final String fieldName) {
        if (value.length() > maximum) {
            throw APIException.badRequests.invalidParameterLengthTooLong(fieldName, value, maximum);
        }
    }

    /**
     * @deprecated raise a specific exception or use a more specific utility, do not use this method with unlocalized message and parameters
     */
    @Deprecated
    public static void checkArgument(final boolean condition, final String pattern, final Object... parameters) {
        if (!condition) {
            throw new ServiceCodeException(ServiceCode.API_BAD_PARAMETERS, pattern, parameters);
        }
    }

    /**
     * @deprecated raise a specific exception or use a more specific utility, do not use this method with an unlocalized message
     */
    @Deprecated
    public static void checkNotNull(final Object value, final String message) {
        checkArgument(value != null, message);
    }

    /**
     * Check the provided String value is a valid type of enum or not
     * 
     * @param value String need to be checked
     * @param enumClass the enum class for which it need to be checked.
     * @return true/false
     */
    public static <T extends Enum<T>> boolean isValidEnum(String value, Class<T> enumClass) {
        for (T e : enumClass.getEnumConstants()) {
            if (e.name().equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * validates whether the schedule policy parameters are valid or not
     * 
     * @param schedule - schedule policy parameters
     * @param schedulePolicy - schedulePolicy object to set schedule values
     * @param errorMsg - error message
     * @return true/false
     */
    public static boolean validateSchedulePolicyParam(SchedulePolicyParam schedule, SchedulePolicy schedulePolicy, StringBuilder errorMsg) {

        if (schedule != null) {

            // check schedule frequency is valid or not
            if (!isValidEnum(schedule.getScheduleFrequency(), ScheduleFrequency.class)) {
                errorMsg.append("Schedule frequency: " + schedule.getScheduleFrequency()
                        + " is invalid. Valid schedule frequencies are days, weeks and months");
            }

            // validating schedule repeat period
            if (schedule.getScheduleRepeat() < 1) {
                errorMsg.append("required parameter schedule_repeat is missing or value: " + schedule.getScheduleRepeat()
                        + " is invalid");
                return false;
            }

            // validating schedule time
            String period = " PM";
            int hour, minute;
            boolean isValid = true;
            if (schedule.getScheduleTime().contains(":")) {
                String splitTime[] = schedule.getScheduleTime().split(":");
                hour = Integer.parseInt(splitTime[0]);
                minute = Integer.parseInt(splitTime[1]);
                if (splitTime[0].startsWith("-") || splitTime[1].startsWith("-")) {
                    isValid = false;
                }
            } else {
                hour = Integer.parseInt(schedule.getScheduleTime());
                minute = 0;
            }
            if (isValid && (hour >= 0 && hour < 24) && (minute >= 0 && minute < 60)) {
                if (hour < 12) {
                    period = " AM";
                }
            } else {
                errorMsg.append("Schedule time: " + schedule.getScheduleTime() + " is invalid");
                return false;
            }

            switch (schedule.getScheduleFrequency().toLowerCase()) {

                case "days":
                    schedulePolicy.setScheduleRepeat((long) schedule.getScheduleRepeat());
                    schedulePolicy.setScheduleTime(schedule.getScheduleTime() + period);
                    break;
                case "weeks":
                    schedulePolicy.setScheduleRepeat((long) schedule.getScheduleRepeat());
                    if (schedule.getScheduleDayOfWeek() != null && !schedule.getScheduleDayOfWeek().isEmpty()) {
                        List<String> weeks = Arrays.asList("monday", "tuesday", "wednesday", "thursday", "friday",
                                "saturday", "sunday");
                        if (weeks.contains(schedule.getScheduleDayOfWeek().toLowerCase())) {
                            schedulePolicy.setScheduleDayOfWeek(schedule.getScheduleDayOfWeek());
                        } else {
                            errorMsg.append("Schedule day of week: " + schedule.getScheduleDayOfWeek() + " is invalid");
                            return false;
                        }
                    } else {
                        errorMsg.append("required parameter schedule_day_of_week was missing or empty");
                        return false;
                    }
                    schedulePolicy.setScheduleTime(schedule.getScheduleTime() + period);
                    break;
                case "months":
                    if (schedule.getScheduleDayOfMonth() > 0 && schedule.getScheduleDayOfMonth() <= 31) {
                        schedulePolicy.setScheduleDayOfMonth((long) schedule.getScheduleDayOfMonth());
                        schedulePolicy.setScheduleRepeat((long) schedule.getScheduleRepeat());
                        schedulePolicy.setScheduleTime(schedule.getScheduleTime() + period);
                    } else {
                        errorMsg.append("required parameter schedule_day_of_month is missing or value: " + schedule.getScheduleDayOfMonth()
                                + " is invalid");
                        return false;
                    }
                    break;
                default:
                    return false;
            }
        }
        return true;
    }

    /**
     * validates whether the snapshot expire parameters are valid or not
     * 
     * @param expireParam - snapshot expire parameters
     * @return true/false
     */
    public static boolean validateSnapshotExpireParam(ScheduleSnapshotExpireParam expireParam) {

        String expireType = expireParam.getExpireType();
        long seconds = 0;
        long minPeriod = 7200;
        long maxPeriod = 10 * 365 * 24 * 3600;
        int expireValue = expireParam.getExpireValue();
        switch (expireType.toLowerCase()) {
            case "hours":
                seconds = expireValue * 3600;
                break;
            case "days":
                seconds = expireValue * 24 * 3600;
                break;
            case "weeks":
                seconds = expireValue * 7 * 24 * 3600;
                break;
            case "months":
                seconds = expireValue * 30 * 24 * 3600;
                break;
        }
        if (seconds >= minPeriod && seconds <= maxPeriod) {
            return true;
        }
        return false;
    }
}
