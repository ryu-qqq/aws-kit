package com.ryuqq.aws.s3.service;

import com.ryuqq.aws.s3.types.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * S3 서비스 인터페이스 - AWS S3 핵심 기능을 단순화하여 제공
 * 
 * 한국어 설명:
 * AWS S3(Simple Storage Service)는 클라우드 기반 객체 스토리지 서비스입니다.
 * 이 인터페이스는 복잡한 AWS SDK를 추상화하여 필요한 핵심 기능만 제공합니다.
 * 
 * 주요 특징:
 * - 비동기(Async) 방식으로 동작하여 성능 최적화
 * - CompletableFuture 반환으로 논블로킹 처리 지원
 * - 멀티파트 업로드 자동 처리 (대용량 파일용)
 * - Presigned URL을 통한 보안 파일 공유
 * 
 * 사용 시나리오:
 * - 이미지, 동영상 등 미디어 파일 저장
 * - 백업 파일 보관
 * - 정적 웹사이트 호스팅
 * - CDN을 통한 전 세계 배포
 */
public interface S3Service {

    /**
     * 파일 업로드 (로컬 파일 → S3)
     * 
     * 한국어 설명:
     * 로컬 파일 시스템의 파일을 S3 버킷에 업로드합니다.
     * 파일 크기에 따라 단일 업로드 또는 멀티파트 업로드가 자동으로 선택됩니다.
     * 
     * 동작 방식:
     * - 파일 크기가 5MB 이하: 단일 PUT 요청
     * - 파일 크기가 5MB 초과: 멀티파트 업로드 (청크 단위로 분할 전송)
     * 
     * @param bucket S3 버킷 이름 (예: "my-app-files")
     * @param key S3 객체 키 (파일 경로, 예: "images/profile/user123.jpg")
     * @param file 업로드할 로컬 파일의 Path 객체
     * @return CompletableFuture<String> - 업로드된 파일의 ETag (무결성 검증용)
     * 
     * 사용 예제:
     * <pre>{@code
     * Path localFile = Paths.get("/path/to/image.jpg");
     * s3Service.uploadFile("my-bucket", "images/user/profile.jpg", localFile)
     *     .thenAccept(eTag -> log.info("업로드 완료: {}", eTag))
     *     .exceptionally(ex -> {
     *         log.error("업로드 실패", ex);
     *         return null;
     *     });
     * }
     * </pre>
     * 
     * 주의사항:
     * - 버킷 이름은 전 세계적으로 유일해야 함
     * - Key는 슬래시(/)로 폴더 구조 표현 가능
     * - 동일한 key로 업로드 시 기존 파일 덮어씀
     */
    CompletableFuture<String> uploadFile(String bucket, String key, Path file);

    /**
     * 바이트 배열 업로드 (메모리 → S3)
     * 
     * 한국어 설명:
     * 메모리상의 바이트 배열을 직접 S3에 업로드합니다.
     * 파일을 디스크에 저장하지 않고 바로 업로드할 때 유용합니다.
     * 
     * 적용 사례:
     * - 동적 생성된 이미지 (썸네일, 워터마크 등)
     * - JSON, XML 등 텍스트 데이터
     * - 압축된 데이터
     * - API로 받은 파일 데이터의 중계 업로드
     * 
     * @param bucket S3 버킷 이름
     * @param key S3 객체 키
     * @param bytes 업로드할 바이트 배열 데이터
     * @param contentType MIME 타입 (예: "image/jpeg", "application/json")
     * @return CompletableFuture<String> - 업로드된 파일의 ETag
     * 
     * 사용 예제:
     * <pre>{@code
     * byte[] jsonData = objectMapper.writeValueAsBytes(userData);
     * s3Service.uploadBytes("my-bucket", "data/user-123.json", 
     *                       jsonData, "application/json")
     *     .thenAccept(eTag -> log.info("JSON 데이터 업로드 완료: {}", eTag));
     * }
     * </pre>
     * 
     * 주의사항:
     * - 큰 파일(5MB+)의 경우 uploadFile() 사용 권장 (메모리 효율)
     * - Content-Type 정확히 설정 (브라우저 다운로드 시 중요)
     * - 바이트 배열 크기 제한 고려 (JVM 힙 메모리)
     */
    CompletableFuture<String> uploadBytes(String bucket, String key, byte[] bytes, String contentType);

