package com.aoh2.engine;

import android.view.MotionEvent;

/**
 * Обработчик тач-управления.
 *
 * Поддерживает:
 *  - TAP: короткое нажатие — ход героя / взаимодействие
 *  - SWIPE: перетаскивание карты
 *  - LONG PRESS: контекстное меню / информация об объекте
 *  - PINCH: (зарезервировано для зума)
 */
public class TouchHandler {

    public interface TouchListener {
        void onTap(float x, float y);
        void onSwipe(float dx, float dy);
        void onLongPress(float x, float y);
    }

    private static final int SWIPE_MIN   = 15;  // px минимум для свайпа
    private static final int TAP_MAX     = 20;  // px максимум дрейфа для тапа
    private static final long LONG_MS    = 400; // мс для долгого нажатия

    private final TouchListener listener;

    private float downX, downY;
    private float lastX, lastY;
    private long  downTime;
    private boolean moved;
    private boolean longFired;

    // Для инерции свайпа
    private float velX, velY;
    private static final float FRICTION = 0.85f;

    public TouchHandler(TouchListener listener) {
        this.listener = listener;
    }

    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {

            case MotionEvent.ACTION_DOWN:
                downX  = lastX = e.getX();
                downY  = lastY = e.getY();
                downTime = System.currentTimeMillis();
                moved  = false;
                longFired = false;
                velX = velY = 0;
                return true;

            case MotionEvent.ACTION_MOVE:
                float cx = e.getX(), cy = e.getY();
                float dx = cx - lastX, dy = cy - lastY;
                float totalDx = cx - downX, totalDy = cy - downY;

                if (!moved && (Math.abs(totalDx) > TAP_MAX || Math.abs(totalDy) > TAP_MAX)) {
                    moved = true;
                }

                if (moved) {
                    listener.onSwipe(dx, dy);
                    velX = dx; velY = dy;
                }

                // Долгое нажатие (без движения)
                if (!moved && !longFired &&
                        System.currentTimeMillis() - downTime >= LONG_MS) {
                    longFired = true;
                    listener.onLongPress(downX, downY);
                }

                lastX = cx; lastY = cy;
                return true;

            case MotionEvent.ACTION_UP:
                if (!moved && !longFired) {
                    long dt = System.currentTimeMillis() - downTime;
                    if (dt < LONG_MS) {
                        listener.onTap(downX, downY);
                    }
                }
                // Инерция после свайпа продолжается в game loop
                return true;

            case MotionEvent.ACTION_CANCEL:
                moved = false;
                velX = velY = 0;
                return true;
        }
        return false;
    }

    /**
     * Вызывать каждый кадр для инерции свайпа.
     * Возвращает {dx, dy} остаточного движения.
     */
    public float[] applyInertia() {
        if (Math.abs(velX) < 0.5f && Math.abs(velY) < 0.5f) {
            velX = velY = 0;
            return new float[]{0, 0};
        }
        float[] result = {velX, velY};
        velX *= FRICTION;
        velY *= FRICTION;
        return result;
    }

    public boolean hasInertia() {
        return Math.abs(velX) > 0.5f || Math.abs(velY) > 0.5f;
    }
}
