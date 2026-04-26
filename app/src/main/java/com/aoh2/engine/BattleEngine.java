package com.aoh2.engine;

import com.aoh2.data.GameConstants;
import com.aoh2.data.GameState;
import java.util.Random;

public class BattleEngine {

    public interface BattleListener {
        void onBattleLog(String message);
        void onBattleEnd(boolean playerWon, int goldReward);
        void onUnitDamaged(boolean isPlayer, int stackIndex, int remaining);
    }

    public static class UnitStack {
        public int type, count, hpCurrent;
        public boolean isPlayer;
        public int stackIndex;

        public UnitStack(int type, int count, boolean isPlayer, int idx) {
            this.type = type; this.count = count;
            this.hpCurrent = GameConstants.UNIT_STATS[type][2];
            this.isPlayer = isPlayer; this.stackIndex = idx;
        }

        public int getAttack()  { return GameConstants.UNIT_STATS[type][0]; }
        public int getDefense() { return GameConstants.UNIT_STATS[type][1]; }
        public int getMaxHp()   { return GameConstants.UNIT_STATS[type][2]; }
        public int getSpeed()   { return GameConstants.UNIT_STATS[type][3]; }
        public boolean isDead() { return count <= 0; }
        public int totalDamage() { return count * getAttack(); }

        public int applyDamage(int damage) {
            int killed = 0;
            int eff = Math.max(1, damage - getDefense());
            hpCurrent -= eff;
            while (hpCurrent <= 0 && count > 0) {
                count--; killed++;
                if (count > 0) hpCurrent += getMaxHp();
            }
            return killed;
        }
    }

    private final GameState state;
    private final BattleListener listener;
    private final Random rng = new Random();

    private UnitStack[] playerStacks;
    private UnitStack[] enemyStacks;
    private UnitStack[] initiative;
    private int currentTurn = 0;
    private boolean resolved = false;

    public BattleEngine(GameState state, BattleListener listener) {
        this.state = state;
        this.listener = listener;
    }

    public void startBattle(int enemyUnitType, int enemyCount) {
        resolved = false; currentTurn = 0;
        playerStacks = new UnitStack[state.armyStackCount];
        for (int i = 0; i < state.armyStackCount; i++)
            playerStacks[i] = new UnitStack(state.armyTypes[i], state.armyCounts[i], true, i);
        enemyStacks = new UnitStack[]{ new UnitStack(enemyUnitType, enemyCount, false, 0) };
        buildInitiative();
        listener.onBattleLog("Бой начался!");
        listener.onBattleLog(enemyCount + "x " + GameConstants.UNIT_NAMES[enemyUnitType] + " атакуют!");
    }

    private void buildInitiative() {
        initiative = new UnitStack[playerStacks.length + enemyStacks.length];
        int idx = 0;
        for (UnitStack s : playerStacks) initiative[idx++] = s;
        for (UnitStack s : enemyStacks)  initiative[idx++] = s;
        java.util.Arrays.sort(initiative, (a, b) -> b.getSpeed() - a.getSpeed());
    }

    public boolean doNextTurn() {
        if (resolved || isBattleOver()) { if (!resolved) finishBattle(); return false; }
        while (currentTurn < initiative.length && initiative[currentTurn].isDead()) currentTurn++;
        if (currentTurn >= initiative.length) { currentTurn = 0; buildInitiative(); return !isBattleOver(); }
        UnitStack attacker = initiative[currentTurn++];
        if (attacker.isDead()) return !isBattleOver();
        UnitStack target = findTarget(attacker);
        if (target == null) return !isBattleOver();
        int base = attacker.totalDamage(), var = Math.max(1, base / 5);
        int dmg = base + rng.nextInt(var * 2 + 1) - var;
        int killed = target.applyDamage(dmg);
        listener.onBattleLog((attacker.isPlayer ? "-> " : "<- ") +
            GameConstants.UNIT_NAMES[attacker.type] + " [" + dmg + " дмг, " + killed + " убито]");
        listener.onUnitDamaged(target.isPlayer, target.stackIndex, target.count);
        if (isBattleOver()) { finishBattle(); return false; }
        return true;
    }

    private UnitStack findTarget(UnitStack a) {
        UnitStack[] targets = a.isPlayer ? enemyStacks : playerStacks;
        for (UnitStack t : targets) if (!t.isDead()) return t;
        return null;
    }

    private boolean isBattleOver() { return allDead(playerStacks) || allDead(enemyStacks); }
    private boolean allDead(UnitStack[] s) { for (UnitStack u : s) if (!u.isDead()) return false; return true; }

    private void finishBattle() {
        resolved = true;
        boolean won = !allDead(playerStacks);
        if (won) {
            for (UnitStack s : playerStacks) state.armyCounts[s.stackIndex] = s.count;
            int n = 0; int[] nt = new int[5], nc = new int[5];
            for (int i = 0; i < state.armyStackCount; i++)
                if (state.armyCounts[i] > 0) { nt[n] = state.armyTypes[i]; nc[n] = state.armyCounts[i]; n++; }
            state.armyTypes = nt; state.armyCounts = nc; state.armyStackCount = n;
            int gold = Math.max(10, 20 * GameConstants.UNIT_STATS[enemyStacks[0].type][0] + rng.nextInt(50));
            state.gold += gold;
            listener.onBattleLog("Победа! +" + gold + " золота");
            listener.onBattleEnd(true, gold);
        } else {
            listener.onBattleLog("Поражение!");
            listener.onBattleEnd(false, 0);
        }
    }

    public boolean isPlayerTurn() {
        if (initiative == null) return true;
        int t = currentTurn;
        while (t < initiative.length && initiative[t].isDead()) t++;
        return t >= initiative.length || initiative[t].isPlayer;
    }

    public boolean isBattleResolved() { return resolved; }
    public UnitStack[] getPlayerStacks() { return playerStacks; }
    public UnitStack[] getEnemyStacks()  { return enemyStacks; }
}
