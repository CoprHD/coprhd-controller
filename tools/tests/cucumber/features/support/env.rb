require 'yaml'
require 'json'
require 'rest-client'
require 'rspec/expectations'
require 'selenium-webdriver'
require 'capybara/cucumber'
require 'capybara/poltergeist'

CONFIG = YAML.load(File.read('settings.yml'))
world = "#{CONFIG['world'].capitalize}World"
setup = "#{CONFIG['setup'].capitalize}Setup"

World do
  the_world = Object.const_get(world).new
  the_world.extend Object.const_get(setup)

  the_world
end

APP_HOST = ENV['CUCUMBER_APP_HOST']
APP_ROOT = 'root'
APP_PASSWORD = ENV['CUCUMBER_APP_PASSWORD']
REMOTE_HOST = ENV['CUCUMBER_REMOTE_HOST']

if ENV['IN_BROWSER']
  # On demand: non-headless tests via Selenium/WebDriver
  # To run the scenarios in browser (default: Firefox), use the following command line:
  # IN_BROWSER=true bundle exec cucumber
  # or (to have a pause of 1 second between each step):
  # IN_BROWSER=true PAUSE=1 bundle exec cucumber

  caps = Selenium::WebDriver::Remote::Capabilities.chrome("chromeOptions" => {"args" => [ "--disable-web-security" ]})
  Capybara.register_driver :remote do |app|
    Capybara::Selenium::Driver.new(app, :browser => :remote, :url => "http://#{REMOTE_HOST}:4444/wd/hub", :desired_capabilities => caps)
  end
  Capybara.default_driver    = :remote
  Capybara.javascript_driver = :remote

  AfterStep do
    (ENV['PAUSE'] || 5).to_i
  end
else
  # DEFAULT: headless tests with poltergeist/PhantomJS
  Capybara.register_driver :poltergeist do |app|
    options = { window_size: [1280, 1024], js_errors: false, debug: false, phantomjs_logger: StringIO.new }
    Capybara::Poltergeist::Driver.new(app, options)
  end
  Capybara.default_driver = :poltergeist
  Capybara.javascript_driver = :poltergeist
end

Capybara.app_host = "https://#{APP_HOST}"
Capybara.default_max_wait_time = 30
Capybara.default_selector = :css
