/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.uimodels;

import com.emc.storageos.db.client.model.*;
import com.emc.storageos.model.valid.EnumType;

import java.net.URI;

@Cf("ExecutionWindow")
public class ExecutionWindow extends ModelObject implements TenantDataObject {

    /** URI to indicate that the next execution window should be used. */
    public static final URI NEXT = URI.create("urn:storageos:ExecutionWindow:NEXT:");

    public static final String HOUR_OF_DAY_IN_UTC = "hourOfDayInUTC";
    public static final String MINUTE_OF_DAY_IN_UTC = "mintueOfHourInUTC";
    public static final String EXECUTION_WINDOW_LENGTH = "executionWindowLength";
    public static final String EXECUTION_WINDOW_LENGTH_TYPE = "executionWindowLengthType";
    public static final String EXECUTION_WINDOW_TYPE = "executionWindowType";
    public static final String DAY_OF_WEEK = "dayOfWeek";
    public static final String DAY_OF_MONTH = "dayOfMonth";
    public static final String LAST_DAY_OF_MONTH = "lastDayOfMonth";
    public static final String TENANT = TenantDataObject.TENANT_COLUMN_NAME;

    private Integer hourOfDayInUTC;

    private Integer minuteOfHourInUTC;

    private Integer executionWindowLength;

    private String executionWindowLengthType;

    private String executionWindowType;

    private Integer dayOfWeek;

    private Integer dayOfMonth;

    private Boolean lastDayOfMonth = Boolean.FALSE;

    private String tenant;

    @Name(HOUR_OF_DAY_IN_UTC)
    public Integer getHourOfDayInUTC() {
        return hourOfDayInUTC;
    }

    public void setHourOfDayInUTC(Integer hourOfDayInUTC) {
        this.hourOfDayInUTC = hourOfDayInUTC;
        setChanged(HOUR_OF_DAY_IN_UTC);
    }

    @Name(MINUTE_OF_DAY_IN_UTC)
    public Integer getMinuteOfHourInUTC() {
        return minuteOfHourInUTC;
    }

    public void setMinuteOfHourInUTC(Integer minuteOfHourInUTC) {
        this.minuteOfHourInUTC = minuteOfHourInUTC;
        setChanged(MINUTE_OF_DAY_IN_UTC);
    }

    @Name(EXECUTION_WINDOW_LENGTH)
    public Integer getExecutionWindowLength() {
        return executionWindowLength;
    }

    public void setExecutionWindowLength(Integer executionWindowLength) {
        this.executionWindowLength = executionWindowLength;
        setChanged(EXECUTION_WINDOW_LENGTH);
    }

    @EnumType(ExecutionWindowLengthType.class)
    @Name(EXECUTION_WINDOW_LENGTH_TYPE)
    public String getExecutionWindowLengthType() {
        return executionWindowLengthType;
    }

    public void setExecutionWindowLengthType(String executionWindowLengthType) {
        this.executionWindowLengthType = executionWindowLengthType;
        setChanged(EXECUTION_WINDOW_LENGTH_TYPE);
    }

    @EnumType(ExecutionWindowType.class)
    @Name(EXECUTION_WINDOW_TYPE)
    public String getExecutionWindowType() {
        return executionWindowType;
    }

    public void setExecutionWindowType(String executionWindowType) {
        this.executionWindowType = executionWindowType;
        setChanged(EXECUTION_WINDOW_TYPE);
    }

    @Name(DAY_OF_WEEK)
    public Integer getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(Integer dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
        setChanged(DAY_OF_WEEK);
    }

    @Name(DAY_OF_MONTH)
    public Integer getDayOfMonth() {
        return dayOfMonth;
    }

    public void setDayOfMonth(Integer dayOfMonth) {
        this.dayOfMonth = dayOfMonth;
        setChanged(DAY_OF_MONTH);
    }

    @Name(LAST_DAY_OF_MONTH)
    public Boolean getLastDayOfMonth() {
        return lastDayOfMonth;
    }

    public void setLastDayOfMonth(Boolean lastDayOfMonth) {
        this.lastDayOfMonth = lastDayOfMonth;
        setChanged(LAST_DAY_OF_MONTH);
    }

    @AlternateId("TenantToExecutionWindow")
    @Name(TENANT)
    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
        setChanged(TENANT);
    }

    /**
     * Determines if the NamedURI refers to the 'next' execution window.
     * 
     * @param id
     *            the named URI id.
     * @return true if the named URI refers to the next window.
     */
    public static boolean isNextWindow(NamedURI id) {
        // If no window is set or if it actually the URI for the next window
        return (id == null) || isNextWindow(id.getURI());
    }

    /**
     * Determines if this ID corresponds to the 'next' execution window.
     * 
     * @param id
     *            the ID.
     * @return true if the ID refers to the next window.
     */
    public static boolean isNextWindow(URI id) {
        return NEXT.equals(id);
    }

    @Override
    public Object[] auditParameters() {
        return new Object[] { getLabel(), getTenant(), getId() };
    }
}
