/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.auth.service.impl.resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.Principal;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jetty.util.B64Code;
import org.eclipse.jetty.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.auth.AuthenticationManager;
import com.emc.storageos.auth.impl.CassandraTokenManager;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageOSUserDAO;
import com.emc.storageos.model.password.PasswordChangeParam;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authentication.RequestProcessingUtils;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.BasePermissionsHelper;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.security.geo.RequestedTokenHelper;
import com.emc.storageos.security.password.InvalidLoginManager;
import com.emc.storageos.security.password.Password;
import com.emc.storageos.security.password.PasswordUtils;
import com.emc.storageos.security.password.PasswordValidator;
import com.emc.storageos.security.password.ValidatorFactory;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.services.util.SecurityUtils;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * Main resource class for all authentication api
 */
@Path("/")
public class AuthenticationResource {
	private static final Logger _log = LoggerFactory.getLogger(AuthenticationResource.class);
	private static final String EVENT_SERVICE_TYPE = "auth";
	public static final String AUTH_FORM_LOGIN_TOKEN_PARAM = "auth-token";
	public static final String FROM_PORTAL = "portal";
	public static final String DUMMY_HOST_NAME = "vipr";

	private static final String UTF8_ENCODING = "UTF-8";
	private static final String AUTH_FORM_LOGIN_PAGE_ACTION = "action=\"";
	public static final String AUTH_REALM_NAME = "ViPR";
	private static final String FORM_LOGIN_DOC_PATH = "storageos-authsvc/docs/login.html";

	private static final String FORM_CHANGE_PASSWORD_DOC_PATH = "storageos-authsvc/docs/changePassword.html"; // NOSONAR
																												// ("Variable
																												// NAME
																												// contains
																												// substring
																												// password,
																												// but
																												// no
																												// sensitive
																												// information
																												// in
																												// the
																												// value.")

	private static final String FORM_LOGIN_HTML_ENT = "(<input\\s*id=\"username\")";
	private static final String FORM_LOGIN_AUTH_ERROR_ENT = "<div class=\"alert alert-danger\">{0}</div>";
	private static final String FORM_SUCCESS_ENT = "<div class=\"alert alert-success\">{0}</div>";
	private static final String FORM_INFO_ENT = "<div class=\"alert alert-info\">{0}</div>";
	private static final String FORM_LOGIN_BAD_CREDS_ERROR = "Invalid Username or Password";

	private static final String FORM_NOT_MATCH_CONFIRM_PASSWORD = "password don't match confirm password"; // NOSONAR
																											// ("Variable
																											// NAME
																											// contains
																											// substring
																											// password,
																											// but
																											// no
																											// sensitive
																											// information
																											// in
																											// the
																											// value.")
	private static final String FORM_INVALID_LOGIN_LIMIT_ERROR = "Exceeded invalid login limit";
	private static final String FORM_INVALID_AUTH_TOKEN_ERROR = "Remote VDC token has either expired or was issued to a local user that is restricted to their home VDC only.  Please relogin.";
	private static final String FORM_LOGIN_POST_NO_SERVICE_ERROR = "The POST request to formlogin does not have service query parameter";
	private static final String SERVICE_URL_FORMAT_ERROR = "The provided service URI has invalid format";

	private static final String LOGIN_BANNER_KEY = "system_login_banner";

	private static String _cachedLoginPagePart1;
	private static String _cachedLoginPagePart2;

	private static String _cachedChangePasswordPagePart1;
	private static String _cachedChangePasswordPagePart2;

	private static String HEADER_PRAGMA = "Pragma";
	private static String HEADER_PRAGMA_VALUE = "no-cache";

	private static CacheControl _cacheControl = null;
	@Autowired
	private RequestedTokenHelper tokenNotificationHelper;

	static {
		_cacheControl = new CacheControl();
		_cacheControl.setNoCache(true);
		_cacheControl.setNoStore(true);

		String[] loginPageParts = getStaticPageParts(FORM_LOGIN_DOC_PATH);
		_cachedLoginPagePart1 = loginPageParts[0];
		_cachedLoginPagePart2 = loginPageParts[1];

		String[] changePasswordPageParts = getStaticPageParts(FORM_CHANGE_PASSWORD_DOC_PATH);
		_cachedChangePasswordPagePart1 = changePasswordPageParts[0];
		_cachedChangePasswordPagePart2 = changePasswordPageParts[1];
	}

	private static String[] getStaticPageParts(String docPath) {
		String pageParts[] = new String[2];

		String loginPage = null;
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		InputStream is = loader.getResourceAsStream(docPath);
		if (is == null) {
			_log.error("Failed to find the custom login page");
		} else {
			StringBuilder sb = new StringBuilder();
			String line;
			BufferedReader br = null;
			try {
				br = new BufferedReader(new InputStreamReader(is));
				while ((line = br.readLine()) != null) {
					if (line.contains("navbar-brand") || line.contains("copyright")) {
						Locale locale = Locale.getDefault();
						String currCountry = locale.getCountry();

						/**
						 * Check if the Locale Country and Language is not China
						 * and Chinese then return "Dell EMC  ViPR Controller"
						 * else just "EMC ViPR Controller"
						 */
						if (!currCountry.equals("CN")) {
							if (line.contains("navbar-brand"))
								line = "<div class=\"navbar-brand\">Dell EMC ViPR Controller</div>";
							else if (line.contains("copyright"))
								line = "<div id=\"copyright\" style=\"padding-top: 10px;\">&copy; 2016 Dell EMC Corporation. All Rights Reserved.</div>";
						}
					}
					sb.append(line);
				}
				loginPage = sb.toString();
				int beforeIndex = loginPage.indexOf(AUTH_FORM_LOGIN_PAGE_ACTION);
				if (beforeIndex >= 0) {
					pageParts[0] = loginPage.substring(0, beforeIndex);
					String remainingChunk = loginPage.substring(beforeIndex + AUTH_FORM_LOGIN_PAGE_ACTION.length());
					int afterIndex = remainingChunk.indexOf("\"");
					if (afterIndex >= 0) {
						pageParts[1] = remainingChunk.substring(afterIndex + 1);
					}
				}

			} catch (IOException e) {
				_log.error("Failed to load custom login template file", e);
			} finally {
				if (br != null) {
					try {
						br.close();
					} catch (IOException e) {
						_log.error("Failed to clean up the BufferedReader resource");
					}
				}
			}
		}

		return pageParts;
	}

