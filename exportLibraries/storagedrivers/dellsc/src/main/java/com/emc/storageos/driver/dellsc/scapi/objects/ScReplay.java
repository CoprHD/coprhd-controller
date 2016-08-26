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
 * Storage Center snapshot "replays".
 */
public class ScReplay extends ScObject {
    public boolean active;
    public ScObject consistencyGroup;
    public boolean consistent;
    public ScObject createVolume;
    public String description;
    public Date expireTime;
    public boolean expires;
    public Date freezeTime;
    public String globalIndex;
    public boolean markedForExpiration;
    public ScObject parent;
    public ScObject replayProfile;
    public ScObject replayProfileRule;
    public String size;
    public String source;
    public boolean spaceRecovery;
    public long writesHeldDuration;
}
