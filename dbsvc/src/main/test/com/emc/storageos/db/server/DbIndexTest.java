/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server;

import static org.junit.Assert.*;

import java.beans.PropertyDescriptor;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.Constraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.ContainmentPermissionsConstraint;
import com.emc.storageos.db.client.constraint.ContainmentPrefixConstraint;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.constraint.impl.AlternateIdConstraintImpl;
import com.emc.storageos.db.client.constraint.impl.ContainmentConstraintImpl;
import com.emc.storageos.db.client.constraint.impl.ContainmentLabelConstraintImpl;
import com.emc.storageos.db.client.constraint.impl.ContainmentPermissionsConstraintImpl;
import com.emc.storageos.db.client.constraint.impl.DecommissionedConstraintImpl;
import com.emc.storageos.db.client.constraint.impl.LabelConstraintImpl;
import com.emc.storageos.db.client.impl.AltIdDbIndex;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.DbClientContext;
import com.emc.storageos.db.client.impl.DbIndex;
import com.emc.storageos.db.client.impl.DecommissionedDbIndex;
import com.emc.storageos.db.client.impl.NamedRelationDbIndex;
import com.emc.storageos.db.client.impl.PermissionsDbIndex;
import com.emc.storageos.db.client.impl.PrefixDbIndex;
import com.emc.storageos.db.client.impl.RelationDbIndex;
import com.emc.storageos.db.client.impl.ScopedLabelDbIndex;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.*;
import com.emc.storageos.db.common.VdcUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.cassandra.io.sstable.Descriptor;
import org.apache.cassandra.tools.NodeProbe;
import org.apache.cassandra.tools.SSTableExport;

// RaceCondition test is to ensure even any rarely happens race condition do occur, DB is still left in an "acceptable" state
// "acceptable" here means:
//    1) cross field data is still somewhat consistency
//    2) index CF is consistent with object CF
//    3) no zombie index entries
//    4) other

@SuppressWarnings("pmd:ArrayIsStoredDirectly")
abstract class ObjectModifier<T extends DataObject> implements Runnable
{
    // The synchronization object used by outside to communicate
    private final DbClientTest.StepLock stepLock;

    private Integer[] readySync;
    private DbClientTest.DbClientImplUnitTester dbClient;
    private Class<T> clazz;
    public volatile URI objId;
    protected Object context;
    protected DataObjectType doType;

    public ObjectModifier() {
        this.stepLock = null;
    }

    public ObjectModifier(Class<? extends DataObject> clazz, DbClientTest.DbClientImplUnitTester dbClient, Integer[] readySync,
            Object context) {
        this.clazz = (Class<T>) clazz;
        this.doType = TypeMap.getDoType(clazz);

        this.dbClient = dbClient;
        this.readySync = readySync;
        this.context = context;

        this.stepLock = new DbClientTest.StepLock();
        this.stepLock.clientId = this.readySync[0]++;
        this.stepLock.step = DbClientTest.StepLock.Step.Pre;
    }

    public void moveToState(DbClientTest.StepLock.Step step) {
        this.stepLock.moveToState(step);
    }

    @Override
    public void run() {
        // Initialize per thread step lock for DbClient
        dbClient.threadStepLock.set(this.stepLock);

        for (;;) {
            // Notify main thread that this thread is ready
            synchronized (this.readySync) {
                this.readySync[0]--;
                this.readySync.notify();
            }

            // Wait for read green light
            if (!this.stepLock.waitForStep(DbClientTest.StepLock.Step.Read)) {
                return;
            }

            try {
                // Now, load the object from DB
                T obj = this.dbClient.queryObject(this.clazz, this.objId);

                this.stepLock.ackStep(DbClientTest.StepLock.Step.Read);

                // Apply modification to object
                modify(obj);

                // Go to persist
                this.dbClient.persistObject(obj);
            } finally {
                // Here we need to ensure we consumed all states
                this.stepLock.drain(DbClientTest.StepLock.Step.CleanupOldColumns);
            }
        }
    }

    public abstract void modify(T obj);
}

interface IndexVerifier {
    public void verify(Class<? extends DataObject> clazz, URI id, DbClient client);
}

class SingleFieldIndexVerifier implements IndexVerifier {

    String fieldName;

    public SingleFieldIndexVerifier(String fieldName) {
        this.fieldName = fieldName;
    }

    @Override
    public void verify(Class<? extends DataObject> clazz, URI id, DbClient client) {

        // Get meta data about the object, field and index we're going to test
        DataObjectType doType = TypeMap.getDoType(clazz);
        ColumnField field = doType.getColumnField(fieldName);
        if (field == null) {
            throw new NullPointerException(String.format("Cannot find field %s from class %s", fieldName, clazz.getSimpleName()));
        }
        DbIndex index = field.getIndex();

        // Load object first
        DataObject obj = (DataObject) client.queryObject(clazz, id);

        // Get current value of indexed field
        Object val = null;
        try {
            val = field.getPropertyDescriptor().getReadMethod().invoke(obj);
        } catch (IllegalAccessException | IllegalArgumentException
                | InvocationTargetException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            Assert.fail("Cannot get field value");
        }

        boolean indexByKey = field.getPropertyDescriptor().getReadMethod().getAnnotation(IndexByKey.class) != null;

        // See which type the index is, and call corresponding verification functions
        if (index instanceof AltIdDbIndex) {
            verifyAltIdDbIndex(obj, field, val, indexByKey, client);
        } else if (index instanceof RelationDbIndex) {
            verifyRelationDbIndex(obj, field, val, indexByKey, client);
        } else if (index instanceof NamedRelationDbIndex) {
            verifyNamedRelationDbIndex(obj, field, val, indexByKey, client);
        } else if (index instanceof PrefixDbIndex) {
            verifyPrefixDbIndex(obj, field, val, indexByKey, client);
        } else if (index instanceof DecommissionedDbIndex) {
            verifyDecommissionedDbIndex(obj, field, val, indexByKey, client);
        } else if (index instanceof PermissionsDbIndex) {
            verifyPermissionsDbIndex(obj, field, val, indexByKey, client);
        } else if (index instanceof ScopedLabelDbIndex) {
            verifyScopedLabelDbIndex(obj, field, val, indexByKey, client);
        } else {
            System.out.printf("Unsupported index type %s%n", index.getClass().getSimpleName());
        }
    }

    private void verifyContain(Constraint c, URI uri, int count, DbClient client) {
        URIQueryResultList list = new URIQueryResultList();
        client.queryByConstraint(c, list);

        int realCount = 0;
        boolean found = false;
        for (URI elem : list) {
            realCount++;

            if (uri != null && uri.equals(elem)) {
                found = true;
            }
        }

        if (uri != null) {
            assertTrue(found);
        }

        if (count >= 0) {
            assertTrue(String.format("Found %d URIs while %d is expected", realCount, count), realCount == count);
        }
    }

    private void verifyAltIdDbIndex(DataObject obj, ColumnField field, Object val, boolean indexByKey, DbClient client) {
        switch (field.getType()) {
            case Primitive: {
                AlternateIdConstraint constraint = new AlternateIdConstraintImpl(field, (String) val);
                verifyContain(constraint, obj.getId(), -1, client);
            }
                break;
            case TrackingMap:
                for (Map.Entry entry : ((AbstractChangeTrackingMap<?>) val).entrySet())
                {
                    Object altId = indexByKey ? entry.getKey() : entry.getValue();
                    AlternateIdConstraint constraint = new AlternateIdConstraintImpl(field, (String) altId);
                    verifyContain(constraint, obj.getId(), -1, client);
                }
                break;
            case TrackingSet:
                for (String key : (AbstractChangeTrackingSet<String>) val)
                {
                    AlternateIdConstraint constraint = new AlternateIdConstraintImpl(field, key);
                    verifyContain(constraint, obj.getId(), -1, client);
                }
                break;
            case Id:
            case NamedURI:
            case NestedObject:
            case TrackingSetMap:
            default:
                throw new IllegalArgumentException(String.format("Field type %s is not supported by AltIdDbIndex", field.getType()
                        .toString()));
        }

    }