	@Autowired
	protected DbClient _dbClient;

	@Autowired
	protected InvalidLoginManager _invLoginManager;

	@Autowired
	protected AuthenticationManager _authManager;

	@Autowired
	protected CassandraTokenManager _tokenManager;

	@Autowired
	protected BasePermissionsHelper _permissionsHelper;

	@Autowired
	protected AuditLogManager _auditMgr;

	@Autowired
	protected PasswordUtils _passwordUtils;

	@Autowired
	protected Map<String, StorageOSUser> _localUsers;

	@Context
	SecurityContext sc;

	@XmlRootElement
	public static class LoggedIn {
		public String user;

		LoggedIn() {
		}

		LoggedIn(String u) {
			user = u;
		}
	}

	@XmlRootElement(name = "LoggedOut")
	public static class LoggedOut {
		public String user;

		LoggedOut() {
		}

		LoggedOut(String u) {
			user = u;
		}
	}

	/**
	 * Create and return a Cookie object with the token
	 * 
	 * @param token
	 * @param setMaxAge
	 *            if true sets the age of the cookie to maxlife of token. Else
	 *            defaults to browser session
	 * @return
	 */
	private NewCookie createWsCookie(String cookieName, String token, boolean setMaxAge, String userAgent) {
		// For IE, we need to use "expires" to support rememberme functionality
		String ieExpires = "";
		int maxAge = setMaxAge ? _tokenManager.getMaxTokenLifeTimeInSecs() : NewCookie.DEFAULT_MAX_AGE;

		if (setMaxAge && StringUtils.contains(userAgent, "MSIE")) {
			ieExpires = ";expires=" + getExpiredTimeGMT(maxAge);
			_log.debug("Expires: " + ieExpires);
		}
		if (token != null && !token.isEmpty()) {
			return new NewCookie(cookieName, token + ";HttpOnly" + ieExpires, null, null, null, maxAge, true);
		}
		return null;
	}

	/**
	 * Adds a special key in the end to identify as the request is redirected
	 * back from authsvc
	 * 
	 * @param service
	 * @return
	 * @throws URISyntaxException
	 */
	private URI getServiceURLForRedirect(String service, HttpServletRequest request)
			throws UnsupportedEncodingException, URISyntaxException {
		String serviceDecoded = URLDecoder.decode(service, UTF8_ENCODING);
		_log.debug("Original service = " + serviceDecoded);
		String newService = "";
		URI uriObject = new URI(serviceDecoded);

		String scheme = uriObject.getScheme();
		if (StringUtils.isBlank(scheme)) {
			scheme = "https";
		}

		// newservice will be constructed by replacing the host component in the
		// original service by
		// serverName obtained from the HttpServletRequest.
		newService = scheme + "://" + request.getServerName();

		int port = uriObject.getPort();
		if (port > 0) {
			newService += ":" + port;
		}

		String path = uriObject.getPath();
		if (StringUtils.isNotBlank(path)) {
			newService += (path.startsWith("/") ? "" : "/") + path;
		}

		String query = uriObject.getQuery();
		if (query != null && !query.isEmpty()) {
			newService += "?" + query;
		}

		if (newService.contains("?")) {
			newService = String.format("%s&%s", newService, RequestProcessingUtils.REDIRECT_FROM_AUTHSVC);
		} else {
			newService = String.format("%s?%s", newService, RequestProcessingUtils.REDIRECT_FROM_AUTHSVC);
		}

		// Append the fragments if any. Fragments are used to the identify
		// particular service catalog. This is done to support the functionality
		// of redirecting directly to a particular service catalog upon the
		// the successful authentication.
		if (StringUtils.isNotBlank(uriObject.getFragment())) {
			newService += "#" + uriObject.getFragment();
		}

		newService = SecurityUtils.stripXSS(newService);

		_log.debug("Updated service = " + newService);
		return URI.create(newService);
	}

	/**
	 * Authenticates the user and obtains authentication token to use in
	 * subsequent api calls. If valid X-SDS-AUTH-TOKEN is provided, that will be
	 * used instead of creating the new authentication token. Setting the
	 * queryParam "using-cookies" to "true" sets the following cookies in the
	 * response.
	 *
	 * <li>X-SDS-AUTH-TOKEN</li>
	 * <li>HttpOnly</li>
	 * <li>Version</li>
	 * <li>Max-Age</li>
	 * <li>Secure</li>
	 *
	 * @brief User login
	 * @param httpRequest
	 *            request object (contains basic authentication header with
	 *            credentials)
	 * @param servletResponse
	 *            Response object
	 * @param service
	 *            Optional query parameter, to specify a URL to redirect to on
	 *            successful authentication
	 * @prereq none
	 * @return Response
	 * @throws IOException
	 * @throws URISyntaxException
	 */
	@GET
	@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
	@Path("login")
	public Response getLoginToken(@Context HttpServletRequest httpRequest, @Context HttpServletResponse servletResponse,
			@QueryParam("service") String service) throws IOException {
		String clientIP = _invLoginManager.getClientIP(httpRequest);
		isClientIPBlocked(clientIP);
		boolean setCookie = RequestProcessingUtils.isRequestingQueryParam(httpRequest,
				RequestProcessingUtils.REQUESTING_COOKIES);
		LoginStatus loginStatus = tryLogin(httpRequest, service, setCookie, servletResponse, false);
		if (loginStatus.loggedIn()) {
			// Clean up the invalid login records if any
			_invLoginManager.removeInvalidRecord(clientIP);
			try {
				Response resp = buildLoginResponse(service, null, setCookie, true, loginStatus, httpRequest);
				return resp;
			} catch (URISyntaxException ex) {
				throw APIException.badRequests.serviceURLBadSyntax();
			}
		}
		// The authentication failed. Make note of that in the ZK only if the
		// user credentials are provided.
		if (loginStatus.areCredentialsProvided()) {
			_invLoginManager.markErrorLogin(clientIP);
		}
		return requestCredentials();
	}

