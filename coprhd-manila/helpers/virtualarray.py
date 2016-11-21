#!/usr/bin/python
# Copyright (c)2012 EMC Corporation
# All Rights Reserved

# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.

import json
from manila.share.drivers.coprhd.helpers import common as commoncoprhdapi

from manila.share.drivers.coprhd.helpers.common import SOSError
from manila.share.drivers.coprhd.helpers.virtualdatacenter import VirtualDatacenter


class VirtualArray(object):

    '''
    The class definition for operations on 'VirtualArray'.
    '''

    # Commonly used URIs for the 'varrays' module
    URI_VIRTUALARRAY = '/vdc/varrays'
    URI_VIRTUALARRAY_BY_VDC_ID = '/vdc/varrays?vdc-id={0}'
    URI_VIRTUALARRAY_BY_VDC_ID_AND_TENANT_ID = '/vdc/varrays?vdc-id={0}&tenant-id={1}'
    URI_VIRTUALARRAY_BY_TENANT_ID = '/vdc/varrays?tenant-id={0}'
    URI_VIRTUALARRAY_URI = '/vdc/varrays/{0}'
    URI_VIRTUALARRAY_ACLS = URI_VIRTUALARRAY_URI + '/acl'
    URI_RESOURCE_DEACTIVATE = '{0}/deactivate'
    URI_AUTO_TIER_POLICY = "/vdc/varrays/{0}/auto-tier-policies"
    URI_LIST_STORAGE_PORTS = "/vdc/varrays/{0}/storage-ports"
    URI_STORAGE_PORT_DETAILS = "/vdc/storage-ports/{0}"

    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the ViPR instance.
        These are needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port

    def get_autotier_policy_by_uri(self, nhuri):
        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port, "GET",
            VirtualArray.URI_AUTO_TIER_POLICY.format(nhuri), None)
        o = commoncoprhdapi.json_decode(s)
        return o["auto_tier_policy"]

    def varray_query(self, name):
        '''
        Returns the UID of the varray specified by the name
        '''
        if (commoncoprhdapi.is_uri(name)):
            return name

        uris = self.varray_list()

        for uri in uris:
            varray = self.varray_show(uri, False)
            if(varray):
                if(varray['name'] == name):
                    return varray['id']

        raise SOSError(SOSError.NOT_FOUND_ERR,
                       "varray " + name + ": not found")

    '''
    This method given list of names of varrays, returns the list of
    corresponding Uris
    '''

    def convertNamesToUris(self, varrayNameList):
        varrayList = []
        if (varrayNameList):
            for varray in varrayNameList:
                varray_uri = self.varray_query(varray)
                varrayList.append(varray_uri)

        return varrayList

    def varray_list(self, vdcname=None, tenant=None):
        '''
        Returns all the varrays in a vdc
        Parameters:
        Returns:
                JSON payload of varray list
        '''
        vdcuri = None
        vdcrestapi = None

        if(tenant != None):
            from manila.share.drivers.coprhd.helpers.tenant import Tenant
            tenant_obj = Tenant(self.__ipAddr, self.__port)
            tenanturi = tenant_obj.tenant_query(tenant)
            if(vdcname != None):
                vdcrestapi = VirtualArray.URI_VIRTUALARRAY_BY_VDC_ID_AND_TENANT_ID.format(vdcname, tenanturi)
            else:
                vdcrestapi = VirtualArray.URI_VIRTUALARRAY_BY_TENANT_ID.format(tenanturi)
        else:
            if(vdcname != None):
                vdcrestapi = VirtualArray.URI_VIRTUALARRAY_BY_VDC_ID.format(vdcname)
            else:
                vdcrestapi = VirtualArray.URI_VIRTUALARRAY

        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port, "GET",
            vdcrestapi, None)

        o = commoncoprhdapi.json_decode(s)

        returnlst = []
        for item in o['varray']:
            returnlst.append(item['id'])

        return returnlst

    def varray_show(self, label, xml=False):
        '''
        Makes a REST API call to retrieve details of a varray
        based on its UUID
        '''
        uri = self.varray_query(label)

        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port, "GET",
            VirtualArray.URI_VIRTUALARRAY_URI.format(uri),
            None, None, xml)

        if(xml is False):
            o = commoncoprhdapi.json_decode(s)
            if('inactive' in o):
                if(o['inactive'] is True):
                    return None
                else:
                    return o
        else:
            return s

    def varray_get_acl(self, label):
        '''
        Makes a REST API call to retrieve details of a varray
        based on its UUID
        '''
        uri = self.varray_query(label)

        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port, "GET",
            self.URI_VIRTUALARRAY_ACLS.format(uri),
            None, None, None)

        o = commoncoprhdapi.json_decode(s)

        return o

    def varray_allow_tenant(self, varray, tenant):
        '''
        Makes a REST API call to retrieve details of a varray
        based on its UUID
        '''
        uri = self.varray_query(varray)

        from manila.share.drivers.coprhd.helpers.tenant import Tenant
        tenant_obj = Tenant(self.__ipAddr, self.__port)
        tenanturi = tenant_obj.tenant_query(tenant)

        parms = {
            'add': [{
                'privilege': ['USE'],
                'tenant': tenanturi,
            }]
        }

        body = json.dumps(parms)

        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port, "PUT",
            self.URI_VIRTUALARRAY_ACLS.format(uri),
            body)

        return s

    def varray_disallow_tenant(self, varray, tenant):
        '''
        Makes a REST API call to retrieve details of a varray
        based on its UUID
        '''
        uri = self.varray_query(varray)

        from manila.share.drivers.coprhd.helpers.tenant import Tenant
        tenant_obj = Tenant(self.__ipAddr, self.__port)
        tenanturi = tenant_obj.tenant_query(tenant)

        parms = {
            'remove': [{
                'privilege': ['USE'],
                'tenant': tenanturi,
            }]
        }

        body = json.dumps(parms)

        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port, "PUT",
            self.URI_VIRTUALARRAY_ACLS.format(uri),
            body)
        return s

    def varray_create(self, label, autosanzoning,
                      devicereg, protection):
        '''
        creates a varray
        parameters:
            label:  label of the varray
        Returns:
            JSON payload response
        '''
        try:
            check = self.varray_show(label)
        except SOSError as e:
            if(e.err_code == SOSError.NOT_FOUND_ERR):
                params = dict()
                params['name'] = label
                if(autosanzoning):
                    params['auto_san_zoning'] = autosanzoning
                if(devicereg):
                    params['device_registered'] = devicereg
                if(protection):
                    params['protection_type'] = protection        

                body = json.dumps(params)
                (s, h) = commoncoprhdapi.service_json_request(
                    self.__ipAddr, self.__port, "POST",
                    VirtualArray.URI_VIRTUALARRAY, body)
                o = commoncoprhdapi.json_decode(s)
                return o
            else:
                raise e

        if(check):
            raise SOSError(SOSError.ENTRY_ALREADY_EXISTS_ERR,
                           "varray with name " + label + " already exists")

    def varray_update(self, label, autosanzoning,
                      devicereg, protection):
        '''
        creates a varray
        parameters:
            label:  label of the varray
        Returns:
            JSON payload response
        '''
        uri = self.varray_query(label)
        params = dict()
        params['name'] = label
        if(autosanzoning):
            params['auto_san_zoning'] = autosanzoning
        if(devicereg):
            params['device_registered'] = devicereg
        if(protection):
            params['protection_type'] = protection        

        body = json.dumps(params)
        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port, "PUT",
            VirtualArray.URI_VIRTUALARRAY_URI.format(uri), body)
        o = commoncoprhdapi.json_decode(s)
        return o

    def varray_delete(self, label):
        '''
        Makes a REST API call to delete a varray by its UUID
        '''
        uri = self.varray_query(label)

        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port, "POST",
            self.URI_RESOURCE_DEACTIVATE.format(
                VirtualArray.URI_VIRTUALARRAY_URI.format(uri)),
            None)
        return str(s) + " ++ " + str(h)

    '''
    Lists storage ports associated with this varray
    Parameter varrayName : Name of the varray for which storage
    ports to be listed
    Parameter network_connectivity :
        True - Lists the storage ports associated implicitly
        False - Lists all the storage ports associated with
                the varray ( implicit and explicit assigned )
    '''

    def list_storageports(self, varrayName, network_connectivity=False):

        from manila.share.drivers.coprhd.helpers.storagesystem import StorageSystem
        storageSystemObj = StorageSystem(self.__ipAddr, self.__port)

        varrayUri = self.varray_query(varrayName)

        if(network_connectivity is True):
            (s, h) = commoncoprhdapi.service_json_request(
                self.__ipAddr, self.__port, "GET",
                VirtualArray.URI_LIST_STORAGE_PORTS.format(
                    varrayUri) + "?network_connectivity=true", None)
        else:
            (s, h) = commoncoprhdapi.service_json_request(
                self.__ipAddr, self.__port,
                "GET",
                VirtualArray.URI_LIST_STORAGE_PORTS.format(varrayUri), None)

        o = commoncoprhdapi.json_decode(s)
        portList = []
        for item in o['storage_port']:
            portList.append(item['id'])

        portDetailsList = []
        if(portList):
            for porturi in portList:
                (s, h) = commoncoprhdapi.service_json_request(
                    self.__ipAddr, self.__port, "GET",
                    self.URI_STORAGE_PORT_DETAILS.format(porturi), None)
                portDetails = commoncoprhdapi.json_decode(s)

                # Get the storage system name to which the port belongs to
                storageSystemUri = portDetails['storage_system']['id']
                ssDetails = storageSystemObj.show_by_uri(storageSystemUri,
                                                         False)
                portDetails['storage_system'] = ssDetails['name']

                portDetailsList.append(portDetails)

        return portDetailsList

