package com.game.cookingspree;

/** Records a completed game's aggregate results using only the caller's local store. */
public final class GameOverPersistence {
    public interface StatsStore {
        int getHighScore();
        int getGamesPlayed();
        float getAverageScore();
        void setHighScore(int score);
        void setAverageScore(float score);
        void setGamesPlayed(int count);
    }

    private GameOverPersistence() {}

    public static void complete(int score, StatsStore stats, Runnable clearSave) {
        if (score > stats.getHighScore()) stats.setHighScore(score);
        int gamesPlayed = stats.getGamesPlayed();
        float average = ((gamesPlayed * stats.getAverageScore()) + score) / (gamesPlayed + 1);
        stats.setAverageScore(average);
        stats.setGamesPlayed(gamesPlayed + 1);
        clearSave.run();
    }
}
