/*
 * Copyright 2016 Dell Inc.
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
package com.emc.storageos.driver.dellsc.scapi.objects;

/**
 * Base for all Storage Center DTO objects.
 */
public class ScObject {

    public String instanceId;
    public String scSerialNumber;
    public String objectType;
    public String instanceName;

    @Override
    public String toString() {
        return String.format("(%s) %s %s", objectType, instanceId, instanceName);
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return toString().equals(String.format("%s", o));
    }
}