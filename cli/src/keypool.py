#!/usr/bin/python
# Copyright (c)2012 EMC Corporation
# All Rights Reserved

# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.

import json
import common

from common import SOSError


class Keypool(object):

    '''
    The class definition for operations on 'Keypool'.
    '''
    # Commonly used URIs for the 'keypool' module

    URI_SERVICES_BASE = ''

    URI_S3_SERVICE_BASE = ''
    URI_S3_BUCKET_INSTANCE = URI_S3_SERVICE_BASE + '/{0}'
    URI_S3_KEY_INSTANCE = URI_S3_SERVICE_BASE + '/{0}/{1}'

    URI_SWIFT_SERVICE_BASE = '/v1'
    URI_SWIFT_ACCOUNT_INSTANCE = URI_SWIFT_SERVICE_BASE + '/{0}'
    URI_SWIFT_CONTAINER_INSTANCE = URI_SWIFT_SERVICE_BASE + '/{0}/{1}'
    URI_SWIFT_KEY_INSTANCE = URI_SWIFT_SERVICE_BASE + '/{0}/{1}/{2}'

    URI_ATMOS_SERVICE_BASE = '/rest'
    URI_ATMOS_OBJECTS = URI_ATMOS_SERVICE_BASE + '/objects'
    URI_ATMOS_OBJECTS_OID = URI_ATMOS_OBJECTS + '/{0}'
    URI_ATMOS_NAMESPACE = URI_ATMOS_SERVICE_BASE + '/namespace'
    URI_ATMOS_NAMESPACE_PATH = URI_ATMOS_NAMESPACE + '{0}'
    URI_ATMOS_SUBTENANT_BASE = URI_ATMOS_SERVICE_BASE + '/subtenant'
    URI_ATMOS_SUBTENANT_INSTANCE = URI_ATMOS_SUBTENANT_BASE + '/{0}'

    S3_PORT = '4080'
    DATA_PORT = '8080'
    ATMOS_PORT = '8082'
    SWIFT_PORT = '5080'

    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the SOS instance. These are
        needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port

    def keypool_show(self, keypool, tenant, uid, secret):
        '''
        show keypool
        parameters:
            keypool:  	label of the keypool
            tenant:  	tenant name
            uid:	user id
            secret:	secret key
        Returns:
            JSON payload response
        '''
        #uri = self.keypool_query(label)

        if (not common.is_uri(tenant)):
            from tenant import Tenant
            tenant_obj = Tenant(self.__ipAddr, self.__port)
            namespace = tenant_obj.namespace_get(tenant)

        try:
            if(apitype == 's3'):
                return s3_bucket_show(namespace, keypool, uid, secretkey)
            elif(apitype == 'swift'):
                return swift_container_show(namespace, keypool, uid, secretkey)
            elif(apitype == 'atmos'):
                return atmos_subtenant_show(namespace, keypool, uid, secretkey)
            else:
                raise SOSError(SOSError.VALUE_ERR,
                               "Wrong API type " + apitype + " specified")
        except SOSError as e:
            raise e

    def keypool_update(
            self, keypool, tenant, versioning, apitype, uid, secret):
        '''
        update keypool versioning
        parameters:
            keypool:  	label of the keypool
            project:  	project name
            tenant:  	tenant  name
            uid:	user id
            secret:	secret key
        Returns:
            JSON payload response
        '''

        if (not common.is_uri(tenant)):
            from tenant import Tenant
            tenant_obj = Tenant(self.__ipAddr, self.__port)
            namespace = tenant_obj.namespace_get(tenant)

        try:
            if(apitype == 's3'):
                return s3_bucket_update(
                    namespace, keypool, versioning, uid, secretkey)
            else:
                raise SOSError(SOSError.VALUE_ERR,
                               "Wrong API type " + apitype + " specified")

        except SOSError as e:
            raise e

    def keypool_create(self,
                       keypool,
                       projectname,
                       tenant,
                       vpoolname,
                       apitype,
                       uid,
                       secret):
        '''
        creates a keypool
        parameters:
            keypool:  	label of the keypool
            project:  	project name
            tenant:  	tenant  name
            vpool:  	vpool  name
            apitype:    api type(s3, swift or atmos)
            uid:	user id
            secret:	secret key
        Returns:
            JSON payload response
        '''
        errorcontext = 0

        if ((projectname) and (not common.is_uri(projectname))):
            from project import Project
            obj = Project(self.__ipAddr, self.__port)
            projectlst = obj.project_list(tenant)

            project_uri = None

            for projiter in projectlst:
                if(projiter['name'] == projectname):
                    project_uri = projiter['id']

            if(not project_uri):
                raise SOSError(SOSError.VALUE_ERR,
                               "Porject " + projectname + ": not found")

        if ((vpoolname) and (not common.is_uri(vpoolname))):
            from virtualpool import VirtualPool
            obj = VirtualPool(self.__ipAddr, self.__port)
            vpool = obj.vpool_show(vpoolname, 'object')
            vpool_uri = vpool['id']

        if (not common.is_uri(tenant)):
            from tenant import Tenant
            tenant_obj = Tenant(self.__ipAddr, self.__port)
            namespace = tenant_obj.namespace_get(tenant)

        try:
            if(apitype == 's3'):
                return s3_bucket_create(namespace,
                                        keypool,
                                        project_uri,
                                        vpool_uri,
                                        uid,
                                        secretkey)
            elif(apitype == 'swift'):
                return swift_container_create(namespace,
                                              keypool,
                                              project_uri,
                                              vpool_uri,
                                              uid,
                                              secretkey)
            elif(apitype == 'atmos'):
                return atmos_subtenant_create(namespace,
                                              tenant,
                                              keypool,
                                              project_uri,
                                              vpool_uri,
                                              uid,
                                              secretkey)
            else:
                raise SOSError(SOSError.VALUE_ERR,
                               "Wrong API type " + apitype + " specified")

        except SOSError as e:
            raise e

    def keypool_delete(self, keypool, tenant, uid, secret):
        '''Makes a REST API call to delete a keypool by its name,
        project and tenant
        '''

        if (not common.is_uri(tenant)):
            from tenant import Tenant
            tenant_obj = Tenant(self.__ipAddr, self.__port)
            namespace = tenant_obj.namespace_get(tenant)

        try:
            if(apitype == 's3'):
                return s3_bucket_delete(namespace, keypool, uid, secretkey)
            elif(apitype == 'swift'):
                return swift_container_delete(namespace,
                                              keypool,
                                              uid,
                                              secretkey)
            elif(apitype == 'atmos'):
                return atmos_subtenant_delete(namespace,
                                              keypool,
                                              uid,
                                              secretkey)
            else:
                raise SOSError(SOSError.VALUE_ERR,
                               "Wrong API type " + apitype + " specified")
        except SOSError as e:
            raise e

    def keypool_list(self, projectname, tenant, apitype, uid, secret):
        '''
        Returns all the keypools in a vdc
        Parameters:
            keypool:  label of the keypool
            project:  project name
            tenant:  tenant  name
            apitype:    api type(s3, swift or atmos)
            uid:	user id
            secret:	secret key
        Returns:
                JSON payload of keypool list
        '''

        if (not common.is_uri(tenant)):
            from tenant import Tenant
            tenant_obj = Tenant(self.__ipAddr, self.__port)
            namespace = tenant_obj.namespace_get(tenant)

        kplst = []

        try:
            if((not apitype) or (apitype == 's3')):
                kplst.append(
                    s3_bucket_list(
                        namespace,
                        projectname,
                        uid,
                        secretkey))
            if((not apitype) or (apitype == 'swift')):
                kplst.append(
                    swift_container_list(
                        namespace,
                        projectname,
                        uid,
                        secretkey))
            if((not apitype) or (apitype == 'atmos')):
                kplst.append(
                    atmos_subtenant_list(
                        namespace,
                        projectname,
                        uid,
                        secretkey))

            # need to convert to table format
            return kplst
        except SOSError as e:
            raise e

    def atmos_subtenant_create(
            namespace, tenant, keypool, project, vpool, uid, secretkey):
        if(vpool):
            from virtualpool import VirtualPool
            obj = VirtualPool(self.__ipAddr, self.__port)

            vpool_uri = obj.vpool_query(vpool, 'object')

            vpool_uri = vpool_uri.strip()

        if ((project) and (not common.is_uri(project))):
            from project import Project
            obj = Project(self.__ipAddr, self.__port)
            projectlst = obj.project_list(tenant)

            project_uri = None

            for projiter in projectlst:
                if(projiter['name'] == projectname):
                    project_uri = projiter['id']

            if(not project_uri):
                raise SOSError(SOSError.NOT_FOUND_ERR,
                               "Project " + project + ": not found")

        _headers = dict()
        _headers['x-emc-namespace'] = namespace
        _headers['x-emc-vpool'] = vpool_uri
        _headers['x-emc-project-id'] = project_uri

        (s, h) = common.service_json_request(self.__ipAddr,
                                             ATMOS_PORT,
                                             "PUT",
                                             self.URI_SWIFT_CONTAINER_INSTANCE.
                                             format(namespace, container),
                                             None,
                                             None,
                                             False,
                                             'application/json',
                                             None,
                                             _headers)

        if(h['subtenantID']):
            return h['subtenantID']

    def atmos_subtenant_delete(self, namespace, subtenant, uid, secret):
        _headers = dict()
        _headers['x-emc-namespace'] = namespace

        (s, h) = common.service_json_request(self.__ipAddr,
                                             ATMOS_PORT,
                                             "DELETE",
                                             URI_ATMOS_SUBTENANT_INSTANCE.
                                             format(subtenant),
                                             None,
                                             None,
                                             False,
                                             'application/json',
                                             None,
                                             _headers)

    def swift_container_create(
            container, namespace, tenant, project, vpool, uid, secret):

        if ((project) and (not common.is_uri(project))):
            from project import Project
            obj = Project(self.__ipAddr, self.__port)
            projectlst = obj.project_list(tenant)

            project_uri = None

            for projiter in projectlst:
                if(projiter['name'] == projectname):
                    project_uri = projiter['id']

            if(not project_uri):
                raise SOSError(SOSError.NOT_FOUND_ERR,
                               "Project " + project + ": not found")

            project_uri = project_uri.strip()
            _headers['x-emc-project-id'] = project_uri

        if(vpool):
            from virtualpool import VirtualPool
            obj = VirtualPool(self.__ipAddr, self.__port)

            vpool_uri = obj.vpool_query(vpool, 'object')

            vpool_uri = vpool_uri.strip()
            _headers['x-emc-vpool'] = vpool_uri

        token = self.swift_authenticate(uid, secret)

        _headers = dict()

        _headers[common.SWIFT_AUTH_TOKEN] = token

        (s, h) = common.service_json_request(self.__ipAddr,
                                             S3_PORT,
                                             "PUT",
                                             self.URI_SWIFT_CONTAINER_INSTANCE.
                                             format(namespace, container),
                                             None,
                                             None,
                                             False,
                                             'application/json',
                                             None,
                                             _headers)

        o = common.json_decode(s)

        # return o

    def swift_container_delete(self, namespace, container, uid, secret):

        token = self.swift_authenticate(uid, secret)

        _headers = dict()
        _headers[common.SWIFT_AUTH_TOKEN] = token

        (s, h) = common.service_json_request(self.__ipAddr,
                                             self.SWIFT_PORT,
                                             "DELETE",
                                             self.URI_SWIFT_CONTAINER_INSTANCE.
                                             format(namespace, container),
                                             None,
                                             None,
                                             False,
                                             'application/json',
                                             None,
                                             _headers)

    def swift_container_show(self, namespace, container, uid, secret):

        token = self.swift_authenticate(uid, secret)

        _headers = dict()
        _headers[common.SWIFT_AUTH_TOKEN] = token

        (s, h) = common.service_json_request(self.__ipAddr,
                                             self.SWIFT_PORT,
                                             "GET",
                                             self.URI_SWIFT_CONTAINER_INSTANCE.
                                             format(namespace, container),
                                             None,
                                             None,
                                             False,
                                             'application/json',
                                             None,
                                             _headers)

        o = common.json_decode(s)

        return o

    def swift_container_list(namespace, projectname, uid, secret):
        # use project name if the api supports it
        token = self.swift_authenticate(uid, secret)

        _headers = dict()
        _headers[common.SWIFT_AUTH_TOKEN] = token

        (s, h) = common.service_json_request(self.__ipAddr, SWIFT_PORT, "GET",
                                             URI_SWIFT_ACCOUNT_INSTANCE.format(
                                                 namespace), None, None, False,
                                             'application/json',
                                             None, _headers)

        o = common.json_decode(s)

        return o

    def swift_authenticate(self, uid, secret):
        qparms = {'X-Auth-User': uid, 'X-Auth-Key': secret}

        uri = '/auth/v1.0'

        if (qparms):
            uri += "?"
            first = True
            for qk in qparms.iterkeys():
                if (not first):
                    uri += '&'
                    uri += qk
                else:
                    first = False
                    uri += qk

                if (qparms[qk] is not None):
                    uri += '=' + qparms[qk]

        (s, h) = common.service_json_request(self.__ipAddr, SWIFT_PORT, "GET",
                                             URI_S3_BUCKET_INSTANCE.format(
                                                 keypool), None, None, False,
                                             'application/json', None, None)

        token = h.get(common.SWIFT_AUTH_TOKEN)

        if not token:
            raise Exception('authentication failed')

        return token

    def s3_bucket_create(self,
                         namespace,
                         bucket,
                         projecturi,
                         vpooluri,
                         uid,
                         secret):
        _headers = dict()
        _headers['x-emc-namespace'] = namespace
        if _headers.get("x-amz-date") is None:
            _headers['Date'] = formatdate()

        _headers = common.s3_hmac_base64_sig(
            'PUT',
            bucket,
            None,
            uid,
            secret,
            'application/json',
            _headers,
            None)

        (s, h) = common.service_json_request(self.__ipAddr, S3_PORT, "PUT",
                                             self.URI_S3_BUCKET_INSTANCE.
                                             format(bucket), None, None, False,
                                             'application/json',
                                             None, _headers)

        o = common.json_decode(s)

        return o

    def s3_bucket_update(self, namespace, bucket, versioning, uid, secret):
        headers = dict()

        # chekc this
        qparms = {'versioning': None}

        _headers['x-emc-namespace'] = namespace
        _headers['Date'] = formatdate()

        _headers = common.s3_hmac_base64_sig(
            'PUT',
            bucket,
            None,
            uid,
            secret,
            'application/json',
            _headers,
            qparms)

        root = ET.Element('VersioningConfiguration')
        root.set('xmlns', S3_XML_NS)
        ET.SubElement(root, 'Status').text = versioning
        parms = ET.tostring(root)

        uri = URI_S3_BUCKET_INSTANCE.format(bucket)

        body = None

        if(parms):
            body = cjson.encode(parms)

        if (qparms):
            uri += "?"
            first = True
            for qk in qparms.iterkeys():
                if (not first):
                    uri += '&'
                    uri += qk
                else:
                    first = False
                    uri += qk

                if (qparms[qk] is not None):
                    uri += '=' + qparms[qk]

        (s, h) = common.service_json_request(self.__ipAddr, S3_PORT, "PUT",
                                             uri, body, None, False,
                                             'application/json',
                                             None, _headers)

        o = common.json_decode(s)

        # return o

    def s3_bucket_show(namespace, bucket, uid, secret):
        _headers = dict()
        _headers['x-emc-namespace'] = namespace
        _headers['Date'] = formatdate()

        _headers = common.s3_hmac_base64_sig(
            'GET',
            bucket,
            None,
            uid,
            secret,
            'application/json',
            _headers,
            None)

        (s, h) = common.service_json_request(self.__ipAddr, S3_PORT, "GET",
                                             URI_S3_BUCKET_INSTANCE.format(
                                                 bucket), None, None, False,
                                             'application/json',
                                             None, _headers)

        o = common.json_decode(s)

        return o

    def s3_bucket_delete(namespace, bucket, uid, secret):
        _headers = dict()
        _headers['x-emc-namespace'] = namespace
        _headers['Date'] = formatdate()

        _headers = common.s3_hmac_base64_sig(
            'DELETE',
            bucket,
            None,
            uid,
            secret,
            'application/json',
            _headers,
            None)

        (s, h) = common.service_json_request(self.__ipAddr, S3_PORT, "DELETE",
                                             URI_S3_BUCKET_INSTANCE.format(
                                                 bucket), None, None, False,
                                             'application/json',
                                             None, _headers)

        o = common.json_decode(s)

        return o

    def s3_bucket_list(namespace, projectname, uid, secret):
        _headers = dict()
        _headers['x-emc-namespace'] = namespace
        _headers['Date'] = formatdate()

        # use projectname if the api supports it
        # check this below line
        _headers = common.s3_hmac_base64_sig(
            'GET',
            None,
            None,
            uid,
            secret,
            'application/json',
            _headers,
            None)

        (s, h) = common.service_json_request(self.__ipAddr, S3_PORT, "GET",
                                             self.URI_S3_SERVICE_BASE, None,
                                             None, False,
                                             'application/json',
                                             None, _headers)

        o = common.json_decode(s)

        return o


