package com.example.boardexamreviewer.ui.pomodoro;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Binder;
import android.os.CountDownTimer;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import com.example.boardexamreviewer.ui.activities.MainActivity;
import com.example.boardexamreviewer.R;
import com.example.boardexamreviewer.data.local.AppDatabase;
import com.example.boardexamreviewer.data.local.entities.StudySessionEntity;
import java.util.ArrayList;
import java.util.List;

/**
 * This service runs in the background to keep the study timer alive.
 */
public class TimerService extends Service {

    private final IBinder binder = new TimerBinder();
    private CountDownTimer timer;
    private MediaPlayer ambientPlayer;
    private MediaPlayer alarmPlayer;
    
    public long timeLeftInMillis = 0;
    public boolean isTimerRunning = false;
    public boolean isBreakMode = false;
    public boolean isStudyExtension = false;
    public boolean wasExtended = false;
    
    public interface OnTickListener {
        void onTick(long millisUntilFinished);
    }
    
    public interface OnFinishListener {
        void onFinish();
    }
    
    private final List<OnTickListener> tickListeners = new ArrayList<>();
    private final List<OnFinishListener> finishListeners = new ArrayList<>();

    public void addOnTickListener(OnTickListener listener) {
        if (!tickListeners.contains(listener)) {
            tickListeners.add(listener);
        }
    }

    public void removeOnTickListener(OnTickListener listener) {
        tickListeners.remove(listener);
    }

    public void addOnFinishListener(OnFinishListener listener) {
        if (!finishListeners.contains(listener)) {
            finishListeners.add(listener);
        }
    }

    public void removeOnFinishListener(OnFinishListener listener) {
        finishListeners.remove(listener);
    }
    
    private String selectedAmbient = "None";
    private String selectedAlarm = "Wake Up";

    public class TimerBinder extends Binder {
        public TimerService getService() {
            return TimerService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    public void startTimer(long durationMillis, boolean breakMode) {
        this.isBreakMode = breakMode;
        if (!breakMode && !isStudyExtension) {
            wasExtended = false;
        }
        timeLeftInMillis = durationMillis;
        
        if (timer != null) {
            timer.cancel();
            timer = null;
        }

        timer = new CountDownTimer(timeLeftInMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timeLeftInMillis = millisUntilFinished;
                for (OnTickListener listener : new ArrayList<>(tickListeners)) {
                    listener.onTick(timeLeftInMillis);
                }
                updateNotification();
            }

            @Override
            public void onFinish() {
                isTimerRunning = false;
                stopAmbient();
                playAlarm(selectedAlarm);
                timeLeftInMillis = 0;

                if (isStudyExtension) {
                    saveSessionToDb(5);
                    isStudyExtension = false;
                    isBreakMode = false;
                    timeLeftInMillis = 0;
                    stopForeground(STOP_FOREGROUND_REMOVE);
                } else if (!isBreakMode) {
                    SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
                    long workMins = prefs.getLong("work_time_mins", 25);
                    saveSessionToDb(workMins);
                    isBreakMode = true;
                    long breakMins = prefs.getLong("break_time_mins", 5);
                    startTimer(breakMins * 60 * 1000, true);
                } else {
                    isBreakMode = false;
                    stopForeground(STOP_FOREGROUND_REMOVE);
                }

                for (OnFinishListener listener : new ArrayList<>(finishListeners)) {
                    listener.onFinish();
                }
            }
        }.start();

        isTimerRunning = true;
        startForeground(1, createNotification());
    }

    public void pauseTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        isTimerRunning = false;
        stopAudio();
        stopForeground(STOP_FOREGROUND_REMOVE);
    }

