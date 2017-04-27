/*
 * Copyright 2015-2016 Dell Inc. or its subsidiaries.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package controllers.catalog;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;

import play.Logger;
import play.data.validation.Validation;
import play.mvc.Controller;
import play.mvc.Util;
import play.mvc.With;
import util.CatalogServiceUtils;
import util.ServiceDescriptorUtils;
import util.TimeUtils;
import util.descriptor.ServiceFieldValidator;

import com.emc.sa.descriptor.ServiceField;
import com.emc.sa.util.TextUtils;
import com.emc.vipr.model.catalog.CatalogServiceFieldRestRep;
import com.emc.vipr.model.catalog.CatalogServiceRestRep;
import com.emc.vipr.model.catalog.OrderCreateParam;
import com.emc.vipr.model.catalog.Parameter;
import com.emc.vipr.model.catalog.ScheduleCycleType;
import com.emc.vipr.model.catalog.ScheduleInfo;
import com.emc.vipr.model.catalog.ScheduledEventCreateParam;
import com.emc.vipr.model.catalog.ServiceDescriptorRestRep;
import com.emc.vipr.model.catalog.ServiceFieldGroupRestRep;
import com.emc.vipr.model.catalog.ServiceFieldModalRestRep;
import com.emc.vipr.model.catalog.ServiceFieldRestRep;
import com.emc.vipr.model.catalog.ServiceFieldTableRestRep;
import com.emc.vipr.model.catalog.ServiceItemRestRep;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import controllers.Common;
import controllers.util.Models;

/**
 * Base order controller that contains operations for executing orders. This is
 * subclassed for the API or main order form.
 * 
 * @author Chris Dail
 */
@With(Common.class)
public class OrderExecution extends Controller {

    /*
     * the services which support scheduler, and can be run re-occurencely
     *   key    -- base service name
     *   value  -- the field name, which can apply {datetime} to.
     */
    private static HashMap<String, String> SCHEDULED_SERVICE =
            new HashMap<String, String>();
    static {
        SCHEDULED_SERVICE.put("CreateCloneOfApplication", "applicationCopySets");
        SCHEDULED_SERVICE.put("CreateSnapshotOfApplication", "applicationCopySets");
        SCHEDULED_SERVICE.put("CreateBlockSnapshot", "name");
        SCHEDULED_SERVICE.put("CreateFullCopy", "name");
        SCHEDULED_SERVICE.put("CreateFileSnapshot", "name");
    }


    @Util
    public static Map<String, String> parseParameters(CatalogServiceRestRep service, ServiceDescriptorRestRep descriptor) {
        Map<String, String> parameters = Maps.newLinkedHashMap();
        Map<String, String> locked = getLockedFields(service);
        addFieldValues(service, descriptor.getItems(), parameters, locked);
        return parameters;
    }

    /**
     * Adds the field values from the service item container to the map.
     * 
     * @param service
     *            the service.
     * @param container
     *            the service item container.
     * @param values
     *            the map holding the values.
     * @param locked
     *            all locked values for the service.
     */
    private static void addFieldValues(CatalogServiceRestRep service, Collection<? extends ServiceItemRestRep> items,
            Map<String, String> values, Map<String, String> locked) {
        for (ServiceItemRestRep item : items) {
            if (item instanceof ServiceFieldTableRestRep) {
                addColumnValues(service, (ServiceFieldTableRestRep) item, values, locked);
            }
            else if (item instanceof ServiceFieldGroupRestRep) {
                addFieldValues(service, ((ServiceFieldGroupRestRep) item).getItems(), values, locked);
            }
            else if (item instanceof ServiceFieldModalRestRep) {
                addFieldValues(service, ((ServiceFieldModalRestRep) item).getItems(), values, locked);
            }
            else if (item instanceof ServiceFieldRestRep) {
                ServiceFieldRestRep field = (ServiceFieldRestRep) item;
                String value = getFieldValue(field);
                if (locked.containsKey(field.getName())) {
                    value = locked.get(field.getName());
                }
                if (value != null) {
                    values.put(field.getName(), value);
                }

                List<String> fieldValues = TextUtils.parseCSV(value);
                if (fieldValues.isEmpty() && field.isRequired()) {
                    Validation.required(field.getName(), null);
                }
                for (String fieldValue : fieldValues) {
                    ServiceFieldValidator.validateField(service, field, fieldValue);
                }
            }
        }
    }

