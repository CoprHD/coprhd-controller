package com.emc.sa.service.vipr.plugins.tasks;

import static com.emc.sa.service.ServiceParams.CONSISTENCY_GROUP;
import static com.emc.sa.service.ServiceParams.NAME;
import static com.emc.sa.service.ServiceParams.NUMBER_OF_VOLUMES;
import static com.emc.sa.service.ServiceParams.PROJECT;
import static com.emc.sa.service.ServiceParams.SIZE_IN_GB;
import static com.emc.sa.service.ServiceParams.VIRTUAL_ARRAY;
import static com.emc.sa.service.ServiceParams.VIRTUAL_POOL;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.addAffectedResource;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.execute;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.service.vipr.block.CreateBlockVolumeHelper;
import com.emc.sa.service.vipr.block.BlockStorageUtils.Params;
import com.emc.sa.service.vipr.block.BlockStorageUtils.VolumeTable;
import com.emc.sa.service.vipr.block.tasks.CreateMultipleBlockVolumes;
import com.emc.sa.service.vipr.plugins.tasks.CustomUtils.CustomParamTable;
import com.emc.sa.service.vipr.plugins.tasks.CustomUtils.CustomParams;
import com.emc.sa.util.DiskSizeConversionUtils;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.google.common.collect.Lists;

public class CustomUtils {

	
    public static class CustomParams implements Params {
        @Param(VIRTUAL_POOL)
        public URI virtualPool;
        @Param(VIRTUAL_ARRAY)
        public URI virtualArray;
        @Param(PROJECT)
        public URI project;
        @Param(value = CONSISTENCY_GROUP, required = false)
        public URI consistencyGroup;

        @Override
        public String toString() {
            return "Virtual Pool=" + virtualPool + ", Virtual Array=" + virtualArray + ", Project=" + project
                    + ", Consistency Group=" + consistencyGroup;
        }

        @Override
        public Map<String, Object> getParams() {
            Map<String, Object> map = new HashMap<String, Object>();
            map.put(VIRTUAL_POOL, virtualPool);
            map.put(VIRTUAL_ARRAY, virtualArray);
            map.put(PROJECT, project);
            map.put(CONSISTENCY_GROUP, consistencyGroup);
            return map;
        }
    }
    
	public static class CustomParamTable {

		@Param(NAME)
		protected String nameParam;
		@Param(SIZE_IN_GB)
		protected Double sizeInGb;
		@Param(value = NUMBER_OF_VOLUMES, required = false)
		protected Integer count;

		@Override
		public String toString() {
			return "Volume=" + nameParam + ", size=" + sizeInGb + ", count="
					+ count;
		}

		public Map<String, Object> getParams() {
			Map<String, Object> map = new HashMap<String, Object>();
			map.put(NAME, nameParam);
			map.put(SIZE_IN_GB, sizeInGb);
			map.put(NUMBER_OF_VOLUMES, count);
			return map;
		}
	}

    public static Map<String, Object> createParam(CustomParamTable table, CustomParams params) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.putAll(table.getParams());
        map.putAll(params.getParams());
        return map;
    }

    public static List<URI> createMultipleVolumes(List<? extends CustomSampleHelper> helpers) {
        Tasks<VolumeRestRep> tasks = execute(new  MultipleCustomTaskExecuteor(helpers));
        List<URI> volumeIds = Lists.newArrayList();
        for (Task<VolumeRestRep> task : tasks.getTasks()) {
            URI volumeId = task.getResourceId();
            addAffectedResource(volumeId);
            volumeIds.add(volumeId);
        }
        return volumeIds;
    }

    public static String gbToVolumeSize(double sizeInGB) {
        // Always use size in bytes, VMAX does not like size in GB
        return String.valueOf(DiskSizeConversionUtils.gbToBytes(sizeInGB));
    }
}