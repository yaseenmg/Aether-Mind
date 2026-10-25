package com.yaseen.aicore.agent;

import com.google.gson.JsonObject;

/** Provider-neutral tool call emitted by the reasoning model. */
public record AgentToolCall(String id, String name, JsonObject arguments) {}
