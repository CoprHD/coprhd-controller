# Copyright Hybrid Logic Ltd.
# Copyright 2015 EMC Corporation
# See LICENSE file for details..

from uuid import UUID, uuid4
from oslo.utils import excutils
from decimal import *

from flocker.node.agents.blockdevice import (
    VolumeException, AlreadyAttachedVolume,
    UnknownVolume, UnattachedVolume,
    IBlockDeviceAPI, BlockDeviceVolume,
    UnknownInstanceID, get_blockdevice_volume
)

import socket
import viprcli.authentication as auth
import viprcli.common as utils
import viprcli.exportgroup as exportgroup
#import viprcli.host as host
import viprcli.hostinitiators as host_initiator
import viprcli.snapshot as snapshot
import viprcli.virtualarray as virtualarray
import viprcli.volume as volume
import viprcli.consistencygroup as consistencygroup
import viprcli.tag as tag
import viprcli.project as coprhdproject

from eliot import Message, Logger
from twisted.python.filepath import FilePath
from zope.interface import implementer
from subprocess import check_output

import base64
import urllib
import urllib2
import json
import os
import re
import socket
import viprcli
_logger = Logger()

def retry_wrapper(func):
    def try_and_retry(*args, **kwargs):
        retry = False
        try:
            return func(*args, **kwargs)
        except vipr_utils.SOSError as e:
            # if we got an http error and
            # the string contains 401 or if the string contains the word cookie
            if (e.err_code == vipr_utils.SOSError.HTTP_ERR and
			    (e.err_text.find('401') != -1 or
				 e.err_text.lower().find('cookie') != -1)):
				retry = True
                EMCViPRDriverCommon.AUTHENTICATED = False
            else:
                exception_message = "\nViPR Exception: %s\nStack Trace:\n%s" \
                    % (e.err_text, traceback.format_exc())
                raise exception.VolumeBackendAPIException(
				    data=exception_message)
        except Exception:
            exception_message = "\nGeneral Exception: %s\nStack Trace:\n%s" \
                % (sys.exc_info()[0], traceback.format_exc())
            raise exception.VolumeBackendAPIException(
                data=exception_message)
        if retry:
            return func(*args, **kwargs)
    return try_and_retry