    private void verifyRelationDbIndex(DataObject obj, ColumnField field, Object val, boolean indexByKey, DbClient client) {
        switch (field.getType()) {
            case Primitive: {
                ContainmentConstraint constraint = new ContainmentConstraintImpl((URI) val, obj.getClass(), field);
                verifyContain(constraint, obj.getId(), -1, client);
            }
                break;
            case TrackingMap:
                for (String key : ((AbstractChangeTrackingMap<String>) val).keySet())
                {
                    ContainmentConstraint constraint = new ContainmentConstraintImpl(URI.create(key), obj.getClass(), field);
                    verifyContain(constraint, obj.getId(), -1, client);
                }
                break;
            case TrackingSet:
                for (String key : (AbstractChangeTrackingSet<String>) val)
                {
                    ContainmentConstraint constraint = new ContainmentConstraintImpl(URI.create(key), obj.getClass(), field);
                    verifyContain(constraint, obj.getId(), -1, client);
                }
                break;
            case Id:
            case NamedURI:
            case NestedObject:
            case TrackingSetMap:
            default:
                throw new IllegalArgumentException(String.format("Field type %s is not supported by RelationDbIndex", field.getType()
                        .toString()));
        }
    }

    private void verifyNamedRelationDbIndex(DataObject obj, ColumnField field, Object val, boolean indexByKey, DbClient client) {
        switch (field.getType()) {
            case NamedURI: {
                NamedURI namedUriVal = (NamedURI) val;
                ContainmentLabelConstraintImpl constraint = new ContainmentLabelConstraintImpl(namedUriVal.getURI(), namedUriVal.getName(),
                        field);
                verifyContain(constraint, obj.getId(), -1, client);
            }
                break;
            case Id:
            case NestedObject:
            case Primitive:
            case TrackingMap:
            case TrackingSet:
            case TrackingSetMap:
            default:
                throw new IllegalArgumentException(String.format("Field type %s is not supported by NamedRelationDbIndex", field.getType()
                        .toString()));
        }
    }

    private void verifyPrefixDbIndex(DataObject obj, ColumnField field, Object val, boolean indexByKey, DbClient client) {
        switch (field.getType()) {
            case Primitive: {
                LabelConstraintImpl constraint = new LabelConstraintImpl((String) val, field);
                verifyContain(constraint, obj.getId(), -1, client);
            }
                break;
            case Id:
            case NestedObject:
            case NamedURI:
            case TrackingMap:
            case TrackingSet:
            case TrackingSetMap:
            default:
                throw new IllegalArgumentException(String.format("Field type %s is not supported by PrefixDbIndex", field.getType()
                        .toString()));
        }
    }

    private void verifyDecommissionedDbIndex(DataObject obj, ColumnField field, Object val, boolean indexByKey, DbClient client) {
        switch (field.getType()) {
            case Primitive: {
                DecommissionedConstraintImpl constraint = new DecommissionedConstraintImpl(obj.getClass(), field, (boolean) val);
                verifyContain(constraint, obj.getId(), -1, client);
            }
                break;
            case Id:
            case NamedURI:
            case NestedObject:
            case TrackingMap:
            case TrackingSet:
            case TrackingSetMap:
            default:
                throw new IllegalArgumentException(String.format("Field type %s is not supported by DecommissionedDbIndex", field.getType()
                        .toString()));
        }
    }

    private void verifyPermissionsDbIndex(DataObject obj, ColumnField field, Object val, boolean indexByKey, DbClient client) {
        switch (field.getType()) {
            case TrackingSetMap:
                for (String key : ((AbstractChangeTrackingSetMap<String>) val).keySet())
                {
                    ContainmentPermissionsConstraintImpl constraint = new ContainmentPermissionsConstraintImpl(key, field, obj.getClass());

                    NamedElementQueryResultList results = new NamedElementQueryResultList();
                    client.queryByConstraint(constraint, results);

                    HashSet<String> setFromIndex = new HashSet<String>();
                    for (NamedElementQueryResultList.NamedElement elem : results) {
                        if (elem.getId().equals(obj.getId())) {
                            setFromIndex.add(elem.getName());
                        }
                    }

                    AbstractChangeTrackingSet<String> values = ((AbstractChangeTrackingSetMap<String>) val).get(key);
                    assertTrue("The value set from index is not same as what is currently in object", setFromIndex.equals(values));
                }
                break;
            case Id:
            case NamedURI:
            case NestedObject:
            case Primitive:
            case TrackingMap:
            case TrackingSet:
            default:
                throw new IllegalArgumentException(String.format("Field type %s is not supported by PermissionsDbIndex", field.getType()
                        .toString()));
        }
    }

    private void verifyScopedLabelDbIndex(DataObject obj, ColumnField field, Object val, boolean indexByKey, DbClient client) {
        switch (field.getType()) {
            case TrackingSet:
                for (ScopedLabel scopedLabel : (AbstractChangeTrackingSet<ScopedLabel>) val)
                {
                    LabelConstraintImpl constraint = new LabelConstraintImpl(URI.create(scopedLabel.getScope()), scopedLabel.getLabel(),
                            field);
                    verifyContain(constraint, obj.getId(), -1, client);
                }
                break;
            case Id:
            case NamedURI:
            case NestedObject:
            case Primitive:
            case TrackingMap:
            case TrackingSetMap:
            default:
                throw new IllegalArgumentException(String.format("Field type %s is not supported by ScopedLabelDbIndex", field.getType()
                        .toString()));
        }
    }

}

// This class holds the simple test cases that blindly set predefined values to single field,
// complex test cases please use ObjectModifier<> directly instead.

@SuppressWarnings("pmd:ArrayIsStoredDirectly")
class IndexTestData {

    public String name; // Name of the test case
    // Which object type to test
    public Class<? extends DataObject> clazz;

    // Initial state
    // Primitive
    // "field=", value
    // "field=", null
    // Set
    // "field<", key
    // "field>", key
    // Map
    // "field<", key, value
    // "field>", key
    // MapSet
    // "field<", key1, key2
    // "field>", key1, key2
    public Object[] initial;

    // Multiple concurrent changes
    public Object[][] modifiers;

    public IndexVerifier verifier;

    public IndexTestData(String name, Class<? extends DataObject> clazz, Object[] initial, Object[][] modifiers, IndexVerifier verifier) {
        this.name = name;
        this.clazz = clazz;
        this.initial = initial;
        this.modifiers = modifiers;
        this.verifier = verifier;
    }

    public static boolean isEqual(DataObject obj, Object[] ops)
    {
        DataObject shouldBe = createInitial(obj.getClass(), obj.getId(), ops);

        // Get DataObjectType
        DataObjectType doType = TypeMap.getDoType(obj.getClass());

        for (ColumnField field : doType.getColumnFields()) {
            if (!shouldBe.isChanged(field.getName())) {
                continue;
            }

            Method readMethod = field.getPropertyDescriptor().getReadMethod();

            try {
                Object shouldBeVal = readMethod.invoke(shouldBe);
                Object realVal = readMethod.invoke(obj);

                if (!equalsObject(realVal, shouldBeVal)) {
                    return false;
                }
            } catch (IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                return false;
            }
        }

        return true;
    }

    public static boolean equalsObject(Object a, Object b) {
        if (a instanceof Map || b instanceof Map) {
            return equalsMap((Map) a, (Map) b);
        }
        if (a instanceof Set && ((Set<?>) a).isEmpty()) {
            a = null;
        }
        if (b instanceof Set && ((Set<?>) b).isEmpty()) {
            b = null;
        }
        return a == null || b == null ? a == b : a.equals(b);
    }

    // Compare two Maps, null values are treated as no such key
    private static <K, V> boolean equalsMap(Map<K, V> a, Map<K, V> b) {

        if (a != null) {
            for (Map.Entry<K, V> entry : a.entrySet()) {
                V valB = b == null ? null : b.get(entry.getKey());
                if (!equalsObject(entry.getValue(), valB)) {
                    return false;
                }
            }
        }

        if (b != null) {
            for (Map.Entry<K, V> entry : b.entrySet()) {
                if (entry.getValue() != null && (a == null || !a.containsKey(entry.getKey()))) {
                    return false;
                }
            }
        }

        return true;
    }

    public static DataObject createInitial(Class<? extends DataObject> clazz, URI id, Object[] ops) {
        DataObject obj;
        try {
            obj = clazz.getConstructor(new Class<?>[0]).newInstance(new Object[0]);
        } catch (InstantiationException | IllegalAccessException
                | IllegalArgumentException | InvocationTargetException
                | NoSuchMethodException | SecurityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return null;
        }

        obj.setId(id);
        apply(obj, TypeMap.getDoType(clazz), ops);
        return obj;
    }

