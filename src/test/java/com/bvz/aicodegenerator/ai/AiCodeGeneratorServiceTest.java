package com.bvz.aicodegenerator.ai;

import com.bvz.aicodegenerator.ai.model.HtmlCodeResult;
import com.bvz.aicodegenerator.ai.model.MultiFileCodeResult;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AiCodeGeneratorServiceTest {

    @Resource
    private AiCodeGeneratorService aiCodeGeneratorService;

    @Test
    void generateHtmlCode() {
        HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode("做个简单的工作记录小工具，这只是一次连通性测试，返回结果不要不超过20行");
        Assertions.assertNotNull(result);
    }

    @Test
    void generateMultiFileCode() {
        MultiFileCodeResult multiFileCode = aiCodeGeneratorService.generateMultiFileCode("做个简单的留言板，这只是一次连通性测试，返回结果不要不超过20行");
        Assertions.assertNotNull(multiFileCode);
    }
}