	/**
	 * 
	 * Generates a response ready to be returned by REST methods in this
	 * resource. The response will either be an ok or 302 depending on the
	 * parameters
	 * 
	 * @param service
	 *            optional, service to forward to. if null, reponse will be 200.
	 * @param setCookie,
	 *            whether or not to set the cookie in the response
	 * @param setMaxAge,
	 *            whether ot not to set the max age on the cookie
	 * @param loginStatus,
	 *            login status containing the token to add
	 * @return the response
	 * @throws UnsupportedEncodingException
	 * @throws URISyntaxException
	 */
	private Response buildLoginResponse(String service, String source, boolean setCookie, boolean setMaxAge,
			LoginStatus loginStatus, HttpServletRequest request)
					throws UnsupportedEncodingException, URISyntaxException {
		String authTokenName = source != null && source.equals(FROM_PORTAL)
				? RequestProcessingUtils.AUTH_PORTAL_TOKEN_HEADER : RequestProcessingUtils.AUTH_TOKEN_HEADER;

		Response.ResponseBuilder resp = null;
		if (service != null && !service.isEmpty()) {
			resp = Response.status(302).location(getServiceURLForRedirect(service, request)).header(authTokenName,
					loginStatus.getToken());
		} else {
			resp = Response.ok(new LoggedIn(loginStatus.getUser())).header(authTokenName, loginStatus.getToken());
		}

		if (setCookie) {
			return resp.cookie(
					createWsCookie(authTokenName, loginStatus.getToken(), setMaxAge, request.getHeader("user-agent")))
					.build();
		} else {
			return resp.build();
		}
	}

	/**
	 * Try to login the user. If not generate the form login page
	 *
	 * @brief INTERNAL USE
	 * @param httpRequest
	 *            request object (contains basic authentication header with
	 *            credentials)
	 * @param servletResponse
	 *            Response object
	 * @param service
	 *            Optional query parameter, to specify a URL to redirect to on
	 *            successful authentication
	 * @return form login page if the user is not authenticated. OK status
	 *         otherwise
	 * @throws UnsupportedEncodingException
	 * @prereq none
	 * @throws IOException
	 */
	@GET
	@Produces({ MediaType.TEXT_HTML })
	@Path("formlogin")
	public Response getFormLogin(@Context HttpServletRequest httpRequest, @Context HttpServletResponse servletResponse,
			@QueryParam("service") String service, @QueryParam("src") String source)
					throws UnsupportedEncodingException, IOException {
		String loginError = null;
		try {
			LoginStatus loginStatus = tryLogin(httpRequest, service, true, servletResponse, true);
			if (loginStatus.loggedIn()) {
				return buildLoginResponse(service, source, true, true, loginStatus, httpRequest);
			}
		} catch (URISyntaxException e) {
			loginError = SERVICE_URL_FORMAT_ERROR;
		} catch (Exception e) {
			loginError = MessageFormat.format(FORM_LOGIN_AUTH_ERROR_ENT, e.getMessage());
		}

		if (service == null) {
			String port = httpRequest.getServerPort() != -1 ? ":" + httpRequest.getServerPort() : "";

			// Dummy Host Name will be replaced by the actual host name during
			// redirection
			service = httpRequest.getScheme() + "://" + DUMMY_HOST_NAME + port + "/login";
		}

		String formLP = getFormLoginPage(service, source, httpRequest.getServerName(), loginError);
		if (formLP != null) {
			return Response.ok(formLP).type(MediaType.TEXT_HTML).cacheControl(_cacheControl)
					.header(HEADER_PRAGMA, HEADER_PRAGMA_VALUE).build();
		} else {
			_log.error("Could not generate custom (form) login page");
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}
	}

	/**
	 * display fromChangePassword page. it contains currently enabled password
	 * rules prompt information to guide user input the new password.
	 *
	 * @brief INTERNAL USE
	 *
	 * @param httpRequest
	 * @param servletResponse
	 * @param service
	 * @param source
	 * @return
	 * @throws UnsupportedEncodingException
	 * @throws IOException
	 */
	@GET
	@Produces({ MediaType.TEXT_HTML })
	@Path("formChangePassword")
	public Response getChangePasswordForm(@Context HttpServletRequest httpRequest,
			@Context HttpServletResponse servletResponse, @QueryParam("service") String service,
			@QueryParam("src") String source) throws UnsupportedEncodingException, IOException {

		String loginError = null;
		if (service == null) {
			String port = httpRequest.getServerPort() != -1 ? ":" + httpRequest.getServerPort() : "";

			// Dummy Host Name will be replaced by the actual host name during
			// redirection
			service = httpRequest.getScheme() + "://" + DUMMY_HOST_NAME + port + "/login";
		}
		String formLP = getFormChangePasswordPage(service, source, httpRequest.getServerName(), loginError);
		if (formLP != null) {
			return Response.ok(formLP).type(MediaType.TEXT_HTML).cacheControl(_cacheControl)
					.header(HEADER_PRAGMA, HEADER_PRAGMA_VALUE).build();
		} else {
			_log.error("Could not generate custom (form) changePassword page");
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}
	}

