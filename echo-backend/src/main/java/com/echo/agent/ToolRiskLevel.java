package com.echo.agent;

/**
 * Tool risk is evaluated on the server. The model never gets to lower it.
 */
public enum ToolRiskLevel {
    READ_ONLY,
    SENSITIVE_READ,
    WRITE_CONFIRM,
    EXTERNAL_CONFIRM
}
