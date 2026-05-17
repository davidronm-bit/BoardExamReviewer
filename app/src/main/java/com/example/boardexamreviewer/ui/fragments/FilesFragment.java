package com.example.boardexamreviewer.ui.fragments;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.navigation.fragment.NavHostFragment;
import androidx.activity.result.contract.ActivityResultContracts;
import com.example.boardexamreviewer.R;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.boardexamreviewer.data.local.AppDatabase;
import com.example.boardexamreviewer.data.local.entities.DocumentEntity;
import com.example.boardexamreviewer.databinding.FragmentFilesBinding;
import com.example.boardexamreviewer.databinding.ItemFileBinding;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * FilesFragment shows a list of all documents you have uploaded with filtering by Subject and Topic.
 */
public class FilesFragment extends Fragment {

    private FragmentFilesBinding binding;
    private FilesAdapter adapter;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    
    private List<DocumentEntity> allDocuments = new ArrayList<>();
    private String selectedSubject = "All Subjects";
    private String selectedTopic = "All Topics";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentFilesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new FilesAdapter(
            this::showExtractedText,
            this::deleteDocument,
            this::showEditCategoryDialog,
            this::startQuizForDoc
        );

        binding.rvFiles.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvFiles.setAdapter(adapter);

        binding.btnNewFolder.setOnClickListener(v -> 
            Toast.makeText(getContext(), "Folder feature coming soon!", Toast.LENGTH_SHORT).show()
        );
        binding.btnUpload.setOnClickListener(v -> openFilePicker());
        binding.btnStartQuiz.setOnClickListener(v -> 
            NavHostFragment.findNavController(this).navigate(R.id.navigation_quiz)
        );