    // Returns a list of fields touched
    public static void apply(DataObject obj, DataObjectType doType, Object[] ops) {

        try {

            for (int i = 0; i < ops.length; i++) {
                if (!(ops[i] instanceof String)) {
                    throw new IllegalArgumentException();
                }

                String op = (String) ops[i];

                // Get name of the field
                String fieldName = op.substring(0, op.length() - 1);
                op = op.substring(op.length() - 1, op.length());

                // Get type of the field
                ColumnField field = doType.getColumnField(fieldName);

                PropertyDescriptor propDesc = field.getPropertyDescriptor();

                switch (field.getType()) {
                    case Primitive:
                    case NamedURI:
                    case Id:
                    case NestedObject:
                        if (op.charAt(0) != '=') {
                            throw new IllegalArgumentException();
                        }
                        propDesc.getWriteMethod().invoke(obj, ops[++i]);
                        break;
                    case TrackingSet:
                        if (op.charAt(0) == '<') {
                            AbstractChangeTrackingSet set = (AbstractChangeTrackingSet) propDesc.getReadMethod().invoke(obj);
                            if (set == null) {
                                set = (AbstractChangeTrackingSet) propDesc.getPropertyType().getConstructor(new Class<?>[0])
                                        .newInstance(new Object[0]);
                                propDesc.getWriteMethod().invoke(obj, set);
                            }
                            set.add(ops[++i]);
                        } else if (op.charAt(0) == '>') {
                            AbstractChangeTrackingSet set = (AbstractChangeTrackingSet) propDesc.getReadMethod().invoke(obj);
                            set.remove(ops[++i]);
                        } else if (op.charAt(0) == '=') {
                            propDesc.getWriteMethod().invoke(obj, ops[++i]);
                        }
                        break;
                    case TrackingMap:
                        if (op.charAt(0) == '<') {
                            AbstractChangeTrackingMap map = (AbstractChangeTrackingMap) propDesc.getReadMethod().invoke(obj);
                            if (map == null) {
                                map = (AbstractChangeTrackingMap) propDesc.getPropertyType().getConstructor(new Class<?>[0])
                                        .newInstance(new Object[0]);
                                propDesc.getWriteMethod().invoke(obj, map);
                            }
                            map.put((String) ops[i + 1], ops[i + 2]);
                            i += 2;
                        } else if (op.charAt(0) == '>') {
                            AbstractChangeTrackingMap map = (AbstractChangeTrackingMap) propDesc.getReadMethod().invoke(obj);
                            map.remove((String) ops[++i]);
                        } else if (op.charAt(0) == '=') {
                            propDesc.getWriteMethod().invoke(obj, ops[++i]);
                        }
                        break;
                    case TrackingSetMap:
                        if (op.charAt(0) == '<') {
                            AbstractChangeTrackingSetMap setMap = (AbstractChangeTrackingSetMap) propDesc.getReadMethod().invoke(obj);
                            if (setMap == null) {
                                setMap = (AbstractChangeTrackingSetMap) propDesc.getPropertyType().getConstructor(new Class<?>[0])
                                        .newInstance(new Object[0]);
                                propDesc.getWriteMethod().invoke(obj, setMap);
                            }
                            setMap.put((String) ops[i + 1], ops[i + 2]);
                            i += 2;
                        } else if (op.charAt(0) == '>') {
                            AbstractChangeTrackingSetMap setMap = (AbstractChangeTrackingSetMap) propDesc.getReadMethod().invoke(obj);
                            setMap.remove((String) ops[i + 1], ops[i + 2]);
                            i += 2;
                        } else if (op.charAt(0) == '=') {
                            propDesc.getWriteMethod().invoke(obj, ops[++i]);
                        }
                        break;
                    default:
                        break;
                }
            }

        } catch (IllegalArgumentException
                | IllegalAccessException | InvocationTargetException | InstantiationException | NoSuchMethodException | SecurityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }
}

abstract class OperationSequenceGenerator {
    int objCount;
    int opCount;
    long readOnlyOpsMask;
    int[] nextOps;
    int[] generatedSequence;
    int generatedCount;
    int targetCount;
    int generatedSequenceCount;

    public OperationSequenceGenerator(int objCount, int opCount, long readOnlyOpsMask) {
        this.objCount = objCount;
        this.opCount = opCount;
        this.readOnlyOpsMask = readOnlyOpsMask;

        this.nextOps = new int[objCount];
        this.targetCount = objCount * opCount;
        this.generatedSequence = new int[this.targetCount * 2];
        this.generatedCount = 0;
    }

    public void generate() {
        // Choose each object as start
        for (int i = 0; i < this.objCount; i++) {
            if (this.nextOps[i] == this.opCount) {
                continue;
            }

            // Found one new operation, however, for read operations
            if (this.generatedCount > 0 && (this.readOnlyOpsMask & (1 << this.nextOps[i])) != 0) {
                int prevObj = this.generatedSequence[this.generatedCount * 2 - 2];
                int prevOp = this.generatedSequence[this.generatedCount * 2 - 1];
                if ((this.readOnlyOpsMask & (1 << prevOp)) != 0 && (i < prevObj || this.nextOps[i] < prevOp)) {
                    continue;
                }
            }

            this.generatedSequence[this.generatedCount * 2 + 0] = i;
            this.generatedSequence[this.generatedCount * 2 + 1] = this.nextOps[i]++;
            if (++this.generatedCount == this.targetCount) { // Done one generation
                onSequence(this.generatedSequenceCount++, this.generatedSequence);
            } else {
                generate();
            }

            this.nextOps[i]--;
            this.generatedCount--;
        }
    }

    protected abstract void onSequence(int index, int[] sequence);
}

public class DbIndexTest extends DbsvcTestBase {
    private static final Logger _logger = LoggerFactory.getLogger(DbIndexTest.class);

    private DbClientTest.DbClientImplUnitTester _dbClient;

    private NodeProbe probe;

    static {
        _startJmx = true;
    }

    private DbClientTest.DbClientImplUnitTester createClient() {
        DbClientTest.DbClientImplUnitTester dbClient = new DbClientTest.DbClientImplUnitTester();
        dbClient.setCoordinatorClient(_coordinator);
        dbClient.setDbVersionInfo(sourceVersion);
        dbClient.setBypassMigrationLock(true);
        _encryptionProvider.setCoordinator(_coordinator);
        dbClient.setEncryptionProvider(_encryptionProvider);

        DbClientContext localCtx = new DbClientContext();
        localCtx.setClusterName("Test");
        localCtx.setKeyspaceName("Test");
        dbClient.setLocalContext(localCtx);

        return dbClient;
    }

    @Before
    public void setupTest() throws IOException {

        DbClientTest.DbClientImplUnitTester dbClient = createClient();

        VdcUtil.setDbClient(dbClient);

        dbClient.setBypassMigrationLock(false);
        dbClient.start();

        _dbClient = dbClient;

        // this.probe = new NodeProbe("127.0.0.1", 7199);
    }

    @After
    public void teardown() {
        if (_dbClient instanceof DbClientTest.DbClientImplUnitTester) {
            ((DbClientTest.DbClientImplUnitTester) _dbClient).removeAll();
        }
    }

    @SuppressWarnings("unchecked")
    private void testRaceCondition(final IndexTestData test) throws InstantiationException, IllegalAccessException,
            IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {

        final Integer[] readySync = new Integer[] { 0 };

        // Allocate threads
        final ObjectModifier[] modifiers = new ObjectModifier[test.modifiers.length];
        final Thread[] threads = new Thread[test.modifiers.length];
        for (int i = 0; i < test.modifiers.length; i++) {
            modifiers[i] = new ObjectModifier(test.clazz, this._dbClient, readySync, (Object) test.modifiers[i]) {

                @Override
                public void modify(DataObject obj) {
                    Object[] ops = (Object[]) this.context;
                    IndexTestData.apply(obj, this.doType, ops);
                }
            };
            threads[i] = new Thread(modifiers[i]);
        }

        // Start each thread
        for (int i = 0; i < threads.length; i++) {
            threads[i].start();
        }

        final DbClientTest.StepLock.Step[] steps = new DbClientTest.StepLock.Step[]
        {
                DbClientTest.StepLock.Step.Read,
                DbClientTest.StepLock.Step.InsertNewColumns,
                DbClientTest.StepLock.Step.FetchNewestColumns,
                DbClientTest.StepLock.Step.CleanupOldColumns
        };

        OperationSequenceGenerator generator = new OperationSequenceGenerator(modifiers.length, steps.length, 9) {

            @Override
            protected void onSequence(int index, int[] sequence) {
                // Wait until all thread done their initialization
                synchronized (readySync) {
                    while (readySync[0] > 0) {
                        try {
                            readySync.wait();
                        } catch (InterruptedException e) {
                            _logger.warn("Thread is interrupted", e);
                        }
                    }

                    readySync[0] = threads.length;
                    // Reset sync number for next round
                }

                // Create initial object
                DataObject initObj = IndexTestData.createInitial(test.clazz, URIUtil.createId(test.clazz), test.initial);
                _dbClient.createObject(initObj);

                // Notify each modifier about the new Id
                for (int i = 0; i < modifiers.length; i++) {
                    modifiers[i].objId = initObj.getId();
                }

                // Run the sequence
                for (int i = 0; i < sequence.length / 2; i++) {
                    int objOrdinal = sequence[i * 2 + 0];
                    int opOrdinal = sequence[i * 2 + 1];
                    modifiers[objOrdinal].moveToState(steps[opOrdinal]);
                }

                // Now, verify the object is consistent with its index
                test.verifier.verify(initObj.getClass(), initObj.getId(), _dbClient);

                // TODO: Verify there's no other way to reach the object in same index

                // Cleanup, delete the object
                // _dbClient.removeObject(initObj);

                // TODO: Verify the index CF is empty
            }
        };

        generator.generate();

        // Notify all threads to quit
        for (int i = 0; i < modifiers.length; i++) {
            modifiers[i].moveToState(DbClientTest.StepLock.Step.Quit);
        }

        for (int i = 0; i < threads.length; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {
                _logger.warn("Thread is interrupted", e);
            }
        }
    }

