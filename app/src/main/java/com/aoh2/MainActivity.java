package com.aoh2;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.aoh2.data.GameState;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideSystemUI();
        buildMenuUI();
    }

    private void buildMenuUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xFF0d0500);

        // Заголовок
        TextView title = new TextView(this);
        title.setText("Age of Heroes II");
        title.setTextColor(0xFFFFD700);
        title.setTextSize(42f);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 12);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Android Edition");
        sub.setTextColor(0xFFaaaaaa);
        sub.setTextSize(20f);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, 0, 0, 80);
        root.addView(sub);

        boolean hasSave = GameState.hasSave(this);

        // Кнопка "Продолжить"
        if (hasSave) {
            Button btnContinue = makeButton("Продолжить");
            btnContinue.setOnClickListener(v -> startGame());
            root.addView(btnContinue);
        }

        // Кнопка "Новая игра"
        Button btnNew = makeButton(hasSave ? "Новая игра" : "Начать игру");
        btnNew.setOnClickListener(v -> {
            if (hasSave) {
                new AlertDialog.Builder(this, R.style.AOH2Dialog)
                    .setTitle("Новая игра")
                    .setMessage("Текущее сохранение будет удалено. Продолжить?")
                    .setPositiveButton("Да", (d, w) -> {
                        GameState.deleteSave(this);
                        GameState.reset();
                        startGame();
                    })
                    .setNegativeButton("Нет", null)
                    .show();
            } else {
                startGame();
            }
        });
        root.addView(btnNew);

        // Кнопка "Об игре"
        Button btnAbout = makeButton("Об игре");
        btnAbout.setOnClickListener(v ->
            new AlertDialog.Builder(this, R.style.AOH2Dialog)
                .setTitle("Age of Heroes 2")
                .setMessage(
                    "Оригинал: Qplaze / mikhan (2006, J2ME)\n\n" +
                    "Android-порт:\n" +
                    "• Управление тачскрин (tap + swipe)\n" +
                    "• Расширенные локации\n" +
                    "• Новые враги и нейтралы\n" +
                    "• Исправлен баг бесконечного фарма зданий\n" +
                    "• HD масштабирование пиксель-арта\n\n" +
                    "Сюжет оригинальной игры сохранён."
                )
                .setPositiveButton("OK", null)
                .show()
        );
        root.addView(btnAbout);

        // Кнопка "Выход"
        Button btnExit = makeButton("Выход");
        btnExit.setBackgroundColor(0x44ff0000);
        btnExit.setOnClickListener(v -> finish());
        root.addView(btnExit);

        // Версия
        TextView ver = new TextView(this);
        ver.setText("v2.0 Android");
        ver.setTextColor(0xFF444444);
        ver.setTextSize(14f);
        ver.setGravity(Gravity.CENTER);
        ver.setPadding(0, 60, 0, 0);
        root.addView(ver);

        setContentView(root);
    }

    private Button makeButton(String text) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.BLACK);
        btn.setTextSize(22f);
        btn.setBackgroundColor(0xFFFFD700);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            500, 110);
        lp.setMargins(0, 20, 0, 0);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        btn.setLayoutParams(lp);
        return btn;
    }

    private void startGame() {
        startActivity(new Intent(this, GameActivity.class));
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        // Перестраиваем меню (кнопка "Продолжить" могла появиться)
        buildMenuUI();
    }

    private void hideSystemUI() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }
}
