/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models.datatable;

import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import util.datatable.DataTable;

import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.google.common.collect.Lists;
import controllers.resources.FileSystems;

public class FileSystemsDataTable extends DataTable {

    public FileSystemsDataTable() {
        addColumn("name");
        addColumn("capacity").setRenderFunction("render.markSoftLimitExceeded");
        addColumn("varray");
        addColumn("vpool");
        addColumn("protocols");
        sortAll();
        setDefaultSort("name", "asc");
        setRowCallback("createRowLink");
    }

    public static List<FileSystem> fetch(URI projectId) {
        if (projectId == null) {
            return Collections.EMPTY_LIST;
        }

        ViPRCoreClient client = getViprClient();
        List<FileShareRestRep> fileSystems = client.fileSystems().findByProject(projectId);
        Map<URI, String> virtualArrays = ResourceUtils.mapNames(client.varrays().list());
        Map<URI, String> virtualPools = ResourceUtils.mapNames(client.fileVpools().list());

        List<FileSystem> results = Lists.newArrayList();
        for (FileShareRestRep fileSystem : fileSystems) {
            results.add(new FileSystem(fileSystem, virtualArrays, virtualPools));
        }
        return results;
    }

    public static class FileSystem {
        public String rowLink;
        public URI id;
        public String name;
        public String capacity;
        public Boolean exceeded;
        public String varray;
        public String vpool;
        public Collection<String> protocols;

        public FileSystem(final FileShareRestRep bourneFs, Map<URI, String> varrayMap, Map<URI, String> vpoolMap) {
            id = bourneFs.getId();
            rowLink = createLink(FileSystems.class, "fileSystem", "fileSystemId", id);
            name = bourneFs.getName();
            capacity = bourneFs.getCapacity();
            if (bourneFs.getVirtualArray() != null) {
                varray = varrayMap.get(bourneFs.getVirtualArray().getId());
            }
            if (bourneFs.getVirtualPool() != null) {
                vpool = vpoolMap.get(bourneFs.getVirtualPool().getId());
            }
            protocols = bourneFs.getProtocols();
            exceeded = bourneFs.getSoftLimitExceeded();
        }
    }
}
