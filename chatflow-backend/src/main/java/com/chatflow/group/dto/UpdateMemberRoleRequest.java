package com.chatflow.group.dto;

import com.chatflow.group.entity.GroupMemberRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateMemberRoleRequest {

    @NotNull
    private GroupMemberRole role;
}