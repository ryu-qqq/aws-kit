package com.ryuqq.aws.s3.service;

import com.ryuqq.aws.s3.service.impl.DefaultS3Service;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * S3 배치 작업 테스트
 * 
 * 한국어 설명:
 * S3의 배치 삭제 작업(deleteObjects)을 테스트합니다.
 * 여러 객체를 한 번에 삭제하는 기능과 오류 처리를 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("S3 배치 작업 테스트")
class S3BatchOperationsTest {

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
    @DisplayName("deleteObjects - 여러 객체 삭제 성공")
    void deleteObjects_ShouldDeleteAllObjects_WhenAllOperationsSucceed() {
        // Given
        String bucket = "test-bucket";
        List<String> keysToDelete = Arrays.asList(
            "folder/file1.txt",
            "folder/file2.jpg", 
            "folder/file3.pdf"
        );
        
        // 모든 삭제 작업이 성공한 경우 (errors는 빈 리스트)
        DeleteObjectsResponse response = DeleteObjectsResponse.builder()
                .errors(Collections.emptyList()) // 오류 없음
                .deleted(Arrays.asList(
                    DeletedObject.builder().key("folder/file1.txt").build(),
                    DeletedObject.builder().key("folder/file2.jpg").build(),
                    DeletedObject.builder().key("folder/file3.pdf").build()
                ))
                .build();

        when(s3AsyncClient.deleteObjects(any(DeleteObjectsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        // When
        CompletableFuture<List<String>> result = s3Service.deleteObjects(bucket, keysToDelete);
        List<String> failedKeys = result.join();

        // Then
        assertThat(failedKeys).isEmpty(); // 실패한 키가 없어야 함
        
        // 요청 검증
        ArgumentCaptor<DeleteObjectsRequest> requestCaptor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3AsyncClient).deleteObjects(requestCaptor.capture());
        
        DeleteObjectsRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.bucket()).isEqualTo(bucket);
        assertThat(capturedRequest.delete().objects()).hasSize(3);
        assertThat(capturedRequest.delete().objects())
                .extracting(ObjectIdentifier::key)
                .containsExactlyInAnyOrder("folder/file1.txt", "folder/file2.jpg", "folder/file3.pdf");
    }

    @Test
    @DisplayName("deleteObjects - 일부 객체 삭제 실패")
    void deleteObjects_ShouldReturnFailedKeys_WhenSomeOperationsFail() {
        // Given
        String bucket = "test-bucket";
        List<String> keysToDelete = Arrays.asList(
            "existing-file.txt",
            "non-existing-file.txt",
            "protected-file.txt"
        );
        
        // 일부 삭제 작업이 실패한 경우
        DeleteObjectsResponse response = DeleteObjectsResponse.builder()
                .errors(Arrays.asList(
                    S3Error.builder()
                            .key("non-existing-file.txt")
                            .code("NoSuchKey")
                            .message("The specified key does not exist.")
                            .build(),
                    S3Error.builder()
                            .key("protected-file.txt")
                            .code("AccessDenied")
                            .message("Access denied")
                            .build()
                ))
                .deleted(Arrays.asList(
                    DeletedObject.builder().key("existing-file.txt").build()
                ))
                .build();

        when(s3AsyncClient.deleteObjects(any(DeleteObjectsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        // When
        CompletableFuture<List<String>> result = s3Service.deleteObjects(bucket, keysToDelete);
        List<String> failedKeys = result.join();

        // Then
        assertThat(failedKeys).hasSize(2);
        assertThat(failedKeys).containsExactlyInAnyOrder(
            "non-existing-file.txt", 
            "protected-file.txt"
        );
    }

    @Test
    @DisplayName("deleteObjects - 빈 키 리스트 처리")
    void deleteObjects_ShouldReturnEmptyList_WhenEmptyKeyList() {
        // Given
        String bucket = "test-bucket";
        List<String> emptyKeyList = Collections.emptyList();

        // When
        CompletableFuture<List<String>> result = s3Service.deleteObjects(bucket, emptyKeyList);
        List<String> failedKeys = result.join();

        // Then
        assertThat(failedKeys).isEmpty();
        verify(s3AsyncClient, never()).deleteObjects(any(DeleteObjectsRequest.class)); // API 호출 안함
    }

    @Test
    @DisplayName("deleteObjects - 최대 1000개 객체 삭제")
    void deleteObjects_ShouldHandleMaximumObjects_WhenExactly1000Objects() {
        // Given - S3 배치 삭제 최대 한도인 1000개 객체
        String bucket = "test-bucket";
        List<String> largeKeyList = IntStream.rangeClosed(1, 1000)
                .mapToObj(i -> String.format("file-%04d.txt", i))
                .toList();
        
        DeleteObjectsResponse response = DeleteObjectsResponse.builder()
                .errors(Collections.emptyList())
                .deleted(largeKeyList.stream()
                        .map(key -> DeletedObject.builder().key(key).build())
                        .toList())
                .build();

        when(s3AsyncClient.deleteObjects(any(DeleteObjectsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        // When
        CompletableFuture<List<String>> result = s3Service.deleteObjects(bucket, largeKeyList);
        List<String> failedKeys = result.join();

        // Then
        assertThat(failedKeys).isEmpty();
        
        ArgumentCaptor<DeleteObjectsRequest> requestCaptor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3AsyncClient).deleteObjects(requestCaptor.capture());
        
        DeleteObjectsRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.delete().objects()).hasSize(1000);
    }

    @Test
    @DisplayName("deleteObjects - 전체 배치 실패 시나리오")
    void deleteObjects_ShouldReturnAllKeys_WhenEntireBatchFails() {
        // Given
        String bucket = "test-bucket";
        List<String> keysToDelete = Arrays.asList(
            "file1.txt", "file2.txt", "file3.txt"
        );
        
        // 전체 배치가 실패한 경우
        DeleteObjectsResponse response = DeleteObjectsResponse.builder()
                .errors(Arrays.asList(
                    S3Error.builder()
                            .key("file1.txt")
                            .code("InternalError")
                            .message("We encountered an internal error. Please try again.")
                            .build(),
                    S3Error.builder()
                            .key("file2.txt")
                            .code("InternalError")
                            .message("We encountered an internal error. Please try again.")
                            .build(),
                    S3Error.builder()
                            .key("file3.txt")
                            .code("InternalError")
                            .message("We encountered an internal error. Please try again.")
                            .build()
                ))
                .deleted(Collections.emptyList()) // 삭제된 객체 없음
                .build();

        when(s3AsyncClient.deleteObjects(any(DeleteObjectsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        // When
        CompletableFuture<List<String>> result = s3Service.deleteObjects(bucket, keysToDelete);
        List<String> failedKeys = result.join();

        // Then
        assertThat(failedKeys).hasSize(3);
        assertThat(failedKeys).containsExactlyInAnyOrder("file1.txt", "file2.txt", "file3.txt");
    }

    @Test
    @DisplayName("deleteObjects - 단일 객체 삭제")
    void deleteObjects_ShouldDeleteSingleObject_WhenSingleKey() {
        // Given
        String bucket = "single-bucket";
        List<String> singleKey = Arrays.asList("single-file.txt");
        
        DeleteObjectsResponse response = DeleteObjectsResponse.builder()
                .errors(Collections.emptyList())
                .deleted(Arrays.asList(
                    DeletedObject.builder().key("single-file.txt").build()
                ))
                .build();

        when(s3AsyncClient.deleteObjects(any(DeleteObjectsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        // When
        CompletableFuture<List<String>> result = s3Service.deleteObjects(bucket, singleKey);
        List<String> failedKeys = result.join();

        // Then
        assertThat(failedKeys).isEmpty();
        
        ArgumentCaptor<DeleteObjectsRequest> requestCaptor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3AsyncClient).deleteObjects(requestCaptor.capture());
        
        DeleteObjectsRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.delete().objects()).hasSize(1);
        assertThat(capturedRequest.delete().objects().get(0).key()).isEqualTo("single-file.txt");
    }

    @Test
    @DisplayName("deleteObjects - 특수 문자 포함 키 처리")
    void deleteObjects_ShouldHandleSpecialCharacters_WhenKeysContainSpecialChars() {
        // Given
        String bucket = "test-bucket";
        List<String> specialKeys = Arrays.asList(
            "files/한글파일.txt",           // 한글 파일명
            "files/file with spaces.pdf",   // 공백 포함
            "files/file+with+plus.jpg",     // 특수문자
            "files/file%20encoded.txt"      // URL 인코딩
        );
        
        DeleteObjectsResponse response = DeleteObjectsResponse.builder()
                .errors(Collections.emptyList())
                .deleted(specialKeys.stream()
                        .map(key -> DeletedObject.builder().key(key).build())
                        .toList())
                .build();

        when(s3AsyncClient.deleteObjects(any(DeleteObjectsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        // When
        CompletableFuture<List<String>> result = s3Service.deleteObjects(bucket, specialKeys);
        List<String> failedKeys = result.join();

        // Then
        assertThat(failedKeys).isEmpty();
        
        ArgumentCaptor<DeleteObjectsRequest> requestCaptor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3AsyncClient).deleteObjects(requestCaptor.capture());
        
        DeleteObjectsRequest capturedRequest = requestCaptor.getValue();
        List<String> actualKeys = capturedRequest.delete().objects().stream()
                .map(ObjectIdentifier::key)
                .toList();
        
        assertThat(actualKeys).containsExactlyInAnyOrder(
            "files/한글파일.txt",
            "files/file with spaces.pdf",
            "files/file+with+plus.jpg",
            "files/file%20encoded.txt"
        );
    }

    @Test
    @DisplayName("deleteObjects - 네트워크 오류 시 예외 전파")
    void deleteObjects_ShouldPropagateException_WhenNetworkError() {
        // Given
        String bucket = "test-bucket";
        List<String> keys = Arrays.asList("file1.txt", "file2.txt");
        
        RuntimeException networkException = new RuntimeException("Network timeout");
        
        when(s3AsyncClient.deleteObjects(any(DeleteObjectsRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(networkException));

        // When & Then
        CompletableFuture<List<String>> result = s3Service.deleteObjects(bucket, keys);
        
        assertThatThrownBy(result::join)
                .hasCauseExactlyInstanceOf(RuntimeException.class)
                .hasMessageContaining("Network timeout");
    }

    @Test
    @DisplayName("deleteObjects - 중복 키 처리")
    void deleteObjects_ShouldHandleDuplicateKeys_WhenKeysContainDuplicates() {
        // Given
        String bucket = "test-bucket";
        List<String> keysWithDuplicates = Arrays.asList(
            "file1.txt", 
            "file2.txt", 
            "file1.txt", // 중복
            "file3.txt", 
            "file2.txt"  // 중복
        );
        
        // AWS S3는 중복 키에 대해 각각 별도로 처리
        DeleteObjectsResponse response = DeleteObjectsResponse.builder()
                .errors(Collections.emptyList())
                .deleted(Arrays.asList(
                    DeletedObject.builder().key("file1.txt").build(),
                    DeletedObject.builder().key("file2.txt").build(),
                    DeletedObject.builder().key("file1.txt").build(), // 중복 삭제
                    DeletedObject.builder().key("file3.txt").build(),
                    DeletedObject.builder().key("file2.txt").build()  // 중복 삭제
                ))
                .build();

        when(s3AsyncClient.deleteObjects(any(DeleteObjectsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        // When
        CompletableFuture<List<String>> result = s3Service.deleteObjects(bucket, keysWithDuplicates);
        List<String> failedKeys = result.join();

        // Then
        assertThat(failedKeys).isEmpty();
        
        // 요청에서 모든 키(중복 포함)가 전송되는지 확인
        ArgumentCaptor<DeleteObjectsRequest> requestCaptor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3AsyncClient).deleteObjects(requestCaptor.capture());
        
        DeleteObjectsRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.delete().objects()).hasSize(5); // 중복 포함 5개
    }
}