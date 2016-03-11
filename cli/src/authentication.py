#!/usr/bin/python

# Copyright 2012 EMC Corporation
# Copyright 2016 Intel Corporation
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.


import os
import sys
import requests
import cookielib
import common
import getpass
from common import SOSError
from requests.exceptions import SSLError
from requests.exceptions import ConnectionError
from requests.exceptions import TooManyRedirects
from requests.exceptions import Timeout
import socket
import json
import ConfigParser


class Authentication(object):

    '''
    The class definition for authenticating the specified user
    '''

    # Commonly used URIs for the 'Authentication' module
    URI_SERVICES_BASE = ''
    URI_AUTHENTICATION = '/login'
    URI_VDC_AUTHN_PROFILE = URI_SERVICES_BASE + '/vdc/admin/authnproviders'
    URI_VDC_AUTHN_PROFILES = (URI_SERVICES_BASE +
                              '/vdc/admin/authnproviders/{0}')
    URI_VDC_AUTHN_PROFILES_FORCE_UPDATE = (URI_SERVICES_BASE +
                              '/vdc/admin/authnproviders/{0}{1}')
    URI_VDC_ROLES = URI_SERVICES_BASE + '/vdc/role-assignments'

    URI_LOGOUT = URI_SERVICES_BASE + '/logout'

    URI_USER_GROUP = URI_SERVICES_BASE + '/vdc/admin/user-groups'
    URI_USER_GROUP_ID = URI_USER_GROUP + '/{0}'

    HEADERS = {'Content-Type': 'application/json',
               'ACCEPT': 'application/json', 'X-EMC-REST-CLIENT': 'TRUE'}
    SEARCH_SCOPE = ['ONELEVEL', 'SUBTREE']
    BOOL_VALS = ['true', 'false']
    ZONE_ROLES = ['SYSTEM_ADMIN', 'SECURITY_ADMIN', 'SYSTEM_MONITOR',
                  'SYSTEM_AUDITOR']
    MODES = ['ad', 'ldap', 'keystone']

    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the ViPR instance.
        These are needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port

    def authenticate_user(self, username, password, cookiedir, cookiefile):
        '''
        Makes REST API call to generate the cookiefile for the
        specified user after validation.
        Returns:
            SUCCESS OR FAILURE
        '''
        SEC_REDIRECT = 302
        SEC_AUTHTOKEN_HEADER = 'X-SDS-AUTH-TOKEN'
        LB_API_PORT = 4443
        # Port on which load-balancer/reverse-proxy listens to all incoming
        # requests for ViPR REST APIs
        APISVC_PORT = 8443  # Port on which apisvc listens to incoming requests

        cookiejar = cookielib.LWPCookieJar()

        url = ('https://' + str(self.__ipAddr) + ':' + str(self.__port) +
               self.URI_AUTHENTICATION)

        try:
            if(self.__port == APISVC_PORT):
                login_response = requests.get(
                    url, headers=self.HEADERS, verify=False,
                    auth=(username, password), cookies=cookiejar,
                    allow_redirects=False, timeout=common.TIMEOUT_SEC)
                if(login_response.status_code == SEC_REDIRECT):
                    location = login_response.headers['Location']
                    if(not location):
                        raise SOSError(
                            SOSError.HTTP_ERR, "The redirect location of " +
                            "the authentication service is not provided")
                    # Make the second request
                    login_response = requests.get(
                        location, headers=self.HEADERS, verify=False,
                        cookies=cookiejar, allow_redirects=False,
                        timeout=common.TIMEOUT_SEC)
                    if(not (login_response.status_code ==
                            requests.codes['unauthorized'])):
                        raise SOSError(
                            SOSError.HTTP_ERR, "The authentication service" +
                            " failed to reply with 401")

                    # Now provide the credentials
                    login_response = requests.get(
                        location, headers=self.HEADERS,
                        auth=(username, password), verify=False,
                        cookies=cookiejar, allow_redirects=False,
                        timeout=common.TIMEOUT_SEC)
                    if(not login_response.status_code == SEC_REDIRECT):
                        raise SOSError(
                            SOSError.HTTP_ERR,
                            "Access forbidden: Authentication required")
                    location = login_response.headers['Location']
                    if(not location):
                        raise SOSError(
                            SOSError.HTTP_ERR, "The authentication service" +
                            " failed to provide the location of the service" +
                            " URI when redirecting back")
                    authToken = login_response.headers[SEC_AUTHTOKEN_HEADER]
                    if (not authToken):
                        details_str = self.extract_error_detail(login_response)
                        raise SOSError(SOSError.HTTP_ERR,
                                       "The token is not generated" +
                                       " by authentication service." + details_str)
                    # Make the final call to get the page with the token
                    newHeaders = self.HEADERS
                    newHeaders[SEC_AUTHTOKEN_HEADER] = authToken
                    login_response = requests.get(
                        location, headers=newHeaders, verify=False,
                        cookies=cookiejar, allow_redirects=False,
                        timeout=common.TIMEOUT_SEC)
                    if(login_response.status_code != requests.codes['ok']):
                        raise SOSError(
                            SOSError.HTTP_ERR, "Login failure code: " +
                            str(login_response.status_code) + " Error: " +
                            login_response.text)
            elif(self.__port == LB_API_PORT):
                login_response = requests.get(
                    url, headers=self.HEADERS, verify=False,
                    cookies=cookiejar, allow_redirects=False)
                if(login_response.status_code ==
                   requests.codes['unauthorized']):
                    # Now provide the credentials
                    login_response = requests.get(
                        url, headers=self.HEADERS, auth=(username, password),
                        verify=False, cookies=cookiejar, allow_redirects=False)
                authToken = None
                if(SEC_AUTHTOKEN_HEADER in login_response.headers):
                    authToken = login_response.headers[SEC_AUTHTOKEN_HEADER]
            else:
                raise SOSError(
                    SOSError.HTTP_ERR,
                    "Incorrect port number.  Load balanced port is: " +
                    str(LB_API_PORT) + ", api service port is: " +
                    str(APISVC_PORT) + ".")

            if (not authToken):
                details_str = self.extract_error_detail(login_response)
                raise SOSError(
                    SOSError.HTTP_ERR,
                    "The token is not generated by authentication service."+details_str)

            if (login_response.status_code != requests.codes['ok']):
                error_msg = None
                if(login_response.status_code == 401):
                    error_msg = "Access forbidden: Authentication required"
                elif(login_response.status_code == 403):
                    error_msg = ("Access forbidden: You don't have" +
                                 " sufficient privileges to perform" +
                                 " this operation")
                elif(login_response.status_code == 500):
                    error_msg = "Bourne internal server error"
                elif(login_response.status_code == 404):
                    error_msg = "Requested resource is currently unavailable"
                elif(login_response.status_code == 405):
                    error_msg = ("GET method is not supported by resource: " +
                                 url)
                elif(login_response.status_code == 503):
                    error_msg = ("Service temporarily unavailable:" +
                                 " The server is temporarily unable" +
                                 " to service your request")
                else:
                    error_msg = login_response.text
                    if isinstance(error_msg, unicode):
                        error_msg = error_msg.encode('utf-8')
                raise SOSError(SOSError.HTTP_ERR, "HTTP code: " +
                               str(login_response.status_code) +
                               ", response: " + str(login_response.reason) +
                               " [" + str(error_msg) + "]")

        except (SSLError, socket.error, ConnectionError, Timeout) as e:
            raise SOSError(SOSError.HTTP_ERR, str(e))

        form_cookiefile = None
        parentshellpid = None
        installdir_cookie = None
        if sys.platform.startswith('linux'):
            parentshellpid = os.getppid()
            if(cookiefile is None):
                if (parentshellpid is not None):
                    cookiefile = str(username) + 'cookie' + str(parentshellpid)
                else:
                    cookiefile = str(username) + 'cookie'
            form_cookiefile = cookiedir + '/' + cookiefile
            if (parentshellpid is not None):
                installdir_cookie = '/cookie/' + str(parentshellpid)
            else:
                installdir_cookie = '/cookie/cookiefile'
        elif sys.platform.startswith('win'):
            if (cookiefile is None):
                cookiefile = str(username) + 'cookie'
            form_cookiefile = cookiedir + '\\' + cookiefile
            installdir_cookie = '\\cookie\\cookiefile'
        else:
            if (cookiefile is None):
                cookiefile = str(username) + 'cookie'
            form_cookiefile = cookiedir + '/' + cookiefile
            installdir_cookie = '/cookie/cookiefile'
        try:
            if(common.create_file(form_cookiefile)):
                tokenFile = open(form_cookiefile, "w")
                if(tokenFile):
                    tokenFile.write(authToken)
                    tokenFile.close()
                else:
                    raise SOSError(SOSError.NOT_FOUND_ERR,
                                   " Failed to save the cookie file path "
                                   + form_cookiefile)

        except (OSError) as e:
            raise SOSError(e.errno, cookiedir + " " + e.strerror)
        except IOError as e:
            raise SOSError(e.errno, e.strerror)

        if (common.create_file(form_cookiefile)):
            # cookiejar.save(form_cookiefile, ignore_discard=True,
            #               ignore_expires=True);
            sos_cli_install_dir = common.getenv('VIPR_CLI_INSTALL_DIR')
            if (sos_cli_install_dir):
                if (not os.path.isdir(sos_cli_install_dir)):
                    raise SOSError(SOSError.NOT_FOUND_ERR,
                                   sos_cli_install_dir + " : Not a directory")
                config_file = sos_cli_install_dir + installdir_cookie
                if (common.create_file(config_file)):
                    fd = open(config_file, 'w+')
                    if (fd):
                        fd_content = os.path.abspath(form_cookiefile) + '\n'
                        fd.write(fd_content)
                        fd.close()
                        ret_val = username +\
                            ' : Authenticated Successfully\n' +\
                            form_cookiefile + ' : Cookie saved successfully'
                    else:
                        raise SOSError(
                            SOSError.NOT_FOUND_ERR, config_file +
                            " : Failed to save the cookie file path " +
                            form_cookiefile)
                else:
                    raise SOSError(SOSError.NOT_FOUND_ERR,
                                   config_file + " : Failed to create file")

            else:
                raise SOSError(
                    SOSError.NOT_FOUND_ERR,
                    "VIPR_CLI_INSTALL_DIR is not set." +
                    " Please check viprcli.profile")
        return ret_val


    def extract_error_detail(self, login_response):
        details_str = ""
        try:
            if(login_response.content):
                json_object = common.json_decode(login_response.content)
                if(json_object.has_key('details')):
                    details_str = json_object['details']

            return details_str
        except SOSError as e:
            return details_str


    def logout_user(self):
        '''
        Makes REST API call to generate the cookiefile for the
        specified user after validation.
        Returns:
            SUCCESS OR FAILURE
        '''
        SEC_REDIRECT = 302
        SEC_AUTHTOKEN_HEADER = 'X-SDS-AUTH-TOKEN'

        try:
            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port, "GET",
                Authentication.URI_LOGOUT, None)
            return s

        except SOSError as e:
            raise e

    def add_authentication_provider(self, mode, url, certificate, managerdn,
                                    managerpwd, searchbase, searchfilter,
                                    searchkey, groupattr, name, domains,
                                    whitelist, searchscope, description,
                                    disable, validatecertificate, maxpagesize,
                                    groupobjectclasses, groupmemberattributes, autoRegCoprHDNImportOSProjects):
        '''
        Makes REST API call to add authentication provider
        specified user after validation.
        Returns:  SUCCESS OR FAILURE
        '''
        
        domainlist_array = domains.split(',')
        urlslist_array = url.split(',')

        parms = {'mode': mode,
                 'server_urls': urlslist_array,
                 #'server_cert': certificate,
                 'manager_dn': managerdn,
                 'manager_password': managerpwd,
                 'name': name,
                 'description': description,
                 'disable': disable}

        if(autoRegCoprHDNImportOSProjects is not None and autoRegCoprHDNImportOSProjects is not ""):
            parms['autoRegCoprHDNImportOSProjects'] = autoRegCoprHDNImportOSProjects

        if(searchbase is not None and searchbase is not ""):
            parms['search_base'] = searchbase

        if(searchfilter is not None and searchfilter is not ""):
            parms['search_filter'] = searchfilter

        if(searchscope is not None and searchscope is not ""):
            parms['search_scope'] = searchscope

        if(groupattr is not None and groupattr is not ""):
            parms['group_attribute'] = groupattr

        if(maxpagesize is not None and maxpagesize is not ""):
            parms['max_page_size'] = maxpagesize

        if(domains is not None and domains is not ""):
            domainlist_array = domains.split(',')
            parms['domains'] = domainlist_array

        if(whitelist is not None and whitelist is not ""):
            whitelist_array = []
            whitelist_array = whitelist.split(',')
            parms['group_whitelist_values'] = whitelist_array
            
        if(groupobjectclasses is not None and groupobjectclasses is not ""):
            groupobjectclasses_array = []
            groupobjectclasses_array = groupobjectclasses.split(',')
            parms['group_object_class'] = groupobjectclasses_array
        
        if(groupmemberattributes is not None and groupmemberattributes is not ""):
            groupmemberattributes_array = []
            groupmemberattributes_array = groupmemberattributes.split(',')
            parms['group_member_attribute'] = groupmemberattributes_array

        body = json.dumps(parms)

        common.service_json_request(self.__ipAddr, self.__port, "POST",
            Authentication.URI_VDC_AUTHN_PROFILE,
            body)

    def list_authentication_provider(self):
        '''
        Makes REST API call to list authentication providers
        Returns:
            SUCCESS OR FAILURE
        '''
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            Authentication.URI_VDC_AUTHN_PROFILE,
            None)

        o = common.json_decode(s)

        profiles = []

        for pr in o['authnprovider']:
            profiles.append(pr)

        return profiles

    def query_authentication_provider(self, name):
        '''
        Makes REST API call to query authentication providers
        Returns:
            SUCCESS OR FAILURE
        '''
        profiles = self.list_authentication_provider()

        for pr in profiles:

            profile = self.show_authentication_provider_by_uri(pr['id'])
            if (profile['name'] == name):
                return profile['id']

    def show_authentication_provider(self, name, xml=False):
        '''
        Makes REST API call to show authentication providers
        Returns:
            SUCCESS OR FAILURE
        '''
        profiles = self.list_authentication_provider()

        for pr in profiles:
            profile = self.show_authentication_provider_by_uri(pr['id'], False)

            if ((profile) and (profile['name'] == name)):
                if(xml):
                    profile = self.show_authentication_provider_by_uri(
                    pr['id'], True)
		    dictobj = json.loads(profile)
		    dictobj_final = dict()
		    dictobj_final['authnprovider'] = dictobj
	            
		    res = common.dict2xml(dictobj_final)
		    return res.display()
                return profile

        raise SOSError(SOSError.NOT_FOUND_ERR,
                       "Authentication Provider with name '" +
                       name + "' not found")

    def delete_authentication_provider(self, name):
        '''
        Makes REST API call to delete authentication provider
        Returns:
            SUCCESS OR FAILURE
        '''
        uri = self.query_authentication_provider(name)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "DELETE",
            Authentication.URI_VDC_AUTHN_PROFILES.format(uri), None)

        return str(s) + " ++ " + str(h)

    def show_authentication_provider_by_uri(self, uri, xml=False):
        '''
        Makes REST API call to show  authentication provider by uri
        Returns:
            SUCCESS OR FAILURE
        '''

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            Authentication.URI_VDC_AUTHN_PROFILES.format(uri),
            None)

        if(not xml):
            o = common.json_decode(s)
            if('inactive' in o):
                if(o['inactive']):
                    return None
            return o
        else:
            return s

    def cleanup_dict(self, lst):

        if(len(lst['add']) == 0):
            del lst['add']

        if(len(lst['remove']) == 0):
            del lst['remove']

        return lst

    def update_authentication_provider(self, mode, add_urls, remove_urls,
                                       certificate, managerdn, managerpwd,
                                       searchbase, searchfilter, searchkey,
                                       groupattr, name, add_domains,
                                       remove_domains, add_whitelist,
                                       remove_whitelist, searchscope,
                                       description, disable,
                                       validatecertificate, maxpagesize,
                                       add_groupobjectclasses, remove_groupobjectclasses,
                                       add_groupmemberattributes, remove_groupmemberattributes,
                                       force_groupattributeupdate, autoRegCoprHDNImportOSProjects):
        '''
        Makes REST API call to generate the cookiefile for the
        specified user after validation.
        Returns:
            SUCCESS OR FAILURE
        '''

        authnprov_id = self.query_authentication_provider(name)

        urls = dict()
        domains = dict()
        whitelist = dict()
        groupobjectclasses = dict()
        groupmemberattributes = dict()

        urls['add'] = []
        if(add_urls is not None):
            for iter1 in add_urls:
                if(iter1 is not ""):
                    urls['add'].append(iter1)

        urls['remove'] = []
        if(remove_urls is not None):
            for iter1 in remove_urls:
                if(iter1 is not ""):
                    urls['remove'].append(iter1)

        domains['add'] = []
        if(add_domains is not None):
            for iter1 in add_domains:
                if(iter1 is not ""):
                    domains['add'].append(iter1)

        domains['remove'] = []
        if(remove_domains is not None):
            for iter1 in remove_domains:
                if(iter1 is not ""):
                    domains['remove'].append(iter1)

        whitelist['remove'] = []
        if(remove_whitelist is not None):
            for iter1 in remove_whitelist:
                if(iter1 is not ""):
                    whitelist['remove'].append(iter1)

        whitelist['add'] = []
        if(add_whitelist is not None):
            for iter1 in add_whitelist:
                if(iter1 is not ""):
                    whitelist['add'].append(iter1)

        groupobjectclasses['remove'] = []
        if(remove_groupobjectclasses is not None):
            for iter1 in remove_groupobjectclasses:
                if(iter1 is not ""):
                    groupobjectclasses['remove'].append(iter1)

        groupobjectclasses['add'] = []
        if(add_groupobjectclasses is not None):
            for iter1 in add_groupobjectclasses:
                if(iter1 is not ""):
                    groupobjectclasses['add'].append(iter1)

        groupmemberattributes['remove'] = []
        if(remove_groupmemberattributes is not None):
            for iter1 in remove_groupmemberattributes:
                if(iter1 is not ""):
                    groupmemberattributes['remove'].append(iter1)

        groupmemberattributes['add'] = []
        if(add_groupmemberattributes is not None):
            for iter1 in add_groupmemberattributes:
                if(iter1 is not ""):
                    groupmemberattributes['add'].append(iter1)

        '''for domain in add_domains:
                domainlist.append({'domain': domain})
            domains['add'] = domainlist #add_domains'''

        parms = {'mode': mode,
                 'manager_dn': managerdn,
                 'manager_password': managerpwd,
                 'name': name,
                 'description': description,
                 'disable': disable}

        if(autoRegCoprHDNImportOSProjects is not None):
            parms['autoRegCoprHDNImportOSProjects'] = autoRegCoprHDNImportOSProjects
        
        if(searchbase is not None):
            parms['search_base'] = searchbase

        if(searchfilter is not None):
            parms['search_filter'] = searchfilter

        if(searchscope is not None):
            parms['search_scope'] = searchscope

        if(groupattr is not None):
            parms['group_attribute'] = groupattr

        if(maxpagesize is not None):
            parms['max_page_size'] = maxpagesize

        if((len(urls['add']) > 0) or (len(urls['remove']) > 0)):
            urls = self.cleanup_dict(urls)
            parms['server_url_changes'] = urls

        if((len(domains['remove']) > 0) or (len(domains['add']) > 0)):
            domains = self.cleanup_dict(domains)
            parms['domain_changes'] = domains

        if((len(whitelist['add']) > 0) or (len(whitelist['remove']) > 0)):
            whitelist = self.cleanup_dict(whitelist)
            parms['group_whitelist_value_changes'] = whitelist

        if((len(groupobjectclasses['add']) > 0) or (len(groupobjectclasses['remove']) > 0)):
            groupobjectclasses = self.cleanup_dict(groupobjectclasses)
            parms['group_objclass_changes'] = groupobjectclasses

        if((len(groupmemberattributes['add']) > 0) or (len(groupmemberattributes['remove']) > 0)):
            groupmemberattributes = self.cleanup_dict(groupmemberattributes)
            parms['group_memberattr_changes'] = groupmemberattributes

        body = json.dumps(parms)

        if (force_groupattributeupdate is 'true'):
            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port, "PUT",
                Authentication.URI_VDC_AUTHN_PROFILES_FORCE_UPDATE.format(authnprov_id, '?allow_group_attr_change=true'),
                body)
        else:
            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port, "PUT",
                Authentication.URI_VDC_AUTHN_PROFILES.format(authnprov_id),
                body)

    def add_vdc_role(self, role, subject_id, group):
        '''
        Makes a REST API call to add vdc role
         '''

        if(subject_id):
            objecttype = 'subject_id'
            objectname = subject_id
        else:
            objecttype = 'group'
            objectname = group

        parms = {"add": [{"role": [role], objecttype: objectname}]}

        body = json.dumps(parms)

        (s, h) = common.service_json_request(self.__ipAddr, self.__port, "PUT",
                                             Authentication.URI_VDC_ROLES,
                                             body)

    def list_vdc_role(self):
        '''
        Makes a REST API call to add vdc role
         '''

        (s, h) = common.service_json_request(self.__ipAddr, self.__port, "GET",
                                             Authentication.URI_VDC_ROLES,
                                             None)

        o = common.json_decode(s)

        return o

    def delete_vdc_role(self, role, subject_id, group):
        '''
        Makes a REST API call to add vdc role
         '''

        if(subject_id):
            objecttype = 'subject_id'
            objectname = subject_id
        else:
            objecttype = 'group'
            objectname = group

        parms = {"remove": [{"role": [role], objecttype: objectname}]}

        body = json.dumps(parms)

        (s, h) = common.service_json_request(self.__ipAddr, self.__port, "PUT",
                                             Authentication.URI_VDC_ROLES,
                                             body)

    def add_user_group(self, name, domain, key, values):
        '''
        Makes REST API call to add user group
        specified user after validation.
        Returns:
            SUCCESS OR FAILURE
        '''

        attribute = {'key' : key,
                     'values' : values.split(',')}
        attributes = []
        attributes.append(attribute)
        
        parms = {'label': name,
                 'domain': domain,
                 'attributes': attributes}

        body = json.dumps(parms)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "POST",
            Authentication.URI_USER_GROUP,
            body)

    def list_user_group(self):
        '''
        Makes REST API call to list user group
        Returns:
            SUCCESS OR FAILURE
        '''
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            Authentication.URI_USER_GROUP,
            None)

        o = common.json_decode(s)

        user_groups_uri = []

        for usergrouop in o['user_group']:
            user_groups_uri.append(usergrouop)

        return user_groups_uri

    def show_user_group_by_uri(self, uri, xml=False):
        '''
        Makes REST API call to show  user group by uri
        Returns:
            SUCCESS OR FAILURE
        '''

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            Authentication.URI_USER_GROUP_ID.format(uri),
            None)

        if(not xml):
            o = common.json_decode(s)
            if('inactive' in o):
                if(o['inactive']):
                    return None
            return o
        else:
            return s

    def show_user_group(self, name, xml=False):
        '''
        Makes REST API call to show user group
        Returns:
            SUCCESS OR FAILURE
        '''
        user_groups_uri = self.list_user_group()

        for usergrouopuri in user_groups_uri:
            usergrouop = self.show_user_group_by_uri(usergrouopuri['id'], False)

            if ((usergrouop) and (usergrouop['name'].lower() == name.lower())):
                if(xml):
                    usergrouop = self.show_user_group_by_uri(
                    usergrouopuri['id'], True)
		    dictobj = json.loads(usergrouop)
		    dictobj_final = dict()
		    dictobj_final['usergroup'] = dictobj
	            
		    res = common.dict2xml(dictobj_final)
		    return res.display()
                return usergrouop

        raise SOSError(SOSError.NOT_FOUND_ERR,
                       "User Group with name '" +
                       name + "' not found")

    def find_attribute_from_user_group(self, key, usergroup):
        '''
        Finds an attribute in the user group that has key matchig
        with the argument key.
        Returns:
            Values of the attribute that matches with the key.
        '''
        
        attributes = usergroup['attributes']
        existingvalues = []
        for attribute in attributes:
            if ((attribute) and (attribute['key'].lower() == key.lower())):
                existingvalues = attribute['values']

        return existingvalues

    def user_group_add_attribute(self, name, key, values):
        '''
        Makes REST API call to add the attribute to the user group.
        Returns:
            SUCCESS OR FAILURE
        '''

        usergroup_id = self.query_user_group(name)
        usergroup = self.show_user_group_by_uri(usergroup_id, False)

        existingvalues = self.find_attribute_from_user_group(key, usergroup)
        if len(existingvalues) != 0:
            raise SOSError(SOSError.NOT_FOUND_ERR,
                       "User Group '" + name + "' already contains attribute with key '" + key +
                           "'. Use user-group-add-values or user-group-remove-values CLIs " +
                           "respectively to add or remove values from an existing attribute of an User Group.")

        attribute = {'key' : key,
                     'values' : values.split(',')}
        attributes = []
        attributes.append(attribute)
        
        parms = {'label': name,
                 'domain': usergroup['domain'],
                 'add_attributes': attributes}

        body = json.dumps(parms)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "PUT",
            Authentication.URI_USER_GROUP_ID.format(usergroup_id),
            body)

    def user_group_add_values(self, name, key, values):
        '''
        Makes REST API call to add values to the existing attribute
        of the user group.
        Returns:
            SUCCESS OR FAILURE
        '''

        usergroup_id = self.query_user_group(name)
        usergroup = self.show_user_group_by_uri(usergroup_id, False)

        existingvalues = self.find_attribute_from_user_group(key, usergroup)
        if len(existingvalues) == 0:
            raise SOSError(SOSError.NOT_FOUND_ERR,
                       "User Group '" +
                       name + "' does not contain attribute with key '" + key + "'.")
            
        newvalues = existingvalues
        newvalues.extend(values.split(','))
        
        attribute = {'key' : key,
                     'values' : newvalues}
        attributes = []
        attributes.append(attribute)
        
        parms = {'label': name,
                 'domain': usergroup['domain'],
                 'add_attributes': attributes}

        body = json.dumps(parms)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "PUT",
            Authentication.URI_USER_GROUP_ID.format(usergroup_id),
            body)

    def user_group_remove_attribute(self, name, keys):
        '''
        Makes REST API call to remove the attribute from the user group.
        Returns:
            SUCCESS OR FAILURE
        '''

        usergroup_id = self.query_user_group(name)
        usergroup = self.show_user_group_by_uri(usergroup_id, False)

        keyslist = keys.split(',')

        for key in keyslist:
            existingvalues = self.find_attribute_from_user_group(key, usergroup)
            if len(existingvalues) == 0:
                raise SOSError(SOSError.NOT_FOUND_ERR,
                           "User Group '" +
                           name + "' does not contain attribute with key '" + key + "'.")

        parms = {'label': name,
                 'domain': usergroup['domain'],
                 'remove_attributes': keyslist}

        body = json.dumps(parms)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "PUT",
            Authentication.URI_USER_GROUP_ID.format(usergroup_id),
            body)

    def user_group_remove_values(self, name, key, values):
        '''
        Makes REST API call to remove values from the existing attribute
        of the user group.
        Returns:
            SUCCESS OR FAILURE
        '''

        usergroup_id = self.query_user_group(name)
        usergroup = self.show_user_group_by_uri(usergroup_id, False)

        existingvalues = self.find_attribute_from_user_group(key, usergroup)
        if len(existingvalues) == 0:
            raise SOSError(SOSError.NOT_FOUND_ERR,
                       "User Group '" +
                       name + "' does not contain attribute with key '" + key + "'.")

        newvalues = set(existingvalues) - set(values.split(','))
        newvalues = list(newvalues)
                
        attribute = {'key' : key,
                     'values' : newvalues}
        attributes = []
        attributes.append(attribute)
        
        parms = {'label': name,
                 'domain': usergroup['domain'],
                 'add_attributes': attributes}

        body = json.dumps(parms)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "PUT",
            Authentication.URI_USER_GROUP_ID.format(usergroup_id),
            body)
    
    def query_user_group(self, name):
        '''
        Makes REST API call to query user group
        Returns:
            SUCCESS OR FAILURE
        '''
        user_groups_uri = self.list_user_group()

        for usergrouopuri in user_groups_uri:

            usergrouop = self.show_user_group_by_uri(usergrouopuri['id'])
            if (usergrouop['name'].lower() == name.lower()):
                return usergrouop['id']

    def delete_user_group(self, name):
        '''
        Makes REST API call to delete user group
        Returns:
            SUCCESS OR FAILURE
        '''
        uri = self.query_user_group(name)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "DELETE",
            Authentication.URI_USER_GROUP_ID.format(uri), None)

        return str(s) + " ++ " + str(h)


