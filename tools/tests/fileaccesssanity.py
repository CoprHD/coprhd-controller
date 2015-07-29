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


#global functions
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
    
def random_string(a, b):
    return base64.b64encode(os.urandom(random.randint(a, b)))

class FileAccessTester(object):
    def __init__(self, bourne, namespace, keypool, hosts, preserveDirStructure):
        self.bourne = bourne
        self.namespace = namespace
        self.keypool = keypool
        self.hosts = hosts
        self.preserveDirStructure = preserveDirStructure
    
    def __getMode(self, namespce, keypool):
        response = self.getAccessMode(namespace, keypool)
        h = response.headers
        if h[FILE_ACCESS_MODE_HEADER]:
            return h[FILE_ACCESS_MODE_HEADER].lower() 
        else:
            return None

    def __getModeDetails(self, namespce, keypool):
        response = self.getAccessModeDetails(namespace, keypool)
        return response

    #wait until the mode is pesent
    def __waitfor(self, namespace, keypool, mode):
        print 'waitfor: ' + mode + ' kpName ' + keypool + ' ns ' + namespace
        while True:
            print 'sleep 10s'
            time.sleep(10)
            cur_mode = self.__getMode(namespace, keypool)
            if(cur_mode == mode.lower()):
                break
            else:
                print "keypool %s mode %s" % (keypool, str(cur_mode)) 
        print 'switched to ' + mode
    
    def __reset(self):
        mode='disabled'
        user='111'
        duration='1000000'
      
        if self.__getMode(self.namespace, self.keypool) == mode.lower():
           return 
        self.switchAccessMode(self.namespace, self.keypool, mode, self.hosts, duration, '', user)
        self.__waitfor(self.namespace, self.keypool, 'disabled')
    
    #test disabled
    def __testDisabled(self, targetMode, token):
        self.__testDisabledInternal(targetMode, targetMode, token)
    
    def __testDisabledInternal(self, targetMode, modeAfterSwitch, token):
        print 'testDisable ' + targetMode + '-----'
        mode=targetMode
        if targetMode == "disabled":
          user=''
        else:
          user='111'
        duration='1000000'
   
        #switch
        self.switchAccessMode(self.namespace, self.keypool, mode, self.hosts, duration, token, user)
        self.__waitfor(self.namespace, self.keypool, modeAfterSwitch)
        #reset(namespace, bucket, uid, secret)
        print "pass switch"
    
    #test readonly and readwrite
    def __testRdRw(self, targetMode):
        print 'testRdRw ' + targetMode + '-----'
        mode=targetMode
        user='111'
        duration='1000000'
        token='0'
    
        self.__reset()
    
        #switch
        self.switchAccessMode(self.namespace, self.keypool, mode, self.hosts, duration, token, user)
        self.__waitfor(self.namespace, self.keypool, mode)
        self.__getModeDetails(self.namespace, self.keypool)
        print "pass normal switch"
    
  
    def __readAndVerifyKey(self, key, value):
        ret_value = self.readKey(self.namespace, self.keypool, key)
        #read to verify
        print "show key %s, got value %s" % (key, ret_value)
        if ret_value != value:
            raise Exception("read key %s returns unexpected content; expected=%s, actual=%s" % (key, value, ret_value))

    def __createKeyFailed(self, key):
        keyFailed = False
    
        try:
            ret_value = self.createKey(self.namespace, self.keypool, key)
            print "create %s" % key
            print ret_value
        except:
            keyFailed = True
            print "Could not create key %s during file access mode testing" % key
    
        if(not keyFailed):
                raise Exception("2. create key %s succeeds, when it is supposed to fail for keypool %s" % (key, self.keypool))

    def __deleteKeyFailed(self, key):
        keyFailed = False
    
        try:
            ret_value = self.deleteKey(self.namespace, self.keypool, key)
            print "delete %s" % key
            print ret_value
        except:
            keyFailed = True
            print "Could not delete key %s during file access mode testing" % key
    
        if(not keyFailed):
                raise Exception("2. delete key %s succeeds, when it is supposed to fail for keypool %s" % (key, self.keypool))

    def __readAndVerifyKeyFailed(self, key, value):
        keyFailed = False
    
        try:
            ret_value = self.readKey(self.namespace, self.keypool, key)
            print "readback"
            print ret_value
        except:
            keyFailed = True
            print "Could not read key %s during file access mode testing" % key
    
        if(not keyFailed):
                raise Exception("2. read key %s succeeds, when it is supposed to fail for keypool %s" % (key, self.keypool))


    def test(self):
        #testDisabled(namespace, bucket, 'readonly', uid, secret, '-1')
 
        # create key before switch to RW
        key1 = "key" + str(uuid.uuid1())
        value1 = self.createKey(self.namespace, self.keypool, key1)
        # Also create empty key
        key11 = "key" + str(uuid.uuid1())
        value11 = self.createEmptyKey(self.namespace, self.keypool, key11)
    
        self.__testDisabled('readwrite', '-1')
    
        # this should fail, so swallow the exception in case of failure, and raise ex if it passes
        self.__readAndVerifyKeyFailed(key1, value1)
        # overwrite key1 should fail
        self.__createKeyFailed(key1)
        # delete key1 should fail
        self.__deleteKeyFailed(key1)
    
        # create a new key, and verify you can read it
        key2 = "key" + str(uuid.uuid1())
        value2 = self.createKey(self.namespace, self.keypool, key2)
        self.__readAndVerifyKey(key2, value2)
    
        self.__testDisabled( 'disabled', '-1')
    
        # read key and verify value after switch back from RW
        self.__readAndVerifyKey(key1, value1)
    
        self.__testRdRw('readonly')
    
        # this should pass
        self.__readAndVerifyKey(key2, value2)
    
        self.__testDisabled('disabled', '-1')

        key3 = "key" + str(uuid.uuid1())
        value3 = self.createKey(self.namespace, self.keypool, key3)
    
        self.__testDisabled('readwrite', '-1')
    
        self.__testDisabledInternal('disabled', 'readwrite','6')
    
        self.__readAndVerifyKey(key2, value2)
        self.__readAndVerifyKey(key1, value1)
        self.__readAndVerifyKeyFailed(key3, value3)

        #delete all keys
        self.__testDisabled("disabled", '-1')
        self.deleteKey(self.namespace, self.keypool, key1)
        self.deleteKey(self.namespace, self.keypool, key11)
        self.deleteKey(self.namespace, self.keypool, key2)
        self.deleteKey(self.namespace, self.keypool, key3)
 
