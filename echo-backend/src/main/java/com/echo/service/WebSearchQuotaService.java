package com.echo.service;

import org.springframework.stereotype.Service;

import java.util.Map;

/** Backward-compatible facade for the generic external API policy service. */
@Service
public class WebSearchQuotaService {
    private final ExternalApiPolicyService policyService;

    public WebSearchQuotaService(ExternalApiPolicyService policyService) {
        this.policyService = policyService;
    }

    public QuotaStatus check() {
        ExternalApiPolicyService.PolicyStatus status = policyService.check("search");
        return new QuotaStatus(status.enabled(), status.monthlyQuota(), status.stopPercent(), status.used(),
                status.stopAt(), status.available(), status.reason(), status.period());
    }

    public Map<String, Object> status() {
        return policyService.status("search");
    }

    public void update(boolean enabled, int monthlyQuota, int stopPercent) {
        policyService.update("search", enabled, monthlyQuota, stopPercent);
    }

    public record QuotaStatus(boolean enabled, int monthlyQuota, int stopPercent, long used,
                              long stopAt, boolean available, String reason, String month) { }
}
