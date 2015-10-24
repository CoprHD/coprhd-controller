/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.services.util;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class Strings {
    public static String repr(final Object object) {
        if (object == null) {
            return "(null)";
        } else {
            final String string = object.toString();
            StringBuilder s = new StringBuilder();
            s.append("'");
            for (int i = 0; i < string.length(); i++) {
                final char c = string.charAt(i);
                if (c >= ' ' && c < 127) {
                    s.append(c);
                } else if (c == '\n') {
                    s.append("\\n");
                } else if (c == '\r') {
                    s.append("\\r");
                } else if (c == '\b') {
                    s.append("\\b");
                } else {
                    s.append("\\0");
                    s.append(Integer.toHexString(Character.getNumericValue(c)));
                }
            }
            s.append("'");
            return s.toString();
        }
    }

    public static String repr(final String string) {
        return repr((Object) string);
    }

    public static String repr(final Object... objs) {
        String[] reprs = new String[objs.length];
        for (int i = 0; i < reprs.length; i++) {
            reprs[i] = repr(objs[i]);
        }
        return Arrays.toString(reprs);
    }

    public static String repr(final String... strings) {
        return repr((Object[]) strings);
    }

    public static String repr(final List list) {
        return (list == null) ? "null" : repr((Object[]) list.toArray());
    }

    public static String repr(final Set set) {
        return (set == null) ? "null" : repr((Object[]) set.toArray());
    }

    public static void main(String[] args) {
        String[] a = { "ala", "ma", "kota" };
        String s = "Ala\nma\nKota\na kot ma ale\rA KOT\n\0\001\01000";
        System.out.println(s);
        System.out.println(Strings.repr(s));
        System.out.println(Strings.repr(a));
    }
}
