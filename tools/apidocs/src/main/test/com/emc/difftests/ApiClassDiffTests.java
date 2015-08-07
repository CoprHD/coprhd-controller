/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.difftests;

import com.emc.apidocs.model.ApiClass;
import com.emc.apidocs.model.ApiField;
import com.emc.apidocs.model.ChangeState;
import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.List;

public class ApiClassDiffTests {

    public static void main(String[] args) throws Exception {
        List<ApiField> sequenceA = Lists.newArrayList(newField("A"), newField("B"), newField("P"), newField("C"));
        List<ApiField> sequenceB = Lists.newArrayList(newField("A"), newField("B"), newField("E"), newField("D"), newField("C"));

        List<ApiField> diffList = generateMergedList(sequenceA, sequenceB);

        System.out.println("OUTPUT : ");
        for (ApiField s : diffList) {
            switch (s.changeState) {
                case NOT_CHANGED:
                    System.out.print("- ");
                    break;
                case REMOVED:
                    System.out.print("< ");
                    break;
                case ADDED:
                    System.out.print("> ");
                    break;
            }
            System.out.println(s.name);
        }

        ApiClass apiClass = new ApiClass();
        apiClass.fields = diffList;

        System.out.println("CONTAINS CHANGES :" + containsChanges(apiClass));
    }

    /**
     * For more information on the LCS algorithm, see http://en.wikipedia.org/wiki/Longest_common_subsequence_problem
     */
    private static int[][] computeLcs(List<ApiField> sequenceA, List<ApiField> sequenceB) {
        int[][] lcs = new int[sequenceA.size() + 1][sequenceB.size() + 1];

        for (int i = 0; i < sequenceA.size(); i++) {

            for (int j = 0; j < sequenceB.size(); j++) {

                if (sequenceA.get(i).compareTo(sequenceB.get(j)) == 0) {
                    lcs[i + 1][j + 1] = lcs[i][j] + 1;
                } else {
                    lcs[i + 1][j + 1] = Math.max(lcs[i][j + 1], lcs[i + 1][j]);
                }
            }
        }

        return lcs;
    }

    /**
     * Generates a merged list with changes
     */
    public static List<ApiField> generateMergedList(List<ApiField> sequenceA, List<ApiField> sequenceB)
    {
        int[][] lcs = computeLcs(sequenceA, sequenceB);

        List<ApiField> mergedFields = Lists.newArrayList();

        int aPos = sequenceA.size();
        int bPos = sequenceB.size();

        while (aPos > 0 || bPos > 0) {

            if (aPos > 0 && bPos > 0 && sequenceA.get(aPos - 1).compareTo(sequenceB.get(bPos - 1)) == 0) {
                ApiField field = sequenceA.get(aPos - 1);
                field.changeState = ChangeState.NOT_CHANGED;
                mergedFields.add(field);

                aPos--;
                bPos--;
            } else if (bPos > 0 && (aPos == 0 || lcs[aPos][bPos - 1] >= lcs[aPos - 1][bPos])) {
                ApiField field = sequenceB.get(bPos - 1);
                field.changeState = ChangeState.ADDED;
                mergedFields.add(field);
                bPos--;
            } else {
                ApiField field = sequenceA.get(aPos - 1);
                field.changeState = ChangeState.REMOVED;
                mergedFields.add(field);
                aPos--;
            }
        }

        // Backtracking generates the list from back to front,
        // so reverse it to get front-to-back.
        Collections.reverse(mergedFields);

        return mergedFields;
    }

    /**
     * @return Indicates if this class contains ANY changes (directly or within a fields type)
     */
    public static boolean containsChanges(ApiClass apiClass) {
        for (ApiField field : apiClass.fields) {
            if (field.changeState != ChangeState.NOT_CHANGED) {
                return true;
            }
        }

        for (ApiField field : apiClass.fields) {
            if (!field.isPrimitive()) {
                boolean containsChanges = containsChanges(field.type);

                if (containsChanges) {
                    return true;
                }
            }
        }

        return false;
    }

    private static ApiField newField(String name) {
        ApiField field = new ApiField();
        field.name = name;
        field.primitiveType = "String";

        return field;
    }
}
