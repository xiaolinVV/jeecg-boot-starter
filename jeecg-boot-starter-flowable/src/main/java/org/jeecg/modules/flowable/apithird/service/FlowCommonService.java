package org.jeecg.modules.flowable.apithird.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.jeecg.modules.flowable.apithird.business.entity.FlowMyBusiness;
import org.jeecg.modules.flowable.apithird.business.service.impl.FlowMyBusinessServiceImpl;
import org.jeecg.modules.flowable.apithird.entity.ActStatus;
import org.jeecg.modules.flowable.common.exception.CustomException;
import org.jeecg.modules.flowable.service.impl.FlowInstanceServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 *业务模块调用API的集合
 *@author PanMeiCheng
 *@date 2021/11/22
 *@version 1.0
 */
@Service
public class FlowCommonService {
    @Autowired
    FlowMyBusinessServiceImpl flowMyBusinessService;
    @Autowired
    FlowInstanceServiceImpl flowInstanceService;
    /**
     * 初始生成或修改业务与流程的关联信息<br/>
     * 当业务模块新增一条数据后调用，此时业务数据关联一个流程定义，以备后续流程使用
     * @return 是否成功
     * @param title 必填。流程业务简要描述。例：2021年11月26日xxxxx申请
     * @param dataId 必填。业务数据Id，如果是一对多业务关系，传入主表的数据Id
     * @param serviceImplName 必填。业务service注入spring容器的名称。
*                        例如：@Service("demoService")则传入 demoService
     * @param processDefinitionKey 必填。流程定义Key，传入此值，未来启动的会是该类流程的最新一个版本
     * @param processDefinitionId 选填。流程定义Id，传入此值，未来启动的为指定版本的流程
     * @param jimuReportId 选填。积木报表ID, 可查看当前审批单挂载的单据报表页面
     * @param pcFormUrl 选填。PC表单组件地址, 可动态加载当前流程挂载的表单页面  eg. healthcenter/purchaseapply/modules/PurchaseApplyForm.vue
     */
    public boolean initActBusiness(String title,String dataId, String serviceImplName, String processDefinitionKey, String processDefinitionId,String jimuReportId,String pcFormUrl){
        boolean hasBlank = StrUtil.hasBlank(title,dataId, serviceImplName, processDefinitionKey);
        if (hasBlank) throw new CustomException("流程关键参数未填完全！dataId, serviceImplName, processDefinitionKey");
        LambdaQueryWrapper<FlowMyBusiness> flowMyBusinessLambdaQueryWrapper = new LambdaQueryWrapper<>();
        flowMyBusinessLambdaQueryWrapper.eq(FlowMyBusiness::getDataId, dataId)
        ;
        FlowMyBusiness flowMyBusiness = new FlowMyBusiness();
        FlowMyBusiness business = flowMyBusinessService.getOne(flowMyBusinessLambdaQueryWrapper);
        if (business!=null){
            flowMyBusiness = business;
        } else {
            flowMyBusiness.setId(IdUtil.fastSimpleUUID());
            flowMyBusiness.setBpmStatus(ActStatus.waitStart.getValue()); // 新增业务数据关联时默认为 "待启动" 状态
        }
        if (processDefinitionId==null){
            // 以便更新流程
            processDefinitionId = "";
        }
        flowMyBusiness.setTitle(title)
                .setDataId(dataId)
                .setServiceImplName(serviceImplName)
                .setProcessDefinitionKey(processDefinitionKey)
                .setProcessDefinitionId(processDefinitionId)
                .setJimuReportId(jimuReportId)
                .setPcFormUrl(pcFormUrl)
                ;
        if (business!=null){
            return flowMyBusinessService.updateById(flowMyBusiness);
        } else {
            return flowMyBusinessService.save(flowMyBusiness);
        }
    }

    /**
     * 删除流程
     * @param dataId
     * @return
     */
    public boolean delActBusiness(String dataId){
        boolean hasBlank = StrUtil.hasBlank(dataId);
        if (hasBlank) throw new CustomException("流程关键参数未填完全！dataId");
        LambdaQueryWrapper<FlowMyBusiness> flowMyBusinessQueryWrapper = new LambdaQueryWrapper<>();
        flowMyBusinessQueryWrapper.eq(FlowMyBusiness::getDataId,dataId);
        FlowMyBusiness one = flowMyBusinessService.getOne(flowMyBusinessQueryWrapper);
        if (one.getProcessInstanceId()!=null){
            try {
                flowInstanceService.delete(one.getProcessInstanceId(),"删除流程");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return flowMyBusinessService.remove(flowMyBusinessQueryWrapper);
    }

    /**
     * 根据业务ID列表查询业务与流程的关联信息列表
     *
     * @param dataIds 业务ID列表
     * @return
     */
    public List<FlowMyBusiness> getByDataIds(List<String> dataIds) {
        if (CollUtil.isEmpty(dataIds)) {
            return CollUtil.newArrayList();
        }
        LambdaQueryWrapper<FlowMyBusiness> flowMyBusinessLambdaQueryWrapper = new LambdaQueryWrapper<>();
        flowMyBusinessLambdaQueryWrapper.in(FlowMyBusiness::getDataId,dataIds);
        return flowMyBusinessService.list(flowMyBusinessLambdaQueryWrapper);
    }

    /**
     * 根据业务ID列表查询业务与流程的关联信息列表
     *
     * @param dataIds 业务ID列表
     * @return
     */
    public Map<String, FlowMyBusiness> getMapByDataIds(List<String> dataIds) {
        return this.getByDataIds(dataIds)
                .stream()
                .collect(Collectors.toMap(FlowMyBusiness::getDataId, e -> e));
    }
}
