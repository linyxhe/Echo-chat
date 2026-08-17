package com.echo.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.echo.agent.AgentToolNames;
import com.echo.mapper.AgentToolGrantMapper;
import com.echo.pojo.AgentToolGrant;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class AgentToolGrantService {
    private final AgentToolGrantMapper mapper;

    public AgentToolGrantService(AgentToolGrantMapper mapper) {
        this.mapper = mapper;
    }

    /** System assistant is deliberately restricted to the non-sensitive defaults. */
    public Set<String> enabledTools(Long assistantId) {
        if (assistantId == null) return AgentToolNames.DEFAULT_ENABLED;
        List<AgentToolGrant> grants = mapper.selectList(new QueryWrapper<AgentToolGrant>()
                .eq("assistant_id", assistantId));
        if (grants.isEmpty()) return AgentToolNames.DEFAULT_ENABLED;
        Set<String> enabled = new HashSet<>();
        for (AgentToolGrant grant : grants) {
            if (Boolean.TRUE.equals(grant.getEnabled()) && AgentToolNames.CONFIGURABLE.contains(grant.getToolName())) {
                enabled.add(grant.getToolName());
            }
        }
        return Set.copyOf(enabled);
    }

    public void replace(Long assistantId, Collection<String> requested) {
        if (assistantId == null) return;
        Set<String> enabled = requested == null ? AgentToolNames.DEFAULT_ENABLED : new HashSet<>();
        if (requested != null) {
            for (String name : requested) if (AgentToolNames.CONFIGURABLE.contains(name)) enabled.add(name);
        }
        mapper.delete(new QueryWrapper<AgentToolGrant>().eq("assistant_id", assistantId));
        LocalDateTime now = LocalDateTime.now();
        for (String name : AgentToolNames.CONFIGURABLE) {
            AgentToolGrant grant = new AgentToolGrant();
            grant.setAssistantId(assistantId);
            grant.setToolName(name);
            grant.setEnabled(enabled.contains(name));
            grant.setCreatedAt(now);
            grant.setUpdatedAt(now);
            mapper.insert(grant);
        }
    }

    public void remove(Long assistantId) {
        if (assistantId != null) mapper.delete(new QueryWrapper<AgentToolGrant>().eq("assistant_id", assistantId));
    }
}
