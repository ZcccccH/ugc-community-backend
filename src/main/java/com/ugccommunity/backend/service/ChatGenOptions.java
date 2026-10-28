package com.ugccommunity.backend.service;

import com.ugccommunity.backend.dto.request.SandboxChatRequest;

/**
 * Tunable parameters for one chat generation, sent by the client with the request; missing values use defaults.
 * Narration toggle, temperature, Top P, max output tokens, and max number of context messages (0 = unlimited).
 */
public final class ChatGenOptions {
    public static final ChatGenOptions DEFAULT = new ChatGenOptions(true, 0.8, 1.0, 1500, 0);

    private final boolean narration;
    private final double temperature;
    private final double topP;
    private final int maxTokens;
    private final int contextLimit;

    public ChatGenOptions(boolean narration, double temperature, double topP, int maxTokens, int contextLimit) {
        this.narration = narration;
        this.temperature = clamp(temperature, 0.0, 2.0);
        this.topP = clamp(topP, 0.05, 1.0);
        this.maxTokens = Math.max(200, Math.min(8000, maxTokens));
        this.contextLimit = Math.max(0, Math.min(200, contextLimit));
    }

    public static ChatGenOptions of(Boolean narration, Double temperature, Double topP, Integer maxTokens, Integer contextLimit) {
        return new ChatGenOptions(
                narration == null || narration,
                temperature == null ? DEFAULT.temperature : temperature,
                topP == null ? DEFAULT.topP : topP,
                maxTokens == null ? DEFAULT.maxTokens : maxTokens,
                contextLimit == null ? DEFAULT.contextLimit : contextLimit);
    }

    public static ChatGenOptions from(SandboxChatRequest r) {
        if (r == null) return DEFAULT;
        return of(r.getNarration(), r.getTemperature(), r.getTopP(), r.getMaxTokens(), r.getContextLimit());
    }

    private static double clamp(double v, double lo, double hi) {
        if (Double.isNaN(v)) return lo;
        return Math.max(lo, Math.min(hi, v));
    }

    public boolean narration() { return narration; }
    public double temperature() { return temperature; }
    public double topP() { return topP; }
    public int maxTokens() { return maxTokens; }
    public int contextLimit() { return contextLimit; }
}
