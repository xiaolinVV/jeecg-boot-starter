package org.jeecg.modules.flowable.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.commons.io.IOUtils;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.image.ProcessDiagramGenerator;
import org.flowable.image.impl.DefaultProcessDiagramGenerator;
import org.flowable.task.api.Task;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.util.SpringContextUtils;
import org.jeecg.modules.flowable.apithird.business.entity.FlowMyBusiness;
import org.jeecg.modules.flowable.apithird.business.service.impl.FlowMyBusinessServiceImpl;
import org.jeecg.modules.flowable.apithird.entity.ActStatus;
import org.jeecg.modules.flowable.apithird.entity.SysUser;
import org.jeecg.modules.flowable.apithird.service.FlowCallBackServiceI;
import org.jeecg.modules.flowable.apithird.service.IFlowThirdService;
import org.jeecg.modules.flowable.common.constant.ProcessConstants;
import org.jeecg.modules.flowable.common.enums.FlowComment;
import org.jeecg.modules.flowable.domain.dto.FlowNextDto;
import org.jeecg.modules.flowable.domain.dto.FlowProcDefDto;
import org.jeecg.modules.flowable.factory.FlowServiceFactory;
import org.jeecg.modules.flowable.service.IFlowDefinitionService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 流程定义
 *
 */
@Service
public class FlowDefinitionServiceImpl extends FlowServiceFactory implements IFlowDefinitionService {
    @Autowired
    IFlowThirdService iFlowThirdService;
    @Autowired
    FlowMyBusinessServiceImpl flowMyBusinessService;
    @Autowired
    FlowTaskServiceImpl flowTaskService;

    private static final String BPMN_FILE_SUFFIX = ".bpmn";

    @Override
    public boolean exist(String processDefinitionKey) {
        ProcessDefinitionQuery processDefinitionQuery
                = repositoryService.createProcessDefinitionQuery().processDefinitionKey(processDefinitionKey);
        long count = processDefinitionQuery.count();
        return count > 0 ? true : false;
    }


    /**
     * 流程定义列表
     *
     * @param pageNum  当前页码
     * @param pageSize 每页条数
     * @param flowProcDefDto
     * @return 流程定义分页列表数据
     */
    @Override
    public Page<FlowProcDefDto> list(Integer pageNum, Integer pageSize, FlowProcDefDto flowProcDefDto) {
        Page<FlowProcDefDto> page = new Page<>();
        // 流程定义列表数据查询
        ProcessDefinitionQuery processDefinitionQuery = repositoryService.createProcessDefinitionQuery();
        processDefinitionQuery
                //.processDefinitionId("cs:5:15e953ed-4d09-11ec-85b8-e884a5deddfc")
                .orderByProcessDefinitionKey().asc().orderByProcessDefinitionVersion().desc();
        /*=====参数=====*/
        if (StrUtil.isNotBlank(flowProcDefDto.getName())){
            processDefinitionQuery.processDefinitionNameLike("%"+flowProcDefDto.getName()+"%");
        }
        if (StrUtil.isNotBlank(flowProcDefDto.getCategory())){
            processDefinitionQuery.processDefinitionCategory(flowProcDefDto.getCategory());
        }
        if (flowProcDefDto.getSuspensionState() == 1){
            processDefinitionQuery.active();
        }
        if (StrUtil.isNotBlank(flowProcDefDto.getKey())){
            processDefinitionQuery.processDefinitionKey(flowProcDefDto.getKey());
        }
        if (flowProcDefDto.getIsLastVersion() == 1){
            processDefinitionQuery.latestVersion();
        }
        /*============*/
        page.setTotal(processDefinitionQuery.count());
        List<ProcessDefinition> processDefinitionList = processDefinitionQuery.listPage((pageNum - 1) * pageSize, pageSize);

        List<FlowProcDefDto> dataList = new ArrayList<>();
        for (ProcessDefinition processDefinition : processDefinitionList) {
            String deploymentId = processDefinition.getDeploymentId();
            Deployment deployment = repositoryService.createDeploymentQuery().deploymentId(deploymentId).singleResult();
            FlowProcDefDto reProcDef = new FlowProcDefDto();
            BeanUtils.copyProperties(processDefinition, reProcDef);
            // 流程定义时间
            reProcDef.setDeploymentTime(deployment.getDeploymentTime());
            dataList.add(reProcDef);
        }
        page.setRecords(dataList);
        return page;
    }