# Keypool create routines

def create_parser(subcommand_parsers, common_parser):
    # create command parser
    create_parser = subcommand_parsers.add_parser('create',
                                                  description='SOS Keypool' +
                                                  ' Create CLI usage.',
                                                  parents=[common_parser],
                                                  conflict_handler='resolve',
                                                  help='Create a keypool')

    mandatory_args = create_parser.add_argument_group('mandatory arguments')

    mandatory_args.add_argument('-name', '-n',
                                help='Name of keypool',
                                metavar='<keypoolname>',
                                dest='name',
                                required=True)

    create_parser.add_argument('-vpool',
                               help='Name of vpool',
                               metavar='<vpoolname>',
                               dest='vpool')

    create_parser.add_argument('-tenant', '-tn',
                               help='Name of tenant',
                               metavar='<tenantname>',
                               dest='tenant')

    mandatory_args.add_argument('-apitype',
                                help='API Type of the keypool',
                                metavar='<apitype>',
                                dest='apitype',
                                choices=['swift', 's3', 'atmos'])

    create_parser.add_argument('-project', '-pr',
                               help='Name of project',
                               metavar='<projectname>',
                               dest='project')

    mandatory_args.add_argument('-uid',
                                help='uid',
                                metavar='<uid>',
                                dest='uid',
                                required=True)

    mandatory_args.add_argument('-secret',
                                help='secret',
                                metavar='<secret>',
                                dest='secret',
                                required=True)

    create_parser.set_defaults(func=keypool_create)


