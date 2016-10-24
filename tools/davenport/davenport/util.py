import airspeed
import datetime
import json
import re
import time


def get_current_time_str(time_format='%Y%m%d-%H%M%S-%f'):
    return datetime.datetime.fromtimestamp(time.time()).strftime(time_format)


def format_datetime(datetime_instance, time_format='%Y-%m-%d %H:%M:%S'):
    return datetime_instance.strftime(time_format)


def find_if_exists(regex, content):
    result = re.findall(regex, content)
    return len(result) > 0


def find_result(regex, content):
    return re.findall(regex, content)


def substitute(template_str, namespace):
    return airspeed.Template(template_str).merge(namespace)


def to_json(instance):
    return json.dumps(instance)
