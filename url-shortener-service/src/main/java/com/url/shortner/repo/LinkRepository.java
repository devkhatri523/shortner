package com.url.shortner.repo;

import com.url.shortner.dto.Link;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface LinkRepository extends JpaRepository<Link, String> {

    /** Single DB update prevents lost click increments under concurrency. */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("update Link l set l.clicks = l.clicks + 1 where l.code = :code")
    int incrementClicks(@Param("code") String code);
}
