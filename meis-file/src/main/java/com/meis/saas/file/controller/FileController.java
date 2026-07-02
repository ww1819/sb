package com.meis.saas.file.controller;

import com.meis.saas.common.result.Result;
import io.minio.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @GetMapping("/download/{object}")
    public org.springframework.http.ResponseEntity<byte[]> download(@PathVariable String object) throws Exception {
        try (var stream = minioClient.getObject(GetObjectArgs.builder().bucket(bucket).object(object).build())) {
            byte[] data = stream.readAllBytes();
            return org.springframework.http.ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=" + object)
                    .body(data);
        }
    }

    private void ensureBucket() throws Exception {
        boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!found) minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
    }
}
