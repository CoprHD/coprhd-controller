#!/usr/bin/python

# Copyright 2015 EMC Corporation
# All Rights Reserved

# Copyright (c) 2013-14 EMC Corporation
# All Rights Reserved

# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.

'''
This module contains the computesystem implementation
'''

import common
import json
from common import SOSError
from common import TableGenerator
from virtualarray import VirtualArray
from computelement import ComputeElement


class ComputeVpool(object):

    URI_COMPUTE_VPOOL = "/compute/vpools"
    URI_COMPUTE_VPOOL_ID = URI_COMPUTE_VPOOL + "/{0}"
    URI_COMPUTE_VPOOL_DELETE = URI_COMPUTE_VPOOL_ID + "/deactivate"
    URI_COMPUTE_VPOOL_ASSIGNED = \
                        URI_COMPUTE_VPOOL_ID + "/assign-matched-elements"

    COMPUTE_TYPE_LIST = ['Cisco_UCSM', 'Cisco_CSeries', 'Generic']

    '''
    Constructor: takes IP address and port of the ViPR instance. These are
    needed to make http requests for REST API
    '''

    def __init__(self, ipAddr, port):
        self.__ipAddr = ipAddr
        self.__port = port

    def _computevpool_prepare_payload(self, param,
                                systemtype,
                                description,
                                usematchedpools,
                                minprocessors, maxprocessors,
                                mincores, maxcores,
                                minthreads, maxthreads,
                                mincpuspeed, maxcpuspeed,
                                minmemory, maxmemory,
                                minnics, maxnics,
                                minhbas, maxhbas):
        if(systemtype):
            param["system_type"] = systemtype
        if(description):
            param["description"] = description
        if(usematchedpools):
            param["use_matched_elements"] = usematchedpools
        if(minprocessors):
            param["min_processors"] = minprocessors
        if(maxprocessors):
            param["max_processors"] = maxprocessors

        if(mincores):
            param["min_total_cores"] = mincores
        if(maxcores):
            param["max_total_cores"] = maxcores

        if(minthreads):
            param["min_total_threads"] = minthreads
        if(maxthreads):
            param["max_total_threads"] = maxthreads

        if(mincpuspeed):
            param["min_cpu_speed"] = mincpuspeed
        if(maxcpuspeed):
            param["max_cpu_speed"] = maxcpuspeed

        if(minmemory):
            param["min_memory"] = minmemory
        if(maxmemory):
            param["max_memory"] = maxmemory

        if(minnics):
            param["min_nics"] = minnics

        if(maxnics):
            param["max_nics"] = maxnics

        if(minhbas):
            param["min_hbas"] = minhbas

        if(maxhbas):
            param["max_hbas"] = maxhbas

        return param

    '''
    Create a Compute Virtual Pool
    '''
    def computevpool_create(self, name,
                                systemtype,
                                description,
                                usematchedpools,
                                varrays,
                                minprocessors, maxprocessors,
                                mincores, maxcores,
                                minthreads, maxthreads,
                                mincpuspeed, maxcpuspeed,
                                minmemory, maxmemory,
                                minnics, maxnics,
                                minhbas, maxhbas,
                                templates):
        params = {
                 'name': name
                 }
        #varray details to computevpool
        if(varrays):
            varrobj = VirtualArray(self.__ipAddr, self.__port)
            varr_list = []
            for varray in varrays:
                varr_list.append(varrobj.varray_query(varray))
            params["varrays"] = varr_list

        #service profile templates
        if(templates):
            params["service_profile_template"] = templates

        self._computevpool_prepare_payload(params,
                                systemtype,
                                description,
                                usematchedpools,
                                minprocessors, maxprocessors,
                                mincores, maxcores,
                                minthreads, maxthreads,
                                mincpuspeed, maxcpuspeed,
                                minmemory, maxmemory,
                                minnics, maxnics,
                                minhbas, maxhbas)

        body = json.dumps(params)
        (s, h) = common.service_json_request(self.__ipAddr,
                                self.__port, "POST",
                                self.URI_COMPUTE_VPOOL,
                                body)

        return common.json_decode(s)

    '''
    Update a Compute Virtual Pool
    return: returns updated Compute Virtual Pool
    '''
    def computevpool_update(self, name, label,
                                systemtype,
                                description,
                                usematchedpools,
                                minprocessors, maxprocessors,
                                mincores, maxcores,
                                minthreads, maxthreads,
                                mincpuspeed, maxcpuspeed,
                                minmemory, maxmemory,
                                minnics, maxnics,
                                minhbas, maxhbas,
                                varray_add, varray_remove,
                                templates_add, templates_remove):
        compute_uri = self.computevpool_query(name)
        params = {
            'name': label
            }
        self._computevpool_prepare_payload(params, systemtype,
                                description,
                                usematchedpools,
                                minprocessors, maxprocessors,
                                mincores, maxcores,
                                minthreads, maxthreads,
                                mincpuspeed, maxcpuspeed,
                                minmemory, maxmemory,
                                minnics, maxnics,
                                minhbas, maxhbas)

        nhobj = VirtualArray(self.__ipAddr, self.__port)
        if (varray_add):
            add_varr_list = []
            for varray in varray_add:
                add_varr_list.append(nhobj.varray_query(varray))
            params['varray_changes'] = {'add': {'varrays': add_varr_list}}

        if (varray_remove):
            remove_varr_list = []
            for varray in varray_remove:
                remove_varr_list.append(nhobj.varray_query(varray))
            params['varray_changes'] = {'remove':
                                        {'varrays': remove_varr_list}}

        if(templates_add):
            params['service_profile_template_changes'] = {'add':
                                 {'service_profile_template': templates_add}}

        if(templates_remove):
            params['service_profile_template_changes'] = {'remove':
                                 {'service_profile_template':
                                   templates_remove}}

        body = json.dumps(params)
        (s, h) = common.service_json_request(self.__ipAddr,
                                self.__port, "PUT",
                                self.URI_COMPUTE_VPOOL_ID.format(compute_uri),
                                body)

        return common.json_decode(s)

    '''
    Get all compute virtual pools
    '''
    def computevpool_list(self):
        (s, h) = common.service_json_request(
                            self.__ipAddr, self.__port,
                            "GET",
                            ComputeVpool.URI_COMPUTE_VPOOL,
                            None)

        o = common.json_decode(s)
        return o['computevirtualpool']

    def computevpool_query(self, name):
        '''
            Returns the UID of the computesystem specified by the name
        '''
        computevpools = self.computevpool_list()

        for computevpool in computevpools:
            if (computevpool['name'] == name):
                return computevpool['id']

        raise SOSError(SOSError.NOT_FOUND_ERR,
                       "computevpool " + name + ": not found")
        return

    '''
    Delete a Compute Virtual Pool
    '''
    def computevpool_delete(self, name):
        uri = self.computevpool_query(name)

        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                            "POST",
                            ComputeVpool.URI_COMPUTE_VPOOL_DELETE.format(uri),
                            None)

        return common.json_decode(s)

    '''
    Get compute virtual pool by ID
    '''
    def computepool_show_by_uri(self, uri, xml=False):
        '''
        Makes a REST API call to retrieve details of a Computesystem
        based on its UUID
        '''

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            ComputeVpool.URI_COMPUTE_VPOOL_ID.format(uri),
            None, None, xml)

        if(not xml):
            return common.json_decode(s)

        else:
            return s

    def computevpool_show(self, name, xml=False):
        uri = self.computevpool_query(name)
        return self.computepool_show_by_uri(uri, xml)

    '''
    Assign Compute Elements to the Compute Virtual Pool
    '''
    def computepool_assgin(self, name, computesystemname, computele_add,
                                                          computele_remove):
        compute_uri = self.computevpool_query(name)
        vpool = {}
        parms = {}
        computele_uri = None

        #add compute element
        computeleobj = ComputeElement(self.__ipAddr, self.__port)
        if(computele_add):
            add_vpooluirs = []
            for computele in computele_add:
                computele_uri = computeleobj.query_compute_element(computele,
                                                   computesystemname)
                add_vpooluirs.append(computele_uri)
            add_vpool = {'compute_element': add_vpooluirs}
            parms['add'] = add_vpool

        if(computele_remove):
            remove_voluris = []
            for computele in computele_remove:
                computele_uri = computeleobj.query_compute_element(computele,
                                                   computesystemname)
                remove_voluris.append(computele_uri)
            remove_vpool = {'compute_element': remove_voluris}
            parms['remove'] = remove_vpool

        vpool['assigned_element_changes'] = parms

        body = json.dumps(vpool)

        (s, h) = common.service_json_request(self.__ipAddr,
                        self.__port, "PUT",
                        self.URI_COMPUTE_VPOOL_ASSIGNED.format(compute_uri),
                        body)
        return common.json_decode(s)


