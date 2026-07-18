package com.meis.saas.file.controller;

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

    @GetMapping("/download/{object:.+}")
    public ResponseEntity<byte[]> download(@PathVariable String object) throws Exception {
        try (var stream = minioClient.getObject(GetObjectArgs.builder().bucket(bucket).object(object).build())) {
            byte[] data = stream.readAllBytes();
            String contentType = resolveContentType(object);
            boolean inline = contentType.startsWith("image/") || "application/pdf".equals(contentType);
            String encoded = URLEncoder.encode(object, StandardCharsets.UTF_8).replace("+", "%20");
            String disposition = (inline ? "inline" : "attachment") + "; filename*=UTF-8''" + encoded;
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(data);
        }
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
