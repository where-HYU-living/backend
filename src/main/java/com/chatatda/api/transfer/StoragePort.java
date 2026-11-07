package com.chatatda.api.transfer;

/** 업로드 파일 저장소. 로컬은 디스크, 배포에서는 S3 호환 저장소로 바꿀 수 있다. */
public interface StoragePort {

    /** 저장하고 브라우저가 바로 쓸 수 있는 전체 주소를 돌려준다. */
    String save(byte[] bytes, String extension);

    /** 이 저장소가 돌려준 주소인가 */
    boolean owns(String url);
}
