/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package util;

import static com.emc.vipr.client.core.util.ResourceUtils.id;
import static com.emc.vipr.client.core.util.ResourceUtils.mapById;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.ObjectUtils;

import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.storageos.model.block.export.ITLRestRep;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.host.InitiatorRestRep;
import com.emc.storageos.model.host.cluster.ClusterRestRep;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class ResourceUtils {

    public static List<HostExport> getHostExports(Collection<ITLRestRep> itls) {
        Map<URI, HostExport> hostExports = Maps.newLinkedHashMap();

        Map<ITLRestRep, ExportGroupRestRep> exportMap = getExportMap(itls);
        for (Map.Entry<ITLRestRep, ExportGroupRestRep> entry : exportMap.entrySet()) {
            HostRestRep host = getHost(entry.getKey(), entry.getValue());
            if (host == null) {
                continue;
            }

            HostExport hostExport = hostExports.get(host.getId());
            if (hostExport == null) {
                hostExport = createHostExport(host);
                hostExports.put(host.getId(), hostExport);
            }
            hostExport.exportMap.put(entry.getKey(), entry.getValue());
        }

        return Lists.newArrayList(hostExports.values());
    }

    private static HostExport createHostExport(HostRestRep host) {
        HostExport hostMapping = new HostExport();
        hostMapping.host = host;
        if (host.getCluster() != null) {
            ClusterRestRep cluster = getViprClient().clusters().get(host.getCluster().getId());
            hostMapping.cluster = (cluster != null) ? cluster.getName() : null;
        }
        return hostMapping;
    }

    private static HostRestRep getHost(ITLRestRep itl, ExportGroupRestRep export) {
        if ((export.getInitiators() == null) || export.getInitiators().isEmpty()) {
            return null;
        }
        URI initiatorId = itl.getInitiator().getId();
        for (InitiatorRestRep initiator : export.getInitiators()) {
            if (ObjectUtils.equals(initiatorId, initiator.getId())) {
                if (initiator.getHost() != null) {
                    return getHost(export, initiator.getHost().getId());
                }
                else {
                    return null;
                }
            }
        }
        return null;
    }

    private static HostRestRep getHost(ExportGroupRestRep export, URI id) {
        if ((export.getHosts() == null) || export.getHosts().isEmpty()) {
            return null;
        }
        for (HostRestRep host : export.getHosts()) {
            if (ObjectUtils.equals(id, host.getId())) {
                return host;
            }
        }
        return null;
    }

    private static Map<ITLRestRep, ExportGroupRestRep> getExportMap(Collection<ITLRestRep> itls) {
        Map<URI, ExportGroupRestRep> blockExports = getBlockExports(itls);

        Map<ITLRestRep, ExportGroupRestRep> exports = Maps.newLinkedHashMap();
        for (ITLRestRep itl : itls) {
            URI exportId = id(itl.getExport());
            if (exportId != null) {
                exports.put(itl, blockExports.get(exportId));
            }
        }
        return exports;
    }

    private static Map<URI, ExportGroupRestRep> getBlockExports(Collection<ITLRestRep> itls) {
        Set<URI> exportIds = Sets.newLinkedHashSet();
        for (ITLRestRep itl : itls) {
            URI exportId = id(itl.getExport());
            if (exportId != null) {
                exportIds.add(exportId);
            }
        }
        return mapById(getViprClient().blockExports().getByIds(exportIds));
    }

    public static class HostExport {
        public HostRestRep host;
        public String cluster;
        public Map<ITLRestRep, ExportGroupRestRep> exportMap = Maps.newLinkedHashMap();
    }
}
