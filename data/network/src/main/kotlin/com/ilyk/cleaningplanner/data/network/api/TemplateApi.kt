package com.ilyk.cleaningplanner.data.network.api

import com.ilyk.cleaningplanner.core.model.TemplateX
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface TemplateApi {
    
    @GET("templates")
    suspend fun listByRoom(@Query("roomId") roomId: String): List<TemplateX>

    @GET("templates/{id}")
    suspend fun getById(@Path("id") id: String): TemplateX

    @POST("templates")
    suspend fun create(@Body template: TemplateX): TemplateX

    @PUT("templates/{id}")
    suspend fun update(@Path("id") id: String, @Body template: TemplateX): TemplateX

    @DELETE("templates/{id}")
    suspend fun delete(@Path("id") id: String)
}

