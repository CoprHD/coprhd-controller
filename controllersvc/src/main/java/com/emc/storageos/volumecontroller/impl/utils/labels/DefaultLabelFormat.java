package com.emc.storageos.volumecontroller.impl.utils.labels;

import java.util.Collection;

/**
 * Default implementation that simply duplicates the first label.
 *
 * @author Ian Bibby
 */
public class DefaultLabelFormat implements LabelFormat {

    private String label;

    @Override
    public boolean matches(String label) {
        this.label = label;
        return true;
    }

    @Override
    public boolean matches(Collection<String> labels) {
        return matches(labels.iterator().next());
    }

    @Override
    public String next() {
        return label;
    }
}
