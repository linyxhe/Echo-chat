package com.echo.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.echo.ai.BotUserService;
import com.echo.mapper.SystemConfigMapper;
import com.echo.pojo.SystemConfig;
import com.echo.pojo.KbDocument;
import com.echo.service.AiAssistantService;
import com.echo.service.KbIndexWorker;
import com.echo.service.KbService;
import com.echo.service.UserService;
import com.echo.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 助手相关 REST API。默认需登录（SecurityConfig 的 anyRequest().authenticated()）。
 */
@RestController
@RequestMapping("/ai")
public class AiController {

    @Autowired
    private BotUserService botUserService;

    @Autowired
    private SystemConfigMapper systemConfigMapper;

    @Autowired
    private AiAssistantService aiAssistantService;

    @Autowired
    private UserService userService;

    @Autowired
    private KbService kbService;

    @Autowired
    private KbIndexWorker kbIndexWorker;

    /** 返回 AI 助手身份信息（botUserId / botNickname / botAvatar / enabled），前端据此注入合成会话。 */
    @GetMapping("/bot-info")
    public Result<Object> botInfo() {
        Map<String, Object> data = new HashMap<>();
        data.put("botUserId", botUserService.getBotUserId());
        data.put("botNickname", botUserService.getBotNickname());
        data.put("botAvatar", botUserService.getBotAvatar());
        data.put("enabled", isAiEnabled());
        return Result.success(data);
    }

    /** 当前用户创建的 AI 助手列表。 */
    @GetMapping("/assistants")
    public Result<Object> assistants() {
        Long uid = getCurrentUserId();
        return uid == null ? Result.fail("未登录") : Result.success(aiAssistantService.listMine(uid));
    }

    /** 已就绪知识库分类，供创建助手时选择。 */
    @GetMapping("/assistants/categories")
    public Result<Object> categories() {
        return Result.success(aiAssistantService.listKnowledgeCategories());
    }

    /** 创建用户自定义 AI 助手，并返回绑定的 BOT 用户信息。 */
    @PostMapping("/assistants")
    public Result<Object> createAssistant(@RequestBody Map<String, Object> body) {
        Long uid = getCurrentUserId();
        if (uid == null) return Result.fail("未登录");
        String name = body == null || body.get("name") == null ? null : String.valueOf(body.get("name"));
        String type = body == null || body.get("assistantType") == null ? null : String.valueOf(body.get("assistantType"));
        String persona = body == null || body.get("persona") == null ? null : String.valueOf(body.get("persona"));
        List<String> categories = new ArrayList<>();
        Object categoryList = body == null ? null : body.get("knowledgeCategories");
        if (categoryList instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item != null) categories.add(String.valueOf(item));
            }
        }
        Object legacyCategory = body == null ? null : body.get("knowledgeCategory");
        if (categories.isEmpty() && legacyCategory != null) categories.add(String.valueOf(legacyCategory));
        String operations = body == null || body.get("defaultOperations") == null ? null : String.valueOf(body.get("defaultOperations"));
        return aiAssistantService.create(uid, name, type, persona, categories, operations);
    }

    /** 彻底删除用户自建 AI 助手及其聊天记录、私有知识库。系统助手不支持删除。 */
    @DeleteMapping("/assistants/{assistantId}")
    public Result<Object> deleteAssistant(@PathVariable Long assistantId) {
        Long uid = getCurrentUserId();
        if (uid == null) return Result.fail("未登录");
        return aiAssistantService.deleteOwned(uid, assistantId)
                ? Result.success("AI 助手及其资料已彻底删除")
                : Result.fail("AI 助手不存在或无权删除");
    }

    @PutMapping("/assistants/{assistantId}/remark")
    public Result<Object> updateAssistantRemark(@PathVariable Long assistantId,
                                                 @RequestBody Map<String, Object> body) {
        Long uid = getCurrentUserId();
        if (uid == null) return Result.fail("未登录");
        String remark = body == null || body.get("remark") == null ? null : String.valueOf(body.get("remark"));
        return aiAssistantService.updateRemark(uid, assistantId, remark);
    }

    @GetMapping("/assistants/{assistantId}/knowledge")
    public Result<Object> privateKnowledge(@PathVariable Long assistantId) {
        Long uid = getCurrentUserId();
        if (uid == null) return Result.fail("未登录");
        if (aiAssistantService.findOwnedById(uid, assistantId) == null) return Result.fail("无权访问该 AI 助手");
        return Result.success(kbService.listPrivateDocuments(uid, assistantId));
    }

    @PostMapping("/assistants/{assistantId}/knowledge")
    public Result<Object> uploadPrivateKnowledge(@PathVariable Long assistantId,
                                                  @RequestParam("file") MultipartFile file) {
        Long uid = getCurrentUserId();
        if (uid == null) return Result.fail("未登录");
        if (aiAssistantService.findOwnedById(uid, assistantId) == null) return Result.fail("无权访问该 AI 助手");
        if (file == null || file.isEmpty()) return Result.fail("文件为空");
        String filename = file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename();
        try {
            byte[] bytes = file.getBytes();
            KbDocument doc = kbService.submitPrivateUpload(filename, file.getContentType(), bytes, uid, assistantId);
            kbIndexWorker.indexDocument(doc.getId(), bytes, filename, file.getContentType());
            return Result.success("已提交索引，完成后将仅供该助手使用");
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            return Result.fail("读取文件失败：" + e.getMessage());
        }
    }

    @DeleteMapping("/assistants/{assistantId}/knowledge/{documentId}")
    public Result<Object> deletePrivateKnowledge(@PathVariable Long assistantId, @PathVariable Long documentId) {
        Long uid = getCurrentUserId();
        if (uid == null) return Result.fail("未登录");
        if (aiAssistantService.findOwnedById(uid, assistantId) == null) return Result.fail("无权访问该 AI 助手");
        return kbService.deletePrivateDocument(uid, assistantId, documentId)
                ? Result.success("已删除") : Result.fail("私有文档不存在");
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || "anonymousUser".equals(auth.getName())) return null;
        com.echo.pojo.User user = userService.findByUsername(auth.getName());
        return user == null ? null : user.getId();
    }

    private boolean isAiEnabled() {
        SystemConfig cfg = systemConfigMapper.selectOne(
                new QueryWrapper<SystemConfig>().eq("config_key", "ai.enabled"));
        return cfg == null || !"false".equalsIgnoreCase(cfg.getConfigValue());
    }
}