    @Test
    public void testIndexRaceCondition() throws InstantiationException, IllegalAccessException, IllegalArgumentException,
            InvocationTargetException, NoSuchMethodException, SecurityException {

        // We're going to need synchronize between threads calling into DbClient
        this._dbClient.threadStepLock = new ThreadLocal<DbClientTest.StepLock>();

        URI vol1Uri = URIUtil.createId(Volume.class);
        URI varr1Uri = URIUtil.createId(VirtualArray.class);

        IndexTestData[] tests = new IndexTestData[]
        {
                new IndexTestData
                (
                        "Test AltIdDbIndex on String field",
                        Volume.class,
                        new Object[] { "personality=", "abc" }, // Initial state
                        new Object[][]
                        {
                                new Object[] { "personality=", "def" },
                                new Object[] { "personality=", "ghi" },
                        },
                        new SingleFieldIndexVerifier("personality")
                ),
                new IndexTestData
                (
                        "Test AltIdDbIndex on StringSet field",
                        AuthnProvider.class,
                        new Object[] { "domains<", "abc" }, // Initial state
                        new Object[][]
                        {
                                new Object[] { "domains>", "abc" },
                                new Object[] { "domains<", "def" },
                        },
                        new SingleFieldIndexVerifier("domains")
                ),
                new IndexTestData
                (
                        "Test AltIdDbIndex on StringMap field",
                        Network.class,
                        new Object[] { "endpoints<", "abc", "123" }, // Initial state
                        new Object[][]
                        {
                                new Object[] { "endpoints>", "abc" },
                                new Object[] { "endpoints<", "def", "456" },
                        },
                        new SingleFieldIndexVerifier("endpoints")
                ),

                new IndexTestData
                (
                        "Test PrefixDbIndex",
                        Cluster.class,
                        new Object[] { "label=", "abc" }, // Initial state
                        new Object[][]
                        {
                                new Object[] { "label=", "def" },
                                new Object[] { "label=", "ghi" },
                        },
                        new SingleFieldIndexVerifier("label")
                ),

                new IndexTestData
                (
                        "Test RelationDbIndex on URI field",
                        Host.class,
                        new Object[] { "cluster=", URIUtil.createId(Cluster.class) }, // Initial state
                        new Object[][]
                        {
                                new Object[] { "cluster=", URIUtil.createId(Cluster.class) },
                                new Object[] { "cluster=", URIUtil.createId(Cluster.class) },
                        },
                        new SingleFieldIndexVerifier("cluster")
                ),
                new IndexTestData
                (
                        "Test RelationDbIndex on StringMap field",
                        ExportGroup.class,
                        new Object[] { "volumes<", vol1Uri.toString(), "111" }, // Initial state
                        new Object[][]
                        {
                                new Object[] { "volumes>", vol1Uri.toString() },
                                new Object[] { "volumes<", URIUtil.createId(Volume.class).toString(), "222" },
                        },
                        new SingleFieldIndexVerifier("volumes")
                ),
                new IndexTestData
                (
                        "Test RelationDbIndex on StringSet field",
                        VirtualPool.class,
                        new Object[] { "virtualArrays<", varr1Uri.toString() }, // Initial state
                        new Object[][]
                        {
                                new Object[] { "virtualArrays>", varr1Uri.toASCIIString() },
                                new Object[] { "virtualArrays<", URIUtil.createId(VirtualArray.class).toString() },
                        },
                        new SingleFieldIndexVerifier("virtualArrays")
                ),

                new IndexTestData
                (
                        "Test NamedRelationDbIndex",
                        BlockMirror.class,
                        new Object[] { "source=", new NamedURI(URIUtil.createId(Volume.class), "abcde") }, // Initial state
                        new Object[][]
                        {
                                new Object[] { "source=", new NamedURI(URIUtil.createId(Volume.class), "fghij") },
                                new Object[] { "source=", new NamedURI(URIUtil.createId(Volume.class), "klmno") },
                        },
                        new SingleFieldIndexVerifier("source")
                ),

                // new IndexTestData
                // (
                // "Test DecommissionedDbIndex",
                // BlockMirror.class,
                // new Object[] {"inactive=", false}, // Initial state
                // new Object[][]
                // {
                // new Object[] {"inactive=", true},
                // new Object[] {"inactive=", false},
                // },
                // new SingleFieldIndexVerifier("inactive")
                // ),

                new IndexTestData
                (
                        "Test PermissionsDbIndex",
                        VirtualDataCenter.class,
                        new Object[] { "role-assignment<", "user1", "role1" }, // Initial state
                        new Object[][]
                        {
                                new Object[] { "role-assignment>", "user1", "role1" },
                                new Object[] { "role-assignment<", "user2", "role1" },
                        },
                        new SingleFieldIndexVerifier("role-assignment")
                ),

                new IndexTestData
                (
                        "Test ScopedLabelDbIndex",
                        FCEndpoint.class,
                        new Object[] { "tags<", new ScopedLabel("scope1", "label1") }, // Initial state
                        new Object[][]
                        {
                                new Object[] { "tags>", new ScopedLabel("scope1", "label1") },
                                new Object[] { "tags<", new ScopedLabel("scope2", "label1") },
                        },
                        new SingleFieldIndexVerifier("tags")
                ),
        };

        for (int i = 0; i < tests.length; i++) {
            testRaceCondition(tests[i]);
        }
    }

    @Test
    public void testInactive() throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException,
            NoSuchMethodException, SecurityException {

        // We're going to need synchronize between threads calling into DbClient
        this._dbClient.threadStepLock = new ThreadLocal<DbClientTest.StepLock>();

        URI vol1Uri = URIUtil.createId(Volume.class);
        URI varr1Uri = URIUtil.createId(VirtualArray.class);

        IndexTestData[] tests = new IndexTestData[]
        {
                new IndexTestData
                (
                        "Test inactive with other index",
                        Volume.class,
                        new Object[] { "personality=", "abc" }, // Initial state
                        new Object[][]
                        {
                                new Object[] { "inactive=", true },
                                new Object[] { "personality=", "ghi" },
                        },
                        new IndexVerifier() {

                            @Override
                            public void verify(Class<? extends DataObject> clazz, URI id, DbClient client) {
                                Volume vol = (Volume) client.queryObject(clazz, id);
                                String per = vol.getPersonality();

                                DataObjectType doType = TypeMap.getDoType(clazz);

                                AlternateIdConstraint constraint = new AlternateIdConstraintImpl(doType.getColumnField("personality"), per);

                                URIQueryResultList list = new URIQueryResultList();
                                client.queryByConstraint(constraint, list);

                                for (URI elem : list) {
                                    assertTrue("The index of .personality should be removed", !elem.equals(id));
                                }
                            }

                        }
                ),

        };

        for (int i = 0; i < tests.length; i++) {
            testRaceCondition(tests[i]);
        }
    }

    public enum Op {
        PrimitiveAdd,
        PrimitiveRemove,

        SetAdd,
        SetRemove,
        // Add+Remove, Remove+Add

        MapAdd,
        MapRemove,
        // Add+Remove, Remove+Add, Add+Modify, Add+ModifySame, ...

        SetMapAdd, // Add a key to existing set
        SetMapRemove, // Remove entire set
    }

    @SuppressWarnings("pmd:ArrayIsStoredDirectly")
    static class TestData {
        public String name; // Name of the test case
        public Object[] initial; // Initial state in DB that two threads will read
        public Object[] rushIn; // this thread will write first
        public Object[] primary; // this thread will write last
        public Object[] target;

