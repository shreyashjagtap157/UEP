package com.universalplatform.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OutboundEndpointValidatorTest {
    private final OutboundEndpointValidator validator = new OutboundEndpointValidator(this::resolve);

    @Test
    void acceptsPublicUnicastEndpoint() {
        assertThat(validator.validate("https://public.example.com/events").toString())
                .isEqualTo("https://public.example.com/events");
    }

    @Test
    void rejectsLoopbackPrivateLinkLocalAndCarrierGradeNatAnswers() {
        for (String host : new String[]{
                "loopback.example.com",
                "private.example.com",
                "link-local.example.com",
                "carrier-nat.example.com",
                "reserved.example.com",
                "ipv6-ula.example.com",
                "ipv4-mapped-loopback.example.com"}) {
            assertThatThrownBy(() -> validator.validate("https://" + host + "/callback"))
                    .as("reject non-public address for %s", host)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("public unicast");
        }
    }

    @Test
    void rejectsHostnamesWithMixedPublicAndPrivateDnsAnswers() {
        assertThatThrownBy(() -> validator.validate("https://mixed.example.com/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("public unicast");
    }

    @Test
    void rejectsLocalNamesCredentialsFragmentsAndNonHttpSchemes() {
        assertThatThrownBy(() -> validator.validate("http://localhost:8080/metadata"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate("http://service.local/internal"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate("https://user:password@public.example.com/callback"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate("https://public.example.com/callback#fragment"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate("file:///etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private InetAddress[] resolve(String host) throws UnknownHostException {
        Map<String, InetAddress[]> answers = Map.of(
                "public.example.com", new InetAddress[]{ip("93.184.216.34")},
                "loopback.example.com", new InetAddress[]{ip("127.0.0.1")},
                "private.example.com", new InetAddress[]{ip("10.20.30.40")},
                "link-local.example.com", new InetAddress[]{ip("169.254.169.254")},
                "carrier-nat.example.com", new InetAddress[]{ip("100.64.0.1")},
                "reserved.example.com", new InetAddress[]{ip("192.0.2.10")},
                "ipv6-ula.example.com", new InetAddress[]{ip("fd12:3456::1")},
                "ipv4-mapped-loopback.example.com", new InetAddress[]{mappedLoopback()},
                "mixed.example.com", new InetAddress[]{ip("93.184.216.34"), ip("192.168.1.10")});
        InetAddress[] result = answers.get(host);
        if (result == null) {
            throw new UnknownHostException(host);
        }
        return result;
    }

    private static InetAddress ip(String value) {
        try {
            return InetAddress.getByName(value);
        } catch (UnknownHostException exception) {
            throw new AssertionError(exception);
        }
    }

    private static InetAddress mappedLoopback() {
        try {
            return InetAddress.getByAddress(new byte[]{
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xff, (byte) 0xff,
                    127, 0, 0, 1});
        } catch (UnknownHostException exception) {
            throw new AssertionError(exception);
        }
    }
}
