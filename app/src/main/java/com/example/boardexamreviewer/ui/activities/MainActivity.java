package com.example.boardexamreviewer.ui.activities;

import com.example.boardexamreviewer.R;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.MenuItem;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import com.example.boardexamreviewer.databinding.ActivityMainBinding;
import com.example.boardexamreviewer.ui.pomodoro.TimerService;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private TimerService timerService;
    private boolean isBound = false;

    private final TimerService.OnTickListener mainTickListener = (studyTime, breakTime) -> updateLockdownUI(breakTime);
    private final TimerService.OnFinishListener mainFinishListener = () -> {
        runOnUiThread(() -> {
            if (timerService != null && timerService.isBreakMode && timerService.isTimerRunning) {
                if (binding.lockdownOverlay.getVisibility() != View.VISIBLE) {
                    binding.lockdownOverlay.setVisibility(View.VISIBLE);
                }
                int targetExtendVis = !timerService.wasExtended ? View.VISIBLE : View.GONE;
                if (binding.btnLockdownExtend.getVisibility() != targetExtendVis) {
                    binding.btnLockdownExtend.setVisibility(targetExtendVis);
                }
            } else {
                if (binding.lockdownOverlay.getVisibility() != View.GONE) {
                    binding.lockdownOverlay.setVisibility(View.GONE);
                }
            }
            if (timerService != null) {
                boolean isStudyExt = timerService.isStudyExtension && timerService.isTimerRunning;
                for (int i = 0; i < binding.bottomNavigation.getMenu().size(); i++) {
                    MenuItem item = binding.bottomNavigation.getMenu().getItem(i);
                    if (item.isEnabled() != !isStudyExt) {
                        item.setEnabled(!isStudyExt);
                    }
                }
            }
        });
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TimerService.TimerBinder binder = (TimerService.TimerBinder) service;
            timerService = binder.getService();
            isBound = true;
            setupTimerListeners();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        BottomNavigationView navView = binding.bottomNavigation;
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        
        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();
            NavigationUI.setupWithNavController(navView, navController);
            
            navView.setOnItemSelectedListener(item -> {
                navController.navigate(item.getItemId(), null, new androidx.navigation.NavOptions.Builder()
                        .setLaunchSingleTop(true)
                        .setPopUpTo(R.id.navigation_home, false)
                        .build());
                return true;
            });

            navView.setOnItemReselectedListener(item -> {
                if (navController.getCurrentDestination() != null && navController.getCurrentDestination().getId() != item.getItemId()) {
                    navController.navigate(item.getItemId(), null, new androidx.navigation.NavOptions.Builder()
                            .setLaunchSingleTop(true)
                            .setPopUpTo(R.id.navigation_home, false)
                            .build());
                }
            });
            
            AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(navView.getMenu()).build();
            NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        }

        binding.btnLockdownExtend.setOnClickListener(v -> {
            if (timerService != null) {
                timerService.extendTimer(5 * 60 * 1000);
            }
        });

        Intent serviceIntent = new Intent(this, TimerService.class);
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);
    }

    private void setupTimerListeners() {
        if (timerService == null) return;

        timerService.addOnTickListener(mainTickListener);
        timerService.addOnFinishListener(mainFinishListener);
        
        runOnUiThread(() -> {
            if (timerService.isTimerRunning && timerService.isBreakMode) {
                if (binding.lockdownOverlay.getVisibility() != View.VISIBLE) {
                    binding.lockdownOverlay.setVisibility(View.VISIBLE);
                }
                int targetExtendVis = !timerService.wasExtended ? View.VISIBLE : View.GONE;
                if (binding.btnLockdownExtend.getVisibility() != targetExtendVis) {
                    binding.btnLockdownExtend.setVisibility(targetExtendVis);
                }
                updateLockdownUI(timerService.breakTimeLeftInMillis);
            } else {
                if (binding.lockdownOverlay.getVisibility() != View.GONE) {
                    binding.lockdownOverlay.setVisibility(View.GONE);
                }
            }
            boolean isStudyExt = timerService.isStudyExtension && timerService.isTimerRunning;
            for (int i = 0; i < binding.bottomNavigation.getMenu().size(); i++) {
                MenuItem item = binding.bottomNavigation.getMenu().getItem(i);
                if (item.isEnabled() != !isStudyExt) {
                    item.setEnabled(!isStudyExt);
                }
            }
        });
    }

    private void updateLockdownUI(long breakTime) {
        runOnUiThread(() -> {
            if (timerService != null && timerService.isBreakMode && timerService.isTimerRunning) {
                if (binding.lockdownOverlay.getVisibility() != View.VISIBLE) {
                    binding.lockdownOverlay.setVisibility(View.VISIBLE);
                }
                int targetExtendVis = !timerService.wasExtended ? View.VISIBLE : View.GONE;
                if (binding.btnLockdownExtend.getVisibility() != targetExtendVis) {
                    binding.btnLockdownExtend.setVisibility(targetExtendVis);
                }
                long minutes = (breakTime / 1000) / 60;
                long seconds = (breakTime / 1000) % 60;
                binding.tvLockdownTimer.setText(String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds));
            } else {
                if (binding.lockdownOverlay.getVisibility() != View.GONE) {
                    binding.lockdownOverlay.setVisibility(View.GONE);
                }
            }
            if (timerService != null) {
                boolean isStudyExt = timerService.isStudyExtension && timerService.isTimerRunning;
                for (int i = 0; i < binding.bottomNavigation.getMenu().size(); i++) {
                    MenuItem item = binding.bottomNavigation.getMenu().getItem(i);
                    if (item.isEnabled() != !isStudyExt) {
                        item.setEnabled(!isStudyExt);
                    }
                }
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            return Navigation.findNavController(this, R.id.nav_host_fragment).navigateUp() || super.onSupportNavigateUp();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timerService != null) {
            timerService.removeOnTickListener(mainTickListener);
            timerService.removeOnFinishListener(mainFinishListener);
        }
        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
    }
}
