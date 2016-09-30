/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.catalog;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jettison.json.JSONObject;

import play.data.validation.Validation;
import play.mvc.Controller;
import play.mvc.Util;
import play.mvc.With;
import util.ServiceDescriptorUtils;
import util.descriptor.ServiceFieldValidator;

import com.emc.sa.descriptor.ServiceField;
import com.emc.sa.util.TextUtils;
import com.emc.vipr.model.catalog.CatalogServiceFieldRestRep;
import com.emc.vipr.model.catalog.CatalogServiceRestRep;
import com.emc.vipr.model.catalog.OrderCreateParam;
import com.emc.vipr.model.catalog.Parameter;
import com.emc.vipr.model.catalog.ServiceDescriptorRestRep;
import com.emc.vipr.model.catalog.ServiceFieldGroupRestRep;
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
    	String externalParam = "externalParam";
        for (ServiceItemRestRep item : items) {
            if (item instanceof ServiceFieldTableRestRep) {
                addColumnValues(service, (ServiceFieldTableRestRep) item, values, locked);
            }
            else if (item instanceof ServiceFieldGroupRestRep) {
                addFieldValues(service, ((ServiceFieldGroupRestRep) item).getItems(), values, locked);
            }
            else if (item instanceof ServiceFieldRestRep) {
                ServiceFieldRestRep field = (ServiceFieldRestRep) item;
                String value = getFieldValue(field);
                if(field.getName().equals(externalParam)){
                	field.setRequired(false);
                	if (value != null) {
                        values.put(field.getName(), value);
                    }else{
                    	values.put(field.getName(), externalParam);
                    }
                }
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
        if(values.containsKey(externalParam)){
        	values.put(externalParam, new JSONObject(values).toString());
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
                values.set(index, params.get(name));
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
}
