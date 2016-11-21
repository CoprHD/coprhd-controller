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


"""Driver for EMC CoprHD FC volumes."""

import re

from oslo_log import log as logging
from manila.openstack.common import log

#from manila import interface
from manila.share import driver
from manila.share.drivers.coprhd import common as coprhd_common
from manila.zonemanager import utils as fczm_utils
from oslo.config import cfg




LOG = logging.getLogger(__name__)

#Ask doubt about renaming COPRHDNFSDriver to CoprHDShareDriver

class EMCCoprHDShareDriver(driver.ShareDriver):
    """CoprHD Share Driver."""
    VERSION = "3.0.0.0"

    def __init__(self, *args, **kwargs):
        super(EMCCoprHDShareDriver, self).__init__(*args, **kwargs)
        self.common = self._get_common_driver()
#protocol needs to be removed ?
    def _get_common_driver(self):
        return coprhd_common.EMCCoprHDDriverCommon(
            protocol='NFS',
            default_backend_name=self.__class__.__name__,
            configuration=self.configuration)

    def check_for_setup_error(self):
        self.common.check_for_setup_error()

    def create_share(self, context, share, share_server=None):
        """Is called to create share."""
        self.common.create_share(self, context, share,share_server)
        self.common.set_share_tags(share, ['_obj_share_type'])
        
    
    def delete_share(self, context, share, share_server=None):
        """Is called to remove share."""
        self.common.delete_share(share)
        
    def create_snapshot(self, context, snapshot, share_server=None):
        """Is called to create snapshot."""
        self.common.create_snapshot(snapshot)
        
            
        
        
        
        
        
            
        
        
        
        
        
        
