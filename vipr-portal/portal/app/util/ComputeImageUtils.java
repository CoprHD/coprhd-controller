/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.model.compute.ComputeImageRestRep;

import com.emc.storageos.model.compute.ComputeImageCreate;
import com.emc.storageos.model.compute.ComputeImageUpdate;

import com.emc.vipr.client.Task;
import com.emc.vipr.client.core.util.CachedResources;

import com.emc.vipr.client.exceptions.ViPRHttpException;

public class ComputeImageUtils {
    private static final String NAME_NOT_AVAILABLE = "ComputeImages.nameNotAvailable";

    public static CachedResources<ComputeImageRestRep> createCache() {
        return new CachedResources<ComputeImageRestRep>(getViprClient().computeImages());
    }

    public static ComputeImageRestRep getComputeImage(String id) {
        return getComputeImage(uri(id));
    }

    public static ComputeImageRestRep getComputeImage(URI id) {
        try {
            return getViprClient().computeImages().get(id);
        } catch (ViPRHttpException e) {
            if (e.getHttpCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    public static List<ComputeImageRestRep> getComputeImages() {
        return getViprClient().computeImages().getAll();
    }

    public static List<ComputeImageRestRep> getComputeImages(Collection<URI> ids) {
        return getViprClient().computeImages().getByIds(ids);
    }

    public static String getName(ComputeImageRestRep computeImage) {
        if (StringUtils.isNotBlank(computeImage.getName())) {
            return computeImage.getName();
        }
        else {
            return MessagesUtils.get(NAME_NOT_AVAILABLE);
        }
    }

    public static Task<ComputeImageRestRep> create(ComputeImageCreate param) {
        return getViprClient().computeImages().create(param);
    }

    public static Task<ComputeImageRestRep> update(String id, ComputeImageUpdate param) {
        return getViprClient().computeImages().update(uri(id), param);
    }

    public static void deactivate(URI id) {
        getViprClient().computeImages().deactivate(id);
    }

    public static Task<ComputeImageRestRep> cloneImage(ComputeImageCreate param) {
        return getViprClient().computeImages().cloneImage(param);
    }

}
