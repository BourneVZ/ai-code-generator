package com.bvz.aicodegenerator.service;

/**
 * 截图服务
 */
public interface ScreenshotService {
    /**
     * 生成网页截图并上传
     *
     * @param webUrl 网页URL
     * @return 截图访问URL
     */
    String generateAndUploadScreenshot(String webUrl);
}
