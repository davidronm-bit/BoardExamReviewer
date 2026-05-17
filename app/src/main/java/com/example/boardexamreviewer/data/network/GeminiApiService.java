package com.example.boardexamreviewer.data.network;

import com.example.boardexamreviewer.data.network.models.*;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Interface for Google Gemini AI API.
 */
public interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    Call<GeminiResponse> generateContent(
        @Path("model") String model,
        @Query("key") String apiKey,
        @Body GeminiRequest request
    );
}
