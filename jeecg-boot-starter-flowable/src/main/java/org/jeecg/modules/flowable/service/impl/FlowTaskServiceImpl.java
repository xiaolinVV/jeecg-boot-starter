package org.jeecg.modules.flowable.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.*;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.common.engine.impl.identity.Authentication;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.task.Comment;
import org.flowable.identitylink.api.IdentityLinkType;
import org.flowable.identitylink.api.history.HistoricIdentityLink;
import org.flowable.image.ProcessDiagramGenerator;
import org.flowable.task.api.DelegationState;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskInfo;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.util.SpringContextUtils;
import org.jeecg.modules.flowable.apithird.business.entity.FlowMyBusiness;
import org.jeecg.modules.flowable.apithird.business.service.impl.FlowMyBusinessServiceImpl;
import org.jeecg.modules.flowable.apithird.entity.ActStatus;
import org.jeecg.modules.flowable.apithird.entity.SysCategory;
import org.jeecg.modules.flowable.apithird.entity.SysUser;
import org.jeecg.modules.flowable.apithird.service.FlowCallBackServiceI;
import org.jeecg.modules.flowable.apithird.service.IFlowThirdService;
import org.jeecg.modules.flowable.common.constant.ProcessConstants;
import org.jeecg.modules.flowable.common.enums.FlowComment;
import org.jeecg.modules.flowable.common.exception.CustomException;
import org.jeecg.modules.flowable.domain.dto.FlowCommentDto;
import org.jeecg.modules.flowable.domain.dto.FlowNextDto;
import org.jeecg.modules.flowable.domain.dto.FlowTaskDto;
import org.jeecg.modules.flowable.domain.dto.FlowViewerDto;
import org.jeecg.modules.flowable.domain.vo.FlowTaskVo;
import org.jeecg.modules.flowable.factory.FlowServiceFactory;
import org.jeecg.modules.flowable.flow.CustomProcessDiagramGenerator;
import org.jeecg.modules.flowable.flow.FindNextNodeUtil;
import org.jeecg.modules.flowable.flow.FlowableUtils;
import org.jeecg.modules.flowable.service.IFlowTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 **/
@Service
@Slf4j
@Transactional
public class FlowTaskServiceImpl extends FlowServiceFactory implements IFlowTaskService {

    @Resource
    private IFlowThirdService iFlowThirdService;
    @Autowired
    FlowMyBusinessServiceImpl flowMyBusinessService;
    /**
     * 完成任务
     *
     * @param taskVo 请求实体参数
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Result complete(FlowTaskVo taskVo) {
        Task task = taskService.createTaskQuery().taskId(taskVo.getTaskId()).singleResult();
        if (Objects.isNull(task)){
            return Result.error("任务不存在");
        }
        SysUser loginUser = iFlowThirdService.getLoginUser();
        if (DelegationState.PENDING.equals(task.getDelegationState())) {
            taskService.addComment(taskVo.getTaskId(), taskVo.getInstanceId(), FlowComment.DELEGATE.getType(), taskVo.getComment());
            //taskService.resolveTask(taskVo.getTaskId(), taskVo.getValues());
        } else {
            taskService.addComment(taskVo.getTaskId(), taskVo.getInstanceId(), FlowComment.NORMAL.getType(), taskVo.getComment());
            taskService.setAssignee(taskVo.getTaskId(), loginUser.getUsername());
            //taskService.complete(taskVo.getTaskId(), taskVo.getValues());
        }
        /*======================审批通过  回调以及关键数据保存======================*/
        //业务数据id
        String dataId = taskVo.getDataId();
        //如果保存数据前未调用必调的FlowCommonService.initActBusiness方法，就会有问题
        FlowMyBusiness business = flowMyBusinessService.getByDataId(dataId);
        //spring容器类名
        String serviceImplName = business.getServiceImplName();
        FlowCallBackServiceI flowCallBackService = (FlowCallBackServiceI) SpringContextUtils.getBean(serviceImplName);
        // 流程变量
        Map<String, Object> flowBeforeParamsValues = flowCallBackService.flowValuesOfTask(business.getTaskNameId(),taskVo.getValues());

        //设置数据
        Map<String, Object> values = taskVo.getValues();
        if (MapUtil.isNotEmpty(flowBeforeParamsValues)){
        //    业务层有设置变量，使用业务层的变量
            values = flowBeforeParamsValues;
        }
        FlowNextDto nextFlowNode = this.getNextFlowNode(task.getId(), values);
        //下一个实例节点
        if (DelegationState.PENDING.equals(task.getDelegationState())) {
            taskService.resolveTask(taskVo.getTaskId(), values);
        } else {
            taskService.complete(taskVo.getTaskId(), values);
        }
        List<Task> task2List = taskService.createTaskQuery().processInstanceId(business.getProcessInstanceId()).active().list();
        Task task2 = null;
        if (CollUtil.isNotEmpty(task2List)){
            task2 = task2List.get(0);
        }

        // 下个节点候选人
        List<String> beforeParamsCandidateUsernames = Lists.newArrayList();
        if(task2!=null){
            beforeParamsCandidateUsernames = flowCallBackService.flowCandidateUsernamesOfTask(task2.getTaskDefinitionKey(),taskVo.getValues());
        }
        List<String> candidateUsers = taskVo.getCandidateUsers();
        if (CollUtil.isNotEmpty(candidateUsers)){
            //    前端传入候选人 覆盖
            beforeParamsCandidateUsernames = candidateUsers;
        }
        String doneUsers = business.getDoneUsers();
        // 处理过流程的人
        JSONArray doneUserList = new JSONArray();
        if (StrUtil.isNotBlank(doneUsers)){
            doneUserList = JSON.parseArray(doneUsers);
        }
        if (!doneUserList.contains(loginUser.getUsername())){
            doneUserList.add(loginUser.getUsername());
        }

        if (task2!=null && task.getTaskDefinitionKey().equals(task2.getTaskDefinitionKey())){
        //    * 当前节点是会签节点，没有走完
            business.setBpmStatus(ActStatus.doing.getValue())
                    .setTaskId(task2.getId())
                    .setDoneUsers(doneUserList.toJSONString())
            ;
            String todoUsersStr = business.getTodoUsers();
            JSONArray todosArr = JSON.parseArray(todoUsersStr);
            // 删除后重写
            for (Task task2One : task2List) {
                for (Object oldUser : todosArr) {
                    taskService.deleteCandidateUser(task2One.getId(),oldUser.toString());
                }
            }
            // 重写
            if (CollUtil.isNotEmpty(beforeParamsCandidateUsernames)){
                beforeParamsCandidateUsernames.remove(loginUser.getUsername());
                // 业务层有指定候选人，覆盖
                for (Task task2One : task2List) {
                    for (String newUser : beforeParamsCandidateUsernames) {
                        taskService.addCandidateUser(task2One.getId(),newUser);
                    }
                }
                business.setTodoUsers(JSON.toJSONString(beforeParamsCandidateUsernames));
            } else {
                todosArr.remove(loginUser.getUsername());
                for (Task task2One : task2List) {
                    for (Object oldUser : todosArr) {
                        taskService.addCandidateUser(task2One.getId(),oldUser.toString());
                    }
                }
                business.setTodoUsers(todosArr.toJSONString());
            }


        } else {
        //    * 下一节点是会签节点 或 普通用户节点，逻辑一致
            if (nextFlowNode!=null){
                //**有下一个节点
                UserTask nextTask = nextFlowNode.getUserTask();
                //能够处理下个节点的候选人
                List<SysUser> nextFlowNodeUserList = nextFlowNode.getUserList();
                List<String> collect_username = nextFlowNodeUserList.stream().map(SysUser::getUsername).collect(Collectors.toList());
                if (CollUtil.isNotEmpty(candidateUsers)){
                    //    前端传入候选人
                    collect_username = candidateUsers;
                }
                business.setBpmStatus(ActStatus.doing.getValue())
                        .setTaskId(task2.getId())
                        .setTaskNameId(nextTask.getId())
                        .setTaskName(nextTask.getName())
                        .setPriority(nextTask.getPriority())
                        .setDoneUsers(doneUserList.toJSONString())
                        .setTodoUsers(JSON.toJSONString(collect_username))
                ;
                // 删除后重写
                for (Task task2One : task2List) {
                    for (String oldUser : collect_username) {
                        taskService.deleteCandidateUser(task2One.getId(),oldUser);
                    }
                }
                if (CollUtil.isEmpty(candidateUsers)&&CollUtil.isNotEmpty(beforeParamsCandidateUsernames)){
                    // 前端没有传入候选人 && 业务层有指定候选人，覆盖
                    for (Task task2One : task2List) {
                        for (String newUser : beforeParamsCandidateUsernames) {
                            taskService.addCandidateUser(task2One.getId(),newUser);
                        }
                    }
                    business.setTodoUsers(JSON.toJSONString(beforeParamsCandidateUsernames));
                } else {
                    for (Task task2One : task2List) {
                        for (String oldUser : collect_username) {
                            taskService.addCandidateUser(task2One.getId(),oldUser);
                        }
                    }
                }

            } else {
                //    **没有下一个节点，流程已经结束了
                business.setBpmStatus(ActStatus.pass.getValue())
                        .setDoneUsers(doneUserList.toJSONString())
                        .setTodoUsers("")
                        .setTaskId("")
                        .setTaskNameId("")
                        .setTaskName("")
                ;
            }
        }

