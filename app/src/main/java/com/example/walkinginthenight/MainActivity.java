package com.example.walkinginthenight;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TextView tvStoryText;
    private LinearLayout choicesContainer;
    private ScrollView scrollView;
    private Button btnSettings;
    private Button btnChapters;
    private Button btnQuickMute;

    private static final String PREFS_NAME = "WalkingInTheNightPrefs";
    private static final String KEY_SAVED_SCENE = "saved_scene";
    private static final String KEY_LAST_CANON_SCENE = "last_canon_scene";
    private static final String KEY_MUSIC_VOLUME = "music_volume";
    private static final String KEY_MUSIC_MUTED = "music_muted";
    private static final String KEY_TEXT_SPEED = "text_speed";

    private SharedPreferences prefs;
    private StoryDatabaseHelper dbHelper;

    private MediaPlayer mediaPlayer;
    private int musicVolumePercent = 70;
    private boolean isMusicMuted = false;
    private int textSpeedProgress = 0;
    private long charDelayMs = 40;

    private final Handler typingHandler = new Handler(Looper.getMainLooper());
    private final Handler notificationHandler = new Handler(Looper.getMainLooper());
    private Runnable typingRunnable;
    private String currentFullText = "";
    private int currentCharIndex = 0;
    private boolean isTyping = false;

    private StoryDatabaseHelper.SceneData currentNode;
    private String currentSceneId = "ch1_p1";
    private String lastCanonSceneId = "ch1_p1";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        View mainRoot = findViewById(R.id.mainRoot);
        final int basePadding = (int) (14 * getResources().getDisplayMetrics().density);
        ViewCompat.setOnApplyWindowInsetsListener(mainRoot, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
            );
            v.setPadding(
                    basePadding + insets.left,
                    basePadding + insets.top,
                    basePadding + insets.right,
                    basePadding + insets.bottom
            );
            return WindowInsetsCompat.CONSUMED;
        });

        tvStoryText = findViewById(R.id.tvStoryText);
        choicesContainer = findViewById(R.id.choicesContainer);
        scrollView = findViewById(R.id.scrollView);
        btnSettings = findViewById(R.id.btnSettings);
        btnChapters = findViewById(R.id.btnChapters);
        btnQuickMute = findViewById(R.id.btnQuickMute);

        dbHelper = new StoryDatabaseHelper(this);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        musicVolumePercent = prefs.getInt(KEY_MUSIC_VOLUME, 70);
        isMusicMuted = prefs.getBoolean(KEY_MUSIC_MUTED, false);
        textSpeedProgress = prefs.getInt(KEY_TEXT_SPEED, 0);
        lastCanonSceneId = prefs.getString(KEY_LAST_CANON_SCENE, "ch1_p1");
        String savedScene = prefs.getString(KEY_SAVED_SCENE, "ch1_p1");

        updateCharDelay();
        initMusic();
        updateQuickMuteButton();

        btnSettings.setOnClickListener(v -> showSettingsDialog());
        btnChapters.setOnClickListener(v -> showChaptersDialog());

        btnQuickMute.setOnClickListener(v -> {
            isMusicMuted = !isMusicMuted;
            applyMusicVolume();
            updateQuickMuteButton();
            saveSettings();
        });

        tvStoryText.setOnClickListener(v -> {
            if (isTyping) {
                finishTypingImmediately();
            }
        });

        if (dbHelper.getScene(savedScene) == null) {
            savedScene = "ch1_p1";
            lastCanonSceneId = "ch1_p1";
        }
        loadScene(savedScene);
    }

    private void initMusic() {
        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.music);
            if (mediaPlayer != null) {
                mediaPlayer.setLooping(true);
                applyMusicVolume();
                mediaPlayer.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void applyMusicVolume() {
        if (mediaPlayer != null) {
            float volume = (isMusicMuted || musicVolumePercent == 0) ? 0f : (musicVolumePercent / 100f);
            mediaPlayer.setVolume(volume, volume);
        }
    }

    private void updateQuickMuteButton() {
        if (btnQuickMute != null) {
            btnQuickMute.setText((isMusicMuted || musicVolumePercent == 0) ? "🔇" : "🔊");
        }
    }

    private void updateCharDelay() {
        if (textSpeedProgress >= 100) {
            charDelayMs = 0;
        } else {
            charDelayMs = Math.max(1, Math.round(40 * (100 - textSpeedProgress) / 100f));
        }
    }

    private String getSpeedLabelText() {
        if (textSpeedProgress >= 100) {
            return "Скорость текста: Моментально";
        } else if (textSpeedProgress == 0) {
            return "Скорость текста: Обычная";
        } else {
            return "Скорость текста: +" + textSpeedProgress + "%";
        }
    }

    private void saveProgress() {
        prefs.edit()
                .putString(KEY_SAVED_SCENE, currentSceneId)
                .putString(KEY_LAST_CANON_SCENE, lastCanonSceneId)
                .apply();
    }

    private void saveSettings() {
        prefs.edit()
                .putInt(KEY_MUSIC_VOLUME, musicVolumePercent)
                .putBoolean(KEY_MUSIC_MUTED, isMusicMuted)
                .putInt(KEY_TEXT_SPEED, textSpeedProgress)
                .apply();
    }

    // =========================================================================
    // СТИЛИЗОВАННОЕ ВСПЛЫВАЮЩЕЕ УВЕДОМЛЕНИЕ В ДИЗАЙНЕ ИГРЫ
    // =========================================================================
    private void showStyledNotification(String message) {
        if (isFinishing()) return;

        float density = getResources().getDisplayMetrics().density;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER);
        int padH = (int) (20 * density);
        int padV = (int) (12 * density);
        box.setPadding(padH, padV, padH, padV);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#181512"));
        bg.setStroke((int) (2 * density), Color.parseColor("#8C6D53"));
        bg.setCornerRadius(12 * density);
        box.setBackground(bg);

        TextView tv = new TextView(this);
        tv.setText(message);
        tv.setTextColor(Color.parseColor("#EAE4DC"));
        tv.setTextSize(15f);
        tv.setTypeface(Typeface.SERIF, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        box.addView(tv);

        builder.setView(box);
        AlertDialog notifyDialog = builder.create();

        Window window = notifyDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            WindowManager.LayoutParams wlp = window.getAttributes();
            wlp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            wlp.y = (int) (60 * density);
            window.setAttributes(wlp);
        }

        notifyDialog.show();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        notificationHandler.postDelayed(() -> {
            if (!isFinishing() && notifyDialog.isShowing()) {
                notifyDialog.dismiss();
            }
        }, 2000);
    }

    // =========================================================================
    // ОКНО «ВЫБОР ГЛАВЫ» С КРЕСТИКОМ И ПОДТВЕРЖДЕНИЕМ ПЕРЕХОДА
    // =========================================================================
    private void showChaptersDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (16 * density);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, (int) (12 * density), pad, pad);

        GradientDrawable dialogBg = new GradientDrawable();
        dialogBg.setColor(Color.parseColor("#181512"));
        dialogBg.setStroke((int) (2 * density), Color.parseColor("#4A3F35"));
        dialogBg.setCornerRadius(14 * density);
        layout.setBackground(dialogBg);

        // Заголовок и круглый крестик закрытия
        LinearLayout headerLayout = new LinearLayout(this);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setGravity(Gravity.CENTER_VERTICAL);
        headerLayout.setPadding(0, 0, 0, (int) (12 * density));

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Выбор главы");
        tvTitle.setTextColor(Color.parseColor("#EAE4DC"));
        tvTitle.setTextSize(18f);
        tvTitle.setTypeface(Typeface.SERIF, Typeface.BOLD);
        headerLayout.addView(tvTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView btnCloseCircle = createCloseCircleButton(density);
        headerLayout.addView(btnCloseCircle);
        layout.addView(headerLayout);

        // Прокручиваемый список глав
        ScrollView chaptersScroll = new ScrollView(this);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (250 * density)
        );
        chaptersScroll.setLayoutParams(scrollParams);

        LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        chaptersScroll.addView(listContainer);
        layout.addView(chaptersScroll);

        // Кнопка подтверждения перехода
        Button btnConfirmJump = new Button(this);
        btnConfirmJump.setText("Выберите открытую главу");
        btnConfirmJump.setAllCaps(false);
        btnConfirmJump.setTextSize(14f);
        btnConfirmJump.setTextColor(Color.parseColor("#857B70"));
        btnConfirmJump.setEnabled(false);

        GradientDrawable confirmDisabledBg = new GradientDrawable();
        confirmDisabledBg.setColor(Color.parseColor("#241F1C"));
        confirmDisabledBg.setStroke((int) (1 * density), Color.parseColor("#3D352E"));
        confirmDisabledBg.setCornerRadius(8 * density);
        btnConfirmJump.setBackground(confirmDisabledBg);

        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (42 * density)
        );
        confirmParams.setMargins(0, (int) (12 * density), 0, 0);
        btnConfirmJump.setLayoutParams(confirmParams);
        layout.addView(btnConfirmJump);

        List<StoryDatabaseHelper.ChapterItem> chapters = dbHelper.getAllChapters();
        final StoryDatabaseHelper.ChapterItem[] selectedChapter = {null};
        final List<Button> unlockedButtons = new ArrayList<>();

        for (StoryDatabaseHelper.ChapterItem ch : chapters) {
            Button chBtn = new Button(this);
            chBtn.setAllCaps(false);
            chBtn.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            chBtn.setTextSize(14f);
            chBtn.setPadding((int) (14 * density), (int) (10 * density), (int) (14 * density), (int) (10 * density));

            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            itemParams.setMargins(0, 0, 0, (int) (8 * density));
            chBtn.setLayoutParams(itemParams);

            if (ch.isUnlocked) {
                chBtn.setText("📖  " + ch.title);
                chBtn.setTextColor(Color.parseColor("#EAE4DC"));
                chBtn.setBackground(createChapterButtonBg(density, false));
                unlockedButtons.add(chBtn);

                chBtn.setOnClickListener(v -> {
                    selectedChapter[0] = ch;
                    for (Button b : unlockedButtons) {
                        b.setBackground(createChapterButtonBg(density, false));
                    }
                    chBtn.setBackground(createChapterButtonBg(density, true));

                    btnConfirmJump.setEnabled(true);
                    btnConfirmJump.setText("Перейти к главе");
                    btnConfirmJump.setTextColor(Color.parseColor("#FFFFFF"));
                    GradientDrawable activeBg = new GradientDrawable();
                    activeBg.setColor(Color.parseColor("#6E2626"));
                    activeBg.setStroke((int) (1.5f * density), Color.parseColor("#A3483B"));
                    activeBg.setCornerRadius(8 * density);
                    btnConfirmJump.setBackground(activeBg);
                });
            } else {
                chBtn.setText("🔒  " + ch.title + " (Не прочитано)");
                chBtn.setTextColor(Color.parseColor("#665D54"));
                GradientDrawable lockedBg = new GradientDrawable();
                lockedBg.setColor(Color.parseColor("#141210"));
                lockedBg.setStroke((int) (1 * density), Color.parseColor("#29231E"));
                lockedBg.setCornerRadius(8 * density);
                chBtn.setBackground(lockedBg);

                chBtn.setOnClickListener(v -> showStyledNotification("Эта глава еще не прочитана"));
            }

            listContainer.addView(chBtn);
        }

        builder.setView(layout);
        AlertDialog chaptersDialog = builder.create();

        btnCloseCircle.setOnClickListener(v -> chaptersDialog.dismiss());

        btnConfirmJump.setOnClickListener(v -> {
            if (selectedChapter[0] != null) {
                chaptersDialog.dismiss();
                lastCanonSceneId = selectedChapter[0].startSceneId;
                loadScene(selectedChapter[0].startSceneId);
                showStyledNotification("Переход: " + selectedChapter[0].title);
            }
        });

        chaptersDialog.show();
        if (chaptersDialog.getWindow() != null) {
            chaptersDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.86);
            chaptersDialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private GradientDrawable createChapterButtonBg(float density, boolean isSelected) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(isSelected ? "#2E221F" : "#1E1B18"));
        bg.setStroke((int) ((isSelected ? 2f : 1f) * density),
                Color.parseColor(isSelected ? "#A3483B" : "#3D352E"));
        bg.setCornerRadius(8 * density);
        return bg;
    }

    private TextView createCloseCircleButton(float density) {
        TextView btnCloseCircle = new TextView(this);
        btnCloseCircle.setText("✕");
        btnCloseCircle.setTextColor(Color.parseColor("#EAE4DC"));
        btnCloseCircle.setTextSize(14f);
        btnCloseCircle.setGravity(Gravity.CENTER);
        int circleSize = (int) (30 * density);
        btnCloseCircle.setLayoutParams(new LinearLayout.LayoutParams(circleSize, circleSize));

        GradientDrawable circleBg = new GradientDrawable();
        circleBg.setShape(GradientDrawable.OVAL);
        circleBg.setColor(Color.parseColor("#2A241F"));
        circleBg.setStroke((int) (1.5f * density), Color.parseColor("#6E5D4F"));
        btnCloseCircle.setBackground(circleBg);
        return btnCloseCircle;
    }

    // =========================================================================
    // ОКНО НАСТРОЕК И ПОЛНЫЙ СБРОС ПРОГРЕССА
    // =========================================================================
    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (16 * density);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, (int) (12 * density), pad, pad);

        GradientDrawable dialogBg = new GradientDrawable();
        dialogBg.setColor(Color.parseColor("#181512"));
        dialogBg.setStroke((int) (2 * density), Color.parseColor("#4A3F35"));
        dialogBg.setCornerRadius(14 * density);
        layout.setBackground(dialogBg);

        LinearLayout headerLayout = new LinearLayout(this);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setGravity(Gravity.CENTER_VERTICAL);
        headerLayout.setPadding(0, 0, 0, (int) (10 * density));

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Настройки");
        tvTitle.setTextColor(Color.parseColor("#EAE4DC"));
        tvTitle.setTextSize(18f);
        tvTitle.setTypeface(Typeface.SERIF, Typeface.BOLD);
        headerLayout.addView(tvTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView btnCloseCircle = createCloseCircleButton(density);
        headerLayout.addView(btnCloseCircle);
        layout.addView(headerLayout);

        final TextView tvVol = new TextView(this);
        tvVol.setTextColor(Color.parseColor("#D7CCC8"));
        tvVol.setTextSize(14f);
        tvVol.setText(isMusicMuted ? "Музыка: 0%" : "Музыка: " + musicVolumePercent + "%");
        layout.addView(tvVol);

        SeekBar sbVolume = new SeekBar(this);
        sbVolume.setMax(100);
        sbVolume.setProgress(isMusicMuted ? 0 : musicVolumePercent);
        ColorStateList accentColor = ColorStateList.valueOf(Color.parseColor("#A3483B"));
        ColorStateList trackColor = ColorStateList.valueOf(Color.parseColor("#4A3F35"));
        sbVolume.setProgressTintList(accentColor);
        sbVolume.setThumbTintList(accentColor);
        sbVolume.setProgressBackgroundTintList(trackColor);
        sbVolume.setPadding(0, (int) (8 * density), 0, (int) (14 * density));
        layout.addView(sbVolume);

        sbVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    musicVolumePercent = progress;
                    isMusicMuted = (progress == 0);
                    tvVol.setText("Музыка: " + progress + "%");
                    applyMusicVolume();
                    updateQuickMuteButton();
                    saveSettings();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        final TextView tvSpeed = new TextView(this);
        tvSpeed.setTextColor(Color.parseColor("#D7CCC8"));
        tvSpeed.setTextSize(14f);
        tvSpeed.setText(getSpeedLabelText());
        layout.addView(tvSpeed);

        SeekBar sbSpeed = new SeekBar(this);
        sbSpeed.setMax(100);
        sbSpeed.setProgress(textSpeedProgress);
        sbSpeed.setProgressTintList(accentColor);
        sbSpeed.setThumbTintList(accentColor);
        sbSpeed.setProgressBackgroundTintList(trackColor);
        sbSpeed.setPadding(0, (int) (8 * density), 0, (int) (16 * density));
        sbSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textSpeedProgress = progress;
                tvSpeed.setText(getSpeedLabelText());
                updateCharDelay();
                saveSettings();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        layout.addView(sbSpeed);

        Button btnRestart = new Button(this);
        btnRestart.setText("Начать сначала");
        btnRestart.setAllCaps(false);
        btnRestart.setTextSize(14f);
        btnRestart.setTextColor(Color.parseColor("#FFFFFF"));

        GradientDrawable restartBg = new GradientDrawable();
        restartBg.setColor(Color.parseColor("#6E2626"));
        restartBg.setCornerRadius(8 * density);
        btnRestart.setBackground(restartBg);

        LinearLayout.LayoutParams restartParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (38 * density)
        );
        btnRestart.setLayoutParams(restartParams);
        layout.addView(btnRestart);

        builder.setView(layout);
        AlertDialog settingsDialog = builder.create();

        btnCloseCircle.setOnClickListener(v -> settingsDialog.dismiss());

        btnRestart.setOnClickListener(v -> {
            AlertDialog.Builder confirmBuilder = new AlertDialog.Builder(this);
            LinearLayout confirmLayout = new LinearLayout(this);
            confirmLayout.setOrientation(LinearLayout.VERTICAL);
            confirmLayout.setPadding(pad, pad, pad, pad);

            GradientDrawable confirmBg = new GradientDrawable();
            confirmBg.setColor(Color.parseColor("#181512"));
            confirmBg.setStroke((int) (2 * density), Color.parseColor("#4A3F35"));
            confirmBg.setCornerRadius(14 * density);
            confirmLayout.setBackground(confirmBg);

            TextView confirmTitle = new TextView(this);
            confirmTitle.setText("Сброс прогресса");
            confirmTitle.setTextColor(Color.parseColor("#EAE4DC"));
            confirmTitle.setTextSize(18f);
            confirmTitle.setTypeface(Typeface.SERIF, Typeface.BOLD);
            confirmTitle.setPadding(0, 0, 0, (int) (8 * density));
            confirmLayout.addView(confirmTitle);

            TextView confirmText = new TextView(this);
            confirmText.setText("Начать историю с первой главы? Все открытые главы будут снова закрыты.");
            confirmText.setTextColor(Color.parseColor("#D7CCC8"));
            confirmText.setTextSize(14f);
            confirmText.setPadding(0, 0, 0, (int) (14 * density));
            confirmLayout.addView(confirmText);

            LinearLayout confirmBtnLayout = new LinearLayout(this);
            confirmBtnLayout.setOrientation(LinearLayout.HORIZONTAL);
            confirmBtnLayout.setGravity(Gravity.END);

            Button btnYes = new Button(this);
            btnYes.setText("Да, сначала");
            btnYes.setAllCaps(false);
            btnYes.setTextColor(Color.parseColor("#FFFFFF"));
            GradientDrawable yesBg = new GradientDrawable();
            yesBg.setColor(Color.parseColor("#6E2626"));
            yesBg.setCornerRadius(6 * density);
            btnYes.setBackground(yesBg);
            btnYes.setPadding(24, 0, 24, 0);

            Button btnNo = new Button(this);
            btnNo.setText("Отмена");
            btnNo.setAllCaps(false);
            btnNo.setTextColor(Color.parseColor("#D7CCC8"));
            GradientDrawable noBg = new GradientDrawable();
            noBg.setColor(Color.parseColor("#2E2722"));
            noBg.setCornerRadius(6 * density);
            btnNo.setBackground(noBg);
            btnNo.setPadding(24, 0, 24, 0);

            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, (int) (36 * density)
            );
            btnParams.setMargins(10, 0, 0, 0);
            btnNo.setLayoutParams(btnParams);
            btnYes.setLayoutParams(btnParams);

            confirmBtnLayout.addView(btnNo);
            confirmBtnLayout.addView(btnYes);
            confirmLayout.addView(confirmBtnLayout);

            confirmBuilder.setView(confirmLayout);
            AlertDialog confirmDialog = confirmBuilder.create();

            if (confirmDialog.getWindow() != null) {
                confirmDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }

            btnNo.setOnClickListener(cd -> confirmDialog.dismiss());
            btnYes.setOnClickListener(cd -> {
                confirmDialog.dismiss();
                settingsDialog.dismiss();
                // Сбрасываем открытые главы в БД и возвращаемся к Главе I
                dbHelper.resetUnlockedChapters();
                lastCanonSceneId = "ch1_p1";
                loadScene("ch1_p1");
                showStyledNotification("История начата сначала");
            });

            confirmDialog.show();
            if (confirmDialog.getWindow() != null) {
                int cWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.78);
                confirmDialog.getWindow().setLayout(cWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });

        settingsDialog.show();

        if (settingsDialog.getWindow() != null) {
            settingsDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.82);
            settingsDialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void loadScene(String sceneId) {
        StoryDatabaseHelper.SceneData node = dbHelper.getScene(sceneId);
        if (node == null) return;

        currentNode = node;
        currentSceneId = sceneId;
        if (!currentNode.isGameOver) {
            lastCanonSceneId = sceneId;
            // Автоматически открываем главу в списке глав, когда игрок дошел до неё
            dbHelper.unlockChapter(currentNode.chapterId);
        }

        saveProgress();

        typingHandler.removeCallbacksAndMessages(null);
        choicesContainer.removeAllViews();
        tvStoryText.setText("");
        scrollView.scrollTo(0, 0);

        if (currentNode.isGameOver) {
            tvStoryText.setTextColor(Color.parseColor("#EF9A9A"));
            currentFullText = "════════════════════\n" +
                    "КОНЕЦ ИГРЫ\n" +
                    "════════════════════\n\n" +
                    "Причина гибели:\n" + currentNode.text;
        } else {
            tvStoryText.setTextColor(Color.parseColor("#E6DFD5"));
            currentFullText = currentNode.text;
        }

        if (textSpeedProgress >= 100 || charDelayMs <= 0) {
            finishTypingImmediately();
            return;
        }

        currentCharIndex = 0;
        isTyping = true;

        typingRunnable = new Runnable() {
            @Override
            public void run() {
                if (textSpeedProgress >= 100 || charDelayMs <= 0) {
                    finishTypingImmediately();
                    return;
                }
                if (currentCharIndex < currentFullText.length()) {
                    tvStoryText.append(String.valueOf(currentFullText.charAt(currentCharIndex)));
                    currentCharIndex++;
                    scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
                    typingHandler.postDelayed(this, charDelayMs);
                } else {
                    isTyping = false;
                    renderChoices(currentNode);
                }
            }
        };

        typingHandler.post(typingRunnable);
    }

    private void finishTypingImmediately() {
        typingHandler.removeCallbacksAndMessages(null);
        isTyping = false;
        tvStoryText.setText(currentFullText);
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        renderChoices(currentNode);
    }

    private void renderChoices(StoryDatabaseHelper.SceneData node) {
        choicesContainer.removeAllViews();

        if (node.isGameOver) {
            addChoiceButton("Начать игру сначала (Глава I)", "ch1_p1", true);
            addChoiceButton("Переиграть с момента выбора", lastCanonSceneId, false);
            return;
        }

        if (node.nextPageId != null && !node.nextPageId.isEmpty()) {
            addArrowButton("Далее  ➔", node.nextPageId);
            return;
        }

        List<StoryDatabaseHelper.ChoiceItem> shuffledChoices = new ArrayList<>(node.choices);
        Collections.shuffle(shuffledChoices);

        for (StoryDatabaseHelper.ChoiceItem choice : shuffledChoices) {
            addChoiceButton(choice.text, choice.nextSceneId, false);
        }
    }

    private void addArrowButton(String text, String targetNodeId) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(Color.parseColor("#F5EBE6"));
        button.setTextSize(16f);
        button.setTypeface(Typeface.SERIF, Typeface.BOLD);

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(Color.parseColor("#2A241F"));
        btnBg.setStroke(2, Color.parseColor("#5C4B40"));
        btnBg.setCornerRadius(10f);
        button.setBackground(btnBg);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, 8);
        button.setLayoutParams(params);
        button.setPadding(28, 22, 28, 22);

        button.setOnClickListener(v -> loadScene(targetNodeId));
        choicesContainer.addView(button);
    }

    private void addChoiceButton(String text, String targetNodeId, boolean resetChaptersOnClick) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(Color.parseColor("#EAE4DC"));
        button.setTextSize(15f);

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(Color.parseColor("#1E1B18"));
        btnBg.setStroke(2, Color.parseColor("#3D352E"));
        btnBg.setCornerRadius(10f);
        button.setBackground(btnBg);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, 14);
        button.setLayoutParams(params);
        button.setPadding(28, 24, 28, 24);

        button.setOnClickListener(v -> {
            if (resetChaptersOnClick) {
                dbHelper.resetUnlockedChapters();
                lastCanonSceneId = "ch1_p1";
                showStyledNotification("История начата сначала");
            }
            loadScene(targetNodeId);
        });
        choicesContainer.addView(button);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        typingHandler.removeCallbacksAndMessages(null);
        notificationHandler.removeCallbacksAndMessages(null);
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}