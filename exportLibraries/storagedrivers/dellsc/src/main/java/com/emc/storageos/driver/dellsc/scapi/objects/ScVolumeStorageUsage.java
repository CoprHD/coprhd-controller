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

import java.util.Date;

/**
 * Volume usage information.
 */
public class ScVolumeStorageUsage extends ScObject {
    public String activeSpace;
    public String actualSpace;
    public String configuredSpace;
    public String freeSpace;
    public String name;
    public String raidOverhead;
    public String replaySpace;
    public String savingsVsRaidTen;
    public String sharedSpace;
    public Date time;
    public String totalDiskSpace;
}