        public TestData(String name, Object[] initial, Object[] rushIn, Object[] primary) {
            this.name = name;
            this.initial = initial;
            this.rushIn = rushIn;
            this.primary = primary;
        }

        public TestData(String name, Object[] initial, Object[] rushIn, Object[] primary, Object[] target) {
            this.name = name;
            this.initial = initial;
            this.rushIn = rushIn;
            this.primary = primary;
            this.target = target;
        }
    }

    static TestData[] testData = new TestData[] {
            new TestData("Primitive add same",
                    null,
                    new Object[] { Op.PrimitiveAdd, "abc" },
                    new Object[] { Op.PrimitiveAdd, "abc" }
            ),
            new TestData("Primitive add diff",
                    null,
                    new Object[] { Op.PrimitiveAdd, "abc" },
                    new Object[] { Op.PrimitiveAdd, "def" }
            ),
            new TestData("Primitive upd same",
                    new Object[] { Op.PrimitiveAdd, "abc" },
                    new Object[] { Op.PrimitiveAdd, "def" },
                    new Object[] { Op.PrimitiveAdd, "def" }
            ),
            new TestData("Primitive upd diff",
                    new Object[] { Op.PrimitiveAdd, "abc" },
                    new Object[] { Op.PrimitiveAdd, "def" },
                    new Object[] { Op.PrimitiveAdd, "ghi" }
            ),
            new TestData("Primitive remove",
                    new Object[] { Op.PrimitiveAdd, "abc", Op.SetAdd, "ghi" },
                    null,
                    new Object[] { Op.PrimitiveRemove },
                    new Object[] { Op.PrimitiveAdd, "abc", Op.SetAdd, "ghi" }
            ),

            new TestData("Set add same",
                    null,
                    new Object[] { Op.SetAdd, "abc" },
                    new Object[] { Op.SetAdd, "abc" } // Should perform no change to DB
            ),
            new TestData("Set add diff",
                    null,
                    new Object[] { Op.SetAdd, "abc" },
                    new Object[] { Op.SetAdd, "def" }
            ),
            new TestData("Set upd same",
                    new Object[] { Op.SetAdd, "abc" },
                    null,
                    new Object[] { Op.SetAdd, "abc" } // Should perform no change to DB
            ),
            new TestData("Set remove same",
                    new Object[] { Op.SetAdd, "abc", Op.SetAdd, "def" },
                    new Object[] { Op.SetRemove, "abc" },
                    new Object[] { Op.SetRemove, "abc" } // Should perform no change to DB
            ),
            new TestData("Set remove diff", // "abc" should not be added back
                    new Object[] { Op.SetAdd, "abc", Op.SetAdd, "def", Op.SetAdd, "ghi" },
                    new Object[] { Op.SetRemove, "abc" },
                    new Object[] { Op.SetRemove, "def" }
            ),
            new TestData("Set remove last",
                    new Object[] { Op.SetAdd, "abc" },
                    null,
                    new Object[] { Op.SetRemove, "abc" }
            ),

            new TestData("Map add same key same value",
                    null,
                    new Object[] { Op.MapAdd, "abc", "123" },
                    new Object[] { Op.MapAdd, "abc", "123" } // Should perform no change to DB
            ),
            new TestData("Map add same key diff value",
                    null,
                    new Object[] { Op.MapAdd, "abc", "123" },
                    new Object[] { Op.MapAdd, "abc", "456" }
            ),
            new TestData("Map add diff key",
                    null,
                    new Object[] { Op.MapAdd, "abc", "123" },
                    new Object[] { Op.MapAdd, "def", "456" }
            ),
            new TestData("Map upd same key same value",
                    new Object[] { Op.MapAdd, "abc", "123", Op.MapAdd, "def", "456" },
                    new Object[] { Op.MapAdd, "abc", "789" },
                    new Object[] { Op.MapAdd, "abc", "789" }
            ),
            new TestData("Map upd same key diff value",
                    new Object[] { Op.MapAdd, "abc", "123", Op.MapAdd, "def", "456" },
                    new Object[] { Op.MapAdd, "abc", "789" },
                    new Object[] { Op.MapAdd, "abc", "012" }
            ),
            new TestData("Map upd diff key",
                    new Object[] { Op.MapAdd, "abc", "123", Op.MapAdd, "def", "456", Op.MapAdd, "ghi", "789" },
                    new Object[] { Op.MapAdd, "abc", "123" },
                    new Object[] { Op.MapAdd, "def", "456" }
            ),
            new TestData("Map del same key",
                    new Object[] { Op.MapAdd, "abc", "123", Op.MapAdd, "def", "456" },
                    new Object[] { Op.MapRemove, "abc" },
                    new Object[] { Op.MapRemove, "abc" }
            ),
            new TestData("Map del diff key",
                    new Object[] { Op.MapAdd, "abc", "123", Op.MapAdd, "def", "456", Op.MapAdd, "ghi", "789" },
                    new Object[] { Op.MapRemove, "abc" },
                    new Object[] { Op.MapRemove, "def" }
            ),
            new TestData("Map del last key",
                    new Object[] { Op.MapAdd, "abc", "123" },
                    null,
                    new Object[] { Op.MapRemove, "abc" }
            ),

            new TestData("SetMap add same key same set",
                    null,
                    new Object[] { Op.SetMapAdd, "abc", "123" },
                    new Object[] { Op.SetMapAdd, "abc", "123" }
            ),
            new TestData("SetMap add diff key same set",
                    null,
                    new Object[] { Op.SetMapAdd, "abc", "123" },
                    new Object[] { Op.SetMapAdd, "abc", "456" }
            ),
            new TestData("SetMap add diff key diff set",
                    null,
                    new Object[] { Op.SetMapAdd, "abc", "123" },
                    new Object[] { Op.SetMapAdd, "def", "456" }
            ),
            new TestData("SetMap remove same key same set",
                    new Object[] { Op.SetMapAdd, "abc", "123", Op.SetMapAdd, "def", "456", Op.SetMapAdd, "ghi", "123" },
                    new Object[] { Op.SetMapRemove, "abc", "123" },
                    new Object[] { Op.SetMapRemove, "abc", "123" }
            ),
            new TestData("SetMap remove diff key same set",
                    new Object[] { Op.SetMapAdd, "abc", "123", Op.SetMapAdd, "abc", "456", Op.SetMapAdd, "abc", "789" },
                    new Object[] { Op.SetMapRemove, "abc", "123" },
                    new Object[] { Op.SetMapRemove, "abc", "123" }
            ),
            new TestData("SetMap remove diff key diff set",
                    new Object[] { Op.SetMapAdd, "abc", "123", Op.SetMapAdd, "abc", "456", Op.SetMapAdd, "abc", "789" },
                    new Object[] { Op.SetMapRemove, "abc", "123" },
                    new Object[] { Op.SetMapRemove, "abc", "123" }
            ),
            new TestData("SetMap remove diff key diff set",
                    new Object[] { Op.SetMapAdd, "abc", "123", Op.SetMapAdd, "abc", "456", Op.SetMapAdd, "def", "789" },
                    new Object[] { Op.SetMapRemove, "abc", "123" },
                    new Object[] { Op.SetMapRemove, "def", "789" }
            ),
            new TestData("SetMap remove last",
                    new Object[] { Op.SetMapAdd, "abc", "123" },
                    null,
                    new Object[] { Op.SetMapRemove, "abc", "123" }
            ),
    };

    class IndexTestWorker extends TestWorker {

        public IndexTestWorker() {
        }

        @Override
        public void modify(Op op, String seed0, String seed1) {

            switch (op) {
                case PrimitiveAdd:
                    this.pool.setLabel(seed0);
                    break;
                case PrimitiveRemove:
                    this.pool.setLabel(null);
                    break;

                case SetAdd:
                    if (this.pool.getVirtualArrays() == null) {
                        this.pool.setVirtualArrays(new StringSet());
                    }
                    this.pool.getVirtualArrays().add(seed0);
                    break;
                case SetRemove:
                    if (this.pool.getVirtualArrays() != null) {
                        this.pool.getVirtualArrays().remove(seed0);
                    }
                    break;

                case MapAdd:
                    if (this.pool.getProtectionVarraySettings() == null) {
                        this.pool.setProtectionVarraySettings(new StringMap());
                    }
                    this.pool.getProtectionVarraySettings().put(seed0, seed1);
                    break;
                case MapRemove:
                    if (this.pool.getProtectionVarraySettings() != null) {
                        this.pool.getProtectionVarraySettings().remove(seed0);
                    }
                    break;

                case SetMapAdd:
                    if (this.pool.getAcls() == null) {
                        this.pool.setAcls(new StringSetMap());
                    }
                    this.pool.getAcls().put(seed0, seed1);
                    break;
                case SetMapRemove:
                    if (this.pool.getAcls() != null) {
                        this.pool.getAcls().remove(seed0, seed1);
                    }
                    break;
            }
        }

