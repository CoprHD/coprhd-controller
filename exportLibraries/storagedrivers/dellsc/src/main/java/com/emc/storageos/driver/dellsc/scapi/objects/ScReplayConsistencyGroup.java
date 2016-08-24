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
 * An instance of a consistent group of snapshots.
 */
public class ScReplayConsistencyGroup extends ScObject {
    public String description;
    public long expectedReplayCount;
    public Date expireTime;
    public boolean expires;
    public Date feezeTime;
    public String globalIndex;
    public ScObject profile;
    public long replayCount;
}
