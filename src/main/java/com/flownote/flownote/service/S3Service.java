package com.flownote.flownote.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Service
public class S3Service {

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.access-key}")
    private String accessKey;

    @Value("${aws.s3.secret-key}")
    private String secretKey;

    @Value("${aws.s3.region}")
    private String region;

    public String uploadFile(MultipartFile file) throws IOException {
        S3Client s3 = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .build();

        String key = UUID.randomUUID() + "_" + file.getOriginalFilename();

        PutObjectRequest putObj = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(file.getContentType())
                .build();

        s3.putObject(
                putObj,
                software.amazon.awssdk.core.sync.RequestBody.fromBytes(file.getBytes())
        );

        s3.close();

        return "https://" + bucketName + ".s3." + region + ".amazonaws.com/" + key;
    }
}

