package com.chatatda.api.housing.soco;

public interface SocoClient {

    String homeList();

    String homeDetail(String homeCode);

    String notices(int pageIndex);

    byte[] image(String path);
}
