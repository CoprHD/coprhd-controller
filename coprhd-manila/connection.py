# Copyright (c) 2016 Dell EMC Corporation
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


"""Dell EMC CoprHD Driver for Manila"""

from oslo_config import cfg
from oslo_utils import excutils
from oslo_log import log
from oslo_utils import units

import six
import sys
import traceback


from manila import context
from manila import db as manila_db
from manila import exception
from manila.i18n import _,_LE,_LW,_LI
from manila.share.drivers.coprhd import base as driver
from manila.share.drivers.emc.plugins.vnx import constants
from manila import utils
from manila.share import share_types
from manila.share.drivers.coprhd.helpers import authentication as coprhd_auth
from manila.share.drivers.coprhd.helpers import common as coprhd_utils
from manila.share.drivers.coprhd.helpers import virtualarray as coprhd_varray
from manila.share.drivers.coprhd.helpers import fileshare as coprhd_file_share


VERSION = "0.1.0"

LOG = log.getLogger(__name__)


COPRHD_NAS_OPTS = [
    cfg.StrOpt('coprhd_tenant',
               default=None,
               help='Tenant to utilize within the Dell EMC CoprHD Instance'),
    cfg.StrOpt('coprhd_project',
               default=None,
               help='Project to utilize within the Dell EMC CoprHD Instance'),
    cfg.StrOpt('coprhd_varray',
               default=None,
               help='Virtual Array to utilize within the Dell EMC CoprHD Instance'),
    cfg.StrOpt('coprhd_cookiedir',
               default='/tmp',
               help='directory to store temporary cookies, defaults to /tmp'),
]

CONF = cfg.CONF
CONF.register_opts(COPRHD_NAS_OPTS)

URI_VPOOL_VARRAY_CAPACITY = '/file/vpools/{0}/varrays/{1}/capacity'

def retry_wrapper(func):
    def try_and_retry(*args, **kwargs):
        retry = False

        try:
            return func(*args, **kwargs)
        except coprhd_utils.SOSError as e:
            # if we got an http error and
            # the string contains 401 or if the string contains the word cookie
            if (e.err_code == coprhd_utils.SOSError.HTTP_ERR and
                (e.err_text.find('401') != -1 or
                 e.err_text.lower().find('cookie') != -1)):
                retry = True
                CoprHDStorageConnection.AUTHENTICATED = False
            else:
                exception_message = "\nCoprHD Exception: %s\nStack Trace:\n%s"\
                    % (e.err_text, traceback.format_exc())
                raise exception.ShareBackendException(
                    data=exception_message)
        except Exception:
            exception_message = "\nGeneral Exception: %s\nStack Trace:\n%s" \
                % (sys.exc_info()[0], traceback.format_exc())
            raise exception.ShareBackendException(
                data=exception_message)

        if retry:
            return func(*args, **kwargs)

    return try_and_retry

