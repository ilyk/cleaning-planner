package com.ilyk.cleaningplanner.data.network.di

import com.ilyk.cleaningplanner.data.network.BuildConfig
import com.ilyk.cleaningplanner.data.remote.api.*
import com.ilyk.cleaningplanner.data.network.api.CleanFlowApi
import com.ilyk.cleaningplanner.data.remote.interceptor.CommonHeadersInterceptor
import com.ilyk.cleaningplanner.data.remote.interceptor.ErrorHandlingInterceptor
import com.ilyk.cleaningplanner.data.remote.websocket.ClaraWebSocketClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaType

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    @Provides
    @Singleton
    fun provideJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
    
    @Provides
    @Singleton
    fun provideOkHttpClient(
        commonHeadersInterceptor: CommonHeadersInterceptor,
        errorHandlingInterceptor: ErrorHandlingInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(commonHeadersInterceptor)
            .addInterceptor(errorHandlingInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    @Provides
    @Singleton
    fun provideCommonHeadersInterceptor(
        tokenProvider: () -> String,
        versions: com.ilyk.cleaningplanner.data.remote.interceptor.ClientVersions
    ): CommonHeadersInterceptor {
        return CommonHeadersInterceptor(tokenProvider, versions)
    }
    
    @Provides
    @Singleton
    fun provideErrorHandlingInterceptor(): ErrorHandlingInterceptor {
        return ErrorHandlingInterceptor()
    }
    
    @Provides
    @Singleton
    fun provideClientVersions(): com.ilyk.cleaningplanner.data.remote.interceptor.ClientVersions {
        return com.ilyk.cleaningplanner.data.remote.interceptor.ClientVersions()
    }
    
    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json,
        @javax.inject.Named("baseUrl") baseUrl: String
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }
    
    @Provides
    @Singleton
    @javax.inject.Named("baseUrl")
    fun provideBaseUrl(): String {
        return BuildConfig.CLARA_API_BASE_URL
    }
    
    @Provides
    @Singleton
    fun providePlansApi(retrofit: Retrofit): PlansApi {
        return retrofit.create(PlansApi::class.java)
    }
    
    @Provides
    @Singleton
    fun provideFamilyApi(retrofit: Retrofit): FamilyApi {
        return retrofit.create(FamilyApi::class.java)
    }
    
    @Provides
    @Singleton
    fun provideTelemetryApi(retrofit: Retrofit): TelemetryApi {
        return retrofit.create(TelemetryApi::class.java)
    }
    
    @Provides
    @Singleton
    fun provideClaraApi(retrofit: Retrofit): ClaraApi {
        return retrofit.create(ClaraApi::class.java)
    }
    
    @Provides
    @Singleton
    fun provideCleanFlowApi(retrofit: Retrofit): CleanFlowApi {
        return retrofit.create(CleanFlowApi::class.java)
    }

    @Provides
    @Singleton
    fun provideHouseholdsApi(retrofit: Retrofit): HouseholdsApi {
        return retrofit.create(HouseholdsApi::class.java)
    }

    @Provides
    @Singleton
    fun provideRoomsApi(retrofit: Retrofit): RoomsApi {
        return retrofit.create(RoomsApi::class.java)
    }

    @Provides
    @Singleton
    fun provideTasksApi(retrofit: Retrofit): TasksApi {
        return retrofit.create(TasksApi::class.java)
    }
    
    @Provides
    @Singleton
    fun provideClaraWebSocketClient(
        okHttpClient: OkHttpClient,
        @javax.inject.Named("baseUrl") baseUrl: String
    ): ClaraWebSocketClient {
        return ClaraWebSocketClient(okHttpClient, baseUrl)
    }
    
    @Provides
    @Singleton
    fun provideTokenProvider(): () -> String {
        // Dev token for testing - bypasses auth in dev mode
        return { "dev-token" }
    }
}