	/**
	 * Requests a proxy authentication token corresponding to the user in the
	 * Context A user must already be authenticated and have an auth-token in
	 * order to be able to get a proxy token for itself. This proxy token never
	 * expires and can be used with the proxy user's authentication token to
	 * make proxy user work on behalf of the user in the context.
	 *
	 * @brief Requests user's proxy authentication token.
	 * @return Response
	 * @throws IOException
	 */
	@GET
	@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
	@Path("proxytoken")
	public Response getProxyToken() {
		_log.debug("Requesting proxy token");
		StorageOSUser user = getUserFromContext();
		if (user == null) {
			_log.error("Unauthenticated request for a proxytoken");
			return requestCredentials();
		}
		String proxyToken = _tokenManager.getProxyToken(user);
		Response.ResponseBuilder resp = Response.ok().header(RequestProcessingUtils.AUTH_PROXY_TOKEN_HEADER,
				proxyToken);
		return resp.build();
	}

	/**
	 * Logs out a user's authentication token and optionally other related
	 * tokens and proxytokens
	 * 
	 * @brief User logout
	 * @param force
	 *            Optional query parameter, if set to true, will delete all
	 *            active tokens for the user, excluding proxy tokens. Otherwise,
	 *            invalidates only the token from the request Default value:
	 *            false
	 * @param includeProxyTokens
	 *            Optional query parameter, if set to true and combined with
	 *            force, will delete all active tokens, including proxy tokens
	 *            for the user. Default value: false
	 * @param username
	 *            Optional query parameter, if supplied, the user pointed by the
	 *            username will be logged out instead of the currently logged in
	 *            user (SECURITY_ADMIN role required to use this parameter)
	 * @param notifyVDCs
	 *            if set to true, will look if the token was copied to other
	 *            VDCs and notify them
	 * @return Response
	 * @prereq none
	 * @throws IOException
	 */
	@GET
	@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
	@Path("logout")
	public Response logout(@DefaultValue("false") @QueryParam("force") boolean force,
			@DefaultValue("false") @QueryParam("proxytokens") boolean includeProxyTokens,
			@QueryParam("username") String username,
			@DefaultValue("true") @QueryParam("notifyvdcs") boolean notifyVDCs) {
		StorageOSUser user = getUserFromContext();
		if (user != null) {
			if (StringUtils.isNotBlank(username)) {
				boolean isTargetUserLocal = _localUsers.containsKey(username);
				boolean hasRestrictedSecurityAdmin = _permissionsHelper.userHasGivenRole(user,
						URI.create(user.getTenantId()), Role.RESTRICTED_SECURITY_ADMIN);
				boolean hasSecurityAdmin = _permissionsHelper.userHasGivenRole(user, URI.create(user.getTenantId()),
						Role.SECURITY_ADMIN);
				// if the user is security admin or restricted sec admin (if the
				// user to be logged out is just local)
				if (hasSecurityAdmin || (isTargetUserLocal && hasRestrictedSecurityAdmin)) {
					// boot the user out
					_tokenManager.deleteAllTokensForUser(username, includeProxyTokens);
					if (notifyVDCs && !isTargetUserLocal) {
						// broadcast the call to other vdcs if this is not a
						// local user
						tokenNotificationHelper.broadcastLogoutForce(user.getToken(), username);
					}
					return Response.ok(new LoggedOut(username)).build();
				} else {
					throw APIException.forbidden.userNotPermittedToLogoutAnotherUser(user.getUserName());
				}
			} else {
				if (force) {
					// delete all tokens for this user
					_tokenManager.deleteAllTokensForUser(user.getUserName(), includeProxyTokens);
					if (notifyVDCs && !user.isLocal()) {
						tokenNotificationHelper.broadcastLogoutForce(user.getToken(), null);
					}
				} else {
					// delete only the current token
					_tokenManager.deleteToken(user.getToken());
					if (notifyVDCs && !user.isLocal()) {
						// if other VDCs have a copy of this token, they need to
						// be notified.
						tokenNotificationHelper.notifyExternalVDCs(user.getToken());
					}
				}
				return Response.ok(new LoggedOut(user.getUserName())).build();
			}
		}
		throw APIException.unauthorized.tokenNotFoundOrInvalidTokenProvided();
	}

	/**
	 * Change a local user's password without login.
	 * 
	 * This interface need be provided with clear text old password and new
	 * password
	 * 
	 * @brief Change your password
	 * @throws APIException
	 */
	@PUT
	@Path("change-password")
	@Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
	@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
	public Response changePassword(@Context HttpServletRequest httpRequest,
			@Context HttpServletResponse servletResponse,
			@DefaultValue("true") @QueryParam("logout_user") boolean logout, PasswordChangeParam passwordChange) {

		String clientIP = _invLoginManager.getClientIP(httpRequest);
		isClientIPBlocked(clientIP);

		// internal call to password service
		Response response = _passwordUtils.changePassword(passwordChange, false);
		if (response.getStatus() != Status.OK.getStatusCode()) {
			String message = response.getEntity().toString();
			if (message.contains(_invLoginManager.OLD_PASSWORD_INVALID_ERROR)) {
				_invLoginManager.markErrorLogin(clientIP);
			}
		} else { // change password successfully, do some cleanup
			try {
				_invLoginManager.removeInvalidRecord(clientIP);
				if (logout) {
					_log.info("logout active sessions for: " + passwordChange.getUsername());
					_tokenManager.deleteAllTokensForUser(passwordChange.getUsername(), false);
				}
			} catch (Exception cleanupException) {
				_log.error("clean up failed: {0}", cleanupException.getMessage());
			}
		}

		return response;
	}

	/**
	 * Check to see if a proposed password change parameter satisfies ViPR's
	 * password content rules
	 * 
	 * the api can be called before login
	 * 
	 * @brief Validate a proposed password for a user
	 * @prereq none
	 * @throws APIException
	 */
	@POST
	@Path("/validate-password-change")
	@Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
	@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
	public Response validateUserPasswordForChange(@Context HttpServletRequest httpRequest,
			PasswordChangeParam passwordParam) {
		String clientIP = _invLoginManager.getClientIP(httpRequest);
		isClientIPBlocked(clientIP);

		// internal call to password service
		Response response = _passwordUtils.changePassword(passwordParam, true);
		return response;
	}