def keypool_create(args):
    obj = Keypool(args.ip, args.port)
    try:
        res = obj.keypool_create(
            args.name,
            args.project,
            args.tenant,
            args.vpool,
            args.apitype,
            args.uid,
            args.secret)
    except SOSError as e:
        if (e.err_code in [SOSError.NOT_FOUND_ERR,
                           SOSError.ENTRY_ALREADY_EXISTS_ERR]):
            raise SOSError(e.err_code, "Keypool " +
                           args.name + ": Create failed\n" + e.err_text)
        else:
            raise e


# NEIGHBORHOOD Delete routines

def delete_parser(subcommand_parsers, common_parser):
    # delete command parser
    delete_parser = subcommand_parsers.add_parser('delete',
                                                  description='SOS Keypool' +
                                                  ' delete CLI usage.',
                                                  parents=[common_parser],
                                                  conflict_handler='resolve',
                                                  help='Delete a keypool')

    mandatory_args = delete_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='name of keypool',
                                dest='name',
                                metavar='keypoolname',
                                required=True)

    delete_parser.add_argument('-tenant', '-tn',
                               help='Name of tenant',
                               metavar='<tenantname>',
                               dest='tenant')

    mandatory_args.add_argument('-apitype',
                                help='API Type of the keypool',
                                metavar='<apitype>',
                                dest='apitype',
                                choices=['swift', 's3', 'atmos'],
                                required=True)

    mandatory_args.add_argument('-uid',
                                help='uid',
                                metavar='<uid>',
                                dest='uid',
                                required=True)

    mandatory_args.add_argument('-secret',
                                help='secret',
                                metavar='<secret>',
                                dest='secret',
                                required=True)

    delete_parser.set_defaults(func=keypool_delete)


