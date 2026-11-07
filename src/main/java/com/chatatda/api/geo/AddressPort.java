package com.chatatda.api.geo;

import java.util.List;

public interface AddressPort {

    List<AddressResult> search(String query);
}