class CoprHDCLIDriver(object):

    OPENSTACK_TAG = 'coprhdflocker'
    AUTHENTICATED = False
    def __init__(self, coprhdhost, 
                 port, username, password, tenant, 
                 project, varray, cookiedir, vpool,vpool_platinum,vpool_gold,vpool_silver,vpool_bronze,hostexportgroup,coprhdcli_security_file):
        self.coprhdhost = coprhdhost
        self.port =  port
        self.username = username
        self.password = password
        self.tenant = tenant
        self.project = project
        self.varray = varray
        self.cookiedir = cookiedir
        self.vpool = vpool
        self.vpool_platinum=vpool_platinum
        self.vpool_gold=vpool_gold
        self.vpool_silver=vpool_silver
        self.vpool_bronze=vpool_bronze
        self.hostexportgroup = hostexportgroup
        self.coprhdcli_security_file = coprhdcli_security_file
        self.host = unicode(socket.gethostname())
        self.stats = {'driver_version': '1.0',
                      'free_capacity_gb': 'unknown',
                      'reserved_percentage': '0',
                      'storage_protocol': 'unknown',
                      'total_capacity_gb': 'unknown',
                      'vendor_name': 'CoprHD',
                      'volume_backend_name': 'CoprHD'}
        self.volume_obj = volume.Volume(
            self.coprhdhost,
            self.port )
        
        self.exportgroup_obj = exportgroup.ExportGroup(
            self.coprhdhost,
            self.port)            

        self.project_obj = coprhdproject.Project(
            self.coprhdhost,
            self.port)
            
    def authenticate_user(self):
         
         if( (self.coprhdcli_security_file is not '')
               and (self.coprhdcli_security_file is not None)):
                from Crypto.Cipher import ARC4
                import getpass
                obj1 = ARC4.new(getpass.getuser())
                security_file = open(self.coprhdcli_security_file, 'r')
                cipher_text = security_file.readline().rstrip()
                self.username = obj1.decrypt(cipher_text)
                cipher_text = security_file.readline().rstrip()
                self.password = obj1.decrypt(cipher_text)
                security_file.close()
                        
        # we should check to see if we are already authenticated before blindly
        # doing it again
        if CoprHDCLIDriver.AUTHENTICATED is False:
            utils.COOKIE = None
            obj = auth.Authentication(
                self.coprhdhost,
                self.port)
            cookiedir = self.cookiedir
            obj.authenticate_user(self.username,
                                  self.password,
                                  cookiedir,
                                  None)
            CoprHDCLIDriver.AUTHENTICATED = True

    def get_volume_lunid(self, vol):
        self.authenticate_user()
        Message.new(Info="coprhd-get_volume_lunid" + vol).write(_logger)
        try:
           volumeuri = self.volume_obj.volume_query(
                         self.tenant +
                         "/" +
                         self.project
                         + "/" + vol)
           if not volumeuri:
            return
           volumedetails = self.volume_obj.show_by_uri(volumeuri)
           groupdetails = self.exportgroup_obj.exportgroup_show(
                         self.hostexportgroup,
                         self.project,
                         self.tenant)
           exportedvolumes =  groupdetails['volumes'] 
           Message.new(Info="coprhd-get_volume_lunid for loop").write(_logger)
           for evolumes in exportedvolumes:
              if volumeuri == evolumes['id']:
               return evolumes['lun']
           return 
        except utils.SOSError:
                    Message.new(Debug="coprhd-get_volume_lunid failed").write(_logger)
                    
    def get_volume_details(self, vol):
        self.authenticate_user()
        Message.new(Info="coprhd-get-volume-details" + vol).write(_logger)
        try:
           volumeuri = self.volume_obj.volume_query(
                         self.tenant +
                         "/" +
                         self.project
                         + "/" + vol)
           if not volumeuri:
            return
           volumedetails = self.volume_obj.show_by_uri(volumeuri)
           groupdetails = self.exportgroup_obj.exportgroup_show(
                         self.hostexportgroup,
                         self.project,
                         self.tenant)
           exportedvolumes =  groupdetails['volumes'] 
           Message.new(Info="coprhd-get-volume-details for loop").write(_logger)
           for evolumes in exportedvolumes:
              if volumeuri == evolumes['id']:
               return volumestatus(name=volumedetails['name'],size=volumedetails['provisioned_capacity_gb'],attached_to=self.host)
           return volumestatus(name=volumedetails['name'],size=volumedetails['provisioned_capacity_gb'],attached_to=None)
        except utils.SOSError:
                    Message.new(Debug="coprhd get volume details failed").write(_logger)
    @retry_wrapper
    def list_volume(self):
        self.authenticate_user()
        volumes = []
        try:
            project_uri = self.project_obj.project_query(self.project)
            volume_uris = self.volume_obj.search_volumes(project_uri)
            if not volume_uris:
             return None
            export_uris = self.exportgroup_obj.exportgroup_list(self.project, self.tenant)
            for v_uri in volume_uris:
             attach_to=None
             for e_uri in export_uris:
              groupdetails = self.exportgroup_obj.exportgroup_show(e_uri, self.project, self.tenant)
              exportedvolumes =  groupdetails['volumes']
              for evolumes in exportedvolumes:
                  Message.new(Debug="coprhd list_volume for loop" + evolumes['id'] + v_uri).write(_logger)           
                  if evolumes['id'] == v_uri:
                    attach_to = socket.gethostbyaddr(groupdetails['name'])
                    attach_to = attach_to[0]
                    attach_to = unicode(attach_to[0:8])                    
                    Message.new(Debug="coprhd list_volume attached to" + attach_to).write(_logger)
                    showvolume = self.volume_obj.show_by_uri(v_uri)
                    volume = volumestatus(name=showvolume['name'],size=showvolume['allocated_capacity_gb'],attached_to = attach_to)
                    volumes.append(volume)
                    break
             if attach_to is None:
              showvolume = self.volume_obj.show_by_uri(v_uri)
              volume = volumestatus(name=showvolume['name'],size=showvolume['allocated_capacity_gb'],attached_to=None)
              volumes.append(volume)
        except utils.SOSError:
            Message.new(Debug="coprhd list volumes failed").write(_logger)
        return volumes
        
    @retry_wrapper
    def create_volume(self, vol, size,profile_name=None):
        self.authenticate_user()
        Message.new(Debug="coprhd create_volume").write(_logger)
        if str(profile_name).lower() == 'platinum':
           self.vpool=self.vpool_platinum
        elif str(profile_name).lower() == 'gold':
           self.vpool=self.vpool_gold
        elif str(profile_name).lower() == 'silver':
           self.vpool=self.vpool_silver
        elif str(profile_name).lower() == 'bronze':
           self.vpool=self.vpool_bronze
        else:
           self.vpool=self.vpool
        try:
                self.volume_obj.create(
                self.tenant + "/" +
                self.project,
                vol, size, self.varray,
                self.vpool, protocol=None,
                # no longer specified in volume creation
                sync=True,
                number_of_volumes=1,
                thin_provisioned=None,
                # no longer specified in volume creation
                consistencygroup=None)
        except utils.SOSError as e:
            if(e.err_code == utils.SOSError.SOS_FAILURE_ERR):
                raise utils.SOSError(
                    utils.SOSError.SOS_FAILURE_ERR,
                    "Volume " + name + ": create failed\n" + e.err_text)
            else:
                with excutils.save_and_reraise_exception():
                    Message.new(Debug="coprhd create_volume failed").write(_logger)

    @retry_wrapper
    def unexport_volume(self, vol):
        self.authenticate_user()
        self.exportgroup_obj.exportgroup_remove_volumes(
                True,
                self.hostexportgroup,
                self.tenant,
                self.project,
                [vol],
                None,
                None)

    @retry_wrapper
    def export_volume(self, vol):
        self.authenticate_user()
        Message.new(Info="coprhd export_volume").write(_logger)
        self.exportgroup_obj.exportgroup_add_volumes(
                True,
                self.hostexportgroup,
                self.tenant,
                self.project,
                [vol],
                None,
                None)

    @retry_wrapper
    def delete_volume(self, vol):
        self.authenticate_user()
        try:
               self.volume_obj.delete(
                self.tenant +
                "/" +
                self.project +
                "/" +
                vol,
                volume_name_list=None,
                sync=True)
        except utils.SOSError as e:
            if e.err_code == utils.SOSError.NOT_FOUND_ERR:
                Message.new(Debug="Volume : already deleted").write(_logger)
            elif e.err_code == utils.SOSError.SOS_FAILURE_ERR:
                raise utils.SOSError(
                    utils.SOSError.SOS_FAILURE_ERR,
                    "Volume " + name + ": Delete failed\n" + e.err_text)
            else:
                with excutils.save_and_reraise_exception():
                    Message.new(Debug="Volume : delete failed").write(_logger)
                
