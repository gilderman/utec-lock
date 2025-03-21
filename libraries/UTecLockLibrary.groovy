library (
    name: "U-tec",
    namespace: "gilderman",
    author: "Ilia Gilderman",
    description: "Integration with U-tec ULTRALOQ Latch 5 NFC lock",
    category: "Utility",
    documentationLink: ""
)

def TOKEN_URL = 'https://oauth.u-tec.com/token';
def API_URL = 'https://api.u-tec.com/action';

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