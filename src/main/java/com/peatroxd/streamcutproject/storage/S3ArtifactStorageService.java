package com.peatroxd.streamcutproject.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketConfiguration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

@Service
@ConditionalOnProperty(prefix = "app.artifact-storage", name = "mode", havingValue = "S3")
public class S3ArtifactStorageService implements ArtifactStorageService {

    private static final String SCHEME = "s3";

    private final ArtifactStorageProperties properties;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    public S3ArtifactStorageService(ArtifactStorageProperties properties) {
        this.properties = properties;
        validateProperties(properties);
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                properties.getAccessKey(),
                properties.getSecretKey()
        );
        Region region = Region.of(properties.getRegion());
        S3Configuration serviceConfiguration = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();

        this.s3Client = S3Client.builder()
                .endpointOverride(properties.getEndpoint())
                .region(region)
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(serviceConfiguration)
                .build();
        this.s3Presigner = S3Presigner.builder()
                .endpointOverride(resolvePresignEndpoint(properties))
                .region(region)
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(serviceConfiguration)
                .build();
        ensureBucketExists();
    }

    @Override
    public String storeSourceVideo(long jobId, String originalFilename, Path localArtifactPath) throws IOException {
        if (!Files.exists(localArtifactPath)) {
            throw new IOException("Local source video does not exist: " + localArtifactPath);
        }

        String key = "sources/jobs/" + jobId + "/source-video" + extension(localArtifactPath);
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(properties.getBucket())
                        .key(key)
                        .contentType("video/mp4")
                        .build(),
                RequestBody.fromFile(localArtifactPath)
        );
        return SCHEME + "://" + properties.getBucket() + "/" + key;
    }

    @Override
    public String storeCompletedExport(long jobId, long candidateId, Path localArtifactPath) throws IOException {
        if (!Files.exists(localArtifactPath)) {
            throw new IOException("Local artifact does not exist: " + localArtifactPath);
        }

        String key = "exports/jobs/" + jobId + "/candidate-" + candidateId + extension(localArtifactPath);
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(properties.getBucket())
                        .key(key)
                        .contentType("video/mp4")
                        .build(),
                RequestBody.fromFile(localArtifactPath)
        );
        return SCHEME + "://" + properties.getBucket() + "/" + key;
    }

    @Override
    public boolean exists(String reference) {
        if (reference == null || reference.isBlank()) {
            return false;
        }
        if (!isS3Reference(reference)) {
            return Files.exists(Path.of(reference).normalize());
        }
        ParsedReference parsed = parse(reference);
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(parsed.bucket())
                    .key(parsed.key())
                    .build());
            return true;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return false;
            }
            throw exception;
        } catch (RuntimeException exception) {
            String message = exception.getMessage();
            if (message != null && message.contains("Not Found")) {
                return false;
            }
            throw exception;
        }
    }

    @Override
    public void delete(String reference) throws IOException {
        if (reference == null || reference.isBlank()) {
            return;
        }
        if (!isS3Reference(reference)) {
            Files.deleteIfExists(Path.of(reference).normalize());
            return;
        }

        ParsedReference parsed = parse(reference);
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(parsed.bucket())
                    .key(parsed.key())
                    .build());
        } catch (S3Exception exception) {
            throw new IOException("Failed to delete artifact from object storage: " + reference, exception);
        }
    }

    @Override
    public Optional<Path> resolveLocalPath(String reference) {
        if (reference == null || reference.isBlank() || isS3Reference(reference)) {
            return Optional.empty();
        }
        return Optional.of(Path.of(reference).normalize());
    }

    @Override
    public Optional<URI> createSignedGetUri(String reference) {
        if (reference == null || reference.isBlank()) {
            return Optional.empty();
        }
        if (!isS3Reference(reference)) {
            return Optional.empty();
        }
        ParsedReference parsed = parse(reference);
        PresignedGetObjectRequest request = s3Presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                        .signatureDuration(resolveTtl())
                        .getObjectRequest(builder -> builder
                                .bucket(parsed.bucket())
                                .key(parsed.key())
                                .responseContentType("video/mp4")
                                .responseContentDisposition("attachment; filename=\"" + fileName(parsed.key()) + "\""))
                        .build()
        );
        return Optional.of(URI.create(request.url().toString()));
    }

    @Override
    public ArtifactResource open(String reference) throws IOException {
        if (!isS3Reference(reference)) {
            Path path = Path.of(reference).normalize();
            return new ArtifactResource(
                    Files.newInputStream(path),
                    Files.size(path),
                    Files.probeContentType(path),
                    path.getFileName().toString()
            );
        }

        ParsedReference parsed = parse(reference);
        try {
            var responseStream = s3Client.getObject(GetObjectRequest.builder()
                    .bucket(parsed.bucket())
                    .key(parsed.key())
                    .build());
            GetObjectResponse response = responseStream.response();
            return new ArtifactResource(
                    responseStream,
                    response.contentLength() == null ? -1L : response.contentLength(),
                    response.contentType(),
                    fileName(parsed.key())
            );
        } catch (S3Exception exception) {
            throw new IOException("Failed to open artifact from object storage: " + reference, exception);
        }
    }

    private void ensureBucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder()
                    .bucket(properties.getBucket())
                    .build());
        } catch (S3Exception exception) {
            if (exception.statusCode() != 404) {
                throw exception;
            }
            createBucket();
        } catch (RuntimeException exception) {
            String message = exception.getMessage();
            if (message != null && message.contains("Not Found")) {
                createBucket();
                return;
            }
            throw exception;
        }
    }

    private void createBucket() {
        CreateBucketRequest.Builder requestBuilder = CreateBucketRequest.builder()
                .bucket(properties.getBucket());
        if (!"us-east-1".equals(properties.getRegion())) {
            requestBuilder.createBucketConfiguration(
                    CreateBucketConfiguration.builder()
                            .locationConstraint(properties.getRegion())
                            .build()
            );
        }
        s3Client.createBucket(requestBuilder.build());
    }

    private Duration resolveTtl() {
        return properties.getPresignTtl() == null ? Duration.ofMinutes(15) : properties.getPresignTtl();
    }

    private static String extension(Path localArtifactPath) {
        String name = localArtifactPath.getFileName().toString();
        int dotIndex = name.lastIndexOf('.');
        return dotIndex >= 0 ? name.substring(dotIndex) : ".mp4";
    }

    private ParsedReference parse(String reference) {
        URI uri = URI.create(reference);
        if (!SCHEME.equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Unsupported artifact reference: " + reference);
        }
        String bucket = uri.getHost();
        String key = uri.getPath().startsWith("/") ? uri.getPath().substring(1) : uri.getPath();
        return new ParsedReference(bucket, key);
    }

    private record ParsedReference(String bucket, String key) {
    }

    private static URI resolvePresignEndpoint(ArtifactStorageProperties properties) {
        return properties.getPublicEndpoint() == null ? properties.getEndpoint() : properties.getPublicEndpoint();
    }

    private static boolean isS3Reference(String reference) {
        return reference.regionMatches(true, 0, SCHEME + "://", 0, (SCHEME + "://").length());
    }

    private static String fileName(String key) {
        int slashIndex = key.lastIndexOf('/');
        return slashIndex >= 0 ? key.substring(slashIndex + 1) : key;
    }

    private static void validateProperties(ArtifactStorageProperties properties) {
        if (properties.getEndpoint() == null) {
            throw new IllegalStateException("app.artifact-storage.endpoint must be configured in S3 mode");
        }
        if (properties.getPublicEndpoint() == null) {
            throw new IllegalStateException("app.artifact-storage.public-endpoint must be configured in S3 mode");
        }
        if (properties.getAccessKey() == null || properties.getAccessKey().isBlank()) {
            throw new IllegalStateException("app.artifact-storage.access-key must be configured in S3 mode");
        }
        if (properties.getSecretKey() == null || properties.getSecretKey().isBlank()) {
            throw new IllegalStateException("app.artifact-storage.secret-key must be configured in S3 mode");
        }
        if (properties.getBucket() == null || properties.getBucket().isBlank()) {
            throw new IllegalStateException("app.artifact-storage.bucket must be configured in S3 mode");
        }
    }
}
