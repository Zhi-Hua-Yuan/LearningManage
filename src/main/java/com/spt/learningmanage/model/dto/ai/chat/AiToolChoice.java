package com.spt.learningmanage.model.dto.ai.chat;

public record AiToolChoice(
        Mode mode,
        String functionName
) {

    public enum Mode {
        AUTO,
        NONE,
        FUNCTION
    }

    public static AiToolChoice auto() {
        return new AiToolChoice(Mode.AUTO, null);
    }

    public static AiToolChoice none() {
        return new AiToolChoice(Mode.NONE, null);
    }

    public static AiToolChoice function(String functionName) {
        return new AiToolChoice(Mode.FUNCTION, functionName);
    }
}
