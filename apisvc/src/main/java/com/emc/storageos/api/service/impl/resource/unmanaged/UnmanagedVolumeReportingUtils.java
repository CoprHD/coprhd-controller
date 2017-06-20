/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.unmanaged;

import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.text.StrSubstitutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;

public class UnmanagedVolumeReportingUtils {
    private static Logger _log = LoggerFactory.getLogger(UnmanagedVolumeReportingUtils.class);

    public static String renderUnmanagedVolumeDependencyTree(DbClient dbClient, 
            CoordinatorClient coordinator, UnManagedVolume unmanagedVolume) {
        _log.info("rendering dependency tree for unmanaged volume " + unmanagedVolume.forDisplay());

        ReportInfoCache cache = new ReportInfoCache(dbClient);

        StrSubstitutor bodyRenderer = new StrSubstitutor(new UnManagedVolumeLookup(unmanagedVolume, cache, dbClient, coordinator));
        String body = bodyRenderer.replace(Templates.TEMPLATE_UMV_TREE);

        Map<String, Object> valuesMap = new HashMap<String, Object>();
        valuesMap.put("title", unmanagedVolume.getLabel());
        valuesMap.put("body", body);

        return StrSubstitutor.replace(Templates.TEMPLATE_HTML_PAGE, valuesMap);
    }

    public static String renderUnmanagedVolumeDependencyTreeList(DbClient dbClient, 
            CoordinatorClient coordinator, String searchString) {
        _log.info("rendering dependency tree for all unmanaged volumes, searchString = {}", searchString);

        ReportInfoCache cache = new ReportInfoCache(dbClient);

        List<URI> umvUris = dbClient.queryByType(UnManagedVolume.class, true);
        Iterator<UnManagedVolume> umvs = dbClient.queryIterativeObjects(UnManagedVolume.class, umvUris);
        while (umvs.hasNext()) {
            UnManagedVolume umv = umvs.next();
            if (searchString == null || umv.getLabel().toLowerCase().contains(searchString.toLowerCase())) {
                cache.mapUnManagedVolume(umv);
            }
        }

        String body = cache.renderUnManagedVolumeTree();

        Map<String, Object> valuesMap = new HashMap<String, Object>();
        valuesMap.put("title", "All UnManagedVolumes");
        valuesMap.put("body", body);

        return StrSubstitutor.replace(Templates.TEMPLATE_HTML_PAGE, valuesMap);
    }

    public static String renderUnmanagedExportMaskDependencyTree(DbClient dbClient, CoordinatorClient coordinator,
            UnManagedExportMask unmanagedExportMask) {

        ReportInfoCache cache = new ReportInfoCache(dbClient);

        StrSubstitutor bodyRenderer = new StrSubstitutor(new UnManagedExportMaskLookup(
                unmanagedExportMask, cache, dbClient, coordinator));
        String body = bodyRenderer.replace(Templates.TEMPLATE_UEM_TREE);

        Map<String, Object> valuesMap = new HashMap<String, Object>();
        valuesMap.put("title", unmanagedExportMask.getLabel());
        valuesMap.put("body", body);

        return StrSubstitutor.replace(Templates.TEMPLATE_HTML_PAGE, valuesMap);
    }

}
