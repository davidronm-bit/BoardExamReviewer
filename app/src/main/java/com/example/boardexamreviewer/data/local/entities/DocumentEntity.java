package com.example.boardexamreviewer.data.local.entities;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "documents")
public class DocumentEntity {
    @PrimaryKey(autoGenerate = true)
    public int id = 0;
    public String subject;
    public String topic;
    public String fileName;
    public String filePath;
    public String extractedText;
    public long timestamp = System.currentTimeMillis();

    @Ignore
    public DocumentEntity() {}

    public DocumentEntity(String subject, String topic, String fileName, String filePath, String extractedText) {
        this.subject = subject;
        this.topic = topic;
        this.fileName = fileName;
        this.filePath = filePath;
        this.extractedText = extractedText;
        this.timestamp = System.currentTimeMillis();
    }
}
