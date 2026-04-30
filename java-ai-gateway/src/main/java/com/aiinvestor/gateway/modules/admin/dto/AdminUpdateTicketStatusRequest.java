package com.aiinvestor.gateway.modules.admin.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 管理端处理工单请求。
 */
public class AdminUpdateTicketStatusRequest {

    /** 新状态。 */
    @NotBlank(message = "工单状态不能为空")
    private String status;

    /** 管理员处理备注。 */
    private String processNote;

    /** 回复给用户的处理结果。 */
    private String responseMessage;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getProcessNote() {
        return processNote;
    }

    public void setProcessNote(String processNote) {
        this.processNote = processNote;
    }

    public String getResponseMessage() {
        return responseMessage;
    }

    public void setResponseMessage(String responseMessage) {
        this.responseMessage = responseMessage;
    }
}
