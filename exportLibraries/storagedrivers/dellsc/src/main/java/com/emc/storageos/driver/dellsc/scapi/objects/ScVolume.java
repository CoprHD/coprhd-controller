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
 * A Storage Center volume.
 */
public class ScVolume extends ScObject {
    public Boolean replicationSource;
    public Boolean liveVolume;
    public int vpdId;
    public Long index;
    public String volumeFolderPath;
    public Boolean hostCacheEnabled;
    public Boolean inRecycleBin;
    public Long volumeFolderIndex;
    public String statusMessage;
    public String status;
    public ScObject storageType;
    public Boolean cmmDestination;
    public Boolean replicationDestination;
    public ScObject volumeFolder;
    public String deviceId;
    public Boolean active;
    public Boolean portableVolumeDestination;
    public Boolean deleteAllowed;
    public String name;
    public String scName;
    public Boolean secureDataUsed;
    public String serialNumber;
    public Boolean replayAllowed;
    public Boolean flashOptimized;
    public String configuredSize;
    public Boolean mapped;
    public Boolean cmmSource;
}
