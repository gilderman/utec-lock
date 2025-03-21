library (
    name: "U-tec",
    namespace: "gilderman",
    author: "Ilia Gilderman",
    description: "Integration with U-tec ULTRALOQ Latch 5 NFC lock",
    category: "Utility",
    documentationLink: ""
)

def TOKEN_URL = "https://oauth.u-tec.com/token"
def API_URL = "https://api.u-tec.com/action"

def exchangeCodeForTokens(String code, String clientId, String clientSecret) {
    def redirectUri = "https://cloud.hubitat.com/oauth/stateredirect"

    def params = [
        uri: TOKEN_URL,
        body: [
            grant_type   : "authorization_code",
            code         : code,
            redirect_uri : redirectUri,
            client_id    : clientId,
            client_secret: clientSecret
        ]
    ]

    try {
        httpPost(params) { resp ->
            if (resp.status == 200) {
                state.accessToken  = resp.data.access_token
                state.refreshToken = resp.data.refresh_token
                log.debug "Access Token: ${state.accessToken}"
                log.debug "Refresh Token: ${state.refreshToken}"
            } else {
                log.error "Token Exchange Failed: ${resp.status} - ${resp.data}"
            }
        }
    } catch (Exception e) {
        log.error "Error getting tokens: ${e.message}"
    }
}

// Step 6: Refresh Token When Expired
def refreshAccessToken() {
    def tokenUrl = "https://api.uhome.com/oauth/token"
    def clientId = "YOUR_CLIENT_ID"
    def clientSecret = "YOUR_CLIENT_SECRET"

    def params = [
        uri: tokenUrl,
        body: [
            grant_type   : "refresh_token",
            refresh_token: state.refreshToken,
            client_id    : clientId,
            client_secret: clientSecret
        ]
    ]

    try {
        httpPost(params) { resp ->
            if (resp.status == 200) {
                state.accessToken  = resp.data.access_token
                state.refreshToken = resp.data.refresh_token
                log.debug "New Access Token: ${state.accessToken}"
            } else {
                log.error "Token Refresh Failed: ${resp.status} - ${resp.data}"
            }
        }
    } catch (Exception e) {
        log.error "Error refreshing token: ${e.message}"
    }
}



def getUHomeHeader(String name) {
    return [
        "header": [
            "namespace": "Uhome.Device",
            "name": name,
            "messageId": UUID.randomUUID().toString(),
            "payloadVersion": "1"
        ]
    ]
}

def getUHomePayload(Map param, String deviceId) {
    def payload = [
        "devices": [
            ["id": deviceId]
        ]
    ]
    payload.devices[0] << param
    return payload
}

def createPostPayload(String payloadName, Map param = [:], String deviceId) {
    return [
        "header": getUHomeHeader(payloadName)["header"],
        "payload": payloadName != "Discovery" ? getUHomePayload(param, deviceId) : [:]
    ]
}

def sendPostRequest(String name, String deviceId, Map param = [:]) {
    def payload = createPostPayload(name, param, deviceId)
    def jsonPayload = JsonOutput.toJson(payload)
    
    try {
        def response = httpPostJson([uri: API_URL, body: jsonPayload]) { resp ->
            def responseData = resp.data
            if (responseData?.payload?.error) {
                log.warn "Error detected: ${responseData.payload.error}"
                if (responseData.payload.error.code == "INVALID_TOKEN") {
                    refreshAccessToken()
                    response = httpPostJson([uri: API_URL, body: jsonPayload]) { r -> r.data }
                }
            }
            return responseData
        }
        return response
    } catch (Exception e) {
        log.error "Error during API request: ${e.message}"
        return null
    }
}