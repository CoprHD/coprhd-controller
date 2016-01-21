/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.imageservercontroller.impl;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.imageservercontroller.ImageServerConf;
import com.emc.storageos.networkcontroller.SSHDialog;
import com.emc.storageos.networkcontroller.SSHPrompt;
import com.emc.storageos.networkcontroller.SSHSession;
import com.emc.storageos.networkcontroller.exceptions.NetworkDeviceControllerException;

public class ImageServerDialog extends SSHDialog {
    private static final Logger logger = LoggerFactory
            .getLogger(ImageServerDialog.class);

    public static void main(String[] args) {
        ImageServerDialog d = null;
        try {
            ImageServerConf imageServerConf = new ImageServerConf();
            imageServerConf.setImageServerIp("<IP>");
            imageServerConf.setImageServerUser("root");
            imageServerConf.setImageServerPassword("<password>");
            imageServerConf.setTftpbootDir("/opt/tftpboot/");
            imageServerConf.setImageServerSecondIp("<IP>");
            imageServerConf.setImageServerHttpPort("44491");
            imageServerConf.setImageDir("images");

            SSHSession session = new SSHSession();
            session.connect(imageServerConf.getImageServerIp(), imageServerConf.getSshPort(),
                    imageServerConf.getImageServerUser(), imageServerConf.getImageServerPassword());
            d = new ImageServerDialog(session, imageServerConf.getSshTimeoutMs());
            d.init();

            d.cd("/tmp");

        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        } finally {
            if (d != null && d.isConnected()) {
                d.close();
            }
        }
        System.out.println("exit");
        System.exit(0);
    }

    private static final Logger log = LoggerFactory.getLogger(ImageServerDialog.class);

    private SSHPrompt[] prompts = { SSHPrompt.LINUX_CUSTOM_PROMPT };
    private static final String PROMPT = ImageServerDialogProperties.getString("PROMPT");

    public ImageServerDialog(SSHSession session, Integer defaultTimeout) {
        super(session, defaultTimeout);
    }

    /**
     * Since prompt is not known, setup a custom one.
     */
    public void init() {
        StringBuilder buf = new StringBuilder();

        // set custom prompt.
        String command = String.format(ImageServerDialogProperties.getString("cmd.promptChange"), PROMPT);
        try {
            sendWaitFor(command, defaultTimeout, prompts, buf);
        } catch (NetworkDeviceControllerException e) {
            // re-try, cut the timeout in half
            log.error("timeout when trying to connect to image server: {}", e.getMessage());
            log.info("let's retry");
            sendWaitFor(command, defaultTimeout / 2, prompts, buf);
            log.info("retry worked");
        }

        // set command line length to a 1000 chars
        command = String.format(ImageServerDialogProperties.getString("cmd.termLength"), 1000);
        sendWaitFor(command, defaultTimeout, prompts, buf);
    }

    /**
     * Close connection.
     */
    public void close() {
        send("exit");
        this.getSession().disconnect();
    }

    /**
     * Is the connection open.
     * 
     * @return
     */
    public boolean isConnected() {
        return this.getSession().isConnected();
    }