    /**
     * 导入流程文件
     *
     * @param name
     * @param category
     * @param in
     */
    @Override
    public void importFile(String name, String category, InputStream in) {
        Deployment deploy = repositoryService.createDeployment().addInputStream(name + BPMN_FILE_SUFFIX, in).name(name).category(category).deploy();
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery().deploymentId(deploy.getId()).singleResult();
        repositoryService.setProcessDefinitionCategory(definition.getId(), category);

    }

    /**
     * 读取xml
     *
     * @param deployId
     * @return
     */
    @Override
    public Result readXml(String deployId) throws IOException {
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery().deploymentId(deployId).singleResult();
        InputStream inputStream = repositoryService.getResourceAsStream(definition.getDeploymentId(), definition.getResourceName());
        String result = IOUtils.toString(inputStream, StandardCharsets.UTF_8.name());
        return Result.OK("", result);
    }

    @Override
    public Result readXmlByDataId(String dataId) throws IOException {
        LambdaQueryWrapper<FlowMyBusiness> flowMyBusinessLambdaQueryWrapper = new LambdaQueryWrapper<>();
        flowMyBusinessLambdaQueryWrapper.eq(FlowMyBusiness::getDataId,dataId)
        ;
        //如果保存数据前未调用必调的FlowCommonService.initActBusiness方法，就会有问题
        FlowMyBusiness business = flowMyBusinessService.getOne(flowMyBusinessLambdaQueryWrapper);
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery().processDefinitionId(business.getProcessDefinitionId()).singleResult();
        InputStream inputStream = repositoryService.getResourceAsStream(definition.getDeploymentId(), definition.getResourceName());
        String result = IOUtils.toString(inputStream, StandardCharsets.UTF_8.name());
        return Result.OK("", result);
    }

