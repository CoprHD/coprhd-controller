/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.simulators;

import com.emc.storageos.isilon.restapi.*;
import com.emc.storageos.simulators.impl.Main;

import com.emc.storageos.simulators.impl.resource.Quota;
import org.apache.commons.lang.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.junit.Assert.*;

import java.net.URI;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/*
* Test client for simulator with db service enabled
*/
public class SimulatorTest {
    private static Logger _log = LoggerFactory.getLogger(SimulatorTest.class);
    private String _exp_path = "/ifs/test" + System.currentTimeMillis();
    private HashMap<String, ArrayList<String>> _cache;
    private static IsilonApi _client;
    private Main _main = null;

    @After
    public void cleanup() throws Exception {
        _main.stop();
    }
    
    @Test
    public void testDb() throws Exception {
        _main = new Main();
        _main.main(new String[]{"/" + getClass().getResource("/simulator-db-config.xml").getPath()});
        IsilonApiFactory factory = new IsilonApiFactory();
        factory.init();
        _client = factory.getRESTClient(URI.create("http://localhost:9999"));

        testAll();
    }

    @Test
    public void testInMem() throws Exception {
        _main = new Main();
        _main.main(new String[]{ "/" + getClass().getResource("/simulator-config.xml").getPath() });
        IsilonApiFactory factory = new IsilonApiFactory();
        factory.init();
        _client = factory.getRESTClient(URI.create("http://localhost:9999"));

        testAll();
    }

    private void testClusterInfo() throws Exception {
        String info = _client.getClusterInfo().toString();
        _log.info("Cluster info: " + info);
    }

    private void testListDir() throws Exception {
        String dirName = _exp_path + "/listdir/simulator/dir10";
        _client.createDir(dirName, true);
        dirName = _exp_path + "/listdir/simulator/dir11";
        _client.createDir(dirName, true);
        dirName = _exp_path + "/listdir/dir12";
        _client.createDir(dirName, true);
        ArrayList<String> expected = new ArrayList<String>();
        expected.add("simulator");
        expected.add("dir12");

        IsilonApi.IsilonList<String> children = _client.listDir(_exp_path + "/listdir", null);
        assertTrue("expected subdirs 2, got subdirs " + children.size(), children.size() == 2);
        assertTrue("expected children " + expected + ", got " + children, 
                children.getList().containsAll(expected));
    }

    private void testLoad() throws Exception {

        int dirCount = 1;
        int exportCount = 10;
        int snapshotCount = 10;
        _cache = new HashMap<String, ArrayList<String>>();
        _cache.put("exports", new ArrayList<String>());
        _cache.put("quotas", new ArrayList<String>());
        _cache.put("snapshots", new ArrayList<String>());

        long start = System.currentTimeMillis();
        for (int i = 0; i < dirCount; i++) {
            // create dir
            String dirName = _exp_path + "/simulator/dir" + i;
            _client.createDir(dirName, true);
            if (!_client.existsDir(dirName)) {
                throw new Exception("existsDir: failed");
            }
            // quota
            String qid = _client.createQuota(dirName, 2048000L);
            _cache.get("quotas").add(qid);
            _log.info("created dir {} path {}", i + 1, dirName);

            // now, exports
            for (int e = 0; e < exportCount; e++) {
                IsilonExport export = new IsilonExport();
                export.addPath(dirName);
                export.addClient("10.0.0." + e);

                String id = _client.createExport(export);
                _cache.get("exports").add(id);
                _log.info("at path {} created export {}", dirName, e + 1);
            }
            // now, snapshots
            for (int s = 0; s < snapshotCount; s++) {
                String snap_name = "snap_" + dirCount + "_" + s;
                String snap_id = _client.createSnapshot(snap_name, dirName);
                _cache.get("snapshots").add(snap_id);
                _log.info("at path {} created snapshot {}", i + 1, s + 1);

                // export snapshot
                String prefix = "ifs/";
                String snap_path = String.format("ifs/.snapshot/%1$s/%2$s", snap_name,
                        dirName.substring("ifs/".length()));
                IsilonExport export = new IsilonExport();
                export.addPath(snap_path);
                export.addClient("10.0.0.0");
                String id = _client.createExport(export);
                _cache.get("exports").add(id);
                _log.info("at path {} created snapshot export {}", snap_path, s + 1);

            }
        }

        _log.info("Total dirs created: " + dirCount);
        _log.info("Total exports created: " + dirCount * exportCount);
        _log.info("Total snapshots created: " + dirCount * snapshotCount);
        _log.info("Total snapshot exports created: " + (dirCount * snapshotCount));

        // cleanup - delete
        _log.info("Cleanup - delete all that is created");

        for (String exp : _cache.get("exports")) {
            _client.deleteExport(exp);
        }

        for (String snap : _cache.get("snapshots")) {
            _client.deleteSnapshot(snap);
        }

        for (String qid : _cache.get("quotas")) {
            _client.deleteQuota(qid);
        }

        for (int i = 0; i < dirCount; i++) {
            _client.deleteDir(_exp_path + "/simulator/dir" + i);
        }
        long end = System.currentTimeMillis();
        _log.info("Execution time was " + (end - start) + " ms.");
    }

