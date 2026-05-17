package com.example.boardexamreviewer.data.local.entities;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "quizzes")
public class QuizEntity {
    @PrimaryKey(autoGenerate = true)
    public int id = 0;
    public int documentId;
    public String title;
    public String questionsJson;
    public double masteryPercentage = 0.0;
    public long timestamp = System.currentTimeMillis();

    @Ignore
    public QuizEntity() {}

    public QuizEntity(int documentId, String title, String questionsJson, double masteryPercentage) {
        this.documentId = documentId;
        this.title = title;
        this.questionsJson = questionsJson;
        this.masteryPercentage = masteryPercentage;
        this.timestamp = System.currentTimeMillis();
    }
}
