package com.slidtable.slidtab_pro.repository;

import com.slidtable.slidtab_pro.entity.Item;
import com.slidtable.slidtab_pro.enums.ItemStatus;
import com.slidtable.slidtab_pro.enums.ItemType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long> {

    Optional<Item> findByItemId(String itemId);

    List<Item> findByType(ItemType type);

    List<Item> findByStatus(ItemStatus status);

    List<Item> findByTitleContainingIgnoreCase(String keyword);

    List<Item> findByTitleContainingIgnoreCaseAndStatus(String keyword, ItemStatus status);
}
