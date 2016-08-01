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
 * Array controller port config settings.
 */
public class ScControllerPortConfiguration extends ScObject {
    public int bothCount;
    public long controllerPortIndex;
    public String description;
    public String deviceName;
    public long homeControllerIndex;
    public int initiatorCount;
    public int mapCount;
    public long preferredControllerIndex;
    public int slot;
    public int slotPort;
    public String speed;
    public int targetCount;
}
