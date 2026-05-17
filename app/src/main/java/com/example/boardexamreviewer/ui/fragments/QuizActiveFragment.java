package com.example.boardexamreviewer.ui.fragments;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.boardexamreviewer.R;
import com.example.boardexamreviewer.data.local.AppDatabase;
import com.example.boardexamreviewer.data.local.QuizParser;
import com.example.boardexamreviewer.data.local.entities.*;
import com.example.boardexamreviewer.databinding.FragmentQuizActiveBinding;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QuizActiveFragment extends Fragment {

    private FragmentQuizActiveBinding binding;
    private List<QuizQuestion> originalQuestions = new ArrayList<>();
    private List<Integer> sourceIds = new ArrayList<>();
    private String quizTitle = "Mixed Topics Quiz";
    private int totalUniqueQuestions = 0;
    private final java.util.Set<Integer> masteredIndices = new java.util.HashSet<>();
    private final List<View> questionViews = new ArrayList<>();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentQuizActiveBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            String json = getArguments().getString("quizJson", "");
            originalQuestions = QuizParser.fromJson(json);
            totalUniqueQuestions = originalQuestions.size();
            sourceIds = getArguments().getIntegerArrayList("sourceIds");
            quizTitle = getArguments().getString("title", "Mixed Topics Quiz");
        }

        startQuiz();

        binding.btnSubmitActive.setOnClickListener(v -> checkAnswers());
        binding.btnSaveActive.setOnClickListener(v -> saveResults());
    }

    private void startQuiz() {
        binding.activeQuestionsLayout.removeAllViews();
        questionViews.clear();
        masteredIndices.clear();
        binding.tvMasteryPercent.setText("0.0%");
        
        for (int i = 0; i < originalQuestions.size(); i++) {
            addQuestionView(originalQuestions.get(i), i);
        }
    }

    private void addQuestionView(QuizQuestion q, int originalIndex) {
        View qView = getLayoutInflater().inflate(R.layout.item_quiz_question_active, binding.activeQuestionsLayout, false);
        ((TextView) qView.findViewById(R.id.tv_active_question_text)).setText("Q: " + q.question);
        ((RadioButton) qView.findViewById(R.id.rb_active_a)).setText(q.optionA);
        ((RadioButton) qView.findViewById(R.id.rb_active_b)).setText(q.optionB);
        ((RadioButton) qView.findViewById(R.id.rb_active_c)).setText(q.optionC);
        ((RadioButton) qView.findViewById(R.id.rb_active_d)).setText(q.optionD);
        
        // Store metadata in tag
        qView.setTag(new QuestionMetadata(q, originalIndex));
        
        binding.activeQuestionsLayout.addView(qView);
        questionViews.add(qView);
    }

    private static class QuestionMetadata {
        final QuizQuestion question;
        final int originalIndex;
        boolean checked = false;

        QuestionMetadata(QuizQuestion question, int originalIndex) {
            this.question = question;
            this.originalIndex = originalIndex;
        }
    }

    private void checkAnswers() {
        List<QuestionMetadata> toRepeat = new ArrayList<>();
        boolean anyNewChecked = false;

        for (View qView : questionViews) {
            QuestionMetadata meta = (QuestionMetadata) qView.getTag();
            if (meta.checked) continue;

            RadioGroup rg = qView.findViewById(R.id.rg_active_options);
            int selectedId = rg.getCheckedRadioButtonId();
            if (selectedId == -1) continue; // Skip unanswered in this batch

            anyNewChecked = true;
            meta.checked = true;
            RadioButton selected = qView.findViewById(selectedId);
            int idx = rg.indexOfChild(selected);
            String letter = (idx == 0 ? "A" : (idx == 1 ? "B" : (idx == 2 ? "C" : "D")));

            // Disable to prevent re-editing
            for (int i = 0; i < rg.getChildCount(); i++) rg.getChildAt(i).setEnabled(false);

            if (letter.equals(meta.question.correctAnswer)) {
                qView.setBackgroundColor(android.graphics.Color.parseColor("#E8F5E9")); // Light Green
                masteredIndices.add(meta.originalIndex);
            } else {
                qView.setBackgroundColor(android.graphics.Color.parseColor("#FFEBEE")); // Light Red
                // Show correct answer hint
                TextView tvHint = new TextView(requireContext());
                tvHint.setText("Incorrect. Correct Answer: " + meta.question.correctAnswer);
                tvHint.setTextColor(android.graphics.Color.parseColor("#C62828"));
                tvHint.setPadding(0, 10, 0, 0);
                ((ViewGroup) qView).addView(tvHint);
                
                toRepeat.add(meta);
            }
        }

        if (!anyNewChecked) {
            Toast.makeText(getContext(), "Please answer at least one new question!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Add repeated questions at the end
        for (QuestionMetadata meta : toRepeat) {
            addQuestionView(meta.question, meta.originalIndex);
        }

        updateMasteryUI();
        
        if (toRepeat.isEmpty() && masteredIndices.size() == totalUniqueQuestions) {
            Toast.makeText(getContext(), "Perfect! You've mastered all questions.", Toast.LENGTH_LONG).show();
        } else if (!toRepeat.isEmpty()) {
            Toast.makeText(getContext(), "Some incorrect answers will be repeated at the bottom.", Toast.LENGTH_SHORT).show();
            // Scroll to the first repeated question
            binding.activeQuizScroll.post(() -> {
                View firstNew = questionViews.get(questionViews.size() - toRepeat.size());
                binding.activeQuizScroll.smoothScrollTo(0, firstNew.getTop());
            });
        }
    }

    private void updateMasteryUI() {
        double mastery = (double) masteredIndices.size() / totalUniqueQuestions * 100;
        binding.tvMasteryPercent.setText(String.format(java.util.Locale.getDefault(), "%.1f%%", mastery));
        
        if (mastery >= 75) {
            binding.tvMasteryPercent.setTextColor(android.graphics.Color.parseColor("#2E7D32"));
        } else {
            binding.tvMasteryPercent.setTextColor(android.graphics.Color.parseColor("#C62828"));
        }
    }

    private void saveResults() {
        double mastery = (double) masteredIndices.size() / totalUniqueQuestions * 100;
        Context appContext = requireContext().getApplicationContext();

        executorService.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(appContext);
            QuizEntity quiz = new QuizEntity(
                (sourceIds != null && !sourceIds.isEmpty()) ? sourceIds.get(0) : 0,
                quizTitle,
                QuizParser.toJson(originalQuestions),
                mastery
            );
            db.appDao().insertQuiz(quiz);
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> 
                    Toast.makeText(getContext(), "Progress Saved!", Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}
