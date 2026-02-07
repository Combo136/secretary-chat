package com.secretary.chat

import android.app.Application

class SecretaryChatApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
    
    companion object {
        lateinit var instance: SecretaryChatApp
            private set
    }
}
