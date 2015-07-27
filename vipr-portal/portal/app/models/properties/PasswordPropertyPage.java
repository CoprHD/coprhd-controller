/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package models.properties;

import com.emc.storageos.security.password.Constants;
import com.emc.storageos.security.password.ValidatorFactory;

import java.util.Map;

public class PasswordPropertyPage extends CustomPropertyPage {
    private Property passwordChagneInterval;
    private Property passwordMinLength;
    private Property passwordLowercaseNumber;
    private Property passwordUppercaseNumber;
    private Property passwordNumericNumber;
    private Property passwordSpecialNumber;
    private Property passwordRepeatingNumber;
    private Property passwordChangedNumber;
    private Property passwordPreventDictionary;
    private Property passwordReuseNumber;
    private Property passwordExpireDays;


    public PasswordPropertyPage(Map<String, Property> properties) {
        super("Password");
        setRenderTemplate("passwordPage.html");
        passwordChagneInterval = addCustomProperty(properties, Constants.PASSWORD_CHANGE_INTERVAL);
        passwordMinLength = addCustomProperty(properties, Constants.PASSWORD_MIN_LENGTH);
        passwordLowercaseNumber = addCustomProperty(properties, Constants.PASSWORD_LOWERCASE_NUMBER);
        passwordUppercaseNumber = addCustomProperty(properties, Constants.PASSWORD_UPPERCASE_NUMBER);
        passwordNumericNumber = addCustomProperty(properties, Constants.PASSWORD_NUMERIC_NUMBER);
        passwordSpecialNumber = addCustomProperty(properties, Constants.PASSWORD_SPECIAL_NUMBER);
        passwordRepeatingNumber = addCustomProperty(properties, Constants.PASSWORD_REPEATING_NUMBER);
        passwordChangedNumber = addCustomProperty(properties, Constants.PASSWORD_CHANGED_NUMBER);
        passwordPreventDictionary = addCustomProperty(properties, Constants.PASSWORD_PREVENT_DICTIONARY);
        passwordReuseNumber = addCustomProperty(properties, Constants.PASSWORD_REUSE_NUMBER);
        passwordExpireDays = addCustomProperty(properties, Constants.PASSWORD_EXPIRE_DAYS);
;
    }

    public Property getPasswordChagneInterval() {
        return passwordChagneInterval;
    }
    public Property getPasswordMinLength() {
        return passwordMinLength;
    }
    public Property getPasswordLowercaseNumber() {
        return passwordLowercaseNumber;
    }
    public Property getPasswordUppercaseNumber() {
        return passwordUppercaseNumber;
    }
    public Property getPasswordNumericNumber() {
        return passwordNumericNumber;
    }
    public Property getPasswordSpecialNumber() {
        return passwordSpecialNumber;
    }
    public Property getPasswordRepeatingNumber() {
        return passwordRepeatingNumber;
    }
    public Property getPasswordChangedNumber() {
        return passwordChangedNumber;
    }
    public Property getPasswordPreventDictionary() {
        return passwordPreventDictionary;
    }
    public Property getPasswordReuseNumber() {
        return passwordReuseNumber;
    }
    public Property getPasswordExpireDays() {
        return passwordExpireDays;
    }
}
