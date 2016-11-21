# Copyright (c) 2016 EMC Corporation
# All Rights Reserved.
#
#    Licensed under the Apache License, Version 2.0 (the "License"); you may
#    not use this file except in compliance with the License. You may obtain
#    a copy of the License at
#
#        http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
#    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
#    License for the specific language governing permissions and limitations
#    under the License.


#Following import statements from Cinder Code 

import base64
import binascii
import random
import string

import eventlet
from oslo_config import cfg
from oslo_log import log as logging
from oslo_utils import encodeutils
from oslo_utils import excutils
from oslo_utils import units
import six

from manila import context
from manila import exception
from manila.i18n import _
from manila.i18n import _LE
from manila.i18n import _LI
#from manila.objects import fields

from manila.share.drivers.coprhd.helpers import (
    authentication as coprhd_auth)
from manila.share.drivers.coprhd.helpers import (
    commoncoprhdapi as coprhd_utils)


from manila.share.drivers.coprhd.helpers import host as coprhd_host
from manila.share.drivers.coprhd.helpers import snapshot as coprhd_snap
from manila.share.drivers.coprhd.helpers import tag as coprhd_tag

from manila.share.drivers.coprhd.helpers import (
    virtualarray as coprhd_varray)
from manila.share.drivers.coprhd.helpers import fileshare as coprhd_fileshare


#Following import statements from Manila raga

from oslo.config import cfg

#from manila.openstack.common import log
from manila.share import driver

# TODO(jay.xu): Implement usage of stevedore for plugins.
from manila.share.drivers.emc.plugins.vnx import connection    # noqa


#Following import statements from isilon

from manila import exception
from manila.i18n import _


LOG = logging.getLogger(__name__)

MAX_RETRIES = 10
INTERVAL_10_SEC = 10

file_opts = [
    cfg.StrOpt('coprhd_hostname',
               default=None,
               help='Hostname for the CoprHD Instance'),
    cfg.PortOpt('coprhd_port',
                default=4443,
                help='Port for the CoprHD Instance'),
    cfg.StrOpt('coprhd_username',
               default=None,
               help='Username for accessing the CoprHD Instance'),
    cfg.StrOpt('coprhd_password',
               default=None,
               help='Password for accessing the CoprHD Instance',
               secret=True),
    cfg.StrOpt('coprhd_tenant',
               default=None,
               help='Tenant to utilize within the CoprHD Instance'),
    cfg.StrOpt('coprhd_project',
               default=None,
               help='Project to utilize within the CoprHD Instance'),
    cfg.StrOpt('coprhd_varray',
               default=None,
               help='Virtual Array to utilize within the CoprHD Instance'),
    cfg.BoolOpt('coprhd_emulate_snapshot',
                default=False,
                help='True | False to indicate if the storage array '
                'in CoprHD is VMAX or VPLEX')
]

CONF = cfg.CONF
CONF.register_opts(file_opts)


def retry_wrapper(func):
    def try_and_retry(*args, **kwargs):
        retry = False
        try:
            return func(*args, **kwargs)
        except coprhd_utils.CoprHdError as e:
            # if we got an http error and
            # the string contains 401 or if the string contains the word cookie
            if (e.err_code == coprhd_utils.CoprHdError.HTTP_ERR and
                (e.msg.find('401') != -1 or
                 e.msg.lower().find('cookie') != -1)):
                retry = True
                args[0].AUTHENTICATED = False
            else:
                exception_message = (_("\nCoprHD Exception: %(msg)s\n") %
                                     {'msg': e.msg})
                LOG.exception(exception_message)
                raise exception.ShareBackendException(
                    data=exception_message)
        except Exception as exc:
            exception_message = (_("\nGeneral Exception: %(exec_info)s\n") %
                                 {'exec_info':
                                  encodeutils.exception_to_unicode(exc)})
            LOG.exception(exception_message)
            raise exception.ShareBackendException(
                data=exception_message)

        if retry:
            return func(*args, **kwargs)

    return try_and_retry


#Note sure about the arguments for self.stats 

