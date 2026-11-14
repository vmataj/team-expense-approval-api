package com.vmataj.expense;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.*;

interface LedgerRepository extends JpaRepository<LedgerEntry, UUID> {
    List<LedgerEntry> findByOwnerAndSpentOnBetweenOrderBySpentOnDescIdDesc(String owner, LocalDate from, LocalDate to);
    Optional<LedgerEntry> findByIdAndOwner(UUID id, String owner);
}

interface LedgerBudgetRepository extends JpaRepository<LedgerBudget, UUID> {
    List<LedgerBudget> findByOwnerAndMonth(String owner, String month);
    Optional<LedgerBudget> findByOwnerAndMonthAndCategory(String owner, String month, LedgerEntry.Category category);
}

interface RecurringExpenseRepository extends JpaRepository<RecurringExpense, UUID> {
    List<RecurringExpense> findByOwnerOrderByNextDateAscIdAsc(String owner);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RecurringExpense r where r.owner = :owner and r.nextDate <= :today order by r.id")
    List<RecurringExpense> due(String owner, LocalDate today);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RecurringExpense r where r.id = :id and r.owner = :owner")
    Optional<RecurringExpense> lock(UUID id, String owner);
}
