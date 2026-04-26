package com.aoh2.engine;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import com.aoh2.data.GameConstants;
import com.aoh2.data.GameState;
import com.aoh2.data.MapData;

/**
 * Рисует тайловую карту, объекты, героя и туман войны.
 * Тайл = 48px (оригинал 16px × 3).
 */
public class MapRenderer {

    private static final int TILE = GameConstants.TILE_SIZE;

    private static final int[] TILE_COLORS = {
        0xFF4a7c3f, 0xFF2255aa, 0xFF7a7a7a, 0xFF1a5c1a,
        0xFF8B7355, 0xFFc8b464, 0xFFd0e8f0, 0xFFcc3300,
    };

    private final Paint tilePaint = new Paint();
    private final Paint objPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fogPaint  = new Paint();
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint heroPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public float cameraX = 0, cameraY = 0;
    private int screenW, screenH;

    public MapRenderer(int screenW, int screenH) {
        this.screenW = screenW;
        this.screenH = screenH;
        fogPaint.setColor(0xCC000000);
        fogPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(20f);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void onSizeChanged(int w, int h) { screenW = w; screenH = h; }

    public void centerOnHero() {
        GameState gs = GameState.get();
        cameraX = gs.heroX * TILE - screenW / 2f + TILE / 2f;
        cameraY = gs.heroY * TILE - (screenH - GameConstants.HUD_HEIGHT) / 2f + TILE / 2f;
        clampCamera();
    }

    public void scrollCamera(float dx, float dy) {
        cameraX -= dx; cameraY -= dy; clampCamera();
    }

    private void clampCamera() {
        MapData m = MapData.get();
        cameraX = Math.max(0, Math.min(cameraX, Math.max(0, m.width  * TILE - screenW)));
        cameraY = Math.max(0, Math.min(cameraY, Math.max(0, m.height * TILE - screenH)));
    }

    public int screenToTileX(float sx) { return (int)((sx + cameraX) / TILE); }
    public int screenToTileY(float sy) { return (int)((sy + cameraY) / TILE); }

    public void render(Canvas canvas) {
        GameState gs = GameState.get();
        MapData   mp = MapData.get();
        drawTiles(canvas, mp, gs);
        drawObjects(canvas, mp, gs);
        drawHero(canvas, gs);
        drawFog(canvas, mp, gs);
    }

    private void drawTiles(Canvas canvas, MapData map, GameState gs) {
        int sc = Math.max(0, (int)(cameraX / TILE));
        int sr = Math.max(0, (int)(cameraY / TILE));
        int ec = Math.min(map.width,  sc + screenW / TILE + 2);
        int er = Math.min(map.height, sr + screenH / TILE + 2);
        for (int r = sr; r < er; r++) {
            for (int c = sc; c < ec; c++) {
                float sx = c * TILE - cameraX, sy = r * TILE - cameraY;
                int tt = map.getTile(c, r);
                tilePaint.setStyle(Paint.Style.FILL);
                tilePaint.setColor(TILE_COLORS[Math.min(tt, TILE_COLORS.length - 1)]);
                canvas.drawRect(sx, sy, sx + TILE, sy + TILE, tilePaint);
                drawTileDetail(canvas, sx, sy, tt);
                tilePaint.setColor(0x18000000);
                tilePaint.setStyle(Paint.Style.STROKE);
                tilePaint.setStrokeWidth(0.5f);
                canvas.drawRect(sx, sy, sx + TILE, sy + TILE, tilePaint);
            }
        }
    }

    private void drawTileDetail(Canvas canvas, float sx, float sy, int type) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float cx = sx + TILE / 2f, cy = sy + TILE / 2f;
        switch (type) {
            case GameConstants.TILE_MOUNTAIN:
                p.setColor(0xFF888888);
                Path tri = new Path();
                tri.moveTo(cx, sy+6); tri.lineTo(sx+6, sy+TILE-6);
                tri.lineTo(sx+TILE-6, sy+TILE-6); tri.close();
                canvas.drawPath(tri, p);
                p.setColor(0xFFdddddd);
                Path peak = new Path();
                peak.moveTo(cx, sy+6); peak.lineTo(cx-6, sy+18); peak.lineTo(cx+6, sy+18); peak.close();
                canvas.drawPath(peak, p);
                break;
            case GameConstants.TILE_FOREST:
                p.setColor(0xFF0a3d0a);
                canvas.drawCircle(cx, cy-4, 14, p);
                p.setColor(0xFF5c3a1e);
                canvas.drawRect(cx-4, cy+9, cx+4, cy+20, p);
                break;
            case GameConstants.TILE_WATER:
                p.setColor(0x881188ff); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.5f);
                canvas.drawLine(sx+8, cy-5, sx+TILE-8, cy-5, p);
                canvas.drawLine(sx+5, cy+5, sx+TILE-5, cy+5, p);
                break;
            case GameConstants.TILE_ROAD:
                p.setColor(0xFFa08050);
                canvas.drawRect(sx+15, sy, sx+TILE-15, sy+TILE, p);
                canvas.drawRect(sx, sy+15, sx+TILE, sy+TILE-15, p);
                break;
        }
    }

    private void drawObjects(Canvas canvas, MapData map, GameState gs) {
        for (MapData.MapObject obj : map.mapObjects) {
            if (!obj.alive || !gs.isTileRevealed(obj.x, obj.y)) continue;
            float sx = obj.x * TILE - cameraX, sy = obj.y * TILE - cameraY;
            if (sx < -TILE || sx > screenW || sy < -TILE || sy > screenH) continue;
            drawMapObject(canvas, obj, sx, sy, gs);
        }
    }

    private void drawMapObject(Canvas canvas, MapData.MapObject obj, float sx, float sy, GameState gs) {
        float cx = sx + TILE / 2f, cy = sy + TILE / 2f;
        objPaint.setStyle(Paint.Style.FILL);
        switch (obj.type) {
            case GameConstants.OBJ_TOWN:
                objPaint.setColor(0xFFFFD700);
                canvas.drawRect(sx+8, sy+14, sx+TILE-8, sy+TILE-4, objPaint);
                canvas.drawRect(sx+6, sy+6, sx+18, sy+20, objPaint);
                canvas.drawRect(sx+TILE-18, sy+6, sx+TILE-6, sy+20, objPaint);
                objPaint.setColor(0xFF8B4513);
                canvas.drawRect(cx-5, sy+TILE-16, cx+5, sy+TILE-4, objPaint);
                lbl(canvas, "🏰", cx, sy);
                break;
            case GameConstants.OBJ_MINE:
                objPaint.setColor(0xFF6688ff);
                canvas.drawCircle(cx, cy, TILE/2f-6, objPaint);
                lbl(canvas, "⛏", cx, sy);
                break;
            case GameConstants.OBJ_NEUTRAL_ARMY:
                objPaint.setColor(0xFF00cc66);
                canvas.drawRoundRect(new RectF(sx+6, sy+6, sx+TILE-6, sy+TILE-6), 8, 8, objPaint);
                lbl(canvas, GameConstants.UNIT_NAMES[obj.unitType].substring(0,1), cx, sy+2);
                textPaint.setTextSize(13f); textPaint.setColor(0xFF003300);
                canvas.drawText("×"+obj.unitCount, cx, sy+TILE-4, textPaint);
                textPaint.setTextSize(20f); textPaint.setColor(Color.WHITE);
                break;
            case GameConstants.OBJ_ENEMY_HERO:
                objPaint.setColor(0xFFdd2222);
                Path dia = new Path();
                dia.moveTo(cx, sy+4); dia.lineTo(sx+TILE-4, cy);
                dia.lineTo(cx, sy+TILE-4); dia.lineTo(sx+4, cy); dia.close();
                canvas.drawPath(dia, objPaint);
                lbl(canvas, "☠", cx, sy+2);
                break;
            case GameConstants.OBJ_CHEST:
                objPaint.setColor(0xFFFFDD00);
                canvas.drawRect(sx+8, sy+16, sx+TILE-8, sy+TILE-8, objPaint);
                objPaint.setColor(0xFFCC8800);
                canvas.drawRect(sx+8, sy+12, sx+TILE-8, sy+20, objPaint);
                lbl(canvas, "💰", cx, sy);
                break;
            case GameConstants.OBJ_SHRINE:
                objPaint.setColor(0xFFcc88ff);
                canvas.drawCircle(cx, cy, TILE/2f-6, objPaint);
                lbl(canvas, "✦", cx, sy+4);
                break;
            case GameConstants.OBJ_ARTIFACT:
                objPaint.setColor(Color.WHITE);
                canvas.drawCircle(cx, cy, TILE/2f-6, objPaint);
                lbl(canvas, "★", cx, sy+4);
                break;
        }
        // Серый оттенок для посещённых зданий
        if (!gs.canVisitBuilding(obj.x, obj.y)
                && obj.type != GameConstants.OBJ_ENEMY_HERO
                && obj.type != GameConstants.OBJ_CHEST) {
            Paint vp = new Paint(); vp.setColor(0x66000000);
            canvas.drawRect(sx, sy, sx+TILE, sy+TILE, vp);
        }
    }

    private void lbl(Canvas canvas, String t, float cx, float sy) {
        textPaint.setColor(Color.WHITE); textPaint.setTextSize(20f);
        canvas.drawText(t, cx, sy+22, textPaint);
    }

    private void drawHero(Canvas canvas, GameState gs) {
        float px = gs.heroX * TILE - cameraX, py = gs.heroY * TILE - cameraY;
        float cx = px + TILE/2f, cy = py + TILE/2f;
        heroPaint.setStyle(Paint.Style.FILL);
        heroPaint.setColor(0x55000000);
        canvas.drawOval(new RectF(px+10, py+TILE-10, px+TILE-10, py+TILE), heroPaint);
        heroPaint.setColor(0xFF0088FF);
        canvas.drawCircle(cx, cy, TILE/2f-4, heroPaint);
        heroPaint.setColor(Color.WHITE); heroPaint.setStyle(Paint.Style.STROKE); heroPaint.setStrokeWidth(3f);
        canvas.drawCircle(cx, cy, TILE/2f-4, heroPaint);
        heroPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(Color.WHITE); textPaint.setTextSize(24f);
        canvas.drawText("⚔", cx, cy+10, textPaint);
        textPaint.setTextSize(20f);
    }

    private void drawFog(Canvas canvas, MapData map, GameState gs) {
        int sc = Math.max(0, (int)(cameraX/TILE)), sr = Math.max(0, (int)(cameraY/TILE));
        int ec = Math.min(map.width, sc+screenW/TILE+2), er = Math.min(map.height, sr+screenH/TILE+2);
        for (int r = sr; r < er; r++) for (int c = sc; c < ec; c++) {
            if (!gs.isTileRevealed(c, r)) {
                float sx = c*TILE-cameraX, sy = r*TILE-cameraY;
                canvas.drawRect(sx, sy, sx+TILE, sy+TILE, fogPaint);
            }
        }
    }
}
