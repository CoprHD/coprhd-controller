/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.propertyhandler;

import com.emc.storageos.model.property.PropertyInfoRestRep;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import java.util.Arrays;
import java.util.List;

public class LoginBannerHandler implements UpdateHandler{

    private static final String SYSTEM_LOGIN_BANNER= "system_login_banner";
    private static final String BACKTICK= "`";
    private static final String BACKSLASH= "\\";
    private static final String LEGAL_CHARACTERS_MESSAGE="Only ASCII characters except ` and \\ ";
    private static final List<Character> LEGAL_ESCAPE_CHARACTERS= Arrays.asList('n','r');

    /**
     * Checks if system_login_banner property conforms to allowed characters
     * Allowed Characters: ASCII, no backtic (`), no backslash (\)
     *
     * @param oldProps
     * @param newProps
     */
    public void before(PropertyInfoRestRep oldProps,PropertyInfoRestRep newProps){
        String newValue = newProps.getProperty(SYSTEM_LOGIN_BANNER);

        if (!testInput(newValue)){
            throw APIException.badRequests.parameterValueContainsInvalidCharacters(SYSTEM_LOGIN_BANNER,LEGAL_CHARACTERS_MESSAGE);
        }
    }

    /**
     * After method is not needed, but must be implemented
     *
     * @param oldProps
     * @param newProps
     */
    @Override
    public void after(PropertyInfoRestRep oldProps, PropertyInfoRestRep newProps) {
        // empty implementation
    }

    private boolean testInput(String input){
        if (!input.matches("\\A\\p{ASCII}*\\z")){
            return false;
        }

        if (input.contains(BACKTICK)){
            return false;
        }

        if (input.contains(BACKSLASH)){
            //check for legal escape characters newline and carriage return
            for (int index = input.indexOf(BACKSLASH); index >= 0; index = input.indexOf(BACKSLASH, index + 1)) {
                if (index == input.length()-1){
                    return false;
                }
                else if (!LEGAL_ESCAPE_CHARACTERS.contains(input.charAt(index+1))){
                    return false;
                }
            }

            return false;
        }

        return true;
    }

    public boolean isProprotyChanged(PropertyInfoRestRep oldProps, PropertyInfoRestRep newProps, String property) {
        String oldValue = oldProps.getProperty(property);
        String newValue = newProps.getProperty(property);

        if (newValue == null) {
            return false;
        }

        if (oldValue == null) {
            oldValue = "0";
        }

        if (oldValue.equals(newValue)) {
            return false;
        }

        return true;
    }

}