def add_authentication_provider(args):
    obj = Authentication(args.ip, args.port)
    try:
        # read authentication provider  parameters from configuration file
        config = ConfigParser.RawConfigParser()
        inif = open(args.configfile, 'rb')
        config.readfp(inif)
        sectionslst = config.sections()

        if(len(sectionslst) == 0):
            raise SOSError(
                SOSError.NOT_FOUND_ERR,
                "Authentication Provider configuration file is empty")

        for sectioniter in sectionslst:
            mode = config.get(sectioniter, "mode")
            if(mode is ""):
                raise SOSError(SOSError.VALUE_ERR, "mode should not be empty")

            if (mode is not None
                and mode not in Authentication.MODES):
                raise SOSError(SOSError.VALUE_ERR,
                     "mode should be one of" + str(Authentication.MODES))

            if(mode == 'keystone'):
                add_keystone_provider(config, sectioniter, obj, mode)
            else:
                add_other_provider(config, sectioniter, obj, mode)

    except IOError as e:
        common.format_err_msg_and_raise("add", "authentication provider",
                                        e[1], e.errno)

    except SOSError as e:
        common.format_err_msg_and_raise("add", "authentication provider",
                                        e.err_text, e.err_code)

    except ConfigParser.NoOptionError as e:
        common.format_err_msg_and_raise("add", "authentication provider",
                                        str(e), SOSError.NOT_FOUND_ERR)

    except (ConfigParser.ParsingError, ConfigParser.Error) as e:
        common.format_err_msg_and_raise("add", "authentication provider",
                                        str(e), SOSError.VALUE_ERR)

