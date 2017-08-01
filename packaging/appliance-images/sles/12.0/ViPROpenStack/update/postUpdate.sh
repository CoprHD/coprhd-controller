#!/bin/bash

##############################################################
# post Update - start
# enable mysql
systemctl start mysql.service

# enable rabbitmq
echo "Setting rabbitmq"
systemctl start rabbitmq-server.service


# enable memcached
systemctl start memcached.service

# Edit keystone config
echo "Updating keystone config"
sed -i "s/driver = keystone.contrib.revoke.backends.sql.Revoke/driver = sql/" /etc/keystone/keystone.conf
sed -i "s/connection = mysql:/connection = mysql+pymysql:/g" /etc/keystone/keystone.conf
sed -i "s/provider = keystone.token.providers.uuid.Provider/provider = uuid/" /etc/keystone/keystone.conf
sed -i "s/driver = keystone.token.persistence.backends.memcache.Token/driver = memcache/" /etc/keystone/keystone.conf 
su -s /bin/sh -c "keystone-manage db_sync" keystone

# apache2 (copy attached file)
echo "Apache settings"
cp /etc/apache2/conf.d/wsgi-keystone.conf /etc/apache2/conf.d/wsgi-keystone.conf.liberty
rm /etc/apache2/conf.d/wsgi-keystone.conf
cp wsgi-keystone.conf /etc/apache2/conf.d/wsgi-keystone.conf
chown -R keystone:keystone /etc/keystone
a2enmod version
systemctl start apache2.service

 
# cinder
echo "Updating cinder config"
sed -i "s/connection = mysql:/connection = mysql+pymysql:/g" /etc/cinder/cinder.conf
su -s /bin/sh -c "cinder-manage db sync" cinder

systemctl start openstack-cinder-api.service openstack-cinder-scheduler.service

# restart all services
echo "Restarting all services"
service rabbitmq-server restart
service openstack-cinder-volume restart
service openstack-cinder-scheduler restart
service openstack-cinder-api restart

# Update ViPR OpenStack version
sed -i "s~ViPROpenStack-1\.[0-99]\.0\.0\.[0-99]*~$(</etc/ImageVersion)~" /etc/motd
# post Update - End
##############################################################