/**
 *  Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.services.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;
import org.owasp.esapi.ESAPI;

/**
 * Class contains web application security helper methods.
 *
 */
public class SecurityUtils {
    private static final Logger log = LoggerFactory.getLogger(SecurityUtils.class);

    /**
     * Removes any potential XSS threats from the value.
     * Depends on the WASP ESAPI (owasp.org) and jsoup libraries (jsoup.org).
     * 
     * @param value data to be cleaned
     * @return cleaned data
     */
    public static String stripXSS(String value) {
        if (value == null)
            return null;

        value = ESAPI.encoder().canonicalize(value);
        value = value.replaceAll("\0", "");
        value = Jsoup.clean(value, Whitelist.none());

        return value;
    }

}