        @Override
        public void verify(TestWorker shouldBe, String caseName) {
            VirtualPool p = shouldBe.pool;

            // Check the property values are correct
            Assert.assertTrue(
                    String.format("Volume.description does not match, case %s, \"%s\" != \"%s\"", caseName, p.getLabel(),
                            this.pool.getLabel()), equalsObject(p.getLabel(), this.pool.getLabel()));
            Assert.assertTrue(String.format("Volume.protocols does not match, case %s", caseName),
                    equalsObject(p.getVirtualArrays(), this.pool.getVirtualArrays()));
            Assert.assertTrue(String.format("Volume.haVarrayVpoolMap does not match, case %s", caseName),
                    equalsMap(p.getProtectionVarraySettings(), this.pool.getProtectionVarraySettings()));
            Assert.assertTrue(String.format("Volume.arrayInfo does not match, case %s", caseName),
                    equalsMap(p.getAcls(), this.pool.getAcls()));
        }
    }

    interface Factory<T> {
        T create();
    }

    class TestWorker {
        VirtualPool pool;

        public TestWorker() {
        }

        public void create(URI id) {
            // Create a volume object for testing
            try {
                this.pool = (VirtualPool) DataObject.createInstance(VirtualPool.class, id);
                this.pool.trackChanges();
            } catch (InstantiationException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        public void load(URI id) {
            this.pool = (VirtualPool) _dbClient.queryObject(VirtualPool.class, id);
            if (this.pool == null) {
                create(id);
            }
        }

        public void modify(Op op, String seed0) {
            modify(op, seed0, null);
        }

        // op determines which property to use
        // For property types involves only single value, only seed0 is used
        // For property types involves 2 values, both seed0 and seed1 are used.
        public void modify(Op op, String seed0, String seed1) {

            switch (op) {
                case PrimitiveAdd:
                    this.pool.setDescription(seed0);
                    break;
                case PrimitiveRemove:
                    this.pool.setDescription(null);
                    break;

                case SetAdd:
                    if (this.pool.getProtocols() == null) {
                        this.pool.setProtocols(new StringSet());
                    }
                    this.pool.getProtocols().add(seed0);
                    break;
                case SetRemove:
                    if (this.pool.getProtocols() != null) {
                        this.pool.getProtocols().remove(seed0);
                    }
                    break;

                case MapAdd:
                    if (this.pool.getHaVarrayVpoolMap() == null) {
                        this.pool.setHaVarrayVpoolMap(new StringMap());
                    }
                    this.pool.getHaVarrayVpoolMap().put(seed0, seed1);
                    break;
                case MapRemove:
                    if (this.pool.getHaVarrayVpoolMap() != null) {
                        this.pool.getHaVarrayVpoolMap().remove(seed0);
                    }
                    break;

                case SetMapAdd:
                    if (this.pool.getArrayInfo() == null) {
                        this.pool.setArrayInfo(new StringSetMap());
                    }
                    this.pool.getArrayInfo().put(seed0, seed1);
                    break;
                case SetMapRemove:
                    if (this.pool.getArrayInfo() != null) {
                        this.pool.getArrayInfo().remove(seed0, seed1);
                    }
                    break;
            }
        }

        public void save() {
            _dbClient.persistObject(this.pool);
            this.pool.trackChanges();
        }

        protected boolean equalsObject(Object a, Object b) {
            if (a instanceof Set && ((Set<?>) a).isEmpty()) {
                a = null;
            }
            if (b instanceof Set && ((Set<?>) b).isEmpty()) {
                b = null;
            }
            return a == null || b == null ? a == b : a.equals(b);
        }

        // Compare two Maps, null values are treated as no such key
        protected <K, V> boolean equalsMap(Map<K, V> a, Map<K, V> b) {

            if (a != null) {
                for (Map.Entry<K, V> entry : a.entrySet()) {
                    V valB = b == null ? null : b.get(entry.getKey());
                    if (!equalsObject(entry.getValue(), valB)) {
                        return false;
                    }
                }
            }

            if (b != null) {
                for (Map.Entry<K, V> entry : b.entrySet()) {
                    if (entry.getValue() != null && (a == null || !a.containsKey(entry.getKey()))) {
                        return false;
                    }
                }
            }

            return true;
        }

        public void verify(TestWorker shouldBe, String caseName) {
            VirtualPool p = shouldBe.pool;

            // Check the property values are correct
            Assert.assertTrue(
                    String.format("Volume.description does not match, case %s, \"%s\" != \"%s\"", caseName, p.getDescription(),
                            this.pool.getDescription()), equalsObject(p.getDescription(), this.pool.getDescription()));
            Assert.assertTrue(String.format("Volume.protocols does not match, case %s", caseName),
                    equalsObject(p.getProtocols(), this.pool.getProtocols()));
            Assert.assertTrue(String.format("Volume.haVarrayVpoolMap does not match, case %s", caseName),
                    equalsMap(p.getHaVarrayVpoolMap(), this.pool.getHaVarrayVpoolMap()));
            Assert.assertTrue(String.format("Volume.arrayInfo does not match, case %s", caseName),
                    equalsMap(p.getArrayInfo(), this.pool.getArrayInfo()));
        }
    }

    public static void applyOperations(TestWorker w, Object[] ops) {
        if (ops == null) {
            return;
        }

        for (int i = 0; i < ops.length;) {
            Op op = (Op) ops[i++];
            String seed0 = null;
            if (i < ops.length && !(ops[i] instanceof Op)) {
                seed0 = (String) ops[i++];
            }
            String seed1 = null;
            if (i < ops.length && !(ops[i] instanceof Op)) {
                seed1 = (String) ops[i++];
            }
            w.modify(op, seed0, seed1);
        }
    }

    private void runTest(TestData[] tests, Factory<TestWorker> fac) throws InstantiationException, IllegalAccessException {
        for (TestData test : tests) {
            // Generate unique object ID for this test case, so it will not interact with other test cases
            URI id = URIUtil.createId(VirtualPool.class);

            // Prepare initial DB state
            TestWorker w0 = fac.create();
            w0.create(id);
            if (test.initial != null) {
                applyOperations(w0, test.initial);
            }
            w0.save();

            // Load primary test worker
            TestWorker primary = fac.create();
            primary.load(id);

            // Run rush-in worker if needed
            if (test.rushIn != null) {
                TestWorker rushIn = fac.create();
                rushIn.load(id);
                applyOperations(rushIn, test.rushIn);
                rushIn.save();
            }

            // Do primary update and save
            applyOperations(primary, test.primary);
            primary.save();

            // Load final result from DB
            TestWorker now = fac.create();
            now.load(id);

            if (test.target == null) {
                applyOperations(w0, test.rushIn);
                applyOperations(w0, test.primary);
                now.verify(w0, test.name);
            }
            else {
                TestWorker target = fac.create();
                target.create(id);
                applyOperations(target, test.target);
                now.verify(target, test.name);
            }
        }
    }

    @Test
    public void testFieldChangePersist() throws InstantiationException, IllegalAccessException {
        runTest(testData, new Factory<TestWorker>() {

            @Override
            public TestWorker create() {
                return new TestWorker();
            }

        });
    }

    @Test
    public void testIndexedFieldChangePersist() throws InstantiationException, IllegalAccessException, IllegalArgumentException {
        runTest(testData, new Factory<TestWorker>() {

            @Override
            public TestWorker create() {
                return new IndexTestWorker();
            }

        });
    }

    private int getTombstoneCount(String cf, String label) throws IOException {

        dumpSSTables(cf, label);

        FileInputStream fis = new FileInputStream(String.format("%s/data/Test/%s/snapshots/%s/data.json", _dataDir.getAbsolutePath(), cf,
                label));

        JsonParser parser = new JsonParser();
        JsonArray arrRoot = parser.parse(new InputStreamReader(fis)).getAsJsonArray();

        int tombstomes = 0;
        for (JsonElement elemFile : arrRoot) {
            for (JsonElement elemRow : elemFile.getAsJsonArray()) {
                JsonObject rowObj = elemRow.getAsJsonObject();
                for (JsonElement elemCol : rowObj.getAsJsonArray("columns")) {
                    JsonArray fieldArr = elemCol.getAsJsonArray();
                    if (fieldArr.size() > 3 && fieldArr.get(3).getAsString().equals("d")) { // A tombstone
                        tombstomes++;
                    }
                }
            }
        }

        return tombstomes;

    }

    private void dumpSSTables(String cf, String label) throws IOException {
        String dirPath = String.format("%s/data/Test/%s/snapshots/%s", _dataDir.getAbsolutePath(), cf, label);
        File dir = new File(dirPath);
        if (!dir.exists()) {
            this.probe.takeSnapshot(label, cf, new String[] { "Test" });
        }

        if (new File(String.format("%s/data.json", dirPath)).exists()) {
            return;
        }

        String[] dataFiles = dir.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith("-Data.db");
            }
        });

