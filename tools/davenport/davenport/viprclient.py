import os

from davenport.viprcli import authentication
from davenport.viprcli import common
from davenport.viprcli import sysmanager
from davenport.viprcli import catalog
from davenport.viprcli import order
from davenport.viprcli import virtualarray
from davenport.viprcli import virtualpool
from davenport.viprcli import project
from davenport.viprcli import host
from davenport import util

virtualArrayMap = {}
virtualPoolMap = {}
projectMap = {}
hostMap = {}


class ViprClient(object):
    """
    This class is used to perform ViPR operations by calling the RESTful API
    provided by ViPR.
    """
    def __init__(self, user, password, vipr_addr, port=4443, cookie_dir='/tmp',
                 cookie_file_prefix='vipr.cookie'):
        self.user = user
        self.password = password
        self.vipr_addr = vipr_addr
        self.port = port
        self.cookie_dir = cookie_dir
        self.cookie_file = '{0}.{1}'.format(cookie_file_prefix,
                                            util.get_current_time_str())
        self.cookie_file_path = '{0}/{1}'.format(self.cookie_dir,
                                                 self.cookie_file)
        self.auth_instance = authentication.Authentication(self.vipr_addr,
                                                           self.port)

    def __enter__(self):
        self.login()
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.logout()

    def login(self):
        self.auth_instance.authenticate_user(self.user, self.password,
                                             self.cookie_dir, self.cookie_file)
        common.COOKIE = self.cookie_file_path
        return self.auth_instance, self.cookie_file_path

    def logout(self):
        result = self.auth_instance.logout_user()
        if result.startswith('{"user":'):
            # Remove the cookie file if logout succeeds.
            os.remove(self.cookie_file_path)
            common.COOKIE = None
        return result

    def get_catalog_service_uuid(self, path_list, catalog_port=443):
        """
        Query the given catalog service's UUID.
        Args:
            path_list: A string list of the path elements like:
                       ['BlockStorageServices', 'CreateBlockVolume']
            catalog_port: The port used to perform catalog related operations.
        Returns: The catalog service's UUID like below:
          urn:storageos:CatalogService:fa05f6e2-2968-4fbb-8825-135ffe0c5033:vdc1
        """
        client = catalog.Catalog(self.vipr_addr, catalog_port)
        categories = client.get_catalog().get('sub_categories')
        # print('categories = {0}'.format(categories))
        searched_category = None
        for category in categories:
            if category.get('name') == path_list[0]:
                searched_category = category
                break
        category_id = searched_category.get('id')
        # print('category_id = {0}'.format(category_id))
        category = client.get_category(category_id)
        # print('services = {0}'.format(category))
        searched_service = None
        for service in category.get('services'):
            if service.get('name') == path_list[1]:
                searched_service = service
                break
        return searched_service.get('id')

    def submit_order(self, path_list, params, catalog_port=443):
        """
        Submit an order to perform the given catalog service operation.
        Args:
            path_list: A string list of the path elements like:
                       ['BlockStorageServices', 'CreateBlockVolume']
            params: The parameters of this catalog service call.
            catalog_port: The port used to perform catalog related operations.
        Returns: The finished order's information including the effected
                 resource(s).
        """
        service_uuid = self.get_catalog_service_uuid(path_list)
        catalog_client = catalog.Catalog(self.vipr_addr, catalog_port)
        order_stub = catalog_client.execute(None, service_uuid, params)
        order_id = order_stub.get('id')
        order_client = order.Order(self.vipr_addr, catalog_port)
        order_info = order_client.block_until_complete(order_id)
        execution_info = order.order_execution(self.vipr_addr, catalog_port,
                                               order_id)
        return {'order_info': order_info, 'execution_info': execution_info}

    def query_resource_id(self, name, rtype):
        resource_query_methods = {
            'Host': 'query_host',
            'Project': 'query_project',
            'VirtualArray': 'query_varray',
            'VirtualPool': 'query_vpool'
        }
        query_method = resource_query_methods.get(rtype)
        if query_method is None:
            raise ValueError('Unsupported resource type: "{0}"'.format(rtype))
        function = getattr(self, query_method)
        resource_id = function(name)
        return resource_id

    def query_varray(self, name):
        global virtualArrayMap
        if virtualArrayMap.get(name, None) is not None:
            return virtualArrayMap.get(name)
        client = virtualarray.VirtualArray(self.vipr_addr, self.port)
        varray_id = client.varray_query(name)
        virtualArrayMap[name] = varray_id
        return varray_id

    def query_vpool(self, name):
        global virtualPoolMap
        if virtualPoolMap.get(name, None) is not None:
            return virtualPoolMap.get(name)
        client = virtualpool.VirtualPool(self.vipr_addr, self.port)
        res = client.vpool_list('block')
        vpool_id = None
        for item in res:
            if item['name'] == name:
                vpool_id = item['id']
                virtualPoolMap[name] = vpool_id
                break
        return vpool_id

    def query_host(self, name):
        global hostMap
        if hostMap.get(name, None) is not None:
            return hostMap.get(name)

        client = host.Host(self.vipr_addr, self.port)
        uri = client.query_by_name(name)
        hostMap[name] = uri
        return uri

    def query_project(self, name):
        global projectMap
        if projectMap.get(name, None) is not None:
            return projectMap.get(name)
        client = project.Project(self.vipr_addr, self.port)
        res = client.project_list(None)
        project_id = None
        for item in res:
            if item['name'] == name:
                project_id = item['id']
                projectMap[name] = project_id
                break
        return project_id

    def get_health(self):
        # print('Get health from {0}:{1}'.format(self.vipr_addr, self.port))
        monitor = sysmanager.Monitoring(self.vipr_addr, self.port)
        result = monitor.get_health(None, None)
        return result

    def get_stats(self):
        # print('Get stats from {0}:{1}'.format(self.vipr_addr, self.port))
        monitor = sysmanager.Monitoring(self.vipr_addr, self.port)
        result = monitor.get_stats(None, None)
        return result

    def get_version(self):
        # print('Get version from {0}:{1}'.format(self.vipr_addr, self.port))
        upgrade = sysmanager.Upgrade(self.vipr_addr, self.port)
        result = upgrade.get_target_version()
        return result['target_version']

