package com.bvz.aicodegenerator.controller;

import com.bvz.aicodegenerator.annotation.AuthCheck;
import com.bvz.aicodegenerator.common.BaseResponse;
import com.bvz.aicodegenerator.common.ResultUtils;
import com.bvz.aicodegenerator.constant.UserConstant;
import com.bvz.aicodegenerator.exception.ErrorCode;
import com.bvz.aicodegenerator.exception.ThrowUtils;
import com.bvz.aicodegenerator.model.dto.chatHistory.ChatHistoryAdminQueryRequest;
import com.bvz.aicodegenerator.model.dto.chatHistory.ChatHistoryQueryRequest;
import com.bvz.aicodegenerator.model.entity.User;
import com.bvz.aicodegenerator.model.vo.ChatHistoryPageVO;
import com.bvz.aicodegenerator.service.ChatHistoryService;
import com.bvz.aicodegenerator.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对话历史 控制层
 */
@RestController
@RequestMapping("/chatHistory")
public class ChatHistoryController {

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private UserService userService;

    /**
     * 分页查询某个应用的聊天记录
     */
    @PostMapping("/app/list/page/vo")
    public BaseResponse<ChatHistoryPageVO> listAppChatHistoryByPage(
            @RequestBody ChatHistoryQueryRequest chatHistoryQueryRequest,
            HttpServletRequest request
    ) {
        ThrowUtils.throwIf(chatHistoryQueryRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        ChatHistoryPageVO result = chatHistoryService.listAppChatHistoryByPage(chatHistoryQueryRequest, loginUser);
        return ResultUtils.success(result);
    }

    /**
     * 管理员查询所有应用的聊天记录
     */
    @PostMapping("/admin/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<ChatHistoryPageVO> listChatHistoryVOByPageByAdmin(
            @RequestBody ChatHistoryAdminQueryRequest chatHistoryAdminQueryRequest
    ) {
        ThrowUtils.throwIf(chatHistoryAdminQueryRequest == null, ErrorCode.PARAMS_ERROR);
        ChatHistoryPageVO result = chatHistoryService.listChatHistoryVOByPageByAdmin(chatHistoryAdminQueryRequest);
        return ResultUtils.success(result);
    }
}
