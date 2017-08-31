After do |scenario|
  if scenario.failed?
    save_screenshot
    save_page
  end
end
