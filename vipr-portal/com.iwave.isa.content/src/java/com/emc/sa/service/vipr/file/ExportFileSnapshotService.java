/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file;

import static com.emc.sa.service.ServiceParams.BYPASS_DNS_CHECK;
import static com.emc.sa.service.ServiceParams.COMMENT;
import static com.emc.sa.service.ServiceParams.SNAPSHOT;

import java.net.URI;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Bindable;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("ExportFileSnapshot")
public class ExportFileSnapshotService extends ViPRService {

    @Param(SNAPSHOT)
    protected URI snapshot;
    @Param(COMMENT)
    protected String comment;

    @Bindable(itemType = FileStorageUtils.FileExportRule.class)
    protected FileStorageUtils.FileExportRule[] exportRules;

    @Param(BYPASS_DNS_CHECK)
    protected boolean bypassDnsCheck;

    @Override
    public void precheck() throws Exception {
        if (exportRules == null || exportRules.length == 0) {
            ExecutionUtils.fail("failTask.CreateFileSnapshotExport.precheck", new Object[] {}, new Object[] {});
        }
    }

    @Override
    public void execute() throws Exception {
        if (exportRules != null) {
            String exportId = FileStorageUtils.createFileSnapshotExport(snapshot, comment, exportRules[0], null);
            if (exportRules.length > 1 && StringUtils.isNotBlank(exportId)) {
                FileStorageUtils.updateFileSnapshotExport(snapshot, null, exportRules, bypassDnsCheck);
            }
        }
    }
}