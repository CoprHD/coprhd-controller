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

package com.emc.storageos.security.password.rules;

import com.emc.storageos.security.password.Password;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class CharacterRuleFactory {
    private static final Logger _log = LoggerFactory.getLogger(CharacterRuleFactory.class);

    public enum CharacterRuleType {
        LOWERCASE,
        UPPERCASE,
        NUMERIC,
        SPECIAL,
    }

    public static CharacterRule getCharacterRule(CharacterRuleType type, int num) {
        CharacterRule rule = null;
        switch (type) {
            case LOWERCASE:
                rule = new LowercaseCharacterRule(num);
                break;
            case UPPERCASE:
                rule = new UppercaseCharacterRule(num);
                break;
            case NUMERIC:
                rule = new NumericCharacterRule(num);
                break;
            case SPECIAL:
                rule = new SpecialCharacterRule(num);
                break;
        }

        return rule;
    }

    /**
     * Rule for determining if a password contains the correct number of lowercase
     * characters.
     */
    private static class LowercaseCharacterRule extends AbstractCharacterRule {

        private static final String CHARACTER_TYPE = "lowercase";

        public LowercaseCharacterRule(final int num) {
            setNumberOfCharacters(num);
        }

        @Override
        public int getNumber(Password password) {
            return password.getNumberOfLowercase();
        }

        @Override
        public String getType() {
            return CHARACTER_TYPE;
        }

        @Override
        public BadRequestException getException() {
            return BadRequestException.badRequests.passwordInvalidLowercaseNumber(getNumberOfCharacters());
        }
    }

    /**
     * Rule for determining if a password contains the correct number of numeric characters.
     */
    public static class NumericCharacterRule extends AbstractCharacterRule {
        private static final String TYPE = "numeric";

        public NumericCharacterRule(final int num) {
            setNumberOfCharacters(num);
        }

        @Override
        public int getNumber(Password password) {
            return password.getNumberOfDigits();
        }

        @Override
        public String getType() {
            return TYPE;
        }

        @Override
        public BadRequestException getException() {
            return BadRequestException.badRequests.passwordInvalidNumericNumber(getNumberOfCharacters());
        }
    }

    /**
     * Rule for determining if a password contains correct number of special characters.
     */
    public static class SpecialCharacterRule extends AbstractCharacterRule {

        private static final String CHARACTER_TYPE = "special";

        public SpecialCharacterRule(final int num) {
            setNumberOfCharacters(num);
        }

        @Override
        public int getNumber(Password password) {
            return password.getNumberOfSpeicial();
        }

        @Override
        public String getType() {
            return CHARACTER_TYPE;
        }

        @Override
        public BadRequestException getException() {
            return BadRequestException.badRequests.passwordInvalidSpecialNumber(getNumberOfCharacters());
        }
    }

    /**
     * Rule for determining if a password contains the correct number of lowercase
     * characters.
     */
    public static class UppercaseCharacterRule extends AbstractCharacterRule {

        private static final String CHARACTER_TYPE = "uppercase";

        public UppercaseCharacterRule(final int num) {
            setNumberOfCharacters(num);
        }

        @Override
        public int getNumber(Password password) {
            return password.getNumberOfUppercase();
        }

        @Override
        public String getType() {
            return CHARACTER_TYPE;
        }

        @Override
        public BadRequestException getException() {
            return BadRequestException.badRequests.passwordInvalidUppercaseNumber(getNumberOfCharacters());
        }
    }
}
