#
# This is a Dockerfile for Davenport: the Docker volume plugin for ViPR/CoprHD.
# Before building this Docker image, please make sure you build Davenport first
# since this Dockerfile is using the content in "davenport/build/dist" directory
# to build the image.
#
# docker run --name davenport -d -p 5000:5000 niqi/davenport run.py -v 10.32.72.133 -u root -p ChangeMe1!
#

FROM python:2.7
MAINTAINER Max Huang <max.huang@emc.com>

ENV DVP_ROOT=/root/davenport/ PATH=$PATH:/root/davenport

# Copy the latest Davenport code into the container.
COPY dist/ $DVP_ROOT

RUN echo "Installing Davenport dependencies..." \
  && pip install -r $DVP_ROOT/requirements.txt

EXPOSE 8000

# Set the work directory of the Cisco simulator as the installation directory.
WORKDIR $DVP_ROOT

CMD ["run.py"]