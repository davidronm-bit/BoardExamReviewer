package com.example.boardexamreviewer.data.local;

import androidx.room.*;
import com.example.boardexamreviewer.data.local.entities.*;
import java.util.List;

@Dao
public interface AppDao {
    // --- Documents ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertDocument(DocumentEntity document);

    @Query("SELECT * FROM documents ORDER BY timestamp DESC")
    List<DocumentEntity> getAllDocuments();

    @Query("SELECT DISTINCT subject FROM documents ORDER BY subject COLLATE NOCASE ASC")
    List<String> getAllSubjects();

    @Query("SELECT DISTINCT topic FROM documents WHERE subject = :subject COLLATE NOCASE ORDER BY topic COLLATE NOCASE ASC")
    List<String> getTopicsBySubject(String subject);

    @Query("SELECT * FROM documents WHERE subject = :subject COLLATE NOCASE AND topic = :topic COLLATE NOCASE")
    List<DocumentEntity> getDocumentsByCategory(String subject, String topic);

    @Query("SELECT * FROM documents ORDER BY timestamp DESC LIMIT 1")
    DocumentEntity getLastDocument();

    @Query("UPDATE documents SET subject = :newSubject WHERE subject = :oldSubject COLLATE NOCASE")
    void updateSubjectGlobally(String oldSubject, String newSubject);

    @Query("UPDATE documents SET topic = :newTopic WHERE subject = :subject COLLATE NOCASE AND topic = :oldTopic COLLATE NOCASE")
    void updateTopicGlobally(String subject, String oldTopic, String newTopic);

    @Update
    void updateDocument(DocumentEntity document);

    @Delete
    void deleteDocument(DocumentEntity document);

    // --- Quizzes ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertQuiz(QuizEntity quiz);

    @Query("SELECT * FROM quizzes ORDER BY timestamp DESC")
    List<QuizEntity> getAllQuizzes();

    // --- Progress Tracking ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertStudySession(StudySessionEntity session);

    @Query("SELECT * FROM study_sessions ORDER BY timestamp DESC")
    List<StudySessionEntity> getAllStudySessions();

    @Query("SELECT SUM(durationMinutes) FROM study_sessions WHERE sessionType = 'Work'")
    Integer getTotalStudyTime();
}