        flowMyBusinessService.updateById(business);
        // 流程处理完后，进行回调业务层
        business.setValues(values);
        if (flowCallBackService!=null)flowCallBackService.afterFlowHandle(business);
        return Result.OK();
    }
    @Override
    public Result completeByDateId(FlowTaskVo flowTaskVo){
        //如果保存数据前未调用必调的FlowCommonService.initActBusiness方法，就会有问题
        FlowMyBusiness business = flowMyBusinessService.getByDataId(flowTaskVo.getDataId());
        flowTaskVo.setTaskId(business.getTaskId());
        flowTaskVo.setInstanceId(business.getProcessInstanceId());
        return this.complete(flowTaskVo);
    }
    @Override
    public void taskRejectByDataId(FlowTaskVo flowTaskVo){
        FlowMyBusiness business = flowMyBusinessService.getByDataId(flowTaskVo.getDataId());
        flowTaskVo.setTaskId(business.getTaskId());
        this.taskReject(flowTaskVo);
    }
    /**
     * 驳回任务
     *
     * @param flowTaskVo
     */
    @Override
    public void taskReject(FlowTaskVo flowTaskVo) {
        if (taskService.createTaskQuery().taskId(flowTaskVo.getTaskId()).singleResult().isSuspended()) {
            throw new CustomException("任务处于挂起状态");
        }
        // 当前任务 task
        Task task = taskService.createTaskQuery().taskId(flowTaskVo.getTaskId()).singleResult();
        // 获取流程定义信息
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery().processDefinitionId(task.getProcessDefinitionId()).singleResult();
        // 获取所有节点信息
        Process process = repositoryService.getBpmnModel(processDefinition.getId()).getProcesses().get(0);
        // 获取全部节点列表，包含子节点
        Collection<FlowElement> allElements = FlowableUtils.getAllElements(process.getFlowElements(), null);
        // 获取当前任务节点元素
        FlowElement source = null;
        if (allElements != null) {
            for (FlowElement flowElement : allElements) {
                // 类型为用户节点
                if (flowElement.getId().equals(task.getTaskDefinitionKey())) {
                    // 获取节点信息
                    source = flowElement;
                }
            }
        }

        // 目的获取所有跳转到的节点 targetIds
        // 获取当前节点的所有父级用户任务节点
        // 深度优先算法思想：延边迭代深入
        List<UserTask> parentUserTaskList = FlowableUtils.iteratorFindParentUserTasks(source, null, null);
        if (parentUserTaskList == null || parentUserTaskList.size() == 0) {
            throw new CustomException("当前节点为初始任务节点，不能驳回");
        }
        // 获取活动 ID 即节点 Key
        List<String> parentUserTaskKeyList = new ArrayList<>();
        parentUserTaskList.forEach(item -> parentUserTaskKeyList.add(item.getId()));
        // 获取全部历史节点活动实例，即已经走过的节点历史，数据采用开始时间升序
        List<HistoricTaskInstance> historicTaskInstanceList = historyService.createHistoricTaskInstanceQuery().processInstanceId(task.getProcessInstanceId()).orderByHistoricTaskInstanceStartTime().asc().list();
        // 数据清洗，将回滚导致的脏数据清洗掉
        List<String> lastHistoricTaskInstanceList = FlowableUtils.historicTaskInstanceClean(allElements, historicTaskInstanceList);
        // 此时历史任务实例为倒序，获取最后走的节点
        List<String> targetIds = new ArrayList<>();
        // 循环结束标识，遇到当前目标节点的次数
        int number = 0;
        StringBuilder parentHistoricTaskKey = new StringBuilder();
        for (String historicTaskInstanceKey : lastHistoricTaskInstanceList) {
            // 当会签时候会出现特殊的，连续都是同一个节点历史数据的情况，这种时候跳过
            if (parentHistoricTaskKey.toString().equals(historicTaskInstanceKey)) {
                continue;
            }
            parentHistoricTaskKey = new StringBuilder(historicTaskInstanceKey);
            if (historicTaskInstanceKey.equals(task.getTaskDefinitionKey())) {
                number++;
            }
            // 在数据清洗后，历史节点就是唯一一条从起始到当前节点的历史记录，理论上每个点只会出现一次
            // 在流程中如果出现循环，那么每次循环中间的点也只会出现一次，再出现就是下次循环
            // number == 1，第一次遇到当前节点
            // number == 2，第二次遇到，代表最后一次的循环范围
            if (number == 2) {
                break;
            }
            // 如果当前历史节点，属于父级的节点，说明最后一次经过了这个点，需要退回这个点
            if (parentUserTaskKeyList.contains(historicTaskInstanceKey)) {
                targetIds.add(historicTaskInstanceKey);
            }
        }


        // 目的获取所有需要被跳转的节点 currentIds
        // 取其中一个父级任务，因为后续要么存在公共网关，要么就是串行公共线路
        UserTask oneUserTask = parentUserTaskList.get(0);
        // 获取所有正常进行的任务节点 Key，这些任务不能直接使用，需要找出其中需要撤回的任务
        List<Task> runTaskList = taskService.createTaskQuery().processInstanceId(task.getProcessInstanceId()).list();
        List<String> runTaskKeyList = new ArrayList<>();
        runTaskList.forEach(item -> runTaskKeyList.add(item.getTaskDefinitionKey()));
        // 需驳回任务列表
        List<String> currentIds = new ArrayList<>();
        // 通过父级网关的出口连线，结合 runTaskList 比对，获取需要撤回的任务
        List<UserTask> currentUserTaskList = FlowableUtils.iteratorFindChildUserTasks(oneUserTask, runTaskKeyList, null, null);
        currentUserTaskList.forEach(item -> currentIds.add(item.getId()));


        // 规定：并行网关之前节点必须需存在唯一用户任务节点，如果出现多个任务节点，则并行网关节点默认为结束节点，原因为不考虑多对多情况
        if (targetIds.size() > 1 && currentIds.size() > 1) {
            throw new CustomException("任务出现多对多情况，无法撤回");
        }

        // 循环获取那些需要被撤回的节点的ID，用来设置驳回原因
        List<String> currentTaskIds = new ArrayList<>();
        currentIds.forEach(currentId -> runTaskList.forEach(runTask -> {
            if (currentId.equals(runTask.getTaskDefinitionKey())) {
                currentTaskIds.add(runTask.getId());
            }
        }));
        // 设置驳回意见
        currentTaskIds.forEach(item -> taskService.addComment(item, task.getProcessInstanceId(), FlowComment.REJECT.getType(), flowTaskVo.getComment()));
        SysUser loginUser = iFlowThirdService.getLoginUser();
        try {
            // 设置处理人
            taskService.setAssignee(task.getId(), loginUser.getUsername());
            // 如果父级任务多于 1 个，说明当前节点不是并行节点，原因为不考虑多对多情况
            if (targetIds.size() > 1) {
                // 1 对 多任务跳转，currentIds 当前节点(1)，targetIds 跳转到的节点(多)
                runtimeService.createChangeActivityStateBuilder()
                        .processInstanceId(task.getProcessInstanceId()).
                        moveSingleActivityIdToActivityIds(currentIds.get(0), targetIds).changeState();
            }
            // 如果父级任务只有一个，因此当前任务可能为网关中的任务
            if (targetIds.size() == 1) {
                // 1 对 1 或 多 对 1 情况，currentIds 当前要跳转的节点列表(1或多)，targetIds.get(0) 跳转到的节点(1)
                runtimeService.createChangeActivityStateBuilder()
                        .processInstanceId(task.getProcessInstanceId())
                        .moveActivityIdsToSingleActivityId(currentIds, targetIds.get(0)).changeState();
            }
            /*======================驳回  回调以及关键数据保存======================*/
            //业务数据id
            String dataId = flowTaskVo.getDataId();
            if (dataId==null) return;
            //如果保存数据前未调用必调的FlowCommonService.initActBusiness方法，就会有问题
            FlowMyBusiness business = flowMyBusinessService.getByDataId(dataId);
            // 驳回到了上一个节点等待处理
            List<Task> task2List = taskService.createTaskQuery().processInstanceId(business.getProcessInstanceId()).active().list();
            Task task2 = task2List.get(0);
            //spring容器类名
            String serviceImplName = business.getServiceImplName();
            FlowCallBackServiceI flowCallBackService = (FlowCallBackServiceI) SpringContextUtils.getBean(serviceImplName);
            Map<String, Object> values = flowTaskVo.getValues();
            if (values ==null){
                values = MapUtil.newHashMap();
                values.put("dataId",dataId);
            } else {
                values.put("dataId",dataId);
            }
            List<String> beforeParamsCandidateUsernames = flowCallBackService.flowCandidateUsernamesOfTask(task2.getTaskDefinitionKey(), values);
            //设置数据
            String doneUsers = business.getDoneUsers();
            // 处理过流程的人
            JSONArray doneUserList = new JSONArray();
            if (StrUtil.isNotBlank(doneUsers)){
                doneUserList = JSON.parseArray(doneUsers);
            }
            if (!doneUserList.contains(loginUser.getUsername())){
                doneUserList.add(loginUser.getUsername());
            }
            business.setBpmStatus(ActStatus.reject.getValue())
                    .setTaskId(task2.getId())
                    .setTaskNameId(task2.getTaskDefinitionKey())
                    .setTaskName(task2.getName())
                    .setDoneUsers(doneUserList.toJSONString())
            ;
            FlowElement targetElement = null;
            if (allElements != null) {
                for (FlowElement flowElement : allElements) {
                    // 类型为用户节点
                    if (flowElement.getId().equals(task2.getTaskDefinitionKey())) {
                        // 获取节点信息
                        targetElement = flowElement;
                    }
                }
            }

            if (targetElement!=null){
                UserTask targetTask = (UserTask) targetElement;
                business.setPriority(targetTask.getPriority());

                if (StrUtil.equals(business.getTaskNameId(),ProcessConstants.START_NODE)){
                    //    开始节点。设置处理人为申请人
                    business.setTodoUsers(JSON.toJSONString(Lists.newArrayList(business.getProposer())));
                    taskService.setAssignee(business.getTaskId(),business.getProposer());
                } else {
                    List<SysUser> sysUserFromTask = getSysUserFromTask(targetTask);
                    List<String> collect_username = sysUserFromTask.stream().map(SysUser::getUsername).collect(Collectors.toList());
                    // 前端存入的候选人
                    List<String> candidateUsers = flowTaskVo.getCandidateUsers();
                    if (CollUtil.isNotEmpty(candidateUsers)){
                        collect_username = candidateUsers;
                    }
                    business.setTodoUsers(JSON.toJSONString(collect_username));
                    // 删除后重写
                    for (Task task2One : task2List) {
                        for (String oldUser : collect_username) {
                            taskService.deleteCandidateUser(task2One.getId(),oldUser);
                        }
                    }
                    if (CollUtil.isNotEmpty(beforeParamsCandidateUsernames)){
                        if (CollUtil.isNotEmpty(candidateUsers)){
                            beforeParamsCandidateUsernames = candidateUsers;
                        }
                        // 业务层有指定候选人，覆盖
                        for (Task task2One : task2List) {
                            for (String newUser : beforeParamsCandidateUsernames) {
                                taskService.addCandidateUser(task2One.getId(), newUser);
                            }
                        }
                        business.setTodoUsers(JSON.toJSONString(beforeParamsCandidateUsernames));
                    } else {
                        for (Task task2One : task2List) {
                            for (String oldUser : collect_username) {
                                taskService.addCandidateUser(task2One.getId(), oldUser);
                            }
                        }
                    }
                }
            }

            flowMyBusinessService.updateById(business);
           // 流程处理完后，进行回调业务层
            business.setValues(values);
            if (flowCallBackService!=null) flowCallBackService.afterFlowHandle(business);
        } catch (FlowableObjectNotFoundException e) {
            throw new CustomException("未找到流程实例，流程可能已发生变化");
        } catch (FlowableException e) {
            throw new CustomException("无法取消或开始活动");
        }

    }
    @Override
    public void taskReturnByDataId(FlowTaskVo flowTaskVo){
        //如果保存数据前未调用必调的FlowCommonService.initActBusiness方法，就会有问题
        FlowMyBusiness business = flowMyBusinessService.getByDataId(flowTaskVo.getDataId());
        flowTaskVo.setTaskId(business.getTaskId());
        taskReturn(flowTaskVo);
    }
    /**
     * 退回任务
     *
     * @param flowTaskVo 请求实体参数
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void taskReturn(FlowTaskVo flowTaskVo) {
        if (taskService.createTaskQuery().taskId(flowTaskVo.getTaskId()).singleResult().isSuspended()) {
            throw new CustomException("任务处于挂起状态");
        }
        // 当前任务 task
        Task task = taskService.createTaskQuery().taskId(flowTaskVo.getTaskId()).singleResult();
        // 获取流程定义信息
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery().processDefinitionId(task.getProcessDefinitionId()).singleResult();
        // 获取所有节点信息
        Process process = repositoryService.getBpmnModel(processDefinition.getId()).getProcesses().get(0);
        // 获取全部节点列表，包含子节点
        Collection<FlowElement> allElements = FlowableUtils.getAllElements(process.getFlowElements(), null);
        // 获取当前任务节点元素
        FlowElement source = null;
        // 获取跳转的节点元素
        FlowElement target = null;
        if (allElements != null) {
            for (FlowElement flowElement : allElements) {
                // 当前任务节点元素
                if (flowElement.getId().equals(task.getTaskDefinitionKey())) {
                    source = flowElement;
                }
                // 跳转的节点元素
                if (flowElement.getId().equals(flowTaskVo.getTargetKey())) {
                    target = flowElement;
                }
            }
        }

        // 从当前节点向前扫描
        // 如果存在路线上不存在目标节点，说明目标节点是在网关上或非同一路线上，不可跳转
        // 否则目标节点相对于当前节点，属于串行
        Boolean isSequential = FlowableUtils.iteratorCheckSequentialReferTarget(source, flowTaskVo.getTargetKey(), null, null);
        if (!isSequential) {
            throw new CustomException("当前节点相对于目标节点，不属于串行关系，无法回退");
        }


        // 获取所有正常进行的任务节点 Key，这些任务不能直接使用，需要找出其中需要撤回的任务
        List<Task> runTaskList = taskService.createTaskQuery().processInstanceId(task.getProcessInstanceId()).list();
        List<String> runTaskKeyList = new ArrayList<>();
        runTaskList.forEach(item -> runTaskKeyList.add(item.getTaskDefinitionKey()));
        // 需退回任务列表
        List<String> currentIds = new ArrayList<>();
        // 通过父级网关的出口连线，结合 runTaskList 比对，获取需要撤回的任务
        List<UserTask> currentUserTaskList = FlowableUtils.iteratorFindChildUserTasks(target, runTaskKeyList, null, null);
        currentUserTaskList.forEach(item -> {
            currentIds.add(item.getId());
        });

        // 循环获取那些需要被撤回的节点的ID，用来设置驳回原因
        List<String> currentTaskIds = new ArrayList<>();
        currentIds.forEach(currentId -> runTaskList.forEach(runTask -> {
            if (currentId.equals(runTask.getTaskDefinitionKey())) {
                currentTaskIds.add(runTask.getId());
            }
        }));
        // 设置回退意见
        for (String currentTaskId : currentTaskIds) {
            taskService.addComment(currentTaskId, task.getProcessInstanceId(), FlowComment.REBACK.getType(), flowTaskVo.getComment());
        }
        SysUser loginUser = iFlowThirdService.getLoginUser();
        try {
            // 设置处理人
            taskService.setAssignee(task.getId(), loginUser.getUsername());
            // 1 对 1 或 多 对 1 情况，currentIds 当前要跳转的节点列表(1或多)，targetKey 跳转到的节点(1)
            runtimeService.createChangeActivityStateBuilder()
                    .processInstanceId(task.getProcessInstanceId())
                    .moveActivityIdsToSingleActivityId(currentIds, flowTaskVo.getTargetKey()).changeState();

            /*======================退回  回调以及关键数据保存======================*/
            //业务数据id
            String dataId = flowTaskVo.getDataId();
            if (dataId==null) return;
            //如果保存数据前未调用必调的FlowCommonService.initActBusiness方法，就会有问题
            FlowMyBusiness business = flowMyBusinessService.getByDataId(dataId);
            //spring容器类名
            String serviceImplName = business.getServiceImplName();
            FlowCallBackServiceI flowCallBackService = (FlowCallBackServiceI) SpringContextUtils.getBean(serviceImplName);
            //设置数据
            String doneUsers = business.getDoneUsers();
            // 处理过流程的人
            JSONArray doneUserList = new JSONArray();
            if (StrUtil.isNotBlank(doneUsers)){
                doneUserList = JSON.parseArray(doneUsers);
            }

            if (!doneUserList.contains(loginUser.getUsername())){
                doneUserList.add(loginUser.getUsername());
            }
                //**跳转到目标节点
            List<Task> task2List = taskService.createTaskQuery().processInstanceId(business.getProcessInstanceId()).active().list();
            Task targetTask = task2List.get(0);
                business.setBpmStatus(ActStatus.reject.getValue())
                        .setTaskId(targetTask.getId())
                        .setTaskNameId(targetTask.getTaskDefinitionKey())
                        .setTaskName(targetTask.getName())
                        .setPriority(targetTask.getPriority()+"")
                        .setDoneUsers(doneUserList.toJSONString())
                ;
            if (target!=null){
                UserTask target2 = (UserTask) target;
                business.setPriority(target2.getPriority());
                if (StrUtil.equals(business.getTaskNameId(),ProcessConstants.START_NODE)){
                //    开始节点。设置处理人为申请人
                    business.setTodoUsers(JSON.toJSONString(Lists.newArrayList(business.getProposer())));
                    taskService.setAssignee(business.getTaskId(),business.getProposer());
                } else {
                    List<SysUser> sysUserFromTask = getSysUserFromTask(target2);
                    List<String> collect_username = sysUserFromTask.stream().map(SysUser::getUsername).collect(Collectors.toList());
                    List<String> candidateUsers = flowTaskVo.getCandidateUsers();
                    if (CollUtil.isNotEmpty(candidateUsers)){
                        collect_username = candidateUsers;
                    }
                    business.setTodoUsers(JSON.toJSONString(collect_username));
                    // 删除后重写
                    for (Task task2One : task2List) {
                        for (String oldUser : collect_username) {
                            taskService.deleteCandidateUser(task2One.getId(),oldUser);
                        }
                    }
                    Map<String, Object> values = flowTaskVo.getValues();
                    if (values==null){
                        values = MapUtil.newHashMap();
                        values.put("dataId",dataId);
                    } else {
                        values.put("dataId",dataId);
                    }
                    List<String> beforeParamsCandidateUsernames = flowCallBackService.flowCandidateUsernamesOfTask(targetTask.getTaskDefinitionKey(), values);
                    if (CollUtil.isNotEmpty(beforeParamsCandidateUsernames)){
                        if (CollUtil.isNotEmpty(candidateUsers)){
                            beforeParamsCandidateUsernames = candidateUsers;
                        }
                        // 业务层有指定候选人，覆盖
                        for (Task task2One : task2List) {
                            for (String newUser : beforeParamsCandidateUsernames) {
                                taskService.addCandidateUser(task2One.getId(),newUser);
                            }
                        }
                        business.setTodoUsers(JSON.toJSONString(beforeParamsCandidateUsernames));
                    } else {
                        for (Task task2One : task2List) {
                            for (String oldUser : collect_username) {
                                taskService.addCandidateUser(task2One.getId(), oldUser);
                            }
                        }
                    }
                }
            }
            flowMyBusinessService.updateById(business);
            // 流程处理完后，进行回调业务层
            business.setValues(flowTaskVo.getValues());
            if (flowCallBackService!=null) flowCallBackService.afterFlowHandle(business);
        } catch (FlowableObjectNotFoundException e) {
            throw new CustomException("未找到流程实例，流程可能已发生变化");
        } catch (FlowableException e) {
            throw new CustomException("无法取消或开始活动");
        }
    }

    @Override
    public Result<List<UserTask>> findReturnTaskListByDataId(FlowTaskVo flowTaskVo) {
        FlowMyBusiness business = flowMyBusinessService.getByDataId(flowTaskVo.getDataId());
        flowTaskVo.setTaskId(business.getTaskId());
        return findReturnTaskList(flowTaskVo);
    }
    /**
     * 获取所有可回退的节点
     *
     * @param flowTaskVo
     * @return
     */
    @Override
    public Result<List<UserTask>> findReturnTaskList(FlowTaskVo flowTaskVo) {
        // 当前任务 task
        Task task = taskService.createTaskQuery().taskId(flowTaskVo.getTaskId()).singleResult();
        // 获取流程定义信息
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery().processDefinitionId(task.getProcessDefinitionId()).singleResult();
        // 获取所有节点信息，暂不考虑子流程情况
        Process process = repositoryService.getBpmnModel(processDefinition.getId()).getProcesses().get(0);
        Collection<FlowElement> flowElements = process.getFlowElements();
        // 获取当前任务节点元素
        UserTask source = null;
        if (flowElements != null) {
            for (FlowElement flowElement : flowElements) {
                // 类型为用户节点
                if (flowElement.getId().equals(task.getTaskDefinitionKey())) {
                    source = (UserTask) flowElement;
                }
            }
        }
        // 获取节点的所有路线
        List<List<UserTask>> roads = FlowableUtils.findRoad(source, null, null, null);
        // 可回退的节点列表
        List<UserTask> userTaskList = new ArrayList<>();
        for (List<UserTask> road : roads) {
            if (userTaskList.size() == 0) {
                // 还没有可回退节点直接添加
                userTaskList = road;
            } else {
                // 如果已有回退节点，则比对取交集部分
                userTaskList.retainAll(road);
            }
        }
        return Result.OK(userTaskList);
    }

    /**
     * 删除任务
     *
     * @param flowTaskVo 请求实体参数
     */
    @Override
    public void deleteTask(FlowTaskVo flowTaskVo) {
        // todo 待确认删除任务是物理删除任务 还是逻辑删除，让这个任务直接通过？
        taskService.deleteTask(flowTaskVo.getTaskId(),flowTaskVo.getComment());
    }

    /**
     * 认领/签收任务
     *
     * @param flowTaskVo 请求实体参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void claim(FlowTaskVo flowTaskVo) {
        taskService.claim(flowTaskVo.getTaskId(), flowTaskVo.getUserId());
    }

    /**
     * 取消认领/签收任务
     *
     * @param flowTaskVo 请求实体参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unClaim(FlowTaskVo flowTaskVo) {
        taskService.unclaim(flowTaskVo.getTaskId());
    }

    /**
     * 委派任务
     *
     * @param flowTaskVo 请求实体参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delegateTask(FlowTaskVo flowTaskVo) {
        taskService.delegateTask(flowTaskVo.getTaskId(), flowTaskVo.getAssignee());
    }


    /**
     * 转办任务
     *
     * @param flowTaskVo 请求实体参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignTask(FlowTaskVo flowTaskVo) {
        taskService.setAssignee(flowTaskVo.getTaskId(),flowTaskVo.getComment());
    }

    /**
     * 我发起的流程
     *
     * @param pageNum
     * @param pageSize
     * @return
     */
    @Override
    public Result<Page<FlowTaskDto>> myProcess(Integer pageNum, Integer pageSize) {
        Page<FlowTaskDto> page = new Page<>();
        String username = iFlowThirdService.getLoginUser().getUsername();
        HistoricProcessInstanceQuery historicProcessInstanceQuery = historyService.createHistoricProcessInstanceQuery()
                .startedBy(username)
                .orderByProcessInstanceStartTime()
                .desc();
        List<HistoricProcessInstance> historicProcessInstances = historicProcessInstanceQuery.listPage((pageNum - 1)*pageSize, pageSize);
        page.setTotal(historicProcessInstanceQuery.count());
        List<FlowTaskDto> flowList = new ArrayList<>();
        for (HistoricProcessInstance hisIns : historicProcessInstances) {
            FlowTaskDto flowTask = new FlowTaskDto();
            flowTask.setCreateTime(hisIns.getStartTime());
            flowTask.setFinishTime(hisIns.getEndTime());
            flowTask.setProcInsId(hisIns.getId());

            // 计算耗时
            if (Objects.nonNull(hisIns.getEndTime())) {
                long time = hisIns.getEndTime().getTime() - hisIns.getStartTime().getTime();
                flowTask.setDuration(getDate(time));
            } else {
                long time = System.currentTimeMillis() - hisIns.getStartTime().getTime();
                flowTask.setDuration(getDate(time));
            }
            // 流程定义信息
            ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionId(hisIns.getProcessDefinitionId())
                    .singleResult();
            flowTask.setDeployId(pd.getDeploymentId());
            flowTask.setProcDefName(pd.getName());
            flowTask.setProcDefVersion(pd.getVersion());
            flowTask.setCategory(pd.getCategory());
            flowTask.setProcDefVersion(pd.getVersion());
            // 当前所处流程 todo: 本地启动放开以下注释
            List<Task> taskList = taskService.createTaskQuery().processInstanceId(hisIns.getId()).list();
            if (CollUtil.isNotEmpty(taskList)) {
                flowTask.setTaskId(taskList.get(0).getId());
            } else {
                List<HistoricTaskInstance> historicTaskInstance = historyService.createHistoricTaskInstanceQuery().processInstanceId(hisIns.getId()).orderByHistoricTaskInstanceEndTime().desc().list();
                flowTask.setTaskId(historicTaskInstance.get(0).getId());
            }
            flowList.add(flowTask);
        }
        page.setRecords(flowList);
        return Result.OK(page);
    }

    /**
     * 取消申请
     *
     * @param flowTaskVo
     * @return
     */
    @Override
    public Result stopProcess(FlowTaskVo flowTaskVo) {
        List<Task> task = taskService.createTaskQuery().processInstanceId(flowTaskVo.getInstanceId()).list();
        if (CollUtil.isEmpty(task)) {
            throw new CustomException("流程未启动或已执行完成，取消申请失败");
        }

        SysUser loginUser = iFlowThirdService.getLoginUser();
        ProcessInstance processInstance =
                runtimeService.createProcessInstanceQuery().processInstanceId(flowTaskVo.getInstanceId()).singleResult();
        BpmnModel bpmnModel = repositoryService.getBpmnModel(processInstance.getProcessDefinitionId());
        if (Objects.nonNull(bpmnModel)) {
            Process process = bpmnModel.getMainProcess();
            List<EndEvent> endNodes = process.findFlowElementsOfType(EndEvent.class, false);
            if (CollUtil.isNotEmpty(endNodes)) {
                Authentication.setAuthenticatedUserId(loginUser.getUsername());
                taskService.addComment(task.get(0).getId(), processInstance.getProcessInstanceId(), FlowComment.STOP.getType(),
                        StringUtils.isBlank(flowTaskVo.getComment()) ? "取消申请" : flowTaskVo.getComment());
                String endId = endNodes.get(0).getId();
                List<Execution> executions =
                        runtimeService.createExecutionQuery().parentId(processInstance.getProcessInstanceId()).list();
                List<String> executionIds = new ArrayList<>();
                executions.forEach(execution -> executionIds.add(execution.getId()));
                runtimeService.createChangeActivityStateBuilder().moveExecutionsToSingleActivityId(executionIds,
                        endId).changeState();
            }
        }

        return Result.OK();
    }

    /**
     * 撤回流程  todo 目前存在错误
     *
     * @param flowTaskVo
     * @return
     */
    @Override
    public Result revokeProcess(FlowTaskVo flowTaskVo) {
        Task task = taskService.createTaskQuery().processInstanceId(flowTaskVo.getInstanceId()).singleResult();
        if (task == null) {
            throw new CustomException("流程未启动或已执行完成，无法撤回");
        }

        SysUser loginUser = iFlowThirdService.getLoginUser();
        List<HistoricTaskInstance> htiList = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(task.getProcessInstanceId())
                .orderByTaskCreateTime()
                .asc()
                .list();
        String myTaskId = null;
        HistoricTaskInstance myTask = null;
        for (HistoricTaskInstance hti : htiList) {
            if (loginUser.getUsername().toString().equals(hti.getAssignee())) {
                myTaskId = hti.getId();
                myTask = hti;
                break;
            }
        }
        if (null == myTaskId) {
            throw new CustomException("该任务非当前用户提交，无法撤回");
        }

        String processDefinitionId = myTask.getProcessDefinitionId();
        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);

        //变量
