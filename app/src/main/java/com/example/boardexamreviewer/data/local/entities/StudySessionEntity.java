package com.example.boardexamreviewer.data.local.entities;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "study_sessions")
public class StudySessionEntity {
    @PrimaryKey(autoGenerate = true)
    public int id = 0;
    public int durationMinutes;
    public String sessionType;
    public long timestamp = System.currentTimeMillis();

    @Ignore
    public StudySessionEntity() {}

    public StudySessionEntity(int durationMinutes, String sessionType) {
        this.durationMinutes = durationMinutes;
        this.sessionType = sessionType;
        this.timestamp = System.currentTimeMillis();
    }
}
