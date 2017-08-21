/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */

package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.BlockMapper;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.model.block.BlockMirrorRestRep;
import com.google.common.base.Function;

public class MapBlockMirror implements Function<BlockMirror, BlockMirrorRestRep> {
	
	// The singleton instance.
    public static final MapBlockMirror instance = new MapBlockMirror();
    
    // A reference to a database client.
    private DbClient dbClient;
    
    /**
     * Function creates if necessary and returns the singleton instance.
     * 
     * @param dbClient A reference to a database client.
     * 
     * @return The singleton MapBlockMirror
     */
    public static MapBlockMirror getInstance(DbClient dbClient) {
        instance.setDBClient(dbClient);
        return instance;
    }

    /**
     * Private default constructor.
     */
    private MapBlockMirror() {
    }
    
    /**
     * Setter for the database client.
     * 
     * @param dbClient A reference to a database client.
     */
    private void setDBClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

	@Override
	public BlockMirrorRestRep apply(BlockMirror mirror) {
		return BlockMapper.map(dbClient, mirror);
	}

}
