class ApiWorld
  def setup(params={})
    setup_environment params
    @session_id = rand 0..9999
  end

  def login(user)
    `security login #{user} #{APP_PASSWORD}`
  end

  def snapshot_database(params={})
    cmd = '/workspace/integration/coprhd-controller/tools/tests/export-tests/snap_db.sh'
    esc_seq = params[:esc_seq] || ''
    `#{cmd} #{@session_id} #{params[:slot]} "#{params[:cfs]}" #{esc_seq}`
  end

  def validate_db(params={})
    cmd = '/workspace/integration/coprhd-controller/tools/tests/export-tests/snap_db.sh'
    `#{cmd} -validate #{@session_id} #{params[:slot_1]} #{params[:slot_2]} "#{params[:cfs]}"`
    expect($?.success?).to be true
  end

  def set_artificial_failure(failure)
    `syssvc $SANITY_CONF localhost set_prop artificial_failure "#{failure}"`
  end

  def clear_artificial_failures
    set_artificial_failure 'none'
  end

  def create_block_volume(params)
    puts "Creating volume: #{params.inspect}"
    `volume create #{params[:name]} project nh vpool 1GB > /dev/null 2>&1`
    # TODO We DO need to grab the Task id, in order to get the messages
    @task_success = $?.success?
  end

  def verify_last_task_failed!(params={})
    # TODO Handle :with_message on task
    expect(@task_success).to be false
  end

  def verify_last_task_succeeded!
    expect(@task_success).to be true
  end
end
