/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util.descriptor;

import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

import play.data.validation.Validation;
import util.MessagesUtils;

import com.emc.sa.descriptor.ServiceField;
import com.emc.vipr.model.catalog.CatalogServiceRestRep;
import com.emc.vipr.model.catalog.ServiceFieldRestRep;

/**
 * Support class to handle validation of ServiceFields.
 * 
 * @author jonnymiller
 */
public class ServiceFieldValidator {

    /** Message key for invalid numbers. */
    private static final String INVALID_INTEGER_KEY = "order.validation.notInteger";
    private static final String INVALID_NUMBER_KEY = "order.validation.notNumber";
    /** Message key for invalid regex. */
    private static final String INVALID_REGEX_KEY = "order.validation.notMatchRegex";
    /** The regular expression for numbers. */
    private static final String INTEGER_REGEX = "[\\-]?\\b\\d+\\b";
    private static final String NUMBER_REGEX = "[-+]?[0-9]*\\.?[0-9]+";

    public static void validateField(CatalogServiceRestRep service, ServiceFieldRestRep field, String value) {
        validateField(service, null, field, value);
    }

    public static void validateField(CatalogServiceRestRep service, String prefix, ServiceFieldRestRep field, String value) {
        String fieldName = StringUtils.isNotBlank(prefix) ? prefix + "." + field.getName() : field.getName();

        if (field.isRequired()) {
            validateRequiredField(fieldName, value);
        }

        if (ServiceField.TYPE_NUMBER.equals(field.getType())) {
            validateIntegerField(service, field, fieldName, value);
        }
        else if (ServiceField.TYPE_TEXT.equals(field.getType())) {
            validateTextField(service, field, fieldName, value);
        }
        else if (ServiceField.TYPE_STORAGE_SIZE.equals(field.getType())) {
            validateStorageSizeField(service, field, fieldName, value);
        }
        else if (ServiceField.TYPE_EXPAND_SIZE.equals(field.getType())) {
            validateExpandSizeField(service, field, fieldName, value);
        }
        else if (ServiceField.TYPE_BOOLEAN.equals(field.getType())) {
            validateBooleanField(service, field, fieldName, value);
        }
    }

    public static void validateRequiredField(String fieldName, String value) {
        Validation.required(fieldName, value);
    }

    /**
     * Validates a number field.
     * 
     * @param service
     *            the catalog service.
     * @param fieldName
     *            the name of the field to validate.
     * @param value
     *            the field value.
     * @param validation
     *            the validation configuration.
     */
    private static void
            validateNumberField(CatalogServiceRestRep catalogService, ServiceFieldRestRep field, String fieldName, String value) {
        if (StringUtils.isNotBlank(value)) {
            validateNumber(fieldName, value);
            validateRange(fieldName, value, field.getMin(), field.getMax());
        }
    }

    /**
     * Validates an integer field.
     * 
     * @param service
     *            the catalog service.
     * @param fieldName
     *            the name of the field to validate.
     * @param value
     *            the field value.
     * @param validation
     *            the validation configuration.
     */
    private static void
            validateIntegerField(CatalogServiceRestRep catalogService, ServiceFieldRestRep field, String fieldName, String value) {
        if (StringUtils.isNotBlank(value)) {
            validateInteger(fieldName, value);
            validateRange(fieldName, value, field.getMin(), field.getMax());
        }
    }

    /**
     * Validates a text field.
     * 
     * @param service
     *            the catalog service.
     * @param fieldName
     *            the name of the field to validate.
     * @param value
     *            the field value.
     * @param validation
     *            the validation configuration.
     */
    private static void validateTextField(CatalogServiceRestRep catalogService, ServiceFieldRestRep field, String fieldName, String value) {
        if (StringUtils.isNotBlank(value)) {
            validateRegex(fieldName, value, field.getRegEx(), field.getFailureMessage());
            validateLength(fieldName, value, field.getMin(), field.getMax());
        }
    }

    /**
     * Validates a storage size field.
     * 
     * @param service
     *            the catalog service.
     * @param fieldName
     *            the name of the field to validate.
     * @param value
     *            the field value.
     * @param validation
     *            the validation configuration.
     */
    private static void validateStorageSizeField(CatalogServiceRestRep catalogService, ServiceFieldRestRep field, String fieldName,
            String value) {
        validateNumber(fieldName, value);

        boolean hasMinSize = field.getMin() != null;
        boolean hasMaxSize = (catalogService.getMaxSize() != null) && (catalogService.getMaxSize() >= 1);

        Integer min = hasMinSize ? field.getMin() : 0;
        Integer max = hasMaxSize ? catalogService.getMaxSize() : null;

        validateRange(fieldName, value, min, max);
    }

    /**
     * Validates a storage size field during expansion.
     * 
     * @param service
     *            the catalog service.
     * @param fieldName
     *            the name of the field to validate.
     * @param value
     *            the field value.
     * @param validation
     *            the validation configuration.
     */
    private static void validateExpandSizeField(CatalogServiceRestRep catalogService, ServiceFieldRestRep field, String fieldName,
            String value) {
        validateNumber(fieldName, value);

        boolean hasMinSize = field.getMin() != null;
        boolean hasMaxSize = (catalogService.getMaxSize() != null) && (catalogService.getMaxSize() >= 1);

        Integer min = Math.max(1, hasMinSize ? field.getMin() : 1);
        Integer max = hasMaxSize ? catalogService.getMaxSize() : null;

        validateRange(fieldName, value, min, max);
    }
    private static void validateBooleanField(CatalogServiceRestRep catalogServiceRestRep, ServiceFieldRestRep field, String fieldName,
            String value) {
        if (StringUtils.isNotBlank(value)) {
            validateRegex(fieldName, value, field.getRegEx(), field.getFailureMessage());
            validateLength(fieldName, value, field.getMin(), field.getMax());
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
        validateRegex(fieldName, value, NUMBER_REGEX, MessagesUtils.get(INVALID_NUMBER_KEY));
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
        validateRegex(fieldName, value, INTEGER_REGEX, MessagesUtils.get(INVALID_INTEGER_KEY));
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
     *            the error message to display if the value does not match, if blank
     *            a default message is used.
     */
    private static void validateRegex(String fieldName, String value, String pattern, String errorMessage) {
        if (!matches(pattern, value)) {
            if (StringUtils.isNotBlank(errorMessage)) {
                Validation.addError(fieldName, errorMessage);
            }
            else {
                Validation.addError(fieldName, MessagesUtils.get(INVALID_REGEX_KEY, pattern));
            }
        }
    }

    private static boolean matches(String pattern, String value) {
        // Null value should be considered matching. If it is required, should
        // be picked up by required field
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

    /**
     * Validates range value of the field.
     * 
     * @param name
     *            the field name.
     * @param value
     *            the field value.
     * @param min
     *            the minimum value.
     * @param max
     *            the maximum value.
     */
    private static void validateRange(String name, Object value, Integer min, Integer max) {
        if (min != null) {
            Validation.min(name, value, min);
        }
        if (max != null) {
            Validation.max(name, value, max);
        }
    }

    /**
     * Validates the length of the field.
     * 
     * @param name
     *            the field name.
     * @param value
     *            the field value.
     * @param minSize
     *            the minimum size.
     * @param maxSize
     *            the maximum size.
     */
    private static void validateLength(String name, Object value, Integer minSize, Integer maxSize) {
        if (minSize != null) {
            Validation.minSize(name, value, minSize);
        }
        if (maxSize != null) {
            Validation.maxSize(name, value, maxSize);
        }
    }
}
