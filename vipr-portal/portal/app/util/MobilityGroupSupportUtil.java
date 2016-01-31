/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;

import java.net.URI;
import java.util.List;
import java.util.Set;

import com.emc.storageos.db.client.model.VolumeGroup;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.application.VolumeGroupCreateParam;
import com.emc.storageos.model.application.VolumeGroupRestRep;
import com.emc.storageos.model.application.VolumeGroupUpdateParam;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.google.common.collect.Lists;

public class MobilityGroupSupportUtil {

    public static List<NamedRelatedResourceRep> getMobilityGroups() {
        List<VolumeGroupRestRep> volumeGroups = BourneUtil.getViprClient().application()
                .getApplications(new ResourceFilter<VolumeGroupRestRep>() {

                    @Override
                    public boolean acceptId(URI id) {
                        return true;
                    }

                    @Override
                    public boolean accept(VolumeGroupRestRep item) {
                        return !item.getRoles().isEmpty() && item.getRoles().contains(VolumeGroup.VolumeGroupRole.MOBILITY.name());
                    }

                });
        List<NamedRelatedResourceRep> reps = Lists.newArrayList();
        for (VolumeGroupRestRep vg : volumeGroups) {
            reps.add(com.emc.vipr.client.core.util.ResourceUtils.createNamedRef(vg));
        }
        return reps;
    }

    public static VolumeGroupRestRep createMobilityGroup(String name, String description, Set<String> roles, String migrationGroupBy,
            String migrationType) {
        VolumeGroupCreateParam create = new VolumeGroupCreateParam();
        create.setName(name);
        create.setDescription(description);
        create.setRoles(roles);
        create.setMigrationGroupBy(migrationGroupBy);
        create.setMigrationType(migrationType);
        return BourneUtil.getViprClient().application().createApplication(create);
    }

    public static void deleteMobilityGroup(URI id) {
        BourneUtil.getViprClient().application().deleteApplication(id);
    }

    public static TaskList updateMobilityGroup(String name, String description, String id) {
        VolumeGroupUpdateParam update = new VolumeGroupUpdateParam();
        if (!name.isEmpty()) {
            update.setName(name);
        }
        if (!description.isEmpty()) {
            update.setDescription(description);
        }
        return BourneUtil.getViprClient().application().updateApplication(uri(id), update);
    }

    public static VolumeGroupRestRep getMobilityGroup(String id) {
        return BourneUtil.getViprClient().application().getApplication(uri(id));
    }
}