	/**
	 * Authenticates a user with credentials provided in the form data of the
	 * request. This method is for internal use by formlogin page.
	 * 
	 * @brief INTERNAL USE
	 * @return On successful authentication the client will be redirected to the
	 *         provided service.
	 * @throws IOException
	 */
	@POST
	@Produces({ MediaType.APPLICATION_XML, MediaType.TEXT_HTML })
	@Consumes("application/x-www-form-urlencoded")
	@Path("formChangePassword")
	public Response changePassword(@Context HttpServletRequest request, @Context HttpServletResponse servletResponse,
			@QueryParam("service") String service, @QueryParam("src") String source,
			@DefaultValue("true") @QueryParam("logout_user") boolean logout, MultivaluedMap<String, String> formData)
					throws IOException {
		boolean isChangeSuccess = false;
		String message = null;
		String clientIP = _invLoginManager.getClientIP(request);

		String userName = formData.getFirst("username");
		String userOldPassw = formData.getFirst("oldPassword");
		String userPassw = formData.getFirst("password");
		String confirmPassw = formData.getFirst("confirmPassword");

		if (_invLoginManager.isTheClientIPBlocked(clientIP) == true) {
			_log.error("The client IP is blocked for too many invalid login attempts: " + clientIP);
			int minutes = _invLoginManager.getTimeLeftToUnblock(clientIP);
			message = String.format("%s.<br>Will be cleared within %d minutes", FORM_INVALID_LOGIN_LIMIT_ERROR,
					minutes);
		} else if (userName == null || userOldPassw == null || userPassw == null || confirmPassw == null) {
			message = FORM_LOGIN_BAD_CREDS_ERROR;
		} else if (!userPassw.equals(confirmPassw)) {
			message = FORM_NOT_MATCH_CONFIRM_PASSWORD;
		} else {
			PasswordChangeParam passwordChange = new PasswordChangeParam();
			passwordChange.setUsername(userName);
			passwordChange.setOldPassword(userOldPassw);
			passwordChange.setPassword(userPassw);
			Response response = _passwordUtils.changePassword(passwordChange, false);
			if (response.getStatus() != Status.OK.getStatusCode()) {
				message = response.getEntity().toString();
				message = message.replaceAll(".*<details>(.*)</details>.*", "$1");
			} else {
				isChangeSuccess = true;
				message = "change password for user " + userName + " successful.";
			}
		}

		String formLP = null;
		if (!isChangeSuccess) {
			formLP = getFormChangePasswordPage(service, source, request.getServerName(),
					MessageFormat.format(FORM_LOGIN_AUTH_ERROR_ENT, message));
			if (message.contains(_invLoginManager.OLD_PASSWORD_INVALID_ERROR)) {
				_invLoginManager.markErrorLogin(clientIP);
			}
		} else { // change password successfully, do some cleanup
			try {
				formLP = getFormLoginPage(service, source, request.getServerName(),
						MessageFormat.format(FORM_SUCCESS_ENT, message));
				_invLoginManager.removeInvalidRecord(clientIP);
				if (logout) {
					_log.info("logout active sessions for: " + userName);
					_tokenManager.deleteAllTokensForUser(userName, false);
				}
			} catch (Exception cleanupException) {
				_log.error("clean up failed: {0}", cleanupException.getMessage());
			}
		}
		if (formLP != null) {
			return Response.ok(formLP).type(MediaType.TEXT_HTML).cacheControl(_cacheControl)
					.header(HEADER_PRAGMA, HEADER_PRAGMA_VALUE).build();
		} else {
			_log.error("Could not generate custom (form) login page");
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}

	}

