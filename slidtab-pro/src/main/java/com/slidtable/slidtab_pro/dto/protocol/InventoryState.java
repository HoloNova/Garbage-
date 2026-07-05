package com.slidtable.slidtab_pro.dto.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InventoryState(
        @JsonProperty("book_id") String bookId,
        @JsonProperty("slot_id") String slotId,
        @JsonProperty("item_state") String itemState
) {
}
