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
package com.emc.vipr.primitives;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Set;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;

import org.reflections.Reflections;
import org.reflections.scanners.MethodAnnotationsScanner;

import com.emc.storageos.api.service.ProvisioningService;
import com.google.common.collect.ImmutableList;

public class Main {

    private static final ImmutableList<Class<? extends Annotation>> REST_METHODS = ImmutableList.<Class<? extends Annotation>>builder()
            .add(GET.class)
            .add(POST.class)
            .add(PUT.class)
            .add(DELETE.class).build();
    /**
     * @param args
     */
    public static void main(String[] args) {
        ProvisioningService prov;
        Reflections reflections = new Reflections("com.emc.storageos.api", new MethodAnnotationsScanner());
        
        for(Class<? extends Annotation> restMethod : REST_METHODS) {
            final Set<Method> methods = reflections.getMethodsAnnotatedWith(restMethod);
            generatePrimitives(restMethod, methods);
        }
    }
    
    private final static void generatePrimitives(final Class<? extends Annotation> restMethod, final Set<Method> methods) {
        for(final Method method : methods) {
            generatePrimitive(method.getAnnotation(restMethod), method);
        }
    }
    
    private final static void generatePrimitive(final Annotation restMethod, final Method method) {
        System.out.println("REST Method: "+restMethod.annotationType());
        System.out.println("Primitive Name: "+ makePrimitiveName(method));
    }
    
    private final static String makePrimitiveName(final Method method) {
        char[] nameAsArray = method.getName().toCharArray();
        nameAsArray[0] = Character.toUpperCase(nameAsArray[0]);
        return method.getDeclaringClass().getSimpleName()+new String(nameAsArray);
        
    }

}
