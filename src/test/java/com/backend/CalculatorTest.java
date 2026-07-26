package com.backend;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CalculatorTest {

    int add(int a, int b) { return a + b; }

    @Test
    void add_returnsSumOfTwoNumbers() {
        assertEquals(5, add(2, 3));
    }

    @Test
    void add_withNegativeNumbers_returnsCorrectSum() {
        assertEquals(-1, add(2, -3));
    }
}
