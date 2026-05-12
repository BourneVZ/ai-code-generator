package com.bvz.aicodegenerator.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.bvz.aicodegenerator.ai.AiCodeGenTypeRoutingService;
import com.bvz.aicodegenerator.ai.AiCodeGenTypeRoutingServiceFactory;
import com.bvz.aicodegenerator.ai.model.message.AiResponseMessage;
import com.bvz.aicodegenerator.constant.AppConstant;
import com.bvz.aicodegenerator.core.AiCodeGeneratorFacade;
import com.bvz.aicodegenerator.core.builder.VueProjectBuilder;
import com.bvz.aicodegenerator.core.handler.StreamHandlerExecutor;
import com.bvz.aicodegenerator.exception.BusinessException;
import com.bvz.aicodegenerator.exception.ErrorCode;
import com.bvz.aicodegenerator.exception.ThrowUtils;
import com.bvz.aicodegenerator.mapper.AppMapper;
import com.bvz.aicodegenerator.model.dto.app.AppAddRequest;
import com.bvz.aicodegenerator.model.dto.app.AppQueryRequest;
import com.bvz.aicodegenerator.model.entity.App;
import com.bvz.aicodegenerator.model.entity.User;
import com.bvz.aicodegenerator.model.enums.CodeGenTypeEnum;
import com.bvz.aicodegenerator.model.vo.AppVO;
import com.bvz.aicodegenerator.model.vo.UserVO;
import com.bvz.aicodegenerator.monitor.MonitorContext;
import com.bvz.aicodegenerator.monitor.MonitorContextHolder;
import com.bvz.aicodegenerator.service.AppService;
import com.bvz.aicodegenerator.service.ChatHistoryService;
import com.bvz.aicodegenerator.service.ScreenshotService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 应用 服务层实现
 *
 * @author <a href="https://github.com/BourneVZ">BVZ</a>
 */
