package com.slidtable.slidtab_pro.repository;

import com.slidtable.slidtab_pro.entity.BorrowRecord;
import com.slidtable.slidtab_pro.entity.Item;
import com.slidtable.slidtab_pro.entity.User;
import com.slidtable.slidtab_pro.enums.BorrowStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BorrowRecordRepository extends JpaRepository<BorrowRecord, Long> {

    List<BorrowRecord> findByUser(User user);

    List<BorrowRecord> findByStatus(BorrowStatus status);

    Optional<BorrowRecord> findByUserAndItemAndStatus(User user, Item item, BorrowStatus status);
}