# VIRTUALARRAY Create routines


def varray_create(args):
    obj = VirtualArray(args.ip, args.port)
    try:
        obj.varray_create(args.name, args.autosanzoning,
                          args.devregistered, args.protection)
    except SOSError as e:
        commoncoprhdapi.format_err_msg_and_raise("create", "varray",
                                        e.err_text, e.err_code)


# VIRTUALARRAY Delete routines

def varray_delete(args):
    obj = VirtualArray(args.ip, args.port)
    try:
        obj.varray_delete(args.name)
    except SOSError as e:
        commoncoprhdapi.format_err_msg_and_raise("delete", "varray",
                                        e.err_text, e.err_code)

# VIRTUALARRAY Show routines

def varray_show(args):
    obj = VirtualArray(args.ip, args.port)
    try:
        res = obj.varray_show(args.name, args.xml)
        if(args.xml):
            return commoncoprhdapi.format_xml(res)

        return commoncoprhdapi.format_json_object(res)
    except SOSError as e:
        commoncoprhdapi.format_err_msg_and_raise("show", "varray",
                                        e.err_text, e.err_code)



def varray_allow_tenant(args):
    obj = VirtualArray(args.ip, args.port)
    try:
        obj.varray_allow_tenant(args.name, args.tenant)
    except SOSError as e:
        commoncoprhdapi.format_err_msg_and_raise("allow_tenant", "varray",
                                        e.err_text, e.err_code)



