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
 * The Storage Center array.
 */
public class StorageCenter extends ScObject {
    public String name;
    public String location;
    public String version;
    public String userName;
    public Boolean connected;
    public Long serialNumber;
    public String hostOrIpAddress;
    public String managementIp;
    public Boolean flashOptimizedConfigured;
    public String operationMode;
    public Boolean portsBalanced;
    public String modelSeries;
    public String status;
    public String statusMessage;
    public String scName;
}