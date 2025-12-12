package com.ilyk.cleaningplanner.feature.clara.di

import com.ilyk.cleaningplanner.feature.clara.chat.ClaraStreamClientFactory
import com.ilyk.cleaningplanner.feature.clara.chat.DefaultClaraStreamClientFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Clara feature dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ClaraModule {

    @Binds
    @Singleton
    abstract fun bindClaraStreamClientFactory(
        impl: DefaultClaraStreamClientFactory
    ): ClaraStreamClientFactory
}
