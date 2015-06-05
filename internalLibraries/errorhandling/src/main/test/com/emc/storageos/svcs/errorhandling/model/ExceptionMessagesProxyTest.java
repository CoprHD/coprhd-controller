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

import static com.emc.storageos.svcs.errorhandling.utils.Documenter.sampleParameters;
import static java.text.MessageFormat.format;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.emc.storageos.svcs.errorhandling.utils.AbstractBundleTest;

@RunWith(Parameterized.class)
public class ExceptionMessagesProxyTest extends AbstractBundleTest {

    public ExceptionMessagesProxyTest(final Class<?> baseClass) {
        super(baseClass);
    }

    @Test
    public void createProxy() {
        final Object object = ExceptionMessagesProxy.create(baseClass);
        assertNotNull(object);
    }

    @Test
    public void createMethods() {
        final Object proxy = ExceptionMessagesProxy.create(baseClass);

        final Method[] methods = baseClass.getDeclaredMethods();
        for (final Method method : methods) {
            try {
                final ServiceCoded sce = (ServiceCoded) method.invoke(proxy,
                        sampleParameters(method));
                assertNotNull(sce);
                assertNotNull(sce.getServiceCode());
                assertNotNull(sce.getMessage());
                if (sce instanceof StatusCoded) {
                    assertNotNull(((StatusCoded) sce).getStatus());
                }
            } catch (final Exception e) {
                fail(format("Unable to generate a message for {0} in bundle {1}: {2}",
                        method.getName(), baseClass.getName(), e.getMessage()));
            }
        }
    }
}
