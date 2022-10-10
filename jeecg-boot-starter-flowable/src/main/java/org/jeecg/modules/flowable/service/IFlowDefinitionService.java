package org.jeecg.modules.flowable.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.flowable.domain.dto.FlowProcDefDto;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 */
public interface IFlowDefinitionService {

    boolean exist(String processDefinitionKey);


    /**
     * 流程定义列表
     *
     * @param pageNum  当前页码
     * @param pageSize 每页条数
     * @param flowProcDefDto
     * @return 流程定义分页列表数据
     */
    Page<FlowProcDefDto> list(Integer pageNum, Integer pageSize, FlowProcDefDto flowProcDefDto);

    /**
     * 导入流程文件
     *
     * @param name
     * @param category
     * @param in
     */
    void importFile(String name, String category, InputStream in);

    /**
     * 读取xml
     * @param deployId
     * @return
     */
    Result readXml(String deployId) throws IOException;
    Result readXmlByDataId(String dataId) throws IOException;
    /**
     * 根据流程定义Key启动流程实例
     *启动最新一个版本
     * @param procDefKey
     * @param variables
     * @return
     */
    Result startProcessInstanceByKey(String procDefKey, Map<String, Object> variables);

    /**
     * 根据流程定义ID启动流程实例
     *
     * @param procDefId
     * @param variables
     * @return
     */

    Result startProcessInstanceById(String procDefId, Map<String, Object> variables);

    /**
     * 根据流程关联的数据ID启动流程实例
     * @param dataId
     * @param variables
     * @return
     */
    Result startProcessInstanceByDataId(String dataId, Map<String, Object> variables);

    /**
     * 激活或挂起流程定义
     *
     * @param state    状态
     * @param deployId 流程部署ID
     */
    void updateState(Integer state, String deployId);


    /**
     * 删除流程定义
     *
     * @param deployId 流程部署ID act_ge_bytearray 表中 deployment_id值
     */
    void delete(String deployId);


    /**
     * 读取图片文件
     * @param deployId
     * @return
     */
    InputStream readImage(String deployId);


    InputStream readImageByDataId(String dataId);
}