def keypool_delete(args):
    obj = Keypool(args.ip, args.port)
    try:
        res = obj.keypool_delete(args.name, args.tenant, args.uid, args.secret)
    except SOSError as e:
        if(e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "Keypool delete failed: " + e.err_text)
        else:
            raise e

# NEIGHBORHOOD Show routines


def show_parser(subcommand_parsers, common_parser):
    # show command parser
    show_parser = subcommand_parsers.add_parser('show',
                                                description='SOS Keypool' +
                                                ' Show CLI usage.',
                                                parents=[common_parser],
                                                conflict_handler='resolve',
                                                help='Show a Keypool')

    mandatory_args = show_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='name of Keypool',
                                dest='name',
                                metavar='keypoolname',
                                required=True)

    show_parser.add_argument('-tenant', '-tn',
                             help='Name of tenant',
                             metavar='tenantname',
                             dest='tenant',
                             required=True)

    show_parser.add_argument('-apitype',
                             help='API Type of the keypool',
                             metavar='<apitype>',
                             dest='apitype',
                             choices=['swift', 's3', 'atmos'])

    mandatory_args.add_argument('-uid',
                                help='uid',
                                metavar='<uid>',
                                dest='uid',
                                required=True)

    mandatory_args.add_argument('-secret',
                                help='secret',
                                metavar='<secret>',
                                dest='secret',
                                required=True)

    show_parser.set_defaults(func=keypool_show)