class CoprHDStorageConnection(driver.StorageConnection):
    """Implements CoprHD specific functionality for Manila"""

    OPENSTACK_TAG = 'OpenStack'
    driver_handles_share_servers=False

    def __init__(self, share_backend_name, configuration=None):
        
        self.AUTHENTICATED = False
        self.conn_configuration = configuration
        self.conn_configuration.append_config_values(COPRHD_NAS_OPTS)
        self._init_coprhd_api_components()
        
    @retry_wrapper
    def create_share(self, emc_share_driver, context, share,
                     share_server=None):

        self._authenticate_user()
        share_name = self._get_fileshare_name(share)
        size = share['size'] * units.Gi
        
        vpool = self._get_vpool(share)
        self.vpool = vpool['CoprHD:VPOOL']
        
        try:
            self.file_share_obj.create(
                self.conn_configuration.coprhd_tenant +
                "/" +
                self.conn_configuration.coprhd_project,
                share_name,
                size,
                self.conn_configuration.coprhd_varray,
                self.vpool,
                id=None,
                protocol=None,
                sync=True,
                advlim=None,
                softlim=None,
                grace=None,
                synctimeout=None)
        except coprhd_utils.SOSError as e:
            if(e.err_code == coprhd_utils.SOSError.SOS_FAILURE_ERR):
                raise coprhd_utils.SOSError(
                    coprhd_utils.SOSError.SOS_FAILURE_ERR,
                    "Fileshare" + share_name + ":create failed\n" + e.err_text)
            else:
                with excutils.save_and_reraise_exception():
                    LOG.exception(_("Fileshare : %s creation failed")
                                                 % share_name)

        """Get the mount path to return."""
        tenantproject = self.conn_configuration.coprhd_tenant + \
                '/' + self.conn_configuration.coprhd_project

        mount_path = self.file_share_obj.show(tenantproject +
                                              '/' + share_name)['mount_path']
        return mount_path

    def create_share_from_snapshot(self, emc_share_driver, context,
                                   share, snapshot, share_server=None):
        return ""

    def create_snapshot(self, emc_share_driver, context, snapshot,
                        share_server=None):
        share_name = snapshot
        """Create snapshot from share."""

    def delete_share(self, emc_share_driver, context, share,
                     share_server=None):
        """Is called to remove share."""
        self._authenticate_user()
        share_name = self._get_fileshare_name(share)
        
        try:
            self.file_share_obj.delete(
                self.conn_configuration.coprhd_project + \
                '/' +
                share_name)
        except coprhd_utils.SOSError as e:
            if(e.err_code == coprhd_utils.SOSError.SOS_FAILURE_ERR):
                raise coprhd_utils.SOSError(
                    coprhd_utils.SOSError.SOS_FAILURE_ERR,
                    "Fileshare" + share_name + ":delete failed\n" + e.err_text)
            else:
                with excutils.save_and_reraise_exception():
                    LOG.exception(_("Fileshare : %s deletion failed")
                                                 % share_name)
        
    def delete_snapshot(self, emc_share_driver, context, snapshot,
                        share_server=None):
        """Remove share's snapshot."""

    def ensure_share(self, emc_share_driver,
                     context, share,
                     share_server=None):
        """Invoked to ensure that share is exported."""
        pass
    
    def extend_share(self, emc_share_driver, context, share, new_size, share_server=None):
        """Invoked to extend share.""" 
        
    def allow_access(self, emc_share_driver, context, share, access,
                     share_server=None):
        """Allow access to the share."""
        self._authenticate_user()

        share_protocol = share['share_proto']
        share_name = self._get_fileshare_name(share)

        try:
            self.file_share_obj.export(
                self.conn_configuration.coprhd_tenant +
                "/" +
                self.conn_configuration.coprhd_project +
                "/" +
                share_name,
                "sys",
                "rw",
                self.conn_configuration.coprhd_nas_login,
                [access['access_to']],
                share_protocol,
                share_name=None,
                share_description=None,
                permission_type=None,
                sub_dir=None,
                sync=True,
                synctimeout=None)
        except coprhd_utils.SOSError as e:
            if(e.err_code == coprhd_utils.SOSError.SOS_FAILURE_ERR):
                raise coprhd_utils.SOSError(
                    coprhd_utils.SOSError.SOS_FAILURE_ERR,
                    "Fileshare" + share_name + ":allow access failed\n" +
                    e.err_text)
            else:
                with excutils.save_and_reraise_exception():
                    LOG.exception(_("Fileshare : %s allow access failed")
                                                 % share_name)

    def deny_access(self, emc_share_driver, context, share, access,
                    share_server=None):
        """Deny access to the share."""

    def check_for_setup_error(self, emc_share_driver):
        """Check for setup error.
        validate all of the coprhd_* configuration values."""
        if self.conn_configuration.coprhd_nas_server is None:
            message = "coprhd_nas_server is not set in manila configuration"
            raise exception.ShareBackendException(data=message)

        if self.conn_configuration.coprhd_nas_server_port is None:
            message = "coprhd_nas_server_port is not set in manila configuration"
            raise exception.ShareBackendException(data=message)

        if self.conn_configuration.coprhd_nas_login is None:
            message = "coprhd_nas_login is not set in manila configuration"
            raise exception.ShareBackendException(data=message)

        if self.conn_configuration.coprhd_nas_password is None:
            message = "coprhd_nas_password is not set in manila configuration"
            raise exception.ShareBackendException(data=message)

        if self.conn_configuration.coprhd_tenant is None:
            message = "coprhd_tenant is not set in manila configuration"
            raise exception.ShareBackendException(data=message)

        if self.conn_configuration.coprhd_project is None:
            message = "coprhd_project is not set in manila configuration"
            raise exception.ShareBackendException(data=message)

        if self.conn_configuration.coprhd_varray is None:
            message = "coprhd_varray is not set in manila configuration"
            raise exception.ShareBackendException(data=message)

    def update_share_stats(self, stats_dict):
        """Communicate with CoprHDNASClient to get the stats."""

        """free_gb = 100.0
        total_gb = 200.0"""
        
        LOG.debug("Updating share stats")
        self._authenticate_user()

        try:
            
            shares = self.file_share_obj.list_fileshares(
                self.conn_configuration.coprhd_tenant +
                "/" +
                self.conn_configuration.coprhd_project)

            vpairs = set()
            if len(shares) > 0:
                for share in shares:
                    if share:
                        vpair = (share["vpool"]["id"], share["varray"]["id"])
                        if vpair not in vpairs:
                            vpairs.add(vpair)

            if len(vpairs) > 0:
                free_gb = 0.0
                used_gb = 0.0
                for vpair in vpairs:
                    if vpair:
                        (s, h) = coprhd_utils.service_json_request(
                            self.conn_configuration.coprhd_nas_server,
                            self.conn_configuration.coprhd_nas_server_port,
                            "GET",
                            URI_VPOOL_VARRAY_CAPACITY.format(vpair[0],
                                                             vpair[1]),
                            body=None)
                        capacity = coprhd_utils.json_decode(s)

                        free_gb += float(capacity["free_gb"])
                        used_gb += float(capacity["used_gb"])
                
                print("printing free_gb")
                print(free_gb)
        
                stats_dict['driver_version'] = VERSION

                stats_dict['free_capacity_gb'] = free_gb
                stats_dict['total_capacity_gb'] = free_gb + used_gb
        
        
                print("status updated")
                print(stats_dict)        
                        

        except coprhd_utils.CoprHdError:
            with excutils.save_and_reraise_exception():
                LOG.exception(_LE("Update share stats failed"))
                
                
        

    def get_network_allocations_number(self, emc_share_driver):
        """Returns number of network allocations for creating VIFs."""
        return constants.IP_ALLOCATIONS

    def setup_server(self, network_info, metadata=None):
        """Set up and configures share server with given network parameters."""
        pass

    def teardown_server(self, server_details, security_services=None):
        """Teardown share server."""
        pass

    def _init_coprhd_api_components(self):
        """Instantiate required coprhd cli objects."""

        coprhd_utils.COOKIE = None
        coprhd_utils.AUTH_TOKEN = None

        self.file_share_obj = coprhd_file_share.Fileshare(
            self.conn_configuration.coprhd_nas_server,
            self.conn_configuration.coprhd_nas_server_port)

        self.varray_obj = coprhd_varray.VirtualArray(
            self.conn_configuration.coprhd_nas_server,
            self.conn_configuration.coprhd_nas_server_port)

    def _authenticate_user(self):
        """Authenticate user if not done already."""
    
        if self.AUTHENTICATED is False:
            obj = coprhd_auth.Authentication(
                self.conn_configuration.coprhd_nas_server,
                self.conn_configuration.coprhd_nas_server_port)

            username = self.conn_configuration.coprhd_nas_login
            password = self.conn_configuration.coprhd_nas_password

            coprhd_utils.AUTH_TOKEN = obj.authenticate_user(username,
                                                            password)
        
            
            self.AUTHENTICATED = True
            
    def _get_fileshare_name(self, file_share):
        """Gets the display name of the fileshare
        or the name of the file share."""

        name = file_share.get('display_name', None)
        if name is None or len(name) == 0:
            name = file_share['name']

        return name

    def _get_vpool(self, file_share):
        vpool = {}
        ctxt = context.get_admin_context()
        type_id = file_share['share_type_id']
        if type_id is not None:
            share_type = share_types.get_share_type(ctxt, type_id)
            specs = share_type.get('extra_specs')
            for key, value in specs.iteritems():
                vpool[key] = value

        return vpool
    