#Common parameters for san_fabric parser.
def computevpool_optional_sub_common_parser(cc_common_parser):

    cc_common_parser.add_argument('-description', '-desc',
                            help='Description of VPOOL',
                            dest='description',
                            metavar='<description>')
    cc_common_parser.add_argument('-usematchedpools', '-ump',
                            help='VPOOL uses matched pools',
                            metavar='<useMatchedPools>',
                            dest='usematchedpools',
                            default='true',
                            choices=['true', 'false'])
    cc_common_parser.add_argument('-t', '-type',
                            dest='type',
                            choices=ComputeVpool.COMPUTE_TYPE_LIST,
                            default='Cisco_UCSM',
                            help='Type of compute system')

    cc_common_parser.add_argument('-minprocessors', '-minp',
                                metavar='<MinProcessors>',
                                dest='minprocessors',
                                type=int,
                                help='Min Processors')
    cc_common_parser.add_argument('-maxprocessors', '-maxp',
                                metavar='<MaxProcessors>',
                                dest='maxprocessors',
                                type=int,
                                help='Max Processors')

    cc_common_parser.add_argument('-mincores', '-minc',
                                metavar='<MinTotalCores>',
                                dest='mincores',
                                type=int,
                                help='Min Total Cores')
    cc_common_parser.add_argument('-maxcores', '-maxc',
                                metavar='<MaxTotalCores>',
                                dest='maxcores',
                                type=int,
                                help='Max Total Cores')

    cc_common_parser.add_argument('-minthreads', '-mint',
                                metavar='<MinTotalThreads>',
                                dest='minthreads',
                                type=int,
                                help='Min Total Threads')
    cc_common_parser.add_argument('-maxthreads', '-maxt',
                                metavar='<MaxTotalThreads>',
                                dest='maxthreads',
                                type=int,
                                help='Max Total Threads')

    cc_common_parser.add_argument('-mincpuspeed', '-mins',
                                metavar='<MinCpuSpeed>',
                                dest='mincpuspeed',
                                type=int,
                                help='Min Cpu Speed')
    cc_common_parser.add_argument('-maxcpuspeed', '-maxs',
                                metavar='<MaxCpuSpeed>',
                                dest='maxcpuspeed',
                                type=int,
                                help='Max Cpu Speed')

    cc_common_parser.add_argument('-minmemory', '-minm',
                                metavar='<MinMemory>',
                                dest='minmemory',
                                type=int,
                                help='Min Memory')
    cc_common_parser.add_argument('-maxmemory', '-maxm',
                                metavar='<MaxMemory>',
                                dest='maxmemory',
                                type=int,
                                help='Max Memory')

    cc_common_parser.add_argument('-minnics', '-minn',
                                metavar='<MinNICs>',
                                dest='minnics',
                                type=int,
                                help='Min NICs')
    cc_common_parser.add_argument('-maxnics', '-maxn',
                                metavar='<MaxNICs>',
                                dest='maxnics',
                                type=int,
                                help='Max NICs')

    cc_common_parser.add_argument('-minhbas', '-minh',
                                metavar='<MinHBAs>',
                                dest='minhbas',
                                type=int,
                                help='Min HBAs')
    cc_common_parser.add_argument('-maxhbas', '-maxh',
                                metavar='<MaxHBAs>',
                                dest='maxhbas',
                                type=int,
                                help='Max HBAs')


