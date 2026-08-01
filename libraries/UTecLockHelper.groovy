import groovy.json.JsonOutput

library(
    name: "UTecLockHelper",
    namespace: "gilderman",
    author: "Ilia Gilderman",
    description: "Helper for integration with U-tec ULTRALOQ Latch 5 NFC lock",
    category: "Utility",
    importUrl: "https://raw.githubusercontent.com/gilderman/utec-lock/main/libraries/UTecLockerHelper.groovy",
    documentationLink: ""
)

def getTokenUri() {
    return "https://oauth.u-tec.com/token"
}

def getApiUri() {
    return "https://api.u-tec.com/action"
}

def getRedirectUri() {
    return "https://cloud.hubitat.com/oauth/stateredirect"
}

def getLoginUrl(String clientId) {
    if (state.accessToken == null) {
        createAccessToken()
    }

    def authUrl = "https://oauth.u-tec.com/authorize"
    def oauthState = URLEncoder.encode("${getHubUID()}/apps/${app.id}/callback?access_token=${state.accessToken}")

    return "${authUrl}?client_id=${clientId}&redirect_uri=${URLEncoder.encode(getRedirectUri())}&response_type=code&scope=openapi&state=${oauthState}"
}

def oauthCallback() {
    if (params.code) {
        if (exchangeCodeForTokens(params.code)) {
            oauthSuccess()
        } else {
            oauthFailure()
        }
    } else {
        render contentType: "text/html", data: "<h1>OAuth Failed</h1><p>Authorization code missing.</p>"
    }
}

def getTokenParams(String grantType, Map param) {
    return [
        grant_type: grantType,
        // These are app preferences, not state values. The app never populates
        // state.clientId/state.clientSecret, and Clear State deliberately erases state.
        client_id: settings.clientId?.toString()?.trim(),
        client_secret: settings.clientSecret?.toString()?.trim(),
        redirect_uri: getRedirectUri()
    ] + param
}

def getTokenData(resp) {
    // U-tec may return either the OAuth payload directly or wrapped in
    // [code: 200, data: [...]], while the HTTP status remains 200.
    def responseData = resp?.data
    return responseData?.data instanceof Map ? responseData.data : responseData
}

def saveTokens(Map tokenData) {
    state.deviceAccessToken = tokenData.access_token

    // Some OAuth providers rotate refresh tokens; others omit them on refresh.
    // Preserve the existing token when the response omits a replacement.
    if (tokenData.refresh_token) {
        state.deviceRefreshToken = tokenData.refresh_token
    }

    // Tokens must never be written to the Hubitat log.
    logDebug("U-tec access token updated.")
}

def scheduleTokenRefresh() {
    def delay = state.authTokenExpires - now()
    if (delay > 0) {
        def delaySeconds = Math.max(1, (delay / 1000).toInteger())
        runIn(delaySeconds, refreshAccessToken)
        logDebug("Token refresh scheduled in ${delaySeconds} seconds.")
    } else {
        log.warn "Token is already expired or has no valid expiration time."
    }
}

def tokenExchange(Map body, boolean refresh = false) {
    def success = false
    def params = [
        uri: getTokenUri(),
        body: body
    ]

    try {
        httpPost(params) { resp ->
            def tokenData = getTokenData(resp)
            if (resp.status == 200 && tokenData?.access_token) {
                saveTokens(tokenData)
                state.authTokenExpires = now() + (tokenData.expires_in.toLong() * 1000) - 60000
                scheduleTokenRefresh()
                success = true
            } else {
                log.error "Token ${refresh ? 'refresh' : 'exchange'} failed: ${resp.status} - ${resp.data}"
            }
        }
    } catch (Exception e) {
        log.error "Error ${refresh ? 'refreshing' : 'getting'} tokens: ${e.message}"
    }

    return success
}

def exchangeCodeForTokens(String code) {
    return tokenExchange(getTokenParams("authorization_code", [code: code]))
}

def refreshAccessToken() {
    if (!state.deviceRefreshToken) {
        log.error "Cannot refresh U-tec access token: no refresh token is stored. Reauthorize the integration."
        return false
    }

    return tokenExchange(getTokenParams("refresh_token", [refresh_token: state.deviceRefreshToken]), true)
}

