package models.datatable;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import com.emc.sa.util.ResourceType;
import com.emc.storageos.model.block.BlockMirrorRestRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.block.export.ExportBlockParam;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.BlockContinuousCopies;
import com.google.common.collect.Lists;

import util.BourneUtil;
import util.datatable.DataTable;

public class BlockExportGroupMirrorsDataTable extends DataTable {
	
	public BlockExportGroupMirrorsDataTable() {
        addColumn("name");
        addColumn("volume");
        addColumn("createdDate").setRenderFunction("render.localDate");
        addColumn("actions").setRenderFunction("renderContinuousCopyActions");
        sortAll();
        setDefaultSort("name", "asc");
        setRowCallback("createRowLink");
    }

    public static List<ExportBlockMirror> fetch(URI exportGroupId) {
        if (exportGroupId == null) {
            return Collections.emptyList();
        }

        ViPRCoreClient client = BourneUtil.getViprClient();

        ExportGroupRestRep exportGroup = client.blockExports().get(exportGroupId);
        List<ExportBlockMirror> mirrors = Lists.newArrayList();
        for (ExportBlockParam exportBlockParam : exportGroup.getVolumes()) {
            if (ResourceType.isType(ResourceType.BLOCK_CONTINUOUS_COPY, exportBlockParam.getId())) {
                BlockMirrorRestRep mirror = client.blockContinuousCopies().get(exportBlockParam.getId());
                VolumeRestRep volume = client.blockVolumes().get(mirror.getSource().getId());
                mirrors.add(new ExportBlockMirror(mirror, volume.getName()));
            }
        }
        return mirrors;
    }
	
	
	public static class ExportBlockMirror {
        public URI id;
        public String name;
        public Long createdDate;
        public String volume;

        public ExportBlockMirror(BlockMirrorRestRep blockMirror, String volumeName) {
            this.id = blockMirror.getId();
            this.name = blockMirror.getName();
            if (blockMirror.getCreationTime() != null) {
                this.createdDate = blockMirror.getCreationTime().getTime().getTime();
            }
            this.volume = volumeName;
        }
    }
}