def keypool_show(args):
    obj = Keypool(args.ip, args.port)
    try:
        res = obj.keypool_show(args.name, args.tenant, args.uid, args.secret)
        return common.format_json_object(res)
    except SOSError as e:
        if(e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "Keypool show failed: " + e.err_text)
        else:
            raise e


# KEYPOOL List routines

def list_parser(subcommand_parsers, common_parser):
    # list command parser
    list_parser = subcommand_parsers.add_parser('list',
                                                description='SOS Keypool' +
                                                ' List CLI usage.',
                                                parents=[common_parser],
                                                conflict_handler='resolve',
                                                help='List of keypools')

    list_parser.add_argument('-verbose', '-v',
                             action='store_true',
                             help='List Keypools with details',
                             dest='verbose')

    mandatory_args = list_parser.add_argument_group('mandatory arguments')
    list_parser.add_argument('-tenant',
                             help='name of Tenant',
                             dest='tenant',
                             metavar='tenantname')

    list_parser.add_argument('-project',
                             help='name of Project',
                             dest='project',
                             metavar='projectname')

    list_parser.add_argument('-apitype',
                             help='API Type of the keypool',
                             metavar='<apitype>',
                             dest='apitype',
                             choices=['swift', 's3', 'atmos'])

    mandatory_args.add_argument('-uid',
                                help='uid',
                                metavar='<uid>',
                                dest='uid',
                                required=True)

    mandatory_args.add_argument('-secret',
                                help='secret',
                                metavar='<secret>',
                                dest='secret',
                                required=True)

    list_parser.set_defaults(func=keypool_list)