    /**
     * 파일 다운로드 (S3 → 메모리)
     * 
     * 한국어 설명:
     * S3 객체를 메모리상의 바이트 배열로 다운로드합니다.
     * 작은 파일이나 즉시 처리가 필요한 데이터에 적합합니다.
     * 
     * 적용 사례:
     * - 설정 파일 로드 (JSON, XML)
     * - 작은 이미지 처리 (썸네일 생성)
     * - 텍스트 데이터 파싱
     * - API 응답으로 파일 내용 전달
     * 
     * @param bucket S3 버킷 이름
     * @param key 다운로드할 S3 객체 키
     * @return CompletableFuture<byte[]> - 파일의 바이트 배열 데이터
     * 
     * 사용 예제:
     * <pre>{@code
     * s3Service.downloadFile("my-bucket", "config/app-settings.json")
     *     .thenApply(bytes -> new String(bytes, StandardCharsets.UTF_8))
     *     .thenApply(json -> objectMapper.readValue(json, AppConfig.class))
     *     .thenAccept(config -> applicationConfigurer.apply(config))
     *     .exceptionally(ex -> {
     *         log.error("설정 파일 로드 실패", ex);
     *         return null;
     *     });
     * }
     * </pre>
     * 
     * 주의사항:
     * - 큰 파일(100MB+)은 downloadToFile() 사용 권장
     * - 메모리 부족 가능성 고려 (파일 크기 > 사용 가능한 힙 메모리)
     * - 네트워크 타임아웃 설정 확인
     * - 파일이 존재하지 않으면 NoSuchKey 예외 발생
     */
    CompletableFuture<byte[]> downloadFile(String bucket, String key);

    /**
     * 파일 다운로드 (S3 → 로컬 파일)
     * 
     * 한국어 설명:
     * S3 객체를 로컬 파일 시스템으로 직접 다운로드합니다.
     * 대용량 파일이나 스트리밍 처리에 적합합니다.
     * 
     * 장점:
     * - 메모리 효율적 (전체 파일을 메모리에 로드하지 않음)
     * - 대용량 파일 처리 가능
     * - 진행률 추적 가능
     * - 멀티파트 다운로드 자동 처리
     * 
     * 적용 사례:
     * - 백업 파일 복원
     * - 미디어 파일 다운로드
     * - 로그 파일 수집
     * - 배치 처리용 데이터 파일
     * 
     * @param bucket S3 버킷 이름
     * @param key 다운로드할 S3 객체 키
     * @param targetFile 저장할 로컬 파일 경로
     * @return CompletableFuture<Void> - 다운로드 완료 시그널
     * 
     * 사용 예제:
     * <pre>{@code
     * Path downloadPath = Paths.get("/temp/downloaded-file.zip");
     * s3Service.downloadToFile("my-bucket", "backups/daily-backup.zip", downloadPath)
     *     .thenRun(() -> {
     *         log.info("백업 파일 다운로드 완료: {}", downloadPath);
     *         // 압축 해제 또는 후속 처리
     *     })
     *     .exceptionally(ex -> {
     *         log.error("다운로드 실패", ex);
     *         return null;
     *     });
     * }
     * </pre>
     * 
     * 주의사항:
     * - 대상 디렉토리가 존재해야 함
     * - 디스크 용량 충분한지 확인
     * - 기존 파일은 덮어씀
     * - 다운로드 중 취소 시 부분 파일 남을 수 있음
     */
    CompletableFuture<Void> downloadToFile(String bucket, String key, Path targetFile);

