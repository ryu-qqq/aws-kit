package com.ryuqq.aws.s3.types;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.services.s3.model.StorageClass;

import static org.assertj.core.api.Assertions.*;

/**
 * S3 스토리지 클래스 테스트
 * 
 * 한국어 설명:
 * S3StorageClass enum의 기능을 테스트합니다.
 * 각 스토리지 클래스의 속성, 변환 메서드, 비즈니스 로직을 검증합니다.
 */
@DisplayName("S3 스토리지 클래스 테스트")
class S3StorageClassTest {

    @Test
    @DisplayName("모든 스토리지 클래스의 기본 속성 검증")
    void allStorageClasses_ShouldHaveValidProperties_WhenAccessingBasicAttributes() {
        // Given & When & Then
        assertThat(S3StorageClass.STANDARD.getValue()).isEqualTo("STANDARD");
        assertThat(S3StorageClass.STANDARD.getKoreanName()).isEqualTo("표준");
        assertThat(S3StorageClass.STANDARD.getUseCase()).isEqualTo("자주 접근하는 데이터");

        assertThat(S3StorageClass.STANDARD_IA.getValue()).isEqualTo("STANDARD_IA");
        assertThat(S3StorageClass.STANDARD_IA.getKoreanName()).isEqualTo("표준 IA");
        assertThat(S3StorageClass.STANDARD_IA.getUseCase()).isEqualTo("덜 자주 접근하는 데이터");

        assertThat(S3StorageClass.ONEZONE_IA.getValue()).isEqualTo("ONEZONE_IA");
        assertThat(S3StorageClass.ONEZONE_IA.getKoreanName()).isEqualTo("단일 영역 IA");
        assertThat(S3StorageClass.ONEZONE_IA.getUseCase()).isEqualTo("중요도 낮은 재생성 가능 데이터");

        assertThat(S3StorageClass.INTELLIGENT_TIERING.getValue()).isEqualTo("INTELLIGENT_TIERING");
        assertThat(S3StorageClass.INTELLIGENT_TIERING.getKoreanName()).isEqualTo("지능형 계층화");
        assertThat(S3StorageClass.INTELLIGENT_TIERING.getUseCase()).isEqualTo("접근 패턴이 변하는 데이터");

        assertThat(S3StorageClass.GLACIER_IR.getValue()).isEqualTo("GLACIER_IR");
        assertThat(S3StorageClass.GLACIER_IR.getKoreanName()).isEqualTo("Glacier 즉시 검색");
        assertThat(S3StorageClass.GLACIER_IR.getUseCase()).isEqualTo("아카이브 데이터 즉시 접근");

        assertThat(S3StorageClass.GLACIER.getValue()).isEqualTo("GLACIER");
        assertThat(S3StorageClass.GLACIER.getKoreanName()).isEqualTo("Glacier 유연한 검색");
        assertThat(S3StorageClass.GLACIER.getUseCase()).isEqualTo("백업 및 재해 복구");

        assertThat(S3StorageClass.DEEP_ARCHIVE.getValue()).isEqualTo("DEEP_ARCHIVE");
        assertThat(S3StorageClass.DEEP_ARCHIVE.getKoreanName()).isEqualTo("Glacier 딥 아카이브");
        assertThat(S3StorageClass.DEEP_ARCHIVE.getUseCase()).isEqualTo("장기 보관 규정 준수");
    }

