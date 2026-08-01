package com.meis.saas.common.asset;

import java.util.UUID;

/**
 * 台账归属区间写回（AST-OWN-01）。由 meis-asset 提供实现；
 * meis-common 审批自动过账等场景通过 ObjectProvider 可选调用。
 */
public interface DeviceOwnershipWriteback {

    /**
     * 闭合当前已确认开放段，并从 medical_device 快照新开已确认归属段。
     */
    void openPeriodFromLedger(UUID deviceId, String changeReason, String sourceMode,
                              String sourceBizType, UUID sourceBizId, String sourceBizNo);
}
