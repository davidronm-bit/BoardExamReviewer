package com.example.boardexamreviewer.ui.pomodoro;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.TextWatcher;
import androidx.appcompat.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.boardexamreviewer.data.local.AppDatabase;
import com.example.boardexamreviewer.data.local.entities.StudySessionEntity;
import com.example.boardexamreviewer.databinding.FragmentPomodoroBinding;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This fragment manages the study timer, white noise, and session tracking.
 */
public class PomodoroFragment extends Fragment {

    private FragmentPomodoroBinding binding;
    private TimerService timerService;
    private boolean isBound = false;
    private boolean isWorking = true;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private final TimerService.OnTickListener fragmentTickListener = (studyTime, breakTime) -> {
        if (getActivity() != null) {
            getActivity().runOnUiThread(this::updateUIFromService);
        }
    };
    private final TimerService.OnFinishListener fragmentFinishListener = () -> {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                showAlarmPopup();
                updateUIFromService();
            });
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TimerService.TimerBinder binder = (TimerService.TimerBinder) service;
            timerService = binder.getService();
            
            isBound = true;
            
            SharedPreferences prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
            String savedNoise = prefs.getString("selected_ambient", "None");
            String savedAlarm = prefs.getString("selected_alarm", "Wake Up");
            timerService.setSelectedAmbient(savedNoise);
            timerService.setSelectedAlarm(savedAlarm);

            setupServiceListeners();
            updateUIFromService();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isBound = false;
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentPomodoroBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Intent serviceIntent = new Intent(requireContext(), TimerService.class);
        requireContext().startService(serviceIntent);
        requireContext().bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);

        setupSpinners();

        binding.etWorkTime.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                String val = s.toString();
                if (val.startsWith("0")) {
                    s.replace(0, val.length(), val.replaceFirst("^0+", ""));
                }
                if (timerService == null || !timerService.isTimerRunning) {
                    try {
                        long mins = Long.parseLong(s.toString());
                        updateCountDownText(mins * 60 * 1000);
                    } catch (NumberFormatException e) {
                        updateCountDownText(25 * 60 * 1000);
                    }
                }
            }
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });

        binding.etBreakTime.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                String val = s.toString();
                if (val.startsWith("0")) {
                    s.replace(0, val.length(), val.replaceFirst("^0+", ""));
                }
            }
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });

        binding.btnStartPause.setOnClickListener(v -> {
            if (timerService != null && timerService.isTimerRunning) {
                pauseTimer();
            } else {
                startTimer();
            }
        });

        binding.btnStop.setOnClickListener(v -> stopTimer());
    }

    private void setupServiceListeners() {
        timerService.addOnTickListener(fragmentTickListener);
        timerService.addOnFinishListener(fragmentFinishListener);
    }

    private void updateUIFromService() {
        if (timerService != null) {
            boolean isRunning = timerService.isTimerRunning;
            boolean isBreak = timerService.isBreakMode;
            boolean isStudyExt = timerService.isStudyExtension && isRunning;
            
            boolean targetWorkEnabled = !isRunning && !isBreak && !isStudyExt;
            if (binding.etWorkTime.isEnabled() != targetWorkEnabled) {
                binding.etWorkTime.setEnabled(targetWorkEnabled);
            }
            if (binding.etBreakTime.isEnabled() != targetWorkEnabled) {
                binding.etBreakTime.setEnabled(targetWorkEnabled);
            }
            
            boolean targetBtnEnabled = !isBreak && !isStudyExt;
            if (binding.btnStartPause.isEnabled() != targetBtnEnabled) {
                binding.btnStartPause.setEnabled(targetBtnEnabled);
            }
            if (binding.btnStop.isEnabled() != targetBtnEnabled) {
                binding.btnStop.setEnabled(targetBtnEnabled);
            }
            if (binding.spinnerWhiteNoise.isEnabled() != targetBtnEnabled) {
                binding.spinnerWhiteNoise.setEnabled(targetBtnEnabled);
            }
            if (binding.spinnerAlarm.isEnabled() != targetBtnEnabled) {
                binding.spinnerAlarm.setEnabled(targetBtnEnabled);
            }

            if (isRunning || timerService.studyTimeLeftInMillis > 0 || timerService.breakTimeLeftInMillis > 0) {
                if (!isBreak) {
                    updateCountDownText(timerService.studyTimeLeftInMillis);
                } else {
                    updateCountDownText(0);
                }
                isWorking = !isBreak;
            } else {
                long mins;
                try {
                    mins = Long.parseLong(isWorking ? binding.etWorkTime.getText().toString() : binding.etBreakTime.getText().toString());
                } catch (NumberFormatException e) {
                    mins = isWorking ? 25 : 5;
                }
                updateCountDownText(mins * 60 * 1000);
            }
            
            if (isRunning) {
                binding.btnStartPause.setText("PAUSE");
            } else if (timerService.studyTimeLeftInMillis > 0 || timerService.breakTimeLeftInMillis > 0) {
                binding.btnStartPause.setText("RESUME");
            } else {
                binding.btnStartPause.setText(isWorking ? "START FOCUS SESSION" : "START BREAK");
            }
        }
    }

    private void setupSpinners() {
        if (getContext() == null) return;
        SharedPreferences prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        
        // 1. Setup Ambient Sound Dropdown with FILTERING DISABLED
        String[] noises = {"None", "Rain", "Fireplace", "Forest", "Snow", "Rough Winds"};
        ArrayAdapter<String> noiseAdapter = new ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_1, noises) {
            @NonNull
            @Override
            public android.widget.Filter getFilter() {
                return new android.widget.Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence constraint) {
                        FilterResults results = new FilterResults();
                        results.values = noises;
                        results.count = noises.length;
                        return results;
                    }
                    @Override
                    protected void publishResults(CharSequence constraint, FilterResults results) {
                        notifyDataSetChanged();
                    }
                };
            }
        };
        binding.spinnerWhiteNoise.setAdapter(noiseAdapter);
        String savedNoise = prefs.getString("selected_ambient", "None");
        binding.spinnerWhiteNoise.setText(savedNoise, false);
        
        binding.spinnerWhiteNoise.setOnItemClickListener((parent, view, position, id) -> {
            String selected = (String) parent.getItemAtPosition(position);
            prefs.edit().putString("selected_ambient", selected).apply();
            if (timerService != null) {
                timerService.setSelectedAmbient(selected);
                if (timerService.isTimerRunning) {
                    timerService.playAmbient(selected);
                }
            }
        });

        // 2. Setup Alarm Sound Dropdown with FILTERING DISABLED
        String[] alarms = {"None", "Wake Up", "Christmas", "Danger", "Morning Flower", "Nuclear", "Rock"};
        ArrayAdapter<String> alarmAdapter = new ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_1, alarms) {
            @NonNull
            @Override
            public android.widget.Filter getFilter() {
                return new android.widget.Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence constraint) {
                        FilterResults results = new FilterResults();
                        results.values = alarms;
                        results.count = alarms.length;
                        return results;
                    }
                    @Override
                    protected void publishResults(CharSequence constraint, FilterResults results) {
                        notifyDataSetChanged();
                    }
                };
            }
        };
        binding.spinnerAlarm.setAdapter(alarmAdapter);
        String savedAlarm = prefs.getString("selected_alarm", "Wake Up");
        binding.spinnerAlarm.setText(savedAlarm, false);
        
        binding.spinnerAlarm.setOnItemClickListener((parent, view, position, id) -> {
            String selected = (String) parent.getItemAtPosition(position);
            prefs.edit().putString("selected_alarm", selected).apply();
            if (timerService != null) {
                timerService.setSelectedAlarm(selected);
            }
        });
    }

    private void startTimer() {
        if (timerService == null) return;
        
        long workMins = 25;
        long breakMins = 5;
        try {
            String wStr = binding.etWorkTime.getText().toString();
            if (!wStr.isEmpty()) {
                workMins = Long.parseLong(wStr);
                if (workMins <= 0) workMins = 25;
            }
        } catch (NumberFormatException e) {}
        try {
            String bStr = binding.etBreakTime.getText().toString();
            if (!bStr.isEmpty()) {
                breakMins = Long.parseLong(bStr);
                if (breakMins <= 0) breakMins = 5;
            }
        } catch (NumberFormatException e) {}

        SharedPreferences prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        prefs.edit()
                .putLong("work_time_mins", workMins)
                .putLong("break_time_mins", breakMins)
                .apply();

        long duration;
        if (timerService.timeLeftInMillis > 0) {
            duration = timerService.timeLeftInMillis;
        } else {
            long mins = isWorking ? workMins : breakMins;
            duration = mins * 60 * 1000;
        }
        timerService.startTimer(duration, !isWorking);
        
        String selectedAmbient = binding.spinnerWhiteNoise.getText().toString();
        timerService.playAmbient(selectedAmbient);
        
        binding.btnStartPause.setText("Pause");
    }

    private void pauseTimer() {
        if (timerService != null) {
            timerService.pauseTimer();
            binding.btnStartPause.setText("Resume");
        }
    }

    private void stopTimer() {
        if (timerService != null) {
            timerService.resetTimer();
        }
        isWorking = true;
        long workMins;
        try {
            workMins = Long.parseLong(binding.etWorkTime.getText().toString());
        } catch (NumberFormatException e) {
            workMins = 25;
        }
        updateCountDownText(workMins * 60 * 1000);
        binding.btnStartPause.setText("START FOCUS SESSION");
        binding.etWorkTime.setEnabled(true);
        binding.etBreakTime.setEnabled(true);
    }

    private void updateCountDownText(long millis) {
        if (binding == null) return;
        long minutes = (millis / 1000) / 60;
        long seconds = (millis / 1000) % 60;
        binding.tvTimerDisplay.setText(String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds));
    }

    private void showAlarmPopup() {
        if (timerService == null) return;
        
        new AlertDialog.Builder(requireContext())
            .setTitle("Time's Up!")
            .setMessage(!isWorking ? "Break time is over! Ready to focus?" : "Focus session finished! Time for a rest?")
            .setCancelable(false)
            .setPositiveButton("Stop Alarm", (dialog, which) -> {
                if (timerService != null) {
                    timerService.stopAlarm();
                    timerService.stopAmbient();
                }
            })
            .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (isBound && timerService != null) {
            timerService.removeOnTickListener(fragmentTickListener);
            timerService.removeOnFinishListener(fragmentFinishListener);
            requireContext().unbindService(connection);
            isBound = false;
        }
        binding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}
