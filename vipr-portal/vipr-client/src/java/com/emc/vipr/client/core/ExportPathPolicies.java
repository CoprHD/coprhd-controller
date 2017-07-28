package com.emc.vipr.client.core;

import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

import java.net.URI;
import java.util.List;

import javax.ws.rs.core.UriBuilder;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.block.export.ExportPathPoliciesBulkRep;
import com.emc.storageos.model.block.export.ExportPathPoliciesList;
import com.emc.storageos.model.block.export.ExportPathPolicy;
import com.emc.storageos.model.block.export.ExportPathPolicyRestRep;
import com.emc.storageos.model.block.export.ExportPathPolicyUpdate;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;
import com.google.common.collect.Lists;

public class ExportPathPolicies extends AbstractCoreBulkResources<ExportPathPolicyRestRep> {

    public ExportPathPolicies(ViPRCoreClient parent, RestClient client) {

        super(parent, client, ExportPathPolicyRestRep.class, PathConstants.EXPORT_PATH_POLICIES_URL);
    }

    /**
     * Deletes the given Export Path Params by ID.
     * <p>
     * API Call: <tt>POST /block/export-path-parameters/{id}/deactivate</tt>
     * 
     * @param id
     *            the ID of Export Path Params to deactivate.
     */
    public void delete(URI id) {
        client.post(String.class, PathConstants.EXPORT_PATH_POLICIES_DEACTIVATE_BY_ID_URL, id);
    }

    /**
     * Creates an Export Path Params.
     * <p>
     * API Call: <tt>POST/block/export-path-parameters</tt>
     * 
     * @param input
     *            the Export Path Params.
     * @return the newly created Export Path Params.
     */
    public ExportPathPolicyRestRep create(ExportPathPolicy input) {
        URI targetUri = client.uriBuilder(baseUrl).build();
        ExportPathPolicyRestRep element = client
                .postURI(ExportPathPolicyRestRep.class, input, targetUri);
        return get(element.getId());
    }

    /**
     * Update the given Export Path Params by ID.
     * <p>
     * API Call: <tt>PUT /block/export-path-parameters/{id}</tt>
     * 
     * @param id
     *            the ID of Export Path Params to deactivate.
     */
    public void update(URI id, ExportPathPolicyUpdate input) {
        client.put(String.class, input, PathConstants.EXPORT_PATH_POLICIES_BY_ID_URL, id);
    }

    @Override
    protected List<ExportPathPolicyRestRep> getBulkResources(BulkIdParam input) {
        ExportPathPoliciesBulkRep response = client.post(ExportPathPoliciesBulkRep.class, input, getBulkUrl());
        return defaultList(response.getExportPathParamsList());
    }

    public List<ExportPathPolicyRestRep> getPortGroups() {

        return get("true");
    }

    public List<ExportPathPolicyRestRep> getExportPathParamsList() {

        return get("flase");
    }

    private List<ExportPathPolicyRestRep> get(String isPortGroup) {
        UriBuilder builder = client.uriBuilder(baseUrl);
        builder.queryParam("is-port-group", isPortGroup);
        ExportPathPoliciesList paramList = client.getURI(ExportPathPoliciesList.class, builder.build());
        List<NamedRelatedResourceRep> namedResourceList = paramList.getPathParamsList();
        List<URI> uris = Lists.newArrayList();
        for (NamedRelatedResourceRep rep : namedResourceList) {
            uris.add(rep.getId());
        }
        BulkIdParam bulkIdParam = new BulkIdParam();
        bulkIdParam.setIds(uris);
        return getBulkResources(bulkIdParam);
    }

}