	/**
	 * Authenticates a user with credentials provided in the form data of the
	 * request. This method is for internal use by formlogin page.
	 * 
	 * @brief INTERNAL USE
	 *
	 * @param request
	 *            the login request from the client.
	 * @param servletResponse
	 *            the response to be sent out to client.
	 * @param service
	 *            to be used to redirect on successful authentication.
	 * @param source
	 *            to be used to identify if the request is coming from portal or
	 *            some other client.
	 * @param fragment
	 *            to used to identify the service catalog to redirect on
	 *            successful authentication.
	 *
	 * @return On successful authentication the client will be redirected to the
	 *         provided service.
	 * @throws IOException
	 */
	@POST
	@Produces({ MediaType.APPLICATION_XML, MediaType.TEXT_HTML })
	@Consumes("application/x-www-form-urlencoded")
	@Path("formlogin")
	public Response formlogin(@Context HttpServletRequest request, @Context HttpServletResponse servletResponse,
			@QueryParam("service") String service, @QueryParam("src") String source,
			@QueryParam("fragment") String fragment, MultivaluedMap<String, String> formData) throws IOException {

		boolean isPasswordExpired = false;
		String loginError = null;
		if (service == null || service.isEmpty()) {
			loginError = FORM_LOGIN_POST_NO_SERVICE_ERROR;
		}

		String updatedService = service;
		if (StringUtils.isNotBlank(service) && StringUtils.isNotBlank(fragment)) {
			updatedService = updatedService + "#" + fragment;
		}

		// Check invalid login count from the client IP
		boolean updateInvalidLoginCount = true;
		String clientIP = _invLoginManager.getClientIP(request);
		_log.debug("Client IP: {}", clientIP);
		if (_invLoginManager.isTheClientIPBlocked(clientIP) == true) {
			_log.error("The client IP is blocked for too many invalid login attempts: " + clientIP);
			int minutes = _invLoginManager.getTimeLeftToUnblock(clientIP);
			loginError = String.format("%s.<br>Will be cleared within %d minutes", FORM_INVALID_LOGIN_LIMIT_ERROR,
					minutes);
			updateInvalidLoginCount = false;
		}

		if (null == loginError) {
			String rememberMeStr = formData.getFirst("remember");
			boolean rememberMe = StringUtils.isNotBlank(rememberMeStr) && rememberMeStr.equalsIgnoreCase("true");
			// Look for a token passed in the form. If so, validate it and
			// return it back
			// as a cookie if valid. Else, continue with the normal flow of
			// formlogin to validate
			// credentials
			String tokenFromForm = formData.getFirst(AUTH_FORM_LOGIN_TOKEN_PARAM);
			if (StringUtils.isNotBlank(tokenFromForm)) {
				try {
					StorageOSUserDAO userDAOFromForm = _tokenManager.validateToken(tokenFromForm);
					if (userDAOFromForm != null) {
						_log.debug("Form login was posted with valid token");
						return buildLoginResponse(updatedService, source, true, rememberMe,
								new LoginStatus(userDAOFromForm.getUserName(), tokenFromForm, false), request);
					}
					_log.error("Auth token passed to this formlogin could not be validated and returned null user");
					loginError = FORM_INVALID_AUTH_TOKEN_ERROR;
				} catch (APIException ex) {
					// It is possible that validateToken would throw if the
					// passed in token is unparsable
					// Unlike the regular use case for validatetoken which is
					// done inside api calls, here we are
					// building a response to a web page, so we need to catch
					// this and let the rest of this method
					// proceed which will result in requesting new credentials.
					loginError = FORM_INVALID_AUTH_TOKEN_ERROR;
					_log.error("Auth token passed to this formlogin could not be validated.  Exception: ", ex);
				} catch (URISyntaxException e) {
					loginError = SERVICE_URL_FORMAT_ERROR;
				}
			}

			UsernamePasswordCredentials credentials = getFormCredentials(formData);
			if (null == loginError) {
				loginError = FORM_LOGIN_BAD_CREDS_ERROR;
			}
			try {
				if (credentials != null) {
					StorageOSUserDAO user = authenticateUser(credentials);
					if (user != null) {
						validateLocalUserExpiration(credentials);
						String token = _tokenManager.getToken(user);
						if (token == null) {
							_log.error("Could not generate token for user: {}", user.getUserName());
							auditOp(null, null, OperationTypeEnum.AUTHENTICATION, false, null,
									credentials.getUserName());
							return Response.status(Status.INTERNAL_SERVER_ERROR).build();
						}
						_log.debug("Redirecting to the original service: {}", updatedService);
						_invLoginManager.removeInvalidRecord(clientIP);

						auditOp(URI.create(user.getTenantId()), URI.create(user.getUserName()),
								OperationTypeEnum.AUTHENTICATION, true, null, credentials.getUserName());

						// If remember me check box is on, set the expiration
						// time.
						return buildLoginResponse(updatedService, source, true, rememberMe,
								new LoginStatus(user.getUserName(), token, null != credentials), request);
					}
				} else {
					// Do not update the invalid login count for this client IP
					// if credentials are not provided
					updateInvalidLoginCount = false;
				}
			} catch (APIException e) {
				loginError = e.getMessage();
				if (loginError.contains("expired")) {
					isPasswordExpired = true;
				}
			} catch (URISyntaxException e) {
				loginError = SERVICE_URL_FORMAT_ERROR;
			}
		}
		// If we are here, request another login with appropriate error message
		// Mark this invalid login as a failure in ZK from the client IP
		if (updateInvalidLoginCount) {
			_invLoginManager.markErrorLogin(clientIP);
		}
		if (null != loginError) {
			_log.error(loginError);
		}

		String formLP = null;
		if (isPasswordExpired) {
			formLP = getFormChangePasswordPage(updatedService, source, request.getServerName(),
					MessageFormat.format(FORM_LOGIN_AUTH_ERROR_ENT, loginError));
		} else {
			formLP = getFormLoginPage(updatedService, source, request.getServerName(),
					MessageFormat.format(FORM_LOGIN_AUTH_ERROR_ENT, loginError));
		}

		auditOp(null, null, OperationTypeEnum.AUTHENTICATION, false, null, getFormCredentials(formData).getUserName());
		if (formLP != null) {
			return Response.ok(formLP).type(MediaType.TEXT_HTML).cacheControl(_cacheControl)
					.header(HEADER_PRAGMA, HEADER_PRAGMA_VALUE).build();
		} else {
			_log.error("Could not generate custom (form) login page");
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}
	}

	/**
	 * See if the user is already logged in or try to login the user if
	 * credentials were supplied. Return authentication status
	 * 
	 * @param httpRequest
	 * @param service
	 * @param setCookie
	 * @param servletResponse
	 * @param tokenOnly
	 *            false if either token or credentials can be used to attempt
	 *            the login. True if only token is accepted.
	 * @return LoginStatus of the user.
	 * @throws UnsupportedEncodingException
	 * @throws IOException
	 */
	private LoginStatus tryLogin(HttpServletRequest httpRequest, String service, boolean setCookie,
			HttpServletResponse servletResponse, boolean tokenOnly) throws UnsupportedEncodingException, IOException {
		String newToken = null;
		String userName = null;
		_log.debug("Logging in");
		UsernamePasswordCredentials credentials = tokenOnly ? null : getCredentials(httpRequest);
		if (credentials == null) {
			// check if we already have a user context
			StorageOSUser user = getUserFromContext();
			if (user != null) {
				newToken = user.getToken();
				userName = user.getName();
				_log.debug("Logged in with user from context");
			}
		} else {
			StorageOSUserDAO user = authenticateUser(credentials);
			if (user != null) {
				validateLocalUserExpiration(credentials);
				newToken = _tokenManager.getToken(user);
				if (newToken == null) {
					_log.error("Could not generate token for user: {}", user.getUserName());
					throw new IllegalStateException(
							MessageFormat.format("Could not generate token for user: {}", user.getUserName()));
				}
				userName = user.getUserName();

				auditOp(URI.create(user.getTenantId()), URI.create(user.getUserName()),
						OperationTypeEnum.AUTHENTICATION, true, null, credentials.getUserName());
			} else {
				auditOp(null, null, OperationTypeEnum.AUTHENTICATION, false, null, credentials.getUserName());
			}
		}
		return new LoginStatus(userName, newToken, null != credentials);
	}

