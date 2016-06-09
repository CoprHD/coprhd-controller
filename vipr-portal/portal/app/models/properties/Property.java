/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.properties;

import com.emc.storageos.model.property.PropertyMetadata;
import com.emc.storageos.systemservices.impl.validate.PropertiesConfigurationValidator;
import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;
import play.Logger;
import play.data.validation.Validation;
import util.MessagesUtils;
import util.PasswordUtil;
import util.SetupUtils;

import java.util.Arrays;
import java.util.Set;

import static com.emc.storageos.model.property.PropertyConstants.*;

public class Property {
    public static final Integer LARGE_TEXT_FIELD_THRESHOLD = 1000;
    public static final String ENCRYPTED = "encrypted";
    public static final String BOOLEAN = "boolean";

    public static final PropertiesConfigurationValidator VALIDATOR = new PropertiesConfigurationValidator();
    private String name;
    private String value;
    private PropertyMetadata metadata;
    private boolean valueHidden;
    private boolean passwordField;
    private boolean booleanField;

    public Property(String name, String value, PropertyMetadata meta) {
        this.name = name;
        this.value = value;
        this.metadata = meta;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        String s = value;
        if (isEncryptedField()) {
            s = PasswordUtil.decrypt(s);
        }
        if (isPasswordField()) {
            s = PasswordUtil.encryptedValue(s);
        }
        return s;
    }

    public String getPageName() {
        return metadata.getTag();
    }

    public String getLabel() {
        String key = "configProperty." + name;
        String label = MessagesUtils.get(key);
        if (StringUtils.equals(label, key)) {
            label = metadata.getLabel();
        }
        return label;
    }

    public String getDescription() {
        String key = "configProperty." + name + ".description";
        String description = MessagesUtils.get(key);
        if (StringUtils.equals(description, key)) {
            description = metadata.getDescription();
        }
        if (SetupUtils.isOssBuild()) {
            description = description.replace("EMC", "");
        }
        return description;
    }

    public Set<String> getAllowedValues() {
        Set<String> allowedValues = Sets.newLinkedHashSet();
        if (metadata.getAllowedValues() != null) {
            allowedValues.addAll(Arrays.asList(metadata.getAllowedValues()));
        }
        return allowedValues;
    }

    public boolean isRebootRequired() {
        return Boolean.TRUE.equals(metadata.getRebootRequired());
    }

    public boolean isLargeField() {
        boolean textType = metadata.getType().equals(TEXT) || metadata.getType().equals(ENCRYPTEDTEXT);
        boolean stringType = metadata.getType().equals(STRING) || metadata.getType().equals(ENCRYPTEDSTRING);

        int maxLen = metadata.getMaxLen() != null ? metadata.getMaxLen() : 0;
        // Only check the length for string types, the maxLen field seems to be used for numeric fields as the maxValue
        boolean veryLongString = stringType && maxLen > LARGE_TEXT_FIELD_THRESHOLD;

        return textType || veryLongString;
    }

    public boolean isEncryptedField() {
        return metadata.getType().contains(ENCRYPTED);
    }

    public boolean isBooleanField() {
        return booleanField || metadata.getType().equalsIgnoreCase(BOOLEAN);
    }

    public boolean isPasswordField() {
        return passwordField || isEncryptedField();
    }

    public void setPasswordField(boolean password) {
        this.passwordField = password;
    }

    public boolean isValueHidden() {
        return valueHidden;
    }

    public void setValueHidden(boolean valueHidden) {
        this.valueHidden = valueHidden;
    }

    public PropertyMetadata getMetadata() {
        return metadata;
    }

    /**
     * Gets the maximum length for the field input (characters).
     * 
     * @return the maximum field length.
     */
    public int getMaxLength() {
        String type = metadata.getType();
        Integer maxLen = metadata.getMaxLen();

        Set<String> numericTypes = Sets.newHashSet(PERCENT, UINT8, UINT16, UINT32, UINT64);
        if (numericTypes.contains(type)) {
            if (maxLen == null) {
                // Sensible default
                return String.valueOf(Long.MAX_VALUE).length();
            }
            else {
                return String.valueOf(maxLen).length();
            }
        }
        else {
            return maxLen;
        }
    }

