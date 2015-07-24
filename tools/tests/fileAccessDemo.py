#!/usr/bin/python

#
# Copyright (c) 2012 EMC Corporation
# All Rights Reserved
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.
#

import argparse
import sys
import xml2obj as x2o
import base64
import cjson
import os
import time
import socket
import subprocess
import webstorage_demo_cfg as demo_cfg
if os.name.lower() == 'nt':
    import _winreg

from bourne import Bourne, FILE_ACCESS_MODE_HEADER
S3_PORT                         = '9021'

def get_ip_address(ifname):
    import fcntl
    import struct
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    return socket.inet_ntoa(fcntl.ioctl(
        s.fileno(),
        0x8915,  # SIOCGIFADDR
        struct.pack('256s', ifname[:15])
    )[20:24])

class FileAccessDemo(object):
    def __init__(self):
        if os.name == "nt":
            self.local_ip = socket.gethostbyname(socket.gethostname())
        else:
            self.local_ip = get_ip_address('eth0')

        if self.local_ip == None or self.local_ip == "127.0.0.1":
            raise "ERROR: can't get an invalid IP address of your computer"

        self.fs_uid = demo_cfg.FS_UID
        self.fs_duration = '3600'

        # this is only used for Mac or Linux, Windows will allocate a driver automatically
        self.mount_path = '/var/tmp/bourne'

        self.__client_setup()

        self.bourne = Bourne()
        self.bourne.connect(demo_cfg.BOURNE_DATA_IPADDR, S3_PORT)

    def __client_setup(self):
        # registry change to support mount
        if os.name == "nt":
            key = _winreg.OpenKey(_winreg.HKEY_LOCAL_MACHINE, r'SOFTWARE\Microsoft\ClientForNFS\CurrentVersion\Default', 0, _winreg.KEY_ALL_ACCESS)
            _winreg.SetValueEx(key, "AnonymousUid", None, _winreg.REG_DWORD, int(demo_cfg.FS_UID))
            _winreg.SetValueEx(key, "AnonymousGid", None, _winreg.REG_DWORD, int(demo_cfg.FS_UID))
            _winreg.CloseKey(key)

    def enablefs(self, keypool, uid, secretKey):
        pass

    def disablefs(self, keypool, uid, secretKey):
        pass

    # wait until the mode is pesent
    def wait(self, keypool, mode, uid, secretKey):
        while True:
            response = self.bourne.bucket_switchget(self.namespace, keypool, uid, secretKey)
            if (response.status_code == 404 or response.status_code == 403):
                sys.stderr.write("Resource not found")
                sys.exit(1)

            h = response.headers
            if (h[FILE_ACCESS_MODE_HEADER] and h[FILE_ACCESS_MODE_HEADER].lower() == mode.lower()):
                return
            time.sleep(2)

    def domount(self, exportpoint):
        if os.name == "nt":
            cmd = "mount %s *" % exportpoint
        else:
            if (os.path.exists(self.mount_path) == False):
                os.makedirs(self.mount_path)
            cmd = "mount -t nfs %s %s" % (exportpoint, self.mount_path)

        process = subprocess.Popen(cmd, shell=True, stdout=subprocess.PIPE)

        (out, err) = process.communicate()
        if process.returncode != 0:
            sys.stderr.write("ERROR: failed to mount %s" % (exportpoint))
            sys.exit(1)

        if os.name == "nt":
            # Windows print a message to indicate where is mounted
            self.mount_path = os.path.join(out[0:2], os.path.sep)

    def dounmount(self):
        process = subprocess.Popen("mount", shell=True, stdout=subprocess.PIPE)
        (out, err) = process.communicate()
        if process.returncode != 0:
            sys.stderr.write("ERROR: failed to get mount list")
            sys.exit(1)
        #TODO: we just remove all mounts which have 'storageos' in the export path
        lines = out.splitlines()
        for line in lines:
            if os.name == 'nt':
                if line.find('storageos') >= 0:
                    process = subprocess.Popen("umount %s" % line[0:2], shell=True, stdout=subprocess.PIPE)
                    process.wait()
                    if process.returncode != 0:
                        sys.stderr.write("ERROR: failed to umount %s" % line[0:2])
            else:
                if line.find(self.mount_path) >= 0:
                    process = subprocess.Popen("umount %s" % self.mount_path, shell=True, stdout=subprocess.PIPE)
                    process.wait()
                    if process.returncode != 0:
                        sys.stderr.write( "ERROR: failed to umount %s" % self.mount_path)

    def print_list(self, result):
        print '---------------objects path---------------------'

        objects = result['fileaccess_response']['objects']
        if type(objects) == dict:
            objects = [objects]
        for obj in objects:
            relpath = obj['relativePath']
            path = os.path.join(self.mount_path, relpath)
            wPath = path.replace("/", "\\")
            print  "%s\t%s\t%s" % (obj['name'], wPath, obj['size'])