def oauthSuccess() {
    state.loginSuccess = true
    render(contentType: 'text/html', data: "<html><body><p>Login successful! You can close this window.</p><button onclick=\"window.close();\">Close</button></body></html>")
}

def oauthFailure() {
    state.loginSuccess = false
    render(contentType: 'text/html', data: "<html><body><p>Authentication failed. Close this window and try again.</p><button onclick=\"window.close();\">Close</button></body></html>")
}

def getUHomeHeader(String name, namespace = "Uhome.Device") {
    return [
        header: [
            namespace: namespace,
            name: name,
            messageId: UUID.randomUUID().toString(),
            payloadVersion: "1"
        ]
    ]
}

def getUHomePayload(Map param, String deviceId) {
    def payload = [
        devices: [
            [id: deviceId]
        ]
    ]
    payload.devices[0] << param
    return payload
}

def createPostPayload(String payloadName, Map param = [:], String deviceId) {
    return [
        header: getUHomeHeader(payloadName)["header"],
        payload: payloadName != "Discovery" ? getUHomePayload(param, deviceId) : [:]
    ]
}

def sendJsonToDevice(def headers, def body) {
    def result = null
    def params = [
        uri: getApiUri(),
        headers: headers,
        contentType: "application/json",
        body: body
    ]

    // Do not log headers: they contain the bearer token.
    logDebug("Sending JSON to ${getApiUri()}: ${JsonOutput.toJson(body)}")

    try {
        httpPostJson(params) { resp ->
            logDebug("Response status: ${resp.status}")
            logDebug("Response data: ${resp.data}")
            result = resp.data
        }
    } catch (Exception e) {
        log.error "HTTP request failed: ${e.message}"
    }

    return result
}

def sendAuthorizedPostRequest(payload, token = state.deviceAccessToken) {
    return sendJsonToDevice([Authorization: "Bearer ${token}"], payload)
}

def sendPostRequest(String name, String deviceId, Map param = [:]) {
    def payload = name == "Set" ? createRegitsrationPayload() : createPostPayload(name, param, deviceId)
    def response = sendAuthorizedPostRequest(payload)

    if (response?.payload?.error?.code == "INVALID_TOKEN") {
        log.warn "U-tec rejected the access token; attempting a token refresh."
        if (refreshAccessToken()) {
            response = sendAuthorizedPostRequest(payload)
        } else {
            log.error "U-tec token refresh failed. Reauthorize the integration."
        }
    } else if (response?.payload?.error) {
        log.warn "U-tec API error: ${response.payload.error}"
    }

    return response
}

def lockCommand(String command, String deviceId, Map argMap = [:]) {
    def param = [
        command: [
            capability: 'st.lock',
            name: command
        ]
    ]

    if (argMap) {
        param.command.arguments = argMap
    }

    def response = sendPostRequest('Command', deviceId, param)

    if (response && !response?.payload?.error) {
        logDebug("Lock command '${command}' accepted for device ${deviceId}.")
    } else {
        log.warn "Lock command '${command}' was rejected: ${response?.payload?.error ?: 'no response'}"
    }

    return response
}

def parseStatusResponse(resp) {
    def device = resp.payload.devices[0]
    return [
        id: device.id,
        onlineStatus: device.states.find { it.name == "status" }?.value,
        lockStatus: device.states.find { it.name == "lockState" }?.value,
        batteryLevel: device.states.find { it.name == "level" }?.value,
        lockMode: device.states.find { it.name == "lockMode" }?.value
    ]
}

def getStatusCommand(String deviceId) {
    def response = sendPostRequest('Query', deviceId)

    if (response && !response?.payload?.error && response?.payload?.devices) {
        logDebug("Query command accepted for device ${deviceId}.")
        return parseStatusResponse(response)
    }

    log.warn "Query command failed: ${response?.payload?.error ?: 'no response'}"
    return null
}

def registerNotificationsCallback() {
    def payload = [
        header: getUHomeHeader("Set", "Uhome.Configure")["header"],
        payload: [
            configure: [
                notification: [
                    access_token: state.accessToken,
                    url: "${getFullApiServerUrl()}/notifications?access_token=${state.accessToken}"
                ]
            ]
        ]
    ]

    return sendAuthorizedPostRequest(payload)
}

def setDebugOn(level) {
    state['debugOn'] = level
}

def logDebug(msg) {
    if (state['debugOn']) {
        log.debug msg
    }
}