    private void testEvents() throws Exception {
        ArrayList<IsilonEvent> eventList = _client.listEvents(null).getList();
        _log.info("Event list size: " + eventList.size());
    }

    private void testEventQuery() throws Exception {
        IsilonApi.IsilonList<IsilonEvent> eventList = _client.queryEvents(-7200, -3600);
        _log.info("Event detail: \n");
        for (IsilonEvent event : eventList.getList())
            _log.info("Event: " + event.toString() + "\n");
    }

    private void testExport() throws Exception {
        IsilonExport export = new IsilonExport();

        ArrayList<String> clients = new ArrayList<String>();
        clients.add("10.1.1.1");
        clients.add("10.1.1.2");
        export.setClients(clients);

        ArrayList<String> paths = new ArrayList<String>();
        paths.add("/ifs/abc");
        paths.add("/ifs/bcd");
        export.setPaths(paths);

        ArrayList<String> security_flavors = new ArrayList<String>();
        security_flavors.add("security1");
        security_flavors.add("security2");
        export.setSecurityFlavors(security_flavors);

        export.setReadOnly();

        export.setComment("No Comment");
        export.setMapAll("usr001");

        // create export
        String id = _client.createExport(export);

        // get export
        IsilonExport read_export = _client.getExport(id);

        assertEquals("No Comment", read_export.getComment());
        assertEquals("security1", read_export.getSecurityFlavors().get(0));
        assertEquals("/ifs/abc", read_export.getPaths().get(0));
        assertEquals("10.1.1.1", read_export.getClients().get(0));
        assertTrue(read_export.getReadOnly());
        assertEquals("usr001", read_export.getMap_all().getUser());
        assertNull(read_export.getMap_root());

        // modify export
        IsilonExport new_export = new IsilonExport();
        new_export.setComment("New Comment");
        _client.modifyExport(id, new_export);
        assertEquals("New Comment", _client.getExport(id).getComment());

        // delete export
        _client.deleteExport(id);
        try {
            read_export = _client.getExport(id);
            assertTrue(false);
        } catch (Exception e) {
            assertTrue(true);
        }
    }

    /*
    private void testStatsCurrent() throws Exception {
        _log.info("Current stats detail: " + _client.getStatsCurrent("cluster.cpu.idle.avg",
        new TypeToken<IsilonStats.StatValueCurrent<Integer>>() {}.getType()).toString());
    }

    private void testStatsHistory() throws Exception {
        _log.info("History stats detail: " + _client.getStatsHistory("cluster.cpu.idle.avg", -7200,
        new TypeToken<IsilonStats.StatValueHistory<Integer>>() {}.getType()).toString());
    }*/

    private void testStatsProtocols() throws Exception {
        _log.info("Protocol list: " + _client.getStatsProtocols().toString());
    }

    public void testGetQuota() throws Exception {
        String dirName = _exp_path + "/testGetQuota/dir";
        _client.createDir(dirName, true);
        String qid = _client.createQuota(dirName, 2048000L);
        IsilonSmartQuota sQuota = _client.getQuota(qid);
        assertTrue("getQuota: expected path " + dirName + ", got " + sQuota.getPath(),
                dirName.equalsIgnoreCase(sQuota.getPath()));
    }