def add_keystone_provider(config, sectioniter, obj, mode):
    url = config.get(sectioniter, "url")
    managerdn = config.get(sectioniter, 'managerdn')
    name = config.get(sectioniter, 'name')
    description = config.get(sectioniter, 'description')
    disable = config.get(sectioniter, 'disable')
    groupattr = config.get(sectioniter, 'groupattr')
    domains = config.get(sectioniter, 'domains')
    autoReg = config.get(sectioniter, 'autoRegCoprHDNImportOSProjects')

    if((url is "") or (managerdn is "") or (name is "") or
                (description is "")):
                    raise SOSError(SOSError.VALUE_ERR, "For keystone mode" +
                                   "name, description, url and managerdn" +
                                   " can not be empty")

    defined_and_valid_value('disable', disable,
                                    Authentication.BOOL_VALS)

    passwd_user = common.get_password(name)

    obj.add_authentication_provider(
                mode, url, None, managerdn, passwd_user, None,
                None, None, groupattr, name, domains, None,
                None, description, disable, None,
                None, None, None, autoReg)


def add_other_provider(config, sectioniter, obj, mode):
    url = config.get(sectioniter, "url")
    managerdn = config.get(sectioniter, 'managerdn')
    name = config.get(sectioniter, 'name')
    description = config.get(sectioniter, 'description')
    disable = config.get(sectioniter, 'disable')
    searchbase = config.get(sectioniter, 'searchbase')
    searchfilter = config.get(sectioniter, 'searchfilter')
    #searchkey = config.get(sectioniter, 'searchkey')
    groupattr = config.get(sectioniter, 'groupattr')
    domains = config.get(sectioniter, 'domains')
    whitelist = config.get(sectioniter, 'whitelist')
    searchscope = config.get(sectioniter, 'searchscope')
    maxpagesize = config.get(sectioniter, 'maxpagesize')

    groupobjectclassnames = config.get(sectioniter, 'groupobjectclassnames')
    groupmemberattributetypenames = config.get(sectioniter, 'groupmemberattributetypenames')

    if((domains is "") or (url is "") or (managerdn is "") or
       (searchbase is "") or (searchfilter is "") or
       (groupattr is "") or (name is "") or (description is "") or
       (searchscope is "")):
        raise SOSError(SOSError.VALUE_ERR, "For ad/ldap, domains" +
                               ",url,managerdn," +
                               "searchbase,searchfilter,groupattr," +
                               "name,description and searchscope" +
                               " can not be empty")

    defined_and_valid_value('search scope', searchscope,
                                    Authentication.SEARCH_SCOPE)
    defined_and_valid_value('disable', disable,
                                    Authentication.BOOL_VALS)

    passwd_user = common.get_password(name)

    obj.add_authentication_provider(mode, url, None, managerdn, passwd_user,
                                    searchbase, searchfilter, None, groupattr,
                                    name, domains, whitelist, searchscope,
                                    description, disable, None,
                                    maxpagesize, groupobjectclassnames, groupmemberattributetypenames, None)




