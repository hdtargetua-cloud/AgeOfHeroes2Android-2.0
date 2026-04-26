package com.aoh2.data;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;
/**
 * Состояние игры.
 * ФИКС БАГА зданий: флаг посещения хранится как "x:y:week".
 * Сброс только при смене недели — повторный вход НЕ даёт армию.
 */
public class GameState {
    public int heroX=5,heroY=5;
    public int heroMovePoints=20,heroMovePointsMax=20;
    public int gold=GameConstants.STARTING_GOLD,week=1,day=1;
    public int[] armyTypes=new int[5],armyCounts=new int[5];
    public int armyStackCount=0;
    public boolean[] mapRevealed;
    public int mapWidth=GameConstants.MAP_COLS,mapHeight=GameConstants.MAP_ROWS;
    private final Set<String> visitedBuildings=new HashSet<>();
    public final Set<String> capturedMines=new HashSet<>();
    public boolean[] storyFlags=new boolean[64];
    private static GameState instance;
    public static GameState get(){if(instance==null)instance=new GameState();return instance;}
    public static void reset(){instance=new GameState();}
    private GameState(){mapRevealed=new boolean[mapWidth*mapHeight];revealAround(heroX,heroY,3);}

    // ФИКС: проверка посещения здания
    public boolean canVisitBuilding(int x,int y){return!visitedBuildings.contains(x+":"+y+":"+week);}
    public void markBuildingVisited(int x,int y){visitedBuildings.add(x+":"+y+":"+week);}

    public void onNewWeek(){week++;day=1;heroMovePoints=heroMovePointsMax;visitedBuildings.clear();
        gold+=capturedMines.size()*GameConstants.MINE_INCOME_PER_DAY*GameConstants.DAYS_PER_WEEK;}
    public void onNewDay(){day++;if(day>GameConstants.DAYS_PER_WEEK){onNewWeek();return;}
        heroMovePoints=heroMovePointsMax;gold+=capturedMines.size()*GameConstants.MINE_INCOME_PER_DAY;}

    public void revealAround(int cx,int cy,int r){
        for(int dy=-r;dy<=r;dy++)for(int dx=-r;dx<=r;dx++){
            int x=cx+dx,y=cy+dy;
            if(x>=0&&x<mapWidth&&y>=0&&y<mapHeight)mapRevealed[y*mapWidth+x]=true;}}
    public boolean isTileRevealed(int x,int y){
        if(x<0||x>=mapWidth||y<0||y>=mapHeight)return false;
        return mapRevealed[y*mapWidth+x];}

    public boolean addToArmy(int type,int count){
        for(int i=0;i<armyStackCount;i++)if(armyTypes[i]==type){armyCounts[i]+=count;return true;}
        if(armyStackCount>=5)return false;
        armyTypes[armyStackCount]=type;armyCounts[armyStackCount]=count;armyStackCount++;return true;}

    public void save(Context ctx){
        try{SharedPreferences.Editor ed=ctx.getSharedPreferences(GameConstants.PREFS_NAME,0).edit();
            ed.putBoolean(GameConstants.KEY_SAVE_EXISTS,true);
            ed.putInt(GameConstants.KEY_GOLD,gold);ed.putInt(GameConstants.KEY_WEEK,week);
            ed.putInt(GameConstants.KEY_DAY,day);ed.putInt(GameConstants.KEY_HERO_X,heroX);
            ed.putInt(GameConstants.KEY_HERO_Y,heroY);
            JSONArray a=new JSONArray();
            for(int i=0;i<armyStackCount;i++){JSONObject o=new JSONObject();
                o.put("t",armyTypes[i]);o.put("c",armyCounts[i]);a.put(o);}
            ed.putString(GameConstants.KEY_ARMY,a.toString());
            ed.putStringSet(GameConstants.KEY_VISITED_BUILDINGS,new HashSet<>(visitedBuildings));
            ed.putStringSet("mines",new HashSet<>(capturedMines));
            StringBuilder fog=new StringBuilder();
            for(boolean b:mapRevealed)fog.append(b?'1':'0');ed.putString("fog",fog.toString());
            ed.apply();}catch(Exception e){e.printStackTrace();}}

    public boolean load(Context ctx){
        SharedPreferences p=ctx.getSharedPreferences(GameConstants.PREFS_NAME,0);
        if(!p.getBoolean(GameConstants.KEY_SAVE_EXISTS,false))return false;
        try{gold=p.getInt(GameConstants.KEY_GOLD,GameConstants.STARTING_GOLD);
            week=p.getInt(GameConstants.KEY_WEEK,1);day=p.getInt(GameConstants.KEY_DAY,1);
            heroX=p.getInt(GameConstants.KEY_HERO_X,5);heroY=p.getInt(GameConstants.KEY_HERO_Y,5);
            String aj=p.getString(GameConstants.KEY_ARMY,"[]");
            JSONArray a=new JSONArray(aj);armyStackCount=a.length();
            for(int i=0;i<armyStackCount;i++){JSONObject o=a.getJSONObject(i);
                armyTypes[i]=o.getInt("t");armyCounts[i]=o.getInt("c");}
            visitedBuildings.clear();
            visitedBuildings.addAll(p.getStringSet(GameConstants.KEY_VISITED_BUILDINGS,new HashSet<>()));
            capturedMines.clear();
            capturedMines.addAll(p.getStringSet("mines",new HashSet<>()));
            String fog=p.getString("fog","");
            for(int i=0;i<Math.min(fog.length(),mapRevealed.length);i++)
                mapRevealed[i]=fog.charAt(i)=='1';
            return true;}catch(Exception e){e.printStackTrace();return false;}}

    public static boolean hasSave(Context ctx){
        return ctx.getSharedPreferences(GameConstants.PREFS_NAME,0)
            .getBoolean(GameConstants.KEY_SAVE_EXISTS,false);}
    public static void deleteSave(Context ctx){
        ctx.getSharedPreferences(GameConstants.PREFS_NAME,0).edit().clear().apply();}
}
