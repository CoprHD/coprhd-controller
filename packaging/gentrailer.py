#!/usr/bin/python

# Copyright 2015 EMC Corporation
# All Rights Reserved
#
# Copyright (c) 2013 EMC Corporation
# All Rights Reserved
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.
#

import sys
import hashlib
import zlib
import struct

def fatal(msg, e = None):
    s  = "Error: " + str(msg);
    if e:
        s += ": (" + str(e) + ")"
    s += "\n"
    sys.stderr.write(s)
    sys.exit(1)

# Read file
def readfile(path):
    try:
        f = open(path, 'r')
        return f.read()
    except Exception, e:
        fatal("Failed to read from file: ' + path", e)
    finally:
        if f:
            f.close()

# Write file
def appendfile(path, data):
    try:
        f = open(path, 'a')
        f.write(data)
    except Exception, e:
        fatal("Failed to write to file: ' + path", e)
    finally:
        if f:
            f.close()

def sha1_file(path):
    sha1 = hashlib.sha1()
    sha1.update(readfile(path))
    return sha1.digest()

def trailer(sha1, crc32 = None):
    t_sha1  = sha1
    t_crc32 = crc32 if crc32 != None else zlib.crc32(trailer(sha1, 0))
    t_len   = 36
    t_magic = 0x3031656e72756f42
    return struct.pack(">20siiQ", t_sha1, t_crc32, t_len, t_magic )

if __name__ == "__main__":
    def usage():
        sys.stderr.write("Usage: " + sys.argv[0] + " <sqfs_file>\n")
        sys.exit(2)
        
    if len(sys.argv) != 2:
        usage()

    path = sys.argv[1]

    try:
        appendfile(path, trailer(sha1_file(path)))
    except Exception, e:
        fatal("Failed to append image trailer for file: " + path, e)


