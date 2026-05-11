package com.ilyk.cleaningplanner.data.remote.api

import com.ilyk.cleaningplanner.data.remote.dto.SkipTaskRequestV1
import com.ilyk.cleaningplanner.data.remote.dto.SkipTaskResponseV1
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * v1 Tasks API — currently exposes only the skip endpoint added in W4
 * (backend handler in `crates/api/src/plans.rs::skip_task`).
 *
 * Note: this is distinct from the plan-level [PlansApi.skipTask] which posts to
 * `/v1/plan/skip`; the task-level endpoint here routes the skip through the
 * telemetry service so the LearningEngine sees a uniform shape regardless of
 * which surface the user used to skip.
 */
interface TasksApi {

    @POST("/v1/tasks/{task_id}/skip")
    suspend fun skip(
        @Path("task_id") taskId: String,
        @Body request: SkipTaskRequestV1 = SkipTaskRequestV1()
    ): SkipTaskResponseV1
}