def delete_authentication_provider(args):
    obj = Authentication(args.ip, args.port)
    try:
        res = obj.delete_authentication_provider(args.name)
    except SOSError as e:
        common.format_err_msg_and_raise("delete", "Authentication Provider",
                                        e.err_text, e.err_code)


def show_authentication_provider(args):
    obj = Authentication(args.ip, args.port)
    try:
        res = obj.show_authentication_provider(args.name, args.xml)
        if(args.xml):
            return res
        return common.format_json_object(res)
    except SOSError as e:
        common.format_err_msg_and_raise("show", "Authentication Provider",
                                        e.err_text, e.err_code)


def get_attribute_value(config, sectioniter, attrname):
    try:
        val = config.get(sectioniter, attrname)
        if(val is ''):
            return None
        else:
            return val

    except IOError as e:
        raise e

    except SOSError as e:
        raise e

    except ConfigParser.NoOptionError as e:
        raise e

    except (ConfigParser.ParsingError, ConfigParser.Error) as e:
        raise e


def update_authentication_provider(args):
    obj = Authentication(args.ip, args.port)
    try:
        # read authentication provider  parameters from configuration file
        config = ConfigParser.RawConfigParser()
        inif = open(args.configfile, 'rb')
        config.readfp(inif)
        sectionslst = config.sections()

        if(len(sectionslst) == 0):
            raise SOSError(
                SOSError.NOT_FOUND_ERR,
                "Authentication Provider configuration file is empty")

        for sectioniter in sectionslst:
            mode = get_attribute_value(config, sectioniter, "mode")
            if(mode is not None and mode == 'keystone'):
                update_keystone_provider(config, sectioniter, mode, obj)
            else:
                update_other_providers(config, sectioniter, mode, obj)

    except IOError as e:
        common.format_err_msg_and_raise("update", "authentication provider",
                                        e[1], e.errno)

    except SOSError as e:
        common.format_err_msg_and_raise("update", "authentication provider",
                                        e.err_text, e.err_code)

    except ConfigParser.NoOptionError as e:
        common.format_err_msg_and_raise("update", "authentication provider",
                                        str(e), SOSError.NOT_FOUND_ERR)

    except (ConfigParser.ParsingError, ConfigParser.Error) as e:
        common.format_err_msg_and_raise("update", "authentication provider",
                                        str(e), SOSError.VALUE_ERR)
        
