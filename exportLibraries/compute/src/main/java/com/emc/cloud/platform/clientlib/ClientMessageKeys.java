/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.cloud.platform.clientlib;

import com.emc.cloud.message.utils.MessageKeysInterface;
import com.emc.cloud.message.utils.MessageResolverService;

public enum ClientMessageKeys implements MessageKeysInterface {
    // Http errors.
    BAD_REQUEST(400, "key.platform.client.bad_request"),
    UNAUTHORIZED(401, "key.platform.client.unauthorized"),
    FORBIDDEN(403, "key.platform.client.forbidden"),
    NOT_FOUND(404, "key.platform.client.not_found"),
    METHOD_NOT_ALLOWED(405, "key.platform.client.method_not_allowed"),
    NOT_ACCEPTABLE(406, "key.platform.client.not_acceptable"),
    REQUEST_TIMEOUT(408, "key.platform.client.request_timeout"),
    CONFLICT(409, "key.platform.client.conflict"),
    GONE(410, "key.platform.client.gone"),
    LENGTH_REQUIRED(411, "key.platform.client.length_required"),
    PRECONDITION_FAILED(412, "key.platform.client.precondition_failed"),
    REQUEST_ENTITY_TOO_LARGE(413, "key.platform.client.entity_too_large"),
    REQUEST_URI_TOO_LONG(414, "key.platform.client.request_uri_too_long"),
    UNSUPPORTED_MEDIA_TYPE(415, "key.platform.client.unsupported_media_type"),
    REQUESTED_RANGE_NOT_SATISFIABLE(416, "key.platform.client.requested_range_not_satisfiable"),
    INTERNAL_SERVER_ERROR(500, "key.platform.client.internal_server_error"),
    NOT_IMPLEMENTED(501, "key.platform.client.not_implemented"),
    BAD_GATEWAY(502, "key.platform.client.bad_gateway"),
    SERVICE_UNAVAILABLE(503, "key.platform.client.service_unavailable"),
    GATEWAY_TIMEOUT(504, "key.platform.client.gateway_timeout"),
    GATEWAY_HTTP_VERSION_NOT_SUPPORTED(505, "key.platform.client.http_version_not_supported"),

    // Other errors.
    SESSION_COOKIE_IS_INVALID(552, "key.platform.client.session_cookie_is_invalid"),
    CLIENT_PROTOCOL_EXCEPTION(700, "key.platform.client.client_protocol_exception"),
    IO_EXCEPTION(701, "key.platform.client.io_exception"),
    UNSUPPORTED_ENCODING_EXCEPTION(702, "key.platform.client.unsupported_encoding_exception"),
    NOT_FOUND_EXCEPTION(703, "key.platform.client.not_found_exception"),
    INVALID_COS_TYPE(704, "key.platform.client.invalid_cos_type"),
    URN_REQUIRED(705, "key.platform.client.urn_required"),
    MALFORMED_URL(706, "key.platform.client.malformed_url"),
    AUTHENTICATION_EXCEPTION(707, "key.platform.client.authentication_exception"),
    SECURITY_EXCEPTION(708, "key.platform.client.security_exception"),
    EXPECTED_PARAMETER_TO_BE_VALUE(709, "key.platform.client.expected_parameter_to_be_value"), // Expected parameter: %s value: %s to be one
                                                                                               // of these values: %s
    EXPECTED_PARAMETER_WAS_NULL(710, "key.platform.client.expected_parameter_was_null"),   // Expected parameter: %s was null
    EXPECTED_PARAMETER_TO_BE_INTEGER(711, "key.platform.client.expected_parameter_to_be_integer"),  // Expected parameter: %s to be integer
    TIMED_OUT(712, "key.platform.client.timed_out"),  // Bourne timed out: %s
    MODEL_EXCEPTION(713, "key.platform.client.model_parsing_exception"),
    UNSUPPORTED_DEVICE_TYPE(714, "key.platform.client.unsupported_device_type"),
    INVALID_TRANSPORT_ZONE_TYPE(715, "key.platform.client.invalid_transport_zone_type"),
    UNSUPPORTED_ENDPOINT_TYPE(716, "key.platform.client.unsupported_endpoint"),
    UNEXPECTED_FAILURE(717, "key.platform.client.unexpected.failure"),
    ROLE_ASSIGNMENT_FAILURE(718, "key.platform.client.role_assignment_failure"),
    DISTRIBUTED_LOCK_ERROR(719, "key.distributed.lock.error"),
    DISTRIBUTED_DATA_CACHE_ERROR(720, "key.distributed.data.cache.error"),
    DATA_ENCRYPTION_ERROR(721, "key.data.encryption.error"),

    UNABLE_TO_UPDATE_MO(722, "key.unable.to.update.mo");

    private int errorCode;
    private String messageKey;

    ClientMessageKeys(int errorCode, String messageKey) {
        this.errorCode = errorCode;
        this.messageKey = messageKey;
    }

    /**
     * Return the appropriate key enum located by errorCode
     * 
     * @param errorCode
     * @return
     */
    public static ClientMessageKeys byErrorCode(int errorCode) {
        for (ClientMessageKeys k : values()) {
            if (k.errorCode == errorCode) {
                return k;
            }
        }
        return INTERNAL_SERVER_ERROR;
    }

    /**
     * Return the appropriate key enum located by messageKey
     * 
     * @param messageKey
     * @return
     */
    public static ClientMessageKeys byMessageKey(String messageKey) {
        for (ClientMessageKeys k : values()) {
            if (k.messageKey == messageKey) {
                return k;
            }
        }
        return INTERNAL_SERVER_ERROR;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public String getDecodedMessage() {
        return MessageResolverService.resolveMessage(messageKey, null);
    }

    public String getDecodedMessage(String[] params) {
        StringBuilder buf = new StringBuilder();
        buf.append(MessageResolverService.resolveMessage(messageKey, params));
        return buf.toString();
    }
}