    public void testListQuota() throws Exception {
        String dirName = _exp_path + "/testListQuota";
        _client.createDir(dirName + "/1", true);
        String id = _client.createQuota(dirName + "/1", 2048000L);

        _client.createDir(dirName + "/2", true);
        _client.createQuota(dirName + "/2", 2048000L);

        String start = null;
        boolean flg = false;
        do {
            IsilonApi.IsilonList<IsilonSmartQuota> list = _client.listQuotas(start);
            ArrayList<IsilonSmartQuota> quotas = list.getList();

            // list
            for (int j = 0; j < list.size(); j++) {
                IsilonSmartQuota quota = quotas.get(j);
                if (id.equals(quota.getId()))
                    flg = true;
            }

            start = list.getToken();
        } while (start != null && !start.isEmpty());

        assertTrue(flg);
    }

    public void testParallel() throws Exception {
        long now = System.currentTimeMillis();
        int numThreads = 50;
        final int total = 5000;
        final AtomicInteger ccount = new AtomicInteger();
        final AtomicInteger dcount = new AtomicInteger();
        final ConcurrentHashMap<Integer, ArrayList<String>> ids = new ConcurrentHashMap<Integer, ArrayList<String>>();
        final ConcurrentHashMap<Integer, ArrayList<String>> dirs = new ConcurrentHashMap<Integer, ArrayList<String>>();
        ExecutorService create_exe = Executors.newFixedThreadPool(numThreads);
        ExecutorService delete_exe = Executors.newFixedThreadPool(numThreads);

        _log.info("create started - time " + ((System.currentTimeMillis() - now)/1000L));

        for (int index = 0; index < numThreads; index++) {
            final int threadIndex = index;
            create_exe.submit(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    ArrayList<String> ids_index = new ArrayList<String>();
                    ArrayList<String> dirs_index = new ArrayList<String>();
                    ids.put(threadIndex, ids_index);
                    dirs.put(threadIndex, dirs_index);
                    int count = ccount.getAndIncrement();
                    while (count < total) {
                        try {
                            _log.info("create start...");

                            String dirName = _exp_path + "/parallel/" + threadIndex + "/dir" + count;
                            _client.createDir(dirName, true);
                            dirs_index.add(dirName);

                            String id = _client.createQuota(dirName, 2048000L);
                            ids_index.add(id);

                            _log.info("create end...");

                            count = ccount.getAndIncrement();
                        } catch (Exception e) {
                            _log.error("Exception encountered:", e);
                            count = ccount.decrementAndGet();
                        }
                    }
                    return null;
                }
            });
        }

        // wait for it to finish
        create_exe.shutdown();
        while (!create_exe.awaitTermination(30, TimeUnit.SECONDS)) {
            _log.info("Waiting for jobs to finish");
        }

        _log.info("create completed - time " + ((System.currentTimeMillis() - now)/1000L));

        // delete start
        Set<Integer> threadkeyset = ids.keySet();
        Iterator<Integer> iterator = threadkeyset.iterator();

        while (iterator.hasNext()) {
            final int threadIndex = iterator.next();

            delete_exe.submit(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    int size = ids.get(threadIndex).size();
                    try {
                        for (int i = 0; i < size; i++) {
                            _log.info("delete start...");
                            _client.deleteQuota(ids.get(threadIndex).get(i));
                            _client.deleteDir(dirs.get(threadIndex).get(i), true);
                            _log.info("delete complete...");
                        }

                    } catch (Exception e) {
                        _log.error("Exception encountered:", e);
                    }
                    return null;
                }
            });
        }

        // wait for it to finish
        delete_exe.shutdown();
        while (!delete_exe.awaitTermination(30, TimeUnit.SECONDS)) {
            _log.info("Waiting for jobs to finish");
        }
        _log.info("delete completed - time " + ((System.currentTimeMillis() - now)/1000L));
    }

    public void testAll() throws Exception{
        testClusterInfo();
        testListDir();
        testLoad();
        testEvents();
        testEventQuery();
        testExport();
        testStatsProtocols();
        testGetQuota();
        testDelDir();
        testQuotaIndex();
        testListQuota();
        testParallel();
        //testListQuotasPerf();
    }

    private void testQuotaIndex() throws Exception {
        String dirName = _exp_path + "/testQuotaIndex";
        String id;

        // create
        _client.createDir(dirName, true);
        id = _client.createQuota(dirName , 2000);
        _log.info("Quota created: " + id);

        // get
        IsilonSmartQuota quota = _client.getQuota(id);
        assertEquals(quota.getThresholds().getHard().longValue(), 2000);

        // delete
        _client.deleteQuota(id);
        try {
            _client.getQuota(id);
            assertTrue(false);
        } catch (Exception e) {
            assertTrue(true);
        }
    }

    public void testListQuotasPerf() throws Exception {
        int i = 0;
        String parentPath = _exp_path + "/listQuotaPerf/";

        // test 1k quotas
        while (i < 1005) {
            String path = parentPath + StringUtils.leftPad(i + "", 10, '0');
            _client.createDir(path, true);
            _client.createQuota(path, i + 1);
            i++;
        }
        _log.info("test 1k quotas start ... [time: " + getTime(System.currentTimeMillis()) + "]");
        listQuotas();
        _log.info("test 1k quotas end ... [time: " + getTime(System.currentTimeMillis()) + "]");

        // test 10k quotas
        while (i < 10005) {
            String path = parentPath + StringUtils.leftPad(i + "", 10, '0');
            _client.createDir(path, true);
            _client.createQuota(path, i + 1);
            i++;
        }
        _log.info("test 10k quotas start ... [time: " + getTime(System.currentTimeMillis()) + "]");
        listQuotas();
        _log.info("test 10k quotas end ... [time: " + getTime(System.currentTimeMillis()) + "]");

        // test 100k quotas
        while (i < 100005) {
            String path = parentPath + StringUtils.leftPad(i + "", 10, '0');
            _client.createDir(path, true);
            _client.createQuota(path, i + 1);
            i++;
        }
        _log.info("test 100k quotas start ... [time: " + getTime(System.currentTimeMillis()) + "]");
        listQuotas();
        _log.info("test 100k quotas end ... [time: " + getTime(System.currentTimeMillis()) + "]");

        // test 1M quotas
        while (i < 1000005) {
            String path = parentPath + StringUtils.leftPad(i + "", 10, '0');
            _client.createDir(path, true);
            _client.createQuota(path, i + 1);
            i++;
        }
        _log.info("test 1M quotas start ... [time: " + getTime(System.currentTimeMillis()) + "]");
        listQuotas();
        _log.info("test 1M quotas end ... [time: " + getTime(System.currentTimeMillis()) + "]");
    }

    private void testDelDir() throws Exception {
        String parentPath = _exp_path + "/testDelDir";
        _client.createDir(parentPath);
        _client.createDir(parentPath + "002");
        _client.createDir(parentPath + "/sub000");
        _client.createDir(parentPath + "/sub001");
        String id = _client.createQuota(parentPath + "/sub001", 1000L);

        // test delete without deleting quota first
        try {
            _client.deleteDir(parentPath, true);
            assertTrue(false);
        } catch (Exception e) {
            assertTrue(true);
        }

        _client.deleteQuota(id);
        try {
            _client.deleteDir(parentPath, true);
            assertTrue(true);
        } catch (Exception e) {
            assertTrue(false);
        }

        // test non-recursive deletion while having sub directories
        parentPath = parentPath + "2";
        _client.createDir(parentPath);
        _client.createDir(parentPath + "/sub000");
        try {
            _client.deleteDir(parentPath, false);
            assertTrue(false);
        } catch (Exception e) {
            assertTrue(true);
        }

        _client.deleteDir(parentPath + "/sub000");
        try {
            _client.deleteDir(parentPath, false);
            assertTrue(true);
        } catch (Exception e) {
            assertTrue(false);
        }
    }

    private void listQuotas() throws Exception {
        IsilonApi.IsilonList<IsilonSmartQuota> quotas = _client.listQuotas(null);
        for (IsilonSmartQuota quota: quotas.getList()) {
            assertEquals(Quota.NUM_PAGE, quotas.size());
        }
        while (quotas.getToken() != null && !quotas.getToken().isEmpty()) {
            quotas = _client.listQuotas(quotas.getToken());
        }
    }

    private String getTime(long timeInMillis) {
        DateFormat formatter = new SimpleDateFormat("dd/MM/yyyy hh:mm:ss.SSS");
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeInMillis);

        return formatter.format(calendar.getTime());
    }
}

