package com.bvz.aicodegenerator.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ZipUtil;
import com.bvz.aicodegenerator.exception.BusinessException;
import com.bvz.aicodegenerator.exception.ErrorCode;
import com.bvz.aicodegenerator.exception.ThrowUtils;
import com.bvz.aicodegenerator.service.ProjectDownloadService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;

@Service
@Slf4j
public class ProjectDownloadServiceImpl implements ProjectDownloadService {
    /**
     * 需要过滤的文件和目录名称
     */
    private static final Set<String> IGNORED_NAMES = Set.of(
            "node_modules",
            ".git",
            "dist",
            "build",
            ".DS_Store",
            ".env",
            "target",
            ".mvn",
            ".idea",
            ".vscode"
    );

    /**
     * 需要过滤的文件扩展名
     */
    private static final Set<String> IGNORED_EXTENSIONS = Set.of(
            ".log",
            ".tmp",
            ".cache"
    );

    @Override
    public void downloadProjectAsZip(String projectPath, String downloadFileName, HttpServletResponse response) {
        // 基础校验
        ThrowUtils.throwIf(StrUtil.isBlank(projectPath), ErrorCode.PARAMS_ERROR, "项目路径不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(downloadFileName), ErrorCode.PARAMS_ERROR, "下载文件名不能为空");
        File projectDir = new File(projectPath);
        ThrowUtils.throwIf(!projectDir.exists(), ErrorCode.NOT_FOUND_ERROR, "项目目录不存在");
        ThrowUtils.throwIf(!projectDir.isDirectory(), ErrorCode.PARAMS_ERROR, "指定路径不是目录");
        log.info("开始打包下载项目: {} -> {}.zip", projectPath, downloadFileName);

        FileFilter filter = file -> isPathAllowed(projectDir.toPath(), file.toPath());

        // 先写入内存缓冲区，确保 ZIP 完整生成后再输出，避免流式写入时异常导致文件损坏
        byte[] zipBytes;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            ZipUtil.zip(bos, StandardCharsets.UTF_8, false, filter, projectDir);
            zipBytes = bos.toByteArray();
        } catch (Exception e) {
            log.error("项目打包异常", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "项目打包失败");
        }

        if (zipBytes.length == 0) {
            log.warn("打包结果为空: {}", projectPath);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "项目打包结果为空");
        }

        // ZIP 完整生成后再设置响应头并输出
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/zip");
        response.setContentLength(zipBytes.length);
        response.addHeader("Content-Disposition",
                String.format("attachment; filename=\"%s.zip\"", downloadFileName));
        try {
            response.getOutputStream().write(zipBytes);
            response.getOutputStream().flush();
            log.info("项目打包下载完成: {}, 大小: {} bytes", downloadFileName, zipBytes.length);
        } catch (IOException e) {
            log.error("响应写入异常", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件下载失败");
        }
    }


    /**
     * 检查路径是否允许包含在压缩包中
     *
     * @param projectRoot 项目根目录
     * @param fullPath    完整路径
     * @return 是否允许
     */
    private boolean isPathAllowed(Path projectRoot, Path fullPath) {
        // 获取相对路径
        Path relativePath = projectRoot.relativize(fullPath);
        // 检查路径中的每一部分
        for (Path part : relativePath) {
            String partName = part.toString();
            // 检查是否在忽略名称列表中
            if (IGNORED_NAMES.contains(partName)) {
                return false;
            }
            // 检查文件扩展名
            if (IGNORED_EXTENSIONS.stream().anyMatch(partName::endsWith)) {
                return false;
            }
        }
        return true;
    }
}
