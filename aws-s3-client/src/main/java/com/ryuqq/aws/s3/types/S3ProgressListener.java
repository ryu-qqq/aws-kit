package com.ryuqq.aws.s3.types;

/**
 * S3 전송 진행률 리스너
 * 
 * 한국어 설명:
 * S3 업로드/다운로드 진행 상황을 추적하기 위한 인터페이스입니다.
 * 대용량 파일 전송 시 진행률을 모니터링하고 사용자에게 피드백을 제공할 수 있습니다.
 */
@FunctionalInterface
public interface S3ProgressListener {
    
    /**
     * 진행률 업데이트 콜백
     * 
     * @param event 진행률 이벤트
     */
    void onProgress(S3ProgressEvent event);
    
    /**
     * 진행률 이벤트
     * 
     * 한국어 설명:
     * S3 전송 진행 상황을 나타내는 이벤트 객체입니다.
     */
    class S3ProgressEvent {
        private final long bytesTransferred;
        private final long totalBytes;
        private final TransferType transferType;
        private final String key;
        private final String bucket;
        
        public S3ProgressEvent(long bytesTransferred, long totalBytes, 
                              TransferType transferType, String bucket, String key) {
            this.bytesTransferred = bytesTransferred;
            this.totalBytes = totalBytes;
            this.transferType = transferType;
            this.bucket = bucket;
            this.key = key;
        }
        
        /**
         * 전송된 바이트 수
         */
        public long getBytesTransferred() {
            return bytesTransferred;
        }
        
        /**
         * 전체 바이트 수
         */
        public long getTotalBytes() {
            return totalBytes;
        }
        
        /**
         * 진행률 백분율 (0-100)
         */
        public double getProgressPercentage() {
            if (totalBytes == 0) return 0;
            return (double) bytesTransferred / totalBytes * 100;
        }
        
        /**
         * 전송 타입 (업로드/다운로드)
         */
        public TransferType getTransferType() {
            return transferType;
        }
        
        /**
         * S3 객체 키
         */
        public String getKey() {
            return key;
        }
        
        /**
         * S3 버킷 이름
         */
        public String getBucket() {
            return bucket;
        }
        
        /**
         * 남은 바이트 수
         */
        public long getRemainingBytes() {
            return totalBytes - bytesTransferred;
        }
        
        /**
         * 전송 완료 여부
         */
        public boolean isCompleted() {
            return bytesTransferred >= totalBytes;
        }
    }
    
    /**
     * 전송 타입
     */
    enum TransferType {
        UPLOAD("업로드"),
        DOWNLOAD("다운로드"),
        COPY("복사");
        
        private final String koreanName;
        
        TransferType(String koreanName) {
            this.koreanName = koreanName;
        }
        
        public String getKoreanName() {
            return koreanName;
        }
    }
    
    /**
     * 빈 리스너 (아무 동작도 하지 않음)
     */
    S3ProgressListener NOOP = event -> {};
    
    /**
     * 로깅 리스너 팩토리 메서드
     * 
     * @return 진행률을 로그로 출력하는 리스너
     */
    static S3ProgressListener loggingListener() {
        return event -> {
            if (event.getBytesTransferred() % (1024 * 1024 * 10) == 0 || event.isCompleted()) {
                System.out.printf("[%s] %s/%s 진행률: %.2f%% - %d/%d bytes%n",
                    event.getTransferType().getKoreanName(),
                    event.getBucket(),
                    event.getKey(),
                    event.getProgressPercentage(),
                    event.getBytesTransferred(),
                    event.getTotalBytes()
                );
            }
        };
    }
}