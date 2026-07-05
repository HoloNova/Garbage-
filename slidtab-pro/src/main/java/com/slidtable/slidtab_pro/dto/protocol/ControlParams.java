package com.slidtable.slidtab_pro.dto.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ControlParams(
        @JsonProperty("cabinet_id") String cabinetId,
        @JsonProperty("slot_id") String slotId,
        @JsonProperty("book_id") String bookId,
        @JsonProperty("item_id") String itemId,
        @JsonProperty("action") String action,
        @JsonProperty("id") Integer id,
        @JsonProperty("x") Integer x,
        @JsonProperty("y") Integer y,
        @JsonProperty("test") String test,
        @JsonProperty("cmd") Integer cmd
) {
}
