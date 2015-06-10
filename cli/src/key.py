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
import string
import hashlib

from common import SOSError

class Key(object):
    '''
    The class definition for operations on 'Key'. 
    '''

    #Commonly used URIs for the 'key' module

    URI_SERVICES_BASE               = '' 

    URI_S3_SERVICE_BASE             = ''
    URI_S3_BUCKET_INSTANCE          = URI_S3_SERVICE_BASE + '/{0}'
    URI_S3_KEY_INSTANCE             = URI_S3_SERVICE_BASE + '/{0}/{1}'


    URI_SWIFT_SERVICE_BASE          = '/v1'
    URI_SWIFT_ACCOUNT_INSTANCE      = URI_SWIFT_SERVICE_BASE + '/{0}'
    URI_SWIFT_CONTAINER_INSTANCE    = URI_SWIFT_SERVICE_BASE + '/{0}/{1}'
    URI_SWIFT_KEY_INSTANCE          = URI_SWIFT_SERVICE_BASE + '/{0}/{1}/{2}'


    FILE_READ_BUFFER_SIZE	    = 20

    S3_PORT                         = '4080'
    DATA_PORT                       = '8080'
    ATMOS_PORT                      = '8082'
    SWIFT_PORT                      = '5080'

    
    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the SOS instance. These are
        needed to make http requests for REST API   
        '''
        self.__ipAddr = ipAddr
        self.__port = port
        

    def _computeMD5(self, value):
        m = hashlib.md5()
        if value != None:
            m.update(value)
        return m.hexdigest()



    def key_read(self, key, keypool, tenant, apitype, version, filepath , uid, secret, type ):
        '''
        Makes a REST API call to retrieve details of a key  based on its key, keypool, apitype,uid, secret
        '''

	if (not common.is_uri(tenant)):
            from tenant import Tenant
            tenant_obj = Tenant(self.__ipAddr, self.__port)
            namespace =  tenant_obj.namespace_get(tenant)

	if(apitype == 's3'):
            return s3_key_read(namespace, keypool, key, version, filepath, uid, secret )
        elif(apitype == 'swift'):
            return swift_object_read(namespace, keypool, key, version, filepath, uid, secret )
        elif(apitype == 'atmos'):
            return atmos_object_read(namespace, keypool, key, version, filepath, uid, secret, type )
        else:
            raise SOSError(SOSError.VALUE_ERR,
                  "Wrong API type " + apitype + " specified")
        
    
       
    def key_write(self, key, filepath , keypool, tenant, apitype,  uid, secret, type):
        '''
        write a key
        parameters:    
            key:  	key
            keypool:  	keypool
            tenant:   	tenant  name
            value:   	value
	    apitype:    api to be used
	    uid:	user id
	    secret:	secret
        Returns:
            JSON payload response
        '''

	if (not common.is_uri(tenant)):
            from tenant import Tenant
            tenant_obj = Tenant(self.__ipAddr, self.__port)
            namespace =  tenant_obj.namespace_get(tenant)

	if(apitype == 's3'):
            s3_key_write(namespace, keypool, key, filepath, uid, secret )
        elif(apitype == 'swift'):
            swift_object_write(namespace, keypool, key, filepath, uid, secret )
        elif(apitype == 'atmos'):
            atmos_object_write(namespace, keypool, key, filepath, uid, secret, type )
        else:
            raise SOSError(SOSError.VALUE_ERR,
                  "Wrong API type " + apitype + " specified")




    def key_list_versions(self, keypool, key, tenant, apitype,  uid, secret):
        '''
        Returns versions of the key`
        Parameters:           
            key:  label of the key
            keypool:  label of the keypool
            project:  project name
            tenant:  tenant  name
	    apitype:    api to be used
	    uid:	user id
	    secret:	secret
        Returns:
                JSON payload of key list
        '''

	if (not common.is_uri(tenant)):
            from tenant import Tenant
            tenant_obj = Tenant(self.__ipAddr, self.__port)
            namespace =  tenant_obj.namespace_get(tenant)

	if(apitype == 's3'):
            s3_key_list_versions(namespace, keypool, key, uid, secret )
	elif ( (apitype =='swift') or (apitype=='atmos')) :
            raise SOSError(SOSError.NOT_FOUND_ERR,
                  "Versioning not available with  API type " + apitype)
        else:
            raise SOSError(SOSError.VALUE_ERR,
                  "Wroing API type " + apitype + " specified")

        

    def key_list(self, keypool, tenant, apitype,  uid, secret, tag):
        '''
        Returns versions of the key`
        Parameters:           
            key:  label of the key
            keypool:  label of the keypool
            project:  project name
            tenant:  tenant  name
	    apitype:    api to be used
	    uid:	user id
	    secret:	secret
        Returns:
                JSON payload of key list
        '''

	if (not common.is_uri(tenant)):
            from tenant import Tenant
            tenant_obj = Tenant(self.__ipAddr, self.__port)
            namespace =  tenant_obj.namespace_get(tenant)

	if(apitype == 's3'):
            s3_key_list(namespace, keypool, uid, secret )
        elif(apitype == 'swift'):
            swift_object_list(namespace, keypool, uid, secret )
        elif(apitype == 'atmos'):
            return atmos_object_list(namespace, keypool, uid, secret, tag )
        else:
            raise SOSError(SOSError.VALUE_ERR,
                  "Wrong API type " + apitype + " specified")
            


    def key_delete(self, key, keypool, tenant, apitype, version, uid, secret):
        '''
        Makes a REST API call to delete a key by its name
        Parameters:           
            key:  label of the key
            keypool:  label of the keypool
            project:  project name
            tenant:  tenant  name
	    apitype:    api to be used
	    uid:	user id
	    secret:	secret
        Returns:
                JSON payload of key list
        '''
	if (not common.is_uri(tenant)):
            from tenant import Tenant
            tenant_obj = Tenant(self.__ipAddr, self.__port)
            namespace =  tenant_obj.namespace_get(tenant)

	if(apitype == 's3'):
            s3_key_delete(namespace, keypool, key, version, uid, secret )
        elif(apitype == 'swift'):
            swift_object_delete(namespace, keypool, key, version, uid, secret )
        elif(apitype == 'atmos'):
            atmos_object_delete(namespace, keypool, key, uid, secret )
        else:
            raise SOSError(SOSError.VALUE_ERR,
                  "Wrong API type " + apitype + " specified")




    def swift_object_write(namespace, container, key, filepath, uid, secret ):
        '''
        Makes a REST API call to write a swift object
        Parameters:           
            key:  label of the key
            container:  label of the keypool
	    apitype:    api to be used
	    uid:	user id
	    secret:	secret
        Returns:
                JSON payload of key list
        '''
	token = self.swift_authenticate(uid, secret)
	
	_headers = dict()
	_headers[common.SWIFT_AUTH_TOKEN] = token

        (s, h) = common.service_json_request(self.__ipAddr, SWIFT_PORT , "PUT",
                                             URI_SWIFT_KEY_INSTANCE.format(namespace, container, key) , None , None, False ,
                                             'application/octet-stream', filepath, _headers, 'swift')
	

    def swift_object_delete(namespace, container, key, version, uid, secret ):
        '''
        Makes a REST API call to delete a swift object
        Parameters:           
            key:  label of the key
            container:  label of the container
	    apitype:    api to be used
	    uid:	user id
	    secret:	secret
        Returns:
                JSON payload of key list
        '''
	token = self.swift_authenticate(uid, secret)
	
	_headers = dict()
	_headers[common.SWIFT_AUTH_TOKEN] = token

        (s, h) = common.service_json_request(self.__ipAddr, SWIFT_PORT , "DELETE",
                                             URI_SWIFT_KEY_INSTANCE.format(namespace, container, key) , None , None, False ,
                                             'application/octet-stream', None, _headers, 'swift')

    def swift_object_read(namespace, container, key, version, filepath, uid, secret ):
        '''
        Makes a REST API call to show a swift object
        Parameters:           
            key:  label of the key
            container:  label of the container
	    uid:	user id
	    secret:	secret
 	    namespace:	namespace
        Returns:
            JSON payload of key list
        '''
	token = self.swift_authenticate(uid, secret)
	
	_headers = dict()
	_headers[common.SWIFT_AUTH_TOKEN] = token

        (s, h) = common.service_json_request(self.__ipAddr, SWIFT_PORT , "GET",
                                             URI_SWIFT_KEY_INSTANCE.format(namespace, container, key) , None , None, False ,
                                             'application/octet-stream', filepath, _headers, 'swift')


    def swift_object_list(namespace, container, uid, secret ):
        
        '''Makes a REST API call to show a swift object
        Parameters:           
            key:  label of the key
            container:  label of the container
	    uid:	user id
	    secret:	secret
 	    namespace:	namespace
        Returns:
            JSON payload of key list'''
        
	token = self.swift_authenticate(uid, secret)
	
	_headers = dict()
	_headers[common.SWIFT_AUTH_TOKEN] = token

        (s, h) = common.service_json_request(self.__ipAddr, SWIFT_PORT , "GET",
                                             URI_SWIFT_KEY_INSTANCE.format(namespace, container) , None , None, False ,
                                             'application/octet-stream', None, _headers, 'swift')


    def s3_key_write(namespace, bucket, key, filepath, uid, secret):
	'''
        Makes a REST API call to write an S3 key value pair
        Parameters:           
            key:  label of the key
            bucket:  label of the bucket
	    uid:	user id
	    secret:	secret
 	    namespace:	namespace
        Returns:
            JSON payload of key list
	'''
	_headers = dict()
        _headers['x-emc-namespace'] = namespace
        _headers['Date'] = formatdate()

	_headers = self.s3_hmac_base64_sig('PUT', bucket, None , uid, secret , 'application/xml' , _headers, None )

        (s, h) = common.service_json_request(self.__ipAddr, S3_PORT , "PUT",
                                             URI_S3_KEY_INSTANCE.format(bucket, key) , None , None, False ,
                                             'application/octet-stream', filepath, _headers, 's3')
        o = common.json_decode(s)

        #return o



    def s3_key_delete(namespace, bucket, key, version, uid, secret ):
	'''
        Makes a REST API call to delete a S3 key value pair
        Parameters:           
            key:  label of the key
            bucket:  label of the bucket
	    uid:	user id
	    secret:	secret
 	    namespace:	namespace
        Returns:
            JSON payload of key list
	'''
	_headers = dict()
        _headers['x-emc-namespace'] = namespace
        _headers['Date'] = formatdate()

	qparms = None
        if version:
            qparms = {'versionId': version}

	_headers = self.s3_hmac_base64_sig('DELETE', bucket, None , uid, secret , 'application/json' , _headers, qparms )

	
	uri = URI_S3_KEY_INSTANCE.format(bucket, key)

	if (qparms):
            for qk in qparms.iterkeys():
                if (qparms[qk] != None):
                    uri += '&' if ('?' in uri) else '?'
                    uri += qk + '=' + qparms[qk]

        (s, h) = common.service_json_request(self.__ipAddr, S3_PORT , "DELETE",
                                       	     uri , None , None , False ,
                                             None, None, _headersi, 's3')

	#return str(s) + " ++ " + str(h)
        

    def s3_key_read(namespace, bucket, key, version, filepath, uid, secret ):
	'''
        Makes a REST API call to show an S3 key value pair
        Parameters:           
            key:  label of the key
            bucket:  label of the bucket
	    uid:	user id
	    secret:	secret
 	    namespace:	namespace
        Returns:
            JSON payload of key list
	'''
	_headers = dict()
        _headers['x-emc-namespace'] = namespace
        _headers['Date'] = formatdate()

	qparms = None
        if version is not None:
            qparms = {'versionId': version}

	_headers = self.s3_hmac_base64_sig('GET', bucket, None , uid, secret , 'application/json' , _headers, qparms )
	
	uri = URI_S3_KEY_INSTANCE.format(bucket, key)

	if (qparms):
            for qk in qparms.iterkeys():
                if (qparms[qk] != None):
                    uri += '&' if ('?' in uri) else '?'
                    uri += qk + '=' + qparms[qk]

        (s, h) = common.service_json_request(self.__ipAddr, S3_PORT , "GET",
                                             uri , None , None, False ,
                                             'application/octet-stream', filepath , _headers, 's3')

   	#return s
	    



    def s3_key_list_versions(namespace, bucket, key, uid, secret ):
	'''
        Makes a REST API call to list versions of a S3 key value pair
        Parameters:           
            key:  label of the key
            bucket:  label of the bucket
	    uid:	user id
	    secret:	secret
 	    namespace:	namespace
        Returns:
            JSON payload of key list
	'''
	_headers = dict()
        _headers['x-emc-namespace'] = namespace
        _headers['Date'] = formatdate()

	qparms = {'versions': None}

	_headers = common.s3_hmac_base64_sig('GET', bucket, None , uid, secret , 'application/json' , _headers, qparms )
	
	uri = URI_S3_KEY_INSTANCE.format(bucket, key)

	if (qparms):
            for qk in qparms.iterkeys():
                if (qparms[qk] != None):
                    uri += '&' if ('?' in uri) else '?'
                    uri += qk + '=' + qparms[qk]

        (s, h) = common.service_json_request(self.__ipAddr, S3_PORT , "GET",
                                             uri , None , None, False ,
                                             'application/octet-stream', None, _headers), 's3'

	o = json_decode(s)

	return o




    def s3_key_list(namespace, bucket, uid, secret ):
	_headers = dict()
        _headers['x-emc-namespace'] = namespace
        _headers['Date'] = formatdate()

	_headers = common.s3_hmac_base64_sig('GET', bucket, None , uid, secret , 'application/json' , _headers, qparms )
	
	uri = URI_S3_BUCKET_INSTANCE.format(bucket)

        (s, h) = common.service_json_request(self.__ipAddr, S3_PORT , "GET",
                                             uri , None , None, False ,
                                             'application/json', None, _headers, 's3')

	o = json_decode(s)

	return o



    def atmos_object_write(namespace, keypool, key, filepath, uid, secret, type ):
	_headers = dict()
	_headers['x-emc-namespace'] = namespace
        _headers['date'] = formatdate()
        _headers['x-emc-uid'] = subtenant + '/' + uid
        _headers['x-emc-signature'] = self.signature()
        _headers['x-emc-meta'] = 'color=red,city=seattle'
        _headers['x-emc-listable-meta'] = 'country=usa'

        if (key != ''):
            uri = URI_ATMOS_NAMESPACE_PATH.format(key)
        else :
            uri = URI_ATMOS_OBJECTS

        (s, h) = common.service_json_request(self.__ipAddr, ATMOS_PORT , "POST",
                                             uri , None , None, False ,
                                             'application/octet-stream', filepath, _headers, 'atmos')

	

    def atmos_object_delete(namespace, keypool, key, filepath, uid, secret, type ):
	_headers = dict()
	_headers['x-emc-namespace'] = namespace
        _headers['date'] = formatdate()
        _headers['x-emc-uid'] = subtenant + '/' + uid
        _headers['x-emc-signature'] = self.signature()
        if (type == 'N'):
            uri = URI_ATMOS_NAMESPACE_PATH.format(key)
        else :
            uri = URI_ATMOS_OBJECTS_OID.format(key)

        (s, h) = common.service_json_request(self.__ipAddr, ATMOS_PORT , "DELETE",
                                             uri , None , None, False ,
                                             'application/octet-stream', filepath, _headers, 'atmos')


    def atmos_object_read(namespace, keypool, key, version, filepath, uid, secret, type ):
	_headers = dict()
	_headers['x-emc-namespace'] = namespace
        _headers['date'] = formatdate()
        _headers['x-emc-uid'] = subtenant + '/' + uid
        _headers['x-emc-signature'] = self.signature()

	if (type == 'N'):
            uri = URI_ATMOS_NAMESPACE_PATH.format(key)
        else :
            uri = URI_ATMOS_OBJECTS_OID.format(key)


        (s, h) = common.service_json_request(self.__ipAddr, ATMOS_PORT , "GET",
                                             uri , None , None, False ,
                                             '*/*', filepath, _headers, 'atmos')

	

    def atmos_object_list(namespace, keypool, tag, version, filepath, uid, secret, type ):
	_headers = dict()

	_headers['x-emc-namespace'] = namespace
        _headers['date'] = formatdate()
        _headers['x-emc-uid'] = subtenant + '/' + uid
        _headers['x-emc-signature'] = self.signature()
        if (type == 'N'):
            uri = URI_ATMOS_NAMESPACE
        else :
            uri = URI_ATMOS_OBJECTS

	qparms = {'listabletags':tag}

        if (qparms):
            for qk in qparms.iterkeys():
                if (qparms[qk] != None):
                    uri += '&' if ('?' in uri) else '?'
                    uri += qk + '=' + qparms[qk]

        (s, h) = common.service_json_request(self.__ipAddr, ATMOS_PORT , "GET",
                                             uri , None , None, False ,
                                             '*/*', None, _headers, 'atmos')



# NEIGHBORHOOD Create routines

def write_parser(subcommand_parsers, common_parser):
    # write command parser
    write_parser = subcommand_parsers.add_parser('write',
                                description='SOS Key Write CLI usage.',
                                parents=[common_parser],
                                conflict_handler='resolve',
                                help='Write a key')

    mandatory_args = write_parser.add_argument_group('mandatory arguments')

    mandatory_args.add_argument('-key', '-k',
                                help='Name of key',
                                metavar='key',
                                dest='key',
                                required=True)

    mandatory_args.add_argument('-keypool', '-kp',
                                help='Name of keypool',
                                metavar='keypool',
                                dest='keypool',
                                required=True)

    write_parser.add_argument('-tenant', '-tn',
                                help='Name of tenant',
                                metavar='tenantname',
                                dest='tenant')

    mandatory_args.add_argument('-apitype', '-at',
                                help='API Type of the keypool',
                                metavar='<apitype>',
                                dest='apitype',
                                required=True)

    mandatory_args.add_argument('-filepath', '-fp',
                                help='filepath',
                                metavar='<filepath>',
                                dest='filepath',
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

    write_parser.add_argument('-project', 
                                help='project',
                                metavar='<project>',
                                dest='project',
                                required=True)

    write_parser.set_defaults(func=key_write)

def key_write(args):
    obj = Key(args.ip, args.port)
    try:
        res = obj.key_write(args.key , args.filepath , args.keypool, args.tenant, args.apitype , args.uid , args.secret)
    except SOSError as e:
        if (e.err_code in [SOSError.NOT_FOUND_ERR, 
                           SOSError.ENTRY_ALREADY_EXISTS_ERR]):
            raise SOSError(e.err_code, "Key " + 
                           args.key + ": Create failed\n" + e.err_text)
        else:
            raise e


# NEIGHBORHOOD Delete routines

def delete_parser(subcommand_parsers, common_parser):
    # delete command parser
    delete_parser = subcommand_parsers.add_parser('delete',
                                description='SOS Key delete CLI usage.',
                                parents=[common_parser],
                                conflict_handler='resolve',
                                help='Delete a key')

    mandatory_args = delete_parser.add_argument_group('mandatory arguments')

    mandatory_args.add_argument('-key', '-k',
                                help='Name of key',
                                metavar='key',
                                dest='key',
                                required=True)

    mandatory_args.add_argument('-keypool', '-kp',
                                help='Name of keypool',
                                metavar='keypool',
                                dest='keypool',
                                required=True)

    delete_parser.add_argument('-tenant', '-tn',
                                help='Name of tenant',
                                metavar='tenantname',
                                dest='tenant')

    mandatory_args.add_argument('-apitype', '-at',
                                help='API Type of the keypool',
                                metavar='<apitype>',
                                dest='apitype',
                                required=True)

    delete_parser.add_argument('-version', '-vr',
                                help='Version of the key',
                                metavar='<version>',
                                dest='version')

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

    delete_parser.set_defaults(func=key_delete)

def key_delete(args):
    obj = Key(args.ip, args.port)
    try:
        res = obj.key_delete(args.key, args.keypool, args.tenant, args.apitype, args.version, args.uid, args.secret)
    except SOSError as e:
        if(e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(SOSError.NOT_FOUND_ERR, 
                           "Key delete failed: " + e.err_text)
        else:
            raise e

# NEIGHBORHOOD Show routines

def read_parser(subcommand_parsers, common_parser):
    # show command parser
    read_parser = subcommand_parsers.add_parser('read',
                                description='SOS Key read CLI usage.',
                                parents=[common_parser],
                                conflict_handler='resolve',
                                help='Show a Key')

    mandatory_args = read_parser.add_argument_group('mandatory arguments')

    mandatory_args.add_argument('-key', '-k',
                                help='Name of key',
                                metavar='key',
                                dest='key',
                                required=True)

    mandatory_args.add_argument('-keypool', '-kp',
                                help='Name of keypool',
                                metavar='keypool',
                                dest='keypool',
                                required=True)


    mandatory_args.add_argument('-apitype', '-at',
                                help='API Type of the keypool',
                                metavar='<apitype>',
                                dest='apitype',
                                required=True)

    read_parser.add_argument('-tenant', '-tn',
                                help='Name of tenant',
                                metavar='tenantname',
                                dest='tenant')

    read_parser.add_argument('-version', '-vr',
                                help='Version of the key',
                                metavar='<version>',
                                dest='version')

    mandatory_args.add_argument('-filepath', '-fp',
                                help='file path to retrive the contents of key',
                                metavar='<filepath>',
                                dest='filepath',
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

    read_parser.set_defaults(func=key_read)

def key_read(args):
    obj = Key(args.ip, args.port)
    try:
        res = obj.key_read(args.key , args.keypool, args.tenant, args.apitype, args.version, args.filepath , args.uid, args.secret)
        return common.format_json_object(res)
    except SOSError as e:
        if(e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(SOSError.NOT_FOUND_ERR, 
                           "Key show failed: " + e.err_text)
        else:
            raise e


# KEYPOOL List routines

def list_versions_parser(subcommand_parsers, common_parser):
    # list command parser
    list_versions_parser = subcommand_parsers.add_parser('list-versions',
                                                description='SOS Key List Versions CLI usage.',
                                                parents=[common_parser],
                                                conflict_handler='resolve',
                                                help='List of versions for a key')
 
    mandatory_args = list_versions_parser.add_argument_group('mandatory arguments')


    mandatory_args.add_argument('-keypool', '-kp',
                                help='Name of keypool',
                                metavar='keypool',
                                dest='keypool',
                                required=True)

    mandatory_args.add_argument('-key', 
                                help='Name of key',
                                metavar='key',
                                dest='key',
                                required=True)

    mandatory_args.add_argument('-apitype', '-at',
                                help='API Type of the keypool',
                                metavar='<apitype>',
                                dest='apitype',
                                required=True)

    list_versions_parser.add_argument('-tenant', '-tn',
                                help='Name of tenant',
                                metavar='tenantname',
                                dest='tenant')

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

    mandatory_args = list_versions_parser.add_argument_group('mandatory arguments')

    list_versions_parser.set_defaults(func=key_list_versions)

def key_list_versions(args):
    obj = Key(args.ip, args.port)
    try:
        lst = obj.key_list_versions(args.keypool ,args.key, args.tenant, args.apitype, args.uid, args.secret)

    except SOSError as e:
        if(e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(SOSError.NOT_FOUND_ERR, 
                           "Key list failed: " + e.err_text)
	else:
            raise e


def list_parser(subcommand_parsers, common_parser):
    # list command parser
    list_parser = subcommand_parsers.add_parser('list',
                                                description='SOS Key List CLI usage.',
                                                parents=[common_parser],
                                                conflict_handler='resolve',
                                                help='List of keys')
 
    mandatory_args = list_parser.add_argument_group('mandatory arguments')


    mandatory_args.add_argument('-keypool', '-kp',
                                help='Name of keypool',
                                metavar='keypool',
                                dest='keypool',
                                required=True)


    mandatory_args.add_argument('-apitype', '-at',
                                help='API Type of the keypool',
                                metavar='<apitype>',
                                dest='apitype',
                                required=True)

    list_parser.add_argument('-tenant', '-tn',
                                help='Name of tenant',
                                metavar='tenantname',
                                dest='tenant')

    list_parser.add_argument('-tag', '-tg',
                                help='Name of tag',
                                metavar='tag',
                                dest='tag')

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

    mandatory_args = list_parser.add_argument_group('mandatory arguments')

    list_parser.set_defaults(func=key_list)

def key_list(args):
    obj = Key(args.ip, args.port)
    try:
        lst = obj.key_list(args.keypool , args.tenant, args.apitype , args.uid ,args.secret, args.tag)

    except SOSError as e:
        if(e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(SOSError.NOT_FOUND_ERR, 
                           "Key list failed: " + e.err_text)
	else:
            raise e


#
# Key Main parser routine
#

def key_parser(parent_subparser, common_parser):
    # main key parser
    parser = parent_subparser.add_parser('key',
                                        description='SOS Key CLI usage',
                                        parents=[common_parser],
                                        conflict_handler='resolve',
                                        help='Operations on Key')
    subcommand_parsers = parser.add_subparsers(help='Use One Of Commands')

    # write command parser
    write_parser(subcommand_parsers, common_parser)

    # delete command parser
    delete_parser(subcommand_parsers, common_parser)

    # show command parser
    read_parser(subcommand_parsers, common_parser)

    # list command parser
    list_versions_parser(subcommand_parsers, common_parser)

    # list command parser
    list_parser(subcommand_parsers, common_parser)

    # write object command parser
    #write_object_parser(subcommand_parsers, common_parser)

    # read object command parser
    #read_object_parser(subcommand_parsers, common_parser)
