package com.chatatda.api.favorite;

import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    boolean existsByUserIdAndTypeAndTargetId(long userId, FavoriteType type, long targetId);

    List<Favorite> findByUserIdOrderByCreatedAtDescIdDesc(long userId);

    List<Favorite> findByUserIdAndTypeOrderByCreatedAtDescIdDesc(long userId, FavoriteType type);

    @Query("select f.targetId from Favorite f where f.userId = :userId and f.type = :type")
    Set<Long> findTargetIds(long userId, FavoriteType type);

    @Modifying
    @Query("delete from Favorite f where f.userId = :userId and f.type = :type and f.targetId = :targetId")
    int deleteOne(long userId, FavoriteType type, long targetId);
}
