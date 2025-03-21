#include gilderman.UTecLockHelper

definition(
    name: "UTecLockManager",
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
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "U-tec Lock Setup", install: true, uninstall: true) {
		section("OAuth2 Settings") {
			input "clientId", "password", title: "Client ID", required: true
			input "clientSecret", "password", title: "Client Secret", required: true
		}

        section("Login to U-tec") {
            href(name: "login", title: "Login with U-tec", required: false, 
                 description: "Click to authenticate", 
                 url: getLoginUrl(settings.clientId))
        }
        section("Access Token Info") {
            paragraph "Access Token: ${state.accessToken ?: 'Not Logged In'}"
            paragraph "Refresh Token: ${state.refreshToken ?: 'Not Available'}"
        }
		
		section("Lock Discovery") {
			input "discoverLocks", "button", title: "Discover Locks", submitOnChange: true
		}
	}
}

def oauthCallback(Map params) {
    if (params.code) {
        exchangeCodeForTokens(params.code, settings.clientId, settings.clientSecret)
    } else {
        log.error "OAuth login failed: ${params}"
    }
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