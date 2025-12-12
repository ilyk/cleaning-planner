package com.ilyk.cleaningplanner.state

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PrefsStoreEntryPoint {
    fun prefsStore(): PrefsStore
}
