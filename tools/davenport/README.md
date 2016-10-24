Put the "coprhd.json" file under "/etc/docker/plugins" directory, root
permission is needed.

Start the davenport server:

``` bash
python ./run.py -v 10.32.72.133 -u root -p ChangeMe1!
```

Then run the following commands to create a ViPR volume and make it be used by
the Docker instance:

``` bash
# 1. On host one create a volume:
docker volume create --driver coprhd --name docker_vol_d6 -o project=docker -o \
virtualArray=docker_varray1 -o virtualPool=docker_vpool1 -o size=2

# 2. Use the volume in a Docker instance:
docker run -it -v docker_vol_d6:/data --volume-driver=coprhd ubuntu

# 3. Create a file:
echo 'This is a volume on ViPR!' > /data/test.txt

# 4. Exit the Docker instance.

# 5. On host two create the same volume(register instead of creation actually):
docker volume create --driver coprhd --name docker_vol_d6

# 6. Use the volume in a Docker instance:
docker run -it -v docker_vol_d6:/data --volume-driver=coprhd ubuntu

# 7. Check the created file:
cat /data/test.txt

# 8. Exit the Docker instance.

# 9. Remove the volume from host two:
docker volume rm docker_vol_d6

# 10. Remove the volume from host one:
docker volume rm docker_vol_d6
```