def keypool_list(args):
    obj = Keypool(args.ip, args.port)
    try:
        lst = obj.keypool_list(
            args.project,
            args.tenant,
            args.apitype,
            args.uid,
            args.secret)

    except SOSError as e:
        if(e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "Keypool list failed: " + e.err_text)
        else:
            raise e


def update_parser(subcommand_parsers, common_parser):
    # show command parser
    update_parser = subcommand_parsers.add_parser('update',
                                                  description='SOS Keypool' +
                                                  ' update CLI usage.',
                                                  parents=[common_parser],
                                                  conflict_handler='resolve',
                                                  help='update of a Keypool')

    mandatory_args = update_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='name of Keypool',
                                dest='name',
                                metavar='<keypoolname>',
                                required=True)

    update_parser.add_argument('-tenant', '-tn',
                               help='Name of tenant',
                               metavar='tenantname',
                               dest='tenant')

    mandatory_args.add_argument('-versioning', '-vng',
                                help='Versioning',
                                metavar='<versioning>',
                                dest='versioning',
                                choices=['on', 'off'],
                                required=True)

    mandatory_args.add_argument('-apitype',
                                help='API Type of the keypool',
                                metavar='<apitype>',
                                dest='apitype',
                                choices=['swift', 's3', 'atmos'],
                                required=True)

    mandatory_args.add_argument('-uid',
                                help='uid',
                                metavar='<uid>',
                                dest='uid',
                                required=True)

    mandatory_args.add_argument('-secret',
                                help='secret',
                                metavar='<secret>',
                                dest='secret',
                                required=True)

    update_parser.set_defaults(func=keypool_update)


def keypool_update(args):
    obj = Keypool(args.ip, args.port)
    try:
        res = obj.keypool_update(
            args.name,
            args.tenant,
            args.versioning,
            args.apitype,
            args.uid,
            args.secret)
    except SOSError as e:
        if (e.err_code in [SOSError.NOT_FOUND_ERR]):
            raise SOSError(e.err_code, "Keypool " +
                           args.name + ": update failed\n" + e.err_text)
        else:
            raise e
#
# Keypool Main parser routine
#


def keypool_parser(parent_subparser, common_parser):
    # main keypool parser
    parser = parent_subparser.add_parser('keypool',
                                         description='SOS Keypool CLI usage',
                                         parents=[common_parser],
                                         conflict_handler='resolve',
                                         help='Operations on Keypool')
    subcommand_parsers = parser.add_subparsers(help='Use One Of Commands')

    # create command parser
    create_parser(subcommand_parsers, common_parser)

    # delete command parser
    delete_parser(subcommand_parsers, common_parser)

    # show command parser
    show_parser(subcommand_parsers, common_parser)

    # list command parser
    list_parser(subcommand_parsers, common_parser)

    # update command parser
    update_parser(subcommand_parsers, common_parser)
