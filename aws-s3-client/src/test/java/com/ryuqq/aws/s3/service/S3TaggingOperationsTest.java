package com.ryuqq.aws.s3.service;

import com.ryuqq.aws.s3.service.impl.DefaultS3Service;
import com.ryuqq.aws.s3.types.S3Tag;
import com.ryuqq.aws.s3.properties.S3Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * S3 태그 작업 테스트
 * 
 * 한국어 설명:
 * S3 객체 태그 관리 기능을 테스트합니다.
 * 태그 설정(putObjectTags), 조회(getObjectTags), 삭제(deleteObjectTags) 기능을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("S3 태그 작업 테스트")
class S3TaggingOperationsTest {

    @Mock
    private S3AsyncClient s3AsyncClient;
    
    @Mock
    private S3TransferManager transferManager;
    
    @Mock
    private S3Presigner s3Presigner;
    
    @Mock
    private S3Properties s3Properties;

    private DefaultS3Service s3Service;

    @BeforeEach
    void setUp() {
        s3Service = new DefaultS3Service(s3AsyncClient, transferManager, s3Presigner, s3Properties);
    }

    @Test
    @DisplayName("putObjectTags - 객체에 태그 설정 성공")
    void putObjectTags_ShouldSetTags_WhenValidTagsProvided() {
        // Given
        String bucket = "test-bucket";
        String key = "documents/report.pdf";
        
        S3Tag tags = S3Tag.builder()
                .tags(Map.of(
                        "Department", "Finance",
                        "Project", "Q4Report",
                        "Owner", "john.doe@company.com",
                        "Environment", "production"
                ))
                .build();

        PutObjectTaggingResponse response = PutObjectTaggingResponse.builder().build();
        when(s3AsyncClient.putObjectTagging(any(PutObjectTaggingRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        // When
        CompletableFuture<Void> result = s3Service.putObjectTags(bucket, key, tags);

        // Then
        assertThat(result.join()).isNull(); // Void 반환
        
        ArgumentCaptor<PutObjectTaggingRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectTaggingRequest.class);
        verify(s3AsyncClient).putObjectTagging(requestCaptor.capture());
        
        PutObjectTaggingRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.bucket()).isEqualTo(bucket);
        assertThat(capturedRequest.key()).isEqualTo(key);
        
        java.util.List<Tag> awsTags = capturedRequest.tagging().tagSet();
        assertThat(awsTags).hasSize(4);
        
        // AWS SDK Tag로 변환된 내용 검증
        Map<String, String> tagMap = new HashMap<>();
        awsTags.forEach(tag -> tagMap.put(tag.key(), tag.value()));
        
        assertThat(tagMap).containsEntry("Department", "Finance");
        assertThat(tagMap).containsEntry("Project", "Q4Report");
        assertThat(tagMap).containsEntry("Owner", "john.doe@company.com");
        assertThat(tagMap).containsEntry("Environment", "production");
    }

    @Test
    @DisplayName("putObjectTags - 한글 태그 설정")
    void putObjectTags_ShouldHandleKoreanTags_WhenKoreanCharactersUsed() {
        // Given
        String bucket = "한글-버킷";
        String key = "문서/보고서.pdf";
        
        S3Tag koreanTags = S3Tag.builder()
                .tags(Map.of(
                        "부서", "개발팀",
                        "프로젝트명", "AWS키트개발",
                        "담당자", "홍길동",
                        "상태", "완료"
                ))
                .build();

        PutObjectTaggingResponse response = PutObjectTaggingResponse.builder().build();
        when(s3AsyncClient.putObjectTagging(any(PutObjectTaggingRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        // When
        CompletableFuture<Void> result = s3Service.putObjectTags(bucket, key, koreanTags);

        // Then
        assertThat(result.join()).isNull();
        
        ArgumentCaptor<PutObjectTaggingRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectTaggingRequest.class);
        verify(s3AsyncClient).putObjectTagging(requestCaptor.capture());
        
        PutObjectTaggingRequest capturedRequest = requestCaptor.getValue();
        java.util.List<Tag> awsTags = capturedRequest.tagging().tagSet();
        
        Map<String, String> tagMap = new HashMap<>();
        awsTags.forEach(tag -> tagMap.put(tag.key(), tag.value()));
        
        assertThat(tagMap).containsEntry("부서", "개발팀");
        assertThat(tagMap).containsEntry("프로젝트명", "AWS키트개발");
        assertThat(tagMap).containsEntry("담당자", "홍길동");
        assertThat(tagMap).containsEntry("상태", "완료");
    }

    @Test
    @DisplayName("getObjectTags - 객체 태그 조회 성공")
    void getObjectTags_ShouldReturnTags_WhenObjectHasTags() {
        // Given
        String bucket = "test-bucket";
        String key = "images/photo.jpg";
        
        Set<Tag> awsTags = Set.of(
                Tag.builder().key("Category").value("Photography").build(),
                Tag.builder().key("Location").value("Seoul").build(),
                Tag.builder().key("Year").value("2024").build(),
                Tag.builder().key("Quality").value("High").build()
        );

        GetObjectTaggingResponse response = GetObjectTaggingResponse.builder()
                .tagSet(awsTags)
                .build();

        when(s3AsyncClient.getObjectTagging(any(GetObjectTaggingRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        // When
        CompletableFuture<S3Tag> result = s3Service.getObjectTags(bucket, key);
        S3Tag retrievedTags = result.join();

        // Then
        assertThat(retrievedTags).isNotNull();
        assertThat(retrievedTags.getTagCount()).isEqualTo(4);
        assertThat(retrievedTags.getTagValue("Category")).isEqualTo("Photography");
        assertThat(retrievedTags.getTagValue("Location")).isEqualTo("Seoul");
        assertThat(retrievedTags.getTagValue("Year")).isEqualTo("2024");
        assertThat(retrievedTags.getTagValue("Quality")).isEqualTo("High");
        
        assertThat(retrievedTags.getTagKeys()).containsExactlyInAnyOrder(
            "Category", "Location", "Year", "Quality");
        
        verify(s3AsyncClient).getObjectTagging(argThat((GetObjectTaggingRequest request) ->
            request.bucket().equals(bucket) && request.key().equals(key)));
    }

    @Test
    @DisplayName("getObjectTags - 태그가 없는 객체 조회")
    void getObjectTags_ShouldReturnEmptyTags_WhenObjectHasNoTags() {
        // Given
        String bucket = "test-bucket";
        String key = "files/no-tags.txt";

        GetObjectTaggingResponse response = GetObjectTaggingResponse.builder()
                .tagSet(Set.of()) // 빈 태그 세트
                .build();

        when(s3AsyncClient.getObjectTagging(any(GetObjectTaggingRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        // When
        CompletableFuture<S3Tag> result = s3Service.getObjectTags(bucket, key);
        S3Tag retrievedTags = result.join();

        // Then
        assertThat(retrievedTags).isNotNull();
        assertThat(retrievedTags.getTagCount()).isEqualTo(0);
        assertThat(retrievedTags.tags()).isEmpty();
        assertThat(retrievedTags.getTagKeys()).isEmpty();
    }

    @Test
    @DisplayName("deleteObjectTags - 객체 태그 삭제 성공")
    void deleteObjectTags_ShouldDeleteTags_WhenCalled() {
        // Given
        String bucket = "test-bucket";
        String key = "data/old-file.csv";

        DeleteObjectTaggingResponse response = DeleteObjectTaggingResponse.builder().build();
        when(s3AsyncClient.deleteObjectTagging(any(DeleteObjectTaggingRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        // When
        CompletableFuture<Void> result = s3Service.deleteObjectTags(bucket, key);

        // Then
        assertThat(result.join()).isNull(); // Void 반환
        
        verify(s3AsyncClient).deleteObjectTagging(argThat((DeleteObjectTaggingRequest request) ->
            request.bucket().equals(bucket) && request.key().equals(key)));
    }

    @Test
    @DisplayName("putObjectTags - 빈 태그 처리")
    void putObjectTags_ShouldHandleEmptyTags_WhenEmptyTagsProvided() {
        // Given
        String bucket = "test-bucket";
        String key = "files/empty-tags.txt";
        
        S3Tag emptyTags = S3Tag.builder().build(); // 빈 태그

        PutObjectTaggingResponse response = PutObjectTaggingResponse.builder().build();
        when(s3AsyncClient.putObjectTagging(any(PutObjectTaggingRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        // When
        CompletableFuture<Void> result = s3Service.putObjectTags(bucket, key, emptyTags);

        // Then
        assertThat(result.join()).isNull();
        
        ArgumentCaptor<PutObjectTaggingRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectTaggingRequest.class);
        verify(s3AsyncClient).putObjectTagging(requestCaptor.capture());
        
        PutObjectTaggingRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.tagging().tagSet()).isEmpty();
    }

    @Test
    @DisplayName("putObjectTags - 최대 태그 개수 제한 테스트")
    void putObjectTags_ShouldAcceptMaximumTags_WhenTenTagsProvided() {
        // Given
        String bucket = "test-bucket";
        String key = "files/max-tags.txt";
        
        Map<String, String> maxTags = new HashMap<>();
        for (int i = 1; i <= 10; i++) {
            maxTags.put("Key" + i, "Value" + i);
        }
        
        S3Tag tags = S3Tag.builder().tags(maxTags).build();

        PutObjectTaggingResponse response = PutObjectTaggingResponse.builder().build();
        when(s3AsyncClient.putObjectTagging(any(PutObjectTaggingRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        // When
        CompletableFuture<Void> result = s3Service.putObjectTags(bucket, key, tags);

        // Then
        assertThat(result.join()).isNull();
        
        ArgumentCaptor<PutObjectTaggingRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectTaggingRequest.class);
        verify(s3AsyncClient).putObjectTagging(requestCaptor.capture());
        
        PutObjectTaggingRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.tagging().tagSet()).hasSize(10);
    }

    @Test
    @DisplayName("getObjectTags - 존재하지 않는 객체 조회 시 예외 전파")
    void getObjectTags_ShouldPropagateException_WhenObjectNotFound() {
        // Given
        String bucket = "test-bucket";
        String key = "non-existent/file.txt";
        
        NoSuchKeyException exception = NoSuchKeyException.builder()
                .message("The specified key does not exist.")
                .build();

        when(s3AsyncClient.getObjectTagging(any(GetObjectTaggingRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(exception));

        // When & Then
        CompletableFuture<S3Tag> result = s3Service.getObjectTags(bucket, key);
        
        assertThatThrownBy(result::join)
                .hasCauseInstanceOf(NoSuchKeyException.class)
                .hasMessageContaining("The specified key does not exist");
    }

    @Test
    @DisplayName("S3Tag 빌더 메서드 체이닝 테스트")
    void s3Tag_ShouldSupportMethodChaining_WhenBuilderMethodsUsed() {
        // Given & When
        S3Tag tag = S3Tag.builder()
                .build()
                .withEnvironment("production")
                .withProject("ecommerce")
                .withOwner("backend-team")
                .withCostCenter("COST-2024-001")
                .addTag("BackupEnabled", "true")
                .addTag("RetentionPeriod", "30days");

        // Then
        assertThat(tag.getTagCount()).isEqualTo(6);
        assertThat(tag.getTagValue("Environment")).isEqualTo("production");
        assertThat(tag.getTagValue("Project")).isEqualTo("ecommerce");
        assertThat(tag.getTagValue("Owner")).isEqualTo("backend-team");
        assertThat(tag.getTagValue("CostCenter")).isEqualTo("COST-2024-001");
        assertThat(tag.getTagValue("BackupEnabled")).isEqualTo("true");
        assertThat(tag.getTagValue("RetentionPeriod")).isEqualTo("30days");
    }

    @Test
    @DisplayName("S3Tag 태그 제거 기능 테스트")
    void s3Tag_ShouldRemoveTags_WhenRemoveTagCalled() {
        // Given
        S3Tag tag = S3Tag.builder()
                .tags(Map.of(
                        "Env", "dev",
                        "Project", "test",
                        "TempTag", "remove-me"
                ))
                .build();

        assertThat(tag.getTagCount()).isEqualTo(3);

        // When
        S3Tag updatedTag = tag.removeTag("TempTag");

        // Then
        assertThat(updatedTag.getTagCount()).isEqualTo(2);
        assertThat(updatedTag.getTagValue("Env")).isEqualTo("dev");
        assertThat(updatedTag.getTagValue("Project")).isEqualTo("test");
        assertThat(updatedTag.getTagValue("TempTag")).isNull();
        assertThat(updatedTag.getTagKeys()).containsExactlyInAnyOrder("Env", "Project");
    }

    @Test
    @DisplayName("S3Tag 여러 태그 추가 기능 테스트")
    void s3Tag_ShouldAddMultipleTags_WhenAddTagsMapCalled() {
        // Given
        S3Tag tag = S3Tag.builder()
                .tags(Map.of("InitialTag", "value"))
                .build();

        Map<String, String> additionalTags = Map.of(
                "NewTag1", "value1",
                "NewTag2", "value2",
                "NewTag3", "value3"
        );

        // When
        S3Tag updatedTag = tag.addTags(additionalTags);

        // Then
        assertThat(updatedTag.getTagCount()).isEqualTo(4);
        assertThat(updatedTag.getTagValue("InitialTag")).isEqualTo("value");
        assertThat(updatedTag.getTagValue("NewTag1")).isEqualTo("value1");
        assertThat(updatedTag.getTagValue("NewTag2")).isEqualTo("value2");
        assertThat(updatedTag.getTagValue("NewTag3")).isEqualTo("value3");
    }

    @Test
    @DisplayName("태그 작업 전체 플로우 테스트")
    void tagOperationsFlow_ShouldWorkTogether_WhenCalledInSequence() {
        // Given
        String bucket = "flow-test-bucket";
        String key = "flow-test/document.pdf";

        // PUT operation setup
        PutObjectTaggingResponse putResponse = PutObjectTaggingResponse.builder().build();
        when(s3AsyncClient.putObjectTagging(any(PutObjectTaggingRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(putResponse));

        // GET operation setup
        Set<Tag> awsTags = Set.of(
                Tag.builder().key("Status").value("Draft").build(),
                Tag.builder().key("Version").value("1.0").build()
        );
        GetObjectTaggingResponse getResponse = GetObjectTaggingResponse.builder()
                .tagSet(awsTags)
                .build();
        when(s3AsyncClient.getObjectTagging(any(GetObjectTaggingRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(getResponse));

        // DELETE operation setup  
        DeleteObjectTaggingResponse deleteResponse = DeleteObjectTaggingResponse.builder().build();
        when(s3AsyncClient.deleteObjectTagging(any(DeleteObjectTaggingRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(deleteResponse));

        // When & Then
        // 1. 태그 설정
        S3Tag initialTags = S3Tag.builder()
                .tags(Map.of("Status", "Draft", "Version", "1.0"))
                .build();
        
        CompletableFuture<Void> putResult = s3Service.putObjectTags(bucket, key, initialTags);
        assertThat(putResult.join()).isNull();

        // 2. 태그 조회
        CompletableFuture<S3Tag> getResult = s3Service.getObjectTags(bucket, key);
        S3Tag retrievedTags = getResult.join();
        
        assertThat(retrievedTags.getTagCount()).isEqualTo(2);
        assertThat(retrievedTags.getTagValue("Status")).isEqualTo("Draft");
        assertThat(retrievedTags.getTagValue("Version")).isEqualTo("1.0");

        // 3. 태그 삭제
        CompletableFuture<Void> deleteResult = s3Service.deleteObjectTags(bucket, key);
        assertThat(deleteResult.join()).isNull();

        // 모든 API 호출 검증
        verify(s3AsyncClient).putObjectTagging(any(PutObjectTaggingRequest.class));
        verify(s3AsyncClient).getObjectTagging(any(GetObjectTaggingRequest.class));
        verify(s3AsyncClient).deleteObjectTagging(any(DeleteObjectTaggingRequest.class));
    }
}