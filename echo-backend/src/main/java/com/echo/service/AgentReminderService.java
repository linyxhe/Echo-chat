package com.echo.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.echo.mapper.AgentReminderMapper;
import com.echo.pojo.AgentConfirmation;
import com.echo.pojo.AgentReminder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Persists user-confirmed reminders and delivers them through the existing durable notification channel. */
@Service
public class AgentReminderService {
    private static final Logger log = LoggerFactory.getLogger(AgentReminderService.class);
    private final AgentReminderMapper reminderMapper;
    private final NotificationService notificationService;

    public AgentReminderService(AgentReminderMapper reminderMapper, NotificationService notificationService) {
        this.reminderMapper = reminderMapper;
        this.notificationService = notificationService;
    }

    public AgentReminder create(AgentConfirmation confirmation, String content, LocalDateTime scheduledAt, LocalDateTime now) {
        AgentReminder reminder = new AgentReminder();
        reminder.setUserId(confirmation.getUserId());
        reminder.setAssistantId(confirmation.getAssistantId());
        reminder.setContent(content);
        reminder.setScheduledAt(scheduledAt);
        reminder.setStatus("PENDING");
        reminder.setSourceConfirmationId(confirmation.getId());
        reminder.setCreatedAt(now);
        reminderMapper.insert(reminder);
        return reminder;
    }

    public List<Map<String, Object>> list(Long userId) {
        if (userId == null) return List.of();
        return reminderMapper.selectList(new QueryWrapper<AgentReminder>().eq("user_id", userId)
                        .orderByDesc("scheduled_at").last("LIMIT 100"))
                .stream().map(reminder -> Map.<String, Object>of(
                        "id", reminder.getId(), "assistantId", reminder.getAssistantId() == null ? 0L : reminder.getAssistantId(),
                        "content", reminder.getContent(), "status", reminder.getStatus(),
                        "scheduledAt", reminder.getScheduledAt().toString(),
                        "createdAt", reminder.getCreatedAt().toString())).toList();
    }

    public boolean cancel(Long userId, Long reminderId) {
        if (userId == null || reminderId == null) return false;
        return reminderMapper.update(null, new UpdateWrapper<AgentReminder>().eq("id", reminderId).eq("user_id", userId)
                .eq("status", "PENDING").set("status", "CANCELLED")) == 1;
    }

    /** The conditional update makes delivery idempotent even when multiple nodes run the scheduler. */
    @Scheduled(fixedDelayString = "${app.ai.agent.reminder-sweep-interval-ms:5000}", initialDelayString = "${app.ai.agent.reminder-sweep-initial-delay-ms:5000}")
    public void deliverDueReminders() {
        LocalDateTime now = LocalDateTime.now();
        try {
            List<AgentReminder> due = reminderMapper.selectList(new QueryWrapper<AgentReminder>().eq("status", "PENDING")
                    .le("scheduled_at", now).orderByAsc("scheduled_at").last("LIMIT 50"));
            for (AgentReminder reminder : due) {
                int claimed = reminderMapper.update(null, new UpdateWrapper<AgentReminder>().eq("id", reminder.getId())
                        .eq("status", "PENDING").set("status", "FIRED").set("fired_at", now));
                if (claimed == 1) {
                    boolean persisted = notificationService.notify(reminder.getUserId(), "AI_REMINDER", "提醒事项", reminder.getContent(), reminder.getId());
                    if (!persisted) {
                        // Keep it retryable when the notification table is
                        // temporarily unavailable. The conditional update
                        // prevents duplicate delivery across scheduler nodes.
                        reminderMapper.update(null, new UpdateWrapper<AgentReminder>().eq("id", reminder.getId())
                                .eq("status", "FIRED").set("status", "PENDING").set("fired_at", null));
                    } else {
                        log.info("AI reminder fired: reminderId={}, userId={}, scheduledAt={}",
                                reminder.getId(), reminder.getUserId(), reminder.getScheduledAt());
                    }
                }
            }
        } catch (Exception e) {
            // A scheduler exception must not disable subsequent sweeps.
            log.error("Reminder sweep failed; will retry on the next interval", e);
        }
    }
}