class S3FileAccessTester(FileAccessTester):
    def __init__(self, bourne, namespace, bucket, hosts, uid, secret, preserveDirStructure):
       super(S3FileAccessTester, self).__init__(bourne, namespace, bucket, hosts, preserveDirStructure)
       self.uid = uid
       self.secret = secret

    def createKeyWithValue(self, namespace, bucket, key, value):
        print "create s3 key %s with value %s" % (key, value)
        # populate user metadata headers for request
        meta = {}
	meta['x-amz-meta-foo']="bar"
	meta['x-amz-meta-bar']="foo"
        self.bourne.bucket_key_create(namespace, bucket, key, value, self.uid, meta, self.secret)

    def createKey(self, namespace, bucket, key):
        value = "value" + random_string(100, 200)
        self.createKeyWithValue(namespace, bucket, key, value)
        return value

    def createEmptyKey(self, namespace, bucket, key):
        value = None
        self.createKeyWithValue(namespace, bucket, key, value)
        return value

    def readKey(self, namespace, bucket, key):
        return self.bourne.bucket_key_show(namespace, bucket, key, None, self.uid, self.secret)

    def deleteKey(self, namespace, bucket, key):
        self.bourne.bucket_key_delete(namespace, bucket, key, None, self.uid, self.secret)
 
    def switchAccessMode(self, namespace, bucket, mode, hosts, duration, token, user):
        print "switching bucket %s to mode %s with preserveDirStructure=%s" % (bucket, mode, self.preserveDirStructure)
        self.bourne.bucket_switch(namespace, bucket, mode, hosts, duration, token, user, self.uid, self.secret, self.preserveDirStructure)
 
    def getAccessMode(self, namespace, bucket):
        response = self.bourne.bucket_switchget(namespace, bucket, self.uid, self.secret)
        if response.status_code !=204:
            print response.content
            raise Exception("get mode failed, satus %s" %(response.status_code))
        return response

    def getAccessModeDetails(self, namespace, bucket):
        response = self.bourne.bucket_fileaccesslist(namespace, bucket, self.uid, self.secret)
        if response.status_code !=200:
            print response.content
            raise Exception("get mode details failed, satus %s" %(response.status_code))
        return response

 
