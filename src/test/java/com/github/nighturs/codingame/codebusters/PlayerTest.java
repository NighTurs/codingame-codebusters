package com.github.nighturs.codingame.codebusters;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;

import static com.github.nighturs.codingame.codebusters.Player.cap;
import static com.github.nighturs.codingame.codebusters.TestUtils.p;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PlayerTest {

    @Test
    public void sanityTest() {
        String in = "2\n" +
                "5\n" +
                "0\n" +
                "2\n" +
                "0 0 0 0 0 -1\n" +
                "1 0 0 0 0 -1\n" +
                "9999";
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();

        InputStream systemIn = System.in;
        PrintStream systemOut = System.out;
        System.setIn(new ByteArrayInputStream(in.getBytes()));
        System.setOut(new PrintStream(outStream));

        String out;
        try {
            Player.main(new String[]{});
            out = outStream.toString();
            assertTrue(out.startsWith("MOVE"));
        } finally {
            System.setIn(systemIn);
            System.setOut(systemOut);
        }
        System.out.println(out);
    }

    @Test
    public void testCap() {
        assertEquals(p(0, 0), cap(-1, -1));
        assertEquals(p(Player.X_UNIT, Player.Y_UNIT), cap(Player.X_UNIT + 100, Player.Y_UNIT + 100));
        assertEquals(p(500, 600), cap(500, 600));
    }
}
