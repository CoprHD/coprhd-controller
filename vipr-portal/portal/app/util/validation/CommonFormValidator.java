/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package util.validation;

import java.util.regex.Pattern;

public class CommonFormValidator {

    private static final Pattern ALPHA_NUMERIC_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9]+");
    private static final Pattern ALPHA_NUMERIC_UNDERSCORE_UNORDERED_PATTERN = Pattern.compile("[A-Za-z0-9_]+");


    public static boolean isAlphaNumeric(String value) {    
        return ALPHA_NUMERIC_PATTERN.matcher(value).matches();
    }
    
    public static boolean isAlphaNumericOrUnderscoreUnordered(String value) {    
        return ALPHA_NUMERIC_UNDERSCORE_UNORDERED_PATTERN.matcher(value).matches();
    }
}