def update_keystone_provider(config, sectioniter, mode, obj):
    managerdn = get_attribute_value(config, sectioniter, 'managerdn')
    add_urls = config.get(sectioniter, "add-urls")
    remove_urls = config.get(sectioniter, "remove-urls")
    name = get_attribute_value(config, sectioniter, 'name')
    description = get_attribute_value(config, sectioniter, 'description')
    disable = get_attribute_value(config, sectioniter, 'disable')
    add_domains = config.get(sectioniter, 'add-domains')
    remove_domains = config.get(sectioniter, 'remove-domains')
    autoReg = config.get(sectioniter, 'autoRegCoprHDNImportOSProjects')
    groupattr = get_attribute_value(config, sectioniter, 'groupattr')
    defined_and_valid_value('disable', disable,
                            Authentication.BOOL_VALS)

    passwd_user = common.get_password(name)

    obj.update_authentication_provider(
                mode, add_urls.split(','), remove_urls.split(','),
                None, managerdn, passwd_user,
                None, None, None,
                groupattr, name, add_domains.split(','),
                remove_domains.split(','), None,
                None, None, description,
                disable, None, None, None,
                None, None, None, False, autoReg)


def update_other_providers(config, sectioniter, mode, obj):
    managerdn = get_attribute_value(config, sectioniter, 'managerdn')
    add_urls = config.get(sectioniter, "add-urls")
    remove_urls = config.get(sectioniter, "remove-urls")
    name = get_attribute_value(config, sectioniter, 'name')
    description = get_attribute_value(config, sectioniter, 'description')

    add_domains = config.get(sectioniter, 'add-domains')
    remove_domains = config.get(sectioniter, 'remove-domains')
    add_whitelist = config.get(sectioniter, 'add-whitelist')
    remove_whitelist = config.get(sectioniter, 'remove-whitelist')
    searchbase = get_attribute_value(config, sectioniter, 'searchbase')
    searchfilter = get_attribute_value(config, sectioniter, 'searchfilter')
    groupattr = get_attribute_value(config, sectioniter, 'groupattr')
    searchscope = get_attribute_value(config, sectioniter, 'searchscope')
    maxpagesize = get_attribute_value(config, sectioniter, 'maxpagesize')
    disable = get_attribute_value(config, sectioniter, 'disable')
    defined_and_valid_value('search scope', searchscope,
                            Authentication.SEARCH_SCOPE)
    defined_and_valid_value('disable', disable,
                            Authentication.BOOL_VALS)

    passwd_user = common.get_password(name)

    add_groupobjectclassnames = config.get(sectioniter, 'add-groupobjectclassnames')
    remove_groupobjectclassnames = config.get(sectioniter, 'remove-groupobjectclassnames')
    add_groupmemberattributetypenames = config.get(sectioniter, 'add-groupmemberattributetypenames')
    remove_groupmemberattributetypenames = config.get(sectioniter, 'remove-groupmemberattributetypenames')
    force_groupattributeupdate = config.get(sectioniter, 'force-groupattributeupdate')

    res = obj.update_authentication_provider(
                mode, add_urls.split(','), remove_urls.split(','),
                None, managerdn, passwd_user,
                searchbase, searchfilter, None,
                groupattr, name, add_domains.split(','),
                remove_domains.split(','), add_whitelist.split(','),
                remove_whitelist.split(','), searchscope, description,
                disable, None, maxpagesize,
                add_groupobjectclassnames.split(','), remove_groupobjectclassnames.split(','),
                add_groupmemberattributetypenames.split(','), remove_groupmemberattributetypenames.split(','),
                force_groupattributeupdate, None)



def defined_and_valid_value(fieldname, value, valid_list):
    if((value) and (value not in valid_list)):
                raise SOSError(
                    SOSError.VALUE_ERR,
                    fieldname + "can take values from among" + str(valid_list))


