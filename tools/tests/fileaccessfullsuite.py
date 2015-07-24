#!/usr/bin/python
#
# Copyright (c) 2015 EMC Corporation
# All Rights Reserved
#

import argparse
import sys
import os
import time
import socket
import uuid
import base64
import random
from bourne import Bourne, FILE_ACCESS_MODE_HEADER, \
  FILE_ACCESS_DURATION_HEADER, TOKEN_HEADER

bourne=Bourne()
hosts=''
cos=''

# map containing func ptrs to file access api's
# used to switch between s3 and swift
fileaccess_ops = {}

def get_ip_by_interface(ifname):
    import fcntl
    import struct
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    return socket.inet_ntoa(fcntl.ioctl(
        s.fileno(),
        0x8915,  # SIOCGIFADDR
        struct.pack('256s', ifname[:15])
    )[20:24])

def get_local_ip():
    if (sys.platform == 'linux2'):
        return get_ip_by_interface('eth0')
    return socket.gethostbyname(socket.gethostname())

#wait until the mode is pesent
def waitfor(namespace, bucket, mode, uid, secret):
    print 'waitfor: ' + mode + ' kpName ' + bucket + ' ns ' + namespace
    while True:
      response = fileaccess_ops["getAccessMode"](namespace, bucket, uid, secret)
      h = response.headers
      if ( h[FILE_ACCESS_MODE_HEADER] and h[FILE_ACCESS_MODE_HEADER].lower() == mode.lower()):
        print 'bucket state: ' + mode
        break
      print 'sleep 2s'
      time.sleep(2)
    print 'switched to ' + mode

def getToken(namespace, bucket, uid, secret):
    response = fileaccess_ops["getAccessMode"](namespace, bucket, uid, secret)
    h = response.headers
    if ( h[TOKEN_HEADER]):
      return  h[TOKEN_HEADER] #TODO return token
    print "failed, cannot get token"
    sys.exit(1)

def getDuration(namespace, bucket, uid, secret):
    response = fileaccess_ops["getAccessMode"](namespace, bucket, uid, secret)
    h = response.headers
    #if ( h[FILE_ACCESS_DURATION_HEADER]):
    return  h[FILE_ACCESS_DURATION_HEADER]

def reset(namespace, bucket, uid, secret):
    mode='disabled'
    user='111'
    duration='1000000'
    fileaccess_ops["switchAccessMode"](namespace, bucket, mode, hosts, duration, '', user, uid, secret)
    waitfor(namespace, bucket, 'disabled', uid, secret)

#test disabled
def testDisabled(namespace, bucket, targetMode, uid, secret):
    print 'testDisable ' + targetMode + '-----'
    mode=targetMode
    user='111'
    duration='1000000'
    token='0'

    #switch
    fileaccess_ops["switchAccessMode"](namespace, bucket, mode, hosts, duration, token, user, uid, secret)
    waitfor(namespace, bucket, mode, uid, secret)
    reset(namespace, bucket, uid, secret)
    print "pass switch"

