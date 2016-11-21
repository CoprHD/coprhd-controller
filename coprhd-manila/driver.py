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



"""Driver for Dell EMC CoprHD Shared FileSystems."""

from oslo_config import cfg
from oslo_log import log

from manila.share.drivers.emc.plugins.vnx import connection
from manila.share.drivers.coprhd import connection
from manila.share import driver
from manila.share.drivers.coprhd.helpers.virtualpool import vpool_addpools

LOG = log.getLogger(__name__)

COPRHD_NAS_OPTS = [
    cfg.StrOpt('coprhd_nas_login',
               default=None,
               help='User name for the CoprHD server.'),
    cfg.StrOpt('coprhd_nas_password',
               default=None,
               help='Password for the CoprHD server.'),
    cfg.StrOpt('coprhd_nas_server',
               default=None,
               help='CoprHD server hostname or IP address.'),
    cfg.IntOpt('coprhd_nas_server_port',
               default=8080,
               help='Port number for the CoprHd server.'),
    cfg.BoolOpt('coprhd_nas_server_secure',
                default=True,
                help='Use secure connection to server.'),
    cfg.StrOpt('coprhd_share_backend',
               default=None,
               help='Share backend.'),
    cfg.StrOpt('coprhd_nas_server_container',
               default='server_2',
               help='Container of share servers.'),
    cfg.StrOpt('coprhd_nas_pool_name',
               default=None,
               help='EMC pool name.'),
]

CONF = cfg.CONF
CONF.register_opts(COPRHD_NAS_OPTS)

driver_handles_share_servers=False

class EMCShareDriver(driver.ShareDriver):
    """CoprHD specific NAS driver. Allows for NFS and CIFS NAS storage usage."""
    def __init__(self, *args, **kwargs):
        super(EMCShareDriver, self).__init__(driver_handles_share_servers,
                                                          *args, **kwargs)
        
        self.configuration = kwargs.get('configuration', None)
        if self.configuration:
            self.configuration.append_config_values(COPRHD_NAS_OPTS)

        self._storage_conn = self._get_common_driver()
        
    def _get_common_driver(self):
        return connection.CoprHDStorageConnection(
            share_backend_name=self.__class__.__name__,
            configuration=self.configuration)
        
    def create_share(self, context, share, share_server=None):
        """Is called to create share."""
        location = self._storage_conn.create_share(self, context, share,
                                                   share_server)

        return location

    def create_share_from_snapshot(self, context, share, snapshot,
                                   share_server=None):
        """Is called to create share from snapshot."""
        location = self._storage_conn.create_share_from_snapshot(
            self, context, share, snapshot, share_server)

        return location

    def create_snapshot(self, context, snapshot, share_server=None):
        """Is called to create snapshot."""
        self._storage_conn.create_snapshot(self, context, snapshot,
                                           share_server)

    def delete_share(self, context, share, share_server=None):
        """Is called to remove share."""
        self._storage_conn.delete_share(self, context, share, share_server)

    def delete_snapshot(self, context, snapshot, share_server=None):
        """Is called to remove snapshot."""
        self._storage_conn.delete_snapshot(self, context, snapshot,
                                           share_server)

    def ensure_share(self, context, share, share_server=None):
        """Invoked to sure that share is exported."""
        self._storage_conn.ensure_share(self, context, share, share_server)
        
    def extend_share(self, context, share, new_size, share_server=None):
        """Invoked to extend share."""
        self._storage_conn.extend_share(self, context, share, new_size, share_server)
            
    def allow_access(self, context, share, access, share_server=None):
        """Allow access to the share."""
        self._storage_conn.allow_access(self, context, share, access,
                                        share_server)

    def deny_access(self, context, share, access, share_server=None):
        """Deny access to the share."""
        self._storage_conn.deny_access(self, context, share, access,
                                       share_server)

    def check_for_setup_error(self):
        """Check for setup error."""
        pass
    
    def get_share_stats(self, refresh=False):
        """Get share stats.

        If 'refresh' is True, run update the stats first.
        """
        if refresh:
            self._update_share_stats()

        return self._stats

    def _update_share_stats(self):
        """Retrieve stats info from share."""

        LOG.debug("Updating share stats.")
        
        backend_name = self.configuration.safe_get(
            'share_backend_name') or "EMC_NAS_Storage"
        
        data = {
                'driver_handles_share_servers': 'False',
                'share_backend_name': backend_name,
                'vendor_name': 'EMC',
                'driver_version': '1.0',
                'storage_protocol': 'NFS_CIFS',
                'total_capacity_gb' : 'infinite',
                'free_capacity_gb'  : 'infinite',
                'pools': [
                     {
                      'pool_name':'vpool',
                      'total_capacity_gb' : 200.0,
                      'free_capacity_gb'  : 100.0,
                      'dedupe': False,
                      'reserved_percentage': 0,
                      'QoS_support': False,
                      'thin_provisioning': True,
                      'replication_type': None,
                      'max_over_subscription_ratio':10
                       }
                          ]
                  }
        
        self._storage_conn.update_share_stats(data)
        super(EMCShareDriver, self)._update_share_stats(data)
                        
    def get_network_allocations_number(self):
        """Returns number of network allocations for creating VIFs."""
        return self._storage_conn.get_network_allocations_number(self)

    def setup_server(self, network_info, metadata=None):
        """Set up and configures share server with given network parameters."""
        return self._storage_conn.setup_server(self, network_info, metadata)

    def teardown_server(self, server_details, security_services=None):
        """Teardown share server."""
        return self._storage_conn.teardown_server(self,
                                                  server_details,
                                                  security_services)
