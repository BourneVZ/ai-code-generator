package com.bvz.aicodegenerator.service;

import com.bvz.aicodegenerator.model.dto.app.AppQueryRequest;
import com.bvz.aicodegenerator.model.entity.App;
import com.bvz.aicodegenerator.model.entity.User;
import com.bvz.aicodegenerator.model.vo.AppVO;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 应用 服务层
 *
 * @author <a href="https://github.com/BourneVZ">BVZ</a>
 */
public interface AppService extends IService<App> {

    /**
     * 通过对话生成应用代码
     *
     * @param appId     应用 ID
     * @param message   提示词
     * @param loginUser 登录用户
     * @return 流式结果
     */
    Flux<String> chatToGenCode(Long appId, String message, User loginUser);

    /**
     * 应用部署
     *
     * @param appId     应用 ID
     * @param loginUser 登录用户
     * @return 部署地址
     */
    String deployApp(Long appId, User loginUser);

    /**
     * 删除应用及其关联的聊天记录
     *
     * @param appId 应用 ID
     * @return 删除结果
     */
    boolean removeById(Long appId);

    /**
     * 校验应用参数
     *
     * @param app 应用
     * @param add 是否为创建校验
     */
    void validApp(App app, boolean add);

    /**
     * 获取查询条件
     *
     * @param appQueryRequest 查询请求
     * @return 查询条件
     */
    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);

    /**
     * 获取应用封装类
     *
     * @param app 应用
     * @return 应用 VO
     */
    AppVO getAppVO(App app);

    /**
     * 获取应用列表封装类
     *
     * @param appList 应用列表
     * @return 应用 VO 列表
     */
    List<AppVO> getAppVOList(List<App> appList);
}
