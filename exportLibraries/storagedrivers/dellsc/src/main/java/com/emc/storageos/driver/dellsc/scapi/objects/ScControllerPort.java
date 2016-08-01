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
 * IO ports on the Storage Center.
 */
public class ScControllerPort extends ScObject {
    public static final String ISCSI_TRANSPORT_TYPE = "Iscsi";
    public static final String FC_TRANSPORT_TYPE = "FibreChannel";

    public static final String PORT_STATUS_UP = "Up";
    public static final String PORT_STATUS_DOWN = "Down";

    public String childStatus;
    public ScObject controller;
    public Boolean embedded;
    public String iscsiGateway;
    public String iscsiIpAddress;
    public String iscsiName;
    public String iscsiSubnetMask;
    public String name;
    public String purpose;
    public String status;
    public String statusMessage;
    public String transportType;
    public Boolean virtual;
    public String wwn;
}
