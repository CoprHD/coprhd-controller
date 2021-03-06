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
 * Overal Storage Center configuration settings.
 */
public class ScConfiguration extends ScObject {
    public boolean backEndConfigured;
    public boolean fibreChannelFrontEndConfigured;
    public String fibreChannelTransportMode;
    public boolean iscsiFrontEndConfigured;
    public String iscsiTransportMode;
    public boolean portRebalanceNeeded;
    public boolean sasFrontEndConfigured;
    public String sasTransportMode;
}