    public void validate(String value) {
        // Skip validation for properties with hidden value
        if (isValueHidden() && StringUtils.isBlank(value)) {
            return;
        }
        // No validation on blank fields
        if (StringUtils.isNotBlank(value)) {
            String fieldName = name;
            validateField(fieldName, StringUtils.defaultString(value, ""));
        }
    }

    @SuppressWarnings("static-access")
	protected void validateField(String fieldName, String value) {
        if (isPasswordField()) {
            value = PasswordUtil.decryptedValue(value);
        }

        String type = metadata.getType();
        if (EMAIL.equals(type)) {
            if (!VALIDATOR.validateEmail(value)) {
                Validation.addError(fieldName, "configProperties.error.email");
            }
        }
        else if (EMAILLIST.equals(type)) {
            if (!VALIDATOR.validateEmailList(value)) {
                Validation.addError(fieldName, "configProperties.error.emaillist");
            }
        }
        else if (HOSTNAME.equals(type)) {
            if (!VALIDATOR.validateHostName(value)) {
                Validation.addError(fieldName, "configProperties.error.hostname");
            }
        }
        else if (STRICTHOSTNAME.equals(type)) {
            if (!VALIDATOR.validateStrictHostName(value)) {
                Validation.addError(fieldName, "configProperties.error.hostname");
            }
        }
        else if (IPADDR.equals(type)) {
            if (!VALIDATOR.validateIpv4Addr(value)) {
                Validation.addError(fieldName, "configProperties.error.ipaddr");
            }
        }
        else if (IPV6ADDR.equals(type)) {
            if (!VALIDATOR.validateIpv6Addr(value)) {
                Validation.addError(fieldName, "configProperties.error.ipv6addr");
            }
        }
        else if (IPLIST.equals(type)) {
            if (!VALIDATOR.validateIpList(value)) {
                Validation.addError(fieldName, "configProperties.error.iplist");
            }
        }
        else if (PERCENT.equals(type)) {
            if (!VALIDATOR.validatePercent(value)) {
                Validation.addError(fieldName, "configProperties.error.percent");
            }
        }
        else if (UINT8.equals(type)) {
            if (!VALIDATOR.validateUint8(value)) {
                Validation.addError(fieldName, "configProperties.error.uint8");
            }
        }
        else if (UINT16.equals(type)) {
            if (!VALIDATOR.validateUint16(value)) {
                Validation.addError(fieldName, "configProperties.error.uint16");
            }
        }
        else if (UINT32.equals(type)) {
            if (!VALIDATOR.validateUint32(value)) {
                Validation.addError(fieldName, "configProperties.error.uint32");
            }
        }
        else if (UINT64.equals(type)) {
            if (!VALIDATOR.validateUint64(value)) {
                Validation.addError(fieldName, "configProperties.error.uint64");
            }
        }
        else if (URL.equals(type)) {
            if (!VALIDATOR.validateUrl(value)) {
                Validation.addError(fieldName, "configProperties.error.url");
            }
        }
        else if (IPPORTLIST.equals(type)) {
            if (!VALIDATOR.validateIpPortList(value)) {
                Validation.addError(fieldName, "configProperties.error.ipportlist");
            }
        }        
        else if (STRING.equals(type) || ENCRYPTEDSTRING.equals(type) || TEXT.equals(type) || ENCRYPTEDTEXT.equals(type)) {
            Integer minLen = metadata.getMinLen();
            Integer maxLen = metadata.getMaxLen();
            if ((minLen != null) && (StringUtils.length(value) < minLen)) {
                Validation.addError(fieldName, "configProperties.error.minLen", minLen.toString());
            }
            if ((maxLen != null) && (StringUtils.length(value) > maxLen)) {
                Validation.addError(fieldName, "configProperties.error.maxLen", String.valueOf(maxLen));
            }
        }
        else {
            Logger.error("Unknown property type: %s", type);
        }

        // Check the allowed values
        Set<String> allowedValues = getAllowedValues();
        if ((allowedValues != null) && (!allowedValues.isEmpty())) {
            if (!allowedValues.contains(value)) {
                String values = StringUtils.join(allowedValues, ",");
                Validation.addError(fieldName, "configProperties.error.allowedValues", values);
            }
        }
    }

    public void setBooleanField(boolean b) {
        this.booleanField = b;
    }

}
