/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.zkutils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import org.apache.commons.io.IOUtils;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.ZooKeeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handle commands about Zookeeper and output some human readable information.
 */
public class ZkCmdHandler implements Watcher {
    private static final Logger log = LoggerFactory.getLogger(ZkCmdHandler.class);

    private String host = "localhost";
    private int port = 2181;
    private String connectionString;
    private boolean turnOnPrintData = false;
    private ZooKeeper zk = null;
    private static final int SESSION_TIMEOUT = 3000;
    private static final String DR_CONFIG_PATH = "/config/disasterRecoveryConfig/global";

    private CountDownLatch connectedSignal = new CountDownLatch(1);

    public ZkCmdHandler(String host, int port, boolean withData) {
        this.host = host;
        this.port = port;
        this.turnOnPrintData = withData;
        this.createConnect();
    }

    /**
     * Create Zookeeper connection
     */
    public void createConnect() {
        this.releaseConnection();
        try {
            connectionString = this.host + ":" + this.port;
            zk = new ZooKeeper(connectionString, SESSION_TIMEOUT, this);
            log.info("Start to connect zookeeper server...");
            connectedSignal.await();
        } catch (Exception e) {
            log.error("Exception happens during connecting zookeeper: {}", e);
        }
    }

    public void setNodeData(String nodePath) throws IOException, KeeperException, InterruptedException {
        // read data from STDIN
        ByteArrayOutputStream memStream = new ByteArrayOutputStream();
        IOUtils.copy(System.in, memStream);
        zk.setData(nodePath, memStream.toByteArray(), -1);
    }

    public void addDrConfig(String key, String value) throws Exception {
        assureNodeExist(DR_CONFIG_PATH);
        Properties config = new Properties();
        config.load(new ByteArrayInputStream(zk.getData(DR_CONFIG_PATH, null, null)));
        config.put(key, value);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        config.store(out, null);
        zk.setData(DR_CONFIG_PATH, out.toByteArray(), -1);
        System.out.println("Data after change:");
        Properties newConfig = new Properties();
        newConfig.load(new ByteArrayInputStream(zk.getData(DR_CONFIG_PATH, null, null)));
        System.out.println(newConfig);
    }

    private void assureNodeExist(String path) throws Exception{
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException(String.format("Invalid ZK path (should be full path): %s", path));
        }
        if (zk.exists(path, false) != null) {
            return;
        }
        path = path.substring(1);
        String[] paths = path.split("/");
        StringBuilder builder = new StringBuilder();
        for (String p : paths) {
            builder.append("/" + p);
            if (zk.exists(builder.toString(), false) == null) {
                System.out.println("Creating path: " + builder.toString() + " ...");
                zk.create(builder.toString(), "".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            }
        }
    }

    /**
     * Print all nodes, which path contains the given substring, like a tree.
     */
    public void printNodes(String subString) throws Exception {
        // find all nodes which match the requirement
        List<String> nodeList = new ArrayList<String>();
        getNodesBySubString("/", subString, nodeList);

        // construct a tree from the path list above
        ZkTree tree = new ZkTree();
        for (String node : nodeList) {
            tree.insert(node);
        }

        printTree(tree);
    }

    /**
     * Print EphemeralNodes
     */
    public void printEphemeralNodes() throws Exception {
        HashMap<String, List<String>> sessionNodesMap = new HashMap<String, List<String>>();
        getEphemeralNodes("/", sessionNodesMap);

        HashMap<String, List<String>> hostSessionsMap = getHostSessions();

        List<String> hostOwnedSession = new ArrayList<String>();

        // print nodes of sessions, which owned by certain host
        Iterator<String> hostIitrator = hostSessionsMap.keySet().iterator();
        while (hostIitrator.hasNext()) {
            String host = hostIitrator.next();
            System.out.println(String.format("======  host: %s  ======", host));
            List<String> sessionList = hostSessionsMap.get(host);
            for (String session : sessionList) {
                ZkTree tree = new ZkTree();
                List<String> nodeList = (List<String>) sessionNodesMap.get(session);
                if (nodeList == null) {
                    continue;
                }

                for (String node : nodeList) {
                    tree.insert(node);
                }
                System.out.println(String.format("-- Ephemeral Nodes of Session: %s --", session));
                printTree(tree);

                hostOwnedSession.add(session);
            }
            System.out.println("");
        }

        // print nodes of sessions, which not owned by any host
        System.out.println("======  no host sessions  ======");
        Iterator<String> it = sessionNodesMap.keySet().iterator();
        while (it.hasNext()) {
            String session = (String) it.next();
            if (hostOwnedSession.contains(session)) {
                continue;
            }

            ZkTree tree = new ZkTree();
            List<String> nodeList = (List<String>) sessionNodesMap.get(session);
            if (nodeList == null) {
                continue;
            }

            for (String node : nodeList) {
                tree.insert(node);
            }

            System.out.println(String.format("-- Ephemeral Nodes for Session ID: %s --", session));
            printTree(tree);
        }
    }

