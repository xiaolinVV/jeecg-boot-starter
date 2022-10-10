package org.jeecg.modules.flowable.service.impl;


import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.util.SpringContextUtils;
import org.jeecg.modules.flowable.apithird.business.entity.FlowMyBusiness;
import org.jeecg.modules.flowable.apithird.business.service.impl.FlowMyBusinessServiceImpl;
import org.jeecg.modules.flowable.apithird.entity.ActStatus;
import org.jeecg.modules.flowable.apithird.entity.SysUser;
import org.jeecg.modules.flowable.apithird.service.FlowCallBackServiceI;
import org.jeecg.modules.flowable.apithird.service.IFlowThirdService;
import org.jeecg.modules.flowable.common.exception.CustomException;
import org.jeecg.modules.flowable.domain.vo.FlowTaskVo;
import org.jeecg.modules.flowable.factory.FlowServiceFactory;
import org.jeecg.modules.flowable.service.IFlowInstanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * <p>工作流流程实例管理<p>
 *
 */
@Service
@Slf4j
public class FlowInstanceServiceImpl extends FlowServiceFactory implements IFlowInstanceService {
    @Autowired
    IFlowThirdService iFlowThirdService;
    @Autowired
    FlowMyBusinessServiceImpl flowMyBusinessService;

    @Override
    public List<Task> queryListByInstanceId(String instanceId) {
        List<Task> list = taskService.createTaskQuery().processInstanceId(instanceId).active().list();
        return list;
    }

    /**
     * 结束流程实例
     *
     * @param vo
     */
    @Override
    public void stopProcessInstance(FlowTaskVo vo) {
        String taskId = vo.getTaskId();

    }

    /**
     * 激活或挂起流程实例
     *
     * @param state      状态
     * @param instanceId 流程实例ID
     */
    @Override
    public void updateState(Integer state, String instanceId) {

        // 激活
        if (state == 1) {
            runtimeService.activateProcessInstanceById(instanceId);
        }
        // 挂起
        if (state == 2) {
            runtimeService.suspendProcessInstanceById(instanceId);
        }
    }

    /**
     * 删除流程实例ID
     *
     * @param instanceId   流程实例ID
     * @param deleteReason 删除原因
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String instanceId, String deleteReason) {
        List<Task> task = taskService.createTaskQuery().processInstanceId(instanceId).list();
        if (CollUtil.isEmpty(task)) {
            throw new CustomException("流程未启动或已执行完成，取消申请失败");
        }
        // 查询历史数据
        HistoricProcessInstance historicProcessInstance = getHistoricProcessInstanceById(instanceId);
        if (historicProcessInstance.getEndTime() != null) {
            historyService.deleteHistoricProcessInstance(historicProcessInstance.getId());
            return;
        }
        // 删除流程实例
        runtimeService.deleteProcessInstance(instanceId, deleteReason);
        // 删除历史流程实例
        historyService.deleteHistoricProcessInstance(instanceId);
        /*======================撤回删除 回调以及关键数据保存======================*/
        LambdaQueryWrapper<FlowMyBusiness> flowMyBusinessLambdaQueryWrapper = new LambdaQueryWrapper<>();
        flowMyBusinessLambdaQueryWrapper.eq(FlowMyBusiness::getProcessInstanceId,instanceId)
        ;
        //如果保存数据前未调用必调的FlowCommonService.initActBusiness方法，就会有问题
        FlowMyBusiness business = flowMyBusinessService.getOne(flowMyBusinessLambdaQueryWrapper);
        //设置数据
        String doneUsers = business.getDoneUsers();
        SysUser sysUser = iFlowThirdService.getLoginUser();
        // 处理过流程的人
        JSONArray doneUserList = new JSONArray();
        if (StrUtil.isNotBlank(doneUsers)){
            doneUserList = JSON.parseArray(doneUsers);
        }
        if (!doneUserList.contains(sysUser.getUsername())){
            doneUserList.add(sysUser.getUsername());
        }
            business
                    .setBpmStatus(ActStatus.recall.getValue())
                    .setTaskId("")
                    .setTaskName("已撤回")
                    .setPriority("")
                    .setDoneUsers(doneUserList.toJSONString())
                    .setTodoUsers("")
            ;
        flowMyBusinessService.updateById(business);
        //spring容器类名
        String serviceImplName = business.getServiceImplName();
        FlowCallBackServiceI flowCallBackService = (FlowCallBackServiceI) SpringContextUtils.getBean(serviceImplName);
        // 流程处理完后，进行回调业务层
        if (flowCallBackService!=null)flowCallBackService.afterFlowHandle(business);

    }

    @Override
    public void deleteByDataId(String dataId, String deleteReason) {
        LambdaQueryWrapper<FlowMyBusiness> flowMyBusinessLambdaQueryWrapper = new LambdaQueryWrapper<>();
        flowMyBusinessLambdaQueryWrapper.eq(FlowMyBusiness::getDataId, dataId)
        ;
        //如果保存数据前未调用必调的FlowCommonService.initActBusiness方法，就会有问题
        FlowMyBusiness business = flowMyBusinessService.getOne(flowMyBusinessLambdaQueryWrapper);
        this.delete(business.getProcessInstanceId(),deleteReason);
    }

    /**
     * 根据实例ID查询历史实例数据
     *
     * @param processInstanceId
     * @return
     */
    @Override
    public HistoricProcessInstance getHistoricProcessInstanceById(String processInstanceId) {
        HistoricProcessInstance historicProcessInstance =
                historyService.createHistoricProcessInstanceQuery().processInstanceId(processInstanceId).singleResult();
        if (Objects.isNull(historicProcessInstance)) {
            throw new FlowableObjectNotFoundException("流程实例不存在: " + processInstanceId);
        }
        return historicProcessInstance;
    }

    /**
     * 根据流程定义ID启动流程实例
     *
     * @param procDefId 流程定义Id
     * @param variables 流程变量
     * @return
     */
    @Override
    public Result startProcessInstanceById(String procDefId, Map<String, Object> variables) {

            // 设置流程发起人Id到流程中
        SysUser user = iFlowThirdService.getLoginUser();
        String username = user.getUsername();
//            identityService.setAuthenticatedUserId(userId.toString());
            variables.put("initiator",username);
            variables.put("_FLOWABLE_SKIP_EXPRESSION_ENABLED", true);
            ProcessInstance processInstance = runtimeService.startProcessInstanceById(procDefId, variables);
            processInstance.getProcessInstanceId();
            return Result.OK("流程启动成功");
    }
}
