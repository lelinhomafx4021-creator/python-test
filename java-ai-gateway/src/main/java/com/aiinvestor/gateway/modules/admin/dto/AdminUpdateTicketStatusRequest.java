package com.aiinvestor.gateway.modules.admin.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 管理端处理工单请求体。
 * <p>
 * 管理员接到人工兜底工单后，通过此请求更新工单状态并填写处理信息。
 * status 可选值：open（待处理）/ processing（处理中）/ closed（已完成）。
 */
public class AdminUpdateTicketStatusRequest {

    /** 目标工单状态：open / processing / closed。 */
    @NotBlank(message = "工单状态不能为空")
    private String status;

    /** 管理员内部处理备注（仅管理端可见）。 */
    private String processNote;

    /** 回复给用户的处理结果说明（用户可见）。 */
    private String responseMessage;

    /**
     * @return 目标工单状态
     */
    public String getStatus() {
        return status;
    }

    /**
     * @param status 目标工单状态
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * @return 管理员处理备注
     */
    public String getProcessNote() {
        return processNote;
    }

    /**
     * @param processNote 管理员处理备注
     */
    public void setProcessNote(String processNote) {
        this.processNote = processNote;
    }

    /**
     * @return 回复用户的结果说明
     */
    public String getResponseMessage() {
        return responseMessage;
    }

    /**
     * @param responseMessage 回复用户的结果说明
     */
    public void setResponseMessage(String responseMessage) {
        this.responseMessage = responseMessage;
    }
}
