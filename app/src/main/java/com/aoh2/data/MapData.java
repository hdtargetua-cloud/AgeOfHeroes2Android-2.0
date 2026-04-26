package com.aoh2.data;

import android.content.Context;
import android.util.Log;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Парсер и хранилище данных карты.
 *
 * Оригинальные бинарные файлы игры:
 *  - r    : данные карты (тайлы, объекты, позиции)
 *  - t    : типы тайлов / terrain
 *  - q    : квесты и диалоги
 *  - i0   : изображения/спрайты метаданные
 *  - d    : данные зданий
 *  - 0, 1 : дополнительные уровни
 *
 * Карта расширена относительно оригинала для большого экрана Android.
 */
public class MapData {

    private static final String TAG = "MapData";

    // Тайловая карта
    public byte[] tiles;        // тип тайла для каждой клетки
    public byte[] objects;      // объект на клетке (0 = пусто)
    public int[]  objectData;   // дополнительные данные объекта (тип юнита, кол-во и т.д.)
    public int width, height;

    // Объекты на карте
    public static class MapObject {
        public int x, y;
        public int type;        // GameConstants.OBJ_*
        public int unitType;    // тип юнита (для OBJ_NEUTRAL_ARMY и OBJ_ENEMY_HERO)
        public int unitCount;
        public int goldAmount;  // для сундуков/шахт
        public boolean visited; // посещён ли
        public boolean alive;   // не побеждён ли

        public MapObject(int x, int y, int type) {
            this.x = x; this.y = y; this.type = type;
            this.alive = true;
        }
    }

    public final List<MapObject> mapObjects = new ArrayList<>();

    private static MapData instance;
    public static MapData get() {
        if (instance == null) instance = new MapData();
        return instance;
    }

    private MapData() {}

    /**
     * Загружает карту: сначала пробует прочитать оригинальные бинарные данные,
     * при ошибке — генерирует процедурную карту.
     */
    public void load(Context ctx) {
        width  = GameConstants.MAP_COLS;
        height = GameConstants.MAP_ROWS;
        tiles   = new byte[width * height];
        objects = new byte[width * height];
        objectData = new int[width * height * 2];

        boolean loaded = tryLoadOriginalMap(ctx);
        if (!loaded) {
            Log.w(TAG, "Original map data not found, generating procedural map");
            generateProceduralMap();
        }
        addExpandedContent(); // новые враги и нейтралы для большого экрана
    }

    // ─────────────────────────────────────────────────────────
    // Загрузка оригинальных данных
    // ─────────────────────────────────────────────────────────

