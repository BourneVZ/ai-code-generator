package com.bvz.aicodegenerator.core;

import cn.hutool.json.JSONUtil;
import com.bvz.aicodegenerator.ai.AiCodeGeneratorService;
import com.bvz.aicodegenerator.ai.AiCodeGeneratorServiceFactory;
import com.bvz.aicodegenerator.ai.model.message.AiResponseMessage;
import com.bvz.aicodegenerator.ai.model.message.ToolExecutedMessage;
import com.bvz.aicodegenerator.ai.model.message.ToolRequestMessage;
import com.bvz.aicodegenerator.core.parser.CodeParserExecutor;
import com.bvz.aicodegenerator.core.saver.CodeFileSaverExecutor;
import com.bvz.aicodegenerator.exception.BusinessException;
import com.bvz.aicodegenerator.exception.ErrorCode;
import com.bvz.aicodegenerator.model.enums.CodeGenTypeEnum;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolExecution;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.bvz.aicodegenerator.model.enums.CodeGenTypeEnum.HTML;

/**
 * AI 代码生成外观类，组合生成和保存功能
 */
@Service
@Slf4j
public class AiCodeGeneratorFacade {

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;


    /**
     * 统一入口：根据类型生成并保存代码
     *
     * @param userMessage 用户提示词
     * @param codeGenType 生成类型
     * @return 保存的目录
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenType, Long appId) {
        if (codeGenType == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        // 根据 appId 获取对应的 AI 服务实例
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId);
        return switch (codeGenType) {
            case HTML -> {
                Object codeResult = aiCodeGeneratorService.generateHtmlCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(codeResult, codeGenType, appId);
            }
            case MULTI_FILE -> {
                Object codeResult = aiCodeGeneratorService.generateMultiFileCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(codeResult, codeGenType, appId);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenType.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * 统一入口：根据类型生成并保存代码（流式）
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @param appId           应用 ID
     */

    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        // 根据 appId 获取对应的 AI 服务实例
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
                yield processCodeStream(codeStream, HTML, appId);
            }
            case MULTI_FILE -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.MULTI_FILE, appId);
            }
            case VUE_PROJECT -> {
                TokenStream tokenStream = aiCodeGeneratorService.generateVueProjectCodeStream(appId, userMessage);
                yield processTokenStream(tokenStream);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * 将 TokenStream 转换为 Flux<String>，并传递工具调用信息
     *
     * @param tokenStream TokenStream 对象
     * @return Flux<String> 流式响应
     */
    private Flux<String> processTokenStream(TokenStream tokenStream) {
        return Flux.create(sink -> {
            AtomicBoolean terminalSignalSent = new AtomicBoolean(false);
            tokenStream.onPartialResponse((String partialResponse) -> {
                        AiResponseMessage aiResponseMessage = new AiResponseMessage(partialResponse);
                        sink.next(JSONUtil.toJsonStr(aiResponseMessage));
                    })
                    .beforeToolExecution((BeforeToolExecution beforeToolExecution) -> {
                        ToolRequestMessage toolRequestMessage = new ToolRequestMessage(beforeToolExecution.request());
                        sink.next(JSONUtil.toJsonStr(toolRequestMessage));
                    })
                    .onToolExecuted((ToolExecution toolExecution) -> {
                        ToolExecutedMessage toolExecutedMessage = new ToolExecutedMessage(toolExecution);
                        sink.next(JSONUtil.toJsonStr(toolExecutedMessage));
                    })
                    .onCompleteResponse((ChatResponse response) -> {
                        completeSinkSafely(sink, terminalSignalSent);
                    })
                    .onError((Throwable error) -> {
                        if (isBenignStreamClosed(error)) {
                            log.warn("Streaming response closed after output completed, treating as complete. message={}", error.getMessage());
                            completeSinkSafely(sink, terminalSignalSent);
                            return;
                        }
                        log.error("Streaming response failed", error);
                        errorSinkSafely(sink, terminalSignalSent, error);
                    })
                    .start();
        });
    }

    private void completeSinkSafely(FluxSink<String> sink, AtomicBoolean terminalSignalSent) {
        if (terminalSignalSent.compareAndSet(false, true)) {
            sink.complete();
        }
    }

    private void errorSinkSafely(FluxSink<String> sink, AtomicBoolean terminalSignalSent, Throwable error) {
        if (terminalSignalSent.compareAndSet(false, true)) {
            sink.error(error);
        }
    }

    private boolean isBenignStreamClosed(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof IOException && "closed".equalsIgnoreCase(current.getMessage())) {
                return true;
            }
            if ("closed".equalsIgnoreCase(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }


    /**
     * 通用流式代码处理方法
     *
     * @param codeStream  代码流
     * @param codeGenType 代码生成类型
     * @param appId       应用 ID
     * @return 流式响应
     */
    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum codeGenType, Long appId) {
        // 当流式返回生成代码完成后，再保存代码
        StringBuilder codeBuilder = new StringBuilder();
        return codeStream
                .doOnNext(chunk -> {
                    // 实时收集代码片段
                    codeBuilder.append(chunk);
                })
                .doOnComplete(() -> {
                    // 流式返回完成后保存代码
                    try {
                        String completeCode = codeBuilder.toString();
                        // 使用执行器解析代码
                        Object codeResult = CodeParserExecutor.executeParser(completeCode, codeGenType);
                        // 使用执行器保存代码
                        File savedDir = CodeFileSaverExecutor.executeSaver(codeResult, codeGenType, appId);
                        log.info("保存成功，路径为：" + savedDir.getAbsolutePath());
                    } catch (Exception e) {
                        log.error("保存失败: {}", e.getMessage());
                    }
                });
    }
}