    /**
     * Adds all column values for the given table.
     * 
     * @param service
     *            the catalog service.
     * @param table
     *            the table of fields.
     * @param values
     *            the map holding the values.
     * @param locked
     *            all locked values for the service.
     */
    private static void addColumnValues(CatalogServiceRestRep service, ServiceFieldTableRestRep table, Map<String, String> values,
            Map<String, String> locked) {

        List<ServiceFieldRestRep> fields = ServiceDescriptorUtils.getAllFieldList(table.getItems());

        int rowCount = 0;
        for (ServiceFieldRestRep field : fields) {
            if (!locked.containsKey(field.getName())) {
                String[] columns = getColumnValue(table, field);
                rowCount = Math.max(rowCount, columns.length);
            }
        }

        for (ServiceFieldRestRep field : fields) {
            String[] columns = new String[rowCount];
            if (locked.containsKey(field.getName())) {
                String lockedValue = locked.get(field.getName());
                for (int i = 0; i < columns.length; i++) {
                    columns[i] = lockedValue;
                }
            }
            else {
                String[] col = getColumnValue(table, field);
                System.arraycopy(col, 0, columns, 0, col.length);
            }

            for (int i = 0; i < columns.length; i++) {
                String prefix = table.getName() + "[" + i + "]";
                ServiceFieldValidator.validateField(service, prefix, field, columns[i]);
            }

            values.put(field.getName(), TextUtils.formatCSV(columns));
        }
    }

    /**
     * Gets all locked fields from the catalog service.
     * 
     * @param service
     *            the catalog service.
     * @return the map of locked field values.
     */
    private static Map<String, String> getLockedFields(CatalogServiceRestRep service) {
        Map<String, String> fields = Maps.newLinkedHashMap();
        for (CatalogServiceFieldRestRep field : service.getCatalogServiceFields()) {
            String value = getLockedValue(field);
            if (value != null) {
                fields.put(field.getName(), value);
            }
        }
        return fields;
    }

    private static String getFieldValue(ServiceFieldRestRep field) {
        if (params._contains(field.getName())) {
            String[] values = params.getAll(field.getName());
            if (values != null) {
                return TextUtils.formatCSV(values);
            }
        }
        return null;
    }

    /**
     * Gets the submitted value for the column field from the HTTP params. The
     * parameters are named: <tt>&lt;<i>table.name</i>&gt;[<i>i</i>].&lt;<i>field.name</i>&gt;</tt>
     * 
     * @param table
     *            the table containing the field.
     * @param field
     *            the field.
     * @return the values for the column.
     */
    private static String[] getColumnValue(ServiceFieldTableRestRep table, ServiceFieldRestRep field) {
        List<String> values = Lists.newArrayList();
        Pattern pattern = Pattern.compile(table.getName() + "\\[(\\d+)\\]." + field.getName());
        for (String name : params.data.keySet()) {
            Matcher match = pattern.matcher(name);
            if (match.matches()) {
                int index = Integer.valueOf(match.group(1));
                for (int i = values.size(); i <= index; i++) {
                    values.add(null);
                }
                // changing params.get() to params.getAll() to support list of values in table column
                values.set(index, String.join(",", params.getAll(name)));
            }
        }
        return values.toArray(new String[values.size()]);
    }

    /**
     * Gets the locked value of the field if it is overridden, otherwise returns
     * null.
     * 
     * @param field
     *            the field.
     * @return the locked value, or null.
     */
    private static String getLockedValue(CatalogServiceFieldRestRep field) {
        if (Boolean.TRUE.equals(field.getOverride()) && StringUtils.isNotBlank(field.getValue())) {
            return field.getValue();
        }
        else {
            return null;
        }
    }

    @Util
    public static OrderCreateParam createOrder(CatalogServiceRestRep service, ServiceDescriptorRestRep descriptor,
            Map<String, String> parameters) {

        OrderCreateParam order = new OrderCreateParam();
        order.setTenantId(uri(Models.currentAdminTenant()));
        order.setCatalogService(service.getId());

        List<Parameter> orderParameters = Lists.newArrayList();
        List<ServiceFieldRestRep> fields = ServiceDescriptorUtils.getAllFieldList(descriptor.getItems());
        for (ServiceFieldRestRep field : fields) {
            String value = parameters.get(field.getName());
            if (StringUtils.isNotBlank(value)) {
                orderParameters.add(createOrderParameter(field, value));
            }
        }
        order.setParameters(orderParameters);
        return order;
    }

    private static Parameter createOrderParameter(ServiceFieldRestRep field, String value) {
        Parameter parameter = new Parameter();
        parameter.setLabel(field.getName());
        if (value != null) {
            parameter.setValue(value.trim());
        }
        else {
            parameter.setValue(value); // NOSONAR
                                       // ("Suppressing Sonar violation of Load of known null value. Value can be null and it needs to be set when null")
        }
        parameter.setUserInput(true);
        if (StringUtils.equals(field.getType(), ServiceField.TYPE_PASSWORD)) {
            parameter.setEncrypted(true);
        }
        return parameter;
    }
    