def list_authentication_provider(args):
    obj = Authentication(args.ip, args.port)
    try:
        uris = obj.list_authentication_provider()

        output = []

        for uri in uris:
            if(obj.show_authentication_provider_by_uri(uri['id'])):
                output.append(
                    obj.show_authentication_provider_by_uri(uri['id']))
        if(len(output) > 0):
            if(args.verbose):
                return common.format_json_object(output)
            elif(args.long):
                from common import TableGenerator
                TableGenerator(output, ['module/name', 'server_urls', 'mode',
                               'domains', 'group_attribute']).printTable()
            else:
                from common import TableGenerator
                TableGenerator(output, ['module/name', 'server_urls',
                               'mode']).printTable()
    except SOSError as e:
            if(e.err_code == SOSError.NOT_FOUND_ERR):
                raise SOSError(SOSError.NOT_FOUND_ERR,
                               "Tenant list failed: " + e.err_text)
            else:
                raise e


def authenticate_user(args):
    obj = Authentication(args.ip, args.port)
    try:
        if (args.username):
            if sys.stdin.isatty():
                passwd_user = getpass.getpass(prompt="Password : ")
            else:
                passwd_user = sys.stdin.readline().rstrip()
        else:
            raise SOSError(SOSError.CMD_LINE_ERR,
                           args.username + " : invalid username")
        res = obj.authenticate_user(args.username, passwd_user, args.cookiedir,
                                    args.cookiefile)
        print res                            
        # check the target version for upgrade.
        common.COOKIE = None
        from sysmanager import Upgrade
        try:
            ugObj = Upgrade(args.ip, args.port)
            tarver = ugObj.get_target_version()
            if (tarver and     'target_version' in tarver):
                viprver =     tarver['target_version']
                viprver = viprver[viprver.find("vipr-")+5:]
                cliver = common.get_viprcli_version()
                cliver = cliver[cliver.find("storageos-cli-")+14:]
                if ( viprver != cliver):
                    print "ViPR appliance [ "+ args.ip + \
                    " ] is at version : "+ viprver + \
                    " , CLI installed is at version : "+ cliver + \
                    " . Some functionality might not be up to date" \
                    " [ Refer to Release Notes ] , We recommend " \
                    " upgrading to latest version [ Refer to CLI " \
                    " Reference for Installation ] "
        except SOSError as e:
            if ("HTTP code: 403" in e.err_text):
                return
            else:
                raise e

    except SOSError as e:
        raise e


def authenticate_parser(parent_subparser, sos_ip, sos_port):
    # main authentication parser

    authenticate_parser = parent_subparser.add_parser(
        'authenticate',
        description='ViPR authenticate CLI usage',
        conflict_handler='resolve',
        help='Authenticate ViPR user')
    authenticate_parser.add_argument(
        '-cf', '-cookiefile',
        metavar='<cookiefile>',
        help='filename for storing cookie information',
        dest='cookiefile')
    authenticate_parser.add_argument(
        '-hostname', '-hn',
        metavar='<hostname>',
        default=sos_ip,
        dest='ip',
        help='Hostname (fully qualifiled domain name) of ViPR')
    authenticate_parser.add_argument(
        '-port', '-po',
        type=int,
        metavar='<port_number>',
        default=sos_port,
        dest='port',
        help='port number of ViPR')

    mandatory_args = authenticate_parser.add_argument_group(
        'mandatory arguments')
    mandatory_args.add_argument(
        '-u', '-username',
        metavar='<username>',
        help='username for login',
        dest='username',
        required=True)
    mandatory_args.add_argument(
        '-d', '-cookiedir',
        metavar='<cookiedir>',
        help='cookie directory to store cookie files',
        dest='cookiedir',
        required=True)
    authenticate_parser.set_defaults(func=authenticate_user)


def logout_user(args):
    obj = Authentication(args.ip, args.port)
    try:
        res = obj.logout_user()
    except SOSError as e:
        raise e


def logout_parser(parent_subparser, sos_ip, sos_port):
    # main authentication parser

    logout_parser = parent_subparser.add_parser(
        'logout',
        description='ViPR authentication CLI usage',
        conflict_handler='resolve',
        help='Logout ViPR user')
    logout_parser.add_argument(
        '-cf', '-cookiefile',
        metavar='<cookiefile>',
        help='filename for storing cookie information',
        dest='cookiefile')
    logout_parser.add_argument(
        '-hostname', '-hn',
        metavar='<hostname>',
        default=sos_ip,
        dest='ip',
        help='Hostname (fully qualifiled domain name) of ViPR')
    logout_parser.add_argument(
        '-port', '-po',
        type=int,
        metavar='<port_number>',
        default=sos_port,
        dest='port',
        help='port number of ViPR')

    logout_parser.set_defaults(func=logout_user)


def add_auth_provider_parser(subcommand_parsers, common_parser):
    # add command parser
    add_auth_provider_parser = subcommand_parsers.add_parser(
        'add-provider',
        description='ViPR Authentication Provider Add CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Add a Authentication Provider')

    mandatory_args = add_auth_provider_parser.add_argument_group(
        'mandatory arguments')

    mandatory_args.add_argument(
        '-configfile',
        metavar='<configfile>',
        help='config file for authentication provider',
        dest='configfile',
        required=True)

    add_auth_provider_parser.set_defaults(func=add_authentication_provider)


def show_auth_provider_parser(subcommand_parsers, common_parser):
    # show command parser
    show_auth_provider_parser = subcommand_parsers.add_parser(
        'show-provider',
        description='ViPR Authentication Provider Show CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show an Authentication Provider')

    mandatory_args = show_auth_provider_parser.add_argument_group(
        'mandatory arguments')

    mandatory_args.add_argument('-name',
                                metavar='<name>',
                                help='name of the authentication provider',
                                dest='name',
                                required=True)

    show_auth_provider_parser.add_argument('-xml',
                                           dest='xml',
                                           action='store_true',
                                           help='XML response')

    show_auth_provider_parser.set_defaults(func=show_authentication_provider)


def update_auth_provider_parser(subcommand_parsers, common_parser):
    # update command parser
    update_auth_provider_parser = subcommand_parsers.add_parser(
        'update',
        description='ViPR Authentication Provider Update CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Update a Authentication Provider')

    mandatory_args = update_auth_provider_parser.add_argument_group(
        'mandatory arguments')

    mandatory_args.add_argument('-configfile',
                                metavar='<configfile>',
                                help='config file for authentication provider',
                                dest='configfile',
                                required=True)

    update_auth_provider_parser.set_defaults(
        func=update_authentication_provider)


def delete_auth_provider_parser(subcommand_parsers, common_parser):
    # delete command parser
    delete_auth_provider_parser = subcommand_parsers.add_parser(
        'delete-provider',
        description='ViPR Authentication Provider delete CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Delete an Authentication Provider')

    mandatory_args = delete_auth_provider_parser.add_argument_group(
        'mandatory arguments')

    mandatory_args.add_argument('-name',
                                metavar='<name>',
                                help='name of the authentication provider',
                                dest='name',
                                required=True)

    delete_auth_provider_parser.set_defaults(
        func=delete_authentication_provider)