class S3FileAccessDemo(FileAccessDemo):
    def __init__(self):
        super(S3FileAccessDemo, self).__init__()
        self.namespace = 's3'

    def bucket_switch(self, bucket, mode, uid, secret):

        if (demo_cfg.DEVICE_TYPE == "filesystems"):
            res = self.bourne.bucket_switch(self.namespace, bucket, mode, self.local_ip, self.fs_duration, None, self.fs_uid,
            uid, secret)
        else:
            res = self.bourne.bucket_switch(self.namespace, bucket, mode, None, self.fs_duration, None, self.fs_uid,
            uid, secret)
        if (res.status_code == 403):
            sys.stderr.write("ERROR: Permission denied")
            sys.exit(1)

    def bucket_switchget(self, bucket, uid, secret):
        res = self.bourne.bucket_switchget(self.namespace, bucket, uid, secret)
        print res.headers[FILE_ACCESS_MODE_HEADER]

    def bucket_fileaccesslist(self, bucket, uid, secretKey):
        res = self.bourne.bucket_fileaccesslist(self.namespace, bucket, uid, secretKey)
        return res.text.strip()

    def enablefs(self, keypool, uid, secretKey):
        # switch
        self.bucket_switch(keypool, 'readwrite', uid, secretKey)

        # wait
        self.wait(keypool, 'readwrite', uid, secretKey)

        # list
        result = x2o.xml2obj(self.bucket_fileaccesslist(keypool, uid, secretKey).encode('ascii', 'ignore'))
        exportpoint = result['fileaccess_response']['mountPoints']

        # mount
        self.domount(exportpoint)
        self.print_list(result)

    def disablefs(self, keypool, uid, secretKey):

        # switch
        self.bucket_switch(keypool, 'disabled', uid, secretKey)
        # wait
        self.wait(keypool, 'disabled', uid, secretKey)

        # unmount if already mounted
        self.dounmount()

        print ("Successfully disabled file access for %s" % keypool)

    def showfs(self, keypool, uid, secretKey):
        self.bucket_switchget(keypool, uid, secretKey)

class SwiftFileAccessDemo(FileAccessDemo):
    def __init__(self):
        super(SwiftFileAccessDemo, self).__init__()
        self.namespace = "swift-demo"

    def container_switch(self, container, mode):
        res = self.bourne.container_switchfileaccess(self.namespace, container, mode, self.local_ip, self.fs_duration, None, self.fs_uid, demo_cfg.BOURNE_USER, demo_cfg.BOURNE_PASSWORD)
        print res

    def container_getfileaccess(self, container):
        res = self.bourne.container_getfileaccess(self.namespace, container, demo_cfg.BOURNE_USER, demo_cfg.BOURNE_PASSWORD)
        return res.text.strip()

    def container_switchget(self, container):
        res = self.bourne.container_getaccessmode(self.namespace, container, demo_cfg.BOURNE_USER, demo_cfg.BOURNE_PASSWORD)
        print res.headers[FILE_ACCESS_MODE_HEADER]

    def enablefs(self, keypool, uid, secretKey):
        # switch
        self.container_switch(keypool, 'readwrite', uid, secretKey)

        self.wait(keypool, 'readwrite', uid, secretKey)

        # list
        result_str = self.container_getfileaccess(keypool).encode('ascii', 'ignore')
        result = {'fileaccess_response' : eval(result_str)}

        # mount
        self.domount(result['fileaccess_response']['mountPoints'])

        # display result
        self.print_list(result)

    def disablefs(self, keypool, uid, secretKey):
        # unmount if already mounted
        self.dounmount()
        # switch
        self.container_switch(keypool, 'disabled')
        # wait
        self.wait(keypool, 'disabled')

        print ("Successfully disabled file access for %s" % keypool)

    def showfs(self, keypool, uid, secretKey):
        self.container_switchget(keypool)

parser = argparse.ArgumentParser(description='webstorage fileaccess demo usage.')
parser.add_argument('cmd', help='cmd = (enable | disable | show )')

enable = argparse.ArgumentParser(parents=[parser], conflict_handler='resolve')
enable.add_argument('bucket', help='name of the bucket')
enable.add_argument('uid', help='user id');
enable.add_argument('secretKey', help='secret key')

disable = argparse.ArgumentParser(parents=[parser], conflict_handler='resolve')
disable.add_argument('bucket', help='name of the bucket')
disable.add_argument('uid', help='user id');
disable.add_argument('secretKey', help='secret key')

show = argparse.ArgumentParser(parents=[parser], conflict_handler='resolve')
show.add_argument('bucket', help='name of the bucket')
show.add_argument('uid', help='user id');
show.add_argument('secretKey', help='secret key')


try:
    if demo_cfg.BOURNE_IPADDR == '0.0.0.0':
        print "please configure bourne IP in webstorage_demo_cfg.py"
        sys.exit()

    if demo_cfg.BOURNE_DATA_IPADDR == '0.0.0.0':
            print "please configure bourne data IP in webstorage_demo_cfg.py"
            sys.exit()

    if (len(sys.argv) > 1):
        cmd = sys.argv[1]
    else:
        cmd = None

    if cmd not in ["enable", "disable", "show"]:
        parser.print_help()
        sys.exit(1)

    if demo_cfg.API.lower() not in ('s3', 'swift'):
        parser.print_help()
        sys.exit(1)


    if demo_cfg.API.lower() == 's3':
        fileaccess = S3FileAccessDemo()
    else:
        fileaccess = SwiftFileAccessDemo()

    if(cmd == "enable"):
        args = enable.parse_args()
        fileaccess.enablefs(args.bucket, args.uid, args.secretKey)
    elif (cmd == "disable"):
        args = disable.parse_args()
        fileaccess.disablefs(args.bucket, args.uid, args.secretKey)
    elif (cmd == "show"):
        args = show.parse_args()
        fileaccess.showfs(args.bucket, args.uid, args.secretKey)
    else:
        parser.print_help()
except:
    raise
