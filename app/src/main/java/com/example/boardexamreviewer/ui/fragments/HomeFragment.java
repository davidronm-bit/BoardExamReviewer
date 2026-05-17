package com.example.boardexamreviewer.ui.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import com.example.boardexamreviewer.R;
import com.example.boardexamreviewer.data.local.AppDatabase;
import com.example.boardexamreviewer.data.local.entities.DocumentEntity;
import com.example.boardexamreviewer.databinding.FragmentHomeBinding;
import com.example.boardexamreviewer.utils.DocumentExtractor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * HomeFragment is the first screen users see.
 * It contains buttons to upload documents and navigate to other parts of the app.
 */
public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                Uri uri = result.getData().getData();
                if (uri != null) {
                    handleSelectedFile(uri);
                }
            }
        }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnUpload.setOnClickListener(v -> openFilePicker());
        
        binding.btnViewFiles.setOnClickListener(v -> 
            NavHostFragment.findNavController(this).navigate(R.id.navigation_files)
        );

        binding.btnGenQuiz.setOnClickListener(v -> 
            NavHostFragment.findNavController(this).navigate(R.id.navigation_quiz)
        );

        binding.btnPomodoro.setOnClickListener(v -> 
            NavHostFragment.findNavController(this).navigate(R.id.navigation_pomodoro)
        );
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimeTypes = {
            "application/pdf", 
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain"
        };
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        filePickerLauncher.launch(intent);
    }

    private void handleSelectedFile(Uri uri) {
        String fileName = DocumentExtractor.getFileName(requireContext(), uri);

        android.widget.LinearLayout layout = new android.widget.LinearLayout(requireContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final android.widget.TextView tvFileName = new android.widget.TextView(requireContext());
        tvFileName.setText("File: " + fileName);
        tvFileName.setTextSize(16);
        tvFileName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvFileName.setPadding(0, 0, 0, 20);
        layout.addView(tvFileName);

        final android.widget.EditText etSubject = new android.widget.EditText(requireContext());
        etSubject.setHint("Subject (e.g. Mathematics)");
        layout.addView(etSubject);

        final android.widget.EditText etTopic = new android.widget.EditText(requireContext());
        etTopic.setHint("Topic (e.g. Algebra)");
        layout.addView(etTopic);

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Categorize Material")
            .setView(layout)
            .setCancelable(false)
            .setPositiveButton("Upload", (dialog, which) -> {
                String subject = normalizeString(etSubject.getText().toString());
                String topic = normalizeString(etTopic.getText().toString());
                if (subject.isEmpty() || topic.isEmpty()) {
                    Toast.makeText(getContext(), "Please fill all fields", Toast.LENGTH_SHORT).show();
                    handleSelectedFile(uri); // Retry
                    return;
                }
                saveFileToDb(uri, subject, topic);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private String normalizeString(String input) {
        if (input == null || input.isEmpty()) return "";
        String trimmed = input.trim();
        if (trimmed.isEmpty()) return "";
        
        // Use Title Case: capitalize first letter, rest lowercase
        if (trimmed.length() == 1) return trimmed.toUpperCase();
        return trimmed.substring(0, 1).toUpperCase() + trimmed.substring(1).toLowerCase();
    }

    private void saveFileToDb(Uri uri, String subject, String topic) {
        Context appContext = requireContext().getApplicationContext();
        binding.progressBar.setVisibility(View.VISIBLE);

        executorService.execute(() -> {
            try {
                String text = DocumentExtractor.extractText(appContext, uri);
                String fileName = DocumentExtractor.getFileName(appContext, uri);

                AppDatabase db = AppDatabase.getDatabase(appContext);
                DocumentEntity doc = new DocumentEntity(
                    subject,
                    topic,
                    fileName, 
                    uri.toString(), 
                    text
                );
                db.appDao().insertDocument(doc);

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (binding != null) {
                            binding.progressBar.setVisibility(View.GONE);
                            Toast.makeText(getContext(), "Uploaded: " + fileName, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (binding != null) {
                            binding.progressBar.setVisibility(View.GONE);
                            Toast.makeText(getContext(), "Upload Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
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
