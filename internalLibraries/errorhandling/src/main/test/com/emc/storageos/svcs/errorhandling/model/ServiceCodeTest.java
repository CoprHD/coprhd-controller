/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.svcs.errorhandling.model;

import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;

import org.junit.Test;

import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.svcs.errorhandling.utils.MessageUtils;

public class ServiceCodeTest {

    @Test
    public void messagesWithoutCodes() {
        final Set<String> failures = new TreeSet<String>();

        final ResourceBundle bundle = MessageUtils.bundleForClass(ServiceCode.class);
        for (final String key : bundle.keySet()) {
            try {
                ServiceCode.valueOf(key);
            } catch (Exception e) {
                failures.add(key);
            }
        }

        assertTrue("The following keys in the Bundle do not map to a ServiceCode: " + failures,
                failures.isEmpty());
    }

    @Test
    public void codesWithoutMessages() {
        final Set<String> failures = new TreeSet<String>();

        final ResourceBundle bundle = MessageUtils.bundleForClass(ServiceCode.class);

        for (final ServiceCode serviceCode : ServiceCode.values()) {
            String name = serviceCode.name();
            if (!bundle.keySet().contains(name)) {
                failures.add(name);
            }
        }

        assertTrue("The following ServiceCodes do not have messages in the Bundle: " + failures,
                failures.isEmpty());
    }

    @Test
    public void serviceCodeNumbersAreUnique() {
        final Map<Integer, ServiceCode> codes = new HashMap<Integer, ServiceCode>();
        final Map<Integer, Set<ServiceCode>> duplicates = new HashMap<Integer, Set<ServiceCode>>();

        for (final ServiceCode code : ServiceCode.values()) {
            final int serviceCode = code.getCode();
            final ServiceCode orginal = codes.put(serviceCode, code);
            if (orginal != null) {
                final Set<ServiceCode> set;
                if (duplicates.containsKey(serviceCode)) {
                    set = duplicates.get(serviceCode);
                } else {
                    set = new HashSet<ServiceCode>();
                    set.add(orginal);
                }
                set.add(code);
                duplicates.put(serviceCode, set);
            }
        }

        assertTrue("Found Duplicates: " + duplicates, duplicates.isEmpty());
    }
}