	/**
	 * @param credentials
	 *            User credentials to authenticate with
	 * @return the User DAO object if authenticated, otherwise null
	 */
	private StorageOSUserDAO authenticateUser(UsernamePasswordCredentials credentials) {
		StorageOSUserDAO user = _authManager.authenticate(credentials);
		if (user != null) {
			if (null == user.getTenantId() || user.getTenantId().isEmpty()) {
				throw APIException.forbidden.userDoesNotMapToAnyTenancy(user.getUserName());
			}
			_log.debug("Logging in after authentication");
		}
		return user;
	}

	private UsernamePasswordCredentials getFormCredentials(MultivaluedMap<String, String> formData) {
		String userName = SecurityUtils.stripXSS(formData.getFirst("username"));
		String userPassw = formData.getFirst("password");
		if (StringUtils.isNotBlank(userName) && StringUtils.isNotBlank(userPassw)) {
			return new UsernamePasswordCredentials(userName, userPassw);
		} else {
			_log.debug("The user name and/or password is empty");
		}
		return null;
	}

	/**
	 * Update the static login page with service query parameter
	 * 
	 * @param service
	 *            The requested service
	 * @return
	 */
	private String getFormLoginPage(final String service, final String source, final String serverName,
			final String error) {

		if (StringUtils.isBlank(_cachedLoginPagePart1) || StringUtils.isBlank(_cachedLoginPagePart2)) {
			_log.error("The form login page is not processed correctly, missing part1 and/or part2");
			return null;
		}
		String encodedTargetService = "";
		try {
			URI serviceURL = getServiceURL(service, serverName);
			encodedTargetService = URLEncoder.encode(serviceURL.toString(), "UTF-8");
		} catch (UnsupportedEncodingException | URISyntaxException e) {
			throw APIException.badRequests.unableToEncodeString(service, e);
		}
		StringBuffer sbFinal = new StringBuffer();
		sbFinal.append(error == null ? _cachedLoginPagePart1
				: _cachedLoginPagePart1.replaceAll(FORM_LOGIN_HTML_ENT, error + "$1"));
		sbFinal.append("action=\"./formlogin?service=");
		sbFinal.append(encodedTargetService);

		if (source != null && source.equals(FROM_PORTAL)) {
			sbFinal.append("&src=");
			sbFinal.append(source);
		}

		sbFinal.append("\" ");
		String loginBannerString = Matcher.quoteReplacement(_passwordUtils.getConfigProperty(LOGIN_BANNER_KEY));
		String _cachedLoginPagePart2Tmp = "";
		_cachedLoginPagePart2Tmp = _cachedLoginPagePart2.replaceAll(LOGIN_BANNER_KEY, loginBannerString)
				.replaceAll(Matcher.quoteReplacement("\\\\n"), "<br>");

		sbFinal.append(error == null ? _cachedLoginPagePart2Tmp
				: _cachedLoginPagePart2Tmp.replaceAll(FORM_LOGIN_HTML_ENT, error + "$1"));
		return sbFinal.toString();

	}

	/**
	 * Update the static changePassword page with service query parameter
	 */
	private String getFormChangePasswordPage(final String service, final String source, final String serverName,
			final String error) {

		if (StringUtils.isBlank(_cachedChangePasswordPagePart1)
				|| StringUtils.isBlank(_cachedChangePasswordPagePart2)) {
			_log.error("The form changePassword page is not processed correctly, missing part1 and/or part2");
			return null;
		}
		String encodedTargetService = "";
		try {
			URI serviceURL = getServiceURL(service, serverName);
			encodedTargetService = URLEncoder.encode(serviceURL.toString(), "UTF-8");
		} catch (UnsupportedEncodingException | URISyntaxException e) {
			throw APIException.badRequests.unableToEncodeString(service, e);
		}
		StringBuffer sbFinal = new StringBuffer();

		sbFinal.append(error == null ? _cachedChangePasswordPagePart1
				: _cachedChangePasswordPagePart1.replaceAll(FORM_LOGIN_HTML_ENT, error + "$1"));
		sbFinal.append("action=\"./formChangePassword?service=");
		sbFinal.append(encodedTargetService);

		if (source != null && source.equals(FROM_PORTAL)) {
			sbFinal.append("&src=");
			sbFinal.append(source);
		}

		sbFinal.append("\" ");

		// add password rule prompt information div
		String passwordRuleInfo = MessageFormat.format(FORM_INFO_ENT, getPasswordChangePromptRule());
		_log.info("password rule info: \n" + passwordRuleInfo);
		String newPart2 = _cachedChangePasswordPagePart2.replaceAll(FORM_LOGIN_HTML_ENT, passwordRuleInfo + "$1");

		String loginBannerString = Matcher.quoteReplacement(_passwordUtils.getConfigProperty(LOGIN_BANNER_KEY));
		newPart2 = newPart2.replaceAll(LOGIN_BANNER_KEY, loginBannerString)
				.replaceAll(Matcher.quoteReplacement("\\\\n"), "<br>");

		sbFinal.append(error == null ? newPart2 : newPart2.replaceAll(FORM_LOGIN_HTML_ENT, error + "$1"));
		return sbFinal.toString();
	}

	/**
	 * Pull credentials from the header
	 * 
	 * @param request
	 * @return
	 */
	private UsernamePasswordCredentials getCredentials(HttpServletRequest request) {
		String credentials = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (credentials != null) {
			credentials = credentials.substring(credentials.indexOf(' ') + 1);
			try {
				credentials = B64Code.decode(credentials, StringUtil.__ISO_8859_1);
			} catch (UnsupportedEncodingException e) {
				return null;
			}
			int i = credentials.indexOf(':');

			UsernamePasswordCredentials creds = new UsernamePasswordCredentials(
					SecurityUtils.stripXSS(credentials.substring(0, i)), credentials.substring(i + 1));
			return creds;
		}
		return null;
	}