class EMCCoprHDDriverCommon(object):

    OPENSTACK_TAG = 'OpenStack'

    def __init__(self, protocol, default_backend_name, configuration=None):
        self.AUTHENTICATED = False
        self.protocol = protocol
        self.configuration = configuration
        self.configuration.append_config_values(file_opts)

        self.init_coprhd_api_components()

        self.stats = {'driver_version': '3.0.0.0',
                      'free_capacity_gb': 'unknown',
                      'reserved_percentage': '0',
                      'storage_protocol': protocol,
                      'total_capacity_gb': 'unknown',
                      'vendor_name': 'CoprHD',
                      'share_backend_name':
                      self.configuration.share_backend_name or
                      default_backend_name}
        
        
        
        
    def init_coprhd_api_components(self):

        coprhd_utils.AUTH_TOKEN = None

        # instantiate coprhd api objects for later use
        self.share_obj = coprhd_fileshare.Fileshare(
            self.configuration.coprhd_hostname,
            self.configuration.coprhd_port)
        
        self.host_obj = coprhd_host.Host(
            self.configuration.coprhd_hostname,
            self.configuration.coprhd_port)

        self.varray_obj = coprhd_varray.VirtualArray(
            self.configuration.coprhd_hostname,
            self.configuration.coprhd_port)

        self.snapshot_obj = coprhd_snap.Snapshot(
            self.configuration.coprhd_hostname,
            self.configuration.coprhd_port)

        self.tag_obj = coprhd_tag.Tag(
            self.configuration.coprhd_hostname,
            self.configuration.coprhd_port)
        
        
    def check_for_setup_error(self):
        # validate all of the coprhd_* configuration values
        if self.configuration.coprhd_hostname is None:
            message = _("coprhd_hostname is not set in manila configuration")
            raise exception.ShareBackendException(data=message)

        if self.configuration.coprhd_port is None:
            message = _("coprhd_port is not set in manila configuration")
            raise exception.ShareBackendException(data=message)

        if self.configuration.coprhd_username is None:
            message = _("coprhd_username is not set in manila configuration")
            raise exception.ShareBackendException(data=message)

        if self.configuration.coprhd_password is None:
            message = _("coprhd_password is not set in manila configuration")
            raise exception.ShareBackendException(data=message)

        if self.configuration.coprhd_tenant is None:
            message = _("coprhd_tenant is not set in manila configuration")
            raise exception.ShareBackendException(data=message)

        if self.configuration.coprhd_project is None:
            message = _("coprhd_project is not set in manila configuration")
            raise exception.ShareBackendException(data=message)

        if self.configuration.coprhd_varray is None:
            message = _("coprhd_varray is not set in manila configuration")
            raise exception.ShareBackendException(data=message)    


    def authenticate_user(self):
        # we should check to see if we are already authenticated before blindly
        # doing it again
        if self.AUTHENTICATED is False:
            obj = coprhd_auth.Authentication(
                self.configuration.coprhd_hostname,
                self.configuration.coprhd_port)

            username = self.configuration.coprhd_username
            password = self.configuration.coprhd_password

            coprhd_utils.AUTH_TOKEN = obj.authenticate_user(username,
                                                            password)
            self.AUTHENTICATED = True
            
    #check arguments for obj.create         
    def create_share(self, context, share, share_server=None):
        self.authenticate_user()
        name = self._get_resource_name(share)
        size = int(share['size']) * units.Gi

        vpool = self._get_vpool(share)
        self.vpool = vpool['CoprHD:VPOOL']
        
        try:
            
            full_project_name = ("%s/%s" % (self.configuration.coprhd_tenant,
                                            self.configuration.coprhd_project))
            self.share_obj.create(full_project_name, name, size,
                                   self.configuration.coprhd_varray,
                                   self.vpool,protocol=None,
                                   # no longer specified in volume creation
                                   sync=True,
                                   # no longer specified in volume creation
                                   )
        except coprhd_utils.CoprHdError as e:
            coprhd_err_msg = (_("File %(name)s: create failed\n%(err)s") %
                              {'name': name, 'err': six.text_type(e.msg)})

            log_err_msg = (_LE("File : %s creation failed") % name)
            self._raise_or_log_exception(
                e.err_code, coprhd_err_msg, log_err_msg)
            
            
            
    def _get_resource_name(self, resource, truncate_name=False):
        name = resource.get('display_name', None)

        if not name:
            name = resource['name']

        if truncate_name and len(name) > 31:
            name = self._id_to_base64(resource.id)

        return name        
            
    def _id_to_base64(self, id):
        # Base64 encode the id to get a volume name less than 32 characters due
        # to ScaleIO limitation.
        name = six.text_type(id).replace("-", "")
        try:
            name = base64.b16decode(name.upper())
        except (TypeError, binascii.Error):
            pass
        encoded_name = name
        if isinstance(encoded_name, six.text_type):
            encoded_name = encoded_name.encode('utf-8')
        encoded_name = base64.b64encode(encoded_name)
        if six.PY3:
            encoded_name = encoded_name.decode('ascii')
        LOG.debug("Converted id %(id)s to scaleio name %(name)s.",
                  {'id': id, 'name': encoded_name})
        return encoded_name

    @retry_wrapper
    def set_share_tags(self, share, exempt_tags=None, truncate_name=False):
        if exempt_tags is None:
            exempt_tags = []

        self.authenticate_user()
        name = self._get_resource_name(share, truncate_name)
        full_project_name = ("%s/%s" % (
            self.configuration.coprhd_tenant,
            self.configuration.coprhd_project))

        share_uri = self.share_obj.fileshare_query(name)
                                               

        self.set_tags_for_resource(
            coprhd_fileshare.Fileshare.URI_TAG_FILESHARE, share_uri, share, exempt_tags)           
            

    @retry_wrapper
    def delete_share(self, context, share, share_server=None):
        self.authenticate_user()
        name = self._get_coprhd_share_name(share)
        try:
            full_project_name = ("%s/%s" % (
                self.configuration.coprhd_tenant,
                self.configuration.coprhd_project))
            self.share_obj.delete(full_project_name, name, sync=True)
        except coprhd_utils.CoprHdError as e:
            if e.err_code == coprhd_utils.CoprHdError.NOT_FOUND_ERR:
                LOG.info(_LI(
                    "Share %s"
                    " no longer exists; share deletion is"
                    " considered successful."), name)
            else:
                coprhd_err_msg = (_("Share %(name)s: delete failed"
                                    "\n%(err)s") %
                                  {'name': name, 'err': six.text_type(e.msg)})

                log_err_msg = (_LE("Share : %s delete failed") % name)
                self._raise_or_log_exception(e.err_code, coprhd_err_msg,
                                             log_err_msg)
                