    /**
     * Start process with nohup command.
     * 
     * @param cmd
     */
    public void nohup(String cmd) {
        send(String.format(ImageServerDialogProperties.getString("cmd.nohup"), cmd));
        // have to sleep a little to skip output after nohup command
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // ignore
        }
    }

    /**
     * Kill process by PID.
     * 
     * @param pid
     */
    public void kill(String pid) {
        send(String.format(ImageServerDialogProperties.getString("cmd.kill"), pid));
        // have to sleep a little to skip output after kill command
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // ignore
        }
    }

    private static final Pattern p = Pattern.compile("(\\d+)(/)");

    /**
     * Returns PID of the process listening on the given port, null if no
     * process.
     * 
     * @param port
     * @return
     */
    public String getServerPid(String port) {
        StringBuilder buf = new StringBuilder();
        String command = String.format(ImageServerDialogProperties.getString("cmd.getServerPid"), port);
        sendWaitFor(command, defaultTimeout, prompts, buf);
        log.debug(buf.toString());
        String lines = cleanOutput(buf);
        if (lines.trim().equals("")) {
            return null;
        }
        Matcher m = p.matcher(lines);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    /**
     * cd command in linux.
     * 
     * @param dir
     */
    public void cd(String dir) {
        StringBuilder buf = new StringBuilder();
        String command = String.format(ImageServerDialogProperties.getString("cmd.cd"), dir);
        sendWaitFor(command, defaultTimeout, prompts, buf);
        log.debug(buf.toString());
    }

    /**
     * Returns file content using the 'cat' command.
     * 
     * @param filePath
     * @return
     */
    public String readFile(String filePath) {
        StringBuilder buf = new StringBuilder();
        String command = String.format(ImageServerDialogProperties.getString("cmd.readFile"), filePath);
        sendWaitFor(command, defaultTimeout, prompts, buf);
        log.debug(buf.toString());
        int from = command.length() + 1;
        int to = buf.indexOf(PROMPT) - 2;
        if (to <= from) {
            return ""; // empty file
        }
        return buf.substring(from, to);
    }

    /**
     * Removes '\r' from the end of every line.
     * 
     * @param str
     * @return
     */
    private String stripOffCR(String str) {
        String[] arr = str.split("\n");
        StringBuilder buf = new StringBuilder();
        for (String s : arr) {
            if (s.endsWith("\r")) {
                buf.append(s.substring(0, s.length() - 1));
            } else {
                buf.append(s);
            }
            buf.append("\n");
        }
        return buf.toString();
    }

    /**
     * Write file using echo command.
     * 
     * @param filePath
     * @param content
     */
    public void writeFile(String filePath, String content) {
        StringBuilder buf = new StringBuilder();
        String newContent = stripOffCR(content);
        String[] lines = newContent.split("\n");
        String cmd = null;
        for (String line : lines) {
            if (cmd == null) { // first time
                cmd = String.format(ImageServerDialogProperties.getString("cmd.writeFile"), line, filePath);
            } else {
                cmd = String.format(ImageServerDialogProperties.getString("cmd.writeFile.append"), line, filePath);
            }
            sendWaitFor(cmd, defaultTimeout, prompts, buf);
        }
    }

    /**
     * Executes 'wget' command. Return true if successful.
     * 
     * @param url
     * @param fileName
     * @param timeout
     * @return
     */
    public boolean wget(String url, String fileName, int timeout) {
        boolean result = false;
        StringBuilder buf = new StringBuilder();
        String command = String.format(ImageServerDialogProperties.getString("cmd.wget"), url, "'" + fileName+ "'");
        sendWaitFor(command, timeout, prompts, buf);
        log.debug(buf.toString());

        String[] lines = getLines(buf);
        String regex = ".*" + fileName + ".* saved .*";
        for (String line : lines) {
            if (Pattern.matches(regex, line)) {
                result = true;
            }
        }
        return result;
    }

    /**
     * Executes 'mkdir -p' command.
     * 
     * @param dir
     */
    public void mkdir(String dir) {
        StringBuilder buf = new StringBuilder();
        String command = String.format(ImageServerDialogProperties.getString("cmd.mkdir"), dir);
        sendWaitFor(command, defaultTimeout, prompts, buf);
        log.debug(buf.toString());
    }

    /**
     * Mounts an ISO file using 'mount' command.
     * 
     * @param iso
     * @param dir
     */
    public void mount(String iso, String dir) {
        StringBuilder buf = new StringBuilder();
        String command = String.format(ImageServerDialogProperties.getString("cmd.mount"), "'" + iso+ "'", dir);
        sendWaitFor(command, defaultTimeout, prompts, buf);
        log.debug(buf.toString());
    }

    /**
     * Remove file or directory using 'rm -rf' command. No error if file or
     * directory don't exist.
     * 
     * @param fileOrDir
     */
    public void rm(String fileOrDir) {
        StringBuilder buf = new StringBuilder();
        String command = String.format(ImageServerDialogProperties.getString("cmd.rm"), fileOrDir);
        sendWaitFor(command, defaultTimeout, prompts, buf);
        log.debug(buf.toString());
    }

    /**
     * Copies directory.
     * 
     * @param fromDir
     * @param toDir
     */
    public void cpDir(String fromDir, String toDir) {
        StringBuilder buf = new StringBuilder();
        String command = String.format(ImageServerDialogProperties.getString("cmd.cpDir"), fromDir, toDir);
        sendWaitFor(command, defaultTimeout, prompts, buf);
        log.debug(buf.toString());
    }

    /**
     * Change permissions on a directory.
     * 
     * @param permissions
     * @param dir
     */
    public void chmodDir(String permissions, String dir) {
        StringBuilder buf = new StringBuilder();
        String command = String.format(ImageServerDialogProperties.getString("cmd.chmodDir"), permissions, dir);
        sendWaitFor(command, defaultTimeout, prompts, buf);
        log.debug(buf.toString());
    }

    /**
     * Change permissions on a file.
     * 
     * @param permissions
     * @param file
     */
    public void chmodFile(String permissions, String file) {
        StringBuilder buf = new StringBuilder();
        String command = String.format(ImageServerDialogProperties.getString("cmd.chmodFile"), permissions, file);
        sendWaitFor(command, defaultTimeout, prompts, buf);
        log.debug(buf.toString());
    }

    /**
     * Executes 'umount' command.
     * 
     * @param dir
     */
    public void umount(String dir) {
        StringBuilder buf = new StringBuilder();
        String command = String.format(ImageServerDialogProperties.getString("cmd.umount"), dir);
        sendWaitFor(command, defaultTimeout, prompts, buf);
        log.debug(buf.toString());
    }

    /**
     * Return true if file exists.
     * 
     * @param filePath
     * @return
     */
    public boolean fileExists(String filePath) {
        boolean result = false;
        StringBuilder buf = new StringBuilder();
        String command = String.format(ImageServerDialogProperties.getString("cmd.fileExists"), filePath);
        sendWaitFor(command, defaultTimeout, prompts, buf);
        log.debug(buf.toString());
        String[] lines = getLines(buf);
        for (String line : lines) {
            if (line.trim().equals("1")) {
                result = true;
                break;
            }
            if (line.trim().equals("0")) {
                result = false;
                break;
            }
        }
        return result;
    }

    /**
     * Return true if directory exists.
     * 
     * @param dir
     * @return
     */
    public boolean directoryExists(String dir) {
        boolean result = false;
        StringBuilder buf = new StringBuilder();
        String command = String.format(ImageServerDialogProperties.getString("cmd.directoryExists"), dir);
        sendWaitFor(command, defaultTimeout, prompts, buf);
        log.debug(buf.toString());
        String[] lines = getLines(buf);
        for (String line : lines) {
            if (line.trim().equals("1")) {
                result = true;
                break;
            }
            if (line.trim().equals("0")) {
                result = false;
                break;
            }
        }
        return result;
    }

    public String[] lsDir(String dir) {
        StringBuilder buf = new StringBuilder();
        String command = String.format(ImageServerDialogProperties.getString("cmd.lsDir"), dir);
        sendWaitFor(command, defaultTimeout, prompts, buf);
        log.debug(buf.toString());
        String files = cleanOutput(buf);
        return files.split("\n");
    }

    /**
     * Expectation here is that the command is less than 80 characters and is
     * terminated by new line!
     * 
     * @param command
     * @return output of the command (empty string if no output)
     */
    public String execCommand(String command) {
        StringBuilder buf = new StringBuilder();
        sendWaitFor(command, defaultTimeout, prompts, buf);
        log.debug(buf.toString());
        return cleanOutput(buf);
    }

    /**
     * Remove the first and last lines.
     * 
     * @param buf
     * @return
     */
    private String cleanOutput(StringBuilder buf) {
        StringBuilder result = new StringBuilder();
        String[] lines = getLines(buf);
        for (int i = 1; i < lines.length - 1; i++) {
            if (i > 1) {
                result.append('\n');
            }
            result.append(lines[i]);
        }
        return result.toString();
    }
}
