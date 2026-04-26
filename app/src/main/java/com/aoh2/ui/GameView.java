package com.aoh2.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import com.aoh2.data.GameConstants;
import com.aoh2.data.GameState;
import com.aoh2.data.MapData;
import com.aoh2.engine.BattleEngine;
import com.aoh2.engine.MapRenderer;
import com.aoh2.engine.TouchHandler;

/**
 * Главный игровой SurfaceView.
 * Диалоги делегируются в GameActivity через GameEventListener.
 */
public class GameView extends SurfaceView
        implements SurfaceHolder.Callback, TouchHandler.TouchListener,
                   BattleEngine.BattleListener {

    // ─── Интерфейс событий для Activity ──────────────────────
    public interface GameEventListener {
        void onShowDialog(String title, String message, String btnOk, Runnable onOk);
        void onShowRecruitDialog(MapData.MapObject obj);
        void onShowTownMenu(MapData.MapObject obj);
        void onBattleResult(boolean won, int gold);
        void onDayEnd(int day, int week, int gold);
        void onGameOver();
    }

    // ─── Game loop ───────────────────────────────────────────
    private static final long FRAME_MS = 1000 / 30;
    private Thread gameThread;
    private volatile boolean running = false;
    private boolean paused = false;

    // ─── Движок ──────────────────────────────────────────────
    private final MapRenderer  mapRenderer;
    private final TouchHandler touchHandler;
    private final BattleEngine battleEngine;
    private final GameState    state;
    private final MapData      map;
    private GameEventListener  eventListener;

    // ─── Состояние ───────────────────────────────────────────
    private int gameState = GameConstants.STATE_MAP;
    private MapData.MapObject pendingBattle;

    // ─── Краски ──────────────────────────────────────────────
    private final Paint hudBg   = new Paint();
    private final Paint hudTxt  = new Paint();
    private final Paint btnFill = new Paint();
    private final Paint btnBrd  = new Paint();
    private final Paint btnTxt  = new Paint();
    private final Paint battleBg = new Paint();
    private final Paint dimPaint = new Paint();

    // ─── Кнопки ──────────────────────────────────────────────
    private final RectF btnEndDay  = new RectF();
    private final RectF btnSave    = new RectF();
    private final RectF btnRetreat = new RectF();
    private final RectF[] enemyBtns = {new RectF(), new RectF(), new RectF(), new RectF(), new RectF()};

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ─── Конструктор ─────────────────────────────────────────

    public GameView(Context ctx, GameState state, MapData map) {
        super(ctx);
        this.state = state;
        this.map   = map;

        getHolder().addCallback(this);
        setFocusable(true);

        touchHandler = new TouchHandler(this);
        battleEngine = new BattleEngine(state, this);
        mapRenderer  = new MapRenderer(0, 0); // размер установится в surfaceCreated

        initPaints();
    }

    private void initPaints() {
        hudBg.setColor(Color.argb(225, 8, 4, 0));
        hudTxt.setColor(Color.rgb(255, 215, 0)); hudTxt.setTextSize(34f); hudTxt.setAntiAlias(true);
        btnFill.setColor(Color.rgb(55, 28, 5)); btnFill.setStyle(Paint.Style.FILL);
        btnBrd.setColor(Color.rgb(180, 120, 0)); btnBrd.setStyle(Paint.Style.STROKE); btnBrd.setStrokeWidth(2f);
        btnTxt.setColor(Color.rgb(255, 215, 0)); btnTxt.setTextSize(26f);
        btnTxt.setTextAlign(Paint.Align.CENTER); btnTxt.setAntiAlias(true);
        battleBg.setColor(Color.rgb(12, 6, 2));
        dimPaint.setColor(Color.argb(140, 0, 0, 0));
    }

    public void setEventListener(GameEventListener l) { this.eventListener = l; }

    // ─── Surface ─────────────────────────────────────────────

    @Override public void surfaceCreated(SurfaceHolder h) {
        int w = getWidth(), ht = getHeight();
        mapRenderer.onSizeChanged(w, ht);
        mapRenderer.centerOnHero();
        setupButtons(w, ht);
        running = true;
        gameThread = new Thread(this::loop);
        gameThread.start();
    }

    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int ht) {
        mapRenderer.onSizeChanged(w, ht);
        setupButtons(w, ht);
    }

    @Override public void surfaceDestroyed(SurfaceHolder h) {
        running = false;
        try { if (gameThread != null) gameThread.join(500); } catch (InterruptedException ignored) {}
    }

    public void pause()  { paused = true; }
    public void resume() { paused = false; }

    // ─── Game loop ───────────────────────────────────────────

    private void loop() {
        while (running) {
            long t0 = System.currentTimeMillis();
            if (!paused) {
                Canvas c = null;
                try {
                    c = getHolder().lockCanvas();
                    if (c != null) synchronized (getHolder()) { render(c); }
                } finally {
                    if (c != null) getHolder().unlockCanvasAndPost(c);
                }
                if (gameState == GameConstants.STATE_MAP && touchHandler.hasInertia()) {
                    float[] v = touchHandler.applyInertia();
                    mapRenderer.scrollCamera(v[0], v[1]);
                }
            }
            long sleep = FRAME_MS - (System.currentTimeMillis() - t0);
            if (sleep > 0) try { Thread.sleep(sleep); } catch (InterruptedException ignored) {}
        }
    }

    // ─── Рендер ──────────────────────────────────────────────

    private void render(Canvas canvas) {
        canvas.drawColor(Color.BLACK);
        switch (gameState) {
            case GameConstants.STATE_MAP:
                mapRenderer.render(canvas);
                renderHUD(canvas);
                break;
            case GameConstants.STATE_BATTLE:
                renderBattle(canvas);
                break;
        }
    }

    private void renderHUD(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        int hy = h - GameConstants.HUD_HEIGHT;
        canvas.drawRect(0, hy, w, h, hudBg);

        Paint line = new Paint(); line.setColor(Color.rgb(160, 100, 0)); line.setStrokeWidth(1.5f);
        canvas.drawLine(0, hy, w, hy, line);

        canvas.drawText("💰 " + state.gold, 14, hy + 44, hudTxt);

        Paint ctr = new Paint(hudTxt); ctr.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("Нед." + state.week + "  День " + state.day, w / 2f, hy + 44, ctr);

        Paint right = new Paint(hudTxt); right.setTextAlign(Paint.Align.RIGHT);
       int armyPower = 0;
for (int i = 0; i < state.armyStackCount; i++) {
    armyPower += state.armyCounts[i];
}
canvas.drawText("⚔ " + armyPower, w - 14f, hy + 44, right);
        drawBtn(canvas, btnEndDay, "▶ День");
        drawBtn(canvas, btnSave,   "💾");

        // Очки движения
        Paint mp = new Paint(hudTxt); mp.setTextSize(22f);
        mp.setColor(state.heroMovePoints > 5 ? Color.rgb(0,200,0) : Color.rgb(220,100,0));
        canvas.drawText("Ход: " + state.heroMovePoints + "/" + state.heroMovePointsMax, 14, hy + 70, mp);
    }

    private void renderBattle(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        canvas.drawColor(Color.rgb(12, 6, 2));

        Paint title = new Paint(hudTxt); title.setTextSize(48f); title.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("⚔  БИТВА  ⚔", w / 2f, 72, title);

        // Армия игрока
        Paint pp = new Paint(hudTxt); pp.setTextSize(28f);
        canvas.drawText("Ваша армия:", 24, 120, pp);
        int py = 155;
        BattleEngine.UnitStack[] ps = battleEngine.getPlayerStacks();
        if (ps != null) for (BattleEngine.UnitStack u : ps) {
            pp.setColor(u.isDead() ? Color.GRAY : Color.rgb(80, 210, 80));
            canvas.drawText(GameConstants.UNIT_NAMES[u.type] + " ×" + u.count
                    + "   HP:" + u.hpCurrent, 24, py, pp);
            py += 40;
        }

        // Враги (кнопки атаки)
        Paint ep = new Paint(hudTxt); ep.setTextSize(28f);
        int ex = w / 2 + 16;
        canvas.drawText("Враги:", ex, 120, ep);
        int ey = 155;
        BattleEngine.UnitStack[] es = battleEngine.getEnemyStacks();
        boolean myTurn = battleEngine.isPlayerTurn();
        if (es != null) for (int i = 0; i < es.length && i < enemyBtns.length; i++) {
            BattleEngine.UnitStack u = es[i];
            enemyBtns[i].set(ex, ey - 30, w - 14, ey + 10);
            Paint eb = new Paint(); eb.setStyle(Paint.Style.FILL);
            eb.setColor(u.isDead() ? 0xFF333333 :
                (myTurn ? Color.argb(200, 110, 18, 18) : Color.argb(100, 80, 18, 18)));
            canvas.drawRoundRect(enemyBtns[i], 8, 8, eb);
            ep.setColor(u.isDead() ? Color.GRAY : Color.rgb(230, 60, 60));
            canvas.drawText(GameConstants.UNIT_NAMES[u.type] + " ×" + u.count, ex + 8, ey, ep);
            ey += 50;
        }

        Paint sp = new Paint(hudTxt); sp.setTextAlign(Paint.Align.CENTER); sp.setTextSize(32f);
        canvas.drawText(myTurn ? "Ваш ход — нажмите на врага" : "Ход врага...", w / 2f, h - 130, sp);
        drawBtn(canvas, btnRetreat, "Отступить");
    }

    // ─── Верстка кнопок ──────────────────────────────────────

    private void setupButtons(int w, int h) {
        int hy = h - GameConstants.HUD_HEIGHT;
        btnEndDay.set(w - 310f, hy + 50, w - 110f, hy + 76);
        btnSave.set(w - 100f, hy + 50, w - 14f, hy + 76);
        btnRetreat.set(w / 2f - 140, h - 90f, w / 2f + 140, h - 44f);
    }

    private void drawBtn(Canvas canvas, RectF r, String label) {
        canvas.drawRoundRect(r, 10, 10, btnFill);
        canvas.drawRoundRect(r, 10, 10, btnBrd);
        canvas.drawText(label, r.centerX(), r.centerY() + 10, btnTxt);
    }

    private void ctr(Paint p, Canvas c, String t, float x, float y) {
        float old = p.getTextSize(); p.setTextAlign(Paint.Align.CENTER);
        c.drawText(t, x, y, p); p.setTextAlign(Paint.Align.LEFT);
    }

    // ─── Touch ───────────────────────────────────────────────

    @Override public boolean onTouchEvent(MotionEvent e) { return touchHandler.onTouchEvent(e); }

    @Override public void onTap(float x, float y) {
        switch (gameState) {
            case GameConstants.STATE_MAP:    onMapTap(x, y);    break;
            case GameConstants.STATE_BATTLE: onBattleTap(x, y); break;
        }
    }

    @Override public void onSwipe(float dx, float dy) {
        if (gameState == GameConstants.STATE_MAP) mapRenderer.scrollCamera(dx, dy);
    }

    @Override public void onLongPress(float x, float y) {
        if (gameState != GameConstants.STATE_MAP) return;
        int tx = mapRenderer.screenToTileX(x), ty = mapRenderer.screenToTileY(y);
        MapData.MapObject obj = map.getObjectAt(tx, ty);
        if (obj == null) return;
        String[] names = {"","Замок","Шахта","Лагерь","Святилище","Сундук","Артефакт","Нейтралы","Враг"};
        String info = (obj.type < names.length ? names[obj.type] : "Объект");
        if (obj.unitCount > 0) info += "\n" + GameConstants.UNIT_NAMES[obj.unitType] + " ×" + obj.unitCount;
        if (obj.goldAmount > 0) info += "\n💰 " + obj.goldAmount;
        final String fi = info;
        mainHandler.post(() -> { if (eventListener != null) eventListener.onShowDialog("Информация", fi, "OK", null); });
    }

    // ─── Логика тапов ────────────────────────────────────────

    private void onMapTap(float x, float y) {
        if (btnEndDay.contains(x, y)) { endDay(); return; }
        if (btnSave.contains(x, y))   { state.save(getContext()); return; }
        if (y >= getHeight() - GameConstants.HUD_HEIGHT) return;
        int tx = mapRenderer.screenToTileX(x), ty = mapRenderer.screenToTileY(y);
        moveHeroToward(tx, ty);
    }

    private void onBattleTap(float x, float y) {
        if (btnRetreat.contains(x, y)) { gameState = GameConstants.STATE_MAP; return; }
        if (!battleEngine.isPlayerTurn()) return;
        BattleEngine.UnitStack[] es = battleEngine.getEnemyStacks();
        if (es == null) return;
        for (int i = 0; i < es.length && i < enemyBtns.length; i++) {
            if (!es[i].isDead() && enemyBtns[i].contains(x, y)) {
                battleEngine.doNextTurn();
                if (!battleEngine.isBattleResolved() && !battleEngine.isPlayerTurn()) {
                    mainHandler.postDelayed(() -> { if (!battleEngine.isBattleResolved()) battleEngine.doNextTurn(); }, 700);
                }
                return;
            }
        }
    }

    // ─── Движение героя ──────────────────────────────────────

    private void moveHeroToward(int tx, int ty) {
        if (!map.isPassable(tx, ty)) return;
        if (state.heroMovePoints <= 0) {
            mainHandler.post(() -> { if (eventListener != null)
                eventListener.onShowDialog("Стоп", "Очки движения закончились!\nНажмите '▶ День'.", "OK", null); });
            return;
        }

        // Шаг в направлении цели
        int dx = Integer.signum(tx - state.heroX);
        int dy = Integer.signum(ty - state.heroY);
        int nx = state.heroX + dx, ny = state.heroY + dy;

        if (!map.isPassable(nx, ny)) {
            nx = state.heroX + dx; ny = state.heroY;
            if (!map.isPassable(nx, ny)) {
                nx = state.heroX; ny = state.heroY + dy;
                if (!map.isPassable(nx, ny)) return;
            }
        }

        state.heroX = nx; state.heroY = ny;
        state.heroMovePoints--;
        state.revealAround(nx, ny, 3);
        mapRenderer.centerOnHero();

        MapData.MapObject obj = map.getObjectAt(nx, ny);
        if (obj != null) handleObjectInteraction(obj);
    }

    private void handleObjectInteraction(MapData.MapObject obj) {
        switch (obj.type) {
            case GameConstants.OBJ_CHEST:
                obj.alive = false;
                state.gold += obj.goldAmount;
                mainHandler.post(() -> { if (eventListener != null)
                    eventListener.onShowDialog("Сундук!", "Найдено: " + obj.goldAmount + " 💰", "OK", null); });
                break;

            case GameConstants.OBJ_MINE:
                // ФИКС БАГА: проверяем посещение на этой неделе
                if (!state.canVisitBuilding(obj.x, obj.y)) {
                    mainHandler.post(() -> { if (eventListener != null)
                        eventListener.onShowDialog("Шахта", "Уже разрабатывается.\nДоход придёт в начале недели.", "OK", null); });
                    return;
                }
                state.capturedMines.add(obj.x + ":" + obj.y);
                state.markBuildingVisited(obj.x, obj.y);
                mainHandler.post(() -> { if (eventListener != null)
                    eventListener.onShowDialog("Шахта захвачена!",
                        "+" + GameConstants.MINE_INCOME_PER_DAY + " 💰/день", "OK", null); });
                break;

            case GameConstants.OBJ_SHRINE:
                // ФИКС БАГА: только 1 раз в неделю
                if (!state.canVisitBuilding(obj.x, obj.y)) {
                    mainHandler.post(() -> { if (eventListener != null)
                        eventListener.onShowDialog("Святилище", "Уже посещено на этой неделе.", "OK", null); });
                    return;
                }
                state.markBuildingVisited(obj.x, obj.y);
                state.heroMovePointsMax += 2;
                state.heroMovePoints = state.heroMovePointsMax;
                mainHandler.post(() -> { if (eventListener != null)
                    eventListener.onShowDialog("Благословение!", "+2 к движению навсегда.", "OK", null); });
                break;

            case GameConstants.OBJ_NEUTRAL_ARMY:
                // ФИКС БАГА: нельзя фармить одну армию бесконечно
                if (!state.canVisitBuilding(obj.x, obj.y)) {
                    mainHandler.post(() -> { if (eventListener != null)
                        eventListener.onShowDialog("Пусто", "Здесь уже никого нет.", "OK", null); });
                    return;
                }
                mainHandler.post(() -> { if (eventListener != null) eventListener.onShowRecruitDialog(obj); });
                break;

            case GameConstants.OBJ_ENEMY_HERO:
                if (state.armyStackCount == 0) {
                    mainHandler.post(() -> { if (eventListener != null)
                        eventListener.onShowDialog("Нет армии", "Невозможно сражаться без войск!", "OK", null); });
                    return;
                }
                pendingBattle = obj;
                battleEngine.startBattle(obj.unitType, obj.unitCount);
                gameState = GameConstants.STATE_BATTLE;
                break;

            case GameConstants.OBJ_TOWN:
                // ФИКС БАГА: замок — вербовка 1 раз в неделю
                if (!state.canVisitBuilding(obj.x, obj.y)) {
                    mainHandler.post(() -> { if (eventListener != null)
                        eventListener.onShowDialog("Замок", "Вербовка уже была на этой неделе.\nПриходите на следующей.", "OK", null); });
                    return;
                }
                state.markBuildingVisited(obj.x, obj.y);
                mainHandler.post(() -> { if (eventListener != null) eventListener.onShowTownMenu(obj); });
                break;
        }
    }

    private void endDay() {
        state.onNewDay();
        mainHandler.post(() -> { if (eventListener != null)
            eventListener.onDayEnd(state.day, state.week, state.gold); });
    }

    // ─── Публичные методы для Activity ───────────────────────

    /** Завербовать нейтральных — вызывается из Activity после диалога */
    public void recruitNeutrals(MapData.MapObject obj) {
        int cost = obj.unitCount * GameConstants.UNIT_STATS[obj.unitType][4];
        if (state.gold < cost) return;
        state.gold -= cost;
        state.addToArmy(obj.unitType, obj.unitCount);
        state.markBuildingVisited(obj.x, obj.y);
        obj.alive = false;
    }

    /** Начать бой с нейтралами — вызывается из Activity */
    public void startBattleWith(MapData.MapObject obj) {
        if (state.armyStackCount == 0) return;
        pendingBattle = obj;
        battleEngine.startBattle(obj.unitType, obj.unitCount);
        gameState = GameConstants.STATE_BATTLE;
    }

    // ─── BattleEngine.BattleListener ─────────────────────────

    @Override public void onBattleLog(String msg) {}

    @Override public void onBattleEnd(boolean won, int gold) {
        if (pendingBattle != null) pendingBattle.alive = false;
        gameState = GameConstants.STATE_MAP;
        if (!won && state.armyStackCount == 0) {
            mainHandler.post(() -> { if (eventListener != null) eventListener.onGameOver(); });
        } else {
            mainHandler.post(() -> { if (eventListener != null) eventListener.onBattleResult(won, gold); });
        }
    }

    @Override public void onUnitDamaged(boolean isPlayer, int stackIdx, int remaining) {}

    // ─── Вспомогательный метод ───────────────────────────────

    private void drawText(Paint p, Canvas c, String t, float x, float y) {
        c.drawText(t, x, y, p);
    }
}
