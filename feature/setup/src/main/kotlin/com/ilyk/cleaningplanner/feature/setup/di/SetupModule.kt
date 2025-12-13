package com.ilyk.cleaningplanner.feature.setup.di

import com.ilyk.cleaningplanner.feature.setup.DefaultSetupRepository
import com.ilyk.cleaningplanner.feature.setup.SetupRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SetupModule {

    @Provides
    @Singleton
    fun provideSetupRepository(
        httpClient: OkHttpClient,
        json: Json,
        @Named("baseUrl") baseUrl: String
    ): SetupRepository {
        return DefaultSetupRepository(httpClient, json, baseUrl)
    }
}
