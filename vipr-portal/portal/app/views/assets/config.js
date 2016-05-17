%{
    healthStatus = com.emc.storageos.systemservices.impl.healthmonitor.models.Status;
    clusterState = com.emc.vipr.model.sys.ClusterInfo.ClusterState;
    workflowStep = com.emc.storageos.workflow.Workflow.StepState;
    task = com.emc.storageos.db.client.model.Task.Status;

    resources = com.emc.sa.util.ResourceType;
}%

angular.module("config", []).constant({
    colors: {
        danger: '#d9534f',
        warn: '#f0ad4e',
        success: '#5cb85c',
        primary: '#428bca',
        info: '#5bc0de'
    },
    resourceTypes: {
        Volume : {
            icon:'Volume.png',
            link: #{jsAction @resources.BlockVolumes.volume(':id') /}
        },
        ExportGroup: {
            icon:'Export.png',
            link: #{jsAction @resources.BlockExportGroups.exportGroup(':id') /}
        },
        FileShare: {
            icon:'Folder.png'
        },
        FileSnapshot: {
            icon:'Snapshot.png'
        },
        BlockSnapshot: {
            icon:'Snapshot.png',
            link: #{jsAction @resources.BlockSnapshots.snapshotDetails(':id')/}
        },
        Vcenter: {
            icon:'VCenter.png'
        },
        Host: {
            icon:'Host.png'
        },
        NetworkSystem: {
            icon:'Switch.png'
        },
        StorageSystem: {
            icon:'StorageArray.png'
        },
        ProtectionSystem: {
            icon:'Protection.png'
        },
        SysEvent: {
            icon:'VDC.png'
        }
    },
    statusTypes: {
        node: {
            '${healthStatus.GOOD}': {
                icon: 'ok',
                class: 'text-success',
                key: 'status.good'
            },
            '${healthStatus.DEGRADED}': {
                icon: 'warning-sign',
                class: 'text-warning',
                key: 'status.degraded'
            },
            '${healthStatus.RESTARTED}': {
                icon: 'off',
                class: 'text-info',
                key: 'status.restarted'
            },
            '${healthStatus.UNAVAILABLE}': {
                icon: 'question-sign',
                class: 'text-danger',
                key: 'status.unavailable'
            },
            default: {
                icon: 'question-sign',
                class: 'text-danger',
                key: 'status.unavailable'
            }
        },
        cluster: {
            '${clusterState.UNKNOWN}': {
                icon: 'question-sign',
                class: 'label label-warning',
                key: 'status.unknown'
            },
            '${clusterState.STABLE}': {
                icon: 'ok',
                class: 'label label-success',
                key: 'status.stable'
            },
            '${clusterState.SYNCING}': {
                icon: 'download',
                class: 'label label-info',
                key: 'status.syncing'
            },
            '${clusterState.UPGRADING}': {
                icon: 'repeat',
                class: 'label label-info',
                key: 'status.upgrading'
            },
            '${clusterState.UPGRADING_PREP_DB}': {
                icon: 'time',
                class: 'label label-info',
                key: 'status.database.prep'
            },
            '${clusterState.UPGRADING_CONVERT_DB}': {
                icon: 'repeat',
                class: 'label label-info',
                key: 'status.database.upgrade'
            },
            '${clusterState.UPGRADING_FAILED}': {
                icon: 'remove',
                class: 'label label-danger',
                key: 'status.upgrading.failed'
            },
            '${clusterState.DEGRADED}': {
                icon: 'warning-sign',
                class: 'label label-warning',
                key: 'status.degraded'
            },
            '${clusterState.UPDATING}': {
                icon: 'wrench',
                class: 'label label-info',
                key: 'status.updating'
            },
            '${clusterState.POWERINGOFF}': {
                icon: 'off',
                class: 'label label-danger',
                key: 'status.powering.off'
            },
            '${clusterState.INITIALIZING}': {
                icon: 'repeat',
                class: 'label label-default',
                key: 'status.initializing'
            }
        },
        task: {
            '${task.pending}':{
                icon: 'refresh',
                iconClass: 'rotate',
                class: '',
                key: 'resources.tasks.pending'
            },
            '${task.ready}':{
                icon: 'ok',
                class: '',
                key: 'resources.tasks.ready'
            },
            '${task.error}':{
                icon: 'remove',
                class: '',
                key: 'resources.tasks.error'
            },
            '${task.suspended_no_error}':{
                icon: 'refresh',
                iconClass: 'rotate',
                class: '',
                key: 'resources.tasks.suspended_no_error'
            },
            '${task.suspended_error}':{
                icon: 'refresh',
                iconClass: 'rotate',
                class: '',
                key: 'resources.tasks.suspended_error'
            }

        },
        workflowStep: {
            '${workflowStep.CREATED}': {
                icon: 'ok',
                class: '',
                key: ''
            },
            '${workflowStep.BLOCKED}': {
                icon: 'pause',
                class: '',
                key: ''
            },
            '${workflowStep.QUEUED}': {
                icon: 'ok',
                class: '',
                key: ''
            },
            '${workflowStep.EXECUTING}': {
                icon: 'refresh',
                iconClass: 'rotate',
                key: ''
            },
            '${workflowStep.CANCELLED}': {
                icon: 'ok',
                class: '',
                key: ''
            },
            '${workflowStep.SUCCESS}': {
                icon: 'ok',
                class: '',
                key: ''
            },
            '${workflowStep.ERROR}': {
                icon: 'remove',
                class: '',
                key: ''
            }
        }
    },
    
    %{
    locale = play.i18n.Lang.get()
    
    if ( play.i18n.Messages.locales?.containsKey(locale) )  {
        value = play.i18n.Messages.all(locale)
    }
    else if ( locale?.length() > 2 && play.i18n.Messages.locales.containsKey(locale?.substring(0, 2)) ) {
        value = play.i18n.Messages.all(locale.substring(0, 2))
    }
    else {
        value = play.i18n.Messages.defaults
    }
    }%
    
    messages: ${new com.google.gson.Gson().toJson(value).raw()},
    routes: window.routes,
    locale: '${locale}'
});
