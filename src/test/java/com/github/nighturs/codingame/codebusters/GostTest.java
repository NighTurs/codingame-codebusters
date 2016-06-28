package com.github.nighturs.codingame.codebusters;

import com.github.nighturs.codingame.codebusters.Player.Gost;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static com.github.nighturs.codingame.codebusters.TestUtils.*;
import static org.junit.Assert.assertEquals;

public class GostTest {
    @Test
    public void testEscapingTo() {
        Player.gameState = TestUtils.gs();
        Player.gameState.initTurn(Arrays.asList(b(1000, 2500), b(5000, 5000)),
                Arrays.asList(b(1500, 1400)), Collections.emptyList());
        Gost g = g(1000, 1600);
        assertEquals(p(768, 1275), g.escapingTo());
        Player.gameState.initTurn(Arrays.asList(b(100, 100)), Collections.emptyList(), Collections.emptyList());
        g = g(0, 0);
        assertEquals(p(0, 0), g.escapingTo());
        Player.gameState.initTurn(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        g = g(0, 0);
        assertEquals(p(0, 0), g.escapingTo());
    }


}