#test readonly and readwrite
def testRdRw(namespace, bucket, targetMode, uid, secret):
    print 'testRdRw ' + targetMode + '-----'
    mode=targetMode
    user='111'
    duration='1000000'
    token='0'

    reset(namespace, bucket, uid, secret)

    #switch
    fileaccess_ops["switchAccessMode"](namespace, bucket, mode, hosts, duration, token, user, uid, secret)
    waitfor(namespace, bucket, mode, uid, secret)
    print "pass normal switch"

    #extend token
    token=getToken(namespace, bucket, uid, secret)
    print token
    fileaccess_ops["switchAccessMode"](namespace, bucket, mode, hosts, duration, token, user, uid, secret)
    waitfor(namespace, bucket, mode, uid, secret)
    newtoken=getToken(namespace, bucket, uid, secret)
    if( token != newtoken):
      print "pass extend token"
    else:
      print 'failed extend token'
      sys.exit(1)

    #extend token with wrong token(bigger)
    token=getToken(namespace, bucket, uid, secret)
    response=fileaccess_ops["switchAccessMode"](namespace, bucket, mode, hosts, duration, '11111111', user, uid, secret)
    waitfor(namespace, bucket, mode, uid, secret)
    newtoken=getToken(namespace, bucket, uid, secret)
    if( token == newtoken and response.status_code == 409):
      print "pass extend token with bigger token"
    else:
      print 'failed extend token with bigger token'
      sys.exit(1)

    #extend token with wrong token(smaller)
    token=getToken(namespace, bucket, uid, secret)
    response=fileaccess_ops["switchAccessMode"](namespace, bucket, mode, hosts, duration, '0', user, uid, secret)
    waitfor(namespace, bucket, mode, uid, secret)
    newtoken=getToken(namespace, bucket, uid, secret)
    if( token == newtoken and response.status_code == 409):
      print "pass extend token with smaller token"
    else:
      print 'failed pass extend token with smaller token'
      sys.exit(1)

    #extend token with a different uid
    token=getToken(namespace, bucket, uid, secret)
    response=fileaccess_ops["switchAccessMode"](namespace, bucket, mode, hosts, duration, token, user+"123", uid, secret)
    waitfor(namespace, bucket, mode, uid, secret)
    newtoken=getToken(namespace, bucket, uid, secret)
    if( token == newtoken and response.status_code == 409):
      print "pass extend token with different uid"
    else:
      print 'failed pass extend token with different uid'
      sys.exit(1)

    #extend token with a different or empty hosts
    if (cos != 'nfsexportpoints'):
        token=getToken(namespace, bucket, uid, secret)
        response=fileaccess_ops["switchAccessMode"](namespace, bucket, mode, '127.0.0.1,127.9.9.9', duration, token, user, uid, secret)
        waitfor(namespace, bucket, mode, uid, secret)
        newtoken=getToken(namespace, bucket, uid, secret)
        if( token == newtoken and response.status_code == 409):
            print "pass extend token with different hosts"
        else:
            print 'failed pass  with different or empty hosts'
            sys.exit(1)
        response=fileaccess_ops["switchAccessMode"](namespace, bucket, mode, '', duration, token, user, uid, secret)
        waitfor(namespace, bucket, mode, uid, secret)
        newtoken=getToken(namespace, bucket, uid, secret)
        print response.status_code
        if( token == newtoken and response.status_code == 409):
            print "pass extend token with empty hosts"
        else:
            print 'failed pass extend token with empty hosts'
            sys.exit(1)


    #extend a lease
    token=getToken(namespace, bucket, uid, secret)
    curduration=getDuration(namespace, bucket, uid, secret)
    newhosts=hosts
    if (cos != 'nfsexportpoints'):
        newhosts=hosts + ',10.247.99.1'
    print newhosts

    fileaccess_ops["switchAccessMode"](namespace, bucket, mode, newhosts, duration, '', user, uid, secret)
    waitfor(namespace, bucket, mode, uid, secret)
    newtoken=getToken(namespace, bucket, uid, secret)
    newduration=getDuration(namespace, bucket, uid, secret)
    if( token == newtoken and curduration != newduration):
      print "pass lease extention"
    else:
      print 'failed lease extention'
      sys.exit(1)


def testOthers(namespace, bucket, uid, secret):
    #readonly to readwrite
    user='111'
    duration='1000000'
    token='0'
    reset(namespace, bucket, uid, secret)
    mode='readonly'
    response=fileaccess_ops["switchAccessMode"](namespace, bucket, mode, hosts, duration, token, user, uid, secret)
    waitfor(namespace, bucket, mode, uid, secret)
    response=fileaccess_ops["switchAccessMode"](namespace, bucket, 'readwrite', hosts, duration, token, user, uid, secret)
    print response.status_code
    if (response.status_code == 409):
      print 'pass readonly->readwrite'
    else:
      print 'failed readonly->readwrite is allowed'
      sys.exit(1)

    #readwrite to readwrite
    reset(namespace, bucket, uid, secret)
    mode='readwrite'
    response=fileaccess_ops["switchAccessMode"](namespace, bucket, mode, hosts, duration, token, user, uid, secret)
    waitfor(namespace, bucket, mode, uid, secret)
    response=fileaccess_ops["switchAccessMode"](namespace, bucket, 'readonly', hosts, duration, token, user, uid, secret)
    print response.status_code
    if (response.status_code == 409):
      print 'pass readwrite->readonly'
    else:
      print 'failed readwrite->readonly is allowed'
      sys.exit(1)

