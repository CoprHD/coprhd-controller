Given(/^the customer has a virtual pool without SRDF$/) do
  # Assume vpool with name containing "TARGET" is not configured with SRDF
  # Get a list of vpools

  # Login / Get auth token
  response = RestClient::Request.execute(method: :get,
                     url: "https://#{APP_HOST}:4443/login",
                     user: APP_ROOT,
                     password: APP_PASSWORD,
                     verify_ssl: false)
  auth_token = response.headers[:x_sds_auth_token]

  # Get list of vpools
  response = JSON.parse(RestClient::Request.execute(
    method: :get,
    url: "https://#{APP_HOST}:4443/block/vpools",
    verify_ssl: false,
    headers: {
      'X-SDS-AUTH-TOKEN' => auth_token,
      accept: :json
    }))

  target_vpools = response['virtualpool'].select { |p| p['name'] =~ /TARGET/ }
  expect(target_vpools.size).to be >= 1

  @vpool_subject = target_vpools.first
end