    @Test
    @DisplayName("즉시 접근 가능한 스토리지 클래스 검증")
    void instantAccessStorageClasses_ShouldReturnTrue_WhenInstantAccessChecked() {
        // Given
        S3StorageClass[] instantAccessClasses = {
                S3StorageClass.STANDARD,
                S3StorageClass.STANDARD_IA,
                S3StorageClass.ONEZONE_IA,
                S3StorageClass.INTELLIGENT_TIERING,
                S3StorageClass.GLACIER_IR
        };

        // When & Then
        for (S3StorageClass storageClass : instantAccessClasses) {
            assertThat(storageClass.isInstantAccess())
                    .as("스토리지 클래스 %s는 즉시 접근 가능해야 함", storageClass.getKoreanName())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("즉시 접근 불가능한 스토리지 클래스 검증")
    void nonInstantAccessStorageClasses_ShouldReturnFalse_WhenInstantAccessChecked() {
        // Given
        S3StorageClass[] nonInstantAccessClasses = {
                S3StorageClass.GLACIER,
                S3StorageClass.DEEP_ARCHIVE
        };

        // When & Then
        for (S3StorageClass storageClass : nonInstantAccessClasses) {
            assertThat(storageClass.isInstantAccess())
                    .as("스토리지 클래스 %s는 즉시 접근 불가능해야 함", storageClass.getKoreanName())
                    .isFalse();
        }
    }

    @Test
    @DisplayName("아카이브 스토리지 클래스 검증")
    void archiveStorageClasses_ShouldReturnTrue_WhenArchiveChecked() {
        // Given
        S3StorageClass[] archiveClasses = {
                S3StorageClass.GLACIER,
                S3StorageClass.GLACIER_IR,
                S3StorageClass.DEEP_ARCHIVE
        };

        // When & Then
        for (S3StorageClass storageClass : archiveClasses) {
            assertThat(storageClass.isArchive())
                    .as("스토리지 클래스 %s는 아카이브여야 함", storageClass.getKoreanName())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("비아카이브 스토리지 클래스 검증")
    void nonArchiveStorageClasses_ShouldReturnFalse_WhenArchiveChecked() {
        // Given
        S3StorageClass[] nonArchiveClasses = {
                S3StorageClass.STANDARD,
                S3StorageClass.STANDARD_IA,
                S3StorageClass.ONEZONE_IA,
                S3StorageClass.INTELLIGENT_TIERING
        };

        // When & Then
        for (S3StorageClass storageClass : nonArchiveClasses) {
            assertThat(storageClass.isArchive())
                    .as("스토리지 클래스 %s는 비아카이브여야 함", storageClass.getKoreanName())
                    .isFalse();
        }
    }

    @Test
    @DisplayName("최소 보관 기간 검증")
    void minimumStorageDays_ShouldReturnCorrectDays_WhenChecked() {
        // Given & When & Then
        assertThat(S3StorageClass.STANDARD.getMinimumStorageDays()).isEqualTo(0);
        assertThat(S3StorageClass.INTELLIGENT_TIERING.getMinimumStorageDays()).isEqualTo(0);
        
        assertThat(S3StorageClass.STANDARD_IA.getMinimumStorageDays()).isEqualTo(30);
        assertThat(S3StorageClass.ONEZONE_IA.getMinimumStorageDays()).isEqualTo(30);
        
        assertThat(S3StorageClass.GLACIER_IR.getMinimumStorageDays()).isEqualTo(90);
        assertThat(S3StorageClass.GLACIER.getMinimumStorageDays()).isEqualTo(90);
        
        assertThat(S3StorageClass.DEEP_ARCHIVE.getMinimumStorageDays()).isEqualTo(180);
    }

    @ParameterizedTest
    @ValueSource(strings = {"STANDARD", "STANDARD_IA", "ONEZONE_IA", "INTELLIGENT_TIERING", 
                           "GLACIER_IR", "GLACIER", "DEEP_ARCHIVE"})
    @DisplayName("문자열 값으로부터 enum 변환 성공")
    void fromValue_ShouldReturnCorrectEnum_WhenValidValueProvided(String value) {
        // When
        S3StorageClass storageClass = S3StorageClass.fromValue(value);

        // Then
        assertThat(storageClass).isNotNull();
        assertThat(storageClass.getValue()).isEqualTo(value);
    }

    @Test
    @DisplayName("잘못된 문자열 값으로부터 enum 변환 실패")
    void fromValue_ShouldThrowException_WhenInvalidValueProvided() {
        // Given
        String invalidValue = "INVALID_STORAGE_CLASS";

        // When & Then
        assertThatThrownBy(() -> S3StorageClass.fromValue(invalidValue))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown storage class: " + invalidValue);
    }

    @Test
    @DisplayName("null 값으로부터 enum 변환 실패")
    void fromValue_ShouldThrowException_WhenNullValueProvided() {
        // When & Then
        assertThatThrownBy(() -> S3StorageClass.fromValue(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown storage class: null");
    }

    @ParameterizedTest
    @EnumSource(S3StorageClass.class)
    @DisplayName("AWS SDK StorageClass 변환 검증")
    void toAwsStorageClass_ShouldReturnValidAwsStorageClass_WhenCalled(S3StorageClass s3StorageClass) {
        // When
        StorageClass awsStorageClass = s3StorageClass.toAwsStorageClass();

        // Then
        assertThat(awsStorageClass).isNotNull();
        assertThat(awsStorageClass.toString()).isEqualTo(s3StorageClass.getValue());
    }

    @Test
    @DisplayName("비즈니스 로직: 비용 효율적인 스토리지 클래스 선택")
    void businessLogic_ShouldRecommendAppropriateStorageClass_BasedOnAccessPattern() {
        // Given - 다양한 사용 시나리오
        
        // 자주 접근하는 프로덕션 데이터
        S3StorageClass frequentAccess = S3StorageClass.STANDARD;
        assertThat(frequentAccess.isInstantAccess()).isTrue();
        assertThat(frequentAccess.getMinimumStorageDays()).isEqualTo(0);
        
        // 월 1-2회 접근하는 백업 데이터
        S3StorageClass infrequentAccess = S3StorageClass.STANDARD_IA;
        assertThat(infrequentAccess.isInstantAccess()).isTrue();
        assertThat(infrequentAccess.getMinimumStorageDays()).isEqualTo(30);
        
        // 예측 불가능한 접근 패턴
        S3StorageClass unpredictableAccess = S3StorageClass.INTELLIGENT_TIERING;
        assertThat(unpredictableAccess.isInstantAccess()).isTrue();
        assertThat(unpredictableAccess.getMinimumStorageDays()).isEqualTo(0);
        
        // 장기 백업 (연 1-2회 접근)
        S3StorageClass longTermBackup = S3StorageClass.GLACIER;
        assertThat(longTermBackup.isArchive()).isTrue();
        assertThat(longTermBackup.getMinimumStorageDays()).isEqualTo(90);
        
        // 규정 준수용 장기 보관 (거의 접근 안함)
        S3StorageClass compliance = S3StorageClass.DEEP_ARCHIVE;
        assertThat(compliance.isArchive()).isTrue();
        assertThat(compliance.getMinimumStorageDays()).isEqualTo(180);
    }

    @Test
    @DisplayName("스토리지 클래스 전환 시나리오 검증")
    void storageClassTransition_ShouldFollowLifecyclePolicies_WhenDataAges() {
        // Given - 데이터 라이프사이클 시뮬레이션
        
        // 신규 데이터: STANDARD
        S3StorageClass newData = S3StorageClass.STANDARD;
        assertThat(newData.isInstantAccess()).isTrue();
        assertThat(newData.getMinimumStorageDays()).isEqualTo(0);
        
        // 30일 후: STANDARD_IA로 전환 가능
        S3StorageClass after30Days = S3StorageClass.STANDARD_IA;
        assertThat(after30Days.getMinimumStorageDays()).isEqualTo(30);
        assertThat(after30Days.isInstantAccess()).isTrue();
        
        // 90일 후: GLACIER로 전환 가능
        S3StorageClass after90Days = S3StorageClass.GLACIER;
        assertThat(after90Days.getMinimumStorageDays()).isEqualTo(90);
        assertThat(after90Days.isArchive()).isTrue();
        
        // 180일 후: DEEP_ARCHIVE로 전환 가능 (최종 단계)
        S3StorageClass after180Days = S3StorageClass.DEEP_ARCHIVE;
        assertThat(after180Days.getMinimumStorageDays()).isEqualTo(180);
        assertThat(after180Days.isArchive()).isTrue();
    }

    @Test
    @DisplayName("특수 스토리지 클래스 특성 검증")
    void specialStorageClasses_ShouldHaveUniqueCharacteristics_WhenEvaluated() {
        // Given & When & Then
        
        // ONEZONE_IA: 단일 AZ 저장으로 저렴하지만 내구성 낮음
        S3StorageClass onezoneIa = S3StorageClass.ONEZONE_IA;
        assertThat(onezoneIa.getUseCase()).contains("중요도 낮은");
        assertThat(onezoneIa.isInstantAccess()).isTrue();
        assertThat(onezoneIa.getMinimumStorageDays()).isEqualTo(30);
        
        // INTELLIGENT_TIERING: 자동 계층화
        S3StorageClass intelligentTiering = S3StorageClass.INTELLIGENT_TIERING;
        assertThat(intelligentTiering.getUseCase()).contains("접근 패턴이 변하는");
        assertThat(intelligentTiering.isInstantAccess()).isTrue();
        assertThat(intelligentTiering.getMinimumStorageDays()).isEqualTo(0);
        
        // GLACIER_IR: 아카이브이지만 즉시 검색 가능
        S3StorageClass glacierIr = S3StorageClass.GLACIER_IR;
        assertThat(glacierIr.isArchive()).isTrue();
        assertThat(glacierIr.isInstantAccess()).isTrue(); // 독특한 특성
        assertThat(glacierIr.getUseCase()).contains("즉시 접근");
    }

    @Test
    @DisplayName("enum 값 개수와 완전성 검증")
    void enumValues_ShouldContainAllExpectedStorageClasses_WhenCounted() {
        // Given
        S3StorageClass[] allValues = S3StorageClass.values();

        // When & Then
        assertThat(allValues).hasSize(7);
        assertThat(allValues).containsExactlyInAnyOrder(
                S3StorageClass.STANDARD,
                S3StorageClass.STANDARD_IA,
                S3StorageClass.ONEZONE_IA,
                S3StorageClass.INTELLIGENT_TIERING,
                S3StorageClass.GLACIER_IR,
                S3StorageClass.GLACIER,
                S3StorageClass.DEEP_ARCHIVE
        );
    }

    @Test
    @DisplayName("대소문자 구분 문자열 변환 검증")
    void fromValue_ShouldBeCaseSensitive_WhenConvertingFromString() {
        // Given
        String lowerCaseValue = "standard";
        String mixedCaseValue = "Standard_Ia";

        // When & Then - 대소문자 정확히 일치해야 함
        assertThatThrownBy(() -> S3StorageClass.fromValue(lowerCaseValue))
                .isInstanceOf(IllegalArgumentException.class);
        
        assertThatThrownBy(() -> S3StorageClass.fromValue(mixedCaseValue))
                .isInstanceOf(IllegalArgumentException.class);
        
        // 정확한 대소문자는 성공
        assertThatNoException().isThrownBy(() -> S3StorageClass.fromValue("STANDARD"));
        assertThatNoException().isThrownBy(() -> S3StorageClass.fromValue("STANDARD_IA"));
    }
}