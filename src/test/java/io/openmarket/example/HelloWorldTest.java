package io.openmarket.example;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HelloWorldTest {
    @Test
    public void testHello() {
        assertEquals("Hello!", new HelloWorld().sayHello());
    }
}