#removed all truncate names, is it useful ?           
    @retry_wrapper
    def create_snapshot(self, context, snapshot, share_server=None):
        self.authenticate_user()

        share = snapshot['share']

        if self.configuration.coprhd_emulate_snapshot:
            self.create_cloned_share(snapshot, share)
            self.set_share_tags(
                snapshot, ['_share', '_obj_share_type'])
            return

        try:
            snapshotname = self._get_resource_name(snapshot)
            file_share = snapshot['share']

            sharename = self._get_coprhd_share_name(file_share)
            projectname = self.configuration.coprhd_project
            tenantname = self.configuration.coprhd_tenant
            storageres_type = 'file'
            storageres_typename = 'shares'
            resource_uri = self.snapshot_obj.storage_resource_query(
                storageres_type,
                share_name=sharename,
                cg_name=None,
                project=projectname,
                tenant=tenantname)
            inactive = False
            sync = True
            self.snapshot_obj.snapshot_create(
                storageres_type,
                storageres_typename,
                resource_uri,
                snapshotname,
                inactive,
                sync)

            snapshot_uri = self.snapshot_obj.snapshot_query(
                storageres_type,
                storageres_typename,
                resource_uri,
                snapshotname)

            self.set_tags_for_resource(
                coprhd_snap.Snapshot.URI_BLOCK_SNAPSHOTS_TAG,
                snapshot_uri, snapshot, ['_share'])

        except coprhd_utils.CoprHdError as e:
            coprhd_err_msg = (_("Snapshot: %(snapshotname)s, create failed"
                                "\n%(err)s") % {'snapshotname': snapshotname,
                                                'err': six.text_type(e.msg)})

            log_err_msg = (_LE("Snapshot : %s create failed") % snapshotname)
            self._raise_or_log_exception(e.err_code, coprhd_err_msg,
                                         log_err_msg)            
                
   

            
            