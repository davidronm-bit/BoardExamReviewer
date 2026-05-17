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
    
    public interface OnTickListener {
        void onTick(long millisUntilFinished);
    }
    
    public interface OnFinishListener {
        void onFinish();
    }
    
    public OnTickListener onTickListener;
    public OnFinishListener onFinishListener;
    
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
        timeLeftInMillis = durationMillis;
        
        if (timer != null) {
            timer.cancel();
        }

        timer = new CountDownTimer(timeLeftInMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timeLeftInMillis = millisUntilFinished;
                if (onTickListener != null) {
                    onTickListener.onTick(timeLeftInMillis);
                }
                updateNotification();
            }

            @Override
            public void onFinish() {
                isTimerRunning = false;
                
                // Stop ambient noise
                stopAmbient();
                
                // START THE ALARM HERE (Service handles it now)
                playAlarm(selectedAlarm);
                
                // Reset time for next session
                timeLeftInMillis = 0;
                isBreakMode = !isBreakMode;

                if (onFinishListener != null) {
                    onFinishListener.onFinish();
                }
                stopForeground(true);
            }
        }.start();

        isTimerRunning = true;
        startForeground(1, createNotification());
    }

    public void pauseTimer() {
        if (timer != null) {
            timer.cancel();
        }
        isTimerRunning = false;
        stopAudio();
        stopForeground(true);
    }

    public void resetTimer() {
        if (timer != null) {
            timer.cancel();
        }
        isTimerRunning = false;
        isBreakMode = false;
        timeLeftInMillis = 0;
        stopAudio();
        stopForeground(true);
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