class SwiftFileAccessTester(FileAccessTester):
    def __init__(self, bourne, namespace, container, hosts, uid, password, preserveDirStructure):
        super(SwiftFileAccessTester, self).__init__(bourne, namespace, container, hosts, preserveDirStructure)
        self.authen_token =  self.bourne.swift_authenticate(uid, password)

    def createKeyWithValue(self, namespace, container, key, value):
        print "create swift key %s with value %s" % (key, value)
        # populate user metadata headers for request
        meta = {}
	meta['x-object-meta-foo']="bar"
	meta['x-object-meta-bar']="foo"
        self.bourne.container_object_create(namespace, container, key, value, meta, self.authen_token, None, None)

    def createKey(self, namespace, container, key):
        value = "value" + random_string(100, 200)
        self.createKeyWithValue(namespace, container, key, value)
        return value      

    def createEmptyKey(self, namespace, container, key):
        value = None
        self.createKeyWithValue(namespace, container, key, value)
        return value      

    def readKey(self, namespace, container, key):
        return self.bourne.container_object_show(namespace, container, key, None, self.authen_token)
    
    def deleteKey(self, namespace, container, key):
        self.bourne.container_object_delete(namespace, container, key, self.authen_token)
     
    def switchAccessMode(self, namespace, container, mode, hosts, duration, token, user):
        print "switching container %s to mode %s with preserveDirStructure=%s" % (container, mode, self.preserveDirStructure)
        return self.bourne.container_switchfileaccess(namespace, container, mode, hosts, duration, token, user, self.authen_token, self.preserveDirStructure)
    
    def getAccessMode(self, namespace, container):
        response = self.bourne.container_getaccessmode(namespace, container, self.authen_token)
        if response.status_code !=204:
            print response.content
            raise Exception("get mode failed, satus %s" %(response.status_code))
        return response

    def getAccessModeDetails(self, namespace, container):
        response = self.bourne.container_getfileaccess(namespace, container, self.authen_token)
        if response.status_code !=200:
            print response.content
            raise Exception("get mode details failed, satus %s" %(response.status_code))
        return response



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


if len(sys.argv) <7 :
    print "usage python fileaccesssanity.py <api> <namespace> <keypool> <cos> <uid> <secret/password>"
    sys.exit(0)

api = sys.argv[1]
namespace = sys.argv[2]
bucket = sys.argv[3]
cos = sys.argv[4]
uid = sys.argv[5]
secret = sys.argv[6]
if (len(sys.argv) == 8):
    preserveDirStructure = sys.argv[7]
else:
    preserveDirStructure = False
S3_PORT                         = '9021'
SWIFT_PORT                      = '9025'

if (cos != 'nfsexportpoints'):
    hosts = get_local_ip()

if api == "s3":
  bourne.connect(bourne_data_ip, S3_PORT)
  tester = S3FileAccessTester(bourne, namespace, bucket, hosts, uid, secret, preserveDirStructure)
  tester.test()
elif api == "swift":
  bourne.connect(bourne_data_ip, SWIFT_PORT)
  tester = SwiftFileAccessTester(bourne, namespace, bucket, hosts, uid, secret, preserveDirStructure)
  tester.test()
else:
  print "unexpected api=%s" % api
  sys.exit(1)


