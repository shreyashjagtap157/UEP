package com.universalplatform.security;

import java.util.Set;

public record AuthenticationAssurance(
        String acr,
        Set<String> methods,
        boolean otpEvidence,
        boolean webAuthnEvidence,
        boolean multiFactorEvidence) {

    public AuthenticationAssurance(String acr, Set<String> methods) {
        this(normalizeAcr(acr), normalizeMethods(methods),
                hasOtp(normalizeMethods(methods)),
                hasWebAuthn(normalizeMethods(methods)),
                hasMultiFactor(normalizeMethods(methods)));
    }

    public AuthenticationAssurance {
        acr = normalizeAcr(acr);
        methods = normalizeMethods(methods);
    }

    private static String normalizeAcr(String acr) {
        return acr == null ? "" : acr;
    }

    private static Set<String> normalizeMethods(Set<String> methods) {
        return methods == null ? Set.of() : Set.copyOf(methods);
    }

    private static boolean hasOtp(Set<String> methods) {
        return methods.contains("otp");
    }

    private static boolean hasWebAuthn(Set<String> methods) {
        return methods.contains("webauthn") || methods.contains("hwk") || methods.contains("swk");
    }

    private static boolean hasMultiFactor(Set<String> methods) {
        return methods.contains("mfa") || hasOtp(methods) || (methods.contains("pwd") && hasWebAuthn(methods));
    }
}
