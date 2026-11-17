package com.nendo.argosy.data.streaming

import com.nendo.argosy.data.preferences.NotOnDeviceAction

object StreamFirstRule {
    fun leadsWithStream(action: NotOnDeviceAction, isOnDevice: Boolean, hasContainer: Boolean, hasMoonlight: Boolean = false): Boolean =
        action == NotOnDeviceAction.STREAM && !isOnDevice && (hasContainer || hasMoonlight)
}