        String outFileName = String.format("%s/data.json", dirPath);
        PrintStream outs = new PrintStream(outFileName);
        outs.println("[");
        for (int i = 0; i < dataFiles.length; i++) {
            String fileName = dataFiles[i];
            Descriptor desc = Descriptor.fromFilename(String.format("%s/%s", dirPath, fileName));
            SSTableExport.export(desc, outs, null, null);
            if (i + 1 < dataFiles.length) {
                outs.println(",");
            }
        }
        outs.println("]");
    }

    // Commented out for now as the required changed to DbSvcTestBase is not merged from release-2.1 now.
    // @Test
    public void testTombstoneCount() throws IOException {

        URI id = URIUtil.createId(VirtualPool.class);

        int tombstoneCount;
        {
            VirtualPool pool = new VirtualPool();
            pool.setId(id);
            pool.trackChanges();
            pool.setLabel("initial");
            _dbClient.persistObject(pool);

            tombstoneCount = getTombstoneCount("VirtualPool", "ver0");
        }

        {
            VirtualPool pool = _dbClient.queryObject(VirtualPool.class, id);
            pool.setLabel("initial");
            _dbClient.persistObject(pool);

            int newTombstoneCount = getTombstoneCount("VirtualPool", "ver1");

            assertTrue(String.format("Expected tombstone count to be %d, but it's %d", tombstoneCount + 1, newTombstoneCount),
                    newTombstoneCount == tombstoneCount + 1);
            tombstoneCount = newTombstoneCount;
        }

        {
            VirtualPool pool = _dbClient.queryObject(VirtualPool.class, id);
            pool.setLabel("changed");
            _dbClient.persistObject(pool);
            pool = _dbClient.queryObject(VirtualPool.class, id);
            assertTrue(pool.getLabel().equals("changed"));

            int newTombstoneCount = getTombstoneCount("VirtualPool", "ver2");

            assertTrue(String.format("Expected tombstone count to be %d, but it's %d", tombstoneCount + 1, newTombstoneCount),
                    newTombstoneCount == tombstoneCount + 1);
            tombstoneCount = newTombstoneCount;
        }

        {
            VirtualPool pool = _dbClient.queryObject(VirtualPool.class, id);
            pool.setLabel("changed");
            _dbClient.persistObject(pool);
            pool = _dbClient.queryObject(VirtualPool.class, id);
            assertTrue(pool.getLabel().equals("changed"));

            int newTombstoneCount = getTombstoneCount("VirtualPool", "ver3");

            assertTrue(String.format("Expected tombstone count to be %d, but it's %d", tombstoneCount + 1, newTombstoneCount),
                    newTombstoneCount == tombstoneCount + 1);
            tombstoneCount = newTombstoneCount;
        }
    }

    private void verifyContain(Constraint c, URI uri, int count) {
        URIQueryResultList list = new URIQueryResultList();
        _dbClient.queryByConstraint(c, list);

        int realCount = 0;
        boolean found = false;
        for (URI elem : list) {
            realCount++;

            if (uri != null && uri.equals(elem)) {
                found = true;
            }
        }

        if (uri != null) {
            assertTrue(found);
        }

        if (count >= 0) {
            assertTrue(String.format("Found %d URIs while %d is expected", realCount, count), realCount == count);
        }
    }

    @Test
    public void testRelationIndex() throws InstantiationException, IllegalAccessException, IOException {
        URI id = URIUtil.createId(StoragePool.class);

        URI pid0 = URIUtil.createId(StorageSystem.class);
        URI pid1 = URIUtil.createId(StorageSystem.class);

        ContainmentConstraint constraint0 = ContainmentConstraint.Factory.getContainedObjectsConstraint(pid0, StoragePool.class,
                "storageDevice");
        ContainmentConstraint constraint1 = ContainmentConstraint.Factory.getContainedObjectsConstraint(pid1, StoragePool.class,
                "storageDevice");

        {
            StoragePool obj = new StoragePool();
            obj.setId(id);
            obj.setStorageDevice(pid0);
            _dbClient.createObject(obj);
        }

        verifyContain(constraint0, id, 1);
        verifyContain(constraint1, null, 0);

        {
            StoragePool obj = _dbClient.queryObject(StoragePool.class, id);
            obj.setStorageDevice(pid1);
            _dbClient.persistObject(obj);
        }

        verifyContain(constraint1, id, 1);
        verifyContain(constraint0, null, 0);
    }

    @Test
    public void testRelationIndexSet() {
        URI id = URIUtil.createId(ExportGroup.class);

        URI pid0 = URIUtil.createId(Snapshot.class);
        URI pid1 = URIUtil.createId(Snapshot.class);

        ContainmentConstraint constraint0 = ContainmentConstraint.Factory.getContainedObjectsConstraint(pid0, ExportGroup.class,
                "snapshots");
        ContainmentConstraint constraint1 = ContainmentConstraint.Factory.getContainedObjectsConstraint(pid1, ExportGroup.class,
                "snapshots");

        {
            ExportGroup obj = new ExportGroup();
            obj.setId(id);
            obj.setSnapshots(new StringSet());
            obj.getSnapshots().add(pid0.toString());
            _dbClient.createObject(obj);
        }

        verifyContain(constraint0, id, 1);
        verifyContain(constraint1, null, 0);

        {
            ExportGroup obj = _dbClient.queryObject(ExportGroup.class, id);
            obj.getSnapshots().add(pid1.toString());
            _dbClient.persistObject(obj);
        }

        verifyContain(constraint0, id, 1);
        verifyContain(constraint1, id, 1);

        {
            ExportGroup obj = _dbClient.queryObject(ExportGroup.class, id);
            // assertTrue(obj.getSnapshots() != null);
            obj.getSnapshots().remove(pid0.toString());
            _dbClient.persistObject(obj);
        }

        verifyContain(constraint1, id, 1);
        verifyContain(constraint0, null, 0);

    }

    @Test
    public void testRelationIndexMap() {
        URI id = URIUtil.createId(ExportGroup.class);

        URI pid0 = URIUtil.createId(Volume.class);
        URI pid1 = URIUtil.createId(Volume.class);

        ContainmentConstraint constraint0 = ContainmentConstraint.Factory.getVolumeExportGroupConstraint(pid0);
        ContainmentConstraint constraint1 = ContainmentConstraint.Factory.getVolumeExportGroupConstraint(pid1);

        {
            ExportGroup obj = new ExportGroup();
            obj.setId(id);
            obj.setVolumes(new StringMap());
            obj.getVolumes().put(pid0.toString(), "1");
            _dbClient.createObject(obj);
        }

        verifyContain(constraint0, id, 1);
        verifyContain(constraint1, null, 0);

        {
            ExportGroup obj = _dbClient.queryObject(ExportGroup.class, id);
            obj.getVolumes().put(pid1.toString(), "2");
            _dbClient.persistObject(obj);
        }

        verifyContain(constraint0, id, 1);
        verifyContain(constraint1, id, 1);

        {
            ExportGroup obj = _dbClient.queryObject(ExportGroup.class, id);
            obj.getVolumes().remove(pid0.toString());
            _dbClient.persistObject(obj);
        }

        verifyContain(constraint1, id, 1);
        verifyContain(constraint0, null, 0);
    }

    @Test
    public void testNamedRelationIndex() {
        URI id = URIUtil.createId(Project.class);

        URI pid0 = URIUtil.createId(TenantOrg.class);
        URI pid1 = URIUtil.createId(TenantOrg.class);

        String lbl0 = "abcd1234";
        String lbl1 = "efgh5678";

        Constraint constraint0 = ContainmentPrefixConstraint.Factory.getProjectUnderTenantConstraint(pid0, lbl0);
        Constraint constraint1 = ContainmentPrefixConstraint.Factory.getProjectUnderTenantConstraint(pid1, lbl1);

        {
            Project obj = new Project();
            obj.setId(id);
            obj.setLabel(lbl0);
            obj.setTenantOrg(new NamedURI(pid0, lbl0));
            _dbClient.createObject(obj);
        }

        verifyContain(constraint0, id, 1);
        verifyContain(constraint1, null, 0);

        {
            Project obj = _dbClient.queryObject(Project.class, id);
            obj.setLabel(lbl1);
            obj.setTenantOrg(new NamedURI(pid1, lbl1));
            _dbClient.persistObject(obj);
        }

        verifyContain(constraint1, id, 1);
        verifyContain(constraint0, null, 0);
    }

