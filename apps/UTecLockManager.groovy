definition(
    name: "U-tec",
    namespace: "gilderman",
    author: "Ilia Gilderman",
    description: "Integration with U-tec ULTRALOQ Latch 5 NFC lock",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    oauth: true,
    singleInstance: false,
    installOnOpen: true
)

preferences {
    section("Lock Discovery") {
        input "discoverLocks", "button", title: "Discover Locks", submitOnChange: true
    }
}

def oauthLogin() {
    def authUrl = "https://oauth.u-tec.com/authorize"
    def redirectUri = "https://cloud.hubitat.com/oauth/callback"

    def params = [
        client_id: "1a26a1670d47d5098df3ae7c57d7ca60",
        response_type: "code",
        redirect_uri: redirectUri,
        scope: "openapi"
    ]

    def loginUrl = "${authUrl}?${params.collect { k, v -> "${k}=${v}" }.join('&')}"
    log.debug "Redirecting to OAuth login: ${loginUrl}"

    return loginUrl
}

def installed() { initialize() }
def updated() { initialize() }

def initialize() {
    log.debug "Initializing Lock Manager..."
    if (discoverLocks) discoverDevices()
}

def discoverDevices() {
    log.debug "Starting lock discovery..."
    
    // Simulate discovery (Replace this with actual API/device scanning logic)
    def foundLocks = [
        [id: "lock1", name: "Front Door Lock"],
        [id: "lock2", name: "Back Door Lock"]
    ]
    
    foundLocks.each { lock ->
        createChildDevice(lock.id, lock.name)
    }
}

def createChildDevice(deviceId, deviceName) {
    log.debug "Creating child device: ${deviceName}"

    if (!getChildDevice(deviceId)) {
        addChildDevice(
            "myNamespace",  // Namespace of your lock driver
            "Lock Driver",  // Name of your custom lock driver
            deviceId,
            [label: deviceName, isComponent: false]
        )
        log.debug "Child device '${deviceName}' created."
    } else {
        log.debug "Child device '${deviceName}' already exists."
    }
}