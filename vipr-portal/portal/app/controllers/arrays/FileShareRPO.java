/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.arrays;

import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.List;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.file.Copy;
import com.emc.storageos.model.file.FileReplicationParam;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.storageos.model.vpool.FileReplicationPolicy;
import com.emc.storageos.model.vpool.FileVirtualPoolProtectionParam;
import com.emc.storageos.model.vpool.FileVirtualPoolRestRep;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.FileSystems;
import com.emc.vipr.client.core.FileVirtualPools;
import com.google.common.collect.Lists;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import models.datatable.VirtualPoolDataTable;
import models.datatable.VirtualPoolDataTable.VirtualPoolInfo;
import models.virtualpool.FileVirtualPoolForm;
import play.mvc.With;
import util.BourneUtil;
import util.VirtualPoolUtils;
import util.datatable.DataTable;
import util.datatable.DataTablesSupport;
import controllers.Common;
import controllers.arrays.FileShareRPO.FileShareDataTable.FileShareInfo;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.ViprResourceController;

@With(Common.class)
@Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
public class FileShareRPO extends ViprResourceController {

    private static final String LOCAL_MIRROR="LOCAL_MIRROR";
    private static final String REMOTE_MIRROR="REMOTE_MIRROR";
    public static void list() {
        FileShareDataTable dataTable = new FileShareDataTable();
        render(dataTable);
    }

    public static void listJson() {
        List<FileShareInfo> items = Lists.newArrayList();
        List<FileVirtualPoolRestRep> virtualPool = VirtualPoolUtils.getFileVirtualPools();
        for (FileVirtualPoolRestRep vpool : virtualPool) {
            if (!vpool.getFileReplicationType().toString().equals("NONE")) {
                items.add(new FileShareInfo(vpool));
            }
        }
        renderJSON(DataTablesSupport.createJSON(items, params));
    }

    public static void fileSystemDetails(String id) {
        ViPRCoreClient client = BourneUtil.getViprClient();
        List<FileShareRestRep> fileSystems = Lists.newArrayList();
        BulkIdParam ids = client.fileSystems().getBulkFS();
        FileVirtualPoolRestRep vpool = client.fileVpools().get(uri(id));
        Long rpo = vpool.getProtection().getReplicationParam().getSourcePolicy().getRpoValue();
        String rpoType = vpool.getProtection().getReplicationParam().getSourcePolicy().getRpoType().toLowerCase();
        String fileReplicationType = vpool.getFileReplicationType();
        List<URI> fsIds = ids.getIds();
        for (URI fsId : fsIds) {
            FileShareRestRep fileSystem = client.fileSystems().get(fsId);
            Boolean value = !fileSystem.getProtection().getMirrorStatus().equals("FAILED_OVER");
            if (fileSystem.getVirtualPool().getId().toString().equals(id) && !fileSystem.getProtection().getPersonality().equals("TARGET") && value) {
                fileSystems.add(fileSystem);
            }
        }
        render(fileSystems,rpo, rpoType, fileReplicationType);
    }

    public static void forceFailover(String id, String fileReplicationType) {
        FileReplicationParam param = new FileReplicationParam();
        List<Copy> copies = Lists.newArrayList();
        Copy copyFile = new Copy();
        if(fileReplicationType.toLowerCase().equals("remote")) {
            copyFile.setType(REMOTE_MIRROR);
        }
        else {
            copyFile.setType(LOCAL_MIRROR);
        }
        copies.add(copyFile);
        param.setCopies(copies);
        ViPRCoreClient client = BourneUtil.getViprClient();
        Tasks<FileShareRestRep> tasks = client.fileSystems().failoverTest(uri(id), param);
    }
    
    public static class FileShareDataTable extends DataTable {
        public FileShareDataTable() {
            addColumn("name");
            addColumn("replicationType");
            addColumn("replicationMode");
            addColumn("rpo");
        }

        public static class FileShareInfo {
            private Long rpoValue;
            private String rpoType;
            private String rpo;
            private String name;
            private String id;
            private String replicationType;
            private String replicationMode;

            public FileShareInfo(FileVirtualPoolRestRep pool) {
                FileReplicationPolicy protection = pool.getProtection().getReplicationParam().getSourcePolicy();
                this.name = pool.getName();
                this.id = pool.getId().toString();
                this.rpoType = protection.getRpoType().toLowerCase();
                this.rpoValue = protection.getRpoValue();
                this.rpo = rpoValue + " " + rpoType;
                this.replicationType = pool.getFileReplicationType();
                this.replicationMode = protection.getCopyMode();

            }

        }
    }
}