package com.sankalp.fairexit.service.storage;

import com.sankalp.fairexit.config.StorageProperties;
import java.net.URL;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/** The only class that talks to the object store. */
@Service
public class ObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(ObjectStorage.class);

    private final S3Client s3;
    private final S3Presigner presigner;
    private final StorageProperties properties;

    public ObjectStorage(S3Client s3, S3Presigner presigner, StorageProperties properties) {
        this.s3 = s3;
        this.presigner = presigner;
        this.properties = properties;
    }

    /**
     * A URL the browser can PUT one file to. Content type and length are part of the
     * signature, so the client cannot upload a different kind or size of file with it.
     */
    public URL presignUpload(String key, String contentType, long sizeBytes) {
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(properties.bucket()).key(key)
                .contentType(contentType).contentLength(sizeBytes)
                .build();
        return presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(properties.urlTtl()).putObjectRequest(put).build()).url();
    }

    public URL presignDownload(String key) {
        return presignDownload(key, properties.urlTtl());
    }

    /** S3 signatures allow at most 7 days, which is exactly what evidence pack links need. */
    public URL presignDownload(String key, Duration ttl) {
        GetObjectRequest get = GetObjectRequest.builder().bucket(properties.bucket()).key(key).build();
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(ttl).getObjectRequest(get).build()).url();
    }

    /** Size of the object in bytes, or empty when it does not exist. */
    public Optional<Long> size(String key) {
        try {
            return Optional.of(s3.headObject(HeadObjectRequest.builder()
                    .bucket(properties.bucket()).key(key).build()).contentLength());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    /** The whole object. Files are at most 10 MB, so reading into memory is fine. */
    public Optional<byte[]> read(String key) {
        try {
            return Optional.of(s3.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(properties.bucket()).key(key).build()).asByteArray());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    public void write(String key, byte[] bytes, String contentType) {
        s3.putObject(PutObjectRequest.builder().bucket(properties.bucket()).key(key).contentType(contentType).build(),
                RequestBody.fromBytes(bytes));
    }

    /** Throws on failure, so a job can retry. Deleting a key that is already gone succeeds. */
    public void delete(String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket()).key(key).build());
    }

    /** Best effort: a leftover staging file is harmless. */
    public void deleteQuietly(String key) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket()).key(key).build());
        } catch (RuntimeException e) {
            log.warn("Could not delete {}: {}", key, e.getMessage());
        }
    }

    public void ensureBucket() {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(properties.bucket()).build());
        } catch (S3Exception e) {
            if (e.statusCode() != 404) {
                throw e;
            }
            s3.createBucket(CreateBucketRequest.builder().bucket(properties.bucket()).build());
            log.info("Created bucket {}", properties.bucket());
        }
    }

    public Duration urlTtl() {
        return properties.urlTtl();
    }

    public long maxUploadBytes() {
        return properties.maxUploadBytes();
    }
}
