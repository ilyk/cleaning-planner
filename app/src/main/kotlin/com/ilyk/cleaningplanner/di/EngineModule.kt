package com.ilyk.cleaningplanner.di

import com.ilyk.cleaningplanner.domain.engine.LearningEngine
import com.ilyk.cleaningplanner.domain.engine.PlanEngine
import com.ilyk.cleaningplanner.domain.engine.SuggestionEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the pure-Kotlin domain engines as singletons.
 *
 * Kept here rather than annotating the engine classes with `@Inject` so the engines
 * themselves stay free of any DI imports — matches the "no Android imports in engine
 * sources" rule from PLAN.md §1.
 *
 * `LearningEngine` is singleton because it holds in-memory per-task history across
 * the app lifecycle. `PlanEngine` and `SuggestionEngine` are stateless but kept
 * singleton for consistency.
 */
@Module
@InstallIn(SingletonComponent::class)
object EngineModule {

    @Provides
    @Singleton
    fun providePlanEngine(): PlanEngine = PlanEngine()

    @Provides
    @Singleton
    fun provideLearningEngine(): LearningEngine = LearningEngine()

    @Provides
    @Singleton
    fun provideSuggestionEngine(): SuggestionEngine = SuggestionEngine()
}