def testList(namespace, bucket, uid, secret):
    user='111'
    duration='1000000'
    token='0'
    reset(namespace, bucket, uid, secret)
    mode='readonly'
    reset(namespace, bucket, uid, secret)
    response=fileaccess_ops["switchAccessMode"](namespace, bucket, mode, hosts, duration, token, user, uid, secret)
    waitfor(namespace, bucket, mode, uid, secret)
    response = fileaccess_ops["getFileList"](namespace, bucket, uid, secret)
    if( response.status_code != 200):
      print 'get file list failed failed'
      sys.exit(1)
    print response.text

def random_string(a, b):
    return base64.b64encode(os.urandom(random.randint(a, b)))

def createKey(namespace, bucket, key, uid, secret):
    value = "value" + random_string(100, 200)
    print "create key %s" % key
    bourne.bucket_key_create(namespace,  bucket, key, value, uid, None, secret)
    return value

def readAndVerifyKey(namespace, bucket, key, value, uid, secret):
    ret_value = bourne.bucket_key_show(namespace, bucket, key, None, uid, secret)
    #read to verify
    print "show key %s" % key
    if ret_value != value:
        raise Exception("read key %s returns unexpected content; expected=%s, actual=%s" % (key, value, ret_value))


def test(bourneInst, namespace, bucket, uid, secret):
    #testDisabled(namespace, bucket, 'readonly', uid, secret)

    # create key before switch to RW
    key = "key" + str(uuid.uuid1())
    value = createKey(namespace, bucket, key, uid, secret)

    testDisabled(namespace, bucket, 'readwrite', uid, secret)

    # read key and verify value after switch back from RW
    readAndVerifyKey(namespace, bucket, key, value, uid, secret)

    testRdRw(namespace, bucket, 'readonly', uid, secret)
    testRdRw(namespace, bucket, 'readwrite', uid, secret)

    testOthers(namespace, bucket, uid, secret)
    testList(namespace, bucket, uid, secret)

#----------------------------------------------------------------------
# command-line parsing
#----------------------------------------------------------------------

try:
    bourne_ip = os.environ['BOURNE_IPADDR']
except:
    bourne_ip = 'localhost'

try:
    bourne_data_ip = os.environ['BOURNE_DATA_IPADDR']
except:
    bourne_data_ip = bourne_ip

bourne = Bourne()

s3_ops={"target": "s3",
  "getAccessMode": bourne.bucket_switchget,
  'switchAccessMode': bourne.bucket_switch,
  'getFileList': bourne.bucket_fileaccesslist}

swift_ops={"target": "swift",
  "getAccessMode": bourne.container_getaccessmode,
  'switchAccessMode': bourne.container_switchfileaccess,
  'getFileList': bourne.container_getfileaccess}

api = sys.argv[1]
namespace = sys.argv[2]
bucket = sys.argv[3]
cos = sys.argv[4]
uid = sys.argv[5]
secret = sys.argv[6]
S3_PORT                         = '9021'
SWIFT_PORT                      = '9025'

if (cos != 'nfsexportpoints'):
    hosts = get_local_ip()

if api == "s3":
  fileaccess_ops = s3_ops
  bourne.connect(bourne_data_ip, S3_PORT)
  test(bourne, namespace, bucket, uid, secret)
elif api == "swift":
  fileaccess_ops = swift_ops
  bourne.connect(bourne_data_ip, SWIFT_PORT)
  test(bourne, namespace, bucket, uid, secret)
else:
  print "unexpected api=%s" % api
  sys.exit(1)
