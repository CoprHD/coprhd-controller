package com.emc.storageos.volumecontroller.impl.smis.srdf;

import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StringSet;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;

/**
 * @author Ian Bibby
 */
public class SRDFUtilsTest {

    @Test
    public void qualifyingRDFGroupName() {
        Project project = new Project();
        Map<String, List<String>> project2RdfGroup = new HashMap<>();

        project2RdfGroup.put("0123456789", asList("V-01234567", "0123456789"));
        project2RdfGroup.put("01234 6789", asList("V-01234_67", "01234_6789", "01234 6789"));
        project2RdfGroup.put("1234_1234 hostname", asList("V-1234_123", "1234_1234"));
        project2RdfGroup.put("1234_12345 hostname", asList("V-1234_123", "1234_12345"));
        project2RdfGroup.put("560562A01 hostname", asList("V-560562A0", "560562A01"));

        for (Map.Entry<String, List<String>> entry : project2RdfGroup.entrySet()) {
            project.setLabel(entry.getKey());
            StringSet qualifyingNames = SRDFUtils.getQualifyingRDFGroupNames(project);

            Assert.assertTrue(qualifyingNames.containsAll(entry.getValue()));
        }
    }
}
