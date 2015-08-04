#!/usr/bin/python
#
# Copyright (c) 2015 EMC Corporation
# All Rights Reserved
#

import zk_utils
import argparse
import sys
import os

CONFIG_KIND = '/config'
DT_CONFIG_KIND = 'DT_KIND_CONFIG'

# TODO: move all to 'ObjectService' folder?
DATA_SVC_KIND = 'DataSvc'
DATA_SERVICE_KIND = 'DataService'
OBJECT_SERVICE_KIND = 'ObjectService'

DS_KIND = 'DS'

KVSTORE_KIND = 'kvstore'
OWNERSHIPZKTABLE_ROOT = 'ZkConfigTable'

try:
    bourne_ip = os.environ['BOURNE_IPADDR']
except:
    bourne_ip = 'localhost'

parser = argparse.ArgumentParser(description = 'Bourne Head Type Info Service CLI usage.')
parser.add_argument('cmd',                                  help = 'cmd = (resetconfig | resetdt)')
parser.add_argument('--ip',         metavar = 'ipaddr',     help = 'IP address of Vipr Control Node', default=bourne_ip)

resetargs = argparse.ArgumentParser(parents = [parser], conflict_handler='resolve')

def zk_reset_config(args):
    zk_utils.zk_remove_leaf(args.ip, CONFIG_KIND, DT_CONFIG_KIND)
    zk_utils.zk_remove_leaf(args.ip, CONFIG_KIND, DATA_SVC_KIND)
    zk_utils.zk_remove_leaf(args.ip, CONFIG_KIND, DATA_SERVICE_KIND)
    zk_utils.zk_remove_leaf(args.ip, CONFIG_KIND, OBJECT_SERVICE_KIND)

def zk_reset_dt(args):
    # remove all DTs
    zk_utils.zk_remove_leaf(args.ip, "", KVSTORE_KIND)

    # remove all config tables
    zk_utils.zk_remove_leaf(args.ip, CONFIG_KIND, OWNERSHIPZKTABLE_ROOT)

    # also remove the config table list dir
    zk_utils.zk_remove_leaf(args.ip, CONFIG_KIND, DS_KIND)

try:
    if (len(sys.argv) > 1):
        cmd = sys.argv[1]
    else:
        cmd = None

    if (cmd == 'resetconfig'):
        args = resetargs.parse_args()
        zk_reset_config(args)
    elif (cmd == 'resetdt'):
        args = resetargs.parse_args()
        zk_reset_dt(args)
    else:
        parser.print_help()
except:
    raise
