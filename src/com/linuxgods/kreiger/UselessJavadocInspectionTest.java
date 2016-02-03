package com.linuxgods.kreiger;

import org.junit.Test;

import static com.linuxgods.kreiger.UselessJavadocInspection.DEFAULT_STOP_WORDS;
import static org.junit.Assert.assertEquals;
import static com.linuxgods.kreiger.UselessJavadocInspection.normalize;
import static com.linuxgods.kreiger.UselessJavadocInspection.unCamelCase;

public class UselessJavadocInspectionTest {

    @Test
    public void testUnCamelCase() {
        assertEquals("set Property", unCamelCase("setProperty"));
        assertEquals("URL Encoder", unCamelCase("URLEncoder"));
        assertEquals("get XML Produkt", unCamelCase("getXMLProdukt"));
    }

    @Test
    public void testNormalize() {
        assertEquals("postnummer", normalize("setPostnummer", DEFAULT_STOP_WORDS));
        assertEquals("postnummer", normalize("returns property postnummer", DEFAULT_STOP_WORDS));
        assertEquals("produkt", normalize(" returns XMLProdukt ", DEFAULT_STOP_WORDS));
    }
}
