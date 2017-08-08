package com.emc.vipr.client.core;

import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

import java.net.URI;
import java.util.List;

import javax.ws.rs.core.UriBuilder;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.block.BlockPerformancePolicyBulkRep;
import com.emc.storageos.model.block.BlockPerformancePolicyCreate;
import com.emc.storageos.model.block.BlockPerformancePolicyList;
import com.emc.storageos.model.block.BlockPerformancePolicyRestRep;
import com.emc.storageos.model.block.BlockPerformancePolicyUpdate;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;
import com.google.common.collect.Lists;

public class BlockPerformancePolicies extends AbstractCoreBulkResources<BlockPerformancePolicyRestRep> {

    public BlockPerformancePolicies(ViPRCoreClient parent, RestClient client) {

        super(parent, client, BlockPerformancePolicyRestRep.class, PathConstants.BLOCK_PERFORMANCE_POLICIES_URL);
    }

    /**
     * Deletes the given Block Performance Policy by ID.
     * <p>
     * API Call: <tt>POST /block/performance-policies/{id}/deactivate</tt>
     * 
     * @param id
     *            the ID of Block Performance Policy to deactivate.
     */
    public void delete(URI id) {
        client.post(String.class, PathConstants.BLOCK_PERFORMANCE_POLICIES_DEACTIVATE_BY_ID_URL, id);
    }

    /**
     * Creates an Block Performance Policy.
     * <p>
     * API Call: <tt>POST/block/performance-policies</tt>
     * 
     * @param input
     *            the Block Performance Policy.
     * @return the newly created Block Performance Policy.
     */
    public BlockPerformancePolicyRestRep create(BlockPerformancePolicyCreate input) {
        URI targetUri = client.uriBuilder(baseUrl).build();
        BlockPerformancePolicyRestRep element = client
                .postURI(BlockPerformancePolicyRestRep.class, input, targetUri);
        return get(element.getId());
    }

    /**
     * Update the given Block Performance Policy by ID.
     * <p>
     * API Call: <tt>PUT /block/performance-policies/{id}</tt>
     * 
     * @param id
     *            the ID of Block Performance Policy to deactivate.
     */
    public void update(URI id, BlockPerformancePolicyUpdate input) {
        client.put(String.class, input, PathConstants.BLOCK_PERFORMANCE_POLICIES_BY_ID_URL, id);
    }

    @Override
    protected List<BlockPerformancePolicyRestRep> getBulkResources(BulkIdParam input) {
        BlockPerformancePolicyBulkRep response = client.post(BlockPerformancePolicyBulkRep.class, input, getBulkUrl());
        return defaultList(response.getPerformancePolicies());
    }

    public List<BlockPerformancePolicyRestRep> getBlockPerformancePoliciesList() {
        UriBuilder builder = client.uriBuilder(baseUrl);
        BlockPerformancePolicyList paramList = client.getURI(BlockPerformancePolicyList.class, builder.build());
        List<NamedRelatedResourceRep> namedResourceList = paramList.getPerformancePolicies();
        List<URI> uris = Lists.newArrayList();
        for (NamedRelatedResourceRep rep : namedResourceList) {
            uris.add(rep.getId());
        }
        BulkIdParam bulkIdParam = new BulkIdParam();
        bulkIdParam.setIds(uris);
        return getBulkResources(bulkIdParam);
    }

}