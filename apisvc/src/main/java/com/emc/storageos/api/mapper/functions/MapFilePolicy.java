package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FilePolicy;
import com.emc.storageos.model.file.FilePolicyRestRep;
import com.google.common.base.Function;

public class MapFilePolicy implements Function<FilePolicy, FilePolicyRestRep> {
    public static final MapFilePolicy instance = new MapFilePolicy();
    // The DB client is required to query the FCEndpoint
    private DbClient dbClient;

    public static MapFilePolicy getInstance(DbClient dbClient) {
        instance.setDbClient(dbClient);
        return instance;
    }

    private MapFilePolicy() {
    }

    private void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    @Override
    public FilePolicyRestRep apply(FilePolicy resource) {
        // return SystemsMapper.map(resource, dbClient);
        return new FilePolicyRestRep();
    }

    /**
     * Translate <code>VirtualNAS</code> object to <code>VirtualNASRestRep</code>
     * 
     * @param VirtualNAS
     * @return
     */
    public FilePolicyRestRep toFilePolicyRestRep(FilePolicy filePolicy) {
        return new FilePolicyRestRep();
        // return apply(vNas);
    }

}
