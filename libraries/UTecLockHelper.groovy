import groovy.json.JsonOutput

library (
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
    if (state.accessToken == null)
		createAccessToken()
        
    def authUrl = "https://oauth.u-tec.com/authorize"
    def oauthState = URLEncoder.encode("${getHubUID()}/apps/${app.id}/callback?access_token=${state.accessToken}")
        
    return "${authUrl}?client_id=${clientId}&redirect_uri=${URLEncoder.encode(getRedirectUri())}&response_type=code&scope=openapi&state=${oauthState}"
}

def oauthCallback() {
    if (params.code) {
        exchangeCodeForTokens(params.code)
        if (state.deviceAccessToken)
			oauthSuccess()
        else
			oauthFailure()
    } else {
        render contentType: "text/html", data: "<h1>OAuth Failed</h1><p>Authorization code missing.</p>"
    }
}

def getTokenParams(String grant_type, Map param) {
    return [
        grant_type: grant_type,
        client_id: state.clientId,
        client_secret: state.clientSecret,
        redirect_uri: getRedirectUri()
    ] + param
}

def saveTokens(resp) {
    state.deviceAccessToken  = resp.data.access_token
    state.deviceRefreshToken = resp.data.refresh_token
    
    logDebug("Access Token: ${state.deviceAccessToken}")
    logDebug("Refresh Token: ${state.deviceRefreshToken}")
}

def scheduleTokenRefresh() {
    def delay = state.tokenExpires - now()  // Time until expiration
    if (delay > 0) {
        runIn(delay / 1000, refreshAccessToken)  // Convert milliseconds to seconds
        logDebug("Token will be refreshed in ${delay / 1000} seconds.")
    } else {
        log.warn "Token is already expired or invalid expiration time."
    }
}

def tokenExchange(Map body, boolean refresh = false) {
    def params = [
        uri: getTokenUri(),
        body: body
    ]

    try {
        httpPost(params) { resp ->
            if (resp.status == 200) {
                saveTokens(resp)
 
                if (!refresh) {
                	state.authTokenExpires = (now() + (resp.data.expires_in * 1000)) - 60000
                    
                    scheduleTokenRefresh()
                }
            } else {
                log.error "Token ${refresh ? 'refresh' : 'exchange'} failed: ${resp.status} - ${resp.data}"
            }
        }
    } catch (Exception e) {
        log.error "Error ${refresh ? 'refreshing' : 'getting'} tokens: ${e.message}"
    }
}

def exchangeCodeForTokens(String code) {
    tokenExchange(getTokenParams("authorization_code", [code: code]))
}

def refreshAccessToken() {
    tokenExchange(getTokenParams("refresh_token", [refresh_token: state.deviceRefreshToken]), true)
}

def oauthSuccess() {
	state.loginSuccess = true
    render(contentType: 'text/html', data: "<html><body><p>Login successful! You can close this window.</p><button onclick=\"window.close();\">Close</button></body></html>")
}

def oauthFailure() {
    state.loginSuccess = false
    render(contentType: 'text/html', data: "<html><body><p>Authentication Failed!Close this window and try again.</p><button onclick=\"window.close();\">Close</button></body></html>")
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

    logDebug("➡️ Sending JSON to ${getApiUri()}: headers: ${headers} ${groovy.json.JsonOutput.toJson(body)}")

    try {
        httpPostJson(params) { resp ->
            logDebug("✅ Response Status: ${resp.status}")
            logDebug("⬅️ Response Data: ${resp.data}")
            
            result = resp.data
        }
    } catch (Exception e) {
        log.error "❌ HTTP Request failed: ${e.message}"
    }
    
    return result
}

def sendAuthorizedPostRequest(payload, token = state.deviceAccessToken) {
    return sendJsonToDevice([Authorization: "Bearer ${token}"], payload)
}

def sendPostRequest(String name, String deviceId, Map param = [:]) {
    def payload = null

    if (name == "Set") {
        payload = createRegitsrationPayload()
    }
    else {
    	payload = createPostPayload(name, param, deviceId)
    }
    
    def response = sendAuthorizedPostRequest(payload)

    if (response?.payload?.error) {
        log.warn "Error detected: ${response.payload.error}"

        if (response.payload.error.code == "INVALID_TOKEN") {
            refreshAccessToken()
            response = sendAuthorizedPostRequest(payload)
        }
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

    if (response) {
        logDebug("Lock command '${command}' sent to device ${deviceId} successfully.")
    } else {
        log.warn "Failed to send lock command '${command}' to device ${deviceId}."
    }

    return response
}

def parseStatusResponse(resp) {
	// Get first device only
	def device = resp.payload.devices[0]

	def result = [
  		id: device.id,
  		onlineStatus: device.states.find { it.name == "status" }?.value,
  		lockStatus: device.states.find { it.name == "lockState" }?.value,
  		batteryLevel: device.states.find { it.name == "level" }?.value,
        lockMode: device.states.find { it.name == "lockMode" }?.value
	]

    return result
}

def getStatusCommand(String deviceId) {
    def response = sendPostRequest('Query', deviceId)

    if (response) {
        logDebug("Query command sent to device ${deviceId} successfully.")
        return parseStatusResponse(response)
    } else {
        log.warn "Failed to send query command to device ${deviceId}."
        return null
    }
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
    
	sendAuthorizedPostRequest(payload)
}

def setDebugOn(level) {
	state['debugOn'] = level    
}

def logDebug(msg) {
    if (state['debugOn']) log.debug msg
}