#Common parameters for san_fabric parser.
def computevpool_mandatory_sub_common_parser(mandatory_common_parser):
    mandatory_common_parser.add_argument('-name', '-n',
                                help='Name of ComputeVirtualPool',
                                metavar='<computevpoolname>',
                                dest='name',
                                required=True)


# ComputeVpool Create routines
def create_parser(subcommand_parsers, common_parser):
    # create command parser
    create_parser = subcommand_parsers.add_parser(
        'create',
        description='ViPR Computevpool Create CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Create a Computevpool')
    #mandatory arguments
    mandatory_args = create_parser.add_argument_group('mandatory arguments')
    computevpool_mandatory_sub_common_parser(mandatory_args)
    #optional arguments
    computevpool_optional_sub_common_parser(create_parser)
    create_parser.add_argument('-varrays', '-va',
                               help='varrays',
                               metavar='<varrays>',
                               dest='varrays',
                               nargs='+')
    create_parser.add_argument('-servicetemplates', '-stp',
                               help='Service Profile Templates',
                               metavar='<ServiceProfileTemplates>',
                               dest='servicetemplates',
                               nargs='+')
    create_parser.set_defaults(func=computevpool_create)


def computevpool_create(args):
    try:
        obj = ComputeVpool(args.ip, args.port)
        obj.computevpool_create(args.name,
                                args.type,
                                args.description,
                                args.usematchedpools,
                                args.varrays,
                                args.minprocessors, args.maxprocessors,
                                args.mincores, args.maxcores,
                                args.minthreads, args.maxthreads,
                                args.mincpuspeed, args.maxcpuspeed,
                                args.minmemory, args.maxmemory,
                                args.minnics, args.maxnics,
                                args.minhbas, args.maxhbas,
                                args.servicetemplates)

    except SOSError as e:
        raise common.format_err_msg_and_raise("create", "computevpool",
                                              e.err_text, e.err_code)

