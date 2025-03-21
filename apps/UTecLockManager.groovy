import gilderman.UTecLockHelper

definition(
    name: "U-tec Lock Integration",
    namespace: "gilderman",
    author: "Ilia Gilderman",
    description: "Integration with U-tec ULTRALOQ Latch 5 NFC lock",
    category: "My Apps",
    iconUrl: "https://raw.githubusercontent.com/gilderman/utec-lock/master/resources/icons/u-tec_2024_new_logo.svg",
    iconX2Url: "",
	importUrl: "https://raw.githubusercontent.com/gilderman/utec-lock/main/apps/UTecLockManager.groovy",
    oauth: true,
    singleInstance: false,
    installOnOpen: true
)

preferences {
	section("OAuth2 Settings") {
		input "clientId", "password", title: "Client ID", required: true
        input "clientSecret", "password", title: "Client Secret", required: true
	}
	
    section("Lock Discovery") {
        input "discoverLocks", "button", title: "Discover Locks", submitOnChange: true
    }
}

def oauthLogin() {
	def clientId = settings.clientId
    def clientSecret = settings.clientSecret

    def authUrl = "https://oauth.u-tec.com/authorize"
    def redirectUri = "https://cloud.hubitat.com/oauth/callback"

    def params = [
        client_id: clientId,
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
    
	def response = sendPostRequest("Discovery", null)
	if (response) {
        def status = response?.payload?.status
        log.debug "Lock Status: ${status}"
    } else {
        log.warn "Failed to receive response from API"
    }
	
    // Simulate discovery (Replace this with actual API/device scanning logic)
    //def foundLocks = [
    //    [id: "lock1", name: "Front Door Lock"],
    //    [id: "lock2", name: "Back Door Lock"]
    //]
    
    //foundLocks.each { lock ->
    //    createChildDevice(lock.id, lock.name)
    //}
}

def createChildDevice(deviceId, deviceName) {
	log.debug "Creating child device: ${deviceName}"

	if (!getChildDevice(deviceId)) {
        addChildDevice(
            "gilderman",  
            "U-tech ULTRALOQ Latch 5 NFC", 
            deviceId,
            [label: deviceName, isComponent: false]
        )
        log.debug "Child device '${deviceName}' created."
    } else {
        log.debug "Child device '${deviceName}' already exists."
    }
}