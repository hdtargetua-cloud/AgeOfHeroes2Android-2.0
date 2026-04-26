package com.aoh2;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import androidx.appcompat.app.AppCompatActivity;
import com.aoh2.data.GameConstants;
import com.aoh2.data.GameState;
import com.aoh2.data.MapData;
import com.aoh2.ui.GameView;

public class GameActivity extends AppCompatActivity implements GameView.GameEventListener {

    private GameView  gameView;
    private GameState state;
    private MapData   map;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideSystemUI();

        state = GameState.get();
        map   = MapData.get();
        map.load(this);

        boolean loaded = state.load(this);
        if (!loaded) {
            state.addToArmy(GameConstants.UNIT_PEASANT, 10);
            state.addToArmy(GameConstants.UNIT_ARCHER,  5);
        }

        gameView = new GameView(this, state, map);
        gameView.setEventListener(this);

        FrameLayout root = new FrameLayout(this);
        root.addView(gameView, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    @Override
    public void onShowDialog(String title, String message, String btnOk, Runnable onOk) {
        runOnUiThread(() -> new AlertDialog.Builder(this, R.style.AOH2Dialog)
            .setTitle(title).setMessage(message)
            .setPositiveButton(btnOk, (d, w) -> { if (onOk != null) onOk.run(); })
            .show());
    }

    @Override
    public void onShowRecruitDialog(MapData.MapObject obj) {
        runOnUiThread(() -> {
            int cost = obj.unitCount * GameConstants.UNIT_STATS[obj.unitType][4];
            new AlertDialog.Builder(this, R.style.AOH2Dialog)
                .setTitle("Нейтральная армия")
                .setMessage(GameConstants.UNIT_NAMES[obj.unitType] + " ×" + obj.unitCount +
                    "\n\nВербовка: " + cost + " 💰\nВаше золото: " + state.gold)
                .setPositiveButton("Завербовать", (d, w) -> gameView.recruitNeutrals(obj))
                .setNeutralButton("Атаковать",   (d, w) -> gameView.startBattleWith(obj))
                .setNegativeButton("Уйти", null)
                .show();
        });
    }

    @Override
    public void onShowTownMenu(MapData.MapObject obj) {
        runOnUiThread(() -> {
            // ФИКС БАГА: здание уже помечено visited в GameView.handleObjectInteraction
            // Здесь просто показываем меню найма
            int[] units  = {GameConstants.UNIT_PEASANT, GameConstants.UNIT_ARCHER,
                            GameConstants.UNIT_PIKEMAN, GameConstants.UNIT_KNIGHT};
            int[] counts = {20, 10, 6, 3};

            StringBuilder sb = new StringBuilder("Доступные войска:\n\n");
            for (int i = 0; i < units.length; i++) {
                int c = counts[i] * GameConstants.UNIT_STATS[units[i]][4];
                sb.append("• ").append(GameConstants.UNIT_NAMES[units[i]])
                  .append(" ×").append(counts[i]).append("  [").append(c).append(" 💰]\n");
            }
            sb.append("\nВаше золото: ").append(state.gold);

            new AlertDialog.Builder(this, R.style.AOH2Dialog)
                .setTitle("🏰 Замок")
                .setMessage(sb.toString())
                .setPositiveButton("Крестьяне",  (d, w) -> hire(obj, units[0], counts[0]))
                .setNeutralButton("Лучники",     (d, w) -> hire(obj, units[1], counts[1]))
                .setNegativeButton("Выйти", null)
                .show();
        });
    }

    private void hire(MapData.MapObject obj, int unitType, int count) {
        int cost = count * GameConstants.UNIT_STATS[unitType][4];
        if (state.gold < cost) {
            onShowDialog("Мало золота", "Нужно " + cost + " 💰\nЕсть: " + state.gold, "OK", null);
            return;
        }
        state.gold -= cost;
        boolean ok = state.addToArmy(unitType, count);
        state.save(this);
        onShowDialog("Найм", ok ?
            "+" + count + " " + GameConstants.UNIT_NAMES[unitType] :
            "Армия полна! (макс. 5 отрядов)", "OK", null);
    }

    @Override
    public void onBattleResult(boolean won, int gold) {
        runOnUiThread(() -> onShowDialog(
            won ? "🏆 Победа!" : "💀 Поражение",
            won ? "Враг пал!\nНаграда: " + gold + " 💰"
                : "Вы потерпели поражение...", "OK", null));
    }

    @Override
    public void onDayEnd(int day, int week, int gold) {
        runOnUiThread(() -> onShowDialog("Конец дня",
            "День " + day + ", Неделя " + week +
            "\nДоход от шахт: +" + state.capturedMines.size() * GameConstants.MINE_INCOME_PER_DAY +
            " 💰\nВсего золота: " + gold, "Далее", null));
    }

    @Override
    public void onGameOver() {
        runOnUiThread(() -> new AlertDialog.Builder(this, R.style.AOH2Dialog)
            .setTitle("Игра окончена")
            .setMessage("Армия уничтожена. Начать заново?")
            .setPositiveButton("Заново", (d, w) -> {
                GameState.deleteSave(this); GameState.reset(); MapData.reset(); recreate();
            })
            .setNegativeButton("Выйти", (d, w) -> finish())
            .setCancelable(false).show());
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (gameView != null) { gameView.pause(); state.save(this); }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        if (gameView != null) gameView.resume();
    }

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this, R.style.AOH2Dialog)
            .setTitle("Пауза")
            .setMessage("Выйти в меню? Игра сохранится.")
            .setPositiveButton("Выйти",       (d, w) -> { state.save(this); finish(); })
            .setNegativeButton("Продолжить",  null)
            .show();
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
