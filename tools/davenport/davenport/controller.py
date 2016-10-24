from davenport.viprclient import ViprClient


def generate_mount_path(volume_name, target_host_name):
    return '/media/{0}'.format(volume_name)


def search_parameter(parameters, parameter_type):
    for parameter in parameters:
        if parameter['label'] == parameter_type:
            return parameter
    return None


def get_affected_resource_by_type(resources, resource_type):
    for resource in resources:
        if resource.find(resource_type) > 0:
            return resource
    raise ValueError('The given type "{0}" cannot be found.'.format(
            resource_type))


class Controller:
    """
    This class calls the order API provided by "viprclient" to perform the given
    provisioning operations.
    """

    def __init__(self, user, password, host):
        self.user = user
        self.password = password
        self.host = host

    def create_volume(self, volume_name, params):
        """
        Perform the "create volume" ViPR catalog service.
        Args:
            volume_name: The volume name.
            params: The parameters of creating volumes.
        Returns: The volume info object of the volume.
        """
        with ViprClient(self.user, self.password, self.host) as client:
            path_list = ['BlockStorageServices', 'CreateBlockVolume']
            params['virtualArray'] = client.query_resource_id(
                    params['virtualArray'], 'VirtualArray')
            params['virtualPool'] = client.query_resource_id(
                    params['virtualPool'], 'VirtualPool')
            params['project'] = client.query_resource_id(
                    params['project'], 'Project')
            params['name'] = volume_name if not params.get('name') else \
                volume_name
            params['size'] = '1' if not params.get('size') else \
                params.get('size')
            params['numberOfVolumes'] = '1'
            order_result = client.submit_order(path_list, params)
        if order_result['order_info']['status'] != 'SUCCESS':
            raise ValueError('Order "creating volume" status: {0}'.format(
                    order_result['order_info']['status']))
        volume = {'name': params['name'],
                  'id': order_result['execution_info']['affectedResources'][0],
                  'formatted': False,
                  'exported': {},
                  'mounted': {},
                  'parameters': order_result['order_info']['parameters']}
        return volume

    def export_volume(self, volume, target_host_name):
        """
        Perform the "export volume" ViPR catalog service.
        Args:
            volume: The volume to be exported.
            target_host_name: The target host of exporting.
        Returns: The volume info object of the volume.
        """
        with ViprClient(self.user, self.password, self.host) as client:
            path_list = ['BlockStorageServices', 'ExportVolumetoaHost']
            target_host_uuid = client.query_resource_id(target_host_name,
                                                        'Host')
            # Remove the leading and ending '"' characters.
            project_uuid = search_parameter(volume['parameters'], 'Project'
                                            ).get('value')[1:-1]
            params = {
                'storageType': 'exclusive',
                'host':        target_host_uuid,
                'project':     project_uuid,
                'volumes':     volume.get('id'),
                'hlu':         '-1'
            }
            order_result = client.submit_order(path_list, params)
        if order_result['order_info']['status'] != 'SUCCESS':
            raise ValueError('Order "Exporting volume" status: {0}'.format(
                    order_result['order_info']['status']))
        # Parse the "export group id" which will be used in unexporting.
        export_group_id = get_affected_resource_by_type(
                order_result['execution_info']['affectedResources'],
                'ExportGroup')
        volume['exported'][target_host_name] = export_group_id
        return volume

    def mount_volume(self, volume, target_host_name):
        """
        Perform the "mount existing volume on Linux" ViPR catalog service. This
        should be ONLY done when this is the first time to mount the volume to
        the target host.
        Args:
            volume: The volume to be mounted.
            target_host_name: The target host of the volume.
        Returns: The volume info object of the volume.
        """
        with ViprClient(self.user, self.password, self.host) as client:
            path_list = ['BlockServicesforLinux', 'MountExistingVolumeonLinux']
            target_host_uuid = client.query_resource_id(target_host_name,
                                                        'Host')
            # Remove the leading and ending '"' characters.
            project_uuid = search_parameter(volume['parameters'], 'Project'
                                            ).get('value')[1:-1]
            need_format = 'true' if not volume.get('formatted') else 'false'
            mount_point = generate_mount_path(volume['name'], target_host_name)
            params = {
                'host':           target_host_uuid,
                'project':        project_uuid,
                'volume':         volume.get('id'),
                'fileSystemType': 'ext3',
                'doFormat':       need_format,
                'mountPoint':     mount_point
            }
            print('path_list = {0}, params = {1}'.format(path_list, params))
            order_result = client.submit_order(path_list, params)
        if order_result['order_info']['status'] != 'SUCCESS':
            raise ValueError('Order "Exporting volume" status: {0}'.format(
                    order_result['order_info']['status']))
        if need_format == 'true':
            volume['formatted'] = True
        volume['mounted'][target_host_name] = {
                'mount_point': mount_point, 'count': 1}
        return volume

    def unmount_volume(self, volume, target_host_name, params={}):
        """
        Perform the "unmount volume on Linux" ViPR catalog service. This should
        be ONLY done when this is the last mapping from a Docker volume on the
        target host to the actual ViPR volume.
        Args:
            volume: The volume to be unmounted.
            target_host_name: The target host of the volume.
        Returns: The volume info object of the volume.
        """
        with ViprClient(self.user, self.password, self.host) as client:
            path_list = ['BlockServicesforLinux', 'UnmountVolumeonLinux']
            target_host_uuid = client.query_resource_id(
                    target_host_name, 'Host')
            params = {
                'host':    target_host_uuid,
                'volumes': volume.get('id')
            }
            order_result = client.submit_order(path_list, params)
        if order_result['order_info']['status'] != 'SUCCESS':
            raise ValueError('Order "Exporting volume" status: {0}'.format(
                    order_result['order_info']['status']))
        del volume['mounted'][target_host_name]
        return volume

    def unexport_volume(self, volume, target_host_name):
        """
        Perform the "Unexport volume" ViPR catalog service.
        Args:
            volume: The volume to be unexported.
            target_host_name: The target host of the volume.
        Returns: The volume info object of the volume.
        """
        with ViprClient(self.user, self.password, self.host) as client:
            path_list = ['BlockStorageServices', 'UnexportVolume']
            target_host_uuid = client.query_resource_id(target_host_name,
                                                        'Host')
            export_group_id = volume['exported'][target_host_name]
            # Get the project id and remove the leading and ending '"'.
            project_uuid = search_parameter(volume['parameters'], 'Project'
                                            ).get('value')[1:-1]
            params = {
                'project': project_uuid,
                'volume':  volume.get('id'),
                'export':  export_group_id
            }
            order_result = client.submit_order(path_list, params)
        if order_result['order_info']['status'] != 'SUCCESS':
            raise ValueError('Order "Exporting volume" status: {0}'.format(
                    order_result['order_info']['status']))
        # Remove the "export" info in the volume object.
        del volume['exported'][target_host_name]
        return volume

    def remove_volume(self, volume, target_host_name):
        """
        Perform the "remove volume by host" ViPR catalog service.
        Args:
            volume: The volume to be removed.
            target_host_name: The target host of the volume.
        Returns: The volume info object being removed.
        """
        with ViprClient(self.user, self.password, self.host) as client:
            path_list = ['BlockStorageServices', 'RemoveVolumebyHost']
            target_host_uuid = client.query_resource_id(target_host_name,
                                                        'Host')
            params = {
                'host':         target_host_uuid,
                'volumes':      volume.get('id'),
                'deletionType': 'FULL'
            }
            order_result = client.submit_order(path_list, params)
        if order_result['order_info']['status'] != 'SUCCESS':
            raise ValueError('Order "Removing volume" status: {0}'.format(
                    order_result['order_info']['status']))
        volume['exported'] = {}
        volume['mounted'] = {}
        volume['formatted'] = False
        return volume