    /**
     * 객체 삭제
     * 
     * 한국어 설명:
     * S3 버킷에서 지정된 객체를 영구적으로 삭제합니다.
     * 
     * 중요한 특징:
     * - 삭제는 즉시 실행되며 복구 불가능 (버전 관리 미활성화 시)
     * - 존재하지 않는 객체 삭제 시도는 오류 발생하지 않음
     * - 폴더 개념이 없으므로 prefix로 시작하는 모든 파일 삭제 시 반복 호출 필요
     * 
     * 적용 사례:
     * - 임시 파일 정리
     * - 사용자 데이터 삭제 (GDPR 준수)
     * - 만료된 백업 파일 제거
     * - 저장 공간 최적화
     * 
     * @param bucket S3 버킷 이름
     * @param key 삭제할 S3 객체 키
     * @return CompletableFuture<Void> - 삭제 완료 시그널
     * 
     * 사용 예제:
     * <pre>{@code
     * // 단일 파일 삭제
     * s3Service.deleteObject("my-bucket", "temp/processing-file.tmp")
     *     .thenRun(() -> log.info("임시 파일 삭제 완료"))
     *     .exceptionally(ex -> {
     *         log.error("삭제 실패", ex);
     *         return null;
     *     });
     * 
     * // 여러 파일 삭제 (폴더 전체)
     * s3Service.listObjects("my-bucket", "user-123/")
     *     .thenCompose(keys -> {
     *         List<CompletableFuture<Void>> deletions = keys.stream()
     *             .map(key -> s3Service.deleteObject("my-bucket", key))
     *             .collect(Collectors.toList());
     *         return CompletableFuture.allOf(deletions.toArray(new CompletableFuture[0]));
     *     })
     *     .thenRun(() -> log.info("사용자 데이터 전체 삭제 완료"));
     * }
     * </pre>
     * 
     * 주의사항:
     * - 삭제 후 복구 불가능 (백업 필요시 미리 준비)
     * - 버전 관리 활성화된 경우 삭제 마커만 생성
     * - 대량 삭제 시 S3 요청 한도 고려
     * - 권한 검증: DeleteObject 권한 필요
     */
    CompletableFuture<Void> deleteObject(String bucket, String key);

    /**
     * 객체 목록 조회 (prefix 기반 필터링)
     * 
     * 한국어 설명:
     * 지정된 prefix로 시작하는 모든 S3 객체의 키 목록을 조회합니다.
     * S3는 실제 폴더 구조가 없으므로 prefix를 이용해 가상 폴더를 구현합니다.
     * 
     * Prefix 동작 방식:
     * - "images/" → images/ 로 시작하는 모든 객체
     * - "user-123/profile" → 해당 패턴으로 시작하는 객체
     * - "" (빈 문자열) → 버킷의 모든 객체
     * 
     * 적용 사례:
     * - 특정 사용자의 파일 목록 조회
     * - 폴더별 파일 관리
     * - 백업 파일 목록 확인
     * - 파일 동기화 전 비교
     * 
     * @param bucket S3 버킷 이름
     * @param prefix 검색할 객체 키의 접두사 (폴더 경로 형태)
     * @return CompletableFuture<List<String>> - 조건에 맞는 객체 키 목록
     * 
     * 사용 예제:
     * <pre>{@code
     * // 특정 사용자의 모든 파일 조회
     * s3Service.listObjects("my-bucket", "users/user-123/")
     *     .thenAccept(fileKeys -> {
     *         log.info("사용자 파일 {}개 발견", fileKeys.size());
     *         fileKeys.forEach(key -> log.info("파일: {}", key));
     *     });
     * 
     * // 이미지 파일만 조회
     * s3Service.listObjects("my-bucket", "images/")
     *     .thenApply(keys -> keys.stream()
     *         .filter(key -> key.endsWith(".jpg") || key.endsWith(".png"))
     *         .collect(Collectors.toList()))
     *     .thenAccept(imageKeys -> 
     *         log.info("이미지 파일 {}개 발견", imageKeys.size()));
     * 
     * // 날짜별 로그 파일 조회
     * String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
     * s3Service.listObjects("log-bucket", "logs/" + today + "/")
     *     .thenAccept(logFiles -> processLogFiles(logFiles));
     * }
     * </pre>
     * 
     * 주의사항:
     * - 한 번에 최대 1000개 객체 반환 (더 많은 객체는 페이징 필요)
     * - 객체가 많을 경우 응답 시간 오래 걸림
     * - prefix는 대소문자 구분
     * - 슬래시(/) 포함 여부에 따라 결과 달라짐
     */
    CompletableFuture<List<String>> listObjects(String bucket, String prefix);

    /**
     * 객체 메타데이터 조회 (HEAD 요청)
     * 
     * 한국어 설명:
     * S3 객체의 메타데이터만 조회합니다. 실제 콘텐츠는 다운로드하지 않습니다.
     * 파일 존재 여부 확인, 크기 확인, 수정 시간 확인 등에 유용합니다.
     * 
     * 적용 사례:
     * - 파일 존재 여부 확인
     * - 파일 크기 확인 (다운로드 전)
     * - 최종 수정 시간 확인
     * - ETag를 통한 파일 변경 감지
     * 
     * @param bucket S3 버킷 이름
     * @param key S3 객체 키
     * @return CompletableFuture<S3Metadata> - 객체 메타데이터
     */
    CompletableFuture<S3Metadata> headObject(String bucket, String key);
    