    private boolean tryLoadOriginalMap(Context ctx) {
        try {
            // Файл 'r' содержит основные данные карты
            InputStream is = ctx.getAssets().open("data/r.bin");
            DataInputStream dis = new DataInputStream(is);

            int objCount = dis.readUnsignedShort(); // количество объектов

            // Позиции объектов
            short[] objX = new short[objCount];
            short[] objY = new short[objCount];
            for (int i = 0; i < objCount; i++) {
                objX[i] = dis.readShort();
                objY[i] = dis.readShort();
            }

            // Типы объектов
            byte[] objTypes = new byte[objCount];
            dis.readFully(objTypes);

            // Построить список объектов
            for (int i = 0; i < objCount; i++) {
                int mx = objX[i], my = objY[i];
                if (mx >= 0 && mx < width && my >= 0 && my < height) {
                    MapObject obj = new MapObject(mx, my, mapObjType(objTypes[i] & 0xFF));
                    mapObjects.add(obj);
                    objects[my * width + mx] = (byte) obj.type;
                }
            }

            dis.close();

            // Файл 't' — типы тайлов
            InputStream ts = ctx.getAssets().open("data/t.bin");
            DataInputStream tds = new DataInputStream(ts);
            int terrainCount = tds.readUnsignedByte(); // количество типов terrain
            // Читаем таблицу размещения тайлов
            // Структура: для каждого региона — тип тайла и диапазон координат
            // (упрощённое заполнение)
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    // Базовая трава везде, потом процедурные изменения
                    tiles[y * width + x] = GameConstants.TILE_GRASS;
                }
            }
            tds.close();

            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to load original map: " + e.getMessage());
            return false;
        }
    }

    /** Конвертирует оригинальные коды объектов в наши константы */
    private int mapObjType(int orig) {
        switch (orig) {
            case 14: return GameConstants.OBJ_TOWN;
            case 15: return GameConstants.OBJ_MINE;
            case 16: return GameConstants.OBJ_CAMP;
            case 6:  return GameConstants.OBJ_CHEST;
            default: return GameConstants.OBJ_NONE;
        }
    }

    // ─────────────────────────────────────────────────────────
    // Процедурная генерация карты (fallback)
    // ─────────────────────────────────────────────────────────

    private void generateProceduralMap() {
        Random rng = new Random(42); // фиксированный seed для воспроизводимости

        // Базовая трава
        for (int i = 0; i < tiles.length; i++) tiles[i] = GameConstants.TILE_GRASS;

        // Горы по краям
        for (int x = 0; x < width; x++) {
            setTile(x, 0, GameConstants.TILE_MOUNTAIN);
            setTile(x, height - 1, GameConstants.TILE_MOUNTAIN);
        }
        for (int y = 0; y < height; y++) {
            setTile(0, y, GameConstants.TILE_MOUNTAIN);
            setTile(width - 1, y, GameConstants.TILE_MOUNTAIN);
        }

        // Горные гряды
        addMountainRange(rng, 4, 4, 6, 8);
        addMountainRange(rng, 15, 2, 4, 10);
        addMountainRange(rng, 8, 18, 8, 5);

        // Леса
        addForests(rng, 12);

        // Реки/вода
        addWaterBodies(rng, 4);

        // Дороги от старта к городам
        addRoad(5, 5, 12, 12);
        addRoad(12, 12, 20, 8);

        // Базовые объекты
        addObject(5, 5, GameConstants.OBJ_TOWN, 0, 0, 0);         // стартовый замок
        addObject(20, 25, GameConstants.OBJ_TOWN, 0, 0, 0);       // замок врага
        addObject(12, 8, GameConstants.OBJ_MINE, 0, 0, 200);      // шахта
        addObject(7, 15, GameConstants.OBJ_MINE, 0, 0, 200);      // шахта
        addObject(18, 14, GameConstants.OBJ_SHRINE, 0, 0, 0);     // святилище
        addObject(3, 20, GameConstants.OBJ_CHEST, 0, 0, 300);     // сундук
        addObject(22, 10, GameConstants.OBJ_CHEST, 0, 0, 150);    // сундук
        addObject(15, 20, GameConstants.OBJ_ARTIFACT, 0, 0, 0);   // артефакт
    }

    private void addMountainRange(Random rng, int sx, int sy, int w, int h) {
        for (int y = sy; y < sy + h && y < height; y++) {
            for (int x = sx; x < sx + w && x < width; x++) {
                if (rng.nextInt(3) > 0) setTile(x, y, GameConstants.TILE_MOUNTAIN);
            }
        }
    }

    private void addForests(Random rng, int count) {
        for (int i = 0; i < count; i++) {
            int fx = 2 + rng.nextInt(width - 4);
            int fy = 2 + rng.nextInt(height - 4);
            int size = 2 + rng.nextInt(3);
            for (int dy = -size; dy <= size; dy++) {
                for (int dx = -size; dx <= size; dx++) {
                    int x = fx + dx, y = fy + dy;
                    if (x > 0 && x < width - 1 && y > 0 && y < height - 1
                            && getTile(x, y) == GameConstants.TILE_GRASS) {
                        if (rng.nextInt(3) > 0) setTile(x, y, GameConstants.TILE_FOREST);
                    }
                }
            }
        }
    }

    private void addWaterBodies(Random rng, int count) {
        for (int i = 0; i < count; i++) {
            int wx = 3 + rng.nextInt(width - 6);
            int wy = 3 + rng.nextInt(height - 6);
            setTile(wx, wy, GameConstants.TILE_WATER);
            setTile(wx + 1, wy, GameConstants.TILE_WATER);
            setTile(wx, wy + 1, GameConstants.TILE_WATER);
            setTile(wx + 1, wy + 1, GameConstants.TILE_WATER);
        }
    }

    private void addRoad(int x1, int y1, int x2, int y2) {
        // Простой L-образный путь
        int x = x1;
        while (x != x2) {
            if (getTile(x, y1) == GameConstants.TILE_GRASS) setTile(x, y1, GameConstants.TILE_ROAD);
            x += (x < x2) ? 1 : -1;
        }
        int y = y1;
        while (y != y2) {
            if (getTile(x2, y) == GameConstants.TILE_GRASS) setTile(x2, y, GameConstants.TILE_ROAD);
            y += (y < y2) ? 1 : -1;
        }
    }

    // ─────────────────────────────────────────────────────────
    // Расширенный контент для большого экрана Android
    // ─────────────────────────────────────────────────────────

    /**
     * Добавляем новых врагов и нейтральные армии для вербовки.
     * Они заполняют расширенную область карты которой не было в оригинале 240x320.
     */
    private void addExpandedContent() {
        Random rng = new Random(1337);

        // Нейтральные армии (можно завербовать за золото)
        int[][] neutralSpawns = {
            {9, 3, GameConstants.UNIT_PEASANT, 15},
            {16, 6, GameConstants.UNIT_ARCHER, 8},
            {4, 12, GameConstants.UNIT_PIKEMAN, 6},
            {21, 16, GameConstants.UNIT_WOLF, 5},
            {11, 22, GameConstants.UNIT_SKELETON, 12},
            {6, 27, GameConstants.UNIT_ARCHER, 10},
            {23, 28, GameConstants.UNIT_KNIGHT, 4},
            {14, 30, GameConstants.UNIT_WIZARD, 3},
        };
        for (int[] s : neutralSpawns) {
            if (isPassable(s[0], s[1])) {
                addObject(s[0], s[1], GameConstants.OBJ_NEUTRAL_ARMY, s[2], s[3], 0);
            }
        }

        // Враги (мобы на карте)
        int[][] enemySpawns = {
            {10, 7, GameConstants.UNIT_SKELETON, 10},
            {17, 11, GameConstants.UNIT_DEMON, 4},
            {8, 16, GameConstants.UNIT_WOLF, 8},
            {19, 20, GameConstants.UNIT_TROLL, 2},
            {13, 26, GameConstants.UNIT_DRAGON, 1},
            {24, 22, GameConstants.UNIT_DEMON, 6},
            {5, 30, GameConstants.UNIT_SKELETON, 15},
            {20, 30, GameConstants.UNIT_TROLL, 3},
        };
        for (int[] s : enemySpawns) {
            if (isPassable(s[0], s[1])) {
                addObject(s[0], s[1], GameConstants.OBJ_ENEMY_HERO, s[2], s[3], 0);
            }
        }

        // Дополнительные сундуки и артефакты в расширенной зоне
        addObject(3, 28, GameConstants.OBJ_CHEST, 0, 0, 500);
        addObject(24, 5, GameConstants.OBJ_CHEST, 0, 0, 250);
        addObject(22, 28, GameConstants.OBJ_MINE, 0, 0, 300);
    }

    private void addObject(int x, int y, int type, int unitType, int unitCount, int gold) {
        if (x < 0 || x >= width || y < 0 || y >= height) return;
        MapObject obj = new MapObject(x, y, type);
        obj.unitType  = unitType;
        obj.unitCount = unitCount;
        obj.goldAmount = gold;
        mapObjects.add(obj);
        objects[y * width + x] = (byte) type;
    }

    // ─────────────────────────────────────────────────────────
    // Утилиты
    // ─────────────────────────────────────────────────────────

    public void setTile(int x, int y, int type) {
        if (x >= 0 && x < width && y >= 0 && y < height)
            tiles[y * width + x] = (byte) type;
    }

    public int getTile(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) return GameConstants.TILE_MOUNTAIN;
        return tiles[y * width + x] & 0xFF;
    }

    public boolean isPassable(int x, int y) {
        int tile = getTile(x, y);
        return tile != GameConstants.TILE_MOUNTAIN && tile != GameConstants.TILE_WATER;
    }

    public MapObject getObjectAt(int x, int y) {
        for (MapObject obj : mapObjects) {
            if (obj.x == x && obj.y == y && obj.alive) return obj;
        }
        return null;
    }

    public static void reset() { instance = null; }
}
