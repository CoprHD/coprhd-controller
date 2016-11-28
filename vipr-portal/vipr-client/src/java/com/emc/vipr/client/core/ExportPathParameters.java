package com.emc.vipr.client.core;

import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

import java.net.URI;
import java.util.List;

import javax.ws.rs.core.UriBuilder;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.block.export.ExportPathParametersBulkRep;
import com.emc.storageos.model.block.export.ExportPathParametersList;
import com.emc.storageos.model.block.export.ExportPathParametersRestRep;
import com.emc.storageos.model.block.export.ExportPathUpdateParams;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;
import com.google.common.collect.Lists;

public class ExportPathParameters extends AbstractCoreBulkResources<ExportPathParametersRestRep> {

    public ExportPathParameters(ViPRCoreClient parent, RestClient client) {

        super(parent, client, ExportPathParametersRestRep.class, PathConstants.EXPORT_PATH_PARAMS_URL);
    }

    /**
     * Deletes the given Export Path Params or Port group by ID.
     * <p>
     * API Call: <tt>DELETE /block/export-path-parameters/{id}</tt>
     * 
     * @param id
     *            the ID of Export Path Params or Port group to deactivate.
     */
    public void delete(URI id) {
        client.delete(String.class, PathConstants.EXPORT_PATH_PARAMS_BY_ID_URL, id);
    }

    /**
     * Creates a xport Path Params or Port group.
     * <p>
     * API Call: <tt>POST/block/export-path-parameters?is-port-group=<value></tt>
     * 
     * @param input
     *            the Export Path Params or Port group configuration.
     * @param isPortGroup
     *            query parameter flag for
     * @return the newly created Export Path Params or Port group.
     */
    public ExportPathParametersRestRep create(ExportPathParameters input, String isPortGroup) {
        URI targetUri = client.uriBuilder(baseUrl).queryParam("is-port-group", isPortGroup).build();
        ExportPathParametersRestRep element = client
                .postURI(ExportPathParametersRestRep.class, input, targetUri);
        return get(element.getId());
    }

    /**
     * Update the given Export Path Params or Port group by ID.
     * <p>
     * API Call: <tt>PUT /block/export-path-parameters/{id}</tt>
     * 
     * @param id
     *            the ID of Export Path Params or Port group to deactivate.
     */
    public void update(URI id, ExportPathUpdateParams input) {
        client.put(String.class, input, PathConstants.EXPORT_PATH_PARAMS_BY_ID_URL, id);
    }

    @Override
    protected List<ExportPathParametersRestRep> getBulkResources(BulkIdParam input) {
        ExportPathParametersBulkRep response = client.post(ExportPathParametersBulkRep.class, input, getBulkUrl());
        return defaultList(response.getExportPathParamsList());
    }

    public List<ExportPathParametersRestRep> getPortGroups() {

        return get("true");
    }

    public List<ExportPathParametersRestRep> getExportPathParamsList() {

        return get("flase");
    }

    private List<ExportPathParametersRestRep> get(String isPortGroup) {
        UriBuilder builder = client.uriBuilder(baseUrl);
        builder.queryParam("is-port-group", isPortGroup);
        ExportPathParametersList paramList = client.getURI(ExportPathParametersList.class, builder.build());
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