# ComputeVpool Update routines


def update_parser(subcommand_parsers, common_parser):
    # create command parser
    update_parser = subcommand_parsers.add_parser(
        'update',
        description='ViPR Compute Virtual Pool Update CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Update a Computevpool')
    #prepare mandatory arguments
    mandatory_args = update_parser.add_argument_group('mandatory arguments')
    computevpool_mandatory_sub_common_parser(mandatory_args)

    # prepare optional arguments
    computevpool_optional_sub_common_parser(update_parser)
    update_parser.add_argument('-label', '-l',
                                help='label of ComputeVirtualPool',
                                metavar='<label>',
                                dest='label')
    update_parser.add_argument('-varray_add', '-va_add',
                   help='varray to be added to ComputeVpool',
                   dest='varray_add',
                   nargs='+',
                   metavar='<varray_add>')
    update_parser.add_argument('-varray_remove', '-va_rm',
                   metavar="<varray_remove>",
                   help='varray to be removed from ComputeVpool',
                   nargs='+',
                   dest='varray_remove')

    update_parser.add_argument('-templates_add', '-tp_add',
                    help='ServiceProfileTemplates to be added to ComputeVpool',
                    dest='templates_add',
                    nargs='+',
                    metavar='<service_profile_templates_add>')
    update_parser.add_argument('-templates_remove', '-tp_rm',
            metavar="<service_profile_templates_remove>",
            help='ServiceProfileTemplates to be removed from ComputeVpool',
            nargs='+',
            dest='templates_remove')

    update_parser.set_defaults(func=computevpool_update)


def computevpool_update(args):
    try:
        obj = ComputeVpool(args.ip, args.port)
        obj.computevpool_update(args.name, args.label, args.type,
                                args.description,
                                args.usematchedpools,
                                args.minprocessors, args.maxprocessors,
                                args.mincores, args.maxcores,
                                args.minthreads, args.maxthreads,
                                args.mincpuspeed, args.maxcpuspeed,
                                args.minmemory, args.maxmemory,
                                args.minnics, args.maxnics,
                                args.minhbas, args.maxhbas,
                                args.varray_add, args.varray_remove,
                                args.templates_add, args.templates_remove)

    except SOSError as e:
        raise common.format_err_msg_and_raise("update", "computevpool",
                                              e.err_text, e.err_code)


# VPOOL List routines

def list_parser(subcommand_parsers, common_parser):
    # list command parser
    list_parser = subcommand_parsers.add_parser(
        'list',
        description='ViPR Compute Virtual Pool List CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='List Classes of Service')

    list_parser.add_argument('-v', '-verbose',
                    dest='verbose',
                    help='List ComputeVpool with details',
                    action='store_true')
    list_parser.add_argument('-long', '-l',
                    action='store_true',
                    help='List ComputeVpool with details in table format',
                    dest='long')
    list_parser.set_defaults(func=computevpool_list)


def computevpool_list(args):
    try:
        obj = ComputeVpool(args.ip, args.port)
        uris = obj.computevpool_list()
        output = []
        for uri in uris:
            temp = obj.computepool_show_by_uri(uri['id'], False)
            if(temp):
                if(args.verbose == False):
                    del temp["service_profile_templates"]
                output.append(temp)
        if(len(output) > 0):
            if(args.verbose):
                return common.format_json_object(output)
            elif(args.long):
                TableGenerator(output,
                               ['name',
                                'system_type',
                                'in_use',
                                'use_matched_elements']).printTable()
            else:
                TableGenerator(output, ['name',
                                        'system_type',
                                        'in_use']).printTable()
    except SOSError as e:
        raise common.format_err_msg_and_raise("list", "computevpool",
                                              e.err_text, e.err_code)


# ComputeVpool Delete routines

def delete_parser(subcommand_parsers, common_parser):
    # delete command parser
    delete_parser = subcommand_parsers.add_parser(
        'delete',
        description='ViPR Compute Virtual Pool Delete CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Delete a VPOOL')
    mandatory_args = delete_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='name of ComputeVirtualPool',
                                dest='name',
                                metavar='<computevpoolname>',
                                required=True)
    delete_parser.set_defaults(func=computevpool_delete)


