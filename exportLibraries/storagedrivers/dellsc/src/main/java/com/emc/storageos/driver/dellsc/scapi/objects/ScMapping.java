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
 * Individual I-T mapping.
 */
public class ScMapping extends ScObject {
    public ScObject controller;
    public ScObject controllerPort;
    public int lun;
    public String operationalState;
    public ScObject profile;
    public boolean readOnly;
    public ScObject server;
    public ScObject serverHba;
    public String status;
    public String statusMessage;
    public String transport;
    public ScObject volume;
}
