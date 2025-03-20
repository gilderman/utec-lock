metadata {
    definition(name: "U-tech ULTRALOQ Latch 5 NFC", namespace: "gilderman", author: "Ilia Gilderman") {
        capability "Lock"
        capability "Refresh"
    }
}

def lock() {
    sendEvent(name: "lock", value: "locked")
    log.debug "Locking the door..."
}

def unlock() {
    sendEvent(name: "lock", value: "unlocked")
    log.debug "Unlocking the door..."
}

def refresh() {
    log.debug "Refreshing lock status..."
    sendEvent(name: "lock", value: device.currentValue("lock") ?: "unknown")
}