def varray_disallow_tenant(args):
    obj = VirtualArray(args.ip, args.port)
    try:
        obj.varray_disallow_tenant(args.name, args.tenant)
    except SOSError as e:
        commoncoprhdapi.format_err_msg_and_raise("disallow_tenant", "varray",
                                        e.err_text, e.err_code)

# VIRTUALARRAY Query routines

def varray_query(args):
    obj = VirtualArray(args.ip, args.port)
    try:
        res = obj.varray_query(args.name)
        return commoncoprhdapi.format_json_object(res)
    except SOSError as e:
        commoncoprhdapi.format_err_msg_and_raise("query", "varray",
                                        e.err_text, e.err_code)

# VIRTUALARRAY List routines


def varray_list(args):
    obj = VirtualArray(args.ip, args.port)
    from manila.share.drivers.coprhd.helpers.common import TableGenerator
    try:
        uris = obj.varray_list(args.vdcname, args.tenant)
        output = []
        for uri in uris:
            temp = obj.varray_show(uri)
            if(temp):
                # add column for auto_tier_policy
                if(args.long):
                    autotierlist = []
                    returnlist = obj.get_autotier_policy_by_uri(uri)
                    # get auto_tier policy object list
                    for item in returnlist:
                        autotierlist.append(item['name'])
                    # append new column
                    temp["auto_tier_policy"] = autotierlist

                output.append(temp)

        if(len(output) > 0):
            if(args.verbose is True):
                return commoncoprhdapi.format_json_object(output)
            elif(args.long is True):
                TableGenerator(output, ['name', 'module/auto_san_zoning',
                                        'auto_tier_policy']).printTable()
            else:
                TableGenerator(output, ['name']).printTable()

    except SOSError as e:
        commoncoprhdapi.format_err_msg_and_raise("list", "varray",
                                        e.err_text, e.err_code)


def varray_get_acl(args):
    obj = VirtualArray(args.ip, args.port)
    try:
        res = obj.varray_get_acl(args.name)
        output = res['acl']
        from manila.share.drivers.coprhd.helpers.tenant import Tenant
        tenant_obj = Tenant(args.ip, args.port)

        for item in output:
            tenantval = tenant_obj.tenant_show(item['tenant'])
            item['tenantname'] = tenantval['name']

        from manila.share.drivers.coprhd.helpers.common import TableGenerator
        TableGenerator(output, ['tenantname', 'privilege']).printTable()

    except SOSError as e:
        commoncoprhdapi.format_err_msg_and_raise("get_acl", "varray",
                                        e.err_text, e.err_code)



def varray_update(args):
    obj = VirtualArray(args.ip, args.port)
    try:
        obj.varray_update(args.name, args.autosanzoning,
                          args.devregistered, args.protection)
    except SOSError as e:
        commoncoprhdapi.format_err_msg_and_raise("update", "varray",
                                        e.err_text, e.err_code)


def storageport_list(args):
    varray = VirtualArray(args.ip, args.port)
    try:
        # Get the URIs of all associated ports
        portList = varray.list_storageports(args.name,
                                            args.network_connectivity)
        from manila.share.drivers.coprhd.helpers.common import TableGenerator
        TableGenerator(portList, ['port_name', 'port_group', 'port_network_id',
                                  'transport_type',
                                  'storage_system']).printTable()
    except SOSError as e:
        commoncoprhdapi.format_err_msg_and_raise("list-storage-ports", "varray",
                                        e.err_text, e.err_code)


#
# varray Main parser routine
#
