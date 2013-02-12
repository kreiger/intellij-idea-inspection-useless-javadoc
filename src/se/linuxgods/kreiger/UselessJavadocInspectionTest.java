package se.linuxgods.kreiger;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static se.linuxgods.kreiger.UselessJavadocInspection.normalize;
import static se.linuxgods.kreiger.UselessJavadocInspection.unCamelCase;

public class UselessJavadocInspectionTest {

    @Test
    public void testUnCamelCase() {
        assertEquals("set Property", unCamelCase("setProperty"));
        assertEquals("URL Encoder", unCamelCase("URLEncoder"));
        assertEquals("get XML Produkt", unCamelCase("getXMLProdukt"));
    }

    @Test
    public void testNormalize() {
        assertEquals("postnummer", normalize("setPostnummer"));
        assertEquals("postnummer", normalize("returns property postnummer"));
        assertEquals("produkt", normalize(" returns XMLProdukt "));
    }
}