    private void saveSessionToDb(long mins) {
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getDatabase(getApplicationContext());
                db.appDao().insertStudySession(new StudySessionEntity((int) mins, "Work"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void extendTimer(long extraMillis) {
        if (isTimerRunning && isBreakMode && !wasExtended) {
            wasExtended = true;
            isStudyExtension = true;
            isBreakMode = false;
            startTimer(extraMillis, false);
        }
    }

    public void resetTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        isTimerRunning = false;
        isBreakMode = false;
        isStudyExtension = false;
        wasExtended = false;
        timeLeftInMillis = 0;
        stopAudio();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private Notification createNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        long minutes = (timeLeftInMillis / 1000) / 60;
        long seconds = (timeLeftInMillis / 1000) % 60;
        String timeText = String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds);

        return new NotificationCompat.Builder(this, "timer_channel")
            .setContentTitle("Study Session Active")
            .setContentText("Time remaining: " + timeText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .build();
    }

    private void updateNotification() {
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.notify(1, createNotification());
        }
    }

    public void setSelectedAmbient(String ambient) {
        this.selectedAmbient = ambient;
    }

    public void setSelectedAlarm(String alarm) {
        this.selectedAlarm = alarm;
    }

    public void playAmbient(String type) {
        this.selectedAmbient = type;
        if (ambientPlayer != null) {
            ambientPlayer.stop();
            ambientPlayer.release();
            ambientPlayer = null;
        }

        if (type == null || "None".equals(type)) return;

        int resId = 0;
        switch (type) {
            case "Rain": resId = R.raw.rain_ambience; break;
            case "Fireplace": resId = R.raw.fireplace_ambience; break;
            case "Forest": resId = R.raw.forest_ambience; break;
            case "Snow": resId = R.raw.snow_ambience; break;
            case "Rough Winds": resId = R.raw.rough_winds_ambience; break;
        }

        if (resId != 0) {
            try {
                ambientPlayer = MediaPlayer.create(this, resId);
                if (ambientPlayer != null) {
                    ambientPlayer.setLooping(true);
                    ambientPlayer.start();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void playAlarm(String type) {
        if (type == null || "None".equals(type)) return;
        
        try {
            if (alarmPlayer != null) {
                alarmPlayer.stop();
                alarmPlayer.release();
                alarmPlayer = null;
            }

            int resId = 0;
            switch (type) {
                case "Wake Up": resId = R.raw.wake_up_alarm; break;
                case "Christmas": resId = R.raw.christmas_alarm; break;
                case "Danger": resId = R.raw.danger_alarm; break;
                case "Morning Flower": resId = R.raw.morning_flower_alarm; break;
                case "Nuclear": resId = R.raw.nuclear_alarm; break;
                case "Rock": resId = R.raw.rock_alarm; break;
                default: resId = R.raw.fah; break; // Default fallback to 'fah'
            }

            alarmPlayer = MediaPlayer.create(this, resId);
            if (alarmPlayer != null) {
                alarmPlayer.setLooping(true);
                alarmPlayer.start();
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            // Final emergency fallback to notification sound
            try {
                Uri fallback = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                alarmPlayer = MediaPlayer.create(this, fallback);
                if (alarmPlayer != null) {
                    alarmPlayer.setLooping(true);
                    alarmPlayer.start();
                }
            } catch (Exception ex) {}
        }
    }

    public void stopAmbient() {
        if (ambientPlayer != null) {
            ambientPlayer.stop();
            ambientPlayer.release();
            ambientPlayer = null;
        }
    }

    public void stopAlarm() {
        if (alarmPlayer != null) {
            alarmPlayer.stop();
            alarmPlayer.release();
            alarmPlayer = null;
        }
    }

    private void stopAudio() {
        if (ambientPlayer != null) {
            ambientPlayer.stop();
            ambientPlayer.release();
            ambientPlayer = null;
        }
        
        if (alarmPlayer != null) {
            alarmPlayer.stop();
            alarmPlayer.release();
            alarmPlayer = null;
        }
    }

    @Override
    public void onDestroy() {
        stopAudio();
        super.onDestroy();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel("timer_channel", "Study Timer", NotificationManager.IMPORTANCE_LOW);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }
}
