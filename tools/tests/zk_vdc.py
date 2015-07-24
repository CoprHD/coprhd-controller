#!/usr/bin/python

#
# Copyright (c) 2012-2014 EMC Corporation
# All Rights Reserved
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.
#

import zk_utils
import argparse
import sys
import os

CONFIG_KIND = '/config'
VDC_KIND= 'standalone_vdc'

try:
    bourne_ip = os.environ['BOURNE_IPADDR']
except:
    bourne_ip = 'localhost'

parser = argparse.ArgumentParser(description = 'Bourne Head Type Info Service CLI usage.')
parser.add_argument('cmd',                                  help = 'cmd = (create|list|reset)')
parser.add_argument('--ip',         metavar = 'ipaddr',     help = 'IP address of Vipr Control Node', default=bourne_ip)

resetargs = argparse.ArgumentParser(parents = [parser], conflict_handler='resolve')

listargs = argparse.ArgumentParser(parents = [parser], conflict_handler='resolve')
listargs.add_argument('--show', action='store_true', default=False, help='show detail')

createargs = argparse.ArgumentParser(parents = [parser], conflict_handler='resolve')
createargs.add_argument('--id', help='vdc id')
createargs.add_argument('--short', help='short id')
createargs.add_argument('--local', help='is local vdc', action='store_true', default=False)
createargs.add_argument('--endpoint', help='command + data endpoint ip address')
createargs.add_argument('--secret', help='secret key')

def zk_create_vdc(args):
    zk_utils.zk_create2(args.ip, CONFIG_KIND, VDC_KIND, "vdcroot=True")

    value = "shortId=%s\nisLocal=%s\ngeoCommandEndpoint=%s\ngeoDataEndpoint=%s\nsecretKey=%s" % \
        (args.short, args.local, args.endpoint, args.endpoint, args.secret)

    zk_utils.zk_create2(args.ip, CONFIG_KIND + "/" + VDC_KIND, args.id, value)

def zk_list_vdc(args):
    kind = CONFIG_KIND + "/" + VDC_KIND
    for vdc in zk_utils.zk_ls(args.ip, kind):
        print vdc
        if args.show:
            print zk_utils.zk_get(args.ip, kind, vdc.strip())        

def zk_reset_all(args):
    zk_utils.zk_remove_leaf(args.ip, CONFIG_KIND, VDC_KIND)

try:
    if (len(sys.argv) > 1):
        cmd = sys.argv[1]
    else:
        cmd = None

    if (cmd == 'create'):
        args = createargs.parse_args()
        zk_create_vdc(args)
    elif (cmd == 'list'):
        args = listargs.parse_args()
        zk_list_vdc(args)
    elif (cmd == 'reset'):
        args = resetargs.parse_args()
        zk_reset_all(args)
    else:
        parser.print_help()
except:
    raise
