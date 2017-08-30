class WebWorld
  def setup(params={})
    setup_environment params
    @session_id = rand 0..9999

    login 'root'

    return if page.current_path == '/dashboard'

    visit '/setup/index'
    click_button 'Start Initial Setup'

    # Root password
    fill_in 'setup_rootPassword_value', with: APP_PASSWORD
    fill_in 'setup_rootPassword_confirm', with: APP_PASSWORD
    #       # System password
    fill_in 'setup_systemPasswords_value', with: APP_PASSWORD
    fill_in 'setup_systemPasswords_confirm', with: APP_PASSWORD
    page.execute_script('updateWizard()')
    sleep 5
    click_link 'next'

    expect(page).to have_content 'DNS Servers'
    # Accept default Network settings
    click_link 'next'

    expect(page).to have_content 'Transport'
    # Select no transport
    find('#setup_connectEmcTransport_chosen').click
    find('li', text: 'None').click
    expect(page).to have_no_selector('#finish.disabled')

    click_button 'finish'
  end

  def login(user)
    visit '/security/logout'
    visit '/dashboard'
    # Should be redirected to formlogin
    fill_in 'username', with: 'root'
    fill_in 'password', with: APP_PASSWORD
    click_button 'Login'
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
    steps %Q{
      Given I visit the Service Catalog
      And I select the emcworld tenant
      And I choose Block Storage Services
      And I choose Create Block Volume
      When I request a single volume with name #{params[:name]}
      And I click Order
    }
    @task = rand 100..999
  end

  def verify_last_task_failed!(params={})
    step 'I see the order fail'
    if params[:with_message]
      expect(page).to have_content(params[:with_message], wait: 120)
    end
  end

  def verify_last_task_succeeded!
    step 'I see the order succeed'
  end
end
