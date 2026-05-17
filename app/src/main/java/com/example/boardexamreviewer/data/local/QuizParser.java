package com.example.boardexamreviewer.data.local;

import com.example.boardexamreviewer.data.local.entities.QuizQuestion;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class QuizParser {
    public static List<QuizQuestion> fromJson(String json) {
        Type type = new TypeToken<List<QuizQuestion>>() {}.getType();
        List<QuizQuestion> questions = new Gson().fromJson(json, type);
        return questions != null ? questions : new ArrayList<>();
    }

    public static String toJson(List<QuizQuestion> questions) {
        return new Gson().toJson(questions);
    }
}
