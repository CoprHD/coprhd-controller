/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import java.util.Comparator;

public class StringComparator implements Comparator<String> {
    private boolean caseSensitive;
    private boolean descending;

    public StringComparator() {
        this(true);
    }

    public StringComparator(boolean caseSensitive) {
        this(caseSensitive, false);
    }

    public StringComparator(boolean caseSensitive, boolean descending) {
        this.caseSensitive = caseSensitive;
        this.descending = descending;
    }

    @Override
    public int compare(String o1, String o2) {
        int result = compareString(o1, o2);
        return descending ? -result : result;
    }

    protected int compareString(String o1, String o2) {
        if (o1 != null && o2 != null) {
            if (caseSensitive) {
                return o1.compareTo(o2);
            }
            else {
                return o1.compareToIgnoreCase(o2);
            }
        }
        else if (o1 != null) {
            return 1;
        }
        else if (o2 != null) {
            return -1;
        }
        else {
            return 0;
        }
    }
}
