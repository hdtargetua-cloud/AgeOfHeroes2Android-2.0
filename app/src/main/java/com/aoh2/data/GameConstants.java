package com.aoh2.data;
public final class GameConstants {
    private GameConstants() {}
    public static final int ORIGINAL_WIDTH=240,ORIGINAL_HEIGHT=320;
    public static final int TILE_SIZE=48;
    public static final float SCALE_FACTOR=3.0f;
    public static final int MAP_COLS=26,MAP_ROWS=32;
    public static final int TILE_GRASS=0,TILE_WATER=1,TILE_MOUNTAIN=2,TILE_FOREST=3;
    public static final int TILE_ROAD=4,TILE_SAND=5,TILE_SNOW=6,TILE_LAVA=7;
    public static final int OBJ_NONE=0,OBJ_TOWN=1,OBJ_MINE=2,OBJ_CAMP=3;
    public static final int OBJ_SHRINE=4,OBJ_CHEST=5,OBJ_ARTIFACT=6;
    public static final int OBJ_NEUTRAL_ARMY=7,OBJ_ENEMY_HERO=8;
    public static final int STATE_MAP=0,STATE_TOWN=1,STATE_BATTLE=2;
    public static final int UNIT_PEASANT=0,UNIT_ARCHER=1,UNIT_KNIGHT=2,UNIT_PIKEMAN=3;
    public static final int UNIT_WIZARD=4,UNIT_DEMON=5,UNIT_SKELETON=6;
    public static final int UNIT_WOLF=7,UNIT_TROLL=8,UNIT_DRAGON=9;
    public static final int[][] UNIT_STATS={
        {2,1,5,3,10},{5,3,10,4,25},{8,6,20,5,80},{6,8,15,4,60},
        {10,4,12,6,120},{12,7,18,7,200},{6,4,8,5,40},
        {9,5,14,8,90},{15,10,30,4,300},{20,15,50,6,500}
    };
    public static final String[] UNIT_NAMES={
        "Крестьянин","Лучник","Рыцарь","Пикинёр",
        "Маг","Демон","Скелет","Волк","Тролль","Дракон"
    };
    public static final int STARTING_GOLD=500;
    public static final int MINE_INCOME_PER_DAY=100;
    public static final int DAYS_PER_WEEK=7;
    public static final int MAX_BUILDING_VISITS_PER_WEEK=1;
    public static final int HUD_HEIGHT=80;
    public static final String PREFS_NAME="aoh2_save";
    public static final String KEY_SAVE_EXISTS="save_exists";
    public static final String KEY_GOLD="gold",KEY_WEEK="week",KEY_DAY="day";
    public static final String KEY_HERO_X="hero_x",KEY_HERO_Y="hero_y";
    public static final String KEY_ARMY="army";
    public static final String KEY_VISITED_BUILDINGS="visited_buildings";
}
