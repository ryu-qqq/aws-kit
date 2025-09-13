package com.ryuqq.aws.s3.types;

/**
 * S3 스토리지 클래스
 * 
 * 한국어 설명:
 * S3 객체의 스토리지 클래스를 정의합니다.
 * 각 클래스는 접근 빈도와 비용 최적화 요구사항에 따라 선택됩니다.
 */
public enum S3StorageClass {
    
    /**
     * 표준 스토리지
     * - 자주 접근하는 데이터용
     * - 최고 성능, 최저 지연 시간
     * - 가장 높은 비용
     */
    STANDARD("STANDARD", "표준", "자주 접근하는 데이터"),
    
    /**
     * 표준 IA (Infrequent Access)
     * - 30일 이상 보관, 덜 자주 접근
     * - 표준보다 저렴한 스토리지 비용
     * - 검색 시 추가 비용
     */
    STANDARD_IA("STANDARD_IA", "표준 IA", "덜 자주 접근하는 데이터"),
    
    /**
     * 단일 영역 IA
     * - 하나의 가용 영역에만 저장
     * - 표준 IA보다 20% 저렴
     * - 가용 영역 장애 시 데이터 손실 위험
     */
    ONEZONE_IA("ONEZONE_IA", "단일 영역 IA", "중요도 낮은 재생성 가능 데이터"),
    
    /**
     * Intelligent-Tiering
     * - 접근 패턴에 따라 자동으로 계층 이동
     * - 모니터링 및 자동화 비용 발생
     * - 예측 불가능한 접근 패턴에 적합
     */
    INTELLIGENT_TIERING("INTELLIGENT_TIERING", "지능형 계층화", "접근 패턴이 변하는 데이터"),
    
    /**
     * Glacier Instant Retrieval
     * - 90일 이상 보관
     * - 밀리초 단위 검색
     * - 아카이브 데이터 즉시 접근 필요 시
     */
    GLACIER_IR("GLACIER_IR", "Glacier 즉시 검색", "아카이브 데이터 즉시 접근"),
    
    /**
     * Glacier Flexible Retrieval
     * - 90일 이상 보관
     * - 1-12시간 검색 시간
     * - 백업 및 재해 복구용
     */
    GLACIER("GLACIER", "Glacier 유연한 검색", "백업 및 재해 복구"),
    
    /**
     * Glacier Deep Archive
     * - 180일 이상 보관
     * - 12-48시간 검색 시간
     * - 장기 보관 규정 준수용
     */
    DEEP_ARCHIVE("DEEP_ARCHIVE", "Glacier 딥 아카이브", "장기 보관 규정 준수");
    
    private final String value;
    private final String koreanName;
    private final String useCase;
    
    S3StorageClass(String value, String koreanName, String useCase) {
        this.value = value;
        this.koreanName = koreanName;
        this.useCase = useCase;
    }
    
    /**
     * AWS SDK에서 사용하는 값
     */
    public String getValue() {
        return value;
    }
    
    /**
     * 한국어 이름
     */
    public String getKoreanName() {
        return koreanName;
    }
    
    /**
     * 사용 사례
     */
    public String getUseCase() {
        return useCase;
    }
    
    /**
     * 즉시 접근 가능 여부
     */
    public boolean isInstantAccess() {
        return this == STANDARD || 
               this == STANDARD_IA || 
               this == ONEZONE_IA || 
               this == INTELLIGENT_TIERING ||
               this == GLACIER_IR;
    }
    
    /**
     * 아카이브 스토리지 여부
     */
    public boolean isArchive() {
        return this == GLACIER || 
               this == GLACIER_IR || 
               this == DEEP_ARCHIVE;
    }
    
    /**
     * 최소 보관 기간 (일)
     */
    public int getMinimumStorageDays() {
        switch (this) {
            case STANDARD:
            case INTELLIGENT_TIERING:
                return 0;
            case STANDARD_IA:
            case ONEZONE_IA:
                return 30;
            case GLACIER_IR:
            case GLACIER:
                return 90;
            case DEEP_ARCHIVE:
                return 180;
            default:
                return 0;
        }
    }
    
    /**
     * 문자열 값으로부터 enum 변환
     * 
     * @param value AWS SDK 값
     * @return S3StorageClass
     */
    public static S3StorageClass fromValue(String value) {
        for (S3StorageClass storageClass : values()) {
            if (storageClass.value.equals(value)) {
                return storageClass;
            }
        }
        throw new IllegalArgumentException("Unknown storage class: " + value);
    }
    
    /**
     * AWS SDK StorageClass로 변환
     */
    public software.amazon.awssdk.services.s3.model.StorageClass toAwsStorageClass() {
        return software.amazon.awssdk.services.s3.model.StorageClass.fromValue(this.value);
    }
}