@Service
@Slf4j
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService {

    @Value("${code.deploy-host:http://localhost}")
    private String deployHost;

    @Resource
    private UserServiceImpl userService;

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private StreamHandlerExecutor streamHandlerExecutor;

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    @Resource
    private ScreenshotService screenshotService;

    @Resource
    private AiCodeGenTypeRoutingServiceFactory aiCodeGenTypeRoutingServiceFactory;


    @Override
    public Flux<String> chatToGenCode(Long appId, String message, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "用户消息不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");

        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");

        // 3. 验证用户是否有权限访问该应用，仅本人可以继续对话生成代码
        ThrowUtils.throwIf(!app.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR, "无权限访问该应用");

        // 4. 获取应用的代码生成类型
        String codeGenTypeStr = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenTypeStr);
        ThrowUtils.throwIf(codeGenTypeEnum == null, ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型");

        // 5. 先保存用户消息，再开始调用 AI，确保对话过程完整可追溯
        chatHistoryService.saveUserMessage(appId, loginUser.getId(), message);

        // 6. 设置监控上下文
        MonitorContextHolder.setContext(
                MonitorContext.builder()
                        .userId(loginUser.getId().toString())
                        .appId(appId.toString())
                        .build()
        );

        // 7. 调用 AI 生成代码（流式）
        Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(message, codeGenTypeEnum, appId);

        // 8. 收集 AI 响应内容并在完成后记录到对话历史
        Flux<String> resultFlux = streamHandlerExecutor
                .doExecute(codeStream, chatHistoryService, appId, loginUser, codeGenTypeEnum)
                .onErrorResume(error -> Flux.just(buildStreamErrorMessage(codeGenTypeEnum, error)));

        // 9. 在流完成后更新 hasGeneratedPreview 标记
        return resultFlux
                .doOnComplete(() -> {
                    try {
                        App updateApp = new App();
                        updateApp.setId(appId);
                        updateApp.setHasGeneratedPreview(1);
                        this.updateById(updateApp);
                        log.info("更新应用 hasGeneratedPreview 标记成功, appId={}", appId);
                    } catch (Exception e) {
                        log.error("更新 hasGeneratedPreview 标记失败, appId={}", appId, e);
                    }
                })
                .doFinally(signalType -> {
                    // 流结束时清理（无论成功/失败/取消）
                    MonitorContextHolder.clearContext();
                });
    }

    @Override
    public Long createApp(AppAddRequest appAddRequest, User loginUser) {
        // 参数校验
        String initPrompt = appAddRequest.getInitPrompt();
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "初始化 prompt 不能为空");
        // 构造入库对象
        App app = new App();
        BeanUtil.copyProperties(appAddRequest, app);
        app.setUserId(loginUser.getId());
        // 应用名称暂时为 initPrompt 前 12 位
        app.setAppName(initPrompt.substring(0, Math.min(initPrompt.length(), 12)));
        // 使用 AI 智能选择代码生成类型（多例模式）
        AiCodeGenTypeRoutingService routingService = aiCodeGenTypeRoutingServiceFactory.createAiCodeGenTypeRoutingService();
        CodeGenTypeEnum selectedCodeGenType = routingService.routeCodeGenType(initPrompt);
        app.setCodeGenType(selectedCodeGenType.getValue());
        // 插入数据库
        boolean result = this.save(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        log.info("应用创建成功，ID: {}, 类型: {}", app.getId(), selectedCodeGenType.getValue());
        return app.getId();
    }

    @Override
    public String deployApp(Long appId, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");

        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");

        // 3. 验证用户是否有权限部署该应用，仅本人可以部署
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限部署该应用");
        }

        // 4. 检查是否已有 deployKey，没有则生成 6 位随机标识
        String deployKey = app.getDeployKey();
        if (StrUtil.isBlank(deployKey)) {
            deployKey = RandomUtil.randomString(6);
        }

        // 5. 获取代码生成类型，构建源目录路径
        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;

        // 6. 检查源目录是否存在
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用代码不存在，请先生成代码");
        }

        // 7. Vue 项目特殊处理：执行构建
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
            // Vue 项目需要构建
            boolean buildSuccess = vueProjectBuilder.buildProject(sourceDirPath);
            ThrowUtils.throwIf(!buildSuccess, ErrorCode.SYSTEM_ERROR, "Vue 项目构建失败，请检查代码和依赖");
            // 检查 dist 目录是否存在
            File distDir = new File(sourceDirPath, "dist");
            ThrowUtils.throwIf(!distDir.exists(), ErrorCode.SYSTEM_ERROR, "Vue 项目构建完成但未生成 dist 目录");
            // 将 dist 目录作为部署源
            sourceDir = distDir;
            log.info("Vue 项目构建成功，将部署 dist 目录: {}", distDir.getAbsolutePath());
        }

        // 8. 复制文件到部署目录
        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
        try {
            FileUtil.copyContent(sourceDir, new File(deployDirPath), true);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "部署失败，" + e.getMessage());
        }

        // 9. 更新应用的 deployKey 和部署时间
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用部署信息失败");

        // 10. 构建应用访问 URL
        String appDeployUrl = String.format("%s/%s/", deployHost, deployKey);

        // 11. 异步生成截图并更新应用封面
        generateAppScreenshotAsync(appId, appDeployUrl);
        return appDeployUrl;

    }


    /**
     * 异步生成应用截图并更新封面
     *
     * @param appId  应用ID
     * @param appUrl 应用访问URL
     */
    @Override
    public void generateAppScreenshotAsync(Long appId, String appUrl) {
        // 使用虚拟线程异步执行
        Thread.startVirtualThread(() -> {
            // 调用截图服务生成截图并上传
            String screenshotUrl = screenshotService.generateAndUploadScreenshot(appUrl);
            // 更新应用封面字段
            App updateApp = new App();
            updateApp.setId(appId);
            updateApp.setCover(screenshotUrl);
            boolean updated = this.updateById(updateApp);
            ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "更新应用封面字段失败");
        });
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(Long appId) {
        // 删除应用时，先清理关联的聊天记录，避免留下冗余数据
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 无效");
        chatHistoryService.removeByAppId(appId);
        return super.removeById(appId);
    }

    @Override
    public void validApp(App app, boolean add) {
        if (app == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用数据不能为空");
        }
        String appName = app.getAppName();
        String initPrompt = app.getInitPrompt();
        String codeGenType = app.getCodeGenType();
        if (add && StrUtil.isBlank(initPrompt)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "initPrompt 不能为空");
        }
        if (appName != null && StrUtil.isBlank(appName)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用名称不能为空");
        }
        if (StrUtil.isNotBlank(appName) && appName.length() > 256) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用名称过长");
        }
        if (StrUtil.isNotBlank(codeGenType) && CodeGenTypeEnum.getEnumByValue(codeGenType) == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "代码生成类型错误");
        }
    }

    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        String codeGenType = appQueryRequest.getCodeGenType();
        String deployKey = appQueryRequest.getDeployKey();
        Integer priority = appQueryRequest.getPriority();
        Long userId = appQueryRequest.getUserId();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", id)
                .eq("codeGenType", codeGenType)
                .eq("deployKey", deployKey)
                .eq("priority", priority)
                .eq("userId", userId)
                .like("appName", appName)
                .like("cover", cover)
                .like("initPrompt", initPrompt)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }

    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO);

        // 关联查询用户信息
        Long userId = app.getUserId();
        if (userId != null) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            appVO.setUser(userVO);
        }
        return appVO;
    }

    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }

        // 批量获取用户信息，避免 N+1 查询问题
        Set<Long> userIds = appList.stream()
                .map(App::getUserId)
                .collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, userService::getUserVO));
        return appList.stream().map(app -> {
            AppVO appVO = getAppVO(app);
            UserVO userVO = userVOMap.get(app.getUserId());
            appVO.setUser(userVO);
            return appVO;
        }).collect(Collectors.toList());
    }


    private String buildStreamErrorMessage(CodeGenTypeEnum codeGenTypeEnum, Throwable error) {
        String errorMessage = StrUtil.blankToDefault(error.getMessage(), "AI 生成失败");
        if (CodeGenTypeEnum.VUE_PROJECT.equals(codeGenTypeEnum)) {
            return JSONUtil.toJsonStr(new AiResponseMessage("生成失败：" + errorMessage));
        }
        return "生成失败：" + errorMessage;
    }
}
