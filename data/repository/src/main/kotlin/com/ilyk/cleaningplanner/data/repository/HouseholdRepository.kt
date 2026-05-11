package com.ilyk.cleaningplanner.data.repository

import com.ilyk.cleaningplanner.core.common.result.Result
import com.ilyk.cleaningplanner.data.remote.api.HouseholdsApi
import com.ilyk.cleaningplanner.data.remote.dto.CreateHomeRequest
import com.ilyk.cleaningplanner.data.remote.dto.UpdateHomeRequest
import com.ilyk.cleaningplanner.domain.model.Home
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Households (Homes) repository — talks to the v1 backend via [HouseholdsApi].
 *
 * Network-only at this stage. Offline-first caching (Room DAO write-through, observe flows)
 * is intentionally deferred to the W6 (Planner) PR, where a UI screen first consumes these
 * reads. Adding the cache here today would require either a schema migration on
 * `HouseholdEntity` (currently keyed on legacy `inviteCode` columns) or a parallel table —
 * both decisions belong with the screen that will actually exercise them.
 */
@Singleton
class HouseholdRepository @Inject constructor(
    private val householdsApi: HouseholdsApi
) {

    suspend fun list(): Result<List<Home>> = runCatching { householdsApi.list() }.toResult()

    suspend fun get(homeId: String): Result<Home> = runCatching { householdsApi.get(homeId) }.toResult()

    suspend fun create(request: CreateHomeRequest): Result<Home> =
        runCatching { householdsApi.create(request) }.toResult()

    suspend fun update(homeId: String, request: UpdateHomeRequest): Result<Home> =
        runCatching { householdsApi.update(homeId, request) }.toResult()

    suspend fun delete(homeId: String): Result<Unit> =
        runCatching {
            householdsApi.delete(homeId)
            Unit
        }.toResult()
}

private fun <T> kotlin.Result<T>.toResult(): Result<T> = fold(
    onSuccess = { Result.Success(it) },
    onFailure = { Result.Error(it) }
)
