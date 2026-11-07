package com.chatatda.api.catalog;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuildingRepository extends JpaRepository<Building, Long> {

    List<Building> findAllByOrderByIdAsc();

    List<Building> findByTypeOrderByIdAsc(BuildingType type);

    long countByIdIn(Collection<Long> ids);
}
