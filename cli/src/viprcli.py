#!/usr/bin/python

# Copyright (c) 2012-14 EMC Corporation
# All Rights Reserved
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.


# importing python system modules

import argparse
import os
from common import SOSError
import sys

# importing ViPR modules

import authentication
import common
import virtualpool
import tenant
import project
import fileshare
import snapshot
import storagepool
import volume
import metering
import monitoring
import storagesystem
import virtualarray
import storageport
import exportgroup
import networksystem
import network
import sysmanager
#import datastore
import vcenter
import vcenterdatacenter
import protectionsystem
import consistencygroup
import host
import hostinitiators
import hostipinterfaces
import cluster
import approval
import assetoptions
import catalog
import executionwindow
import order
import socket
import storageprovider
import virtualdatacenter
import computesystem
import computeimage
import computevpool
import computelement
import quotadirectory
import sanfabrics
import loginfailedip
import keystore
import truststore
import vnasserver
import computeimageserver
import bucket
import warnings
import ipsecmanager


warnings.filterwarnings(
    'ignore',
    message='BaseException.message has been deprecated as of Python 2.6',
    category=DeprecationWarning,
    module='argparse')

# Fetch ViPR environment variables defined (if any)

vipr_ip = common.getenv('VIPR_HOSTNAME')
vipr_port = common.getenv('VIPR_PORT')
vipr_ui_port = common.getenv('VIPR_UI_PORT')
vipr_cli_dir = common.getenv('VIPR_CLI_INSTALL_DIR')
# parser having common arguments across all modules

common_parser = argparse.ArgumentParser()
common_parser.add_argument('-hostname', '-hn',
                           metavar='<hostname>',
                           default=vipr_ip,
                           dest='ip',
                           help='Hostname (fully qualifiled domain name) ' +
                           'or IPv4 address (i.e. 192.0.2.0) or IPv6 address' +
                           ' inside quotes and brackets ' +
                           '(i.e. "[2001:db8::1]") of ViPR')
common_parser.add_argument('-port', '-po',
                           type=int,
                           metavar='<port_number>',
                           default=vipr_port,
                           dest='port',
                           help='port number of ViPR')
common_parser.add_argument('-portui', '-pu',
                           type=int,
                           metavar='<ui_port_number>',
                           default=vipr_ui_port,
                           dest='uiPort',
                           help='https port number of ViPR Portal UI')
common_parser.add_argument('-cf', '-cookiefile',
                           help='Full name of cookiefile',
                           metavar='<cookiefile>',
                           dest='cookiefile')

# main commandline parser

main_parser = argparse.ArgumentParser(
    description='ViPR CLI usage',
    parents=[common_parser],
    conflict_handler='resolve')
main_parser.add_argument('-v', '--version', '-version',
                         action='version',
                         version='%(prog)s 1.0',
                         help='show version number of program and exit')


def display_version():
    print common.get_viprcli_version()


# register module specific parsers with the common_parser
module_parsers = main_parser.add_subparsers(help='Use One Of Commands')

authentication.authenticate_parser(module_parsers, vipr_ip, vipr_port)
authentication.logout_parser(module_parsers, vipr_ip, vipr_port)
authentication.authentication_parser(module_parsers, common_parser)
approval.approval_parser(module_parsers, common_parser)
assetoptions.assetoptions_parser(module_parsers, common_parser)
catalog.catalog_parser(module_parsers, common_parser)
executionwindow.executionwindow_parser(module_parsers, common_parser)
order.order_parser(module_parsers, common_parser)
virtualpool.vpool_parser(module_parsers, common_parser)
tenant.tenant_parser(module_parsers, common_parser)
project.project_parser(module_parsers, common_parser)
fileshare.fileshare_parser(module_parsers, common_parser)
snapshot.snapshot_parser(module_parsers, common_parser)
volume.volume_parser(module_parsers, common_parser)
consistencygroup.consistencygroup_parser(module_parsers, common_parser)
storagepool.storagepool_parser(module_parsers, common_parser)
metering.meter_parser(module_parsers, common_parser)
monitoring.monitor_parser(module_parsers, common_parser)
storageprovider.storageprovider_parser(module_parsers, common_parser)
storagesystem.storagesystem_parser(module_parsers, common_parser)
host.host_parser(module_parsers, common_parser)
hostinitiators.initiator_parser(module_parsers, common_parser)
hostipinterfaces.ipinterface_parser(module_parsers, common_parser)
cluster.cluster_parser(module_parsers, common_parser)
virtualarray.varray_parser(module_parsers, common_parser)
storageport.storageport_parser(module_parsers, common_parser)
exportgroup.exportgroup_parser(module_parsers, common_parser)
sysmanager.system_parser(module_parsers, common_parser)
protectionsystem.protectionsystem_parser(module_parsers, common_parser)
virtualdatacenter.vdc_parser(module_parsers, common_parser)
#datastore.datastore_parser(module_parsers, common_parser)
#objectpool.objectpool_parser(module_parsers, common_parser)
vcenter.vcenter_parser(module_parsers, common_parser)
vcenterdatacenter.vcenterdatacenter_parser(module_parsers, common_parser)
quotadirectory.quotadirectory_parser(module_parsers, common_parser)
sanfabrics.san_fabrics_parser(module_parsers, common_parser)
networksystem.networksystem_parser(module_parsers, common_parser)
computesystem.computesystem_parser(module_parsers, common_parser)
computeimage.computeimage_parser(module_parsers, common_parser)
computelement.computelement_parser(module_parsers, common_parser)
computevpool.computevpool_parser(module_parsers, common_parser)
loginfailedip.loginfailedip_parser(module_parsers, common_parser)
keystore.keystore_parser(module_parsers, common_parser)
truststore.truststore_parser(module_parsers, common_parser)
vnasserver.vnasserver_parser(module_parsers, common_parser)
computeimageserver.computeimageserver_parser(module_parsers, common_parser)
bucket.bucket_parser(module_parsers, common_parser)
ipsecmanager.ipsec_parser(module_parsers, common_parser)
network.network_parser(module_parsers, common_parser)



# Parse Command line Arguments and execute the corresponding routines
try:
    if(len(sys.argv) > 1 and (sys.argv[1] == '-v' or
                              sys.argv[1] == '-version' or
                              sys.argv[1] == '--version')):
        display_version()
    else:
        args = main_parser.parse_args()
        common.COOKIE = args.cookiefile
        result = args.func(args)
        if(result):
            if isinstance(result, list):
                for record in result:
                    print record
            else:
                print result

except SOSError as e:
    sys.stderr.write(e.err_text + "\n")
    sys.exit(e.err_code)
except (EOFError, KeyboardInterrupt):
    sys.stderr.write("\nUser terminated request\n")
    sys.exit(SOSError.CMD_LINE_ERR)

