/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import static com.emc.vipr.client.core.util.ResourceUtils.*;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.List;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.block.BlockConsistencyGroupCreate;
import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;
import com.emc.storageos.model.block.BlockConsistencyGroupUpdate;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.exceptions.ViPRHttpException;

import controllers.security.Security;

public class BlockConsistencyGroupUtils {
    public static BlockConsistencyGroupRestRep getBlockConsistencyGroup(String id) {
        try {
            return getBlockConsistencyGroup(uri(id));
        }
        catch (ViPRHttpException e) {
            if (e.getHttpCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    public static BlockConsistencyGroupRestRep getBlockConsistencyGroup(URI id) {
        return getViprClient().blockConsistencyGroups().get(id);
    }

    public static NamedRelatedResourceRep getBlockConsistencyGroupRef(RelatedResourceRep ref) {
        return getBlockConsistencyGroupRef(id(ref));
    }

    public static NamedRelatedResourceRep getBlockConsistencyGroupRef(URI id) {
       return createNamedRef(getViprClient().blockConsistencyGroups().get(id));
    }

    public static List<BlockConsistencyGroupRestRep> getBlockConsistencyGroups(String projectId) {
        return getViprClient().blockConsistencyGroups().findByProject(uri(projectId));
    }

    public static List<BlockConsistencyGroupRestRep> getBlockConsistencyGroups(List<URI> ids) {
        return getViprClient().blockConsistencyGroups().getByIds(ids);
    }

    public static Task<BlockConsistencyGroupRestRep> deactivate(URI id) {
        return getViprClient().blockConsistencyGroups().deactivate(id);
    }

    public static BlockConsistencyGroupRestRep create(BlockConsistencyGroupCreate group) {
        return getViprClient().blockConsistencyGroups().create(group);
    }

    public static Task<BlockConsistencyGroupRestRep> update(String id, BlockConsistencyGroupUpdate group) {
        return getViprClient().blockConsistencyGroups().update(uri(id), group);
    }
}
