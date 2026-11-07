package com.chatatda.api.realdeal;

import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RealDealRepository extends JpaRepository<RealDeal, Long> {

    @Query("""
            select d from RealDeal d
            where d.dong in :dongs
              and d.contractYm between :from and :to
              and d.areaM2 between :minArea and :maxArea
              and d.rent > 0
              and d.contractType = '신규'
            """)
    List<RealDeal> findSampleDeals(@Param("dongs") Collection<String> dongs,
                                   @Param("from") YearMonth from, @Param("to") YearMonth to,
                                   @Param("minArea") double minArea, @Param("maxArea") double maxArea);

    @Modifying
    @Query("delete from RealDeal d where d.lawdCd = :lawdCd and d.buildingType = :type and d.contractYm = :ym")
    int deleteBatch(@Param("lawdCd") String lawdCd, @Param("type") RealDealType type, @Param("ym") YearMonth ym);
}