    protected static ScheduledEventCreateParam createScheduledOrder(OrderCreateParam orderParam) {
        if (!isSchedulerEnabled()) {
            return null;
        }
        ScheduleInfo scheduleInfo = new ScheduleInfo();
        String cycleFrequency = params.get("scheduler.cycleFrequency");
        if (cycleFrequency != null) {
            scheduleInfo.setCycleFrequency(Integer.parseInt(cycleFrequency));
        } else {
            scheduleInfo.setCycleFrequency(1);
        }

        String cycleType = params.get("scheduler.cycleType");
        if (cycleType != null) {
            ScheduleCycleType cycleTypeEnum = ScheduleCycleType.valueOf(cycleType);
            scheduleInfo.setCycleType(cycleTypeEnum);
            List<String> sectionsInCycleList = Lists.newArrayList();
            if (cycleTypeEnum == ScheduleCycleType.WEEKLY) {
                String sectionsInCycle = params.get("scheduler.dayOfWeek");
                sectionsInCycleList.add(sectionsInCycle);
            } else if(cycleTypeEnum == ScheduleCycleType.MONTHLY) {
                String sectionsInCycle = params.get("scheduler.dayOfMonth");
                sectionsInCycleList.add(sectionsInCycle);
            }
            scheduleInfo.setSectionsInCycle(sectionsInCycleList);
        } else {
            scheduleInfo.setCycleType(ScheduleCycleType.DAILY);
        }

        String currentTimezoneOffsetInMins = params.get("scheduler.currentTimezoneOffsetInMins");
        Integer timezoneOffset = Integer.parseInt(currentTimezoneOffsetInMins);
        
        String startDate = params.get("scheduler.startDate");
        String startTime = params.get("scheduler.startTime");
        
        String isoDateTimeStr = String.format("%sT%s", startDate, startTime);
        DateTime startDateTime = DateTime.parse(isoDateTimeStr, ISODateTimeFormat.localDateOptionalTimeParser().withZone(TimeUtils.getLocalTimeZone(timezoneOffset)));
        startDateTime = startDateTime.withZone(DateTimeZone.UTC);
        scheduleInfo.setHourOfDay(startDateTime.getHourOfDay());
        scheduleInfo.setMinuteOfHour(startDateTime.getMinuteOfHour());
        scheduleInfo.setStartDate(String.format("%d-%02d-%02d", startDateTime.getYear(), startDateTime.getMonthOfYear(), startDateTime.getDayOfMonth()));
        
        String recurrence = params.get("scheduler.recurrence");
        int recurrenceNum = 1;
        if (recurrence != null) {
            recurrenceNum = Integer.parseInt(recurrence);
            if (recurrenceNum == -1) {
                String range = params.get("scheduler.rangeOfRecurrence");
                recurrenceNum = Integer.parseInt(range);
            }
        }
        scheduleInfo.setReoccurrence(recurrenceNum);

        /*
         * if reoccurence number large than 1, we must make sure the name contains patten {datetime},
         * with the pattern in the name, vipr know how to generate dynamic name for each snaphot/fullcopy.
         */
        if (recurrenceNum != 1) {
            List<Parameter> parameters = orderParam.getParameters();
            CatalogServiceRestRep service = CatalogServiceUtils.getCatalogService(orderParam.getCatalogService());
            Logger.info("creating order with parameter for: " + service.getBaseService());
            String nameToValidate = SCHEDULED_SERVICE.get(service.getBaseService());
            for (Parameter parameter : parameters) {
                if (parameter.getLabel().equals(nameToValidate) &&
                        !parameter.getValue().contains("{datetime}")) {
                    Validation.addError(nameToValidate, "need to add patten '{datetime}' in the name for reoccuring scheduled operation");
                }
                Logger.info(parameter.getLabel() + " = " + parameter.getValue() + ", "
                        + parameter.getFriendlyLabel() + " = " + parameter.getFriendlyValue());
            }
        }

        
        String maxNumOfCopies = params.get("scheduler.maxNumOfCopies");
        if (maxNumOfCopies != null) {
        	orderParam.setAdditionalScheduleInfo(maxNumOfCopies);
        }
        
        scheduleInfo.setDurationLength(3600);
        ScheduledEventCreateParam eventParam = new ScheduledEventCreateParam();
        eventParam.setOrderCreateParam(orderParam);
        eventParam.setScheduleInfo(scheduleInfo);
        
        return eventParam;
    }
    
    protected static boolean isSchedulerEnabled() {
        if (params._contains("schedulerEnabled")) {
             return Boolean.valueOf(params.get("schedulerEnabled"));
        }
        return false;
    }

}
