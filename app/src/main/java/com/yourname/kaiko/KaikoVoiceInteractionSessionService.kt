package com.yourname.kaiko

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/**
 * Service that creates active voice interaction sessions for Kaiko.
 */
class KaikoVoiceInteractionSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return KaikoVoiceInteractionSession(this)
    }
}