    /**
     * 读取xml 根据业务Id
     *
     * @param dataId
     * @return
     */
    @Override
    public InputStream readImageByDataId(String dataId) {
        FlowMyBusiness business = flowMyBusinessService.getByDataId(dataId);

        String processId = business.getProcessInstanceId();
        ProcessInstance pi = runtimeService.createProcessInstanceQuery().processInstanceId(processId).singleResult();
        //流程走完的 显示全图
        if (pi == null) {
            ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery().processDefinitionId(business.getProcessDefinitionId()).singleResult();
            return this.readImage(processDefinition.getDeploymentId());
        }

        List<HistoricActivityInstance> historyProcess = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processId).list();
        List<String> activityIds = new ArrayList<>();
        List<String> flows = new ArrayList<>();
        //获取流程图
        BpmnModel bpmnModel = repositoryService.getBpmnModel(pi.getProcessDefinitionId());
        for (HistoricActivityInstance hi : historyProcess) {
            String activityType = hi.getActivityType();
            if (activityType.equals("sequenceFlow") || activityType.equals("exclusiveGateway")) {
                flows.add(hi.getActivityId());
            } else if (activityType.equals("userTask") || activityType.equals("startEvent")) {
                activityIds.add(hi.getActivityId());
            }
        }
        List<Task> tasks = taskService.createTaskQuery().processInstanceId(processId).list();
        for (Task task : tasks) {
            activityIds.add(task.getTaskDefinitionKey());
        }
        ProcessEngineConfiguration engConf = processEngine.getProcessEngineConfiguration();
        //定义流程画布生成器
        ProcessDiagramGenerator processDiagramGenerator = engConf.getProcessDiagramGenerator();
        InputStream in = processDiagramGenerator.generateDiagram(bpmnModel, "png", activityIds, flows, engConf.getActivityFontName(), engConf.getLabelFontName(), engConf.getAnnotationFontName(), engConf.getClassLoader(), 1.0, true);
        return in;
    }
    /**
     * 读取xml
     *
     * @param deployId
     * @return
     */
    @Override
    public InputStream readImage(String deployId) {
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery().deploymentId(deployId).singleResult();
        //获得图片流
        DefaultProcessDiagramGenerator diagramGenerator = new DefaultProcessDiagramGenerator();
        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinition.getId());
        //输出为图片
        return diagramGenerator.generateDiagram(
                bpmnModel,
                "png",
                Collections.emptyList(),
                Collections.emptyList(),
                "宋体",
                "宋体",
                "宋体",
                null,
                1.0,
                false);

    }

    /**
     * 根据流程定义ID启动流程实例
     *
     * @param procDefKey 流程定义Id
     * @param variables 流程变量
     * @return
     */
    @Override
    public Result startProcessInstanceByKey(String procDefKey, Map<String, Object> variables) {
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(procDefKey)
                .latestVersion().singleResult();
        return startProcessInstanceById(processDefinition.getId(),variables);
    }
    /**
     * 根据流程定义ID启动流程实例
     *
     * @param procDefId 流程定义Id
     * @param variables 流程变量
     * @return
     */
    @Override
    @Transactional
    public Result startProcessInstanceById(String procDefId, Map<String, Object> variables) {
            ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionId(procDefId)
                    .singleResult();
            if (Objects.nonNull(processDefinition) && processDefinition.isSuspended()) {
                return Result.error("流程已被挂起,请先激活流程");
            }
//           variables.put("skip", true);
//           variables.put(ProcessConstants.FLOWABLE_SKIP_EXPRESSION_ENABLED, true);
            // 设置流程发起人Id到流程中
            SysUser sysUser = iFlowThirdService.getLoginUser();
            identityService.setAuthenticatedUserId(sysUser.getUsername());
            variables.put(ProcessConstants.PROCESS_INITIATOR, sysUser.getUsername());
            ProcessInstance processInstance = runtimeService.startProcessInstanceById(procDefId, variables);
            // 给第一步申请人节点设置任务执行人和意见
            Task task = taskService.createTaskQuery().processInstanceId(processInstance.getProcessInstanceId()).active().singleResult();
            if (Objects.nonNull(task)) {
                taskService.addComment(task.getId(), processInstance.getProcessInstanceId(), FlowComment.NORMAL.getType(), sysUser.getRealname() + "发起流程申请");
                taskService.setAssignee(task.getId(), sysUser.getUsername());
                //taskService.complete(task.getId(), variables);
            }
        /*======================todo 启动之后  回调以及关键数据保存======================*/
        //业务数据id
        String dataId = variables.get("dataId").toString();
        //如果保存数据前未调用必调的FlowCommonService.initActBusiness方法，就会有问题
        FlowMyBusiness business = flowMyBusinessService.getByDataId(dataId);
        //设置数据
        FlowNextDto nextFlowNode = flowTaskService.getNextFlowNode(task.getId(), variables);
        taskService.complete(task.getId(), variables);
        //下一个实例节点  多实例会是一个list,随意取一个即可  数组中定义Key是一致的
        //Task task2 = taskService.createTaskQuery().processInstanceId(processInstance.getProcessInstanceId()).active().singleResult();
        List<Task> task2List = taskService.createTaskQuery().processInstanceId(processInstance.getProcessInstanceId()).active().list();
        Task task2 = null;
        if(task2List.size()>0) task2 = task2List.get(0);
        String doneUsers = business.getDoneUsers();
        // 处理过流程的人
        JSONArray doneUserList = new JSONArray();
        if (StrUtil.isNotBlank(doneUsers)){
            doneUserList = JSON.parseArray(doneUsers);
        }
        if (!doneUserList.contains(sysUser.getUsername())){
            doneUserList.add(sysUser.getUsername());
        }

        //部门ID、名称
        String proposerDeptName = String.join(",", iFlowThirdService.getDepartNamesByUsername(sysUser.getUsername()));
        String proposerDeptId = String.join(",", iFlowThirdService.getDepartIdsByUsername(sysUser.getUsername()));

        if (nextFlowNode!=null){
            //**有下一个节点
            UserTask nextTask = nextFlowNode.getUserTask();
            //能够处理下个节点的候选人
            List<SysUser> nextFlowNodeUserList = nextFlowNode.getUserList();

            List<String> collect_username = nextFlowNodeUserList.stream().map(SysUser::getUsername).collect(Collectors.toList());
            //spring容器类名
            String serviceImplName = business.getServiceImplName();
            FlowCallBackServiceI flowCallBackService = (FlowCallBackServiceI) SpringContextUtils.getBean(serviceImplName);
            List<String> beforeParamsCandidateUsernames = flowCallBackService.flowCandidateUsernamesOfTask(task2.getTaskDefinitionKey(), variables);

            //前端传入候选人列表 覆盖
            List<String> candidateUsers = StrUtil.split(Convert.toStr(variables.get("candidateUsers")), StrUtil.C_COMMA);
            beforeParamsCandidateUsernames =  CollUtil.isNotEmpty(candidateUsers) ? candidateUsers : beforeParamsCandidateUsernames;

            if (CollUtil.isNotEmpty(beforeParamsCandidateUsernames)){
                // 删除后重写
                for (Task task2One : task2List) {
                    for (String oldUser : collect_username) {
                        taskService.deleteCandidateUser(task2One.getId(),oldUser);
                    }
                }
                // 业务层有指定候选人，覆盖
                for (Task task2One : task2List) {
                    for (String newUser : beforeParamsCandidateUsernames) {
                        taskService.addCandidateUser(task2One.getId(),newUser);
                    }
                }
                collect_username = beforeParamsCandidateUsernames;
            }

            business.setProcessDefinitionId(procDefId)
                    .setProcessInstanceId(processInstance.getProcessInstanceId())
                    .setBpmStatus(ActStatus.start.getValue())
                    .setProposer(sysUser.getUsername())
                    .setProposerName(sysUser.getRealname())
                    .setProposerDeptName(proposerDeptName)
                    .setProposerDeptId(proposerDeptId)
                    .setTaskId(task2.getId())
                    .setTaskName(nextTask.getName())
                    .setTaskNameId(nextTask.getId())
                    .setPriority(nextTask.getPriority())
                    .setDoneUsers(doneUserList.toJSONString())
                    .setTodoUsers(JSON.toJSONString(collect_username))
            ;
        } else {
        //    **没有下一个节点，流程已经结束了
            business.setProcessDefinitionId(procDefId)
                    .setProcessInstanceId(processInstance.getProcessInstanceId())
                    .setBpmStatus(ActStatus.pass.getValue())
                    .setProposer(sysUser.getUsername())
                    .setProposerName(sysUser.getRealname())
                    .setProposerDeptName(proposerDeptName)
                    .setProposerDeptId(proposerDeptId)
                    .setProposerDeptName(proposerDeptName)
                    .setDoneUsers(doneUserList.toJSONString())
            ;
        }
        flowMyBusinessService.updateById(business);
        //spring容器类名
        String serviceImplName = business.getServiceImplName();
        FlowCallBackServiceI flowCallBackService = (FlowCallBackServiceI) SpringContextUtils.getBean(serviceImplName);
        // 流程处理完后，进行回调业务层
        business.setValues(variables);
        if (flowCallBackService!=null)flowCallBackService.afterFlowHandle(business);
        return Result.OK("流程启动成功");
    }

    @Override
    public Result startProcessInstanceByDataId(String dataId, Map<String, Object> variables) {
        LambdaQueryWrapper<FlowMyBusiness> flowMyBusinessLambdaQueryWrapper = new LambdaQueryWrapper<>();
        flowMyBusinessLambdaQueryWrapper.eq(FlowMyBusiness::getDataId, dataId)
        ;
        FlowMyBusiness business = flowMyBusinessService.getOne(flowMyBusinessLambdaQueryWrapper);
        if (business==null){
            return Result.error("未找到dataId："+dataId);
        }
        if (StrUtil.isNotBlank(business.getProcessDefinitionId())){
            return this.startProcessInstanceById(business.getProcessDefinitionId(),variables);
        }
        return this.startProcessInstanceByKey(business.getProcessDefinitionKey(),variables);
    }


    /**
     * 激活或挂起流程定义
     *
     * @param state    状态 激活1 挂起2
     * @param deployId 流程部署ID
     */
    @Override
    public void updateState(Integer state, String deployId) {
        ProcessDefinition procDef = repositoryService.createProcessDefinitionQuery().deploymentId(deployId).singleResult();
        // 激活
        if (state == 1) {
            repositoryService.activateProcessDefinitionById(procDef.getId(), true, null);
        }
        // 挂起
        if (state == 2) {
            repositoryService.suspendProcessDefinitionById(procDef.getId(), true, null);
        }
    }


    /**
     * 删除流程定义
     *
     * @param deployId 流程部署ID act_ge_bytearray 表中 deployment_id值
     */
    @Override
    public void delete(String deployId) {
        // true 允许级联删除 ,不设置会导致数据库外键关联异常
        repositoryService.deleteDeployment(deployId, true);
    }


}
