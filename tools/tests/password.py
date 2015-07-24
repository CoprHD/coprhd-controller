#!/usr/bin/python
#
# Copyright (c) 2015 EMC Corporation
# All Rights Reserved
#

import argparse
import sys
import os
from bourne import Bourne
# OBJCTRL_INSECURE_PORT           = '9010'
# OBJCTRL_PORT                    = '9011'
#----------------------------------------------------------------------
# password cli functions
#----------------------------------------------------------------------
  
def passwordgroup_create(args, bourne):
    bourne.connect(args.ip)
    groups = None
    if args.groups:
        groups = args.groups.split(',')
    bourne.passwordgroup_create(args.uid, args.password, groups, args.namespace)

def passwordgroup_update(args, bourne):
    bourne.connect(args.ip)
    groups = None
    if args.groups:
        groups = args.groups.split(',')
    bourne.passwordgroup_update(args.uid, args.password, groups)
   
def passwordgroup_listgroup(args, bourne):
    bourne.connect(args.ip)
    groups = bourne.passwordgroup_listgroup(args.uid)
    if groups:
        print groups
    else:
        print "no groups set for uid " + args.uid
    return groups

def passwordgroup_remove(args, bourne):
    bourne.connect(args.ip)
    bourne.passwordgroup_remove(args.uid)
     
#----------------------------------------------------------------------
# command-line parsing
#----------------------------------------------------------------------

try:
    bourne_ip = os.environ['BOURNE_IPADDR']
except:
    bourne_ip = 'localhost'

# security <cmd> <cmd_args>  [--ip ipaddr]
parser = argparse.ArgumentParser(description = 'Bourne secretkey cli usage.')
parser.add_argument('cmd', help = 'cmd = (create | update | list | remove)')
parser.add_argument('--ip',    metavar = 'ipaddr',    help = 'IP address of bourne', default=bourne_ip)
parser.add_argument('uid',    help = 'uid to update/remove password for')

create = argparse.ArgumentParser(parents = [parser], conflict_handler='resolve')
create.add_argument('password',  metavar = 'password',   help = 'new password', default=None)
create.add_argument('--groups',    metavar = 'groups',    help = 'groups for this uid', default=None)
create.add_argument('namespace', metavar = 'namespace', help = 'namespace for this uid', default=None)

# add/update password and group
update = argparse.ArgumentParser(parents = [parser], conflict_handler='resolve')
update.add_argument('--password',  metavar = 'password',   help = 'new password', default=None)
update.add_argument('--groups',    metavar = 'groups',    help = 'groups for this uid', default=None)

if __name__ == '__main__':
    try:
        if (len(sys.argv) > 1):
            cmd = sys.argv[1]
        else:
            parser.print_help()
            sys.exit(1)

        bourne = Bourne()

        if (cmd == "create"):
            args = create.parse_args()
            passwordgroup_create(args, bourne)
        elif(cmd == "update"):
            args = update.parse_args()
            passwordgroup_update(args, bourne)
        elif(cmd == "list"):
            args = parser.parse_args()  	
    	    passwordgroup_listgroup(args, bourne)
        elif(cmd == "remove"):
            args = parser.parse_args()
            passwordgroup_remove(args, bourne)
        else:
            parser.print_help()
    except:
        raise
    