def computevpool_delete(args):
    try:
        obj = ComputeVpool(args.ip, args.port)
        obj.computevpool_delete(args.name)

    except SOSError as e:
        raise common.format_err_msg_and_raise("delete", "computevpool",
                                              e.err_text, e.err_code)


# ComputeVpool Show routines

def show_parser(subcommand_parsers, common_parser):
    # show command parser
    show_parser = subcommand_parsers.add_parser(
        'show',
        description='ViPR Compute Virtual Pool Show CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show details of a VPOOL')
    mandatory_args = show_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='name of ComputeVirtualPool',
                                dest='name',
                                metavar='<computevpoolname>',
                                required=True)
    show_parser.add_argument('-xml',
                             dest='xml',
                             action='store_true',
                             help='XML response')
    show_parser.set_defaults(func=computevpool_show)


def computevpool_show(args):
    try:
        obj = ComputeVpool(args.ip, args.port)
        res = obj.computevpool_show(args.name, args.xml)
        if(args.xml):
            return common.format_xml(res)

        return common.format_json_object(res)

    except SOSError as e:
        raise common.format_err_msg_and_raise("show", "computevpool",
                                              e.err_text, e.err_code)


# ComputeVpool Show routines

def assign_parser(subcommand_parsers, common_parser):
    # show command parser
    assgin_parser = subcommand_parsers.add_parser(
        'assign_computele',
        description='ViPR add or remove Compute Pool CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='add or remove a compute pool from ComputeVpool')
    mandatory_args = assgin_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='name of ComputeVirtualPool',
                                dest='name',
                                metavar='<computevpoolname>',
                                required=True)
    mandatory_args.add_argument('-computesystem', '-cs',
                                metavar='<computesystemname>',
                                dest='computesystem',
                                help='Name of computesystem',
                                required=True)
    assgin_parser.add_argument('-computele_add', '-ele_add',
                   help='ComputeElement to be added to ComputeVpool',
                   dest='computele_add',
                   nargs='+',
                   metavar='<computele_add>')
    assgin_parser.add_argument('-computele_remove', '-ele_remove',
                   metavar="<computele_remove>",
                   help='ComputeElement to be removed from ComputeVpool',
                   nargs='+',
                   dest='computele_remove')
    assgin_parser.set_defaults(func=computevpool_assign)


def computevpool_assign(args):
    try:
        obj = ComputeVpool(args.ip, args.port)
        res = obj.computepool_assgin(args.name, args.computesystem,
                                     args.computele_add,
                                     args.computele_remove)

    except SOSError as e:
        raise common.format_err_msg_and_raise("assign_computele",
                                               "computevpool",
                                              e.err_text, e.err_code)

#
# Compute VirtualPool Main parser routine
#


def computevpool_parser(parent_subparser, common_parser):

    # main vpool parser
    parser = parent_subparser.add_parser('computevpool',
                            description='ViPR Compute Virtual Pool CLI usage',
                            parents=[common_parser],
                            conflict_handler='resolve',
                            help='Operations on Compute Virtual Pool')
    subcommand_parsers = parser.add_subparsers(help='Use one of commands')

    # create command parser
    create_parser(subcommand_parsers, common_parser)

    # update command command parser
    update_parser(subcommand_parsers, common_parser)

    # list command parser
    list_parser(subcommand_parsers, common_parser)

    # delete command parser
    delete_parser(subcommand_parsers, common_parser)

    # show command parser
    show_parser(subcommand_parsers, common_parser)

    # assgin command parser
    assign_parser(subcommand_parsers, common_parser)
