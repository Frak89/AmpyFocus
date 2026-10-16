package com.ampy.focus;
import static org.junit.Assert.*;
import org.junit.Test;
import java.util.Set;

public class DomainRulesTest {
    @Test public void acceptsDomainsWithoutCapturingPaths() {
        Set<String> rules = DomainRules.parse("https://example.com/path,*.demo.org; test.com");
        assertTrue(rules.contains("example.com"));
        assertTrue(rules.contains("demo.org"));
        assertTrue(DomainRules.matches("video.example.com", rules));
        assertFalse(DomainRules.matches("notexample.com", rules));
        assertFalse(DomainRules.matches("else.com", rules));
    }
    @Test public void rejectsBadInput() {
        assertTrue(DomainRules.parse("localhost http:// 127.0.0.1:5353").isEmpty());
        assertFalse(DomainRules.matches(null, DomainRules.parse("example.org")));
    }
}