    /**
     * 객체 복사 (서버사이드 복사)
     * 
     * 한국어 설명:
     * S3 내에서 객체를 다른 위치로 복사합니다.
     * 서버사이드 복사이므로 데이터가 클라이언트를 거치지 않아 빠르고 효율적입니다.
     * 
     * 적용 사례:
     * - 백업 생성
     * - 다른 버킷으로 데이터 이동
     * - 객체 이름 변경 (복사 후 원본 삭제)
     * - 메타데이터 업데이트 (복사하면서 변경)
     * 
     * @param sourceBucket 원본 버킷
     * @param sourceKey 원본 객체 키
     * @param destBucket 대상 버킷
     * @param destKey 대상 객체 키
     * @return CompletableFuture<String> - 복사된 객체의 ETag
     */
    CompletableFuture<String> copyObject(String sourceBucket, String sourceKey, 
                                         String destBucket, String destKey);
    
    /**
     * 객체 복사 (메타데이터 변경 포함)
     * 
     * 한국어 설명:
     * 객체를 복사하면서 메타데이터나 스토리지 클래스를 변경합니다.
     * 
     * @param sourceBucket 원본 버킷
     * @param sourceKey 원본 객체 키
     * @param destBucket 대상 버킷
     * @param destKey 대상 객체 키
     * @param newMetadata 새로운 메타데이터 (null이면 원본 유지)
     * @param storageClass 스토리지 클래스 (null이면 원본 유지)
     * @return CompletableFuture<String> - 복사된 객체의 ETag
     */
    CompletableFuture<String> copyObjectWithMetadata(String sourceBucket, String sourceKey,
                                                     String destBucket, String destKey,
                                                     Map<String, String> newMetadata,
                                                     S3StorageClass storageClass);
    
    /**
     * 여러 객체 일괄 삭제
     * 
     * 한국어 설명:
     * 여러 S3 객체를 한 번의 요청으로 삭제합니다.
     * 최대 1000개의 객체를 한 번에 삭제할 수 있습니다.
     * 
     * 적용 사례:
     * - 폴더 전체 삭제
     * - 임시 파일 일괄 정리
     * - 사용자 데이터 완전 삭제
     * 
     * @param bucket S3 버킷 이름
     * @param keys 삭제할 객체 키 목록
     * @return CompletableFuture<List<String>> - 삭제 실패한 키 목록 (성공 시 빈 리스트)
     */
    CompletableFuture<List<String>> deleteObjects(String bucket, List<String> keys);
    
    /**
     * 메타데이터와 함께 파일 업로드
     * 
     * 한국어 설명:
     * 파일을 업로드하면서 사용자 정의 메타데이터를 설정합니다.
     * 
     * @param bucket S3 버킷 이름
     * @param key S3 객체 키
     * @param file 업로드할 파일
     * @param metadata 사용자 정의 메타데이터
     * @param storageClass 스토리지 클래스 (null이면 STANDARD)
     * @return CompletableFuture<String> - 업로드된 파일의 ETag
     */
    CompletableFuture<String> uploadFileWithMetadata(String bucket, String key, Path file,
                                                     Map<String, String> metadata,
                                                     S3StorageClass storageClass);
    
    /**
     * 진행률 추적과 함께 파일 업로드
     * 
     * 한국어 설명:
     * 대용량 파일 업로드 시 진행률을 추적할 수 있습니다.
     * 
     * @param bucket S3 버킷 이름
     * @param key S3 객체 키
     * @param file 업로드할 파일
     * @param progressListener 진행률 리스너
     * @return CompletableFuture<String> - 업로드된 파일의 ETag
     */
    CompletableFuture<String> uploadFileWithProgress(String bucket, String key, Path file,
                                                     S3ProgressListener progressListener);
    
    /**
     * 진행률 추적과 함께 파일 다운로드
     * 
     * 한국어 설명:
     * 대용량 파일 다운로드 시 진행률을 추적할 수 있습니다.
     * 
     * @param bucket S3 버킷 이름
     * @param key S3 객체 키
     * @param targetFile 저장할 파일 경로
     * @param progressListener 진행률 리스너
     * @return CompletableFuture<Void> - 다운로드 완료 시그널
     */
    CompletableFuture<Void> downloadToFileWithProgress(String bucket, String key, Path targetFile,
                                                       S3ProgressListener progressListener);
    
