package com.universalplatform.integration;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Blocks outbound HTTP endpoints that resolve to non-public addresses.
 *
 * Validation must run at configuration time and again immediately before
 * delivery. Network-level egress policy is still required to eliminate DNS
 * rebinding/TOCTOU races between resolution and socket connection.
 */
@Component
class OutboundEndpointValidator {
    private static final Set<String> BLOCKED_SUFFIXES =
            Set.of(".localhost", ".local", ".internal", ".test", ".invalid", ".example");

    private final OutboundHostResolver resolver;

    OutboundEndpointValidator(OutboundHostResolver resolver) {
        this.resolver = resolver;
    }

    URI validate(String rawValue) {
        String raw = rawValue == null ? "" : rawValue.trim();
        if (raw.isEmpty() || raw.length() > 1000) {
            throw invalidEndpoint();
        }

        final URI uri;
        try {
            uri = URI.create(raw);
        } catch (IllegalArgumentException exception) {
            throw invalidEndpoint();
        }

        String scheme = uri.getScheme();
        if (scheme == null
                || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))
                || uri.getRawUserInfo() != null
                || uri.getRawFragment() != null
                || uri.getPort() == 0
                || uri.getPort() > 65535
                || uri.getPort() < -1
                || uri.getHost() == null) {
            throw invalidEndpoint();
        }

        String host = uri.getHost();
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        if (host.isBlank() || host.indexOf('%') >= 0) {
            throw invalidEndpoint();
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (normalizedHost.endsWith(".")) {
            normalizedHost = normalizedHost.substring(0, normalizedHost.length() - 1);
        }
        if (normalizedHost.isBlank()
                || normalizedHost.equals("localhost")
                || BLOCKED_SUFFIXES.stream().anyMatch(normalizedHost::endsWith)
                || normalizedHost.equals("metadata.google.internal")
                || normalizedHost.equals("instance-data.ec2.internal")) {
            throw blockedEndpoint();
        }

        final InetAddress[] addresses;
        try {
            addresses = resolver.resolve(host);
        } catch (UnknownHostException | SecurityException exception) {
            throw new IllegalArgumentException("Outbound endpoint host could not be resolved", exception);
        }
        if (addresses == null || addresses.length == 0) {
            throw new IllegalArgumentException("Outbound endpoint host did not resolve to an address");
        }
        for (InetAddress address : addresses) {
            if (address == null || isNonPublicAddress(address)) {
                throw blockedEndpoint();
            }
        }
        return uri;
    }

    private static boolean isNonPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address || bytes.length == 4) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            int third = bytes[2] & 0xff;
            return first == 0
                    || first == 10
                    || first == 127
                    || first >= 224
                    || (first == 100 && second >= 64 && second <= 127)       // carrier-grade NAT
                    || (first == 169 && second == 254)                     // link-local / metadata
                    || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && second == 0 && third == 0)         // IETF special-purpose
                    || (first == 192 && second == 0 && third == 2)         // documentation
                    || (first == 192 && second == 88 && third == 99)       // deprecated 6to4 relay
                    || (first == 192 && second == 168)
                    || (first == 198 && (second == 18 || second == 19))    // benchmarking
                    || (first == 198 && second == 51 && third == 100)     // documentation
                    || (first == 203 && second == 0 && third == 113)      // documentation
                    || (first == 240);                                    // reserved
        }

        if (!(address instanceof Inet6Address) || bytes.length != 16) {
            return true;
        }

        // IPv4-mapped IPv6 addresses must be checked against the embedded IPv4 target.
        boolean mappedIpv4 = true;
        for (int i = 0; i < 10; i++) {
            mappedIpv4 &= bytes[i] == 0;
        }
        mappedIpv4 &= (bytes[10] & 0xff) == 0xff && (bytes[11] & 0xff) == 0xff;
        if (mappedIpv4) {
            try {
                return isNonPublicAddress(InetAddress.getByAddress(
                        new byte[]{bytes[12], bytes[13], bytes[14], bytes[15]}));
            } catch (UnknownHostException impossible) {
                return true;
            }
        }

        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        int third = bytes[2] & 0xff;
        int fourth = bytes[3] & 0xff;

        // Only global-unicast 2000::/3 is considered; block ULA, link-local,
        // unspecified, multicast, transition and documentation/protocol ranges.
        if ((first & 0xe0) != 0x20) {
            return true;
        }
        if (first == 0x20 && second == 0x01) {
            if ((third == 0 || third == 1)                          // 2001::/23 protocol assignments
                    || (third == 0x0d && fourth == 0xb8)) {        // 2001:db8::/32 documentation
                return true;
            }
        }
        return first == 0x20 && second == 0x02;                    // 2002::/16 6to4 transition
    }

    private static IllegalArgumentException invalidEndpoint() {
        return new IllegalArgumentException("A valid outbound HTTP(S) endpoint is required");
    }

    private static IllegalArgumentException blockedEndpoint() {
        return new IllegalArgumentException("Outbound endpoint must resolve only to public unicast addresses");
    }
}
