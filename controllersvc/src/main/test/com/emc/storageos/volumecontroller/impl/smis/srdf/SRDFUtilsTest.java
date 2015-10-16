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

        // 10 character RDF group names
        project2RdfGroup.put("0123456789", asList("V-01234567", "0123456789"));
        project2RdfGroup.put("01234 6789", asList("V-01234_67", "01234_6789"));
        project2RdfGroup.put("0123456789 hostname", asList("V-01234567", "0123456789"));
        project2RdfGroup.put("01234 6789 hostname", asList("V-01234_67", "01234_6789"));

        // 9 character RDF group names
        project2RdfGroup.put("012345678", asList("V-01234567", "012345678"));
        project2RdfGroup.put("01234 678", asList("V-01234_67", "01234_678"));
        project2RdfGroup.put("012345678 hostname", asList("V-01234567", "012345678_", "012345678"));
        project2RdfGroup.put("01234 678 hostname", asList("V-01234_67", "01234_678_", "01234_678"));

        for (Map.Entry<String, List<String>> entry : project2RdfGroup.entrySet()) {
            project.setLabel(entry.getKey());
            StringSet qualifyingNames = SRDFUtils.getQualifyingRDFGroupNames(project);

            for (String name : qualifyingNames) {
                Assert.assertTrue(entry.getValue().contains(name));
            }

        }
    }
}
