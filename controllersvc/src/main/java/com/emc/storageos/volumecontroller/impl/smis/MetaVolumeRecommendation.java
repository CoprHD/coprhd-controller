/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller.impl.smis;

import com.emc.storageos.db.client.model.Volume;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetaVolumeRecommendation {
    private static final Logger _log = LoggerFactory.getLogger(MetaVolumeRecommendation.class);

    private static final int BYTESCONVERTER = 1024;

    private boolean _createMetaVolumes = false;
    private long _metaMemberSize = 0;
    private long _metaMemberCount = 0;
    private Volume.CompositionType _metaVolumeType = Volume.CompositionType.STRIPED;

    public MetaVolumeRecommendation() {
    }

    public boolean isCreateMetaVolumes() {
        return _createMetaVolumes;
    }

    public long getMetaMemberSize() {
        return _metaMemberSize;
    }

    public long getMetaMemberCount() {
        return _metaMemberCount;
    }

    public void setCreateMetaVolumes(boolean _createMetaVolumes) {
        this._createMetaVolumes = _createMetaVolumes;
    }

    public void setMetaMemberSize(long _metaMemberSize) {
        this._metaMemberSize = _metaMemberSize;
    }

    public void setMetaMemberCount(long _metaMemberCount) {
        this._metaMemberCount = _metaMemberCount;
    }

    public Volume.CompositionType getMetaVolumeType() {
        return _metaVolumeType;
    }

    public void setMetaVolumeType(Volume.CompositionType _metaVolumeType) {
        this._metaVolumeType = _metaVolumeType;
    }

    /**
     * Created COP-37 to track hashCode() implemenatation in this class.
     */
    @SuppressWarnings({ "squid:S1206" })
    public boolean equals(Object o) {
        if (o == null || !(o instanceof MetaVolumeRecommendation)) {
            return false;
        }

        MetaVolumeRecommendation other = (MetaVolumeRecommendation) o;

        return (isCreateMetaVolumes() == other.isCreateMetaVolumes() &&
                getMetaMemberSize() == other.getMetaMemberSize() &&
                getMetaMemberCount() == other.getMetaMemberCount() && getMetaVolumeType().equals(other.getMetaVolumeType()));
    }
}
