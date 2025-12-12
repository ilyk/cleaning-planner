package com.ilyk.cleaningplanner.data.repository

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindUserProfileRepository(
        impl: UserProfileRepositoryImpl
    ): UserProfileRepository

    @Binds
    @Singleton
    abstract fun bindSuggestionRepository(
        impl: SuggestionRepositoryImpl
    ): SuggestionRepository

    // TaskRepository is already annotated with @Singleton
    // No need to bind it since it's a concrete class

    // Remove other bindings that don't exist in CleanFlow
}
