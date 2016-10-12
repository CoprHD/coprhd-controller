/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils.labels;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentPrefixConstraint;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.emc.storageos.db.client.util.CommonTransformerFunctions.fctnBlockObjectToLabel;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Collections2.transform;

/**
 * Build labels for BlockObject instances.
 */
public class LabelBuilder {

    private static final Logger log = LoggerFactory.getLogger(LabelBuilder.class);
    private static final String HYPHEN = "-";
    private String baseLabel;
    private String delimiter = HYPHEN;

    // Used only for continuing existing patterns
    private Project project;
    private DbClient dbClient;

    public LabelBuilder(String baseLabel, Project project, DbClient dbClient) {
        this.baseLabel = checkNotNull(baseLabel);
        this.project = checkNotNull(project);
        this.dbClient = checkNotNull(dbClient);
    }

    public String getDelimiter() {
        return delimiter;
    }

    public LabelBuilder withDelimiter(String delimiter) {
        this.delimiter = delimiter;
        return this;
    }

    public LabelFormat build(int volumeCount) {
        LabelFormatFactory labelFormatFactory = new LabelFormatFactory();

        if (volumeCount > 1) {
            Set<String> existingSequence = existingVolumeLabelsInSequence();
            if (existingSequence.isEmpty()) {
                existingSequence.add(baseLabel + delimiter + 0);
            }
            return labelFormatFactory.getLabelFormat(existingSequence);
        }

        return labelFormatFactory.getLabelFormat(baseLabel);
    }

    private Set<String> existingVolumeLabelsInSequence() {
        List<Volume> tmp = CustomQueryUtility.queryActiveResourcesByConstraint(dbClient, Volume.class,
                ContainmentPrefixConstraint.Factory.getConstraint(Volume.class, "project",
                        project.getId(), baseLabel + delimiter));

        log.info("BIBBY Found {} volumes matching sequence {}", tmp.size(), baseLabel+delimiter);
        for (Volume v : tmp) {
            log.info("BIBBY Volume: \"{}\"", v.getLabel());
        }

        return new HashSet<>(transform(tmp, fctnBlockObjectToLabel()));
    }
}