        setupFilters();
        loadDocuments();
    }

    private final androidx.activity.result.ActivityResultLauncher<android.content.Intent> filePickerLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
            if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                android.net.Uri uri = result.getData().getData();
                if (uri != null) handleSelectedFile(uri);
            }
        }
    );

    private void openFilePicker() {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimeTypes = {"application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/vnd.openxmlformats-officedocument.presentationml.presentation", "text/plain"};
        intent.putExtra(android.content.Intent.EXTRA_MIME_TYPES, mimeTypes);
        filePickerLauncher.launch(intent);
    }

    private void handleSelectedFile(android.net.Uri uri) {
        Toast.makeText(getContext(), "Processing file...", Toast.LENGTH_SHORT).show();
        Toast.makeText(getContext(), "Please use the Home screen to upload and categorize for now.", Toast.LENGTH_LONG).show();
    }

    private void startQuizForDoc(DocumentEntity doc) {
        NavHostFragment.findNavController(this).navigate(R.id.navigation_quiz);
    }

    private void setupFilters() {
        binding.spinnerSubject.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedSubject = parent.getItemAtPosition(position).toString();
                updateTopicSpinner();
                applyFilters();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        binding.spinnerTopic.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedTopic = parent.getItemAtPosition(position).toString();
                applyFilters();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadDocuments() {
        Context appContext = getContext() != null ? getContext().getApplicationContext() : null;
        if (appContext == null) return;

        binding.progressBar.setVisibility(View.VISIBLE);
        
        executorService.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(appContext);
            allDocuments = db.appDao().getAllDocuments();
            List<String> subjects = db.appDao().getAllSubjects();
            
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (binding != null) {
                        updateSubjectSpinner(subjects);
                        applyFilters();
                        binding.progressBar.setVisibility(View.GONE);
                    }
                });
            }
        });
    }

    private void updateSubjectSpinner(List<String> subjects) {
        List<String> options = new ArrayList<>();
        options.add("All Subjects");
        
        for (String s : subjects) {
            boolean exists = false;
            for (String opt : options) {
                if (opt.equalsIgnoreCase(s)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) options.add(s);
        }
        
        if (getContext() != null) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, options);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            binding.spinnerSubject.setAdapter(adapter);
        }
    }

    private void updateTopicSpinner() {
        List<String> options = new ArrayList<>();
        options.add("All Topics");
        
        for (DocumentEntity doc : allDocuments) {
            if (selectedSubject.equals("All Subjects") || doc.subject.equalsIgnoreCase(selectedSubject)) {
                boolean exists = false;
                for (String opt : options) {
                    if (opt.equalsIgnoreCase(doc.topic)) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) options.add(doc.topic);
            }
        }
        
        if (getContext() != null) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, options);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            binding.spinnerTopic.setAdapter(adapter);
        }
    }

    private void applyFilters() {
        List<DocumentEntity> filtered = new ArrayList<>();
        for (DocumentEntity doc : allDocuments) {
            boolean subjectMatch = selectedSubject.equals("All Subjects") || doc.subject.equalsIgnoreCase(selectedSubject);
            boolean topicMatch = selectedTopic.equals("All Topics") || doc.topic.equalsIgnoreCase(selectedTopic);
            
            if (subjectMatch && topicMatch) {
                filtered.add(doc);
            }
        }
        adapter.updateList(filtered);
    }

    private void showExtractedText(DocumentEntity doc) {
        ScrollView scrollView = new ScrollView(requireContext());
        TextView textView = new TextView(requireContext());
        textView.setText(doc.extractedText);
        textView.setPadding(40, 40, 40, 40);
        textView.setTextSize(16f);
        scrollView.addView(textView);

        new AlertDialog.Builder(requireContext())
            .setTitle(doc.fileName)
            .setView(scrollView)
            .setPositiveButton("OK", null)
            .show();
    }

    private void deleteDocument(DocumentEntity doc) {
        Context appContext = requireContext().getApplicationContext();
        new AlertDialog.Builder(requireContext())
            .setTitle("Delete Document")
            .setMessage("Are you sure you want to delete " + doc.fileName + "?")
            .setPositiveButton("Delete", (dialog, which) -> {
                executorService.execute(() -> {
                    AppDatabase db = AppDatabase.getDatabase(appContext);
                    db.appDao().deleteDocument(doc);
                    loadDocuments();
                });
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showEditCategoryDialog(DocumentEntity doc) {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(requireContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final android.widget.EditText etSubject = new android.widget.EditText(requireContext());
        etSubject.setHint("Subject");
        etSubject.setText(doc.subject);
        layout.addView(etSubject);

        final android.widget.EditText etTopic = new android.widget.EditText(requireContext());
        etTopic.setHint("Topic");
        etTopic.setText(doc.topic);
        layout.addView(etTopic);

        final android.widget.CheckBox cbGlobal = new android.widget.CheckBox(requireContext());
        cbGlobal.setText("Apply rename globally to all files in this category");
        cbGlobal.setChecked(true);
        layout.addView(cbGlobal);

        new AlertDialog.Builder(requireContext())
            .setTitle("Edit Categorization")
            .setView(layout)
            .setPositiveButton("Save", (dialog, which) -> {
                String newSub = etSubject.getText().toString().trim();
                String newTop = etTopic.getText().toString().trim();
                if (newSub.isEmpty() || newTop.isEmpty()) return;

                executorService.execute(() -> {
                    AppDatabase db = AppDatabase.getDatabase(requireContext());
                    if (cbGlobal.isChecked()) {
                        // Global rename logic
                        if (!newSub.equalsIgnoreCase(doc.subject)) {
                            db.appDao().updateSubjectGlobally(doc.subject, newSub);
                        }
                        if (!newTop.equalsIgnoreCase(doc.topic)) {
                            db.appDao().updateTopicGlobally(newSub, doc.topic, newTop);
                        }
                    } else {
                        // Single file update
                        doc.subject = newSub;
                        doc.topic = newTop;
                        db.appDao().updateDocument(doc);
                    }
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(this::loadDocuments);
                    }
                });
            })
            .setNegativeButton("Cancel", null)
            .show();
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

    // --- Adapter Class ---
    static class FilesAdapter extends RecyclerView.Adapter<FilesAdapter.ViewHolder> {
        private final List<DocumentEntity> items = new ArrayList<>();
        private final OnItemClickListener onItemClick;
        private final OnItemClickListener onDeleteClick;
        private final OnItemClickListener onLongClick;
        private final OnItemClickListener onQuizClick;

        interface OnItemClickListener {
            void onClick(DocumentEntity doc);
        }

        FilesAdapter(OnItemClickListener onItemClick, OnItemClickListener onDeleteClick, OnItemClickListener onLongClick, OnItemClickListener onQuizClick) {
            this.onItemClick = onItemClick;
            this.onDeleteClick = onDeleteClick;
            this.onLongClick = onLongClick;
            this.onQuizClick = onQuizClick;
        }

        void updateList(List<DocumentEntity> newList) {
            items.clear();
            items.addAll(newList);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemFileBinding binding = ItemFileBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(items.get(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final ItemFileBinding binding;

            ViewHolder(ItemFileBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }

            void bind(DocumentEntity item) {
                binding.tvFileName.setText(item.fileName);
                String info = item.subject + " > " + item.topic;
                binding.tvFileDate.setText(info);

                binding.getRoot().setOnClickListener(v -> onItemClick.onClick(item));
                binding.getRoot().setOnLongClickListener(v -> {
                    onLongClick.onClick(item);
                    return true;
                });
                binding.btnDelete.setOnClickListener(v -> onDeleteClick.onClick(item));
                binding.btnStartItemQuiz.setOnClickListener(v -> onQuizClick.onClick(item));
            }
        }
    }
}
