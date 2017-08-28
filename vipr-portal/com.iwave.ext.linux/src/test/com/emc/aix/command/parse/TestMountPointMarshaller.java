/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.aix.command.parse;

import java.util.List;

import com.emc.aix.command.parse.TextOutputUnmarshaller;
import com.emc.aix.model.MountPoint;

public class TestMountPointMarshaller {

    static String output = "  node       mounted        mounted over    vfs       date        options      \n" +
            "-------- ---------------  ---------------  ------ ------------ --------------- \n" +
            "         /dev/hd4         /                jfs2   Aug 13 20:47 rw,log=/dev/hd8 \n" +
            "         /dev/hd2         /usr             jfs2   Aug 13 20:47 rw,log=/dev/hd8 \n" +
            "         /dev/hd9var      /var             jfs2   Aug 13 20:47 rw,log=/dev/hd8 \n" +
            "         /dev/hd3         /tmp             jfs2   Aug 13 20:47 rw,log=/dev/hd8 \n" +
            "4.3.2.1  /nfs1 /nfs2         nfs3   Oct 02 14:29\n" +
            "         /dev/hd1         /home            jfs2   Aug 13 20:47 rw,log=/dev/hd8 \n" +
            "         /dev/hd11admin   /admin           jfs2   Aug 13 20:47 rw,log=/dev/hd8 \n" +
            "         /proc            /proc            procfs Aug 13 20:47 rw              \n" +
            "         /dev/hd10opt     /opt             jfs2   Aug 13 20:47 rw,log=/dev/hd8 \n" +
            "         /dev/livedump    /var/adm/ras/livedump jfs2   Aug 13 20:47 rw,log=/dev/hd8 \n" +
            "         /dev/hdiskpower12 /mnt/test2       jfs2   Oct 01 12:58 rw,log=/dev/hdiskpower12\n" +
            "         /dev/hdiskpower15 /mnt/test5       jfs2   Oct 02 09:46 rw,log=/dev/hdiskpower15\n" +
            "         /dev/hdiskpower16 /mnt/test6       jfs2   Oct 02 13:43 rw,log=/dev/hdiskpower16\n" +
            "         /dev/hdiskpower17 /mnt/test7       jfs2   Oct 02 14:29 rw,log=/dev/hdiskpower17\n" +
            "1.2.3.4  /nfs4 /nfs5  nfs3 Oct 12 15:15";

    public static void main(String[] args) {

        TextOutputUnmarshaller parser = TextOutputUnmarshaller.instance();

        List<MountPoint> mountPoints = parser.with(output).parse(MountPoint.class);

        System.out.println(mountPoints);

        for (MountPoint m : mountPoints) {
            addMountPointToFilesystems(m);
        }

    }

    public static void addMountPointToFilesystems(MountPoint m) {
        String output = String.format("%s:%n\t\t= dev\t\t%s%n", m.getPath(), m.getDevice());
        output += String.format("\t\t= vfs\t\t%s%n", m.getVfs());
        // output += String.format("\t\t= log\t\t%s\n");
        output += String.format("\t\t= mount\t\t%s%n", true);
        output += String.format("\t\t= account\t\t%s%n", false);

        System.out.print(output);
    }

}