    @Test
    public void testAlternateIdIndex() {
        URI id = URIUtil.createId(FileShare.class);

        String nid0 = UUID.randomUUID().toString();
        String nid1 = UUID.randomUUID().toString();

        AlternateIdConstraint constraint0 = AlternateIdConstraint.Factory.getFileShareNativeIdConstraint(nid0);
        AlternateIdConstraint constraint1 = AlternateIdConstraint.Factory.getFileShareNativeIdConstraint(nid1);

        {
            FileShare obj = new FileShare();
            obj.setId(id);
            obj.setNativeGuid(nid0);
            _dbClient.createObject(obj);
        }

        verifyContain(constraint0, id, 1);
        verifyContain(constraint1, null, 0);

        {
            FileShare obj = _dbClient.queryObject(FileShare.class, id);
            obj.setNativeGuid(nid1);
            _dbClient.persistObject(obj);
        }

        verifyContain(constraint1, id, 1);
        verifyContain(constraint0, null, 0);
    }

    @Test
    public void testDelInactiveAltIdSet() {
        URI id = URIUtil.createId(AuthnProvider.class);

        String key = "key_to_del";

        AuthnProvider obj = new AuthnProvider();
        obj.setId(id);
        obj.setDomains(new StringSet());
        obj.getDomains().add(key);
        _dbClient.createObject(obj);

        obj = _dbClient.queryObject(AuthnProvider.class, id);
        obj.setInactive(true);
        _dbClient.persistObject(obj);

        obj = _dbClient.queryObject(AuthnProvider.class, id);
        obj.getDomains().remove(key);
        _dbClient.updateAndReindexObject(obj);
    }

    @Test
    public void testAlternativeIdSetIndex() {
        URI id = URIUtil.createId(AuthnProvider.class);

        String key0 = "abcd1234";
        String key1 = "efgh5678";

        AlternateIdConstraint constraint0 = AlternateIdConstraint.Factory.getAuthnProviderDomainConstraint(key0);
        AlternateIdConstraint constraint1 = AlternateIdConstraint.Factory.getAuthnProviderDomainConstraint(key1);

        {
            AuthnProvider obj = new AuthnProvider();
            obj.setId(id);
            obj.setDomains(new StringSet());
            obj.getDomains().add(key0);
            _dbClient.createObject(obj);
        }

        verifyContain(constraint0, id, 1);
        verifyContain(constraint1, null, 0);

        {
            AuthnProvider obj = _dbClient.queryObject(AuthnProvider.class, id);
            obj.getDomains().add(key1);
            _dbClient.persistObject(obj);
        }

        verifyContain(constraint0, id, 1);
        verifyContain(constraint1, id, 1);

        {
            AuthnProvider obj = _dbClient.queryObject(AuthnProvider.class, id);
            obj.getDomains().remove(key0);
            _dbClient.persistObject(obj);
        }

        verifyContain(constraint1, id, 1);
        verifyContain(constraint0, null, 0);
    }

    @Test
    public void testAlternativeIdMapIndex() {
        URI id = URIUtil.createId(Network.class);

        String key0 = "abcd1234";
        String key1 = "efgh5678";

        AlternateIdConstraint constraint0 = AlternateIdConstraint.Factory.getEndpointNetworkConstraint(key0);
        AlternateIdConstraint constraint1 = AlternateIdConstraint.Factory.getEndpointNetworkConstraint(key1);

        {
            Network obj = new Network();
            obj.setId(id);
            obj.setEndpointsMap(new StringMap());
            obj.getEndpointsMap().put(key0, "test1");
            _dbClient.createObject(obj);
        }

        verifyContain(constraint0, id, 1);
        verifyContain(constraint1, null, 0);

        {
            Network obj = _dbClient.queryObject(Network.class, id);
            obj.getEndpointsMap().put(key1, "test2");
            _dbClient.persistObject(obj);
        }

        verifyContain(constraint0, id, 1);
        verifyContain(constraint1, id, 1);

        {
            Network obj = _dbClient.queryObject(Network.class, id);
            obj.getEndpointsMap().remove(key0);
            _dbClient.persistObject(obj);
        }

        verifyContain(constraint1, id, 1);
        verifyContain(constraint0, null, 0);
    }

    @Test
    public void testPrefixIndex() {
        URI id = URIUtil.createId(Volume.class);

        String lbl0 = "abcd1234";
        String lbl1 = "efgh5678";

        PrefixConstraint constraint0 = PrefixConstraint.Factory.getFullMatchConstraint(Volume.class, "label", lbl0);
        PrefixConstraint constraint1 = PrefixConstraint.Factory.getFullMatchConstraint(Volume.class, "label", lbl1);

        {
            Volume obj = new Volume();
            obj.setId(id);
            obj.setLabel(lbl0);
            _dbClient.createObject(obj);
        }

        verifyContain(constraint0, id, 1);
        verifyContain(constraint1, null, 0);

        {
            Volume obj = _dbClient.queryObject(Volume.class, id);
            obj.setLabel(lbl1);
            _dbClient.persistObject(obj);
        }

        verifyContain(constraint1, id, 1);
        verifyContain(constraint0, null, 0);
    }

    @Test
    public void testPermissionIndex() {
        URI id = URIUtil.createId(TenantOrg.class);

        String key0 = "abcd1234";
        String key1 = "efgh5678";

        Constraint constraint0 = ContainmentPermissionsConstraint.Factory.getTenantsWithPermissionsConstraint(key0);
        Constraint constraint1 = ContainmentPermissionsConstraint.Factory.getTenantsWithPermissionsConstraint(key1);

        {
            TenantOrg obj = new TenantOrg();
            obj.setId(id);
            // obj.setLabel("test tenant");
            obj.setRoleAssignments(new StringSetMap());
            obj.addRole(key0, "role1");
            obj.addRole(key0, "role2");
            _dbClient.createObject(obj);
        }

        verifyContain(constraint0, id, 2);
        verifyContain(constraint1, null, 0);

        {
            TenantOrg obj = _dbClient.queryObject(TenantOrg.class, id);
            obj.addRole(key1, "role3");
            _dbClient.persistObject(obj);
        }

        verifyContain(constraint0, id, 2);
        verifyContain(constraint1, id, 1);

        {
            TenantOrg obj = _dbClient.queryObject(TenantOrg.class, id);
            obj.removeRole(key0, "role1");
            _dbClient.persistObject(obj);
        }

        verifyContain(constraint0, id, 1);
        verifyContain(constraint1, id, 1);

        {
            TenantOrg obj = _dbClient.queryObject(TenantOrg.class, id);
            obj.removeRole(key0, "role2");
            _dbClient.persistObject(obj);
        }

        verifyContain(constraint1, id, 1);
        verifyContain(constraint0, null, 0);
    }

    @Test
    public void testScopedLabelIndex() {
        URI id = URIUtil.createId(Volume.class);

        URI pid0 = URIUtil.createId(TenantOrg.class);
        URI pid1 = URIUtil.createId(TenantOrg.class);

        String lbl0 = "abcd1234";
        String lbl1 = "efgh5678";

        PrefixConstraint constraint0 = new LabelConstraintImpl(pid0, lbl0, TypeMap.getDoType(Volume.class).getColumnField("tags"));
        PrefixConstraint constraint1 = new LabelConstraintImpl(pid1, lbl1, TypeMap.getDoType(Volume.class).getColumnField("tags"));

        {
            Volume obj = new Volume();
            obj.setId(id);
            obj.setTag(new ScopedLabelSet());
            obj.getTag().add(new ScopedLabel(pid0.toString(), lbl0));
            _dbClient.createObject(obj);
        }

        verifyContain(constraint0, id, 1);
        verifyContain(constraint1, null, 0);

        {
            Volume obj = _dbClient.queryObject(Volume.class, id);
            obj.getTag().add(new ScopedLabel(pid1.toString(), lbl1));
            _dbClient.persistObject(obj);
        }

        verifyContain(constraint0, id, 1);
        verifyContain(constraint1, id, 1);

        {
            Volume obj = _dbClient.queryObject(Volume.class, id);
            obj.getTag().remove(new ScopedLabel(pid0.toString(), lbl0));
            _dbClient.persistObject(obj);
        }

        verifyContain(constraint1, id, 1);
        verifyContain(constraint0, null, 0);
    }

}
