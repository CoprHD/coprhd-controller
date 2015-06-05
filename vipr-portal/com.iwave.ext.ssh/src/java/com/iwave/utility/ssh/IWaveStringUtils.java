/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.iwave.utility.ssh;

import org.apache.commons.lang.StringUtils;

public class IWaveStringUtils {

    /**
     * Removes the {@param stripChars} characters from the beginning of {@param str}, however, if
     * it comes across a a white space character, as determined by {@link StringUtils#isWhitespace(String)}
     * that is not part of the {@param stripChars} before it gets to the end of {@param stripChars},
     * it will automatically skip it.
     * 
     * Mainly used to remove string 1 from the beginning of string 2 where string 2 may have added line
     * breaks etc. 
     * 
     * @param str The string that @{stripChars} should be removed from 
     * @param stripChars The string to remove from @{param str}
     * @return {@param str} with {@param stripChars} remove
     */
    public static String removeStartIgnoringWhiteSpace(String str, String stripChars) {
        int strPos = 0;
        int stripCharsPos = 0;
        for (;stripCharsPos<stripChars.length() && strPos < str.length();stripCharsPos++) {
            
            // Jump forward within str over any white space characters that are NOT part of stripChars
            while ((stripChars.charAt(stripCharsPos) != str.charAt(strPos)) &&
                    strPos < str.length() &&
                    StringUtils.isWhitespace(str.substring(strPos,strPos+1))) {
                strPos++;
            }
            
            if (strPos < str.length()) {
                if (stripChars.charAt(stripCharsPos) != str.charAt(strPos)) {
                        break;
                } else {
                    strPos++;
                }
            }
        }   
        
        return StringUtils.substring(str, strPos);
    }
}
