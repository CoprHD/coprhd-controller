/*
 * Copyright 2016 Dell Inc. or its subsidiaries.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.emc.sa.catalog;

import com.emc.sa.descriptor.ServiceDescriptor;
import com.emc.sa.descriptor.ServiceDescriptors;

import java.util.Locale;

/**
 * Util class for pre-defined(json) service descriptors and workflow service descriptors
 */
public class ServiceDescriptorUtil {

    private ServiceDescriptorUtil() {

    }

    public static ServiceDescriptor getServiceDescriptorByName(final ServiceDescriptors serviceDescriptors, final WorkflowServiceDescriptor workflowServiceDescriptor, final String serviceName) {
        ServiceDescriptor descriptor;
        try {
            descriptor = serviceDescriptors.getDescriptor(Locale.getDefault(), serviceName);
        }
        catch (IllegalStateException e) {
            // If service descriptor is not found, check if this service is Workflow Service

            try {
                descriptor = workflowServiceDescriptor.getDescriptor(serviceName);
            } catch(Exception ex){
                throw ex;
            }
        }
        if (null == descriptor) {
            throw new IllegalStateException(String.format("Service %s not found", serviceName));
        }
        return descriptor;
    }
}