def list_auth_provider_parser(subcommand_parsers, common_parser):
    # update command parser
    list_auth_provider_parser = subcommand_parsers.add_parser(
        'list-providers',
        description='ViPR Authentication Provider List CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='List Authentication Providers')

    list_auth_provider_parser.add_argument(
        '-verbose', '-v',
        action='store_true',
        help='List Authentication providers with details',
        dest='verbose')

    list_auth_provider_parser.add_argument(
        '-long', '-l',
        action='store_true',
        help='List Authentication providers with more details',
        dest='long')

    list_auth_provider_parser.set_defaults(func=list_authentication_provider)


def add_vdc_role_parser(subcommand_parsers, common_parser):
    # add command parser
    add_vdc_role_parser = subcommand_parsers.add_parser(
        'add-vdc-role',
        description='ViPR Add vdc Role CLI usage.',
        conflict_handler='resolve',
        parents=[common_parser],
        help='Add a vdc role to an user')

    mandatory_args = add_vdc_role_parser.add_argument_group(
        'mandatory arguments')

    mandatory_args.add_argument('-role',
                                help='role to be added',
                                dest='role',
                                required=True,
                                choices=Authentication.ZONE_ROLES)

    arggroup = add_vdc_role_parser.add_mutually_exclusive_group(required=True)

    arggroup.add_argument('-subject-id', '-sb',
                          help='Subject ID',
                          dest='subjectid',
                          metavar='<subjectid>')

    arggroup.add_argument('-group', '-g',
                          help='Group',
                          dest='group',
                          metavar='<group>')

    add_vdc_role_parser.set_defaults(func=add_vdc_role)


def add_vdc_role(args):
    obj = Authentication(args.ip, args.port)

    try:
        res = obj.add_vdc_role(args.role, args.subjectid, args.group)
    except SOSError as e:
        raise e


def list_vdc_role_parser(subcommand_parsers, common_parser):
    # add command parser
    list_vdc_role_parser = subcommand_parsers.add_parser(
        'list-vdc-role',
        description='ViPR List vdc Roles CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='List Vdc Roles')

    list_vdc_role_parser.set_defaults(func=list_vdc_role)


def list_vdc_role(args):
    obj = Authentication(args.ip, args.port)

    try:
        res = obj.list_vdc_role()
        return common.format_json_object(res)
    except SOSError as e:
        raise e


def delete_role_parser(subcommand_parsers, common_parser):
    # register command parser
    delete_role_parser = subcommand_parsers.add_parser(
        'delete-role',
        description='ViPR delete Vdc role CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Delete a vdc role of an user')

    mandatory_args = delete_role_parser.add_argument_group(
        'mandatory arguments')

    mandatory_args.add_argument('-role',
                                metavar='<role>',
                                help='role to be deleted',
                                dest='role',
                                required=True,
                                choices=Authentication.ZONE_ROLES)

    arggroup = delete_role_parser.add_mutually_exclusive_group(required=True)

    arggroup.add_argument('-subject-id', '-sb',
                          help='Subject ID',
                          dest='subjectid',
                          metavar='<subjectid>')

    arggroup.add_argument('-group', '-g',
                          help='Group',
                          dest='group',
                          metavar='<group>')

    delete_role_parser.set_defaults(func=delete_vdc_role)


def delete_vdc_role(args):
    obj = Authentication(args.ip, args.port)

    try:
        res = obj.delete_vdc_role(args.role, args.subjectid, args.group)
    except SOSError as e:
        raise e

def add_user_group_parser(subcommand_parsers, common_parser):
    # add command parser
    add_user_group_parser = subcommand_parsers.add_parser(
        'add-user-group',
        description='ViPR User Group Add CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Add an User Group')

    mandatory_args = add_user_group_parser.add_argument_group(
        'mandatory arguments')

    mandatory_args.add_argument(
        '-name',
        metavar='<name>',
        help='name of the user group to be created',
        dest='name',
        required=True)

    mandatory_args.add_argument(
        '-domain',
        metavar='<domain>',
        help='domain to which this user group to be mapped',
        dest='domain',
        required=True)

    mandatory_args.add_argument(
        '-key',
        metavar='<key>',
        help='attribute key',
        dest='key',
        required=True)

    mandatory_args.add_argument(
        '-values',
        metavar='<values>',
        help='attribute values',
        dest='values',
        required=True)
    
    add_user_group_parser.set_defaults(func=add_user_group)

def add_user_group(args):
    obj = Authentication(args.ip, args.port)
    try:
        name = args.name
        domain = args.domain
        key = args.key
        values = args.values

        if((name is "") or (domain is "") or (key is "") or (values is "")):
            raise SOSError(SOSError.VALUE_ERR, "name, " +
                           "domain, key, values," +
                           " can not be empty")

        res = obj.add_user_group(name, domain, key, values)

    except IOError as e:
        common.format_err_msg_and_raise("add", "user group",
                                        e[1], e.errno)

    except SOSError as e:
        common.format_err_msg_and_raise("add", "user group",
                                        e.err_text, e.err_code)

    except ConfigParser.NoOptionError as e:
        common.format_err_msg_and_raise("add", "user group",
                                        str(e), SOSError.NOT_FOUND_ERR)

    except (ConfigParser.ParsingError, ConfigParser.Error) as e:
        common.format_err_msg_and_raise("add", "user group",
                                        str(e), SOSError.VALUE_ERR)

def user_group_add_attribute_parser(subcommand_parsers, common_parser):
    # add command parser
    user_group_add_attribute_parser = subcommand_parsers.add_parser(
        'user-group-add-attribute',
        description='ViPR User Group Add Attribute CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Add an attribute to an User Group')

    mandatory_args = user_group_add_attribute_parser.add_argument_group(
        'mandatory arguments')

    mandatory_args.add_argument(
        '-name',
        metavar='<name>',
        help='name of the user group to which the attribute will be added',
        dest='name',
        required=True)

    mandatory_args.add_argument(
        '-key',
        metavar='<key>',
        help='attribute key',
        dest='key',
        required=True)

    mandatory_args.add_argument(
        '-values',
        metavar='<values>',
        help='attribute values',
        dest='values',
        required=True)
    
    user_group_add_attribute_parser.set_defaults(func=user_group_add_attribute)

def user_group_add_attribute(args):
    obj = Authentication(args.ip, args.port)
    try:
        name = args.name
        key = args.key
        values = args.values

        if((name is "") or (key is "") or (values is "")):
            raise SOSError(SOSError.VALUE_ERR, "name, " +
                           "key, values," +
                           " can not be empty")

        res = obj.user_group_add_attribute(name, key, values)

    except IOError as e:
        common.format_err_msg_and_raise("add attribute to", "user group",
                                        e[1], e.errno)

    except SOSError as e:
        common.format_err_msg_and_raise("add attribute to", "user group",
                                        e.err_text, e.err_code)

    except ConfigParser.NoOptionError as e:
        common.format_err_msg_and_raise("add attribute to", "user group",
                                        str(e), SOSError.NOT_FOUND_ERR)

    except (ConfigParser.ParsingError, ConfigParser.Error) as e:
        common.format_err_msg_and_raise("add attribute to", "user group",
                                        str(e), SOSError.VALUE_ERR)

def user_group_add_values_parser(subcommand_parsers, common_parser):
    # add command parser
    user_group_add_values_parser = subcommand_parsers.add_parser(
        'user-group-add-values',
        description='ViPR User Group Add Values to the existing Attribute CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Add values to an attribute of the User Group')

    mandatory_args = user_group_add_values_parser.add_argument_group(
        'mandatory arguments')

    mandatory_args.add_argument(
        '-name',
        metavar='<name>',
        help='name of the user group to which the attribute will be modified',
        dest='name',
        required=True)

    mandatory_args.add_argument(
        '-key',
        metavar='<key>',
        help='name of the attribute key to which the values will be added',
        dest='key',
        required=True)

    mandatory_args.add_argument(
        '-values',
        metavar='<values>',
        help='attribute values to add',
        dest='values',
        required=True)
    
    user_group_add_values_parser.set_defaults(func=user_group_add_values)

def user_group_add_values(args):
    obj = Authentication(args.ip, args.port)
    try:
        name = args.name
        key = args.key
        values = args.values

        if((name is "") or (key is "") or (values is "")):
            raise SOSError(SOSError.VALUE_ERR, "name, " +
                           "key, values," +
                           " can not be empty")

        res = obj.user_group_add_values(name, key, values)

    except IOError as e:
        common.format_err_msg_and_raise("add values to", "user group attribute",
                                        e[1], e.errno)

    except SOSError as e:
        common.format_err_msg_and_raise("add values to", "user group attribute",
                                        e.err_text, e.err_code)

    except ConfigParser.NoOptionError as e:
        common.format_err_msg_and_raise("add values to", "user group attribute",
                                        str(e), SOSError.NOT_FOUND_ERR)

    except (ConfigParser.ParsingError, ConfigParser.Error) as e:
        common.format_err_msg_and_raise("add values to", "user group attribute",
                                        str(e), SOSError.VALUE_ERR)

def user_group_remove_attribute_parser(subcommand_parsers, common_parser):
    # add command parser
    user_group_remove_attribute_parser = subcommand_parsers.add_parser(
        'user-group-remove-attribute',
        description='ViPR User Group Remove Attribute CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Remove an attribute from User Group')

    mandatory_args = user_group_remove_attribute_parser.add_argument_group(
        'mandatory arguments')

    mandatory_args.add_argument(
        '-name',
        metavar='<name>',
        help='name of the user group from which the attribute will be removed',
        dest='name',
        required=True)

    mandatory_args.add_argument(
        '-keys',
        metavar='<keys>',
        help='attribute keys to remove',
        dest='keys',
        required=True)
    
    user_group_remove_attribute_parser.set_defaults(func=user_group_remove_attribute)

def user_group_remove_attribute(args):
    obj = Authentication(args.ip, args.port)
    try:
        name = args.name
        keys = args.keys

        if((name is "") or (keys is "")):
            raise SOSError(SOSError.VALUE_ERR, "name, " +
                           "keys " +
                           " can not be empty")

        res = obj.user_group_remove_attribute(name, keys)

    except IOError as e:
        common.format_err_msg_and_raise("remove attribute from", "user group",
                                        e[1], e.errno)

    except SOSError as e:
        common.format_err_msg_and_raise("remove attribute from", "user group",
                                        e.err_text, e.err_code)

    except ConfigParser.NoOptionError as e:
        common.format_err_msg_and_raise("remove attribute from", "user group",
                                        str(e), SOSError.NOT_FOUND_ERR)

    except (ConfigParser.ParsingError, ConfigParser.Error) as e:
        common.format_err_msg_and_raise("remove attribute from", "user group",
                                        str(e), SOSError.VALUE_ERR)


def user_group_remove_values_parser(subcommand_parsers, common_parser):
    # add command parser
    user_group_remove_values_parser = subcommand_parsers.add_parser(
        'user-group-remove-values',
        description='ViPR User Group Remove Values from the existing Attribute CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Remove values from an attribute of the User Group')

    mandatory_args = user_group_remove_values_parser.add_argument_group(
        'mandatory arguments')

    mandatory_args.add_argument(
        '-name',
        metavar='<name>',
        help='name of the user group to which the attribute will be modified',
        dest='name',
        required=True)

    mandatory_args.add_argument(
        '-key',
        metavar='<key>',
        help='name of the attribute key from which the values will be removed',
        dest='key',
        required=True)

    mandatory_args.add_argument(
        '-values',
        metavar='<values>',
        help='attribute values to remove',
        dest='values',
        required=True)
    
    user_group_remove_values_parser.set_defaults(func=user_group_remove_values)

def user_group_remove_values(args):
    obj = Authentication(args.ip, args.port)
    try:
        name = args.name
        key = args.key
        values = args.values

        if((name is "") or (key is "") or (values is "")):
            raise SOSError(SOSError.VALUE_ERR, "name, " +
                           "key, values," +
                           " can not be empty")

        res = obj.user_group_remove_values(name, key, values)

    except IOError as e:
        common.format_err_msg_and_raise("remove values from", "user group attribute",
                                        e[1], e.errno)

    except SOSError as e:
        common.format_err_msg_and_raise("remove values from", "user group attribute",
                                        e.err_text, e.err_code)

    except ConfigParser.NoOptionError as e:
        common.format_err_msg_and_raise("remove values from", "user group attribute",
                                        str(e), SOSError.NOT_FOUND_ERR)

    except (ConfigParser.ParsingError, ConfigParser.Error) as e:
        common.format_err_msg_and_raise("remove values from", "user group attribute",
                                        str(e), SOSError.VALUE_ERR)
        
def show_user_group_parser(subcommand_parsers, common_parser):
    # show command parser
    show_user_group_parser = subcommand_parsers.add_parser(
        'show-user-group',
        description='ViPR User Group Show CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show an User Group')

    mandatory_args = show_user_group_parser.add_argument_group(
        'mandatory arguments')

    mandatory_args.add_argument('-name',
                                metavar='<name>',
                                help='name of the user group',
                                dest='name',
                                required=True)

    show_user_group_parser.add_argument('-xml',
                                           dest='xml',
                                           action='store_true',
                                           help='XML response')

    show_user_group_parser.set_defaults(func=show_user_group)

def show_user_group(args):
    obj = Authentication(args.ip, args.port)
    try:
        res = obj.show_user_group(args.name, args.xml)
        if(args.xml):
            return res
        return common.format_json_object(res)
    except SOSError as e:
        common.format_err_msg_and_raise("show", "User Group",
                                        e.err_text, e.err_code)


def delete_user_group_parser(subcommand_parsers, common_parser):
    # delete command parser
    delete_user_group_parser = subcommand_parsers.add_parser(
        'delete-user-group',
        description='ViPR User Group Delete CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Delete an User Group')

    mandatory_args = delete_user_group_parser.add_argument_group(
        'mandatory arguments')

    mandatory_args.add_argument('-name',
                                metavar='<name>',
                                help='name of the user group',
                                dest='name',
                                required=True)

    delete_user_group_parser.set_defaults(func=delete_user_group)


def delete_user_group(args):
    obj = Authentication(args.ip, args.port)
    try:
        res = obj.delete_user_group(args.name)
    except SOSError as e:
        common.format_err_msg_and_raise("delete", "User Group",
                                        e.err_text, e.err_code)


def list_user_group_parser(subcommand_parsers, common_parser):
    # list command parser
    list_user_group_parser = subcommand_parsers.add_parser(
        'list-user-groups',
        description='ViPR User Group List CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='List User Groups')

    list_user_group_parser.add_argument(
        '-verbose', '-v',
        action='store_true',
        help='List User Groups with details',
        dest='verbose')

    list_user_group_parser.set_defaults(func=list_user_group)


def list_user_group(args):
    obj = Authentication(args.ip, args.port)
    try:
        uris = obj.list_user_group()

        output = []

        for uri in uris:
            if(obj.show_user_group_by_uri(uri['id'])):
                output.append(
                    obj.show_user_group_by_uri(uri['id']))
        if(len(output) > 0):
            if(args.verbose):
                return common.format_json_object(output)
            else:
                from common import TableGenerator
                TableGenerator(output, ['name', 'domain', 'attributes']).printTable()
    except SOSError as e:
            if(e.err_code == SOSError.NOT_FOUND_ERR):
                raise SOSError(SOSError.NOT_FOUND_ERR,
                               "User Group list failed: " + e.err_text)
            else:
                raise e


def authentication_parser(parent_subparser, common_parser):
    # main authentication parser
    parser = parent_subparser.add_parser(
        'authentication',
        description='ViPR Authentication Providers CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Operations on Authentication')

    subcommand_parsers = parser.add_subparsers(help='Use One Of Commands')

    # authentication provider parser
    add_auth_provider_parser(subcommand_parsers, common_parser)

    show_auth_provider_parser(subcommand_parsers, common_parser)

    update_auth_provider_parser(subcommand_parsers, common_parser)

    delete_auth_provider_parser(subcommand_parsers, common_parser)

    list_auth_provider_parser(subcommand_parsers, common_parser)

    add_vdc_role_parser(subcommand_parsers, common_parser)

    list_vdc_role_parser(subcommand_parsers, common_parser)

    delete_role_parser(subcommand_parsers, common_parser)

    add_user_group_parser(subcommand_parsers, common_parser)

    user_group_add_attribute_parser(subcommand_parsers, common_parser)

    user_group_add_values_parser(subcommand_parsers, common_parser)

    user_group_remove_attribute_parser(subcommand_parsers, common_parser)

    user_group_remove_values_parser(subcommand_parsers, common_parser)

    show_user_group_parser(subcommand_parsers, common_parser)

    delete_user_group_parser(subcommand_parsers, common_parser)

    list_user_group_parser(subcommand_parsers, common_parser)


