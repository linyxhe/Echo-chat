import Uppy from "@uppy/core";
import Tus from "@uppy/tus";
import request from "@/util/request";

export const SMALL_FILE_LIMIT = 10 * 1024 * 1024;

/**
 * 使用一个独立 Uppy 实例上传单个聊天文件。
 * Tus 会保存上传 URL 指纹，因此刷新或网络中断后再次选择同一文件可继续传输。
 * receiverId（好友）与 groupId（群）二选一。
 */
export const uploadChatFileWithTus = async ({ file, receiverId, groupId, onProgress, onIntent }) => {
  const intentResponse = await request.post("/files/chat/upload-intents", {
    ...(receiverId != null ? { receiverId } : {}),
    ...(groupId != null ? { groupId } : {}),
    fileName: file.name,
    contentType: file.type || "application/octet-stream",
    size: file.size,
  });
  if (intentResponse.code !== 200) {
    throw new Error(intentResponse.message || "创建大文件上传任务失败");
  }

  const intent = intentResponse.data;
  // 上传一开始就通知调用方（拿到 fileId 落本地任务，刷新后可恢复）
  if (typeof onIntent === "function") {
    try {
      onIntent(intent);
    } catch (e) {
      console.warn("onIntent 回调失败", e);
    }
  }
  const uppy = new Uppy({
    autoProceed: false,
    // 实际最大值由后端上传意图返回，避免前端、tusd 与后端出现三处不一致。
    restrictions: { maxNumberOfFiles: 1, maxFileSize: intent.maxFileSize || Number.MAX_SAFE_INTEGER },
    meta: {
      fileId: intent.fileId,
      uploadToken: intent.uploadToken,
    },
  });
  uppy.use(Tus, {
    endpoint: intent.tusEndpoint,
    chunkSize: 5 * 1024 * 1024,
    removeFingerprintOnSuccess: true,
    storeFingerprintForResuming: true,
    allowedMetaFields: ["fileId", "uploadToken", "name", "type"],
  });

  const uppyFileId = uppy.addFile({
    name: file.name,
    type: file.type || "application/octet-stream",
    data: file,
  });
  uppy.on("upload-progress", (uploadedFile, progress) => {
    if (uploadedFile.id !== uppyFileId || typeof onProgress !== "function") return;
    const total = progress.bytesTotal || file.size;
    onProgress(total ? Math.min(100, Math.round((progress.bytesUploaded / total) * 100)) : 0);
  });

  try {
    const result = await uppy.upload();
    if (result.failed?.length) {
      const failure = result.failed[0]?.error;
      throw failure instanceof Error ? failure : new Error("大文件上传失败");
    }
    if (!result.successful?.length) throw new Error("大文件上传未完成");

    // 完成确认会在后端读取完整文件并计算 SHA-256；大文件不能沿用全局 60 秒请求超时。
    const completed = await request.post(`/files/${intent.fileId}/complete`, null, {
      timeout: 30 * 60 * 1000,
    });
    if (completed.code !== 200) throw new Error(completed.message || "文件完成校验失败");
    return completed.data;
  } finally {
    // Uppy 5 使用 destroy() 释放插件与事件；清理失败不能覆盖已经完成的上传结果。
    try {
      uppy.destroy();
    } catch (error) {
      console.warn("释放 Tus 上传实例失败", error);
    }
  }
};
