package com.aoh2.engine;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.Log;
import java.io.IOException;
import java.io.InputStream;

/**
 * Менеджер спрайтов.
 * Загружает PNG из assets/sprites/ (оригинальные файлы из JAR),
 * масштабирует x3 с качественной фильтрацией для HD экрана.
 */
public class SpriteManager {

    private static final String TAG = "SpriteManager";
    private static final float SCALE = 3.0f; // 240x320 → 720x960 (вписываем в экран)

    private final Context ctx;
    private final Bitmap[] sprites = new Bitmap[33];
    private final Paint scalePaint;

    private static SpriteManager instance;
    public static SpriteManager get(Context ctx) {
        if (instance == null) instance = new SpriteManager(ctx.getApplicationContext());
        return instance;
    }

    private SpriteManager(Context ctx) {
        this.ctx = ctx;
        scalePaint = new Paint();
        scalePaint.setFilterBitmap(true);  // билинейная фильтрация — чёткий пиксель-арт
        scalePaint.setAntiAlias(false);    // пиксели остаются пикселями
    }

    /**
     * Загружает все спрайты. Вызывать в фоновом потоке.
     */
    public void loadAll() {
        for (int i = 0; i <= 32; i++) {
            sprites[i] = loadSprite(i);
        }
        Log.d(TAG, "All sprites loaded");
    }

    private Bitmap loadSprite(int index) {
        try {
            InputStream is = ctx.getAssets().open("sprites/" + index + ".png");
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap orig = BitmapFactory.decodeStream(is, null, opts);
            is.close();
            if (orig == null) return null;
            return scaleUp(orig);
        } catch (IOException e) {
            Log.w(TAG, "Sprite " + index + " not found: " + e.getMessage());
            return null;
        }
    }

    /**
     * Масштабирует спрайт x3 с pixel-perfect качеством.
     * Для маленьких спрайтов (< 64px) — ближайший сосед (резкие пиксели).
     * Для больших (фоны) — билинейная для сглаживания.
     */
    private Bitmap scaleUp(Bitmap src) {
        int newW = Math.round(src.getWidth()  * SCALE);
        int newH = Math.round(src.getHeight() * SCALE);
        Matrix matrix = new Matrix();
        matrix.setScale(SCALE, SCALE);
        // Для маленьких спрайтов — nearest neighbor (чёткий пиксель-арт)
        boolean isSmall = src.getWidth() < 64 || src.getHeight() < 64;
        Bitmap result = Bitmap.createBitmap(newW, newH, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(result);
        Paint p = new Paint();
        p.setFilterBitmap(!isSmall);
        c.drawBitmap(src, matrix, p);
        src.recycle();
        return result;
    }

    public Bitmap get(int index) {
        if (index < 0 || index >= sprites.length) return null;
        return sprites[index];
    }

    /** Рисует субспрайт (регион из спрайт-листа) */
    public void drawRegion(Canvas canvas, int spriteIndex,
                           int srcX, int srcY, int srcW, int srcH,
                           int dstX, int dstY, Paint paint) {
        Bitmap bmp = get(spriteIndex);
        if (bmp == null) return;
        // Координаты уже масштабированы x3
        int sx = Math.round(srcX * SCALE);
        int sy = Math.round(srcY * SCALE);
        int sw = Math.round(srcW * SCALE);
        int sh = Math.round(srcH * SCALE);
        android.graphics.Rect src = new android.graphics.Rect(sx, sy, sx + sw, sy + sh);
        android.graphics.Rect dst = new android.graphics.Rect(dstX, dstY, dstX + sw, dstY + sh);
        canvas.drawBitmap(bmp, src, dst, paint);
    }

    public void release() {
        for (int i = 0; i < sprites.length; i++) {
            if (sprites[i] != null) { sprites[i].recycle(); sprites[i] = null; }
        }
        instance = null;
    }
}
