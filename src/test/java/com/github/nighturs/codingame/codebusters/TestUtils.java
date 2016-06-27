package com.github.nighturs.codingame.codebusters;

class TestUtils {
    static Player.GameState gs() {
        return new Player.GameState(0, 0, 0);
    }

    static Player.Point p(int x, int y) {
        return Player.Point.create(x, y);
    }

    static Player.Buster b(int x, int y) {
        return Player.Buster.create(0, p(x, y), false, 0);
    }

    static Player.Gost g(int x, int y) {
        return Player.Gost.create(0, p(x, y), 0);
    }
}
