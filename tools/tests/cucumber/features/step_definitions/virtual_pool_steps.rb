Given(/^the customer has a virtual pool without SRDF$/) do
  # Assume vpool with name containing "TARGET" is not configured with SRDF
  target_vpools = find_vpools { |p| p['name'] =~ /TARGET/ }
  expect(target_vpools.size).to be >= 1

  @vpool_subject = target_vpools.first
end

# TODO Inspect each vpool and check the remote protection properties
Given(/^the customer has a virtual pool with SRDF$/) do
  target_vpools = find_vpools do |p|
    p['name'] == 'vpool'
  end
  expect(target_vpools.size).to be >= 1

  @vpool_subject = target_vpools.first
end

def auth_token
  return @auth_token if @auth_token

  # Login / Get auth token
  response = RestClient::Request.execute(method: :get,
                     url: "https://#{APP_HOST}:4443/login",
                     user: APP_ROOT,
                     password: APP_PASSWORD,
                     verify_ssl: false)
  @auth_token = response.headers[:x_sds_auth_token]
end

def find_vpools(&block)
  # Get list of vpools
  response = JSON.parse(RestClient::Request.execute(
    method: :get,
    url: "https://#{APP_HOST}:4443/block/vpools",
    verify_ssl: false,
    headers: {
      'X-SDS-AUTH-TOKEN' => auth_token,
      accept: :json
    }))

  if block_given?
    response['virtualpool'].select &block
  else
    response['virtualpool']
  end
end