class volumestatus(object):
 def __init__(self,name,size,attached_to):
        self.name = name[8:]
        self.size = size
        self.attached_to = attached_to
        #Message.new(Debug="coprhd list volumestatus attached_to" + self.attached_to).write(_logger)
@implementer(IProfiledBlockDeviceAPI)        
@implementer(IBlockDeviceAPI)
class CoprHDBlockDeviceAPI(object):
    """
    A simulated ``IBlockDeviceAPI`` which creates volumes (devices) with COPRHD.
    """

    def __init__(self, coprhdcliconfig, allocation_unit=None):
        """
       :param configuration: Arrayconfiguration
       """
        self.coprhdcli = coprhdcliconfig
        self._compute_instance_id = unicode(socket.gethostname())
        self._allocation_unit = 1
    
    def compute_instance_id(self):
        """
        :return: Compute instance id
        """
        return self._compute_instance_id
        
    def allocation_unit(self):
        """
        Return allocation unit
        """
        return self._allocation_unit
        
    def create_volume(self, dataset_id, size):
        """
        Create a volume of specified size on the COPRHD.
        The size shall be rounded off to 1BM, as COPRHD
        volumes of these sizes.

        See ``IBlockDeviceAPI.create_volume`` for parameter and return type
        documentation.
        """
        Message.new(Info="coprhd create_volume size is " + str(size)).write(_logger)
        volumesdetails = self.coprhdcli.get_volume_details("flocker-{}".format(dataset_id))
        if not volumesdetails:
         self.coprhdcli.create_volume("flocker-{}".format(dataset_id),size)
         Message.new(Debug="coprhd create_volume done").write(_logger)
        return BlockDeviceVolume(
          size=size, attached_to=None, dataset_id=dataset_id, blockdevice_id=u"block-{0}".format(dataset_id)
        )
    def create_volume_with_profile(self,dataset_id, size, profile_name):
        """
        Create a volume of specified size and profile on the COPRHD.
        The size shall be rounded off to 1BM, as COPRHD
        volumes of these sizes.
        
        profile can either 'PLATINUM' or 'GOLD' or 'SILVER' or 'BRONZE'
        
        See `IProfiledBlockDeviceAPI.create_volume_with_profile` for parameter and return type
        documentation.
        """

        Message.new(Info="coprhd create_volume size is " + str(size)).write(_logger)
        Message.new(Info="coprhd create_volume profile is " + str(profile_name)).write(_logger)
       
        volumesdetails = self.coprhdcli.get_volume_details("flocker-{}".format(dataset_id))
        if not volumesdetails:
           self.coprhdcli.create_volume("flocker-{}".format(dataset_id),size,profile_name=profile_name)
           Message.new(Debug="coprhd create_volume_with_profile done").write(_logger)
        return BlockDeviceVolume(size=size, attached_to=None, dataset_id=dataset_id, blockdevice_id=u"block-{0}".format(dataset_id))

    def destroy_volume(self, blockdevice_id):
        """
        Destroy the storage for the given unattached volume.
        :param: blockdevice_id - the volume id
        :raise: UnknownVolume is not found
        """
        dataset_id = UUID(blockdevice_id[6:])
        volumesdetails = self.coprhdcli.get_volume_details("flocker-{}".format(dataset_id))
        if not volumesdetails:
         raise UnknownVolume(blockdevice_id)
        Message.new(Info="Destroying Volume" + str(blockdevice_id)).write(_logger)
        self.coprhdcli.delete_volume("flocker-{}".format(dataset_id))
   
    def attach_volume(self, blockdevice_id, attach_to):
        """
        Attach volume associates a volume with to a initiator group. The resultant of this is a
        LUN - Logical Unit Number. This association can be made to any number of initiator groups. Post of this
        attachment, the device shall appear in /dev/sd<1>.
        See ``IBlockDeviceAPI.attach_volume`` for parameter and return type
        documentation.
        """
        Message.new(Debug="coprhd attach_volume invoked").write(_logger)
        dataset_id = UUID(blockdevice_id[6:])
        volumesdetails = self.coprhdcli.get_volume_details("flocker-{}".format(dataset_id))
        Message.new(Info="coprhd got volume details").write(_logger)
        if not volumesdetails:
         raise UnknownVolume(blockdevice_id)
         
        if volumesdetails.attached_to is not None:
           Message.new(Info="coprhd already attached volume").write(_logger)
           raise AlreadyAttachedVolume(blockdevice_id)
        else:
           Message.new(Info="coprhd invoking export_volume").write(_logger)
           self.coprhdcli.export_volume("flocker-{}".format(dataset_id))
        self.rescan_scsi()
        size = Decimal(volumesdetails.size)
        size = 1073741824 * int(size)
        return BlockDeviceVolume(
          size=size, attached_to=attach_to,
          dataset_id=dataset_id,
          blockdevice_id=blockdevice_id,
        )
    
    def rescan_scsi(self):
        """
        Rescan SCSI bus. This is needed in situations:
            - Resize of volumes
            - Detach of volumes
            - Possibly creation of new volumes
        :return:none
        """
        channel_number = self._get_channel_number()
        # Check for error condition
        if channel_number < 0:
            Message.new(error="iSCSI login not done for coprhd bailing out").write(_logger)
            raise DeviceException
        else:
            check_output(["rescan-scsi-bus", "-r", "-c", channel_number])

    def _get_channel_number(self):
        """
        Query scsi to get channel number of coprhd devices.
        Right now it supports only one coprhd connected array
        :return: channel number
        """
        output = check_output([b"/usr/bin/lsscsi"])
        # lsscsi gives output in the following form:
        # [0:0:0:0]    disk    ATA      ST91000640NS     SN03  /dev/sdp
        # [1:0:0:0]    disk    DGC      LUNZ             0532  /dev/sdb
        # [1:0:1:0]    disk    DGC      LUNZ             0532  /dev/sdc
        # [8:0:0:0]    disk    MSFT     Virtual HD       6.3   /dev/sdd
        # [9:0:0:0]    disk    VRAID    VRAID            2400  /dev/sde

        # We shall parse the output above and to give out channel number
        # as 9
        for row in output.split('\n'):
            if re.search(r'VRAID', row, re.I):
                channel_row = re.search('\d+', row)
                if channel_row:
                    channel_number = channel_row.group()
                    return channel_number

        # Did not find channel number of coprhd
        # The number cannot be negative
        return -1
        
    def get_device_path(self, blockdevice_id):
        """
        :param blockdevice_id:
        :return:the device path
        """
        dataset_id = UUID(blockdevice_id[6:])
        # Query LunID from CoprHD
        lunid = self.coprhdcli.get_volume_lunid("flocker-{}".format(dataset_id))
        output = check_output([b"/usr/bin/lsscsi"])
        #[1:0:0:0]    cd/dvd  NECVMWar VMware IDE CDR10 1.00  /dev/sr0
        #[2:0:0:0]    disk    VMware   Virtual disk     1.0   /dev/sda
        #[3:0:0:0]    disk    DGC      LUNZ             0533  /dev/sdb
        #[4:0:0:0]    disk    DGC      VRAID            0533  /dev/sdc
        #[4:0:0:1]    disk    DGC      VRAID            0533  /dev/sdd
        #[4:0:0:2]    disk    DGC      VRAID            0533  /dev/sde
        #[4:0:0:3]    disk    DGC      VRAID            0533  /dev/sdf
        #[4:0:0:4]    disk    DGC      VRAID            0533  /dev/sdg
        #[4:0:0:5]    disk    DGC      VRAID            0533  /dev/sdh
        #[4:0:0:6]    disk    DGC      VRAID            0533  /dev/sdi
        #[4:0:0:7]    disk    DGC      VRAID            0533  /dev/sdj

        # We shall parse the output above and give out path /dev/sde as in
        # this case
        for row in output.split('\n'):
            if re.search(r'VRAID', row, re.I):
                if re.search(r'\d:\d:\d:' + str(lunid), row, re.I):
                    device_name = re.findall(r'/\w+', row, re.I)
                    if device_name:
                        return FilePath(device_name[0] + device_name[1])

        raise UnknownVolume(blockdevice_id)
        
    def resize_volume(self, blockdevice_id, size):
        Message.new(Debug="coprhd resize_volume invoked").write(_logger)
        pass

    def detach_volume(self, blockdevice_id):
        """
        :param: volume id = blockdevice_id
        :raises: unknownvolume exception if not found
        """
        dataset_id = UUID(blockdevice_id[6:])
        volumesdetails = self.coprhdcli.get_volume_details("flocker-{}".format(dataset_id))
        if not volumesdetails:
         raise UnknownVolume(blockdevice_id)
        if volumesdetails.attached_to is not None:
         Message.new(Info="coprhd detach_volume" + str(blockdevice_id)).write(_logger)
         dataset_id = UUID(blockdevice_id[6:])
         self.coprhdcli.unexport_volume("flocker-{}".format(dataset_id))
        else:
            Message.new(Info="Volume" + blockdevice_id + "not attached").write(_logger)
            raise UnattachedVolume(blockdevice_id)
               
    def list_volumes(self):
        """
        Return ``BlockDeviceVolume`` instances for all the files in the
        ``unattached`` directory and all per-host directories.

        See ``IBlockDeviceAPI.list_volumes`` for parameter and return type
        documentation.
        """
        volumes = []
        Message.new(Debug="coprhd list_volumes invoked").write(_logger)
        volumeslist = self.coprhdcli.list_volume()
        if volumeslist is None:
         return volumes
        for num in range(len(volumeslist)):
          attached_to = None
          Message.new(Debug="coprhd list_volumes for loop").write(_logger)
          Message.new(Debug="coprhd list_volumes" + volumeslist[num].name).write(_logger)
          if volumeslist[num].attached_to is None:
           Message.new(Debug="coprhd list_volumes attached None").write(_logger)
          else:
           attached_to = volumeslist[num].attached_to
          Message.new(Debug="coprhd list_volumes creating blockvolume size is "+volumeslist[num].size).write(_logger)
          size = Decimal(volumeslist[num].size)
          size = 1073741824 * int(size)
          volume = BlockDeviceVolume(
                                    size=size, attached_to=attached_to,
                                    dataset_id=UUID(volumeslist[num].name), blockdevice_id=u"block-{0}".format(volumeslist[num].name)
                                    )
          Message.new(Debug="coprhd list_volumes appending volume").write(_logger)
          volumes.append(volume)
        Message.new(Debug="coprhd list_volumes returning").write(_logger)
        return volumes

def configuration(coprhdhost, port, username, password, tenant,
                           project, varray, cookiedir, vpool,vpool_platinum,vpool_gold,vpool_silver,vpool_bronze,hostexportgroup,coprhdcli_security_file):
    """
    :return:CoprHDBlockDeviceAPI object
    """
    return CoprHDBlockDeviceAPI(
        coprhdcliconfig=CoprHDCLIDriver(coprhdhost, 
        port, username, password, tenant, 
        project, varray, cookiedir, vpool,vpool_platinum,vpool_gold,vpool_silver,vpool_bronze,hostexportgroup,coprhdcli_security_file),allocation_unit=1
    )
