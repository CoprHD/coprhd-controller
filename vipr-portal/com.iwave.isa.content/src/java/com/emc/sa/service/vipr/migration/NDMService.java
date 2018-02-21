package com.emc.sa.service.vipr.migration;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.emc.sa.service.ServiceParams.*;

@Service("NDM")
public class NDMService extends ViPRService {
    private static Logger log = LoggerFactory.getLogger(NDMService.class);

    @Param(value = STORAGE_TYPE)
    protected String storageType;

    @Param(value = HOST)
    protected String host;

    @Param(value = SOURCE_STORAGE_SYSTEM)
    protected String sourceStorageSystem;

    @Param(value = TARGET_STORAGE_SYSTEMS)
    protected String targetStorageSystems;

    @Param(value = STORAGE_GROUP)
    protected String storageGroup;

    @Param(value = MAXIMUM_PATHS)
    protected Integer maxPaths;

    @Param(value = TARGET_STORAGE_PORT)
    protected List<String> targetStoragePorts;

    @Override
    public void precheck() throws Exception {
        log.info("======== precheck called");

    }

    @Override
    public void execute() throws Exception {
        log.info("======== execute called");
        log.info("parameters: {}, {}, {}, {}, {}, {}, {}", storageType, host, sourceStorageSystem, targetStorageSystems, storageGroup, maxPaths, targetStoragePorts.size());
        log.info("target ports {}", String.join(", ", targetStoragePorts) );
    }
}