    /**
     * 객체 태그 설정
     * 
     * 한국어 설명:
     * S3 객체에 태그를 설정합니다. 태그는 객체 분류, 라이프사이클 관리, 비용 추적에 사용됩니다.
     * 
     * @param bucket S3 버킷 이름
     * @param key S3 객체 키
     * @param tags 설정할 태그
     * @return CompletableFuture<Void> - 태그 설정 완료 시그널
     */
    CompletableFuture<Void> putObjectTags(String bucket, String key, S3Tag tags);
    
    /**
     * 객체 태그 조회
     * 
     * 한국어 설명:
     * S3 객체의 태그를 조회합니다.
     * 
     * @param bucket S3 버킷 이름
     * @param key S3 객체 키
     * @return CompletableFuture<S3Tag> - 객체 태그
     */
    CompletableFuture<S3Tag> getObjectTags(String bucket, String key);
    
    /**
     * 객체 태그 삭제
     * 
     * 한국어 설명:
     * S3 객체의 모든 태그를 삭제합니다.
     * 
     * @param bucket S3 버킷 이름
     * @param key S3 객체 키
     * @return CompletableFuture<Void> - 태그 삭제 완료 시그널
     */
    CompletableFuture<Void> deleteObjectTags(String bucket, String key);
    
    /**
     * Presigned URL 생성 (보안 다운로드 링크)
     * 
     * 한국어 설명:
     * 임시 접근 가능한 서명된 URL을 생성합니다.
     * AWS 자격 증명 없이도 제한된 시간 동안 파일에 접근할 수 있습니다.
     * 
     * Presigned URL의 특징:
     * - 지정된 시간 후 자동 만료 (보안)
     * - AWS 자격 증명 불필요 (공개 공유 가능)
     * - 특정 객체에 대해서만 접근 허용
     * - HTTPS 프로토콜 사용으로 안전한 전송
     * 
     * 적용 사례:
     * - 사용자에게 파일 다운로드 링크 제공
     * - 이메일, SMS를 통한 파일 공유
     * - 웹 애플리케이션의 프라이빗 미디어 서비스
     * - 임시 파일 공유 (24시간 제한 등)
     * 
     * @param bucket S3 버킷 이름
     * @param key 접근할 S3 객체 키
     * @param expiration URL 만료 시간 (예: Duration.ofHours(1))
     * @return CompletableFuture<String> - 생성된 presigned URL
     * 
     * 사용 예제:
     * <pre>{@code
     * // 1시간 유효한 다운로드 링크 생성
     * s3Service.generatePresignedUrl("my-bucket", "reports/monthly-report.pdf", 
     *                               Duration.ofHours(1))
     *     .thenAccept(url -> {
     *         log.info("다운로드 링크 생성: {}", url);
     *         // 사용자에게 이메일 발송
     *         emailService.sendDownloadLink(user.getEmail(), url);
     *     });
     * 
     * // 웹 컨트롤러에서 직접 리다이렉트
     * @GetMapping("/download/{fileId}")
     * public CompletableFuture<ResponseEntity<Void>> downloadFile(@PathVariable String fileId) {
     *     return s3Service.generatePresignedUrl("my-bucket", "files/" + fileId,
     *                                          Duration.ofMinutes(15))
     *         .thenApply(url -> ResponseEntity.status(HttpStatus.FOUND)
     *             .header(HttpHeaders.LOCATION, url)
     *             .build());
     * }
     * 
     * // 이미지 미리보기 URL (짧은 만료 시간)
     * s3Service.generatePresignedUrl("image-bucket", "thumbs/" + imageId,
     *                               Duration.ofMinutes(5))
     *     .thenAccept(url -> response.put("previewUrl", url));
     * }
     * </pre>
     * 
     * 보안 고려사항:
     * - 만료 시간은 최소한으로 설정 (필요한 시간만)
     * - URL이 로그에 기록되지 않도록 주의
     * - HTTPS 환경에서만 사용
     * - URL 공유 시 안전한 채널 사용
     * - 민감한 데이터의 경우 추가 인증 고려
     * 
     * 제한사항:
     * - 최대 7일까지만 유효 기간 설정 가능
     * - 생성 후 취소 불가능 (만료 시까지 유효)
     * - 브라우저 캐시에 저장될 수 있음
     */
    CompletableFuture<String> generatePresignedUrl(String bucket, String key, Duration expiration);
}