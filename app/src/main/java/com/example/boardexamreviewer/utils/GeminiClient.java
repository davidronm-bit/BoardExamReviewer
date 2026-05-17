package com.example.boardexamreviewer.utils;

import com.example.boardexamreviewer.data.network.GeminiApiService;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Handles the network connection to Google Gemini.
 */
public class GeminiClient {

    private static final OkHttpClient okHttpClient = new OkHttpClient.Builder()
        .addInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
        .connectTimeout(30, TimeUnit.MINUTES) // 30 mins timeout for long documents
        .readTimeout(30, TimeUnit.MINUTES)
        .writeTimeout(30, TimeUnit.MINUTES)
        .build();

    private static final Retrofit retrofit = new Retrofit.Builder()
        .baseUrl(AppConfig.GEMINI_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build();

    public static final GeminiApiService apiService = retrofit.create(GeminiApiService.class);
}
