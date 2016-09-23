/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.services.OperationTypeEnum;

public class VolumeExpandCompleter extends VolumeTaskCompleter {
    private static final Logger _log = LoggerFactory.getLogger(VolumeExpandCompleter.class);

    private Long _size;
    private Long _totalMetaMembersSize = 0L;
    private int _metaMemberCount = 0;
    private long _metaMemberSize = 0L;
    private boolean _isComposite = false;
    private String _metaVolumeType = Volume.CompositionType.STRIPED.toString();

    public VolumeExpandCompleter(URI volUri, Long size, String task) {
        super(Volume.class, volUri, task);
        _size = size;
    }

    public Long getSize() {
        return _size;
    }

    public Long getTotalMetaMembersSize() {
        return _totalMetaMembersSize;
    }

    public void setTotalMetaMembersSize(Long _totalMetaMembersSize) {
        this._totalMetaMembersSize = _totalMetaMembersSize;
    }

    public int getMetaMemberCount() {
        return _metaMemberCount;
    }

    public void setMetaMemberCount(int metaMemberCount) {
        this._metaMemberCount = metaMemberCount;
    }

    public long getMetaMemberSize() {
        return _metaMemberSize;
    }

    public void setMetaMemberSize(long metaMemberSize) {
        this._metaMemberSize = metaMemberSize;
    }

    public boolean isComposite() {
        return _isComposite;
    }

    public void setComposite(boolean composite) {
        _isComposite = composite;
    }

    public String getMetaVolumeType() {
        return _metaVolumeType;
    }

    public void setMetaVolumeType(String metaVolumeType) {
        _metaVolumeType = metaVolumeType;
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            super.complete(dbClient, status, coded);
            Volume volume = dbClient.queryObject(Volume.class, getId());
            if (Operation.Status.ready == status) {
                dbClient.ready(Volume.class, getId(), getOpId());
                _log.info(String.format("Done VolumeExpand - Volume Id: %s, NativeId: %s, OpId: %s, status: %s, New size: %d",
                        getId().toString(), volume.getNativeId(), getOpId(), status.name(), _size));
            } else if (Operation.Status.error == status) {
                dbClient.error(Volume.class, getId(), getOpId(), coded);
                _log.info(String.format("VolumeExpand failed - Volume Id: %s, NativeId: %s, OpId: %s, status: %s, New size: %d",
                        getId().toString(), volume.getNativeId(), getOpId(), status.name(), _size));
            }
            recordBlockVolumeOperation(dbClient, OperationTypeEnum.EXPAND_BLOCK_VOLUME, status, getId().toString(), String.valueOf(_size));
        } catch (Exception e) {
            _log.error(String.format("Failed updating status for VolumeExpand - Volume Id: %s, OpId: %s",
                    getId().toString(), getOpId()), e);
        }
    }
}
