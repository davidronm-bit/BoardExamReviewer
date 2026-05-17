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
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import com.example.boardexamreviewer.R;
import com.example.boardexamreviewer.data.local.AppDatabase;
import com.example.boardexamreviewer.data.local.entities.*;
import com.example.boardexamreviewer.data.network.models.*;
import com.example.boardexamreviewer.databinding.FragmentQuizBinding;
import com.example.boardexamreviewer.utils.AppConfig;
import com.example.boardexamreviewer.utils.GeminiClient;
import com.example.boardexamreviewer.utils.NetworkHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * This fragment uses AI to generate questions from multiple study materials.
 */
public class QuizFragment extends Fragment {

    private FragmentQuizBinding binding;
    private List<QuizQuestion> currentQuestions = new ArrayList<>();
    private List<Integer> selectedSourceIds = new ArrayList<>();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentQuizBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnGenerateQuiz.setOnClickListener(v -> generateQuiz());
        binding.btnViewHistory.setOnClickListener(v -> 
            NavHostFragment.findNavController(this).navigate(R.id.navigation_saved)
        );
        binding.btnSelectQuizSource.setOnClickListener(v -> showSourceSelectionDialog());

        int requestedQuizId = getArguments() != null ? getArguments().getInt("quizId", -1) : -1;
        if (requestedQuizId != -1) {
            loadSpecificQuiz(requestedQuizId);
        }
    }

    private void showSourceSelectionDialog() {
        Context appContext = requireContext().getApplicationContext();

        executorService.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(appContext);
            List<DocumentEntity> docs = db.appDao().getAllDocuments();
            
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (docs.isEmpty()) {
                        Toast.makeText(getContext(), "No study materials found!", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String[] allOptions = new String[docs.size()];
                    boolean[] checkedItems = new boolean[docs.size()];
                    for (int i = 0; i < docs.size(); i++) {
                        DocumentEntity d = docs.get(i);
                        allOptions[i] = "[" + d.subject + "] " + d.topic;
                        checkedItems[i] = selectedSourceIds.contains(d.id);
                    }

                    List<Integer> tempSelected = new ArrayList<>(selectedSourceIds);

                    new AlertDialog.Builder(requireContext())
                        .setTitle("Select Topics (Multi-select)")
                        .setMultiChoiceItems(allOptions, checkedItems, (dialog, which, isChecked) -> {
                            int id = docs.get(which).id;
                            if (isChecked) {
                                if (!tempSelected.contains(id)) tempSelected.add(id);
                            } else {
                                tempSelected.remove(Integer.valueOf(id));
                            }
                        })
                        .setPositiveButton("OK", (dialog, which) -> {
                            selectedSourceIds.clear();
                            selectedSourceIds.addAll(tempSelected);
                            if (selectedSourceIds.isEmpty()) {
                                binding.tvQuizSource.setText("Source: Latest Upload");
                            } else {
                                binding.tvQuizSource.setText("Source: " + selectedSourceIds.size() + " Topics Selected");
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                });
            }
        });
    }

    private void loadSpecificQuiz(int quizId) {
        Context appContext = getContext() != null ? getContext().getApplicationContext() : null;
        if (appContext == null) return;

        executorService.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(appContext);
            List<QuizEntity> quizzes = db.appDao().getAllQuizzes();
            QuizEntity quiz = null;
            for (QuizEntity q : quizzes) {
                if (q.id == quizId) {
                    quiz = q;
                    break;
                }
            }

            if (quiz != null) {
                final QuizEntity finalQuiz = quiz;
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (binding != null) {
                            navigateToActiveQuiz(finalQuiz.questionsJson, finalQuiz.title);
                            Toast.makeText(getContext(), "Retaking: " + finalQuiz.title, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }

    private void navigateToActiveQuiz(String json, String title) {
        Bundle bundle = new Bundle();
        bundle.putString("quizJson", json);
        bundle.putString("title", title);
        bundle.putIntegerArrayList("sourceIds", new ArrayList<>(selectedSourceIds));
        NavHostFragment.findNavController(this).navigate(R.id.navigation_quiz_active, bundle);
    }

    private void generateQuiz() {
        Context appContext = getContext() != null ? getContext().getApplicationContext() : null;
        if (appContext == null) return;

        if (!NetworkHelper.isInternetAvailable(appContext)) {
            Toast.makeText(getContext(), "Please connect to the internet", Toast.LENGTH_SHORT).show();
            return;
        }

        int qCount;
        try {
            qCount = Integer.parseInt(binding.etQuestionCount.getText().toString());
            if (qCount < 1) qCount = 5;
            if (qCount > 20) qCount = 20; 
        } catch (Exception e) {
            qCount = 5;
        }

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnGenerateQuiz.setEnabled(false);

        final int finalQCount = qCount;

        executorService.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getDatabase(appContext);
                StringBuilder sourceTextBuilder = new StringBuilder();
                StringBuilder topicsListBuilder = new StringBuilder();
                
                List<DocumentEntity> allDocs = db.appDao().getAllDocuments();
                if (selectedSourceIds.isEmpty()) {
                    DocumentEntity lastDoc = db.appDao().getLastDocument();
                    if (lastDoc != null) {
                        sourceTextBuilder.append(lastDoc.extractedText);
                        topicsListBuilder.append(lastDoc.topic);
                    }
                } else {
                    for (DocumentEntity d : allDocs) {
                        if (selectedSourceIds.contains(d.id)) {
                            sourceTextBuilder.append(d.extractedText).append("\n\n");
                            if (topicsListBuilder.length() > 0) topicsListBuilder.append(", ");
                            topicsListBuilder.append(d.topic);
                        }
                    }
                }

                String sourceText = sourceTextBuilder.toString();
                if (sourceText.length() > 30000) {
                    sourceText = sourceText.substring(0, 30000);
                }
                String topicName = topicsListBuilder.toString();

                if (sourceText.length() < 10) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (binding != null) {
                                Toast.makeText(getContext(), "Upload study material first!", Toast.LENGTH_SHORT).show();
                                binding.progressBar.setVisibility(View.GONE);
                                binding.btnGenerateQuiz.setEnabled(true);
                            }
                        });
                    }
                    return;
                }

                String prompt = "Generate " + finalQCount + " multiple choice questions for a board exam based on this text:\n" +
                                sourceText + "\n" +
                                "Return ONLY a JSON array with: question, optionA, optionB, optionC, optionD, correctAnswer (A, B, C, or D).";

                List<GeminiRequest.Part> parts = new ArrayList<>();
                parts.add(new GeminiRequest.Part(prompt));
                List<GeminiRequest.Content> contents = new ArrayList<>();
                contents.add(new GeminiRequest.Content(parts));
                GeminiRequest request = new GeminiRequest(contents);

                final String finalTopicName = topicName;
                String apiKey = AppConfig.GEMINI_API_KEY;

                GeminiClient.apiService.generateContent(AppConfig.GEMINI_MODEL, apiKey, request).enqueue(new Callback<GeminiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<GeminiResponse> call, @NonNull Response<GeminiResponse> response) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                if (binding != null) {
                                    binding.progressBar.setVisibility(View.GONE);
                                    binding.btnGenerateQuiz.setEnabled(true);

                                    if (response.isSuccessful() && response.body() != null) {
                                        GeminiResponse body = response.body();
                                        if (body.candidates != null && !body.candidates.isEmpty() && 
                                            body.candidates.get(0).content != null && 
                                            body.candidates.get(0).content.parts != null && 
                                            !body.candidates.get(0).content.parts.isEmpty()) {
                                            
                                            String jsonString = body.candidates.get(0).content.parts.get(0).text;
                                            if (jsonString != null && !jsonString.trim().isEmpty()) {
                                                int firstBracket = jsonString.indexOf("[");
                                                int lastBracket = jsonString.lastIndexOf("]");
                                                if (firstBracket != -1 && lastBracket != -1) {
                                                    String cleanJson = jsonString.substring(firstBracket, lastBracket + 1);
                                                    navigateToActiveQuiz(cleanJson, "Quiz: " + (finalTopicName.length() > 30 ? "Multiple Topics" : finalTopicName));
                                                } else {
                                                    Toast.makeText(getContext(), "AI format error. It didn't return a proper list.", Toast.LENGTH_SHORT).show();
                                                }
                                            } else {
                                                Toast.makeText(getContext(), "AI returned an empty response.", Toast.LENGTH_SHORT).show();
                                            }
                                        } else {
                                            // Safety filter or blocked content often ends up here
                                            Toast.makeText(getContext(), "AI blocked the content or returned nothing. Try a different topic.", Toast.LENGTH_LONG).show();
                                        }
                                    } else {
                                        String errorBody = "";
                                        try {
                                            if (response.errorBody() != null) {
                                                errorBody = response.errorBody().string();
                                            }
                                        } catch (Exception e) {
                                            errorBody = "Unknown error";
                                        }
                                        
                                        if (errorBody.contains("not found") || errorBody.contains("404")) {
                                            Toast.makeText(getContext(), "Model Not Found: Your account might not have Gemini 3 Flash yet.", Toast.LENGTH_LONG).show();
                                        } else {
                                            Toast.makeText(getContext(), "AI Error " + response.code() + ": " + errorBody, Toast.LENGTH_LONG).show();
                                        }
                                    }
                                }
                            });
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<GeminiResponse> call, @NonNull Throwable t) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                if (binding != null) {
                                    binding.progressBar.setVisibility(View.GONE);
                                    binding.btnGenerateQuiz.setEnabled(true);
                                    Toast.makeText(getContext(), "Error: " + t.getMessage(), Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    }
                });
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (binding != null) {
                            binding.progressBar.setVisibility(View.GONE);
                            binding.btnGenerateQuiz.setEnabled(true);
                            Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });
    }

    private void handleAiError(int code) {
        String message;
        switch (code) {
            case 400: message = "AI Error 400: Bad Request. Check model/prompt."; break;
            case 401: message = "AI Error 401: Invalid API Key. Please Sync Project with Gradle."; break;
            case 403: message = "AI Error 403: Permission Denied. Check API key restrictions."; break;
            case 404: message = "AI Error 404: Endpoint not found. Check URL/Model."; break;
            case 429: message = "AI Error 429: Too many requests. Wait a moment."; break;
            case 500: message = "AI Error 500: Internal Server Error."; break;
            default: message = "AI Error: " + code; break;
        }
        Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
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