    /**
     * Recursively to find ephemeral nodes, start from the specified path, and
     * group them by Session ID
     */
    private void getEphemeralNodes(String path, HashMap<String, List<String>> outputMap)
            throws Exception {
        Stat stat = zk.exists(path, this);

        // check if it is an ephemeral node?
        // yes - add it into list; no - skip it
        if (stat.getEphemeralOwner() != 0) {

            // format session id to match zookeeper 4-letter-word command "cons"
            // output
            String sessionId = "0x"
                    + Long.toHexString(stat.getEphemeralOwner()).toLowerCase();

            List<String> list = outputMap.get(sessionId);
            if (list == null) {
                list = new ArrayList<String>();
                outputMap.put(sessionId, list);
            }

            list.add(path);
        }

        // recursively check its children
        List<String> children = zk.getChildren(path, false);
        if (!path.endsWith("/")) {
            path += "/";
        }
        for (String child : children) {
            getEphemeralNodes(path + child, outputMap);
        }
    }

    /**
     * A recursive method. to find nodes, which path contains the specified
     * substring
     */
    private void getNodesBySubString(String startPath, String subString,
            List<String> output) throws Exception {
        if (startPath.contains(subString)) {
            output.add(startPath);
        }

        // recursively check its children
        List<String> children = zk.getChildren(startPath, false);
        if (!startPath.endsWith("/")) {
            startPath += "/";
        }

        for (String child : children) {
            getNodesBySubString(startPath + child, subString, output);
        }
    }

    private HashMap<String, List<String>> getHostSessions() throws Exception {

        HashMap<String, List<String>> hostSessionMap = new HashMap<String, List<String>>();
        String result = send4LetterWord("cons");

        String[] lines = result.split("\n");
        for (String line : lines) {
            String host = line.substring(2, line.indexOf(":"));
            int sidIndex = line.indexOf("sid=") + 4;
            String sid = line.substring(sidIndex, sidIndex + 17);

            List<String> sessionList = hostSessionMap.get(host);
            if (sessionList == null) {
                sessionList = new ArrayList<String>();
                hostSessionMap.put(host, sessionList);
            }
            sessionList.add(sid);
        }

        return hostSessionMap;
    }

    /**
     * Send 4 letter command to zookeeper server
     * 
     * @param cmd
     * @return
     * @throws IOException
     */
    private String send4LetterWord(String cmd) throws IOException {

        Socket sock = new Socket(host, port);
        BufferedReader reader = null;
        try {
            OutputStream outstream = sock.getOutputStream();
            outstream.write(cmd.getBytes());
            outstream.flush();
            // this replicates NC - close the output stream before reading
            sock.shutdownOutput();

            reader = new BufferedReader(new InputStreamReader(sock.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n");
            }
            return sb.toString();
        } finally {
            sock.close();
            if (reader != null) {
                reader.close();
            }
        }
    }

    /**
     *
     */
    private void printTree(ZkTree tree) {
        printTree(tree.root, true, "");
    }

    /**
     * Recursively print out the node and all its children.
     * 
     */
    private void printTree(ZkNode node, boolean lastNode, String prefix) {

        String self_prefix = prefix;
        if (lastNode) {
            self_prefix += "+-";
        } else {
            self_prefix += "|-";
        }

        StringBuffer nodeSB = new StringBuffer();

        byte[] data = null;
        if (node.path.equals("/")) {
            nodeSB.append(self_prefix + "/");
        } else {
            nodeSB.append(self_prefix
                    + node.path.substring(node.path.lastIndexOf("/") + 1));
            nodeSB.append("(");
            try {
                data = zk.getData(node.path, false, null);
            } catch (InterruptedException iex) {
                // ignore exception
            } catch (KeeperException kex) {
                // ignore exception
            }

            if (data != null) {
                nodeSB.append("size=").append(data.length);
            }

            nodeSB.append(")");
        }
        System.out.println(nodeSB.toString());

        if (data != null && data.length > 0 && turnOnPrintData) {
            System.out.println(new String(data));
        }

        List<ZkNode> children = node.children;
        for (int i = 0; i < children.size(); i++) {
            ZkNode childNode = children.get(i);
            String child_prefix = prefix;

            boolean lastChild = false;
            if (i == (children.size() - 1)) {
                lastChild = true;
            }

            if (lastNode) {
                child_prefix += "    ";
            } else {
                child_prefix += "|   ";
            }

            printTree(childNode, lastChild, child_prefix);
        }
    }

    /**
     * Interface Method, do things after receiving event from Server Watcher
     */
    @Override
    public void process(WatchedEvent event) {
        KeeperState eventState = event.getState();
        EventType eventType = event.getType();
        log.info("Receive from Watcher, State: {}, Type: {}.", eventState, eventType);
        if (eventState == KeeperState.SyncConnected) {
            connectedSignal.countDown();
            log.info("Connect to {} sucessfully", connectionString);
        }
    }

    public void releaseConnection() {
        if (this.zk != null) {
            try {
                this.zk.close();
            } catch (InterruptedException e) {
                log.error("Fail to close Zookeeper connection: {}", e);
            }
        }
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setTurnOnPrintData(boolean turnOnPrintData) {
        this.turnOnPrintData = turnOnPrintData;
    }
}