//      Map<String, VariableInstance> variables = runtimeService.getVariableInstances(currentTask.getExecutionId());
        String myActivityId = null;
        List<HistoricActivityInstance> haiList = historyService.createHistoricActivityInstanceQuery()
                .executionId(myTask.getExecutionId()).finished().list();
        for (HistoricActivityInstance hai : haiList) {
            if (myTaskId.equals(hai.getTaskId())) {
                myActivityId = hai.getActivityId();
                break;
            }
        }
        FlowNode myFlowNode = (FlowNode) bpmnModel.getMainProcess().getFlowElement(myActivityId);

        Execution execution = runtimeService.createExecutionQuery().executionId(task.getExecutionId()).singleResult();
        String activityId = execution.getActivityId();
        FlowNode flowNode = (FlowNode) bpmnModel.getMainProcess().getFlowElement(activityId);

        //记录原活动方向
        List<SequenceFlow> oriSequenceFlows = new ArrayList<>(flowNode.getOutgoingFlows());


        return Result.OK();
    }

    /**
     * 待办任务列表，包括本人就是处理人的或者还是候选人状态的任务
     *
     * @param pageNum  当前页码
     * @param pageSize 每页条数
     * @return
     */
    @Override
    public Result<Page<FlowTaskDto>> todoList(Integer pageNum, Integer pageSize,
                                              String itemName,
                                              java.util.Date applyTimeBegin,
                                              java.util.Date applyTimeEnd,
                                              String applyKeshi,
                                              String applyPeople) {
            Page<FlowTaskDto> page = new Page<>();
            String username = iFlowThirdService.getLoginUser().getUsername();
            TaskQuery taskQuery = taskService.createTaskQuery();
            taskQuery.taskCandidateOrAssigned(username).orderByTaskCreateTime().desc();
            if (!StrUtil.isAllEmpty(itemName, applyKeshi, applyPeople) || (applyTimeBegin != null && applyTimeEnd != null)) {
                List<String> insIds = flowMyBusinessService.getProcessInstanceIds(itemName, applyTimeBegin, applyTimeEnd, applyKeshi, applyPeople);
                if (CollUtil.isEmpty(insIds)) {
                    page.setTotal(0);
                    return Result.OK(page);
                }
                taskQuery.processInstanceIdIn(insIds);
            }
            List<Task> taskList = taskQuery.listPage((pageNum - 1) * pageSize, pageSize);
            List<FlowTaskDto> flowList = new ArrayList<>();

            //流程业务信息
            List<String> processInstanceIds = taskList.stream().map(TaskInfo::getProcessInstanceId).collect(Collectors.toList());
            Map<String, FlowMyBusiness> flowMyBusinessMap = MapUtil.empty();
            if (CollUtil.isNotEmpty(processInstanceIds)) {
                flowMyBusinessMap = flowMyBusinessService.getByProcessInstanceIds(processInstanceIds);
            }

            List<SysCategory> allCategory = iFlowThirdService.getAllCategory();
            for (Task task : taskList) {
                FlowTaskDto flowTask = new FlowTaskDto();
                // 当前流程信息
                flowTask.setTaskId(task.getId());
                flowTask.setTaskDefKey(task.getTaskDefinitionKey());
                flowTask.setCreateTime(task.getCreateTime());
                flowTask.setProcDefId(task.getProcessDefinitionId());
                flowTask.setTaskName(task.getName());
                // 流程定义信息
                ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
                        .processDefinitionId(task.getProcessDefinitionId())
                        .singleResult();
                flowTask.setDeployId(pd.getDeploymentId());
                flowTask.setProcDefName(pd.getName());
                flowTask.setProcDefVersion(pd.getVersion());
                flowTask.setProcInsId(task.getProcessInstanceId());
                flowTask.setCategory(pd.getCategory());
                String category_dictText = CollUtil.emptyIfNull(allCategory).stream().filter(sysCategory -> StrUtil.equals(pd.getCategory(), sysCategory.getId())).map(SysCategory::getName).findFirst().orElse("");
                flowTask.setCategory_dictText(category_dictText);

                // 流程发起人信息
                HistoricProcessInstance historicProcessInstance = historyService.createHistoricProcessInstanceQuery()
                        .processInstanceId(task.getProcessInstanceId())
                        .singleResult();
                SysUser startUser = iFlowThirdService.getUserByUsername(historicProcessInstance.getStartUserId());
                List<String> departNamesByUsername = iFlowThirdService.getDepartNamesByUsername(historicProcessInstance.getStartUserId());
                flowTask.setStartUserId(startUser.getUsername());
                flowTask.setStartUserName(startUser.getRealname());
                flowTask.setStartDeptName(CollUtil.join(departNamesByUsername, "，"));

                //流程业务信息
                FlowMyBusiness flowMyBusiness = flowMyBusinessMap.get(task.getProcessInstanceId());
                if (flowMyBusiness != null) {
                    flowTask.setTitle(flowMyBusiness.getTitle());
                    flowTask.setBpmStatus(flowMyBusiness.getBpmStatus());
                    flowTask.setDataId(flowMyBusiness.getDataId());
                    flowTask.setTodoUsers(flowMyBusiness.getTodoUsers());
                    flowTask.setDoneUsers(flowMyBusiness.getDoneUsers());
                    flowTask.setJimuReportId(flowMyBusiness.getJimuReportId());
                    flowTask.setPcFormUrl(flowMyBusiness.getPcFormUrl());
                }
                flowList.add(flowTask);
            }

            page.setRecords(flowList);
            return Result.OK(page);
        }


    /**
     * 已办任务列表
     *
     * @param pageNum  当前页码
     * @param pageSize 每页条数
     * @return
     */
    @Override
    public Result<Page<FlowTaskDto>> finishedList (Integer pageNum, Integer pageSize,String itemName,
                                                   java.util.Date applyTimeBegin,
                                                   java.util.Date applyTimeEnd,
                                                   String applyKeshi,
                                                   String applyPeople) {
        Page<FlowTaskDto> page = new Page<>();
        String username = iFlowThirdService.getLoginUser().getUsername();
        HistoricTaskInstanceQuery taskInstanceQuery = historyService.createHistoricTaskInstanceQuery()
                .includeProcessVariables()
                .finished()
                .taskAssignee(username)
                .orderByHistoricTaskInstanceEndTime()
                .desc();

        if (!StrUtil.isAllEmpty(itemName, applyKeshi, applyPeople) || (applyTimeBegin != null && applyTimeEnd != null)) {
            List<String> insIds = flowMyBusinessService.getProcessInstanceIds(itemName, applyTimeBegin, applyTimeEnd, applyKeshi, applyPeople);
            if (CollUtil.isEmpty(insIds)) {
                page.setTotal(0);
                return Result.OK(page);
            }
            taskInstanceQuery.processInstanceIdIn(insIds);
        }

        List<HistoricTaskInstance> historicTaskInstanceList = taskInstanceQuery.listPage((pageNum - 1) * pageSize, pageSize);
        List<FlowTaskDto> hisTaskList = Lists.newArrayList();

        //流程业务信息
        List<String> processInstanceIds = historicTaskInstanceList.stream().map(HistoricTaskInstance::getProcessInstanceId).collect(Collectors.toList());
        Map<String, FlowMyBusiness> flowMyBusinessMap = MapUtil.empty();
        if (CollUtil.isNotEmpty(processInstanceIds)) {
            flowMyBusinessMap = flowMyBusinessService.getByProcessInstanceIds(processInstanceIds);
        }
        List<SysCategory> allCategory = iFlowThirdService.getAllCategory();
        for (HistoricTaskInstance histTask : historicTaskInstanceList) {
            FlowTaskDto flowTask = new FlowTaskDto();
            // 当前流程信息
            flowTask.setTaskId(histTask.getId());
            // 审批人员信息
            flowTask.setCreateTime(histTask.getCreateTime());
            flowTask.setFinishTime(histTask.getEndTime());
            flowTask.setDuration(getDate(histTask.getDurationInMillis()));
            flowTask.setProcDefId(histTask.getProcessDefinitionId());
            flowTask.setTaskDefKey(histTask.getTaskDefinitionKey());
            flowTask.setTaskName(histTask.getName());

            // 流程定义信息
            ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionId(histTask.getProcessDefinitionId())
                    .singleResult();
            flowTask.setDeployId(pd.getDeploymentId());
            flowTask.setProcDefName(pd.getName());
            flowTask.setProcDefVersion(pd.getVersion());
            flowTask.setProcInsId(histTask.getProcessInstanceId());
            flowTask.setHisProcInsId(histTask.getProcessInstanceId());
            flowTask.setCategory(pd.getCategory());
            String category_dictText = CollUtil.emptyIfNull(allCategory).stream().filter(sysCategory -> StrUtil.equals(pd.getCategory(), sysCategory.getId())).map(SysCategory::getName).findFirst().orElse("");
            flowTask.setCategory_dictText(category_dictText);

            // 流程发起人信息
            HistoricProcessInstance historicProcessInstance = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(histTask.getProcessInstanceId())
                    .singleResult();
            SysUser startUser = iFlowThirdService.getUserByUsername(historicProcessInstance.getStartUserId());
            flowTask.setStartUserId(startUser.getUsername());
            flowTask.setStartUserName(startUser.getRealname());
            List<String> departNamesByUsername = iFlowThirdService.getDepartNamesByUsername(historicProcessInstance.getStartUserId());
            flowTask.setStartDeptName(CollUtil.join(departNamesByUsername, "，"));

            //流程业务信息
            FlowMyBusiness flowMyBusiness = flowMyBusinessMap.get(histTask.getProcessInstanceId());
            if (flowMyBusiness != null) {
                flowTask.setTitle(flowMyBusiness.getTitle());
                flowTask.setBpmStatus(flowMyBusiness.getBpmStatus());
                flowTask.setDataId(flowMyBusiness.getDataId());
                flowTask.setTodoUsers(flowMyBusiness.getTodoUsers());
                flowTask.setDoneUsers(flowMyBusiness.getDoneUsers());
                flowTask.setJimuReportId(flowMyBusiness.getJimuReportId());
                flowTask.setPcFormUrl(flowMyBusiness.getPcFormUrl());
            }

            hisTaskList.add(flowTask);
        }
        page.setTotal(taskInstanceQuery.count());
        page.setRecords(hisTaskList);
//        Map<String, Object> result = new HashMap<>();
//        result.put("result",page);
//        result.put("finished",true);
        return Result.OK(page);
    }

    private static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }

    /**
     * 流程历史流转记录
     *
     * @param dataId 流程数据Id
     * @return
     */
    @Override
    public Map<String, Object> flowRecord(String dataId) {
        FlowMyBusiness business = flowMyBusinessService.getByDataId(dataId);
        String procInsId = business.getProcessInstanceId();
        Map<String, Object> map = new HashMap<String, Object>();
        if (StringUtils.isNotBlank(procInsId)) {
            List<HistoricActivityInstance> list = historyService
                    .createHistoricActivityInstanceQuery()
                    .processInstanceId(procInsId)
                    .orderByHistoricActivityInstanceStartTime()
                    .desc().list();
            List<FlowTaskDto> hisFlowList = new ArrayList<>();
            for (HistoricActivityInstance histIns : list) {
                if (StringUtils.isNotBlank(histIns.getTaskId())) {
                    FlowTaskDto flowTask = new FlowTaskDto();
                    flowTask.setTaskId(histIns.getTaskId());
                    flowTask.setTaskName(histIns.getActivityName());
                    flowTask.setTaskDefKey(histIns.getActivityId());
                    flowTask.setCreateTime(histIns.getStartTime());
                    flowTask.setFinishTime(histIns.getEndTime());
                    if (StringUtils.isNotBlank(histIns.getAssignee())) {
                        SysUser sysUser = iFlowThirdService.getUserByUsername(histIns.getAssignee());
                        flowTask.setAssigneeId(sysUser.getUsername());
                        flowTask.setAssigneeName(sysUser.getRealname());
                        List<String> departNamesByUsername = iFlowThirdService.getDepartNamesByUsername(histIns.getAssignee());
                        flowTask.setDeptName(CollUtil.join(departNamesByUsername,"，"));
                        if (StrUtil.equals(histIns.getActivityId(),ProcessConstants.START_NODE)){
                        //    开始节点，把候选人设置为发起人，这个值已被其他地方设置过，与实际办理人一致即可
                            flowTask.setCandidate(sysUser.getRealname());
                        }
                    }
                    // 展示审批人员
                    List<HistoricIdentityLink> linksForTask = historyService.getHistoricIdentityLinksForTask(histIns.getTaskId());
                    StringBuilder stringBuilder = new StringBuilder();
                    for (HistoricIdentityLink identityLink : linksForTask) {
                        if (IdentityLinkType.CANDIDATE.equals(identityLink.getType())) {
                            if (StringUtils.isNotBlank(identityLink.getUserId())) {
                                SysUser sysUser = iFlowThirdService.getUserByUsername(identityLink.getUserId());
                                stringBuilder.append(sysUser.getRealname()).append(",");
                            }
                            /*已经全部设置到 CANDIDATE 了，不拿组了*/
                            /*if (StringUtils.isNotBlank(identityLink.getGroupId())) {
                                List<SysRole> allRole = iFlowThirdService.getAllRole();
                                SysRole sysRole = allRole.stream().filter(o -> StringUtils.equals(identityLink.getGroupId(), o.getId())).findAny().orElse(new SysRole());
                                stringBuilder.append(sysRole.getRoleName()).append(",");
                            }*/
                        }
                    }
                    if (StringUtils.isNotBlank(stringBuilder)) {
                        flowTask.setCandidate(stringBuilder.substring(0, stringBuilder.length() - 1));
                    }

                    flowTask.setDuration(histIns.getDurationInMillis() == null || histIns.getDurationInMillis() == 0 ? null : getDate(histIns.getDurationInMillis()));
                    // 获取意见评论内容
                    List<Comment> commentList = taskService.getProcessInstanceComments(histIns.getProcessInstanceId());
                    commentList.forEach(comment -> {
                        if (histIns.getTaskId().equals(comment.getTaskId())) {
                            flowTask.setComment(FlowCommentDto.builder().type(comment.getType()).comment(comment.getFullMessage()).build());
                        }
                    });
                    hisFlowList.add(flowTask);
                }
            }
            map.put("flowList", hisFlowList);
        }
        // 获取初始化表单
        String serviceImplName = business.getServiceImplName();
        FlowCallBackServiceI flowCallBackService = (FlowCallBackServiceI) SpringContextUtils.getBean(serviceImplName);
        // 流程处理完后，进行回调业务层
        if (flowCallBackService!=null){
            Object businessDataById = flowCallBackService.getBusinessDataById(dataId);
            map.put("formData",businessDataById);
        }
        return map;
    }

    /**
     * 查看积木报表单据
     * @param dataId 关联表单ID
     * @return
     */
    @Override
    public Map<String, Object> jimuReportData(String dataId) {
        Map<String, Object> flowRecordMap = flowRecord(dataId);
        Object formData = flowRecordMap.get("formData");

        Map<String, Object> resultMap = BeanUtil.beanToMap(formData);
        List<FlowTaskDto> flowList = (List<FlowTaskDto>) flowRecordMap.get("flowList");

        Map<String, FlowTaskDto> taskDtoMap = flowList.stream().collect(Collectors.toMap(FlowTaskDto::getTaskDefKey, e->e, (flowTaskDto1,flowTaskDto2) -> DateUtil.compare(flowTaskDto2.getFinishTime(),flowTaskDto1.getFinishTime()) > 0 ? flowTaskDto2 : flowTaskDto1));

        for (FlowTaskDto flowTaskDto : taskDtoMap.values()) {
            String taskDefKey = flowTaskDto.getTaskDefKey();
            FlowCommentDto comment = flowTaskDto.getComment();
            resultMap.put(taskDefKey + "-taskName",flowTaskDto.getTaskName());
            resultMap.put(taskDefKey + "-commentType",comment.getType());
            resultMap.put(taskDefKey + "-comment", comment.getComment());
            resultMap.put(taskDefKey + "-assigneeId",flowTaskDto.getAssigneeId());
            resultMap.put(taskDefKey + "-assigneeName",flowTaskDto.getAssigneeName());
            resultMap.put(taskDefKey + "-deptName",flowTaskDto.getDeptName());
            resultMap.put(taskDefKey + "-candidate",flowTaskDto.getCandidate());
            resultMap.put(taskDefKey + "-finishTime", DateUtil.format(flowTaskDto.getFinishTime(), DatePattern.CHINESE_DATE_FORMAT));
        }

        return resultMap;
    }

    /**
     * 根据任务ID查询挂载的表单信息
     *
     * @param taskId 任务Id
     * @return
     */
    @Override
    public Task getTaskForm(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        return task;
    }

    /**
     * 获取流程过程图
     *
     * @param processId
     * @return
     */
    @Override
    public InputStream diagram(String processId) {
        String processDefinitionId;
        // 获取当前的流程实例
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery().processInstanceId(processId).singleResult();
        // 如果流程已经结束，则得到结束节点
        if (Objects.isNull(processInstance)) {
            HistoricProcessInstance pi = historyService.createHistoricProcessInstanceQuery().processInstanceId(processId).singleResult();

            processDefinitionId = pi.getProcessDefinitionId();
        } else {// 如果流程没有结束，则取当前活动节点
            // 根据流程实例ID获得当前处于活动状态的ActivityId合集
            ProcessInstance pi = runtimeService.createProcessInstanceQuery().processInstanceId(processId).singleResult();
            processDefinitionId = pi.getProcessDefinitionId();
        }

        // 获得活动的节点
        List<HistoricActivityInstance> highLightedFlowList = historyService.createHistoricActivityInstanceQuery().processInstanceId(processId).orderByHistoricActivityInstanceStartTime().asc().list();

        List<String> highLightedFlows = new ArrayList<>();
        List<String> highLightedNodes = new ArrayList<>();
        //高亮线
        for (HistoricActivityInstance tempActivity : highLightedFlowList) {
            if ("sequenceFlow".equals(tempActivity.getActivityType())) {
                //高亮线
                highLightedFlows.add(tempActivity.getActivityId());
            } else {
                //高亮节点
                highLightedNodes.add(tempActivity.getActivityId());
            }
        }

        //获取流程图
        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
        ProcessEngineConfiguration configuration = processEngine.getProcessEngineConfiguration();
        //获取自定义图片生成器
        ProcessDiagramGenerator diagramGenerator = new CustomProcessDiagramGenerator();
        InputStream in = diagramGenerator.generateDiagram(bpmnModel, "png", highLightedNodes, highLightedFlows, configuration.getActivityFontName(),
                configuration.getLabelFontName(), configuration.getAnnotationFontName(), configuration.getClassLoader(), 1.0, true);
        return in;

    }

    /**
     * 获取流程执行过程
     *
     * @param procInsId
     * @return
     */
    @Override
    public List<FlowViewerDto> getFlowViewer(String procInsId) {
        List<FlowViewerDto> flowViewerList = new ArrayList<>();
        FlowViewerDto flowViewerDto;
        // 获得活动的节点
        List<HistoricActivityInstance> hisActIns = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(procInsId)
                .orderByHistoricActivityInstanceStartTime()
                .asc().list();
        for (HistoricActivityInstance activityInstance : hisActIns) {
            if (!"sequenceFlow".equals(activityInstance.getActivityType())) {
                flowViewerDto = new FlowViewerDto();
                flowViewerDto.setKey(activityInstance.getActivityId());
                flowViewerDto.setCompleted(!Objects.isNull(activityInstance.getEndTime()));

                for (FlowViewerDto viewerDto : flowViewerList) {
                    String key = viewerDto.getKey();
                    if (key.equals(flowViewerDto.getKey())){
                    //    重复删除后面更新
                        flowViewerList.remove(viewerDto);
                        break;
                    }
                }
                flowViewerList.add(flowViewerDto);
            }
        }

        return flowViewerList;
    }

    @Override
    public List<FlowViewerDto> getFlowViewerByDataId(String dataId) {
        LambdaQueryWrapper<FlowMyBusiness> flowMyBusinessLambdaQueryWrapper = new LambdaQueryWrapper<>();
        flowMyBusinessLambdaQueryWrapper.eq(FlowMyBusiness::getDataId,dataId)
        ;
        //如果保存数据前未调用必调的FlowCommonService.initActBusiness方法，就会有问题
        FlowMyBusiness business = flowMyBusinessService.getOne(flowMyBusinessLambdaQueryWrapper);
        // 1.执行过的步骤
        List<FlowViewerDto> flowViewers = this.getFlowViewer(business.getProcessInstanceId());
        // 2.获取所有节点信息，根据所有节点 按顺序  和执行过的比较，驳回的节点就被跳过
        Process process = repositoryService.getBpmnModel(business.getProcessDefinitionId()).getProcesses().get(0);
        List<FlowElement> flowElements = Lists.newArrayList(process.getFlowElements());
        // 获取当前任务节点元素
        List<FlowViewerDto> reflowViewers = Lists.newArrayList();
        // *顺序的Key
        List<String> orderKeys = Lists.newArrayList();
        if (flowElements != null) {
            for (FlowElement flowElement : flowElements) {
                try {
                    // 开始节点
                    StartEvent stev = (StartEvent) flowElement;
                    //第一个key节点，
                    String firstKey = stev.getId();
                    orderKeys.add(firstKey);
                    //顺序获取节点
                    this.appendKeys(orderKeys, firstKey,flowElements);
                } catch (Exception e) {
                    break;
                }

            }

            for (String key : orderKeys) {
                Optional<FlowViewerDto> any = flowViewers.stream().filter(o -> StrUtil.equals(o.getKey(), key)).findAny();
                if(any.isPresent()){
                    FlowViewerDto viewerDto = any.get();
                    reflowViewers.add(viewerDto);
                    if (!viewerDto.isCompleted()){
                    //    已到正在等待执行的节点，后面的不要了
                        break;
                    }
                }
            }
        }
        for (FlowViewerDto flowViewer : flowViewers) {
            boolean present = reflowViewers.stream().filter(o -> StrUtil.equals(o.getKey(), flowViewer.getKey())).findAny().isPresent();
            flowViewer.setBack(!present);
        }
        //return reflowViewers;
        return flowViewers;
    }

    /**
     * 顺序抽取节点
     * @param orderKeys 容器
     * @param sourceKey 源
     * @param flowElements 所有的节点对象
     */
    private void appendKeys(List<String> orderKeys, String sourceKey, List<FlowElement> flowElements) {
        for (FlowElement flowElement : flowElements) {
            try {
                SequenceFlow sf = (SequenceFlow) flowElement;
                String sourceRef = sf.getSourceRef();
                String targetRef = sf.getTargetRef();
                if (sourceKey.equals(sourceRef)&&targetRef!=null){
                    orderKeys.add(targetRef);
                    this.appendKeys(orderKeys,targetRef,flowElements);
                }
            } catch (Exception e) {
                continue;
            }

        }
    }

    /**
     * 获取流程变量
     *
     * @param taskId
     * @return
     */
    @Override
    public Result processVariables(String taskId) {
        // 流程变量
        HistoricTaskInstance historicTaskInstance = historyService.createHistoricTaskInstanceQuery().includeProcessVariables().finished().taskId(taskId).singleResult();
        if (Objects.nonNull(historicTaskInstance)) {
            return Result.OK(historicTaskInstance.getProcessVariables());
        } else {
            Map<String, Object> variables = taskService.getVariables(taskId);
            return Result.OK(variables);
        }
    }

    /**
     * 获取下一节点
     *
     * @param flowTaskVo 任务
     * @return
     */
    @Override
    public Result<FlowNextDto> getNextFlowNode(FlowTaskVo flowTaskVo) {
        // todo 似乎逻辑未写完，待检查
        FlowNextDto flowNextDto = this.getNextFlowNode(flowTaskVo.getTaskId(), flowTaskVo.getValues());
        if (flowNextDto==null) {
            return Result.OK("流程已完结", null);
        }
        return Result.OK(flowNextDto);
    }

    /**
     * 获取下一个节点信息,流程定义上的节点信息
     * @param taskId 当前节点id
     * @param values 流程变量
     * @return 如果返回null，表示没有下一个节点，流程结束
     */
    public FlowNextDto getNextFlowNode(String taskId, Map<String, Object> values) {
        // TODO: 2022/6/8 这里可能有 bug 到互斥网关的时候获取不到下一个节点 
        //当前节点
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (Objects.nonNull(task)) {
            // 下个任务节点
            List<UserTask> nextUserTask = FindNextNodeUtil.getNextUserTasks(repositoryService, task, values);
            if (CollUtil.isNotEmpty(nextUserTask)) {
                FlowNextDto flowNextDto = new FlowNextDto();
                for (UserTask userTask : nextUserTask) {
                    flowNextDto.setUserTask(userTask);
                    //待办人员
                    List<SysUser> sysUserFromTask = this.getSysUserFromTask(userTask);
                    flowNextDto.setUserList(sysUserFromTask);

                    MultiInstanceLoopCharacteristics   multiInstance =  userTask.getLoopCharacteristics();
                    if (Objects.nonNull(multiInstance)) {
                    //    会签  多实例
                        String collectionString = multiInstance.getInputDataItem();
                        Object colObj = values.get(collectionString);
                        List<String> userNameList = null;
                        if(colObj!=null){
                            userNameList = (List) colObj;
                        }
                        if (CollUtil.isNotEmpty(userNameList)){
                            // 待办人员从变量中获取  否则就是节点中配置的用户 sysUserFromTask
                            List<SysUser> userList = Lists.newArrayList();
                            for (String username : userNameList) {
                                SysUser userByUsername = iFlowThirdService.getUserByUsername(username);
                                if (userByUsername==null){
                                    throw new CustomException(username + " 用户名未找到");
                                } else {
                                    userList.add(userByUsername);
                                }
                            }
                            flowNextDto.setUserList(userList);
                        } else {
                            // 变量中没有传入，写入节点中配置的用户
                            List<String> collect_username = sysUserFromTask.stream().map(SysUser::getUsername).collect(Collectors.toList());
                            values.put(collectionString,collect_username);
                        }
                    } else {
                        // todo 读取自定义节点属性做些啥？
                        //String dataType = userTask.getAttributeValue(ProcessConstants.NAMASPASE, ProcessConstants.PROCESS_CUSTOM_DATA_TYPE);
                        String userType = userTask.getAttributeValue(ProcessConstants.NAMASPASE, ProcessConstants.PROCESS_CUSTOM_USER_TYPE);
                    }
                }
                return flowNextDto;
            }
        }
        return null;

    }
    public List<SysUser> getSysUserFromTask(UserTask userTask) {
        String assignee = userTask.getAssignee();
        if (StrUtil.isNotBlank(assignee)){
            // 指定单人
            SysUser userByUsername = iFlowThirdService.getUserByUsername(assignee);
            return Lists.newArrayList(userByUsername);
        }
        List<String> candidateUsers = userTask.getCandidateUsers();
        if (CollUtil.isNotEmpty(candidateUsers)){
            // 指定多人
            List<SysUser> list = iFlowThirdService.getAllUser();
            return list.stream().filter(o->candidateUsers.contains(o.getUsername())).collect(Collectors.toList());
        }
        List<String> candidateGroups = userTask.getCandidateGroups();
        if (CollUtil.isNotEmpty(candidateGroups)){
        //    指定多组
            List<SysUser> userList = Lists.newArrayList();
            for (String candidateGroup : candidateGroups) {
                List<SysUser> usersByRoleId = iFlowThirdService.getUsersByRoleId(candidateGroup);
                userList.addAll(usersByRoleId);
            }
            return userList;
        }
        return Lists.newArrayList();
    }
    /**
     * 流程完成时间处理
     *
     * @param ms
     * @return
     */
    private String getDate(long ms) {

        long day = ms / (24 * 60 * 60 * 1000);
        long hour = (ms / (60 * 60 * 1000) - day * 24);
        long minute = ((ms / (60 * 1000)) - day * 24 * 60 - hour * 60);
        long second = (ms / 1000 - day * 24 * 60 * 60 - hour * 60 * 60 - minute * 60);

        if (day > 0) {
            return day + "天" + hour + "小时" + minute + "分钟";
        }
        if (hour > 0) {
            return hour + "小时" + minute + "分钟";
        }
        if (minute > 0) {
            return minute + "分钟";
        }
        if (second > 0) {
            return second + "秒";
        } else {
            return 0 + "秒";
        }
    }
}
