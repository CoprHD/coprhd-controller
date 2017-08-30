module WftestsSetup
  def setup_environment(params={})
    `security login root #{APP_PASSWORD}`
    `cos list block | grep -E '^vpool '`
    if $?.success?
      return
    end

    # TODO Extract this logic
    setup_flags = '-setupsim -resetsim'
    if params[:system] == 'unity'
      setup_flags = '-setuphw'
    end

    wftests = '/workspace/integration/coprhd-controller/tools/tests/export-tests/wftests.sh'
    o = `#{wftests} $SANITY_CONF #{params[:system]} #{setup_flags} none`
    expect($?.success?).to be true
  end
end
