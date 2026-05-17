package com.example.boardexamreviewer.ui.progress;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.boardexamreviewer.data.local.AppDatabase;
import com.example.boardexamreviewer.data.local.entities.StudySessionEntity;
import com.example.boardexamreviewer.databinding.FragmentProgressBinding;
import com.example.boardexamreviewer.databinding.ItemFileBinding;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This fragment shows the user's total study time and session history.
 */
public class ProgressFragment extends Fragment {

    private FragmentProgressBinding binding;
    private SessionAdapter adapter;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentProgressBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new SessionAdapter();
        binding.rvSessions.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvSessions.setAdapter(adapter);

        loadProgress();
    }

    private void loadProgress() {
        Context appContext = getContext() != null ? getContext().getApplicationContext() : null;
        if (appContext == null) return;

        executorService.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(appContext);
            Integer totalTimeInt = db.appDao().getTotalStudyTime();
            final int totalTime = totalTimeInt != null ? totalTimeInt : 0;
            final List<StudySessionEntity> sessions = db.appDao().getAllStudySessions();

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (binding != null) {
                        binding.tvTotalTime.setText(totalTime + " Minutes");
                        adapter.submitList(sessions);
                    }
                });
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

    // --- Adapter Class ---
    static class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.ViewHolder> {
        private final List<StudySessionEntity> items = new ArrayList<>();

        void submitList(List<StudySessionEntity> newList) {
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

            void bind(StudySessionEntity item) {
                binding.tvFileName.setText(item.durationMinutes + " min " + item.sessionType + " Session");
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
                binding.tvFileDate.setText(sdf.format(new Date(item.timestamp)));
                binding.btnDelete.setVisibility(View.GONE);
            }
        }
    }
}
