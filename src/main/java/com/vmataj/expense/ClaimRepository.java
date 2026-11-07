package com.vmataj.expense;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

interface ClaimRepository extends JpaRepository<Claim, Long> {
    List<Claim> findByEmployeeOrderByIdDesc(String employee);
    List<Claim> findAllByOrderByIdDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Claim c where c.id = :id")
    Optional<Claim> lockById(long id);
}
