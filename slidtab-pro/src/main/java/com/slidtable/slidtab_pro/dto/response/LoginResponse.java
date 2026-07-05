package com.slidtable.slidtab_pro.dto.response;

import com.slidtable.slidtab_pro.enums.UserIdentity;

public record LoginResponse(
        String userId,
        String name,
        UserIdentity identity,
        String token
) {
}
