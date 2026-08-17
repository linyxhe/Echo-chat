package com.echo.agent;

import java.util.List;
import java.util.Set;

public final class AgentToolNames {
    public static final String CURRENT_TIME = "current_time";
    public static final String KNOWLEDGE_SEARCH = "knowledge_search";
    public static final String CALCULATE = "calculate";
    public static final String CONVERSATION_SEARCH = "conversation_search";
    public static final String FILE_CATALOG_SEARCH = "file_catalog_search";
    public static final String MEMORY_PROPOSE = "memory_propose";
    public static final String DRAFT_MESSAGE = "draft_message";
    public static final String WEATHER_PROPOSE = "weather_propose";
    public static final String WEB_SEARCH_PROPOSE = "web_search_propose";
    public static final String REMINDER_PROPOSE = "reminder_propose";

    /** Existing assistants retain only harmless default abilities until their owner explicitly changes them. */
    public static final Set<String> DEFAULT_ENABLED = Set.of(CURRENT_TIME, KNOWLEDGE_SEARCH, CALCULATE);
    public static final List<String> CONFIGURABLE = List.of(CURRENT_TIME, KNOWLEDGE_SEARCH, CALCULATE,
            CONVERSATION_SEARCH, FILE_CATALOG_SEARCH, MEMORY_PROPOSE, DRAFT_MESSAGE, WEATHER_PROPOSE, WEB_SEARCH_PROPOSE,
            REMINDER_PROPOSE);

    private AgentToolNames() { }
}
