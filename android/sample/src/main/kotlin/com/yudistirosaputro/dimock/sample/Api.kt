package com.yudistirosaputro.dimock.sample

import com.yudistirosaputro.dimock.okhttp.Dimock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * JSONPlaceholder (https://jsonplaceholder.typicode.com): open source, free, no API key, and it answers
 * GET / POST / DELETE so the inspector has varied traffic to show. Writes are faked server-side and never persist.
 */
interface PostsApi {
    @GET("posts")
    suspend fun posts(@Query("_limit") limit: Int = 20): List<Post>

    @GET("posts/{id}")
    suspend fun post(@Path("id") id: Int): Post

    @GET("posts/{id}/comments")
    suspend fun comments(@Path("id") id: Int): List<Comment>

    @POST("posts")
    suspend fun create(@Body post: NewPost): Post

    @DELETE("posts/{id}")
    suspend fun delete(@Path("id") id: Int)
}

@Serializable
data class Post(val id: Int, val userId: Int = 0, val title: String, val body: String = "")

@Serializable
data class Comment(val id: Int, val name: String, val email: String, val body: String)

@Serializable
data class NewPost(val userId: Int, val title: String, val body: String)

object ApiFactory {
    private val json = Json { ignoreUnknownKeys = true }

    /** The one line every adopter adds. In release builds `Dimock.interceptor()` is a pass-through. */
    val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(Dimock.interceptor())
        .build()

    val api: PostsApi = Retrofit.Builder()
        .baseUrl("https://jsonplaceholder.typicode.com/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(PostsApi::class.java)
}