	/**
	 * Respond with a 401 and challenge for auth
	 * 
	 * @return
	 * @throws IOException
	 */
	private Response requestCredentials() {
		Response response = Response.status(HttpServletResponse.SC_UNAUTHORIZED)
				.header(HttpHeaders.WWW_AUTHENTICATE, "basic realm=\"" + AUTH_REALM_NAME + '"')
				.cacheControl(_cacheControl).header(HEADER_PRAGMA, HEADER_PRAGMA_VALUE).build();
		return response;
	}

	/**
	 * Get StorageOSUser from the security context
	 * 
	 * @return
	 */
	private StorageOSUser getUserFromContext() {
		if (sc != null && sc.getUserPrincipal() != null) {
			Principal principal = sc.getUserPrincipal();
			if (!(principal instanceof StorageOSUser)) {
				throw APIException.forbidden.invalidSecurityContext();
			}
			return (StorageOSUser) principal;
		}
		return null;
	}

	/**
	 * Class to hold the username and auth token pair
	 * 
	 */
	private class LoginStatus {

		private String _user;
		private String _token;
		private boolean _areCredentialsProvided;

		public LoginStatus(final String user, final String token, boolean areCredentialsProvided) {
			_user = user;
			_token = token;
			_areCredentialsProvided = areCredentialsProvided;

		}

		/**
		 * Method to return whether or not the user is logged in
		 * 
		 * @return true if the user and token are not null
		 */
		public boolean loggedIn() {
			return _user != null && _token != null;
		}

		public String getToken() {
			return _token;
		}

		public String getUser() {
			return _user;
		}

		public boolean areCredentialsProvided() {
			return _areCredentialsProvided;
		}
	}

	/**
	 * Record audit log for services
	 * 
	 * @param opType
	 *            audit event type (e.g. CREATE_VPOOL|TENANT etc.)
	 * @param operationalStatus
	 *            Status of operation (true|false)
	 * @param operationStage
	 *            Stage of operation. For sync operation, it should be null; For
	 *            async operation, it should be "BEGIN" or "END";
	 * @param descparams
	 *            Description paramters
	 */
	protected void auditOp(URI tenantId, URI userId, OperationTypeEnum opType, boolean operationalStatus,
			String operationStage, Object... descparams) {
		_auditMgr.recordAuditLog(tenantId, userId, EVENT_SERVICE_TYPE, opType, System.currentTimeMillis(),
				operationalStatus ? AuditLogManager.AUDITLOG_SUCCESS : AuditLogManager.AUDITLOG_FAILURE, operationStage,
				descparams);
	}

	/**
	 * @param maxAge
	 *            in seconds
	 * @return GMT time of current time + maxAge
	 */
	private String getExpiredTimeGMT(int maxAge) {
		String dateFormat = "EEE, d-MMM-yyyy HH:mm:ss zzz";
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat(dateFormat);
		simpleDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
		long time = System.currentTimeMillis() + maxAge * 1000;
		Date expiryDate = new Date(time);
		return simpleDateFormat.format(expiryDate);
	}

	/**
	 * String for prompting Password Rules
	 * 
	 * @return
	 */
	private String getPasswordChangePromptRule() {
		List<String> promptRules = _passwordUtils.getPasswordChangePromptRules();
		StringBuilder promptString = new StringBuilder();
		promptString.append("<p>Password Validation Rules:</p>");
		promptString.append("<ul>");
		for (String item : promptRules) {
			promptString.append("<li>").append(item).append("</li>");
		}
		promptString.append("</ul>");
		return promptString.toString();
	}

	/**
	 * validate if local user's password expired
	 * 
	 * @param credentials
	 */
	private void validateLocalUserExpiration(UsernamePasswordCredentials credentials) {

		// skip validation, if user is not a local one.
		if (!_passwordUtils.isLocalUser(credentials.getUserName())) {
			return;
		}

		PasswordValidator validator = ValidatorFactory.buildExpireValidator(_passwordUtils.getConfigProperties());
		Password password = new Password(credentials.getUserName(), credentials.getPassword(), null);
		password.setPasswordHistory(_passwordUtils.getPasswordHistory(credentials.getUserName()));
		validator.validate(password);
	}

	/**
	 * check if the client be blocked
	 * 
	 * @param clientIP
	 */
	private void isClientIPBlocked(String clientIP) {
		if (_invLoginManager.isTheClientIPBlocked(clientIP)) {
			_log.error("The client IP is blocked for too many invalid login attempts: " + clientIP);
			throw APIException.unauthorized.exceedingErrorLoginLimit(_invLoginManager.getMaxAuthnLoginAttemtsCount(),
					_invLoginManager.getTimeLeftToUnblock(clientIP));
		}
	}

	/**
	 * Returns the Service URL to be redirected upon the successful login of the
	 * user. The service URL is built using the service queryParam and the host
	 * header of the http request.
	 *
	 * @param service
	 *            the requested service url.
	 * @param serverName
	 *            the server name from the host header of the http request.
	 *
	 * @return returns the service url built from the server name.
	 * @throws URISyntaxException
	 */
	private URI getServiceURL(String service, String serverName)
			throws UnsupportedEncodingException, URISyntaxException {
		String serviceDecoded = URLDecoder.decode(service, UTF8_ENCODING);
		_log.debug("Original service = " + serviceDecoded);
		serviceDecoded = SecurityUtils.stripXSS(serviceDecoded);

		String newService = "";
		URI uriObject = new URI(serviceDecoded);
		String scheme = uriObject.getScheme();
		if (StringUtils.isBlank(scheme)) {
			scheme = "https";
		}
		int port = uriObject.getPort();
		// newservice will be constructed by replacing the host component in the
		// original service by
		// serverName obtained from the HttpServletRequest.
		newService = scheme + "://" + serverName;
		if (port > 0) {
			newService += ":" + port;
		}

		String path = uriObject.getPath();
		if (StringUtils.isNotBlank(path)) {
			newService += (path.startsWith("/") ? "" : "/") + path;
		}
		String query = uriObject.getQuery();
		if (query != null && !query.isEmpty()) {
			newService += "?" + query;
		}
		_log.debug("Updated service = " + newService);

		return URI.create(newService);
	}
}