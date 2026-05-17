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
            
            AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(navView.getMenu()).build();
            NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        }

        Intent serviceIntent = new Intent(this, TimerService.class);
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);
    }

    private void setupTimerListeners() {
        if (timerService == null) return;

        TimerService.OnTickListener existingTick = timerService.onTickListener;
        timerService.onTickListener = millis -> {
            if (existingTick != null) existingTick.onTick(millis);
            updateLockdownUI(millis);
        };

        TimerService.OnFinishListener existingFinish = timerService.onFinishListener;
        timerService.onFinishListener = () -> {
            if (existingFinish != null) existingFinish.onFinish();
            runOnUiThread(() -> {
                if (timerService.isBreakMode) {
                    binding.lockdownOverlay.setVisibility(View.VISIBLE);
                } else {
                    binding.lockdownOverlay.setVisibility(View.GONE);
                }
            });
        };
        
        runOnUiThread(() -> {
            if (timerService.isTimerRunning && timerService.isBreakMode) {
                binding.lockdownOverlay.setVisibility(View.VISIBLE);
                updateLockdownUI(timerService.timeLeftInMillis);
            } else {
                binding.lockdownOverlay.setVisibility(View.GONE);
            }
        });
    }

    private void updateLockdownUI(long millis) {
        runOnUiThread(() -> {
            if (timerService != null && timerService.isBreakMode && timerService.isTimerRunning) {
                binding.lockdownOverlay.setVisibility(View.VISIBLE);
                long minutes = (millis / 1000) / 60;
                long seconds = (millis / 1000) % 60;
                binding.tvLockdownTimer.setText(String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds));
            } else {
                binding.lockdownOverlay.setVisibility(View.GONE);
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
        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
    }
}
