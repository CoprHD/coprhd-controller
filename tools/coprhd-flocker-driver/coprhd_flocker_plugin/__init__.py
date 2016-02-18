# Copyright Hybrid Logic Ltd.
# Copyright 2015 EMC Corporation
# See LICENSE file for details.

from flocker.node import BackendDescription, DeployerType
from coprhd_flocker_plugin.coprhd_blockdevice import configuration


def api_factory(cluster_id, **kwargs):
    return configuration(coprhdhost=kwargs[u'coprhdhost'],port=kwargs[u'port'],
                                  username=kwargs[u'username'],password=kwargs[u'password'],
                                  tenant=kwargs[u'tenant'],project=kwargs[u'project'],
                                  varray=kwargs[u'varray'],cookiedir=kwargs[u'cookiedir'],
                                  vpool=kwargs[u'vpool'],hostexportgroup=kwargs[u'hostexportgroup'])


FLOCKER_BACKEND = BackendDescription(
    name=u"coprhd_flocker_plugin",  # name isn't actually used for 3rd party plugins
    needs_reactor=False, needs_cluster_id=True,
    api_factory=api_factory, deployer_type=DeployerType.block) 
