package com.meis.saas.file.controller;

import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.result.Result;
import io.minio.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileController {
    private final MinioClient minioClient;

    @Value("${meis.minio.bucket:meis}")
    private String bucket;

    @PostMapping("/upload")
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file) throws Exception {
        ensureBucket();
        String object = UUID.randomUUID() + "_" + file.getOriginalFilename();
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(object)
                .stream(file.getInputStream(), file.getSize(), -1)
                .contentType(file.getContentType())
                .build());
        return Result.ok(Map.of("object", object, "url", "/api/file/download/" + object));
    }

    /**
     * AST-ARCH-01：MinIO 物理复制（新 object），用于设备档案复制到目标设备。
     */
    @PostMapping("/copy")
    public Result<Map<String, String>> copy(@RequestBody Map<String, String> body) throws Exception {
        ensureBucket();
        String sourceObject = resolveObject(body);
        if (sourceObject == null || sourceObject.isBlank()) {
            throw new BizException(400, "源文件不能为空");
        }
        String baseName = sourceObject;
        int us = sourceObject.indexOf('_');
        String suffix = us > 0 ? sourceObject.substring(us) : ("_" + sourceObject);
        String destObject = UUID.randomUUID() + suffix;
        minioClient.copyObject(CopyObjectArgs.builder()
                .bucket(bucket)
                .object(destObject)
                .source(CopySource.builder().bucket(bucket).object(sourceObject).build())
                .build());
        return Result.ok(Map.of("object", destObject, "url", "/api/file/download/" + destObject));
    }

    @GetMapping("/download/{object:.+}")
    public ResponseEntity<byte[]> download(
            @PathVariable String object,
            @RequestParam(value = "filename", required = false) String filename,
            @RequestParam(value = "disposition", required = false) String dispositionMode) throws Exception {
        try (var stream = minioClient.getObject(GetObjectArgs.builder().bucket(bucket).object(object).build())) {
            byte[] data = stream.readAllBytes();
            String contentType = resolveContentType(object);
            boolean forceAttachment = "attachment".equalsIgnoreCase(dispositionMode);
            boolean inline = !forceAttachment && (contentType.startsWith("image/") || "application/pdf".equals(contentType));
            String displayName = (filename != null && !filename.isBlank()) ? filename.trim() : object;
            int us = displayName.indexOf('_');
            if ((filename == null || filename.isBlank()) && us > 0 && us < displayName.length() - 1) {
                // UUID_原名.jpg → 原名.jpg
                String maybe = displayName.substring(us + 1);
                if (maybe.contains(".")) displayName = maybe;
            }
            String encoded = URLEncoder.encode(displayName, StandardCharsets.UTF_8).replace("+", "%20");
            String disposition = (inline ? "inline" : "attachment") + "; filename*=UTF-8''" + encoded;
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(data);
        }
    }

    private static String resolveObject(Map<String, String> body) {
        if (body == null) return null;
        String object = body.get("object");
        if (object != null && !object.isBlank()) return object.trim();
        String url = body.get("url");
        if (url == null || url.isBlank()) return null;
        String u = url.trim();
        String marker = "/api/file/download/";
        int i = u.indexOf(marker);
        if (i >= 0) return u.substring(i + marker.length());
        int slash = Math.max(u.lastIndexOf('/'), u.lastIndexOf('\\'));
        return slash >= 0 ? u.substring(slash + 1) : u;
    }

    private static String resolveContentType(String object) {
        String lower = object == null ? "" : object.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".bmp")) return "image/bmp";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".doc")) return "application/msword";
        if (lower.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    private void ensureBucket() throws Exception {
        boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!found) minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
    }
}
