/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.security.password;

import com.emc.storageos.security.password.rules.*;

import static com.emc.storageos.security.password.Constants.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;

public class ValidatorFactory {

    /**
     * build validator for UpdatePassword API
     *
     * @param properties
     * @param passwordUtils
     * @return
     */
    public static PasswordValidator buildUpdateValidator(Map<String, String> properties, PasswordUtils passwordUtils) {
        List<Rule> ruleList = buildBaseRuleList(properties);
        ruleList.add(new HistoryRule(NumberUtils.toInt(properties.get(PASSWORD_REUSE_NUMBER), 3), passwordUtils));
        ruleList.add(new ChangedNumberRule(NumberUtils.toInt(properties.get(PASSWORD_CHANGED_NUMBER), 2)));
        ruleList.add(new ChangeIntervalRule(NumberUtils.toInt(properties.get(PASSWORD_CHANGE_INTERVAL), 60)));
        PasswordValidator validator = new PasswordValidator(ruleList);
        return validator;
    }

    /**
     * build validator for /password/validate API
     * 
     * @param properties
     * @return
     */
    public static PasswordValidator buildContentValidator(Map<String, String> properties) {
        List<Rule> ruleList = buildBaseRuleList(properties);
        PasswordValidator validator = new PasswordValidator(ruleList);
        return validator;
    }

    /**
     * build validator for Reset Password API
     * 
     * @param properties
     * @return
     */
    public static PasswordValidator buildResetValidator(Map<String, String> properties) {
        return buildContentValidator(properties);
    }

    /**
     * build validator for Change Password API
     * 
     * @param properties
     * @param passwordUtils
     * @return
     */
    public static PasswordValidator buildChangeValidator(Map<String, String> properties, PasswordUtils passwordUtils) {
        return buildUpdateValidator(properties, passwordUtils);
    }

    /**
     * build validator for ExpireRule, which used in login.
     * 
     * @param properties
     * @return
     */
    public static PasswordValidator buildExpireValidator(Map<String, String> properties) {
        List<Rule> ruleList = new ArrayList<Rule>();
        ruleList.add(new ExpireRule(NumberUtils.toInt(properties.get(PASSWORD_EXPIRE_DAYS), 0)));
        PasswordValidator validator = new PasswordValidator(ruleList);
        return validator;
    }

    /**
     * Build the base list of rules shared by most validators
     * 
     * @param properties
     * @return the rule list
     */
    private static List<Rule> buildBaseRuleList(Map<String, String> properties) {
        List<Rule> ruleList = new ArrayList<Rule>();
        ruleList.add(new LengthRule(NumberUtils.toInt(properties.get(PASSWORD_MIN_LENGTH), 8)));
        ruleList.add(CharacterRuleFactory.getCharacterRule(
                CharacterRuleFactory.CharacterRuleType.LOWERCASE,
                NumberUtils.toInt(properties.get(PASSWORD_LOWERCASE_NUMBER), 1)));
        ruleList.add(CharacterRuleFactory.getCharacterRule(
                CharacterRuleFactory.CharacterRuleType.UPPERCASE,
                NumberUtils.toInt(properties.get(PASSWORD_UPPERCASE_NUMBER), 1)));
        ruleList.add(CharacterRuleFactory.getCharacterRule(
                CharacterRuleFactory.CharacterRuleType.NUMERIC,
                NumberUtils.toInt(properties.get(PASSWORD_NUMERIC_NUMBER), 1)));
        ruleList.add(CharacterRuleFactory.getCharacterRule(
                CharacterRuleFactory.CharacterRuleType.SPECIAL,
                NumberUtils.toInt(properties.get(PASSWORD_SPECIAL_NUMBER), 1)));
        ruleList.add(new RepeatingCharacterRule(NumberUtils.toInt(properties.get(PASSWORD_REPEATING_NUMBER), 3)));
        if (StringUtils.equalsIgnoreCase(properties.get(PASSWORD_PREVENT_DICTIONARY), "yes")) {
            ruleList.add(new DictionaryRule(new ListDictionary()));
        }
        return ruleList;
    }
}
